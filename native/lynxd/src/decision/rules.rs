use crate::state::device_state::ThermalLevel;
use crate::state::DeviceState;

#[derive(Debug, Clone)]
pub struct SwitchingPolicy {
    pub min_battery_percent: u32,
    pub block_boost_on_throttling: bool,
}

impl Default for SwitchingPolicy {
    fn default() -> Self {
        Self {
            min_battery_percent: 15,
            block_boost_on_throttling: true,
        }
    }
}

impl SwitchingPolicy {
    /// Validates hardware envelope safety before allowing a boost transition to performance/extreme.
    pub fn can_boost(&self, telemetry: &DeviceState) -> Result<(), &'static str> {
        // Guard 1: Battery level safety (prevents sudden battery shutdown at low voltage)
        if telemetry.raw.battery.capacity <= self.min_battery_percent {
            return Err("Battery level <= 15% (Low battery guard prevents performance boost)");
        }

        // Guard 2: Thermal throttling safety
        if self.block_boost_on_throttling
            && telemetry.derived.thermal_level == ThermalLevel::Throttling
        {
            return Err("Thermal throttling active (Boost blocked to protect SoC temperature)");
        }

        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::state::device_state::{BatteryRaw, DerivedState, RawState};

    fn make_test_state(battery_pct: u32, thermal: ThermalLevel) -> DeviceState {
        DeviceState {
            timestamp: 100,
            health: Default::default(),
            raw: RawState {
                battery: BatteryRaw {
                    capacity: battery_pct,
                    temp_raw: 350,
                    current_ma: 0,
                    volt_mv: 4000,
                    status: "Discharging".to_string(),
                },
                ..Default::default()
            },
            derived: DerivedState {
                thermal_level: thermal,
                ..Default::default()
            },
        }
    }

    #[test]
    fn test_can_boost_guardrails() {
        let policy = SwitchingPolicy::default();

        // Safe case
        let ok_state = make_test_state(80, ThermalLevel::Normal);
        assert!(policy.can_boost(&ok_state).is_ok());

        // Low battery guardrail
        let low_bat_state = make_test_state(12, ThermalLevel::Normal);
        assert!(policy.can_boost(&low_bat_state).is_err());

        // Thermal throttling guardrail
        let hot_state = make_test_state(90, ThermalLevel::Throttling);
        assert!(policy.can_boost(&hot_state).is_err());
    }
}
