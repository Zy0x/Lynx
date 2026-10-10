use std::time::{SystemTime, UNIX_EPOCH};

use crate::daemon::foreground::rules::ForegroundApp;
use crate::daemon::screen::ScreenState;
use crate::state::DeviceState;

#[derive(Debug, Clone)]
pub struct DaemonState {
    pub screen: ScreenState,
    pub last_screen_change: u64,
    pub current_app: Option<ForegroundApp>,
    pub last_app_change: u64,
    pub last_telemetry: Option<DeviceState>,
    pub uptime_start: u64,
    pub cycle_count: u64,
    pub transition_count: u64,
}

impl DaemonState {
    pub fn new() -> Self {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        Self {
            screen: ScreenState::Unknown,
            last_screen_change: now,
            current_app: None,
            last_app_change: now,
            last_telemetry: None,
            uptime_start: now,
            cycle_count: 0,
            transition_count: 0,
        }
    }

    pub fn increment_transition(&mut self) -> u64 {
        self.transition_count += 1;
        self.transition_count
    }

    /// Updates screen state. Returns true if an actual state transition occurred.
    pub fn update_screen(&mut self, new_screen: ScreenState, now: u64) -> bool {
        if self.screen != new_screen {
            self.screen = new_screen;
            self.last_screen_change = now;
            true
        } else {
            false
        }
    }

    /// Updates foreground app. Returns true if an actual package transition occurred.
    pub fn update_app(&mut self, new_app: Option<ForegroundApp>, now: u64) -> bool {
        let has_changed = match (&self.current_app, &new_app) {
            (Some(cur), Some(nxt)) => cur.package != nxt.package,
            (None, Some(_)) => true,
            (Some(_), None) => true,
            (None, None) => false,
        };

        if has_changed {
            self.current_app = new_app;
            self.last_app_change = now;
            true
        } else {
            false
        }
    }

    pub fn update_telemetry(&mut self, telemetry: DeviceState) {
        self.last_telemetry = Some(telemetry);
    }

    pub fn screen_duration_secs(&self, now: u64) -> u64 {
        now.saturating_sub(self.last_screen_change)
    }

    pub fn app_duration_secs(&self, now: u64) -> u64 {
        now.saturating_sub(self.last_app_change)
    }

    pub fn uptime_secs(&self, now: u64) -> u64 {
        now.saturating_sub(self.uptime_start)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::daemon::foreground::rules::{AppCategory, Confidence};

    #[test]
    fn test_daemon_state_screen_transition() {
        let mut state = DaemonState::new();
        assert_eq!(state.screen, ScreenState::Unknown);

        let changed = state.update_screen(ScreenState::On, 100);
        assert!(changed);
        assert_eq!(state.screen, ScreenState::On);
        assert_eq!(state.last_screen_change, 100);

        let changed_again = state.update_screen(ScreenState::On, 110);
        assert!(!changed_again);
        assert_eq!(state.last_screen_change, 100);
    }

    #[test]
    fn test_daemon_state_app_transition() {
        let mut state = DaemonState::new();
        assert!(state.current_app.is_none());

        let app1 = ForegroundApp {
            package: "com.tencent.ig".to_string(),
            category: AppCategory::Game,
            confidence: Confidence::High,
        };

        let changed = state.update_app(Some(app1.clone()), 200);
        assert!(changed);
        assert_eq!(state.current_app.as_ref().unwrap().package, "com.tencent.ig");

        let app2 = ForegroundApp {
            package: "com.spotify.music".to_string(),
            category: AppCategory::Media,
            confidence: Confidence::High,
        };

        let changed2 = state.update_app(Some(app2), 250);
        assert!(changed2);
        assert_eq!(state.current_app.as_ref().unwrap().package, "com.spotify.music");
        assert_eq!(state.app_duration_secs(270), 20);
    }
}
