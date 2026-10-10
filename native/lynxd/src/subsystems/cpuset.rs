use std::fs;
use std::path::Path;

use crate::hardware::capability::HardwareCapability;
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

pub struct CpusetController;

const CPUMASK_GROUPS: &[&str] = &[
    "top-app",
    "foreground",
    "background",
    "system-background",
    "restricted",
    "audio-app",
    "camera-daemon",
    "dex2oat",
];

impl CpusetController {
    fn write_opt(path: impl AsRef<Path>, val: impl AsRef<str>) -> bool {
        let p = path.as_ref();
        if p.exists() {
            SysfsWriter::write(p, val, WriteMode::ForcePermission).is_ok()
        } else {
            false
        }
    }

    fn read_trimmed(path: impl AsRef<Path>) -> Option<String> {
        SysfsReader::read_trimmed(path.as_ref()).ok().filter(|s| !s.is_empty())
    }

    fn write_cpuset_group(group: &str, val: &str) -> bool {
        let mut ok = false;
        for root in &["/dev/cpuset", "/sys/fs/cgroup/cpuset"] {
            for leaf in &["cpus", "cpuset.cpus"] {
                let target = format!("{}/{}/{}", root, group, leaf);
                if Path::new(&target).exists() {
                    if Self::write_opt(&target, val) {
                        ok = true;
                    }
                }
            }
        }
        ok
    }

    /// Emits JSON inspection of current cpuset groups and their core assignments.
    pub fn info_json() -> String {
        let mut entries = Vec::new();

        for &grp in CPUMASK_GROUPS {
            let mut cpus_val = None;
            let mut procs_count = 0usize;

            for root in &["/dev/cpuset", "/sys/fs/cgroup/cpuset"] {
                for leaf in &["cpus", "cpuset.cpus"] {
                    let p = format!("{}/{}/{}", root, grp, leaf);
                    if let Some(v) = Self::read_trimmed(&p) {
                        cpus_val = Some(v);
                        break;
                    }
                }

                let proc_path = format!("{}/{}/cgroup.procs", root, grp);
                if let Ok(content) = fs::read_to_string(&proc_path) {
                    procs_count = content.lines().filter(|l| !l.trim().is_empty()).count();
                } else {
                    let task_path = format!("{}/{}/tasks", root, grp);
                    if let Ok(content) = fs::read_to_string(&task_path) {
                        procs_count = content.lines().filter(|l| !l.trim().is_empty()).count();
                    }
                }

                if cpus_val.is_some() {
                    break;
                }
            }

            let cpus_str = cpus_val.unwrap_or_else(|| "unsupported".to_string());
            entries.push(format!(
                "{{\"group\":\"{}\",\"cpus\":\"{}\",\"procs\":{}}}",
                grp, cpus_str, procs_count
            ));
        }

        format!("{{\"cpusets\":[{}]}}", entries.join(","))
    }

    /// Applies cpuset layout preset dynamically derived from hardware CPU topology.
    pub fn apply_preset(preset: &str) -> String {
        let cap = HardwareCapability::resolve();
        let total_cores = cap.cpu.total_cores.max(1);
        let max_core = total_cores - 1;

        let mut little_max = if total_cores >= 8 { 3 } else { (total_cores / 2).max(1) - 1 };
        for p in &cap.cpu.policies {
            if p.role.to_lowercase().contains("little") || p.role.to_lowercase().contains("silver") {
                for part in p.related_cpus.split_whitespace() {
                    if let Ok(id) = part.parse::<usize>() {
                        little_max = little_max.max(id);
                    } else if let Some(dash) = part.find('-') {
                        if let Ok(id) = part[dash + 1..].parse::<usize>() {
                            little_max = little_max.max(id);
                        }
                    }
                }
            }
        }
        let bg_max = if total_cores >= 8 { 2 } else { 1 };

        let p = preset.trim().to_lowercase();
        match p.as_str() {
            "gaming" => {
                Self::write_cpuset_group("top-app", &format!("0-{}", max_core));
                Self::write_cpuset_group("foreground", &format!("0-{}", little_max));
                Self::write_cpuset_group("background", &format!("0-{}", bg_max));
                Self::write_cpuset_group("system-background", &format!("0-{}", bg_max));
                Self::write_cpuset_group("restricted", "0-1");
                format!("Cpuset Gaming preset applied (top-app: 0-{}, foreground: 0-{}, bg: 0-{}).", max_core, little_max, bg_max)
            }

            "battery" | "powersave" => {
                Self::write_cpuset_group("top-app", &format!("0-{}", little_max));
                Self::write_cpuset_group("foreground", &format!("0-{}", little_max));
                Self::write_cpuset_group("background", "0-1");
                Self::write_cpuset_group("system-background", "0-1");
                Self::write_cpuset_group("restricted", "0-1");
                format!("Cpuset Battery preset applied (top-app restricted to little cores 0-{}).", little_max)
            }

            "balanced" | "default" => {
                Self::write_cpuset_group("top-app", &format!("0-{}", max_core));
                Self::write_cpuset_group("foreground", &format!("0-{}", max_core));
                Self::write_cpuset_group("background", &format!("0-{}", little_max));
                Self::write_cpuset_group("system-background", &format!("0-{}", little_max));
                Self::write_cpuset_group("restricted", "0-1");
                format!("Cpuset Balanced preset applied (top-app/fg: 0-{}, bg: 0-{}).", max_core, little_max)
            }

            "background-isolation" => {
                Self::write_cpuset_group("background", "0");
                Self::write_cpuset_group("system-background", "0");
                Self::write_cpuset_group("restricted", "0");
                "Cpuset Background Isolation preset applied (background clamped to cpu0 only).".to_string()
            }

            unknown => format!("Error: Unknown cpuset preset '{}'. Valid presets: gaming, battery, balanced, background-isolation.", unknown),
        }
    }

    /// Sets explicit CPU core mask for a specific cpuset group.
    pub fn set_group(group: &str, cores: &str) -> String {
        if Self::write_cpuset_group(group, cores) {
            format!("Cpuset group '{}' updated to '{}'.", group, cores)
        } else {
            format!("Error: Failed to write cpuset group '{}' (Node not found or locked).", group)
        }
    }
}
