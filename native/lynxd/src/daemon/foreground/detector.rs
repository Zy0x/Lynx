use std::fs;
use std::path::PathBuf;

use crate::daemon::foreground::cache::ForegroundCache;
use crate::daemon::foreground::rules::{ForegroundApp, RulesEngine};
use crate::sysfs::SysfsReader;

pub struct ForegroundDetector {
    rules: RulesEngine,
    cache: ForegroundCache,
    cgroup_nodes: Vec<PathBuf>,
}

impl ForegroundDetector {
    pub fn new() -> Self {
        // Multi-tier cgroup candidates: Tier 1 (cpuset v1), Tier 2 (unified cgroup v2)
        let candidates = [
            "/dev/cpuset/top-app/cgroup.procs",
            "/dev/cpuset/top-app/tasks",
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

        Self {
            rules: RulesEngine::new(),
            cache: ForegroundCache::new(64),
            cgroup_nodes,
        }
    }

    /// Detects the raw package name of the top-most foreground application.
    /// Priority 1 & 2: Direct kernel cpuset procfs & cgroup v2 inspection (Sub-1ms, Zero Fork).
    pub fn detect_top_package(&self) -> Option<String> {
        // ── Priority 1 & 2: Kernel Cpuset & Cgroup v2 Procfs Inspection ─────
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
                            return Some(first_token.to_string());
                        }
                    }
                }
            }
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

    /// Queries, categorizes, and caches the current foreground application.
    pub fn detect(&mut self) -> Option<ForegroundApp> {
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

        // Ignore common system daemons and HAL services that reside in top-app cpuset
        let lower = cmd.to_lowercase();
        if lower.starts_with("surfaceflinger")
            || lower.starts_with("android.hardware")
            || lower.starts_with("vendor.")
            || lower.starts_with("/vendor/")
            || lower.starts_with("/system/")
            || lower.starts_with("composer-service")
            || lower == "system_server"
        {
            return false;
        }

        true
    }

    #[cfg(target_os = "android")]
    fn query_dumpsys_top_app() -> Option<String> {
        // Single focused query fallback: dumpsys activity activities (only topResumedActivity)
        use std::process::Command;

        let output = Command::new("dumpsys")
            .args(&["activity", "activities"])
            .output()
            .ok()?;

        if !output.status.success() {
            return None;
        }

        let out_str = String::from_utf8_lossy(&output.stdout);
        for line in out_str.lines() {
            if line.contains("topResumedActivity") {
                // Example format: topResumedActivity=ActivityRecord{... u0 com.tencent.ig/...}
                for part in line.split_whitespace() {
                    if part.contains('/') && part.contains('.') {
                        let pkg = part.split('/').next()?;
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
    }
}
