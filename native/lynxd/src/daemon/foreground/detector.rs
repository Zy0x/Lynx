use std::fs;
use std::path::PathBuf;

use crate::daemon::foreground::cache::ForegroundCache;
use crate::daemon::foreground::rules::{ForegroundApp, RulesEngine};
use crate::sysfs::SysfsReader;

pub struct ForegroundDetector {
    rules: RulesEngine,
    cache: ForegroundCache,
    cgroup_nodes: Vec<PathBuf>,
    last_rules_mtime: u64,
}

impl ForegroundDetector {
    pub fn new() -> Self {
        // Multi-tier cgroup candidates: Tier 1 (cpuset v1 top-app & foreground), Tier 2 (unified cgroup v2)
        let candidates = [
            "/dev/cpuset/top-app/cgroup.procs",
            "/dev/cpuset/top-app/tasks",
            "/dev/cpuset/foreground/cgroup.procs",
            "/dev/cpuset/foreground/tasks",
            "/sys/fs/cgroup/top-app/cgroup.procs",
            "/sys/fs/cgroup/cgroup.procs",
        ];

        let mut cgroup_nodes = Vec::new();
        for &c in &candidates {
            let p = PathBuf::from(c);
            if SysfsReader::exists(&p) {
                cgroup_nodes.push(p);
            }
        }

        let mut detector = Self {
            rules: RulesEngine::new(),
            cache: ForegroundCache::new(64),
            cgroup_nodes,
            last_rules_mtime: 0,
        };
        detector.last_rules_mtime = detector.probe_max_rules_mtime();
        detector
    }

    /// Detects the raw package name of the top-most foreground application.
    /// Priority 1 & 2: Direct kernel cpuset procfs & cgroup v2 inspection (Sub-1ms, Zero Fork).
    pub fn detect_top_package(&self) -> Option<String> {
        // ── Priority 1 & 2: Kernel Cpuset & Cgroup v2 Procfs Inspection ─────
        let mut best_candidate: Option<(i32, String)> = None;

        for node_path in &self.cgroup_nodes {
            if let Ok(content) = fs::read_to_string(node_path) {
                for line in content.lines() {
                    let pid_str = line.trim();
                    if pid_str.is_empty() {
                        continue;
                    }

                    let cmdline_path = format!("/proc/{}/cmdline", pid_str);
                    if let Ok(cmd_bytes) = fs::read(&cmdline_path) {
                        if cmd_bytes.is_empty() {
                            continue;
                        }

                        // Parse null-terminated cmdline
                        let cmd_str = String::from_utf8_lossy(&cmd_bytes);
                        let first_token = cmd_str.split('\0').next().unwrap_or("").trim();

                        if Self::is_valid_user_package(first_token) {
                            // Check /proc/{pid}/oom_score_adj (Android sets TOP_APP_ADJ = 0 for focused activity)
                            let oom_adj_path = format!("/proc/{}/oom_score_adj", pid_str);
                            let oom_adj = fs::read_to_string(&oom_adj_path)
                                .ok()
                                .and_then(|s| s.trim().parse::<i32>().ok())
                                .unwrap_or(999);

                            if oom_adj == 0 {
                                // Direct match for active top-app foreground activity
                                return Some(first_token.to_string());
                            } else if oom_adj > 0 && oom_adj < 200 {
                                if best_candidate.as_ref().map_or(true, |(score, _)| oom_adj < *score) {
                                    best_candidate = Some((oom_adj, first_token.to_string()));
                                }
                            }
                        }
                    }
                }
            }
        }

        if let Some((_, candidate_pkg)) = best_candidate {
            return Some(candidate_pkg);
        }

        // ── Priority 2: Fallback focused dumpsys parsing ────────────────────
        #[cfg(target_os = "android")]
        {
            Self::query_dumpsys_top_app()
        }
        #[cfg(not(target_os = "android"))]
        {
            None
        }
    }

    fn probe_max_rules_mtime(&self) -> u64 {
        let candidates = [
            "/data/adb/lynx/app_rules.json",
            "/data/adb/modules/Lynx/core/app_rules.json",
            "/data/adb/lynx/app_rules.tsv",
            "/storage/emulated/0/Lynx/applist_perf.txt",
        ];
        let mut max_mtime = 0u64;
        for path in &candidates {
            if let Ok(meta) = fs::metadata(path) {
                if let Ok(mtime) = meta.modified() {
                    let secs = mtime
                        .duration_since(std::time::UNIX_EPOCH)
                        .unwrap_or_default()
                        .as_secs();
                    if secs > max_mtime {
                        max_mtime = secs;
                    }
                }
            }
        }
        max_mtime
    }

    fn check_and_reload_rules(&mut self) {
        let current_max = self.probe_max_rules_mtime();
        if current_max > self.last_rules_mtime {
            if self.last_rules_mtime > 0 {
                self.rules = RulesEngine::new();
                self.cache.clear();
            }
            self.last_rules_mtime = current_max;
        }
    }

    /// Queries, categorizes, and caches the current foreground application.
    pub fn detect(&mut self) -> Option<ForegroundApp> {
        self.check_and_reload_rules();
        let pkg = self.detect_top_package()?;

        // Fast in-memory cache lookup
        if let Some(cached) = self.cache.lookup(&pkg) {
            return Some(cached);
        }

        // Evaluate rules & store in cache
        let categorized = self.rules.categorize(&pkg);
        self.cache.store(pkg, categorized.clone());
        Some(categorized)
    }

    /// Validates whether a process cmdline represents an application package.
    fn is_valid_user_package(cmd: &str) -> bool {
        // Must contain at least one dot (e.g. com.something)
        if !cmd.contains('.') {
            return false;
        }

        // Ignore common system daemons, HAL services, input methods, and helper services
        let lower = cmd.to_lowercase();
        if lower.starts_with("surfaceflinger")
            || lower.starts_with("android.hardware")
            || lower.starts_with("vendor.")
            || lower.starts_with("/vendor/")
            || lower.starts_with("/system/")
            || lower.starts_with("composer-service")
            || lower.contains("inputmethod")
            || lower.contains("keyboard")
            || lower.ends_with(".tts")
            || lower == "system_server"
        {
            return false;
        }

        true
    }

    #[cfg(target_os = "android")]
    fn query_dumpsys_top_app() -> Option<String> {
        // Ultra-lightweight fallback: dumpsys window (sub-30ms, zero ATMS global lock contention)
        use std::process::Command;

        let output = Command::new("dumpsys")
            .args(&["window"])
            .output()
            .ok()?;

        if !output.status.success() {
            return None;
        }

        let out_str = String::from_utf8_lossy(&output.stdout);
        for line in out_str.lines() {
            if line.contains("mCurrentFocus") || line.contains("mFocusedApp") {
                // Example format: mCurrentFocus=Window{... u0 com.tencent.ig/...}
                for part in line.split_whitespace() {
                    if part.contains('/') && part.contains('.') {
                        let token = part.trim_end_matches('}');
                        let pkg = token.split('/').next()?;
                        let clean_pkg = pkg.split(':').last()?.trim();
                        if Self::is_valid_user_package(clean_pkg) {
                            return Some(clean_pkg.to_string());
                        }
                    }
                }
            }
        }

        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_valid_user_package_filter() {
        assert!(ForegroundDetector::is_valid_user_package("com.tencent.ig"));
        assert!(ForegroundDetector::is_valid_user_package("com.mobile.legends"));
        assert!(!ForegroundDetector::is_valid_user_package("surfaceflinger"));
        assert!(!ForegroundDetector::is_valid_user_package("vendor.qti.hardware.display"));
        assert!(!ForegroundDetector::is_valid_user_package("system_server"));
        assert!(!ForegroundDetector::is_valid_user_package("com.google.android.inputmethod.latin"));
        assert!(!ForegroundDetector::is_valid_user_package("com.google.android.tts"));
    }
}
