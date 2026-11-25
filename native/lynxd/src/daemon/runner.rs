use std::thread::sleep;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use crate::apply::{ExecutionPlan, ProfileExecutor};
use crate::daemon::foreground::{AppCategory, ForegroundDetector};
use crate::daemon::lifecycle::DaemonLifecycle;
use crate::daemon::screen::{ScreenDetector, ScreenState};
use crate::daemon::state::DaemonState;
use crate::decision::{apply_modifier, DecisionAction, DecisionEngine, ModifierLevel};
use crate::hardware::ChargingRegulator;
use crate::profile::get_profile_by_name;
use crate::state::{DeviceState, ProfileStateManager};
use crate::utils::Logger;
use crate::subsystems::tui::{is_english, ui};

#[cfg(unix)]
extern "C" {
    fn setpriority(which: i32, who: u32, prio: i32) -> i32;
}

pub struct DaemonRunner;


impl DaemonRunner {
    /// Runs the autonomous Phase 4C daemon loop with Foreground App Awareness,
    /// Hysteresis Cooldown, and Deterministic Profile Switching.
    pub fn run() -> Result<(), String> {
        let pid = DaemonLifecycle::acquire_lock()?;
        let boot_cap = crate::hardware::capability::HardwareCapability::resolve();
        let boot_topo = crate::subsystems::game::TopologyMasks::from_policies(&boot_cap.cpu.policies, boot_cap.cpu.total_cores);
        crate::subsystems::GameController::pin_self_to_little(boot_topo.little_mask);
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

        // Apply initial baseline profile if it wasn't just applied cleanly (e.g. by `lynxd boot` moments ago)
        let now_boot_ts = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        let recently_applied = ProfileStateManager::load_active()
            .ok()
            .flatten()
            .map(|s| {
                !s.dirty
                    && s.status == "applied"
                    && s.active_profile.eq_ignore_ascii_case(&initial_profile)
                    && now_boot_ts.saturating_sub(s.timestamp) <= 20
            })
            .unwrap_or(false);

        if !recently_applied {
            if let Some(base_prof) = get_profile_by_name(&initial_profile) {
                let plan = ExecutionPlan::build(&base_prof);
                match ProfileExecutor::execute(&plan, false) {
                    Ok(report) => {
                        Logger::info(if is_english() {
                            format!("[DAEMON] Initial baseline profile '{}' applied ({} nodes committed)", initial_profile, report.operations_committed)
                        } else {
                            format!("[DAEMON] Profil dasar awal '{}' berhasil diterapkan ({} node dikomit)", initial_profile, report.operations_committed)
                        });
                    }
                    Err(e) => {
                        Logger::error(if is_english() {
                            format!("[DAEMON] Failed to apply initial baseline profile '{}': {}", initial_profile, e)
                        } else {
                            format!("[DAEMON] Gagal menerapkan profil dasar awal '{}': {}", initial_profile, e)
                        });
                    }
                }
            }
        } else {
            Logger::info(ui(
                "[DAEMON] Profil dasar baru saja diterapkan saat boot; melewati eksekusi ganda.",
                "[DAEMON] Baseline profile already applied during boot; skipping duplicate commit.",
            ));
        }

        // Optimize SystemUI priority, HWC VSyncThread_0 SCHED_FIFO, kswapd0/kcompactd0 -10 on Little cores,
        // & disable expensive window blurs on boot
        Self::shield_systemui();
        crate::subsystems::GameController::prioritize_surfaceflinger(-10);
        crate::subsystems::GameController::offload_kswapd_kcompactd(boot_topo.little_mask);
        crate::subsystems::thermal::ThermalController::un_isolate_all_cpus();
        let _ = std::process::Command::new("settings").args(&["put", "global", "disable_window_blurs", "1"]).output();
        let _ = std::process::Command::new("setprop").args(&["persist.sys.sf.disable_blurs", "1"]).output();

        crate::state::ConfigStore::sync_profile_markers(&initial_profile, true);

        let monotonic_boot = std::time::Instant::now();
        let mut last_detect_instant = std::time::Instant::now();
        let mut last_cooldown_check = 0u64;
        let mut last_tmp_telemetry_sync = 0u64;
        let mut last_tmp_status_sync = 0u64;
        let mut last_tmp_status_sig = String::new();
        let mut last_tmp_bat_pct = u32::MAX;
        let mut last_hud_override: Option<(String, String)> = None;
        let mut game_boost_active: bool = false;
        let mut game_boost_pkg: Option<String> = None;
        let mut game_boost_pid: Option<u32> = None;
        let mut game_boost_recheck_at: Option<u64> = None;
        let mut game_mem_guard_at: Option<u64> = None;
        let mut last_maintenance_ts = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        while DaemonLifecycle::is_running() {
            let now = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap_or_default()
                .as_secs();

            // Dynamic cooldown duration reload from Companion App / WebUI config
            if now.saturating_sub(last_cooldown_check) >= 2 {
                let target_cooldown = Self::read_cooldown_duration();
                if target_cooldown != decision_engine.cooldown_duration() {
                    decision_engine.set_cooldown_duration(target_cooldown);
                    let label = if target_cooldown == 0 { "Persistent (RAM)".to_string() } else { format!("{}s", target_cooldown) };
                    Logger::info(format!("[DAEMON] Durasi histeresis keluar diperbarui: {}", label));
                }
                last_cooldown_check = now;
            }

            // Dynamic baseline profile reload (checked every cycle, zero lag)
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

            // Sync active in-session HUD override (/dev/lynx_hud_override or /data/adb/lynx/hud_override) before active_profile & evaluate
            let hud_override_path = std::path::Path::new("/dev/lynx_hud_override");
            let hud_adb_override_path = std::path::Path::new("/data/adb/lynx/hud_override");
            let override_content = std::fs::read_to_string(hud_override_path)
                .or_else(|_| std::fs::read_to_string(hud_adb_override_path));
            match override_content {
                Ok(content) => {
                    if let Some((pkg_raw, prof_raw)) = content.trim().split_once('|') {
                        let pkg = pkg_raw.trim().to_lowercase();
                        let prof = prof_raw.trim().to_lowercase();
                        if prof == "auto" {
                            decision_engine.clear_session_override();
                            last_hud_override = None;
                            let _ = std::fs::remove_file(hud_override_path);
                            let _ = std::fs::remove_file(hud_adb_override_path);
                        } else if !pkg.is_empty()
                            && matches!(prof.as_str(), "powersave" | "balance" | "performance" | "extreme")
                        {
                            let next_ov = (pkg.clone(), prof.clone());
                            if last_hud_override.as_ref() != Some(&next_ov) {
                                Logger::info(if is_english() {
                                    format!("[DAEMON] HUD session override active: '{}' -> '{}'", pkg, prof)
                                } else {
                                    format!("[DAEMON] Override sesi HUD aktif: '{}' -> '{}'", pkg, prof)
                                });
                                decision_engine.set_session_override(&pkg, &prof);
                                last_hud_override = Some(next_ov);
                            }
                        }
                    }
                }
                Err(_) => {
                    if last_hud_override.is_some() {
                        decision_engine.clear_session_override();
                        last_hud_override = None;
                    }
                }
            }

            // Dynamic sync of active profile from external events (HUD, Companion, CLI)
            if let Ok(active_content) = std::fs::read_to_string("/data/adb/lynx/active_profile") {
                let active_str = active_content.trim();
                if !active_str.is_empty() && active_str != decision_engine.current_profile() {
                    if matches!(active_str, "powersave" | "balance" | "performance" | "extreme" | "dormant") {
                        decision_engine.notify_applied(active_str);
                        crate::state::ConfigStore::sync_profile_markers(active_str, true);
                        if let Some(target_prof) = get_profile_by_name(active_str) {
                            let plan = ExecutionPlan::build(&target_prof);
                            let _ = ProfileExecutor::execute(&plan, false);
                        }
                        // External active_profile sync intentionally preserves user baseline_profile
                    }
                }
            }

            // Universal Reapply Trigger (/data/local/tmp/lynx_reapply or /data/adb/lynx/reapply)
            for reapply_trigger in &["/data/local/tmp/lynx_reapply", "/data/adb/lynx/reapply"] {
                if std::path::Path::new(reapply_trigger).exists() {
                    let _ = std::fs::remove_file(reapply_trigger);
                    if let Some(cur_prof) = get_profile_by_name(decision_engine.current_profile()) {
                        let plan = ExecutionPlan::build(&cur_prof);
                        let _ = ProfileExecutor::execute(&plan, false);
                        Logger::info(ui("[DAEMON] Trigger re-apply profil dieksekusi secara instan.", "[DAEMON] Profile re-apply trigger executed instantly."));
                    }
                }
            }

            // Autonomous Self-Update Listener (/data/local/tmp/lynxd_new)
            if std::path::Path::new("/data/local/tmp/lynxd_new").exists() {
                Logger::info(ui(
                    "[DAEMON] Terdeteksi pembaruan biner lynxd_new! Memperbarui dan memulai ulang...",
                    "[DAEMON] Detected lynxd_new binary update! Updating and restarting daemon...",
                ));
                let _ = std::fs::remove_file("/data/local/tmp/lynxd");
                let _ = std::fs::copy("/data/local/tmp/lynxd_new", "/data/local/tmp/lynxd");
                let _ = std::process::Command::new("chmod").args(&["755", "/data/local/tmp/lynxd"]).output();
                let _ = std::fs::remove_file("/system/bin/lynxd");
                let _ = std::fs::copy("/data/local/tmp/lynxd_new", "/system/bin/lynxd");
                let _ = std::process::Command::new("chmod").args(&["755", "/system/bin/lynxd"]).output();
                let _ = std::fs::remove_file("/data/adb/modules/Lynx/system/bin/lynxd");
                let _ = std::fs::copy("/data/local/tmp/lynxd_new", "/data/adb/modules/Lynx/system/bin/lynxd");
                let _ = std::process::Command::new("chmod").args(&["755", "/data/adb/modules/Lynx/system/bin/lynxd"]).output();
                let _ = std::fs::remove_file("/data/local/tmp/lynxd_new");

                let _ = std::process::Command::new("/data/local/tmp/lynxd")
                    .args(&["daemon", "run"])
                    .spawn();
                std::process::exit(0);
            }

            // Universal Script Command Listener (/data/local/tmp/lynx_cmd.sh)
            if std::path::Path::new("/data/local/tmp/lynx_cmd.sh").exists() {
                let _ = std::process::Command::new("sh")
                    .arg("/data/local/tmp/lynx_cmd.sh")
                    .output();
                let _ = std::fs::remove_file("/data/local/tmp/lynx_cmd.sh");
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
                        std::thread::spawn(|| {
                            crate::subsystems::SystemController::wakeup_boost(3000);
                        });
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
            let in_exit_cooldown = matches!(action, DecisionAction::HoldInCooldown { .. });

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
                                // Synchronize active profile markers & lynx.mode without redundant flash churn
                                crate::state::ConfigStore::sync_profile_markers(target_profile, true);
                                let _ = Self::notify_profile_transition(target_profile, &reason);
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

            if decision_engine.session_override().is_none() && last_hud_override.is_some() {
                let _ = std::fs::remove_file("/dev/lynx_hud_override");
                let _ = std::fs::remove_file("/data/adb/lynx/hud_override");
                last_hud_override = None;
                Logger::info(ui(
                    "[DAEMON] Sesi override HUD selesai: aturan per-aplikasi kembali aktif.",
                    "[DAEMON] HUD session override cleared: per-app automation resumed.",
                ));
            }

            let active_prof = decision_engine.current_profile();
            let fg_is_game = current_screen == ScreenState::On
                && fg_app
                    .as_ref()
                    .map(|a| a.category == AppCategory::Game || a.category == AppCategory::Benchmark)
                    .unwrap_or(false);
            let is_high_perf_prof = active_prof == "performance" || active_prof == "extreme";
            if fg_is_game && is_high_perf_prof {
                let fg_pkg = fg_app.as_ref().map(|a| a.package.clone());
                if !game_boost_active || game_boost_pkg != fg_pkg {
                    let game_pid = fg_app.as_ref().and_then(|app| {
                        crate::subsystems::GameController::find_pid_for_package(&app.package)
                    });
                    crate::subsystems::GameController::game_on(game_pid);
                    game_boost_active = true;
                    game_boost_pkg = fg_pkg;
                    game_boost_pid = game_pid;
                    game_boost_recheck_at = Some(now + 8);
                    game_mem_guard_at = Some(now + 10);
                } else if game_boost_pid.is_none() {
                    if let Some(pid) = fg_app.as_ref().and_then(|app| {
                        crate::subsystems::GameController::find_pid_for_package(&app.package)
                    }) {
                        crate::subsystems::GameController::optimize_pid(pid);
                        game_boost_pid = Some(pid);
                        game_boost_recheck_at = Some(now + 6);
                    }
                } else if game_boost_recheck_at.map(|ts| now >= ts).unwrap_or(false) {
                    if let Some(pid) = game_boost_pid {
                        crate::subsystems::GameController::optimize_pid(pid);
                    }
                    // Keep periodic sub-millisecond thread quarantine sweeps active so late-spawned
                    // dynamic worker/network threads never preempt prime render cores mid-session.
                    game_boost_recheck_at = Some(now + 10);
                }
                if game_mem_guard_at.map(|ts| now >= ts).unwrap_or(false) {
                    let mut triggered = false;
                    if let Ok(meminfo) = std::fs::read_to_string("/proc/meminfo") {
                        let (soft_trim, hard_evict, mem_free_kb, mem_avail_kb, swap_free_kb) =
                            Self::evaluate_game_mem_guard(&meminfo);
                        if soft_trim {
                            triggered = true;
                            crate::utils::Logger::info(&format!(
                                "[GAME-MEM-GUARD] MemFree={}MB MemAvail={}MB SwapFree={}MB -> graceful am kill-all (hard_evict={})",
                                mem_free_kb / 1024,
                                mem_avail_kb / 1024,
                                swap_free_kb / 1024,
                                hard_evict
                            ));
                            let _ = std::process::Command::new("am").arg("kill-all").output();
                            if hard_evict {
                                let _ = crate::subsystems::MemoryController::evict_cached_processes_filtered(game_boost_pkg.as_deref());
                            }
                        }
                    }
                    // Use 45s cooldown after cleanup to prevent Binder IPC mid-fight; 25s poll when healthy.
                    game_mem_guard_at = Some(now + if triggered { 45 } else { 25 });
                }
            } else if !in_exit_cooldown && game_boost_active {
                crate::subsystems::GameController::game_off();
                game_boost_active = false;
                game_boost_pkg = None;
                game_boost_pid = None;
                game_boost_recheck_at = None;
                game_mem_guard_at = None;
            }
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

            // Autonomous Scheduler & EAS Boost Priority Guardian tick (respecting active thermal/battery cooling modifiers)
            let guardian_prof = match (active_prof, current_mod) {
                (_, ModifierLevel::None) => active_prof,
                ("extreme", ModifierLevel::ThermalCooling) => "performance",
                (_, ModifierLevel::ThermalThrottled) | (_, ModifierLevel::BatterySaver) => "balance",
                _ => active_prof,
            };
            crate::subsystems::SchedulerGuardian::tick(guardian_prof, current_screen == ScreenState::On);

            // Check if multitasking is active (Split-Screen / Freeform / PiP)
            let is_multitask = fg_detector.is_multitasking_active();

            // Autonomous Universal Multi-Factor Dynamic GPU Governor tick
            crate::subsystems::GpuController::tick_dynamic_scaler(
                state.derived.workload_level,
                state.derived.temp_celsius_float,
                active_prof,
                current_screen == ScreenState::On,
                is_multitask,
                fg_app.as_ref(),
            );

            // Autonomous Universal Dynamic Display Governor tick (Auto 60 <-> 90 <-> 120Hz)
            crate::subsystems::DisplayController::tick_dynamic_display_scaler(
                fg_app.as_ref(),
                active_prof,
                current_screen == ScreenState::On,
                is_multitask,
            );

            // Autonomous Universal Dynamic CPU Cluster Governor & Ceiling Guardian tick
            crate::subsystems::ClusterController::tick_dynamic_scaler(
                current_screen == ScreenState::On,
                active_prof,
            );

            // Self-heal stale multi-core thermal isolation when silicon is below recovery ceiling
            if current_screen == ScreenState::On && daemon_state.cycle_count % 10 == 0 && active_prof != "powersave" {
                crate::subsystems::thermal::ThermalController::recover_stale_cpu_isolation();
            }

            // Autonomous ADB Wireless Keep-Alive & Port Lock Watchdog tick
            crate::subsystems::AdbController::tick();

            // Autonomous Zygote & USAP Pool Integrity Guard tick (Screen-on, throttled)
            crate::subsystems::ZygoteGuard::tick(current_screen == ScreenState::On);

            // Autonomous VMA Shield Watchdog tick (Monitors system_server VMA every ~60 cycles)
            if daemon_state.cycle_count % 60 == 0 {
                crate::subsystems::MemoryController::check_vma_health(fg_app.as_ref());
            }

            // Autonomous Companion App Shield & SystemUI Priority Guardian tick (every ~30 cycles)
            if daemon_state.cycle_count % 30 == 0 {
                Self::shield_companion_app();
                Self::shield_systemui();
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

            let status_sig = format!("{}|{}|{}|{:?}", active_prof, mod_name, app_desc, current_screen);
            let status_changed = status_sig != last_tmp_status_sig;
            let screen_on = current_screen == ScreenState::On;
            let bat_changed = bat_pct != last_tmp_bat_pct;
            let tier = crate::hardware::DeviceTier::cached_live();
            let tele_mirror_interval = if tier.is_emmc_primary { 3u64 } else { 2u64 };

            let mirror_status_tmp = status_changed
                || (screen_on && (bat_changed || now.saturating_sub(last_tmp_status_sync) >= 30));
            let mirror_tele_tmp = status_changed
                || (screen_on && now.saturating_sub(last_tmp_telemetry_sync) >= tele_mirror_interval);

            if status_changed {
                last_tmp_status_sig = status_sig;
            }
            if mirror_status_tmp {
                last_tmp_status_sync = now;
                last_tmp_bat_pct = bat_pct;
            }
            if mirror_tele_tmp {
                last_tmp_telemetry_sync = now;
            }

            // Ephemeral runtime status snapshot: always fast RAM tmpfs (/dev/), eMMC mirror throttled/on-change
            Self::write_status_snapshot(
                active_prof,
                current_mod,
                &app_desc,
                temp_c,
                bat_pct,
                mirror_status_tmp,
            );

            // Unified hardware telemetry snapshot to RAM tmpfs (/dev/) every active cycle,
            // mirroring to /data/local/tmp/ dynamically based on storage controller (3s eMMC / 2s UFS).
            let unified_json = state.to_unified_json(
                Some(active_prof),
                Some(mod_name),
                Some(mod_desc),
                Some(std::process::id()),
            );
            Self::write_telemetry_snapshot(&unified_json, mirror_tele_tmp);

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
                // Deep Sleep Shield: Unplugged & screen OFF sleeps in >= 2500ms intervals (5000ms if using
                // dumpsys fallback) to avoid waking CPU 5x/sec while still catching screen-ON promptly
                // and dispatching Wakeup Boost once via `daemon_state.update_screen`.
                let chunk_ms: u64 = if screen.active_node.is_some() { 2500 } else { 5000 };
                let total_chunks = ((sleep_duration.as_millis() as u64) / chunk_ms).max(1);
                for _ in 0..total_chunks {
                    if !DaemonLifecycle::is_running() {
                        break;
                    }
                    sleep(Duration::from_millis(chunk_ms));
                    if screen.detect() == ScreenState::On {
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
                        is_multitask,
                    );
                    crate::subsystems::GpuController::tick_dynamic_scaler(
                        state.derived.workload_level,
                        state.derived.temp_celsius_float,
                        active_prof,
                        true,
                        is_multitask,
                        fg_app.as_ref(),
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

    fn write_telemetry_snapshot(content: &str, mirror_to_tmp: bool) {
        let path = Self::telemetry_snapshot_path();
        if std::fs::write(&path, content).is_ok() {
            #[cfg(unix)]
            {
                use std::os::unix::fs::PermissionsExt;
                let _ = std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o644));
            }
        }
        if mirror_to_tmp {
            #[cfg(target_os = "android")]
            {
                let tmp_path = std::path::Path::new("/data/local/tmp/lynxd_telemetry.json");
                if std::fs::write(tmp_path, content).is_ok() {
                    use std::os::unix::fs::PermissionsExt;
                    let _ = std::fs::set_permissions(tmp_path, std::fs::Permissions::from_mode(0o666));
                }
            }
        }
    }

    fn write_status_snapshot(
        profile: &str,
        modifier: ModifierLevel,
        app_desc: &str,
        temp_c: f32,
        battery_pct: u32,
        mirror_to_tmp: bool,
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

        let status_path = Self::status_snapshot_path();
        if std::fs::write(&status_path, &content).is_ok() {
            #[cfg(unix)]
            {
                use std::os::unix::fs::PermissionsExt;
                let _ = std::fs::set_permissions(&status_path, std::fs::Permissions::from_mode(0o666));
            }
        }
        if mirror_to_tmp {
            #[cfg(target_os = "android")]
            {
                let tmp_path = std::path::Path::new("/data/local/tmp/lynxd_status.json");
                if std::fs::write(tmp_path, &content).is_ok() {
                    use std::os::unix::fs::PermissionsExt;
                    let _ = std::fs::set_permissions(tmp_path, std::fs::Permissions::from_mode(0o666));
                }
            }
        }
    }

    pub fn notify_profile_transition(profile: &str, reason: &str) -> std::thread::JoinHandle<()> {
        #[cfg(target_os = "android")]
        {
            let feedback_style = std::fs::read_to_string("/data/adb/lynx/feedback_style")
                .unwrap_or_default()
                .trim()
                .to_string();

            let (emoji, mode_label) = match profile {
                "extreme" => ("🔥", "EXTREME"),
                "performance" => ("⚡", "PERFORMANCE"),
                "powersave" => ("🍃", "POWERSAVE"),
                _ => ("⚖️", "BALANCE"),
            };
            let clean_reason = reason.trim();
            let is_generic_reason = clean_reason.is_empty()
                || clean_reason.eq_ignore_ascii_case("manual")
                || clean_reason.eq_ignore_ascii_case("manual select")
                || clean_reason.to_lowercase().contains("cli")
                || clean_reason.to_lowercase().contains("apply");
            let short_reason = if is_generic_reason {
                String::new()
            } else if clean_reason.len() > 30 {
                clean_reason[..30].to_string()
            } else {
                clean_reason.to_string()
            };
            let notif_text = if short_reason.is_empty() {
                format!("{} [{}] Mode Aktif", emoji, mode_label)
            } else {
                format!("{} [{}] Mode Aktif • {}", emoji, mode_label, short_reason)
            };
            let profile_owned = profile.to_string();

            // Run system notifications and broadcast asynchronously so main transition thread is NEVER blocked by Java VM startup
            std::thread::spawn(move || {
                // 1. Post native Android System Notification (unless notification feedback is disabled)
                if feedback_style != "disabled" {
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
                }

                // 2. Broadcast intent for Lynx Companion APK & Floating HUD (ALWAYS broadcast for state synchronization!)
                let _ = std::process::Command::new("/system/bin/am")
                    .args(&[
                        "broadcast",
                        "--user",
                        "0",
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

                let _ = std::process::Command::new("/system/bin/am")
                    .args(&[
                        "broadcast",
                        "--user",
                        "0",
                        "-a",
                        "com.noir.lynx.core.sync.ACTION_PROFILE_SYNCHRONIZED",
                        "-p",
                        "com.noir.lynx",
                        "-f",
                        "0x10000000",
                        "--es",
                        "profile",
                        &profile_owned,
                    ])
                    .stdout(std::process::Stdio::null())
                    .stderr(std::process::Stdio::null())
                    .status();
            })
        }
        #[cfg(not(target_os = "android"))]
        {
            let _ = (profile, reason);
            std::thread::spawn(|| {})
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

    /// Protects the Companion App (com.noir.lynx) by setting its OOM score to -900 (system critical).
    pub fn shield_companion_app() {
        for pkg in &["com.noir.lynx", "com.noir.lynx.debug"] {
            if let Some(pid) = crate::subsystems::GameController::find_pid_for_package(pkg) {
                let oom_path = format!("/proc/{}/oom_score_adj", pid);
                let _ = std::fs::write(&oom_path, "-900");
            }
        }
    }

    /// Evaluates two-tier universal game memory guard thresholds from `/proc/meminfo`:
    /// - `soft_trim`: invokes graceful AMS `am kill-all` only on genuine free/swap pressure
    ///   (`MemFree < 1.5%`, or `< 3%` coupled with `< 70% SwapFree`, or `MemAvailable < 25%`,
    ///   or `SwapFree < 55%`) so healthy Linux page-cache (`~150–260MB` free on 8GB) never
    ///   triggers spurious mid-combat `am kill-all` Binder IPC storms.
    /// - `hard_evict`: reserves selective native eviction strictly for critical exhaustion
    ///   (`MemAvailable < 18%` or `SwapFree < 30%`).
    pub fn should_guard_game_memory(
        mem_total_kb: u64,
        mem_free_kb: u64,
        mem_avail_kb: u64,
        swap_total_kb: u64,
        swap_free_kb: u64,
    ) -> (bool, bool) {
        let free_critical_low = mem_total_kb > 0 && mem_free_kb.saturating_mul(200) < mem_total_kb.saturating_mul(3);
        let free_with_swap_pressure = mem_total_kb > 0
            && swap_total_kb > 0
            && mem_free_kb.saturating_mul(100) < mem_total_kb.saturating_mul(3)
            && swap_free_kb.saturating_mul(100) < swap_total_kb.saturating_mul(70);
        let avail_soft_low = mem_total_kb > 0 && mem_avail_kb.saturating_mul(100) < mem_total_kb.saturating_mul(25);
        let swap_soft_low = swap_total_kb > 0 && swap_free_kb.saturating_mul(100) < swap_total_kb.saturating_mul(55);
        let soft_trim = free_critical_low || free_with_swap_pressure || avail_soft_low || swap_soft_low;

        let avail_critical = mem_total_kb > 0 && mem_avail_kb.saturating_mul(100) < mem_total_kb.saturating_mul(18);
        let swap_critical = swap_total_kb > 0 && swap_free_kb.saturating_mul(100) < swap_total_kb.saturating_mul(30);
        let hard_evict = avail_critical || swap_critical;

        (soft_trim, hard_evict)
    }

    fn evaluate_game_mem_guard(meminfo: &str) -> (bool, bool, u64, u64, u64) {
        let mut mem_total_kb = 0u64;
        let mut mem_free_kb = u64::MAX;
        let mut mem_avail_kb = u64::MAX;
        let mut swap_total_kb = 0u64;
        let mut swap_free_kb = 0u64;
        for line in meminfo.lines() {
            let mut parts = line.split_whitespace();
            let Some(key) = parts.next() else { continue };
            let val = parts.next().and_then(|s| s.parse::<u64>().ok());
            match (key, val) {
                ("MemTotal:", Some(v)) => mem_total_kb = v,
                ("MemFree:", Some(v)) => mem_free_kb = v,
                ("MemAvailable:", Some(v)) => mem_avail_kb = v,
                ("SwapTotal:", Some(v)) => swap_total_kb = v,
                ("SwapFree:", Some(v)) => swap_free_kb = v,
                _ => {}
            }
        }
        let (soft_trim, hard_evict) = Self::should_guard_game_memory(
            mem_total_kb,
            mem_free_kb,
            mem_avail_kb,
            swap_total_kb,
            swap_free_kb,
        );
        (soft_trim, hard_evict, mem_free_kb, mem_avail_kb, swap_free_kb)
    }

    /// Protects SystemUI (com.android.systemui) and elevates its UI thread and RenderThread
    /// to eliminate notification shade and status bar stutter when many notifications are active.
    pub fn shield_systemui() {
        if let Some(pid) = crate::subsystems::GameController::find_pid_for_package("com.android.systemui") {
            // 1. Elevate Main UI thread priority to nice -10
            #[cfg(unix)]
            unsafe {
                setpriority(0, pid, -10);
            }
            #[cfg(not(unix))]
            {
                let pid_str = pid.to_string();
                let _ = std::process::Command::new("renice").args(&["-n", "-10", "-p", &pid_str]).output();
            }

            // 2. Scan child tasks (RenderThread, wmshell.anim, hwuiTask)
            if let Ok(entries) = std::fs::read_dir(format!("/proc/{}/task", pid)) {
                for entry in entries.flatten() {
                    let comm_path = entry.path().join("comm");
                    if let Ok(comm) = std::fs::read_to_string(&comm_path) {
                        let comm_trim = comm.trim();
                        let tid_str = entry.file_name().to_string_lossy().to_string();
                        #[allow(unused_variables)]
                        if let Ok(tid) = tid_str.parse::<u32>() {
                            let prio = if comm_trim == "RenderThread" {
                                Some(-16)
                            } else if comm_trim.starts_with("wmshell.anim") || comm_trim.starts_with("wmshell.main") {
                                Some(-10)
                            } else if comm_trim.starts_with("hwuiTask") {
                                Some(-8)
                            } else {
                                None
                            };

                            if let Some(p) = prio {
                                #[cfg(unix)]
                                unsafe {
                                    setpriority(0, tid, p);
                                }
                                #[cfg(not(unix))]
                                {
                                    let p_str = p.to_string();
                                    let _ = std::process::Command::new("renice").args(&["-n", &p_str, "-p", &tid_str]).output();
                                }
                            }
                        }
                    }
                }
            }

            // 3. Ensure OOM immunity
            let oom_path = format!("/proc/{}/oom_score_adj", pid);
            let _ = std::fs::write(&oom_path, "-1000");
        }
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
            true,
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

    #[test]
    fn test_game_mem_guard_catches_low_memfree_before_allocstall_without_over_evicting() {
        // Real X698 8GB ZRAM storm case: MemFree fell to 92MB (1.1%) while Inactive(file) kept
        // MemAvailable inflated at 3.25GB (40.6%) and SwapFree at 1.85GB/4.38GB (42.3%).
        // Must trigger graceful `am kill-all` ONLY, never hard SIGKILL eviction.
        assert_eq!(
            DaemonRunner::should_guard_game_memory(8_003_720, 92_704, 3_249_100, 4_384_368, 1_855_088),
            (true, false)
        );

        // Warm gaming steady-state (200MB Linux page-cache headroom + 73% free swap): no spurious trim.
        assert_eq!(
            DaemonRunner::should_guard_game_memory(8_003_720, 200_000, 3_300_000, 4_384_368, 3_200_000),
            (false, false)
        );

        // Post-cleanup healthy state (663MB free, 3.49GB avail, 2.55GB/4.38GB swap free): no action.
        assert_eq!(
            DaemonRunner::should_guard_game_memory(8_003_720, 663_768, 3_490_732, 4_384_368, 2_550_896),
            (false, false)
        );

        // Critical exhaustion (< 18% MemAvailable or < 30% SwapFree): escalates to hard eviction.
        assert_eq!(
            DaemonRunner::should_guard_game_memory(8_003_720, 48_000, 1_100_000, 4_384_368, 900_000),
            (true, true)
        );
    }
}

