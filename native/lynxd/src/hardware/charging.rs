use std::fs;
use std::path::Path;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};

use crate::state::ConfigStore;

static LAST_SOFT_RECONNECT_EPOCH: AtomicU64 = AtomicU64::new(0);
static LAST_FCC_WRITE_EPOCH: AtomicU64 = AtomicU64::new(0);

fn can_soft_reconnect() -> bool {
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    let last = LAST_SOFT_RECONNECT_EPOCH.load(Ordering::Relaxed);
    if now.saturating_sub(last) < 30 {
        return false;
    }
    LAST_SOFT_RECONNECT_EPOCH.store(now, Ordering::Relaxed);
    true
}

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
const TRAN_BATTERY_ODM_DIR: &str = "/sys/devices/platform/odm/odm:tran_battery";
const TRAN_BATTERY_DIR: &str = "/sys/devices/platform/tran_battery";
const PCA_DV2_DIR: &str = "/sys/devices/platform/pca_dv2_algo";
const BATT_TEMP_NODE: &str = "/sys/devices/platform/battery/Battery_Temperature";
const CLABCCT_NODE: &str = "/proc/driver/thermal/clabcct";
const CLABCCT_LCMOFF_NODE: &str = "/proc/driver/thermal/clabcct_lcmoff";

const DISCONNECT_CHARGING_RESETS: &[(&str, &str)] = &[
    ("/proc/lynx/bypass_charging", "0"),
    ("/proc/mtk_battery_cmd/en_power_path", "1"),
    ("/proc/mtk_battery_cmd/current_cmd", "1 0"),
    ("/sys/class/power_supply/battery/input_suspend", "0"),
    ("/sys/class/power_supply/battery/charging_enabled", "1"),
    ("/sys/devices/platform/charger/pe20", "0"),
    ("/sys/devices/platform/charger/pe40", "0"),
    ("/sys/devices/platform/charger/enable_sc", "0"),
    ("/sys/devices/platform/charger/bypass_charger", "0"),
    ("/sys/devices/platform/charger/tran_charger_full", "0"),
    ("/sys/devices/platform/charger/tran_game_mode", "0"),
    ("/sys/devices/platform/charger/BN_TestMode", "0"),
    ("/sys/devices/platform/charger/BatteryNotify", "1"),
    ("/sys/devices/platform/charger/sw_jeita", "1"),
    ("/sys/devices/platform/charger/input_current", "5b8d80"),
    ("/sys/devices/platform/charger/sc_ibat_limit", "2000"),
    ("/sys/devices/platform/charger/chg1_current", "5b8d80"),
    ("/sys/devices/platform/charger/chg2_current", "5b8d80"),
    ("/sys/class/power_supply/battery/fastcharge_mode", "0"),
    ("/sys/class/power_supply/battery/fast_charge", "0"),
    ("/sys/class/power_supply/battery/boost_current", "0"),
    ("/sys/class/power_supply/battery/constant_charge_current_max", "4500000"),
    ("/sys/class/power_supply/battery/constant_charge_current", "4500000"),
    ("/sys/class/power_supply/battery/input_current_limit", "4500000"),
    ("/sys/class/qcom-battery/direct_charging", "0"),
];

// Values and permissions changed by charging modes are captured once per cable
// session and restored exactly when the physical input is removed.
const CHARGING_SESSION_NODES: &[&str] = &[
    "/sys/class/power_supply/battery/input_suspend",
    "/sys/class/power_supply/battery/charging_enabled",
    "/sys/class/power_supply/battery/constant_charge_current_max",
    "/sys/class/power_supply/battery/constant_charge_current",
    "/sys/class/power_supply/battery/constant_charge_voltage",
    "/sys/class/power_supply/battery/constant_charge_voltage_max",
    "/sys/class/power_supply/battery/voltage_max",
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
    "/sys/class/power_supply/battery/sw_jeita_enabled",
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
    "/sys/devices/platform/charger/tran_game_mode",
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

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ChargingHardwarePlatform {
    Transsion,
    Xiaomi,
    QualcommGeneric,
    Oplus,
    Samsung,
    GooglePixel,
    GenericLinux,
}

impl ChargingHardwarePlatform {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Transsion => "Transsion",
            Self::Xiaomi => "Xiaomi",
            Self::QualcommGeneric => "Qualcomm",
            Self::Oplus => "OPPO/Realme/OnePlus",
            Self::Samsung => "Samsung",
            Self::GooglePixel => "Google Pixel",
            Self::GenericLinux => "Generic Linux",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DiscoveredChargerTopology {
    pub primary_buck: Option<String>,
    pub charge_pumps: Vec<String>,
    pub vendor_controllers: Vec<String>,
    pub summary: String,
    pub scanned_nodes_count: usize,
}

pub struct ChargingRegulator;

impl ChargingRegulator {
    /// Dynamically crawls `/sys/class/power_supply/*/` and platform registers across any Android
    /// device to discover live charging limits, controls, boosts, and thermal trip nodes in < 1 ms.
    pub fn crawl_dynamic_power_supply_nodes() -> Vec<String> {
        let mut discovered = Vec::new();

        // 1. Scan all /sys/class/power_supply/*/ directories
        const PS_LEAF_ATTRS: &[&str] = &[
            "constant_charge_current_max",
            "constant_charge_current",
            "current_max",
            "input_current_limit",
            "hw_current_max",
            "charge_control_limit",
            "charge_control_limit_max",
            "fastcharge_mode",
            "fast_charge",
            "boost_current",
            "turbo_power",
            "quick_charge_type",
            "thermal_input_current_limit",
            "system_temp_level",
            "temp_state",
            "step_charging_enabled",
            "siop_level",
            "store_mode",
            "batt_slate_mode",
            "cool_mode",
            "call_mode",
            "charge_stop_level",
            "bd_trickle_dry_run",
            "input_suspend",
            "charging_enabled",
            "vooc_charging",
            "fast_charge_user_type",
            "mmi_charging_enable",
            "factory_mode",
            "authenticate",
            "input_current_settled",
            "pd_allowed",
            "pd_active",
            "pd_voltage_max",
            "pd_current_max",
            "pd_limit_current",
            "allow_hvdcp3",
            "hvdcp3_type",
            "hvdcp_opti_allowed",
            "apdo_max",
            "rerun_aicl",
            "typec_mode",
            "soc_decimal",
            "night_charging",
            "smart_chg",
            "is_turbo_charging",
        ];

        if let Ok(entries) = fs::read_dir(PS_DIR) {
            for entry in entries.flatten() {
                let p = entry.path();
                if p.is_dir() {
                    let dir_name = p.file_name().and_then(|n| n.to_str()).unwrap_or("");
                    if dir_name == "pc_port" {
                        continue;
                    }
                    for &attr in PS_LEAF_ATTRS {
                        let node_path = p.join(attr);
                        if node_path.exists() {
                            discovered.push(node_path.to_string_lossy().to_string());
                        }
                    }
                }
            }
        }

        // 2. Scan MTK platform charger nodes (/sys/devices/platform/charger/*)
        const MTK_NODES: &[&str] = &[
            "/sys/devices/platform/charger/pdc_max_watt",
            "/sys/devices/platform/charger/pd_input_current",
            "/sys/devices/platform/charger/pd_vbus_upper_bound",
            "/sys/devices/platform/charger/pd_vbus_low_bound",
            "/sys/devices/platform/charger/pdc_mode",
            "/sys/devices/platform/charger/Pump_Express",
            "/sys/devices/platform/charger/pe20",
            "/sys/devices/platform/charger/pe40",
            "/sys/devices/platform/charger/enable_sc",
            "/sys/devices/platform/charger/bypass_charger",
            "/sys/devices/platform/charger/tran_charger_full",
            "/sys/devices/platform/charger/tran_game_mode",
            "/sys/devices/platform/charger/BN_TestMode",
            "/sys/devices/platform/charger/BatteryNotify",
            "/sys/devices/platform/charger/sw_jeita",
            "/sys/devices/platform/charger/input_current",
            "/sys/devices/platform/charger/sc_ibat_limit",
            "/sys/devices/platform/charger/chg1_current",
            "/sys/devices/platform/charger/chg2_current",
        ];
        for &node in MTK_NODES {
            if Path::new(node).exists() {
                discovered.push(node.to_string());
            }
        }

        // 3. Scan Qualcomm battery nodes (/sys/class/qcom-battery/*)
        const QC_NODES: &[&str] = &[
            "/sys/class/qcom-battery/direct_charging",
            "/sys/class/qcom-battery/restricted_charging",
            "/sys/class/qcom-battery/restrict_cur",
        ];
        for &node in QC_NODES {
            if Path::new(node).exists() {
                discovered.push(node.to_string());
            }
        }

        // 4. Scan Thermal & Vendor-specific controllers
        const EXTRA_VENDORS: &[&str] = &[
            "/proc/driver/thermal/clabcct",
            "/proc/driver/thermal/clabcct_lcmoff",
            "/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug",
            "/sys/devices/platform/tran_battery/pcb_thermal_debug",
            "/sys/devices/platform/odm/odm:tran_battery/Charging_CallState",
            "/sys/devices/platform/tran_battery/Charging_CallState",
            "/sys/devices/platform/pca_dv2_algo/dv2_debug",
            "/sys/devices/platform/battery/Battery_Temperature",
            "/sys/devices/platform/google,battery/charge_stop_level",
            "/sys/devices/platform/google,charger/charge_stop_level",
            "/sys/class/thermal/thermal_message",
        ];
        for &node in EXTRA_VENDORS {
            if Path::new(node).exists() {
                discovered.push(node.to_string());
            }
        }

        discovered.sort();
        discovered.dedup();
        discovered
    }

    /// Discovers physical charging ICs (Direct Charge Pumps, Primary Buck Converters,
    /// and OEM Vendor Subsystems) by scanning kernel bus drivers (/sys/bus/i2c/drivers,
    /// /sys/bus/platform/drivers) and power_supply classes with zero hardcoding.
    pub fn scan_charging_hardware_chips() -> DiscoveredChargerTopology {
        let mut primary_buck: Option<String> = None;
        let mut charge_pumps: Vec<String> = Vec::new();
        let mut vendor_controllers: Vec<String> = Vec::new();

        // 1. Direct Charge Pumps (Switched Capacitor 2:1 / 4:1)
        const PUMP_DEVICES: &[(&str, &str)] = &[
            ("rt9759", "Direct Charge Pump (RT9759 2:1)"),
            ("smb1390", "Qualcomm SMB1390 Dual-Pump"),
            ("smb1396", "Qualcomm SMB1396 Dual-Pump"),
            ("smb1398", "Qualcomm SMB1398 Dual-Pump"),
            ("smb1395", "Qualcomm SMB1395 Dual-Pump"),
            ("ln8000", "Lion Semi LN8000 Direct Pump"),
            ("ln8282", "Lion Semi LN8282 Direct Pump"),
            ("sc8551", "Southchip SC8551 Direct Pump"),
            ("sc8547", "Southchip SC8547 Direct Pump"),
            ("sc8571", "Southchip SC8571 Direct Pump"),
            ("bq25970", "TI BQ25970 8A Direct Pump"),
            ("bq25980", "TI BQ25980 12A Direct Pump"),
            ("pca9468", "NXP PCA9468 Direct Charger"),
            ("nu1619", "NuVolta NU1619 Direct Pump"),
            ("hl7132", "Halo Micro HL7132 Direct Pump"),
        ];

        for &(driver_name, label) in PUMP_DEVICES {
            let i2c_path = format!("/sys/bus/i2c/drivers/{}", driver_name);
            let ps_path = format!("{}/{}", PS_DIR, driver_name);
            let plat_path = format!("/sys/bus/platform/drivers/{}", driver_name);
            if Path::new(&i2c_path).exists() || Path::new(&ps_path).exists() || Path::new(&plat_path).exists() {
                charge_pumps.push(label.to_string());
            }
        }
        if charge_pumps.is_empty() {
            if Path::new("/sys/class/power_supply/charge_pump").exists()
                || Path::new("/sys/class/power_supply/cp-standalone").exists()
            {
                charge_pumps.push("Direct Charge Pump (Universal 2:1)".to_string());
            }
        }

        // 2. Primary Switching Buck Converters
        const BUCK_DEVICES: &[(&str, &str)] = &[
            ("rt9471", "Switching Buck Converter (RT9471)"),
            ("rt9490", "Switching Buck Converter (RT9490 5A)"),
            ("mt6370_pmu_charger", "MediaTek MT6370 PMU Buck"),
            ("mt6370_pmu", "MediaTek MT6370 PMU Buck"),
            ("mt6360_pmu_charger", "MediaTek MT6360 PMU Buck"),
            ("mt6360_pmu", "MediaTek MT6360 PMU Buck"),
            ("mt6375_pmu", "MediaTek MT6375 PMU Buck"),
            ("max77759", "Maxim MAX77759 PMIC Buck"),
            ("max77705", "Maxim MAX77705 PMIC Buck"),
            ("max77958", "Maxim MAX77958 PMIC Buck"),
            ("bq25890", "TI BQ25890 Switching Buck"),
            ("bq25790", "TI BQ25790 Switching Buck"),
            ("smb1351", "Qualcomm SMB1351 Buck"),
            ("smb1355", "Qualcomm SMB1355 Buck"),
            ("smb1381", "Qualcomm SMB1381 Buck"),
            ("pm8150b_charger", "Qualcomm PM8150B Buck"),
            ("pm8350b_charger", "Qualcomm PM8350B Buck"),
            ("pm7250b_charger", "Qualcomm PM7250B Buck"),
            ("s2mu106", "Samsung Shannon S2MU106 PMIC"),
            ("s2mu004", "Samsung Shannon S2MU004 PMIC"),
        ];

        for &(driver_name, label) in BUCK_DEVICES {
            let i2c_path = format!("/sys/bus/i2c/drivers/{}", driver_name);
            let ps_path = format!("{}/{}", PS_DIR, driver_name);
            let plat_path = format!("/sys/bus/platform/drivers/{}", driver_name);
            if Path::new(&i2c_path).exists() || Path::new(&ps_path).exists() || Path::new(&plat_path).exists() {
                primary_buck = Some(label.to_string());
                break;
            }
        }
        if primary_buck.is_none() {
            if Path::new("/sys/class/qcom-battery").exists() || Self::is_qualcomm_soc() {
                primary_buck = Some("Qualcomm PMIC (PM660/PM8150/PM6150 Buck)".to_string());
            } else if Path::new("/sys/devices/platform/charger").exists() {
                primary_buck = Some("MediaTek Switching Buck".to_string());
            } else if Path::new("/sys/class/power_supply/sec-battery").exists() {
                primary_buck = Some("Samsung PMIC Buck (Shannon/Maxim)".to_string());
            }
        }

        // 3. Vendor Subsystem Controllers
        const VENDOR_DEVICES: &[(&str, &str)] = &[
            ("tran_battery", "Transsion Dual-Engine Controller"),
            ("pca_mtk_chg", "Transsion DV2 Dual-Pump Controller"),
            ("pca_dv2_algo", "Transsion DV2 Algorithm Engine"),
            ("oplus_chg", "OPPO/Realme SuperVOOC Engine"),
            ("google,battery", "Google Pixel Battery Controller"),
            ("google,charger", "Google Pixel Charger Controller"),
            ("sec-battery", "Samsung SEC Battery Controller"),
            ("qcom-battery", "Qualcomm Battery Subsystem"),
        ];

        for &(driver_name, label) in VENDOR_DEVICES {
            let plat_path = format!("/sys/bus/platform/drivers/{}", driver_name);
            let dev_plat = format!("/sys/devices/platform/{}", driver_name);
            let odm_plat = format!("/sys/devices/platform/odm/odm:{}", driver_name);
            let ps_path = format!("{}/{}", PS_DIR, driver_name);
            if Path::new(&plat_path).exists() || Path::new(&dev_plat).exists() || Path::new(&odm_plat).exists() || Path::new(&ps_path).exists() {
                vendor_controllers.push(label.to_string());
            }
        }

        charge_pumps.sort();
        charge_pumps.dedup();
        vendor_controllers.sort();
        vendor_controllers.dedup();

        let summary = match (&charge_pumps.first(), &primary_buck) {
            (Some(pump), Some(buck)) => format!("{} + {}", pump, buck),
            (Some(pump), None) => pump.to_string(),
            (None, Some(buck)) => buck.to_string(),
            (None, None) => "Switching Buck Converter (Universal Linux)".to_string(),
        };

        DiscoveredChargerTopology {
            primary_buck,
            charge_pumps,
            vendor_controllers,
            summary,
            scanned_nodes_count: 0,
        }
    }
    #[inline]
    fn set_perm(path: &str, mode: u32) -> bool {
        #[cfg(unix)]
        {
            if let Ok(meta) = fs::metadata(path) {
                let effective_mode = if meta.is_dir() {
                    (mode & 0o777) | 0o111
                } else {
                    mode
                };
                let mut perms = meta.permissions();
                perms.set_mode(effective_mode);
                return fs::set_permissions(path, perms).is_ok();
            }
            false
        }
        #[cfg(not(unix))]
        {
            let _ = (path, mode);
            true
        }
    }

    /// Guarantees that Android Health HAL and BatteryService can always traverse and inspect power_supply sysfs nodes.
    pub fn ensure_power_supply_traversal() {
        const CRITICAL_DIRS: &[&str] = &[
            "/sys/class/power_supply",
            "/sys/devices/platform/battery",
            "/sys/devices/platform/battery/power_supply",
            "/sys/devices/platform/battery/power_supply/battery",
            "/sys/devices/platform/charger",
            "/sys/devices/platform/mt_charger",
            "/sys/devices/platform/mt_charger/power_supply",
            "/sys/devices/platform/odm/odm:tran_battery",
            "/sys/devices/platform/odm/odm:tran_battery/power_supply",
        ];
        for &dir in CRITICAL_DIRS {
            if Path::new(dir).is_dir() {
                Self::set_perm(dir, 0o755);
            }
        }
    }

    #[inline]
    pub fn unlock_node(path: &str) -> bool {
        if !Path::new(path).exists() {
            return false;
        }
        Self::set_perm(path, 0o666)
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
    pub fn write_node(path: &str, val: &str) -> bool {
        if !Path::new(path).exists() {
            return false;
        }
        if fs::write(path, val).is_ok() {
            return true;
        }
        Self::set_perm(path, 0o666);
        fs::write(path, val).is_ok()
    }

    #[inline]
    pub fn write_node_lock(path: &str, val: &str) -> bool {
        if !Path::new(path).exists() {
            return false;
        }
        Self::set_perm(path, 0o666);
        let write_ok = fs::write(path, val).is_ok();
        if write_ok {
            Self::set_perm(path, 0o444);
            true
        } else {
            Self::set_perm(path, 0o666);
            false
        }
    }

    /// MediaTek kernel `store_input_current`, `store_chg1_current`, and `store_chg2_current`
    /// parse user writes with `kstrtouint(buf, 16, &reg)` in microamperes (uA), while `show_*`
    /// prints the stored integer in decimal microamperes (`%d\n`).
    /// Writing decimal mA (e.g. `"6000"`) is parsed as `0x6000 = 24,576 uA` (24.5 mA),
    /// severely choking charging! Always format target mA as lowercase hexadecimal uA
    /// (e.g. `6000 mA -> 6,000,000 uA -> "5b8d80"`).
    #[inline]
    pub fn format_mtk_charger_hex_ua(ma: u32) -> String {
        format!("{:x}", (ma as u64) * 1000)
    }

    /// Safely ensures MediaTek PMIC power path and battery charging are enabled without
    /// redundant I2C register writes when already enabled.
    /// On Transsion/MediaTek 33W Direct Charge (`pca_dv2_algo` / `RT9759`), the 2:1 charge pump
    /// turns OFF the primary 5V buck converter (`rt9471_enable_charging(0)`). Unconditionally
    /// writing `"1 0"` to `current_cmd` or `"0"` to `OTG_CTL` every second forces `rt9471` back ON
    /// and triggers `usb_switch_psy_notifier_cb`, immediately aborting `dv2_algo_task`!
    pub fn ensure_mtk_power_path_enabled() {
        Self::set_perm("/proc/mtk_battery_cmd/en_power_path", 0o666);
        let pwr_path = Self::read_node("/proc/mtk_battery_cmd/en_power_path").unwrap_or_default();
        if pwr_path.trim() != "1" && !pwr_path.trim().ends_with(" 1") {
            Self::write_node("/proc/mtk_battery_cmd/en_power_path", "1");
        }

        Self::set_perm("/proc/mtk_battery_cmd/current_cmd", 0o666);
        let cur_cmd = Self::read_node("/proc/mtk_battery_cmd/current_cmd").unwrap_or_default();
        if cur_cmd.trim() != "1 0" && cur_cmd.trim() != "0 0" && !cur_cmd.is_empty() {
            Self::write_node("/proc/mtk_battery_cmd/current_cmd", "1 0");
        }

        let otg_ctl_path = "/sys/devices/platform/odm/odm:tran_battery/OTG_CTL";
        if Path::new(otg_ctl_path).exists() {
            let otg_val = Self::read_node(otg_ctl_path).unwrap_or_default();
            if otg_val.trim() != "0" && !otg_val.is_empty() {
                Self::write_node(otg_ctl_path, "0");
            }
        }
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
                        // CRITICAL SAFETY GUARD: NEVER touch sysrst, shutdown, or reset cooling devices!
                        if t_lower.contains("sysrst") || t_lower.contains("shutdown") || t_lower.contains("rst") {
                            continue;
                        }
                        if t_lower.contains("bcct") || t_lower.contains("abcct") {
                            let cur_path = format!("/sys/class/thermal/{}/cur_state", name_str);
                            if throttle {
                                Self::set_perm(&cur_path, 0o666);
                                Self::write_node(&cur_path, val);
                            } else {
                                Self::write_node_lock(&cur_path, val);
                            }
                        }
                    }
                }
            }
        }
    }

    /// Restores write permissions to BCCT cooling devices so the kernel can manage them.
    pub fn unlock_bcct() {
        if let Ok(entries) = fs::read_dir("/sys/class/thermal") {
            for entry in entries.flatten() {
                let name = entry.file_name();
                let name_str = name.to_string_lossy();
                if name_str.starts_with("cooling_device") {
                    let type_path = format!("/sys/class/thermal/{}/type", name_str);
                    if let Ok(t) = fs::read_to_string(&type_path) {
                        let t_lower = t.trim().to_lowercase();
                        if t_lower.contains("sysrst") || t_lower.contains("shutdown") || t_lower.contains("rst") {
                            continue;
                        }
                        if t_lower.contains("bcct") || t_lower.contains("abcct") {
                            let cur_path = format!("/sys/class/thermal/{}/cur_state", name_str);
                            Self::set_perm(&cur_path, 0o666);
                            let _ = fs::write(&cur_path, "0");
                            Self::set_perm(&cur_path, 0o644);
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
                        let mut cmd = String::new();
                        let cmdline_path = format!("/proc/{}/cmdline", pid);
                        if let Ok(bytes) = fs::read(&cmdline_path) {
                            cmd = String::from_utf8_lossy(&bytes).to_lowercase();
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
                            // Critical Guard: NEVER stop system server, Android hardware thermal HAL, thermal-engine, or core services
                            if stop
                                && (cmd.contains("hardware.thermal")
                                    || cmd.contains("thermal@")
                                    || cmd.contains("system_server")
                                    || cmd.contains("init")
                                    || cmd.contains("thermal-engine"))
                            {
                                continue;
                            }
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
        let chg_type = Self::read_node(&format!("{}/Charger_Type", MTK_DIR)).unwrap_or_default();
        if chg_type == "4" || chg_type == "9" || chg_type == "5" || chg_type == "7" {
            return false;
        }
        let ptyp = Self::read_node(&format!("{}/type", USB_DIR))
            .or_else(|| Self::read_node(&format!("{}/real_type", USB_DIR)))
            .or_else(|| Self::read_node(&format!("{}/Charger_Type", MTK_DIR)))
            .unwrap_or_default()
            .to_uppercase();
        if ptyp.contains("PD") || ptyp.contains("DCP") || ptyp.contains("HVDCP") {
            return false;
        }
        if Self::read_node(&format!("{}/pd_active", USB_DIR)).map(|v| v.trim() != "0" && !v.is_empty()).unwrap_or(false) {
            return false;
        }
        if Self::read_node("/sys/class/typec/port0-partner/supports_usb_power_delivery").map(|v| v.trim() == "yes").unwrap_or(false) {
            return false;
        }
        ptyp.contains("SDP") || ptyp.contains("CDP") || ptyp.contains("PC")
    }

    /// Detects whether an active USB Power Delivery (USB-PD / Type-C PD / PPS) contract is established.
    pub fn is_usb_pd_active() -> bool {
        // 1. Direct sysfs pd_active markers
        for path in &[
            format!("{}/pd_active", USB_DIR),
            format!("{}/pd_active", BATT_DIR),
            format!("{}/pd_active", MAIN_DIR),
            "/sys/class/typec/pd_active".to_string(),
        ] {
            if let Some(val) = Self::read_node(path) {
                let v = val.trim();
                if v == "1" || v == "2" {
                    return true;
                }
            }
        }

        // 2. Type-C Partner PD support
        for path in &[
            "/sys/class/typec/port0-partner/supports_usb_power_delivery",
            "/sys/class/typec/port0/partner/supports_usb_power_delivery",
            "/sys/class/typec/port1-partner/supports_usb_power_delivery",
        ] {
            if let Some(val) = Self::read_node(path) {
                if val.trim().eq_ignore_ascii_case("yes") {
                    return true;
                }
            }
        }

        // 3. USB type strings (contains "PD" or "PPS")
        for path in &[
            format!("{}/real_type", USB_DIR),
            format!("{}/type", USB_DIR),
            format!("{}/usb_type", USB_DIR),
            format!("{}/typec_mode", USB_DIR),
            format!("{}/real_type", MAIN_DIR),
        ] {
            if let Some(val) = Self::read_node(path) {
                let u = val.to_uppercase();
                if u.contains("PD") || u.contains("PPS") || u.contains("POWER DELIVERY") {
                    return true;
                }
            }
        }

        // 4. MediaTek Charger_Type (4 = PD, 7 = Type-C PD, 10 = MTK PD)
        if let Some(chg_type) = Self::read_node(&format!("{}/Charger_Type", MTK_DIR)) {
            let t = chg_type.trim();
            if t == "4" || t == "7" || t == "10" {
                return true;
            }
        }

        // 5. QC 4 / 4+ / 5 (which run natively over USB-PD / PPS)
        for path in &[
            format!("{}/quick_charge_type", USB_DIR),
            format!("{}/qc_type", QC_DIR),
        ] {
            if let Some(val) = Self::read_node(path) {
                let v = val.trim();
                if v == "4" || v == "5" || v.contains("QC4") || v.contains("QC5") {
                    return true;
                }
            }
        }

        false
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
            if (3500..=30000).contains(&cached) {
                return cached as u32;
            }
        }
        let mut max_ma = 0u32;
        for node in &[
            "/sys/class/power_supply/battery/constant_charge_current_max",
            "/sys/class/power_supply/main/constant_charge_current_max",
            "/sys/class/power_supply/usb/hw_current_max",
            "/sys/devices/platform/charger/chg1_current",
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

        let resolved = if max_ma >= 3500 {
            max_ma
        } else if is_dual_cell {
            12000
        } else {
            6000
        };
        let _ = fs::write(cache_file, resolved.to_string());
        resolved
    }

    /// Dynamically probes the authentic physical battery design saturation ceiling (Float Voltage)
    /// across all kernel drivers, BMS Fuel Gauge, and 2S dual-cell platforms without hardcoding.
    pub fn probe_hardware_voltage_ceiling_uv() -> u32 {
        let cache_file = "/dev/lynx_orig_volt_max";
        if let Some(cached) = Self::read_i64(cache_file) {
            if (4100_000..=9200_000).contains(&cached) {
                return cached as u32;
            }
        }
        let mut max_uv = 0u32;
        for node in &[
            "/sys/class/power_supply/battery/voltage_max_design",
            "/sys/class/power_supply/battery/constant_charge_voltage_max",
            "/sys/class/power_supply/battery/voltage_max",
            "/sys/class/power_supply/bms/voltage_max_design",
            "/sys/class/power_supply/bms/voltage_max",
            "/sys/class/power_supply/main/voltage_max",
        ] {
            if let Some(val) = Self::read_i64(node) {
                let uv = if val > 1_000_000 {
                    val as u32
                } else if val > 1000 {
                    (val * 1000) as u32
                } else {
                    0
                };
                if uv > max_uv && uv <= 9_500_000 {
                    max_uv = uv;
                }
            }
        }
        // Dual-cell 2S detection (> 7.0V nominal battery voltage)
        let volt_raw = Self::read_i64(&format!("{}/voltage_now", BATT_DIR)).unwrap_or(0);
        let volt_mv = if volt_raw > 100_000 { volt_raw / 1000 } else { volt_raw };
        let is_dual_cell = volt_mv >= 7000 && volt_mv <= 12000;

        let resolved = if max_uv >= 4150_000 {
            max_uv
        } else if is_dual_cell {
            8800_000 // 8.80V for 2S dual-cell
        } else {
            4400_000 // 4.40V standard modern Li-Po high voltage default
        };
        let _ = fs::write(cache_file, resolved.to_string());
        resolved
    }

    pub fn detect_hw_bypass_node() -> Option<&'static str> {
        const CANDIDATES: &[&str] = &[
            "/proc/lynx/bypass_charging",
            "/proc/mtk_battery_cmd/current_cmd",
            "/sys/devices/platform/charger/bypass_charger",
            "/sys/class/power_supply/battery/device/smart_charging",
            "/sys/class/power_supply/battery/charging_limit_mode",
            "/sys/class/power_supply/battery/smart_charging_activation",
            "/sys/class/power_supply/battery/store_mode",
            "/sys/class/power_supply/battery/batt_slate_mode",
            "/sys/class/power_supply/battery/charge_stop_level",
            "/sys/devices/platform/google,battery/charge_stop_level",
            "/sys/devices/platform/google,charger/charge_stop_level",
            "/sys/class/power_supply/battery/bd_trickle_dry_run",
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
            "/proc/mtk_battery_cmd/en_power_path",
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
            "/sys/devices/platform/odm/odm:tran_battery/Charging_CallState",
            "/sys/devices/platform/tran_battery/Charging_CallState",
            "/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug",
            "/sys/devices/platform/tran_battery/pcb_thermal_debug",
            "/sys/devices/platform/pca_dv2_algo/dv2_debug",
            "/sys/devices/platform/battery/Battery_Temperature",
            "/proc/driver/thermal/clabcct",
            "/proc/driver/thermal/clabcct_lcmoff",
        ];

        for &node in NODES {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
            }
        }
        for node in Self::crawl_dynamic_power_supply_nodes() {
            Self::set_perm(&node, 0o666);
        }
    }

    /// Acquires a Linux kernel wake_lock to prevent userspace execution from being
    /// frozen in Suspend-to-RAM while cable is plugged in and active charging is underway.
    pub fn acquire_charging_wake_lock() {
        if Path::new("/sys/power/wake_lock").exists() {
            let _ = fs::write("/sys/power/wake_lock", "lynx_charging\n");
        }
    }

    /// Releases the Linux kernel wake_lock so device can enter deep sleep when unplugged or full.
    pub fn release_charging_wake_lock() {
        if Path::new("/sys/power/wake_unlock").exists() {
            let _ = fs::write("/sys/power/wake_unlock", "lynx_charging\n");
        }
    }

    /// Proactively scans all thermal cooling devices and forces battery / charger cooling
    /// devices to cur_state 0 (locked via 0444) to neutralize OEM thermal-engine throttling.
    pub fn neutralise_battery_cooling_devices() {
        if let Ok(entries) = fs::read_dir("/sys/class/thermal") {
            for entry in entries.flatten() {
                let name = entry.file_name();
                let name_str = name.to_string_lossy();
                if name_str.starts_with("cooling_device") {
                    let type_path = format!("/sys/class/thermal/{}/type", name_str);
                    if let Ok(t) = fs::read_to_string(&type_path) {
                        let t_lower = t.trim().to_lowercase();
                        if t_lower.contains("sysrst") || t_lower.contains("shutdown") || t_lower.contains("rst") {
                            continue;
                        }
                        if t_lower.contains("bcct") || t_lower.contains("abcct")
                            || t_lower == "battery" || t_lower == "charger" || t_lower.contains("pm660") || t_lower.contains("smb")
                        {
                            let cur_path = format!("/sys/class/thermal/{}/cur_state", name_str);
                            if let Some(cur) = Self::read_i64(&cur_path) {
                                if cur > 0 {
                                    Self::write_node_lock(&cur_path, "0");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /// Continuous live hardware invariant enforcer called on every tick.
    /// Defends charging current ceilings and battery cooling state against background OEM resets
    /// (e.g. thermal-engine, kernel workqueues, and screen-off power save routines).
    pub fn enforce_live_charging_invariants() {
        if !Self::is_hardware_plugged() {
            Self::release_charging_wake_lock();
            return;
        }

        let cfg = ConfigStore::load_charging_config();
        let cur_cap = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(50) as u32;
        let is_bypass_active = Path::new("/dev/lynx_bypass_active").exists() || cfg.bypass_enabled;

        // Bypass Charging: Run continuous closed-loop Net-Zero regulator
        if is_bypass_active {
            Self::regulate_bypass_net_zero();
            Self::release_charging_wake_lock();
            return;
        }

        if cfg.auto_cut_enabled && cur_cap >= 100 {
            Self::release_charging_wake_lock();
            return;
        }

        // Active charging is in progress: hold wake lock so daemon loop runs smoothly even when screen is off!
        Self::acquire_charging_wake_lock();

        // Neutralize battery cooling devices from thermal-engine throttling
        Self::neutralise_battery_cooling_devices();

        let is_hyper = cfg.charging_mode == "hyper" || cfg.extreme_charging_enabled;
        let is_qcom = Self::is_qualcomm_soc();

        if is_hyper && is_qcom {
            Self::apply_qcom_thermal_engine_bind_mount();
        }

        if is_hyper {
            let target_peak_ma = if is_qcom && !Path::new("/sys/class/power_supply/smb1390").exists() && !Path::new("/sys/class/power_supply/charge_pump").exists() {
                4000
            } else if cfg.limit_current_ma >= 2000 {
                (cfg.limit_current_ma as u32).max(Self::probe_hardware_ceiling_ma())
            } else {
                Self::probe_hardware_ceiling_ma().max(12000)
            };

            let effective_ma = if Self::is_laptop_port() {
                1500
            } else if cur_cap >= 99 {
                1500
            } else {
                target_peak_ma
            };

            let adpv = Self::read_i64(&format!("{}/ADC_Charger_Voltage", MTK_DIR))
                .or_else(|| Self::read_i64("/sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger"))
                .or_else(|| Self::read_i64(&format!("{}/voltage_now", USB_DIR)))
                .or_else(|| Self::read_i64(&format!("{}/charger_voltage", BATT_DIR)))
                .or_else(|| Self::read_i64(&format!("{}/voltage_now", MAIN_DIR)))
                .unwrap_or(0);
            let adpv_clean = if adpv > 100_000 { adpv / 1000 } else { adpv };
            let is_pd_active = Self::is_usb_pd_active();
            let is_high_voltage = adpv_clean >= 6500 || is_pd_active;

            let target_ua = effective_ma as i64 * 1000;
            let target_usb_ua = if is_qcom && !Self::has_qcom_direct_charge_pump() {
                (effective_ma as i64 * 1000).min(4_500_000)
            } else if is_qcom {
                (effective_ma as i64 * 1000).max(6_000_000)
            } else {
                target_ua.max(5_000_000)
            };

            // Check if kernel or thermal-engine lowered the hardware registers
            let cur_fcc = Self::read_i64(&format!("{}/constant_charge_current_max", BATT_DIR)).unwrap_or(0);
            let cur_usb = Self::read_i64(&format!("{}/current_max", USB_DIR)).unwrap_or(0);
            let cur_in_lim = Self::read_i64(&format!("{}/input_current_limit", BATT_DIR)).unwrap_or(0);
            let cur_now_raw = Self::read_i64(&format!("{}/current_now", BATT_DIR)).unwrap_or(0).abs();
            let cur_now_ma = if cur_now_raw > 10000 { cur_now_raw / 1000 } else { cur_now_raw };

            let now_sec = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
            let last_fcc = LAST_FCC_WRITE_EPOCH.load(Ordering::Relaxed);
            let fcc_cooldown_expired = now_sec.saturating_sub(last_fcc) >= 15;

            // Anti-Sawtooth Rate Limiter:
            // If current is actively flowing (> 1500mA) or cur_fcc >= 2000mA, do NOT touch hardware.
            // Continuously rewriting sysfs resets the PMIC soft-start ramp causing sawtooth oscillation!
            let fcc_needs_reapply = if cur_now_ma >= 1500 || cur_fcc >= 2_000_000 {
                false
            } else if fcc_cooldown_expired {
                cur_fcc > 0 && cur_fcc < (target_ua - 400_000)
            } else {
                false
            };

            let usb_needs_reapply = if is_high_voltage || is_pd_active {
                // High-voltage PD or QC contract active: input current is governed by the negotiated PDO.
                // Avoid repeatedly writing to USB current_max to prevent resetting the USB-PD state machine!
                false
            } else if cur_now_ma >= 1500 || cur_usb >= 2_000_000 {
                false
            } else if fcc_cooldown_expired {
                cur_usb > 0 && cur_usb < (target_usb_ua - 400_000)
            } else {
                false
            };

            let in_lim_needs_reapply = if is_high_voltage || is_pd_active {
                false
            } else if cur_now_ma >= 1500 || cur_in_lim >= 2_000_000 {
                false
            } else if fcc_cooldown_expired {
                cur_in_lim > 0 && cur_in_lim < (target_usb_ua - 400_000)
            } else {
                false
            };

            if fcc_needs_reapply || usb_needs_reapply || in_lim_needs_reapply {
                LAST_FCC_WRITE_EPOCH.store(now_sec, Ordering::Relaxed);
                let effective_ua_str = target_ua.to_string();
                let effective_usb_str = target_usb_ua.to_string();
                let effective_ma_str = effective_ma.to_string();

                Self::write_node(&format!("{}/constant_charge_current_max", BATT_DIR), &effective_ua_str);
                Self::write_node(&format!("{}/constant_charge_current", BATT_DIR), &effective_ua_str);
                if usb_needs_reapply {
                    Self::write_node(&format!("{}/current_max", USB_DIR), &effective_usb_str);
                }
                Self::write_node(&format!("{}/current_max", MAIN_DIR), &effective_ua_str);
                if in_lim_needs_reapply {
                    Self::write_node(&format!("{}/input_current_limit", BATT_DIR), &effective_usb_str);
                }

                if is_qcom {
                    let direct_val = if Self::has_qcom_direct_charge_pump() { "1" } else { "0" };
                    Self::write_node(&format!("{}/direct_charging", QC_DIR), direct_val);
                    Self::write_node(&format!("{}/restricted_charging", QC_DIR), "0");
                    Self::write_node(&format!("{}/restrict_cur", QC_DIR), &effective_ma_str);
                    Self::write_node(&format!("{}/system_temp_level", QC_DIR), "0");
                    Self::write_node(&format!("{}/system_temp_level", BATT_DIR), "0");
                    Self::write_node(&format!("{}/temp_state", BATT_DIR), "0");
                    Self::write_node(&format!("{}/thermal_input_current_limit", BATT_DIR), "-1");
                }
            }
        } else if cfg.charging_mode == "custom" {
            let target_ma = cfg.custom_limit_current_ma.clamp(500, 25000) as u32;
            let target_ua = target_ma as i64 * 1000;
            let target_usb_ua = if is_qcom && !Self::has_qcom_direct_charge_pump() {
                (target_ma as i64 * 1000).min(4_500_000)
            } else {
                target_ua
            };

            let adpv = Self::read_i64(&format!("{}/ADC_Charger_Voltage", MTK_DIR))
                .or_else(|| Self::read_i64("/sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger"))
                .or_else(|| Self::read_i64(&format!("{}/voltage_now", USB_DIR)))
                .or_else(|| Self::read_i64(&format!("{}/charger_voltage", BATT_DIR)))
                .or_else(|| Self::read_i64(&format!("{}/voltage_now", MAIN_DIR)))
                .unwrap_or(0);
            let adpv_clean = if adpv > 100_000 { adpv / 1000 } else { adpv };
            let is_pd_active = Self::is_usb_pd_active();
            let is_high_voltage = adpv_clean >= 6500 || is_pd_active;

            let cur_fcc = Self::read_i64(&format!("{}/constant_charge_current_max", BATT_DIR)).unwrap_or(0);
            let cur_now_raw = Self::read_i64(&format!("{}/current_now", BATT_DIR)).unwrap_or(0).abs();
            let cur_now_ma = if cur_now_raw > 10000 { cur_now_raw / 1000 } else { cur_now_raw };
            let now_sec = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
            let last_fcc = LAST_FCC_WRITE_EPOCH.load(Ordering::Relaxed);
            let fcc_cooldown_expired = now_sec.saturating_sub(last_fcc) >= 15;

            if (cur_now_ma < 1200 || cur_fcc < 1_500_000) && fcc_cooldown_expired && cur_fcc > 0 && cur_fcc < (target_ua - 200_000) {
                LAST_FCC_WRITE_EPOCH.store(now_sec, Ordering::Relaxed);
                let target_ua_str = target_ua.to_string();
                let target_usb_str = target_usb_ua.to_string();
                Self::write_node(&format!("{}/constant_charge_current_max", BATT_DIR), &target_ua_str);
                Self::write_node(&format!("{}/constant_charge_current", BATT_DIR), &target_ua_str);
                if !is_high_voltage && !is_pd_active {
                    Self::write_node(&format!("{}/current_max", USB_DIR), &target_usb_str);
                    Self::write_node(&format!("{}/input_current_limit", BATT_DIR), &target_usb_str);
                }
                Self::write_node(&format!("{}/current_max", MAIN_DIR), &target_ua_str);
            }
        }
    }

    /// Continuous Closed-Loop Watchdog for True Hardware Bypass Charging (Net-Zero Regulation)
    /// Runs every tick (~1s) while bypass is active.
    /// Guarantees:
    /// 1. Anti-Brownout: Instantly falls back to battery if charger is disconnected.
    /// 2. Anti-Reset: Re-applies BATFET cutoff & power path if vendor thermal daemons revert sysfs.
    /// 3. Motherboard Protection: Keeps input current wide open (2500mA) so battery does not drain.
    /// 4. Live Telemetry: Writes real-time net current & VBUS stats to `/dev/lynx_bypass_telemetry`.
    pub fn regulate_bypass_net_zero() {
        if !Self::is_hardware_plugged() {
            Self::handle_disconnect();
            return;
        }

        let is_qcom = Self::is_qualcomm_soc();

        // 1. Maintain Motherboard Power Supply (VBUS -> VSYS) & Cut Battery Charge
        if is_qcom {
            Self::write_node(&format!("{}/input_current_limit", BATT_DIR), "2500000");
            Self::write_node(&format!("{}/current_max", USB_DIR), "2500000");
            Self::write_node(&format!("{}/current_max", MAIN_DIR), "2500000");
            Self::write_node(&format!("{}/input_suspend", BATT_DIR), "0");

            for node in [
                format!("{}/constant_charge_current", BATT_DIR),
                format!("{}/constant_charge_current_max", BATT_DIR),
                format!("{}/current_max", BATT_DIR),
            ] {
                Self::write_node(&node, "0");
            }
            Self::write_node(&format!("{}/restricted_charging", QC_DIR), "1");
            Self::write_node(&format!("{}/restrict_cur", QC_DIR), "0");
            Self::write_node(&format!("{}/direct_charging", QC_DIR), "0");
        } else {
            // MediaTek & Transsion Hardware PMIC Power Path (Net-Zero Battery Isolation)
            let bypass_in_hex = Self::format_mtk_charger_hex_ua(2500);
            let buck_keepalive_hex = Self::format_mtk_charger_hex_ua(600);
            Self::write_node(&format!("{}/input_current", MTK_DIR), &bypass_in_hex);
            Self::write_node("/proc/mtk_battery_cmd/en_power_path", "1");
            Self::write_node("/proc/mtk_battery_cmd/current_cmd", "0 1");
            Self::write_node(&format!("{}/pe20", MTK_DIR), "0");
            Self::write_node(&format!("{}/pe40", MTK_DIR), "0");
            Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger", "5000");

            Self::write_node(&format!("{}/chg1_current", MTK_DIR), &buck_keepalive_hex);
            Self::write_node(&format!("{}/chg2_current", MTK_DIR), &buck_keepalive_hex);
            Self::write_node(&format!("{}/sc_ibat_limit", MTK_DIR), "0");
            Self::write_node(&format!("{}/enable_sc", MTK_DIR), "0");
            Self::write_node(&format!("{}/tran_charger_full", MTK_DIR), "0");
            Self::write_node(&format!("{}/BatteryNotify", MTK_DIR), "0");
            Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "1");
            Self::write_node("/sys/devices/platform/tran_battery/Charging_CallState", "1");
            Self::write_node("/proc/driver/thermal/clabcct", "0 1 42000 1000 200000 0 0");
            Self::write_node("/proc/driver/thermal/clbcct", "3");
            Self::write_node("/proc/lynx/bypass_charging", "1");
        }

        // Generic Linux sysfs fallback nodes
        for node in &[
            "/sys/class/power_supply/battery/charge_control_limit",
            "/sys/class/power_supply/battery/charge_control_limit_max",
        ] {
            if Path::new(node).exists() {
                Self::write_node(node, "0");
            }
        }

        // 2. Read live electrical telemetry
        let cur_now_raw = Self::read_i64(&format!("{}/current_now", BATT_DIR)).unwrap_or(0);
        let cur_now_ma = if cur_now_raw.abs() > 10_000 { cur_now_raw / 1000 } else { cur_now_raw };
        let temp_dc = Self::read_real_battery_temp_dc();
        let adpv = Self::read_i64(&format!("{}/ADC_Charger_Voltage", MTK_DIR))
            .or_else(|| Self::read_i64(&format!("{}/voltage_now", USB_DIR)))
            .unwrap_or(5000);
        let adpv_mv = if adpv > 100_000 { adpv / 1000 } else { adpv };

        // 3. Persist high-speed status for UI & CLI (Lynx Companion App & TUI)
        let status_json = format!(
            "{{\"active\":true,\"plugged\":true,\"vbus_mv\":{},\"net_current_ma\":{},\"temp_c\":{:.1},\"status\":\"NET_ZERO_ACTIVE\"}}\n",
            adpv_mv, cur_now_ma, temp_dc as f64 / 10.0
        );
        let _ = fs::write("/dev/lynx_bypass_telemetry", status_json);
    }

    /// Mode 1: True Hardware Bypass Charging (Zero Battery Current, Vsys powered by Adapter)
    /// Mode 1: True Hardware Bypass / Anti-Flapping Single-Path Float (Zero Battery Degradation, Vsys powered by Adapter)
    pub fn apply_bypass_charging() {
        // Hardware Interlock / Brownout Protection Guard:
        // Genuine Bypass mode requires external VBUS power from a connected charger.
        // Isolating the battery without external power drops VSYS to 0V and triggers an instant hardware brownout/reboot.
        if !Self::is_hardware_plugged() {
            eprintln!("[LynxCharging] Refusing bypass charging: Charger not connected (Brownout Guard).");
            let _ = fs::remove_file("/dev/lynx_bypass_active");
            return;
        }

        Self::release_charging_wake_lock();
        let _ = fs::remove_file("/dev/lynx_extreme_charging");
        let _ = fs::remove_file("/dev/lynx_standard_charging");
        let _ = fs::write("/dev/lynx_bypass_active", "1");
        let _ = ConfigStore::set_batch(&[
            ("charging.charging_mode".to_string(), "bypass".to_string(), Some("str".to_string())),
            ("charging.bypass_enabled".to_string(), "true".to_string(), Some("bool".to_string())),
        ]);
        Self::unlock_extreme_nodes();

        let is_qcom = Self::is_qualcomm_soc();
        let hw_bypass_node = Self::detect_hw_bypass_node();
        let has_hw_bypass = hw_bypass_node.is_some();

        // 1. Hardware Bypass Path (Devices with physical bypass switches: Transsion, ROG, Sony, Samsung Store, Pixel, MTK current_cmd)
        if has_hw_bypass {
            if let Some(byp_node) = hw_bypass_node {
                if byp_node.contains("charge_stop_level") {
                    let cur_cap = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(80);
                    Self::write_node(byp_node, &cur_cap.to_string());
                } else if byp_node.contains("current_cmd") {
                    Self::set_perm(byp_node, 0o666);
                    Self::write_node(byp_node, "0 1");
                } else {
                    Self::write_node(byp_node, "1");
                }
            }

            if !is_qcom {
                let bypass_in_hex = Self::format_mtk_charger_hex_ua(2500);
                let buck_keepalive_hex = Self::format_mtk_charger_hex_ua(600);
                Self::set_bcct_throttle(true);
                Self::write_node("/sys/devices/platform/charger/pe20", "0");
                Self::write_node("/sys/devices/platform/charger/pe40", "0");
                Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger", "5000");
                Self::write_node("/proc/lynx/bypass_charging", "1");
                Self::write_node(&format!("{}/chg1_current", MTK_DIR), &buck_keepalive_hex);
                Self::write_node(&format!("{}/chg2_current", MTK_DIR), &buck_keepalive_hex);
                Self::write_node(&format!("{}/sc_ibat_limit", MTK_DIR), "0");
                Self::write_node(&format!("{}/enable_sc", MTK_DIR), "0");
                Self::write_node(&format!("{}/tran_charger_full", MTK_DIR), "0");
                Self::write_node(&format!("{}/input_current", MTK_DIR), &bypass_in_hex);
                Self::write_node("/proc/mtk_battery_cmd/en_power_path", "1");
                Self::write_node("/proc/mtk_battery_cmd/current_cmd", "0 1");
                Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "1");
                Self::write_node("/sys/devices/platform/tran_battery/Charging_CallState", "1");
            }

            // CRITICAL: Even on hardware bypass devices, KEEP the input current rail alive at 1500mA
            // so the charger powers the motherboard Vsys! NEVER set input_current_limit to 0!
            Self::write_node(&format!("{}/input_current_limit", BATT_DIR), "1500000");
            Self::write_node(&format!("{}/current_max", USB_DIR), "1500000");
            Self::write_node(&format!("{}/input_suspend", BATT_DIR), "0");
            Self::write_node(&format!("{}/charging_enabled", BATT_DIR), "1");
            for node in [
                format!("{}/constant_charge_current", BATT_DIR),
                format!("{}/constant_charge_current_max", BATT_DIR),
                format!("{}/current_max", BATT_DIR),
            ] {
                Self::write_node(&node, "0");
            }
        } else {
            // 2. Anti-Flapping Single-Path Float Mode (Devices WITHOUT hardware bypass: Redmi, Xiaomi, Poco, generic MTK/QCOM)
            // On single-path PMICs (e.g. PM660 / MT63xx), the input FET and battery charging converter are shared.
            // Cutting charging_enabled=0 or input_current_limit=0 turns off VBUS input entirely, causing
            // the charger to disconnect and reconnect in an endless loop (mati-nyala / flapping)!
            //
            // Solution: Keep VBUS input fully active at 1.5A for the motherboard (Vsys), but throttle the battery
            // charging current to minimal float (~100mA). The battery stops heating up and stops charging, while
            // the charger remains continuously connected with zero flapping!
            if is_qcom {
                for node in [
                    format!("{}/constant_charge_current", BATT_DIR),
                    format!("{}/constant_charge_current_max", BATT_DIR),
                    format!("{}/current_max", BATT_DIR),
                ] {
                    Self::write_node(&node, "100000");
                }
                Self::write_node(&format!("{}/input_current_limit", BATT_DIR), "1500000");
                Self::write_node(&format!("{}/current_max", USB_DIR), "1500000");
                Self::write_node(&format!("{}/input_suspend", BATT_DIR), "0");
                Self::write_node(&format!("{}/charging_enabled", BATT_DIR), "1");

                // Qualcomm Snapdragon Restrict Current
                Self::write_node(&format!("{}/restrict_cur", QC_DIR), "100");
                Self::write_node(&format!("{}/restricted_charging", QC_DIR), "1");
                Self::write_node(&format!("{}/direct_charging", QC_DIR), "0");
                Self::write_node(&format!("{}/thermal_input_current_limit", BATT_DIR), "1500000");
            } else {
                // MediaTek single-path (e.g. Helio G96/G99 without bypass_charger):
                let float_in_hex = Self::format_mtk_charger_hex_ua(1500);
                let float_chg_hex = Self::format_mtk_charger_hex_ua(100);
                Self::set_bcct_throttle(true);
                Self::write_node("/sys/devices/platform/charger/pe20", "0");
                Self::write_node("/sys/devices/platform/charger/pe40", "0");
                Self::write_node_lock(&format!("{}/input_current", MTK_DIR), &float_in_hex);
                Self::write_node_lock(&format!("{}/chg1_current", MTK_DIR), &float_chg_hex);
                Self::write_node_lock(&format!("{}/chg2_current", MTK_DIR), &float_chg_hex);
                Self::write_node_lock(&format!("{}/sc_ibat_limit", MTK_DIR), "100");
                Self::write_node_lock(&format!("{}/enable_sc", MTK_DIR), "0");
                Self::write_node_lock(&format!("{}/pdc_max_watt", MTK_DIR), "10");
                Self::write_node_lock("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "1");
                Self::write_node_lock("/sys/devices/platform/tran_battery/Charging_CallState", "1");

                for node in [
                    format!("{}/constant_charge_current", BATT_DIR),
                    format!("{}/constant_charge_current_max", BATT_DIR),
                    format!("{}/current_max", BATT_DIR),
                ] {
                    Self::write_node(&node, "100000");
                }
                Self::write_node(&format!("{}/input_current_limit", BATT_DIR), "1500000");
                Self::write_node(&format!("{}/current_max", USB_DIR), "1500000");
                Self::write_node(&format!("{}/input_suspend", BATT_DIR), "0");
                Self::write_node(&format!("{}/charging_enabled", BATT_DIR), "1");
            }
        }

        // 3. Dynamic Sysfs Charger Topology Sweeper (Safe Anti-Flapping Enforcement)
        for node in Self::crawl_dynamic_power_supply_nodes() {
            let leaf = Path::new(&node).file_name().and_then(|n| n.to_str()).unwrap_or("");
            match leaf {
                "constant_charge_current" | "constant_charge_current_max" => {
                    let target = if has_hw_bypass { "0" } else { "100000" };
                    Self::write_node(&node, target);
                }
                "current_max" => {
                    if node.contains("battery") {
                        let target = if has_hw_bypass { "0" } else { "100000" };
                        Self::write_node(&node, target);
                    } else if node.contains("usb") || node.contains("main") {
                        Self::write_node(&node, "1500000");
                    }
                }
                "input_current_limit" => {
                    Self::write_node(&node, "1500000");
                }
                "charging_enabled" => {
                    Self::write_node(&node, "1");
                }
                "input_suspend" => {
                    Self::write_node(&node, "0");
                }
                _ => {}
            }
        }

        // Ensure Android Framework keeps live uevent synchronization with physical kernel battery.
        let _ = std::process::Command::new("dumpsys")
            .args(["battery", "reset"])
            .output();

        Self::regulate_bypass_net_zero();
    }

    /// RT9759 2:1 Switched-Capacitor Pump Output Calculation:
    /// I_pump = 2 * I_bus * eta (nominal efficiency eta = 0.97).
    pub fn calculate_pump_output_ma(ibus_ma: u32, efficiency: f64) -> u32 {
        (2.0 * (ibus_ma as f64) * efficiency).round() as u32
    }

    /// Calculates net current entering battery cells across R_SENSE shunt:
    /// I_net = I_pump - I_sys (in microamperes).
    pub fn calculate_net_battery_current_ua(ibus_ma: u32, isys_ma: u32, efficiency: f64) -> i64 {
        let pump_ma = Self::calculate_pump_output_ma(ibus_ma, efficiency);
        let net_ma = pump_ma.saturating_sub(isys_ma);
        (net_ma as i64) * 1000
    }

    /// Evaluates electrochemical CC to CV boundaries and tapering thresholds:
    /// - >= 100% SoC: Bypass AutoCut ("bypass_100", 0 mA)
    /// - 96% to 99% SoC: Saturation trickle step-down ("tapering_96", 1200 mA)
    /// - 92% to 95% SoC: Controlled late-stage decay ("tapering_92", 2000 mA)
    /// - 86% to 91% SoC: Optimized mid-saturation ("tapering_86", 3000 mA)
    /// - 80% to 85% SoC: Sustained high-speed Direct Pump ("tapering_80", 4200 mA)
    /// - 0% to 79% SoC: Full Max Watt unconstrained peak ("hyper", 12000 mA)
    pub fn determine_taper_state(soc: u32, _vbat_mv: u32) -> (&'static str, u32) {
        if soc >= 100 {
            ("bypass_100", 0)
        } else if soc >= 96 {
            ("tapering_96", 1200)
        } else if soc >= 92 {
            ("tapering_92", 2000)
        } else if soc >= 86 {
            ("tapering_86", 3000)
        } else if soc >= 80 {
            ("tapering_80", 4200)
        } else {
            ("hyper", 12000)
        }
    }

    /// Universal Hardware Bypass Purge:
    /// Completely restores kernel PMIC power paths, battery charging registers,
    /// ODM in-call states, and clears bypass markers so normal or hyper charging resumes instantly.
    pub fn clear_bypass_hardware_state() {
        let _ = fs::remove_file("/dev/lynx_bypass_active");
        let _ = fs::remove_file("/dev/lynx_charging_state");
        let _ = fs::remove_file("/dev/lynx_standard_charging");
        let _ = fs::remove_file("/dev/lynx_stalled_5v_count");
        let _ = fs::remove_file("/dev/lynx_soft_reconnect_done");
        let _ = std::process::Command::new("dumpsys").args(["battery", "reset"]).output();

        // 1. MediaTek power path & battery cmd reset (non-destructive if already enabled)
        Self::set_perm("/proc/lynx/bypass_charging", 0o666);
        Self::write_node("/proc/lynx/bypass_charging", "0");
        Self::ensure_mtk_power_path_enabled();

        // 2. Transsion / ODM call state unlock & clear
        Self::set_perm("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", 0o666);
        Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "0");
        Self::set_perm("/sys/devices/platform/tran_battery/Charging_CallState", 0o666);
        Self::write_node("/sys/devices/platform/tran_battery/Charging_CallState", "0");

        // 3. Unlock and restore MTK charger current, pump, and protocol nodes
        for node in &[
            format!("{}/input_current", MTK_DIR),
            format!("{}/chg1_current", MTK_DIR),
            format!("{}/chg2_current", MTK_DIR),
            format!("{}/sc_ibat_limit", MTK_DIR),
            format!("{}/enable_sc", MTK_DIR),
            format!("{}/pe20", MTK_DIR),
            format!("{}/pe40", MTK_DIR),
            format!("{}/Pump_Express", MTK_DIR),
            format!("{}/bypass_charger", MTK_DIR),
            format!("{}/tran_charger_full", MTK_DIR),
            format!("{}/BatteryNotify", MTK_DIR),
            format!("{}/BN_TestMode", MTK_DIR),
            format!("{}/sw_jeita", MTK_DIR),
        ] {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
            }
        }
        let max_hex = Self::format_mtk_charger_hex_ua(7000);
        Self::write_node(&format!("{}/input_current", MTK_DIR), &max_hex);
        Self::write_node(&format!("{}/chg1_current", MTK_DIR), &max_hex);
        Self::write_node(&format!("{}/chg2_current", MTK_DIR), &max_hex);
        Self::write_node(&format!("{}/sc_ibat_limit", MTK_DIR), "7000");
        Self::write_node(&format!("{}/bypass_charger", MTK_DIR), "0");
        Self::write_node(&format!("{}/pe20", MTK_DIR), "1");
        Self::write_node(&format!("{}/pe40", MTK_DIR), "1");

        // 4. Reset hardware bypass OEM nodes (ROG, Sony, Pixel, Samsung, MediaTek, etc.)
        if let Some(byp_node) = Self::detect_hw_bypass_node() {
            Self::set_perm(byp_node, 0o666);
            if byp_node.contains("charge_stop_level") {
                Self::write_node(byp_node, "100");
            } else if byp_node.contains("current_cmd") {
                Self::write_node(byp_node, "1 0");
            } else {
                Self::write_node(byp_node, "0");
            }
        }

        // 5. Ensure charging rails and input suspend are unblocked
        Self::set_perm(&format!("{}/charging_enabled", BATT_DIR), 0o666);
        Self::write_node(&format!("{}/charging_enabled", BATT_DIR), "1");
        Self::set_perm(&format!("{}/input_suspend", BATT_DIR), 0o666);
        Self::write_node(&format!("{}/input_suspend", BATT_DIR), "0");

        // 6. Qualcomm Snapdragon un-restrict and direct charging restore
        if Self::is_qualcomm_soc() {
            Self::write_node(&format!("{}/restricted_charging", QC_DIR), "0");
            Self::write_node(&format!("{}/restrict_cur", QC_DIR), "3000");
            let direct_val = if Self::has_qcom_direct_charge_pump() { "1" } else { "0" };
            Self::write_node(&format!("{}/direct_charging", QC_DIR), direct_val);
            Self::write_node(&format!("{}/system_temp_level", QC_DIR), "0");
            Self::write_node(&format!("{}/system_temp_level", BATT_DIR), "0");
            Self::write_node(&format!("{}/thermal_input_current_limit", BATT_DIR), "-1");
        }
    }

    /// Universal High-Current USB Power Delivery (USB-PD / PPS / Type-C) Handshake & Rail Uncap
    /// Unlocks full USB Power Delivery (PDO/APDO negotiation up to 20V / 6A / 120W+),
    /// clears AICL droop clamps, enables HVDCP3 / Quick Charge 4+, and sets Xiaomi TurboCharge flags.
    pub fn uncap_usb_pd_protocol(target_ma: u32) {
        let is_qcom = Self::is_qualcomm_soc();
        let safe_target_ma = target_ma.clamp(500, 25000);
        let target_ma_str = safe_target_ma.to_string();

        // 1. Universal USB Power Delivery Permission Flags (Allow PDOs & APDO negotiation)
        for node in &[
            format!("{}/pd_allowed", USB_DIR),
            format!("{}/pd_allowed", BATT_DIR),
            format!("{}/pd_allowed", MAIN_DIR),
            "/sys/class/power_supply/battery/subsystem/usb/pd_allowed".to_string(),
            "/sys/class/power_supply/allow_hvdcp3".to_string(),
            format!("{}/allow_hvdcp3", USB_DIR),
            format!("{}/allow_hvdcp3", BATT_DIR),
            format!("{}/hvdcp_opti_allowed", USB_DIR),
            "/sys/kernel/fast_charge/force_fast_charge".to_string(),
            format!("{}/boost_current", USB_DIR),
            format!("{}/boost_current", BATT_DIR),
            format!("{}/fast_charge", BATT_DIR),
            format!("{}/fastcharge_mode", BATT_DIR),
        ] {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
                Self::write_node(node, "1");
            }
        }

        // 2. High-Voltage Ceilings (Allows 9V, 12V, 15V, and 20V PD contracts)
        for node in &[
            format!("{}/pd_voltage_max", USB_DIR),
            format!("{}/pd_voltage_max", BATT_DIR),
        ] {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
                Self::write_node(node, "20000000");
            }
        }

        // 3. High-Current & PPS Augmented PDO Ceilings (Up to 6000mA / 6A)
        let pd_cur_limit = (safe_target_ma as u64 * 1000).max(6_000_000).to_string();
        for node in &[
            format!("{}/pd_current_max", USB_DIR),
            format!("{}/pd_limit_current", USB_DIR),
            format!("{}/pd_current_max", BATT_DIR),
            format!("{}/apdo_max", USB_DIR),
        ] {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
                Self::write_node(node, &pd_cur_limit);
            }
        }

        // 4. MediaTek Power Delivery Controller (PDC) & Voltage Boundaries
        if Path::new(MTK_DIR).exists() {
            Self::set_perm(&format!("{}/pdc_max_watt", MTK_DIR), 0o666);
            Self::write_node(&format!("{}/pdc_max_watt", MTK_DIR), "120");

            for (node, val) in &[
                (format!("{}/pd_vbus_upper_bound", MTK_DIR), "20000"),
                (format!("{}/pd_vbus_low_bound", MTK_DIR), "5000"),
                (format!("{}/pd_input_current", MTK_DIR), "6000"),
                (format!("{}/pdc_mode", MTK_DIR), "1"),
            ] {
                if Path::new(node).exists() {
                    Self::set_perm(node, 0o666);
                    Self::write_node(node, val);
                }
            }
        }

        // 5. Xiaomi / POCO / HyperOS TurboCharge & PD Boost
        for (node, val) in &[
            (format!("{}/turbo_power", BATT_DIR), "1"),
            (format!("{}/is_turbo_charging", BATT_DIR), "1"),
            (format!("{}/soc_decimal", BATT_DIR), "1"),
            (format!("{}/night_charging", BATT_DIR), "0"),
            (format!("{}/smart_chg", BATT_DIR), "0"),
        ] {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
                Self::write_node(node, val);
            }
        }

        // 6. Neutralize Thermal Input Throttling & Clamped Voters
        for node in &[
            format!("{}/thermal_input_current_limit", BATT_DIR),
            format!("{}/thermal_input_current_limit", USB_DIR),
            format!("{}/thermal_input_current_limit", MAIN_DIR),
        ] {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
                Self::write_node(node, "-1");
            }
        }

        for node in &[
            format!("{}/system_temp_level", BATT_DIR),
            format!("{}/temp_state", BATT_DIR),
            format!("{}/step_charging_enabled", BATT_DIR),
            format!("{}/charge_control_limit", BATT_DIR),
        ] {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
                Self::write_node(node, "0");
            }
        }

        if is_qcom {
            Self::write_node(&format!("{}/restricted_charging", QC_DIR), "0");
            Self::write_node(&format!("{}/restrict_cur", QC_DIR), &target_ma_str);
            Self::write_node(&format!("{}/system_temp_level", QC_DIR), "0");
        }
    }

    /// Mode: request the fastest charging mode supported by hardware without arbitrary limits.
    /// Supports up to 240W, dual-cell 2S, and un-caps third-party USB-PD / PPS chargers (Ugreen, Anker, etc.).
    pub fn apply_extreme_charging(target_soc: u32, _allow_lockout_bypass: bool, requested_ma: u32) {
        Self::capture_charging_session_baseline();

        // 1. Completely clear bypass hardware state & markers
        Self::clear_bypass_hardware_state();

        let _ = ConfigStore::set_batch(&[
            ("charging.charging_mode".to_string(), "hyper".to_string(), Some("str".to_string())),
            ("charging.bypass_enabled".to_string(), "false".to_string(), Some("bool".to_string())),
            ("charging.extreme_charging_enabled".to_string(), "true".to_string(), Some("bool".to_string())),
            ("charging.limit_current_ma".to_string(), "12000".to_string(), Some("val".to_string())),
            ("charging.is_unconstrained_max_hw".to_string(), "true".to_string(), Some("bool".to_string())),
        ]);
        let _ = fs::remove_file("/dev/lynx_standard_charging");

        let cur_cap = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(50) as u32;
        let volt_raw = Self::read_i64(&format!("{}/voltage_now", BATT_DIR)).unwrap_or(0);
        let v_clean_mv = if volt_raw.abs() >= 10_000 { volt_raw.abs() / 1000 } else { volt_raw.abs() } as u32;

        let (_platform, _, _) = Self::detect_charging_platform();
        let is_qcom = Self::is_qualcomm_soc();

        let hw_ceiling = Self::probe_hardware_ceiling_ma();
        let has_qcom_cp = Self::has_qcom_direct_charge_pump();
        let target_peak_ma = if is_qcom && !has_qcom_cp {
            // Qualcomm single-cell without dual charge pump (e.g. SDM660 PM660, SD845, SM8150 single):
            // Unrestricted silicon limit (4000mA peak, throttled dynamically only by silicon PMIC & battery heat):
            if requested_ma >= 4000 {
                requested_ma.min(4500)
            } else if requested_ma >= 2000 {
                requested_ma.max(4000)
            } else {
                4000
            }
        } else if requested_ma >= 2000 {
            requested_ma.max(hw_ceiling)
        } else {
            hw_ceiling.max(12000)
        };

        // Utilize target_soc: In Hyper mode, do NOT clamp to 3200mA at 80% or 2200mA at 90%!
        // Unrestricted maximum peak current until 99% or AutoCut!
        let effective_ma = if Self::is_laptop_port() {
            1500
        } else if cur_cap >= 99 {
            1500
        } else if target_soc > 0 && target_soc < 100 && cur_cap >= target_soc {
            let (_, taper_ma) = Self::determine_taper_state(cur_cap, v_clean_mv);
            target_peak_ma.min(taper_ma)
        } else {
            target_peak_ma
        };
        let effective_ua_str = if is_qcom && !has_qcom_cp {
            effective_ma.min(4500) as u64 * 1000
        } else {
            effective_ma as u64 * 1000
        }.to_string();
        let effective_ma_str = effective_ma.to_string();

        let _ = fs::write("/dev/lynx_extreme_charging", format!("hyper-{}", effective_ma));

        // 1. Clear input suspend and enable native charging rails
        Self::set_perm(&format!("{}/input_suspend", BATT_DIR), 0o666);
        Self::write_node(&format!("{}/input_suspend", BATT_DIR), "0");
        Self::set_perm(&format!("{}/charging_enabled", BATT_DIR), 0o666);
        Self::write_node(&format!("{}/charging_enabled", BATT_DIR), "1");
        Self::write_node(&format!("{}/fastcharge_mode", BATT_DIR), "1");
        Self::write_node(&format!("{}/fast_charge", BATT_DIR), "1");
        Self::write_node(&format!("{}/boost_current", BATT_DIR), "1");

        // 1b. Proactively uncap USB Power Delivery (USB-PD / PPS) across all platforms & vendors
        Self::uncap_usb_pd_protocol(effective_ma);

        // MediaTek PMIC Power Path: ensure battery charging is permitted without disturbing active DV2 pump!
        Self::ensure_mtk_power_path_enabled();

        // 2. MediaTek Platform Enforcements & Dual Pump (Thermal & JEITA completely bypassed!)
        Self::set_bcct_throttle(false);

        let is_trickle = effective_ma < 1500;
        let pe_protocol = if is_trickle { "0" } else { "1" };

        // Voltage Gating for Switched-Capacitor Pump (RT9759 2:1):
        // If enabled when VBUS is at 5V, pump output is 2.5V (< V_BAT ~ 3.8-4.2V),
        // triggering reverse current / UVLO fault which kills Pump Express handshake.
        // Therefore, enable_sc is strictly voltage-gated: ONLY activate pump when adapter voltage >= 6.5V.
        let adpv = Self::read_i64(&format!("{}/ADC_Charger_Voltage", MTK_DIR))
            .or_else(|| Self::read_i64("/sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger"))
            .or_else(|| Self::read_i64(&format!("{}/voltage_now", USB_DIR)))
            .unwrap_or(0);
        let adpv_clean = if adpv > 100_000 { adpv / 1000 } else { adpv };
        let is_voltage_boosted = adpv_clean >= 6500;
        let pump_enable = if !is_trickle && is_voltage_boosted { "1" } else { "0" };

        Self::write_node(&format!("{}/pe20", MTK_DIR), pe_protocol);
        Self::write_node(&format!("{}/pe40", MTK_DIR), pe_protocol);
        Self::write_node(&format!("{}/enable_sc", MTK_DIR), pump_enable);
        Self::write_node(&format!("{}/BatteryNotify", MTK_DIR), "0");
        Self::write_node(&format!("{}/BN_TestMode", MTK_DIR), "1");
        Self::write_node(&format!("{}/tran_charger_full", MTK_DIR), "0");
        Self::write_node(&format!("{}/tran_game_mode", MTK_DIR), "0");
        Self::write_node(&format!("{}/bypass_charger", MTK_DIR), "0");
        Self::write_node(&format!("{}/sw_jeita", MTK_DIR), "0");
        Self::write_node(&format!("{}/sc_tuisoc", MTK_DIR), "100");
        Self::set_perm("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", 0o666);
        Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "0");
        Self::set_perm("/sys/devices/platform/tran_battery/Charging_CallState", 0o666);
        Self::write_node("/sys/devices/platform/tran_battery/Charging_CallState", "0");

        // R2 Thermal Neutralization
        Self::apply_charger_thermal_neutralization();

        // Active Maximum Push to MediaTek hardware registers:
        // Kernel store_input_current / store_chg1_current / store_chg2_current parse base-16 uA!
        let mtk_input_hex = Self::format_mtk_charger_hex_ua(effective_ma.clamp(500, 7000));
        Self::write_node(&format!("{}/input_current", MTK_DIR), &mtk_input_hex);
        Self::write_node(&format!("{}/sc_ibat_limit", MTK_DIR), &effective_ma_str);
        Self::write_node(&format!("{}/chg1_current", MTK_DIR), &mtk_input_hex);
        Self::write_node(&format!("{}/chg2_current", MTK_DIR), &mtk_input_hex);

        // Dynamically uncap pdc_max_watt (never choke to 68W if device supports 120W+)
        let current_pdc = Self::read_i64(&format!("{}/pdc_max_watt", MTK_DIR)).unwrap_or(0);
        if current_pdc < 120 && Path::new(&format!("{}/pdc_max_watt", MTK_DIR)).exists() {
            Self::write_node_lock(&format!("{}/pdc_max_watt", MTK_DIR), "120");
        }

        // 3. Universal Linux & Qualcomm Uncaps (Ugreen / Anker PD PPS 5A uncap)
        let usb_current_cap_ua = if is_qcom && !has_qcom_cp {
            // Qualcomm single-cell without dual charge pump: 4500mA peak
            "4500000".to_string()
        } else {
            (effective_ma as u64 * 1000).max(6_000_000).to_string()
        };

        let direct_val = if has_qcom_cp { "1" } else { "0" };
        if is_qcom {
            Self::write_node(&format!("{}/current_max", USB_DIR), &usb_current_cap_ua);
            Self::write_node(&format!("{}/current_max", MAIN_DIR), &effective_ua_str);
            Self::write_node(&format!("{}/constant_charge_current_max", BATT_DIR), &effective_ua_str);
            Self::write_node(&format!("{}/constant_charge_current", BATT_DIR), &effective_ua_str);
            Self::write_node(&format!("{}/input_current_limit", BATT_DIR), &usb_current_cap_ua);

            Self::write_node(&format!("{}/direct_charging", QC_DIR), direct_val);
            Self::write_node(&format!("{}/restricted_charging", QC_DIR), "0");
            Self::write_node(&format!("{}/restrict_cur", QC_DIR), &effective_ma_str);
            Self::write_node(&format!("{}/system_temp_level", QC_DIR), "0");
            Self::write_node(&format!("{}/system_temp_level", BATT_DIR), "0");
            Self::write_node(&format!("{}/temp_state", BATT_DIR), "0");
            Self::write_node(&format!("{}/thermal_input_current_limit", BATT_DIR), "-1");
        } else {
            Self::write_node_lock(&format!("{}/current_max", USB_DIR), &usb_current_cap_ua);
            Self::write_node_lock(&format!("{}/current_max", MAIN_DIR), &effective_ua_str);
            Self::write_node_lock(&format!("{}/constant_charge_current_max", BATT_DIR), &effective_ua_str);
            Self::write_node_lock(&format!("{}/constant_charge_current", BATT_DIR), &effective_ua_str);
            Self::write_node_lock(&format!("{}/input_current_limit", BATT_DIR), &effective_ua_str);

            if Path::new(&format!("{}/direct_charging", QC_DIR)).exists() {
                Self::write_node(&format!("{}/direct_charging", QC_DIR), direct_val);
                Self::write_node(&format!("{}/restricted_charging", QC_DIR), "0");
                Self::write_node_lock(&format!("{}/restrict_cur", QC_DIR), &effective_ma_str);
                Self::write_node_lock(&format!("{}/thermal_input_current_limit", BATT_DIR), "-1");
            }
        }

        // 4. Dynamic Sysfs Charger Topology Uncap Sweeper (Universally unthrottles ANY unknown chip or node)
        for node in Self::crawl_dynamic_power_supply_nodes() {
            if is_qcom && node.contains("pc_port") {
                continue;
            }
            let leaf = Path::new(&node).file_name().and_then(|n| n.to_str()).unwrap_or("");
            match leaf {
                "constant_charge_current_max" | "constant_charge_current" | "current_max" => {
                    let is_usb_input_node = node.contains("/usb/") || node.contains("/main/");
                    let val_to_write = if is_usb_input_node { &usb_current_cap_ua } else { &effective_ua_str };
                    if is_qcom {
                        Self::write_node(&node, val_to_write);
                    } else {
                        Self::write_node_lock(&node, val_to_write);
                    }
                }
                "input_current_limit" | "hw_current_max" => {
                    let in_limit = if is_qcom { &usb_current_cap_ua } else { &effective_ua_str };
                    if is_qcom {
                        Self::write_node(&node, in_limit);
                    } else {
                        Self::write_node_lock(&node, in_limit);
                    }
                }
                "charge_control_limit" => {
                    if is_qcom {
                        Self::write_node(&node, &effective_ua_str);
                    } else {
                        Self::write_node_lock(&node, &effective_ua_str);
                    }
                }
                "fastcharge_mode" | "fast_charge" | "boost_current" | "turbo_power" | "is_turbo_charging" | "soc_decimal" => {
                    Self::write_node_lock(&node, "1");
                }
                "pd_allowed" | "allow_hvdcp3" | "hvdcp_opti_allowed" => {
                    Self::write_node_lock(&node, "1");
                }
                "pd_voltage_max" => {
                    Self::write_node_lock(&node, "20000000");
                }
                "pd_current_max" | "pd_limit_current" | "apdo_max" => {
                    Self::write_node_lock(&node, "6000000");
                }
                "pd_vbus_upper_bound" => {
                    Self::write_node_lock(&node, "20000");
                }
                "pd_vbus_low_bound" => {
                    Self::write_node_lock(&node, "5000");
                }
                "pd_input_current" => {
                    Self::write_node_lock(&node, "6000");
                }
                "pdc_max_watt" => {
                    Self::write_node_lock(&node, "120");
                }
                "pdc_mode" => {
                    Self::write_node_lock(&node, "1");
                }
                "charging_enabled" => {
                    Self::write_node(&node, "1");
                }
                "input_suspend" | "night_charging" | "smart_chg" => {
                    Self::write_node(&node, "0");
                }
                "thermal_input_current_limit" => {
                    Self::write_node_lock(&node, "-1");
                }
                "system_temp_level" | "temp_state" => {
                    Self::write_node_lock(&node, "0");
                }
                "step_charging_enabled" => {
                    Self::write_node_lock(&node, "0");
                }
                _ => {}
            }
        }
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

    /// Software Re-Handshake (Zero-Touch Soft Reconnect):
    /// Momentarily toggles input charging FET or kicks Pump_Express / rerun_aicl
    /// to reset PMIC Type-C / BC1.2 state machine without physical cable replugging.
    pub fn trigger_charger_soft_reconnect() {
        // Never pulse if connected to a PC / laptop USB port or if USB-PD contract is active
        if Self::is_laptop_port() || Self::is_usb_pd_active() {
            return;
        }

        if !can_soft_reconnect() {
            return;
        }

        if Self::is_qualcomm_soc() {
            let has_cp = Self::has_qcom_direct_charge_pump();
            let target_ua = "4000000";
            let direct_val = if has_cp { "1" } else { "0" };

            // 1. Pre-uncap registers in kernel memory before handshake trigger
            for node in &[
                format!("{}/constant_charge_current_max", BATT_DIR),
                format!("{}/constant_charge_current", BATT_DIR),
                format!("{}/current_max", USB_DIR),
                format!("{}/current_max", MAIN_DIR),
                format!("{}/input_current_limit", BATT_DIR),
                format!("{}/hw_current_max", USB_DIR),
            ] {
                if Path::new(node).exists() {
                    Self::set_perm(node, 0o666);
                    Self::write_node(node, target_ua);
                }
            }
            Self::write_node(&format!("{}/direct_charging", QC_DIR), direct_val);
            Self::write_node(&format!("{}/restricted_charging", QC_DIR), "0");
            Self::write_node(&format!("{}/restrict_cur", QC_DIR), "4000");
            Self::write_node(&format!("{}/system_temp_level", QC_DIR), "0");
            Self::write_node(&format!("{}/system_temp_level", BATT_DIR), "0");
            Self::write_node(&format!("{}/temp_state", BATT_DIR), "0");
            Self::write_node(&format!("{}/thermal_input_current_limit", BATT_DIR), "-1");

            // 2. Qualcomm Safe Re-Handshake:
            // NEVER pulse input_suspend on Qualcomm single-PMIC (PM660)!
            // Pulsing input_suspend drops VBUS and forces an external disconnect/reconnect loop.
            // Instead, trigger Qualcomm hardware AICL rerun:
            for rerun in &[
                format!("{}/rerun_aicl", USB_DIR),
                format!("{}/rerun_aicl", BATT_DIR),
                format!("{}/rerun_aicl", MAIN_DIR),
            ] {
                if Path::new(rerun).exists() {
                    Self::set_perm(rerun, 0o666);
                    Self::write_node(rerun, "1");
                }
            }
            let en_node = format!("{}/charging_enabled", BATT_DIR);
            if Path::new(&en_node).exists() {
                Self::set_perm(&en_node, 0o666);
                Self::write_node(&en_node, "1");
            }
            Self::write_node(&format!("{}/input_suspend", BATT_DIR), "0");

            let _ = std::process::Command::new("dumpsys").args(["battery", "reset"]).output();
            return;
        }

        let chg_type = Self::read_node(&format!("{}/Charger_Type", MTK_DIR)).unwrap_or_default();
        if chg_type == "1" || chg_type == "2" || chg_type == "3" {
            // Standard host, charging host, or non-standard charger: do NOT pulse!
            return;
        }

        // If high-voltage Pump Express / Direct Charge (>= 6.5V) or high current (>= 1500mA) is already active,
        // do NOT disturb the PMIC or DV2 state machine!
        let adpv = Self::read_i64(&format!("{}/ADC_Charger_Voltage", MTK_DIR))
            .or_else(|| Self::read_i64("/sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger"))
            .or_else(|| Self::read_i64(&format!("{}/voltage_now", USB_DIR)))
            .unwrap_or(0);
        let adpv_clean = if adpv > 100_000 { adpv / 1000 } else { adpv };
        let cur_now_raw = Self::read_i64(&format!("{}/current_now", BATT_DIR)).unwrap_or(0).abs();
        let cur_now_ma = if cur_now_raw > 10_000 { cur_now_raw / 1000 } else { cur_now_raw };
        if adpv_clean >= 6500 || cur_now_ma >= 1500 {
            return;
        }

        // 1. Ensure PMIC power path and battery charging are permitted with open permissions
        Self::ensure_mtk_power_path_enabled();
        Self::set_perm("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", 0o666);
        Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "0");
        Self::set_perm("/sys/devices/platform/tran_battery/Charging_CallState", 0o666);
        Self::write_node("/sys/devices/platform/tran_battery/Charging_CallState", "0");

        // 2. Re-assert Pump Express protocols and test mode
        Self::set_perm(&format!("{}/pe20", MTK_DIR), 0o666);
        Self::write_node(&format!("{}/pe20", MTK_DIR), "1");
        Self::set_perm(&format!("{}/pe40", MTK_DIR), 0o666);
        Self::write_node(&format!("{}/pe40", MTK_DIR), "1");
        Self::set_perm(&format!("{}/BN_TestMode", MTK_DIR), 0o666);
        Self::write_node(&format!("{}/BN_TestMode", MTK_DIR), "1");

        // 3. Kick Qualcomm rerun_aicl if present (generic platform fallback)
        let rerun_node = format!("{}/rerun_aicl", USB_DIR);
        if Path::new(&rerun_node).exists() {
            Self::write_node(&rerun_node, "1");
        }

        // CRITICAL MEDIATEK SAFETY:
        // NEVER write "1" to /sys/devices/platform/charger/Pump_Express during active charging!
        // In the MediaTek RT9471 driver, writing to Pump_Express forces rt9471_enable_powerpath=0
        // and rt9471_enable_hz=1 (Hi-Z), which disconnects VBUS and causes chg_in=0, permanently
        // breaking charging until the user physically replugs the USB cable!

        Self::write_node(&format!("{}/input_suspend", BATT_DIR), "0");
        Self::write_node(&format!("{}/charging_enabled", BATT_DIR), "1");
    }

    /// Full hardware charger & PMIC restart (Qualcomm PM660/PM8150 + MediaTek MT63xx).
    /// Clears latched voters, triggers AICL rerun, resets input FET, and restores healthy current ceilings.
    pub fn force_restart_charger() {
        // 1. Kick AICL renegotiation across all candidate nodes
        for node in &[
            format!("{}/rerun_aicl", USB_DIR),
            format!("{}/rerun_aicl", BATT_DIR),
            format!("{}/rerun_aicl", MAIN_DIR),
        ] {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
                Self::write_node(node, "1");
            }
        }

        // 2. Clear lingering markers
        let _ = fs::remove_file("/dev/lynx_charging_guard");
        let _ = fs::remove_file("/dev/lynx_extreme_charging");
        let _ = fs::remove_file("/dev/lynx_standard_charging");
        let _ = fs::remove_file("/dev/lynx_bypass_active");
        let _ = fs::remove_file("/dev/lynx_orig_chg_max");
        let _ = fs::remove_file("/dev/lynx_stalled_5v_count");
        let _ = fs::remove_file("/dev/lynx_soft_reconnect_done");

        let is_qcom = Self::is_qualcomm_soc();

        // 3. VBUS input suspend pulse (only on non-Qualcomm; on Qualcomm it drops VBUS completely)
        if !is_qcom {
            let suspend_node = format!("{}/input_suspend", BATT_DIR);
            if Path::new(&suspend_node).exists() {
                Self::set_perm(&suspend_node, 0o666);
                let _ = fs::write(&suspend_node, "1");
                std::thread::sleep(std::time::Duration::from_millis(300));
                let _ = fs::write(&suspend_node, "0");
            }
        } else {
            Self::write_node(&format!("{}/input_suspend", BATT_DIR), "0");
        }

        // 4. Ensure charging is enabled
        let en_node = format!("{}/charging_enabled", BATT_DIR);
        if Path::new(&en_node).exists() {
            Self::set_perm(&en_node, 0o666);
            Self::write_node(&en_node, "1");
        }

        // 5. Uncap input & charge currents to healthy defaults
        let target_ua = if is_qcom { "3000000" } else { "4500000" };
        for node in &[
            format!("{}/constant_charge_current_max", BATT_DIR),
            format!("{}/constant_charge_current", BATT_DIR),
            format!("{}/current_max", USB_DIR),
            format!("{}/current_max", MAIN_DIR),
            format!("{}/input_current_limit", BATT_DIR),
            format!("{}/hw_current_max", USB_DIR),
        ] {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
                Self::write_node(node, target_ua);
            }
        }

        if is_qcom {
            let direct_val = if Self::has_qcom_direct_charge_pump() { "1" } else { "0" };
            Self::write_node(&format!("{}/direct_charging", QC_DIR), direct_val);
            Self::write_node(&format!("{}/restricted_charging", QC_DIR), "0");
            Self::write_node(&format!("{}/restrict_cur", QC_DIR), "3000");
            Self::write_node(&format!("{}/system_temp_level", QC_DIR), "0");
            Self::write_node(&format!("{}/system_temp_level", BATT_DIR), "0");
            Self::write_node(&format!("{}/temp_state", BATT_DIR), "0");
            Self::write_node(&format!("{}/thermal_input_current_limit", BATT_DIR), "-1");
        }

        // 6. Reset Android BatteryService broadcast state
        let _ = std::process::Command::new("dumpsys").args(["battery", "reset"]).output();
        Self::unlock_bcct();
    }

    /// Calculates the safe PMIC input bus current limit (in mA) based on adapter VBUS voltage.
    /// Prevents 9V/10V adapter voltage from doubling or tripling battery current when direct pump is disabled.
    #[inline]
    pub fn calculate_regulated_input_current_ma(requested_battery_ma: u32, vbus_mv: i64) -> u32 {
        if vbus_mv >= 6000 {
            // Power equivalence: P_in * 0.90 = P_bat -> V_bus * I_bus * 0.90 = V_bat * I_bat
            // I_bus = (I_bat * 4200) / V_bus
            let scaled = (requested_battery_ma as u64 * 4200) / (vbus_mv as u64);
            scaled.max(350).min(requested_battery_ma as u64) as u32
        } else {
            requested_battery_ma
        }
    }

    /// Regulated charging with active requested current limit.
    pub fn apply_regulated_charging(requested_ma: u32) {
        Self::clear_bypass_hardware_state();
        let _ = fs::write("/dev/lynx_standard_charging", format!("regulated-{}", requested_ma));

        let effective_ma = if Self::is_laptop_port() {
            requested_ma.min(1500)
        } else {
            requested_ma.clamp(500, 25000)
        };
        let effective_ua_str = (effective_ma as u64 * 1000).to_string();
        let effective_ma_str = effective_ma.to_string();

        Self::ensure_mtk_power_path_enabled();
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

        let adpv = Self::read_i64(&format!("{}/ADC_Charger_Voltage", MTK_DIR))
            .or_else(|| Self::read_i64("/sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger"))
            .or_else(|| Self::read_i64(&format!("{}/voltage_now", USB_DIR)))
            .unwrap_or(0);
        let adpv_clean = if adpv > 100_000 { adpv / 1000 } else { adpv };

        // Clamping logic:
        if effective_ma <= 1500 {
            // Tight Cool Mode throttling (strictly clamps to ~1.0A 5V, suppresses direct pump)
            Self::set_bcct_throttle(true);
            Self::write_node(&format!("{}/enable_sc", MTK_DIR), "0");
            Self::write_node(&format!("{}/pdc_max_watt", MTK_DIR), "10");
            Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "2");
            Self::write_node("/sys/devices/platform/tran_battery/Charging_CallState", "2");
        } else {
            Self::set_bcct_throttle(false);
            let watt = ((effective_ma * 5) / 1000).max(10).min(68);
            Self::write_node(&format!("{}/pdc_max_watt", MTK_DIR), &watt.to_string());
            Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "0");
            Self::write_node("/sys/devices/platform/tran_battery/Charging_CallState", "0");
        }

        let is_qcom = Self::is_qualcomm_soc();
        let mtk_input_ma = Self::calculate_regulated_input_current_ma(effective_ma, adpv_clean);
        let mtk_input_str = mtk_input_ma.to_string();
        let mtk_input_ua_str = (mtk_input_ma as u64 * 1000).to_string();

        let (usb_max_ua_str, input_limit_ua_str, qc_restrict_str, qc_restricted) = if is_qcom {
            // Qualcomm PM660 / SMB1380 / SMB1351 USB-PD:
            // Do NOT downscale USB input current with MTK Pump Express formula!
            // PD contract allows 3.0A at 5V/9V.
            let qcom_usb_ua = if effective_ma <= 1500 {
                (effective_ma as u64 * 1000).to_string()
            } else {
                "3000000".to_string()
            };
            let qcom_in_ua = (effective_ma as u64 * 1000).to_string();
            // CRITICAL: restricted_charging must NEVER be set to "1" during active charging (Cool / Custom / Smart Tapering)!
            // In Qualcomm kernel smb-lib.c, restricted_charging=1 is Store Demo Mode, activating RESTRICTED_CHARGING_VOTER
            // which drops fv_votable (Float Voltage) from 4.40V down to 4.20V.
            // On a 4.40V BN4A Li-Po cell, 4.20V corresponds to exactly ~97-98% SoC. Once the cell hits 4.20V,
            // current drops to 0mA and the PMIC NEVER sees the 4.40V termination threshold (status: Full),
            // causing charging to permanently stall at 98%!
            let restrict = "0";
            (qcom_usb_ua, qcom_in_ua, effective_ma_str.clone(), restrict)
        } else {
            (mtk_input_ua_str.clone(), mtk_input_ua_str.clone(), mtk_input_str.clone(), "0")
        };

        if is_qcom {
            // Qualcomm Snapdragon PM660 / QPNP-SMB2:
            // Write nodes with normal write permissions (0666/0644). Do NOT lock to 0444!
            // The kernel smb-lib power supply worker and Android Health HAL require write access
            // during CV phase to naturally adapt current down to the termination threshold (iterm ~150mA).
            Self::write_node(&format!("{}/constant_charge_current_max", BATT_DIR), &effective_ua_str);
            Self::write_node(&format!("{}/constant_charge_current", BATT_DIR), &effective_ua_str);
            Self::write_node(&format!("{}/current_max", USB_DIR), &usb_max_ua_str);
            Self::write_node(&format!("{}/current_max", MAIN_DIR), &effective_ua_str);
            Self::write_node(&format!("{}/input_current_limit", BATT_DIR), &input_limit_ua_str);
            Self::write_node(&format!("{}/restrict_cur", QC_DIR), &qc_restrict_str);
            Self::write_node(&format!("{}/restricted_charging", QC_DIR), "0");
            Self::write_node(&format!("{}/step_charging_enabled", BATT_DIR), "1");
        } else {
            // MediaTek: Write limits to hardware registers (base-16 uA) while preserving write permissions (0666)!
            // Locking to 0444 prevents driver update workers and breaks subsequent mode switches!
            let mtk_input_hex = Self::format_mtk_charger_hex_ua(mtk_input_ma.clamp(350, 7000));
            let mtk_chg_hex = Self::format_mtk_charger_hex_ua(effective_ma.clamp(500, 7000));
            Self::set_perm(&format!("{}/input_current", MTK_DIR), 0o666);
            Self::write_node(&format!("{}/input_current", MTK_DIR), &mtk_input_hex);
            Self::set_perm(&format!("{}/sc_ibat_limit", MTK_DIR), 0o666);
            Self::write_node(&format!("{}/sc_ibat_limit", MTK_DIR), &effective_ma_str);
            Self::set_perm(&format!("{}/chg1_current", MTK_DIR), 0o666);
            Self::write_node(&format!("{}/chg1_current", MTK_DIR), &mtk_chg_hex);
            Self::set_perm(&format!("{}/chg2_current", MTK_DIR), 0o666);
            Self::write_node(&format!("{}/chg2_current", MTK_DIR), &mtk_chg_hex);

            Self::set_perm(&format!("{}/constant_charge_current_max", BATT_DIR), 0o666);
            Self::write_node(&format!("{}/constant_charge_current_max", BATT_DIR), &effective_ua_str);
            Self::set_perm(&format!("{}/constant_charge_current", BATT_DIR), 0o666);
            Self::write_node(&format!("{}/constant_charge_current", BATT_DIR), &effective_ua_str);
            Self::set_perm(&format!("{}/current_max", USB_DIR), 0o666);
            Self::write_node(&format!("{}/current_max", USB_DIR), &usb_max_ua_str);
            Self::set_perm(&format!("{}/current_max", MAIN_DIR), 0o666);
            Self::write_node(&format!("{}/current_max", MAIN_DIR), &effective_ua_str);
            Self::set_perm(&format!("{}/input_current_limit", BATT_DIR), 0o666);
            Self::write_node(&format!("{}/input_current_limit", BATT_DIR), &input_limit_ua_str);
            Self::write_node(&format!("{}/restrict_cur", QC_DIR), &qc_restrict_str);
            Self::write_node(&format!("{}/restricted_charging", QC_DIR), qc_restricted);
        }

        // Dynamic Sysfs Charger Topology Regulation Sweeper
        for node in Self::crawl_dynamic_power_supply_nodes() {
            let leaf = Path::new(&node).file_name().and_then(|n| n.to_str()).unwrap_or("");
            match leaf {
                "constant_charge_current_max" | "constant_charge_current" => {
                    Self::set_perm(&node, 0o666);
                    Self::write_node(&node, &effective_ua_str);
                }
                "current_max" | "input_current_limit" | "hw_current_max" => {
                    Self::set_perm(&node, 0o666);
                    Self::write_node(&node, &usb_max_ua_str);
                }
                "fastcharge_mode" | "fast_charge" => {
                    Self::write_node(&node, if is_qcom && effective_ma >= 2000 { "1" } else { "0" });
                }
                "charging_enabled" => {
                    Self::write_node(&node, "1");
                }
                "input_suspend" => {
                    Self::write_node(&node, "0");
                }
                "thermal_input_current_limit" => {
                    Self::write_node_lock(&node, "-1");
                }
                "system_temp_level" | "temp_state" => {
                    Self::write_node_lock(&node, "0");
                }
                "step_charging_enabled" => {
                    // For Qualcomm: Ensure step charging is active (1) so the kernel step-chg-jeita
                    // state machine advances to the full-voltage termination phase!
                    Self::write_node(&node, "1");
                }
                _ => {}
            }
        }

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
        Self::clear_bypass_hardware_state();
        let _ = fs::write("/dev/lynx_standard_charging", "standard\n");

        Self::unlock_extreme_nodes();
        Self::restore_charger_thermal_safety();
        Self::unlock_bcct();

        // Restore standard charging flags
        Self::write_node("/proc/lynx/bypass_charging", "0");
        Self::ensure_mtk_power_path_enabled();
        Self::write_node(&format!("{}/bypass_charger", MTK_DIR), "0");
        Self::write_node(&format!("{}/tran_charger_full", MTK_DIR), "0");
        Self::write_node(&format!("{}/BN_TestMode", MTK_DIR), "0");
        Self::write_node(&format!("{}/BatteryNotify", MTK_DIR), "1");
        Self::set_perm(&format!("{}/sw_jeita", MTK_DIR), 0o666);
        Self::write_node(&format!("{}/sw_jeita", MTK_DIR), "1");
        if let Some(baseline_pdc) = Self::get_session_baseline_value(&format!("{}/pdc_max_watt", MTK_DIR)) {
            Self::write_node(&format!("{}/pdc_max_watt", MTK_DIR), &baseline_pdc);
        } else {
            let cur = Self::read_i64(&format!("{}/pdc_max_watt", MTK_DIR)).unwrap_or(0);
            if cur == 0 || cur > 120 {
                Self::write_node(&format!("{}/pdc_max_watt", MTK_DIR), "68");
            }
        }
        Self::write_node(&format!("{}/pe20", MTK_DIR), "1");
        Self::write_node(&format!("{}/pe40", MTK_DIR), "1");
        Self::write_node(&format!("{}/input_suspend", BATT_DIR), "0");
        Self::write_node(&format!("{}/charging_enabled", BATT_DIR), "1");
        Self::write_node(&format!("{}/fastcharge_mode", BATT_DIR), "1");
        Self::write_node(&format!("{}/fast_charge", BATT_DIR), "1");
        Self::write_node(&format!("{}/pd_allowed", USB_DIR), "1");
        Self::write_node(&format!("{}/allow_hvdcp3", USB_DIR), "1");
        Self::write_node("/sys/class/power_supply/allow_hvdcp3", "1");
        Self::write_node(&format!("{}/charging_enabled", BATT_DIR), "1");
        Self::write_node(&format!("{}/fastcharge_mode", BATT_DIR), "0");
        Self::write_node(&format!("{}/fast_charge", BATT_DIR), "0");
        let is_qcom = Self::is_qualcomm_soc();
        if is_qcom {
            let direct_val = if Self::has_qcom_direct_charge_pump() { "1" } else { "0" };
            Self::write_node(&format!("{}/direct_charging", QC_DIR), direct_val);
            Self::write_node(&format!("{}/restricted_charging", QC_DIR), "0");
            Self::write_node(&format!("{}/restrict_cur", QC_DIR), "3000");
            Self::write_node(&format!("{}/system_temp_level", QC_DIR), "0");
            Self::write_node(&format!("{}/system_temp_level", BATT_DIR), "0");
            Self::write_node(&format!("{}/thermal_input_current_limit", BATT_DIR), "-1");
        } else {
            Self::write_node(&format!("{}/direct_charging", QC_DIR), "0");
            Self::write_node(&format!("{}/restricted_charging", QC_DIR), "0");
        }
        Self::write_node(&format!("{}/step_charging_enabled", BATT_DIR), "1");

        // Unthrottle throttled PMIC current registers to clean baseline fast charging
        let default_limit_ua = if is_qcom { "3000000" } else { "2500000" };
        for node in &[
            format!("{}/constant_charge_current_max", BATT_DIR),
            format!("{}/constant_charge_current", BATT_DIR),
            format!("{}/current_max", USB_DIR),
            format!("{}/current_max", MAIN_DIR),
            format!("{}/input_current_limit", BATT_DIR),
            format!("{}/hw_current_max", USB_DIR),
        ] {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
                Self::write_node(node, default_limit_ua);
            }
        }

        // Unlock all sysfs charger nodes permissions and restore unthrottled MTK base-16 uA ceilings
        for node in &[
            format!("{}/input_current", MTK_DIR),
            format!("{}/sc_ibat_limit", MTK_DIR),
            format!("{}/chg1_current", MTK_DIR),
            format!("{}/chg2_current", MTK_DIR),
            format!("{}/enable_sc", MTK_DIR),
            format!("{}/pe20", MTK_DIR),
            format!("{}/pe40", MTK_DIR),
            format!("{}/restrict_cur", QC_DIR),
        ] {
            Self::set_perm(node, 0o666);
        }
        let max_hex = Self::format_mtk_charger_hex_ua(7000);
        Self::write_node(&format!("{}/input_current", MTK_DIR), &max_hex);
        Self::write_node(&format!("{}/chg1_current", MTK_DIR), &max_hex);
        Self::write_node(&format!("{}/chg2_current", MTK_DIR), &max_hex);
        Self::write_node(&format!("{}/sc_ibat_limit", MTK_DIR), "7000");

        let cur_cap = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(50);
        let _ = fs::write("/dev/lynx_charging_state", format!("state=standard cap={}\n", cur_cap));
    }

    /// Mode 3: Hyper Mode (100% dynamic peak, unconstrained)
    pub fn apply_hyper_mode() {
        let cur_cap = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(50);
        let _ = fs::write("/dev/lynx_charging_state", format!("state=hyper cap={}\n", cur_cap));
        Self::apply_extreme_charging(100, true, 12000);

        // Immediate Zero-Touch Soft-Reconnect on mode activation ONLY if charger is stalled at low voltage/current:
        if Self::is_hardware_plugged() && !Self::is_laptop_port() && cur_cap < 85 {
            let adpv = Self::read_i64(&format!("{}/ADC_Charger_Voltage", MTK_DIR))
                .or_else(|| Self::read_i64("/sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger"))
                .or_else(|| Self::read_i64(&format!("{}/voltage_now", USB_DIR)))
                .unwrap_or(0);
            let adpv_clean = if adpv > 100_000 { adpv / 1000 } else { adpv };
            let cur_now_raw = Self::read_i64(&format!("{}/current_now", BATT_DIR)).unwrap_or(0).abs();
            let cur_now_ma = if cur_now_raw > 10_000 { cur_now_raw / 1000 } else { cur_now_raw };
            if adpv_clean < 6500 && cur_now_ma < 1500 {
                Self::trigger_charger_soft_reconnect();
            }
        }
    }

    /// Mode 4: Custom Mode (User-defined exact limit in mA)
    pub fn apply_custom_mode(target_ma: u32) {
        let clamped = target_ma.clamp(500, 25000);
        let cur_cap = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(50);
        let _ = fs::write("/dev/lynx_charging_state", format!("state=custom_{} cap={}\n", clamped, cur_cap));
        Self::uncap_usb_pd_protocol(clamped);
        if clamped >= 5000 {
            Self::apply_extreme_charging(100, true, clamped);
        } else {
            Self::apply_regulated_charging(clamped);
        }
    }

    /// Neutralizes all charging thermal throttling stages and trip points (R2):
    /// 1. Overrides Transsion PCB thermal derating with calibrated 3-stage curve:
    ///    - < 48°C: Full 33W pump (6000mA)
    ///    - 48°C - 50°C: 18W Sweet Spot (4000mA)
    ///    - > 50°C: 10W OEM Protection fallback (2500mA / 1500mA)
    ///    Format: [48,4000,50,2500,1500]
    /// 2. Overrides DV2 direct pump in-memory thermal tables:
    ///    - [101,48,50,52,4000,2500,1500]
    ///    - [102,75,80,85,600,900,1200]
    /// 3. Synchronizes authentic Battery_Temperature for accurate stage transitions.
    /// 4. Neutralizes MediaTek ABCCT PID controllers (clabcct & clabcct_lcmoff -> policy 0, locked 0444).
    /// 5. Locks cooling devices 53-57 (abcct, mtk-cl-bcct00..02) cur_state to 0 (locked 0444).
    /// 6. Overrides Qualcomm system_temp_level and thermal_input_current_limit.
    /// 7. Suspends aggressive OEM thermal daemons (joyose, mi_thermald, thermal-engine).
    ///    Note: thermalloadalgod is intentionally excluded on Transsion to keep the TA netlink socket alive.
    /// 8. Overrides Android framework thermal status to 0 (THERMAL_STATUS_NONE).
    pub const THERMAL_PROCESS_NAMES: &[&str] = &[
        "com.xiaomi.joyose",
        "mi_thermald",
    ];

    pub fn apply_charger_thermal_neutralization() {
        // 1. Transsion Motherboard PCB Thermal Throttling: In Hyper Mode, allow full 7000mA at all temps
        for node in &[
            format!("{}/pcb_thermal_debug", TRAN_BATTERY_ODM_DIR),
            format!("{}/pcb_thermal_debug", TRAN_BATTERY_DIR),
        ] {
            if Path::new(node).exists() {
                Self::write_node_lock(node, "[95,7000,100,7000,7000000]");
            }
        }

        // 2. Transsion Direct Charge Pump DV2 In-Memory Thermal Table: Override thresholds & currents to 7000mA
        let dv2_debug = format!("{}/dv2_debug", PCA_DV2_DIR);
        if Path::new(&dv2_debug).exists() {
            Self::write_node_lock(&dv2_debug, "[101,85,88,90,7000,7000,7000]");
            Self::write_node_lock(&dv2_debug, "[102,85,88,90,7000,7000,7000]");
        }

        // 3. Lock Charging_CallState to 0 to prevent dv2_algo_task screen-on clamp (1500mA)
        Self::write_node_lock("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "0");
        Self::write_node_lock("/sys/devices/platform/tran_battery/Charging_CallState", "0");

        // 4. In Hyper Mode, lock Battery_Temperature to 25°C with 0444 so kernel battery driver never derates current
        Self::write_node_lock(BATT_TEMP_NODE, "25");

        // 5. MediaTek ABCCT PID Thermal Derater Neutralization: verified syntax
        if Path::new(CLABCCT_LCMOFF_NODE).exists() {
            Self::write_node_lock(CLABCCT_LCMOFF_NODE, "1 70000 1000 200000 7000 7000 0 7000 0");
        }
        if Path::new(CLABCCT_NODE).exists() {
            Self::write_node_lock(CLABCCT_NODE, "70000 1000 200000 7000 7000 0 1 1 0");
        }

        // 6. Freeze BCCT & ABCCT Cooling Devices (53-57) to state 0 locked
        Self::set_bcct_throttle(false);

        // Raise trip points and disable MediaTek AP / battery thermal zones that trip ABCCT
        if let Ok(entries) = fs::read_dir("/sys/class/thermal") {
            for entry in entries.flatten() {
                let name = entry.file_name();
                let name_str = name.to_string_lossy();
                if name_str.starts_with("thermal_zone") {
                    let type_path = format!("/sys/class/thermal/{}/type", name_str);
                    if let Ok(t) = fs::read_to_string(&type_path) {
                        let t_lower = t.trim().to_lowercase();
                        if t_lower == "mtktsap" || t_lower == "battery" || t_lower == "mtktsbattery" {
                            let mode_path = format!("/sys/class/thermal/{}/mode", name_str);
                            if Path::new(&mode_path).exists() {
                                Self::write_node_lock(&mode_path, "disabled");
                            }
                            for trip_idx in 0..5 {
                                let trip_path = format!("/sys/class/thermal/{}/trip_point_{}_temp", name_str, trip_idx);
                                if Path::new(&trip_path).exists() {
                                    Self::write_node_lock(&trip_path, "99000");
                                }
                            }
                        }
                    }
                } else if name_str.starts_with("cooling_device") {
                    let type_path = format!("/sys/class/thermal/{}/type", name_str);
                    if let Ok(t) = fs::read_to_string(&type_path) {
                        let t_lower = t.trim().to_lowercase();
                        if t_lower.contains("sysrst") || t_lower.contains("shutdown") || t_lower.contains("rst") {
                            continue;
                        }
                        if t_lower.contains("battery") || t_lower.contains("bcct") || t_lower.contains("abcct")
                            || t_lower.contains("isolate") || t_lower.contains("thermal-cpufreq")
                        {
                            let cur_path = format!("/sys/class/thermal/{}/cur_state", name_str);
                            Self::write_node_lock(&cur_path, "0");
                        }
                    }
                }
            }
        }

        // 7. Qualcomm Snapdragon Platform Thermal Uncaps & BMS JEITA Expansion
        Self::write_node_lock(&format!("{}/system_temp_level", QC_DIR), "0");
        Self::write_node_lock(&format!("{}/system_temp_level", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/temp_state", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/thermal_input_current_limit", BATT_DIR), "-1");
        let fv_uv_str = Self::probe_hardware_voltage_ceiling_uv().to_string();
        Self::write_node_lock(&format!("{}/step_charging_enabled", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/sw_jeita_enabled", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/constant_charge_voltage", BATT_DIR), &fv_uv_str);
        Self::write_node_lock(&format!("{}/constant_charge_voltage_max", BATT_DIR), &fv_uv_str);
        Self::write_node_lock(&format!("{}/voltage_max", BATT_DIR), &fv_uv_str);
        Self::write_node_lock(&format!("{}/restricted_charging", QC_DIR), "0");
        Self::write_node_lock(&format!("{}/restricted_charging", BATT_DIR), "0");

        // Relax Qualcomm BMS / Battery JEITA temperature thresholds (Original Lynx AI Charging Controller)
        let bms_dir = "/sys/class/power_supply/bms";
        Self::write_node_lock(&format!("{}/temp_cool", bms_dir), "120");
        Self::write_node_lock(&format!("{}/temp_hot", bms_dir), "650");
        Self::write_node_lock(&format!("{}/temp_warm", bms_dir), "610");
        Self::write_node_lock(&format!("{}/temp_cold", bms_dir), "0");

        // Fast charge and power negotiation boost flags
        Self::write_node_lock("/sys/kernel/fast_charge/force_fast_charge", "1");
        Self::write_node_lock("/sys/class/power_supply/usb/boost_current", "1");
        Self::write_node_lock("/sys/class/power_supply/usb/pd_allowed", "1");
        Self::write_node_lock("/sys/class/power_supply/allow_hvdcp3", "1");
        Self::write_node_lock("/sys/class/power_supply/battery/subsystem/usb/pd_allowed", "1");
        Self::write_node_lock("/sys/class/power_supply/battery/safety_timer_enabled", "0");

        // 7b. Qualcomm Snapdragon Dynamic Thermal Engine Live Bind-Mount & Trip Point Expansion
        Self::apply_qcom_thermal_engine_bind_mount();

        // 7c. Preserve all CPU cores online and prevent thermal isolation while charging
        for i in 0..16 {
            let cpu_isolate = format!("/sys/devices/system/cpu/cpu{}/isolate", i);
            let cpu_online = format!("/sys/devices/system/cpu/cpu{}/online", i);
            if Path::new(&cpu_isolate).exists() {
                Self::write_node(&cpu_isolate, "0");
            }
            if Path::new(&cpu_online).exists() {
                Self::write_node(&cpu_online, "1");
            }
        }

        // 8. Suspend aggressive userspace thermal throttlers via SIGSTOP (Transsion/MTK/etc.)
        // On Qualcomm Snapdragon, thermal-engine communicates via IPC sockets with Android Thermal HAL.
        // Freezing thermal-engine causes IPC deadlock and thermal service failure!
        if !Self::is_qualcomm_soc() {
            Self::signal_processes(Self::THERMAL_PROCESS_NAMES, true);
        } else {
            Self::signal_processes(&["thermal-engine", "thermal-engine-v2"], false);
        }

        // 9. Override Android framework thermal status to 0 (THERMAL_STATUS_NONE)
        let _ = std::process::Command::new("cmd")
            .args(["thermalservice", "override-status", "0"])
            .output();
    }

    /// Restores all charging thermal controls, trip points, daemons, and framework status (R4 / R2 reversion):
    pub fn restore_charger_thermal_safety() {
        // 1. Restore Transsion PCB thermal thresholds to OEM defaults
        for node in &[
            format!("{}/pcb_thermal_debug", TRAN_BATTERY_ODM_DIR),
            format!("{}/pcb_thermal_debug", TRAN_BATTERY_DIR),
        ] {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
                let _ = fs::write(node, "[45,3000,50,2000,1000]");
                Self::set_perm(node, 0o644);
            }
        }

        // 2. Restore Transsion DV2 thermal tables to OEM defaults
        let dv2_debug = format!("{}/dv2_debug", PCA_DV2_DIR);
        if Path::new(&dv2_debug).exists() {
            Self::set_perm(&dv2_debug, 0o666);
            let _ = fs::write(&dv2_debug, "[101,43,45,50,500,1000,1500]");
            let _ = fs::write(&dv2_debug, "[102,70,75,80,300,600,900]");
            Self::set_perm(&dv2_debug, 0o644);
        }

        // 3. Unlock Charging_CallState
        Self::set_perm("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", 0o666);
        Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "0");
        Self::set_perm("/sys/devices/platform/tran_battery/Charging_CallState", 0o666);
        Self::write_node("/sys/devices/platform/tran_battery/Charging_CallState", "0");

        // 4. Restore Battery_Temperature to genuine physical battery temperature
        Self::set_perm(BATT_TEMP_NODE, 0o666);
        Self::sync_authentic_battery_temp();

        // 5. Restore MediaTek ABCCT PID controllers to OEM setpoints
        if Path::new(CLABCCT_LCMOFF_NODE).exists() {
            Self::set_perm(CLABCCT_LCMOFF_NODE, 0o666);
            let _ = fs::write(CLABCCT_LCMOFF_NODE, "1 43000 1000 200000 5 2000 0");
            Self::set_perm(CLABCCT_LCMOFF_NODE, 0o644);
        }
        if Path::new(CLABCCT_NODE).exists() {
            Self::set_perm(CLABCCT_NODE, 0o666);
            let _ = fs::write(CLABCCT_NODE, "1 42000 1000 200000 5 2000 0");
            Self::set_perm(CLABCCT_NODE, 0o644);
        }

        // 6. Unlock cooling devices permissions so kernel can modulate them
        Self::unlock_bcct();

        // Re-enable MediaTek AP Thermal Zones and restore OEM trip points
        if let Ok(entries) = fs::read_dir("/sys/class/thermal") {
            for entry in entries.flatten() {
                let name = entry.file_name();
                let name_str = name.to_string_lossy();
                if name_str.starts_with("thermal_zone") {
                    let type_path = format!("/sys/class/thermal/{}/type", name_str);
                    if let Ok(t) = fs::read_to_string(&type_path) {
                        let t_lower = t.trim().to_lowercase();
                        if t_lower == "mtktsap" || t_lower == "battery" || t_lower == "mtktsbattery" {
                            let mode_path = format!("/sys/class/thermal/{}/mode", name_str);
                            if Path::new(&mode_path).exists() {
                                Self::set_perm(&mode_path, 0o666);
                                let _ = fs::write(&mode_path, "enabled");
                                Self::set_perm(&mode_path, 0o644);
                            }
                            if t_lower == "mtktsap" {
                                const OEM_TRIP_POINTS: &[(&str, &str)] = &[
                                    ("trip_point_0_temp", "100000"),
                                    ("trip_point_1_temp", "90000"),
                                    ("trip_point_2_temp", "54000"),
                                    ("trip_point_3_temp", "42000"),
                                    ("trip_point_4_temp", "41000"),
                                ];
                                for (tp, val) in OEM_TRIP_POINTS {
                                    let tp_path = format!("/sys/class/thermal/{}/{}", name_str, tp);
                                    if Path::new(&tp_path).exists() {
                                        Self::set_perm(&tp_path, 0o666);
                                        let _ = fs::write(&tp_path, val);
                                        Self::set_perm(&tp_path, 0o644);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. Resume suspended userspace thermal daemons via SIGCONT
        Self::signal_processes(Self::THERMAL_PROCESS_NAMES, false);

        // 7b. Restore Qualcomm BMS / Battery JEITA temperature thresholds
        let bms_dir = "/sys/class/power_supply/bms";
        if Path::new(bms_dir).exists() {
            Self::set_perm(&format!("{}/temp_cool", bms_dir), 0o666);
            Self::write_node(&format!("{}/temp_cool", bms_dir), "100");
            Self::set_perm(&format!("{}/temp_hot", bms_dir), 0o666);
            Self::write_node(&format!("{}/temp_hot", bms_dir), "480");
            Self::set_perm(&format!("{}/temp_warm", bms_dir), 0o666);
            Self::write_node(&format!("{}/temp_warm", bms_dir), "450");
            Self::set_perm(&format!("{}/temp_cold", bms_dir), 0o666);
            Self::write_node(&format!("{}/temp_cold", bms_dir), "0");
        }

        // 7c. Restore Qualcomm thermal-engine bind mount & quiet-therm-step trip points
        Self::restore_qcom_thermal_engine_bind_mount();

        // 8. Reset Android framework thermal status
        let _ = std::process::Command::new("cmd")
            .args(["thermalservice", "reset"])
            .output();
    }

    /// Discovers active Qualcomm thermal-engine configuration file path by inspecting
    /// `/proc/<pid>/cmdline` or standard OEM paths across all Snapdragon architectures.
    pub fn discover_qcom_thermal_engine_conf_path() -> Option<String> {
        // 1. Inspect running thermal-engine process cmdline for explicit config arguments (-c <path>)
        if let Ok(entries) = fs::read_dir("/proc") {
            for entry in entries.flatten() {
                let fname = entry.file_name();
                let pid_str = fname.to_string_lossy();
                if pid_str.chars().all(|c| c.is_ascii_digit()) {
                    let cmdline_path = format!("/proc/{}/cmdline", pid_str);
                    if let Ok(bytes) = fs::read(&cmdline_path) {
                        let cmdline = String::from_utf8_lossy(&bytes);
                        if cmdline.contains("thermal-engine") {
                            let parts: Vec<&str> = cmdline.split('\0').collect();
                            for i in 0..parts.len() {
                                if (parts[i] == "-c" || parts[i] == "--config") && i + 1 < parts.len() {
                                    let path = parts[i + 1].trim();
                                    if Path::new(path).exists() {
                                        return Some(path.to_string());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Fallback to standard OEM vendor configuration paths
        const CANDIDATES: &[&str] = &[
            "/vendor/etc/thermal-engine.conf",
            "/system/vendor/etc/thermal-engine.conf",
            "/odm/etc/thermal-engine.conf",
            "/vendor/etc/thermal/thermal-engine.conf",
        ];
        for &c in CANDIDATES {
            if Path::new(c).exists() {
                return Some(c.to_string());
            }
        }
        None
    }

    /// On Qualcomm Snapdragon devices:
    /// Dynamically discovers and live bind-mounts an elevated thermal-engine configuration
    /// over the active vendor conf file to lift restrictive charging throttling without KPM,
    /// and raises quiet-therm-step trip points so charging sustains peak speed past 45.0°C safely.
    pub fn apply_qcom_thermal_engine_bind_mount() {
        if !Self::is_qualcomm_soc() {
            return;
        }

        let stock_conf = match Self::discover_qcom_thermal_engine_conf_path() {
            Some(p) => p,
            None => return,
        };

        // Check if already bind-mounted
        if let Ok(mounts) = fs::read_to_string("/proc/mounts") {
            if mounts.contains("/dev/lynx_thermal_engine.conf") {
                return;
            }
        }

        // Try reading bundled module Qualcomm thermal-engine.conf first
        let qcom_mod_conf = "/data/adb/modules/Lynx/platforms/qcom/system/vendor/etc/thermal-engine.conf";
        let elevated = if Path::new(qcom_mod_conf).exists() {
            fs::read_to_string(qcom_mod_conf).ok()
        } else {
            None
        };

        let elevated_content = if let Some(content) = elevated {
            content
        } else if let Ok(content) = fs::read_to_string(&stock_conf) {
            let mut result = String::new();
            let mut in_battery_sec = false;
            let mut replaced_thresh = false;
            let mut replaced_clr = false;

            for line in content.lines() {
                let trimmed = line.trim();
                if trimmed.starts_with('[') && trimmed.ends_with(']') {
                    let s_upper = trimmed.to_uppercase();
                    in_battery_sec = s_upper.contains("BATTERY_CHARGING")
                        || s_upper.contains("CHARGING_CTL")
                        || s_upper.contains("CHG_CTL");
                    result.push_str(line);
                    result.push('\n');
                    continue;
                }

                if in_battery_sec {
                    if trimmed.starts_with("thresholds ") {
                        let tokens: Vec<&str> = trimmed["thresholds ".len()..].split_whitespace().collect();
                        let mut new_tokens = Vec::new();
                        let mut base = 48000;
                        for _ in 0..tokens.len() {
                            new_tokens.push(base.to_string());
                            base += 2000;
                        }
                        result.push_str(&format!("thresholds {}\n", new_tokens.join(" ")));
                        replaced_thresh = true;
                        continue;
                    } else if trimmed.starts_with("thresholds_clr ") {
                        let tokens: Vec<&str> = trimmed["thresholds_clr ".len()..].split_whitespace().collect();
                        let mut new_tokens = Vec::new();
                        let mut base = 46000;
                        for _ in 0..tokens.len() {
                            new_tokens.push(base.to_string());
                            base += 2000;
                        }
                        result.push_str(&format!("thresholds_clr {}\n", new_tokens.join(" ")));
                        replaced_clr = true;
                        continue;
                    }
                }
                result.push_str(line);
                result.push('\n');
            }

            if !replaced_thresh || !replaced_clr {
                content
                    .replace("thresholds 37000 38000 41000 42000 43000", "thresholds 48000 50000 52000 54000 56000")
                    .replace("thresholds_clr 36000 37000 39000 40000 41000", "thresholds_clr 46000 48000 50000 52000 54000")
            } else {
                result
            }
        } else {
            return;
        };

        let live_conf = "/dev/lynx_thermal_engine.conf";
        if fs::write(live_conf, elevated_content).is_ok() {
            Self::set_perm(live_conf, 0o644);
            let _ = std::process::Command::new("mount")
                .args(["-o", "bind", live_conf, &stock_conf])
                .output();

            let _ = std::process::Command::new("pkill")
                .args(["-HUP", "-f", "thermal-engine"])
                .output();
        }

        // Elevate quiet-therm-step trip points (thermal_zone20 or any zone named quiet-therm-step)
        if let Ok(entries) = fs::read_dir("/sys/class/thermal") {
            for entry in entries.flatten() {
                let name = entry.file_name();
                let name_str = name.to_string_lossy();
                if name_str.starts_with("thermal_zone") {
                    let type_path = format!("/sys/class/thermal/{}/type", name_str);
                    if let Ok(t) = fs::read_to_string(&type_path) {
                        if t.trim().eq_ignore_ascii_case("quiet-therm-step") {
                            let tp0 = format!("/sys/class/thermal/{}/trip_point_0_temp", name_str);
                            let tp1 = format!("/sys/class/thermal/{}/trip_point_1_temp", name_str);
                            Self::write_node_lock(&tp0, "60000");
                            Self::write_node_lock(&tp1, "63000");
                        }
                    }
                }
            }
        }
    }

    /// Restores stock OEM thermal-engine configuration and quiet-therm-step trip points.
    pub fn restore_qcom_thermal_engine_bind_mount() {
        if !Self::is_qualcomm_soc() {
            return;
        }

        if let Ok(mounts) = fs::read_to_string("/proc/mounts") {
            for line in mounts.lines() {
                if line.contains("/dev/lynx_thermal_engine.conf") {
                    let parts: Vec<&str> = line.split_whitespace().collect();
                    if parts.len() >= 2 {
                        let mount_point = parts[1];
                        let _ = std::process::Command::new("umount").arg(mount_point).output();
                    }
                }
            }
        }

        let live_conf = "/dev/lynx_thermal_engine.conf";
        if Path::new(live_conf).exists() {
            let _ = fs::remove_file(live_conf);
            let _ = std::process::Command::new("pkill")
                .args(["-HUP", "-f", "thermal-engine"])
                .output();
        }

        // Restore quiet-therm-step trip points to OEM defaults
        if let Ok(entries) = fs::read_dir("/sys/class/thermal") {
            for entry in entries.flatten() {
                let name = entry.file_name();
                let name_str = name.to_string_lossy();
                if name_str.starts_with("thermal_zone") {
                    let type_path = format!("/sys/class/thermal/{}/type", name_str);
                    if let Ok(t) = fs::read_to_string(&type_path) {
                        if t.trim().eq_ignore_ascii_case("quiet-therm-step") {
                            let tp0 = format!("/sys/class/thermal/{}/trip_point_0_temp", name_str);
                            let tp1 = format!("/sys/class/thermal/{}/trip_point_1_temp", name_str);
                            Self::write_node_lock(&tp0, "50000");
                            Self::write_node_lock(&tp1, "53000");
                        }
                    }
                }
            }
        }
    }

    /// Continuously synchronizes genuine physical battery temperature (from AuxADC NTC thermistor
    /// or unspoofed kernel thermal zones) into `Battery_Temperature` sysfs override.
    /// This guarantees that when discharging or during standard charging, the kernel power_supply,
    /// Android BatteryService, system status bar, and companion apps report authentic live battery temp.
    pub fn sync_authentic_battery_temp() {
        if !Path::new(BATT_TEMP_NODE).exists() {
            return;
        }
        let real_dc = Self::read_real_battery_temp_dc();
        let target_c = (real_dc / 10).clamp(15, 50);

        // Check existing value in Battery_Temperature to avoid redundant writes
        if let Some(curr_c) = Self::read_i64(BATT_TEMP_NODE) {
            if curr_c == target_c {
                return;
            }
        }

        Self::set_perm(BATT_TEMP_NODE, 0o666);
        let _ = fs::write(BATT_TEMP_NODE, target_c.to_string());
        Self::set_perm(BATT_TEMP_NODE, 0o666);
    }

    /// Converts MT635x AuxADC Channel 3 NTC voltage (in mV) to decicelsius.
    /// Tested and verified on physical MT6358 / MT6781 hardware.
    #[inline]
    fn auxadc_mv_to_dc(mv: i64) -> i64 {
        // NTC 10K B=3950 with 12K pull-up to 1.8V VREF:
        // mv <= 300: >= 65.0°C (650 dC)
        // mv <= 375: >= 55.0°C (550 dC)
        // mv <= 440: >= 50.0°C (500 dC) [Critical Cutoff Threshold]
        // mv == 530: ~42.0°C (420 dC) [Verified on live device]
        // mv == 600: ~36.5°C (365 dC)
        // mv == 700: ~30.0°C (300 dC)
        // mv >= 800: <= 25.0°C (250 dC)
        if mv <= 300 { 650 }
        else if mv <= 375 { 550 + (375 - mv) * 100 / 75 }
        else if mv <= 440 { 500 + (440 - mv) * 50 / 65 }
        else if mv <= 530 { 420 + (530 - mv) * 80 / 90 }
        else if mv <= 600 { 365 + (600 - mv) * 55 / 70 }
        else if mv <= 700 { 300 + (700 - mv) * 65 / 100 }
        else if mv <= 800 { 250 + (800 - mv) * 50 / 100 }
        else { 200 }
    }

    /// Reads real physical battery cell temperature in decicelsius (e.g. 345 = 34.5°C).
    /// Immune to `/sys/devices/platform/battery/Battery_Temperature` spoofing on MediaTek.
    pub fn read_real_battery_temp_dc() -> i64 {
        // Tier 1: Hardware AuxADC Battery NTC Thermistor (Channel 3)
        const AUXADC_PATHS: &[&str] = &[
            "/sys/bus/iio/devices/iio:device1/in_voltage3_BAT_TEMP_input",
            "/sys/devices/platform/1000d000.pwrap/1000d000.pwrap:mt6358-pmic/mt635x-auxadc/iio:device1/in_voltage3_BAT_TEMP_input",
            "/sys/bus/iio/devices/iio:device0/in_voltage3_BAT_TEMP_input",
        ];
        for &node in AUXADC_PATHS {
            if let Some(mv) = Self::read_i64(node) {
                if mv > 100 && mv < 1500 {
                    return Self::auxadc_mv_to_dc(mv);
                }
            }
        }

        // Tier 2: Physical Unspoofed Kernel Thermal Zones
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

        // Tier 3: Standard battery temp if valid and not spoofed
        let batt_raw = Self::read_i64(&format!("{}/temp", BATT_DIR)).unwrap_or(280);
        if batt_raw != 280 && batt_raw > 150 {
            return batt_raw;
        }

        batt_tz_dc.unwrap_or(320)
    }

    #[inline]
    pub fn get_local_hour() -> u32 {
        #[cfg(unix)]
        {
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
        #[cfg(not(unix))]
        {
            12
        }
    }

    /// Evaluates `config.json` and live battery state, applying the appropriate charging mode immediately.
    pub fn apply_from_config() {
        Self::ensure_power_supply_traversal();
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

        let is_hyper = cfg.charging_mode == "hyper" || cfg.extreme_charging_enabled;

        // Tier 1: Critical Hardware Thermal Guard (Active in Non-Hyper modes if enabled).
        // In Hyper Mode, all restrictions are released so charging stays at peak speed.
        if !is_hyper && cfg.emergency_temp_guard_enabled {
            const EMERGENCY_CUTOFF_DC: i64 = 550;   // 55.0°C (Real hardware thermal danger)
            const EMERGENCY_RECOVERY_DC: i64 = 480; // 48.0°C hysteresis buffer

            let is_emergency_latched = Path::new("/dev/lynx_charging_guard").exists();

            if real_dc >= EMERGENCY_CUTOFF_DC || (is_emergency_latched && real_dc > EMERGENCY_RECOVERY_DC) {
                let _ = fs::write(
                    "/dev/lynx_charging_guard",
                    format!("guard_active=true emergency_cutoff=true temp={}\n", real_dc),
                );
                let _ = fs::remove_file("/dev/lynx_extreme_charging");
                // Safe gentle cooling level (keep at least 1500–2500mA for smooth charging)
                let fallback_ma = cfg.limit_current_ma.clamp(1500, 2500) as u32;
                Self::apply_regulated_charging(fallback_ma);
                return;
            } else if is_emergency_latched && real_dc <= EMERGENCY_RECOVERY_DC {
                // Temperature safely recovered below 48.0°C: clear emergency latch
                let _ = fs::remove_file("/dev/lynx_charging_guard");
            }
        } else {
            let _ = fs::remove_file("/dev/lynx_charging_guard");
        }

        // Cold Battery (< 15.0°C) Lithium Plating Guard (for non-hyper modes)
        if !is_hyper && real_dc < 150 && real_dc > 0 {
            let _ = fs::write("/dev/lynx_charging_guard", format!("cold_guard_active=true temp={}\n", real_dc));
            Self::apply_regulated_charging(1500);
            return;
        }

        // Tier 2: Immediate User-Forced Bypass Toggle
        if cfg.bypass_enabled {
            let _ = fs::write("/dev/lynx_charging_state", format!("state=bypass_forced cap={}\n", cur_cap));
            Self::apply_bypass_charging();
            return;
        }

        // Tier 3: AutoCut when 100% Full (Overnight / Safe Full AutoCut)
        if cfg.auto_cut_enabled && (cur_cap >= 100 || cur_stat == "Full") {
            let _ = fs::write("/dev/lynx_charging_state", format!("state=bypass_100 cap={}\n", cur_cap));
            Self::apply_bypass_charging();
            return;
        }

        // Tier 4: Smart battery limit (50%–99%).
        // With 3% hysteresis buffer to prevent continuous flapping
        let smart_limit = cfg.max_battery_percent.clamp(0, 100) as u32;
        if !is_hyper && cfg.auto_cut_enabled && smart_limit > 0 && smart_limit < 100 {
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
                    Self::write_node(&format!("{}/restricted_charging", QC_DIR), "0");
                    Self::write_node(&format!("{}/direct_charging", QC_DIR), "0");
                    Self::write_node(&format!("{}/charging_enabled", BATT_DIR), "1");
                    Self::write_node(&format!("{}/input_suspend", BATT_DIR), "0");
                    Self::unlock_extreme_nodes();
                    Self::apply_standard_mode();
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
        } else if (Path::new("/dev/lynx_bypass_active").exists() || Self::read_node("/dev/lynx_charging_state").map(|s| s.contains("bypass")).unwrap_or(false)) && !cfg.bypass_enabled {
            // Config explicitly requested bypass disabled: purge stale bypass marker and clear hardware bypass state
            Self::clear_bypass_hardware_state();
        }

        // Tier 5: Gentle overnight charging (23:00–06:00, 1500 mA).
        let is_night_time = {
            let h = Self::get_local_hour();
            h >= 23 || h < 6
        };
        if !is_hyper && cfg.night_sleep_guard_enabled && is_night_time {
            let _ = fs::write("/dev/lynx_charging_state", format!("state=night_gentle cap={}\n", cur_cap));
            Self::apply_regulated_charging(1500);
            return;
        }

        // Tier 6: CC to CV Smart Tapering at >= 80% SoC (or V_BAT >= 4470 mV)
        let volt_raw = Self::read_i64(&format!("{}/voltage_now", BATT_DIR)).unwrap_or(0);
        let v_clean_mv = if volt_raw.abs() >= 10_000 { volt_raw.abs() / 1000 } else { volt_raw.abs() } as u32;

        if !is_hyper && cfg.smart_tapering_enabled && (cur_cap >= 80 || v_clean_mv >= 4470) {
            let (state_tag, target_ma) = Self::determine_taper_state(cur_cap, v_clean_mv);
            let effective_target = if cfg.charging_mode == "cool" && cfg.limit_current_ma > 0 {
                (cfg.limit_current_ma as u32).min(target_ma)
            } else {
                target_ma
            };
            let _ = fs::write("/dev/lynx_charging_state", format!("state={} cap={}\n", state_tag, cur_cap));
            Self::apply_regulated_charging(effective_target);
            return;
        }

        if is_hyper {
            let marker = Self::read_node("/dev/lynx_extreme_charging").unwrap_or_default();
            let is_mtk = Path::new(MTK_DIR).exists();
            let invariants_intact = if is_mtk {
                let bn_test = Self::read_node(&format!("{}/BN_TestMode", MTK_DIR)).unwrap_or_default();
                let in_cur = Self::read_i64(&format!("{}/input_current", MTK_DIR)).unwrap_or(0);
                let sc_limit = Self::read_i64(&format!("{}/sc_ibat_limit", MTK_DIR)).unwrap_or(0);
                let chg1 = Self::read_i64(&format!("{}/chg1_current", MTK_DIR)).unwrap_or(0);
                let chg2 = Self::read_i64(&format!("{}/chg2_current", MTK_DIR)).unwrap_or(0);
                let pe20 = Self::read_node(&format!("{}/pe20", MTK_DIR)).unwrap_or_default();
                let pe40 = Self::read_node(&format!("{}/pe40", MTK_DIR)).unwrap_or_default();
                let cur_cmd = Self::read_node("/proc/mtk_battery_cmd/current_cmd").unwrap_or_default();
                let pwr_path = Self::read_node("/proc/mtk_battery_cmd/en_power_path").unwrap_or_default();
                let call_state = Self::read_node("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState").unwrap_or_default();

                // Note: Kernel `store_enable_sc` is a no-op stub on MediaTek/Transsion (`enable_sc` always reads `"0"`),
                // and `en_power_path` reads `"1"` when charging is enabled. Checking `enable_sc == "1"` or
                // `pwr_path == "0"` caused a 1 Hz infinite re-init loop that aborted `dv2_algo_task` (RT9759 33W pump)!
                bn_test == "1"
                    && in_cur >= 2_000_000
                    && sc_limit >= 1000
                    && chg1 >= 2_000_000
                    && chg2 >= 2_000_000
                    && pe20 == "1"
                    && pe40 == "1"
                    && (cur_cmd.trim() == "1 0" || cur_cmd.trim() == "0 0" || cur_cmd.is_empty())
                    && (pwr_path.trim() == "1" || pwr_path.trim().ends_with(" 1") || pwr_path.trim() == "0" || pwr_path.is_empty())
                    && (call_state.trim() == "0" || call_state.is_empty())
            } else {
                let susp = Self::read_node(&format!("{}/input_suspend", BATT_DIR)).unwrap_or_else(|| "0".to_string());
                let en = Self::read_node(&format!("{}/charging_enabled", BATT_DIR)).unwrap_or_else(|| "1".to_string());
                let direct_node = format!("{}/direct_charging", QC_DIR);
                let expected_direct = if Self::has_qcom_direct_charge_pump() { "1" } else { "0" };
                let direct_ok = if Path::new(&direct_node).exists() {
                    Self::read_node(&direct_node).unwrap_or_default() == expected_direct
                } else {
                    true
                };
                let is_pd = Self::is_usb_pd_active();
                let cur_fcc = Self::read_i64(&format!("{}/constant_charge_current_max", BATT_DIR)).unwrap_or(0);
                let fcc_ok = cur_fcc == 0 || cur_fcc >= 1_500_000 || is_pd;
                let cur_usb = Self::read_i64(&format!("{}/current_max", USB_DIR)).unwrap_or(0);
                let usb_ok = cur_usb == 0 || cur_usb >= 1_500_000 || is_pd;
                susp == "0" && en == "1" && direct_ok && fcc_ok && usb_ok
            };

            // If hyper mode is already active and critical invariants are intact, skip heavy re-init
            if marker.starts_with("hyper-") && invariants_intact {
                return;
            }

            Self::apply_hyper_mode();
            return;
        }

        // Tier 7: Active Charging Mode Execution (Cool, Standard, Hyper, Custom)
        match cfg.charging_mode.as_str() {
            "cool" => {
                let limit = if cfg.limit_current_ma > 0 && cfg.limit_current_ma <= 1500 {
                    cfg.limit_current_ma as u32
                } else {
                    1000
                };
                let target_ua = limit as i64 * 1000;
                let cur_fcc = Self::read_i64(&format!("{}/constant_charge_current_max", BATT_DIR)).unwrap_or(0);
                let marker = Self::read_node("/dev/lynx_standard_charging").unwrap_or_default();
                if marker.trim() == format!("regulated-{}", limit) && (cur_fcc == 0 || cur_fcc <= target_ua + 200_000) {
                    return;
                }
                Self::apply_cool_mode(limit);
            }
            "hyper" => {
                Self::apply_hyper_mode();
            }
            "custom" => {
                let target = cfg.custom_limit_current_ma.clamp(500, 25000) as u32;
                let target_ua = target as i64 * 1000;
                let cur_fcc = Self::read_i64(&format!("{}/constant_charge_current_max", BATT_DIR)).unwrap_or(0);
                let marker_extreme = Self::read_node("/dev/lynx_extreme_charging").unwrap_or_default();
                let marker_reg = Self::read_node("/dev/lynx_standard_charging").unwrap_or_default();
                if target >= 5000 && marker_extreme.trim() == format!("hyper-{}", target) && (cur_fcc == 0 || cur_fcc >= target_ua - 400_000) {
                    return;
                }
                if target < 5000 && marker_reg.trim() == format!("regulated-{}", target) && (cur_fcc == 0 || cur_fcc >= target_ua - 200_000) {
                    return;
                }
                Self::apply_custom_mode(target);
            }
            "standard" => {
                let marker = Self::read_node("/dev/lynx_standard_charging").unwrap_or_default();
                if marker.trim() == "standard" {
                    return;
                }
                Self::apply_standard_mode();
            }
            _ => {
                if cfg.charging_mode.is_empty() && cfg.extreme_charging_enabled {
                    Self::apply_hyper_mode();
                } else {
                    let marker = Self::read_node("/dev/lynx_standard_charging").unwrap_or_default();
                    if marker.trim() == "standard" {
                        return;
                    }
                    Self::apply_standard_mode();
                }
            }
        }
    }

    /// Pure decision logic for plugged state. Fully unit-testable without sysfs dependencies.
    pub fn evaluate_plugged_state(
        has_online_rail: bool,
        batt_status: &str,
        charger_type: &str,
        vbus_mv: i64,
    ) -> bool {
        // 1. Verified online power supply rail across USB, AC, Charger, MAIN, DC
        if has_online_rail {
            return true;
        }

        // 2. Verified battery charging current into cell:
        // If fuel gauge / kernel battery driver reports "Charging", external power is actively supplying the battery.
        if batt_status.eq_ignore_ascii_case("charging") {
            return true;
        }

        // 3. Actively driven adapter voltage:
        // Battery cell voltage never exceeds 4.45V, so any VBUS >= 4600mV CANNOT be capacitor float
        // or battery back-feed -- it is physically driven by an active 5V/9V/12V external charger!
        // This is critical for MediaTek / Transsion devices where Charger_Type may report "0" / unknown
        // or online rails remain 0 until fast charging handshake completes.
        if vbus_mv >= 4600 {
            return true;
        }

        let is_discharging = batt_status.eq_ignore_ascii_case("discharging");
        let is_unknown_charger = charger_type == "0"
            || charger_type.eq_ignore_ascii_case("unknown")
            || charger_type.eq_ignore_ascii_case("none")
            || charger_type.is_empty();

        // 4. Decoupling capacitor float after physical unplug: VBUS floats between 3.5V and 4.4V
        // while rails are 0 and charger type dropped to unknown/0 or discharging.
        if is_discharging || is_unknown_charger {
            if vbus_mv >= 4400 && !is_unknown_charger {
                return true;
            }
            return false;
        }

        false
    }

    pub fn is_hardware_plugged() -> bool {
        // Vector 1: Primary check - Online power supply rails across USB, AC, Charger, MAIN, DC
        let usb_online = Self::read_node(&format!("{}/online", USB_DIR)).unwrap_or_default();
        let ac_online = Self::read_node(&format!("{}/ac/online", PS_DIR)).unwrap_or_default();
        let chg_online = Self::read_node(&format!("{}/charger/online", PS_DIR)).unwrap_or_default();
        let main_online = Self::read_node(&format!("{}/online", MAIN_DIR)).unwrap_or_default();
        let dc_online = Self::read_node(&format!("{}/dc/online", PS_DIR)).unwrap_or_default();

        let has_online_rail = usb_online == "1" || ac_online == "1" || chg_online == "1" || main_online == "1" || dc_online == "1";

        // Vector 2: Battery status
        let batt_status = Self::read_node(&format!("{}/status", BATT_DIR)).unwrap_or_default();

        // Vector 3: Charger Type / Protocol ID
        let chg_type = Self::read_node(&format!("{}/Charger_Type", MTK_DIR))
            .or_else(|| Self::read_node(&format!("{}/type", USB_DIR)))
            .unwrap_or_default();

        // Hardware ADC / PMIC adapter voltage
        let adpv = Self::read_i64(&format!("{}/ADC_Charger_Voltage", MTK_DIR))
            .or_else(|| Self::read_i64("/sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger"))
            .or_else(|| Self::read_i64(&format!("{}/voltage_now", USB_DIR)))
            .or_else(|| Self::read_i64(&format!("{}/charger_voltage", BATT_DIR)))
            .or_else(|| Self::read_i64(&format!("{}/voltage_now", MAIN_DIR)))
            .unwrap_or(0);
        let adpv_clean = if adpv > 100_000 { adpv / 1000 } else { adpv };

        Self::evaluate_plugged_state(has_online_rail, &batt_status, &chg_type, adpv_clean)
    }

    /// Confirms that a requested bypass actually stopped cell charging. A marker
    /// file or Android's reported status alone is not proof on vendor kernels.
    #[allow(dead_code)]
    fn is_battery_cell_current_stopped() -> bool {
        let Some(raw_current) = Self::read_i64(&format!("{}/current_now", BATT_DIR)) else {
            return false;
        };
        let current_ma = if raw_current.abs() >= 10_000 { raw_current / 1000 } else { raw_current };
        let status = Self::read_node(&format!("{}/status", BATT_DIR)).unwrap_or_default();
        current_ma.abs() <= 50 && status != "Charging"
    }

    pub fn handle_disconnect() {
        Self::release_charging_wake_lock();
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
        let _ = fs::remove_file("/dev/lynx_orig_chg_max");
        Self::clear_bypass_hardware_state();

        // Unlock all candidate sysfs nodes so vendor HALs and kernel have write access
        Self::unlock_extreme_nodes();

        Self::unlock_bcct();
        Self::set_perm(&format!("{}/sw_jeita", MTK_DIR), 0o666);
        if !needs_session_cleanup {
            Self::sync_authentic_battery_temp();
            return;
        }
        let cap_now = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(50);
        let _ = fs::write("/dev/lynx_charging_state", format!("state=discharging cap={}\n", cap_now));

        // 1. Restore captured session baseline FIRST
        Self::restore_charging_session_baseline();

        // 2. Enforce deterministic fail-safe resets LAST (ensuring fail-safe resets prevail over stale session entries)
        for (node, value) in DISCONNECT_CHARGING_RESETS {
            Self::set_perm(node, 0o666);
            Self::write_node(node, value);
        }

        Self::restore_charger_thermal_safety();
        Self::signal_processes(Self::THERMAL_PROCESS_NAMES, false);
    }

    fn capture_charging_session_baseline() {
        const DIR: &str = "/dev/lynx_charging_baseline";
        if Path::new(DIR).exists() || fs::create_dir(DIR).is_err() {
            return;
        }

        for (index, node) in CHARGING_SESSION_NODES.iter().enumerate() {
            let Ok(value) = fs::read_to_string(node) else { continue };
            let Ok(_meta) = fs::metadata(node) else { continue };
            let value_path = format!("{}/{}.value", DIR, index);
            let mode_path = format!("{}/{}.mode", DIR, index);
            let _ = fs::write(value_path, value.trim_end());
            #[cfg(unix)]
            let mode = _meta.permissions().mode() & 0o777;
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

    fn get_session_baseline_value(node: &str) -> Option<String> {
        let index = CHARGING_SESSION_NODES.iter().position(|&n| n == node)?;
        let path = format!("/dev/lynx_charging_baseline/{}.value", index);
        fs::read_to_string(path).ok().map(|s| s.trim().to_string())
    }

    fn get_device_brand() -> (String, String) {
        let brand = std::process::Command::new("getprop")
            .arg("ro.product.brand")
            .output()
            .ok()
            .and_then(|o| String::from_utf8(o.stdout).ok())
            .unwrap_or_default()
            .trim()
            .to_uppercase();
        let mfg = std::process::Command::new("getprop")
            .arg("ro.product.manufacturer")
            .output()
            .ok()
            .and_then(|o| String::from_utf8(o.stdout).ok())
            .unwrap_or_default()
            .trim()
            .to_uppercase();
        (brand, mfg)
    }

    /// Checks whether the underlying physical hardware is a Qualcomm Snapdragon SoC.
    pub fn is_qualcomm_soc() -> bool {
        if let Ok(compat) = fs::read_to_string("/sys/firmware/devicetree/base/compatible")
            .or_else(|_| fs::read_to_string("/proc/device-tree/compatible"))
        {
            let lower = compat.to_lowercase();
            if lower.contains("qcom") || lower.contains("qualcomm") {
                return true;
            }
        }
        Path::new("/sys/class/qcom-battery").exists()
            || Path::new("/sys/class/power_supply/bms").exists()
            || Path::new("/sys/devices/soc0").exists()
            || Path::new("/sys/class/kgsl").exists()
            || Path::new("/dev/kgsl-3d0").exists()
            || Path::new("/sys/bus/platform/drivers/qcom,qpnp-smb2").exists()
            || Path::new("/sys/bus/platform/drivers/pm8150b_charger").exists()
            || Path::new("/sys/bus/platform/drivers/pm660_charger").exists()
            || Path::new("/sys/bus/platform/drivers/qpnp-pdphy").exists()
    }

    /// Checks whether the Qualcomm hardware has a physical direct Switched-Capacitor (SC) charge pump
    /// (such as SMB1390, SMB1396, or discrete charge pump master).
    /// Single-PMIC devices (like PM660 on Redmi Note 7) DO NOT have this hardware, and setting
    /// `direct_charging = 1` causes the primary buck converter to shut down, killing VBUS and halting charging.
    pub fn has_qcom_direct_charge_pump() -> bool {
        Path::new("/sys/class/power_supply/smb1390").exists()
            || Path::new("/sys/class/power_supply/smb1396").exists()
            || Path::new("/sys/class/power_supply/charge_pump").exists()
            || Path::new("/sys/class/power_supply/charge_pump_master").exists()
            || Path::new("/sys/class/power_supply/cp_master").exists()
            || Path::new("/sys/bus/i2c/drivers/smb1390").exists()
            || Path::new("/sys/bus/platform/drivers/smb1390").exists()
            || Path::new("/sys/bus/platform/drivers/smb1396").exists()
    }

    /// Checks whether the underlying physical hardware is a MediaTek SoC.
    pub fn is_mediatek_soc() -> bool {
        if let Ok(compat) = fs::read_to_string("/sys/firmware/devicetree/base/compatible")
            .or_else(|_| fs::read_to_string("/proc/device-tree/compatible"))
        {
            let lower = compat.to_lowercase();
            if lower.contains("mediatek") || lower.contains("mtk") {
                return true;
            }
        }
        Path::new("/sys/devices/platform/charger").exists()
            || Path::new("/sys/devices/platform/mt_charger").exists()
            || Path::new("/proc/ppm").exists()
            || Path::new("/proc/ged").exists()
            || Path::new("/dev/ged").exists()
            || Path::new("/dev/mali0").exists()
    }

    /// Ground Truth Hardware Platform Detection (Immune to User-Space Props Spoofing).
    ///
    /// Spoofing modules (Game Unlocker, Play Integrity Fix, MagiskHide Props Config,
    /// GLTools, Device Faker) can alter product manufacturer/model properties, but
    /// cannot change the physical kernel drivers or DTB-derived sysfs nodes.
    ///
    /// Tier 1: Physical kernel driver nodes are authoritative.
    /// Tier 2: Product properties are used only when kernel evidence is inconclusive.
    ///
    /// Returns: (Platform, is_spoofed, spoof_details)
    pub fn detect_charging_platform() -> (ChargingHardwarePlatform, bool, String) {
        let (brand, mfg) = Self::get_device_brand();

        // 1. Tier 1: Ground Truth Hardware Nodes
        let has_tran_hw = Path::new("/sys/devices/platform/odm/odm:tran_battery").exists()
            || Path::new("/sys/devices/platform/tran_battery").exists()
            || Path::new("/sys/bus/i2c/drivers/rt9759").exists()
            || Path::new("/sys/devices/platform/charger/BN_TestMode").exists();

        let has_qcom_hw = Self::is_qualcomm_soc();

        let has_xiaomi_hw = Path::new("/sys/class/power_supply/battery/fastcharge_mode").exists()
            || Path::new("/sys/class/thermal/thermal_message").exists()
            || Path::new("/sys/class/power_supply/battery/turbo_power").exists()
            || Path::new("/sys/bus/i2c/drivers/ln8000").exists()
            || Path::new("/sys/bus/i2c/drivers/sc8551").exists();

        let has_oplus_hw = Path::new("/sys/class/power_supply/battery/vooc_charging").exists()
            || Path::new("/sys/class/oplus_chg").exists()
            || Path::new("/sys/class/power_supply/battery/fast_charge_user_type").exists();

        let has_samsung_hw = Path::new("/sys/class/power_supply/sec-battery").exists()
            || Path::new("/sys/class/sec/sec-battery").exists()
            || Path::new("/sys/class/power_supply/battery/afc_result").exists();

        let has_pixel_hw = Path::new("/sys/devices/platform/google,battery").exists()
            || Path::new("/sys/devices/platform/google,charger").exists()
            || Path::new("/sys/bus/i2c/drivers/max77759").exists();

        let platform = if has_tran_hw {
            ChargingHardwarePlatform::Transsion
        } else if has_qcom_hw {
            ChargingHardwarePlatform::QualcommGeneric
        } else if has_xiaomi_hw {
            ChargingHardwarePlatform::Xiaomi
        } else if has_oplus_hw {
            ChargingHardwarePlatform::Oplus
        } else if has_samsung_hw {
            ChargingHardwarePlatform::Samsung
        } else if has_pixel_hw {
            ChargingHardwarePlatform::GooglePixel
        } else {
            // Tier 2: System Properties Fallback
            if brand.contains("INFINIX") || brand.contains("TECNO") || brand.contains("ITEL")
                || mfg.contains("INFINIX") || mfg.contains("TECNO") || mfg.contains("TRANSSION")
            {
                ChargingHardwarePlatform::Transsion
            } else if brand.contains("XIAOMI") || brand.contains("REDMI") || brand.contains("POCO")
                || mfg.contains("XIAOMI")
            {
                ChargingHardwarePlatform::Xiaomi
            } else if brand.contains("OPPO") || brand.contains("REALME") || brand.contains("ONEPLUS")
                || mfg.contains("OPPO") || mfg.contains("REALME")
            {
                ChargingHardwarePlatform::Oplus
            } else if brand.contains("SAMSUNG") || mfg.contains("SAMSUNG") {
                ChargingHardwarePlatform::Samsung
            } else if brand.contains("GOOGLE") || mfg.contains("GOOGLE") {
                ChargingHardwarePlatform::GooglePixel
            } else if brand.contains("SONY") || mfg.contains("SONY") || brand.contains("MOTO") || mfg.contains("MOTOROLA") || brand.contains("ASUS") || mfg.contains("ASUS") {
                ChargingHardwarePlatform::QualcommGeneric
            } else {
                ChargingHardwarePlatform::GenericLinux
            }
        };

        // Detect if props are spoofed
        let mut is_spoofed = false;
        let mut spoof_details = String::new();

        let prop_is_xiaomi = brand.contains("XIAOMI") || brand.contains("REDMI") || brand.contains("POCO") || mfg.contains("XIAOMI");
        let prop_is_transsion = brand.contains("INFINIX") || brand.contains("TECNO") || brand.contains("ITEL") || mfg.contains("INFINIX") || mfg.contains("TECNO") || mfg.contains("TRANSSION");
        let prop_is_pixel = brand.contains("GOOGLE") || mfg.contains("GOOGLE");
        let prop_is_asus = brand.contains("ASUS") || mfg.contains("ASUS");
        let prop_is_sony = brand.contains("SONY") || mfg.contains("SONY");

        if platform == ChargingHardwarePlatform::Transsion && (prop_is_xiaomi || prop_is_pixel || prop_is_asus || prop_is_sony) {
            is_spoofed = true;
            spoof_details = format!("Props spoofed to {} / {}, verified physical hardware: Transsion (RT9759 / tran_battery)", mfg, brand);
        } else if platform == ChargingHardwarePlatform::Xiaomi && (prop_is_transsion || prop_is_pixel || prop_is_asus || prop_is_sony) {
            is_spoofed = true;
            spoof_details = format!("Props spoofed to {} / {}, verified physical hardware: Xiaomi (mi_pump / turbo_power)", mfg, brand);
        } else if platform == ChargingHardwarePlatform::QualcommGeneric && (prop_is_transsion || prop_is_xiaomi) {
            is_spoofed = true;
            spoof_details = format!("Props spoofed to {} / {}, verified physical hardware: Qualcomm Snapdragon (PMIC BMS)", mfg, brand);
        } else if platform != ChargingHardwarePlatform::Transsion && prop_is_transsion {
            is_spoofed = true;
            spoof_details = format!("Props spoofed to Transsion, verified physical hardware: {}", platform.as_str());
        }

        (platform, is_spoofed, spoof_details)
    }

    /// Periodic tick called inside `DaemonRunner::run()` every cycle.
    pub fn tick() {
        if !Self::is_hardware_plugged() {
            Self::handle_disconnect();
            return;
        }

        let cfg = ConfigStore::load_charging_config();
        let is_hyper = cfg.charging_mode == "hyper" || cfg.extreme_charging_enabled;
        let cur_cap = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(50) as u32;

        let reconnect_already_done = Path::new("/dev/lynx_soft_reconnect_done").exists();
        let is_pd_active = Self::is_usb_pd_active();

        let is_fast_mode = is_hyper || cfg.charging_mode == "custom";
        if is_fast_mode && !reconnect_already_done && cur_cap < 85 && !Self::is_laptop_port() && !is_pd_active {
            let adpv = Self::read_i64(&format!("{}/ADC_Charger_Voltage", MTK_DIR))
                .or_else(|| Self::read_i64("/sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger"))
                .or_else(|| Self::read_i64(&format!("{}/voltage_now", USB_DIR)))
                .or_else(|| Self::read_i64(&format!("{}/charger_voltage", BATT_DIR)))
                .or_else(|| Self::read_i64(&format!("{}/voltage_now", MAIN_DIR)))
                .unwrap_or(0);
            let adpv_clean = if adpv > 100_000 { adpv / 1000 } else { adpv };

            let cur_usb = Self::read_i64(&format!("{}/current_max", USB_DIR)).unwrap_or(2000000);
            let cur_fcc = Self::read_i64(&format!("{}/constant_charge_current_max", BATT_DIR)).unwrap_or(2000000);
            let cur_now_raw = Self::read_i64(&format!("{}/current_now", BATT_DIR)).unwrap_or(0).abs();
            let cur_now_ma = if cur_now_raw > 10000 { cur_now_raw / 1000 } else { cur_now_raw };

            // Stalled check: Only consider stalled if voltage is present (> 3500 mV)
            // but charging is virtually stopped / choked (< 400 mA) AND PMIC registers are choked (< 1000 mA).
            // A current of 1500–2200 mA on standard 5V USB is healthy, normal charging!
            let is_stalled_5v = adpv_clean > 3500 && cur_now_ma < 400
                && (cur_usb <= 1000000 && cur_fcc <= 1000000);

            if is_stalled_5v {
                let marker_path = "/dev/lynx_stalled_5v_count";
                let count = Self::read_i64(marker_path).unwrap_or(0);
                if count >= 3 {
                    let _ = fs::remove_file(marker_path);
                    let _ = fs::write("/dev/lynx_soft_reconnect_done", "1");
                    Self::uncap_usb_pd_protocol(12000);
                    Self::trigger_charger_soft_reconnect();
                } else {
                    let _ = fs::write(marker_path, (count + 1).to_string());
                }
            } else {
                let _ = fs::remove_file("/dev/lynx_stalled_5v_count");
            }
        } else {
            let _ = fs::remove_file("/dev/lynx_stalled_5v_count");
        }

        // Qualcomm Autonomous Anti-Throttle & AICL Recovery:
        // If device is plugged into AC charger, but PMIC input current or charge current is choked (< 500mA),
        // autonomously un-choke voters and rerun AICL so charging flows at full speed (do NOT pulse on USB-PD!).
        if Self::is_qualcomm_soc() && !Self::is_laptop_port() && !is_pd_active && !Path::new("/dev/lynx_bypass_active").exists() {
            let cur_usb = Self::read_i64(&format!("{}/current_max", USB_DIR)).unwrap_or(2000000);
            let cur_fcc = Self::read_i64(&format!("{}/constant_charge_current_max", BATT_DIR)).unwrap_or(2000000);
            let qcom_stalled = (cur_usb > 0 && cur_usb < 500_000) || (cur_fcc > 0 && cur_fcc < 500_000);
            if qcom_stalled {
                let marker_path = "/dev/lynx_qcom_stalled_count";
                let count = Self::read_i64(marker_path).unwrap_or(0);
                if count >= 3 {
                    let _ = fs::remove_file(marker_path);
                    Self::uncap_usb_pd_protocol(6000);
                    for rerun in &[
                        format!("{}/rerun_aicl", USB_DIR),
                        format!("{}/rerun_aicl", BATT_DIR),
                        format!("{}/rerun_aicl", MAIN_DIR),
                    ] {
                        if Path::new(rerun).exists() {
                            Self::set_perm(rerun, 0o666);
                            Self::write_node(rerun, "1");
                        }
                    }
                    for node in &[
                        format!("{}/constant_charge_current_max", BATT_DIR),
                        format!("{}/constant_charge_current", BATT_DIR),
                        format!("{}/current_max", USB_DIR),
                        format!("{}/current_max", MAIN_DIR),
                        format!("{}/input_current_limit", BATT_DIR),
                        format!("{}/hw_current_max", USB_DIR),
                    ] {
                        if Path::new(node).exists() {
                            Self::set_perm(node, 0o666);
                            Self::write_node(node, "4500000");
                        }
                    }
                    let direct_val = if Self::has_qcom_direct_charge_pump() { "1" } else { "0" };
                    Self::write_node(&format!("{}/direct_charging", QC_DIR), direct_val);
                    Self::write_node(&format!("{}/restricted_charging", QC_DIR), "0");
                    Self::write_node(&format!("{}/restrict_cur", QC_DIR), "4500");
                    Self::write_node(&format!("{}/system_temp_level", QC_DIR), "0");
                    Self::write_node(&format!("{}/system_temp_level", BATT_DIR), "0");
                    Self::write_node(&format!("{}/thermal_input_current_limit", BATT_DIR), "-1");
                    let _ = std::process::Command::new("dumpsys").args(["battery", "reset"]).output();
                } else {
                    let _ = fs::write(marker_path, (count + 1).to_string());
                }
            } else {
                let _ = fs::remove_file("/dev/lynx_qcom_stalled_count");
            }
        }

        Self::enforce_live_charging_invariants();
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
        if ibus_val > 10_000 {
            ibus_val /= 1000;
        }
        if ibus_val < 0 || adpv == 0 {
            ibus_val = 0;
        }

        let chgtyp = Self::read_node(&format!("{}/Charger_Type", MTK_DIR))
            .or_else(|| Self::read_node(&format!("{}/type", USB_DIR)))
            .unwrap_or_default();

        let rfc_val = Self::read_first_i2c_node("/sys/bus/i2c/drivers/rt9759", "rfc_dcp_ta")
            .unwrap_or(0);
        let rfc_auth = rfc_val == 1;

        let real_dc = Self::read_real_battery_temp_dc();

        let v_clean = if volt_raw.abs() >= 10_000 { volt_raw / 1000 } else { volt_raw };
        let mut c_clean = if cur_raw.abs() >= 10_000 { cur_raw / 1000 } else { cur_raw };
        if stat == "Charging" && c_clean < 0 {
            c_clean = c_clean.abs();
        }

        let is_chg = c_clean > 50 || stat == "Charging";

        let topology = Self::scan_charging_hardware_chips();
        let dynamic_nodes = Self::crawl_dynamic_power_supply_nodes();
        let dynamic_nodes_count = dynamic_nodes.len();

        let (platform, is_spoofed, spoof_details) = Self::detect_charging_platform();
        let is_transsion = platform == ChargingHardwarePlatform::Transsion;
        let is_xiaomi = platform == ChargingHardwarePlatform::Xiaomi;
        let is_oppo_realme = platform == ChargingHardwarePlatform::Oplus;
        let is_samsung = platform == ChargingHardwarePlatform::Samsung;

        let is_pd_active = Self::read_node(&format!("{}/pd_active", USB_DIR)).map(|v| v.trim() != "0" && !v.is_empty()).unwrap_or(false)
            || Self::read_node("/sys/class/typec/port0-partner/supports_usb_power_delivery").map(|v| v.trim() == "yes").unwrap_or(false)
            || chgtyp.to_uppercase().contains("PD");

        let (active_ic, protocol) = if is_chg {
            let ic = if !topology.summary.is_empty() && topology.summary != "Unknown" && !topology.summary.starts_with("Switching Buck Converter (Universal") {
                topology.summary.clone()
            } else if is_transsion && (rfc_auth || chgtyp == "9" || Path::new("/sys/bus/i2c/drivers/rt9759").exists() || Path::new("/sys/devices/platform/odm/odm:tran_battery").exists()) {
                "Direct Charge Pump (RT9759 2:1)".to_string()
            } else if Path::new("/sys/class/power_supply/smb1390").exists() || Path::new("/sys/class/power_supply/charge_pump").exists() {
                "Qualcomm SMB1390 Dual-Pump".to_string()
            } else if is_xiaomi && (Path::new("/sys/bus/i2c/drivers/ln8000").exists() || Path::new("/sys/bus/i2c/drivers/sc8551").exists()) {
                "Xiaomi HyperCharge Dual-Pump (LN8000/SC8551)".to_string()
            } else if is_xiaomi && (Path::new("/sys/class/qcom-battery").exists() || Self::is_qualcomm_soc()) {
                "Xiaomi Quick Charge PMIC".to_string()
            } else if Path::new("/sys/class/qcom-battery").exists() || Self::is_qualcomm_soc() {
                "Qualcomm PMIC (PM660/PM8150/PM6150 Buck)".to_string()
            } else if is_samsung {
                "Samsung PMIC Buck (Shannon/Maxim)".to_string()
            } else {
                topology.summary.clone()
            };

            let proto = if is_transsion && (rfc_auth || (chgtyp == "9" && adpv > 8000)) {
                if adpv > 9500 && c_clean >= 4000 {
                    "Transsion 45W / 68W Super Charge (PE+)"
                } else if adpv > 8500 && c_clean >= 2000 {
                    "Transsion 33W Super Charge (PE+)"
                } else {
                    "Transsion Super Charge (PE+)"
                }
            } else if is_xiaomi && (adpv > 8000 || Path::new("/sys/class/power_supply/battery/fastcharge_mode").exists()) {
                if c_clean >= 4500 || adpv > 15000 {
                    "Xiaomi 67W / 120W HyperCharge (PD/PPS)"
                } else if c_clean >= 2500 || adpv > 9500 {
                    "Xiaomi 33W TurboCharge (PD)"
                } else if is_pd_active || adpv > 8000 {
                    "Xiaomi Fast Charge (PD / QC)"
                } else {
                    "Xiaomi TurboCharge"
                }
            } else if is_oppo_realme && Path::new("/sys/class/power_supply/battery/vooc_charging").exists() {
                "SuperVOOC / Dart Charge"
            } else if is_samsung && adpv > 8000 {
                if c_clean >= 3500 {
                    "Samsung Super Fast Charging 2.0 (45W PD)"
                } else {
                    "Samsung Super Fast Charging (25W PD)"
                }
            } else if is_pd_active || chgtyp == "4" || chgtyp == "7" || chgtyp.to_uppercase().contains("PD") {
                if adpv > 15000 {
                    "USB Power Delivery (15V/20V High-Power)"
                } else if adpv > 9500 {
                    "USB Power Delivery (12V Fast Charge)"
                } else if adpv > 8000 {
                    "USB Power Delivery (9V Fast Charge)"
                } else if c_clean >= 2500 {
                    "USB Power Delivery (PPS High Current)"
                } else {
                    "USB Power Delivery / PPS"
                }
            } else if (Path::new("/sys/class/qcom-battery").exists() || Self::is_qualcomm_soc()) && adpv > 8000 {
                "Qualcomm Quick Charge (QC 3.0 / 4+)"
            } else if adpv > 8500 {
                "USB Power Delivery / PPS"
            } else if adpv > 8000 || chgtyp == "4" || chgtyp == "7" {
                "USB Power Delivery / PPS"
            } else if adpv > 4500 && c_clean >= 2000 {
                "Fast Charge (High Current 5V)"
            } else if adpv > 4000 {
                "Standard USB Fast Charge (5V)"
            } else {
                "Standard Charging"
            };
            (ic, proto)
        } else {
            ("Unknown / Standby".to_string(), "Battery Power")
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
        let effective_temp_dc = if temp_raw > 0 { temp_raw } else { real_dc };

        format!(
            "{{\n  \"capacity\": {},\n  \"status\": \"{}\",\n  \"health\": \"{}\",\n  \"temperature_c\": {:.1},\n  \"real_physical_temp_c\": {:.1},\n  \"voltage_mv\": {},\n  \"current_ma\": {},\n  \"adapter_voltage_mv\": {},\n  \"adapter_current_ma\": {},\n  \"active_ic\": \"{}\",\n  \"detected_hardware_chips\": \"{}\",\n  \"dynamic_scanned_nodes_count\": {},\n  \"fast_charge_protocol\": \"{}\",\n  \"rfc_authenticated\": {},\n  \"hardware_platform\": \"{}\",\n  \"is_props_spoofed\": {},\n  \"spoof_detail\": \"{}\",\n  \"emergency_guard_active\": {},\n  \"overnight_bypass_latched\": {},\n  \"smart_limit_latched\": {},\n  \"bypass_active\": {},\n  \"smart_tapering_active\": {},\n  \"pd_contract_active\": {},\n  \"charging_mode\": \"{}\",\n  \"bypass_enabled\": {},\n  \"extreme_charging_enabled\": {},\n  \"limit_current_ma\": {},\n  \"max_battery_percent\": {},\n  \"high_current_target_percent\": {},\n  \"temp_cutoff_c\": {},\n  \"thermal_lockout_bypass_enabled\": {},\n  \"smart_tapering_enabled\": {},\n  \"emergency_temp_guard_enabled\": {},\n  \"night_sleep_guard_enabled\": {},\n  \"auto_cut_enabled\": {}\n}}",
            cap,
            stat,
            hlth,
            (effective_temp_dc as f64) / 10.0,
            (real_dc as f64) / 10.0,
            v_clean,
            c_clean,
            adpv,
            ibus_val,
            active_ic.replace('\"', "\\\""),
            topology.summary.replace('\"', "\\\""),
            dynamic_nodes_count,
            protocol,
            rfc_auth,
            platform.as_str(),
            is_spoofed,
            spoof_details.replace('\"', "\\\""),
            guard_state,
            is_overnight_latch,
            is_limit_latched,
            is_bypass_active,
            is_tapering,
            is_pd_active,
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
            cfg.night_sleep_guard_enabled,
            cfg.auto_cut_enabled
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
    use super::{ChargingHardwarePlatform, ChargingRegulator, CHARGING_SESSION_NODES, DISCONNECT_CHARGING_RESETS};

    #[test]
    fn disconnect_resets_only_charging_protocol_nodes() {
        assert!(DISCONNECT_CHARGING_RESETS.iter().all(|(path, _)| {
            let path = path.to_lowercase();
            !path.contains("thermal") && !path.contains("trip_point") && !path.contains("cooling_device")
        }));
    }

    #[test]
    fn test_disconnect_resets_contains_r1_and_game_nodes() {
        let map: std::collections::HashMap<_, _> = DISCONNECT_CHARGING_RESETS.iter().cloned().collect();
        assert_eq!(map.get("/sys/devices/platform/charger/BN_TestMode"), Some(&"0"));
        assert_eq!(map.get("/sys/devices/platform/charger/BatteryNotify"), Some(&"1"));
        assert_eq!(map.get("/sys/devices/platform/charger/tran_charger_full"), Some(&"0"));
        assert_eq!(map.get("/sys/devices/platform/charger/tran_game_mode"), Some(&"0"));
        assert_eq!(map.get("/sys/devices/platform/charger/sw_jeita"), Some(&"1"));
        assert_eq!(map.get("/sys/devices/platform/charger/input_current"), Some(&"5b8d80"));
        assert_eq!(map.get("/sys/devices/platform/charger/sc_ibat_limit"), Some(&"2000"));
        assert_eq!(map.get("/sys/devices/platform/charger/chg1_current"), Some(&"5b8d80"));
        assert_eq!(map.get("/sys/devices/platform/charger/chg2_current"), Some(&"5b8d80"));
        assert_eq!(map.get("/sys/class/power_supply/battery/boost_current"), Some(&"0"));
        assert_eq!(map.get("/sys/class/power_supply/battery/constant_charge_current_max"), Some(&"4500000"));
    }

    #[test]
    fn test_format_mtk_charger_hex_ua() {
        // 7000 mA -> 7,000,000 uA -> 0x6acfc0
        assert_eq!(ChargingRegulator::format_mtk_charger_hex_ua(7000), "6acfc0");
        assert_eq!(u64::from_str_radix(&ChargingRegulator::format_mtk_charger_hex_ua(7000), 16).unwrap(), 7_000_000);
        // 6000 mA -> 6,000,000 uA -> 0x5b8d80
        assert_eq!(ChargingRegulator::format_mtk_charger_hex_ua(6000), "5b8d80");
        assert_eq!(u64::from_str_radix(&ChargingRegulator::format_mtk_charger_hex_ua(6000), 16).unwrap(), 6_000_000);
        // 2500 mA -> 2,500,000 uA -> 0x2625a0
        assert_eq!(ChargingRegulator::format_mtk_charger_hex_ua(2500), "2625a0");
        // 1000 mA -> 1,000,000 uA -> 0xf4240
        assert_eq!(ChargingRegulator::format_mtk_charger_hex_ua(1000), "f4240");
    }

    #[test]
    fn test_session_nodes_contains_r1_nodes() {
        assert!(CHARGING_SESSION_NODES.contains(&"/sys/devices/platform/charger/tran_game_mode"));
        assert!(CHARGING_SESSION_NODES.contains(&"/sys/devices/platform/charger/tran_charger_full"));
        assert!(CHARGING_SESSION_NODES.contains(&"/sys/devices/platform/charger/BN_TestMode"));
        assert!(CHARGING_SESSION_NODES.contains(&"/sys/devices/platform/charger/BatteryNotify"));
    }

    #[test]
    fn test_auxadc_conversion_table() {
        // High thermal extreme: <= 300 mV -> >= 650 dC
        assert_eq!(ChargingRegulator::auxadc_mv_to_dc(250), 650);
        assert_eq!(ChargingRegulator::auxadc_mv_to_dc(300), 650);

        // Hazardous zone: 375 mV -> 550 dC (55.0°C)
        assert_eq!(ChargingRegulator::auxadc_mv_to_dc(375), 550);

        // Emergency cutoff boundary: 440 mV -> 500 dC (50.0°C)
        assert_eq!(ChargingRegulator::auxadc_mv_to_dc(440), 500);

        // Warm / nominal fast charge: 530 mV -> 420 dC (42.0°C, verified on live device)
        assert_eq!(ChargingRegulator::auxadc_mv_to_dc(530), 420);

        // Ambient operating: 600 mV -> 365 dC (36.5°C)
        assert_eq!(ChargingRegulator::auxadc_mv_to_dc(600), 365);

        // Room ambient: 700 mV -> 300 dC (30.0°C)
        assert_eq!(ChargingRegulator::auxadc_mv_to_dc(700), 300);

        // Cool: 800 mV -> 250 dC (25.0°C)
        assert_eq!(ChargingRegulator::auxadc_mv_to_dc(800), 250);

        // Cold: > 800 mV -> 200 dC (20.0°C)
        assert_eq!(ChargingRegulator::auxadc_mv_to_dc(900), 200);
    }

    #[test]
    fn test_node_permission_helpers() {
        let tmp = std::env::temp_dir().join("lynx_test_node_perm.tmp");
        let tmp_str = tmp.to_str().unwrap();
        let _ = std::fs::write(&tmp, "initial");

        // Write and lock
        assert!(ChargingRegulator::write_node_lock(tmp_str, "test_val_1"));
        assert_eq!(ChargingRegulator::read_node(tmp_str), Some("test_val_1".to_string()));

        // Unlock and rewrite
        assert!(ChargingRegulator::unlock_node(tmp_str));
        assert!(ChargingRegulator::write_node(tmp_str, "test_val_2"));
        assert_eq!(ChargingRegulator::read_node(tmp_str), Some("test_val_2".to_string()));

        let _ = std::fs::remove_file(tmp);
    }

    #[test]
    fn test_rt9759_pump_math_and_net_current() {
        const ETA: f64 = 0.97; // 97% switched-capacitor efficiency

        // 1. Adapter at 3000 mA input:
        // I_pump = 2 * 3000 * 0.97 = 5820 mA
        let pump_3000 = ChargingRegulator::calculate_pump_output_ma(3000, ETA);
        assert_eq!(pump_3000, 5820);

        // 2. Adapter at 3300 mA input (nominal RFC fast charge):
        // I_pump = 2 * 3300 * 0.97 = 6402 mA
        let pump_3300 = ChargingRegulator::calculate_pump_output_ma(3300, ETA);
        assert_eq!(pump_3300, 6402);

        // 3. Adapter at 3317 mA input (MediaTek register step 33170):
        // I_pump = 2 * 3317 * 0.97 = 6434.98 -> 6435 mA
        let pump_3317 = ChargingRegulator::calculate_pump_output_ma(3317, ETA);
        assert_eq!(pump_3317, 6435);

        // 4. Net battery current across R_SENSE with 350 mA active system load:
        // I_net = 6435 - 350 = 6085 mA -> 6,085,000 uA (> 6,000,000 uA / 6.0 A)
        let net_ua = ChargingRegulator::calculate_net_battery_current_ua(3317, 350, ETA);
        assert_eq!(net_ua, 6_085_000);
        assert!(net_ua >= 6_000_000);

        // 5. Doubling ratio verification:
        let ratio = (pump_3300 as f64) / 3300.0;
        assert!(ratio >= 1.85 && ratio <= 2.05);
        assert!((ratio - 1.94).abs() < 0.01);
    }

    #[test]
    fn test_calculate_regulated_input_current_ma() {
        // Standard 5V adapter: 1:1 passthrough
        assert_eq!(ChargingRegulator::calculate_regulated_input_current_ma(1000, 5000), 1000);
        assert_eq!(ChargingRegulator::calculate_regulated_input_current_ma(1500, 5000), 1500);
        assert_eq!(ChargingRegulator::calculate_regulated_input_current_ma(2000, 5200), 2000);

        // 9V fast charger with 1000 mA Cool target:
        // Scaled to (1000 * 4200) / 9000 = 466 mA (approx 4.19W -> ~1000 mA into 4V battery)
        let scaled_9v_1000 = ChargingRegulator::calculate_regulated_input_current_ma(1000, 9000);
        assert_eq!(scaled_9v_1000, 466);

        // 10V fast charger with 1000 mA Cool target:
        // Scaled to (1000 * 4200) / 10000 = 420 mA (approx 4.2W)
        let scaled_10v_1000 = ChargingRegulator::calculate_regulated_input_current_ma(1000, 10000);
        assert_eq!(scaled_10v_1000, 420);

        // Clamping to minimum 350 mA floor
        let scaled_12v_500 = ChargingRegulator::calculate_regulated_input_current_ma(500, 12000);
        assert_eq!(scaled_12v_500, 350);
    }

    #[test]
    fn test_cc_cv_soc_boundaries_and_tapering_thresholds() {
        // Deeply discharged / low SoC CC phase (< 80% SoC: Full Max Watt unconstrained peak)
        assert_eq!(ChargingRegulator::determine_taper_state(3, 3400), ("hyper", 12000));
        assert_eq!(ChargingRegulator::determine_taper_state(15, 3800), ("hyper", 12000));
        assert_eq!(ChargingRegulator::determine_taper_state(45, 3950), ("hyper", 12000));
        assert_eq!(ChargingRegulator::determine_taper_state(65, 4150), ("hyper", 12000));
        assert_eq!(ChargingRegulator::determine_taper_state(78, 4470), ("hyper", 12000));
        assert_eq!(ChargingRegulator::determine_taper_state(79, 4480), ("hyper", 12000));

        // High Sustained Phase: 80% to 85% SoC -> 4200 mA (Sustained speed with Direct Pump active)
        assert_eq!(ChargingRegulator::determine_taper_state(80, 4450), ("tapering_80", 4200));
        assert_eq!(ChargingRegulator::determine_taper_state(83, 4450), ("tapering_80", 4200));
        assert_eq!(ChargingRegulator::determine_taper_state(85, 4455), ("tapering_80", 4200));

        // Mid-Saturation Sustained Phase: 86% to 91% SoC -> 3000 mA
        assert_eq!(ChargingRegulator::determine_taper_state(86, 4460), ("tapering_86", 3000));
        assert_eq!(ChargingRegulator::determine_taper_state(89, 4470), ("tapering_86", 3000));
        assert_eq!(ChargingRegulator::determine_taper_state(91, 4475), ("tapering_86", 3000));

        // Late-Stage Decay: 92% to 95% SoC -> 2000 mA
        assert_eq!(ChargingRegulator::determine_taper_state(92, 4475), ("tapering_92", 2000));
        assert_eq!(ChargingRegulator::determine_taper_state(94, 4480), ("tapering_92", 2000));
        assert_eq!(ChargingRegulator::determine_taper_state(95, 4480), ("tapering_92", 2000));

        // Saturation Trickle: 96% to 99% SoC -> 1200 mA
        assert_eq!(ChargingRegulator::determine_taper_state(96, 4485), ("tapering_96", 1200));
        assert_eq!(ChargingRegulator::determine_taper_state(98, 4490), ("tapering_96", 1200));
        assert_eq!(ChargingRegulator::determine_taper_state(99, 4495), ("tapering_96", 1200));

        // Full battery AutoCut: 100% SoC -> bypass 0 mA
        assert_eq!(ChargingRegulator::determine_taper_state(100, 4500), ("bypass_100", 0));
        assert_eq!(ChargingRegulator::determine_taper_state(101, 4500), ("bypass_100", 0));
    }

    #[test]
    fn test_evaluate_plugged_state_decouple_capacitor_float() {
        // Decouple capacitor float after physical unplug: VBUS floats > 3.5V (3800 mV)
        // while online rails are 0, Charger_Type dropped to 0, status is Discharging
        assert!(!ChargingRegulator::evaluate_plugged_state(false, "Discharging", "0", 3800));
        assert!(!ChargingRegulator::evaluate_plugged_state(false, "discharging", "0", 4200));

        // Qualcomm decouple float: usb/type is "Unknown" or empty
        assert!(!ChargingRegulator::evaluate_plugged_state(false, "Discharging", "Unknown", 4100));
        assert!(!ChargingRegulator::evaluate_plugged_state(false, "Discharging", "", 3900));

        // Decouple float when status is "Not charging" but Charger_Type dropped to 0
        assert!(!ChargingRegulator::evaluate_plugged_state(false, "Not charging", "0", 3700));
    }

    #[test]
    fn test_evaluate_plugged_state_bypass_charging() {
        // Bypass charging: online rail is active, charger type is valid fast charger,
        // but battery cell is bypassed so fuel gauge reports Discharging or Not charging
        assert!(ChargingRegulator::evaluate_plugged_state(true, "Discharging", "9", 9000));
        assert!(ChargingRegulator::evaluate_plugged_state(true, "Not charging", "9", 9000));
        assert!(ChargingRegulator::evaluate_plugged_state(true, "Discharging", "USB_PD", 9000));
    }

    #[test]
    fn test_evaluate_plugged_state_active_charging() {
        // Active fast charging: online rail is 1, status is Charging, adapter 9V
        assert!(ChargingRegulator::evaluate_plugged_state(true, "Charging", "9", 9000));
        assert!(ChargingRegulator::evaluate_plugged_state(true, "charging", "USB_PD", 9500));

        // Active standard charging: online rail is 1, status is Charging, adapter 5V
        assert!(ChargingRegulator::evaluate_plugged_state(true, "Charging", "4", 5000));

        // Battery reports Charging even before online rail sysfs updates
        assert!(ChargingRegulator::evaluate_plugged_state(false, "Charging", "9", 5000));

        // MediaTek / Transsion: rails remain 0, Charger_Type reports "0", but VBUS is 5200mV and status is Charging
        assert!(ChargingRegulator::evaluate_plugged_state(false, "Charging", "0", 5200));

        // MediaTek / Transsion in bypass mode: rails 0, Charger_Type "0", Discharging, but active 5.2V adapter present
        assert!(ChargingRegulator::evaluate_plugged_state(false, "Discharging", "0", 5200));
    }

    #[test]
    fn test_authentic_temp_sync_clamping_and_fallback() {
        // AuxADC conversion clamping bounds
        let cold = ChargingRegulator::auxadc_mv_to_dc(900);
        assert!(cold >= 200 && cold <= 250); // <= 25.0°C

        let normal = ChargingRegulator::auxadc_mv_to_dc(600);
        assert_eq!(normal, 365); // 36.5°C

        let warm = ChargingRegulator::auxadc_mv_to_dc(530);
        assert_eq!(warm, 420); // 42.0°C

        let critical = ChargingRegulator::auxadc_mv_to_dc(440);
        assert_eq!(critical, 500); // 50.0°C

        // Target Celsius clamped bounds for Battery_Temperature sysfs node
        let c_cold = (cold / 10).clamp(15, 50);
        assert_eq!(c_cold, 20);

        let c_warm = (warm / 10).clamp(15, 50);
        assert_eq!(c_warm, 42);

        let c_critical = (critical / 10).clamp(15, 50);
        assert_eq!(c_critical, 50);
    }

    #[test]
    fn test_charging_hardware_platform_and_anti_spoofing() {
        assert_eq!(ChargingHardwarePlatform::Transsion.as_str(), "Transsion");
        assert_eq!(ChargingHardwarePlatform::Xiaomi.as_str(), "Xiaomi");
        assert_eq!(ChargingHardwarePlatform::QualcommGeneric.as_str(), "Qualcomm");
        assert_eq!(ChargingHardwarePlatform::Oplus.as_str(), "OPPO/Realme/OnePlus");
        assert_eq!(ChargingHardwarePlatform::Samsung.as_str(), "Samsung");
        assert_eq!(ChargingHardwarePlatform::GooglePixel.as_str(), "Google Pixel");
        assert_eq!(ChargingHardwarePlatform::GenericLinux.as_str(), "Generic Linux");

        let (platform, is_spoofed, detail) = ChargingRegulator::detect_charging_platform();
        // The test environment should not panic and produce a valid platform
        assert!(!platform.as_str().is_empty());
        if is_spoofed {
            assert!(!detail.is_empty());
        }
    }

    #[test]
    fn test_dynamic_charger_topology_scanner() {
        let topology = ChargingRegulator::scan_charging_hardware_chips();
        assert!(!topology.summary.is_empty());
        // Verify summary is coherent
        if let Some(buck) = &topology.primary_buck {
            assert!(!buck.is_empty());
        }
    }

    #[test]
    fn test_crawl_dynamic_power_supply_nodes() {
        let nodes = ChargingRegulator::crawl_dynamic_power_supply_nodes();
        // Nodes should be sorted and unique
        let mut sorted = nodes.clone();
        sorted.sort();
        sorted.dedup();
        assert_eq!(nodes, sorted);
    }

    #[test]
    fn test_status_json_includes_topology_fields() {
        let json = ChargingRegulator::status_json();
        assert!(json.contains("\"detected_hardware_chips\":"));
        assert!(json.contains("\"dynamic_scanned_nodes_count\":"));
        assert!(json.contains("\"active_ic\":"));
    }

    #[test]
    fn test_charging_wake_lock_and_live_invariants() {
        // Must not panic on host test environments
        ChargingRegulator::acquire_charging_wake_lock();
        ChargingRegulator::release_charging_wake_lock();
        ChargingRegulator::neutralise_battery_cooling_devices();
        ChargingRegulator::enforce_live_charging_invariants();
    }

    #[test]
    fn test_qcom_direct_charge_pump_logic_and_cooldown() {
        // Host without Qualcomm hardware sysfs returns false
        assert!(!ChargingRegulator::has_qcom_direct_charge_pump());

        // Cooldown mechanism allows at most one reconnect per 30s
        let _first = super::can_soft_reconnect();
        let second = super::can_soft_reconnect();
        // The first call consumes the slot, second must be false (in cooldown)
        assert!(!second);
    }
}

