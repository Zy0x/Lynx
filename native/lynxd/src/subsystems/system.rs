use std::path::Path;
use std::process::Command;

use crate::sysfs::{SysfsWriter, WriteMode};
use crate::utils::Logger;
use crate::subsystems::tui::ui;

pub struct SystemController;

impl SystemController {
    fn write_opt(path: impl AsRef<Path>, val: impl AsRef<str>) {
        let p = path.as_ref();
        if p.exists() {
            let _ = SysfsWriter::write(p, val, WriteMode::ForcePermission);
        }
    }

    /// Applies universal kernel core optimizations:
    /// - Real-time scheduler limits & timer migration suppression
    /// - RCU expedited synchronization
    /// - Telemetry, kernel tracing, and logger daemon suppression
    /// - Printk & kernel panic overhead reduction
    pub fn apply() -> String {
        Logger::info(ui("Menerapkan optimasi inti sistem dan kernel universal...", "Applying universal system and kernel core optimizations..."));
        let mut count = 0usize;

        // 1. Kernel Scheduler Core Tunings
        let sched_tunables = [
            ("/proc/sys/kernel/sched_tunable_scaling", "0"),
            ("/proc/sys/kernel/sched_child_runs_first", "0"),
            ("/proc/sys/kernel/timer_migration", "0"),
            ("/proc/sys/kernel/sched_autogroup_enabled", "1"),
            ("/proc/sys/kernel/sched_min_task_util_for_boost", "15"),
            ("/proc/sys/kernel/sched_min_task_util_for_colocation", "0"),
            ("/proc/sys/kernel/sched_rt_runtime_us", "950000"),
            ("/proc/sys/kernel/sched_rt_period_us", "1000000"),
            ("/proc/sys/kernel/sched_migration_cost_ns", "500000"),
        ];

        for (path, val) in sched_tunables {
            if Path::new(path).exists() {
                Self::write_opt(path, val);
                count += 1;
            }
        }

        // 2. RCU Expedited Synchronization
        let rcu_tunables = [
            ("/sys/kernel/rcu_expedited", "1"),
            ("/sys/module/rcupdate/parameters/rcu_cpu_stall_suppress", "1"),
        ];

        for (path, val) in rcu_tunables {
            if Path::new(path).exists() {
                Self::write_opt(path, val);
                count += 1;
            }
        }

        // 3. Tracing & Profiling Suppression (Saves CPU cycles & RAM)
        let tracing_nodes = [
            "/proc/sys/kernel/tracing/tracing_on",
            "/sys/kernel/debug/tracing/tracing_on",
            "/sys/kernel/tracing/tracing_on",
        ];

        for path in tracing_nodes {
            if Path::new(path).exists() {
                Self::write_opt(path, "0");
                count += 1;
            }
        }

        // Android settings for perfetto profiler and watchdog
        let _ = Command::new("settings")
            .args(["put", "global", "debug.perfetto.profiler.enabled", "0"])
            .output();
        let _ = Command::new("settings")
            .args(["put", "global", "watchdog_enabled", "0"])
            .output();

        // 4. Printk & Kernel Panic Overhead Reduction
        let panic_nodes = [
            ("/proc/sys/kernel/printk", "0 0 0 0"),
            ("/proc/sys/kernel/panic_on_oops", "0"),
            ("/proc/sys/kernel/panic", "0"),
        ];

        for (path, val) in panic_nodes {
            if Path::new(path).exists() {
                Self::write_opt(path, val);
                count += 1;
            }
        }

        // 5. Universal Telemetry & Logger Daemon Suppression
        let daemons_to_stop = [
            "logcatd", "tcpdump", "statsd", "traced", "idd-logreader",
            "idd-logreadermain", "dumpstate", "aplogd", "vendor_tcpdump", "vendor.tcpdump",
        ];
        for d in daemons_to_stop {
            let _ = Command::new("stop").arg(d).output();
        }

        // 6. Platform-Specific Kernel Driver Optimizations (MediaTek & Qualcomm)
        let (vendor, _) = crate::hardware::detector::ScoreBoard::evaluate();
        match vendor {
            crate::hardware::detector::Vendor::MediaTek => {
                Self::write_opt("/sys/kernel/ccci/debug", "0");
                Self::write_opt("/sys/module/ged/parameters/ged_log_perf_trace_enable", "0");
                Self::write_opt("/sys/module/ged/parameters/ged_log_trace_enable", "0");
                Self::write_opt("/sys/module/ged/parameters/ged_monitor_3D_fence_debug", "0");
                Self::write_opt("/sys/module/ged/parameters/ged_monitor_3D_fence_systrace", "0");
                Self::write_opt("/proc/ged/hal/ged_kpi", "0");
                Self::write_opt("/proc/perfmgr/syslimiter/syslimiter_force_disable", "1");
                Self::write_opt("/proc/perfmgr/syslimiter/syslimiter_fps_60", "0");
                Self::write_opt("/proc/perfmgr/syslimiter/syslimiter_fps_90", "0");
                Self::write_opt("/proc/perfmgr/syslimiter/syslimiter_fps_120", "0");
                Self::write_opt("/proc/perfmgr/syslimiter/syslimiter_fps_144", "0");
                Self::write_opt("/sys/devices/system/cpu/perf/gpu_pmu_enable", "1");
                Self::write_opt("/sys/devices/system/cpu/perf/fuel_gauge_enable", "1");
                Self::write_opt("/sys/devices/system/cpu/perf/enable", "1");
                Self::write_opt("/sys/devices/system/cpu/perf/charger_enable", "1");
                count += 15;
            }
            crate::hardware::detector::Vendor::Qualcomm => {
                let kgsl = "/sys/class/kgsl/kgsl-3d0";
                Self::write_opt(format!("{}/snapshot/snapshot_crashdumper", kgsl), "0");
                Self::write_opt(format!("{}/snapshot/force_panic", kgsl), "0");
                Self::write_opt(format!("{}/dispatch/fault_throttle_burst", kgsl), "0");
                for dbg in ["/sys/kernel/debug/kgsl/kgsl-3d0", "/d/kgsl/kgsl-3d0"] {
                    Self::write_opt(format!("{}/log_level_cmd", dbg), "0");
                    Self::write_opt(format!("{}/log_level_ctxt", dbg), "0");
                    Self::write_opt(format!("{}/log_level_drv", dbg), "0");
                    Self::write_opt(format!("{}/log_level_mem", dbg), "0");
                    Self::write_opt(format!("{}/log_level_pwr", dbg), "0");
                }
                Self::write_opt("/sys/module/cpu_boost/parameters/sched_boost_on_input", "0");
                Self::write_opt("/sys/module/cpu_boost/parameters/input_boost_ms", "40");
                Self::write_opt("/proc/sys/kernel/sched_walt_io_is_busy", "1");
                Self::write_opt("/proc/sys/kernel/sched_walt_cross_window_migration", "1");
                Self::write_opt("/d/dri/0/debug/core_perf/perf_mode", "1");
                Self::write_opt("/sys/module/subsystem_restart/parameters/enable_ramdumps", "0");
                Self::write_opt("/sys/module/subsystem_restart/parameters/enable_mini_ramdumps", "0");
                let _ = Command::new("stop").arg("cnss_diag").output();
                let _ = Command::new("stop").arg("vendor.cnss_diag").output();
                count += 20;
            }
            crate::hardware::detector::Vendor::Generic => {}
        }
        Self::write_opt("/sys/module/workqueue/parameters/power_efficient", "Y");
        Self::write_opt("/proc/sys/kernel/sched_autogroup_enabled", "1");

        format!("Universal system and kernel core optimizations applied ({} nodes written).", count)
    }

    /// Checks if kernel Boeffla / Generic Wakelock Blocker driver is present.
    pub fn boeffla_status_json() -> String {
        let boeffla_nodes = [
            "/sys/devices/virtual/misc/boeffla_wl_blocker/wakelock_blocker",
            "/sys/class/misc/boeffla_wl_blocker/wakelock_blocker",
            "/sys/module/wakeup/parameters/enable_sipper",
        ];
        let supported = boeffla_nodes.iter().any(|p| Path::new(p).exists());
        format!("{{\"supported\":{}}}", supported)
    }

    /// Queries device Doze status.
    pub fn doze_info_json() -> String {
        let is_idle = if let Ok(out) = Command::new("dumpsys").args(["deviceidle", "get", "deep"]).output() {
            let s = String::from_utf8_lossy(&out.stdout).to_uppercase();
            s.contains("IDLE") || s.contains("LIGHT_IDLE")
        } else {
            false
        };

        let state_str = if is_idle { "IDLE" } else { "ACTIVE" };
        format!("{{\"state\":\"{}\"}}", state_str)
    }

    /// Toggles aggressive Android Doze idle constants.
    pub fn set_doze(enabled: bool) -> String {
        if enabled {
            let _ = Command::new("dumpsys").args(["deviceidle", "enable"]).output();
            let _ = Command::new("settings").args([
                "put",
                "global",
                "device_idle_constants",
                "light_after_inactive_to=5000,light_pre_idle_to=30000,light_idle_to=1800000,light_max_idle_to=21600000,locating_to=10000,location_accuracy=500,inactive_to=30000,sensing_to=30000,motion_inactive_to=30000,idle_after_inactive_to=30000,idle_to=14400000,max_idle_to=43200000,quick_doze_delay_to=10000,min_time_to_alarm=600000",
            ]).output();
            "Aggressive Doze enabled (Deep sleep parameters expedited).".to_string()
        } else {
            let _ = Command::new("settings").args(["delete", "global", "device_idle_constants"]).output();
            "Aggressive Doze disabled (Restored OEM default idle timers).".to_string()
        }
    }

    /// Switches SELinux enforcement mode.
    pub fn set_selinux(enforcing: bool) -> String {
        let val = if enforcing { "1" } else { "0" };
        let _ = Command::new("setenforce").arg(val).output();
        format!("SELinux mode set to {}.", if enforcing { "Enforcing" } else { "Permissive" })
    }

    /// Sets kernel printk log level.
    pub fn set_printk(silent: bool) -> String {
        let val = if silent { "0 0 0 0" } else { "7 4 1 7" };
        Self::write_opt("/proc/sys/kernel/printk", val);
        format!("Kernel printk logging set to {}.", if silent { "silent (0 0 0 0)" } else { "verbose (7 4 1 7)" })
    }

    /// Reads active kernel wakeup sources and returns formatted lines: name|count|total_time_ms.
    pub fn top_wakelocks() -> String {
        let sources = [
            "/sys/kernel/debug/wakeup_sources",
            "/d/wakeup_sources",
            "/proc/wakelocks",
        ];

        for src in sources {
            if let Ok(content) = std::fs::read_to_string(src) {
                let mut locks: Vec<(String, u64, u64)> = Vec::new();
                for line in content.lines().skip(1) {
                    let parts: Vec<&str> = line.split_whitespace().collect();
                    if parts.len() >= 3 {
                        let name = parts[0].to_string();
                        let count = parts[1].parse::<u64>().unwrap_or(0);
                        let ms = if parts.len() >= 7 {
                            parts[6].parse::<u64>().unwrap_or(0)
                        } else {
                            0
                        };
                        if count > 0 && !name.is_empty() {
                            locks.push((name, count, ms));
                        }
                    }
                }

                if !locks.is_empty() {
                    locks.sort_by(|a, b| b.1.cmp(&a.1));
                    return locks.iter().take(20).map(|(n, c, ms)| format!("{}|{}|{}", n, c, ms)).collect::<Vec<_>>().join("\n");
                }
            }
        }

        // Fallback to dumpsys power
        if let Ok(out) = Command::new("dumpsys").args(["power"]).output() {
            let content = String::from_utf8_lossy(&out.stdout);
            let mut lines_out = Vec::new();
            let mut in_wl_section = false;
            for line in content.lines() {
                if line.contains("Wake Locks: size=") {
                    in_wl_section = true;
                    continue;
                }
                if in_wl_section {
                    if line.trim().is_empty() || line.starts_with("  Suspend Blockers:") {
                        break;
                    }
                    let trimmed = line.trim();
                    if let Some(tag) = trimmed.split('\'').nth(1) {
                        lines_out.push(format!("{}|1|1000", tag));
                    }
                }
            }
            if !lines_out.is_empty() {
                return lines_out.iter().take(15).cloned().collect::<Vec<_>>().join("\n");
            }
        }

        String::new()
    }

    /// Triggers an instantaneous, hardware-level Wakeup Boost (1.5 - 2.5s) to eliminate deep sleep wakeup stutter / freeze.
    pub fn wakeup_boost(duration_ms: u64) -> String {
        Logger::info(ui(
            "[WAKEUP-BOOST] Memicu lonjakan CPU sementara untuk menyerap beban bangun tidur (thundering herd)...",
            "[WAKEUP-BOOST] Triggering transient CPU boost to absorb wakeup thundering herd..."
        ));

        let dur = if duration_ms == 0 { 2000 } else { duration_ms };

        // 1. Immediately ensure top-app and foreground have access to all cores
        let (vendor, _) = crate::hardware::detector::ScoreBoard::evaluate();
        let total_cores = std::fs::read_to_string("/sys/devices/system/cpu/online")
            .unwrap_or_default()
            .trim()
            .split('-')
            .last()
            .and_then(|s| s.parse::<usize>().ok())
            .map(|m| m + 1)
            .unwrap_or(8);

        let max_core_idx = total_cores.saturating_sub(1);
        let all_cores_str = format!("0-{}", max_core_idx);

        Self::write_opt("/dev/cpuset/top-app/cpus", &all_cores_str);
        Self::write_opt("/dev/cpuset/foreground/cpus", &all_cores_str);
        Self::write_opt("/sys/fs/cgroup/cpuset/top-app/cpus", &all_cores_str);
        Self::write_opt("/sys/fs/cgroup/cpuset/foreground/cpus", &all_cores_str);

        // 2. Universal EAS Schedtune & CFS CPU Shares Surge (for 4.9/4.14/4.19 EAS kernels like SDM660)
        Self::write_opt("/dev/stune/top-app/schedtune.boost", "50");
        Self::write_opt("/dev/stune/top-app/schedtune.prefer_idle", "1");
        Self::write_opt("/dev/stune/top-app/schedtune.prefer_high_cap", "1");
        Self::write_opt("/dev/cpuctl/top-app/cpu.shares", "4096");
        Self::write_opt("/dev/cpuctl/background/cpu.shares", "128");
        Self::write_opt("/dev/cpuctl/system-background/cpu.shares", "256");
        Self::write_opt("/sys/devices/system/cpu/cpufreq/policy0/schedutil/up_rate_limit_us", "0");
        Self::write_opt("/sys/devices/system/cpu/cpufreq/policy4/schedutil/up_rate_limit_us", "0");

        // 3. Dynamic I/O Priority Elevation (Anti-Lockscreen Freeze):
        // Elevate I/O priority for UI critical processes (keystore2, system_server, surfaceflinger).
        // Strictly ban drop_caches on eMMC to prevent I/O avalanche & SQLite lockups!
        Self::elevate_ui_io_priority();

        match vendor {
            crate::hardware::detector::Vendor::Qualcomm => {
                // Qualcomm WALT Sched Boost
                Self::write_opt(crate::hardware::QcomHardware::SCHED_BOOST, "1");
                // Unpark Big Cores (Core Control: at least 2 big cores active)
                Self::write_opt(crate::hardware::QcomHardware::CORE_CTL_MIN_CPUS_CPU4, "2");
                // Touch/Input Boost
                Self::write_opt(crate::hardware::QcomHardware::CPU_BOOST_INPUT_MS, "120");
                Self::write_opt(crate::hardware::QcomHardware::CPU_BOOST_SCHED_BOOST_ON_INPUT, "1");
            }
            crate::hardware::detector::Vendor::MediaTek => {
                Self::write_opt("/proc/perfmgr/boost_ta/req_boost", "1");
                Self::write_opt("/proc/perfmgr/syslimiter/syslimiter_force_disable", "1");
            }
            crate::hardware::detector::Vendor::Generic => {}
        }

        // Spawn background thread to gracefully decay boost back to baseline after duration_ms
        std::thread::spawn(move || {
            std::thread::sleep(std::time::Duration::from_millis(dur));
            Self::write_opt("/dev/stune/top-app/schedtune.boost", "15");
            Self::write_opt("/dev/stune/top-app/schedtune.prefer_high_cap", "0");
            Self::write_opt("/dev/cpuctl/top-app/cpu.shares", "2048");
            Self::write_opt("/sys/devices/system/cpu/cpufreq/policy0/schedutil/up_rate_limit_us", "500");
            Self::write_opt("/sys/devices/system/cpu/cpufreq/policy4/schedutil/up_rate_limit_us", "500");
            match vendor {
                crate::hardware::detector::Vendor::Qualcomm => {
                    Self::write_opt(crate::hardware::QcomHardware::SCHED_BOOST, "0");
                    Self::write_opt(crate::hardware::QcomHardware::CORE_CTL_MIN_CPUS_CPU4, "1");
                    Self::write_opt(crate::hardware::QcomHardware::CPU_BOOST_INPUT_MS, "60");
                    Self::write_opt(crate::hardware::QcomHardware::CPU_BOOST_SCHED_BOOST_ON_INPUT, "0");
                }
                crate::hardware::detector::Vendor::MediaTek => {
                    Self::write_opt("/proc/perfmgr/boost_ta/req_boost", "0");
                }
                crate::hardware::detector::Vendor::Generic => {}
            }
            Logger::info(ui(
                "[WAKEUP-BOOST] Lonjakan bangun tidur selesai, ritme seimbang normal dipulihkan.",
                "[WAKEUP-BOOST] Wakeup surge completed, restored to normal balanced rhythm."
            ));
        });

        format!("Wakeup boost triggered for {}ms (All cpus unparked, schedtune.boost=50, sched_boost=1).", dur)
    }

    /// Bundles system diagnostics into a timestamped archive on /sdcard/Lynx/.
    pub fn export_diagnostic_zip() -> String {
        let export_dir = Path::new("/sdcard/Lynx");
        let _ = std::fs::create_dir_all(export_dir);

        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        let tmp_dir = format!("/data/local/tmp/lynx_diag_{}", now);
        let _ = std::fs::create_dir_all(&tmp_dir);

        // Copy configs and logs
        let _ = Command::new("cp").args(["-af", "/data/adb/modules/Lynx/config.json", &tmp_dir]).output();
        let _ = Command::new("cp").args(["-af", "/data/adb/modules/Lynx/logs", &tmp_dir]).output();

        // Dump diagnostic outputs
        if let Ok(out) = Command::new("uname").arg("-a").output() {
            let _ = std::fs::write(format!("{}/uname.txt", tmp_dir), out.stdout);
        }
        if let Ok(out) = Command::new("getprop").output() {
            let _ = std::fs::write(format!("{}/getprop.txt", tmp_dir), out.stdout);
        }
        if let Ok(out) = Command::new("dmesg").output() {
            let s = String::from_utf8_lossy(&out.stdout);
            let tail = s.lines().rev().take(500).collect::<Vec<_>>().into_iter().rev().collect::<Vec<_>>().join("\n");
            let _ = std::fs::write(format!("{}/dmesg_tail.txt", tmp_dir), tail);
        }
        if let Ok(out) = Command::new("logcat").args(["-d", "-t", "500"]).output() {
            let _ = std::fs::write(format!("{}/logcat_tail.txt", tmp_dir), out.stdout);
        }
        if let Ok(content) = std::fs::read_to_string("/proc/meminfo") {
            let _ = std::fs::write(format!("{}/meminfo.txt", tmp_dir), content);
        }

        let zip_out = format!("{}/lynx_diagnostic_{}.zip", export_dir.display(), now);
        let zip_status = Command::new("zip")
            .args(["-r", &zip_out, "."])
            .current_dir(&tmp_dir)
            .status();

        let final_out = if zip_status.map(|s| s.success()).unwrap_or(false) {
            zip_out
        } else {
            let tar_out = format!("{}/lynx_diagnostic_{}.tar.gz", export_dir.display(), now);
            let _ = Command::new("tar")
                .args(["-czf", &tar_out, "-C", &tmp_dir, "."])
                .status();
            tar_out
        };

        let _ = std::fs::remove_dir_all(&tmp_dir);
        final_out
    }

    /// Dynamically elevates I/O priority for lockscreen & UI render critical processes
    /// to Real-Time class (or High Best-Effort), eliminating lockscreen lag on eMMC half-duplex bus.
    pub fn elevate_ui_io_priority() {
        let ionice_bin = if Path::new("/system/bin/ionice").exists() {
            "/system/bin/ionice"
        } else if Path::new("/vendor/bin/ionice").exists() {
            "/vendor/bin/ionice"
        } else {
            "ionice"
        };

        let target_procs = ["keystore2", "android.system.keystore2", "system_server", "surfaceflinger"];
        if let Ok(entries) = std::fs::read_dir("/proc") {
            for entry in entries.flatten() {
                if let Ok(file_name) = entry.file_name().into_string() {
                    if let Ok(pid) = file_name.parse::<i32>() {
                        let cmd_path = entry.path().join("cmdline");
                        if let Ok(cmd_bytes) = std::fs::read(&cmd_path) {
                            let cmd = String::from_utf8_lossy(&cmd_bytes);
                            for target in &target_procs {
                                if cmd.contains(target) {
                                    let pid_str = pid.to_string();
                                    let rt_res = Command::new(ionice_bin)
                                        .args(["-c", "1", "-n", "0", "-p", &pid_str])
                                        .output();
                                    if rt_res.is_err() || !rt_res.as_ref().map(|o| o.status.success()).unwrap_or(false) {
                                        let _ = Command::new(ionice_bin)
                                            .args(["-c", "2", "-n", "0", "-p", &pid_str])
                                            .output();
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

