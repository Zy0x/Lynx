use crate::profile::policy::{CpuPolicy, GpuMode, GpuPolicy, Governor, SchedulerPolicy, ThermalPolicy};
use crate::profile::profile::Profile;

pub fn get_profile() -> Profile {
    Profile {
        name: "powersave",
        description: "Maximum battery life, cool device temperature",
        cpu: CpuPolicy {
            governor: Governor::Powersave,
            min_percent: 0,
            max_percent: 60,
            boost: false,
        },
        gpu: GpuPolicy {
            mode: GpuMode::PowerEfficient,
            boost: false,
        },
        thermal: ThermalPolicy {
            allow_boost_temperature: 38,
            critical_temperature: 44,
        },
        scheduler: SchedulerPolicy {
            prefer_latency: false,
            prefer_power: true,
            uclamp_min_percent: 0,
        },
    }
}
