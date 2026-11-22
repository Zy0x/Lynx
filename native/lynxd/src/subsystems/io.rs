use std::collections::{BTreeSet, HashMap};
use std::fmt::Write as FmtWrite;
use std::fs;
use std::path::{Path, PathBuf};

use crate::apply::lock::ModeLock;
use crate::hardware::capability::{
    BlockDeviceCapability, BlockDeviceType, HardwareCapability, StorageCapability,
};
use crate::hardware::storage::{MountedVolumeInfo, StoragePolicy};
use crate::state::storage_state::StorageState;
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

const MOUNTX_OVERRIDE_FLAG: &str = "/dev/lynx_mountx_override";

const MOUNTX_MODULE_CANDIDATES: &[&str] = &[
    "/data/adb/modules/MountX",
    "/data/adb/modules/mountx",
    "/data/adb/modules/Mountify",
    "/data/adb/ksu/modules/MountX",
    "/data/adb/ksud/modules/MountX",
    "/data/adb/ap/modules/MountX",
];

#[derive(Debug, Clone, Default)]
struct ParsedDatastorePrefs {
    sd_base_path: Option<String>,
    sd_block_device: Option<String>,
    io_tweaks_enabled: Option<bool>,
    io_read_ahead_kb: Option<u32>,
    io_scheduler: Option<String>,
    io_preset: Option<String>,
}

#[derive(Debug, Clone, Default)]
pub struct MountXInfo {
    pub mountx_installed: bool,
    pub module_installed: bool,
    pub app_installed: bool,
    pub mountx_mode: String,
    pub module_dir: String,
    pub module_version: String,
    pub io_tweaks_active: bool,
    pub override_unlocked: bool,
    pub sd_base: String,
    pub sd_block: String,
    pub configured_read_ahead_kb: Option<u32>,
    pub configured_scheduler: String,
    pub configured_nr_requests: Option<u32>,
    pub configured_rq_affinity: Option<u32>,
    pub managed_disks: Vec<String>,
    pub managed_partitions: Vec<String>,
    pub active_bind_count: usize,
    // Legacy & UI compatibility telemetry
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

impl MountXInfo {
    /// Returns true if MountX actively governs this physical disk and Lynx override is locked.
    pub fn is_disk_protected(&self, disk_name: &str) -> bool {
        self.mountx_installed
            && self.io_tweaks_active
            && !self.override_unlocked
            && self.managed_disks.iter().any(|d| d == disk_name)
    }

    pub fn is_disk_managed(&self, disk_name: &str) -> bool {
        self.mountx_installed && self.managed_disks.iter().any(|d| d == disk_name)
    }
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

fn read_varint(buf: &[u8], pos: &mut usize) -> Option<u64> {
    let mut val = 0u64;
    let mut shift = 0u32;
    while *pos < buf.len() && shift < 64 {
        let b = buf[*pos];
        *pos += 1;
        val |= ((b & 0x7f) as u64) << shift;
        if b & 0x80 == 0 {
            return Some(val);
        }
        shift += 7;
    }
    None
}

fn skip_wire_field(buf: &[u8], pos: &mut usize, wire_type: u64) -> bool {
    match wire_type {
        0 => read_varint(buf, pos).is_some(),
        1 => {
            if *pos + 8 <= buf.len() {
                *pos += 8;
                true
            } else {
                false
            }
        }
        2 => {
            let len = match read_varint(buf, pos) {
                Some(l) => l as usize,
                None => return false,
            };
            if *pos + len <= buf.len() {
                *pos += len;
                true
            } else {
                false
            }
        }
        5 => {
            if *pos + 4 <= buf.len() {
                *pos += 4;
                true
            } else {
                false
            }
        }
        _ => false,
    }
}

fn read_len_delimited<'a>(buf: &'a [u8], pos: &mut usize) -> Option<&'a [u8]> {
    let len = read_varint(buf, pos)? as usize;
    if *pos + len <= buf.len() {
        let slice = &buf[*pos..*pos + len];
        *pos += len;
        Some(slice)
    } else {
        None
    }
}

/// Decodes Android Preferences DataStore protobuf (`Preferences { map<string, Value> strings = 1; }`).
fn decode_datastore_prefs(data: &[u8]) -> ParsedDatastorePrefs {
    let mut out = ParsedDatastorePrefs::default();
    let mut pos = 0usize;
    while pos < data.len() {
        let tag = match read_varint(data, &mut pos) {
            Some(t) => t,
            None => break,
        };
        let field = tag >> 3;
        let wire = tag & 0x7;
        if field == 1 && wire == 2 {
            let entry = match read_len_delimited(data, &mut pos) {
                Some(s) => s,
                None => break,
            };
            let mut epos = 0usize;
            let mut key: Option<String> = None;
            let mut val_slice: Option<&[u8]> = None;
            while epos < entry.len() {
                let etag = match read_varint(entry, &mut epos) {
                    Some(t) => t,
                    None => break,
                };
                match (etag >> 3, etag & 0x7) {
                    (1, 2) => {
                        if let Some(kbytes) = read_len_delimited(entry, &mut epos) {
                            key = std::str::from_utf8(kbytes).ok().map(|s| s.to_string());
                        }
                    }
                    (2, 2) => {
                        val_slice = read_len_delimited(entry, &mut epos);
                    }
                    (_, w) => {
                        if !skip_wire_field(entry, &mut epos, w) {
                            break;
                        }
                    }
                }
            }
            if let (Some(k), Some(vs)) = (key, val_slice) {
                let mut vpos = 0usize;
                let mut b_val: Option<bool> = None;
                let mut i_val: Option<i64> = None;
                let mut s_val: Option<String> = None;
                while vpos < vs.len() {
                    let vtag = match read_varint(vs, &mut vpos) {
                        Some(t) => t,
                        None => break,
                    };
                    match (vtag >> 3, vtag & 0x7) {
                        (1, 0) => b_val = read_varint(vs, &mut vpos).map(|v| v != 0),
                        (3 | 4, 0) => i_val = read_varint(vs, &mut vpos).map(|v| v as i64),
                        (5, 2) => {
                            if let Some(sb) = read_len_delimited(vs, &mut vpos) {
                                s_val = std::str::from_utf8(sb).ok().map(|s| s.to_string());
                            }
                        }
                        (_, w) => {
                            if !skip_wire_field(vs, &mut vpos, w) {
                                break;
                            }
                        }
                    }
                }
                match k.as_str() {
                    "sd_base_path" => out.sd_base_path = s_val.filter(|s| !s.is_empty()),
                    "sd_block_device" => out.sd_block_device = s_val.filter(|s| !s.is_empty()),
                    "io_tweaks_enabled" => out.io_tweaks_enabled = b_val,
                    "io_read_ahead_kb" => out.io_read_ahead_kb = i_val.and_then(|v| u32::try_from(v).ok()),
                    "io_scheduler" => out.io_scheduler = s_val.filter(|s| !s.is_empty()),
                    "io_preset" => out.io_preset = s_val.filter(|s| !s.is_empty()),
                    _ => {}
                }
            }
        } else if !skip_wire_field(data, &mut pos, wire) {
            break;
        }
    }
    out
}

fn find_mountx_module() -> Option<(PathBuf, String)> {
    for candidate in MOUNTX_MODULE_CANDIDATES {
        let dir = Path::new(candidate);
        if dir.is_dir() && !dir.join("disable").exists() && !dir.join("remove").exists() {
            let ver = fs::read_to_string(dir.join("module.prop"))
                .ok()
                .and_then(|prop| {
                    prop.lines()
                        .find_map(|l| l.strip_prefix("version=").map(|v| v.trim().to_string()))
                })
                .unwrap_or_default();
            return Some((dir.to_path_buf(), ver));
        }
    }
    None
}

fn parse_kv_conf(path: &Path) -> HashMap<String, String> {
    let mut map = HashMap::new();
    let content = match fs::read_to_string(path) {
        Ok(c) => c,
        Err(_) => return map,
    };
    for line in content.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() || trimmed.starts_with('#') {
            continue;
        }
        if let Some((k, v)) = trimmed.split_once('=') {
            let val = v.trim().trim_matches('"').trim_matches('\'').to_string();
            map.insert(k.trim().to_string(), val);
        }
    }
    map
}

fn resolve_block_ref_to_disk(block_ref: &str) -> Option<(String, String)> {
    let clean = block_ref.trim();
    if clean.is_empty() {
        return None;
    }
    let sys_path = if let Some(dev_name) = clean.strip_prefix("/dev/block/") {
        PathBuf::from("/sys/class/block").join(dev_name)
    } else if !clean.contains('/') {
        PathBuf::from("/sys/class/block").join(clean)
    } else {
        fs::canonicalize(clean).ok().and_then(|c| {
            c.file_name()
                .map(|n| PathBuf::from("/sys/class/block").join(n))
        })?
    };
    if !sys_path.exists() {
        return None;
    }
    StoragePolicy::resolve_partition_and_disk(&sys_path).map(|(p, d, _)| (p, d))
}

fn detect_app_mountx_installed() -> bool {
    Path::new("/data/data/app.mountx").is_dir()
        || Path::new("/data/user/0/app.mountx").is_dir()
        || Path::new("/data/user_de/0/app.mountx").is_dir()
        || Path::new("/dev/mountx_bin").is_dir()
        || Path::new("/dev/mountx").is_dir()
        || Path::new("/dev/.mountx_booted").exists()
        || Path::new("/dev/.mountx_service.pid").exists()
}

fn read_app_datastore_prefs() -> ParsedDatastorePrefs {
    for p in &[
        "/data/data/app.mountx/files/datastore/mountx_settings.preferences_pb",
        "/data/user/0/app.mountx/files/datastore/mountx_settings.preferences_pb",
        "/data/user_de/0/app.mountx/files/datastore/mountx_settings.preferences_pb",
    ] {
        if let Ok(bytes) = fs::read(p) {
            return decode_datastore_prefs(&bytes);
        }
    }
    ParsedDatastorePrefs::default()
}

fn scan_mountx_mountinfo() -> (usize, BTreeSet<String>, BTreeSet<String>) {
    let mut bind_count = 0usize;
    let mut bind_disks = BTreeSet::new();
    let mut bind_parts = BTreeSet::new();
    let mounts = match fs::read_to_string("/proc/self/mountinfo") {
        Ok(m) => m,
        Err(_) => return (0, bind_disks, bind_parts),
    };
    let mut seen_targets = BTreeSet::new();
    for line in mounts.lines() {
        let (mount_part, _) = match line.split_once(" - ") {
            Some(parts) => parts,
            None => continue,
        };
        let fields: Vec<_> = mount_part.split_whitespace().collect();
        if fields.len() < 5 {
            continue;
        }
        let root = fields[3];
        if root.starts_with("/MountX") {
            let mnt = StoragePolicy::decode_mountinfo_path(fields[4]);
            if seen_targets.insert(mnt) {
                bind_count += 1;
            }
            if !fields[2].starts_with("0:") {
                let sys_dev = Path::new("/sys/dev/block").join(fields[2]);
                if let Some((part, disk, _)) = StoragePolicy::resolve_partition_and_disk(&sys_dev) {
                    bind_disks.insert(disk);
                    bind_parts.insert(part);
                }
            }
        }
    }
    (bind_count, bind_disks, bind_parts)
}

fn matches_sched(raw: &str, expected: &str) -> bool {
    raw == expected || raw.split_whitespace().any(|t| t == format!("[{}]", expected))
}

fn write_and_verify_queue(dev: &BlockDeviceCapability, param: &str, value: &str) -> Result<bool, ()> {
    let target_path = dev.queue_path.join(param);
    if !target_path.exists() {
        return Err(());
    }
    let current = SysfsReader::read_trimmed(&target_path).unwrap_or_default();
    if param == "scheduler" {
        if !dev.available_schedulers.iter().any(|s| s == value) {
            return Err(());
        }
        if matches_sched(&current, value) {
            return Ok(false);
        }
        SysfsWriter::write(&target_path, value, WriteMode::Direct).map_err(|_| ())?;
        let after = SysfsReader::read_trimmed(&target_path).unwrap_or_default();
        if matches_sched(&after, value) {
            Ok(true)
        } else {
            Err(())
        }
    } else {
        if current == value {
            return Ok(false);
        }
        SysfsWriter::write_verify(&target_path, value, WriteMode::Direct).map_err(|_| ())?;
        Ok(true)
    }
}

pub struct IoController;

impl IoController {
    pub fn info_json() -> String {
        let storage = HardwareCapability::resolve_storage();
        let volumes = StoragePolicy::mounted_volumes(&storage);
        let mountx = Self::detect_mountx_with_context(&storage, &volumes);
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
            let scheds: Vec<String> = dev.available_schedulers.iter().map(|s| json_string(s)).collect();
            let current_ra = SysfsReader::read_int::<u32>(dev.queue_path.join("read_ahead_kb")).ok();
            let nr_req = SysfsReader::read_int::<u32>(dev.queue_path.join("nr_requests")).ok();
            let iostats = SysfsReader::read_int::<u32>(dev.queue_path.join("iostats")).ok();
            let rq_affinity = SysfsReader::read_int::<u32>(dev.queue_path.join("rq_affinity")).ok();
            let add_random = SysfsReader::read_int::<u32>(dev.queue_path.join("add_random")).ok();
            let rotational = SysfsReader::read_int::<u32>(dev.queue_path.join("rotational")).ok();

            let block_root = dev.queue_path.parent();
            let size_bytes = block_root
                .and_then(|p| SysfsReader::read_int::<u64>(p.join("size")).ok())
                .unwrap_or(0)
                .saturating_mul(512);
            let removable = block_root
                .and_then(|p| SysfsReader::read_int::<u32>(p.join("removable")).ok())
                .map(|r| r != 0)
                .unwrap_or(dev.device_type == BlockDeviceType::Sd || dev.device_type == BlockDeviceType::Usb);
            let is_read_only = block_root
                .and_then(|p| SysfsReader::read_trimmed(p.join("ro")).ok())
                .as_deref()
                == Some("1");

            let mountx_managed = mountx.is_disk_managed(&dev.name);
            let mountx_locked = mountx.is_disk_protected(&dev.name);
            let target = if mountx_locked {
                mountx.configured_read_ahead_kb
            } else {
                StoragePolicy::read_ahead_target(dev)
            };

            let _ = write!(
                out,
                "{{\"name\":{},\"type\":{},\"group\":{},\"is_primary\":{},\"is_read_only\":{},\"size_bytes\":{},\"removable\":{},\"current_scheduler\":{},\"available_schedulers\":[{}],\"current_read_ahead_kb\":{},\"nr_requests\":{},\"iostats\":{},\"rq_affinity\":{},\"add_random\":{},\"rotational\":{},\"mountx_managed\":{},\"mountx_locked\":{},\"target_read_ahead_kb\":{},\"policy\":{}}}",
                json_string(&dev.name),
                json_string(dev.device_type.as_str()),
                json_string(StoragePolicy::block_group_for_path(
                    block_root,
                    &dev.name,
                    dev.device_type,
                )),
                is_primary,
                is_read_only,
                size_bytes,
                removable,
                json_string(&dev.current_scheduler),
                scheds.join(","),
                current_ra.map(|n| n.to_string()).unwrap_or_else(|| "null".into()),
                nr_req.map(|n| n.to_string()).unwrap_or_else(|| "null".into()),
                iostats.map(|n| n.to_string()).unwrap_or_else(|| "null".into()),
                rq_affinity.map(|n| n.to_string()).unwrap_or_else(|| "null".into()),
                add_random.map(|n| n.to_string()).unwrap_or_else(|| "null".into()),
                rotational.map(|n| n.to_string()).unwrap_or_else(|| "null".into()),
                mountx_managed,
                mountx_locked,
                target.map(|n| n.to_string()).unwrap_or_else(|| "null".into()),
                json_string(if mountx_locked {
                    "mountx_protected"
                } else if target.is_some() {
                    "sd_prefetch_cap"
                } else {
                    "preserve_kernel"
                }),
            );
        }

        out.push_str("],\"volumes\":[");
        for (idx, vol) in volumes.iter().enumerate() {
            if idx > 0 {
                out.push(',');
            }
            let is_mountx_target = mountx.mountx_installed
                && (mountx.managed_partitions.iter().any(|p| p == &vol.partition)
                    || (!mountx.sd_base.is_empty() && vol.mount_point == mountx.sd_base));
            let _ = write!(
                out,
                "{{\"partition\":{},\"parent_disk\":{},\"device_type\":{},\"group\":{},\"mount_point\":{},\"fs_type\":{},\"partition_size_bytes\":{},\"f2fs_sysfs_exists\":{},\"is_primary_internal\":{},\"is_mountx_target\":{}}}",
                json_string(&vol.partition),
                json_string(&vol.parent_disk),
                json_string(vol.device_type.as_str()),
                json_string(StoragePolicy::block_group_for_path(
                    Some(Path::new("/sys/block").join(&vol.parent_disk).as_path()),
                    &vol.parent_disk,
                    vol.device_type,
                )),
                json_string(&vol.mount_point),
                json_string(&vol.fs_type),
                vol.partition_size_bytes,
                vol.f2fs_sysfs_exists,
                vol.is_primary_internal,
                is_mountx_target,
            );
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

    /// Apply conservative SD cap ONLY on non-MountX-protected SD devices.
    pub fn apply_default() -> String {
        let _guard = match ModeLock::acquire() {
            Ok(guard) => guard,
            Err(_) => return "{\"ok\":false,\"error\":\"busy\"}".into(),
        };
        let storage = HardwareCapability::resolve_storage();
        let volumes = StoragePolicy::mounted_volumes(&storage);
        let mountx = Self::detect_mountx_with_context(&storage, &volumes);

        let (mut changed, mut unchanged, mut preserved, mut failed) = (0, 0, 0, 0);
        for dev in &storage.devices {
            if mountx.is_disk_protected(&dev.name) {
                preserved += 1;
                continue;
            }
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
        Self::group_tune(block_prefix_or_name, "scheduler", scheduler)
    }

    /// Unified verified batch writer for a block group (`internal`, `sd`, `usb`, `virtual`)
    /// or specific block device name(s) / comma-separated list.
    pub fn group_tune(target: &str, param: &str, value: &str) -> String {
        let valid_param = matches!(
            param,
            "scheduler" | "read_ahead_kb" | "nr_requests" | "rq_affinity" | "iostats" | "add_random"
        );
        if !valid_param {
            return "{\"ok\":false,\"error\":\"unsupported_param\"}".into();
        }
        if param != "scheduler" && value.trim().parse::<u32>().is_err() {
            return "{\"ok\":false,\"error\":\"invalid_numeric_value\"}".into();
        }
        let _guard = match ModeLock::acquire() {
            Ok(guard) => guard,
            Err(_) => return "{\"ok\":false,\"error\":\"busy\"}".into(),
        };

        let storage = HardwareCapability::resolve_storage();
        let volumes = StoragePolicy::mounted_volumes(&storage);
        let mountx = Self::detect_mountx_with_context(&storage, &volumes);

        let target_trimmed = target.trim();
        let target_tokens: Vec<&str> = target_trimmed.split(',').map(|s| s.trim()).filter(|s| !s.is_empty()).collect();
        let is_group = matches!(
            target_trimmed,
            "internal" | "sd" | "usb" | "external" | "dm" | "loop" | "zram" | "hw_boot" | "ram" | "other"
        );
        let exact_single = !is_group
            && target_tokens.len() == 1
            && storage.devices.iter().any(|dev| dev.name == target_trimmed);

        let mut updated = 0usize;
        let mut unchanged = 0usize;
        let mut skipped_protected = 0usize;
        let mut failed = 0usize;
        let mut updated_names: Vec<String> = Vec::new();

        for dev in &storage.devices {
            let matches = if is_group {
                StoragePolicy::block_group_for_path(
                    dev.queue_path.parent(),
                    &dev.name,
                    dev.device_type,
                ) == target_trimmed
            } else if target_tokens.len() > 1 {
                target_tokens.iter().any(|t| dev.name == *t)
            } else if exact_single {
                dev.name == target_trimmed
            } else {
                !target_trimmed.is_empty() && dev.name.starts_with(target_trimmed)
            };

            if !matches {
                continue;
            }
            if mountx.is_disk_protected(&dev.name) {
                skipped_protected += 1;
                continue;
            }
            if is_group {
                let is_ro = dev
                    .queue_path
                    .parent()
                    .and_then(|p| SysfsReader::read_trimmed(p.join("ro")).ok())
                    .as_deref()
                    == Some("1");
                if is_ro {
                    continue;
                }
            }

            match write_and_verify_queue(dev, param, value.trim()) {
                Ok(true) => {
                    updated += 1;
                    updated_names.push(json_string(&dev.name));
                }
                Ok(false) => {
                    unchanged += 1;
                    updated_names.push(json_string(&dev.name));
                }
                Err(()) => {
                    failed += 1;
                }
            }
        }

        let ok = (updated + unchanged) > 0 && skipped_protected == 0;
        format!(
            "{{\"ok\":{},\"updated\":{},\"unchanged\":{},\"skipped_protected\":{},\"failed\":{},\"devices\":[{}]}}",
            ok,
            updated,
            unchanged,
            skipped_protected,
            failed,
            updated_names.join(",")
        )
    }

    pub fn set_mountx_override(unlock: bool) -> String {
        let res = if unlock {
            fs::write(MOUNTX_OVERRIDE_FLAG, "1\n")
        } else {
            match fs::remove_file(MOUNTX_OVERRIDE_FLAG) {
                Ok(()) => Ok(()),
                Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(()),
                Err(e) => Err(e),
            }
        };
        let active = Path::new(MOUNTX_OVERRIDE_FLAG).exists();
        format!(
            "{{\"ok\":{},\"override_unlocked\":{}}}",
            res.is_ok(),
            active
        )
    }

    pub fn detect_mountx() -> MountXInfo {
        let storage = HardwareCapability::resolve_storage();
        let volumes = StoragePolicy::mounted_volumes(&storage);
        Self::detect_mountx_with_context(&storage, &volumes)
    }

    fn detect_mountx_with_context(storage: &StorageCapability, volumes: &[MountedVolumeInfo]) -> MountXInfo {
        let mut info = MountXInfo::default();
        info.override_unlocked = Path::new(MOUNTX_OVERRIDE_FLAG).exists();

        // 1. Primary Flash Telemetry
        let is_primary_emmc = StoragePolicy::is_primary_system_emmc();
        info.is_primary_emmc = is_primary_emmc;
        if let Some((_, dev_type)) = StoragePolicy::primary_storage(storage) {
            info.primary_storage_type = match dev_type {
                BlockDeviceType::Emmc => "eMMC 5.1 Flash".to_string(),
                BlockDeviceType::Ufs => "UFS High-Throughput".to_string(),
                BlockDeviceType::Nvme => "NVMe Solid State".to_string(),
                BlockDeviceType::Sd => "MicroSD Card".to_string(),
                _ => "Generic Block Device".to_string(),
            };
        } else if is_primary_emmc {
            info.primary_storage_type = "eMMC 5.1 Flash".to_string();
        }

        if let Some(emmc_dev) = storage.devices.iter().find(|d| d.device_type == BlockDeviceType::Emmc) {
            if let Some(p) = emmc_dev.queue_path.parent() {
                info.emmc_name = SysfsReader::read_trimmed(p.join("device/name")).unwrap_or_default();
                info.emmc_life_time = SysfsReader::read_trimmed(p.join("device/life_time")).unwrap_or_default();
            }
        }

        // 2. Discover MountX Module and/or Standalone App (zero hardcoded path assumptions)
        let module_found = find_mountx_module();
        let app_found = detect_app_mountx_installed();
        info.module_installed = module_found.is_some();
        info.app_installed = app_found;
        info.mountx_installed = info.module_installed || info.app_installed;
        info.mountx_mode = match (info.module_installed, info.app_installed) {
            (true, true) => "module+app".to_string(),
            (true, false) => "module".to_string(),
            (false, true) => "app".to_string(),
            (false, false) => "none".to_string(),
        };

        let mut mod_conf = HashMap::new();
        if let Some((mod_dir, mod_ver)) = module_found {
            info.module_dir = mod_dir.display().to_string();
            info.module_version = mod_ver;
            mod_conf = parse_kv_conf(&mod_dir.join("config.conf"));
        }
        let app_prefs = if info.app_installed {
            read_app_datastore_prefs()
        } else {
            ParsedDatastorePrefs::default()
        };

        let conf_sd_base = app_prefs
            .sd_base_path
            .clone()
            .or_else(|| mod_conf.get("SD_BASE").cloned())
            .unwrap_or_default();
        let conf_sd_block = app_prefs
            .sd_block_device
            .clone()
            .or_else(|| mod_conf.get("SD_BLOCK").cloned())
            .unwrap_or_default();

        info.sd_base = conf_sd_base.clone();
        info.sd_block = conf_sd_block.clone();

        let mod_tweaks = mod_conf.get("IO_TWEAKS_ENABLED").map(|v| v == "1");
        let pref_io_tweaks_enabled = app_prefs
            .io_tweaks_enabled
            .or(mod_tweaks)
            .unwrap_or(true);

        info.configured_read_ahead_kb = app_prefs
            .io_read_ahead_kb
            .or_else(|| mod_conf.get("READ_AHEAD_KB").and_then(|v| v.parse::<u32>().ok()));
        info.configured_scheduler = app_prefs
            .io_scheduler
            .clone()
            .or_else(|| mod_conf.get("IO_SCHEDULER").cloned())
            .unwrap_or_default();
        info.configured_nr_requests = mod_conf
            .get("NR_REQUESTS")
            .and_then(|v| v.parse::<u32>().ok());
        info.configured_rq_affinity = mod_conf
            .get("RQ_AFFINITY")
            .and_then(|v| v.parse::<u32>().ok());

        // 3. Resolve MountX-managed disks dynamically from configured block, base mountpoint, and bind mounts
        let mut managed_set = BTreeSet::new();
        let mut managed_parts = BTreeSet::new();
        let mut preferred_vol: Option<&MountedVolumeInfo> = None;

        if info.mountx_installed {
            if let Some((part, disk)) = resolve_block_ref_to_disk(&conf_sd_block) {
                managed_set.insert(disk);
                managed_parts.insert(part.clone());
                preferred_vol = volumes.iter().find(|v| v.partition == part);
            }
            if !conf_sd_base.is_empty() {
                if let Some(v) = volumes.iter().find(|v| v.mount_point == conf_sd_base) {
                    managed_set.insert(v.parent_disk.clone());
                    managed_parts.insert(v.partition.clone());
                    if preferred_vol.is_none() {
                        preferred_vol = Some(v);
                    }
                }
            }
            let (bind_count, bind_disks, bind_parts) = scan_mountx_mountinfo();
            info.active_bind_count = bind_count;
            for d in bind_disks {
                // Only guard non-internal / non-primary disks so internal UFS/eMMC stays unlocked unless SD/USB.
                if let Some(dev_cap) = storage.devices.iter().find(|c| c.name == d) {
                    if !matches!(dev_cap.device_type, BlockDeviceType::Ufs | BlockDeviceType::Emmc) {
                        managed_set.insert(d);
                    }
                }
            }
            for p in bind_parts {
                managed_parts.insert(p);
            }
        }
        info.managed_disks = managed_set.into_iter().collect();
        info.managed_partitions = managed_parts.into_iter().collect();
        info.io_tweaks_active = info.mountx_installed && !info.managed_disks.is_empty() && pref_io_tweaks_enabled;

        // 4. Populate active SD/MountX block telemetry cleanly
        let fallback_sd = volumes.iter().find(|v| v.device_type == BlockDeviceType::Sd);
        let chosen_vol = preferred_vol.or(fallback_sd);
        if let Some(vol) = chosen_vol {
            info.is_detected = true;
            info.mount_point = vol.mount_point.clone();
            info.partition_name = vol.partition.clone();
            info.parent_device = vol.parent_disk.clone();
            info.fs_type = vol.fs_type.clone();
            info.f2fs_sysfs_exists = vol.f2fs_sysfs_exists;
        } else if !conf_sd_block.is_empty() {
            if let Some((part, disk)) = resolve_block_ref_to_disk(&conf_sd_block) {
                info.is_detected = true;
                info.mount_point = conf_sd_base;
                info.partition_name = part;
                info.parent_device = disk;
                info.fs_type = mod_conf.get("FS_TYPE").cloned().unwrap_or_default();
                info.f2fs_sysfs_exists = Path::new("/sys/fs/f2fs").join(&info.partition_name).is_dir();
            }
        }

        if let Some(dev) = storage.devices.iter().find(|d| d.name == info.parent_device) {
            info.current_read_ahead_kb =
                SysfsReader::read_int::<u32>(dev.queue_path.join("read_ahead_kb")).unwrap_or(0);
            info.current_scheduler = dev.current_scheduler.clone();

            if info.is_disk_protected(&dev.name) {
                info.target_read_ahead_kb = info
                    .configured_read_ahead_kb
                    .or(Some(info.current_read_ahead_kb));
                info.is_optimized = info
                    .configured_read_ahead_kb
                    .map(|t| t == info.current_read_ahead_kb)
                    .unwrap_or(true);
            } else {
                info.target_read_ahead_kb = StoragePolicy::read_ahead_target(dev);
                info.is_optimized = info.target_read_ahead_kb == Some(info.current_read_ahead_kb);
            }
        }

        info.dirty_background_bytes =
            SysfsReader::read_int::<u64>("/proc/sys/vm/dirty_background_bytes").unwrap_or(0);
        info.dirty_bytes =
            SysfsReader::read_int::<u64>("/proc/sys/vm/dirty_bytes").unwrap_or(0);
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
        let managed: Vec<String> = info.managed_disks.iter().map(|d| json_string(d)).collect();
        format!(
            concat!(
                "{{\"mountx_installed\":{},\"module_installed\":{},\"app_installed\":{},\"mountx_mode\":{},",
                "\"module_dir\":{},\"module_version\":{},\"io_tweaks_active\":{},\"override_unlocked\":{},",
                "\"sd_base\":{},\"sd_block\":{},\"configured_read_ahead_kb\":{},\"configured_scheduler\":{},",
                "\"configured_nr_requests\":{},\"configured_rq_affinity\":{},\"managed_disks\":[{}],\"active_bind_count\":{},",
                "\"is_detected\":{},\"mount_point\":{},\"partition_name\":{},\"parent_device\":{},",
                "\"fs_type\":{},\"f2fs_sysfs_exists\":{},\"mmcqd_pids\":[{}],\"current_read_ahead_kb\":{},",
                "\"current_scheduler\":{},\"dirty_background_bytes\":{},\"dirty_bytes\":{},\"is_optimized\":{},\"target_read_ahead_kb\":{},",
                "\"is_primary_emmc\":{},\"primary_storage_type\":{},\"emmc_name\":{},\"emmc_life_time\":{}}}"
            ),
            info.mountx_installed,
            info.module_installed,
            info.app_installed,
            json_string(&info.mountx_mode),
            json_string(&info.module_dir),
            json_string(&info.module_version),
            info.io_tweaks_active,
            info.override_unlocked,
            json_string(&info.sd_base),
            json_string(&info.sd_block),
            info.configured_read_ahead_kb.map(|n| n.to_string()).unwrap_or_else(|| "null".into()),
            json_string(&info.configured_scheduler),
            info.configured_nr_requests.map(|n| n.to_string()).unwrap_or_else(|| "null".into()),
            info.configured_rq_affinity.map(|n| n.to_string()).unwrap_or_else(|| "null".into()),
            managed.join(","),
            info.active_bind_count,
            info.is_detected,
            json_string(&info.mount_point),
            json_string(&info.partition_name),
            json_string(&info.parent_device),
            json_string(&info.fs_type),
            info.f2fs_sysfs_exists,
            pids.join(","),
            info.current_read_ahead_kb,
            json_string(&info.current_scheduler),
            info.dirty_background_bytes,
            info.dirty_bytes,
            info.is_optimized,
            info.target_read_ahead_kb.map(|n| n.to_string()).unwrap_or_else(|| "null".into()),
            info.is_primary_emmc,
            json_string(&info.primary_storage_type),
            json_string(&info.emmc_name),
            json_string(&info.emmc_life_time)
        )
    }

    /// Respects MountX-protected disks and only tunes unprotected SD cards when explicitly called.
    pub fn apply_flash_streaming_boost(boost: bool) -> String {
        if boost {
            return Self::apply_default();
        }
        let _guard = match ModeLock::acquire() {
            Ok(guard) => guard,
            Err(_) => return "{\"ok\":false,\"error\":\"busy\"}".into(),
        };
        let storage = HardwareCapability::resolve_storage();
        let volumes = StoragePolicy::mounted_volumes(&storage);
        let mountx = Self::detect_mountx_with_context(&storage, &volumes);
        let (mut restored, mut skipped) = (0, 0);
        for dev in storage
            .devices
            .iter()
            .filter(|dev| dev.device_type == BlockDeviceType::Sd)
        {
            if mountx.is_disk_protected(&dev.name) {
                skipped += 1;
                continue;
            }
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn decodes_mountx_datastore_protobuf_fields() {
        // Entry 1: "sd_block_device" -> string "/dev/block/mmcblk0p3"
        // Entry 2: "sd_base_path" -> string "/mnt/custom_sd"
        // Entry 3: "io_tweaks_enabled" -> bool true
        // Entry 4: "io_read_ahead_kb" -> int32 1024
        fn str_val_entry(k: &str, v: &str) -> Vec<u8> {
            let mut val = vec![0x2au8, v.len() as u8];
            val.extend_from_slice(v.as_bytes());
            let mut inner = vec![0x0au8, k.len() as u8];
            inner.extend_from_slice(k.as_bytes());
            inner.push(0x12);
            inner.push(val.len() as u8);
            inner.extend_from_slice(&val);
            let mut outer = vec![0x0au8, inner.len() as u8];
            outer.extend_from_slice(&inner);
            outer
        }
        let mut buf = Vec::new();
        buf.extend_from_slice(&str_val_entry("sd_block_device", "/dev/block/mmcblk0p3"));
        buf.extend_from_slice(&str_val_entry("sd_base_path", "/mnt/custom_sd"));
        // io_tweaks_enabled = true (val = [0x08, 0x01])
        buf.extend_from_slice(&[0x0a, 23, 0x0a, 17]);
        buf.extend_from_slice(b"io_tweaks_enabled");
        buf.extend_from_slice(&[0x12, 0x02, 0x08, 0x01]);

        let parsed = decode_datastore_prefs(&buf);
        assert_eq!(parsed.sd_block_device.as_deref(), Some("/dev/block/mmcblk0p3"));
        assert_eq!(parsed.sd_base_path.as_deref(), Some("/mnt/custom_sd"));
        assert_eq!(parsed.io_tweaks_enabled, Some(true));
    }

    #[test]
    fn mountx_disk_protection_respects_override_unlock() {
        let mut info = MountXInfo::default();
        info.mountx_installed = true;
        info.io_tweaks_active = true;
        info.managed_disks = vec!["mmcblk0".to_string()];
        assert!(info.is_disk_protected("mmcblk0"));
        assert!(!info.is_disk_protected("sdc"));

        info.override_unlocked = true;
        assert!(!info.is_disk_protected("mmcblk0"));
    }

    #[test]
    fn block_groups_separate_storage_and_kernel_virtual_categories() {
        assert_eq!(StoragePolicy::block_group_for_device("sdc", BlockDeviceType::Ufs), "internal");
        assert_eq!(StoragePolicy::block_group_for_device("mmcblk0", BlockDeviceType::Emmc), "internal");
        assert_eq!(StoragePolicy::block_group_for_device("mmcblk1", BlockDeviceType::Sd), "sd");
        assert_eq!(StoragePolicy::block_group_for_device("sda", BlockDeviceType::Usb), "usb");
        assert_eq!(StoragePolicy::block_group_for_device("dm-2", BlockDeviceType::Virtual), "dm");
        assert_eq!(StoragePolicy::block_group_for_device("loop0", BlockDeviceType::Loop), "loop");
        assert_eq!(StoragePolicy::block_group_for_device("zram0", BlockDeviceType::Virtual), "zram");
        assert_eq!(StoragePolicy::block_group_for_device("ram0", BlockDeviceType::Virtual), "ram");
        assert_eq!(StoragePolicy::block_group_for_device("mmcblk0boot0", BlockDeviceType::Emmc), "hw_boot");
        assert_eq!(StoragePolicy::block_group_for_device("nbd0", BlockDeviceType::Virtual), "other");
    }
}
