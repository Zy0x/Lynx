use crate::profile::policy::{CpuPolicy, GpuMode, GpuPolicy, Governor, SchedulerPolicy, ThermalPolicy};
use crate::profile::profile::Profile;

pub fn get_profile() -> Profile {
    Profile {
        name: "extreme",
        description: "Hardcore gaming & benchmarks with highest performance limits",
        cpu: CpuPolicy {
            governor: Governor::Performance,
            min_percent: 85,
            max_percent: 100,
            boost: true,
        },
        gpu: GpuPolicy {
            mode: GpuMode::Maximum,
            boost: true,
        },
        thermal: ThermalPolicy {
            allow_boost_temperature: 48,
            critical_temperature: 55,
        },
        scheduler: SchedulerPolicy {
            prefer_latency: true,
            prefer_power: false,
            uclamp_min_percent: 100,
        },
    }
}
