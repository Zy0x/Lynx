use std::fs;
use std::path::Path;
use std::process::Command;
use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, Ordering};
use std::sync::OnceLock;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::daemon::foreground::rules::{AppCategory, ForegroundApp};
use crate::daemon::TouchDetector;
use crate::state::ConfigStore;
use crate::sysfs::{SysfsWriter, WriteMode};
use crate::utils::Logger;

static CURRENT_DISPLAY_HZ: AtomicU32 = AtomicU32::new(0);
static LAST_HZ_CHANGE_MS: AtomicU64 = AtomicU64::new(0);
static BACKDOOR_RELEASED: AtomicBool = AtomicBool::new(false);

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

    /// Robust command execution using exact /system/bin/ absolute path fallback
    pub fn run_cmd(bin: &str, args: &[&str]) -> Result<std::process::Output, std::io::Error> {
        let direct = format!("/system/bin/{}", bin);
        let path = if Path::new(&direct).exists() {
            &direct
        } else {
            bin
        };
        Command::new(path).args(args).output()
    }

    /// Automatically probes physical display refresh rates and SurfaceFlinger mode IDs.
    /// Runs once at startup and caches the result with zero continuous shell overhead.
    pub fn probe_display_modes() -> DisplayModeTable {
        let mut supported_rates = Vec::new();
        let mut sf_modes = Vec::new();

        // 1. Try dumpsys SurfaceFlinger (Direct HWC mode indices)
        if let Ok(out) = Self::run_cmd("dumpsys", &["SurfaceFlinger"]) {
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
            if let Ok(out) = Self::run_cmd("dumpsys", &["display"]) {
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
            if let Ok(out) = Self::run_cmd("settings", &["get", "system", "min_refresh_rate"]) {
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
                    let _ = Self::run_cmd("settings", &["put", "system", "min_refresh_rate", trimmed]);
                }
            }
            let _ = fs::remove_file(orig_path);
            "Display refresh rate restored to OEM baseline.".to_string()
        } else {
            "No refresh rate override active (baseline intact).".to_string()
        }
    }

    /// Universally configures hardware composer, Android framework settings, and panel sysfs nodes.
    /// Used when a static fixed refresh rate is locked by the user.
    pub fn apply_hz_hardware(target_hz: u32, sf_id: Option<i32>) {
        // 1. Direct SurfaceFlinger HWC mode switch (sub-ms instant physical mode change)
        if let Some(id) = sf_id {
            let _ = Self::run_cmd("service", &["call", "SurfaceFlinger", "1035", "i32", &id.to_string()]);
        }

        // 2. Universal Android settings provider
        let hz_str = format!("{}.0", target_hz);
        let _ = Self::run_cmd("settings", &["put", "system", "min_refresh_rate", &hz_str]);
        let _ = Self::run_cmd("settings", &["put", "system", "peak_refresh_rate", &hz_str]);
        let _ = Self::run_cmd("settings", &["put", "secure", "user_refresh_rate", &target_hz.to_string()]);

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

    /// Smooth adaptive rate adjustment for Auto mode without hard SurfaceFlinger 1035 backdoor stalls.
    /// Manages min/peak refresh rate via DisplayModeDirector and panel sysfs.
    pub fn apply_hz_auto(target_hz: u32, high_hz: u32) {
        let hz_str = format!("{}.0", target_hz);
        let peak_str = format!("{}.0", high_hz);

        let _ = Self::run_cmd("settings", &["put", "system", "min_refresh_rate", &hz_str]);
        let _ = Self::run_cmd("settings", &["put", "system", "peak_refresh_rate", &peak_str]);
        let _ = Self::run_cmd("settings", &["put", "secure", "user_refresh_rate", &target_hz.to_string()]);

        // Kernel / Vendor Panel Sysfs Nodes
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

    /// Immediate sub-1ms hardware touch boost notification triggered by TouchDetector thread.
    /// Elevates min_refresh_rate to high_hz ceiling with zero latency on touch contact.
    pub fn on_hardware_touch() {
        let modes = Self::get_display_modes();
        if modes.supported_rates.len() <= 1 {
            return;
        }

        let lock_cfg = ConfigStore::get("display_touch.refresh_rate_lock")
            .and_then(|v| v.parse::<u32>().ok())
            .unwrap_or(0);
        if lock_cfg > 0 {
            return;
        }

        let high_hz = modes.supported_rates.last().copied().unwrap_or(60);
        let cur = CURRENT_DISPLAY_HZ.load(Ordering::Relaxed);
        if cur != high_hz {
            CURRENT_DISPLAY_HZ.store(high_hz, Ordering::Relaxed);
            let now_ms = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap_or_default()
                .as_millis() as u64;
            LAST_HZ_CHANGE_MS.store(now_ms, Ordering::Relaxed);
            let hz_str = format!("{}.0", high_hz);
            let _ = Self::run_cmd("settings", &["put", "system", "min_refresh_rate", &hz_str]);
            let _ = Self::run_cmd("settings", &["put", "system", "peak_refresh_rate", &hz_str]);
        }
    }

    /// Autonomous Universal Multi-Factor Dynamic Display Governor tick executed on every daemon cycle.
    /// Scales physical display refresh rate instantly (0 ms) on touch, drops to low-rate during media/reading,
    /// and locks to high-rate during gaming to prevent mode-switching flickers.
    pub fn tick_dynamic_display_scaler(
        fg_app: Option<&ForegroundApp>,
        active_profile: &str,
        is_screen_on: bool,
        is_multitask: bool,
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
        // 0 = Auto (Dynamic Adaptive), >0 = Explicitly locked rate (e.g. 60, 90, 120, 144)
        let lock_cfg = ConfigStore::get("display_touch.refresh_rate_lock")
            .and_then(|v| v.parse::<u32>().ok())
            .unwrap_or(0);

        if lock_cfg > 0 {
            // User explicitly chose a fixed locked rate! DO NOT AUTO-SCALE!
            let locked_rate = lock_cfg;

            BACKDOOR_RELEASED.store(false, Ordering::Relaxed);

            if CURRENT_DISPLAY_HZ.load(Ordering::Relaxed) != locked_rate {
                let sf_id = modes.sf_modes.iter().find(|(hz, _)| *hz == locked_rate).map(|(_, id)| *id);
                Self::apply_hz_hardware(locked_rate, sf_id);
                CURRENT_DISPLAY_HZ.store(locked_rate, Ordering::Relaxed);
            }
            return;
        }

        // ── Auto (Dynamic / Adaptive) Mode ─────────────────────────────────
        // In Auto Mode, release SurfaceFlinger backdoor so Android's native
        // Choreographer and DisplayModeDirector handle frame pacing smoothly without HWC stall glitches.
        if !BACKDOOR_RELEASED.load(Ordering::Relaxed) {
            let _ = Self::run_cmd("service", &["call", "SurfaceFlinger", "1035", "i32", "-1"]);
            let _ = Self::run_cmd("cmd", &["display", "clear-user-preferred-display-mode", "0"]);
            BACKDOOR_RELEASED.store(true, Ordering::Relaxed);
            Logger::info("[DISPLAY] SurfaceFlinger backdoor released for seamless dynamic frame pacing.");
        }

        let low_hz = modes.supported_rates.first().copied().unwrap_or(60);
        let high_hz = modes.supported_rates.last().copied().unwrap_or(60);

        let now_ms = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as u64;

        let is_game = active_profile == "performance"
            || active_profile == "extreme"
            || fg_app.map(|a| a.category == AppCategory::Game).unwrap_or(false);

        let is_media = fg_app.map(|a| a.category == AppCategory::Media).unwrap_or(false);

        // Kinetic fling window: 2,200ms for single app scrolling; extended to 3,000ms during multitasking
        // to prevent multi-layer SurfaceFlinger compositor stutter while switching focus between split windows.
        let kinetic_window_ms: u64 = if is_multitask { 3000 } else { 2200 };

        let is_kinetic_active = TouchDetector::is_touch_recent(kinetic_window_ms)
            || TouchDetector::is_touch_moving(kinetic_window_ms)
            || TouchDetector::is_rapid_touch(150, 2);

        let target_hz = if active_profile == "powersave" {
            // Powersave: Lock to Low Tier (60Hz) for maximum battery conservation
            low_hz
        } else if is_game {
            // Gaming: Lock to High Tier continuously to eliminate panel switching flicker
            high_hz
        } else if is_multitask && is_kinetic_active {
            // Multitasking (Split-Screen / Freeform / PiP): Keep pinned at High Tier (120Hz)
            // during active window interaction to guarantee zero compositor jank
            high_hz
        } else if is_media {
            // Video / Streaming (YouTube, Netflix, VLC, MX Player):
            // If actively touching / scrubbing timeline: High Tier (120Hz)
            // If watching video hands-off: Low Tier (60Hz) to match 24/30/60 FPS video and save ~35-40% battery
            if TouchDetector::is_touch_recent(1500) || TouchDetector::is_touch_moving(1500) {
                high_hz
            } else {
                low_hz
            }
        } else if is_kinetic_active {
            // Active touch / scrolling / fling gesture: Zero Latency (0 ms) High Tier
            high_hz
        } else {
            // Stationary reading / idle: Drop to Low Tier (60Hz) for maximum panel power savings
            low_hz
        };

        let current_hz = CURRENT_DISPLAY_HZ.load(Ordering::Relaxed);
        if target_hz != current_hz {
            let last_change = LAST_HZ_CHANGE_MS.load(Ordering::Relaxed);
            let dwell_ms: u64 = if is_multitask { 2000 } else { 1000 };
            // Instant Ramp-Up (0 ms) when target > current.
            // Dwell time (1000ms, or 2000ms in multitask) when stepping down to prevent visual flapping.
            if target_hz > current_hz || now_ms.saturating_sub(last_change) >= dwell_ms {
                Self::apply_hz_auto(target_hz, high_hz);
                CURRENT_DISPLAY_HZ.store(target_hz, Ordering::Relaxed);
                LAST_HZ_CHANGE_MS.store(now_ms, Ordering::Relaxed);
                Logger::info(format!(
                    "[DISPLAY] Dynamic Hz scale: {} Hz -> {} Hz (App: {:?}, Multitask: {})",
                    current_hz,
                    target_hz,
                    fg_app.map(|a| a.package.as_str()),
                    is_multitask
                ));
            }
        }
    }

    /// Toggles touch sampling rate boost across common OEM kernel nodes and framework settings.
    pub fn apply_touch_boost(enable: bool) -> String {
        let val = if enable { "1" } else { "0" };
        let mut count = 0usize;

        // 1. Android Framework High Polling & Sensitivity Settings
        let _ = Self::run_cmd("settings", &["put", "system", "high_touch_polling_rate_enable", val]);
        let _ = Self::run_cmd("settings", &["put", "secure", "high_touch_polling_rate_enable", val]);
        let _ = Self::run_cmd("settings", &["put", "global", "high_touch_polling_rate_enable", val]);
        let _ = Self::run_cmd("settings", &["put", "system", "high_touch_sensitivity_enable", val]);
        let _ = Self::run_cmd("settings", &["put", "secure", "high_touch_sensitivity_enable", val]);
        let _ = Self::run_cmd("settings", &["put", "system", "touch_game_mode", val]);

        // 2. MediaTek Touch Panel Driver (TPD) Low-Latency & Zero-Debounce
        let debounce_val = if enable { "0" } else { "10" };
        let tpd_nodes = [
            "/sys/module/tpd_setting/parameters/tpd_em_debounce_time",
            "/sys/module/tpd_setting/parameters/tpd_em_debounce_time0",
            "/sys/module/tpd_setting/parameters/tpd_em_debounce_time1",
        ];
        for node in tpd_nodes {
            if Self::write_opt(node, debounce_val) {
                count += 1;
            }
        }
        if Self::write_opt("/sys/module/tpd_setting/parameters/tpd_mode", val) {
            count += 1;
        }

        // 3. Multi-OEM Hardware Game Mode & Sampling Nodes
        let touch_nodes = [
            "/sys/class/touch/touch_dev/touch_game_mode",
            "/sys/devices/virtual/touch/touch_dev/bump_sample_rate",
            "/proc/touchscreen/game_mode",
            "/proc/touchpanel/game_switch_enable",
            "/sys/devices/platform/goodix_ts.0/game_mode",
            "/sys/devices/platform/tp_wake_switch/game_mode",
            "/sys/devices/platform/fts_touch/game_mode",
            "/sys/devices/platform/soc/soc:touch/game_mode",
            "/proc/ppm/policy/touch_boost",
            "/proc/perfmgr/boost_ctrl/eas_ctrl/touch_boost",
            "/sys/module/perfmgr/parameters/touch_boost",
            "/sys/module/msm_performance/parameters/touchboost",
        ];

        for node in touch_nodes {
            if Self::write_opt(node, val) {
                count += 1;
            }
        }

        // 4. Oppo/Realme/OnePlus TP limit override (write 0 to remove throttling)
        if enable {
            let _ = Self::write_opt("/proc/touchpanel/oppo_tp_limit_enable", "0");
        }

        // 5. Wildcard scan for virtual and platform input devices
        if let Ok(entries) = fs::read_dir("/sys/devices/virtual/input") {
            for entry in entries.flatten() {
                let gm_path = entry.path().join("touch_game_mode");
                if Self::write_opt(gm_path, val) {
                    count += 1;
                }
                let sr_path = entry.path().join("sample_rate");
                if enable && Self::write_opt(sr_path, "240") {
                    count += 1;
                }
            }
        }

        if let Ok(entries) = fs::read_dir("/sys/class/input") {
            for entry in entries.flatten() {
                let gm_path = entry.path().join("device").join("game_mode");
                if Self::write_opt(gm_path, val) {
                    count += 1;
                }
            }
        }

        format!(
            "Touch boost set to {} ({} kernel nodes updated, framework high-polling set to {}).",
            if enable { "ENABLED" } else { "DISABLED" },
            count,
            val
        )
    }

    /// Returns current refresh rate settings as JSON.
    pub fn info_json() -> String {
        let min_rr = Self::run_cmd("settings", &["get", "system", "min_refresh_rate"])
            .ok()
            .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
            .filter(|s| !s.is_empty() && s != "null")
            .unwrap_or_else(|| "60.0".to_string());

        let peak_rr = Self::run_cmd("settings", &["get", "system", "peak_refresh_rate"])
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
