use crate::profile::policy::{CpuPolicy, GpuMode, GpuPolicy, Governor, SchedulerPolicy, ThermalPolicy};
use crate::profile::profile::Profile;

pub fn get_profile() -> Profile {
    Profile {
        name: "balanced",
        description: "Daily smooth responsiveness, fluid video playback & efficient multitasking",
        cpu: CpuPolicy {
            // schedutil / WALT dynamically scales cleanly from the hardware idle OPP (0-100%),
            // relying on kernel/perfmgr uclamp & transient touch boost instead of pinning high static idle floors.
            governor: Governor::Schedutil,
            min_percent: 0,
            max_percent: 100,
            boost: false,
        },
        gpu: GpuPolicy {
            mode: GpuMode::Balanced,
            boost: false,
        },
        thermal: ThermalPolicy {
            allow_boost_temperature: 44, // 44°C headroom allows sustained 60/90Hz video playback
            critical_temperature: 49,
        },
        scheduler: SchedulerPolicy {
            prefer_latency: false,
            prefer_power: false,
            // 10% mild top-app uclamp floor avoids 24/7 idle big-core energy waste while keeping SurfaceFlinger responsive.
            uclamp_min_percent: 10,
        },
    }
}
