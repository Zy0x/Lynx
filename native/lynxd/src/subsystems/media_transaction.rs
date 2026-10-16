use std::fs;
use std::io::Write;
use std::path::Path;

use super::media::{getprop, setprop, DIR, KEYS};

/// Exclusive create prevents two media actions from interleaving, even across UI surfaces.
pub struct MediaOperationGuard;

impl MediaOperationGuard {
    pub fn acquire() -> Result<Self, String> {
        fs::create_dir_all(DIR).map_err(|_| "media_lock_unavailable")?;
        let mut file = fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(Path::new(DIR).join("operation.lock"))
            .map_err(|_| "media_busy_or_stale_lock")?;
        let guard = Self;
        file.write_all(std::process::id().to_string().as_bytes())
            .map_err(|_| "media_lock_unavailable")?;
        Ok(guard)
    }
}

impl Drop for MediaOperationGuard {
    fn drop(&mut self) {
        let _ = fs::remove_file(Path::new(DIR).join("operation.lock"));
    }
}
use crate::state::ProfileStateManager;

/// Restore the exact preceding property and session state if saving an action fails.
pub struct MediaTransaction {
    properties: Vec<String>,
    mode: Option<String>,
    pending: Option<String>,
}

impl MediaTransaction {
    pub fn capture() -> Result<Self, String> {
        Ok(Self {
            properties: KEYS
                .iter()
                .map(|key| getprop(key))
                .collect::<Result<_, _>>()?,
            mode: Self::read_optional("mode")?,
            pending: Self::read_optional("pending")?,
        })
    }

    fn read_optional(name: &str) -> Result<Option<String>, String> {
        match fs::read_to_string(Path::new(DIR).join(name)) {
            Ok(value) => Ok(Some(value)),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(None),
            Err(_) => Err("session_read_failed".into()),
        }
    }

    pub fn error_with_rollback(&self, error: &str) -> String {
        let mut restored = true;
        for (key, value) in KEYS.iter().zip(&self.properties) {
            if getprop(key).as_ref() != Ok(value) && setprop(key, value).is_err() {
                restored = false;
            }
        }
        for (name, value) in [("mode", &self.mode), ("pending", &self.pending)] {
            let path = Path::new(DIR).join(name);
            let success = match value {
                Some(value) => ProfileStateManager::write_atomic(&path, value).is_ok(),
                None => fs::remove_file(&path)
                    .map(|_| true)
                    .unwrap_or_else(|error| error.kind() == std::io::ErrorKind::NotFound),
            };
            restored &= success;
        }
        if restored {
            error.to_string()
        } else {
            format!("{}_rollback_incomplete", error)
        }
    }
}
