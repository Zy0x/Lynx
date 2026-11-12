use std::fs;
use std::path::Path;
use std::process::Command;

use crate::state::ConfigStore;

pub struct TouchPrecisionController;

impl TouchPrecisionController {
    const NVT_MICRO_GAIN_NODE: &'static str = "/proc/nvt_micro_gain";
    const NVT_EDGE_GUARD_NODE: &'static str = "/proc/nvt_edge_guard";
    const NVT_SPI_SPEED_NODE: &'static str = "/proc/nvt_spi_speed";
    const NVT_GAME_MODE_NODE: &'static str = "/proc/nvt_game_mode";
    const NVT_TOUCH_CMD_NODE: &'static str = "/proc/nvt_touch_cmd";
    const NVT_TOUCH_STATS_NODE: &'static str = "/proc/nvt_touch_stats";
    const KTCH_ENABLE_NODE: &'static str = "/proc/perfmgr/tchbst/kernel/tb_enable";
    const KTCH_DUR_NODE: &'static str = "/proc/perfmgr/tchbst/kernel/tb_duration_ms";
    const KTCH_EAS_NODE: &'static str = "/proc/perfmgr/tchbst/kernel/tb_eas_boost";

    fn read_str(key: &str, default: &str) -> String {
        ConfigStore::get(key)
            .map(|s| s.trim().trim_matches('"').to_string())
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| default.to_string())
    }

    fn read_bool(key: &str, default: bool) -> bool {
        match ConfigStore::get(key)
            .map(|s| s.trim().trim_matches('"').to_ascii_lowercase())
            .as_deref()
        {
            Some("true") | Some("1") | Some("yes") | Some("on") => true,
            Some("false") | Some("0") | Some("no") | Some("off") => false,
            _ => default,
        }
    }

    fn read_i64(key: &str, default: i64, min_v: i64, max_v: i64) -> i64 {
        ConfigStore::get(key)
            .and_then(|s| s.trim().trim_matches('"').parse::<i64>().ok())
            .unwrap_or(default)
            .clamp(min_v, max_v)
    }

    fn read_f64(key: &str, default: f64, min_v: f64, max_v: f64) -> f64 {
        ConfigStore::get(key)
            .and_then(|s| s.trim().trim_matches('"').parse::<f64>().ok())
            .filter(|v| v.is_finite())
            .unwrap_or(default)
            .clamp(min_v, max_v)
    }

    fn write_node_if_exists(path: &str, val: &str) -> bool {
        if !Path::new(path).exists() {
            return false;
        }
        fs::write(path, val).is_ok()
    }

    fn set_prop_reset_first(key: &str, val: &str) {
        let resetprop_candidates = [
            "/data/adb/ksu/bin/resetprop",
            "/data/adb/magisk/resetprop",
            "/data/adb/ap/bin/resetprop",
            "resetprop",
        ];
        for bin in resetprop_candidates {
            if !bin.contains('/') || Path::new(bin).exists() {
                if let Ok(st) = Command::new(bin).args(&[key, val]).status() {
                    if st.success() {
                        return;
                    }
                }
            }
        }
        let _ = Command::new("/system/bin/setprop").args(&[key, val]).status();
    }

    pub fn configured_touch_slop() -> i64 {
        Self::read_i64("display_touch.view_touch_slop", 4, 1, 16)
    }

    pub fn configured_input_resampling() -> bool {
        Self::read_bool("display_touch.input_resampling", false)
    }

    pub fn configured_freq_boost() -> bool {
        Self::read_bool("display_touch.debug_touch_freq_boost", true)
    }

    /// Applies kernel Novatek NT36672C, MTK KTCH, and touch IRQ affinity parameters from config.json.
    pub fn apply_kernel_hardware() -> usize {
        let mut applied = 0usize;

        let micro_gain = Self::read_i64("display_touch.nvt_micro_gain", 4, 0, 12);
        let edge_guard = Self::read_i64("display_touch.nvt_edge_guard_px", 48, 0, 240);
        let spi_hz = Self::read_i64("display_touch.nvt_spi_speed_hz", 15_000_000, 4_800_000, 24_000_000);
        let game_mode = Self::read_bool("display_touch.nvt_game_mode", true);
        let hop_fix = Self::read_bool("display_touch.nvt_hop_fix", true);
        let jitter_lvl = Self::read_i64("display_touch.nvt_jitter_level", 0, 0, 3);
        let smooth_lvl = Self::read_i64("display_touch.nvt_smooth_level", 0, 0, 3);

        if Self::write_node_if_exists(Self::NVT_GAME_MODE_NODE, if game_mode { "1" } else { "0" }) {
            applied += 1;
        }
        if Self::write_node_if_exists(Self::NVT_SPI_SPEED_NODE, &spi_hz.to_string()) {
            applied += 1;
        }
        if Self::write_node_if_exists(Self::NVT_MICRO_GAIN_NODE, &micro_gain.to_string()) {
            applied += 1;
        }
        if Self::write_node_if_exists(Self::NVT_EDGE_GUARD_NODE, &edge_guard.to_string()) {
            applied += 1;
        }
        if Path::new(Self::NVT_TOUCH_CMD_NODE).exists() {
            // Best-effort host commands: 0x75/0x76=hop-fix, 0x7F 0x0C <lv>=jitter, 0x7F 0x0D <lv>=smooth
            if Self::write_node_if_exists(
                Self::NVT_TOUCH_CMD_NODE,
                if hop_fix { "0x75" } else { "0x76" },
            ) {
                applied += 1;
            }
            if Self::write_node_if_exists(
                Self::NVT_TOUCH_CMD_NODE,
                &format!("0x7F 0x0C 0x{:02X}", jitter_lvl),
            ) {
                applied += 1;
            }
            if Self::write_node_if_exists(
                Self::NVT_TOUCH_CMD_NODE,
                &format!("0x7F 0x0D 0x{:02X}", smooth_lvl),
            ) {
                applied += 1;
            }
        }

        let ktch_en = Self::read_bool("display_touch.ktch_enabled", true);
        let ktch_dur = Self::read_i64("display_touch.ktch_duration_ms", 280, 0, 2000);
        let ktch_eas = Self::read_i64("display_touch.ktch_eas_boost", 80, 0, 100);
        if Self::write_node_if_exists(Self::KTCH_ENABLE_NODE, if ktch_en { "1" } else { "0" }) {
            applied += 1;
        }
        if Self::write_node_if_exists(Self::KTCH_DUR_NODE, &ktch_dur.to_string()) {
            applied += 1;
        }
        if Self::write_node_if_exists(Self::KTCH_EAS_NODE, &ktch_eas.to_string()) {
            applied += 1;
        }

        if Self::read_bool("display_touch.touch_irq_big_cores", true) {
            if let Ok(interrupts) = fs::read_to_string("/proc/interrupts") {
                for line in interrupts.lines() {
                    let lower = line.to_ascii_lowercase();
                    if lower.contains("novatek") || lower.contains("11013000.spi3") {
                        if let Some((irq_num, _)) = line.split_once(':') {
                            let irq = irq_num.trim();
                            let affinity_node = format!("/proc/irq/{}/smp_affinity_list", irq);
                            if Self::write_node_if_exists(&affinity_node, "6-7") {
                                applied += 1;
                            }
                        }
                    }
                }
            }
        }

        applied
    }

    /// Applies Android input pipeline props while respecting user saved configuration.
    pub fn apply_input_props() {
        let slop = Self::configured_touch_slop().to_string();
        let resamp = if Self::configured_input_resampling() { "true" } else { "false" };
        let freq_boost = if Self::configured_freq_boost() { "1" } else { "0" };

        Self::set_prop_reset_first("view.touch_slop", &slop);
        Self::set_prop_reset_first("ro.input.resampling", resamp);
        Self::set_prop_reset_first("debug.touch.frequency.boost", freq_boost);
    }

    /// Applies Edge Back Gesture overlays, left/right inset scales, and ML confidence threshold.
    pub fn apply_edge_gesture() {
        let overlay = Self::read_str("display_touch.back_gesture_overlay", "narrow");
        let scale_l = format!("{:.2}", Self::read_f64("display_touch.back_gesture_scale_left", 0.18, 0.0, 2.0));
        let scale_r = format!("{:.2}", Self::read_f64("display_touch.back_gesture_scale_right", 0.18, 0.0, 2.0));
        let ml_enabled = Self::read_bool("display_touch.back_gesture_ml_enabled", true);
        let ml_thresh = Self::read_f64("display_touch.back_gesture_ml_threshold", 0.96, 0.50, 0.99);
        let ml_thresh_str = format!("{:.2}", ml_thresh);

        const OVERLAYS: &[(&str, &str)] = &[
            ("narrow", "com.android.internal.systemui.navbar.gestural_narrow_back"),
            ("wide", "com.android.internal.systemui.navbar.gestural_wide_back"),
            ("extra_wide", "com.android.internal.systemui.navbar.gestural_extra_wide_back"),
        ];
        for (tier, pkg) in OVERLAYS {
            let action = if *tier == overlay { "enable" } else { "disable" };
            let _ = Command::new("/system/bin/cmd").args(&["overlay", action, pkg]).status();
        }

        let _ = Command::new("/system/bin/settings")
            .args(&["put", "secure", "back_gesture_inset_scale_left", &scale_l])
            .status();
        let _ = Command::new("/system/bin/settings")
            .args(&["put", "secure", "back_gesture_inset_scale_right", &scale_r])
            .status();

        // Force SystemUI EdgeBackGestureHandler to reload threshold via a transient sentinel value
        let tmp_thresh = if (ml_thresh - 0.95).abs() > 0.005 { "0.951" } else { "0.961" };
        let _ = Command::new("/system/bin/device_config")
            .args(&["put", "systemui", "use_back_gesture_ml_model", "false"])
            .status();
        let _ = Command::new("/system/bin/device_config")
            .args(&["put", "systemui", "back_gesture_ml_model_threshold", tmp_thresh])
            .status();
        std::thread::sleep(std::time::Duration::from_millis(250));
        let _ = Command::new("/system/bin/device_config")
            .args(&["put", "systemui", "back_gesture_ml_model_threshold", &ml_thresh_str])
            .status();
        let _ = Command::new("/system/bin/device_config")
            .args(&[
                "put",
                "systemui",
                "use_back_gesture_ml_model",
                if ml_enabled { "true" } else { "false" },
            ])
            .status();
    }

    pub fn apply_full() -> String {
        let count = Self::apply_kernel_hardware();
        Self::apply_input_props();
        Self::apply_edge_gesture();
        format!(
            "Touch Precision & Edge Guard applied ({} kernel nodes, slop={}, resampling={}, gain={}).",
            count,
            Self::configured_touch_slop(),
            Self::configured_input_resampling(),
            Self::read_i64("display_touch.nvt_micro_gain", 4, 0, 12)
        )
    }

    pub fn status_json() -> String {
        let nvt_present = Path::new(Self::NVT_MICRO_GAIN_NODE).exists();
        let ktch_present = Path::new(Self::KTCH_ENABLE_NODE).exists();
        let gain = fs::read_to_string(Self::NVT_MICRO_GAIN_NODE)
            .ok()
            .and_then(|s| s.trim().parse::<i64>().ok())
            .unwrap_or_else(|| Self::read_i64("display_touch.nvt_micro_gain", 4, 0, 12));
        let edge_guard = fs::read_to_string(Self::NVT_EDGE_GUARD_NODE)
            .ok()
            .and_then(|s| s.trim().parse::<i64>().ok())
            .unwrap_or_else(|| Self::read_i64("display_touch.nvt_edge_guard_px", 48, 0, 240));
        let stats = fs::read_to_string(Self::NVT_TOUCH_STATS_NODE).unwrap_or_default();
        let ktch_dur = fs::read_to_string(Self::KTCH_DUR_NODE)
            .ok()
            .and_then(|s| s.trim().parse::<i64>().ok())
            .unwrap_or(280);
        let ktch_eas = fs::read_to_string(Self::KTCH_EAS_NODE)
            .ok()
            .and_then(|s| s.trim().parse::<i64>().ok())
            .unwrap_or(80);
        let stats_escaped = stats.replace('\n', " ").replace('"', "");

        format!(
            "{{\"nvt_present\":{},\"ktch_present\":{},\"micro_gain\":{},\"edge_guard_px\":{},\"ktch_duration_ms\":{},\"ktch_eas_boost\":{},\"slop\":{},\"resampling\":{},\"stats\":\"{}\"}}",
            nvt_present,
            ktch_present,
            gain,
            edge_guard,
            ktch_dur,
            ktch_eas,
            Self::configured_touch_slop(),
            Self::configured_input_resampling(),
            stats_escaped.trim()
        )
    }
}
