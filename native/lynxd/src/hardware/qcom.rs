use std::path::Path;

pub struct QcomHardware;

impl QcomHardware {
    pub const KGSL_3D0_DEV: &'static str = "/dev/kgsl-3d0";
    pub const KGSL_GPUCLK: &'static str = "/sys/class/kgsl/kgsl-3d0/gpuclk";
    pub const KGSL_DEVFREQ_CUR: &'static str = "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq";
    pub const KGSL_GPUBUSY: &'static str = "/sys/class/kgsl/kgsl-3d0/gpubusy";
    pub const CPU_BOOST_INPUT: &'static str = "/sys/module/cpu_boost/parameters/input_boost_ms";

    pub fn is_kgsl_available() -> bool {
        Path::new(Self::KGSL_3D0_DEV).exists() || Path::new("/sys/class/kgsl/kgsl-3d0").exists()
    }

    pub fn is_cpu_boost_available() -> bool {
        Path::new("/sys/module/cpu_boost").exists()
    }
}
