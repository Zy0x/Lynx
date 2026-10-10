use std::path::Path;

pub struct MtkHardware;

impl MtkHardware {
    pub const PPM_ENABLED: &'static str = "/proc/ppm/enabled";
    pub const PPM_POLICY_STATUS: &'static str = "/proc/ppm/policy_status";
    pub const CPUFREQ_POWER_MODE: &'static str = "/proc/cpufreq/cpufreq_power_mode";
    pub const CPUFREQ_CCI_MODE: &'static str = "/proc/cpufreq/cpufreq_cci_mode";
    pub const GED_HAL_FREQ: &'static str = "/sys/kernel/ged/hal/current_freqency";
    pub const GED_HAL_UTIL: &'static str = "/sys/kernel/ged/hal/gpu_utilization";
    pub const GPUFREQ_VAR_DUMP: &'static str = "/proc/gpufreq/gpufreq_var_dump";

    pub fn is_ppm_available() -> bool {
        Path::new(Self::PPM_ENABLED).exists()
    }

    pub fn is_ged_available() -> bool {
        Path::new("/dev/ged").exists() || Path::new("/sys/module/ged").exists()
    }

    pub fn is_gpufreq_available() -> bool {
        Path::new(Self::GPUFREQ_VAR_DUMP).exists()
    }
}
