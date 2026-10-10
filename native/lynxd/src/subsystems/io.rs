use std::fmt::Write as FmtWrite;
use std::path::Path;

use crate::hardware::capability::{BlockDeviceType, HardwareCapability};
use crate::sysfs::{SysfsWriter, WriteMode};

pub struct IoController;

impl IoController {
    fn write_opt(path: impl AsRef<Path>, val: impl AsRef<str>) {
        let p = path.as_ref();
        if p.exists() {
            let _ = SysfsWriter::write(p, val, WriteMode::ForcePermission);
        }
    }

    pub fn info_json() -> String {
        let storage = HardwareCapability::resolve_storage();
        let mut out = String::from("{\"devices\":[");
        for (idx, dev) in storage.devices.iter().enumerate() {
            if idx > 0 {
                out.push(',');
            }
            let scheds: Vec<String> = dev.available_schedulers.iter().map(|s| format!("\"{}\"", s)).collect();
            let _ = write!(
                out,
                "{{\"name\":\"{}\",\"type\":\"{}\",\"current_scheduler\":\"{}\",\"available_schedulers\":[{}]}}",
                dev.name,
                dev.device_type.as_str(),
                dev.current_scheduler,
                scheds.join(",")
            );
        }
        out.push_str("]}");
        out
    }

    pub fn apply_default() -> String {
        let storage = HardwareCapability::resolve_storage();
        let mut count = 0usize;

        for dev in &storage.devices {
            let q = &dev.queue_path;
            Self::write_opt(q.join("add_random"), "0");
            Self::write_opt(q.join("iostats"), "0");
            Self::write_opt(q.join("rq_affinity"), "1");
            Self::write_opt(q.join("nr_requests"), "128");

            match dev.device_type {
                BlockDeviceType::Ufs | BlockDeviceType::Nvme => {
                    for cand in &["none", "mq-deadline", "bfq", "kyber", "noop"] {
                        if dev.available_schedulers.iter().any(|s| s == cand) {
                            Self::write_opt(q.join("scheduler"), cand);
                            break;
                        }
                    }
                    Self::write_opt(q.join("read_ahead_kb"), "128");
                    count += 1;
                }
                BlockDeviceType::Emmc => {
                    for cand in &["mq-deadline", "bfq", "kyber", "noop", "none"] {
                        if dev.available_schedulers.iter().any(|s| s == cand) {
                            Self::write_opt(q.join("scheduler"), cand);
                            break;
                        }
                    }
                    Self::write_opt(q.join("read_ahead_kb"), "512");
                    Self::write_opt(q.join("iosched/slice_idle"), "0");
                    Self::write_opt(q.join("iosched/slice_idle_us"), "0");
                    Self::write_opt(q.join("iosched/group_idle"), "0");
                    Self::write_opt(q.join("iosched/group_idle_us"), "0");
                    Self::write_opt(q.join("iosched/low_latency"), "1");
                    count += 1;
                }
                BlockDeviceType::Loop => {
                    if dev.available_schedulers.iter().any(|s| s == "none") {
                        Self::write_opt(q.join("scheduler"), "none");
                    }
                    Self::write_opt(q.join("read_ahead_kb"), "128");
                }
                BlockDeviceType::Virtual => {
                    Self::write_opt(q.join("rotational"), "0");
                    Self::write_opt(q.join("write_cache"), "write back");
                }
            }
        }

        format!("Universal Block I/O optimizations applied across {} primary storage queues.", count)
    }

    pub fn set_scheduler(block_prefix_or_name: &str, scheduler: &str) -> String {
        let storage = HardwareCapability::resolve_storage();
        let mut updated = 0usize;
        for dev in &storage.devices {
            if dev.name == block_prefix_or_name || dev.name.starts_with(block_prefix_or_name) {
                if dev.available_schedulers.is_empty() || dev.available_schedulers.iter().any(|s| s == scheduler) {
                    Self::write_opt(dev.queue_path.join("scheduler"), scheduler);
                    updated += 1;
                }
            }
        }
        format!("Updated scheduler to '{}' on {} block device(s).", scheduler, updated)
    }
}
