use std::path::Path;
use std::process::Command;

use crate::sysfs::{SysfsWriter, WriteMode};
use crate::utils::Logger;

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
        Logger::info("Applying universal system & kernel core optimizations...");
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
                Self::write_opt("/sys/module/cpu_boost/parameters/input_boost_ms", "0");
                for i in 0..8 {
                    Self::write_opt("/sys/module/cpu_boost/parameters/input_boost_freq", format!("{}:0", i));
                }
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
}

