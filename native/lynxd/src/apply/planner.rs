use std::fmt::Write;

use crate::hardware::capability::HardwareCapability;
use crate::hardware::detector::Vendor;
use crate::hardware::translator::{HardwareTranslator, PlannedOperation};
use crate::profile::profile::Profile;

pub struct ExecutionPlan {
    pub profile_name: &'static str,
    pub vendor: Vendor,
    pub capability: HardwareCapability,
    pub operations: Vec<PlannedOperation>,
}

impl ExecutionPlan {
    /// Builds an execution plan from the given profile and the device's probed capabilities.
    pub fn build(profile: &Profile) -> Self {
        let capability = HardwareCapability::resolve();
        let operations = HardwareTranslator::translate(profile, &capability);

        Self {
            profile_name: profile.name,
            vendor: capability.vendor,
            capability,
            operations,
        }
    }

    /// Formats the plan into a clean visual summary of planned changes.
    pub fn display_summary(&self) -> String {
        let mut out = String::new();
        let _ = writeln!(out, "==================================================");
        let _ = writeln!(out, "LYNX EXECUTION PLAN: [ {} ]", self.profile_name);
        let _ = writeln!(out, "==================================================");
        let _ = writeln!(out, "• Target SoC Platform : {}", self.vendor);
        let _ = writeln!(out, "• CPU Policies Probed : {} clusters ({} total cores)", self.capability.cpu.policies.len(), self.capability.cpu.total_cores);
        let _ = writeln!(out, "• GPU Driver Detected : {}", self.capability.gpu.driver.as_str());
        let _ = writeln!(out, "• UClamp Scheduler    : {}", if self.capability.scheduler.uclamp_min_node.is_some() { "Available" } else { "Not Available" });
        let _ = writeln!(out, "--------------------------------------------------");

        if self.operations.is_empty() {
            let _ = writeln!(out, "No kernel modifications required (Hardware is already matching profile targets).");
        } else {
            let _ = writeln!(out, "Planned Node Modifications ({} operations):", self.operations.len());
            for (idx, op) in self.operations.iter().enumerate() {
                let _ = writeln!(
                    out,
                    "  [{}] {:<9} : {}\n       Target Node: {}\n       Transition : '{}' ──> '{}'",
                    idx + 1,
                    op.subsystem,
                    op.description,
                    op.path.display(),
                    op.current_value,
                    op.target_value
                );
            }
        }
        let _ = write!(out, "==================================================");
        out
    }
}
