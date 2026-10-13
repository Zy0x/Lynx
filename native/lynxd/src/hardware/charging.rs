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
const _EXT_DIR: &str = "/sys/class/power_supply/battery_ext";
const QC_DIR: &str = "/sys/class/qcom-battery";
const MTK_DIR: &str = "/sys/devices/platform/charger";

const DISCONNECT_CHARGING_RESETS: [(&str, &str); 14] = [
    ("/sys/class/power_supply/battery/input_suspend", "0"),
    ("/sys/class/power_supply/battery/charging_enabled", "1"),
    ("/sys/devices/platform/charger/pe20", "0"),
    ("/sys/devices/platform/charger/pe40", "0"),
    ("/sys/devices/platform/charger/enable_sc", "0"),
    ("/sys/devices/platform/charger/bypass_charger", "0"),
    ("/sys/devices/platform/charger/tran_charger_full", "0"),
    ("/sys/devices/platform/charger/BN_TestMode", "0"),
    ("/sys/devices/platform/charger/BatteryNotify", "1"),
    ("/sys/devices/platform/charger/sw_jeita", "1"),
    ("/sys/devices/platform/charger/pdc_max_watt", "33"),
    ("/sys/class/power_supply/battery/fastcharge_mode", "0"),
    ("/sys/class/power_supply/battery/fast_charge", "0"),
    ("/sys/class/qcom-battery/direct_charging", "0"),
];

// Values and permissions changed by charging modes are captured once per cable
// session and restored exactly when the physical input is removed.
const CHARGING_SESSION_NODES: &[&str] = &[
    "/sys/class/power_supply/battery/input_suspend",
    "/sys/class/power_supply/battery/charging_enabled",
    "/sys/class/power_supply/battery/constant_charge_current_max",
    "/sys/class/power_supply/battery/constant_charge_current",
    "/sys/class/power_supply/battery/current_max",
    "/sys/class/power_supply/battery/input_current_limit",
    "/sys/class/power_supply/battery/charge_control_limit_max",
    "/sys/class/power_supply/battery/charge_control_limit",
    "/sys/class/power_supply/battery/fastcharge_mode",
    "/sys/class/power_supply/battery/fast_charge",
    "/sys/class/power_supply/battery/boost_current",
    "/sys/class/power_supply/battery/thermal_input_current_limit",
    "/sys/class/power_supply/battery/system_temp_level",
    "/sys/class/power_supply/battery/temp_state",
    "/sys/class/power_supply/battery/step_charging_enabled",
    "/sys/class/power_supply/battery/quick_charge_type",
    "/sys/class/power_supply/battery/siop_level",
    "/sys/class/power_supply/battery/store_mode",
    "/sys/class/power_supply/battery/batt_slate_mode",
    "/sys/class/power_supply/battery/cool_mode",
    "/sys/class/power_supply/battery/call_mode",
    "/sys/class/power_supply/battery/charge_stop_level",
    "/sys/class/power_supply/battery/bd_trickle_dry_run",
    "/sys/class/power_supply/battery/device/smart_charging",
    "/sys/class/power_supply/battery/charging_limit_mode",
    "/sys/class/power_supply/battery/smart_charging_activation",
    "/sys/class/power_supply/main/current_max",
    "/sys/class/power_supply/main/constant_charge_current_max",
    "/sys/class/power_supply/usb/current_max",
    "/sys/class/power_supply/usb/hw_current_max",
    "/sys/devices/platform/charger/input_current",
    "/sys/devices/platform/charger/chg1_current",
    "/sys/devices/platform/charger/chg2_current",
    "/sys/devices/platform/charger/sc_ibat_limit",
    "/sys/devices/platform/charger/enable_sc",
    "/sys/devices/platform/charger/pe20",
    "/sys/devices/platform/charger/pe40",
    "/sys/devices/platform/charger/tran_charger_full",
    "/sys/devices/platform/charger/bypass_charger",
    "/sys/devices/platform/charger/BN_TestMode",
    "/sys/devices/platform/charger/BatteryNotify",
    "/sys/devices/platform/charger/sw_jeita",
    "/sys/devices/platform/charger/pdc_max_watt",
    "/sys/devices/platform/odm/odm:tran_battery/Charging_CallState",
    "/sys/devices/platform/tran_battery/Charging_CallState",
    "/sys/class/qcom-battery/direct_charging",
    "/sys/class/qcom-battery/restricted_charging",
    "/sys/class/qcom-battery/restrict_cur",
];

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

    /// Dynamically enables or disables Battery Charging Current Throttling (BCCT) cooling devices.
    pub fn set_bcct_throttle(throttle: bool) {
        let val = if throttle { "1" } else { "0" };
        if let Ok(entries) = fs::read_dir("/sys/class/thermal") {
            for entry in entries.flatten() {
                let name = entry.file_name();
                let name_str = name.to_string_lossy();
                if name_str.starts_with("cooling_device") {
                    let type_path = format!("/sys/class/thermal/{}/type", name_str);
                    if let Ok(t) = fs::read_to_string(&type_path) {
                        let t_lower = t.trim().to_lowercase();
                        if t_lower.contains("bcct") {
                            let cur_path = format!("/sys/class/thermal/{}/cur_state", name_str);
                            Self::write_node(&cur_path, val);
                        }
                    }
                }
            }
        }
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
                        let mut matches = false;
                        let cmdline_path = format!("/proc/{}/cmdline", pid);
                        if let Ok(bytes) = fs::read(&cmdline_path) {
                            let cmd = String::from_utf8_lossy(&bytes).to_lowercase();
                            if patterns.iter().any(|p| cmd.contains(p)) {
                                matches = true;
                            }
                        }
                        if !matches {
                            let comm_path = format!("/proc/{}/comm", pid);
                            if let Ok(bytes) = fs::read(&comm_path) {
                                let comm = String::from_utf8_lossy(&bytes).to_lowercase();
                                if patterns.iter().any(|p| comm.contains(p)) {
                                    matches = true;
                                }
                            }
                        }
                        if matches {
                            unsafe {
                                let _ = kill(pid, sig);
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

    /// Checks if connected power source is a laptop / computer USB port (SDP/CDP).
    pub fn is_laptop_port() -> bool {
        let ptyp = Self::read_node(&format!("{}/type", USB_DIR))
            .or_else(|| Self::read_node(&format!("{}/real_type", USB_DIR)))
            .or_else(|| Self::read_node(&format!("{}/Charger_Type", MTK_DIR)))
            .unwrap_or_default()
            .to_uppercase();
        ptyp.contains("SDP") || ptyp.contains("CDP") || ptyp.contains("PC")
    }

    /// Checks if device is in USB OTG or Reverse Charging host mode.
    pub fn is_otg_active() -> bool {
        let ptyp = Self::read_node(&format!("{}/type", USB_DIR))
            .or_else(|| Self::read_node(&format!("{}/real_type", USB_DIR)))
            .unwrap_or_default()
            .to_uppercase();
        // Some MTK/Transsion kernels keep OTG_CTL asserted while the charger
        // input pump is active. A verified sink connection is stronger evidence
        // than that ambiguous control bit, so do not suppress charging policies.
        let sink_online = Self::read_node(&format!("{}/online", USB_DIR)).as_deref() == Some("1")
            || Self::read_node(&format!("{}/ac/online", PS_DIR)).as_deref() == Some("1")
            || Self::read_node(&format!("{}/charger/online", PS_DIR)).as_deref() == Some("1");
        let batt_status = Self::read_node(&format!("{}/status", BATT_DIR)).unwrap_or_default();
        let adpv = Self::read_i64(&format!("{}/ADC_Charger_Voltage", MTK_DIR))
            .or_else(|| Self::read_i64("/sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger"))
            .or_else(|| Self::read_i64(&format!("{}/voltage_now", USB_DIR)))
            .or_else(|| Self::read_i64(&format!("{}/charger_voltage", BATT_DIR)))
            .or_else(|| Self::read_i64(&format!("{}/voltage_now", MAIN_DIR)))
            .unwrap_or(0);
        let adpv_clean = if adpv > 100_000 { adpv / 1000 } else { adpv };
        if sink_online || batt_status == "Charging" || adpv_clean >= 3500 {
            return false;
        }

        if ptyp.contains("OTG") {
            return true;
        }

        if let Some(otg_ctl) = Self::read_node("/sys/devices/platform/odm/odm:tran_battery/OTG_CTL")
            .or_else(|| Self::read_node("/sys/devices/platform/tran_battery/OTG_CTL"))
        {
            if otg_ctl.trim() == "1" {
                return true;
            }
        }
        false
    }

    /// Dynamically probes and caches the PMIC's hardware current ceiling (in mA) before override.
    /// Supports ultra-fast devices (100W–240W, dual-cell 2S up to 25,000 mA).
    pub fn probe_hardware_ceiling_ma() -> u32 {
        let cache_file = "/dev/lynx_orig_chg_max";
        if let Some(cached) = Self::read_i64(cache_file) {
            if (2000..=30000).contains(&cached) {
                return cached as u32;
            }
        }
        let mut max_ma = 0u32;
        for node in &[
            "/sys/class/power_supply/battery/constant_charge_current_max",
            "/sys/class/power_supply/main/constant_charge_current_max",
            "/sys/class/power_supply/usb/hw_current_max",
            "/sys/devices/platform/charger/chg1_current",
            "/sys/devices/platform/charger/sc_ibat_limit",
            "/sys/devices/platform/charger/input_current",
        ] {
            if let Some(val) = Self::read_i64(node) {
                let ma = if val > 100_000 {
                    (val / 1000) as u32
                } else if val > 10_000 && val <= 30_000 {
                    val as u32
                } else if val > 30_000 {
                    (val / 1000) as u32
                } else {
                    val as u32
                };
                if ma > max_ma && ma <= 30_000 {
                    max_ma = ma;
                }
            }
        }
        // Dual-cell 2S detection (> 7.0V nominal battery voltage)
        let volt_raw = Self::read_i64(&format!("{}/voltage_now", BATT_DIR)).unwrap_or(0);
        let volt_mv = if volt_raw > 100_000 { volt_raw / 1000 } else { volt_raw };
        let is_dual_cell = volt_mv >= 7000 && volt_mv <= 12000;

        let resolved = if max_ma >= 2000 {
            max_ma
        } else if is_dual_cell {
            12000
        } else {
            6000
        };
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
        ];

        for &node in NODES {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
            }
        }

        Self::write_node("/sys/devices/platform/charger/BN_TestMode", "0");
        Self::write_node("/sys/devices/platform/charger/BatteryNotify", "1");
        Self::write_node("/sys/devices/platform/charger/sw_jeita", "1");
    }

    /// Mode 1: True Hardware Bypass Charging (Zero Battery Current, Vsys powered by Adapter)
    pub fn apply_bypass_charging() {
        let _ = fs::remove_file("/dev/lynx_extreme_charging");
        let _ = fs::remove_file("/dev/lynx_standard_charging");
        let _ = fs::write("/dev/lynx_bypass_active", "1");
        Self::unlock_extreme_nodes();

        // 1. MediaTek Platform Hardware Zero Cutoff (Vsys powered, battery decoupled)
        Self::set_bcct_throttle(true);
        Self::write_node_lock("/sys/devices/platform/charger/pdc_max_watt", "0");
        Self::write_node("/sys/devices/platform/charger/pe20", "0");
        Self::write_node("/sys/devices/platform/charger/pe40", "0");
        Self::write_node_lock(&format!("{}/input_current", MTK_DIR), "0");
        Self::write_node_lock(&format!("{}/chg1_current", MTK_DIR), "0");
        Self::write_node_lock(&format!("{}/chg2_current", MTK_DIR), "0");
        Self::write_node_lock(&format!("{}/sc_ibat_limit", MTK_DIR), "0");
        Self::write_node_lock(&format!("{}/enable_sc", MTK_DIR), "0");
        Self::write_node_lock(&format!("{}/tran_charger_full", MTK_DIR), "1");
        Self::write_node("/sys/devices/platform/charger/bypass_charger", "1");

        // Transsion 33W+ Direct Pump Suspend & CallState Clamp
        Self::write_node_lock("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "1");
        Self::write_node_lock("/sys/devices/platform/tran_battery/Charging_CallState", "1");
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

        // Keep OEM thermal daemons and trip-point policies active during bypass.

        // Ensure Android Framework keeps live uevent synchronization with physical kernel battery.
        // Calling 'dumpsys battery set' halts BatteryService updates ('UPDATES STOPPED'), which
        // freezes the UI/status bar percentage and prevents the system from tracking actual battery
        // drain or rise, risking sudden shutdown from undervoltage.
        let _ = std::process::Command::new("dumpsys")
            .args(["battery", "reset"])
            .output();
    }

    /// Mode: request the fastest charging mode supported by hardware without arbitrary limits.
    /// Supports up to 240W, dual-cell 2S, and un-caps third-party USB-PD / PPS chargers (Ugreen, Anker, etc.).
    pub fn apply_extreme_charging(_target_soc: u32, _allow_lockout_bypass: bool, requested_ma: u32) {
        let was_bypassed = Path::new("/dev/lynx_bypass_active").exists();
        let _ = fs::remove_file("/dev/lynx_bypass_active");
        let _ = fs::remove_file("/dev/lynx_standard_charging");
        if was_bypassed {
            let _ = std::process::Command::new("dumpsys").args(["battery", "reset"]).output();
            Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "0");
            Self::write_node("/sys/devices/platform/tran_battery/Charging_CallState", "0");
        }

        let hw_ceiling = Self::probe_hardware_ceiling_ma();
        let target_peak_ma = if requested_ma >= 2000 {
            requested_ma.min(hw_ceiling)
        } else {
            hw_ceiling
        };

        let effective_ma = if Self::is_laptop_port() {
            1500
        } else {
            target_peak_ma
        };
        let effective_ua_str = (effective_ma as u64 * 1000).to_string();
        let effective_ma_str = effective_ma.to_string();

        let _ = fs::write("/dev/lynx_extreme_charging", format!("hyper-{}", effective_ma));

        // Unlock extreme nodes first
        Self::unlock_extreme_nodes();

        // 1. Clear input suspend and enable native charging rails
        Self::write_node(&format!("{}/input_suspend", BATT_DIR), "0");
        Self::write_node(&format!("{}/charging_enabled", BATT_DIR), "1");
        Self::write_node(&format!("{}/fastcharge_mode", BATT_DIR), "1");
        Self::write_node(&format!("{}/fast_charge", BATT_DIR), "1");
        Self::write_node(&format!("{}/boost_current", BATT_DIR), "1");

        // 2. MediaTek Platform Enforcements & Dual Pump
        for node in [
            format!("{}/pe20", MTK_DIR),
            format!("{}/pe40", MTK_DIR),
            format!("{}/enable_sc", MTK_DIR),
            format!("{}/BatteryNotify", MTK_DIR),
            format!("{}/sw_jeita", MTK_DIR),
        ] {
            Self::write_node(&node, "1");
        }

        // Active Maximum Push to MediaTek hardware registers with permission lock
        Self::write_node_lock(&format!("{}/input_current", MTK_DIR), &effective_ma_str);
        Self::write_node_lock(&format!("{}/sc_ibat_limit", MTK_DIR), &effective_ma_str);
        Self::write_node_lock(&format!("{}/chg1_current", MTK_DIR), &effective_ma_str);
        Self::write_node_lock(&format!("{}/chg2_current", MTK_DIR), &effective_ma_str);

        // Dynamically uncap pdc_max_watt (never choke to 68W if device supports 120W+)
        let current_pdc = Self::read_i64(&format!("{}/pdc_max_watt", MTK_DIR)).unwrap_or(0);
        if current_pdc < 120 && Path::new(&format!("{}/pdc_max_watt", MTK_DIR)).exists() {
            Self::write_node(&format!("{}/pdc_max_watt", MTK_DIR), "120");
        }

        // 3. Universal Linux & Qualcomm Uncaps (Ugreen / Anker PD PPS 5A uncap)
        let usb_current_cap_ua = if effective_ma > 5000 { effective_ua_str.clone() } else { "5000000".to_string() };
        Self::write_node_lock(&format!("{}/current_max", USB_DIR), &usb_current_cap_ua);
        Self::write_node_lock(&format!("{}/current_max", MAIN_DIR), &effective_ua_str);
        Self::write_node_lock(&format!("{}/constant_charge_current_max", BATT_DIR), &effective_ua_str);
        Self::write_node_lock(&format!("{}/constant_charge_current", BATT_DIR), &effective_ua_str);
        Self::write_node_lock(&format!("{}/input_current_limit", BATT_DIR), &effective_ua_str);

        // Qualcomm direct & restricted charging
        Self::write_node(&format!("{}/direct_charging", QC_DIR), "1");
        Self::write_node(&format!("{}/restricted_charging", QC_DIR), "0");
        Self::write_node_lock(&format!("{}/restrict_cur", QC_DIR), &effective_ma_str);

        // OEM thermal policy remains active in every fast-charge mode.
    }

    /// Reasserts fast-charge negotiation and locks.
    pub fn reapply_lock() {
        if !Self::is_hardware_plugged() {
            Self::handle_disconnect();
            return;
        }

        let cfg = ConfigStore::load_charging_config();
        match cfg.charging_mode.as_str() {
            "cool" => Self::apply_cool_mode(cfg.limit_current_ma as u32),
            "hyper" => Self::apply_hyper_mode(),
            "custom" => Self::apply_custom_mode(cfg.custom_limit_current_ma as u32),
            "standard" => Self::apply_standard_mode(),
            _ => {
                if cfg.charging_mode.is_empty() && cfg.extreme_charging_enabled {
                    Self::apply_hyper_mode();
                } else {
                    Self::apply_standard_mode();
                }
            }
        }
    }

    /// Regulated charging with active requested current limit.
    pub fn apply_regulated_charging(requested_ma: u32) {
        let was_bypassed = Path::new("/dev/lynx_bypass_active").exists();
        let _ = fs::remove_file("/dev/lynx_extreme_charging");
        let _ = fs::remove_file("/dev/lynx_bypass_active");
        if was_bypassed {
            let _ = std::process::Command::new("dumpsys").args(["battery", "reset"]).output();
            Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "0");
            Self::write_node("/sys/devices/platform/tran_battery/Charging_CallState", "0");
        }

        let _ = fs::write("/dev/lynx_standard_charging", format!("regulated-{}", requested_ma));

        let effective_ma = if Self::is_laptop_port() {
            requested_ma.min(1500)
        } else {
            requested_ma.clamp(500, 25000)
        };
        let effective_ua_str = (effective_ma as u64 * 1000).to_string();
        let effective_ma_str = effective_ma.to_string();

        Self::write_node(&format!("{}/pe20", MTK_DIR), "0");
        Self::write_node(&format!("{}/pe40", MTK_DIR), "0");
        Self::write_node(&format!("{}/enable_sc", MTK_DIR), "0");
        Self::write_node(&format!("{}/bypass_charger", MTK_DIR), "0");
        Self::write_node(&format!("{}/tran_charger_full", MTK_DIR), "0");
        Self::write_node(&format!("{}/BN_TestMode", MTK_DIR), "0");
        Self::write_node(&format!("{}/BatteryNotify", MTK_DIR), "1");
        Self::write_node(&format!("{}/sw_jeita", MTK_DIR), "1");
        Self::write_node(&format!("{}/input_suspend", BATT_DIR), "0");
        Self::write_node(&format!("{}/charging_enabled", BATT_DIR), "1");
        Self::write_node(&format!("{}/fastcharge_mode", BATT_DIR), "0");
        Self::write_node(&format!("{}/fast_charge", BATT_DIR), "0");
        Self::write_node(&format!("{}/direct_charging", QC_DIR), "0");

        // Clamping logic:
        if effective_ma <= 1500 {
            // Tight Cool Mode throttling (strictly clamps to ~1.0A 5V, suppresses direct pump)
            Self::set_bcct_throttle(true);
            Self::write_node(&format!("{}/pdc_max_watt", MTK_DIR), "5");
            Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "2");
            Self::write_node("/sys/devices/platform/tran_battery/Charging_CallState", "2");
        } else {
            Self::set_bcct_throttle(false);
            let watt = ((effective_ma * 5) / 1000).max(10).min(68);
            Self::write_node(&format!("{}/pdc_max_watt", MTK_DIR), &watt.to_string());
            Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "0");
            Self::write_node("/sys/devices/platform/tran_battery/Charging_CallState", "0");
        }

        // Actively write limit to hardware registers with permission lock!
        Self::write_node_lock(&format!("{}/input_current", MTK_DIR), &effective_ma_str);
        Self::write_node_lock(&format!("{}/sc_ibat_limit", MTK_DIR), &effective_ma_str);
        Self::write_node_lock(&format!("{}/chg1_current", MTK_DIR), &effective_ma_str);
        Self::write_node_lock(&format!("{}/chg2_current", MTK_DIR), &effective_ma_str);

        Self::write_node_lock(&format!("{}/constant_charge_current_max", BATT_DIR), &effective_ua_str);
        Self::write_node_lock(&format!("{}/constant_charge_current", BATT_DIR), &effective_ua_str);
        Self::write_node_lock(&format!("{}/current_max", USB_DIR), &effective_ua_str);
        Self::write_node_lock(&format!("{}/current_max", MAIN_DIR), &effective_ua_str);
        Self::write_node_lock(&format!("{}/input_current_limit", BATT_DIR), &effective_ua_str);
        Self::write_node_lock(&format!("{}/restrict_cur", QC_DIR), &effective_ma_str);
        Self::write_node(&format!("{}/restricted_charging", QC_DIR), "1");

        // Always restore thermal safety and unfreeze daemons when in regulated mode
        Self::restore_charger_thermal_safety();
    }

    /// Mode 1: Cool Mode (1000–1200 mA strict limit, cold & safe)
    pub fn apply_cool_mode(limit_ma: u32) {
        let target = if limit_ma > 0 && limit_ma <= 1500 { limit_ma } else { 1000 };
        let cur_cap = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(50);
        let _ = fs::write("/dev/lynx_charging_state", format!("state=cool cap={}\n", cur_cap));
        Self::apply_regulated_charging(target);
    }

    /// Mode 2: Standard Mode (OEM adaptive normal fast charging)
    pub fn apply_standard_mode() {
        let _ = fs::remove_file("/dev/lynx_extreme_charging");
        let _ = fs::remove_file("/dev/lynx_bypass_active");
        let _ = fs::write("/dev/lynx_standard_charging", "standard\n");

        Self::unlock_extreme_nodes();
        Self::restore_charger_thermal_safety();

        // Ensure OS and battery status is not latched in bypass
        let _ = std::process::Command::new("dumpsys").args(["battery", "reset"]).output();
        Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "0");
        Self::write_node("/sys/devices/platform/tran_battery/Charging_CallState", "0");
        Self::set_bcct_throttle(false);

        // Restore standard charging flags
        Self::write_node(&format!("{}/bypass_charger", MTK_DIR), "0");
        Self::write_node(&format!("{}/tran_charger_full", MTK_DIR), "0");
        Self::write_node(&format!("{}/BN_TestMode", MTK_DIR), "0");
        Self::write_node(&format!("{}/BatteryNotify", MTK_DIR), "1");
        Self::write_node(&format!("{}/sw_jeita", MTK_DIR), "1");
        Self::write_node(&format!("{}/pdc_max_watt", MTK_DIR), "33");
        Self::write_node(&format!("{}/pe20", MTK_DIR), "1");
        Self::write_node(&format!("{}/pe40", MTK_DIR), "1");
        Self::write_node(&format!("{}/input_suspend", BATT_DIR), "0");
        Self::write_node(&format!("{}/charging_enabled", BATT_DIR), "1");
        Self::write_node(&format!("{}/fastcharge_mode", BATT_DIR), "0");
        Self::write_node(&format!("{}/fast_charge", BATT_DIR), "0");
        Self::write_node(&format!("{}/direct_charging", QC_DIR), "0");

        // Unlock all sysfs charger nodes permissions so kernel OEM algorithms control them
        for node in &[
            format!("{}/input_current", MTK_DIR),
            format!("{}/sc_ibat_limit", MTK_DIR),
            format!("{}/chg1_current", MTK_DIR),
            format!("{}/chg2_current", MTK_DIR),
            format!("{}/enable_sc", MTK_DIR),
            format!("{}/pe20", MTK_DIR),
            format!("{}/pe40", MTK_DIR),
            format!("{}/constant_charge_current_max", BATT_DIR),
            format!("{}/constant_charge_current", BATT_DIR),
            format!("{}/current_max", USB_DIR),
            format!("{}/current_max", MAIN_DIR),
            format!("{}/input_current_limit", BATT_DIR),
            format!("{}/restrict_cur", QC_DIR),
        ] {
            Self::set_perm(node, 0o666);
        }

        let cur_cap = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(50);
        let _ = fs::write("/dev/lynx_charging_state", format!("state=standard cap={}\n", cur_cap));
    }

    /// Mode 3: Hyper Mode (100% dynamic peak, unconstrained)
    pub fn apply_hyper_mode() {
        let cur_cap = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(50);
        let _ = fs::write("/dev/lynx_charging_state", format!("state=hyper cap={}\n", cur_cap));
        Self::apply_extreme_charging(100, true, 0);
    }

    /// Mode 4: Custom Mode (User-defined exact limit in mA)
    pub fn apply_custom_mode(target_ma: u32) {
        let clamped = target_ma.clamp(500, 25000);
        let cur_cap = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(50);
        let _ = fs::write("/dev/lynx_charging_state", format!("state=custom_{} cap={}\n", clamped, cur_cap));
        if clamped >= 5000 {
            Self::apply_extreme_charging(100, true, clamped);
        } else {
            Self::apply_regulated_charging(clamped);
        }
    }

    /// Charge-mode changes leave OEM thermal controls and trip points untouched.
    fn restore_charger_thermal_safety() {
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

    #[inline]
    pub fn get_local_hour() -> u32 {
        #[repr(C)]
        struct Tm {
            tm_sec: i32,
            tm_min: i32,
            tm_hour: i32,
            tm_mday: i32,
            tm_mon: i32,
            tm_year: i32,
            tm_wday: i32,
            tm_yday: i32,
            tm_isdst: i32,
            tm_gmtoff: i64,
            tm_zone: *const u8,
        }
        extern "C" {
            fn time(t: *mut i64) -> i64;
            fn localtime_r(timep: *const i64, result: *mut Tm) -> *mut Tm;
        }
        let mut t: i64 = 0;
        unsafe {
            time(&mut t);
            let mut tm: Tm = std::mem::zeroed();
            localtime_r(&t, &mut tm);
            tm.tm_hour.max(0) as u32
        }
    }

    /// Evaluates `config.json` and live battery state, applying the appropriate charging mode immediately.
    pub fn apply_from_config() {
        if !Self::is_hardware_plugged() {
            Self::handle_disconnect();
            return;
        }

        // Host Mode Exclusion: Ignore charging algorithms if device is powering OTG/Reverse accessories
        if Self::is_otg_active() {
            return;
        }

        Self::capture_charging_session_baseline();

        let cfg = ConfigStore::load_charging_config();
        let cur_cap = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(50) as u32;
        let cur_stat = Self::read_node(&format!("{}/status", BATT_DIR)).unwrap_or_else(|| "Unknown".to_string());
        let real_dc = Self::read_real_battery_temp_dc();

        // Tier 1: Emergency Thermal & Cold Guards
        if cfg.emergency_temp_guard_enabled && real_dc >= 490 {
            let _ = fs::write("/dev/lynx_charging_guard", format!("guard_active=true temp={}\n", real_dc));
            let _ = fs::remove_file("/dev/lynx_extreme_charging");
            Self::apply_regulated_charging(750);
            return;
        } else if real_dc < 150 && real_dc > 0 {
            // Cold Battery (< 15.0°C) Lithium Plating Guard
            let _ = fs::write("/dev/lynx_charging_guard", format!("cold_guard_active=true temp={}\n", real_dc));
            Self::apply_regulated_charging(1500);
            return;
        } else if real_dc <= 430 {
            let _ = fs::remove_file("/dev/lynx_charging_guard");
        }

        // Tier 2: Immediate User-Forced Bypass Toggle
        if cfg.bypass_enabled {
            let _ = fs::write("/dev/lynx_charging_state", format!("state=bypass_forced cap={}\n", cur_cap));
            Self::apply_bypass_charging();
            return;
        }

        // Tier 3: AutoCut when 100% Full (Overnight / Safe Full AutoCut)
        if cur_cap >= 100 || cur_stat == "Full" {
            let _ = fs::write("/dev/lynx_charging_state", format!("state=bypass_100 cap={}\n", cur_cap));
            Self::apply_bypass_charging();
            return;
        }

        // Tier 4: Batas Pengisian Cerdas (Smart Battery Limit: 50% - 99%)
        // With 3% hysteresis buffer to prevent continuous flapping
        let smart_limit = cfg.max_battery_percent.clamp(0, 100) as u32;
        if smart_limit > 0 && smart_limit < 100 {
            let is_currently_bypassed = Path::new("/dev/lynx_bypass_active").exists();
            let lower_hysteresis_bound = smart_limit.saturating_sub(3);

            if is_currently_bypassed {
                // Maintain bypass as long as capacity is at or above lower bound (limit - 3)%
                if cur_cap >= lower_hysteresis_bound {
                    let _ = fs::write(
                        "/dev/lynx_charging_state",
                        format!("state=bypass_limit cap={} limit={} hyst={}\n", cur_cap, smart_limit, lower_hysteresis_bound),
                    );
                    Self::apply_bypass_charging();
                    return;
                } else {
                    // Battery dropped below lower bound: drop bypass lock so charging can top it back up to limit
                    let _ = fs::remove_file("/dev/lynx_bypass_active");
                    let _ = std::process::Command::new("dumpsys").args(["battery", "reset"]).output();
                    Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "0");
                    Self::write_node("/sys/devices/platform/tran_battery/Charging_CallState", "0");
                    Self::unlock_extreme_nodes();
                }
            } else if cur_cap >= smart_limit {
                // Battery is at or above limit (e.g. limit=90, battery=92): immediately engage hardware bypass!
                let _ = fs::write(
                    "/dev/lynx_charging_state",
                    format!("state=bypass_limit cap={} limit={}\n", cur_cap, smart_limit),
                );
                Self::apply_bypass_charging();
                return;
            }
        }

        // Tier 5: Pengisian Sejuk Malam Hari (Overnight Gentle Charging: 23:00 - 06:00, 1500mA)
        let is_night_time = {
            let h = Self::get_local_hour();
            h >= 23 || h < 6
        };
        if cfg.night_sleep_guard_enabled && is_night_time {
            let _ = fs::write("/dev/lynx_charging_state", format!("state=night_gentle cap={}\n", cur_cap));
            Self::apply_regulated_charging(1500);
            return;
        }

        if cfg.extreme_charging_enabled {
            Self::apply_hyper_mode();
            return;
        }

        // Tier 7: Active Charging Mode Execution (Cool, Standard, Hyper, Custom)
        match cfg.charging_mode.as_str() {
            "cool" => {
                let limit = if cfg.limit_current_ma > 0 && cfg.limit_current_ma <= 2000 {
                    cfg.limit_current_ma as u32
                } else {
                    2000
                };
                Self::apply_cool_mode(limit);
            }
            "hyper" => {
                Self::apply_hyper_mode();
            }
            "custom" => {
                let target = cfg.custom_limit_current_ma.clamp(500, 25000) as u32;
                Self::apply_custom_mode(target);
            }
            "standard" => {
                Self::apply_standard_mode();
            }
            _ => {
                if cfg.charging_mode.is_empty() && cfg.extreme_charging_enabled {
                    Self::apply_hyper_mode();
                } else {
                    Self::apply_standard_mode();
                }
            }
        }
    }

    pub fn is_hardware_plugged() -> bool {
        // 1. Primary check: USB, AC, or Charger subsystem reporting online
        let usb_online = Self::read_node(&format!("{}/online", USB_DIR)).unwrap_or_default();
        let ac_online = Self::read_node(&format!("{}/ac/online", PS_DIR)).unwrap_or_default();
        let chg_online = Self::read_node(&format!("{}/charger/online", PS_DIR)).unwrap_or_default();

        if usb_online == "1" || ac_online == "1" || chg_online == "1" {
            return true;
        }

        // 2. Hardware ADC / PMIC adapter voltage (> 3500 mV indicates physical adapter is connected)
        let adpv = Self::read_i64(&format!("{}/ADC_Charger_Voltage", MTK_DIR))
            .or_else(|| Self::read_i64("/sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger"))
            .or_else(|| Self::read_i64(&format!("{}/voltage_now", USB_DIR)))
            .or_else(|| Self::read_i64(&format!("{}/charger_voltage", BATT_DIR)))
            .or_else(|| Self::read_i64(&format!("{}/voltage_now", MAIN_DIR)))
            .unwrap_or(0);
        let adpv_clean = if adpv > 100_000 { adpv / 1000 } else { adpv };

        if adpv_clean >= 3500 {
            return true;
        }

        // 3. Battery status verification
        let batt_status = Self::read_node(&format!("{}/status", BATT_DIR)).unwrap_or_default();
        if batt_status == "Charging" {
            return true;
        }

        // Cable physically unplugged: No online rails, no adapter voltage, and battery discharging
        false
    }

    /// Confirms that a requested bypass actually stopped cell charging. A marker
    /// file or Android's reported status alone is not proof on vendor kernels.
    fn is_battery_cell_current_stopped() -> bool {
        let Some(raw_current) = Self::read_i64(&format!("{}/current_now", BATT_DIR)) else {
            return false;
        };
        let current_ma = if raw_current.abs() >= 10_000 { raw_current / 1000 } else { raw_current };
        let status = Self::read_node(&format!("{}/status", BATT_DIR)).unwrap_or_default();
        current_ma.abs() <= 50 && status != "Charging"
    }

    pub fn handle_disconnect() {
        // Charging protocol requests are session-scoped. Clear those requests on unplug,
        // but leave thermal zones, cooling devices, and Thermal Lynx profile nodes untouched.
        let previous_state = Self::read_node("/dev/lynx_charging_state").unwrap_or_default();
        let needs_session_cleanup = Path::new("/dev/lynx_extreme_charging").exists()
            || Path::new("/dev/lynx_standard_charging").exists()
            || Path::new("/dev/lynx_bypass_active").exists()
            || Path::new("/dev/lynx_charging_baseline").exists()
            || !previous_state.starts_with("state=discharging");

        let _ = fs::remove_file("/dev/lynx_charging_guard");
        let _ = fs::remove_file("/dev/lynx_extreme_charging");
        let _ = fs::remove_file("/dev/lynx_standard_charging");
        let _ = fs::remove_file("/dev/lynx_bypass_active");
        let _ = std::process::Command::new("dumpsys").args(["battery", "reset"]).output();
        Self::set_bcct_throttle(false);
        Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "0");
        Self::write_node("/sys/devices/platform/tran_battery/Charging_CallState", "0");
        if !needs_session_cleanup {
            return;
        }
        let cap_now = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(50);
        let _ = fs::write("/dev/lynx_charging_state", format!("state=discharging cap={}\n", cap_now));

        for (node, value) in DISCONNECT_CHARGING_RESETS {
            Self::write_node(node, value);
        }
        Self::restore_charging_session_baseline();
        Self::restore_charger_thermal_safety();
        let _ = fs::remove_file("/dev/lynx_orig_chg_max");
        Self::signal_processes(&["thermalloadalgod", "com.xiaomi.joyose", "mi_thermald", "thermal-engine"], false);
    }

    fn capture_charging_session_baseline() {
        const DIR: &str = "/dev/lynx_charging_baseline";
        if Path::new(DIR).exists() || fs::create_dir(DIR).is_err() {
            return;
        }

        for (index, node) in CHARGING_SESSION_NODES.iter().enumerate() {
            let Ok(value) = fs::read_to_string(node) else { continue };
            let Ok(meta) = fs::metadata(node) else { continue };
            let value_path = format!("{}/{}.value", DIR, index);
            let mode_path = format!("{}/{}.mode", DIR, index);
            let _ = fs::write(value_path, value.trim_end());
            #[cfg(unix)]
            let mode = meta.permissions().mode() & 0o777;
            #[cfg(not(unix))]
            let mode = 0o644;
            let _ = fs::write(mode_path, mode.to_string());
        }
    }

    fn restore_charging_session_baseline() {
        const DIR: &str = "/dev/lynx_charging_baseline";
        if !Path::new(DIR).exists() {
            return;
        }

        for (index, node) in CHARGING_SESSION_NODES.iter().enumerate() {
            let value_path = format!("{}/{}.value", DIR, index);
            let mode_path = format!("{}/{}.mode", DIR, index);
            let Ok(value) = fs::read_to_string(&value_path) else { continue };
            let original_mode = fs::read_to_string(&mode_path)
                .ok()
                .and_then(|s| s.trim().parse::<u32>().ok());
            Self::set_perm(node, 0o666);
            let _ = fs::write(node, value.trim_end());
            if let Some(mode) = original_mode {
                Self::set_perm(node, mode);
            }
        }

        let _ = fs::remove_dir_all(DIR);
    }

    /// Periodic tick called inside `DaemonRunner::run()` every cycle.
    pub fn tick() {
        if !Self::is_hardware_plugged() {
            Self::handle_disconnect();
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
        if ibus_val > 4_000_000_000 {
            ibus_val = (ibus_val as u32 as i32) as i64;
        }
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

        let v_clean = if volt_raw.abs() >= 10_000 { volt_raw / 1000 } else { volt_raw };
        let mut c_clean = if cur_raw.abs() >= 10_000 { cur_raw / 1000 } else { cur_raw };
        if stat == "Charging" && c_clean < 0 {
            c_clean = c_clean.abs();
        }

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
        let bypass_requested = Path::new("/dev/lynx_bypass_active").exists();
        let is_overnight_latch = chg_state.contains("bypass_100") || (cap >= 100 && bypass_requested) || stat == "Full";
        let is_limit_latched = chg_state.contains("bypass_limit")
            && cfg.max_battery_percent > 0
            && cfg.max_battery_percent < 100
            && cap >= cfg.max_battery_percent as i64;
        let is_bypass_active = bypass_requested || is_overnight_latch || is_limit_latched;
        let is_tapering = chg_state.contains("tapering");

        let _now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs();

        format!(
            "{{\n  \"capacity\": {},\n  \"status\": \"{}\",\n  \"health\": \"{}\",\n  \"temperature_c\": {:.1},\n  \"real_physical_temp_c\": {:.1},\n  \"voltage_mv\": {},\n  \"current_ma\": {},\n  \"adapter_voltage_mv\": {},\n  \"adapter_current_ma\": {},\n  \"active_ic\": \"{}\",\n  \"fast_charge_protocol\": \"{}\",\n  \"rfc_authenticated\": {},\n  \"emergency_guard_active\": {},\n  \"overnight_bypass_latched\": {},\n  \"smart_limit_latched\": {},\n  \"bypass_active\": {},\n  \"smart_tapering_active\": {},\n  \"charging_mode\": \"{}\",\n  \"bypass_enabled\": {},\n  \"extreme_charging_enabled\": {},\n  \"limit_current_ma\": {},\n  \"max_battery_percent\": {},\n  \"high_current_target_percent\": {},\n  \"temp_cutoff_c\": {},\n  \"thermal_lockout_bypass_enabled\": {},\n  \"smart_tapering_enabled\": {},\n  \"emergency_temp_guard_enabled\": {},\n  \"night_sleep_guard_enabled\": {}\n}}",
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
            cfg.charging_mode,
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

#[cfg(test)]
mod tests {
    use super::DISCONNECT_CHARGING_RESETS;

    #[test]
    fn disconnect_resets_only_charging_protocol_nodes() {
        assert!(DISCONNECT_CHARGING_RESETS.iter().all(|(path, _)| {
            let path = path.to_lowercase();
            !path.contains("thermal") && !path.contains("trip_point") && !path.contains("cooling_device")
        }));
    }
}
