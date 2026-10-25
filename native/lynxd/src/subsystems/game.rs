use std::fs;
use std::path::Path;
use std::process::Command;

use crate::hardware::capability::HardwareCapability;
use crate::hardware::{MtkHardware, QcomHardware};
use crate::state::ConfigStore;
use crate::subsystems::thermal::ThermalController;
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

pub struct GameController;

const GAME_LIBS: &str = "com.miHoYo., com.miHoYo.GenshinImpact, com.activision., com.epicgames, com.dts., UnityMain, libunity.so, libil2cpp.so, libmain.so, libcri_vip_unity.so, libopus.so, libxlua.so, libUE4.so, libAsphalt9.so, libnative-lib.so, libRiotGamesApi.so, libResources.so, libagame.so, libapp.so, libflutter.so, libMSDKCore.so, libFIFAMobileNeon.so, libUnreal.so, libEOSSDK.so, libcocos2dcpp.so, libfb.so";

impl GameController {
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

    /// Engages aggressive low-latency game optimizations across scheduler, touch, render pipeline, and thermal guards.
    pub fn game_on(top_pid: Option<u32>) -> String {
        // 1. Freeze OEM background throttlers
        ThermalController::freeze_oem_throttlers();

        // 2. UCLAMP Min Allocation
        let min_ratio = ConfigStore::get("game_min_ratio")
            .and_then(|v| v.parse::<u32>().ok())
            .unwrap_or(70);

        let max_scale = Self::read_trimmed("/dev/cpuset/top-app/cpu.uclamp.max")
            .and_then(|v| v.parse::<u32>().ok())
            .unwrap_or(100);

        let scaled_val = if max_scale > 100 {
            (min_ratio * 1024) / 100
        } else {
            min_ratio
        };

        Self::write_opt("/dev/cpuset/top-app/cpu.uclamp.min", scaled_val.to_string());
        Self::write_opt("/proc/sys/kernel/sched_util_clamp_min", scaled_val.to_string());

        // Schedutil Ultra-Fast Rate Limits (Sub-millisecond latency)
        for id in 0..16 {
            Self::write_opt(
                format!("/sys/devices/system/cpu/cpufreq/policy{}/schedutil/up_rate_limit_us", id),
                "500",
            );
            Self::write_opt(
                format!("/sys/devices/system/cpu/cpufreq/policy{}/schedutil/down_rate_limit_us", id),
                "20000",
            );
        }

        // 3. Multi-Gen LRU (MGLRU)
        Self::write_opt("/sys/kernel/mm/lru_gen/enabled", "y");

        // 4. Kernel Game Library Priority Injection
        Self::write_opt("/proc/sys/kernel/sched_lib_name", GAME_LIBS);
        Self::write_opt("/proc/sys/kernel/sched_lib_mask_force", "255");

        // 5. Audio Pipeline Safety: Preserve OEM/user audio policies to prevent
        // audioserver watchdog crash loops with custom HALs / Dolby Atmos / AML mods.
        let _ = Command::new("setprop").args(&["af.fast_track_multiplier", "1"]).output();

        // 6. Display Refresh Rate & Touch Sampling
        if let Ok(output) = Command::new("settings").args(&["get", "system", "peak_refresh_rate"]).output() {
            let peak = String::from_utf8_lossy(&output.stdout).trim().to_string();
            if !peak.is_empty() && peak != "null" {
                if !Path::new("/dev/lynx_orig_min_rr").exists() {
                    if let Ok(min_out) = Command::new("settings").args(&["get", "system", "min_refresh_rate"]).output() {
                        let orig_min = String::from_utf8_lossy(&min_out.stdout).trim().to_string();
                        let _ = fs::write("/dev/lynx_orig_min_rr", if orig_min.is_empty() { "60.0" } else { &orig_min });
                    }
                }
                let _ = Command::new("settings").args(&["put", "system", "min_refresh_rate", &peak]).output();
            }
        }

        // Touch Game Mode Nodes
        const TOUCH_NODES: &[&str] = &[
            "/sys/class/touch/touch_dev/touch_game_mode",
            "/sys/devices/virtual/touch/touch_dev/bump_sample_rate",
            "/proc/touchscreen/game_mode",
            "/sys/devices/platform/tp_wake_switch/game_mode",
        ];
        for node in TOUCH_NODES {
            Self::write_opt(node, "1");
        }

        // 7. MediaTek Platform Game Boost (GED, FPSGO, PPM, Perfmgr, Mali)
        Self::write_opt(MtkHardware::GED_GX_GAME_MODE, "1");
        Self::write_opt(MtkHardware::GED_GX_BOOST_ON, "1");
        Self::write_opt(MtkHardware::GED_GX_FORCE_CPU_BOOST, "1");
        Self::write_opt(MtkHardware::GED_FB_DVFS_THRESHOLD, "30");
        Self::write_opt(MtkHardware::GED_BOOST_AMP, "5");
        Self::write_opt(MtkHardware::GED_BOOST_EXTRA, "1");
        Self::write_opt(MtkHardware::GED_BOOST_GPU_ENABLE, "1");
        Self::write_opt(MtkHardware::GED_GPU_IDLE, "0");
        Self::write_opt(MtkHardware::GED_MONITOR_3D_FENCE_DISABLE, "1");
        Self::write_opt("/sys/module/ged/parameters/target_t_cpu_remained", "4166666");

        // Universal Dynamic Hardware-Aware GPU Frequency Floor & Ceiling
        // BANS all hardcoded frequencies (e.g. 850/950 MHz). Adapts dynamically to ANY SoC
        // (Helio, Dimensity, Snapdragon Adreno, Exynos Xclipse).
        // Resolves max hardware clock and locks floor strictly to >= 95% of peak (or 100% max OPP)
        // to prevent premature downclocking and ensure rock-solid frame rates.
        let (vendor, _) = crate::hardware::detector::ScoreBoard::evaluate();
        let gpu = HardwareCapability::resolve_gpu(vendor);

        // Probe live kernel hardware nodes directly to detect any Overclock (OC) kernel or KPM in real-time
        let live_upbound_mhz = SysfsReader::read_int::<u64>(MtkHardware::GED_GPU_CUST_UPBOUND_FREQ)
            .or_else(|_| SysfsReader::read_int::<u64>("/sys/class/kgsl/kgsl-3d0/max_clock_mhz"))
            .or_else(|_| SysfsReader::read_int::<u64>("/sys/class/kgsl/kgsl-3d0/max_gpuclk"))
            .map(HardwareCapability::normalize_freq_to_mhz)
            .unwrap_or(0);

        let live_cur_mhz = gpu.cur_freq_mhz;
        let table_max_mhz = gpu.available_freqs_mhz.last().copied().unwrap_or(gpu.max_freq_mhz);

        // Peak frequency: takes the highest between hardware table, sysfs ceiling, and active clock (never capping an OC)
        let peak_mhz = table_max_mhz.max(live_upbound_mhz).max(live_cur_mhz);

        if peak_mhz > 0 {
            // Lock GPU frequency strictly to peak hardware/OC capability (titik tertingginya)
            // Completely bans hardcoded numbers and eliminates gaming frame drops & microstutters.
            let peak_khz = peak_mhz * 1000;

            // Apply universal GPU frequency boost: locks strictly to peak hardware/OC frequency
            crate::subsystems::GpuController::set_freq(peak_mhz, Some(peak_mhz));

            // Specialized MediaTek GED parameter bindings (Dynamic kHz & respects live OC ceiling)
            let existing_ged_upbound = SysfsReader::read_int::<u64>(MtkHardware::GED_GPU_CUST_UPBOUND_FREQ).unwrap_or(0);
            let final_upbound_khz = peak_khz.max(existing_ged_upbound);

            Self::write_opt(MtkHardware::GED_GPU_CUST_UPBOUND_FREQ, final_upbound_khz.to_string());
            Self::write_opt(MtkHardware::GED_GPU_CUST_BOOST_FREQ, peak_khz.to_string());
            Self::write_opt(MtkHardware::GED_GPU_BOTTOM_FREQ, peak_khz.to_string());

            // GED HAL upbound and boost: '0' points to topmost OPP table index (even if extended by OC)
            Self::write_opt("/sys/kernel/ged/hal/custom_upbound_gpu_freq", "0");
            Self::write_opt("/sys/kernel/ged/hal/custom_boost_gpu_freq", "0");
            Self::write_opt("/sys/kernel/ged/hal/gpu_boost_level", "100");
            Self::write_opt("/sys/kernel/ged/hal/dvfs_loading_mode", "1");

            // MediaTek GPU Hardware Throttling Bypass (Disable THERMAL ID 3 & PBM ID 7)
            Self::write_opt("/proc/gpufreq/gpufreq_limit_table", "3 0 0");
            Self::write_opt("/proc/gpufreq/gpufreq_limit_table", "7 0 0");
        }

        Self::write_opt(MtkHardware::FPSGO_ENABLE, "1");
        Self::write_opt(MtkHardware::FPSGO_BOOST_TA, "1");
        Self::write_opt(MtkHardware::FPSGO_ULTRA_RESCUE, "1");
        Self::write_opt(MtkHardware::FPSGO_FBT_THRM_TEMP_TH, "85");
        Self::write_opt(MtkHardware::FPSGO_FBT_THRM_LIMIT_CPU, "0");
        Self::write_opt(MtkHardware::FBT_CPU_RESCUE_SECOND, "1");
        Self::write_opt(MtkHardware::FBT_CPU_RESCUE_PERCENT, "100");
        Self::write_opt(MtkHardware::FBT_CPU_BHR_OPP, "1");
        Self::write_opt(MtkHardware::FBT_CPU_GCC_ENABLE, "1");

        Self::write_opt("/sys/kernel/fpsgo/fbt/switch_idleprefer", "0");
        Self::write_opt("/sys/kernel/fpsgo/fbt/enable_switch_down_throttle", "0");
        Self::write_opt("/sys/kernel/fpsgo/common/gpu_block_boost", "1 1 -1");
        Self::write_opt("/sys/kernel/fpsgo/common/force_onoff", "1");
        Self::write_opt(MtkHardware::PPM_POLICY_STATUS, "9 1");
        Self::write_opt(MtkHardware::PERFMGR_TA_BOOST, "50");
        Self::write_opt(MtkHardware::PERFMGR_BIG_TASK_ROTATION, "1");

        // Dynamic Mali Bifrost/Valhall GPU Driver Tuning
        for entry in &["/sys/devices/platform/13000000.mali", "/sys/devices/platform/mali.0", "/sys/class/misc/mali0/device"] {
            Self::write_opt(format!("{}/dvfs_period", entry), "10");
            Self::write_opt(format!("{}/power_policy", entry), "always_on");
            Self::write_opt(format!("{}/js_scheduling_period", entry), "50");
        }
        Self::write_opt("/proc/ged/hal/dvfs_margin_value", "50");

        // 8. Dynamic Media-Aware Storage Engine (UFS vs eMMC vs SD Card — Zero-Hardcoding)
        // High-end UFS gets 2048KB burst prefetch & 512 queue depth for stutter-free asset streaming.
        // eMMC 5.1 gets 256KB & deadline to prevent FBE crypto queue choke and UI freezes.
        let storage = HardwareCapability::resolve_storage();
        for dev in &storage.devices {
            if !dev.queue_path.is_dir() {
                continue;
            }
            let block = match dev.queue_path.parent() {
                Some(b) => b,
                None => continue,
            };
            if SysfsReader::read_trimmed(block.join("ro")).ok().as_deref() == Some("1") {
                continue;
            }
            let effective_type = if dev.device_type == crate::hardware::capability::BlockDeviceType::Virtual && dev.name.starts_with("dm-") {
                crate::hardware::storage::StoragePolicy::resolve_dm_slave_type(&dev.queue_path)
            } else {
                dev.device_type
            };

            let (ra_kb, nr, sched) = match effective_type {
                crate::hardware::capability::BlockDeviceType::Ufs | crate::hardware::capability::BlockDeviceType::Nvme => {
                    (2048, "512", "none")
                }
                crate::hardware::capability::BlockDeviceType::Emmc => {
                    (256, "128", "deadline")
                }
                crate::hardware::capability::BlockDeviceType::Sd => {
                    (512, "128", "deadline")
                }
                _ => (512, "128", "none"),
            };

            Self::write_opt(dev.queue_path.join("read_ahead_kb"), ra_kb.to_string());
            Self::write_opt(dev.queue_path.join("nr_requests"), nr);
            Self::write_opt(dev.queue_path.join("nomerges"), "0");
            Self::write_opt(dev.queue_path.join("rq_affinity"), "2");

            if dev.available_schedulers.iter().any(|s| s == sched) {
                Self::write_opt(dev.queue_path.join("scheduler"), sched);
            } else if sched == "deadline" {
                if let Some(alt) = dev.available_schedulers.iter().find(|s| s.as_str() == "mq-deadline" || s.as_str() == "kyber") {
                    Self::write_opt(dev.queue_path.join("scheduler"), alt);
                }
            }
        }

        // Virtual Memory & VFS Cache Shield (Eliminates Asset Streaming & Chunk Loading Stalls)
        Self::write_opt("/proc/sys/vm/vfs_cache_pressure", "30");
        Self::write_opt("/proc/sys/vm/watermark_boost_factor", "0");

        // Dynamic RAM-Proportional extra_free_kbytes (Prevents background memory compaction spikes)
        let mem = HardwareCapability::resolve_memory();
        let extra_free_kb = if mem.total_ram_mb >= 7000 {
            20480
        } else if mem.total_ram_mb >= 3800 {
            12288
        } else {
            6144
        };
        Self::write_opt("/proc/sys/vm/extra_free_kbytes", extra_free_kb.to_string());

        // Dynamic Schedutil iowait_boost_enable across all CPU clusters
        for id in 0..16 {
            Self::write_opt(
                format!("/sys/devices/system/cpu/cpufreq/policy{}/schedutil/iowait_boost_enable", id),
                "1",
            );
        }

        // 9. Qualcomm Snapdragon Platform Game Boost (KGSL, WALT, Core Control, CPU Boost)
        Self::write_opt(QcomHardware::KGSL_IDLE_TIMER, "120");
        Self::write_opt(QcomHardware::KGSL_THROTTLING, "0");
        Self::write_opt(QcomHardware::KGSL_THERMAL_PWRLEVEL, "0");
        Self::write_opt(QcomHardware::KGSL_PMQOS_ACTIVE_LATENCY, "0");
        Self::write_opt(QcomHardware::KGSL_ADRENOBOOST, "2");
        Self::write_opt("/sys/class/kgsl/kgsl-3d0/adrenoboost", "2");
        Self::write_opt(QcomHardware::KGSL_SPTP_PC, "0");
        Self::write_opt(QcomHardware::KGSL_SKIPSAVERESTORE, "1");
        Self::write_opt(QcomHardware::KGSL_WAKE_NICE, "-20");
        Self::write_opt(QcomHardware::KGSL_GPU_LLC_SLICE, "1");

        // Qualcomm WALT Scheduler Gaming Latency (downmigrate first to comply with kernel down < up rule)
        Self::write_opt(QcomHardware::SCHED_BOOST, "1");
        Self::write_opt(QcomHardware::SCHED_DOWNMIGRATE, "50 70");
        Self::write_opt(QcomHardware::SCHED_UPMIGRATE, "65 85");
        Self::write_opt(QcomHardware::SCHED_WALT_ROTATE_BIG_TASKS, "1");
        Self::write_opt(QcomHardware::SCHED_MIN_TASK_UTIL_FOR_BOOST, "10");

        // Qualcomm Core Control Active Core Unparking (Gold cores 4-6 & Prime core 7)
        Self::write_opt(format!("{}/min_cpus", QcomHardware::CORE_CTL_DIR_CPU4), "3");
        Self::write_opt(format!("{}/busy_up_thres", QcomHardware::CORE_CTL_DIR_CPU4), "40 40 40");
        Self::write_opt(format!("{}/min_cpus", QcomHardware::CORE_CTL_DIR_CPU7), "1");
        Self::write_opt(format!("{}/busy_up_thres", QcomHardware::CORE_CTL_DIR_CPU7), "40");

        // Qualcomm CPU Boost
        Self::write_opt(QcomHardware::CPU_BOOST_INPUT_MS, "80");
        Self::write_opt(QcomHardware::CPU_BOOST_SCHED_BOOST_ON_INPUT, "1");

        // 9. Active Game Flag
        let _ = fs::write("/dev/lynx_active_game", "1\n");

        // 10. Process Optimization if PID provided
        let pid_msg = if let Some(pid) = top_pid {
            Self::optimize_pid(pid);
            format!(" [PID {} optimized]", pid)
        } else {
            String::new()
        };

        format!("Game Mode successfully engaged (UClamp: {}%, SchedLib, Audio MMAP, TouchBoost).{}", min_ratio, pid_msg)
    }

    /// Disengages game mode, restoring standard system responsiveness and scheduler balance.
    pub fn game_off() -> String {
        // 1. Unfreeze OEM Throttlers
        ThermalController::unfreeze_oem_throttlers();

        // 2. Reset UCLAMP
        Self::write_opt("/dev/cpuset/top-app/cpu.uclamp.min", "0");
        Self::write_opt("/proc/sys/kernel/sched_util_clamp_min", "0");

        // Balanced Schedutil Rate Limits
        for id in 0..16 {
            Self::write_opt(
                format!("/sys/devices/system/cpu/cpufreq/policy{}/schedutil/up_rate_limit_us", id),
                "1000",
            );
            Self::write_opt(
                format!("/sys/devices/system/cpu/cpufreq/policy{}/schedutil/down_rate_limit_us", id),
                "4000",
            );
        }

        // 3. Clear Sched Lib
        Self::write_opt("/proc/sys/kernel/sched_lib_name", "");
        Self::write_opt("/proc/sys/kernel/sched_lib_mask_force", "0");

        // 4. Reset Audio Multiplier
        let _ = Command::new("setprop").args(&["af.fast_track_multiplier", "2"]).output();

        // 5. Restore Refresh Rate
        if Path::new("/dev/lynx_orig_min_rr").exists() {
            if let Ok(orig_min) = fs::read_to_string("/dev/lynx_orig_min_rr") {
                let trimmed = orig_min.trim();
                if !trimmed.is_empty() {
                    let _ = Command::new("settings").args(&["put", "system", "min_refresh_rate", trimmed]).output();
                }
            }
            let _ = fs::remove_file("/dev/lynx_orig_min_rr");
        }

        // Touch Game Mode Nodes Reset
        const TOUCH_NODES: &[&str] = &[
            "/sys/class/touch/touch_dev/touch_game_mode",
            "/sys/devices/virtual/touch/touch_dev/bump_sample_rate",
            "/proc/touchscreen/game_mode",
            "/sys/devices/platform/tp_wake_switch/game_mode",
        ];
        for node in TOUCH_NODES {
            Self::write_opt(node, "0");
        }

        // 6. Universal GPU Clock Restore to Auto DVFS
        crate::subsystems::GpuController::set_freq(0, Some(0));

        // Restore MediaTek GPU limit table (Re-enable THERMAL ID 3 & PBM ID 7)
        Self::write_opt("/proc/gpufreq/gpufreq_limit_table", "3 1 1");
        Self::write_opt("/proc/gpufreq/gpufreq_limit_table", "7 1 1");
        Self::write_opt("/sys/kernel/ged/hal/gpu_boost_level", "0");
        Self::write_opt("/sys/kernel/ged/hal/dvfs_loading_mode", "0");

        // Reset MediaTek GED, FPSGO, and PPM Hooks
        Self::write_opt("/sys/module/ged/parameters/gx_top_app_pid", "0");
        Self::write_opt(MtkHardware::GED_GX_GAME_MODE, "0");
        Self::write_opt(MtkHardware::GED_GX_BOOST_ON, "0");
        Self::write_opt(MtkHardware::GED_GX_FORCE_CPU_BOOST, "0");
        Self::write_opt(MtkHardware::GED_FB_DVFS_THRESHOLD, "80");
        Self::write_opt(MtkHardware::GED_BOOST_AMP, "3");
        Self::write_opt(MtkHardware::GED_BOOST_EXTRA, "0");
        Self::write_opt(MtkHardware::GED_GPU_IDLE, "100");
        Self::write_opt(MtkHardware::GED_MONITOR_3D_FENCE_DISABLE, "0");

        Self::write_opt(MtkHardware::FPSGO_FBT_THRM_TEMP_TH, "65");
        Self::write_opt(MtkHardware::FPSGO_FBT_THRM_LIMIT_CPU, "1");
        Self::write_opt(MtkHardware::FBT_CPU_RESCUE_SECOND, "0");
        Self::write_opt(MtkHardware::FBT_CPU_GCC_ENABLE, "0");

        Self::write_opt(MtkHardware::PPM_POLICY_STATUS, "9 0");
        Self::write_opt(MtkHardware::PERFMGR_TA_BOOST, "0");
        Self::write_opt(MtkHardware::PERFMGR_BIG_TASK_ROTATION, "0");

        for entry in &["/sys/devices/platform/13000000.mali", "/sys/devices/platform/mali.0", "/sys/class/misc/mali0/device"] {
            Self::write_opt(format!("{}/dvfs_period", entry), "20");
            Self::write_opt(format!("{}/power_policy", entry), "coarse_demand");
            Self::write_opt(format!("{}/js_scheduling_period", entry), "100");
        }
        Self::write_opt("/proc/ged/hal/dvfs_margin_value", "0");

        // Restore Dynamic Storage to Balanced Targets (Zero-Hardcoding)
        let storage = HardwareCapability::resolve_storage();
        for dev in &storage.devices {
            if !dev.queue_path.is_dir() {
                continue;
            }
            if let Some(params) = crate::hardware::storage::StoragePolicy::profile_targets(dev, "balance") {
                Self::write_opt(dev.queue_path.join("read_ahead_kb"), params.read_ahead_kb.to_string());
                if let Some(nr) = params.nr_requests {
                    Self::write_opt(dev.queue_path.join("nr_requests"), nr.to_string());
                }
            }
        }

        // Restore Virtual Memory & VFS Cache
        Self::write_opt("/proc/sys/vm/vfs_cache_pressure", "100");
        Self::write_opt("/proc/sys/vm/extra_free_kbytes", "4096");
        Self::write_opt("/proc/sys/vm/watermark_boost_factor", "15000");

        for id in 0..16 {
            Self::write_opt(
                format!("/sys/devices/system/cpu/cpufreq/policy{}/schedutil/iowait_boost_enable", id),
                "0",
            );
        }

        // 7. Reset Qualcomm Snapdragon KGSL, WALT, and Core Control Hooks
        Self::write_opt(QcomHardware::KGSL_IDLE_TIMER, "80");
        Self::write_opt(QcomHardware::KGSL_THROTTLING, "1");
        Self::write_opt(QcomHardware::KGSL_THERMAL_PWRLEVEL, "1");
        Self::write_opt(QcomHardware::KGSL_ADRENOBOOST, "0");
        Self::write_opt("/sys/class/kgsl/kgsl-3d0/adrenoboost", "0");
        Self::write_opt(QcomHardware::KGSL_SPTP_PC, "1");
        Self::write_opt(QcomHardware::KGSL_SKIPSAVERESTORE, "0");
        Self::write_opt(QcomHardware::KGSL_WAKE_NICE, "0");

        // Qualcomm WALT Scheduler Balanced Baseline (upmigrate first when raising thresholds)
        Self::write_opt(QcomHardware::SCHED_UPMIGRATE, "85 95");
        Self::write_opt(QcomHardware::SCHED_DOWNMIGRATE, "65 85");
        Self::write_opt(QcomHardware::SCHED_BOOST, "0");
        Self::write_opt(QcomHardware::SCHED_MIN_TASK_UTIL_FOR_BOOST, "15");

        // Qualcomm Core Control Reset
        Self::write_opt(format!("{}/min_cpus", QcomHardware::CORE_CTL_DIR_CPU4), "1");
        Self::write_opt(format!("{}/busy_up_thres", QcomHardware::CORE_CTL_DIR_CPU4), "60 60 60");
        Self::write_opt(format!("{}/min_cpus", QcomHardware::CORE_CTL_DIR_CPU7), "1");
        Self::write_opt(format!("{}/busy_up_thres", QcomHardware::CORE_CTL_DIR_CPU7), "60");

        // Qualcomm CPU Boost Reset
        Self::write_opt(QcomHardware::CPU_BOOST_INPUT_MS, "40");
        Self::write_opt(QcomHardware::CPU_BOOST_SCHED_BOOST_ON_INPUT, "0");

        // 8. Remove Active Game Flag
        let _ = fs::remove_file("/dev/lynx_active_game");

        "Game Mode disengaged (Restored Balanced EAS, Audio, and Touch profiles).".to_string()
    }

    /// Optimizes thread scheduling, CPU affinity, and I/O priority for a specific process ID.
    pub fn optimize_pid(pid: u32) {
        let pid_str = pid.to_string();

        // Process level priority & I/O
        let _ = Command::new("renice").args(&["-n", "-20", "-p", &pid_str]).output();
        let _ = Command::new("ionice").args(&["-c", "1", "-n", "0", "-p", &pid_str]).output();

        // Put process into top-app cpuset
        Self::write_opt("/dev/cpuset/top-app/cgroup.procs", &pid_str);
        Self::write_opt("/sys/module/ged/parameters/gx_top_app_pid", &pid_str);

        // Calculate CPU masks
        let cap = HardwareCapability::resolve();
        let total_cores = cap.cpu.total_cores.max(1);
        let all_mask: u64 = if total_cores >= 64 { !0 } else { (1 << total_cores) - 1 };

        let mut little_mask: u64 = 0;
        for p in &cap.cpu.policies {
            if p.role.to_lowercase().contains("little") || p.role.to_lowercase().contains("silver") {
                for part in p.related_cpus.split_whitespace() {
                    if let Ok(id) = part.parse::<usize>() {
                        if id < 64 {
                            little_mask |= 1 << id;
                        }
                    } else if let Some(dash) = part.find('-') {
                        if let (Ok(start), Ok(end)) = (part[..dash].parse::<usize>(), part[dash + 1..].parse::<usize>()) {
                            for id in start..=end {
                                if id < 64 {
                                    little_mask |= 1 << id;
                                }
                            }
                        }
                    }
                }
            }
        }
        if little_mask == 0 {
            little_mask = all_mask;
        }

        let hex_all = format!("{:x}", all_mask);
        let hex_little = format!("{:x}", little_mask);

        // Thread level tuning via /proc/<pid>/task
        let task_dir = format!("/proc/{}/task", pid);
        if let Ok(entries) = fs::read_dir(&task_dir) {
            for entry in entries.flatten() {
                let tid = entry.file_name().to_string_lossy().to_string();
                if let Ok(comm) = fs::read_to_string(entry.path().join("comm")) {
                    let c = comm.trim();
                    if c.contains("UnityMain")
                        || c.contains("UnityGfx")
                        || c.contains("Main")
                        || c.contains("main")
                        || c.contains("RenderThread")
                        || c.contains("Gfx")
                        || c.contains("VKWorker")
                        || c.contains("GLWorker")
                    {
                        let _ = Command::new("renice").args(&["-n", "-20", "-p", &tid]).output();
                        let _ = Command::new("taskset").args(&["-p", &hex_all, &tid]).output();
                    } else if c.contains("Audio") || c.contains("Sound") || c.contains("FMOD") || c.contains("OkHttp") {
                        let _ = Command::new("renice").args(&["-n", "-10", "-p", &tid]).output();
                        let _ = Command::new("taskset").args(&["-p", &hex_little, &tid]).output();
                    } else if c.contains("Worker") || c.contains("Job") {
                        let _ = Command::new("renice").args(&["-n", "-5", "-p", &tid]).output();
                        let _ = Command::new("taskset").args(&["-p", &hex_all, &tid]).output();
                    }
                }
            }
        }

        // Trigger asynchronous Ahead-Of-Time (AOT) machine code compilation in background if not already compiled with 'speed'
        if let Ok(cmd_bytes) = fs::read(format!("/proc/{}/cmdline", pid)) {
            let cmd_str = String::from_utf8_lossy(&cmd_bytes);
            let pkg = cmd_str.split('\0').next().unwrap_or("").trim().to_string();
            if !pkg.is_empty() && !pkg.starts_with('/') && pkg.contains('.') {
                std::thread::spawn(move || {
                    let dump = Command::new("dumpsys").args(&["package", &pkg]).output().ok();
                    let already_speed = dump.map(|d| {
                        let s = String::from_utf8_lossy(&d.stdout);
                        s.contains("status=speed]")
                    }).unwrap_or(false);
                    if !already_speed {
                        let _ = Command::new("cmd").args(&["package", "compile", "-m", "speed", &pkg]).output();
                    }
                });
            }
        }
    }

    /// Locates the primary process ID (PID) for a given package name by scanning /proc cmdlines.
    pub fn find_pid_for_package(pkg: &str) -> Option<u32> {
        let pkg_clean = pkg.split(':').next().unwrap_or(pkg).trim();
        if pkg_clean.is_empty() {
            return None;
        }
        if let Ok(entries) = fs::read_dir("/proc") {
            for entry in entries.flatten() {
                if let Ok(file_name) = entry.file_name().into_string() {
                    if let Ok(pid) = file_name.parse::<u32>() {
                        let cmd_path = entry.path().join("cmdline");
                        if let Ok(bytes) = fs::read(&cmd_path) {
                            let cmd_str = String::from_utf8_lossy(&bytes);
                            let first = cmd_str.split('\0').next().unwrap_or("").trim();
                            if first == pkg_clean || first.starts_with(&format!("{}:", pkg_clean)) {
                                return Some(pid);
                            }
                        }
                    }
                }
            }
        }
        None
    }

    /// Emits JSON status of the game optimizer.
    pub fn status_json() -> String {
        let is_active = Path::new("/dev/lynx_active_game").exists();
        let uclamp_val = Self::read_trimmed("/dev/cpuset/top-app/cpu.uclamp.min").unwrap_or_else(|| "0".to_string());
        let sched_lib = Self::read_trimmed("/proc/sys/kernel/sched_lib_name").unwrap_or_default();
        let has_sched_lib = !sched_lib.is_empty();

        format!(
            "{{\"game_mode_active\":{},\"uclamp_min\":\"{}\",\"sched_lib_injected\":{}}}",
            is_active, uclamp_val, has_sched_lib
        )
    }
}
