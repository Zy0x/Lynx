use std::thread::sleep;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use crate::apply::{ExecutionPlan, ProfileExecutor};
use crate::daemon::foreground::ForegroundDetector;
use crate::daemon::lifecycle::DaemonLifecycle;
use crate::daemon::screen::{ScreenDetector, ScreenState};
use crate::daemon::state::DaemonState;
use crate::decision::{apply_modifier, DecisionAction, DecisionEngine, ModifierLevel};
use crate::hardware::ChargingRegulator;
use crate::profile::get_profile_by_name;
use crate::state::{DeviceState, ProfileStateManager};
use crate::utils::Logger;
use crate::subsystems::tui::{is_english, ui};

pub struct DaemonRunner;


impl DaemonRunner {
    /// Runs the autonomous Phase 4C daemon loop with Foreground App Awareness,
    /// Hysteresis Cooldown, and Deterministic Profile Switching.
    pub fn run() -> Result<(), String> {
        let pid = DaemonLifecycle::acquire_lock()?;
        Logger::info(if is_english() { format!("Lynx Daemon (lynxd) started successfully [PID: {}]", pid) } else { format!("Daemon Lynx (lynxd) berhasil dimulai [PID: {}]", pid) });
        Logger::info(ui("Mode: Mesin Profil Otonom dengan Histeresis dan Perlindungan Tidur Dalam", "Mode: Autonomous Profile Engine with Hysteresis and Deep Sleep Shield"));
        crate::utils::update_module_prop_description("[ Auto (AI) Mode Active ] Deity Hybrid Architecture");

        let screen = ScreenDetector::probe();
        let mut fg_detector = ForegroundDetector::new();
        let mut daemon_state = DaemonState::new();
        let _touch_detector = crate::daemon::TouchDetector::start();
        if _touch_detector.is_some() {
            Logger::info(ui(
                "[DAEMON] Detektor sentuhan fisik aktif (respons sub-milidetik)",
                "[DAEMON] Physical touch detector active (sub-millisecond response)",
            ));
        }

        let initial_profile = Self::read_baseline_profile()
            .or_else(|| {
                ProfileStateManager::load_active()
                    .ok()
                    .flatten()
                    .map(|a| a.active_profile)
            })
            .unwrap_or_else(|| "balance".to_string());

        let initial_cooldown = Self::read_cooldown_duration();
        let mut decision_engine = DecisionEngine::new(&initial_profile, initial_cooldown);
        let dur_label = if initial_cooldown == 0 { "Persistent (RAM)" } else { "dynamic" };
        Logger::info(if is_english() {
            format!("[DAEMON] Initialized DecisionEngine with baseline profile: '{}' (Exit Hysteresis: {}s [{}])", initial_profile, initial_cooldown, dur_label)
        } else {
            format!("[DAEMON] Mesin keputusan diinisialisasi dengan profil dasar: '{}' (Histeresis keluar: {} dtk [{}])", initial_profile, initial_cooldown, dur_label)
        });

        let monotonic_boot = std::time::Instant::now();
        let mut last_detect_instant = std::time::Instant::now();
        let mut last_cooldown_check = 0u64;
        let mut last_maintenance_ts = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        while DaemonLifecycle::is_running() {
            let now = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap_or_default()
                .as_secs();

            // Dynamic cooldown duration & baseline profile reload from Companion App / WebUI config
            if now.saturating_sub(last_cooldown_check) >= 2 {
                let target_cooldown = Self::read_cooldown_duration();
                if target_cooldown != decision_engine.cooldown_duration() {
                    decision_engine.set_cooldown_duration(target_cooldown);
                    let label = if target_cooldown == 0 { "Persistent (RAM)".to_string() } else { format!("{}s", target_cooldown) };
                    Logger::info(format!("[DAEMON] Durasi histeresis keluar diperbarui: {}", label));
                }
                if let Some(target_baseline) = Self::read_baseline_profile() {
                    if target_baseline != decision_engine.baseline_profile() {
                        Logger::info(if is_english() {
                            format!("[DAEMON] Dynamic baseline profile updated: '{}' -> '{}'", decision_engine.baseline_profile(), target_baseline)
                        } else {
                            format!("[DAEMON] Profil dasar dinamis diperbarui: '{}' -> '{}'", decision_engine.baseline_profile(), target_baseline)
                        });
                        decision_engine.update_baseline_profile(&target_baseline);
                    }
                }
                last_cooldown_check = now;
            }

            // Dynamic sync of active profile from external events (HUD, Companion, CLI)
            if let Ok(active_content) = std::fs::read_to_string("/data/adb/lynx/active_profile") {
                let active_str = active_content.trim();
                if !active_str.is_empty() && active_str != decision_engine.current_profile() {
                    if matches!(active_str, "powersave" | "balance" | "performance" | "extreme" | "dormant") {
                        decision_engine.notify_applied(active_str);
                    }
                }
            }

            let current_screen = screen.detect();

            // Screen transition tracking
            if daemon_state.update_screen(current_screen, now) {
                match current_screen {
                    ScreenState::Off => {
                        Logger::info(ui("[DAEMON] Layar MATI terdeteksi. Perlindungan tidur dalam aktif (interval loop 20 dtk).", "[DAEMON] Screen OFF detected. Activating Deep Sleep Shield (loop throttled to 20s)."));
                        ChargingRegulator::apply_from_config();
                    }
                    ScreenState::On => {
                        Logger::info(ui("[DAEMON] Layar MENYALA terdeteksi. Pemantauan aktif dilanjutkan (interval: 5 dtk).", "[DAEMON] Screen ON detected. Resuming active monitoring (interval: 5s)."));
                        ChargingRegulator::apply_from_config();
                    }
                    ScreenState::Unknown => {}
                }
            }

            // Foreground app detection (Only when screen is ON to preserve deep sleep)
            let fg_app = if current_screen == ScreenState::Off {
                None
            } else {
                fg_detector.detect()
            };

            // App transition tracking
            if daemon_state.update_app(fg_app.clone(), now) {
                last_detect_instant = std::time::Instant::now();
                let detect_ns = last_detect_instant.duration_since(monotonic_boot).as_nanos();
                if let Some(ref app) = fg_app {
                    Logger::info(if is_english() {
                        format!("[DAEMON] GAME_DETECTED_NS: {} | Foreground switch: '{}' [Category: {}, Confidence: {:?}]", detect_ns, app.package, app.category.as_str(), app.confidence)
                    } else {
                        format!("[DAEMON] GAME_DETECTED_NS: {} | Peralihan aplikasi depan: '{}' [Kategori: {}, Keyakinan: {:?}]", detect_ns, app.package, app.category.as_str(), app.confidence)
                    });
                }
            }

            // Health snapshot
            let state = DeviceState::capture();

            // ── Phase 4C: Autonomous Profile Decision & Hysteresis ───────────
            let action = decision_engine.evaluate(
                fg_app.as_ref(),
                current_screen,
                &state,
                now,
            );

            match action {
                DecisionAction::ApplyProfile { target_profile, reason } => {
                    let switch_start = std::time::Instant::now();
                    Logger::info(if is_english() { format!("[DAEMON] Profile switch triggered -> '{}' (Reason: {})", target_profile, reason) } else { format!("[DAEMON] Peralihan profil dipicu -> '{}' (Alasan: {})", target_profile, reason) });
                    if let Some(prof) = get_profile_by_name(target_profile) {
                        let plan = ExecutionPlan::build(&prof);
                        match ProfileExecutor::execute(&plan, false) {
                            Ok(report) => {
                                let total_transitions = daemon_state.increment_transition();
                                let commit_ns = switch_start.duration_since(monotonic_boot).as_nanos();
                                let delta_ns = last_detect_instant.elapsed().as_nanos();
                                let delta_ms = delta_ns as f64 / 1_000_000.0;
                                Logger::info(if is_english() {
                                    format!("[DAEMON] PROFILE_COMMIT_NS: {} -> '{}' (Switch Latency delta: {:.2} ms [{} ns], Committed: {} nodes, Total Transitions: {})", commit_ns, target_profile, delta_ms, delta_ns, report.operations_committed, total_transitions)
                                } else {
                                    format!("[DAEMON] PROFILE_COMMIT_NS: {} -> '{}' (Selisih latensi peralihan: {:.2} ms [{} ns], Commit: {} node, Total peralihan: {})", commit_ns, target_profile, delta_ms, delta_ns, report.operations_committed, total_transitions)
                                });
                                decision_engine.notify_applied(target_profile);
                                if target_profile == "performance" || target_profile == "extreme" {
                                    let game_pid = fg_app.as_ref().and_then(|app| {
                                        crate::subsystems::GameController::find_pid_for_package(&app.package)
                                    });
                                    crate::subsystems::GameController::game_on(game_pid);
                                } else {
                                    crate::subsystems::GameController::game_off();
                                }
                                Self::notify_profile_transition(target_profile, &reason);
                            }
                            Err(e) => {
                                Logger::error(if is_english() { format!("[DAEMON] Failed to apply profile '{}': {}", target_profile, e) } else { format!("[DAEMON] Gagal menerapkan profil '{}': {}", target_profile, e) });
                            }
                        }
                    } else {
                        Logger::error(if is_english() { format!("[DAEMON] Target profile '{}' not found in registry!", target_profile) } else { format!("[DAEMON] Profil target '{}' tidak ditemukan di registry!", target_profile) });
                    }
                }
                DecisionAction::ApplyModifier { target_profile, modifier, reason } => {
                    Logger::info(if is_english() { format!("[DAEMON] Adaptive Modifier triggered -> '{}' [{}] (Reason: {})", target_profile, modifier.as_str(), reason) } else { format!("[DAEMON] Modifier adaptif dipicu -> '{}' [{}] (Alasan: {})", target_profile, modifier.as_str(), reason) });
                    if let Some(base_prof) = get_profile_by_name(target_profile) {
                        let adapted_prof = apply_modifier(&base_prof, modifier);
                        let plan = ExecutionPlan::build(&adapted_prof);
                        match ProfileExecutor::execute(&plan, false) {
                            Ok(report) => {
                                Logger::info(if is_english() { format!("[DAEMON] Adaptive modifier applied successfully in {:.2} ms ({} nodes adjusted)", report.elapsed_micros as f64 / 1000.0, report.operations_committed) } else { format!("[DAEMON] Modifier adaptif berhasil diterapkan dalam {:.2} ms ({} node disesuaikan)", report.elapsed_micros as f64 / 1000.0, report.operations_committed) });
                            }
                            Err(e) => {
                                Logger::error(if is_english() { format!("[DAEMON] Failed to apply adaptive modifier: {}", e) } else { format!("[DAEMON] Gagal menerapkan modifier adaptif: {}", e) });
                            }
                        }
                    }
                }
                DecisionAction::HoldInCooldown { remaining_secs } => {
                    Logger::info(if is_english() { format!("[DAEMON] Target app exited: Holding '{}' in hysteresis cooldown ({}s remaining)...", decision_engine.current_profile(), remaining_secs) } else { format!("[DAEMON] Aplikasi target ditutup: Mempertahankan '{}' selama cooldown histeresis (tersisa {} dtk)...", decision_engine.current_profile(), remaining_secs) });
                }
                DecisionAction::Inhibited { target_profile, reason } => {
                    let localized_reason = match reason {
                        "Battery level <= 15% (Low battery guard prevents performance boost)" => ui("Level baterai ≤15% (pengaman baterai rendah mencegah boost performa)", "Battery level <= 15% (Low battery guard prevents performance boost)"),
                        "Thermal throttling active (Boost blocked to protect SoC temperature)" => ui("Thermal throttling aktif (boost diblokir untuk melindungi suhu SoC)", "Thermal throttling active (Boost blocked to protect SoC temperature)"),
                        other => other,
                    };
                    Logger::warn(if is_english() { format!("[DAEMON] Boost transition to '{}' inhibited by safety guardrail: {}", target_profile, reason) } else { format!("[DAEMON] Peralihan boost ke '{}' ditahan oleh pengaman keselamatan: {}", target_profile, localized_reason) });
                }
                DecisionAction::MaintainCurrent => {}
            }

            let active_prof = decision_engine.current_profile();
            let current_mod = decision_engine.current_modifier();
            let profile_desc = if current_mod == ModifierLevel::None {
                active_prof.to_string()
            } else {
                format!("{} [{}]", active_prof, current_mod.as_str())
            };

            let temp_c = state.derived.temp_celsius_float;
            let bat_pct = state.raw.battery.capacity;
            let workload = state.derived.workload_level.as_str();

            let app_desc = match &daemon_state.current_app {
                Some(app) => format!("{} ({})", app.package, app.category.as_str()),
                None => ui("Tidak ada (Diam/Layar Mati)", "None (Idle/ScreenOff)").to_string(),
            };

            Logger::info(if is_english() {
                format!("[DAEMON] Telemetry: Temp: {:.1}°C | Battery: {}% | Workload: {} | Profile: {} | Screen: {:?} | App: {}", temp_c, bat_pct, workload, profile_desc, current_screen, app_desc)
            } else {
                format!("[DAEMON] Telemetri: Suhu: {:.1}°C | Baterai: {}% | Beban: {} | Profil: {} | Layar: {:?} | Aplikasi: {}", temp_c, bat_pct, workload, profile_desc, current_screen, app_desc)
            });

            daemon_state.update_telemetry(state.clone());
            daemon_state.cycle_count += 1;

            // Autonomous Charging Regulator & Safety Guard tick (Zero-fork native)
            ChargingRegulator::tick();

            // Check if multitasking is active (Split-Screen / Freeform / PiP)
            let is_multitask = fg_detector.is_multitasking_active();

            // Autonomous Universal Multi-Factor Dynamic GPU Governor tick
            crate::subsystems::GpuController::tick_dynamic_scaler(
                state.derived.workload_level,
                state.derived.temp_celsius_float,
                active_prof,
                current_screen == ScreenState::On,
                is_multitask,
            );

            // Autonomous Universal Dynamic Display Governor tick (Auto 60 <-> 90 <-> 120Hz)
            crate::subsystems::DisplayController::tick_dynamic_display_scaler(
                fg_app.as_ref(),
                active_prof,
                current_screen == ScreenState::On,
            );

            // Autonomous ADB Wireless Keep-Alive & Port Lock Watchdog tick
            crate::subsystems::AdbController::tick();

            // Autonomous VMA Shield Watchdog tick (Monitors system_server VMA every ~60 cycles)
            if daemon_state.cycle_count % 60 == 0 {
                crate::subsystems::MemoryController::check_vma_health();
            }

            // Native Daily Maintenance Scheduler (replaces legacy crond & core/cron/root)
            if current_screen == ScreenState::Off && now.saturating_sub(last_maintenance_ts) >= 86400 {
                Logger::info(ui("[DAEMON] Menjalankan pemeliharaan penyimpanan dan memori terjadwal 24 jam...", "[DAEMON] Executing scheduled 24h storage and memory maintenance..."));
                let _ = crate::subsystems::MaintenanceController::run(false);
                let _ = crate::subsystems::MemoryController::clean_memory();
                last_maintenance_ts = now;
            }

            let (mod_name, mod_desc) = match current_mod {
                ModifierLevel::None => ("None", "Full Base Profile"),
                ModifierLevel::ThermalCooling => ("ThermalCooling", "-15% CPU floor"),
                ModifierLevel::ThermalThrottled => ("ThermalThrottled", "-30% CPU floor, GPU boost off"),
                ModifierLevel::BatterySaver => ("BatterySaver", "-10% CPU floor"),
            };

            // Ephemeral runtime status snapshot for `lynxd status` and WebUI bridge
            Self::write_status_snapshot(
                active_prof,
                current_mod,
                &app_desc,
                temp_c,
                bat_pct,
            );

            // Unified hardware telemetry snapshot to RAM tmpfs (/dev/) for sub-0.1ms UI & companion app reads
            let unified_json = state.to_unified_json(
                Some(active_prof),
                Some(mod_name),
                Some(mod_desc),
                Some(std::process::id()),
            );
            Self::write_telemetry_snapshot(&unified_json);

            // Dynamic Polling Interval per PROJECT.md interface contract:
            // - If ChargingRegulator::is_hardware_plugged() is true: loop interval <= 1 second (regardless of screen state).
            // - If unplugged and screen off: loop interval 20 seconds.
            // - Disconnect latency guarantee: <= 1000 ms (strictly < 2 seconds).
            let is_plugged = ChargingRegulator::is_hardware_plugged();
            let sleep_duration = if is_plugged {
                match current_screen {
                    ScreenState::Off => Duration::from_secs(4), // Screen off while charging: active 1s ticks in 500ms chunks
                    _ => Duration::from_secs(1),                // Screen active while charging: responsive yet zero churn
                }
            } else {
                match current_screen {
                    ScreenState::Off => Duration::from_secs(20), // Deep Sleep Shield: 20 seconds
                    _ => Duration::from_secs(1),                // Active screen: 1 second for instant responsiveness
                }
            };

            // Dynamic sleep chunking:
            // - While plugged in: 500ms chunks for sub-500ms disconnect detection.
            // - While unplugged & screen off: undisturbed deep sleep without sysfs polling wakeups.
            // - While unplugged & screen active: 250ms chunks to detect events promptly.
            if is_plugged {
                let chunks = (sleep_duration.as_millis() / 500) as usize;
                for idx in 0..chunks {
                    if !DaemonLifecycle::is_running() {
                        break;
                    }
                    let currently_plugged = ChargingRegulator::is_hardware_plugged();
                    if currently_plugged != is_plugged {
                        // Edge trigger: hardware plug state changed! Break sleep loop immediately
                        if !currently_plugged {
                            ChargingRegulator::handle_disconnect();
                        }
                        break;
                    }

                    if !currently_plugged {
                        ChargingRegulator::handle_disconnect();
                        break;
                    } else if idx > 0 && idx % 2 == 0 {
                        ChargingRegulator::tick();
                    }
                    if idx > 0 && idx % 2 == 0 {
                        crate::subsystems::AdbController::tick();
                    }
                    sleep(Duration::from_millis(500));
                }
            } else if current_screen == ScreenState::Off {
                // Deep Sleep Shield: When unplugged and screen is off, sleep in 1000ms intervals
                // checking for instant screen ON edge trigger to break out immediately (<1000ms).
                let total_chunks = (sleep_duration.as_millis() / 1000).max(1);
                for _ in 0..total_chunks {
                    if !DaemonLifecycle::is_running() {
                        break;
                    }
                    sleep(Duration::from_millis(1000));
                    if screen.detect() == ScreenState::On {
                        // Instant wakeup: Screen turned on! Trigger native wakeup boost and resume immediately!
                        Logger::info(if is_english() {
                            "[DAEMON] Screen turned ON from sleep! Aborting deep sleep wait and triggering Wakeup Boost..."
                        } else {
                            "[DAEMON] Layar menyala dari tidur! Menghentikan sleep deep-sleep dan memicu Wakeup Boost..."
                        });
                        crate::subsystems::SystemController::wakeup_boost(2000);
                        break;
                    }
                }
            } else {
                // Active screen (unplugged): 1s sleep in 250ms chunks checking lifecycle and charger plug-in
                let chunks = (sleep_duration.as_millis() / 250).max(1);
                for _ in 0..chunks {
                    if !DaemonLifecycle::is_running() {
                        break;
                    }
                    let currently_plugged = ChargingRegulator::is_hardware_plugged();
                    if currently_plugged {
                        // Cable plugged in while screen is active: break sleep loop immediately
                        break;
                    }
                    sleep(Duration::from_millis(250));

                    // Sub-cycle dynamic display & GPU scaler responsiveness (every 250ms chunk)
                    crate::subsystems::DisplayController::tick_dynamic_display_scaler(
                        fg_app.as_ref(),
                        active_prof,
                        true,
                    );
                    crate::subsystems::GpuController::tick_dynamic_scaler(
                        state.derived.workload_level,
                        state.derived.temp_celsius_float,
                        active_prof,
                        true,
                        is_multitask,
                    );
                }
            }
        }

        Logger::info(if is_english() {
            format!("Lynx Daemon shutting down gracefully after {} health cycles. Releasing PID lock...", daemon_state.cycle_count)
        } else {
            format!("Daemon Lynx berhenti dengan aman setelah {} siklus kesehatan. Melepaskan kunci PID...", daemon_state.cycle_count)
        });
        let _ = std::fs::remove_file(Self::status_snapshot_path());
        let _ = std::fs::remove_file(Self::telemetry_snapshot_path());
        DaemonLifecycle::release_lock();
        Logger::info(ui("Daemon Lynx berhenti dengan bersih.", "Lynx Daemon stopped cleanly."));

        Ok(())
    }

    pub fn status_snapshot_path() -> std::path::PathBuf {
        #[cfg(target_os = "android")]
        {
            if std::path::Path::new("/dev").exists() {
                return std::path::PathBuf::from("/dev/lynxd_status.json");
            }
        }
        std::path::PathBuf::from("./lynxd_status.json")
    }

    pub fn telemetry_snapshot_path() -> std::path::PathBuf {
        #[cfg(target_os = "android")]
        {
            if std::path::Path::new("/dev").exists() {
                return std::path::PathBuf::from("/dev/lynxd_telemetry.json");
            }
        }
        std::path::PathBuf::from("./lynxd_telemetry.json")
    }

    fn write_telemetry_snapshot(content: &str) {
        let path = Self::telemetry_snapshot_path();
        if std::fs::write(&path, content).is_ok() {
            #[cfg(target_os = "android")]
            {
                let _ = std::process::Command::new("chmod")
                    .args(&["644", "/dev/lynxd_telemetry.json"])
                    .status();
            }
            #[cfg(unix)]
            {
                use std::os::unix::fs::PermissionsExt;
                let _ = std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o644));
            }
        }
        #[cfg(target_os = "android")]
        {
            let tmp_path = std::path::Path::new("/data/local/tmp/lynxd_telemetry.json");
            if std::fs::write(tmp_path, content).is_ok() {
                use std::os::unix::fs::PermissionsExt;
                let _ = std::fs::set_permissions(tmp_path, std::fs::Permissions::from_mode(0o666));
            }
        }
    }

    fn write_status_snapshot(
        profile: &str,
        modifier: ModifierLevel,
        app_desc: &str,
        temp_c: f32,
        battery_pct: u32,
    ) {
        let (mod_name, mod_desc) = match modifier {
            ModifierLevel::None => ("None", "Full Base Profile"),
            ModifierLevel::ThermalCooling => ("ThermalCooling", "-15% CPU floor"),
            ModifierLevel::ThermalThrottled => ("ThermalThrottled", "-30% CPU floor, GPU boost off"),
            ModifierLevel::BatterySaver => ("BatterySaver", "-10% CPU floor"),
        };

        let content = format!(
            "{{\"profile\":\"{}\",\"modifier\":\"{}\",\"modifier_desc\":\"{}\",\"app\":\"{}\",\"temp_c\":{:.1},\"battery_pct\":{}}}\n",
            profile, mod_name, mod_desc, app_desc, temp_c, battery_pct
        );

        let _ = std::fs::write(Self::status_snapshot_path(), &content);
        #[cfg(target_os = "android")]
        {
            let tmp_path = std::path::Path::new("/data/local/tmp/lynxd_status.json");
            if std::fs::write(tmp_path, &content).is_ok() {
                use std::os::unix::fs::PermissionsExt;
                let _ = std::fs::set_permissions(tmp_path, std::fs::Permissions::from_mode(0o666));
            }
        }
    }

    fn notify_profile_transition(profile: &str, reason: &str) {
        #[cfg(target_os = "android")]
        {
            let feedback_style = std::fs::read_to_string("/data/adb/lynx/feedback_style")
                .unwrap_or_default()
                .trim()
                .to_string();

            if feedback_style == "disabled" {
                return;
            }

            let (emoji, mode_label) = match profile {
                "extreme" => ("🔥", "EXTREME"),
                "performance" => ("⚡", "PERFORMANCE"),
                "powersave" => ("🍃", "POWERSAVE"),
                _ => ("⚖️", "BALANCE"),
            };
            let short_reason = if reason.len() > 60 {
                reason[..60].to_string()
            } else {
                reason.to_string()
            };
            let notif_text = format!("{} [{}] Mode Aktif • {}", emoji, mode_label, short_reason);
            let profile_owned = profile.to_string();

            // Run system notifications and broadcast asynchronously so main transition thread is NEVER blocked by Java VM startup
            std::thread::spawn(move || {
                // 1. Post native Android System Notification (100% independent of companion APK)
                let _ = std::process::Command::new("/system/bin/cmd")
                    .args(&[
                        "notification",
                        "post",
                        "-t",
                        "Lynx Universal",
                        "lynx_mode",
                        &notif_text,
                    ])
                    .stdout(std::process::Stdio::null())
                    .stderr(std::process::Stdio::null())
                    .status();

                // 2. Broadcast intent for Lynx Companion APK (unless status_bar only)
                if feedback_style != "status_bar" {
                    let _ = std::process::Command::new("/system/bin/am")
                        .args(&[
                            "broadcast",
                            "-a",
                            "com.noir.lynx.PROFILE_CHANGED",
                            "-n",
                            "com.noir.lynx/.receiver.LynxProfileReceiver",
                            "-f",
                            "0x10000000",
                            "--include-stopped-packages",
                            "-e",
                            "profile",
                            &profile_owned,
                            "-e",
                            "reason",
                            &short_reason,
                        ])
                        .stdout(std::process::Stdio::null())
                        .stderr(std::process::Stdio::null())
                        .status();
                }
            });
        }
        #[cfg(not(target_os = "android"))]
        {
            let _ = (profile, reason);
        }
    }

    pub fn read_cooldown_duration() -> u64 {
        for path in &[
            "/data/adb/lynx/cooldown_duration",
            "/data/adb/modules/Lynx/cooldown_duration",
        ] {
            if let Ok(s) = std::fs::read_to_string(path) {
                if let Ok(val) = s.trim().parse::<u64>() {
                    return val;
                }
            }
        }
        30 // Default 30s
    }

    pub fn read_baseline_profile() -> Option<String> {
        for path in &[
            "/data/adb/lynx/baseline_profile",
            "/data/adb/modules/Lynx/baseline_profile",
        ] {
            if let Ok(s) = std::fs::read_to_string(path) {
                let trimmed = s.trim().to_lowercase();
                if !trimmed.is_empty() {
                    return Some(trimmed);
                }
            }
        }
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_write_and_parse_status_snapshot() {
        let path = DaemonRunner::status_snapshot_path();
        DaemonRunner::write_status_snapshot(
            "performance",
            ModifierLevel::ThermalCooling,
            "com.dts.freefireth (Gaming)",
            41.2,
            78,
        );

        assert!(path.exists());
        let content = std::fs::read_to_string(&path).expect("read snapshot");
        assert!(content.contains("\"profile\":\"performance\""));
        assert!(content.contains("\"modifier\":\"ThermalCooling\""));
        assert!(content.contains("\"app\":\"com.dts.freefireth (Gaming)\""));
        assert!(content.contains("\"temp_c\":41.2"));
        assert!(content.contains("\"battery_pct\":78"));

        let _ = std::fs::remove_file(&path);
    }
}

