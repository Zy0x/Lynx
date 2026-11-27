use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Mutex, OnceLock};

use crate::subsystems::tui::ui;
use crate::sysfs::{SysfsWriter, WriteMode};
use crate::Logger;

static LAST_WAKEUP_MS: AtomicU64 = AtomicU64::new(0);
static WAKEUP_GENERATION: AtomicU64 = AtomicU64::new(0);
static ELEVATED_PIDS: OnceLock<Mutex<HashMap<&'static str, i32>>> = OnceLock::new();

fn write_opt(path: impl AsRef<Path>, val: impl AsRef<str>) {
    let p = path.as_ref();
    if p.exists() {
        let _ = SysfsWriter::write(p, val, WriteMode::ForcePermission);
    }
}

/// Resolves thermal- and battery-aware wakeup boost parameters without hardcoded frequencies.
/// Returns `(duration_ms, opp_quantile_pct, stune_boost, top_shares)`.
pub(crate) fn resolve_wakeup_boost_params(
    requested_ms: u64,
    active_profile: &str,
    batt_temp_c: f32,
    batt_pct: u32,
    is_charging: bool,
) -> (u64, usize, &'static str, &'static str) {
    let base_dur = if requested_ms == 0 {
        3000
    } else {
        requested_ms.clamp(800, 4000)
    };
    if active_profile == "powersave" {
        return (base_dur.min(1200), 25, "20", "2048");
    }
    let thermally_warm = batt_temp_c >= 43.0;
    let low_battery = batt_pct > 0 && batt_pct <= 15 && !is_charging;
    if thermally_warm || low_battery {
        return (base_dur.min(1600), 35, "25", "3072");
    }
    (base_dur, 55, "45", "4096")
}

fn discover_core_ctl_min_nodes() -> Vec<PathBuf> {
    let mut nodes = Vec::new();
    for cpu_idx in 1..16 {
        let p = PathBuf::from(format!(
            "/sys/devices/system/cpu/cpu{}/core_ctl/min_cpus",
            cpu_idx
        ));
        if p.exists() {
            nodes.push(p);
        }
    }
    nodes
}

/// Triggers an instantaneous, debounced, hardware-level Wakeup Boost to absorb deep sleep wakeup stutter.
pub fn run_wakeup_boost(duration_ms: u64) -> String {
    let now_ms = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64;

    let marker_path = Path::new("/dev/lynx_last_wakeup_ms");
    let last_cross_ms = std::fs::read_to_string(marker_path)
        .ok()
        .and_then(|s| s.trim().parse::<u64>().ok())
        .unwrap_or(0);
    let last_ms = LAST_WAKEUP_MS.load(Ordering::Relaxed).max(last_cross_ms);
    if last_ms > 0 && now_ms.saturating_sub(last_ms) < 3000 {
        return "Wakeup boost skipped (cooldown active).".to_string();
    }

    LAST_WAKEUP_MS.store(now_ms, Ordering::Relaxed);
    let my_gen = WAKEUP_GENERATION.fetch_add(1, Ordering::AcqRel) + 1;
    let _ = std::fs::write(marker_path, now_ms.to_string());

    let active_profile = std::fs::read_to_string("/data/adb/lynx/active_profile")
        .or_else(|_| std::fs::read_to_string("/data/local/tmp/lynxd_profile"))
        .unwrap_or_else(|_| "balance".to_string())
        .trim()
        .to_ascii_lowercase();

    let snap = crate::state::DeviceState::capture();
    let is_plugged = snap.raw.battery.status.eq_ignore_ascii_case("Charging")
        || crate::hardware::ChargingRegulator::is_hardware_plugged();
    let (dur, quantile_pct, stune_boost, top_shares) = resolve_wakeup_boost_params(
        duration_ms,
        &active_profile,
        snap.derived.temp_celsius_float,
        snap.raw.battery.capacity,
        is_plugged,
    );

    Logger::info(ui(
        "[WAKEUP-BOOST] Memicu lonjakan CPU adaptif sementara untuk menyerap beban bangun tidur...",
        "[WAKEUP-BOOST] Triggering adaptive transient CPU boost to absorb wakeup thundering herd...",
    ));

    // 1. Ensure top-app and foreground have access to all online cores
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

    write_opt("/dev/cpuset/top-app/cpus", &all_cores_str);
    write_opt("/dev/cpuset/foreground/cpus", &all_cores_str);
    write_opt("/sys/fs/cgroup/cpuset/top-app/cpus", &all_cores_str);
    write_opt("/sys/fs/cgroup/cpuset/foreground/cpus", &all_cores_str);

    // 2. Universal EAS Schedtune & CFS CPU Shares Surge
    let fg_boost = if quantile_pct <= 35 { "20" } else { "30" };
    write_opt("/dev/stune/top-app/schedtune.boost", stune_boost);
    write_opt("/dev/stune/top-app/schedtune.prefer_idle", "1");
    write_opt("/dev/stune/top-app/schedtune.prefer_high_cap", "1");
    write_opt("/dev/stune/foreground/schedtune.boost", fg_boost);
    write_opt("/dev/cpuctl/top-app/cpu.shares", top_shares);

    // Elevate dynamic OPP floor across non-efficiency clusters without hardcoding frequencies
    let cpu = crate::hardware::capability::HardwareCapability::resolve_cpu();
    let single_cluster = cpu.policies.len() <= 1;
    for pol in &cpu.policies {
        for gdir in &pol.gov_tunable_dirs {
            write_opt(gdir.join("up_rate_limit_us"), "0");
        }
        let is_big = single_cluster
            || pol.index > 0
            || pol.role.contains("Big")
            || pol.role.contains("Prime");
        if !is_big || pol.is_locked || pol.current_governor == "performance" {
            continue;
        }
        let boost_hz = crate::subsystems::cluster::pick_opp_quantile_freq(
            &pol.available_freqs,
            pol.min_freq,
            pol.max_freq,
            quantile_pct,
            100,
        );
        if pol.current_min_freq < boost_hz {
            let boost_str = boost_hz.to_string();
            let _ = SysfsWriter::write(
                pol.path.join("scaling_min_freq"),
                &boost_str,
                WriteMode::ForcePermission,
            );
            if vendor == crate::hardware::detector::Vendor::MediaTek {
                write_opt(
                    "/proc/ppm/policy/hard_userlimit_min_cpu_freq",
                    format!("{} {}", pol.mtk_cluster_index, boost_hz),
                );
            }
        }
    }
    write_opt("/sys/devices/system/cpu/cpufreq/schedutil/up_rate_limit_us", "0");

    // 3. Asynchronous I/O Priority Elevation (never block wakeup boost caller on /proc scan)
    if crate::hardware::DeviceTier::cached_live().is_emmc_primary {
        std::thread::spawn(elevate_ui_io_priority);
    }

    // Snapshot core_ctl min_cpus dynamically across all non-efficiency clusters before boosting
    let mut core_ctl_snapshots: Vec<(PathBuf, String)> = Vec::new();
    match vendor {
        crate::hardware::detector::Vendor::Qualcomm => {
            write_opt(crate::hardware::QcomHardware::SCHED_BOOST, "1");
            for node in discover_core_ctl_min_nodes() {
                if let Ok(prev) = std::fs::read_to_string(&node) {
                    let trimmed = prev.trim().to_string();
                    if !trimmed.is_empty() {
                        core_ctl_snapshots.push((node.clone(), trimmed));
                    }
                }
                let target_min = if quantile_pct <= 35 { "1" } else { "2" };
                write_opt(&node, target_min);
            }
            write_opt(crate::hardware::QcomHardware::CPU_BOOST_INPUT_MS, "100");
            write_opt(crate::hardware::QcomHardware::CPU_BOOST_SCHED_BOOST_ON_INPUT, "1");
        }
        crate::hardware::detector::Vendor::MediaTek => {
            write_opt("/proc/perfmgr/boost_ta/req_boost", "1");
            write_opt("/proc/perfmgr/syslimiter/syslimiter_force_disable", "1");
        }
        crate::hardware::detector::Vendor::Generic => {}
    }

    // Spawn background thread to gracefully decay boost back to baseline if no newer boost superseded it
    std::thread::spawn(move || {
        std::thread::sleep(std::time::Duration::from_millis(dur));

        if WAKEUP_GENERATION.load(Ordering::Acquire) != my_gen {
            return;
        }
        if let Ok(latest_ts) = std::fs::read_to_string("/dev/lynx_last_wakeup_ms") {
            if latest_ts.trim().parse::<u64>().unwrap_or(now_ms) > now_ms {
                return;
            }
        }

        let latest_profile = std::fs::read_to_string("/data/adb/lynx/active_profile")
            .or_else(|_| std::fs::read_to_string("/data/local/tmp/lynxd_profile"))
            .unwrap_or_else(|_| "balance".to_string())
            .trim()
            .to_string();

        let cpu = crate::hardware::capability::HardwareCapability::resolve_cpu();
        crate::subsystems::ClusterController::restore_baseline_floor(&cpu, &latest_profile);

        let restored_shares = if latest_profile == "powersave" {
            "1024"
        } else {
            "2048"
        };
        write_opt("/dev/cpuctl/top-app/cpu.shares", restored_shares);
        let restored_up = match latest_profile.as_str() {
            "extreme" | "performance" => "0",
            "powersave" => "5000",
            _ => "2000",
        };
        for pol in &cpu.policies {
            for gdir in &pol.gov_tunable_dirs {
                write_opt(gdir.join("up_rate_limit_us"), restored_up);
            }
        }
        write_opt("/sys/devices/system/cpu/cpufreq/schedutil/up_rate_limit_us", restored_up);

        match vendor {
            crate::hardware::detector::Vendor::Qualcomm => {
                write_opt(crate::hardware::QcomHardware::SCHED_BOOST, "0");
                for (node, prev) in &core_ctl_snapshots {
                    write_opt(node, prev);
                }
                write_opt(crate::hardware::QcomHardware::CPU_BOOST_INPUT_MS, "60");
                write_opt(crate::hardware::QcomHardware::CPU_BOOST_SCHED_BOOST_ON_INPUT, "0");
            }
            crate::hardware::detector::Vendor::MediaTek => {
                write_opt("/proc/perfmgr/boost_ta/req_boost", "0");
            }
            crate::hardware::detector::Vendor::Generic => {}
        }
        Logger::info(ui(
            "[WAKEUP-BOOST] Lonjakan bangun tidur selesai, ritme seimbang normal dipulihkan.",
            "[WAKEUP-BOOST] Wakeup surge completed, restored to normal balanced rhythm.",
        ));
    });

    format!(
        "Wakeup boost triggered for {}ms (OPP quantile {}%, schedtune.boost={}, profile={}).",
        dur, quantile_pct, stune_boost, active_profile
    )
}

/// Dynamically elevates I/O priority for lockscreen & UI render critical processes
/// using cached PIDs to avoid repeated `/proc` directory scans on every wakeup.
pub fn elevate_ui_io_priority() {
    let cache = ELEVATED_PIDS.get_or_init(|| Mutex::new(HashMap::new()));

    let ionice_bin = if Path::new("/system/bin/ionice").exists() {
        "/system/bin/ionice"
    } else if Path::new("/vendor/bin/ionice").exists() {
        "/vendor/bin/ionice"
    } else {
        "ionice"
    };

    let target_procs: [&'static str; 4] = [
        "keystore2",
        "android.system.keystore2",
        "system_server",
        "surfaceflinger",
    ];

    let mut guard = match cache.lock() {
        Ok(g) => g,
        Err(_) => return,
    };

    let mut missing: Vec<&'static str> = Vec::new();
    for &target in &target_procs {
        if let Some(&cached_pid) = guard.get(target) {
            let cmd_path = format!("/proc/{}/cmdline", cached_pid);
            if let Ok(bytes) = std::fs::read(&cmd_path) {
                if String::from_utf8_lossy(&bytes).contains(target) {
                    continue;
                }
            }
        }
        missing.push(target);
    }

    if missing.is_empty() {
        return;
    }

    if let Ok(entries) = std::fs::read_dir("/proc") {
        for entry in entries.flatten() {
            if let Ok(file_name) = entry.file_name().into_string() {
                if let Ok(pid) = file_name.parse::<i32>() {
                    let cmd_path = entry.path().join("cmdline");
                    if let Ok(cmd_bytes) = std::fs::read(&cmd_path) {
                        let cmd = String::from_utf8_lossy(&cmd_bytes);
                        for &target in &missing {
                            if cmd.contains(target) {
                                let pid_str = pid.to_string();
                                let rt_res = Command::new(ionice_bin)
                                    .args(["-c", "1", "-n", "0", "-p", &pid_str])
                                    .output();
                                if rt_res.is_err()
                                    || !rt_res.as_ref().map(|o| o.status.success()).unwrap_or(false)
                                {
                                    let _ = Command::new(ionice_bin)
                                        .args(["-c", "2", "-n", "0", "-p", &pid_str])
                                        .output();
                                }
                                guard.insert(target, pid);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::resolve_wakeup_boost_params;

    #[test]
    fn wakeup_boost_scales_down_under_warm_or_low_battery_and_powersave() {
        assert_eq!(
            resolve_wakeup_boost_params(3500, "balance", 34.0, 75, false),
            (3500, 55, "45", "4096")
        );
        assert_eq!(
            resolve_wakeup_boost_params(3500, "balance", 43.5, 60, false),
            (1600, 35, "25", "3072")
        );
        assert_eq!(
            resolve_wakeup_boost_params(3500, "balance", 35.0, 15, false),
            (1600, 35, "25", "3072")
        );
        assert_eq!(
            resolve_wakeup_boost_params(3500, "balance", 35.0, 15, true),
            (3500, 55, "45", "4096")
        );
        assert_eq!(
            resolve_wakeup_boost_params(3500, "powersave", 32.0, 80, false),
            (1200, 25, "20", "2048")
        );
    }
}
