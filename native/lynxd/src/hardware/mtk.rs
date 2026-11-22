use std::fs;
use std::path::{Path, PathBuf};
use std::thread::sleep;
use std::time::Duration;

use super::capability::{GpuCapability, GpuOppEntry};

pub struct MtkHardware;

impl MtkHardware {
    // ── MediaTek PPM (Processor Power Management) ───────────────────────────
    pub const PPM_ENABLED: &'static str = "/proc/ppm/enabled";
    pub const PPM_MODE: &'static str = "/proc/ppm/mode";
    pub const PPM_POLICY_STATUS: &'static str = "/proc/ppm/policy_status";
    pub const PPM_HARD_USERLIMIT_MAX_CPU_FREQ: &'static str = "/proc/ppm/policy/hard_userlimit_max_cpu_freq";
    pub const PPM_HARD_USERLIMIT_MIN_CPU_FREQ: &'static str = "/proc/ppm/policy/hard_userlimit_min_cpu_freq";
    pub const PPM_USERLIMIT_MAX_CPU_FREQ: &'static str = "/proc/ppm/policy/userlimit_max_cpu_freq";
    pub const PPM_USERLIMIT_MIN_CPU_FREQ: &'static str = "/proc/ppm/policy/userlimit_min_cpu_freq";

    // ── MediaTek CPUFreq & CoreLink Interconnect (CCI) ───────────────────────
    pub const CPUFREQ_POWER_MODE: &'static str = "/proc/cpufreq/cpufreq_power_mode";
    pub const CPUFREQ_CCI_MODE: &'static str = "/proc/cpufreq/cpufreq_cci_mode";
    pub const CPUFREQ_IMAX_THERMAL_PROTECT: &'static str = "/proc/cpufreq/cpufreq_imax_thermal_protect";

    // ── MediaTek Perfmgr & EAS Boosting ─────────────────────────────────────
    pub const PERFMGR_TA_BOOST: &'static str = "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost";
    pub const PERFMGR_FG_BOOST: &'static str = "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_fg_boost";
    pub const PERFMGR_UCLAMP_MIN: &'static str = "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_uclamp_min";
    pub const PERFMGR_TA_UCLAMP_MIN: &'static str = "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_uclamp_min";
    pub const PERFMGR_BIG_TASK_ROTATION: &'static str = "/proc/perfmgr/boost_ctrl/eas_ctrl/sched_big_task_rotation";
    pub const PERFMGR_MIGRATE_COST: &'static str = "/proc/perfmgr/boost_ctrl/eas_ctrl/m_sched_migrate_cost_n";
    pub const PERFMGR_SCHEDPLUS_UP_THROTTLE: &'static str = "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_schedplus_up_throttle";
    pub const PERFMGR_SCHEDPLUS_DOWN_THROTTLE: &'static str = "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_schedplus_down_throttle";
    pub const PERFMGR_SCHEDPLUS_SYNC_FLAG: &'static str = "/proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_schedplus_sync_flag";

    // ── MediaTek GED & ARM Mali GPU ─────────────────────────────────────────
    pub const GED_HAL_FREQ: &'static str = "/sys/kernel/ged/hal/current_freqency";
    pub const GED_HAL_UTIL: &'static str = "/sys/kernel/ged/hal/gpu_utilization";
    pub const GED_HAL_DVFS: &'static str = "/sys/kernel/ged/hal/dvfs_loading_mode";
    pub const GED_HAL_CUSTOM_BOOST_FREQ: &'static str = "/sys/kernel/ged/hal/custom_boost_gpu_freq";
    pub const GED_HAL_CUSTOM_UPBOUND_FREQ: &'static str = "/sys/kernel/ged/hal/custom_upbound_gpu_freq";
    pub const GED_BOOST_AMP: &'static str = "/sys/module/ged/parameters/boost_amp";
    pub const GED_GX_FORCE_CPU_BOOST: &'static str = "/sys/module/ged/parameters/gx_force_cpu_boost";
    pub const GED_GX_GAME_MODE: &'static str = "/sys/module/ged/parameters/gx_game_mode";
    pub const GED_GX_BOOST_ON: &'static str = "/sys/module/ged/parameters/gx_boost_on";
    pub const GED_GPU_BOTTOM_FREQ: &'static str = "/sys/module/ged/parameters/gpu_bottom_freq";
    pub const GED_GPU_CUST_BOOST_FREQ: &'static str = "/sys/module/ged/parameters/gpu_cust_boost_freq";
    pub const GED_GPU_CUST_UPBOUND_FREQ: &'static str = "/sys/module/ged/parameters/gpu_cust_upbound_freq";
    pub const GED_MONITOR_3D_FENCE_DISABLE: &'static str = "/sys/module/ged/parameters/ged_monitor_3D_fence_disable";
    pub const GED_FB_DVFS_THRESHOLD: &'static str = "/sys/module/ged/parameters/g_fb_dvfs_threshold";
    pub const GED_BOOST_EXTRA: &'static str = "/sys/module/ged/parameters/boost_extra";
    pub const GED_BOOST_GPU_ENABLE: &'static str = "/sys/module/ged/parameters/boost_gpu_enable";
    pub const GED_GPU_IDLE: &'static str = "/sys/module/ged/parameters/gpu_idle";

    // ── MediaTek GPUFreq v1 & v2 ────────────────────────────────────────────
    pub const GPUFREQ_VAR_DUMP: &'static str = "/proc/gpufreq/gpufreq_var_dump";
    pub const GPUFREQ_OPP_FREQ: &'static str = "/proc/gpufreq/gpufreq_opp_freq";
    pub const GPUFREQV2_OPP_FREQ: &'static str = "/proc/gpufreqv2/gpufreq_opp_freq";

    // ── MediaTek FPSGO Frame Pacer ──────────────────────────────────────────
    pub const FPSGO_ENABLE: &'static str = "/sys/kernel/fpsgo/common/fpsgo_enable";
    pub const FPSGO_BOOST_TA: &'static str = "/sys/kernel/fpsgo/fbt/boost_ta";
    pub const FPSGO_ULTRA_RESCUE: &'static str = "/sys/kernel/fpsgo/fbt/ultra_rescue";
    pub const FPSGO_FBT_THRM_TEMP_TH: &'static str = "/sys/kernel/fpsgo/fbt/thrm_temp_th";
    pub const FPSGO_FBT_THRM_LIMIT_CPU: &'static str = "/sys/kernel/fpsgo/fbt/thrm_limit_cpu";
    pub const FBT_CPU_BOOST_AFFINITY: &'static str = "/sys/module/fbt_cpu/parameters/boost_affinity";
    pub const FBT_CPU_RESCUE_SECOND: &'static str = "/sys/module/fbt_cpu/parameters/rescue_second_enable";
    pub const FBT_CPU_RESCUE_OPP_F: &'static str = "/sys/module/fbt_cpu/parameters/rescue_opp_f";
    pub const FBT_CPU_RESCUE_PERCENT: &'static str = "/sys/module/fbt_cpu/parameters/rescue_percent";
    pub const FBT_CPU_CM_BIG_CAP: &'static str = "/sys/module/fbt_cpu/parameters/cm_big_cap";
    pub const FBT_CPU_BHR_OPP: &'static str = "/sys/module/fbt_cpu/parameters/bhr_opp";
    pub const FBT_CPU_GCC_ENABLE: &'static str = "/sys/module/fbt_cpu/parameters/gcc_enable";

    pub fn is_ppm_available() -> bool {
        Path::new(Self::PPM_ENABLED).exists() || Path::new(Self::PPM_POLICY_STATUS).exists()
    }

    pub fn is_ged_available() -> bool {
        Path::new("/dev/ged").exists() || Path::new("/sys/module/ged").exists()
    }

    pub fn is_gpufreq_available() -> bool {
        Path::new(Self::GPUFREQ_VAR_DUMP).exists() || Path::new(Self::GPUFREQ_OPP_FREQ).exists()
    }

    pub fn is_perfmgr_available() -> bool {
        Path::new("/proc/perfmgr/boost_ctrl/eas_ctrl").exists()
    }

    pub fn is_fpsgo_available() -> bool {
        Path::new(Self::FPSGO_ENABLE).exists()
    }

    pub const LYNX_GPU_MAX_KHZ: &'static str = "/proc/lynx/gpu_max_khz";
    pub const LYNX_THERMAL_SILENCER: &'static str = "/proc/lynx/thermal_silencer";
    pub const CPUFREQ_IMAX_ENABLE: &'static str = "/proc/cpufreq/cpufreq_imax_enable";
    pub const DRAM_CTRL_DDR: &'static str = "/proc/perfmgr/boost_ctrl/dram_ctrl/ddr";

    const ORIG_GPU_MAX_FILE: &'static str = "/dev/lynx_orig_gpu_max_khz";
    const VERIFIED_GPU_MAX_FILE: &'static str = "/dev/lynx_verified_gpu_max_khz";
    const ORIG_IMAX_MODE_FILE: &'static str = "/dev/lynx_orig_imax_mode";
    const DVFSRC_PEAK_OPP_FILE: &'static str = "/dev/lynx_dvfsrc_peak_opp";

    fn read_u64_node(path: &Path) -> Option<u64> {
        fs::read_to_string(path)
            .ok()
            .and_then(|s| s.trim().parse::<u64>().ok())
    }

    fn write_raw_node(path: impl AsRef<Path>, val: &str) -> bool {
        let p = path.as_ref();
        if !p.exists() {
            return false;
        }
        crate::sysfs::SysfsWriter::write(p, val, crate::sysfs::WriteMode::ForcePermission).is_ok()
    }

    /// Dynamically discovers Mali driver sysfs base directories instead of assuming one fixed platform bus address.
    pub fn mali_sysfs_paths() -> Vec<PathBuf> {
        let mut paths = Vec::new();
        if let Ok(entries) = fs::read_dir("/sys/devices/platform") {
            for entry in entries.flatten() {
                let p = entry.path();
                if p.is_dir() && entry.file_name().to_string_lossy().to_lowercase().contains("mali") {
                    paths.push(p);
                }
            }
        }
        for fallback in &["/sys/class/misc/mali0/device", "/sys/kernel/gpu"] {
            let p = PathBuf::from(fallback);
            if p.exists() && !paths.contains(&p) {
                paths.push(p);
            }
        }
        paths
    }

    /// Snapshots boot/default `/proc/lynx/gpu_max_khz` once so restores never hardcode clock limits.
    pub fn capture_orig_gpu_max_khz() -> Option<u64> {
        if !Path::new(Self::LYNX_GPU_MAX_KHZ).exists() {
            return None;
        }
        if let Some(saved) = Self::read_u64_node(Path::new(Self::ORIG_GPU_MAX_FILE)) {
            if saved > 0 {
                return Some(saved);
            }
        }
        if let Some(live) = Self::read_u64_node(Path::new(Self::LYNX_GPU_MAX_KHZ)) {
            if live > 0 {
                let _ = fs::write(Self::ORIG_GPU_MAX_FILE, live.to_string());
                return Some(live);
            }
        }
        None
    }

    /// Restores `/proc/lynx/gpu_max_khz` to the dynamically captured kernel baseline.
    pub fn restore_orig_gpu_max_khz() {
        if let Some(orig_khz) = Self::read_u64_node(Path::new(Self::ORIG_GPU_MAX_FILE))
            .or_else(Self::capture_orig_gpu_max_khz)
        {
            let _ = Self::write_raw_node(Self::LYNX_GPU_MAX_KHZ, &orig_khz.to_string());
        }
    }

    fn read_gpufreq_var_dump_freq_khz() -> Option<u64> {
        let content = fs::read_to_string(Self::GPUFREQ_VAR_DUMP).ok()?;
        let mut sw_khz: Option<u64> = None;
        for line in content.lines() {
            if let Some(pos) = line.find("freq:") {
                let prefix = &line[..pos];
                let digits: String = line[pos + 5..]
                    .trim_start()
                    .chars()
                    .take_while(|c| c.is_ascii_digit())
                    .collect();
                if let Ok(khz) = digits.parse::<u64>() {
                    if prefix.contains("(real)") {
                        if khz > 0 {
                            return Some(khz);
                        }
                    } else if khz > 0 && sw_khz.is_none() {
                        sw_khz = Some(khz);
                    }
                }
            }
        }
        sw_khz
    }

    /// Dynamically probes and caches the highest silicon-verified GPU OPP entry without hardcoding.
    pub fn resolve_verified_peak_opp(gpu: &GpuCapability) -> Option<GpuOppEntry> {
        if gpu.opp_table.is_empty() {
            return None;
        }
        let table_top_khz = gpu.highest_opp().map(|e| e.freq_khz).unwrap_or(0);
        let orig_cap_khz = Self::capture_orig_gpu_max_khz();
        if let Ok(raw_cache) = fs::read_to_string(Self::VERIFIED_GPU_MAX_FILE) {
            let mut parts = raw_cache.trim().split(':');
            if let (Some(c_khz), Some(c_top)) = (
                parts.next().and_then(|s| s.parse::<u64>().ok()),
                parts.next().and_then(|s| s.parse::<u64>().ok()),
            ) {
                if c_top == table_top_khz {
                    if let Some(found) = gpu.opp_table.iter().min_by_key(|e| (e.freq_khz as i64 - c_khz as i64).abs()) {
                        if found.freq_khz == c_khz {
                            return Some(found.clone());
                        }
                    }
                }
            }
        }

        let mut sorted = gpu.opp_table.clone();
        sorted.sort_by(|a, b| b.freq_khz.cmp(&a.freq_khz));

        if Path::new(Self::LYNX_GPU_MAX_KHZ).exists() && Path::new(Self::GPUFREQ_OPP_FREQ).exists() {
            let mut verified: Option<GpuOppEntry> = None;
            for opp in &sorted {
                // Avoid testing lower than boot baseline when boot baseline itself is valid.
                if let Some(orig) = orig_cap_khz {
                    if opp.freq_khz <= orig {
                        verified = Some(opp.clone());
                        break;
                    }
                }
                let khz_s = opp.freq_khz.to_string();
                let _ = Self::write_raw_node(Self::LYNX_GPU_MAX_KHZ, &khz_s);
                let _ = Self::write_raw_node(Self::GPUFREQ_OPP_FREQ, &khz_s);
                sleep(Duration::from_millis(2));
                if let Some(actual_khz) = Self::read_gpufreq_var_dump_freq_khz() {
                    let delta = (actual_khz as i64 - opp.freq_khz as i64).abs();
                    if delta * 100 <= (opp.freq_khz as i64 * 3) {
                        verified = Some(opp.clone());
                        break;
                    }
                }
            }
            let _ = Self::write_raw_node(Self::GPUFREQ_OPP_FREQ, "0");
            Self::restore_orig_gpu_max_khz();

            if let Some(ref best) = verified {
                let _ = fs::write(Self::VERIFIED_GPU_MAX_FILE, format!("{}:{}", best.freq_khz, table_top_khz));
                return Some(best.clone());
            }
        }

        gpu.highest_opp().cloned()
    }

    /// Dynamically snapshots `/proc/cpufreq/cpufreq_imax_enable` mode before disabling IMAX/MPMM.
    pub fn capture_and_disable_imax() {
        if !Path::new(Self::CPUFREQ_IMAX_ENABLE).exists() {
            return;
        }
        if !Path::new(Self::ORIG_IMAX_MODE_FILE).exists() {
            if let Ok(raw) = fs::read_to_string(Self::CPUFREQ_IMAX_ENABLE) {
                let mode_digit = raw
                    .split('=')
                    .nth(1)
                    .unwrap_or(&raw)
                    .trim_start()
                    .chars()
                    .take_while(|c| c.is_ascii_digit())
                    .collect::<String>();
                if !mode_digit.is_empty() {
                    let _ = fs::write(Self::ORIG_IMAX_MODE_FILE, &mode_digit);
                }
            }
        }
        let _ = Self::write_raw_node(Self::CPUFREQ_IMAX_ENABLE, "0");
    }

    /// Restores `/proc/cpufreq/cpufreq_imax_enable` to the captured original mode (e.g., MPMM / Enabled).
    pub fn restore_orig_imax() {
        if let Ok(saved) = fs::read_to_string(Self::ORIG_IMAX_MODE_FILE) {
            let trimmed = saved.trim();
            if !trimmed.is_empty() {
                let _ = Self::write_raw_node(Self::CPUFREQ_IMAX_ENABLE, trimmed);
            }
        }
    }

    /// Dynamically discovers any `*.dvfsrc/helio-dvfsrc` controller in `/sys/devices/platform`.
    pub fn find_dvfsrc_dir() -> Option<PathBuf> {
        if let Ok(entries) = fs::read_dir("/sys/devices/platform") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                if name.contains("dvfsrc") {
                    let candidate = entry.path().join("helio-dvfsrc");
                    if candidate.is_dir() {
                        return Some(candidate);
                    }
                    if entry.path().join("dvfsrc_opp_table").exists() {
                        return Some(entry.path());
                    }
                }
            }
        }
        None
    }

    /// Parses `dvfsrc_opp_table` dynamically to find the OPP index offering peak DDR frequency & Vcore.
    fn resolve_dvfsrc_peak_opp(dir: &Path) -> String {
        if let Ok(cached) = fs::read_to_string(Self::DVFSRC_PEAK_OPP_FILE) {
            let t = cached.trim();
            if !t.is_empty() && t.chars().all(|c| c.is_ascii_digit()) {
                return t.to_string();
            }
        }
        let mut best: Option<(u64, u64, String)> = None;
        if let Ok(table) = fs::read_to_string(dir.join("dvfsrc_opp_table")) {
            for line in table.lines() {
                // Example: `[OPP0 ]: 800000   uv 4266000  khz`
                let Some(opp_tag_start) = line.find("[OPP") else { continue };
                let after_opp = &line[opp_tag_start + 4..];
                let Some(opp_tag_end) = after_opp.find(']') else { continue };
                let opp_idx = after_opp[..opp_tag_end].trim().to_string();
                if opp_idx.is_empty() || !opp_idx.chars().all(|c| c.is_ascii_digit()) {
                    continue;
                }
                let nums: Vec<u64> = after_opp[opp_tag_end + 1..]
                    .split_whitespace()
                    .filter_map(|tok| tok.parse::<u64>().ok())
                    .collect();
                let (uv, khz) = match nums.as_slice() {
                    [u, k, ..] => (*u, *k),
                    [k] => (0, *k),
                    _ => continue,
                };
                if best.as_ref().map_or(true, |(bk, bu, _)| (khz, uv) > (*bk, *bu)) {
                    best = Some((khz, uv, opp_idx));
                }
            }
        }
        let opp_str = best.map(|(_, _, idx)| idx).unwrap_or_else(|| "0".to_string());
        let _ = fs::write(Self::DVFSRC_PEAK_OPP_FILE, &opp_str);
        opp_str
    }

    /// Dynamically boosts or releases MediaTek DVFSRC DDR bandwidth and Perfmgr DRAM floor.
    pub fn boost_ddr_peak(enable: bool) {
        if let Some(dir) = Self::find_dvfsrc_dir() {
            let val = if enable {
                Self::resolve_dvfsrc_peak_opp(&dir)
            } else {
                "-1".to_string()
            };
            let _ = Self::write_raw_node(Self::DRAM_CTRL_DDR, &val);
            let _ = Self::write_raw_node(dir.join("dvfsrc_force_vcore_dvfs_opp"), &val);
            let _ = Self::write_raw_node(dir.join("dvfsrc_req_ddr_opp"), &val);
            let _ = Self::write_raw_node(dir.join("dvfsrc_req_vcore_opp"), &val);
        } else {
            let val = if enable { "0" } else { "-1" };
            let _ = Self::write_raw_node(Self::DRAM_CTRL_DDR, val);
        }
    }
}
