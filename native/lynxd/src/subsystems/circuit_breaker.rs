use std::fs;
use std::path::Path;
use std::time::{SystemTime, UNIX_EPOCH};

const SAFETY_FILE: &'static str = "/data/adb/lynx/safety_watchdog.json";
const ROLLBACK_LOG: &'static str = "/data/adb/lynx/panic_rollback.log";
const WATCHDOG_TIMEOUT_SECS: u64 = 60;

#[derive(Debug, Clone)]
pub struct SafetyMarker {
    pub armed_at: u64,
    pub profile: String,
    pub completed: bool,
}

pub struct CircuitBreaker;

impl CircuitBreaker {
    fn current_timestamp() -> u64 {
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0)
    }

    /// Arms the watchdog before applying an aggressive or extreme profile.
    pub fn arm(profile_name: &str) {
        let timestamp = Self::current_timestamp();
        let payload = format!("{}\n{}\n0\n", timestamp, profile_name);

        let _ = fs::create_dir_all("/data/adb/lynx");
        let _ = fs::write(SAFETY_FILE, payload);
    }

    /// Marks the profile change as stable (called after 60s without panic).
    pub fn disarm() {
        if Path::new(SAFETY_FILE).exists() {
            let _ = fs::remove_file(SAFETY_FILE);
        }
    }

    /// Checks on daemon cold-boot whether an unclean reboot occurred while armed.
    /// Returns true if a rollback was triggered.
    pub fn check_on_boot() -> bool {
        if !Path::new(SAFETY_FILE).exists() {
            return false;
        }

        let content = match fs::read_to_string(SAFETY_FILE) {
            Ok(c) => c,
            Err(_) => return false,
        };

        let lines: Vec<&str> = content.lines().collect();
        if lines.len() < 3 {
            let _ = fs::remove_file(SAFETY_FILE);
            return false;
        }

        let armed_at: u64 = lines[0].trim().parse().unwrap_or(0);
        let profile = lines[1].trim().to_string();
        let completed = lines[2].trim() == "1";
        let now = Self::current_timestamp();

        if !completed || (armed_at > 0 && now.saturating_sub(armed_at) > WATCHDOG_TIMEOUT_SECS) {
            // Unclean reboot detected while armed!
            let log_msg = format!(
                "[{}] SAFETY CIRCUIT BREAKER TRIGGERED: Unclean reboot detected under profile '{}'. Rolling back to balanced.\n",
                now,
                profile
            );
            let _ = fs::write(ROLLBACK_LOG, log_msg);
            let _ = fs::remove_file(SAFETY_FILE);
            return true;
        }

        false
    }
}
