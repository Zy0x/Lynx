pub mod apply;
pub mod core;
pub mod daemon;
pub mod decision;
pub mod hardware;
pub mod inspector;
pub mod profile;
pub mod state;
pub mod subsystems;
pub mod sysfs;
pub mod utils;

use std::env;
use std::time::Instant;

use crate::apply::{ExecutionPlan, ProfileExecutor};
use crate::daemon::{DaemonLifecycle, DaemonRunner};
use crate::hardware::{ChargingRegulator, MtkHardware, QcomHardware, ScoreBoard};
use crate::inspector::{TunableScanner, TunableStore};
use crate::profile::{get_profile_by_name, list_profiles, ProfileValidator, ProfileVerifier};
use crate::state::{ConfigStore, DeviceState, ProfileStateManager, RecoveryReport};
use crate::subsystems::{
    ApplistController, ClusterController, CpuIdleController, CpusetController, DisplayController,
    FlasherController, GameController, GpuController, IoController, LifecycleHooks,
    MaintenanceController, MemoryController, NetworkController, SwapController, SystemController,
    ThermalController, TuiController,
};
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};
use crate::utils::Logger;

fn print_usage() {
    println!("==================================================");
    println!("        LYNX NATIVE CORE ENGINE (lynxd) v{}    ", env!("CARGO_PKG_VERSION"));
    println!("==================================================");
    println!("Usage: lynxd <command> [subcommand] [arguments...]");
    println!("");
    println!("Commands:");
    println!("  boot                              Execute native module boot orchestrator (replaces service.sh)");
    println!("  uninstall                         Execute native system & sysfs restoration (replaces uninstall.sh)");
    println!("  action                            Execute native maintenance & memory compaction (replaces action.sh)");
    println!("  flasher backup|list_backups|restore|flash");
    println!("  tui                               Launch interactive terminal UI console (replaces system/bin/lynx)");
    println!("  status [--json]                   Inspect unified system, daemon, profile & modifier status");
    println!("  telemetry [--pretty]              Emit live hardware telemetry (unified JSON or formatted table)");
    println!("  state get <key> [default]         Read key from config.json in < 1ms");
    println!("  state set <k> <v> | <k=v>...      Atomically update single or multiple keys in config.json");
    println!("  state list                        Print current config.json contents");
    println!("  charging apply|lock|bypass|extreme|regulated|status|tick");
    println!("  profile list|show|validate|apply|verify|current|revert|recover|journal");
    println!("  thermal status|mode|set-limit|restore");
    println!("  game on [pid]|off|optimize-pid|status");
    println!("  display set-refresh-rate|restore|touch-boost|info");
    println!("  oem freeze|unfreeze");
    println!("  cpuset info|apply|set");
    println!("  cpuidle info|apply|set-state");
    println!("  swap info|set|enable|disable|remove");
    println!("  maintenance run|status");
    println!("  system apply|boeffla-status|doze-info|set-doze|set-selinux|set-printk|top-wakelocks|log-export");
    println!("  applist list|add|remove|check     Manage game & performance target app database");
    println!("  cluster topology|set-freq|lock|unlock|set-gov|set-schedutil-preset");
    println!("  gpu info|set-freq|set-gov|set-boost");
    println!("  memory apply|zram-info|zram-set|zram-disable|clean|vm-info|vm-set|vm-preset");
    println!("  io apply|info|set-scheduler");
    println!("  network apply|game|balance|set-algo|info");
    println!("  inspect scan|node <path>          Deep sysfs/procfs hardware tunable introspection (<40ms)");
    println!("  tunable set|apply|list|reset      Declarative user kernel tunable store with auto-rollback");
    println!("  daemon run|status|stop            Manage native adaptive background daemon");
    println!("  probe                             Detect SoC hardware vendor via weighted scoring");
    println!("  sysfs read|write|test-write|benchmark");
    println!("  help                              Display this help message");
    println!("==================================================");
}



fn handle_telemetry(pretty: bool) {
    if !pretty {
        #[cfg(target_os = "android")]
        {
            let snapshot_path = std::path::Path::new("/dev/lynxd_telemetry.json");
            if let Ok(metadata) = std::fs::metadata(snapshot_path) {
                if let Ok(modified) = metadata.modified() {
                    if let Ok(elapsed) = modified.elapsed() {
                        if elapsed.as_millis() < 2500 {
                            if let Ok(content) = std::fs::read_to_string(snapshot_path) {
                                print!("{}", content);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    let state = DeviceState::capture();
    if pretty {
        println!("{}", state.display_pretty());
    } else {
        let active_profile = ProfileStateManager::load_active()
            .ok()
            .flatten()
            .map(|a| a.active_profile)
            .unwrap_or_else(|| "balance".to_string());
        println!("{}", state.to_unified_json(Some(&active_profile), None, None, None));
    }
}

fn handle_status(json: bool) {
    let daemon_pid = DaemonLifecycle::check_status();
    let is_daemon_running = daemon_pid.is_some();

    let active_state = ProfileStateManager::load_active().ok().flatten();
    let state = DeviceState::capture();

    let (modifier, modifier_desc, fg_app, temp_c, bat_pct) = if is_daemon_running {
        let snap_path = DaemonRunner::status_snapshot_path();
        if let Ok(content) = std::fs::read_to_string(&snap_path) {
            let m = crate::state::extract_json_string(&content, "modifier")
                .unwrap_or_else(|| "None".to_string());
            let md = crate::state::extract_json_string(&content, "modifier_desc")
                .unwrap_or_else(|| "Full Base Profile".to_string());
            let app = crate::state::extract_json_string(&content, "app")
                .unwrap_or_else(|| "Unknown".to_string());
            (m, md, app, state.derived.temp_celsius_float, state.raw.battery.capacity)
        } else {
            ("None".to_string(), "Full Base Profile".to_string(), "None (Idle)".to_string(), state.derived.temp_celsius_float, state.raw.battery.capacity)
        }
    } else {
        ("None".to_string(), "Base Profile".to_string(), "N/A (Daemon stopped)".to_string(), state.derived.temp_celsius_float, state.raw.battery.capacity)
    };

    let active_profile = active_state.as_ref().map(|s| s.active_profile.as_str()).unwrap_or("balance");
    let active_status = active_state.as_ref().map(|s| s.status.as_str()).unwrap_or("baseline");
    let is_dirty = active_state.as_ref().map(|s| s.dirty).unwrap_or(false);
    let timestamp = active_state.as_ref().map(|s| s.timestamp).unwrap_or(0);
    let device_platform = active_state.as_ref().map(|s| s.device.as_str()).unwrap_or("OEM");

    if json {
        let pid_str = match daemon_pid {
            Some(pid) => format!("{}", pid),
            None => "null".to_string(),
        };

        println!(
            "{{\"daemon\":{{\"running\":{},\"pid\":{}}},\"profile\":{{\"active\":\"{}\",\"status\":\"{}\",\"dirty\":{},\"timestamp\":{}}},\"runtime\":{{\"modifier\":\"{}\",\"modifier_desc\":\"{}\",\"foreground_app\":\"{}\",\"temp_c\":{:.1},\"battery_pct\":{}}},\"health\":{{\"cpu\":{},\"gpu\":{},\"battery\":{},\"memory\":{}}}}}",
            is_daemon_running,
            pid_str,
            active_profile,
            active_status,
            is_dirty,
            timestamp,
            modifier,
            modifier_desc,
            fg_app,
            temp_c,
            bat_pct,
            state.health.cpu_available,
            state.health.gpu_available,
            state.health.battery_available,
            state.health.memory_available,
        );
    } else {
        println!("==================================================");
        println!("           LYNX SYSTEM & DAEMON STATUS            ");
        println!("==================================================");
        println!("• Native Daemon:");
        if let Some(pid) = daemon_pid {
            println!("  Running       : YES (PID: {})", pid);
            println!("  PID Lockfile  : {}", DaemonLifecycle::pid_file_path().display());
        } else {
            println!("  Running       : NO (Stopped)");
        }
        println!("• Profile Engine:");
        println!("  Active Profile: {}", active_profile);
        println!(
            "  Lifecycle     : {} ({})",
            active_status,
            if is_dirty { "DIRTY - Interrupted" } else { "Clean" }
        );
        println!("  Platform      : {}", device_platform);
        println!("• Runtime Adaptive Controller:");
        println!("  Modifier      : {} ({})", modifier, modifier_desc);
        println!("  Foreground App: {}", fg_app);
        println!("• System Telemetry & Health:");
        println!("  Temperature   : {:.1}°C", temp_c);
        println!("  Battery Level : {}%", bat_pct);
        println!(
            "  Sensors Health: CPU({}) GPU({}) Batt({}) Mem({})",
            if state.health.cpu_available { "OK" } else { "FAIL" },
            if state.health.gpu_available { "OK" } else { "FAIL" },
            if state.health.battery_available { "OK" } else { "FAIL" },
            if state.health.memory_available { "OK" } else { "FAIL" },
        );
        println!("==================================================");
    }
}

fn handle_profile_list() {
    println!("Available Performance Policy Profiles:");
    for p in list_profiles() {
        println!("  • {:<12} : {}", p.name, p.description);
    }
}

fn handle_profile_show(name: &str) {
    match get_profile_by_name(name) {
        Some(p) => {
            println!("{}", p.display());
        }
        None => {
            Logger::error(format!(
                "Profile '{}' not found. Run 'lynxd profile list' to view available profiles.",
                name
            ));
            std::process::exit(1);
        }
    }
}

fn handle_profile_validate() {
    Logger::info("Validating all performance policy profiles against hardware safety bounds...");
    let profiles = list_profiles();
    let mut all_valid = true;

    for p in &profiles {
        match ProfileValidator::validate(p) {
            Ok(_) => {
                println!(
                    "  [OK] Profile '{:<12}' satisfies all thermal and mathematical safety bounds.",
                    p.name
                );
            }
            Err(errors) => {
                all_valid = false;
                Logger::error(format!("Profile '{}' failed validation:", p.name));
                for e in errors {
                    eprintln!("    [-] {}", e);
                }
            }
        }
    }

    if all_valid {
        Logger::info("All 4 profiles successfully passed hardware safety validation.");
    } else {
        Logger::error("Validation errors encountered!");
        std::process::exit(1);
    }
}

fn handle_profile_apply(name: &str, dry_run: bool) {
    let profile = match get_profile_by_name(name) {
        Some(p) => p,
        None => {
            Logger::error(format!(
                "Profile '{}' not found. Run 'lynxd profile list' to view available profiles.",
                name
            ));
            std::process::exit(1);
        }
    };

    if let Err(errors) = ProfileValidator::validate(&profile) {
        Logger::error(format!(
            "Profile '{}' failed safety validation! Cannot apply.",
            name
        ));
        for e in errors {
            eprintln!("  [-] {}", e);
        }
        std::process::exit(1);
    }

    let plan = ExecutionPlan::build(&profile);
    match ProfileExecutor::execute(&plan, dry_run) {
        Ok(report) => {
            if !dry_run {
                println!("{}", report.display());
            }
        }
        Err(e) => {
            Logger::error(format!("Execution failed: {}", e));
            std::process::exit(1);
        }
    }
}

fn handle_profile_current() {
    match ProfileStateManager::load_active() {
        Ok(Some(state)) => {
            let snap_count = ProfileStateManager::load_snapshot().map(|s| s.len()).unwrap_or(0);
            println!("Active Profile Persistent Status:");
            println!("  • Current Profile : {}", state.active_profile);
            println!("  • Applied Epoch   : {}", state.timestamp);
            println!("  • Device Platform : {}", state.device);
            println!("  • Lifecycle Status: {}", state.status);
            println!(
                "  • Crash Recovery  : {}",
                if state.dirty {
                    "DIRTY (Interrupted transaction detected!)"
                } else {
                    "CLEAN (Transaction fully committed)"
                }
            );
            println!("  • Snapshot Backup : {} nodes recorded in last_snapshot.json", snap_count);
        }
        Ok(None) => {
            println!("Active Profile Persistent Status: No active profile recorded yet (OEM baseline).");
        }
        Err(e) => {
            Logger::error(format!("Failed to read active profile state: {}", e));
            std::process::exit(1);
        }
    }
}

fn handle_profile_revert() {
    Logger::info("Reverting kernel sysfs nodes to last known good snapshot...");
    match ProfileStateManager::revert() {
        Ok(report) => {
            println!("Hardware State Revert Report:");
            println!("  • Restored Nodes : {} nodes", report.restored_nodes.len());
            for (idx, (path, val)) in report.restored_nodes.iter().enumerate() {
                println!("      [{}] {} ──> '{}'", idx + 1, path.display(), val);
            }
            if !report.skipped_nodes.is_empty() {
                println!("  • Skipped Nodes  : {} nodes (non-existent/offline)", report.skipped_nodes.len());
                for (path, _) in &report.skipped_nodes {
                    println!("      [-] {}", path.display());
                }
            }
            println!(
                "  • Latency        : {} µs ({:.2} ms)",
                report.elapsed_micros,
                report.elapsed_micros as f64 / 1000.0
            );
            Logger::info("Hardware baseline successfully restored.");
        }
        Err(e) => {
            Logger::error(format!("Failed to revert to snapshot: {}", e));
            std::process::exit(1);
        }
    }
}

fn handle_profile_recover() {
    Logger::info("Checking system profile integrity for boot recovery...");
    match ProfileStateManager::recover_boot() {
        Ok(RecoveryReport::RecoveredFromDirty {
            previous_profile,
            restored_count,
            restored_to,
        }) => {
            Logger::warn(format!(
                "RECOVERED: Interrupted transaction from '{}' detected. Restored {} nodes and reset to '{}'.",
                previous_profile, restored_count, restored_to
            ));
        }
        Ok(RecoveryReport::CleanBaselineVerified {
            active_profile,
            applied_at,
        }) => {
            Logger::info(format!(
                "CLEAN: Active profile '{}' verified (Applied at epoch {}). No recovery required.",
                active_profile, applied_at
            ));
        }
        Ok(RecoveryReport::NoPriorState) => {
            Logger::info("CLEAN: No prior state recorded. System running on OEM baseline.");
        }
        Err(e) => {
            Logger::error(format!("Boot recovery encountered an error: {}", e));
            std::process::exit(1);
        }
    }
}

fn handle_profile_journal() {
    println!("Lynx Hardware Transaction & Recovery Journal:");
    match ProfileStateManager::load_journal(50) {
        Ok(lines) => {
            if lines.is_empty() {
                println!("  [INFO] Journal is empty. No transaction records found.");
            } else {
                for line in &lines {
                    println!("  {}", line);
                }
            }
        }
        Err(e) => {
            Logger::error(format!("Failed to read transaction journal: {}", e));
            std::process::exit(1);
        }
    }
}

fn handle_daemon_run() {
    if let Err(e) = DaemonRunner::run() {
        Logger::error(format!("Daemon exited with error: {}", e));
        std::process::exit(1);
    }
}

fn handle_daemon_status() {
    match DaemonLifecycle::check_status() {
        Some(pid) => {
            println!("Lynx Daemon Status: RUNNING");
            println!("  • Process ID  : {}", pid);
            println!("  • PID Lockfile: {}", DaemonLifecycle::pid_file_path().display());
        }
        None => {
            println!("Lynx Daemon Status: STOPPED (No active background process)");
        }
    }
}

fn handle_daemon_stop() {
    Logger::info("Sending termination signal to Lynx Daemon...");
    match DaemonLifecycle::stop_daemon() {
        Ok(_) => {
            Logger::info("Stop signal successfully delivered to daemon process.");
        }
        Err(e) => {
            Logger::error(format!("Failed to stop daemon: {}", e));
            std::process::exit(1);
        }
    }
}

fn handle_probe() {
    let (vendor, board) = ScoreBoard::evaluate();
    Logger::info(format!("Identified Platform: {}", vendor));
    println!("  • MTK Score   : {}", board.mtk_score);
    println!("  • Qualcomm Score: {}", board.qcom_score);
    if !board.dt_compatible.is_empty() {
        println!("  • DT Compatible : {}", board.dt_compatible);
    }
    println!("  • Detected Signals:");
    for sig in &board.detected_signals {
        println!("      [+] {}", sig);
    }
    if board.detected_signals.is_empty() {
        println!("      [-] No specific vendor driver signature matched.");
    }
}

fn handle_sysfs_read(path: &str) {
    match SysfsReader::read_trimmed(path) {
        Ok(val) => {
            println!("{}", val);
        }
        Err(e) => {
            Logger::error(format!("{}", e));
            std::process::exit(1);
        }
    }
}

fn handle_sysfs_write(path: &str, value: &str, flags: &[String]) {
    let dry_run = flags.iter().any(|f| f == "--dry-run");
    let force_perm = flags.iter().any(|f| f == "--force-perm");

    let mode = if force_perm {
        WriteMode::ForcePermission
    } else {
        WriteMode::Direct
    };

    if dry_run {
        let report = SysfsWriter::dry_run(path, value);
        println!("{}", report.display());
    } else {
        match SysfsWriter::write(path, value, mode) {
            Ok(_) => {
                Logger::info(format!("Successfully applied '{}' -> {}", value, path));
            }
            Err(e) => {
                Logger::error(format!("{}", e));
                std::process::exit(1);
            }
        }
    }
}

fn handle_benchmark() {
    Logger::info("Starting Lynx Real-Workload Hardware Benchmark...");
    let start_total = Instant::now();
    let mut read_count = 0usize;
    let mut nodes_tested = Vec::new();

    // 1. CPU nodes
    for idx in 0..8 {
        let cur_f = format!("/sys/devices/system/cpu/cpu{}/cpufreq/scaling_cur_freq", idx);
        let gov = format!("/sys/devices/system/cpu/cpufreq/policy{}/scaling_governor", idx);
        let avail_f = format!("/sys/devices/system/cpu/cpufreq/policy{}/scaling_available_frequencies", idx);

        for n in [cur_f, gov, avail_f] {
            if SysfsReader::exists(&n) {
                nodes_tested.push(n);
            }
        }
    }

    // 2. Battery nodes
    for b in [
        "/sys/class/power_supply/battery/capacity",
        "/sys/class/power_supply/battery/temp",
        "/sys/class/power_supply/battery/current_now",
        "/sys/class/power_supply/battery/voltage_now",
        "/sys/class/power_supply/battery/status",
    ] {
        if SysfsReader::exists(b) {
            nodes_tested.push(b.to_string());
        }
    }

    // 3. Vendor nodes
    for v in [
        MtkHardware::PPM_ENABLED,
        MtkHardware::CPUFREQ_POWER_MODE,
        MtkHardware::GED_HAL_FREQ,
        MtkHardware::GED_HAL_UTIL,
        QcomHardware::KGSL_GPUCLK,
        QcomHardware::KGSL_DEVFREQ_CUR,
        QcomHardware::KGSL_GPUBUSY,
    ] {
        if SysfsReader::exists(v) {
            nodes_tested.push(v.to_string());
        }
    }

    println!("Target workload: {} active hardware nodes detected on device.", nodes_tested.len());

    let mut latencies_micros = Vec::new();

    for node in &nodes_tested {
        let t0 = Instant::now();
        let _ = SysfsReader::read_trimmed(node);
        let el = t0.elapsed().as_micros();
        latencies_micros.push(el);
        read_count += 1;
    }

    let total_elapsed = start_total.elapsed();
    let total_micros = total_elapsed.as_micros();
    let avg_micros = if read_count > 0 { total_micros / read_count as u128 } else { 0 };

    println!("--------------------------------------------------");
    println!("Benchmark Results (Real Kernel Hardware Nodes):");
    println!("  • Operations Executed : {}", read_count);
    println!("  • Total Elapsed Time  : {} µs ({:.2} ms)", total_micros, total_elapsed.as_secs_f64() * 1000.0);
    println!("  • Average Per-Node I/O: {} µs ({:.3} ms)", avg_micros, avg_micros as f64 / 1000.0);
    println!("--------------------------------------------------");

    Logger::info("Benchmark completed safely without kernel mutations.");
}

fn handle_state(args: &[String]) {
    if args.len() < 3 {
        print_usage();
        return;
    }
    match args[2].as_str() {
        "get" => {
            if args.len() < 4 {
                Logger::error("Missing key: lynxd state get <key> [default]");
                std::process::exit(1);
            }
            let key = &args[3];
            let default_val = if args.len() >= 5 { args[4].as_str() } else { "" };
            let val = ConfigStore::get(key).unwrap_or_else(|| default_val.to_string());
            println!("{}", val);
        }
        "set" => {
            if args.len() < 4 {
                Logger::error("Missing arguments: lynxd state set <key> <val> [type] | <k1=v1> [k2=v2...]");
                std::process::exit(1);
            }
            let mut pairs: Vec<(String, String, Option<String>)> = Vec::new();
            if args[3].contains('=') {
                for item in &args[3..] {
                    if let Some(eq_idx) = item.find('=') {
                        let k = item[..eq_idx].trim().to_string();
                        let v = item[eq_idx + 1..].trim().to_string();
                        if !k.is_empty() {
                            pairs.push((k, v, None));
                        }
                    }
                }
            } else if args.len() >= 5 {
                let type_hint = args.get(5).cloned();
                pairs.push((args[3].clone(), args[4].clone(), type_hint));
            } else {
                Logger::error("Invalid state set arguments.");
                std::process::exit(1);
            }

            if let Err(e) = ConfigStore::set_batch(&pairs) {
                Logger::error(format!("Failed to update config.json: {}", e));
                std::process::exit(1);
            }
        }
        "list" | "dump" => {
            let path = ConfigStore::resolve_config_path();
            if let Ok(content) = std::fs::read_to_string(path) {
                println!("{}", content);
            } else {
                println!("{{}}");
            }
        }
        other => {
            Logger::error(format!("Unknown state subcommand: '{}'", other));
            print_usage();
            std::process::exit(1);
        }
    }
}

fn handle_charging(args: &[String]) {
    if args.len() < 3 {
        ChargingRegulator::apply_from_config();
        return;
    }
    match args[2].as_str() {
        "apply" => {
            ChargingRegulator::apply_from_config();
        }
        "lock" => {
            ChargingRegulator::reapply_lock();
        }
        "bypass" => {
            ChargingRegulator::apply_bypass_charging();
        }
        "extreme" => {
            let target_soc = args.get(3).and_then(|s| s.parse::<u32>().ok()).unwrap_or(90);
            let allow_lockout = args.get(4).map(|s| s != "false").unwrap_or(true);
            let target_ma = args.get(5).and_then(|s| s.parse::<u32>().ok()).unwrap_or(6000);
            ChargingRegulator::apply_extreme_charging(target_soc, allow_lockout, target_ma);
        }
        "regulated" => {
            let target_ma = args.get(3).and_then(|s| s.parse::<u32>().ok()).unwrap_or(1500);
            ChargingRegulator::apply_regulated_charging(target_ma);
        }
        "status" => {
            println!("{}", ChargingRegulator::status_json());
        }
        "tick" => {
            ChargingRegulator::tick();
        }
        other => {
            Logger::error(format!("Unknown charging subcommand: '{}'", other));
            print_usage();
            std::process::exit(1);
        }
    }
}

fn handle_system(args: &[String]) {
    if args.len() < 3 || args[2] == "apply" {
        println!("{}", SystemController::apply());
    } else {
        match args[2].as_str() {
            "boeffla-status" | "boeffla" => {
                println!("{}", SystemController::boeffla_status_json());
            }
            "doze-info" | "doze" if args.len() == 3 => {
                println!("{}", SystemController::doze_info_json());
            }
            "set-doze" | "doze" => {
                let enabled = args.get(3).map(|s| s == "1" || s == "true").unwrap_or(true);
                println!("{}", SystemController::set_doze(enabled));
            }
            "set-selinux" | "selinux" => {
                let enforcing = args.get(3).map(|s| s == "1" || s == "enforcing").unwrap_or(true);
                println!("{}", SystemController::set_selinux(enforcing));
            }
            "set-printk" | "printk" => {
                let silent = args.get(3).map(|s| s == "silent" || s == "0").unwrap_or(true);
                println!("{}", SystemController::set_printk(silent));
            }
            "top-wakelocks" | "wakelocks" => {
                println!("{}", SystemController::top_wakelocks());
            }
            "log-export" | "export-log" => {
                println!("{}", SystemController::export_diagnostic_zip());
            }
            other => {
                Logger::error(format!("Unknown system subcommand: '{}'", other));
                print_usage();
                std::process::exit(1);
            }
        }
    }
}

fn handle_display(args: &[String]) {
    if args.len() < 3 {
        println!("{}", DisplayController::info_json());
        return;
    }
    match args[2].as_str() {
        "info" => println!("{}", DisplayController::info_json()),
        "set-refresh-rate" | "refresh-rate" | "set_refresh_rate" => {
            let hz = args.get(3).and_then(|s| s.parse::<u32>().ok()).unwrap_or(60);
            println!("{}", DisplayController::set_refresh_rate(hz));
        }
        "restore-refresh-rate" | "restore" => {
            println!("{}", DisplayController::restore_refresh_rate());
        }
        "touch-boost" | "touch" => {
            let enable = args.get(3).map(|s| s == "1" || s == "true" || s == "on").unwrap_or(true);
            println!("{}", DisplayController::apply_touch_boost(enable));
        }
        other => {
            Logger::error(format!("Unknown display subcommand: '{}'", other));
            print_usage();
            std::process::exit(1);
        }
    }
}


fn handle_applist(args: &[String]) {
    if args.len() < 3 {
        println!("Packages in performance list:");
        for app in ApplistController::list() {
            println!("  {}", app);
        }
        return;
    }
    match args[2].as_str() {
        "list" => {
            for app in ApplistController::list() {
                println!("{}", app);
            }
        }
        "add" => {
            if let Some(pkg) = args.get(3) {
                match ApplistController::add(pkg) {
                    Ok(msg) => println!("{}", msg),
                    Err(e) => {
                        Logger::error(e);
                        std::process::exit(1);
                    }
                }
            } else {
                Logger::error("Usage: lynxd applist add <package>");
                std::process::exit(1);
            }
        }
        "remove" => {
            if let Some(pkg) = args.get(3) {
                match ApplistController::remove(pkg) {
                    Ok(msg) => println!("{}", msg),
                    Err(e) => {
                        Logger::error(e);
                        std::process::exit(1);
                    }
                }
            } else {
                Logger::error("Usage: lynxd applist remove <package>");
                std::process::exit(1);
            }
        }
        "check" => {
            if let Some(pkg) = args.get(3) {
                let present = ApplistController::check(pkg);
                println!("{}", if present { "1" } else { "0" });
            } else {
                Logger::error("Usage: lynxd applist check <package>");
                std::process::exit(1);
            }
        }
        other => {
            Logger::error(format!("Unknown applist subcommand: '{}'", other));
            print_usage();
            std::process::exit(1);
        }
    }
}

fn main() {
    let args: Vec<String> = env::args().collect();

    if args.len() < 2 {
        print_usage();
        return;
    }

    match args[1].as_str() {
        "boot" => {
            std::process::exit(LifecycleHooks::run_boot());
        }
        "uninstall" => {
            std::process::exit(LifecycleHooks::run_uninstall());
        }
        "action" => {
            std::process::exit(LifecycleHooks::run_action());
        }
        "flasher" => {
            let sub = args.get(2).map(|s| s.as_str()).unwrap_or("list_backups");
            match sub {
                "backup" => match FlasherController::backup_boot() {
                    Ok(msg) => println!("{}", msg),
                    Err(e) => {
                        eprintln!("{}", e);
                        std::process::exit(1);
                    }
                },
                "list_backups" | "list-backups" | "list" => {
                    println!("{}", FlasherController::list_backups_json());
                }
                "restore" => {
                    if let Some(file) = args.get(3) {
                        match FlasherController::restore_boot(file) {
                            Ok(msg) => println!("{}", msg),
                            Err(e) => {
                                eprintln!("{}", e);
                                std::process::exit(1);
                            }
                        }
                    } else {
                        eprintln!("Usage: lynxd flasher restore <backup_file>");
                        std::process::exit(1);
                    }
                }
                "flash" => {
                    if let Some(zip) = args.get(3) {
                        match FlasherController::flash_anykernel(zip) {
                            Ok(msg) => println!("{}", msg),
                            Err(e) => {
                                eprintln!("{}", e);
                                std::process::exit(1);
                            }
                        }
                    } else {
                        eprintln!("Usage: lynxd flasher flash <zip_path>");
                        std::process::exit(1);
                    }
                }
                _ => {
                    eprintln!("Usage: lynxd flasher {{backup|list_backups|restore <file>|flash <zip>}}");
                    std::process::exit(1);
                }
            }
        }
        "tui" | "menu" => {
            TuiController::run();
        }
        "status" => {
            let json = args.iter().any(|a| a == "--json");
            handle_status(json);
        }
        "telemetry" => {
            let pretty = args.iter().any(|a| a == "--pretty");
            handle_telemetry(pretty);
        }
        "state" => {
            handle_state(&args);
        }
        "charging" => {
            handle_charging(&args);
        }
        "profile" => {
            if args.len() < 3 {
                print_usage();
                return;
            }
            match args[2].as_str() {
                "list" => handle_profile_list(),
                "show" => {
                    if args.len() < 4 {
                        Logger::error("Missing profile name: lynxd profile show <name>");
                        std::process::exit(1);
                    }
                    handle_profile_show(&args[3]);
                }
                "validate" => handle_profile_validate(),
                "apply" => {
                    if args.len() < 4 {
                        Logger::error("Missing profile name: lynxd profile apply <name> [--dry-run]");
                        std::process::exit(1);
                    }
                    let dry_run = args.iter().any(|a| a == "--dry-run");
                    handle_profile_apply(&args[3], dry_run);
                }
                "verify" => {
                    let json = args.iter().any(|a| a == "--json");
                    let prof = args.get(3).filter(|s| !s.starts_with('-')).map(|s| s.as_str());
                    let code = ProfileVerifier::verify(prof, json);
                    std::process::exit(code);
                }
                "current" | "status" => handle_profile_current(),
                "revert" => handle_profile_revert(),
                "recover" => handle_profile_recover(),
                "journal" | "history" => handle_profile_journal(),
                other => {
                    Logger::error(format!("Unknown profile subcommand: '{}'", other));
                    print_usage();
                    std::process::exit(1);
                }
            }
        }
        "thermal" => {
            let sub = args.get(2).map(|s| s.as_str()).unwrap_or("status");
            match sub {
                "status" | "info" => println!("{}", ThermalController::status_json()),
                "mode" | "set-mode" => {
                    let mode = args.get(3).map(|s| s.as_str()).unwrap_or("balanced");
                    println!("{}", ThermalController::set_mode(mode));
                }
                "limit" | "set-limit" => {
                    let temp = args.get(3).and_then(|s| s.parse::<i32>().ok()).unwrap_or(55);
                    println!("{}", ThermalController::set_limit(temp));
                }
                "restore" => println!("{}", ThermalController::set_mode("restore")),
                _ => println!("{}", ThermalController::status_json()),
            }
        }
        "game" => {
            let sub = args.get(2).map(|s| s.as_str()).unwrap_or("status");
            match sub {
                "on" | "enable" | "start" => {
                    let pid = args.get(3).and_then(|s| s.parse::<u32>().ok());
                    println!("{}", GameController::game_on(pid));
                }
                "off" | "disable" | "stop" => {
                    println!("{}", GameController::game_off());
                }
                "optimize" | "optimize-pid" => {
                    if let Some(pid) = args.get(3).and_then(|s| s.parse::<u32>().ok()) {
                        GameController::optimize_pid(pid);
                        println!("Process {} optimized for low latency gaming.", pid);
                    } else {
                        eprintln!("Usage: lynxd game optimize-pid <PID>");
                    }
                }
                "status" | "info" => println!("{}", GameController::status_json()),
                _ => println!("{}", GameController::status_json()),
            }
        }
        "display" => {
            handle_display(&args);
        }
        "oem" => {
            let sub = args.get(2).map(|s| s.as_str()).unwrap_or("freeze");
            match sub {
                "freeze" => {
                    ThermalController::freeze_oem_throttlers();
                    println!("OEM background throttlers frozen.");
                }
                "unfreeze" | "restore" => {
                    ThermalController::unfreeze_oem_throttlers();
                    println!("OEM background throttlers unfrozen.");
                }
                _ => eprintln!("Usage: lynxd oem freeze|unfreeze"),
            }
        }
        "cpuset" => {
            let sub = args.get(2).map(|s| s.as_str()).unwrap_or("info");
            match sub {
                "info" | "list" => println!("{}", CpusetController::info_json()),
                "apply" => {
                    let preset = args.get(3).map(|s| s.as_str()).unwrap_or("balanced");
                    println!("{}", CpusetController::apply_preset(preset));
                }
                "set" => {
                    if args.len() < 5 {
                        eprintln!("Usage: lynxd cpuset set <group> <cores>");
                        std::process::exit(1);
                    }
                    println!("{}", CpusetController::set_group(&args[3], &args[4]));
                }
                _ => println!("{}", CpusetController::info_json()),
            }
        }
        "cpuidle" => {
            let sub = args.get(2).map(|s| s.as_str()).unwrap_or("info");
            match sub {
                "info" | "list" => println!("{}", CpuIdleController::info_json()),
                "apply" => {
                    let preset = args.get(3).map(|s| s.as_str()).unwrap_or("balanced");
                    println!("{}", CpuIdleController::apply_preset(preset));
                }
                "set-state" | "set_state" => {
                    let idx = args.get(3).and_then(|s| s.parse::<usize>().ok()).unwrap_or(0);
                    let disabled = args.get(4).map(|s| s == "1" || s == "true").unwrap_or(false);
                    println!("{}", CpuIdleController::set_state(idx, disabled));
                }
                _ => println!("{}", CpuIdleController::info_json()),
            }
        }
        "swap" => {
            let sub = args.get(2).map(|s| s.as_str()).unwrap_or("info");
            match sub {
                "info" | "list" => println!("{}", SwapController::info_json()),
                "set" => {
                    let size = args.get(3).map(|s| s.as_str()).unwrap_or("1G");
                    match SwapController::set(size) {
                        Ok(msg) => println!("{}", msg),
                        Err(e) => {
                            eprintln!("Error: {}", e);
                            std::process::exit(1);
                        }
                    }
                }
                "enable" => match SwapController::enable() {
                    Ok(msg) => println!("{}", msg),
                    Err(e) => {
                        eprintln!("Error: {}", e);
                        std::process::exit(1);
                    }
                },
                "disable" => match SwapController::disable() {
                    Ok(msg) => println!("{}", msg),
                    Err(e) => {
                        eprintln!("Error: {}", e);
                        std::process::exit(1);
                    }
                },
                "remove" => match SwapController::remove() {
                    Ok(msg) => println!("{}", msg),
                    Err(e) => {
                        eprintln!("Error: {}", e);
                        std::process::exit(1);
                    }
                },
                _ => println!("{}", SwapController::info_json()),
            }
        }
        "maintenance" | "ccleaner" => {
            let sub = args.get(2).map(|s| s.as_str()).unwrap_or("run");
            match sub {
                "run" => {
                    let force = args.iter().any(|a| a == "--force" || a == "-f");
                    println!("{}", MaintenanceController::run(force));
                }
                "status" => {
                    let busy = MaintenanceController::is_device_busy();
                    println!("{{\"busy\":{}}}", busy);
                }
                _ => {
                    println!("{}", MaintenanceController::run(false));
                }
            }
        }
        "system" => {
            handle_system(&args);
        }
        "applist" => {
            handle_applist(&args);
        }
        "cluster" => {
            let sub = args.get(2).map(|s| s.as_str()).unwrap_or("topology");
            match sub {
                "topology" | "info" => {
                    println!("{}", ClusterController::topology_json());
                }
                "set_freq" | "set-freq" => {
                    let pid = args.get(3).and_then(|s| s.parse::<usize>().ok()).unwrap_or(0);
                    let min_f = args.get(4).and_then(|s| s.parse::<u64>().ok());
                    let max_f = args.get(5).and_then(|s| s.parse::<u64>().ok());
                    match ClusterController::set_freq(pid, min_f, max_f) {
                        Ok(msg) => println!("{}", msg),
                        Err(err) => {
                            eprintln!("{}", err);
                            std::process::exit(1);
                        }
                    }
                }
                "lock" | "lock_freq" | "lock-freq" => {
                    let pid = args.get(3).and_then(|s| s.parse::<usize>().ok()).unwrap_or(0);
                    let min_f = args.get(4).and_then(|s| s.parse::<u64>().ok());
                    let max_f = args.get(5).and_then(|s| s.parse::<u64>().ok());
                    match ClusterController::lock_freq(pid, min_f, max_f) {
                        Ok(msg) => println!("{}", msg),
                        Err(err) => {
                            eprintln!("{}", err);
                            std::process::exit(1);
                        }
                    }
                }
                "unlock" | "unlock_freq" | "unlock-freq" => {
                    let pid = args.get(3).and_then(|s| s.parse::<usize>().ok()).unwrap_or(0);
                    match ClusterController::unlock_freq(pid) {
                        Ok(msg) => println!("{}", msg),
                        Err(err) => {
                            eprintln!("{}", err);
                            std::process::exit(1);
                        }
                    }
                }
                "set_gov" | "set-gov" => {
                    let pid = args.get(3).and_then(|s| s.parse::<usize>().ok()).unwrap_or(0);
                    let gov = args.get(4).map(|s| s.as_str()).unwrap_or("schedutil");
                    match ClusterController::set_gov(pid, gov) {
                        Ok(msg) => println!("{}", msg),
                        Err(err) => {
                            eprintln!("{}", err);
                            std::process::exit(1);
                        }
                    }
                }
                "set_schedutil_preset" | "set-schedutil-preset" | "schedutil-preset" => {
                    let preset = args.get(3).map(|s| s.as_str()).unwrap_or("balanced");
                    println!("{}", ClusterController::set_schedutil_preset(preset));
                }
                _ => {
                    println!("{}", ClusterController::topology_json());
                }
            }
        }
        "gpu" => {
            let sub = args.get(2).map(|s| s.as_str()).unwrap_or("info");
            match sub {
                "info" => {
                    println!("{}", GpuController::info_json());
                }
                "set_freq" | "set-freq" => {
                    let min_m = args.get(3).and_then(|s| s.parse::<u64>().ok()).unwrap_or(0);
                    let max_m = args.get(4).and_then(|s| s.parse::<u64>().ok());
                    println!("{}", GpuController::set_freq(min_m, max_m));
                }
                "set_gov" | "set-gov" => {
                    let gov = args.get(3).map(|s| s.as_str()).unwrap_or("simple_ondemand");
                    println!("{}", GpuController::set_gov(gov));
                }
                "boost" | "set_boost" | "set-boost" => {
                    let lvl = args.get(3).and_then(|s| s.parse::<u32>().ok()).unwrap_or(1);
                    println!("{}", GpuController::set_boost(lvl));
                }
                _ => {
                    println!("{}", GpuController::info_json());
                }
            }
        }
        "memory" | "ram" | "zram" => {
            let sub = args.get(2).map(|s| s.as_str()).unwrap_or("apply");
            match sub {
                "apply" => {
                    println!("{}", MemoryController::apply_ram());
                }
                "zram-info" | "info" => {
                    println!("{}", MemoryController::zram_info_json());
                }
                "zram-set" | "set" => {
                    let mut size_arg = None;
                    let mut algo_arg = None;
                    for a in args.iter().skip(3) {
                        if let Some(s) = a.strip_prefix("size=") {
                            size_arg = Some(s);
                        } else if let Some(al) = a.strip_prefix("algo=") {
                            algo_arg = Some(al);
                        }
                    }
                    match MemoryController::set_zram(size_arg, algo_arg) {
                        Ok(msg) => println!("{}", msg),
                        Err(err) => {
                            eprintln!("{}", err);
                            std::process::exit(1);
                        }
                    }
                }
                "zram-disable" | "disable" => {
                    match MemoryController::disable_zram() {
                        Ok(msg) => println!("{}", msg),
                        Err(err) => {
                            eprintln!("{}", err);
                            std::process::exit(1);
                        }
                    }
                }
                "clean" | "compact" | "ccleaner" => {
                    println!("{}", MemoryController::clean_memory());
                }
                "vm-info" | "vm" => {
                    println!("{}", MemoryController::get_vm_tunables_json());
                }
                "vm-set" => {
                    if let (Some(p), Some(v)) = (args.get(3), args.get(4)) {
                        match MemoryController::set_vm_tunable(p, v) {
                            Ok(msg) => println!("{}", msg),
                            Err(e) => {
                                eprintln!("{}", e);
                                std::process::exit(1);
                            }
                        }
                    } else {
                        eprintln!("Usage: lynxd memory vm-set <param> <val>");
                        std::process::exit(1);
                    }
                }
                "vm-preset" => {
                    let preset = args.get(3).map(|s| s.as_str()).unwrap_or("balanced");
                    match MemoryController::apply_vm_preset(preset) {
                        Ok(msg) => println!("{}", msg),
                        Err(e) => {
                            eprintln!("{}", e);
                            std::process::exit(1);
                        }
                    }
                }
                _ => {
                    println!("{}", MemoryController::apply_ram());
                }
            }
        }
        "io" => {
            let sub = args.get(2).map(|s| s.as_str()).unwrap_or("apply");
            match sub {
                "info" => {
                    println!("{}", IoController::info_json());
                }
                "set-scheduler" | "set_scheduler" => {
                    let blk = args.get(3).map(|s| s.as_str()).unwrap_or("sd");
                    let sched = args.get(4).map(|s| s.as_str()).unwrap_or("none");
                    println!("{}", IoController::set_scheduler(blk, sched));
                }
                _ => {
                    println!("{}", IoController::apply_default());
                    for arg in args.iter().skip(2) {
                        if let Some(dash) = arg.find('-') {
                            let blk = &arg[..dash];
                            let sched = &arg[dash + 1..];
                            if !blk.is_empty() && !sched.is_empty() {
                                println!("{}", IoController::set_scheduler(blk, sched));
                            }
                        }
                    }
                }
            }
        }
        "network" | "net" => {
            let sub = args.get(2).map(|s| s.as_str()).unwrap_or("apply");
            match sub {
                "info" => println!("{}", NetworkController::info_json()),
                "game" => println!("{}", NetworkController::apply_game()),
                "balance" => println!("{}", NetworkController::apply_balance()),
                "set-algo" | "set_algo" => {
                    let algo = args.get(3).map(|s| s.as_str()).unwrap_or("cubic");
                    match NetworkController::set_tcp_algo(algo) {
                        Ok(msg) => println!("{}", msg),
                        Err(err) => {
                            eprintln!("{}", err);
                            std::process::exit(1);
                        }
                    }
                }
                other if other.starts_with("algo=") => {
                    let algo = &other[5..];
                    match NetworkController::set_tcp_algo(algo) {
                        Ok(msg) => println!("{}", msg),
                        Err(err) => {
                            eprintln!("{}", err);
                            std::process::exit(1);
                        }
                    }
                }
                _ => println!("{}", NetworkController::apply_universal()),
            }
        }
        "inspect" | "deep" => {
            let sub = args.get(2).map(|s| s.as_str()).unwrap_or("scan");
            match sub {
                "scan" => {
                    println!("{}", TunableScanner::scan_all_json());
                }
                "node" | "inspect" => {
                    if let Some(path) = args.get(3) {
                        if let Some(json) = TunableScanner::inspect_node_json(path) {
                            println!("{}", json);
                        } else {
                            std::process::exit(1);
                        }
                    } else {
                        Logger::error("Missing path: lynxd inspect node <path>");
                        std::process::exit(1);
                    }
                }
                "set" => {
                    if let (Some(path), Some(val)) = (args.get(3), args.get(4)) {
                        match TunableStore::set(path, val) {
                            Ok(msg) => println!("{}", msg),
                            Err(err) => {
                                eprintln!("{}", err);
                                std::process::exit(1);
                            }
                        }
                    } else {
                        Logger::error("Usage: lynxd inspect set <path> <val>");
                        std::process::exit(1);
                    }
                }
                _ => {
                    println!("{}", TunableScanner::scan_all_json());
                }
            }
        }
        "tunable" => {
            let sub = args.get(2).map(|s| s.as_str()).unwrap_or("list");
            match sub {
                "set" => {
                    if let (Some(path), Some(val)) = (args.get(3), args.get(4)) {
                        match TunableStore::set(path, val) {
                            Ok(msg) => println!("{}", msg),
                            Err(err) => {
                                eprintln!("{}", err);
                                std::process::exit(1);
                            }
                        }
                    } else {
                        Logger::error("Usage: lynxd tunable set <path> <val>");
                        std::process::exit(1);
                    }
                }
                "apply" => {
                    println!("{}", TunableStore::apply_all());
                }
                "reset" => {
                    let target = args.get(3).map(|s| s.as_str()).unwrap_or("all");
                    println!("{}", TunableStore::reset(target));
                }
                _ => {
                    println!("{}", TunableStore::list_json());
                }
            }
        }
        "daemon" => {
            if args.len() < 3 {
                handle_daemon_status();
                return;
            }
            match args[2].as_str() {
                "run" | "start" => handle_daemon_run(),
                "status" => handle_daemon_status(),
                "stop" => handle_daemon_stop(),
                other => {
                    Logger::error(format!("Unknown daemon subcommand: '{}'", other));
                    print_usage();
                    std::process::exit(1);
                }
            }
        }
        "probe" => handle_probe(),
        "sysfs" => {
            if args.len() < 3 {
                print_usage();
                return;
            }
            match args[2].as_str() {
                "read" => {
                    if args.len() < 4 {
                        Logger::error("Missing path argument: lynxd sysfs read <path>");
                        std::process::exit(1);
                    }
                    handle_sysfs_read(&args[3]);
                }
                "write" => {
                    if args.len() < 5 {
                        Logger::error("Missing arguments: lynxd sysfs write <path> <val> [--dry-run] [--force-perm]");
                        std::process::exit(1);
                    }
                    let flags = if args.len() > 5 { args[5..].to_vec() } else { Vec::new() };
                    handle_sysfs_write(&args[3], &args[4], &flags);
                }
                "test-write" => {
                    if args.len() < 5 {
                        Logger::error("Missing arguments: lynxd sysfs test-write <path> <val>");
                        std::process::exit(1);
                    }
                    let flags = vec!["--dry-run".to_string()];
                    handle_sysfs_write(&args[3], &args[4], &flags);
                }
                "benchmark" => handle_benchmark(),
                other => {
                    Logger::error(format!("Unknown sysfs subcommand: '{}'", other));
                    print_usage();
                    std::process::exit(1);
                }
            }
        }
        "version" | "-v" | "--version" => {
            println!("lynxd v{}", env!("CARGO_PKG_VERSION"));
        }
        "help" | "-h" | "--help" => print_usage(),
        unknown => {
            Logger::error(format!("Unknown command: '{}'", unknown));
            print_usage();
            std::process::exit(1);
        }
    }
}
