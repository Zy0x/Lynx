use crate::profile::policy::{CpuPolicy, GpuMode, GpuPolicy, Governor, SchedulerPolicy, ThermalPolicy};
use crate::profile::profile::Profile;

pub fn get_profile() -> Profile {
    Profile {
        name: "balanced",
        description: "Daily smooth responsiveness with optimal battery efficiency",
        cpu: CpuPolicy {
            governor: Governor::Schedutil,
            min_percent: 20,
            max_percent: 100,
            boost: false,
        },
        gpu: GpuPolicy {
            mode: GpuMode::Balanced,
            boost: false,
        },
        thermal: ThermalPolicy {
            allow_boost_temperature: 42,
            critical_temperature: 48,
        },
        scheduler: SchedulerPolicy {
            prefer_latency: false,
            prefer_power: false,
            uclamp_min_percent: 20,
        },
    }
}
