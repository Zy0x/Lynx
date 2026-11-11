use crate::profile::policy::{CpuPolicy, GpuMode, GpuPolicy, Governor, SchedulerPolicy, ThermalPolicy};
use crate::profile::profile::Profile;

pub fn get_profile() -> Profile {
    Profile {
        name: "performance",
        description: "High responsiveness for heavy apps and gaming with thermal limits enforced",
        cpu: CpuPolicy {
            // WALT / schedutil scales immediately (up_rate_limit_us = 0); HardwareTranslator + DeviceTier
            // scale dynamic floors per hardware tier (leaving Little cores unconstrained for FBE/UI).
            governor: Governor::Schedutil,
            min_percent: 35,
            max_percent: 100,
            boost: true,
        },
        gpu: GpuPolicy {
            mode: GpuMode::High,
            boost: true,
        },
        thermal: ThermalPolicy {
            allow_boost_temperature: 50,
            critical_temperature: 55,
        },
        scheduler: SchedulerPolicy {
            prefer_latency: true,
            prefer_power: false,
            uclamp_min_percent: 50,
        },
    }
}
