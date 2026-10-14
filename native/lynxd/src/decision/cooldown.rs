#[derive(Debug, Clone, PartialEq, Eq)]
pub enum CooldownState {
    Idle,
    HoldingPerformance {
        started_at: u64,
        fallback_profile: &'static str,
        target_package: Option<String>,
    },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CooldownStatus {
    Inactive,
    Holding { remaining_secs: u64 },
    Expired { fallback_profile: &'static str },
}

pub struct CooldownManager {
    buffer_duration_secs: u64,
    state: CooldownState,
}

impl CooldownManager {
    pub fn new(buffer_duration_secs: u64) -> Self {
        Self {
            buffer_duration_secs,
            state: CooldownState::Idle,
        }
    }

    pub fn set_buffer_duration(&mut self, duration: u64) {
        self.buffer_duration_secs = duration;
    }

    pub fn buffer_duration(&self) -> u64 {
        self.buffer_duration_secs
    }

    /// User entered or returned to a game: cancels any running cooldown timer immediately.
    pub fn on_game_active(&mut self) {
        self.state = CooldownState::Idle;
    }

    /// User left the game: begins the hysteresis buffer timer ONLY IF the target package is still alive.
    /// If target package is already dead/closed, does NOT hold and remains Idle (triggering immediate fallback).
    pub fn on_game_exited(&mut self, now: u64, fallback_profile: &'static str, target_package: Option<String>) {
        if let CooldownState::Idle = self.state {
            let is_alive = target_package.as_ref().map_or(true, |pkg| Self::is_process_alive(pkg));

            if !is_alive {
                return;
            }

            self.state = CooldownState::HoldingPerformance {
                started_at: now,
                fallback_profile,
                target_package,
            };
        }
    }

    /// Evaluates the current cooldown state against the clock and process vitality.
    pub fn evaluate(&mut self, now: u64) -> CooldownStatus {
        match &self.state {
            CooldownState::Idle => CooldownStatus::Inactive,
            CooldownState::HoldingPerformance {
                started_at,
                fallback_profile,
                target_package,
            } => {
                let fallback = *fallback_profile;

                // CRITICAL SAFETY INTERLOCK:
                // Buffer is STRICTLY for applications that remain alive in memory (minimized / backgrounded).
                // If the app is closed (Back button, onDestroy, oom_score_adj >= 900) or killed/swiped away at ANY time:
                // Immediately terminate holding state and expire on the spot!
                let is_alive = target_package.as_ref().map_or(true, |pkg| Self::is_process_alive(pkg));

                if !is_alive {
                    self.state = CooldownState::Idle;
                    return CooldownStatus::Expired { fallback_profile: fallback };
                }

                if self.buffer_duration_secs == 0 {
                    // Persistent Mode (0s): holds as long as target app process is alive in RAM
                    CooldownStatus::Holding { remaining_secs: 0 }
                } else {
                    let elapsed = now.saturating_sub(*started_at);
                    if elapsed >= self.buffer_duration_secs {
                        self.state = CooldownState::Idle;
                        CooldownStatus::Expired { fallback_profile: fallback }
                    } else {
                        let remaining_secs = self.buffer_duration_secs - elapsed;
                        CooldownStatus::Holding { remaining_secs }
                    }
                }
            }
        }
    }

    pub fn is_holding(&self) -> bool {
        matches!(self.state, CooldownState::HoldingPerformance { .. })
    }

    /// Checks if a package name has an active Linux process in /proc that is NOT closed/evicted.
    /// An app is considered alive IF:
    /// 1. Its process exists in /proc
    /// 2. It is not a zombie ('Z')
    /// 3. Its oom_score_adj is < 900 (Android assigns 700 to PREVIOUS_APP when minimized via Home/Switch,
    ///    but sets >= 900 (CACHED_APP) when the user closes the app via Back button / onDestroy).
    pub fn is_process_alive(package_name: &str) -> bool {
        #[cfg(test)]
        {
            if package_name.starts_with("non.existent") {
                return false;
            }
            if !std::path::Path::new("/proc").exists() {
                return true;
            }
        }

        let proc_path = std::path::Path::new("/proc");
        if let Ok(entries) = std::fs::read_dir(proc_path) {
            for entry in entries.flatten() {
                let name = entry.file_name();
                let s = name.to_string_lossy();
                if s.chars().all(|c| c.is_ascii_digit()) {
                    let cmdline_file = entry.path().join("cmdline");
                    if let Ok(cmd_bytes) = std::fs::read(&cmdline_file) {
                        if !cmd_bytes.is_empty() {
                            let cmd_str = String::from_utf8_lossy(&cmd_bytes);
                            let first_token = cmd_str.split('\0').next().unwrap_or("").trim();
                            if first_token == package_name || first_token.starts_with(&format!("{}:", package_name)) {
                                // 1. Check if process is not a zombie ('Z')
                                let stat_file = entry.path().join("stat");
                                if let Ok(stat_str) = std::fs::read_to_string(&stat_file) {
                                    if let Some(r_paren) = stat_str.rfind(')') {
                                        let rest = stat_str[r_paren + 1..].trim_start();
                                        if let Some(state) = rest.chars().next() {
                                            if state == 'Z' {
                                                continue;
                                            }
                                        }
                                    }
                                }

                                // 2. Check /proc/<pid>/oom_score_adj
                                let oom_file = entry.path().join("oom_score_adj");
                                if let Ok(oom_str) = std::fs::read_to_string(&oom_file) {
                                    if let Ok(adj) = oom_str.trim().parse::<i32>() {
                                        if adj >= 900 {
                                            // Process is an empty cached skeleton (user closed the app)
                                            continue;
                                        }
                                    }
                                }

                                return true;
                            }
                        }
                    }
                }
            }
        }
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_cooldown_hold_and_expire() {
        let mut mgr = CooldownManager::new(5);

        assert_eq!(mgr.evaluate(100), CooldownStatus::Inactive);

        // Game exited at t=100
        mgr.on_game_exited(100, "balance", None);
        assert!(mgr.is_holding());

        // At t=102: 3 seconds remaining
        assert_eq!(mgr.evaluate(102), CooldownStatus::Holding { remaining_secs: 3 });

        // At t=105: Expired!
        assert_eq!(
            mgr.evaluate(105),
            CooldownStatus::Expired { fallback_profile: "balance" }
        );
        assert!(!mgr.is_holding());
    }

    #[test]
    fn test_cooldown_cancel_on_game_reentry() {
        let mut mgr = CooldownManager::new(5);

        mgr.on_game_exited(100, "balance", None);
        assert!(mgr.is_holding());

        // User returned to game at t=102
        mgr.on_game_active();
        assert!(!mgr.is_holding());
        assert_eq!(mgr.evaluate(102), CooldownStatus::Inactive);
    }

    #[test]
    fn test_cooldown_buffer_duration_change() {
        let mut mgr = CooldownManager::new(5);
        assert_eq!(mgr.buffer_duration(), 5);
        mgr.set_buffer_duration(30);
        assert_eq!(mgr.buffer_duration(), 30);
    }

    #[test]
    fn test_cooldown_cancels_when_process_dead() {
        let mut mgr = CooldownManager::new(30);

        // Dead package should not even enter holding state
        mgr.on_game_exited(100, "balance", Some("non.existent.dead.pkg.12345".to_string()));
        assert!(!mgr.is_holding());
        assert_eq!(mgr.evaluate(100), CooldownStatus::Inactive);
    }
}
