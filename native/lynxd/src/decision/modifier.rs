use crate::profile::profile::Profile;
use crate::state::device_state::PowerState;
use crate::state::DeviceState;

/// Discrete levels for dynamic runtime modifiers.
/// These do not replace the user's intent or base profile (e.g. "performance"),
/// but dynamically adapt kernel operating envelopes to preserve stability and hardware health.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ModifierLevel {
    /// Normal operation: 100% of the base profile targets applied.
    None,

    /// Thermal pressure elevated (>= 45°C): slightly relaxes CPU floor (-15%)
    /// to dissipate thermal energy without inducing frame drops or governor switching.
    ThermalCooling,

    /// Critical thermal boundary (>= 48°C): throttles CPU floor (-30%) and disables
    /// GPU turbo boost to protect SoC hardware from permanent damage.
    ThermalThrottled,

    /// Battery level low (<= 25% and discharging): trims CPU floor (-10%)
    /// to preserve remaining battery runtime without crippling responsiveness.
    BatterySaver,
}

impl ModifierLevel {
    pub fn as_str(&self) -> &'static str {
        match self {
            ModifierLevel::None => "Normal (Full Base Profile)",
            ModifierLevel::ThermalCooling => "Thermal Cooling (-15% CPU floor)",
            ModifierLevel::ThermalThrottled => "Thermal Throttled (-30% CPU floor, GPU boost off)",
            ModifierLevel::BatterySaver => "Battery Saver (-10% CPU floor)",
        }
    }
}

/// Applies a modifier layer on top of a base profile, producing an adapted runtime Profile.
/// Guaranteed to preserve mathematical safety invariants (min <= max, clamp bounds).
pub fn apply_modifier(base: &Profile, level: ModifierLevel) -> Profile {
    let mut modified = base.clone();

    match level {
        ModifierLevel::None => modified,

        ModifierLevel::ThermalCooling => {
            // Relax CPU minimum frequency floor by 15%, clamped to minimum 20%
            modified.cpu.min_percent = modified.cpu.min_percent.saturating_sub(15).max(20);
            // Relax scheduler uclamp min floor by 15%, clamped to minimum 10%
            modified.scheduler.uclamp_min_percent =
                modified.scheduler.uclamp_min_percent.saturating_sub(15).max(10);
            modified
        }

        ModifierLevel::ThermalThrottled => {
            // Drop CPU floor by 30%, clamped to minimum 15%
            modified.cpu.min_percent = modified.cpu.min_percent.saturating_sub(30).max(15);
            // Drop scheduler uclamp min floor by 30%, clamped to minimum 10%
            modified.scheduler.uclamp_min_percent =
                modified.scheduler.uclamp_min_percent.saturating_sub(30).max(10);
            // Disable GPU turbo boost during critical thermal condition
            modified.gpu.boost = false;
            modified
        }

        ModifierLevel::BatterySaver => {
            // Reduce CPU floor by 10%, clamped to minimum 20%
            modified.cpu.min_percent = modified.cpu.min_percent.saturating_sub(10).max(20);
            modified
        }
    }
}

/// Adaptive Thermal & Power Governor.
/// Manages the dynamic modifier state using deterministic thresholds with hysteresis deadbands
/// to prevent rapid oscillation (flip-flopping) at temperature or battery boundaries.
#[derive(Debug, Clone)]
pub struct AdaptiveGovernor {
    current_modifier: ModifierLevel,
}

impl Default for AdaptiveGovernor {
    fn default() -> Self {
        Self::new()
    }
}

impl AdaptiveGovernor {
    // Thermal Thresholds with 3°C Hysteresis Deadbands
    pub const TEMP_COOLING_ENTER: f32 = 45.0;
    pub const TEMP_COOLING_EXIT: f32 = 42.0;

    pub const TEMP_THROTTLE_ENTER: f32 = 48.0;
    pub const TEMP_THROTTLE_EXIT: f32 = 45.0;

    // Battery Thresholds with 5% Hysteresis Deadband
    pub const BATTERY_SAVER_ENTER: u32 = 25;
    pub const BATTERY_SAVER_EXIT: u32 = 30;

    pub fn new() -> Self {
        Self {
            current_modifier: ModifierLevel::None,
        }
    }

    pub fn current_modifier(&self) -> ModifierLevel {
        self.current_modifier
    }

    pub fn reset(&mut self) {
        self.current_modifier = ModifierLevel::None;
    }

    /// Evaluates the device telemetry and updates the active modifier with hysteresis.
    /// Returns Some((new_modifier, reason)) if a modifier transition occurred, or None if unchanged.
    pub fn update(&mut self, state: &DeviceState) -> Option<(ModifierLevel, &'static str)> {
        let temp = state.derived.temp_celsius_float;
        let battery = state.raw.battery.capacity;
        let is_discharging = state.derived.power_state == PowerState::Discharging;

        let (target_level, reason) = self.resolve_target(temp, battery, is_discharging);

        if target_level != self.current_modifier {
            self.current_modifier = target_level;
            Some((target_level, reason))
        } else {
            None
        }
    }

    /// Resolves target modifier level with strict hysteresis deadbands against current state.
    fn resolve_target(
        &self,
        temp: f32,
        battery: u32,
        is_discharging: bool,
    ) -> (ModifierLevel, &'static str) {
        // ── 1. Critical Thermal Priority (>= 48°C) ──────────────────────────
        match self.current_modifier {
            ModifierLevel::ThermalThrottled => {
                // Must cool down below 45°C to exit throttled state
                if temp < Self::TEMP_THROTTLE_EXIT {
                    // Fall back to cooling if still warm, else normal
                    if temp >= Self::TEMP_COOLING_ENTER {
                        return (
                            ModifierLevel::ThermalCooling,
                            "Temperature cooled below 45°C; transitioning to Thermal Cooling",
                        );
                    } else if temp <= Self::TEMP_COOLING_EXIT {
                        return (
                            ModifierLevel::None,
                            "Temperature normalized below 42°C; releasing thermal modifier",
                        );
                    } else {
                        return (
                            ModifierLevel::ThermalCooling,
                            "Temperature in deadband (42-45°C); falling back to Thermal Cooling",
                        );
                    }
                }
                return (
                    ModifierLevel::ThermalThrottled,
                    "Maintaining Thermal Throttled state",
                );
            }
            _ => {
                if temp >= Self::TEMP_THROTTLE_ENTER {
                    return (
                        ModifierLevel::ThermalThrottled,
                        "SoC temperature >= 48°C; entering critical Thermal Throttled state",
                    );
                }
            }
        }

        // ── 2. Thermal Cooling Evaluation (45°C enter, 42°C exit) ───────────
        match self.current_modifier {
            ModifierLevel::ThermalCooling => {
                // Must cool down below 42°C to exit cooling state
                if temp <= Self::TEMP_COOLING_EXIT {
                    // Check if battery saver applies after cooling down
                    if is_discharging && battery <= Self::BATTERY_SAVER_ENTER {
                        return (
                            ModifierLevel::BatterySaver,
                            "Temperature normalized; battery <= 25%, activating Battery Saver",
                        );
                    }
                    return (
                        ModifierLevel::None,
                        "Temperature normalized below 42°C; releasing thermal modifier",
                    );
                }
                return (
                    ModifierLevel::ThermalCooling,
                    "Maintaining Thermal Cooling state (waiting to cool below 42°C)",
                );
            }
            _ => {
                if temp >= Self::TEMP_COOLING_ENTER {
                    return (
                        ModifierLevel::ThermalCooling,
                        "SoC temperature >= 45°C; activating Thermal Cooling modifier",
                    );
                }
            }
        }

        // ── 3. Battery Saver Evaluation (25% enter, 30% exit) ───────────────
        match self.current_modifier {
            ModifierLevel::BatterySaver => {
                // Exits if charging or battery charged above 30%
                if !is_discharging || battery >= Self::BATTERY_SAVER_EXIT {
                    return (
                        ModifierLevel::None,
                        "Battery charged or charger connected; releasing Battery Saver",
                    );
                }
                return (
                    ModifierLevel::BatterySaver,
                    "Maintaining Battery Saver state",
                );
            }
            _ => {
                if is_discharging && battery <= Self::BATTERY_SAVER_ENTER {
                    return (
                        ModifierLevel::BatterySaver,
                        "Battery level <= 25% while discharging; activating Battery Saver modifier",
                    );
                }
            }
        }

        (ModifierLevel::None, "Optimal thermal and power headroom")
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::profile::get_profile_by_name;
    use crate::state::device_state::{BatteryRaw, DerivedState, RawState};

    fn make_test_state(temp_c: f32, battery: u32, discharging: bool) -> DeviceState {
        DeviceState {
            timestamp: 100,
            health: Default::default(),
            raw: RawState {
                battery: BatteryRaw {
                    capacity: battery,
                    status: if discharging {
                        "Discharging".to_string()
                    } else {
                        "Charging".to_string()
                    },
                    ..Default::default()
                },
                ..Default::default()
            },
            derived: DerivedState {
                temp_celsius_float: temp_c,
                power_state: if discharging {
                    PowerState::Discharging
                } else {
                    PowerState::ChargingStandard
                },
                ..Default::default()
            },
        }
    }

    #[test]
    fn test_apply_modifier_transformations() {
        let base = get_profile_by_name("performance").expect("performance profile must exist");
        assert_eq!(base.cpu.min_percent, 60);
        assert_eq!(base.scheduler.uclamp_min_percent, 60);
        assert!(base.gpu.boost);

        // 1. ThermalCooling modifier
        let cooling = apply_modifier(&base, ModifierLevel::ThermalCooling);
        assert_eq!(cooling.cpu.min_percent, 45); // 60 - 15 = 45
        assert_eq!(cooling.scheduler.uclamp_min_percent, 45); // 60 - 15 = 45
        assert!(cooling.gpu.boost); // GPU boost preserved

        // 2. ThermalThrottled modifier
        let throttled = apply_modifier(&base, ModifierLevel::ThermalThrottled);
        assert_eq!(throttled.cpu.min_percent, 30); // 60 - 30 = 30
        assert_eq!(throttled.scheduler.uclamp_min_percent, 30); // 60 - 30 = 30
        assert!(!throttled.gpu.boost); // GPU boost disabled

        // 3. BatterySaver modifier
        let bat_saver = apply_modifier(&base, ModifierLevel::BatterySaver);
        assert_eq!(bat_saver.cpu.min_percent, 50); // 60 - 10 = 50
    }

    #[test]
    fn test_thermal_hysteresis_deadband() {
        let mut gov = AdaptiveGovernor::new();
        assert_eq!(gov.current_modifier(), ModifierLevel::None);

        // 1. Temp at 44.0°C (below 45°C threshold): no change
        let s1 = make_test_state(44.0, 80, true);
        assert!(gov.update(&s1).is_none());
        assert_eq!(gov.current_modifier(), ModifierLevel::None);

        // 2. Temp reaches 45.5°C: enters ThermalCooling
        let s2 = make_test_state(45.5, 80, true);
        let change = gov.update(&s2);
        assert!(change.is_some());
        assert_eq!(gov.current_modifier(), ModifierLevel::ThermalCooling);

        // 3. Temp drops to 44.0°C (inside deadband 42-45°C): MUST NOT exit cooling!
        let s3 = make_test_state(44.0, 80, true);
        assert!(gov.update(&s3).is_none());
        assert_eq!(gov.current_modifier(), ModifierLevel::ThermalCooling);

        // 4. Temp drops to 41.5°C (below 42°C exit threshold): exits to None!
        let s4 = make_test_state(41.5, 80, true);
        let change = gov.update(&s4);
        assert!(change.is_some());
        assert_eq!(gov.current_modifier(), ModifierLevel::None);
    }

    #[test]
    fn test_critical_thermal_throttle_and_recovery() {
        let mut gov = AdaptiveGovernor::new();

        // 1. Spike to 48.5°C: enters ThermalThrottled directly
        let s1 = make_test_state(48.5, 80, true);
        let change = gov.update(&s1);
        assert_eq!(change.unwrap().0, ModifierLevel::ThermalThrottled);
        assert_eq!(gov.current_modifier(), ModifierLevel::ThermalThrottled);

        // 2. Cools to 46.0°C (above 45°C exit): remains ThermalThrottled
        let s2 = make_test_state(46.0, 80, true);
        assert!(gov.update(&s2).is_none());
        assert_eq!(gov.current_modifier(), ModifierLevel::ThermalThrottled);

        // 3. Cools to 44.0°C (below 45°C): transitions to ThermalCooling
        let s3 = make_test_state(44.0, 80, true);
        let change = gov.update(&s3);
        assert_eq!(change.unwrap().0, ModifierLevel::ThermalCooling);
        assert_eq!(gov.current_modifier(), ModifierLevel::ThermalCooling);

        // 4. Cools to 41.0°C: transitions to None
        let s4 = make_test_state(41.0, 80, true);
        let change = gov.update(&s4);
        assert_eq!(change.unwrap().0, ModifierLevel::None);
        assert_eq!(gov.current_modifier(), ModifierLevel::None);
    }

    #[test]
    fn test_battery_saver_hysteresis() {
        let mut gov = AdaptiveGovernor::new();

        // 1. Battery at 26%: no change
        let s1 = make_test_state(38.0, 26, true);
        assert!(gov.update(&s1).is_none());

        // 2. Battery drops to 24%: enters BatterySaver
        let s2 = make_test_state(38.0, 24, true);
        let change = gov.update(&s2);
        assert_eq!(change.unwrap().0, ModifierLevel::BatterySaver);

        // 3. Battery at 28% (inside deadband 25-30%): remains in BatterySaver
        let s3 = make_test_state(38.0, 28, true);
        assert!(gov.update(&s3).is_none());

        // 4. Charger connected (Charging): exits BatterySaver immediately
        let s4 = make_test_state(38.0, 28, false);
        let change = gov.update(&s4);
        assert_eq!(change.unwrap().0, ModifierLevel::None);
    }
}
