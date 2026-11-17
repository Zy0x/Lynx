use std::fs;
use std::path::Path;
use std::process::Command;

use crate::hardware::capability::HardwareCapability;
use crate::hardware::{MtkHardware, QcomHardware};
use crate::state::ConfigStore;
use crate::subsystems::thermal::ThermalController;
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

pub struct GameController;

const GAME_LIBS: &str = "com.HoYoverse., com.HoYoverse.hkrpgoversea, com.miHoYo., com.miHoYo.GenshinImpact, com.activision., com.epicgames, com.dts., UnityMain, libunity.so, libil2cpp.so, libmain.so, libcri_vip_unity.so, libopus.so, libxlua.so, libUE4.so, libAsphalt9.so, libnative-lib.so, libRiotGamesApi.so, libResources.so, libagame.so, libapp.so, libflutter.so, libMSDKCore.so, libFIFAMobileNeon.so, libUnreal.so, libEOSSDK.so, libcocos2dcpp.so, libfb.so";

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
        Self::write_opt("/sys/devices/system/cpu/cpufreq/schedutil/up_rate_limit_us", "500");
        Self::write_opt("/sys/devices/system/cpu/cpufreq/schedutil/down_rate_limit_us", "20000");

        // 3. Multi-Gen LRU (MGLRU)
        super::memory::MemoryController::enable_mglru();

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

        // Peak frequency: dynamically probe & verify highest silicon-capable OPP (zero hardcoding)
        let verified_peak = if Path::new(MtkHardware::LYNX_GPU_MAX_KHZ).exists() {
            MtkHardware::resolve_verified_peak_opp(&gpu)
        } else {
            gpu.highest_opp().cloned()
        };
        let peak_mhz = verified_peak
            .as_ref()
            .map(|e| e.freq_mhz)
            .unwrap_or_else(|| table_max_mhz.max(live_upbound_mhz).max(live_cur_mhz));

        if peak_mhz > 0 {
            let peak_khz = verified_peak.as_ref().map(|e| e.freq_khz).unwrap_or(peak_mhz * 1000);
            Self::write_opt(MtkHardware::LYNX_GPU_MAX_KHZ, peak_khz.to_string());

            // Apply universal GPU frequency boost ephemerally without mutating user config.json
            crate::subsystems::GpuController::apply_freq_ephemeral(peak_mhz, Some(peak_mhz));

            // Specialized MediaTek GED parameter bindings (Dynamic kHz & respects live OC ceiling)
            let final_upbound_khz = peak_khz;

            Self::write_opt(MtkHardware::GED_GPU_CUST_UPBOUND_FREQ, final_upbound_khz.to_string());
            Self::write_opt(MtkHardware::GED_GPU_CUST_BOOST_FREQ, peak_khz.to_string());
            Self::write_opt(MtkHardware::GED_GPU_BOTTOM_FREQ, peak_khz.to_string());
            Self::write_opt("/sys/module/ged/parameters/gpu_bottom_freq", peak_khz.to_string());
            Self::write_opt("/sys/module/ged/parameters/gpu_cust_boost_freq", peak_khz.to_string());
            Self::write_opt("/sys/module/ged/parameters/gpu_cust_upbound_freq", final_upbound_khz.to_string());
            Self::write_opt("/sys/module/ged/parameters/boost_upper_bound", "100");
            Self::write_opt("/sys/module/ged/parameters/boost_extra", "1");
            Self::write_opt("/sys/module/ged/parameters/gx_force_cpu_boost", "1");
            Self::write_opt("/sys/module/ged/parameters/gx_game_mode", "1");
            Self::write_opt("/sys/module/ged/parameters/gx_boost_on", "1");

            // GED HAL upbound and boost: resolve exact OPP index matching verified peak_mhz
            let peak_opp_idx = verified_peak
                .as_ref()
                .map(|e| e.index.to_string())
                .unwrap_or_else(|| {
                    gpu.opp_table
                        .iter()
                        .min_by_key(|e| (e.freq_mhz as i64 - peak_mhz as i64).abs())
                        .map(|e| e.index.to_string())
                        .unwrap_or_else(|| "0".to_string())
                });
            Self::write_opt("/sys/kernel/ged/hal/custom_upbound_gpu_freq", &peak_opp_idx);
            Self::write_opt("/sys/kernel/ged/hal/custom_boost_gpu_freq", &peak_opp_idx);
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
        Self::write_opt(MtkHardware::PERFMGR_TA_BOOST, "60");
        Self::write_opt(MtkHardware::PERFMGR_BIG_TASK_ROTATION, "1");

        // EAS Schedtune Top-App Gaming Boost & Permission Shield
        Self::write_opt("/dev/stune/top-app/schedtune.boost", "50");
        Self::write_opt("/dev/stune/top-app/schedtune.prefer_idle", "1");
        Self::write_opt("/dev/stune/top-app/schedtune.prefer_high_cap", "1");
        crate::subsystems::SchedulerGuardian::enforce_permission_shield(true);

        // Dynamic Mali Bifrost/Valhall GPU Driver Tuning
        for entry in MtkHardware::mali_sysfs_paths() {
            Self::write_opt(entry.join("dvfs_period"), "10");
            Self::write_opt(entry.join("power_policy"), "always_on");
            Self::write_opt(entry.join("js_scheduling_period"), "50");
        }
        Self::write_opt("/sys/kernel/ged/hal/dvfs_margin_value", "100");
        Self::write_opt("/sys/kernel/ged/hal/timer_base_dvfs_margin", "100");
        Self::write_opt("/sys/kernel/ged/hal/loading_base_dvfs_step", "4");
        Self::write_opt(MtkHardware::LYNX_THERMAL_SILENCER, "1");
        MtkHardware::capture_and_disable_imax();
        Self::write_opt(MtkHardware::CPUFREQ_IMAX_THERMAL_PROTECT, "0");
        MtkHardware::boost_ddr_peak(true);
        Self::write_opt(MtkHardware::FPSGO_BOOST_TA, "1");
        Self::write_opt(MtkHardware::FPSGO_ULTRA_RESCUE, "1");
        Self::write_opt("/sys/kernel/fpsgo/fbt/switch_idleprefer", "0");
        Self::write_opt("/sys/kernel/fpsgo/fbt/enable_switch_down_throttle", "0");
        Self::write_opt("/sys/kernel/fpsgo/common/gpu_block_boost", "1 1 -1");
        Self::write_opt("/sys/kernel/fpsgo/common/force_onoff", "1");
        Self::write_opt(MtkHardware::PPM_POLICY_STATUS, "9 1");
        Self::write_opt(MtkHardware::PERFMGR_TA_BOOST, "60");
        Self::write_opt(MtkHardware::PERFMGR_BIG_TASK_ROTATION, "1");
        let cap_for_irq = HardwareCapability::resolve();
        let topo_for_irq = TopologyMasks::from_policies(&cap_for_irq.cpu.policies, cap_for_irq.cpu.total_cores);
        Self::offload_heavy_irqs(&topo_for_irq);
        Self::offload_kswapd_kcompactd(topo_for_irq.little_mask);

        // 8. Unified Media-Aware Storage Engine (Shared Single Source of Truth with StoragePolicy)
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
            let effective_type = crate::hardware::storage::StoragePolicy::effective_device_type(dev);
            if let Some(params) = crate::hardware::storage::StoragePolicy::profile_targets(dev, "performance") {
                Self::write_opt(dev.queue_path.join("read_ahead_kb"), params.read_ahead_kb.to_string());
                if let Some(nr) = params.nr_requests {
                    Self::write_opt(dev.queue_path.join("nr_requests"), nr.to_string());
                }
                Self::write_opt(dev.queue_path.join("rq_affinity"), params.rq_affinity.to_string());
                Self::write_opt(dev.queue_path.join("add_random"), "0");
                Self::write_opt(dev.queue_path.join("iostats"), "0");
                Self::write_opt(dev.queue_path.join("nomerges"), "0");
                if let Some(wbt) = params.wbt_lat_usec {
                    Self::write_opt(dev.queue_path.join("wbt_lat_usec"), wbt.to_string());
                }
                if let Some(exp) = params.read_expire_ms {
                    Self::write_opt(dev.queue_path.join("iosched/read_expire"), exp.to_string());
                }
                if let Some(starv) = params.writes_starved {
                    Self::write_opt(dev.queue_path.join("iosched/writes_starved"), starv.to_string());
                }
                if let Some(low_lat) = params.bfq_low_latency {
                    Self::write_opt(dev.queue_path.join("iosched/low_latency"), low_lat.to_string());
                }

                let sched_candidates: &[&str] = match effective_type {
                    crate::hardware::capability::BlockDeviceType::Emmc => &["bfq", "mq-deadline", "deadline", "none"],
                    crate::hardware::capability::BlockDeviceType::Ufs
                    | crate::hardware::capability::BlockDeviceType::Nvme => &["mq-deadline", "deadline", "bfq", "kyber", "none", "noop"],
                    crate::hardware::capability::BlockDeviceType::Sd => &["mq-deadline", "deadline", "bfq", "noop", "none"],
                    _ => &["mq-deadline", "deadline", "none"],
                };
                if let Some(chosen) = sched_candidates.iter().find(|&&c| dev.available_schedulers.iter().any(|s| s == c)) {
                    Self::write_opt(dev.queue_path.join("scheduler"), chosen);
                }
            }
        }

        // Virtual Memory & VFS Cache Shield (Hardware-Tiered: Protects 4GB RAM & eMMC FBE from reclaim thrashing)
        let mountx = crate::subsystems::IoController::detect_mountx();
        let mem = HardwareCapability::resolve_memory();
        let primary_type = crate::hardware::storage::StoragePolicy::primary_storage(&storage).map(|(_, t)| t);
        let has_emmc = storage.devices.iter().any(|d| d.device_type == crate::hardware::capability::BlockDeviceType::Emmc);
        let has_ufs = storage.devices.iter().any(|d| d.device_type == crate::hardware::capability::BlockDeviceType::Ufs);
        let tier = crate::hardware::DeviceTier::classify(mem.total_ram_mb, primary_type, has_emmc && !has_ufs, mountx.is_detected);
        let vm = tier.vm_profile_targets("performance");

        Self::write_opt("/proc/sys/vm/vfs_cache_pressure", vm.vfs_cache_pressure.to_string());
        Self::write_opt("/proc/sys/vm/swappiness", vm.swappiness.to_string());
        Self::write_opt("/proc/sys/vm/watermark_boost_factor", "0");
        Self::write_opt("/proc/sys/vm/min_free_kbytes", vm.min_free_kbytes.to_string());
        Self::write_opt("/proc/sys/vm/watermark_scale_factor", vm.watermark_scale_factor.to_string());
        Self::write_opt("/proc/sys/vm/extra_free_kbytes", vm.extra_free_kbytes.to_string());
        Self::write_opt("/proc/sys/vm/dirty_writeback_centisecs", vm.dirty_writeback_cs.to_string());
        Self::write_opt("/proc/sys/vm/dirty_expire_centisecs", vm.dirty_expire_cs.to_string());
        Self::write_opt("/proc/sys/vm/dirty_background_ratio", vm.dirty_background_ratio.to_string());
        Self::write_opt("/proc/sys/vm/dirty_ratio", vm.dirty_ratio.to_string());
        Self::write_opt("/proc/sys/vm/page-cluster", "0");
        Self::write_opt("/proc/sys/vm/stat_interval", "20");

        if mountx.is_detected {
            if let Some(sd_dev) = storage.devices.iter().find(|d| d.device_type == crate::hardware::capability::BlockDeviceType::Sd) {
                Self::write_opt(sd_dev.queue_path.join("nr_requests"), "512");
                Self::write_opt(sd_dev.queue_path.join("iosched/writes_starved"), "8");
                Self::write_opt(sd_dev.queue_path.join("read_ahead_kb"), "1024");
                Self::write_opt(sd_dev.queue_path.join("scheduler"), "deadline");
            }
            let _ = std::fs::write("/dev/lynx_mountx_active", "1\n");
        } else {
            let _ = std::fs::remove_file("/dev/lynx_mountx_active");
        }

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

        // 10. Dynamic Low-Latency Network & Wi-Fi Silicon Engagement
        crate::subsystems::NetworkController::apply_game();

        // 11. Process Optimization if PID provided
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
        Self::write_opt("/sys/devices/system/cpu/cpufreq/schedutil/up_rate_limit_us", "1000");
        Self::write_opt("/sys/devices/system/cpu/cpufreq/schedutil/down_rate_limit_us", "10000");

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

        // 6. Universal GPU Clock Restore: preserve saved user GPU lock if configured, else reset to Auto DVFS
        let saved_locked = ConfigStore::get("gpu.is_locked")
            .map(|v| v == "true" || v == "1")
            .unwrap_or(false);
        let saved_freq = ConfigStore::get("gpu.locked_freq_mhz")
            .and_then(|s| s.parse::<u64>().ok())
            .unwrap_or(0);
        if saved_locked && saved_freq > 0 {
            crate::subsystems::GpuController::apply_freq_ephemeral(saved_freq, Some(saved_freq));
        } else {
            crate::subsystems::GpuController::apply_freq_ephemeral(0, Some(0));
        }
        Self::restore_offloaded_irqs();

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
        Self::write_opt(MtkHardware::PERFMGR_TA_BOOST, "10");
        Self::write_opt(MtkHardware::PERFMGR_BIG_TASK_ROTATION, "1");

        // EAS Schedtune Top-App Restore to Balanced Targets & Unlock Shield
        Self::write_opt("/dev/stune/top-app/schedtune.boost", "18");
        Self::write_opt("/dev/stune/top-app/schedtune.prefer_idle", "1");
        Self::write_opt("/dev/stune/top-app/schedtune.prefer_high_cap", "0");
        crate::subsystems::SchedulerGuardian::enforce_permission_shield(false);

        for entry in MtkHardware::mali_sysfs_paths() {
            Self::write_opt(entry.join("dvfs_period"), "20");
            Self::write_opt(entry.join("power_policy"), "coarse_demand");
            Self::write_opt(entry.join("js_scheduling_period"), "100");
        }
        Self::write_opt("/sys/kernel/ged/hal/dvfs_margin_value", "0");
        Self::write_opt("/sys/kernel/ged/hal/timer_base_dvfs_margin", "0");
        Self::write_opt("/sys/kernel/ged/hal/loading_base_dvfs_step", "1");
        Self::write_opt(MtkHardware::LYNX_THERMAL_SILENCER, "0");
        MtkHardware::restore_orig_imax();
        Self::write_opt(MtkHardware::CPUFREQ_IMAX_THERMAL_PROTECT, "1");
        MtkHardware::restore_orig_gpu_max_khz();
        MtkHardware::boost_ddr_peak(false);
        Self::write_opt(MtkHardware::FPSGO_ULTRA_RESCUE, "0");

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

        // Restore Virtual Memory & VFS Cache according to device hardware tier
        let tier = crate::hardware::DeviceTier::detect_live();
        let vm = tier.vm_profile_targets("balance");
        Self::write_opt("/proc/sys/vm/vfs_cache_pressure", vm.vfs_cache_pressure.to_string());
        Self::write_opt("/proc/sys/vm/min_free_kbytes", vm.min_free_kbytes.to_string());
        Self::write_opt("/proc/sys/vm/extra_free_kbytes", vm.extra_free_kbytes.to_string());
        Self::write_opt("/proc/sys/vm/watermark_scale_factor", vm.watermark_scale_factor.to_string());
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
        Self::write_opt(QcomHardware::KGSL_THERMAL_PWRLEVEL, "0");
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

        // 9. Restore Dynamic Balanced Network & Standard Wi-Fi Power Save
        crate::subsystems::NetworkController::apply_balance();

        "Game Mode disengaged (Restored Balanced EAS, Network, and Audio profiles).".to_string()
    }

    /// Optimizes thread scheduling, CPU affinity, and I/O priority for a specific process ID
    /// dynamically across any SoC cluster layout and any game engine without hardcoding.
    pub fn optimize_pid(pid: u32) {
        let pid_str = pid.to_string();

        // Process level priority & I/O
        Self::set_tid_nice(pid, -20);
        let _ = Command::new("ionice").args(&["-c", "1", "-n", "0", "-p", &pid_str]).output();

        // Put process into top-app cpuset & notify GED top app PID
        Self::write_opt("/dev/cpuset/top-app/cgroup.procs", &pid_str);
        Self::write_opt("/sys/module/ged/parameters/gx_top_app_pid", &pid_str);

        // Dynamic topology discovery (Zero hardcoding of core counts or SoC IDs)
        let cap = HardwareCapability::resolve();
        let topo = TopologyMasks::from_policies(&cap.cpu.policies, cap.cpu.total_cores);

        // Read process short binary name for unknown-engine fallback matching
        let proc_comm = fs::read_to_string(format!("/proc/{}/comm", pid))
            .map(|s| s.trim().to_ascii_lowercase())
            .unwrap_or_default();

        // Collect all live threads with `(tid, comm, live_ticks = utime + stime)` via pure Rust std
        let task_dir = format!("/proc/{}/task", pid);
        let mut threads: Vec<ThreadInfo> = Vec::new();
        if let Ok(entries) = fs::read_dir(&task_dir) {
            for entry in entries.flatten() {
                let Ok(tid) = entry.file_name().to_string_lossy().parse::<u32>() else { continue };
                let comm = fs::read_to_string(entry.path().join("comm"))
                    .map(|s| s.trim().to_string())
                    .unwrap_or_default();
                if comm.is_empty() {
                    continue;
                }
                let ticks = fs::read_to_string(entry.path().join("stat"))
                    .ok()
                    .map(|s| Self::parse_stat_ticks(&s))
                    .unwrap_or(0);
                let role = classify_thread_role(&comm, ticks, &proc_comm);
                threads.push(ThreadInfo { tid, ticks, role });
            }
        }

        // Order critical threads cleanly: RenderBackend -> MainLoop -> HeavyRuntime -> ActiveUnknown
        // Within same role tier, highest live CPU ticks wins; smallest TID breaks zero-tick ties at launch.
        threads.sort_by(|a, b| {
            b.role
                .tier()
                .cmp(&a.role.tier())
                .then_with(|| b.ticks.cmp(&a.ticks))
                .then_with(|| a.tid.cmp(&b.tid))
        });

        // Allocate exclusive prime cores when the SoC genuinely has separate non-prime cores to absorb workers
        let mut isolated_cores: Vec<usize> = Vec::new();
        if topo.has_asymmetric_worker_pool() {
            // Reserve up to 2 highest prime cores (or n-1 if prime cluster has >= 3 cores) for #1 Render + #2 Main
            let max_exclusive = if topo.prime_cores.len() == 1 {
                1
            } else if topo.prime_cores.len() == 2 {
                2
            } else {
                2.min(topo.prime_cores.len().saturating_sub(1))
            };

            // Reverse order puts highest physical core ID first (e.g. Core 7 then Core 6 on 6+2)
            for &core_id in topo.prime_cores.iter().rev().take(max_exclusive) {
                isolated_cores.push(core_id);
            }
        }

        let reserved_mask: u64 = isolated_cores.iter().fold(0u64, |acc, &c| if c < 64 { acc | (1u64 << c) } else { acc });
        let worker_mask = if reserved_mask != 0 && (topo.all_mask & !reserved_mask) != 0 {
            topo.all_mask & !reserved_mask
        } else {
            topo.all_mask
        };

        let mut next_isolated_idx = 0usize;
        let mut assigned_primary_render = false;
        let mut assigned_primary_main = false;

        for t in &threads {
            let mut pinned_exclusive = false;
            if next_isolated_idx < isolated_cores.len() && t.role.is_exclusive_candidate() {
                let take = match t.role {
                    ThreadRole::RenderBackend if !assigned_primary_render => {
                        assigned_primary_render = true;
                        true
                    }
                    ThreadRole::MainGameLoop if !assigned_primary_main => {
                        assigned_primary_main = true;
                        true
                    }
                    ThreadRole::ActiveGeneric if next_isolated_idx == 0 && !assigned_primary_render && !assigned_primary_main => true,
                    ThreadRole::ActiveGeneric if isolated_cores.len() > 1 && next_isolated_idx < isolated_cores.len() && t.ticks > 0 => true,
                    _ => false,
                };
                if take {
                    let core = isolated_cores[next_isolated_idx];
                    next_isolated_idx += 1;
                    let core_mask = if core < 64 { 1u64 << core } else { topo.all_mask };
                    Self::set_thread_affinity_and_nice(t.tid, core_mask, -20);
                    pinned_exclusive = true;
                }
            }

            if !pinned_exclusive {
                let (mask, nice) = match t.role {
                    ThreadRole::RenderBackend | ThreadRole::MainGameLoop | ThreadRole::EngineHeavyWorker => (worker_mask, -16),
                    ThreadRole::GpuDriverWorker => (worker_mask, -12),
                    ThreadRole::AudioRealtime => (topo.little_mask.max(1), -10),
                    ThreadRole::ActiveGeneric => (worker_mask, 0),
                    ThreadRole::BackgroundHelper => (worker_mask, 0),
                };
                Self::set_thread_affinity_and_nice(t.tid, mask, nice);
            }
        }

        // Keep background VM reclaimers confined to energy-efficient cores
        Self::offload_kswapd_kcompactd(topo.little_mask);

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

    fn parse_stat_ticks(stat_line: &str) -> u64 {
        let Some(rparen) = stat_line.rfind(')') else { return 0 };
        let after = &stat_line[rparen + 1..];
        let mut fields = after.split_whitespace();
        // After ')': field 0 is state, indices 11 & 12 are utime ($14) and stime ($15)
        let utime = fields.nth(11).and_then(|v| v.parse::<u64>().ok()).unwrap_or(0);
        let stime = fields.next().and_then(|v| v.parse::<u64>().ok()).unwrap_or(0);
        utime.saturating_add(stime)
    }

    fn offload_kswapd_kcompactd(little_mask: u64) {
        if little_mask == 0 {
            return;
        }
        if let Ok(entries) = fs::read_dir("/proc") {
            for entry in entries.flatten() {
                let Ok(pid) = entry.file_name().to_string_lossy().parse::<u32>() else { continue };
                if let Ok(comm) = fs::read_to_string(entry.path().join("comm")) {
                    let c = comm.trim();
                    if c.starts_with("kswapd") || c.starts_with("kcompactd") {
                        Self::set_thread_affinity(pid, little_mask);
                    }
                }
            }
        }
    }

    fn offload_heavy_irqs(topo: &TopologyMasks) {
        let worker_mask = if !topo.prime_cores.is_empty() && topo.all_mask != 0 {
            let prime_mask: u64 = topo.prime_cores.iter().fold(0u64, |acc, &c| if c < 64 { acc | (1u64 << c) } else { acc });
            let non_prime = topo.all_mask & !prime_mask;
            if non_prime != 0 { non_prime } else { topo.little_mask }
        } else {
            return;
        };
        if worker_mask == 0 || worker_mask == topo.all_mask {
            return;
        }
        let Ok(interrupts) = fs::read_to_string("/proc/interrupts") else { return };
        let mask_hex = format!("{:x}", worker_mask);
        let save_file = Path::new("/dev/lynx_orig_irq_aff");
        let should_save = !save_file.exists();
        let mut saved_lines: Vec<String> = Vec::new();
        for line in interrupts.lines() {
            let lower = line.to_ascii_lowercase();
            let is_heavy_irq = lower.contains("mali")
                || lower.contains("kgsl")
                || lower.contains("adreno")
                || lower.contains("ufshcd")
                || lower.contains("msdc")
                || lower.contains("mmc")
                || lower.contains("disp")
                || lower.contains("dsi")
                || lower.contains("mdss")
                || lower.contains("cmdq");
            if !is_heavy_irq {
                continue;
            }
            let Some(colon_pos) = line.find(':') else { continue };
            let irq_str = line[..colon_pos].trim();
            if irq_str.is_empty() || !irq_str.chars().all(|c| c.is_ascii_digit()) {
                continue;
            }
            let aff_path = format!("/proc/irq/{}/smp_affinity", irq_str);
            if Path::new(&aff_path).exists() {
                if should_save {
                    if let Ok(orig) = fs::read_to_string(&aff_path) {
                        saved_lines.push(format!("{}:{}", aff_path, orig.trim()));
                    }
                }
                let _ = fs::write(&aff_path, &mask_hex);
            }
        }
        if should_save && !saved_lines.is_empty() {
            let _ = fs::write(save_file, saved_lines.join("\n"));
        }
    }

    fn restore_offloaded_irqs() {
        let save_file = Path::new("/dev/lynx_orig_irq_aff");
        if let Ok(content) = fs::read_to_string(save_file) {
            for line in content.lines() {
                if let Some((path, orig)) = line.split_once(':') {
                    if !orig.is_empty() {
                        let _ = fs::write(path, orig);
                    }
                }
            }
            let _ = fs::remove_file(save_file);
        }
    }

    #[cfg(any(target_os = "android", target_os = "linux"))]
    fn set_tid_nice(tid: u32, nice: i32) {
        extern "C" {
            fn setpriority(which: i32, who: u32, prio: i32) -> i32;
        }
        unsafe {
            let _ = setpriority(0, tid, nice);
        }
    }

    #[cfg(not(any(target_os = "android", target_os = "linux")))]
    fn set_tid_nice(_tid: u32, _nice: i32) {}

    #[cfg(any(target_os = "android", target_os = "linux"))]
    fn set_thread_affinity(tid: u32, mask: u64) {
        if mask == 0 {
            return;
        }
        extern "C" {
            fn syscall(num: i64, ...) -> i64;
        }
        #[cfg(target_arch = "aarch64")]
        const SYS_SCHED_SETAFFINITY: i64 = 122;
        #[cfg(target_arch = "arm")]
        const SYS_SCHED_SETAFFINITY: i64 = 241;
        #[cfg(target_arch = "x86_64")]
        const SYS_SCHED_SETAFFINITY: i64 = 203;
        #[cfg(target_arch = "x86")]
        const SYS_SCHED_SETAFFINITY: i64 = 241;

        let mut cpuset = [0u64; 16]; // 128-byte Linux cpu_set_t
        cpuset[0] = mask;
        unsafe {
            let _ = syscall(
                SYS_SCHED_SETAFFINITY,
                tid as i32 as i64,
                std::mem::size_of_val(&cpuset) as i64,
                cpuset.as_ptr(),
            );
        }
    }

    #[cfg(not(any(target_os = "android", target_os = "linux")))]
    fn set_thread_affinity(_tid: u32, _mask: u64) {}

    fn set_thread_affinity_and_nice(tid: u32, mask: u64, nice: i32) {
        Self::set_tid_nice(tid, nice);
        Self::set_thread_affinity(tid, mask);
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

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum ThreadRole {
    RenderBackend,
    MainGameLoop,
    EngineHeavyWorker,
    ActiveGeneric,
    GpuDriverWorker,
    AudioRealtime,
    BackgroundHelper,
}

impl ThreadRole {
    pub(crate) fn tier(self) -> u8 {
        match self {
            Self::RenderBackend => 4,
            Self::MainGameLoop => 3,
            Self::EngineHeavyWorker => 2,
            Self::ActiveGeneric => 1,
            Self::GpuDriverWorker | Self::AudioRealtime | Self::BackgroundHelper => 0,
        }
    }

    pub(crate) fn is_exclusive_candidate(self) -> bool {
        matches!(self, Self::RenderBackend | Self::MainGameLoop | Self::ActiveGeneric)
    }
}

#[derive(Debug, Clone)]
struct ThreadInfo {
    tid: u32,
    ticks: u64,
    role: ThreadRole,
}

#[derive(Debug, Clone)]
pub(crate) struct TopologyMasks {
    pub(crate) all_mask: u64,
    pub(crate) little_mask: u64,
    pub(crate) prime_cores: Vec<usize>,
}

impl TopologyMasks {
    pub(crate) fn from_policies(policies: &[crate::hardware::capability::PolicyCapability], total_cores: usize) -> Self {
        let cores = total_cores.max(1);
        let fallback_all: u64 = if cores >= 64 { !0 } else { (1u64 << cores) - 1 };

        if policies.is_empty() {
            return Self {
                all_mask: fallback_all,
                little_mask: fallback_all,
                prime_cores: (0..cores).collect(),
            };
        }

        let mut all_mask: u64 = 0;
        let min_policy_freq = policies.iter().map(|p| p.max_freq).min().unwrap_or(0);
        let max_policy_freq = policies.iter().map(|p| p.max_freq).max().unwrap_or(0);

        let mut little_mask: u64 = 0;
        let mut prime_cores: Vec<usize> = Vec::new();

        for p in policies {
            let parsed = parse_cpu_list(&p.related_cpus);
            let mut p_mask = 0u64;
            for &id in &parsed {
                if id < 64 {
                    all_mask |= 1u64 << id;
                    p_mask |= 1u64 << id;
                }
            }
            if p.max_freq == min_policy_freq {
                little_mask |= p_mask;
            }
            if p.max_freq == max_policy_freq {
                for id in parsed {
                    if id < 64 && !prime_cores.contains(&id) {
                        prime_cores.push(id);
                    }
                }
            }
        }
        prime_cores.sort_unstable();

        if all_mask == 0 {
            all_mask = fallback_all;
        }
        if little_mask == 0 {
            little_mask = all_mask;
        }

        Self {
            all_mask,
            little_mask,
            prime_cores,
        }
    }

    pub(crate) fn has_asymmetric_worker_pool(&self) -> bool {
        if self.prime_cores.is_empty() {
            return false;
        }
        let prime_mask: u64 = self
            .prime_cores
            .iter()
            .fold(0u64, |acc, &c| if c < 64 { acc | (1u64 << c) } else { acc });
        let non_prime = self.all_mask & !prime_mask;
        non_prime != 0
    }
}

pub(crate) fn parse_cpu_list(spec: &str) -> Vec<usize> {
    let mut out = Vec::new();
    for token in spec.split(|c: char| c.is_whitespace() || c == ',') {
        let tok = token.trim();
        if tok.is_empty() {
            continue;
        }
        if let Some((start_s, end_s)) = tok.split_once('-') {
            if let (Ok(s), Ok(e)) = (start_s.trim().parse::<usize>(), end_s.trim().parse::<usize>()) {
                let (lo, hi) = if s <= e { (s, e) } else { (e, s) };
                for id in lo..=hi {
                    if id < 64 && !out.contains(&id) {
                        out.push(id);
                    }
                }
            }
        } else if let Ok(id) = tok.parse::<usize>() {
            if id < 64 && !out.contains(&id) {
                out.push(id);
            }
        }
    }
    out
}

pub(crate) fn classify_thread_role(comm: &str, ticks: u64, proc_comm: &str) -> ThreadRole {
    let lower = comm.trim().to_ascii_lowercase();

    // Driver / kernel-helper / audio / telemetry / async loader threads MUST stay off reserved exclusive render cores
    if lower.starts_with("mali-")
        || lower.starts_with("kgsl")
        || lower.contains("adreno")
        || lower.contains("utility-wo")
    {
        return ThreadRole::GpuDriverWorker;
    }
    if lower.contains("audio") || lower.contains("sound") || lower.contains("fmod") || lower.starts_with("ak::") {
        return ThreadRole::AudioRealtime;
    }
    if lower.contains("worker")
        || lower.starts_with("job")
        || lower.contains("network")
        || lower.contains("asyncread")
        || lower.contains("loadthread")
        || lower.contains("deviceinfo")
        || lower.contains("choreograph")
        || lower.starts_with("binder:")
        || lower.starts_with("thread-")
        || lower.contains("finalizer")
        || lower.contains("jit")
        || lower.contains("heap")
        || lower.contains("gcd")
    {
        return ThreadRole::BackgroundHelper;
    }

    // Engine multi-render / secondary heavy native threads run strongly on shared worker pool
    if lower.contains("multirend") || lower.contains("nativethread") {
        return ThreadRole::EngineHeavyWorker;
    }

    // Primary render backend (Unity, Unreal RHI/RenderThread, Vulkan/GLES render)
    if lower.contains("unitygfx")
        || lower.contains("rhithread")
        || lower.contains("renderthread")
        || lower.contains("vkworker")
        || lower.contains("glworker")
    {
        return ThreadRole::RenderBackend;
    }

    // Primary game/main loop thread
    if lower.contains("unitymain")
        || lower.contains("gamethread")
        || lower == "mainthread"
        || (!proc_comm.is_empty() && lower == proc_comm)
    {
        return ThreadRole::MainGameLoop;
    }

    if ticks > 0 {
        ThreadRole::ActiveGeneric
    } else {
        ThreadRole::BackgroundHelper
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::hardware::capability::PolicyCapability;
    use std::path::PathBuf;

    fn mock_policy(index: usize, related_cpus: &str, max_freq: u64) -> PolicyCapability {
        PolicyCapability {
            index,
            mtk_cluster_index: index,
            role: String::new(),
            related_cpus: related_cpus.to_string(),
            path: PathBuf::new(),
            gov_tunable_dirs: vec![],
            min_freq: 300_000,
            max_freq,
            available_freqs: vec![],
            available_governors: vec![],
            current_governor: String::new(),
            current_min_freq: 0,
            current_max_freq: max_freq,
            is_locked: false,
        }
    }

    #[test]
    fn dynamic_topology_supports_heterogeneous_and_symmetric_socs() {
        // 6+2 SoC (e.g. Helio G96)
        let topo_6_2 = TopologyMasks::from_policies(
            &[mock_policy(0, "0 1 2 3 4 5", 2_000_000), mock_policy(6, "6 7", 2_050_000)],
            8,
        );
        assert_eq!(topo_6_2.prime_cores, vec![6, 7]);
        assert_eq!(topo_6_2.little_mask, 0b0011_1111);
        assert!(topo_6_2.has_asymmetric_worker_pool());

        // 1+3+4 SoC (e.g. Snapdragon flagship)
        let topo_1_3_4 = TopologyMasks::from_policies(
            &[
                mock_policy(0, "0-3", 1_800_000),
                mock_policy(4, "4-6", 2_400_000),
                mock_policy(7, "7", 3_000_000),
            ],
            8,
        );
        assert_eq!(topo_1_3_4.prime_cores, vec![7]);
        assert_eq!(topo_1_3_4.little_mask, 0b0000_1111);
        assert!(topo_1_3_4.has_asymmetric_worker_pool());

        // Symmetric quad-core (must not isolate or split worker pool)
        let topo_sym = TopologyMasks::from_policies(&[mock_policy(0, "0 1 2 3", 1_800_000)], 4);
        assert_eq!(topo_sym.prime_cores, vec![0, 1, 2, 3]);
        assert!(!topo_sym.has_asymmetric_worker_pool());
    }

    #[test]
    fn thread_classifier_handles_multiple_engines_and_unknown_games() {
        assert_eq!(classify_thread_role("UnityGfxDeviceW", 300, "game"), ThreadRole::RenderBackend);
        assert_eq!(classify_thread_role("RHIThread", 250, "ue4game"), ThreadRole::RenderBackend);
        assert_eq!(classify_thread_role("UnityMain", 210, "game"), ThreadRole::MainGameLoop);
        assert_eq!(classify_thread_role("GameThread", 210, "ue4game"), ThreadRole::MainGameLoop);
        assert_eq!(classify_thread_role("UnityMultiRende", 150, "game"), ThreadRole::EngineHeavyWorker);
        assert_eq!(classify_thread_role("NativeThread", 145, "game"), ThreadRole::EngineHeavyWorker);
        assert_eq!(classify_thread_role("mali-cmar-backe", 110, "game"), ThreadRole::GpuDriverWorker);
        assert_eq!(classify_thread_role("Thread-108", 20, "game"), ThreadRole::BackgroundHelper);
        assert_eq!(classify_thread_role("MIHOYO_NETWORK", 25, "game"), ThreadRole::BackgroundHelper);
        assert_eq!(classify_thread_role("CustomRenderX", 190, "custom"), ThreadRole::ActiveGeneric);
    }

    #[cfg(any(target_os = "android", target_os = "linux"))]
    #[test]
    fn thread_priority_and_affinity_syscalls_take_effect_on_target_pid() {
        let Ok(mut child) = Command::new("sleep").arg("2").spawn() else {
            return;
        };
        let pid = child.id();
        GameController::set_tid_nice(pid, -12);
        GameController::set_thread_affinity(pid, 0b0000_0001);

        let stat_ok = fs::read_to_string(format!("/proc/{}/stat", pid))
            .ok()
            .and_then(|s| s.rfind(')').map(|pos| s[pos + 1..].split_whitespace().nth(16).map(|n| n.to_string())))
            .flatten();
        let cpus_ok = fs::read_to_string(format!("/proc/{}/status", pid))
            .ok()
            .and_then(|s| {
                s.lines()
                    .find(|l| l.starts_with("Cpus_allowed:"))
                    .map(|l| l.trim_start_matches("Cpus_allowed:").trim().to_string())
            });

        let is_root = fs::read_to_string("/proc/self/status")
            .map(|s| s.lines().any(|l| l.starts_with("Uid:") && l.split_whitespace().nth(1) == Some("0")))
            .unwrap_or(false);

        let _ = child.kill();
        let _ = child.wait();

        if is_root {
            assert_eq!(stat_ok.as_deref(), Some("-12"));
        }
        if let Some(mask) = cpus_ok {
            let parsed = u64::from_str_radix(mask.trim_start_matches('0'), 16).unwrap_or(1);
            assert_eq!(parsed, 1);
        }
    }
}
