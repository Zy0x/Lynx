use std::fs;
use std::path::Path;
use std::process::Command;
use std::sync::atomic::{AtomicU32, AtomicU64, Ordering};
use std::sync::OnceLock;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::daemon::foreground::rules::{AppCategory, ForegroundApp};
use crate::daemon::TouchDetector;
use crate::state::ConfigStore;
use crate::sysfs::{SysfsWriter, WriteMode};
use crate::utils::Logger;

static CURRENT_DISPLAY_HZ: AtomicU32 = AtomicU32::new(0);
static LAST_HZ_CHANGE_MS: AtomicU64 = AtomicU64::new(0);

#[derive(Debug, Clone, Default)]
pub struct DisplayModeTable {
    pub supported_rates: Vec<u32>,
    pub sf_modes: Vec<(u32, i32)>, // (Hz, SurfaceFlinger mode ID)
}

static DISPLAY_MODE_TABLE: OnceLock<DisplayModeTable> = OnceLock::new();

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

    /// Automatically probes physical display refresh rates and SurfaceFlinger mode IDs.
    /// Runs once at startup and caches the result with zero continuous shell overhead.
    pub fn probe_display_modes() -> DisplayModeTable {
        let mut supported_rates = Vec::new();
        let mut sf_modes = Vec::new();

        // 1. Try dumpsys SurfaceFlinger (Direct HWC mode indices)
        if let Ok(out) = Command::new("dumpsys").arg("SurfaceFlinger").output() {
            let text = String::from_utf8_lossy(&out.stdout);
            for line in text.lines() {
                if line.contains("refreshRate=") && line.contains("id=") {
                    let mut sf_id: Option<i32> = None;
                    let mut hz: Option<u32> = None;

                    for token in line.split(',') {
                        let token = token.trim();
                        if token.starts_with("{id=") || token.starts_with("id=") {
                            if let Some(id_str) = token.split('=').nth(1) {
                                sf_id = id_str.trim().parse::<i32>().ok();
                            }
                        } else if token.starts_with("refreshRate=") {
                            if let Some(val_str) = token.split('=').nth(1) {
                                let num_str = val_str.split_whitespace().next().unwrap_or("");
                                if let Ok(val_f) = num_str.parse::<f32>() {
                                    hz = Some(val_f.round() as u32);
                                }
                            }
                        }
                    }

                    if let (Some(id), Some(rate)) = (sf_id, hz) {
                        if rate >= 30 && rate <= 360 {
                            if !supported_rates.contains(&rate) {
                                supported_rates.push(rate);
                            }
                            if !sf_modes.iter().any(|(r, _)| *r == rate) {
                                sf_modes.push((rate, id));
                            }
                        }
                    }
                }
            }
        }

        // 2. Fallback to dumpsys display if SurfaceFlinger parsing didn't find modes
        if supported_rates.is_empty() {
            if let Ok(out) = Command::new("dumpsys").arg("display").output() {
                let text = String::from_utf8_lossy(&out.stdout);
                for line in text.lines() {
                    if line.contains("fps=") || line.contains("renderFrameRate") {
                        for token in line.split(|c: char| c == ',' || c == ' ' || c == '{' || c == '}') {
                            if token.starts_with("fps=") {
                                if let Some(fps_val) = token.strip_prefix("fps=") {
                                    if let Ok(val_f) = fps_val.parse::<f32>() {
                                        let rate = val_f.round() as u32;
                                        if rate >= 30 && rate <= 360 && !supported_rates.contains(&rate) {
                                            supported_rates.push(rate);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Fallback baseline 60 Hz if detection returned empty
        if supported_rates.is_empty() {
            supported_rates.push(60);
            sf_modes.push((60, 0));
        }

        supported_rates.sort_unstable();
        sf_modes.sort_by_key(|(hz, _)| *hz);

        DisplayModeTable {
            supported_rates,
            sf_modes,
        }
    }

    pub fn get_display_modes() -> &'static DisplayModeTable {
        DISPLAY_MODE_TABLE.get_or_init(Self::probe_display_modes)
    }

    /// Sets the physical display refresh rate (e.g. 60, 90, 120, 144 Hz).
    /// Updates Android settings (min_refresh_rate and peak_refresh_rate),
    /// SurfaceFlinger HWC physical mode, and backs up original baseline for clean restoration.
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

        let modes = Self::get_display_modes();
        let sf_id = modes.sf_modes.iter().find(|(h, _)| *h == hz).map(|(_, id)| *id);
        Self::apply_hz_hardware(hz, sf_id);

        CURRENT_DISPLAY_HZ.store(hz, Ordering::Relaxed);
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

    /// Universally configures hardware composer, Android framework settings, and panel sysfs nodes.
    pub fn apply_hz_hardware(target_hz: u32, sf_id: Option<i32>) {
        // 1. Direct SurfaceFlinger HWC mode switch (sub-ms instant physical mode change)
        if let Some(id) = sf_id {
            let _ = Command::new("service")
                .args(["call", "SurfaceFlinger", "1035", "i32", &id.to_string()])
                .output();
        }

        // 2. Universal Android settings provider
        let hz_str = format!("{}.0", target_hz);
        let _ = Command::new("settings")
            .args(["put", "system", "min_refresh_rate", &hz_str])
            .output();
        let _ = Command::new("settings")
            .args(["put", "system", "peak_refresh_rate", &hz_str])
            .output();
        let _ = Command::new("settings")
            .args(["put", "secure", "user_refresh_rate", &target_hz.to_string()])
            .output();

        // 3. Kernel / Vendor Panel Sysfs Nodes
        let nodes = [
            "/sys/devices/virtual/graphics/fb0/mode",
            "/sys/class/graphics/fb0/mode",
            "/proc/driver/disp_fps",
            "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/fps",
        ];
        for node in nodes {
            Self::write_opt(node, &target_hz.to_string());
        }
    }

    /// Autonomous Universal Multi-Factor Dynamic Display Governor tick executed on every daemon cycle.
    /// Scales physical display refresh rate instantly (0 ms) on touch, drops to low-rate during media/reading,
    /// and locks to high-rate during gaming to prevent mode-switching flickers.
    pub fn tick_dynamic_display_scaler(
        fg_app: Option<&ForegroundApp>,
        active_profile: &str,
        is_screen_on: bool,
    ) {
        if !is_screen_on {
            return;
        }

        let modes = Self::get_display_modes();
        if modes.supported_rates.len() <= 1 {
            // Panel has single physical rate (e.g. 60Hz only on RN7). Safe no-op!
            return;
        }

        // Check user config override (fixed rate vs auto)
        let lock_cfg = ConfigStore::get("display_touch.refresh_rate_lock")
            .and_then(|v| v.parse::<u32>().ok())
            .unwrap_or(0);

        // Also check if system settings have min_refresh_rate == peak_refresh_rate and min > 1.0 (Static fixed mode locked by user)
        let system_min = Command::new("settings")
            .args(["get", "system", "min_refresh_rate"])
            .output()
            .ok()
            .and_then(|o| String::from_utf8_lossy(&o.stdout).trim().parse::<f32>().ok())
            .unwrap_or(0.0);

        let system_peak = Command::new("settings")
            .args(["get", "system", "peak_refresh_rate"])
            .output()
            .ok()
            .and_then(|o| String::from_utf8_lossy(&o.stdout).trim().parse::<f32>().ok())
            .unwrap_or(0.0);

        let is_system_locked = system_min > 1.0 && (system_min - system_peak).abs() < 0.5;

        if lock_cfg > 0 || is_system_locked {
            // User explicitly chose a fixed locked rate! DO NOT AUTO-SCALE!
            let locked_rate = if lock_cfg > 0 {
                lock_cfg
            } else {
                system_peak.round() as u32
            };

            if CURRENT_DISPLAY_HZ.load(Ordering::Relaxed) != locked_rate {
                let sf_id = modes.sf_modes.iter().find(|(hz, _)| *hz == locked_rate).map(|(_, id)| *id);
                Self::apply_hz_hardware(locked_rate, sf_id);
                CURRENT_DISPLAY_HZ.store(locked_rate, Ordering::Relaxed);
            }
            return;
        }

        let low_hz = modes.supported_rates.first().copied().unwrap_or(60);
        let high_hz = modes.supported_rates.last().copied().unwrap_or(60);
        let mid_hz = if modes.supported_rates.len() >= 3 {
            modes.supported_rates[1]
        } else {
            high_hz
        };

        let now_ms = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as u64;

        let is_game = active_profile == "performance"
            || active_profile == "extreme"
            || fg_app.map(|a| a.category == AppCategory::Game).unwrap_or(false);

        let is_media = fg_app.map(|a| a.category == AppCategory::Media).unwrap_or(false);

        let is_rapid = TouchDetector::is_rapid_touch(150, 2);
        let is_moving = TouchDetector::is_touch_moving(400);
        let is_touch = TouchDetector::is_touch_recent(500);
        let is_recent = TouchDetector::is_touch_recent(1200);

        let target_hz = if active_profile == "powersave" {
            // Powersave: Lock to Low Tier (60Hz) for maximum battery conservation
            low_hz
        } else if is_game {
            // Gaming: Lock to High Tier continuously to eliminate panel switching flicker
            high_hz
        } else if is_media {
            // Video / Streaming (YouTube, Netflix, VLC):
            // If actively touching (seeking, browsing), ramp up to High Tier;
            // Otherwise, pulldown to Low Tier (60Hz) to match video frame rate and save ~35% battery.
            if is_touch || is_moving {
                high_hz
            } else {
                low_hz
            }
        } else if is_rapid || is_moving || is_touch {
            // Active interaction / scrolling / fling gesture: Zero Latency (0 ms) High Tier
            high_hz
        } else if is_recent {
            // Decaying / gliding interaction: Mid Tier
            mid_hz
        } else {
            // Stationary reading / idle: Drop to Low Tier (60Hz) for maximum panel power savings
            low_hz
        };

        let current_hz = CURRENT_DISPLAY_HZ.load(Ordering::Relaxed);
        if target_hz != current_hz {
            let last_change = LAST_HZ_CHANGE_MS.load(Ordering::Relaxed);
            // Instant Ramp-Up (0 ms) when target > current.
            // Minimum dwell time (400ms) when stepping down to prevent visual flapping.
            if target_hz > current_hz || now_ms.saturating_sub(last_change) >= 400 {
                let sf_id = modes.sf_modes.iter().find(|(hz, _)| *hz == target_hz).map(|(_, id)| *id);
                Self::apply_hz_hardware(target_hz, sf_id);
                CURRENT_DISPLAY_HZ.store(target_hz, Ordering::Relaxed);
                LAST_HZ_CHANGE_MS.store(now_ms, Ordering::Relaxed);
                Logger::info(format!(
                    "[DISPLAY] Dynamic Hz scale: {} Hz -> {} Hz (App: {:?})",
                    current_hz,
                    target_hz,
                    fg_app.map(|a| a.package.as_str())
                ));
            }
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
        let modes = Self::get_display_modes();
        let rates_str = modes
            .supported_rates
            .iter()
            .map(|r| r.to_string())
            .collect::<Vec<_>>()
            .join(",");

        let cur_hz = CURRENT_DISPLAY_HZ.load(Ordering::Relaxed);

        format!(
            "{{\"current_hz\":{},\"min_refresh_rate\":\"{}\",\"peak_refresh_rate\":\"{}\",\"supported_rates\":[{}],\"override_active\":{}}}",
            cur_hz, min_rr, peak_rr, rates_str, has_override
        )
    }
}
