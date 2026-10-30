use std::fmt::Write as FmtWrite;
use std::fs;
use std::path::Path;

use crate::apply::lock::ModeLock;
use crate::hardware::capability::{BlockDeviceType, HardwareCapability};
use crate::hardware::storage::StoragePolicy;
use crate::state::storage_state::StorageState;
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
    pub target_read_ahead_kb: Option<u32>,
    pub is_primary_emmc: bool,
    pub primary_storage_type: String,
    pub emmc_name: String,
    pub emmc_life_time: String,
}

fn json_string(value: &str) -> String {
    let mut out = String::from("\"");
    for ch in value.chars() {
        match ch {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            ch if ch.is_control() => {
                let _ = write!(out, "\\u{:04x}", ch as u32);
            }
            ch => out.push(ch),
        }
    }
    out.push('"');
    out
}

pub struct IoController;

impl IoController {
    pub fn info_json() -> String {
        let storage = HardwareCapability::resolve_storage();
        let primary_info = StoragePolicy::primary_storage(&storage);
        let mut out = String::from("{\"devices\":[");
        for (idx, dev) in storage.devices.iter().enumerate() {
            if idx > 0 {
                out.push(',');
            }
            let is_primary = match primary_info {
                Some((prim_idx, _)) => prim_idx == idx,
                None => dev.device_type == BlockDeviceType::Ufs || dev.device_type == BlockDeviceType::Emmc,
            };
            let scheds: Vec<String> = dev
                .available_schedulers
                .iter()
                .map(|s| json_string(s))
                .collect();
            let current = SysfsReader::read_int::<u32>(dev.queue_path.join("read_ahead_kb")).ok();
            let target = StoragePolicy::read_ahead_target(dev);
            let _ = write!(out,
                "{{\"name\":{},\"type\":{},\"is_primary\":{},\"current_scheduler\":{},\"available_schedulers\":[{}],\"current_read_ahead_kb\":{},\"target_read_ahead_kb\":{},\"policy\":{}}}",
                json_string(&dev.name), json_string(dev.device_type.as_str()), is_primary, json_string(&dev.current_scheduler),
                scheds.join(","), current.map(|n| n.to_string()).unwrap_or_else(|| "null".into()),
                target.map(|n| n.to_string()).unwrap_or_else(|| "null".into()),
                json_string(if target.is_some() { "sd_prefetch_cap" } else { "preserve_kernel" }));
        }
        out.push_str("]}");
        out
    }

    /// Returns dynamic eMMC health telemetry: JEDEC life_time (pre_eol, type A, type B) and primary OS mount status.
    pub fn emmc_health_json() -> String {
        let storage = HardwareCapability::resolve_storage();
        let emmc_dev = storage.devices.iter().find(|d| d.device_type == BlockDeviceType::Emmc);
        let block_path = emmc_dev.and_then(|d| d.queue_path.parent());

        let life_time = block_path
            .and_then(|p| SysfsReader::read_trimmed(p.join("device/life_time")).ok())
            .unwrap_or_default();
        let pre_eol = block_path
            .and_then(|p| SysfsReader::read_trimmed(p.join("device/pre_eol_info")).ok())
            .unwrap_or_default();
        let rev = block_path
            .and_then(|p| SysfsReader::read_trimmed(p.join("device/rev")).ok())
            .unwrap_or_default();
        let name = block_path
            .and_then(|p| SysfsReader::read_trimmed(p.join("device/name")).ok())
            .unwrap_or_default();

        let is_primary_emmc = StoragePolicy::is_primary_system_emmc();

        format!(
            "{{\"is_emmc_present\":{},\"is_primary\":{},\"name\":{},\"life_time\":{},\"pre_eol_info\":{},\"rev\":{}}}",
            emmc_dev.is_some(),
            is_primary_emmc,
            json_string(&name),
            json_string(&life_time),
            json_string(&pre_eol),
            json_string(&rev)
        )
    }

    /// Apply the same media policy used by profiles, with verified writes and recovery.
    pub fn apply_default() -> String {
        let _guard = match ModeLock::acquire() {
            Ok(guard) => guard,
            Err(_) => return "{\"ok\":false,\"error\":\"busy\"}".into(),
        };
        let storage = HardwareCapability::resolve_storage();
        let (mut changed, mut unchanged, mut preserved, mut failed) = (0, 0, 0, 0);
        for dev in &storage.devices {
            let target = match StoragePolicy::read_ahead_target(dev) {
                Some(target) => target,
                None => {
                    preserved += 1;
                    continue;
                }
            };
            let path = dev.queue_path.join("read_ahead_kb");
            if SysfsReader::read_int::<u32>(&path).ok() == Some(target) {
                unchanged += 1;
                continue;
            }
            let target = target.to_string();
            if StorageState::remember_read_ahead(&path, &target).is_ok()
                && SysfsWriter::write_verify(&path, &target, WriteMode::Direct).is_ok()
            {
                changed += 1;
            } else {
                failed += 1;
            }
        }
        format!(
            "{{\"ok\":{},\"changed\":{},\"unchanged\":{},\"preserved\":{},\"failed\":{}}}",
            failed == 0,
            changed,
            unchanged,
            preserved,
            failed
        )
    }

    pub fn set_scheduler(block_prefix_or_name: &str, scheduler: &str) -> String {
        let _guard = match ModeLock::acquire() {
            Ok(guard) => guard,
            Err(_) => return "{\"ok\":false,\"error\":\"busy\"}".into(),
        };
        let storage = HardwareCapability::resolve_storage();
        let mut updated = 0usize;
        let exact = storage
            .devices
            .iter()
            .any(|dev| dev.name == block_prefix_or_name);
        for dev in &storage.devices {
            if !block_prefix_or_name.is_empty()
                && (dev.name == block_prefix_or_name
                    || (!exact && dev.name.starts_with(block_prefix_or_name)))
                && dev.available_schedulers.iter().any(|s| s == scheduler)
            {
                let path = dev.queue_path.join("scheduler");
                if SysfsWriter::write(&path, scheduler, WriteMode::Direct).is_ok()
                    && SysfsReader::read_trimmed(&path)
                        .map(|s| {
                            s == scheduler
                                || s.split_whitespace()
                                    .any(|s| s == format!("[{}]", scheduler))
                        })
                        .unwrap_or(false)
                {
                    updated += 1;
                }
            }
        }
        format!("{{\"ok\":{},\"updated\":{}}}", updated > 0, updated)
    }

    /// Enhanced Storage endpoint: reports primary system flash (eMMC/UFS) and external MicroSD.
    pub fn detect_mountx() -> MountXInfo {
        let mut info = MountXInfo::default();
        let storage = HardwareCapability::resolve_storage();

        // 1. Resolve Primary Storage Hardware Telemetry
        let is_primary_emmc = StoragePolicy::is_primary_system_emmc();
        info.is_primary_emmc = is_primary_emmc;
        if let Some((_, dev_type)) = StoragePolicy::primary_storage(&storage) {
            info.primary_storage_type = match dev_type {
                BlockDeviceType::Emmc => "eMMC 5.1 Flash Shield".to_string(),
                BlockDeviceType::Ufs => "UFS High-Throughput".to_string(),
                BlockDeviceType::Nvme => "NVMe Solid State".to_string(),
                BlockDeviceType::Sd => "MicroSD Card".to_string(),
                _ => "Generic Block Device".to_string(),
            };
        } else if is_primary_emmc {
            info.primary_storage_type = "eMMC 5.1 Flash Shield".to_string();
        }

        if let Some(emmc_dev) = storage.devices.iter().find(|d| d.device_type == BlockDeviceType::Emmc) {
            if let Some(p) = emmc_dev.queue_path.parent() {
                info.emmc_name = SysfsReader::read_trimmed(p.join("device/name")).unwrap_or_default();
                info.emmc_life_time = SysfsReader::read_trimmed(p.join("device/life_time")).unwrap_or_default();
            }
        }

        // 2. Resolve External MicroSD Card (if mounted)
        let (index, partition, mount_point, fs_type) = match StoragePolicy::mounted_sd(&storage) {
            Some(mounted) => mounted,
            None => {
                info.mmcqd_pids = Self::find_mmcqd_pids();
                return info;
            }
        };
        let dev = &storage.devices[index];
        info.is_detected = true;
        info.mount_point = mount_point;
        info.partition_name = partition;
        info.parent_device = dev.name.clone();
        info.fs_type = fs_type;
        info.f2fs_sysfs_exists = Path::new("/sys/fs/f2fs")
            .join(&info.partition_name)
            .is_dir();
        info.current_read_ahead_kb =
            SysfsReader::read_int::<u32>(dev.queue_path.join("read_ahead_kb")).unwrap_or(0);
        info.current_scheduler = dev.current_scheduler.clone();
        info.target_read_ahead_kb = StoragePolicy::read_ahead_target(dev);
        info.is_optimized = info.target_read_ahead_kb == Some(info.current_read_ahead_kb);
        // Compatibility fields are observation only, not evidence of applied boosts.
        info.dirty_background_bytes =
            SysfsReader::read_int::<u64>("/proc/sys/vm/dirty_background_bytes").unwrap_or(0);
        info.dirty_bytes = SysfsReader::read_int::<u64>("/proc/sys/vm/dirty_bytes").unwrap_or(0);
        info.mmcqd_pids = Self::find_mmcqd_pids();
        info
    }

    pub fn find_mmcqd_pids() -> Vec<i32> {
        let mut pids = Vec::new();
        if let Ok(entries) = fs::read_dir("/proc") {
            for entry in entries.flatten() {
                let pid = match entry.file_name().to_string_lossy().parse::<i32>() {
                    Ok(pid) => pid,
                    Err(_) => continue,
                };
                if fs::read_to_string(entry.path().join("comm"))
                    .map(|comm| comm.trim().starts_with("mmcqd"))
                    .unwrap_or(false)
                {
                    pids.push(pid);
                }
            }
        }
        pids.sort_unstable();
        pids
    }

    pub fn mountx_info_json() -> String {
        let info = Self::detect_mountx();
        let pids: Vec<String> = info.mmcqd_pids.iter().map(|p| p.to_string()).collect();
        format!(concat!("{{\"is_detected\":{},\"mount_point\":{},\"partition_name\":{},\"parent_device\":{},",
            "\"fs_type\":{},\"f2fs_sysfs_exists\":{},\"mmcqd_pids\":[{}],\"current_read_ahead_kb\":{},",
            "\"current_scheduler\":{},\"dirty_background_bytes\":{},\"dirty_bytes\":{},\"is_optimized\":{},\"target_read_ahead_kb\":{},",
            "\"is_primary_emmc\":{},\"primary_storage_type\":{},\"emmc_name\":{},\"emmc_life_time\":{}}}"),
            info.is_detected, json_string(&info.mount_point), json_string(&info.partition_name),
            json_string(&info.parent_device), json_string(&info.fs_type), info.f2fs_sysfs_exists,
            pids.join(","), info.current_read_ahead_kb, json_string(&info.current_scheduler),
            info.dirty_background_bytes, info.dirty_bytes, info.is_optimized,
            info.target_read_ahead_kb.map(|n| n.to_string()).unwrap_or_else(|| "null".into()),
            info.is_primary_emmc, json_string(&info.primary_storage_type), json_string(&info.emmc_name),
            json_string(&info.emmc_life_time))
    }

    /// Keep existing command names, but retire the unmeasured filesystem/VM/thread bundle.
    pub fn apply_flash_streaming_boost(boost: bool) -> String {
        if boost {
            return Self::apply_default();
        }
        let _guard = match ModeLock::acquire() {
            Ok(guard) => guard,
            Err(_) => return "{\"ok\":false,\"error\":\"busy\"}".into(),
        };
        let storage = HardwareCapability::resolve_storage();
        let (mut restored, mut skipped) = (0, 0);
        for dev in storage
            .devices
            .iter()
            .filter(|dev| dev.device_type == BlockDeviceType::Sd)
        {
            if StorageState::restore_read_ahead(&dev.queue_path) {
                restored += 1;
            } else {
                skipped += 1;
            }
        }
        format!(
            "{{\"ok\":{},\"restored\":{},\"skipped\":{}}}",
            restored > 0,
            restored,
            skipped
        )
    }
}
