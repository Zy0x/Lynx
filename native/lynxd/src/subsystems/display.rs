use std::fs;
use std::path::Path;
use std::process::Command;

use crate::sysfs::{SysfsWriter, WriteMode};

pub struct DisplayController;

impl DisplayController {
    fn write_opt(path: impl AsRef<Path>, val: impl AsRef<str>) -> bool {
        let p = path.as_ref();
        if p.exists() {
            SysfsWriter::write(p, val, WriteMode::ForcePermission).is_ok()
        } else {
            false
        }
    }

    /// Sets the dynamic display refresh rate (e.g. 60, 90, 120, 144 Hz).
    /// Updates Android settings (min_refresh_rate and peak_refresh_rate)
    /// and backs up the original minimum rate for clean restoration.
    pub fn set_refresh_rate(hz: u32) -> String {
        let hz_str = format!("{}.0", hz);

        // Backup original min refresh rate once
        let orig_path = Path::new("/dev/lynx_orig_min_rr");
        if !orig_path.exists() {
            if let Ok(out) = Command::new("settings")
                .args(["get", "system", "min_refresh_rate"])
                .output()
            {
                let cur = String::from_utf8_lossy(&out.stdout).trim().to_string();
                let val_to_save = if cur.is_empty() || cur == "null" {
                    "60.0".to_string()
                } else {
                    cur
                };
                let _ = fs::write(orig_path, val_to_save);
            }
        }

        // Apply new min & peak refresh rates
        let _ = Command::new("settings")
            .args(["put", "system", "min_refresh_rate", &hz_str])
            .output();
        let _ = Command::new("settings")
            .args(["put", "system", "peak_refresh_rate", &hz_str])
            .output();

        format!("Display refresh rate set to {} Hz (min/peak: {}).", hz, hz_str)
    }

    /// Restores the original baseline refresh rate.
    pub fn restore_refresh_rate() -> String {
        let orig_path = Path::new("/dev/lynx_orig_min_rr");
        if orig_path.exists() {
            if let Ok(orig_val) = fs::read_to_string(orig_path) {
                let trimmed = orig_val.trim();
                if !trimmed.is_empty() {
                    let _ = Command::new("settings")
                        .args(["put", "system", "min_refresh_rate", trimmed])
                        .output();
                }
            }
            let _ = fs::remove_file(orig_path);
            "Display refresh rate restored to OEM baseline.".to_string()
        } else {
            "No refresh rate override active (baseline intact).".to_string()
        }
    }

    /// Toggles touch sampling rate boost across common OEM kernel nodes.
    pub fn apply_touch_boost(enable: bool) -> String {
        let val = if enable { "1" } else { "0" };
        let mut count = 0usize;

        let touch_nodes = [
            "/sys/class/touch/touch_dev/touch_game_mode",
            "/sys/devices/virtual/touch/touch_dev/bump_sample_rate",
            "/proc/touchscreen/game_mode",
            "/sys/devices/platform/goodix_ts.0/game_mode",
            "/sys/devices/platform/tp_wake_switch/game_mode",
        ];

        for node in touch_nodes {
            if Self::write_opt(node, val) {
                count += 1;
            }
        }

        // Wildcard scan for virtual inputs
        if let Ok(entries) = fs::read_dir("/sys/devices/virtual/input") {
            for entry in entries.flatten() {
                let gm_path = entry.path().join("touch_game_mode");
                if Self::write_opt(gm_path, val) {
                    count += 1;
                }
            }
        }

        format!(
            "Touch boost set to {} ({} kernel nodes updated).",
            if enable { "ENABLED" } else { "DISABLED" },
            count
        )
    }

    /// Returns current refresh rate settings as JSON.
    pub fn info_json() -> String {
        let min_rr = Command::new("settings")
            .args(["get", "system", "min_refresh_rate"])
            .output()
            .ok()
            .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
            .filter(|s| !s.is_empty() && s != "null")
            .unwrap_or_else(|| "60.0".to_string());

        let peak_rr = Command::new("settings")
            .args(["get", "system", "peak_refresh_rate"])
            .output()
            .ok()
            .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
            .filter(|s| !s.is_empty() && s != "null")
            .unwrap_or_else(|| "60.0".to_string());

        let has_override = Path::new("/dev/lynx_orig_min_rr").exists();

        format!(
            "{{\"min_refresh_rate\":\"{}\",\"peak_refresh_rate\":\"{}\",\"override_active\":{}}}",
            min_rr, peak_rr, has_override
        )
    }
}
