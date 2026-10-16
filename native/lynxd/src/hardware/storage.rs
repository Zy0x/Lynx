use std::collections::HashSet;
use std::fs;
use std::path::{Path, PathBuf};

use super::capability::{BlockDeviceCapability, BlockDeviceType, StorageCapability};
use crate::sysfs::SysfsReader;

/// A conservative policy, shared by every profile and the explicit I/O commands.
pub struct StoragePolicy;

impl StoragePolicy {
    pub fn classify(block: &Path) -> BlockDeviceType {
        let real = match fs::canonicalize(block) {
            Ok(real) => real,
            Err(_) => return BlockDeviceType::Unknown,
        };
        if real.starts_with("/sys/devices/virtual/block") {
            return if block.join("loop").is_dir() {
                BlockDeviceType::Loop
            } else {
                BlockDeviceType::Virtual
            };
        }

        // MMC exposes the actual card type. The mmcblk index does not identify SD/eMMC.
        match SysfsReader::read_trimmed(block.join("device/type")).as_deref() {
            Ok("SD") | Ok("SDcombo") => return BlockDeviceType::Sd,
            Ok("MMC") => return BlockDeviceType::Emmc,
            _ => {}
        }

        // SCSI disk names are shared by UFS, USB, SATA and other transports.
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
        Some(1024)
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
}
