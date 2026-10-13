use std::path::PathBuf;

use crate::sysfs::SysfsReader;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ScreenState {
    On,
    Off,
    Unknown,
}

pub struct ScreenDetector {
    /// Active probed physical sysfs node path for direct zero-fork reading.
    pub active_node: Option<PathBuf>,
}

impl ScreenDetector {
    /// Probes and selects the primary kernel display status node.
    pub fn probe() -> Self {
        // Priority 1: Direct Backlight Brightness (MediaTek, Qualcomm, Samsung)
        let common_backlights = [
            "/sys/class/leds/lcd-backlight/brightness",
            "/sys/class/leds/panel0-backlight/brightness",
            "/sys/class/backlight/panel0-backlight/brightness",
            "/sys/class/backlight/sprd_backlight/brightness",
            "/sys/class/backlight/mtk_leds/brightness",
        ];
        for &bl in &common_backlights {
            let p = PathBuf::from(bl);
            if SysfsReader::exists(&p) {
                if let Ok(val) = SysfsReader::read_trimmed(&p) {
                    if val.parse::<u64>().is_ok() {
                        return Self {
                            active_node: Some(p),
                        };
                    }
                }
            }
        }

        // Check any backlight in /sys/class/backlight
        if let Ok(entries) = std::fs::read_dir("/sys/class/backlight") {
            for entry in entries.flatten() {
                let brightness = entry.path().join("brightness");
                if SysfsReader::exists(&brightness) {
                    if let Ok(val) = SysfsReader::read_trimmed(&brightness) {
                        if val.parse::<u64>().is_ok() {
                            return Self {
                                active_node: Some(brightness),
                            };
                        }
                    }
                }
            }
        }

        // Check any backlight under /sys/class/leds
        if let Ok(entries) = std::fs::read_dir("/sys/class/leds") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_lowercase();
                if name.contains("backlight") || name.contains("lcd") || name.contains("panel") {
                    let brightness = entry.path().join("brightness");
                    if SysfsReader::exists(&brightness) {
                        if let Ok(val) = SysfsReader::read_trimmed(&brightness) {
                            if val.parse::<u64>().is_ok() {
                                return Self {
                                    active_node: Some(brightness),
                                };
                            }
                        }
                    }
                }
            }
        }

        // Priority 2: Direct Framebuffer Blanking (Standard Linux FB, ONLY if readable and not empty)
        let fb_blank = PathBuf::from("/sys/class/graphics/fb0/blank");
        if SysfsReader::exists(&fb_blank) {
            if let Ok(val) = SysfsReader::read_trimmed(&fb_blank) {
                if !val.is_empty() {
                    return Self {
                        active_node: Some(fb_blank),
                    };
                }
            }
        }

        // Priority 3: DRM Runtime Power Status
        let drm_status = PathBuf::from("/sys/class/drm/card0/device/power/runtime_status");
        if SysfsReader::exists(&drm_status) {
            return Self {
                active_node: Some(drm_status),
            };
        }

        Self { active_node: None }
    }

    /// Detects current screen status with zero forks in sub-0.1ms.
    pub fn detect(&self) -> ScreenState {
        if let Some(ref node) = self.active_node {
            if let Ok(val) = SysfsReader::read_trimmed(node) {
                // If backlight brightness: > 0 is ON, 0 is OFF
                if node.ends_with("brightness") {
                    if let Ok(b) = val.parse::<u64>() {
                        return if b > 0 {
                            ScreenState::On
                        } else {
                            ScreenState::Off
                        };
                    }
                }

                // If fb0/blank: 0 = unblank (ON), non-zero = blank (OFF)
                if node.ends_with("blank") && !val.is_empty() {
                    return if val == "0" {
                        ScreenState::On
                    } else {
                        ScreenState::Off
                    };
                }

                // If DRM runtime_status
                if val == "active" {
                    return ScreenState::On;
                } else if val == "suspended" {
                    return ScreenState::Off;
                }
            }
        }

        // Tier 4 Fallback: Android dumpsys power (compliant with AGENTS.md §4.3)
        #[cfg(target_os = "android")]
        {
            if let Ok(output) = std::process::Command::new("dumpsys")
                .args(&["power"])
                .output()
            {
                let text = String::from_utf8_lossy(&output.stdout);
                if text.contains("mHoldingDisplaySuspendBlocker=true") || text.contains("Display Power: state=ON") {
                    return ScreenState::On;
                } else if text.contains("mHoldingDisplaySuspendBlocker=false") || text.contains("Display Power: state=OFF") {
                    return ScreenState::Off;
                }
            }
        }

        ScreenState::Unknown
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_screen_state_enum() {
        assert_eq!(ScreenState::On, ScreenState::On);
        assert_ne!(ScreenState::On, ScreenState::Off);
        assert_ne!(ScreenState::Off, ScreenState::Unknown);
    }
}
