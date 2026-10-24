use std::path::Path;

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

    // ── MediaTek GPUFreq v1 & v2 ────────────────────────────────────────────
    pub const GPUFREQ_VAR_DUMP: &'static str = "/proc/gpufreq/gpufreq_var_dump";
    pub const GPUFREQ_OPP_FREQ: &'static str = "/proc/gpufreq/gpufreq_opp_freq";
    pub const GPUFREQV2_OPP_FREQ: &'static str = "/proc/gpufreqv2/gpufreq_opp_freq";

    // ── MediaTek FPSGO Frame Pacer ──────────────────────────────────────────
    pub const FPSGO_ENABLE: &'static str = "/sys/kernel/fpsgo/common/fpsgo_enable";
    pub const FPSGO_BOOST_TA: &'static str = "/sys/kernel/fpsgo/fbt/boost_ta";
    pub const FPSGO_ULTRA_RESCUE: &'static str = "/sys/kernel/fpsgo/fbt/ultra_rescue";

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
}
