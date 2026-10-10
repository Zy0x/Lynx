use crate::profile::policy::{CpuPolicy, GpuMode, GpuPolicy, Governor, SchedulerPolicy, ThermalPolicy};
use crate::profile::profile::Profile;

pub fn get_profile() -> Profile {
    Profile {
        name: "performance",
        description: "High responsiveness for esports gaming and fluid rendering",
        cpu: CpuPolicy {
            governor: Governor::Performance,
            min_percent: 60,
            max_percent: 100,
            boost: true,
        },
        gpu: GpuPolicy {
            mode: GpuMode::High,
            boost: true,
        },
        thermal: ThermalPolicy {
            allow_boost_temperature: 45,
            critical_temperature: 50,
        },
        scheduler: SchedulerPolicy {
            prefer_latency: true,
            prefer_power: false,
            uclamp_min_percent: 60,
        },
    }
}
