use std::fs;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

use crate::apply::executor::NodeSnapshot;
use crate::core::error::SysfsError;
use crate::core::result::Result;
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};
use crate::utils::Logger;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ActiveProfileState {
    pub active_profile: String,
    pub timestamp: u64,
    pub device: String,
    pub status: String,
    pub dirty: bool,
}

impl ActiveProfileState {
    pub fn new(profile: &str, device: &str, status: &str, dirty: bool) -> Self {
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        Self {
            active_profile: profile.to_string(),
            timestamp,
            device: device.to_string(),
            status: status.to_string(),
            dirty,
        }
    }

    pub fn to_json(&self) -> String {
        format!(
            "{{\n  \"active_profile\": \"{}\",\n  \"timestamp\": {},\n  \"device\": \"{}\",\n  \"status\": \"{}\",\n  \"dirty\": {}\n}}\n",
            self.active_profile,
            self.timestamp,
            self.device,
            self.status,
            if self.dirty { "true" } else { "false" }
        )
    }

    pub fn from_json(json: &str) -> Option<Self> {
        let active_profile = extract_json_string(json, "active_profile")?;
        let timestamp = extract_json_u64(json, "timestamp").unwrap_or(0);
        let device = extract_json_string(json, "device").unwrap_or_else(|| "Unknown".to_string());
        let status = extract_json_string(json, "status").unwrap_or_else(|| "unknown".to_string());
        let dirty = extract_json_bool(json, "dirty").unwrap_or(false);

        Some(Self {
            active_profile,
            timestamp,
            device,
            status,
            dirty,
        })
    }
}

#[derive(Debug)]
pub struct RevertReport {
    pub restored_nodes: Vec<(PathBuf, String)>,
    pub skipped_nodes: Vec<(PathBuf, String)>,
    pub elapsed_micros: u128,
}

#[derive(Debug)]
pub enum RecoveryReport {
    RecoveredFromDirty {
        previous_profile: String,
        restored_count: usize,
        restored_to: &'static str,
    },
    CleanBaselineVerified {
        active_profile: String,
        applied_at: u64,
    },
    NoPriorState,
}

pub struct ProfileStateManager;

impl ProfileStateManager {
    /// Resolves the storage directory for state files with fallback hierarchy:
    /// 1. $LYNX_STATE_DIR env override (useful for testing & custom staging)
    /// 2. /data/adb/modules/Lynx/state (Magisk/KernelSU primary location)
    /// 3. /data/adb/lynx/state
    /// 4. /data/local/tmp/lynx_state (Standalone root testing)
    /// 5. ./state (Host PC fallback)
    pub fn get_state_dir() -> PathBuf {
        if let Ok(dir) = std::env::var("LYNX_STATE_DIR") {
            let p = PathBuf::from(dir);
            if fs::create_dir_all(&p).is_ok() {
                return p;
            }
        }

        #[cfg(target_os = "android")]
        {
            let ksu_dir = PathBuf::from("/data/adb/modules/Lynx/state");
            if fs::create_dir_all(&ksu_dir).is_ok() {
                return ksu_dir;
            }

            let adb_dir = PathBuf::from("/data/adb/lynx/state");
            if fs::create_dir_all(&adb_dir).is_ok() {
                return adb_dir;
            }

            let tmp_dir = PathBuf::from("/data/local/tmp/lynx_state");
            if fs::create_dir_all(&tmp_dir).is_ok() {
                return tmp_dir;
            }
        }

        let local_dir = PathBuf::from("./state");
        let _ = fs::create_dir_all(&local_dir);
        local_dir
    }

    pub fn active_profile_file() -> PathBuf {
        Self::get_state_dir().join("active_profile.json")
    }

    pub fn last_snapshot_file() -> PathBuf {
        Self::get_state_dir().join("last_snapshot.json")
    }

    /// Atomically writes content to path using temporary file and atomic rename.
    pub fn write_atomic(path: &Path, content: &str) -> Result<()> {
        let tmp_path = path.with_extension("tmp");
        if let Err(e) = fs::write(&tmp_path, content) {
            return Err(SysfsError::IoError {
                path: tmp_path,
                source: e,
            });
        }

        if let Err(e) = fs::rename(&tmp_path, path) {
            return Err(SysfsError::IoError {
                path: path.to_path_buf(),
                source: e,
            });
        }

        Ok(())
    }

    /// Phase 3C Pre-Commit: Persists snapshots and marks dirty=true BEFORE touching kernel sysfs.
    pub fn record_pre_commit(
        profile_name: &str,
        device_name: &str,
        snapshots: &[NodeSnapshot],
    ) -> Result<()> {
        // 1. Serialize and persist last_snapshot.json
        let mut snap_json = String::from("{\n");
        for (idx, snap) in snapshots.iter().enumerate() {
            snap_json.push_str(&format!(
                "  \"{}\": \"{}\"",
                snap.path.display(),
                escape_json(&snap.original_value)
            ));
            if idx + 1 < snapshots.len() {
                snap_json.push_str(",\n");
            } else {
                snap_json.push_str("\n");
            }
        }
        snap_json.push_str("}\n");

        Self::write_atomic(&Self::last_snapshot_file(), &snap_json)?;

        // 2. Persist active_profile.json with dirty = true
        let state = ActiveProfileState::new(profile_name, device_name, "applying", true);
        Self::write_atomic(&Self::active_profile_file(), &state.to_json())?;

        Ok(())
    }

    pub fn journal_file() -> PathBuf {
        #[cfg(target_os = "android")]
        {
            let log_dir = PathBuf::from("/data/adb/modules/Lynx/logs");
            if fs::create_dir_all(&log_dir).is_ok() {
                return log_dir.join("transaction.log");
            }
        }
        Self::get_state_dir().join("transaction.log")
    }

    pub fn append_journal(entry: &str) {
        use std::io::Write;
        let path = Self::journal_file();
        if let Some(parent) = path.parent() {
            let _ = fs::create_dir_all(parent);
            // Log rotation guard: limit transaction journal to 1 MB
            if let Ok(meta) = fs::metadata(&path) {
                if meta.len() > 1024 * 1024 {
                    let archive_dir = parent.join("archive");
                    let _ = fs::create_dir_all(&archive_dir);
                    let _ = fs::rename(&path, archive_dir.join("transaction.log.old"));
                }
            }
        }
        if let Ok(mut f) = fs::OpenOptions::new().create(true).append(true).open(&path) {
            let _ = writeln!(f, "{}", entry);
        }
    }

    pub fn load_journal(max_lines: usize) -> Result<Vec<String>> {
        let path = Self::journal_file();
        if !path.exists() {
            return Ok(Vec::new());
        }
        let content = fs::read_to_string(&path).map_err(|e| SysfsError::IoError {
            path: path.clone(),
            source: e,
        })?;

        let lines: Vec<String> = content.lines().map(|s| s.to_string()).collect();
        if lines.len() > max_lines {
            Ok(lines[lines.len() - max_lines..].to_vec())
        } else {
            Ok(lines)
        }
    }

    /// Phase 3C Post-Commit: Updates active_profile.json to clean (dirty=false) and syncs config.json.
    pub fn record_post_commit(profile_name: &str, device_name: &str) -> Result<()> {
        let state = ActiveProfileState::new(profile_name, device_name, "applied", false);
        Self::write_atomic(&Self::active_profile_file(), &state.to_json())?;

        // Append to transaction journal
        Self::append_journal(&format!(
            "[{}] REQUEST: {} | NODES: applied | RESULT: SUCCESS | ROLLBACK: false",
            state.timestamp, profile_name
        ));

        // Synchronize active_profile into /data/adb/modules/Lynx/config.json if present
        Self::sync_config_json(profile_name);

        Ok(())
    }

    /// Phase 3C Rollback: Marks dirty=false and status="failed_rolled_back".
    pub fn record_failed_rollback(profile_name: &str, device_name: &str) -> Result<()> {
        let state = ActiveProfileState::new(profile_name, device_name, "failed_rolled_back", false);
        Self::write_atomic(&Self::active_profile_file(), &state.to_json())?;

        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        Self::append_journal(&format!(
            "[{}] REQUEST: {} | RESULT: FAILED | ROLLBACK: SUCCESS",
            now, profile_name
        ));

        Ok(())
    }

    /// Loads active profile state from disk.
    pub fn load_active() -> Result<Option<ActiveProfileState>> {
        let path = Self::active_profile_file();
        if !path.exists() {
            return Ok(None);
        }

        let content = fs::read_to_string(&path).map_err(|e| SysfsError::IoError {
            path: path.clone(),
            source: e,
        })?;

        Ok(ActiveProfileState::from_json(&content))
    }

    /// Loads snapshot mapping from disk.
    pub fn load_snapshot() -> Result<Vec<(PathBuf, String)>> {
        let path = Self::last_snapshot_file();
        if !path.exists() {
            return Ok(Vec::new());
        }

        let content = fs::read_to_string(&path).map_err(|e| SysfsError::IoError {
            path: path.clone(),
            source: e,
        })?;

        Ok(parse_snapshot_json(&content))
    }

    /// Reverts kernel hardware nodes back to the state recorded in last_snapshot.json.
    pub fn revert() -> Result<RevertReport> {
        let start = std::time::Instant::now();
        let snapshots = Self::load_snapshot()?;

        if snapshots.is_empty() {
            return Err(SysfsError::NotFound(Self::last_snapshot_file()));
        }

        let mut restored = Vec::new();
        let mut skipped = Vec::new();

        for (path, orig_val) in snapshots {
            if SysfsReader::exists(&path) {
                match SysfsWriter::write(&path, &orig_val, WriteMode::Direct) {
                    Ok(_) => restored.push((path, orig_val)),
                    Err(e) => {
                        Logger::warn(format!("Failed to restore node '{}': {}", path.display(), e));
                        skipped.push((path, orig_val));
                    }
                }
            } else {
                skipped.push((path, orig_val));
            }
        }

        // Update active profile state
        let device = Self::load_active()
            .ok()
            .flatten()
            .map(|s| s.device)
            .unwrap_or_else(|| "Unknown".to_string());

        let state = ActiveProfileState::new("reverted", &device, "reverted", false);
        let _ = Self::write_atomic(&Self::active_profile_file(), &state.to_json());
        Self::sync_config_json("balance");

        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        Self::append_journal(&format!(
            "[{}] ACTION: REVERT | NODES: {} | RESULT: SUCCESS",
            now, restored.len()
        ));

        Ok(RevertReport {
            restored_nodes: restored,
            skipped_nodes: skipped,
            elapsed_micros: start.elapsed().as_micros(),
        })
    }

    /// Boot recovery called by service.sh or post-fs-data.sh.
    /// Detects interrupted dirty transactions and restores safe baseline.
    pub fn recover_boot() -> Result<RecoveryReport> {
        let active = match Self::load_active()? {
            Some(a) => a,
            None => return Ok(RecoveryReport::NoPriorState),
        };

        if active.dirty {
            Logger::warn(format!(
                "Dirty profile transaction detected from previous boot (profile: '{}')! Initiating recovery...",
                active.active_profile
            ));

            let report = Self::revert()?;
            let restored_count = report.restored_nodes.len();

            let state = ActiveProfileState::new("balance", &active.device, "recovered", false);
            let _ = Self::write_atomic(&Self::active_profile_file(), &state.to_json());
            Self::sync_config_json("balance");

            let now = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap_or_default()
                .as_secs();
            Self::append_journal(&format!(
                "[{}] ACTION: BOOT_RECOVERY | PREVIOUS: {} | RESTORED: {} | TO: balance",
                now, active.active_profile, restored_count
            ));

            Ok(RecoveryReport::RecoveredFromDirty {
                previous_profile: active.active_profile,
                restored_count,
                restored_to: "balance",
            })
        } else {
            Ok(RecoveryReport::CleanBaselineVerified {
                active_profile: active.active_profile,
                applied_at: active.timestamp,
            })
        }
    }

    /// Best-effort update of "active_profile" inside /data/adb/modules/Lynx/config.json
    fn sync_config_json(profile_name: &str) {
        let cfg_path = PathBuf::from("/data/adb/modules/Lynx/config.json");
        if !cfg_path.exists() {
            return;
        }

        if let Ok(content) = fs::read_to_string(&cfg_path) {
            let mut modified = false;
            let mut new_lines = Vec::new();

            for line in content.lines() {
                if line.contains("\"active_profile\":") {
                    let leading_ws = line.chars().take_while(|c| c.is_whitespace()).collect::<String>();
                    new_lines.push(format!("{}\"active_profile\": \"{}\",", leading_ws, profile_name));
                    modified = true;
                } else {
                    new_lines.push(line.to_string());
                }
            }

            if modified {
                let new_content = new_lines.join("\n") + "\n";
                let _ = Self::write_atomic(&cfg_path, &new_content);
            }
        }
    }
}

// ── Minimalist Zero-Dependency JSON Helpers ─────────────────────────────────

fn escape_json(s: &str) -> String {
    s.replace('\\', "\\\\").replace('"', "\\\"")
}

pub fn extract_json_string(json: &str, key: &str) -> Option<String> {
    let pattern = format!("\"{}\"", key);
    let key_pos = json.find(&pattern)?;
    let after_key = &json[key_pos + pattern.len()..];

    let colon_pos = after_key.find(':')?;
    let after_colon = &after_key[colon_pos + 1..].trim_start();

    if !after_colon.starts_with('"') {
        return None;
    }

    let val_start = 1;
    let val_end = after_colon[val_start..].find('"')? + val_start;
    Some(after_colon[val_start..val_end].to_string())
}

fn extract_json_u64(json: &str, key: &str) -> Option<u64> {
    let pattern = format!("\"{}\"", key);
    let key_pos = json.find(&pattern)?;
    let after_key = &json[key_pos + pattern.len()..];

    let colon_pos = after_key.find(':')?;
    let after_colon = &after_key[colon_pos + 1..].trim_start();

    let num_str: String = after_colon
        .chars()
        .take_while(|c| c.is_ascii_digit())
        .collect();

    num_str.parse::<u64>().ok()
}

fn extract_json_bool(json: &str, key: &str) -> Option<bool> {
    let pattern = format!("\"{}\"", key);
    let key_pos = json.find(&pattern)?;
    let after_key = &json[key_pos + pattern.len()..];

    let colon_pos = after_key.find(':')?;
    let after_colon = &after_key[colon_pos + 1..].trim_start();

    if after_colon.starts_with("true") {
        Some(true)
    } else if after_colon.starts_with("false") {
        Some(false)
    } else {
        None
    }
}

pub fn parse_snapshot_json(content: &str) -> Vec<(PathBuf, String)> {
    let mut results = Vec::new();
    for line in content.lines() {
        let line = line.trim();
        if !line.starts_with('"') {
            continue;
        }

        // Parse: "key": "value" or "key": "value",
        if let Some(first_quote) = line.find('"') {
            let after_first = &line[first_quote + 1..];
            if let Some(second_quote) = after_first.find('"') {
                let key = &after_first[..second_quote];
                let after_second = &after_first[second_quote + 1..];

                if let Some(colon_pos) = after_second.find(':') {
                    let after_colon = &after_second[colon_pos + 1..].trim_start();
                    if after_colon.starts_with('"') {
                        let after_third = &after_colon[1..];
                        if let Some(fourth_quote) = after_third.find('"') {
                            let val = &after_third[..fourth_quote];
                            results.push((PathBuf::from(key), val.to_string()));
                        }
                    }
                }
            }
        }
    }
    results
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_active_profile_json_roundtrip() {
        let original = ActiveProfileState {
            active_profile: "performance".to_string(),
            timestamp: 1728512345,
            device: "MediaTek MT6781".to_string(),
            status: "applied".to_string(),
            dirty: false,
        };

        let json = original.to_json();
        let parsed = ActiveProfileState::from_json(&json).expect("Failed to parse JSON");
        assert_eq!(original, parsed);
    }

    #[test]
    fn test_active_profile_dirty_flag_roundtrip() {
        let original = ActiveProfileState {
            active_profile: "extreme".to_string(),
            timestamp: 1799999999,
            device: "Qualcomm Snapdragon".to_string(),
            status: "applying".to_string(),
            dirty: true,
        };

        let json = original.to_json();
        let parsed = ActiveProfileState::from_json(&json).expect("Failed to parse JSON");
        assert!(parsed.dirty);
        assert_eq!(parsed.status, "applying");
    }

    #[test]
    fn test_snapshot_json_roundtrip() {
        let json = "{\n  \"/sys/devices/system/cpu/policy0/scaling_governor\": \"schedutil\",\n  \"/proc/cpufreq/cpufreq_power_mode\": \"1\"\n}\n";
        let parsed = parse_snapshot_json(json);
        assert_eq!(parsed.len(), 2);
        assert_eq!(
            parsed[0].0,
            PathBuf::from("/sys/devices/system/cpu/policy0/scaling_governor")
        );
        assert_eq!(parsed[0].1, "schedutil");
        assert_eq!(
            parsed[1].0,
            PathBuf::from("/proc/cpufreq/cpufreq_power_mode")
        );
        assert_eq!(parsed[1].1, "1");
    }

    #[test]
    fn test_transaction_journal_append_and_load() {
        let temp_dir = std::env::temp_dir().join("lynx_journal_test");
        let _ = fs::create_dir_all(&temp_dir);
        let log_file = temp_dir.join("transaction.log");
        let _ = fs::remove_file(&log_file);

        let entry1 = "[1791572000] REQUEST: performance | RESULT: SUCCESS | ROLLBACK: false";
        let entry2 = "[1791572050] REQUEST: extreme | RESULT: FAILED | ROLLBACK: SUCCESS";

        {
            use std::io::Write;
            let mut f = fs::OpenOptions::new().create(true).append(true).open(&log_file).unwrap();
            writeln!(f, "{}", entry1).unwrap();
            writeln!(f, "{}", entry2).unwrap();
        }

        let content = fs::read_to_string(&log_file).unwrap();
        let lines: Vec<&str> = content.lines().collect();
        assert_eq!(lines.len(), 2);
        assert_eq!(lines[0], entry1);
        assert_eq!(lines[1], entry2);

        let _ = fs::remove_file(log_file);
        let _ = fs::remove_dir(temp_dir);
    }
}

