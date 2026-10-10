use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::Path;
use std::process::{Command, Stdio};
use std::thread::sleep;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use crate::daemon::DaemonLifecycle;
use crate::hardware::{ChargingRegulator, ScoreBoard};
use crate::inspector::TunableStore;
use crate::state::{ConfigStore, ProfileStateManager};
use crate::subsystems::{
    MaintenanceController, MemoryController, SwapController, SystemController, ThermalController,
};
use crate::sysfs::{SysfsWriter, WriteMode};

const MODPATH: &str = "/data/adb/modules/Lynx";
const LOG_FILE: &str = "/storage/emulated/0/Lynx/Lynx.log";

pub struct LifecycleHooks;

impl LifecycleHooks {
    fn log_msg(msg: &str) {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        let line = format!("[{}] {}\n", now, msg);
        if let Ok(mut f) = OpenOptions::new().create(true).append(true).open(LOG_FILE) {
            let _ = f.write_all(line.as_bytes());
        }
    }

    fn rotate_log(target: &str, max_size: u64) {
        if let Ok(meta) = fs::metadata(target) {
            if meta.len() > max_size {
                let archive_dir = format!("{}/logs/archive", MODPATH);
                let _ = fs::create_dir_all(&archive_dir);
                if let Some(fname) = Path::new(target).file_name() {
                    let dst = format!("{}/{}.old", archive_dir, fname.to_string_lossy());
                    let _ = fs::rename(target, dst);
                }
            }
        }
    }

    fn getprop(prop: &str) -> String {
        if let Ok(out) = Command::new("getprop").arg(prop).output() {
            return String::from_utf8_lossy(&out.stdout).trim().to_string();
        }
        String::new()
    }

    fn post_notification(text: &str) {
        let cmd = format!(
            "cmd notification post -S bigtext -t 'Lynx - Deity' 'Lynx' '{}'",
            text.replace('\'', "")
        );
        let _ = Command::new("su")
            .args(["-lp", "2000", "-c", &cmd])
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status();
    }

    /// Replaces the 289-line `service.sh` with a 100% native Rust boot orchestrator.
    pub fn run_boot() -> i32 {
        // 0. Critical Safe Mode & Bootloop Protection Guard
        for flag in &[
            "/data/adb/modules/.disable_magisk",
            "/data/adb/apatch/.disable",
            "/data/adb/modules/Lynx/disable",
            "/sdcard/Debug/SAFE_MODE",
        ] {
            if Path::new(flag).exists() {
                return 0;
            }
        }

        // Clean up any legacy service.d scripts
        if let Ok(entries) = fs::read_dir("/data/adb/service.d") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                if name.starts_with("lynx") {
                    let _ = fs::remove_file(entry.path());
                }
            }
        }

        // 1. Wait for boot completion (max 120s timeout)
        for _ in 0..60 {
            if Self::getprop("sys.boot_completed") == "1" {
                break;
            }
            sleep(Duration::from_secs(2));
        }

        // 2. Wait for decrypted user storage (max 50s timeout for FBE)
        for _ in 0..25 {
            if Path::new("/sdcard/Android").is_dir() {
                break;
            }
            sleep(Duration::from_secs(2));
        }

        if Path::new("/sdcard/Debug/SAFE_MODE").exists() {
            return 0;
        }

        let _ = fs::create_dir_all("/storage/emulated/0/Lynx");
        let _ = fs::create_dir_all(format!("{}/logs/archive", MODPATH));

        Self::rotate_log(LOG_FILE, 524_288);
        Self::rotate_log(&format!("{}/logs/transaction.log", MODPATH), 1_048_576);

        // 3. Target Architecture & Environment
        let target_soc_file = format!("{}/target_soc", MODPATH);
        let mut target_soc = fs::read_to_string(&target_soc_file)
            .unwrap_or_default()
            .trim()
            .to_string();

        if target_soc.is_empty() || target_soc == "generic" {
            let cfg_soc = ConfigStore::get("soc_type").unwrap_or_default();
            if !cfg_soc.is_empty() && cfg_soc != "generic" {
                target_soc = cfg_soc;
            } else {
                let eval = ScoreBoard::evaluate();
                target_soc = eval.0.as_platform_id().to_string();
            }
            let _ = fs::write(&target_soc_file, &target_soc);
        }

        let brand = Self::getprop("ro.product.brand");
        let model = Self::getprop("ro.product.model");
        Self::log_msg("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Self::log_msg("Lynx Universal Native Boot Engine Initializing...");
        Self::log_msg(&format!("• Platform: {}", target_soc));
        Self::log_msg(&format!("• Device: {} {}", brand, model));

        // 3b. Boot Recovery Guard
        if let Ok(report) = ProfileStateManager::recover_boot() {
            Self::log_msg(&format!("[+] Boot recovery verified system state: {:?}", report));
        }

        // 4. WebUI HTTP Server on 127.0.0.1:8080
        let webroot = format!("{}/webroot", MODPATH);
        if Path::new(&webroot).is_dir() {
            ChargingRegulator::signal_processes(&["httpd -p 127.0.0.1:8080"], true);
            for bin in &[
                "/data/adb/magisk/busybox",
                "/data/adb/ksu/bin/busybox",
                "/data/adb/ap/bin/busybox",
                "busybox",
                "toybox",
            ] {
                if *bin == "busybox" || *bin == "toybox" || Path::new(bin).exists() {
                    if Command::new(bin)
                        .args(["httpd", "-p", "127.0.0.1:8080", "-h", &webroot])
                        .stdout(Stdio::null())
                        .stderr(Stdio::null())
                        .spawn()
                        .is_ok()
                    {
                        Self::log_msg("[*] WebUI HTTP Server active on 127.0.0.1:8080");
                        break;
                    }
                }
            }
        }

        // 5. Profile Enforcement & First-Boot Dormant Safety Guard
        let mut active_profile = ConfigStore::get("active_profile").unwrap_or_else(|| "dormant".to_string());
        if active_profile.is_empty() {
            active_profile = "dormant".to_string();
        }
        Self::log_msg(&format!("Active profile: {}", active_profile));

        let modprop = format!("{}/module.prop", MODPATH);
        if active_profile == "dormant" {
            if let Ok(content) = fs::read_to_string(&modprop) {
                let mut updated = Vec::new();
                for line in content.lines() {
                    if line.starts_with("description=") {
                        updated.push("description=[ Dormant (Pending Setup) ] Universal Hybrid Performance Engine".to_string());
                    } else {
                        updated.push(line.to_string());
                    }
                }
                let _ = fs::write(&modprop, updated.join("\n") + "\n");
            }
            Self::post_notification(
                "Modul terpasang aman (Standby). Buka WebUI atau Aplikasi Lynx untuk konfigurasi awal.",
            );
            Self::log_msg("Lynx initialized safely in dormant standby mode. No hardware sysfs applied.");
            return 0;
        }

        // 6. Universal & Platform-Specific Subsystem Optimization
        let _ = SystemController::apply();
        Self::log_msg("[+] Core and platform subsystems optimized natively via lynxd");

        let _ = Command::new("settings")
            .args(["put", "secure", "long_press_timeout", "280"])
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status();
        let _ = Command::new("settings")
            .args(["put", "secure", "multi_press_timeout", "80"])
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status();

        // 7. Apply Charging & Storage Maintenance
        ChargingRegulator::apply_from_config();
        let _ = MaintenanceController::run(false);
        let _ = MemoryController::clean_memory();
        Self::log_msg("[+] Charging & storage maintenance executed natively via lynxd");

        // 8. Profile Action Execution
        let lynxd_bin = format!("{}/system/bin/lynxd", MODPATH);
        if active_profile == "auto" {
            let lynxd_log = format!("{}/logs/lynxd.log", MODPATH);
            Self::rotate_log(&lynxd_log, 1_048_576);
            let _ = DaemonLifecycle::stop_running();
            if let Ok(log_file) = OpenOptions::new().create(true).append(true).open(&lynxd_log) {
                let err_file = log_file.try_clone().unwrap_or_else(|_| {
                    OpenOptions::new().create(true).append(true).open(&lynxd_log).unwrap()
                });
                let _ = Command::new(&lynxd_bin)
                    .args(["daemon", "run"])
                    .stdout(Stdio::from(log_file))
                    .stderr(Stdio::from(err_file))
                    .spawn();
                Self::log_msg("[+] Native lynxd daemon launched with Autonomous Engine & Hysteresis");
            }
        } else {
            let normalized = if active_profile == "balanced" {
                "balance"
            } else {
                active_profile.as_str()
            };
            let _ = Command::new(&lynxd_bin)
                .args(["profile", "apply", normalized])
                .stdout(Stdio::null())
                .stderr(Stdio::null())
                .status();
            Self::log_msg(&format!(
                "[+] Applied profile '{}' via lynxd transactional pipeline",
                normalized
            ));
        }

        // 9. Lynx Thermal Framework Boot Mode Application
        let thermal_boot_cfg = format!("{}/thermal_boot_mode.json", MODPATH);
        if let Ok(content) = fs::read_to_string(&thermal_boot_cfg) {
            if let Some(mode) = crate::state::extract_json_string(&content, "mode") {
                if !mode.is_empty() && mode != "default_oem" {
                    Self::log_msg(&format!("[*] Applying boot thermal mode via lynxd: {}", mode));
                    let _ = ThermalController::set_mode(&mode);
                }
            }
        }

        // 10. Execute Custom User Rules & Deep Tunables
        let _ = TunableStore::apply_all();
        let custom_rules = format!("{}/custom_rules.sh", MODPATH);
        if Path::new(&custom_rules).is_file() {
            let _ = Command::new("sh")
                .arg(&custom_rules)
                .stdout(Stdio::null())
                .stderr(Stdio::null())
                .status();
        }

        Self::post_notification(&format!("{} Mode Applied...", active_profile));
        Self::log_msg("Lynx native boot sequence completed successfully.");
        0
    }

    /// Replaces the 109-line `uninstall.sh` with a 100% native Rust system restoration engine.
    pub fn run_uninstall() -> i32 {
        // 1. Terminate Background Daemons
        let _ = DaemonLifecycle::stop_running();

        // 2. Unfreeze any OEM Throttler Processes
        ThermalController::unfreeze_oem_throttlers();

        // 3. Restore Battery Charging Nodes & Thermal Tables
        ChargingRegulator::unlock_extreme_nodes();
        ChargingRegulator::write_node("/sys/class/power_supply/battery/charging_enabled", "1");
        ChargingRegulator::write_node("/sys/class/power_supply/battery/input_suspend", "0");
        for bypass in &[
            "/sys/devices/platform/charger/bypass_charger",
            "/sys/class/power_supply/battery/device/smart_charging",
            "/sys/class/power_supply/battery/smart_charging_activation",
            "/sys/class/qcom-battery/direct_charging",
        ] {
            ChargingRegulator::write_node(bypass, "0");
        }

        // 4. Restore CPU Frequency & Capacity Permissions
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            for cpu in 0..8 {
                for suffix in &["cpufreq/cpuinfo_max_freq", "cpu_capacity"] {
                    let p = format!("/sys/devices/system/cpu/cpu{}/{}", cpu, suffix);
                    if let Ok(meta) = fs::metadata(&p) {
                        let mut perms = meta.permissions();
                        perms.set_mode(0o444);
                        let _ = fs::set_permissions(&p, perms);
                    }
                }
            }
        }

        // 5. Remove SWAP file if present
        let _ = SwapController::remove();

        // 6. Reset Android Framework Services & Thermal Baseline
        let _ = Command::new("cmd")
            .args(["thermalservice", "reset"])
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status();
        let _ = Command::new("cmd")
            .args(["wifi", "force-low-latency-mode", "disabled"])
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status();
        let _ = ThermalController::set_mode("restore");
        let _ = fs::remove_file(format!("{}/thermal_boot_mode.json", MODPATH));
        let _ = fs::remove_file(format!("{}/thermal_baseline.json", MODPATH));

        for scale in &[
            "window_animation_scale",
            "transition_animation_scale",
            "animator_duration_scale",
        ] {
            let _ = Command::new("settings")
                .args(["put", "global", scale, "1.0"])
                .stdout(Stdio::null())
                .stderr(Stdio::null())
                .status();
        }

        // 7. Restore Core Kernel & VM Tunables to Baseline Snapshot / Stock Defaults
        let _ = ProfileStateManager::revert();
        let _ = TunableStore::reset("all");

        for (node, val) in &[
            ("/proc/sys/vm/dirty_ratio", "20"),
            ("/proc/sys/vm/dirty_background_ratio", "10"),
            ("/proc/sys/vm/vfs_cache_pressure", "100"),
            ("/proc/sys/vm/swappiness", "60"),
            ("/proc/sys/kernel/sched_util_clamp_min", "0"),
            ("/proc/sys/kernel/sched_util_clamp_max", "1024"),
        ] {
            let p = Path::new(node);
            if p.exists() {
                let _ = SysfsWriter::write(p, val, WriteMode::ForcePermission);
            }
        }

        // 8. Clean Runtime Lockfiles, Storage, & Shared Memory
        if let Ok(entries) = fs::read_dir("/dev") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                if name.starts_with("lynx") {
                    let p = entry.path();
                    if p.is_dir() {
                        let _ = fs::remove_dir_all(p);
                    } else {
                        let _ = fs::remove_file(p);
                    }
                }
            }
        }
        let _ = fs::remove_dir_all("/storage/emulated/0/Lynx");

        // 9. Uninstall Companion App & Notification Utilities
        for pkg in &["com.noir.lynx", "com.noir.lynx.debug", "bellavita.toast"] {
            let _ = Command::new("pm")
                .args(["uninstall", pkg])
                .stdout(Stdio::null())
                .stderr(Stdio::null())
                .status();
        }
        for dir in &[
            "/data/user/0/com.noir.lynx",
            "/data/user/0/com.noir.lynx.debug",
            "/data/data/com.noir.lynx",
            "/data/data/com.noir.lynx.debug",
        ] {
            let _ = fs::remove_dir_all(dir);
        }

        0
    }

    /// Replaces `action.sh` with a 100% native Rust maintenance & memory compaction runner.
    pub fn run_action() -> i32 {
        println!("==========================================");
        println!("      LYNX DEITY — MAINTENANCE ACTION     ");
        println!("==========================================");
        println!("");
        println!("[*] Executing Lynx Native Storage & Memory Maintenance...");
        println!("{}", MaintenanceController::run(true));
        println!("{}", MemoryController::clean_memory());
        println!("");
        println!("==========================================");
        println!("    [OK] System Optimization Finished!    ");
        println!("==========================================");
        0
    }
}
