use std::path::{Path, PathBuf};

use crate::hardware::capability::{BlockDeviceType, GpuDriver, HardwareCapability};
use crate::hardware::detector::Vendor;
use crate::hardware::MtkHardware;
use crate::profile::profile::Profile;
use crate::sysfs::SysfsReader;

#[derive(Debug, Clone)]
pub struct PlannedOperation {
    pub subsystem: &'static str,
    pub description: String,
    pub path: PathBuf,
    pub current_value: String,
    pub target_value: String,
    pub critical: bool,
}

pub struct HardwareTranslator;

impl HardwareTranslator {
    /// Helper to add a planned operation only if the node exists, is writable (or root-unlockable),
    /// and its current value differs from target_value.
    fn push_op(
        ops: &mut Vec<PlannedOperation>,
        subsystem: &'static str,
        description: impl Into<String>,
        path: impl Into<PathBuf>,
        target_value: impl Into<String>,
        critical: bool,
    ) {
        let p: PathBuf = path.into();
        if !p.exists() {
            return;
        }
        let target: String = target_value.into();
        let cur = SysfsReader::read_trimmed(&p).unwrap_or_default();

        // Handle bracketed active choice strings like "mq-deadline [kyber] none"
        if cur.contains(&format!("[{}]", target)) || cur == target {
            return;
        }

        ops.push(PlannedOperation {
            subsystem,
            description: description.into(),
            path: p,
            current_value: cur,
            target_value: target,
            critical,
        });
    }

    /// Translates declarative profile intent into concrete sysfs planned operations across
    /// all 7 hardware subsystems based on the device's probed capabilities (Zero-Hardcode).
    pub fn translate(profile: &Profile, cap: &HardwareCapability) -> Vec<PlannedOperation> {
        let mut ops = Vec::new();
        let is_perf_or_ext = matches!(profile.name, "extreme" | "performance");
        let is_extreme = profile.name == "extreme";
        let is_powersave = profile.name == "powersave";

        // ── 1. CPU Policies, Governor Sub-Tunables & PPM Sync ───────────────
        for pol in &cap.cpu.policies {
            // A. Dynamic Governor Resolution
            let gov_str = profile.cpu.governor.as_str();
            let target_gov = if is_extreme && pol.available_governors.iter().any(|g| g == "performance") {
                "performance"
            } else if pol.available_governors.iter().any(|g| g == gov_str) {
                gov_str
            } else {
                // Dynamic priority chain: walt -> sugov_ext -> schedutil -> interactive -> first available
                let mut picked = gov_str;
                for candidate in &["walt", "sugov_ext", "schedutil", "energy_step", "interactive", "ondemand"] {
                    if pol.available_governors.iter().any(|g| g == candidate) {
                        picked = candidate;
                        break;
                    }
                }
                if !pol.available_governors.iter().any(|g| g == picked) {
                    if let Some(first) = pol.available_governors.first() {
                        picked = first.as_str();
                    }
                }
                picked
            };

            // B. Min & Max Frequency Percentage Snapping into Real OPP Table
            let raw_min_hz = Self::snap_frequency(
                pol.min_freq,
                pol.max_freq,
                profile.cpu.min_percent,
                &pol.available_freqs,
                true,
            );
            let target_max_hz = Self::snap_frequency(
                pol.min_freq,
                pol.max_freq,
                profile.cpu.max_percent,
                &pol.available_freqs,
                false,
            );
            // Note: Linux cpufreq 'performance' governor locks min_freq == max_freq on many kernels
            let target_min_hz = if target_gov == "performance" {
                raw_min_hz.min(target_max_hz)
            } else {
                raw_min_hz
            };

            // If leaving 'performance' governor, switch governor first so scaling_min_freq is unlocked
            if target_gov != "performance" {
                Self::push_op(
                    &mut ops,
                    "CPU",
                    format!("Policy {} ({}) governor", pol.index, pol.role),
                    pol.path.join("scaling_governor"),
                    target_gov,
                    true,
                );
            }

            // Order matters: raise max before raising min, or lower min before lowering max.
            // Marked non-critical so PPM/Thermal/Governor clamping never aborts the entire profile.
            if target_max_hz >= pol.current_max_freq {
                Self::push_op(
                    &mut ops,
                    "CPU",
                    format!("Policy {} ceiling clock ({}%)", pol.index, profile.cpu.max_percent),
                    pol.path.join("scaling_max_freq"),
                    target_max_hz.to_string(),
                    false,
                );
                Self::push_op(
                    &mut ops,
                    "CPU",
                    format!("Policy {} floor clock ({}%)", pol.index, profile.cpu.min_percent),
                    pol.path.join("scaling_min_freq"),
                    target_min_hz.to_string(),
                    false,
                );
            } else {
                Self::push_op(
                    &mut ops,
                    "CPU",
                    format!("Policy {} floor clock ({}%)", pol.index, profile.cpu.min_percent),
                    pol.path.join("scaling_min_freq"),
                    target_min_hz.to_string(),
                    false,
                );
                Self::push_op(
                    &mut ops,
                    "CPU",
                    format!("Policy {} ceiling clock ({}%)", pol.index, profile.cpu.max_percent),
                    pol.path.join("scaling_max_freq"),
                    target_max_hz.to_string(),
                    false,
                );
            }

            // If entering 'performance' governor, apply governor after setting frequency bounds
            if target_gov == "performance" {
                Self::push_op(
                    &mut ops,
                    "CPU",
                    format!("Policy {} ({}) governor", pol.index, pol.role),
                    pol.path.join("scaling_governor"),
                    target_gov,
                    true,
                );
            }

            // C. Governor Sub-Directory Rate Limits (schedutil / walt / sugov_ext)
            let (up_rate, down_rate) = match profile.name {
                "extreme" => ("0", "20000"),
                "performance" => ("0", "10000"),
                "powersave" => ("4000", "1000"),
                _ => ("1000", "4000"),
            };

            for gdir in &pol.gov_tunable_dirs {
                Self::push_op(
                    &mut ops,
                    "CPU",
                    format!("Policy {} governor up_rate_limit_us", pol.index),
                    gdir.join("up_rate_limit_us"),
                    up_rate,
                    false,
                );
                Self::push_op(
                    &mut ops,
                    "CPU",
                    format!("Policy {} governor down_rate_limit_us", pol.index),
                    gdir.join("down_rate_limit_us"),
                    down_rate,
                    false,
                );
            }

            // D. MediaTek PPM Per-Cluster Frequency Sync
            if cap.vendor == Vendor::MediaTek {
                for ppm_max in &[
                    "/proc/ppm/policy/hard_userlimit_max_cpu_freq",
                    "/proc/ppm/policy/userlimit_max_cpu_freq",
                ] {
                    if Path::new(ppm_max).exists() {
                        ops.push(PlannedOperation {
                            subsystem: "MTK-PPM",
                            description: format!("Cluster {} PPM max limit", pol.mtk_cluster_index),
                            path: PathBuf::from(ppm_max),
                            current_value: "dynamic".to_string(),
                            target_value: format!("{} {}", pol.mtk_cluster_index, target_max_hz),
                            critical: false,
                        });
                    }
                }
                for ppm_min in &[
                    "/proc/ppm/policy/hard_userlimit_min_cpu_freq",
                    "/proc/ppm/policy/userlimit_min_cpu_freq",
                ] {
                    if Path::new(ppm_min).exists() {
                        ops.push(PlannedOperation {
                            subsystem: "MTK-PPM",
                            description: format!("Cluster {} PPM min limit", pol.mtk_cluster_index),
                            path: PathBuf::from(ppm_min),
                            current_value: "dynamic".to_string(),
                            target_value: format!("{} {}", pol.mtk_cluster_index, target_min_hz),
                            critical: false,
                        });
                    }
                }
            }
        }

        // ── 2. Vendor & GPU OPP Acceleration ────────────────────────────────
        match cap.vendor {
            Vendor::MediaTek => {
                let target_mode = match profile.name {
                    "extreme" | "performance" => "3",
                    "balanced" | "balance" => "1",
                    _ => "0",
                };
                Self::push_op(
                    &mut ops,
                    "MTK",
                    "MediaTek cpufreq power mode",
                    MtkHardware::CPUFREQ_POWER_MODE,
                    target_mode,
                    false,
                );

                let target_cci = if is_perf_or_ext { "1" } else { "0" };
                Self::push_op(
                    &mut ops,
                    "MTK",
                    "MediaTek CCI interconnect boost",
                    MtkHardware::CPUFREQ_CCI_MODE,
                    target_cci,
                    false,
                );

                // Dynamic MediaTek GPU OPP & GED Translation
                if !cap.gpu.opp_table.is_empty() {
                    let lowest_opp = cap.gpu.opp_table.last().unwrap();
                    let highest_opp = cap.gpu.opp_table.first().unwrap();

                    let (target_min_opp, target_max_opp) = match profile.name {
                        "extreme" => (highest_opp, highest_opp),
                        "performance" => {
                            let mid_idx = cap.gpu.opp_table.len() / 3;
                            let floor_opp = cap.gpu.opp_table.get(mid_idx).unwrap_or(lowest_opp);
                            (floor_opp, highest_opp)
                        }
                        "powersave" => {
                            let cap_idx = cap.gpu.opp_table.len() / 3;
                            let ceil_opp = cap.gpu.opp_table.get(cap_idx).unwrap_or(highest_opp);
                            (lowest_opp, ceil_opp)
                        }
                        _ => (lowest_opp, highest_opp),
                    };

                    Self::push_op(
                        &mut ops,
                        "GPU",
                        format!("MediaTek GED bottom floor ({} MHz)", target_min_opp.freq_mhz),
                        "/sys/module/ged/parameters/gpu_bottom_freq",
                        target_min_opp.freq_khz.to_string(),
                        false,
                    );
                    Self::push_op(
                        &mut ops,
                        "GPU",
                        format!("MediaTek GED boost floor ({} MHz)", target_min_opp.freq_mhz),
                        "/sys/module/ged/parameters/gpu_cust_boost_freq",
                        target_min_opp.freq_khz.to_string(),
                        false,
                    );
                    Self::push_op(
                        &mut ops,
                        "GPU",
                        format!("MediaTek GED upbound ceiling ({} MHz)", target_max_opp.freq_mhz),
                        "/sys/module/ged/parameters/gpu_cust_upbound_freq",
                        target_max_opp.freq_khz.to_string(),
                        false,
                    );
                    Self::push_op(
                        &mut ops,
                        "GPU",
                        format!("MediaTek GED HAL boost OPP index ({})", target_min_opp.index),
                        "/sys/kernel/ged/hal/custom_boost_gpu_freq",
                        target_min_opp.index.to_string(),
                        false,
                    );
                    Self::push_op(
                        &mut ops,
                        "GPU",
                        format!("MediaTek GED HAL upbound OPP index ({})", target_max_opp.index),
                        "/sys/kernel/ged/hal/custom_upbound_gpu_freq",
                        target_max_opp.index.to_string(),
                        false,
                    );
                }

                let ged_flag = if is_perf_or_ext { "1" } else { "0" };
                for (node, desc) in &[
                    ("/sys/module/ged/parameters/ged_smart_boost", "GED smart boost"),
                    ("/sys/module/ged/parameters/boost_gpu_enable", "GED boost GPU enable"),
                    ("/sys/module/ged/parameters/enable_gpu_boost", "GED GPU boost"),
                    ("/sys/module/ged/parameters/gx_game_mode", "GED GX game mode"),
                    ("/sys/module/ged/parameters/gx_boost_on", "GED GX boost"),
                    ("/sys/kernel/fpsgo/common/fpsgo_enable", "MediaTek FPSGO frame pacer"),
                ] {
                    Self::push_op(&mut ops, "GPU", *desc, *node, ged_flag, false);
                }
            }
            Vendor::Qualcomm => {
                let target_b = if profile.cpu.boost { "1" } else { "0" };
                Self::push_op(
                    &mut ops,
                    "Qualcomm",
                    "Qualcomm sched boost on input",
                    "/sys/module/cpu_boost/parameters/sched_boost_on_input",
                    target_b,
                    false,
                );

                if cap.gpu.driver == GpuDriver::QcomKgsl {
                    let max_idx = cap.gpu.num_pwrlevels.saturating_sub(1);
                    let target_max_pwr = match profile.name {
                        "extreme" => 0,
                        "performance" => (max_idx / 2).max(0),
                        _ => max_idx,
                    };
                    let target_min_pwr = if is_powersave && max_idx > 0 { 1 } else { 0 };
                    let target_adrenoboost = match profile.name {
                        "extreme" => "3",
                        "performance" => "2",
                        _ => "0",
                    };
                    let target_throttling = if is_perf_or_ext { "0" } else { "1" };

                    Self::push_op(
                        &mut ops,
                        "GPU",
                        "Adreno KGSL max_pwrlevel (lowest clock bound)",
                        "/sys/class/kgsl/kgsl-3d0/max_pwrlevel",
                        target_max_pwr.to_string(),
                        false,
                    );
                    Self::push_op(
                        &mut ops,
                        "GPU",
                        "Adreno KGSL min_pwrlevel (highest clock bound)",
                        "/sys/class/kgsl/kgsl-3d0/min_pwrlevel",
                        target_min_pwr.to_string(),
                        false,
                    );
                    Self::push_op(
                        &mut ops,
                        "GPU",
                        "Adreno KGSL thermal throttling",
                        "/sys/class/kgsl/kgsl-3d0/throttling",
                        target_throttling,
                        false,
                    );
                    Self::push_op(
                        &mut ops,
                        "GPU",
                        "Adreno devfreq boost level",
                        "/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost",
                        target_adrenoboost,
                        false,
                    );
                }
            }
            Vendor::Generic => {}
        }

        // Generic Devfreq GPU Fallback
        if cap.gpu.driver == GpuDriver::GenericDevfreq {
            if let (Some(ref devpath), Some(&min_mhz), Some(&max_mhz)) = (
                &cap.gpu.devfreq_path,
                cap.gpu.available_freqs_mhz.first(),
                cap.gpu.available_freqs_mhz.last(),
            ) {
                let target_min_mhz = match profile.name {
                    "extreme" => max_mhz,
                    "performance" => {
                        let mid = cap.gpu.available_freqs_mhz.len() / 2;
                        cap.gpu.available_freqs_mhz.get(mid).copied().unwrap_or(min_mhz)
                    }
                    _ => min_mhz,
                };
                Self::push_op(
                    &mut ops,
                    "GPU",
                    "Devfreq GPU min_freq",
                    devpath.join("min_freq"),
                    (target_min_mhz * 1_000_000).to_string(),
                    false,
                );
                Self::push_op(
                    &mut ops,
                    "GPU",
                    "Devfreq GPU max_freq",
                    devpath.join("max_freq"),
                    (max_mhz * 1_000_000).to_string(),
                    false,
                );
            }
        }

        // ── 3. Scheduler, EAS & UClamp Translation ──────────────────────────
        if let Some(ref uclamp_min) = cap.scheduler.uclamp_min_node {
            let target_uclamp_val = ((profile.scheduler.uclamp_min_percent as u32 * 1024) / 100).to_string();
            Self::push_op(
                &mut ops,
                "Scheduler",
                format!("EAS sched_util_clamp_min ({}%)", profile.scheduler.uclamp_min_percent),
                uclamp_min.clone(),
                target_uclamp_val,
                false,
            );
        }

        if let Some(ref top_uclamp) = cap.scheduler.top_app_uclamp_min {
            let scaled = if cap.scheduler.uclamp_max_scale > 100 {
                (profile.scheduler.uclamp_min_percent as u32 * 1024) / 100
            } else {
                profile.scheduler.uclamp_min_percent as u32
            };
            Self::push_op(
                &mut ops,
                "Scheduler",
                format!("Top-App cpuset uclamp.min ({}%)", profile.scheduler.uclamp_min_percent),
                top_uclamp.clone(),
                scaled.to_string(),
                false,
            );
        }

        let (mig_cost, timer_mig) = if is_perf_or_ext {
            ("50000", "0")
        } else {
            ("500000", "1")
        };
        Self::push_op(
            &mut ops,
            "Scheduler",
            "Kernel sched_migration_cost_ns",
            "/proc/sys/kernel/sched_migration_cost_ns",
            mig_cost,
            false,
        );
        Self::push_op(
            &mut ops,
            "Scheduler",
            "Kernel timer_migration",
            "/proc/sys/kernel/timer_migration",
            timer_mig,
            false,
        );
        Self::push_op(
            &mut ops,
            "Scheduler",
            "Disable scheduler statistics overhead",
            "/proc/sys/kernel/sched_schedstats",
            "0",
            false,
        );

        // ── 4. Memory, VM & MGLRU (Proportional to Physical RAM) ────────────
        if cap.memory.mglru_supported {
            Self::push_op(
                &mut ops,
                "Memory",
                "Linux Multi-Gen LRU (MGLRU)",
                "/sys/kernel/mm/lru_gen/enabled",
                "y",
                false,
            );
        }

        // Dynamic RAM-proportional VM parameters
        let ram_mb = cap.memory.total_ram_mb;
        let (swappiness, dirty_ratio, dirty_bg_ratio, vfs_pressure) = if is_perf_or_ext {
            if ram_mb <= 4500 {
                ("100", "15", "5", "80")
            } else if ram_mb <= 6500 {
                ("85", "20", "8", "70")
            } else {
                ("60", "25", "10", "50")
            }
        } else if is_powersave {
            ("100", "15", "5", "100")
        } else if ram_mb <= 4500 {
            ("90", "20", "8", "100")
        } else {
            ("75", "20", "10", "85")
        };

        Self::push_op(&mut ops, "Memory", "VM swappiness (RAM-proportional)", "/proc/sys/vm/swappiness", swappiness, false);
        Self::push_op(&mut ops, "Memory", "VM dirty_ratio", "/proc/sys/vm/dirty_ratio", dirty_ratio, false);
        Self::push_op(&mut ops, "Memory", "VM dirty_background_ratio", "/proc/sys/vm/dirty_background_ratio", dirty_bg_ratio, false);
        Self::push_op(&mut ops, "Memory", "VM vfs_cache_pressure", "/proc/sys/vm/vfs_cache_pressure", vfs_pressure, false);

        // ── 5. Storage Block I/O Queues ─────────────────────────────────────
        for dev in &cap.storage.devices {
            if dev.device_type == BlockDeviceType::Virtual {
                continue;
            }
            let pref_order: &[&str] = match dev.device_type {
                BlockDeviceType::Ufs | BlockDeviceType::Nvme | BlockDeviceType::Loop => {
                    &["none", "mq-deadline", "kyber", "bfq", "noop"]
                }
                BlockDeviceType::Emmc => &["mq-deadline", "bfq", "kyber", "none", "noop"],
                BlockDeviceType::Virtual => &[],
            };

            for &cand in pref_order {
                if dev.available_schedulers.iter().any(|s| s == cand) {
                    Self::push_op(
                        &mut ops,
                        "Storage",
                        format!("Block {} ({}) I/O scheduler", dev.name, dev.device_type.as_str()),
                        dev.queue_path.join("scheduler"),
                        cand,
                        false,
                    );
                    break;
                }
            }

            let read_ahead = match (dev.device_type, is_perf_or_ext) {
                (BlockDeviceType::Emmc, true) => "512",
                (BlockDeviceType::Emmc, false) => "256",
                _ => "128",
            };
            Self::push_op(
                &mut ops,
                "Storage",
                format!("Block {} read_ahead_kb", dev.name),
                dev.queue_path.join("read_ahead_kb"),
                read_ahead,
                false,
            );
            Self::push_op(
                &mut ops,
                "Storage",
                format!("Block {} disable iostats overhead", dev.name),
                dev.queue_path.join("iostats"),
                "0",
                false,
            );
            Self::push_op(
                &mut ops,
                "Storage",
                format!("Block {} disable entropy add_random", dev.name),
                dev.queue_path.join("add_random"),
                "0",
                false,
            );
        }

        // ── 6. Network & Low-Latency TCP Stack ──────────────────────────────
        if !cap.network.best_tcp_algo.is_empty() {
            Self::push_op(
                &mut ops,
                "Network",
                "Dynamic TCP congestion control",
                "/proc/sys/net/ipv4/tcp_congestion_control",
                cap.network.best_tcp_algo.clone(),
                false,
            );
        }
        let tcp_low_lat = if is_perf_or_ext { "1" } else { "0" };
        Self::push_op(
            &mut ops,
            "Network",
            "TCP low latency socket mode",
            "/proc/sys/net/ipv4/tcp_low_latency",
            tcp_low_lat,
            false,
        );
        Self::push_op(
            &mut ops,
            "Network",
            "TCP FastOpen (client + server)",
            "/proc/sys/net/ipv4/tcp_fastopen",
            "3",
            false,
        );

        // ── 7. Display & Touch Game Mode ────────────────────────────────────
        let touch_val = if is_perf_or_ext { "1" } else { "0" };
        for tnode in &[
            "/sys/class/touch/touch_dev/touch_game_mode",
            "/sys/devices/virtual/touch/touch_dev/bump_sample_rate",
            "/proc/touchscreen/game_mode",
            "/sys/devices/platform/tp_wake_switch/game_mode",
        ] {
            Self::push_op(
                &mut ops,
                "Display",
                "Touchscreen high-sampling game mode",
                *tnode,
                touch_val,
                false,
            );
        }

        ops
    }

    /// Snaps a percentage target into real hardware frequency tables.
    pub fn snap_frequency(min: u64, max: u64, percent: u8, available: &[u64], snap_up: bool) -> u64 {
        if percent == 0 {
            return min;
        }
        if percent >= 100 {
            return max;
        }

        let calculated = min + ((max - min) * percent as u64) / 100;

        if available.is_empty() {
            return calculated;
        }

        if snap_up {
            for &f in available {
                if f >= calculated {
                    return f;
                }
            }
            max
        } else {
            let mut best = min;
            for &f in available {
                if f <= calculated {
                    best = f;
                } else {
                    break;
                }
            }
            best
        }
    }
}
