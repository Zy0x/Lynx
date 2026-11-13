use std::fs;
use std::path::Path;

use crate::state::ConfigStore;

/// Hardware controller for MediaTek / Novatek NT36672C panel CABC (DCS 0x55),
/// Outdoor HBM readback, and ESD polling watchdog interval (/proc/mtk_esd_period_ms).
pub struct MtkPanelController;

impl MtkPanelController {
    const CABC_NODE: &'static str = "/sys/kernel/oplus_display/LCM_CABC";
    const HBM_NODE: &'static str = "/sys/kernel/oplus_display/hbm";
    const ESD_PERIOD_NODE: &'static str = "/proc/mtk_esd_period_ms";

    pub const DEFAULT_CABC_MODE: i64 = 0;
    pub const MIN_CABC_MODE: i64 = 0;
    pub const MAX_CABC_MODE: i64 = 3;

    pub const DEFAULT_ESD_PERIOD_MS: i64 = 15_000;
    pub const MIN_ACTIVE_ESD_PERIOD_MS: i64 = 2_000;
    pub const MAX_ESD_PERIOD_MS: i64 = 60_000;

    pub fn clamp_cabc_mode(raw: i64) -> i64 {
        raw.clamp(Self::MIN_CABC_MODE, Self::MAX_CABC_MODE)
    }

    pub fn clamp_esd_period_ms(raw: i64) -> i64 {
        if raw <= 0 {
            0
        } else {
            raw.clamp(Self::MIN_ACTIVE_ESD_PERIOD_MS, Self::MAX_ESD_PERIOD_MS)
        }
    }

    fn read_bool_cfg(key: &str, default: bool) -> bool {
        match ConfigStore::get(key)
            .map(|s| s.trim().trim_matches('"').to_ascii_lowercase())
            .as_deref()
        {
            Some("true") | Some("1") | Some("yes") | Some("on") => true,
            Some("false") | Some("0") | Some("no") | Some("off") => false,
            _ => default,
        }
    }

    fn read_i64_cfg(key: &str, default: i64) -> i64 {
        ConfigStore::get(key)
            .and_then(|s| s.trim().trim_matches('"').parse::<i64>().ok())
            .unwrap_or(default)
    }

    fn read_sys_i64(path: &str) -> Option<i64> {
        if !Path::new(path).exists() {
            return None;
        }
        let raw = fs::read_to_string(path).ok()?;
        let trimmed = raw.trim();
        if trimmed.is_empty() {
            return None;
        }
        trimmed.parse::<i64>().ok()
    }

    fn write_sys_i64(path: &str, value: i64) -> bool {
        if !Path::new(path).exists() {
            return false;
        }
        fs::write(path, format!("{}\n", value)).is_ok()
    }

    /// Applies saved panel CABC and ESD polling interval from config.json when present on kernel.
    pub fn apply_full() -> String {
        let cabc = Self::clamp_cabc_mode(Self::read_i64_cfg("panel.cabc_mode", Self::DEFAULT_CABC_MODE));
        let esd_ms = Self::clamp_esd_period_ms(Self::read_i64_cfg("panel.esd_period_ms", Self::DEFAULT_ESD_PERIOD_MS));

        if Self::read_sys_i64(Self::CABC_NODE).is_some() {
            let _ = Self::write_sys_i64(Self::CABC_NODE, cabc);
        }
        if Self::read_sys_i64(Self::ESD_PERIOD_NODE).is_some() {
            let _ = Self::write_sys_i64(Self::ESD_PERIOD_NODE, esd_ms);
        }
        Self::status_json()
    }

    /// Boot hook called during non-dormant initialization when user opted in to boot persistence.
    pub fn apply_boot() -> bool {
        if !Self::read_bool_cfg("panel.apply_on_boot", true) {
            return false;
        }
        let _ = Self::apply_full();
        true
    }

    /// Updates CABC mode in both config.json and panel sysfs node.
    pub fn apply_cabc(mode: i64) -> String {
        let clamped = Self::clamp_cabc_mode(mode);
        let _ = ConfigStore::set("panel.cabc_mode", &clamped.to_string(), "int");
        let _ = Self::write_sys_i64(Self::CABC_NODE, clamped);
        Self::status_json()
    }

    /// Updates ESD polling period in both config.json and procfs watchdog node.
    pub fn apply_esd_period(period_ms: i64) -> String {
        let clamped = Self::clamp_esd_period_ms(period_ms);
        let _ = ConfigStore::set("panel.esd_period_ms", &clamped.to_string(), "int");
        let _ = Self::write_sys_i64(Self::ESD_PERIOD_NODE, clamped);
        Self::status_json()
    }

    /// Restores X698 verified baseline (CABC Off, ESD 15000 ms).
    pub fn reset_defaults() -> String {
        let _ = ConfigStore::set("panel.cabc_mode", &Self::DEFAULT_CABC_MODE.to_string(), "int");
        let _ = ConfigStore::set(
            "panel.esd_period_ms",
            &Self::DEFAULT_ESD_PERIOD_MS.to_string(),
            "int",
        );
        Self::apply_full()
    }

    /// Emits unified hardware capability and live value snapshot as JSON.
    pub fn status_json() -> String {
        let active_cabc = Self::read_sys_i64(Self::CABC_NODE);
        let active_esd = Self::read_sys_i64(Self::ESD_PERIOD_NODE);
        let active_hbm = Self::read_sys_i64(Self::HBM_NODE);

        let cfg_cabc = Self::clamp_cabc_mode(Self::read_i64_cfg("panel.cabc_mode", Self::DEFAULT_CABC_MODE));
        let cfg_esd = Self::clamp_esd_period_ms(Self::read_i64_cfg("panel.esd_period_ms", Self::DEFAULT_ESD_PERIOD_MS));
        let apply_on_boot = Self::read_bool_cfg("panel.apply_on_boot", true);

        format!(
            "{{\"cabc_supported\":{},\"esd_supported\":{},\"hbm_supported\":{},\"active_cabc\":{},\"active_esd_ms\":{},\"active_hbm\":{},\"cfg_cabc\":{},\"cfg_esd_ms\":{},\"apply_on_boot\":{}}}",
            active_cabc.is_some(),
            active_esd.is_some(),
            active_hbm.is_some(),
            active_cabc.unwrap_or(-1),
            active_esd.unwrap_or(-1),
            active_hbm.unwrap_or(-1),
            cfg_cabc,
            cfg_esd,
            apply_on_boot,
        )
    }
}

#[cfg(test)]
mod tests {
    use super::MtkPanelController;

    #[test]
    fn clamps_cabc_mode_to_valid_range() {
        assert_eq!(MtkPanelController::clamp_cabc_mode(-4), 0);
        assert_eq!(MtkPanelController::clamp_cabc_mode(0), 0);
        assert_eq!(MtkPanelController::clamp_cabc_mode(2), 2);
        assert_eq!(MtkPanelController::clamp_cabc_mode(9), 3);
    }

    #[test]
    fn clamps_esd_period_preserves_pause_and_bounds() {
        assert_eq!(MtkPanelController::clamp_esd_period_ms(0), 0);
        assert_eq!(MtkPanelController::clamp_esd_period_ms(-500), 0);
        assert_eq!(MtkPanelController::clamp_esd_period_ms(500), 2000);
        assert_eq!(MtkPanelController::clamp_esd_period_ms(15000), 15000);
        assert_eq!(MtkPanelController::clamp_esd_period_ms(999_999), 60000);
    }

    #[test]
    fn status_json_contains_expected_keys() {
        let json = MtkPanelController::status_json();
        assert!(json.contains("\"cabc_supported\""));
        assert!(json.contains("\"esd_supported\""));
        assert!(json.contains("\"hbm_supported\""));
        assert!(json.contains("\"active_esd_ms\""));
        assert!(json.contains("\"cfg_cabc\""));
        assert!(json.contains("\"cfg_esd_ms\""));
    }
}
