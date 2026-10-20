use crate::profile::policy::{CpuPolicy, GpuMode, GpuPolicy, Governor, SchedulerPolicy, ThermalPolicy};
use crate::profile::profile::Profile;

pub fn get_profile() -> Profile {
    Profile {
        name: "balanced",
        description: "Daily smooth responsiveness, fluid video playback & efficient multitasking",
        cpu: CpuPolicy {
            // schedutil / WALT dynamically scales within 0–100%.
            // Removing the 75% artificial ceiling ensures low-end and mid-end devices have full
            // burst capacity during video decoding (1080p60/4K) and app switching without frame drop.
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
            // 12% UI task floor guarantees SurfaceFlinger & video presentation queues
            // are dispatched without idle micro-stalls.
            uclamp_min_percent: 12,
        },
    }
}
