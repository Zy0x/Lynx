use std::fmt::Write;

use crate::profile::policy::{CpuPolicy, GpuPolicy, SchedulerPolicy, ThermalPolicy};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Profile {
    pub name: &'static str,
    pub description: &'static str,
    pub cpu: CpuPolicy,
    pub gpu: GpuPolicy,
    pub thermal: ThermalPolicy,
    pub scheduler: SchedulerPolicy,
}

impl Profile {
    pub fn display(&self) -> String {
        let mut out = String::new();
        let _ = writeln!(out, "==================================================");
        let _ = writeln!(out, "Profile: {} ({})", self.name, self.description);
        let _ = writeln!(out, "==================================================");
        let _ = writeln!(out, "• CPU Policy:");
        let _ = writeln!(out, "  Governor   : {}", self.cpu.governor.as_str());
        let _ = writeln!(out, "  Min Target : {}%", self.cpu.min_percent);
        let _ = writeln!(out, "  Max Target : {}%", self.cpu.max_percent);
        let _ = writeln!(out, "  Input Boost: {}", if self.cpu.boost { "Enabled" } else { "Disabled" });

        let _ = writeln!(out, "• GPU Policy:");
        let _ = writeln!(out, "  Mode       : {}", self.gpu.mode.as_str());
        let _ = writeln!(out, "  Turbo Boost: {}", if self.gpu.boost { "Enabled" } else { "Disabled" });

        let _ = writeln!(out, "• Thermal Policy:");
        let _ = writeln!(out, "  Boost Threshold   : {}°C", self.thermal.allow_boost_temperature);
        let _ = writeln!(out, "  Critical Emergency: {}°C", self.thermal.critical_temperature);

        let _ = writeln!(out, "• Scheduler Policy:");
        let _ = writeln!(out, "  Prefer Latency    : {}", self.scheduler.prefer_latency);
        let _ = writeln!(out, "  Prefer Power Save : {}", self.scheduler.prefer_power);
        let _ = writeln!(out, "  UClamp Floor      : {}%", self.scheduler.uclamp_min_percent);
        let _ = write!(out, "==================================================");
        out
    }
}
