use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};

#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;

use crate::core::error::SysfsError;
use crate::core::result::Result;
use crate::sysfs::reader::SysfsReader;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WriteMode {
    /// Standard direct root write without changing file permissions (Default & Safest).
    Direct,
    /// Verifies process has UID 0 (root) before attempting write.
    RootRetry,
    /// Explicit opt-in chmod 0666 fallback only if direct write fails with permission denied.
    ForcePermission,
}

#[derive(Debug, Clone)]
pub struct DryRunReport {
    pub path: PathBuf,
    pub current_value: Option<String>,
    pub target_value: String,
    pub exists: bool,
    pub is_writable: bool,
    pub is_root: bool,
}

impl DryRunReport {
    pub fn display(&self) -> String {
        format!(
            "--- Dry-Run Inspection ---\n\
             Target Node : {}\n\
             Exists      : {}\n\
             Old Value   : {}\n\
             New Value   : {}\n\
             Writable    : {}\n\
             Root (UID 0): {}\n\
             Action      : SKIPPED (No changes made to kernel)",
            self.path.display(),
            if self.exists { "YES" } else { "NO" },
            self.current_value.as_deref().unwrap_or("<unreadable / non-existent>"),
            self.target_value,
            if self.is_writable { "YES" } else { "NO" },
            if self.is_root { "YES" } else { "NO" }
        )
    }
}

pub struct SysfsWriter;

impl SysfsWriter {
    /// Check whether the current process has effective UID 0 (root) via /proc/self/status.
    pub fn is_current_process_root() -> bool {
        if let Ok(content) = fs::read_to_string("/proc/self/status") {
            for line in content.lines() {
                if line.starts_with("Uid:") {
                    // Format: Uid:\t<real>\t<effective>\t<saved>\t<fs>
                    let parts: Vec<&str> = line.split_whitespace().collect();
                    if parts.len() >= 3 {
                        return parts[2] == "0";
                    }
                }
            }
        }
        false
    }

    /// Perform a safe inspection of what would be written without touching the kernel node.
    pub fn dry_run(path: impl AsRef<Path>, target_val: impl AsRef<str>) -> DryRunReport {
        let p = path.as_ref();
        let exists = p.exists();
        let current_val = if exists { SysfsReader::read_trimmed(p).ok() } else { None };
        let is_writable = if exists { SysfsReader::is_writable(p) } else { false };
        let is_root = Self::is_current_process_root();

        DryRunReport {
            path: p.to_path_buf(),
            current_value: current_val,
            target_value: target_val.as_ref().to_string(),
            exists,
            is_writable,
            is_root,
        }
    }

    /// Write value to node using the specified WriteMode.
    pub fn write(path: impl AsRef<Path>, value: impl AsRef<str>, mode: WriteMode) -> Result<()> {
        let p = path.as_ref();
        let path_buf: PathBuf = p.to_path_buf();
        let val_str = value.as_ref();

        if !p.exists() {
            return Err(SysfsError::NotFound(path_buf));
        }

        if mode == WriteMode::RootRetry && !Self::is_current_process_root() {
            return Err(SysfsError::NotRoot);
        }

        // Try direct open and write
        match fs::OpenOptions::new().write(true).truncate(true).open(p) {
            Ok(mut file) => {
                if let Err(write_err) = file.write_all(val_str.as_bytes()) {
                    return Err(SysfsError::IoError {
                        path: path_buf,
                        source: write_err,
                    });
                }
                let _ = file.flush();
                Ok(())
            }
            Err(open_err) => {
                if open_err.kind() == std::io::ErrorKind::PermissionDenied {
                    // Check if ForcePermission was explicitly requested
                    if mode == WriteMode::ForcePermission {
                        #[cfg(unix)]
                        {
                            let perm = fs::Permissions::from_mode(0o666);
                            let _ = fs::set_permissions(p, perm);
                            if let Ok(mut retry_file) = fs::OpenOptions::new().write(true).truncate(true).open(p) {
                                if let Err(write_err) = retry_file.write_all(val_str.as_bytes()) {
                                    return Err(SysfsError::IoError {
                                        path: path_buf,
                                        source: write_err,
                                    });
                                }
                                let _ = retry_file.flush();
                                return Ok(());
                            }
                        }
                    }

                    // If not root, report NotRoot; otherwise report PermissionDenied or SelinuxDenied
                    if !Self::is_current_process_root() {
                        Err(SysfsError::NotRoot)
                    } else {
                        Err(SysfsError::PermissionDenied(path_buf))
                    }
                } else {
                    Err(SysfsError::IoError {
                        path: path_buf,
                        source: open_err,
                    })
                }
            }
        }
    }

    /// Write value to node, and immediately read back to verify if the hardware kernel accepted it.
    pub fn write_verify(path: impl AsRef<Path>, value: impl AsRef<str>, mode: WriteMode) -> Result<()> {
        let p = path.as_ref();
        let val_str = value.as_ref();

        Self::write(p, val_str, mode)?;

        // Verification phase
        let read_back = SysfsReader::read_trimmed(p)?;
        if read_back != val_str {
            return Err(SysfsError::VerificationFailed {
                path: p.to_path_buf(),
                expected: val_str.to_string(),
                actual: read_back,
            });
        }

        Ok(())
    }
}
