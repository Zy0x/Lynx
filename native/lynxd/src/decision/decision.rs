use crate::daemon::foreground::rules::{AppCategory, ForegroundApp};
use crate::daemon::screen::ScreenState;
use crate::decision::cooldown::{CooldownManager, CooldownStatus};
use crate::decision::modifier::{AdaptiveGovernor, ModifierLevel};
use crate::decision::rules::SwitchingPolicy;
use crate::state::DeviceState;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum DecisionAction {
    MaintainCurrent,
    HoldInCooldown {
        remaining_secs: u64,
    },
    ApplyProfile {
        target_profile: &'static str,
        reason: String,
    },
    ApplyModifier {
        target_profile: &'static str,
        modifier: ModifierLevel,
        reason: &'static str,
    },
    Inhibited {
        target_profile: &'static str,
        reason: &'static str,
    },
}

pub struct DecisionEngine {
    policy: SwitchingPolicy,
    cooldown: CooldownManager,
    adaptive: AdaptiveGovernor,
    current_profile: String,
    baseline_profile: String,
    last_target_app: Option<String>,
}

impl DecisionEngine {
    pub fn new(initial_profile: &str, cooldown_seconds: u64) -> Self {
        Self {
            policy: SwitchingPolicy::default(),
            cooldown: CooldownManager::new(cooldown_seconds),
            adaptive: AdaptiveGovernor::new(),
            current_profile: initial_profile.to_string(),
            baseline_profile: initial_profile.to_string(),
            last_target_app: None,
        }
    }

    pub fn current_profile(&self) -> &str {
        &self.current_profile
    }

    pub fn baseline_profile(&self) -> &str {
        &self.baseline_profile
    }

    pub fn update_baseline_profile(&mut self, new_baseline: &str) {
        if !new_baseline.is_empty() && self.baseline_profile != new_baseline {
            self.baseline_profile = new_baseline.to_string();
        }
    }

    pub fn last_target_app(&self) -> Option<&str> {
        self.last_target_app.as_deref()
    }

    pub fn current_modifier(&self) -> ModifierLevel {
        self.adaptive.current_modifier()
    }

    pub fn set_cooldown_duration(&mut self, duration: u64) {
        self.cooldown.set_buffer_duration(duration);
    }

    pub fn cooldown_duration(&self) -> u64 {
        self.cooldown.buffer_duration()
    }

    pub fn notify_applied(&mut self, profile_name: &str) {
        self.current_profile = profile_name.to_string();
    }

    /// Evaluates the current system state, foreground app, and telemetry to decide the target action.
    /// Strictly deterministic: prevents profile flip-flops via the hysteresis cooldown manager,
    /// and adapts runtime operating envelopes via the Adaptive Governor modifier layer.
    pub fn evaluate(
        &mut self,
        fg_app: Option<&ForegroundApp>,
        screen: ScreenState,
        telemetry: &DeviceState,
        now: u64,
    ) -> DecisionAction {
        // ── 1. Screen OFF Guard ─────────────────────────────────────────────
        if screen == ScreenState::Off {
            self.adaptive.reset();
            self.cooldown.on_game_active();
            self.last_target_app = None;
            // If screen turned off while in performance or extreme, cleanly revert to balance (or powersave) to save battery
            // BUT if the device is plugged into power and baseline_profile is high performance, keep performance active!
            let is_plugged = crate::hardware::ChargingRegulator::is_hardware_plugged();
            if !is_plugged && (self.current_profile == "performance" || self.current_profile == "extreme") {
                let fallback = match self.baseline_profile.as_str() {
                    "powersave" => "powersave",
                    _ => "balance",
                };
                return DecisionAction::ApplyProfile {
                    target_profile: fallback,
                    reason: "Screen turned OFF (Reverting to baseline to prevent pocket battery drain)"
                        .to_string(),
                };
            }
            return DecisionAction::MaintainCurrent;
        }

        // ── 2. Screen ON Evaluation ─────────────────────────────────────────
        let is_target_app = match fg_app {
            Some(app) => {
                app.category == AppCategory::Game
                    || app.category == AppCategory::Benchmark
                    || app.target_profile.is_some()
            }
            None => false,
        };

        if is_target_app {
            let app_name = fg_app.map(|a| a.package.as_str()).unwrap_or("Game");
            self.last_target_app = fg_app.map(|a| a.package.clone());
            let requested_profile: &'static str = match fg_app.and_then(|a| a.target_profile.as_deref()) {
                Some("extreme") => "extreme",
                Some("powersave") => "powersave",
                Some("balance") => "balance",
                Some("performance") => "performance",
                _ => {
                    if fg_app.map(|a| a.category) == Some(AppCategory::Benchmark) {
                        "extreme"
                    } else {
                        "performance"
                    }
                }
            };

            // A. Cancel any running exit cooldown
            self.cooldown.on_game_active();

            // B. If current profile does not match requested_profile, switch!
            if self.current_profile != requested_profile {
                match self.policy.can_boost_profile(requested_profile, telemetry) {
                    Ok(_) => {
                        self.adaptive.reset();
                        return DecisionAction::ApplyProfile {
                            target_profile: requested_profile,
                            reason: format!(
                                "Target application active ({}) requesting '{}' profile",
                                app_name, requested_profile
                            ),
                        };
                    }
                    Err(inhibit_reason) => {
                        return DecisionAction::Inhibited {
                            target_profile: requested_profile,
                            reason: inhibit_reason,
                        };
                    }
                }
            }

            // C. If already in requested_profile, evaluate Adaptive Governor modifier
            if self.current_profile == "performance" || self.current_profile == "extreme" {
                if let Some((new_modifier, reason)) = self.adaptive.update(telemetry) {
                    let target_prof = if self.current_profile == "extreme" {
                        "extreme"
                    } else {
                        "performance"
                    };
                    return DecisionAction::ApplyModifier {
                        target_profile: target_prof,
                        modifier: new_modifier,
                        reason,
                    };
                }
                return DecisionAction::MaintainCurrent;
            }
            DecisionAction::MaintainCurrent
        } else {
            // Non-target application (Normal, System, Media, or None)
            let fallback: &'static str = match self.baseline_profile.as_str() {
                "powersave" => "powersave",
                "performance" => "performance",
                "extreme" => "extreme",
                _ => "balance",
            };

            // If we are currently in powersave, but baseline is NOT powersave, revert to baseline
            if self.current_profile == "powersave" && fallback != "powersave" {
                self.adaptive.reset();
                self.last_target_app = None;
                return DecisionAction::ApplyProfile {
                    target_profile: fallback,
                    reason: "Screen active: Reverting powersave back to baseline profile for instant responsiveness"
                        .to_string(),
                };
            }

            // If current profile is ALREADY the fallback baseline profile, stay there!
            if self.current_profile == fallback {
                self.cooldown.on_game_active();
                self.last_target_app = None;
                return DecisionAction::MaintainCurrent;
            }

            // If device woke from Screen OFF (parked in balance) without exiting a game, restore baseline immediately
            if self.last_target_app.is_none() {
                self.adaptive.reset();
                return DecisionAction::ApplyProfile {
                    target_profile: fallback,
                    reason: format!(
                        "Restoring baseline profile '{}' for active foreground app",
                        fallback
                    ),
                };
            }

            // User left target app: enter or evaluate hysteresis cooldown before dropping back to baseline
            self.cooldown.on_game_exited(now, fallback, self.last_target_app.clone());

            match self.cooldown.evaluate(now) {
                CooldownStatus::Holding { remaining_secs } => {
                    DecisionAction::HoldInCooldown { remaining_secs }
                }
                CooldownStatus::Expired { fallback_profile } => {
                    self.adaptive.reset();
                    self.last_target_app = None;
                    DecisionAction::ApplyProfile {
                        target_profile: fallback_profile,
                        reason: "Target application closed or exit cooldown expired"
                            .to_string(),
                    }
                }
                CooldownStatus::Inactive => {
                    self.adaptive.reset();
                    self.last_target_app = None;
                    DecisionAction::ApplyProfile {
                        target_profile: fallback,
                        reason: "Target application closed (immediate reversion to baseline)"
                            .to_string(),
                    }
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::daemon::foreground::rules::Confidence;

    fn make_test_telemetry() -> DeviceState {
        DeviceState::default()
    }

    #[test]
    fn test_decision_game_entry_and_cooldown_exit() {
        let mut engine = DecisionEngine::new("balance", 5);
        let telemetry = make_test_telemetry();

        let game_app = ForegroundApp {
            package: "com.tencent.ig".to_string(),
            category: AppCategory::Game,
            confidence: Confidence::High,
            target_profile: None,
        };

        // 1. Enter game at t=100
        let action1 = engine.evaluate(Some(&game_app), ScreenState::On, &telemetry, 100);
        match action1 {
            DecisionAction::ApplyProfile { target_profile, .. } => {
                assert_eq!(target_profile, "performance");
            }
            _ => panic!("Expected ApplyProfile performance"),
        }
        engine.notify_applied("performance");

        // 2. Next tick at t=101: still in game -> MaintainCurrent
        let action2 = engine.evaluate(Some(&game_app), ScreenState::On, &telemetry, 101);
        assert_eq!(action2, DecisionAction::MaintainCurrent);

        // 3. User switches to WhatsApp at t=102: should HoldInCooldown
        let wa_app = ForegroundApp {
            package: "com.whatsapp".to_string(),
            category: AppCategory::Normal,
            confidence: Confidence::Low,
            target_profile: None,
        };
        let action3 = engine.evaluate(Some(&wa_app), ScreenState::On, &telemetry, 102);
        match action3 {
            DecisionAction::HoldInCooldown { remaining_secs } => {
                assert_eq!(remaining_secs, 5);
            }
            _ => panic!("Expected HoldInCooldown"),
        }

        // 4. User returns to game at t=104: cooldown cancelled, MaintainCurrent!
        let action4 = engine.evaluate(Some(&game_app), ScreenState::On, &telemetry, 104);
        assert_eq!(action4, DecisionAction::MaintainCurrent);

        // 5. User leaves game permanently at t=110
        let _ = engine.evaluate(Some(&wa_app), ScreenState::On, &telemetry, 110);
        // At t=116 (>5s after t=110): cooldown expires -> ApplyProfile balance!
        let action5 = engine.evaluate(Some(&wa_app), ScreenState::On, &telemetry, 116);
        match action5 {
            DecisionAction::ApplyProfile { target_profile, .. } => {
                assert_eq!(target_profile, "balance");
            }
            _ => panic!("Expected ApplyProfile balance"),
        }
    }

    #[test]
    fn test_decision_custom_extreme_game_profile() {
        let mut engine = DecisionEngine::new("balance", 5);
        let telemetry = make_test_telemetry();

        let extreme_game = ForegroundApp {
            package: "com.HoYoverse.hkrpgoversea".to_string(),
            category: AppCategory::Game,
            confidence: Confidence::High,
            target_profile: Some("extreme".to_string()),
        };

        let action = engine.evaluate(Some(&extreme_game), ScreenState::On, &telemetry, 100);
        match action {
            DecisionAction::ApplyProfile { target_profile, .. } => {
                assert_eq!(target_profile, "extreme");
            }
            _ => panic!("Expected ApplyProfile extreme"),
        }
    }

    #[test]
    fn test_decision_screen_off_revert() {
        let mut engine = DecisionEngine::new("performance", 5);
        let telemetry = make_test_telemetry();

        let action = engine.evaluate(None, ScreenState::Off, &telemetry, 100);
        match action {
            DecisionAction::ApplyProfile { target_profile, .. } => {
                assert_eq!(target_profile, "balance");
            }
            _ => panic!("Expected ApplyProfile balance on Screen OFF"),
        }
    }

    #[test]
    fn test_decision_inhibit_on_low_battery() {
        let mut engine = DecisionEngine::new("balance", 5);
        let mut telemetry = make_test_telemetry();
        telemetry.raw.battery.capacity = 10; // Low battery (<= 15%)

        let game_app = ForegroundApp {
            package: "com.miHoYo.GenshinImpact".to_string(),
            category: AppCategory::Game,
            confidence: Confidence::High,
            target_profile: None,
        };

        let action = engine.evaluate(Some(&game_app), ScreenState::On, &telemetry, 100);
        match action {
            DecisionAction::Inhibited { target_profile, reason } => {
                assert_eq!(target_profile, "performance");
                assert!(reason.contains("Battery"));
            }
            _ => panic!("Expected Inhibited decision on low battery"),
        }
    }

    #[test]
    fn test_decision_adaptive_modifier_in_game() {
        let mut engine = DecisionEngine::new("balance", 5);
        let mut telemetry = make_test_telemetry();
        telemetry.raw.battery.capacity = 80;
        telemetry.derived.temp_celsius_float = 38.0;

        let game_app = ForegroundApp {
            package: "com.tencent.ig".to_string(),
            category: AppCategory::Game,
            confidence: Confidence::High,
            target_profile: None,
        };

        // 1. Enter game at normal temp: ApplyProfile performance
        let action1 = engine.evaluate(Some(&game_app), ScreenState::On, &telemetry, 100);
        assert!(matches!(action1, DecisionAction::ApplyProfile { target_profile: "performance", .. }));
        engine.notify_applied("performance");

        // 2. Temp elevates to 46.0°C: ApplyModifier ThermalCooling
        telemetry.derived.temp_celsius_float = 46.0;
        let action2 = engine.evaluate(Some(&game_app), ScreenState::On, &telemetry, 101);
        match action2 {
            DecisionAction::ApplyModifier { target_profile, modifier, .. } => {
                assert_eq!(target_profile, "performance");
                assert_eq!(modifier, ModifierLevel::ThermalCooling);
            }
            _ => panic!("Expected ApplyModifier ThermalCooling"),
        }

        // 3. Next tick at 46.5°C: remains in ThermalCooling without redundant mutations
        telemetry.derived.temp_celsius_float = 46.5;
        let action3 = engine.evaluate(Some(&game_app), ScreenState::On, &telemetry, 102);
        assert_eq!(action3, DecisionAction::MaintainCurrent);

        // 4. Temp cools down to 41.0°C: releases modifier back to None
        telemetry.derived.temp_celsius_float = 41.0;
        let action4 = engine.evaluate(Some(&game_app), ScreenState::On, &telemetry, 103);
        match action4 {
            DecisionAction::ApplyModifier { target_profile, modifier, .. } => {
                assert_eq!(target_profile, "performance");
                assert_eq!(modifier, ModifierLevel::None);
            }
            _ => panic!("Expected ApplyModifier None"),
        }
    }

    #[test]
    fn test_decision_screen_on_powersave_reverts_to_baseline() {
        let mut engine = DecisionEngine::new("powersave", 5);
        // User's baseline is balance, but engine had powersave applied
        engine.baseline_profile = "balance".to_string();
        let telemetry = make_test_telemetry();

        let action = engine.evaluate(None, ScreenState::On, &telemetry, 100);
        match action {
            DecisionAction::ApplyProfile { target_profile, reason } => {
                assert_eq!(target_profile, "balance");
                assert!(reason.contains("Reverting powersave back to baseline"));
            }
            _ => panic!("Expected ApplyProfile balance when screen is ON and current profile is powersave"),
        }
    }

    #[test]
    fn test_decision_performance_baseline_retains_performance_in_normal_apps() {
        let mut engine = DecisionEngine::new("performance", 5);
        let telemetry = make_test_telemetry();

        let twitter_app = ForegroundApp {
            package: "com.twitter.android".to_string(),
            category: AppCategory::Media,
            confidence: Confidence::High,
            target_profile: None,
        };

        // When user opens Twitter while in performance baseline, it must MAINTAIN current performance!
        let action = engine.evaluate(Some(&twitter_app), ScreenState::On, &telemetry, 100);
        assert_eq!(action, DecisionAction::MaintainCurrent);
    }

    #[test]
    fn test_decision_wake_screen_restores_performance_baseline() {
        let mut engine = DecisionEngine::new("performance", 5);
        let telemetry = make_test_telemetry();

        // 1. Screen turns OFF: parks into balance
        let action1 = engine.evaluate(None, ScreenState::Off, &telemetry, 100);
        assert_eq!(
            action1,
            DecisionAction::ApplyProfile {
                target_profile: "balance",
                reason: "Screen turned OFF (Reverting to baseline to prevent pocket battery drain)".to_string(),
            }
        );
        engine.notify_applied("balance");

        // 2. Screen turns ON with Twitter: immediately restores performance baseline!
        let twitter_app = ForegroundApp {
            package: "com.twitter.android".to_string(),
            category: AppCategory::Media,
            confidence: Confidence::High,
            target_profile: None,
        };
        let action2 = engine.evaluate(Some(&twitter_app), ScreenState::On, &telemetry, 101);
        match action2 {
            DecisionAction::ApplyProfile { target_profile, .. } => {
                assert_eq!(target_profile, "performance");
            }
            _ => panic!("Expected immediate restoration of performance baseline upon screen wake"),
        }
    }
}
