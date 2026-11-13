use std::collections::HashMap;
use std::fs;
use std::path::Path;
use crate::subsystems::IrqTopology;
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

#[derive(Debug, Clone)]
pub struct DetectedIrq {
    pub irq_num: u32,
    pub name: String,
    pub current_affinity: String,
    pub target_core: u32,
}

pub struct IrqController;

impl IrqController {
    /// Parses /proc/interrupts to map device categories to their IRQ numbers.
    pub fn scan_interrupts() -> HashMap<String, u32> {
        let mut map = HashMap::new();
        let content = match fs::read_to_string("/proc/interrupts") {
            Ok(c) => c,
            Err(_) => return map,
        };

        let controller_tokens = IrqTopology::discover_touch_controller_tokens();
        for (irq_num, _) in IrqTopology::find_touch_irqs_in_proc(&content, &controller_tokens) {
            map.entry("touchscreen".into()).or_insert(irq_num);
        }

        for line in content.lines() {
            let parts: Vec<&str> = line.split_whitespace().collect();
            if parts.is_empty() {
                continue;
            }
            let irq_str = parts[0].trim_end_matches(':');
            let irq_num: u32 = match irq_str.parse() {
                Ok(n) => n,
                Err(_) => continue,
            };

            let line_lower = line.to_lowercase();
            if line_lower.contains("wlan") || line_lower.contains("wcnss") {
                map.insert("wifi".into(), irq_num);
            } else if line_lower.contains("kgsl") || line_lower.contains("mali") {
                map.insert("gpu".into(), irq_num);
            } else if line_lower.contains("disp") || line_lower.contains("drm") || line_lower.contains("mdss") {
                map.insert("display".into(), irq_num);
            }
        }
        map
    }

    /// Steers hardware IRQs using dynamically discovered CPU cluster topology.
    pub fn balance_for_gaming() -> Result<Vec<DetectedIrq>, String> {
        let irqs = Self::scan_interrupts();
        let clusters = IrqTopology::probe_clusters();
        let mut results = Vec::new();

        // 1. Touchscreen -> Dynamic non-CPU0 LITTLE offload core
        if let Some(&irq) = irqs.get("touchscreen") {
            let path = format!("/proc/irq/{}/smp_affinity_list", irq);
            let cur = SysfsReader::read_trimmed(&path).unwrap_or_default();
            let core = clusters.little_offload_core(0);
            let _ = SysfsWriter::write(&path, &core.to_string(), WriteMode::ForcePermission);
            results.push(DetectedIrq {
                irq_num: irq,
                name: "touchscreen".into(),
                current_affinity: cur,
                target_core: core,
            });
        }

        // 2. Wi-Fi -> Secondary non-CPU0 LITTLE offload core
        if let Some(&irq) = irqs.get("wifi") {
            let path = format!("/proc/irq/{}/smp_affinity_list", irq);
            let cur = SysfsReader::read_trimmed(&path).unwrap_or_default();
            let core = clusters.little_offload_core(1);
            let _ = SysfsWriter::write(&path, &core.to_string(), WriteMode::ForcePermission);
            results.push(DetectedIrq {
                irq_num: irq,
                name: "wifi".into(),
                current_affinity: cur,
                target_core: core,
            });
        }

        // 3. GPU -> Dynamically discovered Big/Performance cluster
        if let Some(&irq) = irqs.get("gpu") {
            let path = format!("/proc/irq/{}/smp_affinity_list", irq);
            let cur = SysfsReader::read_trimmed(&path).unwrap_or_default();
            let _ = SysfsWriter::write(&path, &clusters.big_cpu_range, WriteMode::ForcePermission);
            results.push(DetectedIrq {
                irq_num: irq,
                name: "gpu".into(),
                current_affinity: cur,
                target_core: clusters.big_cpus.first().copied().unwrap_or(0),
            });
        }

        Ok(results)
    }

    /// Restores all managed IRQs back to all online CPUs dynamically.
    pub fn restore_default() -> Result<(), String> {
        let irqs = Self::scan_interrupts();
        let clusters = IrqTopology::probe_clusters();
        for (_, &irq) in irqs.iter() {
            let path = format!("/proc/irq/{}/smp_affinity_list", irq);
            if Path::new(&path).exists() {
                let _ = SysfsWriter::write(&path, &clusters.all_cpu_range, WriteMode::ForcePermission);
            }
        }
        Ok(())
    }
}
