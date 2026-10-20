use crate::profile::policy::{CpuPolicy, GpuMode, GpuPolicy, Governor, SchedulerPolicy, ThermalPolicy};
use crate::profile::profile::Profile;

pub fn get_profile() -> Profile {
    Profile {
        name: "balanced",
        description: "Daily smooth responsiveness, fluid video playback & efficient multitasking",
        cpu: CpuPolicy {
            // schedutil / WALT dynamically scales within 25–100%.
            // 25% floor (~1100MHz Little / ~1400MHz Big) prevents low-end and mid-end devices (SDM660, Helio G96)
            // from dropping to sluggish 500-633MHz idle states during video playback (Twitter/TikTok) and feed scrolling.
            governor: Governor::Schedutil,
            min_percent: 25,
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
            // 25% UI & codec task floor guarantees SurfaceFlinger, MediaCodec & video presentation queues
            // are dispatched without idle micro-stalls.
            uclamp_min_percent: 25,
        },
    }
}
