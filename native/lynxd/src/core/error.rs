use std::fmt;
use std::io;
use std::path::PathBuf;

#[derive(Debug)]
pub enum SysfsError {
    NotFound(PathBuf),
    PermissionDenied(PathBuf),
    SelinuxDenied(PathBuf),
    NotRoot,
    ReadOnlyFilesystem(PathBuf),
    UnsupportedNode(PathBuf),
    InvalidValue {
        path: PathBuf,
        value: String,
    },
    VendorMismatch {
        detected: String,
        expected: String,
    },
    IoError {
        path: PathBuf,
        source: io::Error,
    },
    ParseError {
        path: PathBuf,
        raw: String,
    },
    VerificationFailed {
        path: PathBuf,
        expected: String,
        actual: String,
    },
}

impl fmt::Display for SysfsError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::NotFound(p) => write!(f, "Node not found: {}", p.display()),
            Self::PermissionDenied(p) => write!(f, "Permission denied on: {}", p.display()),
            Self::SelinuxDenied(p) => write!(f, "SELinux policy denied access to: {}", p.display()),
            Self::NotRoot => write!(f, "Operation requires UID 0 (root) privileges"),
            Self::ReadOnlyFilesystem(p) => write!(f, "Read-only filesystem on: {}", p.display()),
            Self::UnsupportedNode(p) => write!(f, "Node unsupported on this SoC/kernel: {}", p.display()),
            Self::InvalidValue { path, value } => {
                write!(f, "Invalid value '{}' for node: {}", value, path.display())
            }
            Self::VendorMismatch { detected, expected } => {
                write!(
                    f,
                    "Hardware vendor mismatch: detected '{}', expected '{}'",
                    detected, expected
                )
            }
            Self::IoError { path, source } => {
                write!(f, "I/O error on {}: {}", path.display(), source)
            }
            Self::ParseError { path, raw } => {
                write!(f, "Failed to parse value '{}' from: {}", raw, path.display())
            }
            Self::VerificationFailed {
                path,
                expected,
                actual,
            } => {
                write!(
                    f,
                    "Verification failed on {}: expected '{}', got '{}'",
                    path.display(),
                    expected,
                    actual
                )
            }
        }
    }
}

impl std::error::Error for SysfsError {
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            Self::IoError { source, .. } => Some(source),
            _ => None,
        }
    }
}
