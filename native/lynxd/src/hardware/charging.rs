use std::fs;
use std::path::Path;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::state::ConfigStore;

#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;

#[cfg(unix)]
extern "C" {
    fn kill(pid: i32, sig: i32) -> i32;
}

const PS_DIR: &str = "/sys/class/power_supply";
const BATT_DIR: &str = "/sys/class/power_supply/battery";
const USB_DIR: &str = "/sys/class/power_supply/usb";
const MAIN_DIR: &str = "/sys/class/power_supply/main";
const EXT_DIR: &str = "/sys/class/power_supply/battery_ext";
const QC_DIR: &str = "/sys/class/qcom-battery";
const MTK_DIR: &str = "/sys/devices/platform/charger";

pub struct ChargingRegulator;

impl ChargingRegulator {
    #[inline]
    fn set_perm(path: &str, mode: u32) {
        #[cfg(unix)]
        {
            if let Ok(meta) = fs::metadata(path) {
                let mut perms = meta.permissions();
                perms.set_mode(mode);
                let _ = fs::set_permissions(path, perms);
            }
        }
        #[cfg(not(unix))]
        {
            let _ = (path, mode);
        }
    }

    #[inline]
    pub fn read_node(path: &str) -> Option<String> {
        fs::read_to_string(path).ok().map(|s| s.trim().to_string()).filter(|s| !s.is_empty())
    }

    #[inline]
    pub fn read_i64(path: &str) -> Option<i64> {
        Self::read_node(path)?.parse::<i64>().ok()
    }

    #[inline]
    pub fn write_node(path: &str, val: &str) {
        if !Path::new(path).exists() {
            return;
        }
        if fs::write(path, val).is_ok() {
            return;
        }
        Self::set_perm(path, 0o666);
        let _ = fs::write(path, val);
    }

    #[inline]
    pub fn write_node_lock(path: &str, val: &str) {
        if !Path::new(path).exists() {
            return;
        }
        Self::set_perm(path, 0o666);
        let _ = fs::write(path, val);
        Self::set_perm(path, 0o444);
    }

    /// Sends SIGSTOP (19) or SIGCONT (18) to matching processes by scanning `/proc` in pure Rust
    /// without spawning `pgrep` or `killall`.
    pub fn signal_processes(patterns: &[&str], stop: bool) {
        #[cfg(unix)]
        {
            let sig = if stop { 19 } else { 18 }; // SIGSTOP = 19, SIGCONT = 18
            if let Ok(entries) = fs::read_dir("/proc") {
                for entry in entries.flatten() {
                    let fname = entry.file_name();
                    let pid_str = fname.to_string_lossy();
                    if let Ok(pid) = pid_str.parse::<i32>() {
                        if pid <= 1 {
                            continue;
                        }
                        let cmdline_path = format!("/proc/{}/cmdline", pid);
                        if let Ok(bytes) = fs::read(&cmdline_path) {
                            let cmd = String::from_utf8_lossy(&bytes).to_lowercase();
                            if patterns.iter().any(|p| cmd.contains(p)) {
                                unsafe {
                                    let _ = kill(pid, sig);
                                }
                            }
                        }
                    }
                }
            }
        }
        #[cfg(not(unix))]
        {
            let _ = (patterns, stop);
        }
    }

    /// Dynamically probes and caches the PMIC's hardware current ceiling (in mA) before override.
    pub fn probe_hardware_ceiling_ma() -> u32 {
        let cache_file = "/dev/lynx_orig_chg_max";
        if let Some(cached) = Self::read_i64(cache_file) {
            if (2000..=10000).contains(&cached) {
                return cached as u32;
            }
        }
        let mut max_ma = 0u32;
        for node in &[
            "/sys/class/power_supply/battery/constant_charge_current_max",
            "/sys/class/power_supply/main/constant_charge_current_max",
            "/sys/class/power_supply/usb/hw_current_max",
            "/sys/devices/platform/charger/chg1_current",
        ] {
            if let Some(val) = Self::read_i64(node) {
                let ma = if val > 10_000 { (val / 1000) as u32 } else { val as u32 };
                if ma > max_ma && ma <= 10_000 {
                    max_ma = ma;
                }
            }
        }
        let resolved = if max_ma >= 3000 { max_ma } else { 6000 };
        let _ = fs::write(cache_file, resolved.to_string());
        resolved
    }

    pub fn detect_hw_bypass_node() -> Option<&'static str> {
        const CANDIDATES: &[&str] = &[
            "/sys/devices/platform/charger/bypass_charger",
            "/sys/class/power_supply/battery/device/smart_charging",
            "/sys/class/power_supply/battery/charging_limit_mode",
            "/sys/class/power_supply/battery/smart_charging_activation",
            "/sys/class/qcom-battery/direct_charging",
            "/sys/class/power_supply/battery/store_mode",
            "/sys/class/power_supply/battery/batt_slate_mode",
            "/sys/class/power_supply/battery/charge_stop_level",
        ];
        for &c in CANDIDATES {
            if Path::new(c).exists() {
                return Some(c);
            }
        }
        None
    }

    pub fn unlock_extreme_nodes() {
        const NODES: &[&str] = &[
            "/sys/class/power_supply/battery/constant_charge_current_max",
            "/sys/class/power_supply/battery/constant_charge_current",
            "/sys/class/power_supply/battery/current_max",
            "/sys/class/power_supply/battery/input_current_limit",
            "/sys/class/power_supply/main/constant_charge_current_max",
            "/sys/class/power_supply/main/current_max",
            "/sys/class/power_supply/usb/current_max",
            "/sys/class/power_supply/usb/hw_current_max",
            "/sys/class/power_supply/battery/charge_control_limit_max",
            "/sys/class/power_supply/battery/charge_control_limit",
            "/sys/class/power_supply/battery/input_suspend",
            "/sys/class/power_supply/battery/fastcharge_mode",
            "/sys/class/power_supply/battery/fast_charge",
            "/sys/class/power_supply/battery/charging_enabled",
            "/sys/devices/platform/charger/BN_TestMode",
            "/sys/devices/platform/charger/BatteryNotify",
            "/sys/devices/platform/charger/sw_jeita",
            "/sys/devices/platform/charger/tran_charger_full",
            "/sys/devices/platform/charger/bypass_charger",
            "/sys/devices/platform/charger/tran_game_mode",
            "/sys/devices/platform/charger/input_current",
            "/sys/devices/platform/charger/chg1_current",
            "/sys/devices/platform/charger/chg2_current",
            "/sys/devices/platform/charger/sc_ibat_limit",
            "/sys/devices/platform/charger/pe40",
            "/sys/devices/platform/charger/pe20",
            "/sys/devices/platform/charger/pdc_max_watt",
            "/sys/devices/platform/charger/enable_sc",
            "/sys/class/qcom-battery/direct_charging",
            "/sys/class/qcom-battery/restricted_charging",
            "/sys/class/qcom-battery/restrict_cur",
            "/sys/class/power_supply/battery/system_temp_level",
            "/sys/class/power_supply/battery/temp_state",
            "/sys/class/power_supply/battery/thermal_input_current_limit",
            "/sys/class/power_supply/battery/input_current_settled",
            "/sys/class/power_supply/battery/boost_current",
            "/sys/class/power_supply/battery/step_charging_enabled",
            "/sys/class/power_supply/battery/quick_charge_type",
            "/sys/class/power_supply/battery/siop_level",
            "/sys/class/power_supply/battery/store_mode",
            "/sys/class/power_supply/battery/batt_slate_mode",
            "/sys/class/power_supply/battery/wc_control",
            "/sys/class/power_supply/battery/afc_result",
            "/sys/class/power_supply/battery/direct_charger_mode",
            "/sys/class/power_supply/battery/hv_charger_status",
            "/sys/class/power_supply/battery/cool_mode",
            "/sys/class/power_supply/battery/call_mode",
            "/sys/class/power_supply/battery/vooc_charging",
            "/sys/class/power_supply/battery/fast_charge_user_type",
            "/sys/class/power_supply/battery/authenticate",
            "/sys/class/power_supply/battery/charge_stop_level",
            "/sys/devices/platform/google,battery/charge_stop_level",
            "/sys/devices/platform/google,charger/charge_stop_level",
            "/sys/class/power_supply/battery/bd_trickle_dry_run",
            "/sys/class/power_supply/battery/device/smart_charging",
            "/sys/class/power_supply/battery/charging_limit_mode",
            "/sys/class/power_supply/battery/mmi_charging_enable",
            "/sys/class/power_supply/battery/factory_mode",
            "/proc/driver/thermal/clabcct",
            "/proc/driver/thermal/clabcct_lcmoff",
            "/proc/driver/thermal/tzbts_param",
            "/sys/devices/platform/pca_dv2_algo/dv2_debug",
            "/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug",
            "/sys/devices/platform/tran_battery/pcb_thermal_debug",
        ];

        for &node in NODES {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
            }
        }

        if let Ok(entries) = fs::read_dir("/sys/class/thermal") {
            for entry in entries.flatten() {
                let name = entry.file_name();
                let name_str = name.to_string_lossy();
                if name_str.starts_with("cooling_device") {
                    let cur = format!("/sys/class/thermal/{}/cur_state", name_str);
                    if Path::new(&cur).exists() {
                        Self::set_perm(&cur, 0o666);
                    }
                } else if name_str.starts_with("thermal_zone") {
                    let mode_path = format!("/sys/class/thermal/{}/mode", name_str);
                    if Path::new(&mode_path).exists() {
                        Self::set_perm(&mode_path, 0o666);
                        let _ = fs::write(&mode_path, "enabled");
                    }
                }
            }
        }

        if Path::new("/sys/devices/platform/pca_dv2_algo/dv2_debug").exists() {
            Self::write_node("/sys/devices/platform/pca_dv2_algo/dv2_debug", "[101,43,45,50,500,1000,1500]");
            Self::write_node("/sys/devices/platform/pca_dv2_algo/dv2_debug", "[102,65,68,72,500,1000,1500]");
        }
        if Path::new("/proc/driver/thermal/tzbts_param").exists() {
            Self::write_node(
                "/proc/driver/thermal/tzbts_param",
                "PUP_R 100000 PUP_VOLT 1800 OVER_CRITICAL_L 4397119 NTC_TABLE 7 0",
            );
        }

        Self::write_node("/sys/devices/platform/charger/BN_TestMode", "0");
        Self::write_node("/sys/devices/platform/charger/BatteryNotify", "1");
        Self::write_node("/sys/devices/platform/charger/sw_jeita", "1");
        Self::signal_processes(&["thermalloadalgod", "com.xiaomi.joyose", "mi_thermald", "thermal-engine"], false);
    }

    /// Mode 1: True Hardware Bypass Charging (Zero Battery Current, Vsys powered by Adapter)
    pub fn apply_bypass_charging() {
        let _ = fs::remove_file("/dev/lynx_extreme_charging");
        let _ = fs::write("/dev/lynx_bypass_active", "1");
        Self::unlock_extreme_nodes();

        // 1. MediaTek Platform Hardware Zero Cutoff (Vsys powered, battery decoupled)
        Self::write_node_lock(&format!("{}/input_current", MTK_DIR), "0");
        Self::write_node_lock(&format!("{}/chg1_current", MTK_DIR), "0");
        Self::write_node_lock(&format!("{}/chg2_current", MTK_DIR), "0");
        Self::write_node_lock(&format!("{}/sc_ibat_limit", MTK_DIR), "0");
        Self::write_node_lock(&format!("{}/enable_sc", MTK_DIR), "0");
        Self::write_node_lock(&format!("{}/tran_charger_full", MTK_DIR), "1");
        Self::write_node("/sys/devices/platform/charger/bypass_charger", "1");

        // MediaTek Kernel PID Thermal Derater (ABCCT) Zero Current Enforcement
        let abcct_zero = "30000 1000 200000 5 0 0 0 0";
        Self::write_node_lock("/proc/driver/thermal/clabcct", abcct_zero);
        Self::write_node_lock("/proc/driver/thermal/clabcct_lcmoff", abcct_zero);

        // Transsion 33W+ Direct Pump Suspend
        let pcb_zero = "[30,0,35,0,0]";
        Self::write_node_lock("/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug", pcb_zero);
        Self::write_node_lock("/sys/devices/platform/tran_battery/pcb_thermal_debug", pcb_zero);
        if Path::new("/sys/devices/platform/pca_dv2_algo/dv2_debug").exists() {
            Self::set_perm("/sys/devices/platform/pca_dv2_algo/dv2_debug", 0o666);
            let _ = fs::write("/sys/devices/platform/pca_dv2_algo/dv2_debug", "[101,10,20,30,6000,6000,6000]");
            let _ = fs::write("/sys/devices/platform/pca_dv2_algo/dv2_debug", "[102,10,20,30,6000,6000,6000]");
            Self::set_perm("/sys/devices/platform/pca_dv2_algo/dv2_debug", 0o444);
        }

        // 2. Universal Linux & Android Charge Rails Zero Cutoff
        for node in [
            format!("{}/constant_charge_current", BATT_DIR),
            format!("{}/constant_charge_current_max", BATT_DIR),
            format!("{}/current_max", BATT_DIR),
            format!("{}/input_current_limit", BATT_DIR),
        ] {
            Self::write_node_lock(&node, "0");
        }

        Self::write_node_lock(&format!("{}/charge_control_limit_max", BATT_DIR), "1");
        Self::write_node_lock(&format!("{}/charge_control_limit", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/charging_enabled", BATT_DIR), "0");

        // Universal Android Store/Demo Mode
        Self::write_node(&format!("{}/device/smart_charging", BATT_DIR), "1");
        Self::write_node(&format!("{}/charging_limit_mode", BATT_DIR), "1");
        Self::write_node(&format!("{}/smart_charging_activation", BATT_DIR), "1");
        Self::write_node(&format!("{}/store_mode", BATT_DIR), "1");
        Self::write_node(&format!("{}/batt_slate_mode", BATT_DIR), "1");
        Self::write_node_lock(&format!("{}/siop_level", BATT_DIR), "100");
        Self::write_node_lock(&format!("{}/cool_mode", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/call_mode", BATT_DIR), "0");
        Self::write_node(&format!("{}/bd_trickle_dry_run", BATT_DIR), "1");

        let cur_cap = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(80);
        let cap_str = cur_cap.to_string();
        Self::write_node(&format!("{}/charge_stop_level", BATT_DIR), &cap_str);
        Self::write_node("/sys/devices/platform/google,battery/charge_stop_level", &cap_str);
        Self::write_node("/sys/devices/platform/google,charger/charge_stop_level", &cap_str);

        // 3. Qualcomm Snapdragon Zero Cutoff
        Self::write_node_lock(&format!("{}/restrict_cur", QC_DIR), "0");
        Self::write_node_lock(&format!("{}/restricted_charging", QC_DIR), "1");
        Self::write_node_lock(&format!("{}/direct_charging", QC_DIR), "0");
        Self::write_node_lock(&format!("{}/thermal_input_current_limit", BATT_DIR), "0");

        if let Some(byp_node) = Self::detect_hw_bypass_node() {
            Self::write_node(byp_node, "1");
        }

        Self::restore_battery_temperature();
        Self::signal_processes(&["thermalloadalgod", "com.xiaomi.joyose", "mi_thermald", "thermal-engine"], true);
    }

    /// Mode 2: Extreme / Super Fast Charging (Screen-On & Screen-Off Unthrottled)
    pub fn apply_extreme_charging(target_soc: u32, allow_lockout_bypass: bool, requested_ma: u32) {
        let hw_ceiling = Self::probe_hardware_ceiling_ma();
        let mut target_ma = if requested_ma == 0 { hw_ceiling } else { requested_ma };

        // Adaptive thermal safety curve when full thermal lockout bypass is not forced
        if !allow_lockout_bypass {
            let real_dc = Self::read_real_battery_temp_dc();
            if real_dc >= 470 {
                target_ma = (target_ma * 60 / 100).max(2000);
            } else if real_dc >= 445 {
                target_ma = (target_ma * 80 / 100).max(3000);
            }
        }

        // Host PC USB safeguard
        let ptyp = Self::read_node(&format!("{}/type", USB_DIR))
            .or_else(|| Self::read_node(&format!("{}/real_type", USB_DIR)))
            .or_else(|| Self::read_node(&format!("{}/Charger_Type", MTK_DIR)))
            .unwrap_or_default()
            .to_lowercase();

        if Path::new("/sys/class/power_supply/pc_port").exists()
            || ptyp.contains("sdp")
            || ptyp.contains("cdp")
            || ptyp.contains("pc")
        {
            target_ma = 1500;
        }

        let target_ua = (target_ma as u64) * 1000;
        let target_ua_str = target_ua.to_string();
        let mtk_ma = target_ma.min(hw_ceiling.max(6000));
        let mtk_ma_str = mtk_ma.to_string();
        let target_soc_str = target_soc.to_string();

        let _ = fs::write("/dev/lynx_extreme_charging", &mtk_ma_str);
        let _ = fs::remove_file("/dev/lynx_bypass_active");

        // 1. Universal Linux & Android Rails
        for node in [
            format!("{}/constant_charge_current_max", BATT_DIR),
            format!("{}/constant_charge_current", BATT_DIR),
            format!("{}/current_max", BATT_DIR),
            format!("{}/input_current_limit", BATT_DIR),
            format!("{}/constant_charge_current_max", MAIN_DIR),
            format!("{}/current_max", MAIN_DIR),
            format!("{}/current_max", USB_DIR),
            format!("{}/hw_current_max", USB_DIR),
        ] {
            Self::write_node_lock(&node, &target_ua_str);
        }

        for node in [
            format!("{}/charge_control_limit_max", BATT_DIR),
            format!("{}/charge_control_limit", BATT_DIR),
            format!("{}/input_suspend", BATT_DIR),
        ] {
            Self::write_node_lock(&node, "0");
        }

        for node in [
            format!("{}/fastcharge_mode", BATT_DIR),
            format!("{}/fast_charge", BATT_DIR),
            format!("{}/charging_enabled", BATT_DIR),
        ] {
            Self::write_node_lock(&node, "1");
        }

        // 2. MediaTek (Dimensity & Helio) Architecture & Direct Charge Pump
        for node in [
            format!("{}/pe40", MTK_DIR),
            format!("{}/pe20", MTK_DIR),
            format!("{}/BatteryNotify", MTK_DIR),
            format!("{}/sw_jeita", MTK_DIR),
            format!("{}/enable_sc", MTK_DIR),
        ] {
            Self::write_node_lock(&node, "1");
        }

        for node in [
            format!("{}/BN_TestMode", MTK_DIR),
            format!("{}/tran_charger_full", MTK_DIR),
            format!("{}/bypass_charger", MTK_DIR),
            format!("{}/tran_game_mode", MTK_DIR),
        ] {
            Self::write_node_lock(&node, "0");
        }

        Self::write_node_lock(&format!("{}/input_current", MTK_DIR), &mtk_ma_str);
        Self::write_node_lock(&format!("{}/chg1_current", MTK_DIR), &mtk_ma_str);
        Self::write_node_lock(&format!("{}/chg2_current", MTK_DIR), &mtk_ma_str);
        Self::write_node_lock(&format!("{}/pdc_max_watt", MTK_DIR), "68");
        Self::write_node_lock(&format!("{}/sc_ibat_limit", MTK_DIR), &mtk_ma_str);
        Self::write_node(&format!("{}/sc_tuisoc", MTK_DIR), &target_soc_str);

        // MediaTek Kernel PID Thermal Derater (ABCCT) Screen-On & Screen-Off Full Speed
        // Format: <target_temp> <kp> <ki> <kd> <max_bat_chr_curr_limit> <min_bat_chr_curr_limit> <pep30_max> <pep30_min>
        let abcct_payload = format!("70000 1000 200000 5 {} 4500 {} 4500", mtk_ma, mtk_ma);
        Self::write_node_lock("/proc/driver/thermal/clabcct_lcmoff", &abcct_payload);
        Self::write_node_lock("/proc/driver/thermal/clabcct", &abcct_payload);
        Self::signal_processes(&["thermalloadalgod", "com.xiaomi.joyose", "mi_thermald", "thermal-engine"], true);

        // 3. Qualcomm Snapdragon Architecture
        Self::write_node_lock(&format!("{}/direct_charging", QC_DIR), "1");
        Self::write_node_lock(&format!("{}/restricted_charging", QC_DIR), "0");
        Self::write_node_lock(&format!("{}/restrict_cur", QC_DIR), &target_ua_str);
        Self::write_node_lock("/sys/class/power_supply/smb1390/parallel_charging_enabled", "1");
        Self::write_node_lock(&format!("{}/system_temp_level", QC_DIR), "0");
        Self::write_node_lock(&format!("{}/system_temp_level", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/temp_state", BATT_DIR), "0");

        // 4. Xiaomi / Samsung / OnePlus / Pixel / ROG / Motorola
        Self::write_node_lock(&format!("{}/thermal_input_current_limit", BATT_DIR), &target_ua_str);
        Self::write_node_lock(&format!("{}/input_current_settled", BATT_DIR), &target_ua_str);
        Self::write_node_lock(&format!("{}/boost_current", BATT_DIR), "1");
        Self::write_node_lock(&format!("{}/step_charging_enabled", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/quick_charge_type", BATT_DIR), "2");
        Self::write_node_lock(&format!("{}/siop_level", BATT_DIR), "100");
        Self::write_node_lock(&format!("{}/store_mode", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/batt_slate_mode", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/wc_control", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/afc_result", BATT_DIR), "1");
        Self::write_node_lock(&format!("{}/direct_charger_mode", BATT_DIR), "1");
        Self::write_node_lock(&format!("{}/hv_charger_status", BATT_DIR), "1");
        Self::write_node_lock(&format!("{}/cool_mode", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/call_mode", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/vooc_charging", BATT_DIR), "1");
        Self::write_node_lock(&format!("{}/fast_charge_user_type", BATT_DIR), "1");
        Self::write_node_lock(&format!("{}/authenticate", BATT_DIR), "1");
        Self::write_node_lock(&format!("{}/charge_stop_level", BATT_DIR), "100");
        Self::write_node_lock("/sys/devices/platform/google,battery/charge_stop_level", "100");
        Self::write_node_lock("/sys/devices/platform/google,charger/charge_stop_level", "100");
        Self::write_node_lock(&format!("{}/bd_trickle_dry_run", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/device/smart_charging", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/charging_limit_mode", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/mmi_charging_enable", BATT_DIR), "1");
        Self::write_node_lock(&format!("{}/factory_mode", BATT_DIR), "1");

        // 5. Transsion (Infinix / Tecno) 33W+ Direct Charge Pump (pca_dv2_algo) & PCB Thermal Bypass
        let pcb_payload = format!("[95,{},100,{},{}]", mtk_ma, mtk_ma, mtk_ma);
        Self::write_node_lock("/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug", &pcb_payload);
        Self::write_node_lock("/sys/devices/platform/tran_battery/pcb_thermal_debug", &pcb_payload);
        if Path::new("/sys/devices/platform/pca_dv2_algo/dv2_debug").exists() {
            Self::set_perm("/sys/devices/platform/pca_dv2_algo/dv2_debug", 0o666);
            let _ = fs::write("/sys/devices/platform/pca_dv2_algo/dv2_debug", "[101,85,88,90,0,0,0]");
            let _ = fs::write("/sys/devices/platform/pca_dv2_algo/dv2_debug", "[102,85,88,90,0,0,0]");
            Self::set_perm("/sys/devices/platform/pca_dv2_algo/dv2_debug", 0o444);
        }
        if Path::new("/proc/driver/thermal/tzbts_param").exists() {
            Self::write_node_lock(
                "/proc/driver/thermal/tzbts_param",
                "PUP_R 200000 PUP_VOLT 1800 OVER_CRITICAL_L 4397119 NTC_TABLE 7 0",
            );
        }

        // 6. Universal Thermal Zones (Keep mode="enabled" so pca_dv2_algo stays alive, raise trip_points to 95000)
        if let Ok(entries) = fs::read_dir("/sys/class/thermal") {
            for entry in entries.flatten() {
                let name = entry.file_name();
                let name_str = name.to_string_lossy();
                if name_str.starts_with("thermal_zone") {
                    let tz_dir = format!("/sys/class/thermal/{}", name_str);
                    let tz_type = Self::read_node(&format!("{}/type", tz_dir))
                        .unwrap_or_default()
                        .to_lowercase();
                    if [
                        "battery", "bms", "chg", "charger", "mtktsap", "tsbuck", "skin",
                        "pcb", "sub_batt", "quiet", "xo_therm", "pmic",
                    ]
                    .iter()
                    .any(|k| tz_type.contains(k))
                    {
                        let mode_path = format!("{}/mode", tz_dir);
                        if Path::new(&mode_path).exists() {
                            Self::set_perm(&mode_path, 0o666);
                            let _ = fs::write(&mode_path, "enabled");
                        }
                        for idx in 0..10 {
                            let tp = format!("{}/trip_point_{}_temp", tz_dir, idx);
                            if Path::new(&tp).exists() {
                                Self::write_node_lock(&tp, "95000");
                            }
                        }
                    }
                } else if allow_lockout_bypass && name_str.starts_with("cooling_device") {
                    let c_dir = format!("/sys/class/thermal/{}", name_str);
                    let c_type = Self::read_node(&format!("{}/type", c_dir))
                        .unwrap_or_default()
                        .to_lowercase();
                    if [
                        "bcct", "chg", "current", "abcct", "battery", "cdev", "skin", "thermal",
                    ]
                    .iter()
                    .any(|k| c_type.contains(k))
                    {
                        Self::write_node_lock(&format!("{}/cur_state", c_dir), "0");
                    }
                }
            }
        }

        // 7. Thermal Lockout Bypass (Spoof 28°C on Battery_Temperature if enabled)
        if allow_lockout_bypass {
            Self::write_node_lock("/sys/devices/platform/battery/Battery_Temperature", "28");
        } else {
            Self::restore_battery_temperature();
        }

        if let Some(byp_node) = Self::detect_hw_bypass_node() {
            Self::write_node(byp_node, "0");
        }
    }

    pub fn restore_battery_temperature() {
        let path = "/sys/devices/platform/battery/Battery_Temperature";
        if Path::new(path).exists() {
            let real_c = (Self::read_real_battery_temp_dc() / 10).clamp(28, 42);
            Self::set_perm(path, 0o666);
            let _ = fs::write(path, real_c.to_string());
            Self::set_perm(path, 0o644);
        }
    }

    /// Ultra-fast (< 0.5 ms) lock re-assertion for active Extreme/Fast Charging
    pub fn reapply_lock() {
        let cfg = ConfigStore::load_charging_config();
        let mtk_ma = cfg.limit_current_ma.max(3500).min(6000);
        let mtk_ma_str = mtk_ma.to_string();
        let target_ua_str = ((mtk_ma as u64) * 1000).to_string();

        let abcct_payload = format!("70000 1000 200000 5 {} 4500 {} 4500", mtk_ma, mtk_ma);
        Self::write_node_lock("/proc/driver/thermal/clabcct_lcmoff", &abcct_payload);
        Self::write_node_lock("/proc/driver/thermal/clabcct", &abcct_payload);

        let pcb_payload = format!("[95,{},100,{},{}]", mtk_ma, mtk_ma, mtk_ma);
        Self::write_node_lock("/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug", &pcb_payload);
        Self::write_node_lock("/sys/devices/platform/tran_battery/pcb_thermal_debug", &pcb_payload);
        if Path::new("/sys/devices/platform/pca_dv2_algo/dv2_debug").exists() {
            Self::set_perm("/sys/devices/platform/pca_dv2_algo/dv2_debug", 0o666);
            let _ = fs::write("/sys/devices/platform/pca_dv2_algo/dv2_debug", "[101,85,88,90,0,0,0]");
            let _ = fs::write("/sys/devices/platform/pca_dv2_algo/dv2_debug", "[102,85,88,90,0,0,0]");
            Self::set_perm("/sys/devices/platform/pca_dv2_algo/dv2_debug", 0o444);
        }
        if Path::new("/proc/driver/thermal/tzbts_param").exists() {
            Self::write_node_lock(
                "/proc/driver/thermal/tzbts_param",
                "PUP_R 200000 PUP_VOLT 1800 OVER_CRITICAL_L 4397119 NTC_TABLE 7 0",
            );
        }

        Self::write_node_lock(&format!("{}/input_current", MTK_DIR), &mtk_ma_str);
        Self::write_node_lock(&format!("{}/chg1_current", MTK_DIR), &mtk_ma_str);
        Self::write_node_lock(&format!("{}/chg2_current", MTK_DIR), &mtk_ma_str);
        Self::write_node_lock(&format!("{}/sc_ibat_limit", MTK_DIR), &mtk_ma_str);
        Self::write_node_lock(&format!("{}/constant_charge_current_max", BATT_DIR), &target_ua_str);

        if cfg.thermal_lockout_bypass_enabled {
            Self::write_node_lock("/sys/devices/platform/battery/Battery_Temperature", "28");
        }
        Self::signal_processes(&["thermalloadalgod"], true);
    }

    /// Mode 3: Normal Regulated Charging
    pub fn apply_regulated_charging(requested_ma: u32) {
        let mut target_ma = if requested_ma == 0 { 1500 } else { requested_ma };

        let _ = fs::remove_file("/dev/lynx_extreme_charging");
        let _ = fs::remove_file("/dev/lynx_bypass_active");
        Self::unlock_extreme_nodes();

        if let Some(byp_node) = Self::detect_hw_bypass_node() {
            Self::write_node(byp_node, "0");
        }
        Self::write_node(&format!("{}/device/smart_charging", BATT_DIR), "0");
        Self::write_node(&format!("{}/smart_charging_activation", BATT_DIR), "0");
        Self::write_node(&format!("{}/direct_charging", QC_DIR), "0");
        Self::write_node(&format!("{}/store_mode", BATT_DIR), "0");
        Self::write_node(&format!("{}/batt_slate_mode", BATT_DIR), "0");
        Self::write_node(&format!("{}/enable_sc", MTK_DIR), "0");

        Self::set_perm(&format!("{}/siop_level", BATT_DIR), 0o644);
        Self::write_node(&format!("{}/siop_level", BATT_DIR), "100");
        Self::set_perm(&format!("{}/cool_mode", BATT_DIR), 0o644);
        Self::set_perm(&format!("{}/call_mode", BATT_DIR), 0o644);
        Self::write_node(&format!("{}/boost_current", BATT_DIR), "0");
        Self::write_node(&format!("{}/sw_jeita", MTK_DIR), "1");

        if Path::new("/proc/driver/thermal/clabcct_lcmoff").exists() {
            Self::set_perm("/proc/driver/thermal/clabcct_lcmoff", 0o666);
            let _ = fs::write("/proc/driver/thermal/clabcct_lcmoff", "1");
            Self::set_perm("/proc/driver/thermal/clabcct_lcmoff", 0o644);
        }
        if Path::new("/proc/driver/thermal/clabcct").exists() {
            Self::set_perm("/proc/driver/thermal/clabcct", 0o666);
            let _ = fs::write("/proc/driver/thermal/clabcct", "1 42000 1000 200000 5 2000 0");
            Self::set_perm("/proc/driver/thermal/clabcct", 0o644);
        }

        Self::restore_battery_temperature();

        for node in [
            "/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug",
            "/sys/devices/platform/tran_battery/pcb_thermal_debug",
        ] {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
                let _ = fs::write(node, "[45,3000,50,2000,1000]");
                Self::set_perm(node, 0o644);
            }
        }

        Self::write_node(&format!("{}/BN_TestMode", MTK_DIR), "0");
        Self::write_node(&format!("{}/BatteryNotify", MTK_DIR), "1");
        Self::write_node(&format!("{}/sw_jeita", MTK_DIR), "1");

        Self::write_node(&format!("{}/input_suspend", BATT_DIR), "0");
        Self::write_node(&format!("{}/charging_enabled", BATT_DIR), "1");
        Self::write_node(&format!("{}/charge_control_limit_max", BATT_DIR), "0");

        Self::write_node(&format!("{}/input_current", MTK_DIR), "6000");
        Self::write_node(&format!("{}/current_max", USB_DIR), "4500000");
        Self::write_node(&format!("{}/hw_current_max", USB_DIR), "4500000");
        Self::write_node(&format!("{}/current_max", MAIN_DIR), "4500000");

        // Dynamic Headroom Guard: Ensure net current is not negative
        if let Some(mut cur_now) = Self::read_i64(&format!("{}/current_now", BATT_DIR)) {
            if cur_now > 100_000 || cur_now < -100_000 {
                cur_now /= 1000;
            }
            if cur_now < 0 {
                let deficit = (-cur_now) as u32;
                target_ma = (target_ma + deficit + 300).min(3500);
            }
        }

        let target_ua_str = ((target_ma as u64) * 1000).to_string();
        let target_ma_str = target_ma.to_string();

        Self::set_perm(&format!("{}/constant_charge_current_max", BATT_DIR), 0o644);
        Self::write_node(&format!("{}/constant_charge_current", BATT_DIR), &target_ua_str);
        Self::write_node(&format!("{}/constant_charge_current_max", BATT_DIR), &target_ua_str);
        Self::write_node(&format!("{}/constant_charge_current_max", MAIN_DIR), &target_ua_str);
        Self::write_node(&format!("{}/current_max", MAIN_DIR), &target_ua_str);
        Self::write_node(&format!("{}/current_max", USB_DIR), &target_ua_str);
        Self::write_node(&format!("{}/hw_current_max", USB_DIR), &target_ua_str);
        Self::write_node(&format!("{}/max_charge_current", EXT_DIR), &target_ua_str);
        Self::write_node(&format!("{}/chg_pwr_fcc", EXT_DIR), &target_ua_str);

        Self::write_node(&format!("{}/chg1_current", MTK_DIR), &target_ma_str);
        Self::write_node(&format!("{}/chg2_current", MTK_DIR), &target_ma_str);
        Self::write_node(&format!("{}/sc_ibat_limit", MTK_DIR), &target_ma_str);

        Self::write_node(&format!("{}/restrict_cur", QC_DIR), &target_ua_str);
        Self::write_node(&format!("{}/restricted_charging", QC_DIR), "0");
    }

    /// Reads real physical battery cell temperature in decicelsius (e.g. 345 = 34.5°C).
    /// Immune to `/sys/devices/platform/battery/Battery_Temperature` spoofing on MediaTek.
    pub fn read_real_battery_temp_dc() -> i64 {
        let mut batt_tz_dc: Option<i64> = None;
        let mut wmt_tz_dc: Option<i64> = None;
        let mut bts_tz_dc: Option<i64> = None;

        if let Ok(entries) = fs::read_dir("/sys/class/thermal") {
            for entry in entries.flatten() {
                let name = entry.file_name();
                let name_str = name.to_string_lossy();
                if name_str.starts_with("thermal_zone") {
                    let tz_dir = format!("/sys/class/thermal/{}", name_str);
                    let tz_type = Self::read_node(&format!("{}/type", tz_dir))
                        .unwrap_or_default()
                        .to_lowercase();
                    if let Some(raw_tz) = Self::read_i64(&format!("{}/temp", tz_dir)) {
                        if raw_tz > 10_000 && raw_tz < 95_000 {
                            let dc = raw_tz / 100;
                            if tz_type.contains("battery")
                                || tz_type.contains("mtktsbattery")
                                || tz_type.contains("bms")
                            {
                                batt_tz_dc = Some(dc);
                            } else if tz_type.contains("mtktswmt") {
                                wmt_tz_dc = Some(dc);
                            } else if tz_type.contains("mtktsbtsmdpa") || tz_type.contains("mtktsap") {
                                bts_tz_dc = Some((dc - 40).max(280));
                            }
                        }
                    }
                }
            }
        }

        // If mtktsbattery is NOT spoofed to 28.0C (280 dC), use it directly;
        // otherwise use the unspoofed physical board/WMT thermistor!
        if let Some(bdc) = batt_tz_dc {
            if bdc != 280 && bdc > 150 {
                return bdc;
            }
        }
        if let Some(wdc) = wmt_tz_dc {
            return wdc;
        }
        if let Some(apdc) = bts_tz_dc {
            return apdc;
        }
        batt_tz_dc.unwrap_or(320)
    }

    /// Evaluates `config.json` and live battery state, applying the appropriate charging mode immediately.
    pub fn apply_from_config() {
        let cfg = ConfigStore::load_charging_config();
        let cur_cap = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(50) as u32;
        let cur_stat = Self::read_node(&format!("{}/status", BATT_DIR)).unwrap_or_else(|| "Unknown".to_string());
        let real_dc = Self::read_real_battery_temp_dc();

        // 1. Emergency Thermal Guard (>= 49.0°C physical battery cell)
        if cfg.emergency_temp_guard_enabled && real_dc >= 490 {
            let _ = fs::write("/dev/lynx_charging_guard", format!("guard_active=true temp={}\n", real_dc));
            let _ = fs::remove_file("/dev/lynx_extreme_charging");
            Self::restore_battery_temperature();
            Self::apply_regulated_charging(1500);
            return;
        } else if real_dc <= 430 {
            let _ = fs::remove_file("/dev/lynx_charging_guard");
        }

        // 2. Immediate User-Forced Bypass Toggle
        if cfg.bypass_enabled {
            let _ = fs::write("/dev/lynx_charging_state", format!("state=bypass_forced cap={}\n", cur_cap));
            Self::apply_bypass_charging();
            return;
        }

        // 3. AutoCut when 100% Full (Overnight / Safe Full AutoCut)
        if cur_cap >= 100 || cur_stat == "Full" {
            let _ = fs::write("/dev/lynx_charging_state", format!("state=bypass_100 cap={}\n", cur_cap));
            Self::apply_bypass_charging();
            return;
        }

        // 4. Smart Battery Limit (Batas Pengisian Cerdas, e.g. 70% - 99%)
        // With 3% hysteresis buffer to prevent continuous cycling
        let smart_limit = cfg.max_battery_percent.clamp(0, 100) as u32;
        if smart_limit > 0 && smart_limit < 100 {
            let is_currently_bypassed = Path::new("/dev/lynx_bypass_active").exists();
            let lower_hysteresis_bound = smart_limit.saturating_sub(3);

            if is_currently_bypassed {
                // Keep bypass active until battery drops below (limit - 3)%
                if cur_cap >= lower_hysteresis_bound {
                    let _ = fs::write(
                        "/dev/lynx_charging_state",
                        format!("state=bypass_limit cap={} limit={} hyst={}\n", cur_cap, smart_limit, lower_hysteresis_bound),
                    );
                    Self::apply_bypass_charging();
                    return;
                }
            } else if cur_cap >= smart_limit {
                // Trigger bypass as soon as battery hits smart limit
                let _ = fs::write(
                    "/dev/lynx_charging_state",
                    format!("state=bypass_limit cap={} limit={}\n", cur_cap, smart_limit),
                );
                Self::apply_bypass_charging();
                return;
            }
        }

        // 4. Smart Tapering (>= 90%)
        if cfg.smart_tapering_enabled && cur_cap >= 90 {
            Self::restore_battery_temperature();
            if cur_cap >= 95 {
                let _ = fs::write("/dev/lynx_charging_state", format!("state=tapering_95 cap={}\n", cur_cap));
                Self::apply_regulated_charging(750);
            } else {
                let _ = fs::write("/dev/lynx_charging_state", format!("state=tapering_90 cap={}\n", cur_cap));
                Self::apply_regulated_charging(1500);
            }
            return;
        }

        // 5. Extreme / Super Fast Charging or High Current (>= 3000mA)
        let active_prof = Self::read_node("/data/adb/lynx/active_profile").unwrap_or_else(|| "balance".to_string());
        if cfg.extreme_charging_enabled || active_prof == "extreme" || cfg.limit_current_ma >= 3000 {
            let effective_ma = if cfg.limit_current_ma >= 3000 {
                cfg.limit_current_ma as u32
            } else {
                6000
            };
            let _ = fs::write("/dev/lynx_charging_state", format!("state=extreme cap={}\n", cur_cap));
            Self::apply_extreme_charging(
                cfg.high_current_target_percent.max(0) as u32,
                cfg.thermal_lockout_bypass_enabled,
                effective_ma,
            );
        } else {
            let _ = fs::write("/dev/lynx_charging_state", format!("state=regulated cap={}\n", cur_cap));
            Self::apply_regulated_charging(cfg.limit_current_ma.max(500) as u32);
        }
    }

    /// Periodic tick called inside `DaemonRunner::run()` every cycle.
    pub fn tick() {
        let usb_online = Self::read_node(&format!("{}/online", USB_DIR)).unwrap_or_default();
        let ac_online = Self::read_node(&format!("{}/ac/online", PS_DIR)).unwrap_or_default();
        let chg_online = Self::read_node(&format!("{}/charger/online", PS_DIR)).unwrap_or_default();
        let batt_status = Self::read_node(&format!("{}/status", BATT_DIR)).unwrap_or_default();

        let is_plugged = usb_online == "1"
            || ac_online == "1"
            || chg_online == "1"
            || batt_status == "Charging"
            || batt_status == "Not charging";

        if !is_plugged || batt_status == "Discharging" {
            let _ = fs::remove_file("/dev/lynx_charging_guard");
            let _ = fs::remove_file("/dev/lynx_extreme_charging");
            let _ = fs::remove_file("/dev/lynx_bypass_active");
            Self::restore_battery_temperature();
            let cap_now = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(50);
            let _ = fs::write("/dev/lynx_charging_state", format!("state=discharging cap={}\n", cap_now));
            return;
        }

        Self::apply_from_config();
    }

    /// Generates the complete JSON telemetry output in < 1.5 ms with zero subprocess forks.
    pub fn status_json() -> String {
        let cfg = ConfigStore::load_charging_config();
        let cap = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(0);
        let stat = Self::read_node(&format!("{}/status", BATT_DIR)).unwrap_or_else(|| "Unknown".to_string());
        let hlth = Self::read_node(&format!("{}/health", BATT_DIR)).unwrap_or_else(|| "Good".to_string());
        let temp_raw = Self::read_i64(&format!("{}/temp", BATT_DIR)).unwrap_or(280);
        let volt_raw = Self::read_i64(&format!("{}/voltage_now", BATT_DIR)).unwrap_or(0);
        let cur_raw = Self::read_i64(&format!("{}/current_now", BATT_DIR)).unwrap_or(0);

        // Adapter Voltage (mV)
        let mut adpv = Self::read_i64(&format!("{}/ADC_Charger_Voltage", MTK_DIR))
            .or_else(|| Self::read_i64("/sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger"))
            .or_else(|| Self::read_i64(&format!("{}/voltage_now", USB_DIR)))
            .or_else(|| Self::read_i64(&format!("{}/charger_voltage", BATT_DIR)))
            .or_else(|| Self::read_i64(&format!("{}/voltage_now", MAIN_DIR)))
            .unwrap_or(0);
        if adpv > 100_000 {
            adpv /= 1000;
        }
        if adpv < 1000 {
            adpv = 0;
        }

        // Adapter Current (mA)
        let mut ibus_val = Self::read_i64("/sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger")
            .map(|v| if v > 10 && v < 1000 { v * 10 } else { v })
            .or_else(|| Self::read_first_i2c_node("/sys/bus/i2c/drivers/rt9759", "Ibus"))
            .or_else(|| Self::read_i64(&format!("{}/current_now", USB_DIR)))
            .or_else(|| Self::read_i64(&format!("{}/input_current_now", USB_DIR)))
            .or_else(|| Self::read_i64(&format!("{}/current_now", MAIN_DIR)))
            .unwrap_or(0);
        if ibus_val > 100_000 {
            ibus_val /= 1000;
        }
        if ibus_val < 0 || adpv == 0 {
            ibus_val = 0;
        }

        let chgtyp = Self::read_node(&format!("{}/Charger_Type", MTK_DIR))
            .or_else(|| Self::read_node(&format!("{}/type", USB_DIR)))
            .unwrap_or_default();

        let rfc_val = Self::read_first_i2c_node("/sys/bus/i2c/drivers/rt9759", "rfc_dcp_ta")
            .unwrap_or_else(|| if chgtyp == "9" { 1 } else { 0 });
        let rfc_auth = rfc_val == 1;

        let real_dc = Self::read_real_battery_temp_dc();

        let v_clean = if volt_raw.abs() > 100_000 { volt_raw / 1000 } else { volt_raw };
        let c_clean = if cur_raw.abs() > 100_000 { cur_raw / 1000 } else { cur_raw };

        let is_chg = c_clean > 50 || stat == "Charging";

        let (active_ic, protocol) = if is_chg {
            let ic = if rfc_auth || chgtyp == "9" || Path::new("/sys/bus/i2c/drivers/rt9759").exists() || (adpv > 7000 && c_clean >= 1500) {
                "Direct Charge Pump (RT9759 2:1)"
            } else if Path::new("/sys/class/power_supply/smb1390").exists() {
                "Qualcomm SMB1390 Dual-Pump"
            } else if Path::new("/sys/class/qcom-battery").exists() {
                "Qualcomm PMIC (PM8150/PM6150 Buck)"
            } else {
                "Switching Buck Converter (RT9471/Universal)"
            };

            let proto = if rfc_auth || chgtyp == "9" {
                "Transsion Super Charge (33W/45W/68W RFC)"
            } else if adpv > 8500 {
                "USB Power Delivery / PPS"
            } else if adpv > 8000 || chgtyp == "4" {
                "USB-PD / PE2.0 Fast Charge (18W)"
            } else if adpv > 4500 && c_clean >= 2000 {
                "Fast Charge (High Current 5V)"
            } else if adpv > 4000 {
                "Standard USB Fast Charge (5V)"
            } else {
                "Standard Charging"
            };
            (ic, proto)
        } else {
            ("Unknown / Standby", "Battery Power")
        };

        let guard_state = Path::new("/dev/lynx_charging_guard").exists();
        let chg_state = Self::read_node("/dev/lynx_charging_state").unwrap_or_default();
        let is_bypass_active = Path::new("/dev/lynx_bypass_active").exists();
        let is_overnight_latch = chg_state.contains("bypass_100") || (cap >= 100 && is_bypass_active) || stat == "Full";
        let is_limit_latched = chg_state.contains("bypass_limit") || (cfg.max_battery_percent > 0 && cfg.max_battery_percent < 100 && cap >= (cfg.max_battery_percent as i64) && is_bypass_active);
        let is_tapering = chg_state.contains("tapering");

        let _now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs();

        format!(
            "{{\n  \"capacity\": {},\n  \"status\": \"{}\",\n  \"health\": \"{}\",\n  \"temperature_c\": {:.1},\n  \"real_physical_temp_c\": {:.1},\n  \"voltage_mv\": {},\n  \"current_ma\": {},\n  \"adapter_voltage_mv\": {},\n  \"adapter_current_ma\": {},\n  \"active_ic\": \"{}\",\n  \"fast_charge_protocol\": \"{}\",\n  \"rfc_authenticated\": {},\n  \"emergency_guard_active\": {},\n  \"overnight_bypass_latched\": {},\n  \"smart_limit_latched\": {},\n  \"bypass_active\": {},\n  \"smart_tapering_active\": {},\n  \"bypass_enabled\": {},\n  \"extreme_charging_enabled\": {},\n  \"limit_current_ma\": {},\n  \"max_battery_percent\": {},\n  \"high_current_target_percent\": {},\n  \"temp_cutoff_c\": {},\n  \"thermal_lockout_bypass_enabled\": {},\n  \"smart_tapering_enabled\": {},\n  \"emergency_temp_guard_enabled\": {},\n  \"night_sleep_guard_enabled\": {}\n}}",
            cap,
            stat,
            hlth,
            (temp_raw as f64) / 10.0,
            (real_dc as f64) / 10.0,
            v_clean,
            c_clean,
            adpv,
            ibus_val,
            active_ic,
            protocol,
            rfc_auth,
            guard_state,
            is_overnight_latch,
            is_limit_latched,
            is_bypass_active,
            is_tapering,
            cfg.bypass_enabled,
            cfg.extreme_charging_enabled,
            cfg.limit_current_ma,
            cfg.max_battery_percent,
            cfg.high_current_target_percent,
            cfg.temp_cutoff_c,
            cfg.thermal_lockout_bypass_enabled,
            cfg.smart_tapering_enabled,
            cfg.emergency_temp_guard_enabled,
            cfg.night_sleep_guard_enabled
        )
    }

    fn read_first_i2c_node(driver_dir: &str, leaf: &str) -> Option<i64> {
        let entries = fs::read_dir(driver_dir).ok()?;
        for entry in entries.flatten() {
            let candidate = entry.path().join(leaf);
            if candidate.exists() {
                if let Some(val) = Self::read_i64(&candidate.to_string_lossy()) {
                    return Some(val);
                }
            }
        }
        None
    }
}
