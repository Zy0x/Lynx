use std::fs::{File, OpenOptions};
use std::path::Path;
use std::thread::sleep;
use std::time::Duration;

/// Own the serialization primitive for the entire read-modify-rename transaction.
pub(super) struct ConfigLock {
    _file: File,
    #[cfg(not(unix))]
    path: std::path::PathBuf,
}

impl ConfigLock {
    pub fn acquire(path: &Path) -> Result<Self, String> {
        #[cfg(unix)]
        {
            use std::os::fd::AsRawFd;
            use std::os::unix::fs::OpenOptionsExt;
            extern "C" {
                fn flock(fd: i32, operation: i32) -> i32;
            }
            let file = OpenOptions::new()
                .read(true)
                .write(true)
                .create(true)
                .mode(0o600)
                .open(path)
                .map_err(|_| "config_lock_unavailable".to_string())?;
            for _ in 0..80 {
                // LOCK_EX | LOCK_NB; the kernel releases this lock when the descriptor closes.
                if unsafe { flock(file.as_raw_fd(), 2 | 4) } == 0 {
                    return Ok(Self { _file: file });
                }
                if std::io::Error::last_os_error().kind() != std::io::ErrorKind::WouldBlock {
                    return Err("config_lock_unavailable".into());
                }
                sleep(Duration::from_millis(5));
            }
        }
        #[cfg(not(unix))]
        {
            for _ in 0..80 {
                match OpenOptions::new().write(true).create_new(true).open(path) {
                    Ok(file) => {
                        return Ok(Self {
                            _file: file,
                            path: path.to_path_buf(),
                        })
                    }
                    Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {}
                    Err(_) => return Err("config_lock_unavailable".into()),
                }
                sleep(Duration::from_millis(5));
            }
        }
        Err("config_busy".into())
    }
}

#[cfg(not(unix))]
impl Drop for ConfigLock {
    fn drop(&mut self) {
        let _ = std::fs::remove_file(&self.path);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn concurrent_writer_cannot_break_an_owned_lock() {
        let stamp = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let path =
            std::env::temp_dir().join(format!("lynx_config_test_{}_{}", std::process::id(), stamp));
        let first = ConfigLock::acquire(&path).unwrap();
        assert!(matches!(ConfigLock::acquire(&path), Err(error) if error == "config_busy"));
        drop(first);
        let next = ConfigLock::acquire(&path).unwrap();
        drop(next);
        let _ = std::fs::remove_file(path);
    }
}
