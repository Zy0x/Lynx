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

pub struct DaemonRunner;


impl DaemonRunner {
    /// Runs the autonomous Phase 4C daemon loop with Foreground App Awareness,
    /// Hysteresis Cooldown, and Deterministic Profile Switching.
    pub fn run() -> Result<(), String> {
        let pid = DaemonLifecycle::acquire_lock()?;
        Logger::info(format!("Lynx Daemon (lynxd) started successfully [PID: {}]", pid));
        Logger::info("Mode: Autonomous Profile Engine with Hysteresis & Deep Sleep Shield");
        crate::utils::update_module_prop_description("[ Auto (AI) Mode Active ] Deity Hybrid Architecture");

        let screen = ScreenDetector::probe();
        let mut fg_detector = ForegroundDetector::new();
        let mut daemon_state = DaemonState::new();

        let initial_profile = ProfileStateManager::load_active()
            .ok()
            .flatten()
            .map(|a| a.active_profile)
            .unwrap_or_else(|| "balance".to_string());

        let mut decision_engine = DecisionEngine::new(&initial_profile, 5);
        Logger::info(format!(
            "[DAEMON] Initialized DecisionEngine with baseline profile: '{}' (Exit Hysteresis: 5s)",
            initial_profile
        ));

        let monotonic_boot = std::time::Instant::now();
        let mut last_detect_instant = std::time::Instant::now();
        let mut last_maintenance_ts = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        while DaemonLifecycle::is_running() {
            let now = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap_or_default()
                .as_secs();

            let current_screen = screen.detect();

            // Screen transition tracking
            if daemon_state.update_screen(current_screen, now) {
                match current_screen {
                    ScreenState::Off => {
                        Logger::info("[DAEMON] Screen OFF detected. Activating Deep Sleep Shield (loop throttled to 20s).");
                    }
                    ScreenState::On => {
                        Logger::info("[DAEMON] Screen ON detected. Resuming active monitoring (interval: 5s).");
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
                    Logger::info(format!(
                        "[DAEMON] GAME_DETECTED_NS: {} | Foreground switch: '{}' [Category: {}, Confidence: {:?}]",
                        detect_ns,
                        app.package,
                        app.category.as_str(),
                        app.confidence
                    ));
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
                    Logger::info(format!(
                        "[DAEMON] Profile switch triggered -> '{}' (Reason: {})",
                        target_profile, reason
                    ));
                    if let Some(prof) = get_profile_by_name(target_profile) {
                        let plan = ExecutionPlan::build(&prof);
                        match ProfileExecutor::execute(&plan, false) {
                            Ok(report) => {
                                let total_transitions = daemon_state.increment_transition();
                                let commit_ns = switch_start.duration_since(monotonic_boot).as_nanos();
                                let delta_ns = last_detect_instant.elapsed().as_nanos();
                                let delta_ms = delta_ns as f64 / 1_000_000.0;
                                Logger::info(format!(
                                    "[DAEMON] PROFILE_COMMIT_NS: {} -> '{}' (Switch Latency delta: {:.2} ms [{} ns], Committed: {} nodes, Total Transitions: {})",
                                    commit_ns,
                                    target_profile,
                                    delta_ms,
                                    delta_ns,
                                    report.operations_committed,
                                    total_transitions
                                ));
                                decision_engine.notify_applied(target_profile);
                                if target_profile == "performance" || target_profile == "extreme" {
                                    crate::subsystems::GameController::game_on(None);
                                } else {
                                    crate::subsystems::GameController::game_off();
                                }
                            }
                            Err(e) => {
                                Logger::error(format!(
                                    "[DAEMON] Failed to apply profile '{}': {}",
                                    target_profile, e
                                ));
                            }
                        }
                    } else {
                        Logger::error(format!(
                            "[DAEMON] Target profile '{}' not found in registry!",
                            target_profile
                        ));
                    }
                }
                DecisionAction::ApplyModifier { target_profile, modifier, reason } => {
                    Logger::info(format!(
                        "[DAEMON] Adaptive Modifier triggered -> '{}' [{}] (Reason: {})",
                        target_profile,
                        modifier.as_str(),
                        reason
                    ));
                    if let Some(base_prof) = get_profile_by_name(target_profile) {
                        let adapted_prof = apply_modifier(&base_prof, modifier);
                        let plan = ExecutionPlan::build(&adapted_prof);
                        match ProfileExecutor::execute(&plan, false) {
                            Ok(report) => {
                                Logger::info(format!(
                                    "[DAEMON] Adaptive modifier applied successfully in {:.2} ms ({} nodes adjusted)",
                                    report.elapsed_micros as f64 / 1000.0,
                                    report.operations_committed
                                ));
                            }
                            Err(e) => {
                                Logger::error(format!(
                                    "[DAEMON] Failed to apply adaptive modifier: {}",
                                    e
                                ));
                            }
                        }
                    }
                }
                DecisionAction::HoldInCooldown { remaining_secs } => {
                    Logger::info(format!(
                        "[DAEMON] Target app exited: Holding '{}' in hysteresis cooldown ({}s remaining)...",
                        decision_engine.current_profile(),
                        remaining_secs
                    ));
                }
                DecisionAction::Inhibited { target_profile, reason } => {
                    Logger::warn(format!(
                        "[DAEMON] Boost transition to '{}' inhibited by safety guardrail: {}",
                        target_profile, reason
                    ));
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
                None => "None (Idle/ScreenOff)".to_string(),
            };

            Logger::info(format!(
                "[DAEMON] Telemetry: Temp: {:.1}°C | Battery: {}% | Workload: {} | Profile: {} | Screen: {:?} | App: {}",
                temp_c, bat_pct, workload, profile_desc, current_screen, app_desc
            ));

            daemon_state.update_telemetry(state.clone());
            daemon_state.cycle_count += 1;

            // Autonomous Charging Regulator & Safety Guard tick (Zero-fork native)
            ChargingRegulator::tick();

            // Native Daily Maintenance Scheduler (replaces legacy crond & core/cron/root)
            if current_screen == ScreenState::Off && now.saturating_sub(last_maintenance_ts) >= 86400 {
                Logger::info("[DAEMON] Executing scheduled 24h storage & memory maintenance...");
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

            // Adaptive sleep interval based on Screen State
            let sleep_duration = match current_screen {
                ScreenState::Off => Duration::from_secs(20), // Deep Sleep Shield: 20 seconds
                _ => Duration::from_secs(5),                // Active screen: 5 seconds
            };

            // Sleep in small 500ms chunks to respond immediately to SIGTERM/SIGINT
            let chunks = (sleep_duration.as_millis() / 500) as usize;
            for idx in 0..chunks {
                if !DaemonLifecycle::is_running() {
                    break;
                }
                if idx > 0 && idx % 6 == 0 && std::path::Path::new("/dev/lynx_extreme_charging").exists() {
                    ChargingRegulator::reapply_lock();
                }
                sleep(Duration::from_millis(500));
            }
        }

        Logger::info(format!(
            "Lynx Daemon shutting down gracefully after {} health cycles. Releasing PID lock...",
            daemon_state.cycle_count
        ));
        let _ = std::fs::remove_file(Self::status_snapshot_path());
        let _ = std::fs::remove_file(Self::telemetry_snapshot_path());
        DaemonLifecycle::release_lock();
        Logger::info("Lynx Daemon stopped cleanly.");

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

        let _ = std::fs::write(Self::status_snapshot_path(), content);
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

