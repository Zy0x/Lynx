use std::path::Path;
use std::process::Command;
use std::thread::sleep;
use std::time::Duration;

use crate::state::ConfigStore;

pub struct MtkPqController;

impl MtkPqController {
    fn getprop(key: &str) -> String {
        Command::new("getprop")
            .arg(key)
            .output()
            .ok()
            .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
            .unwrap_or_default()
    }

    fn set_prop(key: &str, val: &str) {
        if let Ok(st) = Command::new("setprop").args(&[key, val]).status() {
            if st.success() {
                return;
            }
        }
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
    }

    fn parse_bool(raw: Option<String>, default: bool) -> bool {
        match raw.map(|s| s.trim().trim_matches('"').to_ascii_lowercase()).as_deref() {
            Some("true") | Some("1") | Some("yes") | Some("on") => true,
            Some("false") | Some("0") | Some("no") | Some("off") => false,
            _ => default,
        }
    }

    fn read_bool(key: &str, default: bool) -> bool {
        Self::parse_bool(ConfigStore::get(key), default)
    }

    fn read_i64(key: &str, default: i64, min_v: i64, max_v: i64) -> i64 {
        ConfigStore::get(key)
            .and_then(|s| s.trim().trim_matches('"').parse::<i64>().ok())
            .unwrap_or(default)
            .clamp(min_v, max_v)
    }

    pub fn detect_pq_service() -> Option<String> {
        const CANDIDATES: &[&str] = &["pq-2-2", "vendor.pq-2-2", "pq-2-1", "vendor.pq-2-1", "pq-2-0"];
        for svc in CANDIDATES {
            let status = Self::getprop(&format!("init.svc.{}", svc));
            if !status.is_empty() {
                return Some((*svc).to_string());
            }
        }
        None
    }

    pub fn is_supported() -> bool {
        let pq_lvl = Self::getprop("ro.vendor.mtk_pq_support")
            .parse::<i64>()
            .unwrap_or(0);
        let aal_sup = Self::getprop("ro.vendor.mtk_aal_support") == "1";
        pq_lvl > 0 || aal_sup || Self::detect_pq_service().is_some()
    }

    fn reload_service(svc: &str) -> bool {
        let _ = Command::new("stop").arg(svc).status();
        sleep(Duration::from_millis(200));
        let _ = Command::new("start").arg(svc).status();
        for _ in 0..5 {
            if Self::getprop(&format!("init.svc.{}", svc)) == "running" {
                return true;
            }
            sleep(Duration::from_millis(150));
        }
        false
    }

    pub fn apply_full() -> String {
        if !Self::is_supported() {
            return "{\"supported\":false,\"status\":\"skipped_non_mtk\"}".to_string();
        }

        let aal_bypass = Self::read_bool("mtk_pq.aal_bypass", false);
        let dre_mode = Self::read_i64("mtk_pq.dre_mode", 2, 0, 2);
        let vp_dre = Self::read_bool("mtk_pq.vp_dre_enabled", true);
        let shp_idx = Self::read_i64("mtk_pq.shp_index", 2, 0, 9);
        let iso_shp = Self::read_bool("mtk_pq.iso_shp_enabled", true);
        let ultrares_en = Self::read_bool("mtk_pq.ultrares_enabled", true);
        let ultrares_str = Self::read_i64("mtk_pq.ultrares_strength", 0, 0, 4);
        let dbg_shp = Self::read_bool("mtk_pq.debug_shp_enabled", true);
        let dbg_dshp = Self::read_bool("mtk_pq.debug_dshp_enabled", true);

        Self::set_prop(
            "persist.vendor.sys.pq.disp.aal.bypass",
            if aal_bypass { "1" } else { "0" },
        );
        Self::set_prop("persist.vendor.sys.pq.mdp.dre.en", &dre_mode.to_string());
        Self::set_prop(
            "persist.vendor.sys.pq.mdp.vp.dre.en",
            if vp_dre { "1" } else { "0" },
        );
        Self::set_prop("persist.vendor.sys.pq.shp.idx", &shp_idx.to_string());
        Self::set_prop(
            "persist.vendor.sys.pq.iso.shp.en",
            if iso_shp { "1" } else { "0" },
        );
        Self::set_prop(
            "persist.vendor.sys.pq.ultrares.en",
            if ultrares_en { "1" } else { "0" },
        );
        Self::set_prop(
            "persist.vendor.sys.pq.ultrares.strength",
            &ultrares_str.to_string(),
        );
        Self::set_prop("vendor.debug.pq.shp.en", if dbg_shp { "1" } else { "0" });
        Self::set_prop("vendor.debug.pq.dshp.en", if dbg_dshp { "1" } else { "0" });

        let reloaded = Self::detect_pq_service()
            .map(|svc| Self::reload_service(&svc))
            .unwrap_or(false);

        format!(
            "{{\"reloaded\":{},\"state\":{}}}",
            reloaded,
            Self::status_json()
        )
    }

    pub fn reset_defaults() -> String {
        let _ = ConfigStore::set("mtk_pq.aal_bypass", "false", "bool");
        let _ = ConfigStore::set("mtk_pq.dre_mode", "2", "int");
        let _ = ConfigStore::set("mtk_pq.vp_dre_enabled", "true", "bool");
        let _ = ConfigStore::set("mtk_pq.shp_index", "2", "int");
        let _ = ConfigStore::set("mtk_pq.iso_shp_enabled", "true", "bool");
        let _ = ConfigStore::set("mtk_pq.ultrares_enabled", "true", "bool");
        let _ = ConfigStore::set("mtk_pq.ultrares_strength", "0", "int");
        let _ = ConfigStore::set("mtk_pq.debug_shp_enabled", "true", "bool");
        let _ = ConfigStore::set("mtk_pq.debug_dshp_enabled", "true", "bool");
        Self::apply_full()
    }

    pub fn status_json() -> String {
        let pq_lvl = Self::getprop("ro.vendor.mtk_pq_support")
            .parse::<i64>()
            .unwrap_or(0);
        let aal_sup = Self::getprop("ro.vendor.mtk_aal_support") == "1";
        let svc = Self::detect_pq_service().unwrap_or_default();
        let supported = pq_lvl > 0 || aal_sup || !svc.is_empty();

        let aal_byp = Self::getprop("persist.vendor.sys.pq.disp.aal.bypass") == "1";
        let dre_mode = Self::getprop("persist.vendor.sys.pq.mdp.dre.en")
            .parse::<i64>()
            .unwrap_or(2)
            .clamp(0, 2);
        let vp_dre = Self::getprop("persist.vendor.sys.pq.mdp.vp.dre.en") != "0";
        let shp_idx = Self::getprop("persist.vendor.sys.pq.shp.idx")
            .parse::<i64>()
            .unwrap_or(2)
            .clamp(0, 9);
        let iso_shp = Self::getprop("persist.vendor.sys.pq.iso.shp.en") != "0";
        let ur_en = Self::getprop("persist.vendor.sys.pq.ultrares.en") != "0";
        let ur_str = Self::getprop("persist.vendor.sys.pq.ultrares.strength")
            .parse::<i64>()
            .unwrap_or(0)
            .clamp(0, 4);
        let dbg_shp = Self::getprop("vendor.debug.pq.shp.en") != "0";
        let dbg_dshp = Self::getprop("vendor.debug.pq.dshp.en") != "0";

        format!(
            "{{\"supported\":{},\"mtk_pq_support\":{},\"mtk_aal_support\":{},\"pq_service\":\"{}\",\"aal_bypass\":{},\"dre_mode\":{},\"vp_dre_enabled\":{},\"shp_index\":{},\"iso_shp_enabled\":{},\"ultrares_enabled\":{},\"ultrares_strength\":{},\"debug_shp_enabled\":{},\"debug_dshp_enabled\":{}}}",
            supported, pq_lvl, aal_sup, svc, aal_byp, dre_mode, vp_dre, shp_idx, iso_shp, ur_en, ur_str, dbg_shp, dbg_dshp
        )
    }
}

#[cfg(test)]
mod tests {
    use super::MtkPqController;

    #[test]
    fn parse_bool_handles_variants() {
        assert!(MtkPqController::parse_bool(Some("true".to_string()), false));
        assert!(MtkPqController::parse_bool(Some("1".to_string()), false));
        assert!(!MtkPqController::parse_bool(Some("0".to_string()), true));
        assert!(!MtkPqController::parse_bool(Some("false".to_string()), true));
        assert!(MtkPqController::parse_bool(None, true));
    }
}
