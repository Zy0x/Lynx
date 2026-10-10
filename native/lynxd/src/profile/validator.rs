use crate::core::error::SysfsError;
use crate::profile::profile::Profile;
use std::path::PathBuf;

pub struct ProfileValidator;

impl ProfileValidator {
    pub fn validate(profile: &Profile) -> Result<(), Vec<SysfsError>> {
        let mut errors = Vec::new();

        // 1. CPU Target Validation
        if profile.cpu.max_percent < profile.cpu.min_percent {
            errors.push(SysfsError::InvalidValue {
                path: PathBuf::from(format!("profile/{}/cpu/max_percent", profile.name)),
                value: format!(
                    "max_percent ({}%) cannot be lower than min_percent ({}%)",
                    profile.cpu.max_percent, profile.cpu.min_percent
                ),
            });
        }

        if profile.cpu.max_percent > 100 || profile.cpu.min_percent > 100 {
            errors.push(SysfsError::InvalidValue {
                path: PathBuf::from(format!("profile/{}/cpu/percentages", profile.name)),
                value: "Percentages cannot exceed 100%".to_string(),
            });
        }

        // 2. Thermal Envelope Validation
        if profile.thermal.critical_temperature <= profile.thermal.allow_boost_temperature {
            errors.push(SysfsError::InvalidValue {
                path: PathBuf::from(format!("profile/{}/thermal/envelope", profile.name)),
                value: format!(
                    "critical_temperature ({}°C) must be strictly higher than allow_boost_temperature ({}°C)",
                    profile.thermal.critical_temperature, profile.thermal.allow_boost_temperature
                ),
            });
        }

        if profile.thermal.critical_temperature > 60 {
            errors.push(SysfsError::InvalidValue {
                path: PathBuf::from(format!("profile/{}/thermal/critical_temperature", profile.name)),
                value: format!(
                    "critical_temperature ({}°C) exceeds absolute hardware safety envelope of 60°C",
                    profile.thermal.critical_temperature
                ),
            });
        }

        // 3. Scheduler Policy Conflict Validation
        if profile.scheduler.prefer_latency && profile.scheduler.prefer_power {
            errors.push(SysfsError::InvalidValue {
                path: PathBuf::from(format!("profile/{}/scheduler/intent", profile.name)),
                value: "Conflicting scheduler intent: cannot simultaneously prioritize maximum power saving and minimum latency".to_string(),
            });
        }

        if errors.is_empty() {
            Ok(())
        } else {
            Err(errors)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::profile::{balanced, extreme, performance, powersave};

    #[test]
    fn test_all_standard_profiles_pass_validation() {
        assert!(ProfileValidator::validate(&powersave::get_profile()).is_ok());
        assert!(ProfileValidator::validate(&balanced::get_profile()).is_ok());
        assert!(ProfileValidator::validate(&performance::get_profile()).is_ok());
        assert!(ProfileValidator::validate(&extreme::get_profile()).is_ok());
    }

    #[test]
    fn test_reject_inverted_cpu_bounds() {
        let mut p = balanced::get_profile();
        p.cpu.min_percent = 90;
        p.cpu.max_percent = 20; // Invalid: max < min
        let res = ProfileValidator::validate(&p);
        assert!(res.is_err());
    }

    #[test]
    fn test_reject_dangerous_thermal_envelope() {
        let mut p = extreme::get_profile();
        p.thermal.critical_temperature = 40;
        p.thermal.allow_boost_temperature = 45; // Invalid: critical <= boost
        let res = ProfileValidator::validate(&p);
        assert!(res.is_err());
    }

    #[test]
    fn test_reject_exceeding_safety_envelope_60c() {
        let mut p = extreme::get_profile();
        p.thermal.critical_temperature = 65; // Invalid: exceeds 60°C safety cap
        let res = ProfileValidator::validate(&p);
        assert!(res.is_err());
    }

    #[test]
    fn test_reject_conflicting_scheduler_intents() {
        let mut p = powersave::get_profile();
        p.scheduler.prefer_latency = true;
        p.scheduler.prefer_power = true; // Invalid: conflict
        let res = ProfileValidator::validate(&p);
        assert!(res.is_err());
    }
}
