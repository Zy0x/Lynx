use std::fmt::Write as FmtWrite;
use std::path::Path;
use std::sync::atomic::{AtomicU64, AtomicU8, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};

use crate::hardware::capability::{GpuCapability, GpuDriver, HardwareCapability};
use crate::hardware::detector::ScoreBoard;
use crate::state::device_state::WorkloadLevel;
use crate::state::ConfigStore;
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub enum DynamicGpuState {
    Idle = 0,  // Tier 0: Lowest OPP (e.g. 300 MHz) - Stationary / Reading / Deep Sleep
    Light = 1, // Tier 1: Light OPP (~30% quantile, e.g. ~462-495 MHz) - Single tap, photo preview, light UI
    Fluid = 2, // Tier 2: Fluid OPP (~55% quantile, e.g. ~588-675 MHz) - 90/120Hz continuous scroll, rapid fling, video
    Peak = 3,  // Tier 3: Peak OPP (100% quantile, e.g. 850-950+ MHz) - Heavy 3D games, load > 65%, heavy shaders
}

static CURRENT_SCALER_STATE: AtomicU8 = AtomicU8::new(0); // 0 = Idle, 1 = Light, 2 = Fluid, 3 = Peak
static LAST_TOUCH_BOOST_MS: AtomicU64 = AtomicU64::new(0);
static LAST_PEAK_MS: AtomicU64 = AtomicU64::new(0);
static LAST_FLUID_MS: AtomicU64 = AtomicU64::new(0);
static LAST_LIGHT_MS: AtomicU64 = AtomicU64::new(0);

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

        let cfg_locked = ConfigStore::get("gpu.is_locked").unwrap_or_default() == "true";
        let locked_freq: u64 = ConfigStore::get("gpu.locked_freq_mhz")
            .and_then(|s| s.parse().ok())
            .unwrap_or(0);

        let (final_min, final_max, is_locked) = if cfg_locked && locked_freq > 0 {
            (locked_freq, locked_freq, true)
        } else if gpu.min_freq_mhz > 0 && gpu.min_freq_mhz == gpu.max_freq_mhz {
            (gpu.min_freq_mhz, gpu.max_freq_mhz, true)
        } else {
            (gpu.min_freq_mhz, gpu.max_freq_mhz, false)
        };

        let mut out = String::new();
        let _ = write!(
            out,
            "{{\"vendor\":\"{}\",\"platform\":\"{}\",\"cur_freq\":{},\"cur_mhz\":{},\"min_freq\":{},\"min_mhz\":{},\"max_freq\":{},\"max_mhz\":{},\"cur_gov\":\"{}\",\"load\":{},\"busy\":{},\"boost\":{},\"is_locked\":{},\"avail_freqs\":[{}],\"avail_govs\":[{}]}}",
            gpu.vendor_name,
            gpu.platform_tag,
            gpu.cur_freq_mhz,
            gpu.cur_freq_mhz,
            final_min,
            final_min,
            final_max,
            final_max,
            gpu.cur_governor,
            gpu.load_pct,
            gpu.load_pct,
            gpu.boost_level,
            is_locked,
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

        let unit_mult: u64 = if let Ok(avail_str) = SysfsReader::read_trimmed("/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies")
            .or_else(|_| SysfsReader::read_trimmed("/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies")) {
            if avail_str.split_whitespace().any(|tok| tok.parse::<u64>().unwrap_or(0) > 10_000_000) {
                1_000_000
            } else if avail_str.split_whitespace().any(|tok| tok.parse::<u64>().unwrap_or(0) > 10_000) {
                1_000
            } else {
                1
            }
        } else {
            1_000_000
        };

        if target_min == 0 && target_max == 0 {
            let _ = ConfigStore::set_batch(&[
                ("gpu.is_locked".to_string(), "false".to_string(), Some("bool".to_string())),
                ("gpu.locked_freq_mhz".to_string(), "0".to_string(), Some("val".to_string())),
                ("gpu.min_freq_mhz".to_string(), "0".to_string(), Some("val".to_string())),
                ("gpu.max_freq_mhz".to_string(), "0".to_string(), Some("val".to_string())),
            ]);

            match gpu.driver {
                GpuDriver::QcomKgsl => {
                    let min_f = gpu.idle_opp().map(|e| e.freq_mhz).unwrap_or(gpu.min_freq_mhz);
                    let max_f = gpu.peak_opp().map(|e| e.freq_mhz).unwrap_or(gpu.max_freq_mhz);
                    let lowest_idx = gpu.num_pwrlevels.saturating_sub(1);
                    Self::write_opt("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", lowest_idx.to_string());
                    Self::write_opt("/sys/class/kgsl/kgsl-3d0/max_pwrlevel", "0");
                    Self::write_opt("/sys/class/kgsl/kgsl-3d0/default_pwrlevel", lowest_idx.to_string());
                    Self::write_opt("/sys/class/kgsl/kgsl-3d0/pwrscale", "1");
                    Self::write_opt("/sys/class/kgsl/kgsl-3d0/min_clock_mhz", min_f.to_string());
                    Self::write_opt("/sys/class/kgsl/kgsl-3d0/max_clock_mhz", max_f.to_string());
                    Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq", "0");
                    Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq", (max_f * unit_mult).to_string());
                    return "Adreno GPU clock reset to Auto DVFS.".to_string();
                }
                GpuDriver::MtkGed | GpuDriver::MtkGpufreq => {
                    Self::write_opt("/proc/gpufreq/gpufreq_opp_freq", "0");
                    Self::write_opt("/proc/gpufreqv2/gpufreq_opp_freq", "0");
                    let min_khz = gpu.idle_opp().map(|e| e.freq_khz).unwrap_or(300_000);
                    let max_khz = gpu.peak_opp().map(|e| e.freq_khz).unwrap_or(950_000);
                    Self::write_opt("/sys/module/ged/parameters/gpu_bottom_freq", min_khz.to_string());
                    Self::write_opt("/sys/module/ged/parameters/gpu_cust_boost_freq", min_khz.to_string());
                    Self::write_opt("/sys/module/ged/parameters/gpu_cust_upbound_freq", max_khz.to_string());
                    if let Some(boost_opp) = gpu.idle_opp() {
                        Self::write_opt("/sys/kernel/ged/hal/custom_boost_gpu_freq", boost_opp.index.to_string());
                    }
                    if let Some(max_opp) = gpu.peak_opp() {
                        Self::write_opt("/sys/kernel/ged/hal/custom_upbound_gpu_freq", max_opp.index.to_string());
                    }
                    return "MediaTek Mali GPU clock reset to Auto DVFS (0).".to_string();
                }
                GpuDriver::GenericDevfreq => {
                    if let (Some(ref devpath), Some(lowest), Some(highest)) = (
                        &gpu.devfreq_path,
                        gpu.idle_opp(),
                        gpu.peak_opp(),
                    ) {
                        Self::write_opt(devpath.join("min_freq"), (lowest.freq_mhz * 1_000_000).to_string());
                        Self::write_opt(devpath.join("max_freq"), (highest.freq_mhz * 1_000_000).to_string());
                    }
                    return "Devfreq GPU clock reset to Auto DVFS.".to_string();
                }
                GpuDriver::Unsupported => return "GPU frequency control is not exposed on this kernel.".to_string(),
            }
        }

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

        let is_locked = target_min == target_max;
        let _ = ConfigStore::set_batch(&[
            ("gpu.is_locked".to_string(), is_locked.to_string(), Some("bool".to_string())),
            ("gpu.locked_freq_mhz".to_string(), if is_locked { target_max.to_string() } else { "0".to_string() }, Some("val".to_string())),
            ("gpu.min_freq_mhz".to_string(), target_min.to_string(), Some("val".to_string())),
            ("gpu.max_freq_mhz".to_string(), target_max.to_string(), Some("val".to_string())),
        ]);

        match gpu.driver {
            GpuDriver::QcomKgsl => {
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/max_clock_mhz", target_max.to_string());
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/min_clock_mhz", target_min.to_string());
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/max_gpuclk", (target_max * 1_000_000).to_string());
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq", "0");
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq", (target_max * unit_mult).to_string());
                Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq", (target_min * unit_mult).to_string());

                if let Ok(entries) = std::fs::read_dir("/sys/class/devfreq") {
                    for entry in entries.flatten() {
                        let path = entry.path();
                        if path.to_string_lossy().contains("kgsl") {
                            Self::write_opt(path.join("min_freq"), "0");
                            Self::write_opt(path.join("max_freq"), (target_max * unit_mult).to_string());
                            Self::write_opt(path.join("min_freq"), (target_min * unit_mult).to_string());
                        }
                    }
                }

                let mut freqs_desc = gpu.available_freqs_mhz.clone();
                freqs_desc.sort_unstable_by(|a, b| b.cmp(a));
                freqs_desc.dedup();

                if !freqs_desc.is_empty() {
                    let lowest_idx = freqs_desc.len() - 1;
                    let max_pwr_idx = freqs_desc.iter().position(|&f| f <= target_max).unwrap_or(0);
                    let min_pwr_idx = freqs_desc.iter().rposition(|&f| f >= target_min).unwrap_or(lowest_idx);
                    let safe_min_pwr = min_pwr_idx.max(max_pwr_idx);

                    Self::write_opt("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", lowest_idx.to_string());
                    Self::write_opt("/sys/class/kgsl/kgsl-3d0/max_pwrlevel", max_pwr_idx.to_string());
                    Self::write_opt("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", safe_min_pwr.to_string());
                    Self::write_opt("/sys/class/kgsl/kgsl-3d0/default_pwrlevel", max_pwr_idx.to_string());
                    Self::write_opt("/sys/class/kgsl/kgsl-3d0/pwrscale", "1");
                }
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

                if let Some(min_opp) = gpu.opp_table.iter().min_by_key(|e| (e.freq_mhz as i64 - target_min as i64).abs()) {
                    Self::write_opt("/sys/kernel/ged/hal/custom_boost_gpu_freq", min_opp.index.to_string());
                }
                if let Some(max_opp) = gpu.opp_table.iter().min_by_key(|e| (e.freq_mhz as i64 - target_max as i64).abs()) {
                    Self::write_opt("/sys/kernel/ged/hal/custom_upbound_gpu_freq", max_opp.index.to_string());
                }

                Self::write_opt("/sys/module/ged/parameters/gpu_bottom_freq", min_khz.to_string());
                Self::write_opt("/sys/module/ged/parameters/gpu_cust_boost_freq", min_khz.to_string());

                let live_upbound = SysfsReader::read_int::<u64>("/sys/module/ged/parameters/gpu_cust_upbound_freq").unwrap_or(0);
                let safe_upbound = max_khz.max(live_upbound);
                Self::write_opt("/sys/module/ged/parameters/gpu_cust_upbound_freq", safe_upbound.to_string());
                format!("MediaTek Mali GPU clock set to {} - {} MHz.", target_min, target_max)
            }
            GpuDriver::GenericDevfreq => {
                if let Some(ref devpath) = gpu.devfreq_path {
                    Self::write_opt(devpath.join("min_freq"), (target_min * 1_000_000).to_string());
                    Self::write_opt(devpath.join("max_freq"), (target_max * 1_000_000).to_string());
                }
                if Path::new("/sys/kernel/gpu/gpu_min_clock").exists() {
                    Self::write_opt("/sys/kernel/gpu/gpu_min_clock", (target_min * 1000).to_string());
                    Self::write_opt("/sys/kernel/gpu/gpu_max_clock", (target_max * 1000).to_string());
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

    /// Immediate sub-1ms hardware touch boost notification triggered by TouchDetector thread.
    /// Elevates GPU floor to Light (~495 MHz) or Fluid (~675 MHz) with zero delay.
    /// NEVER boosts to Peak (950 MHz) on touch alone, preserving maximum battery efficiency.
    pub fn on_hardware_touch() {
        let now_ms = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as u64;

        let last = LAST_TOUCH_BOOST_MS.load(Ordering::Relaxed);
        let is_rapid = crate::daemon::TouchDetector::is_rapid_touch(150, 3);
        let target_state = if is_rapid {
            DynamicGpuState::Fluid
        } else {
            DynamicGpuState::Light
        };

        let current = CURRENT_SCALER_STATE.load(Ordering::Relaxed);
        if now_ms.saturating_sub(last) < 40 && current >= (target_state as u8) {
            return;
        }
        LAST_TOUCH_BOOST_MS.store(now_ms, Ordering::Relaxed);

        let is_locked = ConfigStore::get("gpu.is_locked")
            .map(|v| v == "true" || v == "1")
            .unwrap_or(false);
        if is_locked {
            return;
        }

        // Jump floor up to Light or Fluid instantly (0 ms)
        if current < (target_state as u8) {
            let (vendor, _) = ScoreBoard::evaluate();
            let gpu = HardwareCapability::resolve_gpu(vendor);
            if gpu.is_supported && !gpu.opp_table.is_empty() {
                let target_u8 = target_state as u8;
                CURRENT_SCALER_STATE.store(target_u8, Ordering::Relaxed);
                if target_state == DynamicGpuState::Fluid {
                    LAST_FLUID_MS.store(now_ms, Ordering::Relaxed);
                    LAST_LIGHT_MS.store(now_ms, Ordering::Relaxed);
                } else if target_state == DynamicGpuState::Light {
                    LAST_LIGHT_MS.store(now_ms, Ordering::Relaxed);
                }
                Self::apply_state_hardware(&gpu, target_state);
            }
        }
    }

    /// Autonomous Universal Multi-Factor Dynamic GPU Governor tick executed on every daemon cycle.
    pub fn tick_dynamic_scaler(
        workload: WorkloadLevel,
        temp_c: f32,
        active_profile: &str,
        is_screen_on: bool,
    ) {
        let is_locked = ConfigStore::get("gpu.is_locked")
            .map(|v| v == "true" || v == "1")
            .unwrap_or(false);
        if is_locked {
            return;
        }

        let (vendor, _) = ScoreBoard::evaluate();
        let gpu = HardwareCapability::resolve_gpu(vendor);
        if !gpu.is_supported || gpu.opp_table.is_empty() {
            return;
        }

        let now_ms = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as u64;

        // When screen is OFF, immediately drop to Idle (0 ms latency) for maximum deep sleep savings
        if !is_screen_on {
            if CURRENT_SCALER_STATE.load(Ordering::Relaxed) != 0 {
                CURRENT_SCALER_STATE.store(0, Ordering::Relaxed);
                Self::apply_state_hardware(&gpu, DynamicGpuState::Idle);
            }
            return;
        }

        // Step 1: Base State Resolution from Profile, Workload & Touch Dynamics
        let mut target_state = if active_profile == "extreme" {
            DynamicGpuState::Peak
        } else if active_profile == "powersave" {
            DynamicGpuState::Idle
        } else if active_profile == "performance" {
            if workload == WorkloadLevel::Heavy {
                DynamicGpuState::Peak
            } else {
                DynamicGpuState::Fluid
            }
        } else {
            // Balance profile: Ultra-Efficient 4-Tier Multi-Factor Decision
            let is_rapid = crate::daemon::TouchDetector::is_rapid_touch(150, 3);
            let is_touch = crate::daemon::TouchDetector::is_touch_recent(350);

            // True normalized compute load (MHz) = (current frequency * load %) / 100
            // Eliminates harmonic oscillation / low-frequency load bias where 20% load at 300MHz (only 60MHz compute)
            // was previously falsely misclassified as "Moderate" and pushed GPU to 675MHz.
            let effective_mhz = (gpu.cur_freq_mhz * (gpu.load_pct as u64)) / 100;

            if workload == WorkloadLevel::Heavy || effective_mhz > 520 {
                // Heavy 3D rendering / sustained high compute (> 520 MHz effective): Engage Peak
                DynamicGpuState::Peak
            } else if is_rapid || workload == WorkloadLevel::Moderate || effective_mhz > 280 {
                // Rapid fling gesture, active high-refresh scrolling, or moderate compute (> 280 MHz effective): Engage Fluid
                DynamicGpuState::Fluid
            } else if is_touch || effective_mhz > 160 {
                // Single tap, interactive touch, or light rendering (> 160 MHz effective): Engage Light
                DynamicGpuState::Light
            } else {
                // Screen stationary, reading, idle background composition: Drop to Idle (300 MHz)
                DynamicGpuState::Idle
            }
        };

        // Step 2: Proactive Thermal Guard (Caps Peak to Fluid at >= 47.5°C to prevent hard OEM throttle)
        if temp_c >= 47.5 && active_profile != "extreme" && target_state == DynamicGpuState::Peak {
            target_state = DynamicGpuState::Fluid;
        }

        let current_u8 = CURRENT_SCALER_STATE.load(Ordering::Relaxed);
        let current_state = match current_u8 {
            1 => DynamicGpuState::Light,
            2 => DynamicGpuState::Fluid,
            3 => DynamicGpuState::Peak,
            _ => DynamicGpuState::Idle,
        };

        // Step 3: Multi-Tier Staircase Hysteresis (Instant UP, Gradual Downclocking)
        let resolved_state = if target_state > current_state {
            // RAMP UP: ZERO LATENCY (0 ms)
            if target_state == DynamicGpuState::Peak {
                LAST_PEAK_MS.store(now_ms, Ordering::Relaxed);
                LAST_FLUID_MS.store(now_ms, Ordering::Relaxed);
                LAST_LIGHT_MS.store(now_ms, Ordering::Relaxed);
            } else if target_state == DynamicGpuState::Fluid {
                LAST_FLUID_MS.store(now_ms, Ordering::Relaxed);
                LAST_LIGHT_MS.store(now_ms, Ordering::Relaxed);
            } else if target_state == DynamicGpuState::Light {
                LAST_LIGHT_MS.store(now_ms, Ordering::Relaxed);
            }
            target_state
        } else if target_state < current_state {
            // STEP DOWN: MULTI-TIER ANTI-FLAPPING HYSTERESIS
            match current_state {
                DynamicGpuState::Peak => {
                    let last_peak = LAST_PEAK_MS.load(Ordering::Relaxed);
                    if now_ms.saturating_sub(last_peak) < 500 {
                        // Hold at Peak during 500ms cooldown window
                        DynamicGpuState::Peak
                    } else {
                        // Step down to Fluid first
                        LAST_FLUID_MS.store(now_ms, Ordering::Relaxed);
                        DynamicGpuState::Fluid
                    }
                }
                DynamicGpuState::Fluid => {
                    let last_fluid = LAST_FLUID_MS.load(Ordering::Relaxed);
                    let is_rapid = crate::daemon::TouchDetector::is_rapid_touch(150, 3);
                    if now_ms.saturating_sub(last_fluid) < 500 || is_rapid {
                        // Hold at Fluid during 500ms scrolling window
                        DynamicGpuState::Fluid
                    } else {
                        // Step down to Light
                        LAST_LIGHT_MS.store(now_ms, Ordering::Relaxed);
                        DynamicGpuState::Light
                    }
                }
                DynamicGpuState::Light => {
                    let last_light = LAST_LIGHT_MS.load(Ordering::Relaxed);
                    let is_touch = crate::daemon::TouchDetector::is_touch_recent(350);
                    if now_ms.saturating_sub(last_light) < 400 || is_touch {
                        // Hold at Light during 400ms interaction / ripple window
                        DynamicGpuState::Light
                    } else {
                        // Cleanly decay down to Idle (300 MHz)
                        DynamicGpuState::Idle
                    }
                }
                DynamicGpuState::Idle => DynamicGpuState::Idle,
            }
        } else {
            // Maintain current state, refresh active timestamps
            match current_state {
                DynamicGpuState::Peak => {
                    LAST_PEAK_MS.store(now_ms, Ordering::Relaxed);
                    LAST_FLUID_MS.store(now_ms, Ordering::Relaxed);
                    LAST_LIGHT_MS.store(now_ms, Ordering::Relaxed);
                }
                DynamicGpuState::Fluid => {
                    LAST_FLUID_MS.store(now_ms, Ordering::Relaxed);
                    LAST_LIGHT_MS.store(now_ms, Ordering::Relaxed);
                }
                DynamicGpuState::Light => {
                    LAST_LIGHT_MS.store(now_ms, Ordering::Relaxed);
                }
                DynamicGpuState::Idle => {}
            }
            current_state
        };

        let new_u8 = resolved_state as u8;
        if current_u8 != new_u8 {
            CURRENT_SCALER_STATE.store(new_u8, Ordering::Relaxed);
            Self::apply_state_hardware(&gpu, resolved_state);
        }
    }

    /// Universally configures the underlying hardware registers & sysfs nodes for any SoC.
    fn apply_state_hardware(gpu: &GpuCapability, state: DynamicGpuState) {
        let idle_opp = gpu.idle_opp();
        let light_opp = gpu.light_opp();
        let fluid_opp = gpu.fluid_opp();
        let peak_opp = gpu.peak_opp();

        match gpu.driver {
            // ── 1. MEDIATEK GED & GPUFREQ ──────────────────────────────────────────
            GpuDriver::MtkGed | GpuDriver::MtkGpufreq => {
                match state {
                    DynamicGpuState::Idle => {
                        if let Some(low) = idle_opp {
                            Self::write_opt("/sys/kernel/ged/hal/custom_boost_gpu_freq", low.index.to_string());
                            Self::write_opt("/sys/module/ged/parameters/gpu_cust_boost_freq", low.freq_khz.to_string());
                            Self::write_opt("/sys/module/ged/parameters/gpu_bottom_freq", low.freq_khz.to_string());
                        }
                        Self::write_opt("/sys/kernel/ged/hal/gpu_boost_level", "0");
                        Self::write_opt("/sys/kernel/ged/hal/dvfs_margin_value", "50");
                        Self::write_opt("/sys/kernel/ged/hal/loading_base_dvfs_step", "4");
                        Self::write_opt("/sys/kernel/ged/hal/dvfs_loading_mode", "0");
                        Self::write_opt("/sys/module/ged/parameters/boost_amp", "0");
                        Self::write_opt("/sys/module/ged/parameters/ged_boost_enable", "0");
                        Self::write_opt("/proc/gpufreq/gpufreq_opp_freq", "0");
                        Self::write_opt("/proc/gpufreqv2/gpufreq_opp_freq", "0");
                    }
                    DynamicGpuState::Light => {
                        if let Some(light) = light_opp {
                            Self::write_opt("/sys/kernel/ged/hal/custom_boost_gpu_freq", light.index.to_string());
                            Self::write_opt("/sys/module/ged/parameters/gpu_cust_boost_freq", light.freq_khz.to_string());
                            Self::write_opt("/sys/module/ged/parameters/gpu_bottom_freq", light.freq_khz.to_string());
                        }
                        Self::write_opt("/sys/kernel/ged/hal/gpu_boost_level", "20");
                        Self::write_opt("/sys/kernel/ged/hal/dvfs_margin_value", "55");
                        Self::write_opt("/sys/kernel/ged/hal/loading_base_dvfs_step", "5");
                        Self::write_opt("/sys/kernel/ged/hal/dvfs_loading_mode", "1");
                        Self::write_opt("/sys/module/ged/parameters/boost_amp", "1");
                        Self::write_opt("/sys/module/ged/parameters/ged_boost_enable", "1");
                    }
                    DynamicGpuState::Fluid => {
                        if let Some(fluid) = fluid_opp {
                            Self::write_opt("/sys/kernel/ged/hal/custom_boost_gpu_freq", fluid.index.to_string());
                            Self::write_opt("/sys/module/ged/parameters/gpu_cust_boost_freq", fluid.freq_khz.to_string());
                            Self::write_opt("/sys/module/ged/parameters/gpu_bottom_freq", fluid.freq_khz.to_string());
                        }
                        Self::write_opt("/sys/kernel/ged/hal/gpu_boost_level", "40");
                        Self::write_opt("/sys/kernel/ged/hal/dvfs_margin_value", "70");
                        Self::write_opt("/sys/kernel/ged/hal/loading_base_dvfs_step", "6");
                        Self::write_opt("/sys/kernel/ged/hal/dvfs_loading_mode", "1");
                        Self::write_opt("/sys/module/ged/parameters/boost_amp", "1");
                        Self::write_opt("/sys/module/ged/parameters/ged_boost_enable", "1");
                    }
                    DynamicGpuState::Peak => {
                        if let Some(high) = peak_opp {
                            Self::write_opt("/sys/kernel/ged/hal/custom_boost_gpu_freq", high.index.to_string());
                            Self::write_opt("/sys/module/ged/parameters/gpu_cust_boost_freq", high.freq_khz.to_string());
                            Self::write_opt("/sys/module/ged/parameters/gpu_bottom_freq", high.freq_khz.to_string());
                        }
                        Self::write_opt("/sys/kernel/ged/hal/gpu_boost_level", "80");
                        Self::write_opt("/sys/kernel/ged/hal/dvfs_margin_value", "100");
                        Self::write_opt("/sys/kernel/ged/hal/loading_base_dvfs_step", "8");
                        Self::write_opt("/sys/kernel/ged/hal/dvfs_loading_mode", "1");
                        Self::write_opt("/sys/module/ged/parameters/boost_amp", "2");
                        Self::write_opt("/sys/module/ged/parameters/ged_boost_enable", "1");
                    }
                }
            }

            // ── 2. QUALCOMM SNAPDRAGON ADRENO (KGSL & DEVFREQ) ───────────────────
            GpuDriver::QcomKgsl => {
                let unit_mult: u64 = if let Ok(avail_str) = SysfsReader::read_trimmed("/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies")
                    .or_else(|_| SysfsReader::read_trimmed("/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies")) {
                    if avail_str.split_whitespace().any(|tok| tok.parse::<u64>().unwrap_or(0) > 10_000_000) {
                        1_000_000
                    } else if avail_str.split_whitespace().any(|tok| tok.parse::<u64>().unwrap_or(0) > 10_000) {
                        1_000
                    } else {
                        1
                    }
                } else {
                    1_000_000
                };

                let num_pwr = gpu.num_pwrlevels.max(1);
                let lowest_pwr_idx = num_pwr.saturating_sub(1);
                let light_pwr_idx = (lowest_pwr_idx * 3) / 4;
                let fluid_pwr_idx = lowest_pwr_idx / 2;

                match state {
                    DynamicGpuState::Idle => {
                        if let Some(low) = idle_opp {
                            Self::write_opt("/sys/class/kgsl/kgsl-3d0/min_clock_mhz", low.freq_mhz.to_string());
                            Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq", (low.freq_khz * unit_mult / 1000).to_string());
                        }
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", lowest_pwr_idx.to_string());
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/default_pwrlevel", lowest_pwr_idx.to_string());
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/max_pwrlevel", "0");
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/pwrscale", "1");
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost", "0");
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/adrenoboost", "0");
                    }
                    DynamicGpuState::Light => {
                        if let Some(light) = light_opp {
                            Self::write_opt("/sys/class/kgsl/kgsl-3d0/min_clock_mhz", light.freq_mhz.to_string());
                            Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq", (light.freq_khz * unit_mult / 1000).to_string());
                        }
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", light_pwr_idx.to_string());
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/default_pwrlevel", light_pwr_idx.to_string());
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/max_pwrlevel", "0");
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/pwrscale", "1");
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost", "1");
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/adrenoboost", "1");
                    }
                    DynamicGpuState::Fluid => {
                        if let Some(fluid) = fluid_opp {
                            Self::write_opt("/sys/class/kgsl/kgsl-3d0/min_clock_mhz", fluid.freq_mhz.to_string());
                            Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq", (fluid.freq_khz * unit_mult / 1000).to_string());
                        }
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", fluid_pwr_idx.to_string());
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/default_pwrlevel", fluid_pwr_idx.to_string());
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/max_pwrlevel", "0");
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/pwrscale", "1");
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost", "2");
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/adrenoboost", "2");
                    }
                    DynamicGpuState::Peak => {
                        if let Some(high) = peak_opp {
                            Self::write_opt("/sys/class/kgsl/kgsl-3d0/min_clock_mhz", high.freq_mhz.to_string());
                            Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq", (high.freq_khz * unit_mult / 1000).to_string());
                        }
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/min_pwrlevel", "0");
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/default_pwrlevel", "0");
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/max_pwrlevel", "0");
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/pwrscale", "1");
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost", "3");
                        Self::write_opt("/sys/class/kgsl/kgsl-3d0/adrenoboost", "3");
                    }
                }

                // Sync all /sys/class/devfreq/*kgsl* instances
                if let Ok(entries) = std::fs::read_dir("/sys/class/devfreq") {
                    for entry in entries.flatten() {
                        let path = entry.path();
                        if path.to_string_lossy().contains("kgsl") {
                            let target_f = match state {
                                DynamicGpuState::Idle => idle_opp.map(|e| e.freq_khz * unit_mult / 1000).unwrap_or(0),
                                DynamicGpuState::Light => light_opp.map(|e| e.freq_khz * unit_mult / 1000).unwrap_or(0),
                                DynamicGpuState::Fluid => fluid_opp.map(|e| e.freq_khz * unit_mult / 1000).unwrap_or(0),
                                DynamicGpuState::Peak => peak_opp.map(|e| e.freq_khz * unit_mult / 1000).unwrap_or(0),
                            };
                            Self::write_opt(path.join("min_freq"), target_f.to_string());
                        }
                    }
                }
            }

            // ── 3. SAMSUNG EXYNOS, GOOGLE TENSOR & GENERIC DEVFREQ ────────────────
            GpuDriver::GenericDevfreq => {
                let unit_mult: u64 = if let Some(ref devpath) = gpu.devfreq_path {
                    if let Ok(avail_str) = SysfsReader::read_trimmed(devpath.join("available_frequencies")) {
                        if avail_str.split_whitespace().any(|tok| tok.parse::<u64>().unwrap_or(0) > 10_000_000) {
                            1_000_000
                        } else if avail_str.split_whitespace().any(|tok| tok.parse::<u64>().unwrap_or(0) > 10_000) {
                            1_000
                        } else {
                            1
                        }
                    } else {
                        1_000_000
                    }
                } else {
                    1_000_000
                };

                let target_opp = match state {
                    DynamicGpuState::Idle => idle_opp,
                    DynamicGpuState::Light => light_opp,
                    DynamicGpuState::Fluid => fluid_opp,
                    DynamicGpuState::Peak => peak_opp,
                };

                if let Some(opp) = target_opp {
                    if let Some(ref devpath) = gpu.devfreq_path {
                        let target_f = opp.freq_khz * unit_mult / 1000;
                        Self::write_opt(devpath.join("min_freq"), target_f.to_string());
                    }
                    if Path::new("/sys/kernel/gpu/gpu_min_clock").exists() {
                        Self::write_opt("/sys/kernel/gpu/gpu_min_clock", opp.freq_khz.to_string());
                    }
                    // Sync multi-devfreq nodes for Exynos / Tensor
                    if let Ok(entries) = std::fs::read_dir("/sys/class/devfreq") {
                        for entry in entries.flatten() {
                            let name = entry.file_name().to_string_lossy().to_lowercase();
                            if name.contains("gpu") || name.contains("mali") || name.contains("sgpu") {
                                let target_f = opp.freq_khz * unit_mult / 1000;
                                Self::write_opt(entry.path().join("min_freq"), target_f.to_string());
                            }
                        }
                    }
                }
            }

            GpuDriver::Unsupported => {}
        }
    }
}
