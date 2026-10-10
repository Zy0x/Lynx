use std::path::{Path, PathBuf};
use std::time::Instant;

use crate::core::error::SysfsError;
use crate::core::result::Result;
use crate::sysfs::reader::SysfsReader;
use crate::sysfs::writer::{SysfsWriter, WriteMode};

#[derive(Debug, Clone)]
pub struct WriteOperation {
    pub path: PathBuf,
    pub value: String,
}

#[derive(Debug, Default)]
pub struct BatchReport {
    pub total: usize,
    pub applied: usize,
    pub failed: Vec<(PathBuf, SysfsError)>,
    pub elapsed_micros: u128,
}

impl BatchReport {
    pub fn display(&self) -> String {
        let mut out = format!(
            "Batch Transaction Report:\n\
             • Total Operations: {}\n\
             • Successfully Applied: {}\n\
             • Failures: {}\n\
             • Execution Time: {} µs ({:.2} ms)\n",
            self.total,
            self.applied,
            self.failed.len(),
            self.elapsed_micros,
            self.elapsed_micros as f64 / 1000.0
        );

        if !self.failed.is_empty() {
            out.push_str("Failed Operations:\n");
            for (p, err) in &self.failed {
                out.push_str(&format!("  - {}: {}\n", p.display(), err));
            }
        }
        out
    }
}

pub struct Transaction {
    operations: Vec<WriteOperation>,
    mode: WriteMode,
}

impl Transaction {
    pub fn new(mode: WriteMode) -> Self {
        Self {
            operations: Vec::new(),
            mode,
        }
    }

    pub fn add(&mut self, path: impl AsRef<Path>, value: impl Into<String>) -> &mut Self {
        self.operations.push(WriteOperation {
            path: path.as_ref().to_path_buf(),
            value: value.into(),
        });
        self
    }

    pub fn operations(&self) -> &[WriteOperation] {
        &self.operations
    }

    /// Phase 1: Pre-validation. Verifies existence and accessibility of all target nodes
    /// before applying ANY writes, preventing half-applied broken states.
    pub fn validate(&self) -> std::result::Result<(), Vec<SysfsError>> {
        let mut errors = Vec::new();

        for op in &self.operations {
            if !SysfsReader::exists(&op.path) {
                errors.push(SysfsError::NotFound(op.path.clone()));
            } else if !SysfsReader::is_writable(&op.path) && self.mode != WriteMode::ForcePermission {
                errors.push(SysfsError::PermissionDenied(op.path.clone()));
            }
        }

        if errors.is_empty() {
            Ok(())
        } else {
            Err(errors)
        }
    }

    /// Phase 2: Execution. Applies all writes sequentially and records timing.
    pub fn commit(&self) -> Result<BatchReport> {
        let start = Instant::now();
        let mut report = BatchReport {
            total: self.operations.len(),
            ..Default::default()
        };

        for op in &self.operations {
            match SysfsWriter::write(&op.path, &op.value, self.mode) {
                Ok(_) => {
                    report.applied += 1;
                }
                Err(e) => {
                    report.failed.push((op.path.clone(), e));
                }
            }
        }

        report.elapsed_micros = start.elapsed().as_micros();
        Ok(report)
    }

    /// Phase 3: Post-verification. Reads back values to ensure they took effect in the kernel.
    pub fn verify(&self) -> std::result::Result<(), Vec<SysfsError>> {
        let mut errors = Vec::new();

        for op in &self.operations {
            match SysfsReader::read_trimmed(&op.path) {
                Ok(actual) => {
                    if actual != op.value {
                        errors.push(SysfsError::VerificationFailed {
                            path: op.path.clone(),
                            expected: op.value.clone(),
                            actual,
                        });
                    }
                }
                Err(e) => {
                    errors.push(e);
                }
            }
        }

        if errors.is_empty() {
            Ok(())
        } else {
            Err(errors)
        }
    }
}
