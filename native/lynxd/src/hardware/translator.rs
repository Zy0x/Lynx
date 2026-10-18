use std::path::{Path, PathBuf};

use crate::hardware::capability::{GpuDriver, HardwareCapability};
use crate::hardware::storage::StoragePolicy;
use crate::hardware::detector::Vendor;
use crate::hardware::{MtkHardware, QcomHardware};
use crate::profile::profile::Profile;
use crate::state::ConfigStore;
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

        // Handle MediaTek fixed OPP status node explicitly
        if p.ends_with("gpufreq_opp_freq") {
            if target == "0" && cur.contains("fixed OPP is disabled") {
                return;
            }
            if target != "0" && cur.contains(&format!("freq = {}", target)) && cur.contains("fixed OPP is enabled") {
                return;
            }
        } else {
            // Handle bracketed active choice strings like "mq-deadline [kyber] none"
            let is_choice_node = p.ends_with("scheduler") || p.ends_with("scaling_governor") || p.ends_with("current_governor");
            if (is_choice_node && cur.contains(&format!("[{}]", target))) || cur == target {
                return;
            }
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
        let anti_heat_enabled = ConfigStore::get("cpu.anti_heat_mode")
            .map(|s| s != "false" && s != "0")
            .unwrap_or(true);

        // ── 1. CPU Policies, Governor Sub-Tunables & PPM Sync ───────────────
        for pol in &cap.cpu.policies {
            let is_little = pol.role.contains("Little")
                || pol.role.contains("Efisiensi")
                || (cap.cpu.policies.len() > 1 && pol.index == 0);

            // A. Dynamic Governor Resolution & Mode Gaming Anti-Panas
            let (target_gov, min_pct, max_pct) = if profile.name == "performance" && is_little && anti_heat_enabled {
                // Mode Gaming Dingin / Anti-Panas (Thermal Shield):
                // Little cores run on schedutil/walt with 80% Fmax ceiling to prevent thermal runaway
                let picked_gov = ["schedutil", "walt", "sugov_ext", "interactive"]
                    .iter()
                    .find(|&&g| pol.available_governors.iter().any(|ag| ag == g))
                    .copied()
                    .unwrap_or("schedutil");
                (picked_gov, 60, 80)
            } else if is_extreme {
                // Mode Extreme: Unrestricted maximum power on all cores!
                let picked_gov = if pol.available_governors.iter().any(|g| g == "performance") {
                    "performance"
                } else {
                    profile.cpu.governor.as_str()
                };
                (picked_gov, profile.cpu.min_percent, 100)
            } else {
                let gov_str = profile.cpu.governor.as_str();
                let picked_gov = if pol.available_governors.iter().any(|g| g == gov_str) {
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
                (picked_gov, profile.cpu.min_percent, profile.cpu.max_percent)
            };

            // B. Min & Max Frequency Percentage Snapping into Real OPP Table
            let raw_min_hz = Self::snap_frequency(
                pol.min_freq,
                pol.max_freq,
                min_pct,
                &pol.available_freqs,
                true,
            );
            let target_max_hz = Self::snap_frequency(
                pol.min_freq,
                pol.max_freq,
                max_pct,
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
                    false,
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
                    false,
                );
            }

            // C. Governor Sub-Directory Rate Limits (schedutil / walt / sugov_ext)
            let (up_rate, down_rate) = match profile.name {
                "extreme" => ("0", "20000"),
                "performance" => ("0", "10000"),
                "powersave" => ("4000", "1000"),
                _ => ("500", "15000"),
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
                    "powersave" => "1",
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

                    // Fixed OPP frequency lock for Extreme mode ONLY!
                    // In Performance/Balance/Powersave, keep OPP fixed at "0" (dynamic DVFS enabled)
                    // so the GPU scales down during idle to preserve battery and keep device cool!
                    let mtk_opp_val = if is_extreme {
                        target_max_opp.freq_khz.to_string()
                    } else {
                        "0".to_string()
                    };
                    Self::push_op(
                        &mut ops,
                        "GPU",
                        "MediaTek GPUFreq Fixed OPP Lock/Release",
                        "/proc/gpufreq/gpufreq_opp_freq",
                        mtk_opp_val,
                        false,
                    );
                }

                let ged_flag = if is_perf_or_ext { "1" } else { "0" };
                for (node, desc) in &[
                    (MtkHardware::GED_GX_GAME_MODE, "GED GX game mode"),
                    (MtkHardware::GED_GX_BOOST_ON, "GED GX boost"),
                    (MtkHardware::GED_GX_FORCE_CPU_BOOST, "GED GX force CPU boost"),
                    ("/sys/module/ged/parameters/ged_smart_boost", "GED smart boost"),
                    ("/sys/module/ged/parameters/boost_gpu_enable", "GED boost GPU enable"),
                    ("/sys/module/ged/parameters/enable_gpu_boost", "GED GPU boost"),
                    (MtkHardware::FPSGO_ENABLE, "MediaTek FPSGO frame pacer"),
                    (MtkHardware::FPSGO_BOOST_TA, "FPSGO top-app boost"),
                    (MtkHardware::FPSGO_ULTRA_RESCUE, "FPSGO emergency ultra rescue"),
                ] {
                    Self::push_op(&mut ops, "MTK-GPU", *desc, *node, ged_flag, false);
                }

                // MediaTek GED GPU Boost Amplitude (0=stock, 2=perf, 3=extreme)
                let boost_amp = if is_extreme { "3" } else if is_perf_or_ext { "2" } else { "0" };
                Self::push_op(&mut ops, "MTK-GPU", "GED boost amplitude", MtkHardware::GED_BOOST_AMP, boost_amp, false);

                // MediaTek GED GPU Boost Level (100 for extreme, 50 for perf, 0 for balance)
                let boost_lvl = if is_extreme { "100" } else if is_perf_or_ext { "50" } else { "0" };
                Self::push_op(&mut ops, "MTK-GPU", "GED GPU boost level", "/sys/kernel/ged/hal/gpu_boost_level", boost_lvl, false);

                let loading_mode = if is_perf_or_ext { "1" } else { "0" };
                Self::push_op(&mut ops, "MTK-GPU", "GED DVFS loading mode", MtkHardware::GED_HAL_DVFS, loading_mode, false);

                // MediaTek CoreLink Interconnect (CCI) & Power Mode
                let power_mode = if is_perf_or_ext { "3" } else if is_powersave { "1" } else { "0" };
                let cci_mode = if is_perf_or_ext { "1" } else { "0" };
                Self::push_op(&mut ops, "MTK-CCI", "MediaTek CPU frequency power mode", MtkHardware::CPUFREQ_POWER_MODE, power_mode, false);
                Self::push_op(&mut ops, "MTK-CCI", "MediaTek CCI interconnect mode", MtkHardware::CPUFREQ_CCI_MODE, cci_mode, false);

                // MediaTek PPM Policy 9 (PPM_POLICY_SYS_BOOST) & Policy 3 (PPM_POLICY_PWR_THRO)
                let ppm_sys_boost = if is_perf_or_ext { "9 1" } else { "9 0" };
                Self::push_op(&mut ops, "MTK-PPM", "MediaTek PPM System Boost Policy", MtkHardware::PPM_POLICY_STATUS, ppm_sys_boost, false);
                if is_extreme {
                    Self::push_op(&mut ops, "MTK-PPM", "MediaTek PPM Power Throttling Disable", MtkHardware::PPM_POLICY_STATUS, "3 0", false);
                } else if is_powersave {
                    Self::push_op(&mut ops, "MTK-PPM", "MediaTek PPM Power Throttling Restore", MtkHardware::PPM_POLICY_STATUS, "3 1", false);
                }

                // MediaTek Perfmgr EAS Boosting (Top-App & Foreground)
                let (ta_boost, fg_boost, uclamp_min_perf, big_task_rot) = if is_extreme {
                    ("100", "50", "1024", "1")
                } else if is_perf_or_ext {
                    ("50", "25", "512", "1")
                } else {
                    ("0", "0", "0", "0")
                };
                Self::push_op(&mut ops, "MTK-EAS", "MediaTek Perfmgr top-app boost", MtkHardware::PERFMGR_TA_BOOST, ta_boost, false);
                Self::push_op(&mut ops, "MTK-EAS", "MediaTek Perfmgr foreground boost", MtkHardware::PERFMGR_FG_BOOST, fg_boost, false);
                Self::push_op(&mut ops, "MTK-EAS", "MediaTek Perfmgr uclamp min", MtkHardware::PERFMGR_UCLAMP_MIN, uclamp_min_perf, false);
                Self::push_op(&mut ops, "MTK-EAS", "MediaTek Perfmgr big task rotation", MtkHardware::PERFMGR_BIG_TASK_ROTATION, big_task_rot, false);
            }
            Vendor::Qualcomm => {
                // ── A. Qualcomm CPU Boost Driver ────────────────────────────
                let target_input_boost_ms = match profile.name {
                    "extreme" => "120",
                    "performance" => "80",
                    "powersave" => "0",
                    _ => "40",
                };
                let target_sched_boost_input = if is_perf_or_ext || profile.cpu.boost { "1" } else { "0" };

                Self::push_op(
                    &mut ops,
                    "Qualcomm",
                    "Qualcomm CPU input boost duration",
                    QcomHardware::CPU_BOOST_INPUT_MS,
                    target_input_boost_ms,
                    false,
                );
                Self::push_op(
                    &mut ops,
                    "Qualcomm",
                    "Qualcomm sched boost on input",
                    QcomHardware::CPU_BOOST_SCHED_BOOST_ON_INPUT,
                    target_sched_boost_input,
                    false,
                );

                // ── B. Qualcomm WALT Scheduler (Safe Migration Ordering) ────
                let sched_boost_val = if is_perf_or_ext { "1" } else { "0" };
                Self::push_op(
                    &mut ops,
                    "Qualcomm",
                    "Qualcomm WALT task placement sched_boost",
                    QcomHardware::SCHED_BOOST,
                    sched_boost_val,
                    false,
                );

                let (qcom_up, qcom_down) = match profile.name {
                    "extreme" => ("60 80", "40 60"),
                    "performance" => ("65 85", "50 70"),
                    "powersave" => ("90 95", "75 85"),
                    _ => ("85 95", "65 85"), // Balanced OEM baseline for 3-cluster SM8150
                };

                // Critical Linux WALT rule: downmigrate must strictly be <= upmigrate.
                // When lowering thresholds (performance/extreme), lower downmigrate FIRST!
                // When raising thresholds (powersave/balance), raise upmigrate FIRST!
                if is_perf_or_ext {
                    Self::push_op(&mut ops, "Qualcomm", "Qualcomm WALT task downmigrate threshold", QcomHardware::SCHED_DOWNMIGRATE, qcom_down, false);
                    Self::push_op(&mut ops, "Qualcomm", "Qualcomm WALT task upmigrate threshold", QcomHardware::SCHED_UPMIGRATE, qcom_up, false);
                } else {
                    Self::push_op(&mut ops, "Qualcomm", "Qualcomm WALT task upmigrate threshold", QcomHardware::SCHED_UPMIGRATE, qcom_up, false);
                    Self::push_op(&mut ops, "Qualcomm", "Qualcomm WALT task downmigrate threshold", QcomHardware::SCHED_DOWNMIGRATE, qcom_down, false);
                }

                let min_task_util = match profile.name {
                    "extreme" => "0",
                    "performance" => "10",
                    "powersave" => "25",
                    _ => "15",
                };
                Self::push_op(&mut ops, "Qualcomm", "Qualcomm WALT min task util for boost", QcomHardware::SCHED_MIN_TASK_UTIL_FOR_BOOST, min_task_util, false);
                Self::push_op(&mut ops, "Qualcomm", "Qualcomm WALT rotate big tasks", QcomHardware::SCHED_WALT_ROTATE_BIG_TASKS, "1", false);

                let coloc_fmin = if is_perf_or_ext { "1000000" } else if is_powersave { "0" } else { "800000" };
                Self::push_op(&mut ops, "Qualcomm", "Qualcomm little cluster colocation fmin", QcomHardware::SCHED_LITTLE_CLUSTER_COLOC_FMIN_KHZ, coloc_fmin, false);

                let cstate_aware = if is_extreme { "0" } else { "1" };
                Self::push_op(&mut ops, "Qualcomm", "Qualcomm WALT C-state power awareness", QcomHardware::SCHED_CSTATE_AWARE, cstate_aware, false);

                // ── C. Qualcomm Core Control (Big & Prime Cluster Unparking) ─
                let (c4_min, c4_up, c4_down) = match profile.name {
                    "extreme" => ("3", "40 40 40", "20 20 20"),
                    "performance" => ("2", "50 50 50", "30 30 30"),
                    "powersave" => ("0", "80 80 80", "40 40 40"),
                    _ => ("1", "60 60 60", "30 30 30"),
                };
                let (c7_min, c7_up, c7_down) = match profile.name {
                    "extreme" => ("1", "40", "20"),
                    "performance" => ("1", "50", "30"),
                    "powersave" => ("0", "80", "40"),
                    _ => ("1", "60", "30"),
                };

                Self::push_op(&mut ops, "Qualcomm", "Qualcomm CPU4-6 Gold core_ctl min_cpus", format!("{}/min_cpus", QcomHardware::CORE_CTL_DIR_CPU4), c4_min, false);
                Self::push_op(&mut ops, "Qualcomm", "Qualcomm CPU4-6 Gold core_ctl busy_up_thres", format!("{}/busy_up_thres", QcomHardware::CORE_CTL_DIR_CPU4), c4_up, false);
                Self::push_op(&mut ops, "Qualcomm", "Qualcomm CPU4-6 Gold core_ctl busy_down_thres", format!("{}/busy_down_thres", QcomHardware::CORE_CTL_DIR_CPU4), c4_down, false);

                Self::push_op(&mut ops, "Qualcomm", "Qualcomm CPU7 Prime core_ctl min_cpus", format!("{}/min_cpus", QcomHardware::CORE_CTL_DIR_CPU7), c7_min, false);
                Self::push_op(&mut ops, "Qualcomm", "Qualcomm CPU7 Prime core_ctl busy_up_thres", format!("{}/busy_up_thres", QcomHardware::CORE_CTL_DIR_CPU7), c7_up, false);
                Self::push_op(&mut ops, "Qualcomm", "Qualcomm CPU7 Prime core_ctl busy_down_thres", format!("{}/busy_down_thres", QcomHardware::CORE_CTL_DIR_CPU7), c7_down, false);

                // ── D. Qualcomm GPU Bandwidth & Memlat DDR Scaling ──────────
                if is_perf_or_ext {
                    Self::push_op(&mut ops, "GPUBW", "Qualcomm GPU bus bandwidth governor", QcomHardware::QCOM_GPUBW_GOV, "performance", false);
                    Self::push_op(&mut ops, "MEMLAT", "Qualcomm LPDDR bus bandwidth boost", format!("{}/governor", QcomHardware::QCOM_LLCC_DDR_BW), "performance", false);
                } else if is_powersave {
                    Self::push_op(&mut ops, "GPUBW", "Qualcomm GPU bus bandwidth governor", QcomHardware::QCOM_GPUBW_GOV, "powersave", false);
                }

                // ── E. Qualcomm Adreno KGSL GPU Acceleration ────────────────
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
                    let target_thermal_pwr = if is_extreme { "0" } else { "1" };
                    let target_idle_timer = match profile.name {
                        "extreme" => "250",
                        "performance" => "120",
                        "powersave" => "64",
                        _ => "80",
                    };

                    Self::push_op(
                        &mut ops,
                        "GPU",
                        "Adreno KGSL max_pwrlevel (lowest clock bound)",
                        QcomHardware::KGSL_MAX_PWRLEVEL,
                        target_max_pwr.to_string(),
                        false,
                    );
                    Self::push_op(
                        &mut ops,
                        "GPU",
                        "Adreno KGSL min_pwrlevel (highest clock bound)",
                        QcomHardware::KGSL_MIN_PWRLEVEL,
                        target_min_pwr.to_string(),
                        false,
                    );
                    Self::push_op(
                        &mut ops,
                        "GPU",
                        "Adreno KGSL thermal throttling",
                        QcomHardware::KGSL_THROTTLING,
                        target_throttling,
                        false,
                    );
                    Self::push_op(
                        &mut ops,
                        "GPU",
                        "Adreno KGSL thermal power level clamp",
                        QcomHardware::KGSL_THERMAL_PWRLEVEL,
                        target_thermal_pwr,
                        false,
                    );
                    Self::push_op(
                        &mut ops,
                        "GPU",
                        "Adreno KGSL idle clock downscale timer",
                        QcomHardware::KGSL_IDLE_TIMER,
                        target_idle_timer,
                        false,
                    );
                    Self::push_op(
                        &mut ops,
                        "GPU",
                        "Adreno devfreq boost level",
                        QcomHardware::KGSL_ADRENOBOOST,
                        target_adrenoboost,
                        false,
                    );

                    // Extreme hardware rails & zero-latency PMQoS
                    if is_extreme {
                        Self::push_op(&mut ops, "GPU", "Adreno KGSL bus split disable", QcomHardware::KGSL_BUS_SPLIT, "0", false);
                        Self::push_op(&mut ops, "GPU", "Adreno KGSL force bus always on", QcomHardware::KGSL_FORCE_BUS_ON, "1", false);
                        Self::push_op(&mut ops, "GPU", "Adreno KGSL force clk always on", QcomHardware::KGSL_FORCE_CLK_ON, "1", false);
                        Self::push_op(&mut ops, "GPU", "Adreno KGSL force power rail on", QcomHardware::KGSL_FORCE_RAIL_ON, "1", false);
                        Self::push_op(&mut ops, "GPU", "Adreno KGSL active latency PMQoS", QcomHardware::KGSL_PMQOS_ACTIVE_LATENCY, "0", false);
                    } else if !is_perf_or_ext {
                        Self::push_op(&mut ops, "GPU", "Adreno KGSL bus split restore", QcomHardware::KGSL_BUS_SPLIT, "1", false);
                        Self::push_op(&mut ops, "GPU", "Adreno KGSL force bus auto restore", QcomHardware::KGSL_FORCE_BUS_ON, "0", false);
                        Self::push_op(&mut ops, "GPU", "Adreno KGSL force clk auto restore", QcomHardware::KGSL_FORCE_CLK_ON, "0", false);
                        Self::push_op(&mut ops, "GPU", "Adreno KGSL force power rail auto restore", QcomHardware::KGSL_FORCE_RAIL_ON, "0", false);
                    }
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
        let (swappiness, vfs_pressure) = if is_extreme {
            ("30", "50")
        } else if is_perf_or_ext {
            if ram_mb <= 4500 {
                ("100", "80")
            } else if ram_mb <= 6500 {
                ("85", "70")
            } else {
                ("60", "50")
            }
        } else if is_powersave {
            ("100", "100")
        } else if ram_mb <= 4500 {
            ("90", "100")
        } else {
            ("75", "85")
        };

        Self::push_op(&mut ops, "Memory", "VM swappiness (RAM-proportional)", "/proc/sys/vm/swappiness", swappiness, false);
        Self::push_op(&mut ops, "Memory", "VM vfs_cache_pressure", "/proc/sys/vm/vfs_cache_pressure", vfs_pressure, false);

        // Universal ZRAM Latency Eliminator (page-cluster = 0 reads 1 page instead of 8, cutting swap latency)
        Self::push_op(&mut ops, "Memory", "ZRAM single-page read latency fix (page-cluster 0)", "/proc/sys/vm/page-cluster", "0", false);

        // KSM (Kernel Samepage Merging) scanning suppression during gaming
        let ksm_run = if is_perf_or_ext { "0" } else { "1" };
        Self::push_op(&mut ops, "Memory", "KSM background memory scanning control", "/sys/kernel/mm/ksm/run", ksm_run, false);

        // Global writeback and filesystem durability remain under the OEM policy.

        // ── 5. Media-aware Storage Policy (Shared Across All Profiles) ─────

        let stune_top = Path::new("/dev/stune/top-app");
        if stune_top.is_dir() {
            let (s_boost, s_min) = if is_perf_or_ext { ("50", "30") } else { ("0", "0") };
            Self::push_op(&mut ops, "Scheduler", "Schedtune top-app boost", stune_top.join("schedtune.boost"), s_boost, false);
            Self::push_op(&mut ops, "Scheduler", "Schedtune top-app util min", stune_top.join("schedtune.util.min"), s_min, false);
        }

        let (ra_kb, sched, read_exp, writes_starv) = match profile.name {
            "extreme" => ("2048", "deadline", "100", "4"),
            "performance" => ("1024", "deadline", "100", "4"),
            "powersave" => ("256", "deadline", "500", "2"),
            _ => ("512", "deadline", "250", "3"), // Balance: responsive & smooth 512KB for daily use
        };

        for block_name in &["sda", "sdb", "sdc", "mmcblk0", "mmcblk1"] {
            let dev_dir = format!("/sys/block/{}", block_name);
            let q_dir = format!("{}/queue", dev_dir);
            if Path::new(&q_dir).is_dir() {
                Self::push_op(&mut ops, "Storage", format!("Block {} {}KB read-ahead", block_name, ra_kb), PathBuf::from(format!("{}/read_ahead_kb", q_dir)), ra_kb, false);
                Self::push_op(&mut ops, "Storage", format!("Block {} {} scheduler", block_name, sched), PathBuf::from(format!("{}/scheduler", q_dir)), sched, false);
                Self::push_op(&mut ops, "Storage", format!("Block {} rq_affinity (CPU cache affinity)", block_name), PathBuf::from(format!("{}/rq_affinity", q_dir)), "2", false);
                Self::push_op(&mut ops, "Storage", format!("Block {} disable random entropy overhead", block_name), PathBuf::from(format!("{}/add_random", q_dir)), "0", false);
                if is_extreme {
                    Self::push_op(&mut ops, "Storage", format!("Block {} disable iostats overhead", block_name), PathBuf::from(format!("{}/iostats", q_dir)), "0", false);
                }
                let read_exp_path = PathBuf::from(format!("{}/iosched/read_expire", q_dir));
                if read_exp_path.exists() {
                    Self::push_op(&mut ops, "Storage", format!("Block {} read expire", block_name), read_exp_path, read_exp, false);
                }
                let writes_starv_path = PathBuf::from(format!("{}/iosched/writes_starved", q_dir));
                if writes_starv_path.exists() {
                    Self::push_op(&mut ops, "Storage", format!("Block {} writes starved", block_name), writes_starv_path, writes_starv, false);
                }
            }
        }

        for dev in &cap.storage.devices {
            if let Some(target) = StoragePolicy::read_ahead_target(dev) {
                Self::push_op(&mut ops, "Storage", format!("Block {} SD prefetch ceiling", dev.name),
                    dev.queue_path.join("read_ahead_kb"), target.to_string(), false);
            }
        }

        // Dynamic Fsync (Dyn_Fsync) suspend during gaming
        let dyn_fsync = if is_perf_or_ext { "0" } else { "1" };
        for fsync_node in &["/sys/kernel/dyn_fsync/Dyn_fsync_active", "/sys/class/misc/fsynccontrol/fsync_enabled"] {
            Self::push_op(&mut ops, "Storage", "Dynamic Fsync storage barrier suspend", *fsync_node, dyn_fsync, false);
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
        Self::push_op(
            &mut ops,
            "Network",
            "TCP time-wait socket reuse",
            "/proc/sys/net/ipv4/tcp_tw_reuse",
            "1",
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

        // ── 8. Hardware IRQ Affinity Steering (Unchoke CPU0) ────────────────
        let irqs = crate::subsystems::IrqController::scan_interrupts();
        if is_perf_or_ext {
            if let Some(&touch_irq) = irqs.get("touchscreen") {
                Self::push_op(&mut ops, "IRQ", "Touchscreen interrupt affinity (Core 1)", format!("/proc/irq/{}/smp_affinity_list", touch_irq), "1", false);
            }
            if let Some(&wifi_irq) = irqs.get("wifi") {
                Self::push_op(&mut ops, "IRQ", "Wi-Fi interrupt affinity (Core 2)", format!("/proc/irq/{}/smp_affinity_list", wifi_irq), "2", false);
            }
            if let Some(&gpu_irq) = irqs.get("gpu") {
                Self::push_op(&mut ops, "IRQ", "GPU interrupt affinity (Big Cluster)", format!("/proc/irq/{}/smp_affinity_list", gpu_irq), "4-7", false);
            }
        } else {
            for (_, &irq) in irqs.iter() {
                Self::push_op(&mut ops, "IRQ", "Restore universal IRQ affinity", format!("/proc/irq/{}/smp_affinity_list", irq), "0-7", false);
            }
        }

        // ── 8. Power-Efficient Workqueue (Battery Saver & Balanced) ─────────
        let wq_path = Path::new("/sys/module/workqueue/parameters/power_efficient");
        if wq_path.exists() {
            let target_wq = if is_extreme { "N" } else { "Y" };
            Self::push_op(
                &mut ops,
                "Kernel",
                "Power-efficient workqueue",
                wq_path,
                target_wq,
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
