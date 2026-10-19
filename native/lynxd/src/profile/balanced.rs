use crate::profile::policy::{CpuPolicy, GpuMode, GpuPolicy, Governor, SchedulerPolicy, ThermalPolicy};
use crate::profile::profile::Profile;

pub fn get_profile() -> Profile {
    Profile {
        name: "balanced",
        description: "Daily smooth responsiveness with optimal battery efficiency",
        cpu: CpuPolicy {
            // schedutil / WALT dynamically scales within the 0–75% ceiling.
            // The 75% hardware cap (written to scaling_max_freq) prevents runaway burst
            // heat during sustained multitasking while keeping burst headroom for UI frames.
            governor: Governor::Schedutil,
            min_percent: 0,
            max_percent: 75,
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
            // Slight floor so UI tasks get dispatched promptly without idle stall.
            uclamp_min_percent: 10,
        },
    }
}
