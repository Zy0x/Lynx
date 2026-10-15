use std::fs;
use std::path::{Path, PathBuf};

use crate::hardware::capability::BlockDeviceType;
use crate::hardware::storage::StoragePolicy;
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

/// Volatile recovery records never survive a reboot or follow a card to another slot.
pub struct StorageState;

impl StorageState {
    fn identity(queue: &Path) -> Option<String> {
        let block = queue.parent()?;
        if StoragePolicy::classify(block) != BlockDeviceType::Sd {
            return None;
        }
        let boot = SysfsReader::read_trimmed("/proc/sys/kernel/random/boot_id").ok()?;
        let cid = SysfsReader::read_trimmed(block.join("device/cid")).ok()?;
        let dev = SysfsReader::read_trimmed(block.join("dev")).ok()?;
        let real = fs::canonicalize(block).ok()?;
        if boot.is_empty() || cid.is_empty() || dev.is_empty() {
            return None;
        }
        Some(format!("{}|{}|{}|{}", boot, cid, dev, real.display()))
    }

    fn record_path(queue: &Path) -> Option<PathBuf> {
        let name = queue.parent()?.file_name()?.to_str()?;
        if name.is_empty()
            || !name
                .chars()
                .all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_')
        {
            return None;
        }
        Some(Path::new("/dev/lynx_io").join(name))
    }

    /// Called under ModeLock before the first write, including writes from profile apply.
    pub fn remember_read_ahead(path: &Path, target: &str) -> std::io::Result<()> {
        let invalid = || {
            std::io::Error::new(
                std::io::ErrorKind::InvalidData,
                "storage_identity_unavailable",
            )
        };
        if path.file_name().and_then(|n| n.to_str()) != Some("read_ahead_kb") {
            return Err(invalid());
        }
        let queue = path.parent().ok_or_else(invalid)?;
        let identity = Self::identity(queue).ok_or_else(invalid)?;
        let record = Self::record_path(queue).ok_or_else(invalid)?;
        let original = SysfsReader::read_int::<u32>(path).map_err(|_| invalid())?;
        let target = target.parse::<u32>().map_err(|_| invalid())?;
        let previous = fs::read_to_string(&record).unwrap_or_default();
        let mut lines = previous.lines();
        let original = if lines.next() == Some(identity.as_str()) {
            lines
                .next()
                .and_then(|v| v.parse::<u32>().ok())
                .unwrap_or(original)
        } else {
            original
        };
        fs::create_dir_all(record.parent().ok_or_else(invalid)?)?;
        let temp = record.with_extension("tmp");
        fs::write(&temp, format!("{}\n{}\n{}\n", identity, original, target))?;
        fs::rename(temp, record)
    }

    /// Only restore our own current value on the same card and boot; preserve later edits.
    pub fn restore_read_ahead(queue: &Path) -> bool {
        let (identity, record) = match (Self::identity(queue), Self::record_path(queue)) {
            (Some(identity), Some(record)) => (identity, record),
            _ => return false,
        };
        let saved = match fs::read_to_string(&record) {
            Ok(saved) => saved,
            Err(_) => return false,
        };
        let mut lines = saved.lines();
        if lines.next() != Some(identity.as_str()) {
            return false;
        }
        let (original, target) = match (lines.next(), lines.next()) {
            (Some(original), Some(target)) => {
                match (original.parse::<u32>(), target.parse::<u32>()) {
                    (Ok(original), Ok(target)) => (original, target),
                    _ => return false,
                }
            }
            _ => return false,
        };
        let path = queue.join("read_ahead_kb");
        let current = SysfsReader::read_int::<u32>(&path).ok();
        if current != Some(target) && current != Some(original) {
            return false;
        }
        if current != Some(original)
            && SysfsWriter::write_verify(&path, original.to_string(), WriteMode::Direct).is_err()
        {
            return false;
        }
        fs::remove_file(record).is_ok()
    }
}
