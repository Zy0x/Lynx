#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CooldownState {
    Idle,
    HoldingPerformance {
        started_at: u64,
        fallback_profile: &'static str,
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

    /// User entered or returned to a game: cancels any running cooldown timer immediately.
    pub fn on_game_active(&mut self) {
        self.state = CooldownState::Idle;
    }

    /// User left the game: begins the hysteresis buffer timer.
    pub fn on_game_exited(&mut self, now: u64, fallback_profile: &'static str) {
        if let CooldownState::Idle = self.state {
            self.state = CooldownState::HoldingPerformance {
                started_at: now,
                fallback_profile,
            };
        }
    }

    /// Evaluates the current cooldown state against the clock.
    pub fn evaluate(&mut self, now: u64) -> CooldownStatus {
        match self.state {
            CooldownState::Idle => CooldownStatus::Inactive,
            CooldownState::HoldingPerformance {
                started_at,
                fallback_profile,
            } => {
                let elapsed = now.saturating_sub(started_at);
                if elapsed >= self.buffer_duration_secs {
                    self.state = CooldownState::Idle;
                    CooldownStatus::Expired { fallback_profile }
                } else {
                    let remaining_secs = self.buffer_duration_secs - elapsed;
                    CooldownStatus::Holding { remaining_secs }
                }
            }
        }
    }

    pub fn is_holding(&self) -> bool {
        matches!(self.state, CooldownState::HoldingPerformance { .. })
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
        mgr.on_game_exited(100, "balance");
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

        mgr.on_game_exited(100, "balance");
        assert!(mgr.is_holding());

        // User returned to game at t=102
        mgr.on_game_active();
        assert!(!mgr.is_holding());
        assert_eq!(mgr.evaluate(102), CooldownStatus::Inactive);
    }
}
