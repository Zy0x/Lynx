use std::fmt::Write as FmtWrite;
use std::path::Path;

use crate::hardware::capability::{GpuDriver, HardwareCapability};
use crate::hardware::detector::ScoreBoard;
use crate::sysfs::{SysfsWriter, WriteMode};

pub struct GpuController;

impl GpuController {
    fn write_opt(path: impl AsRef<Path>, val: impl AsRef<str>) {
        let p = path.as_ref();
        if p.exists() {
            let _ = SysfsWriter::write(p, val, WriteMode::ForcePermission);
        }
    }

    /// Emits the exact JSON schema expected by LynxRepository & WebUI for GPU telemetry & capabilities.
    pub fn info_json() -> String {
        let (vendor, _) = ScoreBoard::evaluate();
        let gpu = HardwareCapability::resolve_gpu(vendor);

        let freqs_str: Vec<String> = gpu.available_freqs_mhz.iter().map(|f| f.to_string()).collect();
        let govs_str: Vec<String> = gpu.available_governors.iter().map(|g| format!("\"{}\"", g)).collect();

        let mut out = String::new();
        let _ = write!(
            out,
            "{{\"vendor\":\"{}\",\"platform\":\"{}\",\"cur_freq\":{},\"cur_mhz\":{},\"min_freq\":{},\"min_mhz\":{},\"max_freq\":{},\"max_mhz\":{},\"cur_gov\":\"{}\",\"load\":{},\"busy\":{},\"boost\":{},\"avail_freqs\":[{}],\"avail_govs\":[{}]}}",
            gpu.vendor_name,
            gpu.platform_tag,
            gpu.cur_freq_mhz,
            gpu.cur_freq_mhz,
            gpu.min_freq_mhz,
            gpu.min_freq_mhz,
            gpu.max_freq_mhz,
            gpu.max_freq_mhz,
            gpu.cur_governor,
            gpu.load_pct,
            gpu.load_pct,
            gpu.boost_level,
            freqs_str.join(","),
            govs_str.join(",")
        );
        out
    }

    pub fn set_freq(min_mhz: u64, max_mhz: Option<u64>) -> String {
        let mut target_min = min_mhz;
        let mut target_max = max_mhz.unwrap_or(min_mhz);
        if target_min > target_max {
            std::mem::swap(&mut target_min, &mut target_max);
        }

        let (vendor, _) = ScoreBoard::evaluate();
        let gpu = HardwareCapability::resolve_gpu(vendor);

        // Snap to real hardware OPP frequencies if available
        if !gpu.available_freqs_mhz.is_empty() {
            target_min = gpu
                .available_freqs_mhz
                .iter()
                .copied()
                .filter(|&f| f >= target_min)
                .min()
                .unwrap_or(*gpu.available_freqs_mhz.last().unwrap());
            target_max = gpu
                .available_freqs_mhz
                .iter()
                .copied()
                .filter(|&f| f <= target_max)
                .max()
                .unwrap_or(*gpu.available_freqs_mhz.first().unwrap());
            if target_min > target_max {
                target_min = target_max;
            }
        }

        match gpu.driver {
            GpuDriver::QcomKgsl => {
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/max_clock_mhz", target_max.to_string());
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/min_clock_mhz", target_min.to_string());
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/max_gpuclk", (target_max * 1_000_000).to_string());
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq", (target_min * 1_000_000).to_string());
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq", (target_max * 1_000_000).to_string());
                format!("Adreno GPU clock set to {} - {} MHz.", target_min, target_max)
            }
            GpuDriver::MtkGed | GpuDriver::MtkGpufreq => {
                let min_khz = target_min * 1000;
                let max_khz = target_max * 1000;

                if target_min == target_max {
                    Self::write_opt("/proc/gpufreq/gpufreq_opp_freq", max_khz.to_string());
                    Self::write_opt("/proc/gpufreqv2/gpufreq_opp_freq", max_khz.to_string());
                } else {
                    Self::write_opt("/proc/gpufreq/gpufreq_opp_freq", "0");
                    Self::write_opt("/proc/gpufreqv2/gpufreq_opp_freq", "0");
                }

                // Resolve exact OPP indices from parsed table
                if let Some(min_opp) = gpu.opp_table.iter().min_by_key(|e| (e.freq_mhz as i64 - target_min as i64).abs()) {
                    Self::write_opt("/sys/kernel/ged/hal/custom_boost_gpu_freq", min_opp.index.to_string());
                }
                if let Some(max_opp) = gpu.opp_table.iter().min_by_key(|e| (e.freq_mhz as i64 - target_max as i64).abs()) {
                    Self::write_opt("/sys/kernel/ged/hal/custom_upbound_gpu_freq", max_opp.index.to_string());
                }

                Self::write_opt("/sys/module/ged/parameters/gpu_bottom_freq", min_khz.to_string());
                Self::write_opt("/sys/module/ged/parameters/gpu_cust_boost_freq", min_khz.to_string());
                Self::write_opt("/sys/module/ged/parameters/gpu_cust_upbound_freq", max_khz.to_string());
                format!("MediaTek Mali GPU clock set to {} - {} MHz.", target_min, target_max)
            }
            GpuDriver::GenericDevfreq => {
                if let Some(ref devpath) = gpu.devfreq_path {
                    Self::write_opt(devpath.join("min_freq"), (target_min * 1_000_000).to_string());
                    Self::write_opt(devpath.join("max_freq"), (target_max * 1_000_000).to_string());
                }
                format!("Devfreq GPU clock set to {} - {} MHz.", target_min, target_max)
            }
            GpuDriver::Unsupported => "GPU frequency control is not exposed on this kernel.".to_string(),
        }
    }

    pub fn set_gov(target_gov: &str) -> String {
        let (vendor, _) = ScoreBoard::evaluate();
        let gpu = HardwareCapability::resolve_gpu(vendor);

        match gpu.driver {
            GpuDriver::QcomKgsl => {
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/governor", target_gov);
                format!("Qualcomm Adreno GPU governor set to {}.", target_gov)
            }
            GpuDriver::MtkGed | GpuDriver::MtkGpufreq => {
                Self::write_opt("/sys/kernel/ged/hal/dvfs_loading_mode", target_gov);
                format!("MediaTek GED GPU loading mode set to {}.", target_gov)
            }
            GpuDriver::GenericDevfreq => {
                if let Some(ref devpath) = gpu.devfreq_path {
                    Self::write_opt(devpath.join("governor"), target_gov);
                }
                format!("Devfreq GPU governor set to {}.", target_gov)
            }
            GpuDriver::Unsupported => "GPU governor control is not exposed on this kernel.".to_string(),
        }
    }

    pub fn set_boost(level: u32) -> String {
        let lvl = level.min(3);
        if Path::new("/sys/class/kgsl/kgsl-3d0").is_dir() {
            Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost", lvl.to_string());
            Self::write_opt("/sys/class/kgsl/kgsl-3d0/adrenoboost", lvl.to_string());
            return format!("Adreno GPU boost set to {}.", lvl);
        }
        let flag = if lvl > 0 { "1" } else { "0" };
        Self::write_opt("/sys/module/ged/parameters/boost_amp", lvl.to_string());
        Self::write_opt("/sys/module/ged/parameters/ged_boost_enable", flag);
        Self::write_opt("/sys/module/ged/parameters/boost_gpu_enable", flag);
        Self::write_opt("/sys/module/ged/parameters/enable_gpu_boost", flag);
        Self::write_opt("/sys/module/ged/parameters/ged_smart_boost", flag);
        format!("MediaTek GED GPU boost set to level {}.", lvl)
    }
}
