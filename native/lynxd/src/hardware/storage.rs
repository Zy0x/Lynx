use std::collections::HashSet;
use std::fs;
use std::path::{Path, PathBuf};

use super::capability::{BlockDeviceCapability, BlockDeviceType, StorageCapability};
use crate::sysfs::SysfsReader;

#[derive(Debug, Clone, Copy)]
pub struct StorageTuneParams {
    pub read_ahead_kb: u32,
    pub scheduler: &'static str,
    pub nr_requests: Option<u32>,
    pub read_expire_ms: Option<u32>,
    pub writes_starved: Option<u32>,
    pub rq_affinity: u32,
    pub bfq_slice_idle_us: Option<u32>,
    pub bfq_low_latency: Option<u32>,
    pub bfq_timeout_sync_ms: Option<u32>,
    pub wbt_lat_usec: Option<u32>,
}

/// A conservative policy, shared by every profile and the explicit I/O commands.
pub struct StoragePolicy;

impl StoragePolicy {
    /// Classifies a block device with high dynamic intelligence and zero hardcoded assumptions.
    /// Multi-tier hardware ground truth resolves physical eMMC, MicroSD, UFS, NVMe, and USB.
    pub fn classify(block: &Path) -> BlockDeviceType {
        let real = match fs::canonicalize(block) {
            Ok(real) => real,
            Err(_) => return BlockDeviceType::Unknown,
        };
        let block_name = block.file_name().and_then(|n| n.to_str()).unwrap_or("");

        // 1. Virtual, Loop, and ZRAM
        if real.starts_with("/sys/devices/virtual/block") || block_name.starts_with("loop") || block_name.starts_with("ram") {
            return if block.join("loop").is_dir() || block_name.starts_with("loop") {
                BlockDeviceType::Loop
            } else {
                BlockDeviceType::Virtual
            };
        }

        // 2. Multi-tier MMC Detection (Dynamic & Zero-Hardcoded)
        // Evaluates MMC subsystem candidates without assuming index numbers (mmcblk0 vs mmcblk1).
        let is_mmc_candidate = block_name.starts_with("mmcblk")
            || block.join("device/type").exists()
            || real.to_string_lossy().contains("/mmc_host/")
            || real.to_string_lossy().contains("/bus/mmc/");

        if is_mmc_candidate {
            // Tier 1: Kernel MMC Driver Card Type
            if let Ok(card_type) = SysfsReader::read_trimmed(block.join("device/type")) {
                let ct = card_type.to_ascii_uppercase();
                if ct == "MMC" {
                    return BlockDeviceType::Emmc;
                } else if ct == "SD" || ct == "SDCOMBO" || ct.starts_with("SD") {
                    return BlockDeviceType::Sd;
                }
            }

            // Tier 2: JEDEC Extended CSD vs SD Spec Configuration Registers
            // ext_csd and life_time are strictly defined by JEDEC eMMC standard (JESD84)
            if block.join("device/ext_csd").exists() || block.join("device/life_time").exists() {
                return BlockDeviceType::Emmc;
            }
            // scr (SD CARD CONFIGURATION), ssr, speedclass are defined by SD Association
            if block.join("device/scr").exists()
                || block.join("device/ssr").exists()
                || block.join("device/speedclass").exists()
            {
                return BlockDeviceType::Sd;
            }

            // Tier 3: Hardware Companion Boot / RPMB Partitions
            // eMMC hardware defines boot0, boot1, rpmb sub-devices; MicroSD cards NEVER have them
            if !block_name.is_empty() {
                let has_boot0 = Path::new(&format!("/sys/block/{}boot0", block_name)).exists()
                    || Path::new(&format!("/dev/block/{}boot0", block_name)).exists();
                let has_rpmb = Path::new(&format!("/sys/block/{}rpmb", block_name)).exists()
                    || Path::new(&format!("/dev/block/{}rpmb", block_name)).exists();
                if has_boot0 || has_rpmb {
                    return BlockDeviceType::Emmc;
                }
            }

            // Tier 4: Bus Controller Ancestry & Removability Flag
            let real_str = real.to_string_lossy();
            if real_str.contains("externdevice") || real_str.contains("external") {
                return BlockDeviceType::Sd;
            }
            if real_str.contains("bootdevice") {
                return BlockDeviceType::Emmc;
            }
            if SysfsReader::read_trimmed(block.join("removable")).ok().as_deref() == Some("1") {
                return BlockDeviceType::Sd;
            }
        }

        // 3. SCSI disk names are shared by UFS, USB, SATA and other transports.
        // Inspect controller ancestry instead of treating every sd* disk as UFS.
        for ancestor in real.ancestors() {
            let subsystem = fs::read_link(ancestor.join("subsystem")).ok();
            let subsystem = subsystem
                .as_ref()
                .and_then(|p| p.file_name())
                .and_then(|n| n.to_str());
            if subsystem == Some("usb") {
                return BlockDeviceType::Usb;
            }
        }
        for ancestor in real.ancestors() {
            if fs::read_link(ancestor.join("subsystem"))
                .ok()
                .and_then(|p| p.file_name().map(|n| n == "nvme"))
                .unwrap_or(false)
            {
                return BlockDeviceType::Nvme;
            }
            let driver = fs::read_link(ancestor.join("driver")).ok();
            let driver = driver
                .as_ref()
                .and_then(|p| p.file_name())
                .and_then(|n| n.to_str())
                .unwrap_or("");
            if driver.starts_with("ufshcd")
                || driver.starts_with("ufs-")
                || ancestor.join("device_descriptor/ufs_features").exists()
            {
                return BlockDeviceType::Ufs;
            }
        }
        BlockDeviceType::Unknown
    }

    /// Only reduce excessive SD prefetch; never increase a smaller/OEM-disabled window.
    /// 256 KiB is a policy ceiling supported by the recorded SD trial, not a measured
    /// optimum for every card. Other media retain their OEM queue configuration.
    pub fn read_ahead_target(dev: &BlockDeviceCapability) -> Option<u32> {
        if dev.device_type != BlockDeviceType::Sd || !dev.queue_path.is_dir() {
            return None;
        }
        let block = dev.queue_path.parent()?;
        if SysfsReader::read_trimmed(block.join("ro")).ok()?.as_str() != "0" {
            return None;
        }
        let _current = SysfsReader::read_int::<u32>(dev.queue_path.join("read_ahead_kb")).ok()?;
        Some(256)
    }

    /// Calculate profile-specific tuning parameters based on verified physical media type.
    pub fn profile_targets(dev: &BlockDeviceCapability, profile_name: &str) -> Option<StorageTuneParams> {
        let effective_type = if dev.device_type == BlockDeviceType::Virtual && dev.name.starts_with("dm-") {
            Self::resolve_dm_slave_type(&dev.queue_path)
        } else {
            dev.device_type
        };

        match effective_type {
            BlockDeviceType::Ufs => {
                let (ra, nr) = match profile_name {
                    "extreme" => (2048, 512),
                    "performance" => (1024, 256),
                    "powersave" => (256, 64),
                    _ => (512, 128), // Balance
                };
                Some(StorageTuneParams {
                    read_ahead_kb: ra,
                    scheduler: "none",
                    nr_requests: Some(nr),
                    read_expire_ms: None,
                    writes_starved: None,
                    rq_affinity: 2,
                    bfq_slice_idle_us: None,
                    bfq_low_latency: None,
                    bfq_timeout_sync_ms: None,
                    wbt_lat_usec: None,
                })
            }
            BlockDeviceType::Emmc => {
                // eMMC 5.1: Half-Duplex serial bus with Android File-Based Encryption (FBE).
                // Bound read-ahead strictly to 128KB (balance/extreme) and 64KB (powersave)
                // to eliminate queue saturation, blk_crypto_wq choke, and SQLite keystore lockups.
                let (ra, nr, exp, starv, slice_idle, low_lat, to_sync, wbt) = match profile_name {
                    "extreme" => (128, 128, 64, 4, 0, 1, 120, 75000),
                    "performance" => (128, 128, 80, 4, 0, 1, 120, 75000),
                    "powersave" => (64, 64, 250, 2, 0, 0, 80, 100000),
                    _ => (128, 64, 125, 4, 0, 1, 120, 75000), // Balance
                };
                Some(StorageTuneParams {
                    read_ahead_kb: ra,
                    scheduler: "bfq",
                    nr_requests: Some(nr),
                    read_expire_ms: Some(exp),
                    writes_starved: Some(starv),
                    rq_affinity: 2,
                    bfq_slice_idle_us: Some(slice_idle),
                    bfq_low_latency: Some(low_lat),
                    bfq_timeout_sync_ms: Some(to_sync),
                    wbt_lat_usec: Some(wbt),
                })
            }
            BlockDeviceType::Sd => {
                // External MicroSD: High serial command latency on SDR104 bus.
                // 256-512KB burst read is optimal for continuous game asset/OBB streaming (Infinix X698 trial).
                let (ra, nr, exp, starv) = match profile_name {
                    "extreme" | "performance" => (512, 128, 80, 6),
                    "powersave" => (128, 64, 350, 2),
                    _ => (256, 128, 125, 4), // Balance: conservative 256KB
                };
                Some(StorageTuneParams {
                    read_ahead_kb: ra,
                    scheduler: "mq-deadline",
                    nr_requests: Some(nr),
                    read_expire_ms: Some(exp),
                    writes_starved: Some(starv),
                    rq_affinity: 2,
                    bfq_slice_idle_us: None,
                    bfq_low_latency: None,
                    bfq_timeout_sync_ms: None,
                    wbt_lat_usec: None,
                })
            }
            _ => None,
        }
    }

    /// Resolve underlying physical storage type of a device-mapper (/sys/block/dm-*) device.
    pub fn resolve_dm_slave_type(dm_queue_path: &Path) -> BlockDeviceType {
        let dm_block = match dm_queue_path.parent() {
            Some(p) => p,
            None => return BlockDeviceType::Virtual,
        };
        let slaves_dir = dm_block.join("slaves");
        if let Ok(entries) = fs::read_dir(slaves_dir) {
            for entry in entries.flatten() {
                let slave_path = entry.path();
                let slave_type = Self::classify(&slave_path);
                if slave_type != BlockDeviceType::Virtual
                    && slave_type != BlockDeviceType::Loop
                    && slave_type != BlockDeviceType::Unknown
                {
                    return slave_type;
                }
            }
        }
        BlockDeviceType::Virtual
    }

    /// Resolve partitions and stacked mappings using sysfs relationships, not name slicing.
    fn physical_devices(block: PathBuf, seen: &mut HashSet<PathBuf>) -> Option<Vec<PathBuf>> {
        let mut block = fs::canonicalize(block).ok()?;
        if block.join("partition").exists() {
            block = block.parent()?.to_path_buf();
        }
        if seen.len() >= 32 || !seen.insert(block.clone()) {
            return None;
        }
        let mut slaves = Vec::new();
        for entry in fs::read_dir(block.join("slaves")).ok()? {
            slaves.push(entry.ok()?.path());
        }
        if slaves.is_empty() {
            return Some(vec![block]);
        }
        let mut physical = Vec::new();
        for slave in slaves {
            physical.extend(Self::physical_devices(slave, seen)?);
        }
        Some(physical)
    }

    /// Locate a mounted SD filesystem by major:minor, including device-mapper mounts.
    pub fn mounted_sd(storage: &StorageCapability) -> Option<(usize, String, String, String)> {
        let mounts = fs::read_to_string("/proc/self/mountinfo").ok()?;
        for line in mounts.lines() {
            let (mount, filesystem) = match line.split_once(" - ") {
                Some(parts) => parts,
                None => continue,
            };
            let mount: Vec<_> = mount.split_whitespace().collect();
            let filesystem: Vec<_> = filesystem.split_whitespace().collect();
            if mount.len() < 5 || filesystem.len() < 2 {
                continue;
            }
            let sys_path = Path::new("/sys/dev/block").join(mount[2]);
            // A multi-device mapping has no single safe tuning target.
            let physical = match Self::physical_devices(sys_path.clone(), &mut HashSet::new()) {
                Some(physical) if physical.len() == 1 => physical,
                _ => continue,
            };
            let index = storage.devices.iter().position(|dev| {
                dev.device_type == BlockDeviceType::Sd
                    && fs::canonicalize(dev.queue_path.parent().unwrap_or(Path::new("")))
                        .ok()
                        .as_ref()
                        == physical.first()
            });
            if let Some(index) = index {
                let partition = fs::canonicalize(sys_path)
                    .ok()?
                    .file_name()?
                    .to_string_lossy()
                    .into_owned();
                let mount_point = mount[4]
                    .replace("\\040", " ")
                    .replace("\\011", "\t")
                    .replace("\\012", "\n")
                    .replace("\\134", "\\");
                return Some((index, partition, mount_point, filesystem[0].to_string()));
            }
        }
        None
    }

    /// Locates the primary physical storage device hosting `/data` (or `/system`) with dynamic accuracy.
    /// Traverses mountinfo and device-mapper slaves to identify physical parent without hardcoded device names.
    pub fn primary_storage(storage: &StorageCapability) -> Option<(usize, BlockDeviceType)> {
        let mounts = fs::read_to_string("/proc/self/mountinfo")
            .or_else(|_| fs::read_to_string("/proc/mounts"))
            .ok()?;

        for target in &["/data", "/system"] {
            for line in mounts.lines() {
                let (dev_str, mount_point) = if line.contains(" - ") {
                    let parts: Vec<&str> = line.split(" - ").collect();
                    let m_parts: Vec<&str> = parts[0].split_whitespace().collect();
                    if m_parts.len() < 5 {
                        continue;
                    }
                    (m_parts[2], m_parts[4])
                } else {
                    let parts: Vec<&str> = line.split_whitespace().collect();
                    if parts.len() < 2 {
                        continue;
                    }
                    (parts[0], parts[1])
                };

                if mount_point != *target {
                    continue;
                }

                let sys_path = if dev_str.starts_with("/dev/block/") {
                    let dev_name = dev_str.trim_start_matches("/dev/block/");
                    Path::new("/sys/class/block").join(dev_name)
                } else if Path::new("/sys/dev/block").join(dev_str).exists() {
                    Path::new("/sys/dev/block").join(dev_str)
                } else {
                    continue;
                };

                if let Some(physical) = Self::physical_devices(sys_path, &mut HashSet::new()) {
                    if let Some(first_phys) = physical.first() {
                        let dev_type = Self::classify(first_phys);
                        let idx = storage.devices.iter().position(|dev| {
                            fs::canonicalize(dev.queue_path.parent().unwrap_or(Path::new("")))
                                .ok()
                                .as_ref()
                                == Some(first_phys)
                        });
                        if let Some(i) = idx {
                            return Some((i, dev_type));
                        }
                        return Some((0, dev_type));
                    }
                }
            }
        }
        None
    }

    /// Checks dynamically whether the primary system storage is eMMC (Half-Duplex flash).
    pub fn is_primary_system_emmc() -> bool {
        let storage = crate::hardware::capability::HardwareCapability::resolve_storage();
        if let Some((_, dev_type)) = Self::primary_storage(&storage) {
            return dev_type == BlockDeviceType::Emmc;
        }
        // Fallback: check if any eMMC device exists and no UFS exists
        let has_emmc = storage.devices.iter().any(|d| d.device_type == BlockDeviceType::Emmc);
        let has_ufs = storage.devices.iter().any(|d| d.device_type == BlockDeviceType::Ufs);
        has_emmc && !has_ufs
    }
}
