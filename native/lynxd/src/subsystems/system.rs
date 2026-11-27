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

        // Android settings for perfetto profiler and watchdog + running telemetry daemon suppression
        // Batched in a single shell invocation to avoid 12 serial fork/execs during cold boot.
        let _ = Command::new("/system/bin/sh")
            .args([
                "-c",
                concat!(
                    "[ \"$(settings get global debug.perfetto.profiler.enabled 2>/dev/null)\" != \"0\" ] && settings put global debug.perfetto.profiler.enabled 0 2>/dev/null; ",
                    "[ \"$(settings get global watchdog_enabled 2>/dev/null)\" != \"0\" ] && settings put global watchdog_enabled 0 2>/dev/null; ",
                    "for d in logcatd tcpdump statsd traced idd-logreader idd-logreadermain dumpstate aplogd vendor_tcpdump vendor.tcpdump; do ",
                    "[ \"$(getprop init.svc.$d 2>/dev/null)\" = \"running\" ] && stop \"$d\" 2>/dev/null; ",
                    "done"
                ),
            ])
            .status();

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
        count += Self::apply_universal_graphics_pipeline();

        format!("Universal system and kernel core optimizations applied ({} nodes written).", count)
    }

    pub fn compute_adaptive_egl_blobcache_limit_bytes(total_ram_mb: u64) -> u64 {
        // Persistent disk shader blobcache cap (`/data/user_de/0/<pkg>/cache/shaders_cache.multifile`),
        // NOT a RAM buffer. Modern AAA open-world titles (Genshin/WuWa/HSR) compile 20k+ shaders (~6-40 MB);
        // an 8 MB cap causes constant LRU eviction and synchronous mid-gameplay shader recompiles.
        if total_ram_mb <= 4096 {
            16 * 1024 * 1024
        } else if total_ram_mb <= 8192 {
            64 * 1024 * 1024
        } else {
            128 * 1024 * 1024
        }
    }

    /// Streams ELF/shared libraries in bounded 256 KiB chunks to check whether a property key is supported.
    pub fn binary_contains_bytes(path: &Path, needle: &[u8]) -> bool {
        use std::io::Read;
        if needle.is_empty() {
            return false;
        }
        let Ok(mut file) = std::fs::File::open(path) else {
            return false;
        };
        let chunk_len = 256 * 1024;
        let overlap = needle.len().saturating_sub(1);
        let mut buf = vec![0u8; chunk_len + overlap];
        let mut carry = 0usize;
        loop {
            let Ok(read_n) = file.read(&mut buf[carry..]) else {
                return false;
            };
            if read_n == 0 {
                return false;
            }
            let valid_len = carry + read_n;
            if buf[..valid_len].windows(needle.len()).any(|w| w == needle) {
                return true;
            }
            if overlap > 0 && valid_len > overlap {
                buf.copy_within(valid_len - overlap..valid_len, 0);
                carry = overlap;
            } else {
                carry = 0;
            }
        }
    }

    fn set_system_prop(key: &str, val: &str) -> bool {
        let bin = if Path::new("/system/bin/setprop").exists() {
            "/system/bin/setprop"
        } else {
            "setprop"
        };
        Command::new(bin)
            .args([key, val])
            .status()
            .map(|s| s.success())
            .unwrap_or(false)
    }

    fn graphics_lib_fingerprint(paths: &[&str]) -> String {
        use std::time::UNIX_EPOCH;
        let mut fp = String::new();
        for p in paths {
            if let Ok(meta) = std::fs::metadata(p) {
                let mtime = meta
                    .modified()
                    .ok()
                    .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
                    .map(|d| d.as_secs())
                    .unwrap_or(0);
                fp.push_str(&format!("{}:{}:{};", p, meta.len(), mtime));
            }
        }
        fp
    }

    /// Probes Android ELF libraries at runtime (with persistent size+mtime cache to avoid
    /// multi-MB ELF reads on every cold boot) and applies lossless OpenGL ES / HWUI optimizations
    /// (EGL multi-file shader blobcache + HWUI ADPF hint manager + skip empty damage).
    pub fn apply_universal_graphics_pipeline() -> usize {
        let mut applied = 0usize;
        let mem = crate::hardware::capability::HardwareCapability::resolve_memory();
        let limit_bytes = Self::compute_adaptive_egl_blobcache_limit_bytes(mem.total_ram_mb);

        let egl_libs = ["/system/lib64/libEGL.so", "/system/lib/libEGL.so"];
        let hwui_libs = [
            "/system/lib64/libhwui.so",
            "/system/lib/libhwui.so",
            "/system_ext/lib64/libhwui.so",
            "/system_ext/lib/libhwui.so",
        ];
        let all_libs = [
            "/system/lib64/libEGL.so",
            "/system/lib/libEGL.so",
            "/system/lib64/libhwui.so",
            "/system/lib/libhwui.so",
            "/system_ext/lib64/libhwui.so",
            "/system_ext/lib/libhwui.so",
        ];
        let fp = Self::graphics_lib_fingerprint(&all_libs);
        let cache_path = Path::new("/data/adb/lynx/graphics_probe.cache");

        let (supports_multifile, supports_hint, supports_skip_dmg) = std::fs::read_to_string(cache_path)
            .ok()
            .and_then(|raw| {
                let mut lines = raw.lines();
                let saved_fp = lines.next()?;
                let flags = lines.next()?;
                if saved_fp == fp {
                    let parts: Vec<&str> = flags.split(',').collect();
                    if parts.len() == 3 {
                        return Some((parts[0] == "1", parts[1] == "1", parts[2] == "1"));
                    }
                }
                None
            })
            .unwrap_or_else(|| {
                let mf = egl_libs
                    .iter()
                    .map(Path::new)
                    .any(|p| Self::binary_contains_bytes(p, b"debug.egl.blobcache.multifile"));
                let hint = hwui_libs
                    .iter()
                    .map(Path::new)
                    .any(|p| Self::binary_contains_bytes(p, b"debug.hwui.use_hint_manager"));
                let dmg = hwui_libs
                    .iter()
                    .map(Path::new)
                    .any(|p| Self::binary_contains_bytes(p, b"debug.hwui.skip_empty_damage"));
                if !fp.is_empty() {
                    let _ = std::fs::create_dir_all("/data/adb/lynx");
                    let _ = std::fs::write(
                        cache_path,
                        format!("{}\n{},{},{}\n", fp, mf as u8, hint as u8, dmg as u8),
                    );
                }
                (mf, hint, dmg)
            });

        let mut prop_cmds: Vec<String> = Vec::new();
        if supports_multifile {
            let user_opt_out = std::fs::read_to_string("/data/adb/lynx/user_egl_multifile")
                .map(|s| s.trim() == "0")
                .unwrap_or(false);
            if !user_opt_out {
                prop_cmds.push("setprop debug.egl.blobcache.multifile true".to_string());
                prop_cmds.push(format!("setprop debug.egl.blobcache.multifile_limit {}", limit_bytes));
                applied += 2;
            }
        }
        if supports_hint {
            let user_opt_out = std::fs::read_to_string("/data/adb/lynx/user_hwui_hint_mgr")
                .map(|s| s.trim() == "0")
                .unwrap_or(false);
            if !user_opt_out {
                prop_cmds.push("setprop debug.hwui.use_hint_manager true".to_string());
                applied += 1;
            }
        }
        if supports_skip_dmg {
            prop_cmds.push("setprop debug.hwui.skip_empty_damage true".to_string());
            applied += 1;
        }
        if !prop_cmds.is_empty() {
            let _ = Command::new("/system/bin/sh")
                .args(["-c", &prop_cmds.join("; ")])
                .status();
        }

        applied
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

    /// Triggers an instantaneous, debounced, hardware-level Wakeup Boost to absorb deep sleep wakeup stutter.
    pub fn wakeup_boost(duration_ms: u64) -> String {
        super::wakeup::run_wakeup_boost(duration_ms)
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

    /// Dynamically elevates I/O priority for lockscreen & UI render critical processes.
    pub fn elevate_ui_io_priority() {
        super::wakeup::elevate_ui_io_priority();
    }
}

#[cfg(test)]
mod tests {
    use super::SystemController;

    #[test]
    fn adaptive_egl_blobcache_limit_scales_by_ram_tier() {
        assert_eq!(SystemController::compute_adaptive_egl_blobcache_limit_bytes(3584), 16 * 1024 * 1024);
        assert_eq!(SystemController::compute_adaptive_egl_blobcache_limit_bytes(7680), 64 * 1024 * 1024);
        assert_eq!(SystemController::compute_adaptive_egl_blobcache_limit_bytes(12288), 128 * 1024 * 1024);
    }

    #[test]
    fn binary_contains_bytes_handles_boundary_match() {
        let mut p = std::env::temp_dir();
        p.push("lynx_probe_test_blob.bin");
        let mut data = vec![b'A'; 262_130];
        data.extend_from_slice(b"debug.egl.blobcache.multifile");
        data.extend_from_slice(&[b'Z'; 32]);
        std::fs::write(&p, &data).unwrap();
        assert!(SystemController::binary_contains_bytes(&p, b"debug.egl.blobcache.multifile"));
        assert!(!SystemController::binary_contains_bytes(&p, b"ro.hwui.nonexistent_prop"));
        let _ = std::fs::remove_file(&p);
    }
}

