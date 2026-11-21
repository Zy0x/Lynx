use std::path::Path;
use std::process::Command;

use crate::state::ConfigStore;
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

    const SAFE_SILICON_TRIP_FLOOR_MC: i64 = 90_000;
    const SILICON_RECOVERY_CEILING_MC: i64 = 80_000;

    pub fn is_battery_zone(zone_type: &str) -> bool {
        let t = zone_type.to_lowercase();
        t.contains("batt") || t.contains("chg") || t.contains("charger") || t.contains("bms") || t.contains("battery")
    }

    /// Identifies SoC silicon, CPU cluster/core, GPU, PMIC, DSP, modem, and board-ADC zones
    /// whose normal operating temperature sits between 55°C and 80°C (never clamp to skin limits).
    pub fn is_silicon_compute_zone(zone_type: &str) -> bool {
        let t = zone_type.to_lowercase();
        !Self::is_battery_zone(&t)
            && (t.contains("cpu")
                || t.contains("cpuss")
                || t.contains("gpu")
                || t.contains("tsens")
                || t.contains("soc")
                || t.contains("mpm")
                || t.contains("cdsp")
                || t.contains("mdm")
                || t.contains("video")
                || t.contains("camera")
                || t.contains("npu")
                || t.contains("ddr")
                || t.contains("pm660")
                || t.contains("pm8")
                || t.contains("pm7")
                || t.contains("pmic")
                || t.contains("xo-therm")
                || t.contains("msm-therm")
                || t.contains("backlight")
                || t.contains("little")
                || t.contains("big")
                || t.contains("prime"))
    }

    /// Decides what millidegree Celsius trip point (if any) should be written for a zone trip node.
    /// Protects battery zones and hardware critical/hot shutdown trips, prevents clamping silicon
    /// compute/core-isolate zones down to skin comfort limits (e.g. 52°C), and heals any
    /// previously poisoned silicon trip points back up to `90000` (90.0°C).
    pub(crate) fn resolve_zone_trip_write(
        zone_type: &str,
        trip_type: &str,
        current_trip_mc: Option<i64>,
        requested_mc: i64,
    ) -> Option<i64> {
        if Self::is_battery_zone(zone_type) {
            return None;
        }
        let tp_lower = trip_type.trim().to_lowercase();
        if tp_lower == "critical" || tp_lower == "hot" {
            return None;
        }
        if Self::is_silicon_compute_zone(zone_type) && requested_mc < Self::SAFE_SILICON_TRIP_FLOOR_MC {
            // Never clamp silicon/cpu-isolate zones below 90°C (accounting for 10°C step_wise hysteresis);
            // if a prior profile poisoned this trip point below 90°C, heal it back to 90000.
            return match current_trip_mc {
                Some(cur) if cur > 0 && cur < Self::SAFE_SILICON_TRIP_FLOOR_MC => {
                    Some(Self::SAFE_SILICON_TRIP_FLOOR_MC)
                }
                _ => None,
            };
        }
        Some(requested_mc)
    }

    /// Restores any silicon/CPU/GPU zone trip points that were previously clamped below 90°C
    /// (such as legacy `stable` mode writing 52000 to `cpuss-0-step` / `cpu-1-*-step`),
    /// preventing kernel `step_wise` hysteresis from trapping CPU cores in `isolate=1`.
    pub fn heal_silicon_trip_points() -> usize {
        let mut healed = 0usize;
        for id in 0..128 {
            let tz_dir = format!("/sys/class/thermal/thermal_zone{}", id);
            if !Path::new(&tz_dir).exists() {
                continue;
            }
            let Some(t_type) = Self::read_trimmed(format!("{}/type", tz_dir)) else {
                continue;
            };
            if !Self::is_silicon_compute_zone(&t_type) {
                continue;
            }
            for tid in 0..16 {
                let tp_path = format!("{}/trip_point_{}_temp", tz_dir, tid);
                let Some(cur_tp) = Self::read_trimmed(&tp_path).and_then(|s| s.parse::<i64>().ok()) else {
                    continue;
                };
                let tp_type = Self::read_trimmed(format!("{}/trip_point_{}_type", tz_dir, tid))
                    .unwrap_or_default();
                if let Some(target_mc) = Self::resolve_zone_trip_write(
                    &t_type,
                    &tp_type,
                    Some(cur_tp),
                    Self::SAFE_SILICON_TRIP_FLOOR_MC,
                ) {
                    if target_mc != cur_tp && Self::write_opt(&tp_path, target_mc.to_string()) {
                        healed += 1;
                    }
                }
            }
        }
        healed
    }

    /// Recovers stale thermal core isolation (e.g., multiple cores left at `isolate=1`)
    /// whenever no silicon thermal zone is genuinely overheating (`< 78°C`).
    pub fn recover_stale_cpu_isolation() {
        let mut isolated_count = 0usize;
        for i in 0..16 {
            let iso_path = format!("/sys/devices/system/cpu/cpu{}/isolate", i);
            if let Some(val) = Self::read_trimmed(&iso_path) {
                if val == "1" {
                    isolated_count += 1;
                }
            }
        }
        if isolated_count < 2 {
            return;
        }
        let mut max_silicon_mc = 0i64;
        for id in 0..64 {
            let tz_dir = format!("/sys/class/thermal/thermal_zone{}", id);
            if !Path::new(&tz_dir).exists() {
                continue;
            }
            if let Some(t_type) = Self::read_trimmed(format!("{}/type", tz_dir)) {
                if Self::is_silicon_compute_zone(&t_type) {
                    if let Some(temp_mc) = Self::read_trimmed(format!("{}/temp", tz_dir))
                        .and_then(|s| s.parse::<i64>().ok())
                    {
                        if temp_mc > max_silicon_mc && temp_mc < 120_000 {
                            max_silicon_mc = temp_mc;
                        }
                    }
                }
            }
        }
        if max_silicon_mc < Self::SILICON_RECOVERY_CEILING_MC {
            Self::un_isolate_all_cpus();
        }
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

    /// Un-isolates any thermal-isolated CPU cores, heals silicon trip floors, resets core cooling devices,
    /// and restores physical Fmax.
    pub fn un_isolate_all_cpus() {
        // 1. Heal poisoned silicon zone trip floors (>= 90°C) first so step_wise drops cooling targets cleanly
        Self::heal_silicon_trip_points();

        // 2. Clear cooling device min_state / cur_state clamps before clearing cpu/isolate sysfs
        for id in 0..128 {
            let cdev_type_path = format!("/sys/class/thermal/cooling_device{}/type", id);
            if let Some(c_type) = Self::read_trimmed(&cdev_type_path) {
                let lower = c_type.to_lowercase();
                // CRITICAL SAFETY GUARD: NEVER touch sysrst, shutdown, or reset cooling devices!
                if lower.contains("sysrst") || lower.contains("shutdown") || lower.contains("rst") {
                    continue;
                }
                if lower.contains("isolate")
                    || lower.contains("thermal-cpufreq")
                    || lower.contains("battery")
                    || lower.contains("cpu_adaptive")
                    || lower.contains("adaptive")
                    || lower.contains("abcct")
                    || (lower.starts_with("cpu") && lower[3..].chars().all(|c| c.is_ascii_digit()))
                {
                    let min_state_path = format!("/sys/class/thermal/cooling_device{}/min_state", id);
                    if Path::new(&min_state_path).exists() {
                        Self::write_opt(&min_state_path, "0");
                    }
                    Self::write_opt(format!("/sys/class/thermal/cooling_device{}/cur_state", id), "0");
                }
            }
        }

        // 3. Un-isolate CPUs, ensure online=1, and restore physical hardware Fmax
        for i in 0..16 {
            let cpu_isolate = format!("/sys/devices/system/cpu/cpu{}/isolate", i);
            let cpu_online = format!("/sys/devices/system/cpu/cpu{}/online", i);
            if Path::new(&cpu_isolate).exists() {
                Self::write_opt(&cpu_isolate, "0");
            }
            if Path::new(&cpu_online).exists() {
                Self::write_opt(&cpu_online, "1");
            }

            // Restore full physical hardware Fmax dynamically (Zero-Hardcoding)
            let max_info_path = format!("/sys/devices/system/cpu/cpu{}/cpufreq/cpuinfo_max_freq", i);
            let scaling_max_path = format!("/sys/devices/system/cpu/cpu{}/cpufreq/scaling_max_freq", i);
            if let Ok(info_max) = SysfsReader::read_trimmed(&max_info_path) {
                if !info_max.is_empty() {
                    let cur_scaling_max = SysfsReader::read_trimmed(&scaling_max_path).unwrap_or_default();
                    if cur_scaling_max != info_max {
                        Self::write_opt(&scaling_max_path, &info_max);
                    }
                }
            }
        }
        Self::neutralize_quiet_therm();
    }

    /// Neutralizes Qualcomm's premature 37°C software skin throttling zone (quiet-therm-step)
    /// and resets the cpufreq QoS clamp on Big cores.
    pub fn neutralize_quiet_therm() {
        for id in 0..64 {
            let tz_dir = format!("/sys/class/thermal/thermal_zone{}", id);
            if !Path::new(&tz_dir).exists() {
                continue;
            }
            if let Some(t_type) = Self::read_trimmed(format!("{}/type", tz_dir)) {
                let lower = t_type.to_lowercase();
                if lower.contains("quiet-therm-step") || lower.contains("skin-therm-step") {
                    // CRITICAL: NEVER set mode to "disabled"! Setting mode to "disabled" causes the kernel
                    // to read -274000 (-274°C), which trips JEITA cold charging protection and locks charging to 500mA!
                    // Keep mode "enabled" and elevate trip points to 75°C instead.
                    let _ = Self::write_opt(format!("{}/mode", tz_dir), "enabled");
                    let _ = Self::write_opt(format!("{}/policy", tz_dir), "user_space");
                    let _ = Self::write_opt(format!("{}/trip_point_0_temp", tz_dir), "75000");
                    let _ = Self::write_opt(format!("{}/trip_point_1_temp", tz_dir), "75000");
                } else if lower.contains("quiet-therm") || lower.contains("skin-therm") {
                    let _ = Self::write_opt(format!("{}/mode", tz_dir), "enabled");
                }
            }
        }
        for id in 0..64 {
            let cdev_dir = format!("/sys/class/thermal/cooling_device{}", id);
            if !Path::new(&cdev_dir).exists() {
                continue;
            }
            if let Some(c_type) = Self::read_trimmed(format!("{}/type", cdev_dir)) {
                let lower = c_type.to_lowercase();
                if lower.contains("thermal-cpufreq") || lower.contains("cpu_adaptive") {
                    let cur = Self::read_trimmed(format!("{}/cur_state", cdev_dir))
                        .and_then(|s| s.parse::<i64>().ok())
                        .unwrap_or(0);
                    if cur > 0 {
                        let _ = Self::write_opt(format!("{}/cur_state", cdev_dir), "0");
                    }
                }
            }
        }
    }

    /// Freezes aggressive OEM thermal daemons with SIGSTOP.
    pub fn freeze_oem_throttlers() {
        let (vendor, _) = crate::hardware::detector::ScoreBoard::evaluate();
        // NEVER freeze thermal-engine on Qualcomm! Freezing it breaks Thermal HAL IPC and freezes system_server / SystemUI.
        let targets: &[&str] = &[
            "mi_thermald",
            "ituxd",
            "thermalloadalgod",
            "com.samsung.android.game.gos",
            "com.xiaomi.joyose",
            "iomanager",
        ];
        crate::hardware::ChargingRegulator::signal_processes(targets, true);
        if vendor == crate::hardware::Vendor::Qualcomm {
            crate::hardware::ChargingRegulator::signal_processes(&["thermal-engine", "thermal-engine-v2"], false);
        }
        let _ = Command::new("cmd").args(&["thermalservice", "override-status", "0"]).output();
        Self::un_isolate_all_cpus();
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
            "iomanager",
        ];
        crate::hardware::ChargingRegulator::signal_processes(TARGETS, false);
        let _ = Command::new("cmd").args(&["thermalservice", "reset"]).output();
        Self::un_isolate_all_cpus();
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
                Self::write_opt(crate::hardware::mtk::MtkHardware::CPUFREQ_IMAX_THERMAL_PROTECT, "1");
                crate::hardware::mtk::MtkHardware::restore_orig_imax();
                Self::write_opt(crate::hardware::mtk::MtkHardware::LYNX_THERMAL_SILENCER, "0");
                crate::hardware::mtk::MtkHardware::restore_orig_gpu_max_khz();

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
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel", "0");

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
                Self::un_isolate_all_cpus();

                "Thermal mode restored to OEM Baseline / Balanced.".to_string()
            }

            "stable" | "thermal_stable" => {
                Self::unfreeze_oem_throttlers();
                let _ = Command::new("cmd").args(&["thermalservice", "override-status", "0"]).output();

                // PPM Balanced (MTK): Disable aggressive power/thermal downclocking so Big Cores reach 2.05 GHz
                Self::write_opt("/proc/ppm/policy_status", "3 0");
                Self::write_opt("/proc/ppm/policy_status", "4 0");
                Self::write_opt("/proc/ppm/policy_status", "5 0");
                Self::write_opt("/proc/cpufreq/cpufreq_imax_thermal_protect", "0");

                // Unthrottle MediaTek GPU thermal clamp so 120Hz UI, video playback, and media rendering scale up freely
                if Path::new("/proc/gpufreq/gpufreq_limit_table").exists() {
                    for id in 3..=7 {
                        Self::write_opt("/proc/gpufreq/gpufreq_limit_table", format!("{} 0 0", id));
                    }
                }

                // Schedutil rate limits (per-policy and global cpufreq fallback)
                for id in 0..16 {
                    let p_up = format!("/sys/devices/system/cpu/cpufreq/policy{}/schedutil/up_rate_limit_us", id);
                    let p_down = format!("/sys/devices/system/cpu/cpufreq/policy{}/schedutil/down_rate_limit_us", id);
                    Self::write_opt(p_up, "500");
                    Self::write_opt(p_down, "10000");
                }
                Self::write_opt("/sys/devices/system/cpu/cpufreq/schedutil/up_rate_limit_us", "500");
                Self::write_opt("/sys/devices/system/cpu/cpufreq/schedutil/down_rate_limit_us", "10000");

                // Calibrate non-battery zones to user's configured temperature limit (default 50°C)
                let custom_limit = ConfigStore::get("thermal.custom_temp_limit_c")
                    .and_then(|s| s.parse::<i32>().ok())
                    .unwrap_or(50)
                    .clamp(40, 95);
                Self::set_limit(custom_limit);
                Self::un_isolate_all_cpus();

                // FPSGO margins
                Self::write_opt("/sys/kernel/fpsgo/fbt/thrm_limit_cpu", "0");
                Self::write_opt("/sys/module/fbt_cpu/parameters/thrm_limit_cpu", "0");

                format!("Thermal mode configured to Thermal Stable ({}°C calibrated curve).", custom_limit)
            }

            "gaming" | "hardware_safety_dominant" | "dominant" => {
                Self::freeze_oem_throttlers();

                // Software Throttling Stripped (MTK)
                Self::write_opt(crate::hardware::mtk::MtkHardware::LYNX_THERMAL_SILENCER, "1");
                crate::hardware::mtk::MtkHardware::capture_and_disable_imax();
                Self::write_opt(crate::hardware::mtk::MtkHardware::CPUFREQ_IMAX_THERMAL_PROTECT, "0");
                if Path::new(crate::hardware::mtk::MtkHardware::LYNX_GPU_MAX_KHZ).exists() {
                    let (v, _) = crate::hardware::detector::ScoreBoard::evaluate();
                    let gpu = crate::hardware::capability::HardwareCapability::resolve_gpu(v);
                    if let Some(opp) = crate::hardware::mtk::MtkHardware::resolve_verified_peak_opp(&gpu) {
                        let base_khz = crate::hardware::mtk::MtkHardware::capture_orig_gpu_max_khz().unwrap_or(opp.freq_khz);
                        Self::write_opt(crate::hardware::mtk::MtkHardware::LYNX_GPU_MAX_KHZ, opp.freq_khz.max(base_khz).to_string());
                    }
                }
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
                Self::neutralize_quiet_therm();

                "Thermal mode configured to Hardware Safety Dominant (Gaming, 75°C limits).".to_string()
            }

            "extreme" | "bypass" => {
                Self::freeze_oem_throttlers();

                // Maximum bypass for extreme benchmarks/gaming
                Self::write_opt(crate::hardware::mtk::MtkHardware::LYNX_THERMAL_SILENCER, "1");
                crate::hardware::mtk::MtkHardware::capture_and_disable_imax();
                Self::write_opt(crate::hardware::mtk::MtkHardware::CPUFREQ_IMAX_THERMAL_PROTECT, "0");
                if Path::new(crate::hardware::mtk::MtkHardware::LYNX_GPU_MAX_KHZ).exists() {
                    let (v, _) = crate::hardware::detector::ScoreBoard::evaluate();
                    let gpu = crate::hardware::capability::HardwareCapability::resolve_gpu(v);
                    if let Some(opp) = crate::hardware::mtk::MtkHardware::resolve_verified_peak_opp(&gpu) {
                        let base_khz = crate::hardware::mtk::MtkHardware::capture_orig_gpu_max_khz().unwrap_or(opp.freq_khz);
                        Self::write_opt(crate::hardware::mtk::MtkHardware::LYNX_GPU_MAX_KHZ, opp.freq_khz.max(base_khz).to_string());
                    }
                }
                Self::write_opt("/proc/ppm/policy_status", "3 0");
                Self::write_opt("/proc/ppm/policy_status", "4 0");
                Self::write_opt("/proc/ppm/policy_status", "5 0");
                Self::write_opt("/proc/ppm/cpi/cpi_enabled", "0");

                // Unthrottle MediaTek GPU & FPSGO limits
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

                Self::write_opt("/sys/class/kgsl/kgsl-3d0/throttling", "0");
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel", "0");

                // Elevate non-battery trip points to 85°C (85000)
                Self::set_limit_raw(85000);
                Self::neutralize_quiet_therm();

                // Disable thermal zone modes on non-battery zones
                // CRITICAL HARDWARE SAFETY GUARD: MediaTek vendor thermal zones (mtkts*) must NEVER
                // have their mode set to "disabled", because MTK's kernel driver attempts unregistration
                // which dereferences ERR_PTR(-13) inside mtk_thermal_zone_device_unregister_wrapper at strncpy.
                for id in 0..128 {
                    let tz_dir = format!("/sys/class/thermal/thermal_zone{}", id);
                    if let Some(t_type) = Self::read_trimmed(format!("{}/type", tz_dir)) {
                        let lower = t_type.to_lowercase();
                        if !Self::is_battery_zone(&t_type) && !lower.starts_with("mtkts") {
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
        let mut modified = 0;

        for id in 0..128 {
            let tz_dir = format!("/sys/class/thermal/thermal_zone{}", id);
            if !Path::new(&tz_dir).exists() {
                continue;
            }

            // Skip disabled/dummy zones reporting uninitialized negative readings (e.g. -273000)
            if let Some(cur_temp) = Self::read_trimmed(format!("{}/temp", tz_dir))
                .and_then(|s| s.parse::<i64>().ok())
            {
                if cur_temp < 0 {
                    continue;
                }
            }

            let t_type = Self::read_trimmed(format!("{}/type", tz_dir)).unwrap_or_default();
            if Self::is_battery_zone(&t_type) {
                // NEVER tamper with battery / charger thermal safety
                continue;
            }

            for tid in 0..16 {
                let tp_path = format!("{}/trip_point_{}_temp", tz_dir, tid);
                if !Path::new(&tp_path).exists() {
                    continue;
                }
                let cur_tp = Self::read_trimmed(&tp_path).and_then(|s| s.parse::<i64>().ok());
                let tp_type = Self::read_trimmed(format!("{}/trip_point_{}_type", tz_dir, tid))
                    .unwrap_or_default();
                if let Some(target_mc) =
                    Self::resolve_zone_trip_write(&t_type, &tp_type, cur_tp, milli_c)
                {
                    if cur_tp != Some(target_mc) && Self::write_opt(&tp_path, target_mc.to_string()) {
                        modified += 1;
                    }
                }
            }
        }
        modified
    }

    /// Sets custom trip point temperature limit on eligible non-silicon skin zones
    /// while shielding silicon CPU/GPU trip floors and recovering stale core isolation.
    pub fn set_limit(temp_c: i32) -> String {
        let clamped = temp_c.clamp(40, 95);
        let milli_c = (clamped as i64) * 1000;
        let count = Self::set_limit_raw(milli_c);
        Self::recover_stale_cpu_isolation();
        format!("Successfully updated {} trip points to {}°C across active SoC thermal zones.", count, clamped)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn silicon_zone_classifier_protects_cpu_gpu_pmic_and_allows_skin_zones() {
        for z in [
            "cpuss-0-step",
            "cpu-1-0-step",
            "cpu-1-3-step",
            "gpu-step",
            "pm660-tz",
            "mpm-lowf",
            "camera-lowf",
            "msm-therm-step",
            "xo-therm-step",
        ] {
            assert!(ThermalController::is_silicon_compute_zone(z), "expected silicon zone: {}", z);
        }
        for z in ["quiet-therm-step", "quiet-therm-adc", "pa-therm0-step", "battery", "bms"] {
            assert!(!ThermalController::is_silicon_compute_zone(z), "unexpected silicon classification: {}", z);
        }
    }

    #[test]
    fn resolve_zone_trip_write_protects_silicon_from_stable_52c_and_heals_poisoned_trips() {
        // Skin passive zone accepts 52°C
        assert_eq!(
            ThermalController::resolve_zone_trip_write("quiet-therm-step", "passive", Some(46_000), 52_000),
            Some(52_000)
        );
        // Healthy silicon zone at 95°C must NOT be lowered to 52°C
        assert_eq!(
            ThermalController::resolve_zone_trip_write("cpu-1-0-step", "passive", Some(95_000), 52_000),
            None
        );
        // Poisoned silicon zone stuck at 52°C or 75°C must heal to 90°C floor
        assert_eq!(
            ThermalController::resolve_zone_trip_write("cpuss-0-step", "passive", Some(52_000), 52_000),
            Some(90_000)
        );
        assert_eq!(
            ThermalController::resolve_zone_trip_write("cpu-1-1-step", "passive", Some(75_000), 52_000),
            Some(90_000)
        );
        // Critical / hot shutdown trips and battery zones are strictly untouchable
        assert_eq!(
            ThermalController::resolve_zone_trip_write("cpu-1-0-step", "critical", Some(52_000), 90_000),
            None
        );
        assert_eq!(
            ThermalController::resolve_zone_trip_write("quiet-therm-step", "hot", Some(60_000), 52_000),
            None
        );
        assert_eq!(
            ThermalController::resolve_zone_trip_write("battery", "passive", Some(45_000), 80_000),
            None
        );
        assert_eq!(
            ThermalController::resolve_zone_trip_write("bms", "passive", Some(45_000), 52_000),
            None
        );
    }
}
