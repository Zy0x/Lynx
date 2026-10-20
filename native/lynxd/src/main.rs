pub mod apply;
pub mod core;
pub mod daemon;
pub mod decision;
pub mod hardware;
pub mod hud;
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
use crate::profile::user_catalog;
use crate::state::{ConfigStore, DeviceState, ProfileStateManager, RecoveryReport};
use crate::subsystems::{
    AdbController, ApplistController, ClusterController, CpuIdleController, CpusetController,
    DisplayController, FlasherController, GameController, GpuController, IoController,
    LifecycleHooks, MaintenanceController, MemoryController, NetworkController, SwapController,
    SystemController, ThermalController, TuiController,
};
use crate::subsystems::tui::ui;
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};
use crate::utils::Logger;

macro_rules! log_info {
    ($indonesian:literal, $english:literal $(, $argument:expr)* $(,)?) => {
        if crate::subsystems::tui::is_english() {
            Logger::info(format!($english $(, $argument)*));
        } else {
            Logger::info(format!($indonesian $(, $argument)*));
        }
    };
}

macro_rules! log_warn {
    ($indonesian:literal, $english:literal $(, $argument:expr)* $(,)?) => {
        if crate::subsystems::tui::is_english() {
            Logger::warn(format!($english $(, $argument)*));
        } else {
            Logger::warn(format!($indonesian $(, $argument)*));
        }
    };
}

macro_rules! log_error {
    ($indonesian:literal, $english:literal $(, $argument:expr)* $(,)?) => {
        if crate::subsystems::tui::is_english() {
            Logger::error(format!($english $(, $argument)*));
        } else {
            Logger::error(format!($indonesian $(, $argument)*));
        }
    };
}

fn print_usage() {
    if !crate::subsystems::tui::is_english() {
        println!("==================================================");
        println!("        LYNX NATIVE CORE ENGINE (lynxd) v{}    ", env!("CARGO_PKG_VERSION"));
        println!("==================================================");
        println!("Penggunaan: lynxd <perintah> [subperintah] [argumen...]");
        println!("\nPerintah:");
        println!("  boot                              Jalankan orkestrator boot modul native");
        println!("  uninstall                         Pulihkan sistem dan sysfs saat modul dihapus");
        println!("  action                            Jalankan pemeliharaan sistem dan pemadatan memori");
        println!("  flasher backup|list_backups|restore|flash");
        println!("  tui                               Buka antarmuka terminal interaktif");
        println!("  status [--json]                   Periksa status sistem, daemon, profil, dan modifier");
        println!("  telemetry [--pretty]              Tampilkan telemetri hardware langsung");
        println!("  state get <key> [default]         Baca key dari config.json");
        println!("  state set <k> <v> | <k=v>...      Perbarui satu atau beberapa key config.json secara atomik");
        println!("  state list                        Tampilkan isi config.json");
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
        println!("  system apply|wakeup-boost|boeffla-status|doze-info|set-doze|set-selinux|set-printk|top-wakelocks|log-export");
        println!("  applist list|add|remove|check     Kelola basis data aplikasi game dan target performa");
        println!("  cluster topology|set-freq|lock|unlock|set-gov|set-schedutil-preset");
        println!("  gpu info|set-freq|set-gov|set-boost");
        println!("  memory apply|zram-info|zram-set|zram-disable|clean|vm-info|vm-set|vm-preset");
        println!("  io apply|info|set-scheduler");
        println!("  media status|set <mode> <true|false> [--restart-player]|restore  Preferensi decoder dan penerapan boot");
        println!("  network apply|game|balance|set-algo|info");
        println!("  adb status|enable|disable|set-port Kelola keep-alive ADB nirkabel dan port tetap");
        println!("  inspect scan|node <path>          Periksa parameter hardware sysfs/procfs");
        println!("  tunable set|apply|list|reset      Kelola pengaturan kernel dengan pemulihan otomatis");
        println!("  daemon run|status|stop            Kelola daemon latar belakang adaptif native");
        println!("  probe                             Deteksi vendor SoC melalui pembobotan skor");
        println!("  sysfs read|write|test-write|benchmark");
        println!("  help                              Tampilkan bantuan ini");
        println!("==================================================");
        return;
    }
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
    println!("  system apply|wakeup-boost|boeffla-status|doze-info|set-doze|set-selinux|set-printk|top-wakelocks|log-export");
    println!("  applist list|add|remove|check     Manage game & performance target app database");
    println!("  cluster topology|set-freq|lock|unlock|set-gov|set-schedutil-preset");
    println!("  gpu info|set-freq|set-gov|set-boost");
    println!("  memory apply|zram-info|zram-set|zram-disable|clean|vm-info|vm-set|vm-preset");
    println!("  io apply|info|set-scheduler");
    println!("  media status|set <mode> <true|false> [--restart-player]|restore  Decoder preferences and boot persistence");
    println!("  network apply|game|balance|set-algo|info");
    println!("  adb status|enable|disable|set-port Manage wireless ADB keep-alive & persistent port lock");
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
        println!("{}", ui("           STATUS SISTEM & DAEMON            ", "           LYNX SYSTEM & DAEMON STATUS            "));
        println!("==================================================");
        println!("{}", ui("• Daemon Native:", "• Native Daemon:"));
        if let Some(pid) = daemon_pid {
            println!("  Running       : YES (PID: {})", pid);
            println!("  PID Lockfile  : {}", DaemonLifecycle::pid_file_path().display());
        } else {
            println!("{}", ui("  Berjalan      : TIDAK (Berhenti)", "  Running       : NO (Stopped)"));
        }
        println!("{}", ui("• Mesin Profil:", "• Profile Engine:"));
        println!("{}: {}", ui("  Profil Aktif", "  Active Profile"), active_profile);
        println!(
            "{}: {} ({})",
            ui("  Siklus        ", "  Lifecycle     "),
            active_status,
            if is_dirty { ui("KOTOR - Terputus", "DIRTY - Interrupted") } else { ui("Bersih", "Clean") }
        );
        println!("  Platform      : {}", device_platform);
        println!("{}", ui("• Pengendali Adaptif Runtime:", "• Runtime Adaptive Controller:"));
        println!("{}: {} ({})", ui("  Modifier", "  Modifier"), modifier, modifier_desc);
        println!("{}: {}", ui("  Aplikasi Depan", "  Foreground App"), fg_app);
        println!("{}", ui("• Telemetri & Kesehatan Sistem:", "• System Telemetry & Health:"));
        println!("{}: {:.1}°C", ui("  Suhu", "  Temperature"), temp_c);
        println!("{}: {}%", ui("  Level Baterai", "  Battery Level"), bat_pct);
        println!(
            "{}: CPU({}) GPU({}) {}({}) Mem({})",
            ui("  Kesehatan Sensor", "  Sensors Health"),
            if state.health.cpu_available { "OK" } else { "FAIL" },
            if state.health.gpu_available { "OK" } else { "FAIL" },
            ui("Baterai", "Batt"),
            if state.health.battery_available { "OK" } else { "FAIL" },
            if state.health.memory_available { "OK" } else { "FAIL" },
        );
        println!("==================================================");
    }
}

fn handle_profile_list() {
    println!("{}", ui("Profil Kebijakan Performa yang Tersedia:", "Available Performance Policy Profiles:"));
    for p in list_profiles() {
        println!("  • {:<12} : {}", p.name, p.description);
    }
    for profile in user_catalog::read_catalog() {
        if !matches!(profile.id.as_str(), "auto" | "balance" | "performance" | "extreme" | "powersave") {
            println!("  • {:<12} : {} (adaptif; basis {})", profile.id, profile.name, profile.base_profile);
        }
    }
}

fn handle_profile_show(name: &str) {
    match get_profile_by_name(name) {
        Some(p) => {
            if let Some(custom) = user_catalog::resolve_profile(name) {
                println!("{}: {} ({})\n{}: {}\n", ui("Profil Kustom", "Custom Profile"), custom.id, custom.name, ui("Basis native", "Native base"), custom.base_profile);
            }
            println!("{}", p.display());
        }
        None => {
            log_error!("Profil '{}' tidak ditemukan. Jalankan 'lynxd profile list' untuk melihat profil yang tersedia.", "Profile '{}' not found. Run 'lynxd profile list' to view available profiles.", name);
            std::process::exit(1);
        }
    }
}

fn handle_profile_validate() {
    log_info!("Memvalidasi kebijakan performa bawaan dan pengguna terhadap batas keamanan hardware...", "Validating all built-in and user performance policies against hardware safety bounds...");
    let mut profiles: Vec<(String, crate::profile::Profile)> = list_profiles()
        .into_iter()
        .map(|profile| (profile.name.to_string(), profile))
        .collect();
    for entry in user_catalog::read_catalog() {
        if let Some(profile) = get_profile_by_name(&entry.id) {
            if let Some(existing) = profiles.iter_mut().find(|(id, _)| *id == entry.id) {
                *existing = (entry.id, profile);
            } else {
                profiles.push((entry.id, profile));
            }
        }
    }
    let mut all_valid = true;

    for (id, p) in &profiles {
        match ProfileValidator::validate(p) {
            Ok(_) => {
                println!("  [OK] {}", if crate::subsystems::tui::is_english() { format!("Profile '{:<12}' satisfies all thermal and mathematical safety bounds.", id) } else { format!("Profil '{:<12}' memenuhi seluruh batas keamanan termal dan matematis.", id) });
            }
            Err(errors) => {
                all_valid = false;
                log_error!("Validasi profil '{}' gagal:", "Profile '{}' failed validation:", id);
                for e in errors {
                    eprintln!("    [-] {}", e);
                }
            }
        }
    }

    if all_valid {
        log_info!("Semua {} profil lolos validasi keamanan hardware.", "All {} profile entries passed hardware safety validation.", profiles.len());
    } else {
        log_error!("Ditemukan kesalahan validasi!", "Validation errors encountered!");
        std::process::exit(1);
    }
}

fn handle_profile_apply(name: &str, dry_run: bool) {
    let profile = match get_profile_by_name(name) {
        Some(p) => p,
        None => {
            log_error!("Profil '{}' tidak ditemukan. Jalankan 'lynxd profile list' untuk melihat profil yang tersedia.", "Profile '{}' not found. Run 'lynxd profile list' to view available profiles.", name);
            std::process::exit(1);
        }
    };

    if let Err(errors) = ProfileValidator::validate(&profile) {
        log_error!("Validasi keamanan profil '{}' gagal! Profil tidak dapat diterapkan.", "Profile '{}' failed safety validation! Cannot apply.", name);
        for e in errors {
            eprintln!("  [-] {}", e);
        }
        std::process::exit(1);
    }

    let display_name = if user_catalog::resolve_profile(name).is_some() { name } else { profile.name };
    let plan = ExecutionPlan::build_named(&profile, display_name);
    match ProfileExecutor::execute(&plan, dry_run) {
        Ok(report) => {
            if !dry_run {
                println!("{}", report.display());
            }
        }
        Err(e) => {
            log_error!("Eksekusi gagal: {}", "Execution failed: {}", e);
            std::process::exit(1);
        }
    }
}

fn handle_profile_current() {
    match ProfileStateManager::load_active() {
        Ok(Some(state)) => {
            let snap_count = ProfileStateManager::load_snapshot().map(|s| s.len()).unwrap_or(0);
            println!("{}", ui("Status Persisten Profil Aktif:", "Active Profile Persistent Status:"));
            println!("  • {}: {}", ui("Profil Saat Ini", "Current Profile"), state.active_profile);
            println!("  • {}: {}", ui("Epoch Penerapan", "Applied Epoch"), state.timestamp);
            println!("  • {}: {}", ui("Platform Perangkat", "Device Platform"), state.device);
            println!("  • {}: {}", ui("Status Siklus", "Lifecycle Status"), state.status);
            println!(
                "  • Crash Recovery  : {}",
                if state.dirty {
                    ui("KOTOR (Transaksi terputus terdeteksi!)", "DIRTY (Interrupted transaction detected!)")
                } else {
                    ui("BERSIH (Transaksi berhasil diselesaikan)", "CLEAN (Transaction fully committed)")
                }
            );
            println!("  • {}: {} {} last_snapshot.json", ui("Cadangan Snapshot", "Snapshot Backup"), snap_count, ui("node tercatat di", "nodes recorded in"));
        }
        Ok(None) => {
            println!("{}", ui("Status Persisten Profil Aktif: Belum ada profil aktif (setelan dasar OEM).", "Active Profile Persistent Status: No active profile recorded yet (OEM baseline)."));
        }
        Err(e) => {
            log_error!("Gagal membaca status profil aktif: {}", "Failed to read active profile state: {}", e);
            std::process::exit(1);
        }
    }
}

fn handle_profile_revert() {
    log_info!("Memulihkan node sysfs kernel ke snapshot terakhir yang diketahui baik...", "Reverting kernel sysfs nodes to last known good snapshot...");
    match ProfileStateManager::revert() {
        Ok(report) => {
            println!("{}", ui("Laporan Pemulihan Status Hardware:", "Hardware State Revert Report:"));
            println!("  • {}: {} {}", ui("Node Dipulihkan", "Restored Nodes"), report.restored_nodes.len(), ui("node", "nodes"));
            for (idx, (path, val)) in report.restored_nodes.iter().enumerate() {
                println!("      [{}] {} ──> '{}'", idx + 1, path.display(), val);
            }
            if !report.skipped_nodes.is_empty() {
                println!("  • {}: {} {} ({})", ui("Node Dilewati", "Skipped Nodes"), report.skipped_nodes.len(), ui("node", "nodes"), ui("tidak ada/offline", "non-existent/offline"));
                for (path, _) in &report.skipped_nodes {
                    println!("      [-] {}", path.display());
                }
            }
            println!(
                "  • Latency        : {} µs ({:.2} ms)",
                report.elapsed_micros,
                report.elapsed_micros as f64 / 1000.0
            );
            log_info!("Baseline hardware berhasil dipulihkan.", "Hardware baseline successfully restored.");
        }
        Err(e) => {
            log_error!("Gagal memulihkan snapshot: {}", "Failed to revert to snapshot: {}", e);
            std::process::exit(1);
        }
    }
}

fn handle_profile_recover() {
    log_info!("Memeriksa integritas profil sistem untuk pemulihan boot...", "Checking system profile integrity for boot recovery...");
    match ProfileStateManager::recover_boot() {
        Ok(RecoveryReport::RecoveredFromDirty {
            previous_profile,
            restored_count,
            restored_to,
        }) => {
            log_warn!("DIPULIHKAN: Transaksi terputus dari '{}' terdeteksi. {} node dipulihkan dan profil diatur ulang ke '{}'.", "RECOVERED: Interrupted transaction from '{}' detected. Restored {} nodes and reset to '{}'.", previous_profile, restored_count, restored_to);
        }
        Ok(RecoveryReport::CleanBaselineVerified {
            active_profile,
            applied_at,
        }) => {
            log_info!("BERSIH: Profil aktif '{}' terverifikasi (diterapkan pada epoch {}). Tidak perlu pemulihan.", "CLEAN: Active profile '{}' verified (Applied at epoch {}). No recovery required.", active_profile, applied_at);
        }
        Ok(RecoveryReport::NoPriorState) => {
            log_info!("BERSIH: Belum ada status sebelumnya. Sistem berjalan dengan baseline OEM.", "CLEAN: No prior state recorded. System running on OEM baseline.");
        }
        Err(e) => {
            log_error!("Pemulihan boot mengalami kesalahan: {}", "Boot recovery encountered an error: {}", e);
            std::process::exit(1);
        }
    }
}

fn handle_profile_journal() {
    println!("{}", ui("Jurnal Transaksi & Pemulihan Hardware Lynx:", "Lynx Hardware Transaction & Recovery Journal:"));
    match ProfileStateManager::load_journal(50) {
        Ok(lines) => {
            if lines.is_empty() {
                println!("{}", ui("  [INFO] Jurnal kosong. Tidak ada catatan transaksi.", "  [INFO] Journal is empty. No transaction records found."));
            } else {
                for line in &lines {
                    println!("  {}", line);
                }
            }
        }
        Err(e) => {
            log_error!("Gagal membaca jurnal transaksi: {}", "Failed to read transaction journal: {}", e);
            std::process::exit(1);
        }
    }
}

fn handle_daemon_run() {
    if let Err(e) = DaemonRunner::run() {
        log_error!("Daemon berhenti karena kesalahan: {}", "Daemon exited with error: {}", e);
        std::process::exit(1);
    }
}

fn handle_daemon_start() {
    if let Some(pid) = DaemonLifecycle::check_status() {
        println!("{}", ui(&format!("Daemon Lynx sudah berjalan (PID: {})", pid), &format!("Lynx Daemon is already running (PID: {})", pid)));
        return;
    }
    let lynxd_bin = std::env::current_exe().unwrap_or_else(|_| std::path::PathBuf::from("/data/adb/modules/Lynx/system/bin/lynxd"));
    let log_path = "/data/adb/modules/Lynx/logs/lynxd.log";
    let _ = std::fs::create_dir_all("/data/adb/modules/Lynx/logs");
    let log_file = std::fs::OpenOptions::new().create(true).append(true).open(log_path);
    if let Ok(file) = log_file {
        let err_file = file.try_clone().unwrap_or_else(|_| std::fs::OpenOptions::new().create(true).append(true).open(log_path).unwrap());
        match std::process::Command::new(&lynxd_bin)
            .args(["daemon", "run"])
            .stdin(std::process::Stdio::null())
            .stdout(std::process::Stdio::from(file))
            .stderr(std::process::Stdio::from(err_file))
            .spawn()
        {
            Ok(child) => {
                println!("{}", ui(&format!("Daemon Lynx berhasil diluncurkan di latar belakang (PID: {})", child.id()), &format!("Lynx Daemon launched successfully in background (PID: {})", child.id())));
            }
            Err(e) => {
                eprintln!("{}", ui(&format!("Gagal meluncurkan daemon di latar belakang: {}", e), &format!("Failed to launch daemon in background: {}", e)));
                std::process::exit(1);
            }
        }
    } else {
        eprintln!("{}", ui("Gagal membuka file log daemon", "Failed to open daemon log file"));
        std::process::exit(1);
    }
}

fn handle_daemon_status() {
    match DaemonLifecycle::check_status() {
        Some(pid) => {
            println!("{}", ui("Status Daemon Lynx: BERJALAN", "Lynx Daemon Status: RUNNING"));
            println!("  • {}: {}", ui("ID Proses", "Process ID"), pid);
            println!("  • {}: {}", ui("File Kunci PID", "PID Lockfile"), DaemonLifecycle::pid_file_path().display());
        }
        None => {
            println!("{}", ui("Status Daemon Lynx: BERHENTI (Tidak ada proses latar aktif)", "Lynx Daemon Status: STOPPED (No active background process)"));
            std::process::exit(1);
        }
    }
}

fn handle_daemon_stop() {
    log_info!("Mengirim sinyal penghentian ke daemon Lynx...", "Sending termination signal to Lynx Daemon...");
    match DaemonLifecycle::stop_daemon() {
        Ok(_) => {
            log_info!("Sinyal berhenti berhasil dikirim ke proses daemon.", "Stop signal successfully delivered to daemon process.");
        }
        Err(e) => {
            log_error!("Gagal menghentikan daemon: {}", "Failed to stop daemon: {}", e);
            std::process::exit(1);
        }
    }
}

fn handle_probe() {
    let (vendor, board) = ScoreBoard::evaluate();
    log_info!("Platform teridentifikasi: {}", "Identified Platform: {}", vendor);
    println!("  • MTK Score   : {}", board.mtk_score);
    println!("  • Qualcomm Score: {}", board.qcom_score);
    if !board.dt_compatible.is_empty() {
        println!("  • DT Compatible : {}", board.dt_compatible);
    }
    println!("{}", ui("  • Sinyal Terdeteksi:", "  • Detected Signals:"));
    for sig in &board.detected_signals {
        println!("      [+] {}", sig);
    }
    if board.detected_signals.is_empty() {
        println!("{}", ui("      [-] Tidak ada tanda tangan driver vendor yang cocok.", "      [-] No specific vendor driver signature matched."));
    }
}

fn handle_sysfs_read(path: &str) {
    match SysfsReader::read_trimmed(path) {
        Ok(val) => {
            println!("{}", val);
        }
        Err(e) => {
            log_error!("Gagal membaca node sysfs: {}", "Failed to read sysfs node: {}", e);
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
                log_info!("Berhasil menerapkan '{}' -> {}", "Successfully applied '{}' -> {}", value, path);
            }
            Err(e) => {
                log_error!("Gagal menulis node sysfs: {}", "Failed to write sysfs node: {}", e);
                std::process::exit(1);
            }
        }
    }
}

fn handle_benchmark() {
    log_info!("Memulai benchmark hardware Lynx dengan beban kerja nyata...", "Starting Lynx real-workload hardware benchmark...");
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

    println!("{}: {}", ui("Beban target", "Target workload"), if crate::subsystems::tui::is_english() { format!("{} active hardware nodes detected on device", nodes_tested.len()) } else { format!("{} node hardware aktif terdeteksi pada perangkat", nodes_tested.len()) });

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
    println!("{}", ui("Hasil Benchmark (Node Hardware Kernel Nyata):", "Benchmark Results (Real Kernel Hardware Nodes):"));
    println!("  • Operations Executed : {}", read_count);
    println!("  • Total Elapsed Time  : {} µs ({:.2} ms)", total_micros, total_elapsed.as_secs_f64() * 1000.0);
    println!("  • Average Per-Node I/O: {} µs ({:.3} ms)", avg_micros, avg_micros as f64 / 1000.0);
    println!("--------------------------------------------------");

    log_info!("Benchmark selesai dengan aman tanpa mengubah kernel.", "Benchmark completed safely without kernel mutations.");
}

fn handle_state(args: &[String]) {
    if args.len() < 3 {
        print_usage();
        return;
    }
    match args[2].as_str() {
        "get" => {
            if args.len() < 4 {
                log_error!("Key belum diisi: lynxd state get <key> [default]", "Missing key: lynxd state get <key> [default]");
                std::process::exit(1);
            }
            let key = &args[3];
            let default_val = if args.len() >= 5 { args[4].as_str() } else { "" };
            let val = ConfigStore::get(key).unwrap_or_else(|| default_val.to_string());
            println!("{}", val);
        }
        "set" => {
            if args.len() < 4 {
                log_error!("Argumen belum lengkap: lynxd state set <key> <val> [type] | <k1=v1> [k2=v2...]", "Missing arguments: lynxd state set <key> <val> [type] | <k1=v1> [k2=v2...]");
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
                log_error!("Argumen state set tidak valid.", "Invalid state set arguments.");
                std::process::exit(1);
            }

            if let Err(e) = ConfigStore::set_batch(&pairs) {
                log_error!("Gagal memperbarui config.json: {}", "Failed to update config.json: {}", e);
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
            log_error!("Subcommand state tidak dikenal: '{}'", "Unknown state subcommand: '{}'", other);
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
        "mode" => {
            let m = args.get(3).map(|s| s.as_str()).unwrap_or("standard");
            let target_ma = args.get(4).and_then(|s| s.parse::<u32>().ok()).unwrap_or(if m == "cool" { 1000 } else { 2000 });
            let _ = ConfigStore::set("charging.charging_mode", m, "str");
            let _ = ConfigStore::set("charging.bypass_enabled", "false", "bool");
            let _ = std::fs::remove_file("/dev/lynx_bypass_active");
            match m {
                "cool" => {
                    let _ = ConfigStore::set("charging.extreme_charging_enabled", "false", "bool");
                    let _ = ConfigStore::set("charging.limit_current_ma", &target_ma.to_string(), "val");
                    let _ = ConfigStore::set("charging.is_unconstrained_max_hw", "false", "bool");
                    ChargingRegulator::apply_from_config();
                    println!("[OK] {}: COOL ({} mA)", ui("Mode Pengisian diatur ke", "Charging Mode set to"), target_ma);
                }
                "standard" => {
                    let _ = ConfigStore::set("charging.extreme_charging_enabled", "false", "bool");
                    let _ = ConfigStore::set("charging.limit_current_ma", "2000", "val");
                    let _ = ConfigStore::set("charging.is_unconstrained_max_hw", "false", "bool");
                    ChargingRegulator::apply_from_config();
                    println!("[OK] {}: STANDARD ({})", ui("Mode Pengisian diatur ke", "Charging Mode set to"), ui("Adaptif OEM", "OEM Adaptive"));
                }
                "hyper" => {
                    let _ = ConfigStore::set("charging.extreme_charging_enabled", "true", "bool");
                    let _ = ConfigStore::set("charging.limit_current_ma", "12000", "val");
                    let _ = ConfigStore::set("charging.is_unconstrained_max_hw", "true", "bool");
                    ChargingRegulator::apply_from_config();
                    println!("[OK] {}: HYPER ({})", ui("Mode Pengisian diatur ke", "Charging Mode set to"), ui("Puncak Tanpa Batas", "Unconstrained Peak"));
                }
                "custom" => {
                    let _ = ConfigStore::set("charging.extreme_charging_enabled", "false", "bool");
                    let _ = ConfigStore::set("charging.limit_current_ma", &target_ma.to_string(), "val");
                    let _ = ConfigStore::set("charging.is_unconstrained_max_hw", "false", "bool");
                    ChargingRegulator::apply_from_config();
                    println!("[OK] {}: CUSTOM ({} mA)", ui("Mode Pengisian diatur ke", "Charging Mode set to"), target_ma);
                }
                other => {
                    log_error!("Mode charging '{}' tidak dikenal. Gunakan: cool, standard, hyper, custom", "Unknown charging mode: '{}'. Use: cool, standard, hyper, custom", other);
                }
            }
        }
        "cool" => {
            let limit = args.get(3).and_then(|s| s.parse::<u32>().ok()).unwrap_or(1000);
            let _ = ConfigStore::set("charging.charging_mode", "cool", "str");
            let _ = ConfigStore::set("charging.bypass_enabled", "false", "bool");
            let _ = std::fs::remove_file("/dev/lynx_bypass_active");
            let _ = ConfigStore::set("charging.extreme_charging_enabled", "false", "bool");
            let _ = ConfigStore::set("charging.limit_current_ma", &limit.to_string(), "val");
            let _ = ConfigStore::set("charging.is_unconstrained_max_hw", "false", "bool");
            ChargingRegulator::apply_from_config();
            println!("[OK] {}: COOL ({} mA)", ui("Mode Pengisian", "Charging Mode"), limit);
        }
        "standard" => {
            let _ = ConfigStore::set("charging.charging_mode", "standard", "str");
            let _ = ConfigStore::set("charging.bypass_enabled", "false", "bool");
            let _ = std::fs::remove_file("/dev/lynx_bypass_active");
            let _ = ConfigStore::set("charging.extreme_charging_enabled", "false", "bool");
            let _ = ConfigStore::set("charging.limit_current_ma", "2000", "val");
            let _ = ConfigStore::set("charging.is_unconstrained_max_hw", "false", "bool");
            ChargingRegulator::apply_from_config();
            println!("[OK] {}: STANDARD", ui("Mode Pengisian", "Charging Mode"));
        }
        "hyper" => {
            let _ = ConfigStore::set("charging.charging_mode", "hyper", "str");
            let _ = ConfigStore::set("charging.bypass_enabled", "false", "bool");
            let _ = std::fs::remove_file("/dev/lynx_bypass_active");
            let _ = ConfigStore::set("charging.extreme_charging_enabled", "true", "bool");
            let _ = ConfigStore::set("charging.limit_current_ma", "12000", "val");
            let _ = ConfigStore::set("charging.is_unconstrained_max_hw", "true", "bool");
            ChargingRegulator::apply_from_config();
            println!("[OK] {}: HYPER", ui("Mode Pengisian", "Charging Mode"));
        }
        "custom" => {
            let target_ma = args.get(3).and_then(|s| s.parse::<u32>().ok()).unwrap_or(2500);
            let _ = ConfigStore::set("charging.charging_mode", "custom", "str");
            let _ = ConfigStore::set("charging.bypass_enabled", "false", "bool");
            let _ = std::fs::remove_file("/dev/lynx_bypass_active");
            let _ = ConfigStore::set("charging.extreme_charging_enabled", "false", "bool");
            let _ = ConfigStore::set("charging.limit_current_ma", &target_ma.to_string(), "val");
            let _ = ConfigStore::set("charging.is_unconstrained_max_hw", "false", "bool");
            ChargingRegulator::apply_from_config();
            println!("[OK] {}: CUSTOM ({} mA)", ui("Mode Pengisian", "Charging Mode"), target_ma);
        }
        "lock" => {
            ChargingRegulator::reapply_lock();
        }
        "bypass" => {
            ChargingRegulator::apply_bypass_charging();
            println!("[OK] {}", ui("Bypass pengisian hardware aktif (arus ke sel baterai 0 mA)", "Hardware Bypass Charging engaged (0 mA battery cell cutoff)"));
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
        "scan" | "topology" => {
            let topology = ChargingRegulator::scan_charging_hardware_chips();
            let dynamic_nodes = ChargingRegulator::crawl_dynamic_power_supply_nodes();
            println!("{}", ui("=== TOPOLOGI PERANGKAT KERAS PENGISIAN LYNX ===", "=== LYNX CHARGING HARDWARE TOPOLOGY ==="));
            println!("  • {}: {}", ui("Ringkasan Hardware", "Hardware Summary"), topology.summary);
            if let Some(buck) = &topology.primary_buck {
                println!("  • {}: {}", ui("Primary Buck Converter", "Primary Buck Converter"), buck);
            }
            if !topology.charge_pumps.is_empty() {
                println!("  • {}: {}", ui("Direct Charge Pump", "Direct Charge Pump"), topology.charge_pumps.join(", "));
            }
            if !topology.vendor_controllers.is_empty() {
                println!("  • {}: {}", ui("Vendor Controller Engine", "Vendor Controller Engine"), topology.vendor_controllers.join(", "));
            }
            println!("  • {}: {}", ui("Node Sysfs Dinamis Terpindai", "Dynamic Sysfs Nodes Scanned"), dynamic_nodes.len());
            for node in &dynamic_nodes {
                println!("      - {}", node);
            }
        }
        "disconnect" | "reset" => {
            ChargingRegulator::handle_disconnect();
            println!("[OK] {}", ui("Pengisian cepat dihentikan, node direset, dan batas termal dipulihkan.", "Fast charging stopped, nodes reset, and thermal baseline restored."));
        }
        "tick" => {
            ChargingRegulator::tick();
        }
        other => {
            log_error!("Subcommand charging tidak dikenal: '{}'", "Unknown charging subcommand: '{}'", other);
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
            "wakeup-boost" | "wakeup" => {
                let duration_ms = args.get(3).and_then(|s| s.parse::<u64>().ok()).unwrap_or(2000);
                println!("{}", SystemController::wakeup_boost(duration_ms));
            }
            other => {
                log_error!("Subcommand system tidak dikenal: '{}'", "Unknown system subcommand: '{}'", other);
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
            log_error!("Subcommand display tidak dikenal: '{}'", "Unknown display subcommand: '{}'", other);
            print_usage();
            std::process::exit(1);
        }
    }
}


fn handle_applist(args: &[String]) {
    if args.len() < 3 {
        println!("{}", ui("Paket dalam daftar performa:", "Packages in performance list:"));
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
                log_error!("Cara pakai: lynxd applist add <package>", "Usage: lynxd applist add <package>");
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
                log_error!("Cara pakai: lynxd applist remove <package>", "Usage: lynxd applist remove <package>");
                std::process::exit(1);
            }
        }
        "check" => {
            if let Some(pkg) = args.get(3) {
                let present = ApplistController::check(pkg);
                println!("{}", if present { "1" } else { "0" });
            } else {
                log_error!("Cara pakai: lynxd applist check <package>", "Usage: lynxd applist check <package>");
                std::process::exit(1);
            }
        }
        other => {
            log_error!("Subcommand applist tidak dikenal: '{}'", "Unknown applist subcommand: '{}'", other);
            print_usage();
            std::process::exit(1);
        }
    }
}

fn main() {
    // Gracefully handle BrokenPipe (e.g. piped to head, grep, or reader closed stream)
    // to prevent SIGABRT tombstones on Android.
    let default_hook = std::panic::take_hook();
    std::panic::set_hook(Box::new(move |info| {
        let msg = if let Some(s) = info.payload().downcast_ref::<&str>() {
            *s
        } else if let Some(s) = info.payload().downcast_ref::<String>() {
            s.as_str()
        } else {
            ""
        };
        if msg.contains("Broken pipe") {
            std::process::exit(0);
        }
        default_hook(info);
    }));

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
                        eprintln!("{}", ui("Penggunaan: lynxd flasher restore <backup_file>", "Usage: lynxd flasher restore <backup_file>"));
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
                        eprintln!("{}", ui("Penggunaan: lynxd flasher flash <zip_path>", "Usage: lynxd flasher flash <zip_path>"));
                        std::process::exit(1);
                    }
                }
                _ => {
                    eprintln!("{}", ui("Penggunaan: lynxd flasher {{backup|list_backups|restore <file>|flash <zip>}}", "Usage: lynxd flasher {{backup|list_backups|restore <file>|flash <zip>}}"));
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
        "hud" if args.get(2).map(String::as_str) == Some("stream") => hud::run(),
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
                        log_error!("Nama profil belum diisi: lynxd profile show <name>", "Missing profile name: lynxd profile show <name>");
                        std::process::exit(1);
                    }
                    handle_profile_show(&args[3]);
                }
                "validate" => handle_profile_validate(),
                "apply" => {
                    if args.len() < 4 {
                        log_error!("Nama profil belum diisi: lynxd profile apply <name> [--dry-run]", "Missing profile name: lynxd profile apply <name> [--dry-run]");
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
                    log_error!("Subcommand profile tidak dikenal: '{}'", "Unknown profile subcommand: '{}'", other);
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
                    println!("{}", if crate::subsystems::tui::is_english() { format!("Process {} optimized for low latency gaming.", pid) } else { format!("Proses {} dioptimalkan untuk gaming latensi rendah.", pid) });
                    } else {
                        eprintln!("{}", ui("Penggunaan: lynxd game optimize-pid <PID>", "Usage: lynxd game optimize-pid <PID>"));
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
                    println!("{}", ui("Pembatas performa latar OEM dibekukan.", "OEM background throttlers frozen."));
                }
                "unfreeze" | "restore" => {
                    ThermalController::unfreeze_oem_throttlers();
                    println!("{}", ui("Pembatas performa latar OEM dipulihkan.", "OEM background throttlers unfrozen."));
                }
                _ => eprintln!("{}", ui("Penggunaan: lynxd oem freeze|unfreeze", "Usage: lynxd oem freeze|unfreeze")),
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
                        eprintln!("{}", ui("Penggunaan: lynxd cpuset set <group> <cores>", "Usage: lynxd cpuset set <group> <cores>"));
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
                            eprintln!("{}: {}", ui("Kesalahan", "Error"), e);
                            std::process::exit(1);
                        }
                    }
                }
                "enable" => match SwapController::enable() {
                    Ok(msg) => println!("{}", msg),
                    Err(e) => {
                        eprintln!("{}: {}", ui("Kesalahan", "Error"), e);
                        std::process::exit(1);
                    }
                },
                "disable" => match SwapController::disable() {
                    Ok(msg) => println!("{}", msg),
                    Err(e) => {
                        eprintln!("{}: {}", ui("Kesalahan", "Error"), e);
                        std::process::exit(1);
                    }
                },
                "remove" => match SwapController::remove() {
                    Ok(msg) => println!("{}", msg),
                    Err(e) => {
                        eprintln!("{}: {}", ui("Kesalahan", "Error"), e);
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
                        eprintln!("{}", ui("Penggunaan: lynxd memory vm-set <param> <val>", "Usage: lynxd memory vm-set <param> <val>"));
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
        "media" => {
            std::process::exit(crate::subsystems::media::MediaController::cli(&args[2..]));
        }
        "io" => {
            let sub = args.get(2).map(|s| s.as_str()).unwrap_or("apply");
            match sub {
                "info" => {
                    println!("{}", IoController::info_json());
                }
                "mountx-info" | "mountx" => {
                    println!("{}", IoController::mountx_info_json());
                }
                "mountx-tune" | "flash-boost" => {
                    println!("{}", IoController::apply_flash_streaming_boost(true));
                }
                "mountx-restore" | "flash-restore" => {
                    println!("{}", IoController::apply_flash_streaming_boost(false));
                }
                "set-scheduler" | "set_scheduler" => {
                    let blk = args.get(3).map(|s| s.as_str()).unwrap_or("");
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
        "adb" => {
            let sub = args.get(2).map(|s| s.as_str()).unwrap_or("status");
            match sub {
                "status" => {
                    println!("{}", AdbController::status_json());
                }
                "enable" => {
                    let port = args.get(3).and_then(|p| p.parse::<u16>().ok()).unwrap_or_else(|| {
                        AdbController::load_config().port
                    });
                    if let Err(e) = AdbController::save_config(true, port) {
                        eprintln!("{}", e);
                        std::process::exit(1);
                    }
                    match AdbController::apply(port) {
                        Ok(msg) => println!("{}", msg),
                        Err(e) => {
                            eprintln!("{}", e);
                            std::process::exit(1);
                        }
                    }
                }
                "disable" => {
                    match AdbController::disable() {
                        Ok(msg) => println!("{}", msg),
                        Err(e) => {
                            eprintln!("{}", e);
                            std::process::exit(1);
                        }
                    }
                }
                "set-port" | "set_port" => {
                    if let Some(port_str) = args.get(3) {
                        if let Ok(port) = port_str.parse::<u16>() {
                            let cfg = AdbController::load_config();
                            if let Err(e) = AdbController::save_config(cfg.enabled, port) {
                                eprintln!("{}", e);
                                std::process::exit(1);
                            }
                            if cfg.enabled {
                                match AdbController::apply(port) {
                                    Ok(msg) => println!("{}", msg),
                                    Err(e) => {
                                        eprintln!("{}", e);
                                        std::process::exit(1);
                                    }
                                }
                            } else {
                                if crate::subsystems::tui::is_english() {
                                    println!("Port configured to {}. Enable ADB keep-alive to activate.", port);
                                } else {
                                    println!("Port {} telah diatur. Aktifkan ADB keep-alive untuk menggunakannya.", port);
                                }
                            }
                        } else {
                            if crate::subsystems::tui::is_english() {
                                eprintln!("Invalid port number: {}", port_str);
                            } else {
                                eprintln!("Nomor port tidak valid: {}", port_str);
                            }
                            std::process::exit(1);
                        }
                    } else {
                        eprintln!("{}", ui("Penggunaan: lynxd adb set-port <port>", "Usage: lynxd adb set-port <port>"));
                        std::process::exit(1);
                    }
                }
                "watchdog" => {
                    AdbController::run_watchdog_loop();
                }
                other => {
                    if crate::subsystems::tui::is_english() {
                        eprintln!("Unknown adb subcommand: '{}'. Available: status, enable [port], disable, set-port <port>, watchdog", other);
                    } else {
                        eprintln!("Subperintah adb '{}' tidak dikenal. Tersedia: status, enable [port], disable, set-port <port>, watchdog", other);
                    }
                    std::process::exit(1);
                }
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
                        log_error!("Path belum diisi: lynxd inspect node <path>", "Missing path: lynxd inspect node <path>");
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
                        log_error!("Cara pakai: lynxd inspect set <path> <val>", "Usage: lynxd inspect set <path> <val>");
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
                        log_error!("Cara pakai: lynxd tunable set <path> <val>", "Usage: lynxd tunable set <path> <val>");
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
                "run" => handle_daemon_run(),
                "start" => handle_daemon_start(),
                "status" => handle_daemon_status(),
                "stop" => handle_daemon_stop(),
                other => {
                    log_error!("Subcommand daemon tidak dikenal: '{}'", "Unknown daemon subcommand: '{}'", other);
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
                        log_error!("Argumen path belum diisi: lynxd sysfs read <path>", "Missing path argument: lynxd sysfs read <path>");
                        std::process::exit(1);
                    }
                    handle_sysfs_read(&args[3]);
                }
                "write" => {
                    if args.len() < 5 {
                        log_error!("Argumen belum lengkap: lynxd sysfs write <path> <val> [--dry-run] [--force-perm]", "Missing arguments: lynxd sysfs write <path> <val> [--dry-run] [--force-perm]");
                        std::process::exit(1);
                    }
                    let flags = if args.len() > 5 { args[5..].to_vec() } else { Vec::new() };
                    handle_sysfs_write(&args[3], &args[4], &flags);
                }
                "test-write" => {
                    if args.len() < 5 {
                        log_error!("Argumen belum lengkap: lynxd sysfs test-write <path> <val>", "Missing arguments: lynxd sysfs test-write <path> <val>");
                        std::process::exit(1);
                    }
                    let flags = vec!["--dry-run".to_string()];
                    handle_sysfs_write(&args[3], &args[4], &flags);
                }
                "benchmark" => handle_benchmark(),
                other => {
                    log_error!("Subcommand sysfs tidak dikenal: '{}'", "Unknown sysfs subcommand: '{}'", other);
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
            log_error!("Perintah tidak dikenal: '{}'", "Unknown command: '{}'", unknown);
            print_usage();
            std::process::exit(1);
        }
    }
}
