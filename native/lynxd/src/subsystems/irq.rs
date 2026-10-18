use std::collections::HashMap;
use std::fs;
use std::path::Path;
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
    /// Parses /proc/interrupts to map device names to their IRQ numbers.
    pub fn scan_interrupts() -> HashMap<String, u32> {
        let mut map = HashMap::new();
        let content = match fs::read_to_string("/proc/interrupts") {
            Ok(c) => c,
            Err(_) => return map,
        };

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
            if line_lower.contains("touch") || line_lower.contains("nvt") || line_lower.contains("goodix") || line_lower.contains("fts") {
                map.insert("touchscreen".into(), irq_num);
            } else if line_lower.contains("wlan") || line_lower.contains("wcnss") {
                map.insert("wifi".into(), irq_num);
            } else if line_lower.contains("kgsl") || line_lower.contains("mali") {
                map.insert("gpu".into(), irq_num);
            } else if line_lower.contains("disp") || line_lower.contains("drm") || line_lower.contains("mdss") {
                map.insert("display".into(), irq_num);
            }
        }
        map
    }

    /// Steers hardware IRQs to dedicated CPU cores to eliminate CPU0 bottleneck during gaming.
    pub fn balance_for_gaming() -> Result<Vec<DetectedIrq>, String> {
        let irqs = Self::scan_interrupts();
        let mut results = Vec::new();

        // 1. Touchscreen -> Core 1 (Little core with minimal background noise)
        if let Some(&irq) = irqs.get("touchscreen") {
            let path = format!("/proc/irq/{}/smp_affinity_list", irq);
            let cur = SysfsReader::read_trimmed(&path).unwrap_or_default();
            let _ = SysfsWriter::write(&path, "1", WriteMode::ForcePermission);
            results.push(DetectedIrq {
                irq_num: irq,
                name: "touchscreen".into(),
                current_affinity: cur,
                target_core: 1,
            });
        }

        // 2. Wi-Fi -> Core 2
        if let Some(&irq) = irqs.get("wifi") {
            let path = format!("/proc/irq/{}/smp_affinity_list", irq);
            let cur = SysfsReader::read_trimmed(&path).unwrap_or_default();
            let _ = SysfsWriter::write(&path, "2", WriteMode::ForcePermission);
            results.push(DetectedIrq {
                irq_num: irq,
                name: "wifi".into(),
                current_affinity: cur,
                target_core: 2,
            });
        }

        // 3. GPU -> Big Cluster (e.g. Cores 4-7 on 8-core Snapdragon/MTK)
        if let Some(&irq) = irqs.get("gpu") {
            let path = format!("/proc/irq/{}/smp_affinity_list", irq);
            let cur = SysfsReader::read_trimmed(&path).unwrap_or_default();
            let _ = SysfsWriter::write(&path, "4-7", WriteMode::ForcePermission);
            results.push(DetectedIrq {
                irq_num: irq,
                name: "gpu".into(),
                current_affinity: cur,
                target_core: 4,
            });
        }

        Ok(results)
    }

    /// Restores all managed IRQs back to universal distribution (0-7).
    pub fn restore_default() -> Result<(), String> {
        let irqs = Self::scan_interrupts();
        for (_, &irq) in irqs.iter() {
            let path = format!("/proc/irq/{}/smp_affinity_list", irq);
            if Path::new(&path).exists() {
                let _ = SysfsWriter::write(&path, "0-7", WriteMode::ForcePermission);
            }
        }
        Ok(())
    }
}
