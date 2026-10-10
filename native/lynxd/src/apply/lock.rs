use std::fs;
use std::path::PathBuf;

pub struct ModeLockGuard {
    path: PathBuf,
}

impl Drop for ModeLockGuard {
    fn drop(&mut self) {
        if self.path.exists() {
            let _ = fs::remove_file(&self.path);
        }
    }
}

pub struct ModeLock;

impl ModeLock {
    /// Resolves the mode lockfile location. On Android, strictly uses volatile RAM tmpfs (/dev/lynx_mode.lock).
    pub fn lock_path() -> PathBuf {
        #[cfg(target_os = "android")]
        {
            if std::path::Path::new("/dev").exists() {
                return PathBuf::from("/dev/lynx_mode.lock");
            }
        }
        PathBuf::from("./lynx_mode.lock")
    }

    /// Acquires the profile transaction lock.
    /// Returns a RAII guard that automatically removes the lockfile when dropped.
    pub fn acquire() -> Result<ModeLockGuard, String> {
        let path = Self::lock_path();
        let my_pid = std::process::id();

        if path.exists() {
            if let Ok(content) = fs::read_to_string(&path) {
                if let Ok(existing_pid) = content.trim().parse::<u32>() {
                    if Self::is_process_alive(existing_pid) && existing_pid != my_pid {
                        return Err(format!(
                            "Profile transaction is currently locked by active process (PID: {}).",
                            existing_pid
                        ));
                    } else {
                        // Stale lock from crashed process
                        let _ = fs::remove_file(&path);
                    }
                }
            }
        }

        if let Err(e) = fs::write(&path, my_pid.to_string()) {
            return Err(format!(
                "Failed to acquire profile lock {}: {}",
                path.display(),
                e
            ));
        }

        Ok(ModeLockGuard { path })
    }

    fn is_process_alive(pid: u32) -> bool {
        #[cfg(unix)]
        {
            std::path::Path::new(&format!("/proc/{}", pid)).exists()
        }
        #[cfg(windows)]
        {
            let _ = pid;
            false
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_mode_lock_acquire_and_drop() {
        let guard = ModeLock::acquire().expect("Failed to acquire mode lock");
        assert!(guard.path.exists());
        let path_clone = guard.path.clone();
        drop(guard);
        assert!(!path_clone.exists());
    }
}
