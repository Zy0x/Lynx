use std::path::Path;
use std::process::Command;

use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

pub struct ThermalController;

impl ThermalController {
    fn write_opt(path: impl AsRef<Path>, val: impl AsRef<str>) -> bool {
        let p = path.as_ref();
        if p.exists() {
            SysfsWriter::write(p, val, WriteMode::ForcePermission).is_ok()
        } else {
            false
        }
    }

    fn read_trimmed(path: impl AsRef<Path>) -> Option<String> {
        SysfsReader::read_trimmed(path.as_ref()).ok().filter(|s| !s.is_empty())
    }

    pub fn is_battery_zone(zone_type: &str) -> bool {
        let t = zone_type.to_lowercase();
        t.contains("batt") || t.contains("chg") || t.contains("charger") || t.contains("bms") || t.contains("battery")
    }

    /// Scans all thermal zones and cooling devices in /sys/class/thermal, returning a detailed JSON string.
    pub fn status_json() -> String {
        let mut out = String::new();
        out.push_str("{\"thermal_zones\":[");

        let mut tz_entries = Vec::new();
        for id in 0..128 {
            let tz_dir = format!("/sys/class/thermal/thermal_zone{}", id);
            let p = Path::new(&tz_dir);
            if !p.exists() {
                continue;
            }

            let z_type = Self::read_trimmed(format!("{}/type", tz_dir)).unwrap_or_else(|| "unknown".to_string());
            let z_temp = Self::read_trimmed(format!("{}/temp", tz_dir))
                .and_then(|s| s.parse::<i64>().ok())
                .unwrap_or(0);
            let z_mode = Self::read_trimmed(format!("{}/mode", tz_dir)).unwrap_or_else(|| "unknown".to_string());
            let is_batt = Self::is_battery_zone(&z_type);

            let mut trips = Vec::new();
            for tid in 0..16 {
                let tp_temp_path = format!("{}/trip_point_{}_temp", tz_dir, tid);
                let tp_type_path = format!("{}/trip_point_{}_type", tz_dir, tid);
                if let Some(t_val) = Self::read_trimmed(&tp_temp_path).and_then(|s| s.parse::<i64>().ok()) {
                    let t_type = Self::read_trimmed(&tp_type_path).unwrap_or_else(|| "unknown".to_string());
                    trips.push(format!("{{\"id\":{},\"type\":\"{}\",\"temp\":{}}}", tid, t_type, t_val));
                }
            }

            tz_entries.push(format!(
                "{{\"id\":{},\"type\":\"{}\",\"temp\":{},\"temp_c\":{:.1},\"mode\":\"{}\",\"is_battery\":{},\"trip_points\":[{}]}}",
                id,
                z_type,
                z_temp,
                z_temp as f64 / 1000.0,
                z_mode,
                is_batt,
                trips.join(",")
            ));
        }

        out.push_str(&tz_entries.join(","));
        out.push_str("],\"cooling_devices\":[");

        let mut cdev_entries = Vec::new();
        for id in 0..128 {
            let cdev_dir = format!("/sys/class/thermal/cooling_device{}", id);
            let p = Path::new(&cdev_dir);
            if !p.exists() {
                continue;
            }

            let c_type = Self::read_trimmed(format!("{}/type", cdev_dir)).unwrap_or_else(|| "unknown".to_string());
            let cur_state = Self::read_trimmed(format!("{}/cur_state", cdev_dir))
                .and_then(|s| s.parse::<i64>().ok())
                .unwrap_or(0);
            let max_state = Self::read_trimmed(format!("{}/max_state", cdev_dir))
                .and_then(|s| s.parse::<i64>().ok())
                .unwrap_or(0);

            cdev_entries.push(format!(
                "{{\"id\":{},\"type\":\"{}\",\"cur_state\":{},\"max_state\":{}}}",
                id, c_type, cur_state, max_state
            ));
        }

        out.push_str(&cdev_entries.join(","));
        out.push(']');
        out.push('}');
        out
    }

    /// Freezes aggressive OEM thermal daemons with SIGSTOP.
    pub fn freeze_oem_throttlers() {
        const TARGETS: &[&str] = &[
            "mi_thermald",
            "thermal-engine",
            "thermal-engine-v2",
            "ituxd",
            "thermalloadalgod",
            "com.samsung.android.game.gos",
            "com.xiaomi.joyose",
        ];
        for target in TARGETS {
            let _ = Command::new("killall").arg("-STOP").arg(target).output();
        }
        let _ = Command::new("cmd").args(&["thermalservice", "override-status", "0"]).output();
    }

    /// Unfreezes OEM thermal daemons with SIGCONT.
    pub fn unfreeze_oem_throttlers() {
        const TARGETS: &[&str] = &[
            "mi_thermald",
            "thermal-engine",
            "thermal-engine-v2",
            "ituxd",
            "thermalloadalgod",
            "com.samsung.android.game.gos",
            "com.xiaomi.joyose",
        ];
        for target in TARGETS {
            let _ = Command::new("killall").arg("-CONT").arg(target).output();
        }
        let _ = Command::new("cmd").args(&["thermalservice", "reset"]).output();
    }

    /// Applies thermal mode (conservative, balanced, stable, gaming, extreme, bypass).
    pub fn set_mode(mode: &str) -> String {
        let m = mode.trim().to_lowercase();
        match m.as_str() {
            "conservative" | "balanced" | "default" | "default_oem" | "restore" => {
                Self::unfreeze_oem_throttlers();

                // Restore PPM (MTK)
                Self::write_opt("/proc/ppm/policy_status", "3 1");
                Self::write_opt("/proc/ppm/policy_status", "4 1");
                Self::write_opt("/proc/ppm/policy_status", "5 1");
                Self::write_opt("/proc/ppm/cpi/cpi_enabled", "1");
                Self::write_opt("/proc/cpufreq/cpufreq_imax_thermal_protect", "1");

                // Restore GPU limits (MTK)
                if Path::new("/proc/gpufreq/gpufreq_limit_table").exists() {
                    for id in 3..=7 {
                        Self::write_opt("/proc/gpufreq/gpufreq_limit_table", format!("{} 1 1", id));
                    }
                }
                Self::write_opt("/sys/module/fbt_cpu/parameters/thrm_limit_cpu", "1");
                Self::write_opt("/sys/kernel/fpsgo/fbt/thrm_limit_cpu", "1");
                Self::write_opt("/sys/kernel/eara_thermal/enable", "1");

                // Restore KGSL (QCOM)
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/throttling", "1");
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel", "1");

                // Re-enable thermal zone modes
                for id in 0..128 {
                    let tz_dir = format!("/sys/class/thermal/thermal_zone{}", id);
                    if Path::new(&tz_dir).exists() {
                        Self::write_opt(format!("{}/mode", tz_dir), "enabled");
                    }
                }

                // Reset cooling devices
                for id in 0..128 {
                    let cdev_dir = format!("/sys/class/thermal/cooling_device{}", id);
                    if Path::new(&cdev_dir).exists() {
                        Self::write_opt(format!("{}/cur_state", cdev_dir), "0");
                    }
                }

                "Thermal mode restored to OEM Baseline / Balanced.".to_string()
            }

            "stable" | "thermal_stable" => {
                Self::unfreeze_oem_throttlers();
                let _ = Command::new("cmd").args(&["thermalservice", "override-status", "0"]).output();

                // PPM Balanced (MTK)
                Self::write_opt("/proc/ppm/policy_status", "3 1");
                Self::write_opt("/proc/ppm/policy_status", "4 1");
                Self::write_opt("/proc/ppm/policy_status", "5 0");
                Self::write_opt("/proc/cpufreq/cpufreq_imax_thermal_protect", "1");

                // Schedutil rate limits
                for id in 0..16 {
                    let p_up = format!("/sys/devices/system/cpu/cpufreq/policy{}/schedutil/up_rate_limit_us", id);
                    let p_down = format!("/sys/devices/system/cpu/cpufreq/policy{}/schedutil/down_rate_limit_us", id);
                    Self::write_opt(p_up, "500");
                    Self::write_opt(p_down, "10000");
                }

                // Calibrate non-battery zones to 55°C (55000 milli-celsius)
                Self::set_limit_raw(55000);

                // FPSGO margins
                Self::write_opt("/sys/kernel/fpsgo/fbt/thrm_limit_cpu", "0");
                Self::write_opt("/sys/module/fbt_cpu/parameters/thrm_limit_cpu", "0");

                "Thermal mode configured to Thermal Stable (55°C calibrated curve).".to_string()
            }

            "gaming" | "hardware_safety_dominant" | "dominant" => {
                Self::freeze_oem_throttlers();

                // Software Throttling Stripped (MTK)
                Self::write_opt("/proc/cpufreq/cpufreq_imax_thermal_protect", "0");
                Self::write_opt("/proc/ppm/policy_status", "3 0");
                Self::write_opt("/proc/ppm/policy_status", "4 0");
                Self::write_opt("/proc/ppm/policy_status", "5 0");
                Self::write_opt("/proc/ppm/cpi/cpi_enabled", "0");

                if Path::new("/proc/gpufreq/gpufreq_limit_table").exists() {
                    for id in 3..=7 {
                        Self::write_opt("/proc/gpufreq/gpufreq_limit_table", format!("{} 0 0", id));
                    }
                }
                Self::write_opt("/proc/gpufreqv2/gpufreq_power_limited", "ignore_thermal_protect 1");
                Self::write_opt("/proc/gpufreqv2/gpufreq_power_limited", "ignore_pbm_limited 1");
                Self::write_opt("/sys/module/fbt_cpu/parameters/thrm_limit_cpu", "0");
                Self::write_opt("/sys/kernel/fpsgo/fbt/thrm_limit_cpu", "0");
                Self::write_opt("/sys/kernel/eara_thermal/enable", "0");

                // KGSL (QCOM)
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/throttling", "0");
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel", "0");
                Self::write_opt("/sys/module/msm_thermal/parameters/enabled", "0");
                Self::write_opt("/sys/module/msm_thermal/core_control/enabled", "0");

                // Elevate non-battery zones to 75°C (75000)
                Self::set_limit_raw(75000);

                "Thermal mode configured to Hardware Safety Dominant (Gaming, 75°C limits).".to_string()
            }

            "extreme" | "bypass" => {
                Self::freeze_oem_throttlers();

                // Maximum bypass for extreme benchmarks/gaming
                Self::write_opt("/proc/cpufreq/cpufreq_imax_thermal_protect", "0");
                Self::write_opt("/proc/ppm/policy_status", "3 0");
                Self::write_opt("/proc/ppm/policy_status", "4 0");
                Self::write_opt("/proc/ppm/policy_status", "5 0");
                Self::write_opt("/proc/ppm/cpi/cpi_enabled", "0");

                Self::write_opt("/sys/class/kgsl/kgsl-3d0/throttling", "0");
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel", "0");

                // Elevate non-battery trip points to 85°C (85000)
                Self::set_limit_raw(85000);

                // Disable thermal zone modes on non-battery zones
                for id in 0..128 {
                    let tz_dir = format!("/sys/class/thermal/thermal_zone{}", id);
                    if let Some(t_type) = Self::read_trimmed(format!("{}/type", tz_dir)) {
                        if !Self::is_battery_zone(&t_type) {
                            Self::write_opt(format!("{}/mode", tz_dir), "disabled");
                        }
                    }
                }

                "Thermal mode configured to Extreme Bypass (85°C limits, full relaxation).".to_string()
            }

            unknown => format!("Error: Unknown thermal mode '{}'. Valid: conservative, stable, gaming, extreme.", unknown),
        }
    }

    fn set_limit_raw(milli_c: i64) -> usize {
        let val_str = milli_c.to_string();
        let mut modified = 0;

        for id in 0..128 {
            let tz_dir = format!("/sys/class/thermal/thermal_zone{}", id);
            if !Path::new(&tz_dir).exists() {
                continue;
            }

            let t_type = Self::read_trimmed(format!("{}/type", tz_dir)).unwrap_or_default();
            if Self::is_battery_zone(&t_type) {
                // NEVER tamper with battery / charger thermal safety
                continue;
            }

            for tid in 0..16 {
                let tp_path = format!("{}/trip_point_{}_temp", tz_dir, tid);
                if Path::new(&tp_path).exists() {
                    if Self::write_opt(&tp_path, &val_str) {
                        modified += 1;
                    }
                }
            }
        }
        modified
    }

    /// Sets custom trip point temperature limit on all non-battery SoC zones.
    pub fn set_limit(temp_c: i32) -> String {
        let clamped = temp_c.clamp(40, 95);
        let milli_c = (clamped as i64) * 1000;
        let count = Self::set_limit_raw(milli_c);
        format!("Successfully updated {} trip points to {}°C across active SoC thermal zones.", count, clamped)
    }
}
