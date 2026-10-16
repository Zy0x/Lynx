use std::path::Path;

pub struct QcomHardware;

impl QcomHardware {
    // ── Adreno KGSL Direct GPU Driver Nodes ─────────────────────────────────
    pub const KGSL_3D0_DEV: &'static str = "/dev/kgsl-3d0";
    pub const KGSL_SYSFS_DIR: &'static str = "/sys/class/kgsl/kgsl-3d0";
    pub const KGSL_GPUCLK: &'static str = "/sys/class/kgsl/kgsl-3d0/gpuclk";
    pub const KGSL_DEVFREQ_CUR: &'static str = "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq";
    pub const KGSL_DEVFREQ_GOV: &'static str = "/sys/class/kgsl/kgsl-3d0/devfreq/governor";
    pub const KGSL_DEVFREQ_MIN: &'static str = "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq";
    pub const KGSL_DEVFREQ_MAX: &'static str = "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq";
    pub const KGSL_GPUBUSY: &'static str = "/sys/class/kgsl/kgsl-3d0/gpubusy";
    pub const KGSL_IDLE_TIMER: &'static str = "/sys/class/kgsl/kgsl-3d0/idle_timer";
    pub const KGSL_THROTTLING: &'static str = "/sys/class/kgsl/kgsl-3d0/throttling";
    pub const KGSL_THERMAL_PWRLEVEL: &'static str = "/sys/class/kgsl/kgsl-3d0/thermal_pwrlevel";
    pub const KGSL_ADRENOBOOST: &'static str = "/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost";
    pub const KGSL_MAX_PWRLEVEL: &'static str = "/sys/class/kgsl/kgsl-3d0/max_pwrlevel";
    pub const KGSL_MIN_PWRLEVEL: &'static str = "/sys/class/kgsl/kgsl-3d0/min_pwrlevel";
    pub const KGSL_BUS_SPLIT: &'static str = "/sys/class/kgsl/kgsl-3d0/bus_split";
    pub const KGSL_FORCE_BUS_ON: &'static str = "/sys/class/kgsl/kgsl-3d0/force_bus_on";
    pub const KGSL_FORCE_CLK_ON: &'static str = "/sys/class/kgsl/kgsl-3d0/force_clk_on";
    pub const KGSL_FORCE_RAIL_ON: &'static str = "/sys/class/kgsl/kgsl-3d0/force_rail_on";
    pub const KGSL_PMQOS_ACTIVE_LATENCY: &'static str = "/sys/class/kgsl/kgsl-3d0/pmqos_active_latency";

    // ── Qualcomm CPU Boost Driver ───────────────────────────────────────────
    pub const CPU_BOOST_INPUT_MS: &'static str = "/sys/module/cpu_boost/parameters/input_boost_ms";
    pub const CPU_BOOST_INPUT_FREQ: &'static str = "/sys/module/cpu_boost/parameters/input_boost_freq";
    pub const CPU_BOOST_SCHED_BOOST_ON_INPUT: &'static str = "/sys/module/cpu_boost/parameters/sched_boost_on_input";
    pub const CPU_BOOST_POWERKEY_BOOST_MS: &'static str = "/sys/module/cpu_boost/parameters/powerkey_boost_ms";

    // ── Qualcomm WALT Scheduler (Window-Assisted Load Tracking) ─────────────
    pub const SCHED_BOOST: &'static str = "/proc/sys/kernel/sched_boost";
    pub const SCHED_UPMIGRATE: &'static str = "/proc/sys/kernel/sched_upmigrate";
    pub const SCHED_DOWNMIGRATE: &'static str = "/proc/sys/kernel/sched_downmigrate";
    pub const SCHED_GROUP_UPMIGRATE: &'static str = "/proc/sys/kernel/sched_group_upmigrate";
    pub const SCHED_GROUP_DOWNMIGRATE: &'static str = "/proc/sys/kernel/sched_group_downmigrate";
    pub const SCHED_WALT_ROTATE_BIG_TASKS: &'static str = "/proc/sys/kernel/sched_walt_rotate_big_tasks";
    pub const SCHED_LITTLE_CLUSTER_COLOC_FMIN_KHZ: &'static str = "/proc/sys/kernel/sched_little_cluster_coloc_fmin_khz";
    pub const SCHED_MIN_TASK_UTIL_FOR_BOOST: &'static str = "/proc/sys/kernel/sched_min_task_util_for_boost";
    pub const SCHED_CSTATE_AWARE: &'static str = "/proc/sys/kernel/sched_cstate_aware";

    // ── Qualcomm Core Control (Big/Prime Cluster Active Unparking) ──────────
    pub const CORE_CTL_DIR_CPU4: &'static str = "/sys/devices/system/cpu/cpu4/core_ctl";
    pub const CORE_CTL_DIR_CPU7: &'static str = "/sys/devices/system/cpu/cpu7/core_ctl";

    pub fn is_kgsl_available() -> bool {
        Path::new(Self::KGSL_3D0_DEV).exists() || Path::new(Self::KGSL_SYSFS_DIR).exists()
    }

    pub fn is_cpu_boost_available() -> bool {
        Path::new("/sys/module/cpu_boost").exists()
    }

    pub fn is_walt_available() -> bool {
        Path::new(Self::SCHED_BOOST).exists() && Path::new(Self::SCHED_UPMIGRATE).exists()
    }

    pub fn is_core_ctl_available() -> bool {
        Path::new(Self::CORE_CTL_DIR_CPU4).exists() || Path::new("/sys/devices/system/cpu/core_ctl").exists()
    }
}
