use std::fs;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};

pub static RUNNING: AtomicBool = AtomicBool::new(true);

pub struct DaemonLifecycle;

impl DaemonLifecycle {
    /// Resolves the PID file location. On Android root, prefers volatile RAM tmpfs (/dev/lynxd.pid).
    pub fn pid_file_path() -> PathBuf {
        #[cfg(target_os = "android")]
        {
            if std::path::Path::new("/dev").exists() {
                return PathBuf::from("/dev/lynxd.pid");
            }
        }
        PathBuf::from("./lynxd.pid")
    }

    /// Acquires the daemon execution lock by writing the current process ID to the PID file.
    /// Safely handles stale PID files if a previous daemon crashed.
    pub fn acquire_lock() -> Result<u32, String> {
        let path = Self::pid_file_path();
        let my_pid = Self::get_current_pid();

        if path.exists() {
            if let Ok(content) = fs::read_to_string(&path) {
                if let Ok(existing_pid) = content.trim().parse::<u32>() {
                    if Self::is_process_alive(existing_pid) {
                        return Err(format!(
                            "Lynx daemon is already running (PID: {}). Only one instance is allowed.",
                            existing_pid
                        ));
                    } else {
                        // Stale lock from abnormal termination
                        let _ = fs::remove_file(&path);
                    }
                }
            }
        }

        if let Err(e) = fs::write(&path, my_pid.to_string()) {
            return Err(format!(
                "Failed to write PID lockfile {}: {}",
                path.display(),
                e
            ));
        }

        Self::register_signal_handlers();
        Ok(my_pid)
    }

    /// Releases and removes the PID lock file upon graceful exit.
    pub fn release_lock() {
        let path = Self::pid_file_path();
        if path.exists() {
            let _ = fs::remove_file(path);
        }
    }

    pub fn is_running() -> bool {
        RUNNING.load(Ordering::SeqCst)
    }

    pub fn stop_running() {
        RUNNING.store(false, Ordering::SeqCst);
    }

    pub fn get_current_pid() -> u32 {
        std::process::id()
    }

    /// Checks whether the daemon is currently running. Returns Some(pid) if active.
    pub fn check_status() -> Option<u32> {
        let path = Self::pid_file_path();
        if path.exists() {
            if let Ok(content) = fs::read_to_string(&path) {
                if let Ok(pid) = content.trim().parse::<u32>() {
                    if Self::is_process_alive(pid) {
                        return Some(pid);
                    }
                }
            }
        }
        None
    }

    /// Sends a graceful termination signal (SIGTERM) to the active daemon process.
    pub fn stop_daemon() -> Result<(), String> {
        match Self::check_status() {
            Some(pid) => {
                #[cfg(unix)]
                {
                    extern "C" {
                        fn kill(pid: i32, sig: i32) -> i32;
                    }
                    let res = unsafe { kill(pid as i32, 15) }; // 15 = SIGTERM
                    if res == 0 {
                        Ok(())
                    } else {
                        Err(format!("Failed to signal daemon process PID {}", pid))
                    }
                }
                #[cfg(windows)]
                {
                    Err(format!("Stop signal not supported natively on Windows (PID: {})", pid))
                }
            }
            None => Err("No active Lynx daemon process is running.".to_string()),
        }
    }

    pub fn is_process_alive(pid: u32) -> bool {
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

    fn register_signal_handlers() {
        #[cfg(unix)]
        {
            extern "C" {
                fn signal(sig: std::os::raw::c_int, handler: extern "C" fn(std::os::raw::c_int)) -> usize;
            }
            extern "C" fn sig_handler(_sig: std::os::raw::c_int) {
                RUNNING.store(false, Ordering::SeqCst);
            }
            unsafe {
                signal(2, sig_handler);  // SIGINT (Ctrl+C)
                signal(15, sig_handler); // SIGTERM (kill)
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_current_pid_positive() {
        let pid = DaemonLifecycle::get_current_pid();
        assert!(pid > 0);
    }

    #[test]
    fn test_running_flag() {
        DaemonLifecycle::stop_running();
        assert!(!DaemonLifecycle::is_running());
        RUNNING.store(true, Ordering::SeqCst);
        assert!(DaemonLifecycle::is_running());
    }
}
