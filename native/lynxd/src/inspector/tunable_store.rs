use std::collections::BTreeMap;
use std::fmt::Write as FmtWrite;
use std::fs;
use std::path::Path;

#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;

use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

const USER_TUNABLES_PATH: &str = "/data/adb/modules/Lynx/user_tunables.json";
const LEGACY_BOOT_SCRIPT_PATH: &str = "/data/adb/modules/Lynx/core/custom_tunables.sh";

#[derive(Debug, Clone)]
pub struct UserTunableEntry {
    pub path: String,
    pub value: String,
    pub original_value: String,
}

pub struct TunableStore;

impl TunableStore {
    pub fn is_safe(path: &str, value: &str) -> bool {
        let lower_path = path.trim().to_lowercase();
        let lower_val = value.trim().to_lowercase();

        if lower_val.is_empty()
            || lower_val.contains("<unsupported>")
            || lower_val.contains("from : to")
            || lower_val.contains('\n')
            || lower_val.contains('\r')
        {
            return false;
        }

        const FORBIDDEN: &[&str] = &[
            "trans_table",
            "scaling_setspeed",
            "time_in_state",
            "cpuinfo",
            "affected_cpus",
            "available_",
            "/stats",
            "debug_stat",
            "io_stat",
        ];
        if FORBIDDEN.iter().any(|k| lower_path.contains(k)) {
            return false;
        }

        if lower_path.ends_with("sched_latency_ns") {
            if let Ok(num) = lower_val.parse::<i64>() {
                if !(1_000_000..=50_000_000).contains(&num) {
                    return false;
                }
            } else {
                return false;
            }
        }
        if lower_path.ends_with("rmem_max") || lower_path.ends_with("wmem_max") {
            if let Ok(num) = lower_val.parse::<i64>() {
                if num < 65_536 {
                    return false;
                }
            } else {
                return false;
            }
        }
        if lower_path.ends_with("swappiness") {
            if let Ok(num) = lower_val.parse::<i32>() {
                if !(0..=200).contains(&num) {
                    return false;
                }
            } else {
                return false;
            }
        }

        true
    }

    pub fn load_entries() -> BTreeMap<String, UserTunableEntry> {
        let mut map = BTreeMap::new();
        if let Ok(content) = fs::read_to_string(USER_TUNABLES_PATH) {
            for line in content.lines() {
                let t = line.trim().trim_end_matches(',');
                if t.starts_with('{') && t.ends_with('}') && t.contains("\"path\"") {
                    let path = Self::extract_field(t, "path").unwrap_or_default();
                    let value = Self::extract_field(t, "value").unwrap_or_default();
                    let original_value = Self::extract_field(t, "original_value").unwrap_or_default();
                    if !path.is_empty() {
                        map.insert(
                            path.clone(),
                            UserTunableEntry {
                                path,
                                value,
                                original_value,
                            },
                        );
                    }
                }
            }
        }
        map
    }

    fn extract_field(json_line: &str, key: &str) -> Option<String> {
        let pattern = format!("\"{}\":\"", key);
        let start = json_line.find(&pattern)? + pattern.len();
        let rest = &json_line[start..];
        let end = rest.find('"')?;
        Some(rest[..end].to_string())
    }

    pub fn save_entries(entries: &BTreeMap<String, UserTunableEntry>) {
        let mut out = String::from("{\n  \"tunables\": [\n");
        for (idx, entry) in entries.values().enumerate() {
            if idx > 0 {
                out.push_str(",\n");
            }
            let _ = write!(
                out,
                "    {{\"path\":\"{}\",\"value\":\"{}\",\"original_value\":\"{}\"}}",
                entry.path.replace('"', ""),
                entry.value.replace('"', ""),
                entry.original_value.replace('"', "")
            );
        }
        out.push_str("\n  ]\n}\n");

        if let Some(parent) = Path::new(USER_TUNABLES_PATH).parent() {
            let _ = fs::create_dir_all(parent);
        }
        let _ = fs::write(USER_TUNABLES_PATH, out);
        Self::ensure_boot_facade_script();
    }

    fn ensure_boot_facade_script() {
        if let Some(parent) = Path::new(LEGACY_BOOT_SCRIPT_PATH).parent() {
            let _ = fs::create_dir_all(parent);
        }
        let script = "#!/system/bin/sh\n\
                      # Lynx Declarative Tunable Boot Delegator (Managed by lynxd v0.3.0)\n\
                      [ -f /sdcard/Debug/SAFE_MODE ] && exit 0\n\
                      [ -x /data/adb/modules/Lynx/system/bin/lynxd ] && exec /data/adb/modules/Lynx/system/bin/lynxd tunable apply\n";
        if fs::write(LEGACY_BOOT_SCRIPT_PATH, script).is_ok() {
            #[cfg(unix)]
            {
                let _ = fs::set_permissions(LEGACY_BOOT_SCRIPT_PATH, fs::Permissions::from_mode(0o755));
            }
        }
    }

    fn read_current_value(path: &str) -> String {
        if let Some(idx) = path.strip_prefix("/proc/ppm/policy_status:") {
            if let Ok(content) = fs::read_to_string("/proc/ppm/policy_status") {
                let prefix = format!("[{}]", idx);
                for line in content.lines() {
                    if line.trim().starts_with(&prefix) {
                        return if line.to_lowercase().contains("enabled") {
                            "1".to_string()
                        } else {
                            "0".to_string()
                        };
                    }
                }
            }
            return String::new();
        }
        SysfsReader::read_trimmed(path).unwrap_or_default()
    }

    fn write_hardware_node(path: &str, value: &str) -> Result<(), String> {
        if let Some(idx) = path.strip_prefix("/proc/ppm/policy_status:") {
            let payload = format!("{} {}", idx.trim(), value.trim());
            SysfsWriter::write("/proc/ppm/policy_status", payload, WriteMode::ForcePermission)
                .map_err(|e| format!("Failed writing PPM policy {}: {}", idx, e))
        } else {
            SysfsWriter::write(path, value.trim(), WriteMode::ForcePermission)
                .map_err(|e| format!("Failed writing {}: {}", path, e))
        }
    }

    pub fn set(path: &str, value: &str) -> Result<String, String> {
        let clean_path = path.trim();
        let clean_val = value.trim();

        if !Self::is_safe(clean_path, clean_val) {
            return Err(format!("Rejected unsafe tunable write: {} -> {}", clean_path, clean_val));
        }

        let mut entries = Self::load_entries();
        let current_hw = Self::read_current_value(clean_path);

        Self::write_hardware_node(clean_path, clean_val)?;

        let orig = entries
            .get(clean_path)
            .map(|e| e.original_value.clone())
            .filter(|v| !v.is_empty())
            .unwrap_or(current_hw);

        entries.insert(
            clean_path.to_string(),
            UserTunableEntry {
                path: clean_path.to_string(),
                value: clean_val.to_string(),
                original_value: orig,
            },
        );
        Self::save_entries(&entries);

        Ok(format!("Successfully wrote '{}' to {}", clean_val, clean_path))
    }

    pub fn apply_all() -> String {
        if Path::new("/sdcard/Debug/SAFE_MODE").exists()
            || Path::new("/data/adb/modules/.disable_magisk").exists()
            || Path::new("/data/adb/apatch/.disable").exists()
            || Path::new("/data/adb/modules/Lynx/disable").exists()
        {
            return "Safe Mode active; skipped applying user tunables.".to_string();
        }

        let entries = Self::load_entries();
        let mut applied = 0usize;
        for entry in entries.values() {
            if Self::is_safe(&entry.path, &entry.value) && Self::write_hardware_node(&entry.path, &entry.value).is_ok() {
                applied += 1;
            }
        }
        format!("Applied {} declarative user tunables.", applied)
    }

    pub fn reset(target: &str) -> String {
        let mut entries = Self::load_entries();
        if target == "all" || target.is_empty() {
            let mut restored = 0usize;
            for entry in entries.values() {
                if !entry.original_value.is_empty() {
                    let _ = Self::write_hardware_node(&entry.path, &entry.original_value);
                    restored += 1;
                }
            }
            entries.clear();
            Self::save_entries(&entries);
            format!("Reset and restored {} user tunables to kernel defaults.", restored)
        } else if let Some(entry) = entries.remove(target.trim()) {
            if !entry.original_value.is_empty() {
                let _ = Self::write_hardware_node(&entry.path, &entry.original_value);
            }
            Self::save_entries(&entries);
            format!("Reset '{}' back to '{}'.", entry.path, entry.original_value)
        } else {
            format!("No custom override recorded for '{}'.", target)
        }
    }

    pub fn list_json() -> String {
        if let Ok(content) = fs::read_to_string(USER_TUNABLES_PATH) {
            content
        } else {
            "{\"tunables\":[]}".to_string()
        }
    }
}
