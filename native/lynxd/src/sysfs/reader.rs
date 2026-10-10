use std::fs;
use std::path::{Path, PathBuf};
use std::str::FromStr;

use crate::core::error::SysfsError;
use crate::core::result::Result;

pub struct SysfsReader;

impl SysfsReader {
    /// Fast check if a kernel node exists.
    #[inline]
    pub fn exists(path: impl AsRef<Path>) -> bool {
        path.as_ref().exists()
    }

    /// Check if a node is readable by opening with read-only permissions.
    pub fn is_readable(path: impl AsRef<Path>) -> bool {
        let p = path.as_ref();
        if !p.exists() {
            return false;
        }
        fs::File::open(p).is_ok()
    }

    /// Check if a node is writable by opening with write permissions.
    pub fn is_writable(path: impl AsRef<Path>) -> bool {
        let p = path.as_ref();
        if !p.exists() {
            return false;
        }
        fs::OpenOptions::new().write(true).open(p).is_ok()
    }

    /// Read raw node content and trim trailing newlines, whitespace, and null characters.
    pub fn read_trimmed(path: impl AsRef<Path>) -> Result<String> {
        let p = path.as_ref();
        let path_buf: PathBuf = p.to_path_buf();

        if !p.exists() {
            return Err(SysfsError::NotFound(path_buf));
        }

        match fs::read_to_string(p) {
            Ok(content) => {
                let trimmed = content.trim_matches(|c: char| c.is_whitespace() || c == '\0').to_string();
                Ok(trimmed)
            }
            Err(_e) => {
                // If read_to_string fails due to invalid UTF-8 (common in some binary DTB nodes),
                // fall back to reading raw bytes and lossy UTF-8 conversion.
                match fs::read(p) {
                    Ok(bytes) => {
                        let text = String::from_utf8_lossy(&bytes)
                            .trim_matches(|c: char| c.is_whitespace() || c == '\0')
                            .to_string();
                        Ok(text)
                    }
                    Err(read_err) => {
                        if read_err.kind() == std::io::ErrorKind::PermissionDenied {
                            Err(SysfsError::PermissionDenied(path_buf))
                        } else {
                            Err(SysfsError::IoError {
                                path: path_buf,
                                source: read_err,
                            })
                        }
                    }
                }
            }
        }
    }

    /// Read node content and safely parse it into any primitive integer or type implementing FromStr.
    pub fn read_int<T: FromStr>(path: impl AsRef<Path>) -> Result<T> {
        let p = path.as_ref();
        let raw = Self::read_trimmed(p)?;
        match raw.parse::<T>() {
            Ok(val) => Ok(val),
            Err(_) => Err(SysfsError::ParseError {
                path: p.to_path_buf(),
                raw,
            }),
        }
    }
}
