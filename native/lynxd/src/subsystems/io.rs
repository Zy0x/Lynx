use std::fmt::Write as FmtWrite;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

use crate::hardware::capability::{BlockDeviceType, HardwareCapability};
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

#[derive(Debug, Clone, Default)]
pub struct MountXInfo {
    pub is_detected: bool,
    pub mount_point: String,
    pub partition_name: String,
    pub parent_device: String,
    pub fs_type: String,
    pub f2fs_sysfs_exists: bool,
    pub mmcqd_pids: Vec<i32>,
    pub current_read_ahead_kb: u32,
    pub current_scheduler: String,
    pub dirty_background_bytes: u64,
    pub dirty_bytes: u64,
    pub is_optimized: bool,
}

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

    /// Detects active MountX installation, /data/sdext2 partition, or external MicroSD block devices.
    pub fn detect_mountx() -> MountXInfo {
        let mut info = MountXInfo::default();

        // 1. Scan /proc/mounts for /data/sdext2 or external SD partitions
        if let Ok(mounts) = fs::read_to_string("/proc/mounts") {
            for line in mounts.lines() {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if parts.len() >= 3 {
                    let dev_node = parts[0];
                    let mount_point = parts[1];
                    let fs_type = parts[2];

                    if mount_point == "/data/sdext2" || mount_point.starts_with("/data/sdext") {
                        info.is_detected = true;
                        info.mount_point = mount_point.to_string();
                        info.fs_type = fs_type.to_string();

                        let part_name = Path::new(dev_node)
                            .file_name()
                            .map(|s| s.to_string_lossy().to_string())
                            .unwrap_or_else(|| "mmcblk0p3".to_string());

                        let parent_dev = if let Some(pos) = part_name.find('p') {
                            part_name[..pos].to_string()
                        } else {
                            part_name.clone()
                        };

                        info.partition_name = part_name;
                        info.parent_device = parent_dev;
                        break;
                    }
                }
            }
        }

        // Fallback: If not mounted at /data/sdext2, check if mmcblk0 or mmcblk1 exists
        if !info.is_detected {
            for cand in &["mmcblk0", "mmcblk1"] {
                let sys_block = PathBuf::from(format!("/sys/block/{}", cand));
                if sys_block.is_dir() {
                    info.is_detected = true;
                    info.mount_point = "external_storage".to_string();
                    info.parent_device = (*cand).to_string();
                    info.partition_name = format!("{}p1", cand);
                    info.fs_type = "unknown".to_string();
                    break;
                }
            }
        }

        if info.is_detected {
            // Check F2FS sysfs directory
            let f2fs_dir = PathBuf::from(format!("/sys/fs/f2fs/{}", info.partition_name));
            info.f2fs_sysfs_exists = f2fs_dir.is_dir();

            // Read queue tunables
            let q_dir = PathBuf::from(format!("/sys/block/{}/queue", info.parent_device));
            info.current_read_ahead_kb = SysfsReader::read_int::<u32>(q_dir.join("read_ahead_kb")).unwrap_or(0);

            if let Ok(raw_sched) = SysfsReader::read_trimmed(q_dir.join("scheduler")) {
                let current = raw_sched
                    .split_whitespace()
                    .find(|s| s.starts_with('[') && s.ends_with(']'))
                    .map(|s| s.trim_matches(|c| c == '[' || c == ']').to_string())
                    .unwrap_or(raw_sched);
                info.current_scheduler = current;
            }

            // Read VM writeback parameters
            info.dirty_background_bytes = SysfsReader::read_int::<u64>("/proc/sys/vm/dirty_background_bytes").unwrap_or(0);
            info.dirty_bytes = SysfsReader::read_int::<u64>("/proc/sys/vm/dirty_bytes").unwrap_or(0);

            // Find mmcqd kernel threads
            info.mmcqd_pids = Self::find_mmcqd_pids();

            // Evaluated as optimized if read_ahead >= 1024 and dirty_background_bytes > 0
            info.is_optimized = info.current_read_ahead_kb >= 1024 && info.dirty_background_bytes > 0;
        }

        info
    }

    /// Finds all mmcqd kernel I/O threads in /proc.
    pub fn find_mmcqd_pids() -> Vec<i32> {
        let mut pids = Vec::new();
        let proc_path = Path::new("/proc");
        if let Ok(entries) = fs::read_dir(proc_path) {
            for entry in entries.flatten() {
                let fname = entry.file_name();
                let s = fname.to_string_lossy();
                if s.chars().all(|c| c.is_ascii_digit()) {
                    if let Ok(pid) = s.parse::<i32>() {
                        let comm_file = entry.path().join("comm");
                        if let Ok(comm) = fs::read_to_string(comm_file) {
                            if comm.trim().starts_with("mmcqd") {
                                pids.push(pid);
                            }
                        }
                    }
                }
            }
        }
        pids.sort_unstable();
        pids
    }

    /// Serializes MountX and MicroSD storage information to JSON.
    pub fn mountx_info_json() -> String {
        let info = Self::detect_mountx();
        let pids_str: Vec<String> = info.mmcqd_pids.iter().map(|p| p.to_string()).collect();

        let mut out = String::new();
        let _ = write!(
            out,
            "{{\"is_detected\":{},\"mount_point\":\"{}\",\"partition_name\":\"{}\",\"parent_device\":\"{}\",\"fs_type\":\"{}\",\"f2fs_sysfs_exists\":{},\"mmcqd_pids\":[{}],\"current_read_ahead_kb\":{},\"current_scheduler\":\"{}\",\"dirty_background_bytes\":{},\"dirty_bytes\":{},\"is_optimized\":{}}}",
            info.is_detected,
            info.mount_point,
            info.partition_name,
            info.parent_device,
            info.fs_type,
            info.f2fs_sysfs_exists,
            pids_str.join(","),
            info.current_read_ahead_kb,
            info.current_scheduler,
            info.dirty_background_bytes,
            info.dirty_bytes,
            info.is_optimized
        );
        out
    }

    /// Applies or restores high-speed Flash & Game Asset Streaming optimizations for MicroSD/MountX.
    pub fn apply_flash_streaming_boost(boost: bool) -> String {
        let info = Self::detect_mountx();
        let target_dev = if !info.parent_device.is_empty() {
            info.parent_device.clone()
        } else {
            "mmcblk0".to_string()
        };

        let queue_dir = PathBuf::from(format!("/sys/block/{}/queue", target_dev));

        if boost {
            // ── 1. Storage Block Layer Flash Prefetching ───────────────
            if queue_dir.is_dir() {
                // Pick best scheduler for flash
                let sched_file = queue_dir.join("scheduler");
                if let Ok(avail) = SysfsReader::read_trimmed(&sched_file) {
                    for cand in &["deadline", "mq-deadline", "none", "noop"] {
                        if avail.contains(cand) {
                            Self::write_opt(&sched_file, cand);
                            break;
                        }
                    }
                }
                Self::write_opt(queue_dir.join("read_ahead_kb"), "2048");
                Self::write_opt(queue_dir.join("nr_requests"), "512");
                Self::write_opt(queue_dir.join("rq_affinity"), "2");
                Self::write_opt(queue_dir.join("nomerges"), "0");
                Self::write_opt(queue_dir.join("iostats"), "0");
                Self::write_opt(queue_dir.join("add_random"), "0");
                Self::write_opt(queue_dir.join("rotational"), "0");
                Self::write_opt(queue_dir.join("iosched/fifo_batch"), "64");
            }

            // ── 2. F2FS Filesystem Gaming Shield ───────────────────────
            if info.mount_point == "/data/sdext2" || info.mount_point.starts_with("/data/sdext") {
                let _ = Command::new("mount")
                    .args(&[
                        "-o",
                        "remount,nodiscard,background_gc=off,fsync_mode=nobarrier,lazytime",
                        &info.mount_point,
                    ])
                    .output();
            }

            if !info.partition_name.is_empty() {
                let f2fs_dir = PathBuf::from(format!("/sys/fs/f2fs/{}", info.partition_name));
                if f2fs_dir.is_dir() {
                    Self::write_opt(f2fs_dir.join("gc_urgent"), "0");
                    Self::write_opt(f2fs_dir.join("cp_interval"), "300");
                    Self::write_opt(f2fs_dir.join("ipu_policy"), "7");
                    Self::write_opt(f2fs_dir.join("min_fsync_blocks"), "64");
                }
            }

            // ── 3. VM Writeback Pacing (Prevent MicroSD Stalls/Freezes) ─
            Self::write_opt("/proc/sys/vm/dirty_background_bytes", "67108864"); // 64 MB
            Self::write_opt("/proc/sys/vm/dirty_bytes", "134217728");           // 128 MB
            Self::write_opt("/proc/sys/vm/dirty_writeback_centisecs", "100");   // 1s
            Self::write_opt("/proc/sys/vm/dirty_expire_centisecs", "300");      // 3s

            // ── 4. Driver Thread Realtime Priority (mmcqd) ─────────────
            let pids = Self::find_mmcqd_pids();
            for &pid in &pids {
                let _ = Command::new("renice")
                    .args(&["-n", "-20", "-p", &pid.to_string()])
                    .output();
            }

            // ── 5. Schedtune / EAS Top-App Priority ────────────────────
            let stune_top = Path::new("/dev/stune/top-app");
            if stune_top.is_dir() {
                Self::write_opt(stune_top.join("schedtune.boost"), "50");
                Self::write_opt(stune_top.join("schedtune.util.min"), "30");
            }

            // ── 6. TCP Low-Latency Network Stack ───────────────────────
            Self::write_opt("/proc/sys/net/ipv4/tcp_low_latency", "1");
            Self::write_opt("/proc/sys/net/ipv4/tcp_adv_win_scale", "2");
            Self::write_opt("/proc/sys/net/core/rmem_max", "8388608");

            format!(
                "MountX & MicroSD Flash Asset Streaming Boost ACTIVATED (Target: {}, ReadAhead: 2048KB, F2FS Gaming Shield: Active, MMCQD PIDs: {:?})",
                target_dev, pids
            )
        } else {
            // Restore Balanced / Standard Storage Envelope
            if queue_dir.is_dir() {
                Self::write_opt(queue_dir.join("read_ahead_kb"), "512");
                Self::write_opt(queue_dir.join("nr_requests"), "128");
                Self::write_opt(queue_dir.join("rq_affinity"), "1");
            }

            if info.mount_point == "/data/sdext2" || info.mount_point.starts_with("/data/sdext") {
                let _ = Command::new("mount")
                    .args(&["-o", "remount,background_gc=on", &info.mount_point])
                    .output();
            }

            if !info.partition_name.is_empty() {
                let f2fs_dir = PathBuf::from(format!("/sys/fs/f2fs/{}", info.partition_name));
                if f2fs_dir.is_dir() {
                    Self::write_opt(f2fs_dir.join("cp_interval"), "60");
                }
            }

            // Revert VM to percentage-based ratios
            Self::write_opt("/proc/sys/vm/dirty_background_bytes", "0");
            Self::write_opt("/proc/sys/vm/dirty_bytes", "0");
            Self::write_opt("/proc/sys/vm/dirty_writeback_centisecs", "500");
            Self::write_opt("/proc/sys/vm/dirty_expire_centisecs", "3000");

            let pids = Self::find_mmcqd_pids();
            for &pid in &pids {
                let _ = Command::new("renice")
                    .args(&["-n", "0", "-p", &pid.to_string()])
                    .output();
            }

            let stune_top = Path::new("/dev/stune/top-app");
            if stune_top.is_dir() {
                Self::write_opt(stune_top.join("schedtune.boost"), "0");
                Self::write_opt(stune_top.join("schedtune.util.min"), "0");
            }

            format!("MountX & MicroSD Flash Storage reverted to Balanced mode (Target: {}).", target_dev)
        }
    }
}
