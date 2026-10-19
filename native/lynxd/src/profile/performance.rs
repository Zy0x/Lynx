use crate::profile::policy::{CpuPolicy, GpuMode, GpuPolicy, Governor, SchedulerPolicy, ThermalPolicy};
use crate::profile::profile::Profile;

pub fn get_profile() -> Profile {
    Profile {
        name: "performance",
        description: "High responsiveness for heavy apps and gaming with thermal limits enforced",
        cpu: CpuPolicy {
            // WALT / schedutil scales aggressively (up_rate_limit_us = 0) but
            // still yields during thermal throttle. The translator's Anti-Heat Shield
            // further caps Little cores to 80% Fmax preventing thermal runaway.
            // 'Performance' governor (lock to max) is reserved for Extreme-only.
            governor: Governor::Schedutil,
            min_percent: 35,    // Big cluster floor ~35% OPP (scheduler scales from here)
            max_percent: 100,   // Full Fmax ceiling on Big/Prime; Anti-Heat caps Little to 80%
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
            uclamp_min_percent: 50,  // Tasks get at least 50% utilization headroom
        },
    }
}
