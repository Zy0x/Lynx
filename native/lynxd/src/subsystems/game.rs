use std::fs;
use std::path::Path;
use std::process::Command;

use crate::hardware::capability::HardwareCapability;
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

        // 5. Low-Latency Audio Pipeline
        let _ = Command::new("setprop").args(&["af.fast_track_multiplier", "1"]).output();
        let _ = Command::new("setprop").args(&["aaudio.mmap_policy", "2"]).output();
        let _ = Command::new("setprop").args(&["aaudio.mmap_exclusive_policy", "2"]).output();

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

        // 7. MediaTek GED & FPSGO Hooks
        Self::write_opt("/sys/module/ged/parameters/gx_game_mode", "1");
        Self::write_opt("/sys/module/ged/parameters/gx_boost_on", "1");
        Self::write_opt("/sys/module/ged/parameters/gx_force_cpu_boost", "1");
        Self::write_opt("/sys/module/ged/parameters/target_t_cpu_remained", "4166666");
        Self::write_opt("/sys/kernel/fpsgo/fbt/switch_idleprefer", "0");
        Self::write_opt("/sys/kernel/fpsgo/fbt/enable_switch_down_throttle", "0");
        Self::write_opt("/sys/kernel/fpsgo/fbt/ultra_rescue", "1");
        Self::write_opt("/sys/kernel/fpsgo/common/gpu_block_boost", "1 1 -1");
        Self::write_opt("/sys/kernel/fpsgo/common/force_onoff", "1");

        // 8. Qualcomm KGSL Boost
        Self::write_opt("/sys/class/kgsl/kgsl-3d0/devfreq/adreno_boost", "3");

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

        // 4. Reset Audio
        let _ = Command::new("setprop").args(&["aaudio.mmap_policy", "1"]).output();
        let _ = Command::new("setprop").args(&["aaudio.mmap_exclusive_policy", "1"]).output();
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

        // 6. Reset MediaTek GED Hooks
        Self::write_opt("/sys/module/ged/parameters/gx_top_app_pid", "0");
        Self::write_opt("/sys/module/ged/parameters/gx_game_mode", "0");
        Self::write_opt("/sys/module/ged/parameters/gx_boost_on", "0");

        // 7. Remove Active Game Flag
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
