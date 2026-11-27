use crate::utils::json::quote;
use std::path::Path;

use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

pub struct CpuIdleController;

impl CpuIdleController {
    fn write_opt(path: impl AsRef<Path>, val: impl AsRef<str>) -> bool {
        let p = path.as_ref();
        if p.exists() {
            SysfsWriter::write(p, val, WriteMode::ForcePermission).is_ok()
        } else {
            false
        }
    }

    fn read_trimmed(path: impl AsRef<Path>) -> Option<String> {
        SysfsReader::read_trimmed(path.as_ref())
            .ok()
            .filter(|s| !s.is_empty())
    }

    /// Dynamically parses kernel procfs help text (e.g. `0: on  1: gating  2: off`) so mode codes
    /// are never hardcoded blindly across differing SoC/kernel driver revisions.
    fn parse_control_mode_code(help_text: &str, wanted_keywords: &[&str]) -> Option<String> {
        let lower = help_text.to_ascii_lowercase();
        for kw in wanted_keywords {
            let needle = kw.to_ascii_lowercase();
            let mut search_from = 0usize;
            while let Some(pos) = lower[search_from..].find(&needle) {
                let abs_pos = search_from + pos;
                let prefix = lower[..abs_pos].trim_end();
                if let Some(before_colon) = prefix.strip_suffix(':') {
                    let digits: String = before_colon
                        .trim_end()
                        .chars()
                        .rev()
                        .take_while(|c| c.is_ascii_digit())
                        .collect::<String>()
                        .chars()
                        .rev()
                        .collect();
                    if !digits.is_empty() {
                        return Some(digits);
                    }
                }
                search_from = abs_pos + needle.len();
            }
        }
        None
    }

    fn write_control_mode(path: &str, wanted_keywords: &[&str], fallback: &str) {
        if !Path::new(path).exists() {
            return;
        }
        let code = Self::read_trimmed(path)
            .and_then(|txt| Self::parse_control_mode_code(&txt, wanted_keywords))
            .unwrap_or_else(|| fallback.to_string());
        Self::write_opt(path, &code);
    }

    /// Emits JSON report of CPU idle states across all CPU cores.
    pub fn info_json() -> String {
        let mut states_json = Vec::new();

        for s_idx in 0..10 {
            // Check cpu0 as representative archetype
            let state_dir = format!("/sys/devices/system/cpu/cpu0/cpuidle/state{}", s_idx);
            if !Path::new(&state_dir).exists() {
                continue;
            }

            let name = Self::read_trimmed(format!("{}/name", state_dir))
                .unwrap_or_else(|| format!("state{}", s_idx));
            let desc = Self::read_trimmed(format!("{}/desc", state_dir)).unwrap_or_default();
            let latency = Self::read_trimmed(format!("{}/latency", state_dir))
                .and_then(|v| v.parse::<u64>().ok())
                .unwrap_or(0);
            let disabled = Self::read_trimmed(format!("{}/disable", state_dir))
                .map(|v| v == "1")
                .unwrap_or(false);

            states_json.push(format!(
                "{{\"index\":{},\"name\":{},\"desc\":{},\"latency_us\":{},\"disabled\":{}}}",
                s_idx,
                quote(&name),
                quote(&desc),
                latency,
                disabled
            ));
        }

        let armpll = Self::read_trimmed("/proc/cpuidle/control/armpll_mode")
            .unwrap_or_else(|| "N/A".to_string());
        let cstate_aware = Self::read_trimmed("/proc/sys/kernel/sched_cstate_aware")
            .unwrap_or_else(|| "N/A".to_string());
        let sleep_disabled = Self::read_trimmed("/sys/module/lpm_levels/parameters/sleep_disabled")
            .unwrap_or_else(|| "N/A".to_string());

        format!(
            "{{\"states\":[{}],\"armpll_mode\":{},\"sched_cstate_aware\":{},\"sleep_disabled\":{}}}",
            states_json.join(","),
            quote(&armpll),
            quote(&cstate_aware),
            quote(&sleep_disabled)
        )
    }

    /// Applies CPU idle C-state tuning preset.
    pub fn apply_preset(preset: &str) -> String {
        let p = preset.trim().to_lowercase();
        match p.as_str() {
            "gaming" | "latency" => {
                // Zero Latency: Disable deeper states (2..9) across all CPUs
                for s in 2..10 {
                    for cpu in 0..32 {
                        let path = format!(
                            "/sys/devices/system/cpu/cpu{}/cpuidle/state{}/disable",
                            cpu, s
                        );
                        Self::write_opt(&path, "1");
                    }
                }

                // Shallow states (0: WFI, 1: cpuoff/ret) kept enabled
                for s in 0..2 {
                    for cpu in 0..32 {
                        let path = format!(
                            "/sys/devices/system/cpu/cpu{}/cpuidle/state{}/disable",
                            cpu, s
                        );
                        Self::write_opt(&path, "0");
                    }
                }

                Self::write_control_mode("/proc/cpuidle/control/armpll_mode", &["on"], "0");
                Self::write_control_mode("/proc/cpuidle/control/buck_mode", &["on"], "0");
                Self::write_opt("/proc/sys/kernel/sched_cstate_aware", "0");
                Self::write_opt("/sys/module/lpm_levels/parameters/sleep_disabled", "1");

                // Unpark all cores
                for cpu in 0..32 {
                    let online_path = format!("/sys/devices/system/cpu/cpu{}/online", cpu);
                    Self::write_opt(&online_path, "1");
                }

                "CpuIdle Latency preset applied (Deep C-states 2-9 disabled, unparked all cores)."
                    .to_string()
            }

            "battery" | "powersave" => {
                // Enable all C-states across all CPUs
                for s in 0..10 {
                    for cpu in 0..32 {
                        let path = format!(
                            "/sys/devices/system/cpu/cpu{}/cpuidle/state{}/disable",
                            cpu, s
                        );
                        Self::write_opt(&path, "0");
                    }
                }

                Self::write_control_mode("/proc/cpuidle/control/armpll_mode", &["off", "gating"], "2");
                Self::write_control_mode("/proc/cpuidle/control/buck_mode", &["off", "low power"], "2");
                Self::write_opt("/proc/sys/kernel/sched_cstate_aware", "1");
                Self::write_opt("/sys/module/lpm_levels/parameters/sleep_disabled", "0");

                "CpuIdle Battery preset applied (Full deep sleep C-states 0-9 + ARMPLL/Buck power-down enabled).".to_string()
            }

            "balanced" | "default" => {
                // Enable all C-states across all CPUs
                for s in 0..10 {
                    for cpu in 0..32 {
                        let path = format!(
                            "/sys/devices/system/cpu/cpu{}/cpuidle/state{}/disable",
                            cpu, s
                        );
                        Self::write_opt(&path, "0");
                    }
                }

                // Always restore ARMPLL & Vproc buck idle gating after exiting gaming/extreme mode
                Self::write_control_mode("/proc/cpuidle/control/armpll_mode", &["off", "gating"], "2");
                Self::write_control_mode("/proc/cpuidle/control/buck_mode", &["off", "low power"], "2");
                Self::write_opt("/proc/sys/kernel/sched_cstate_aware", "1");
                Self::write_opt("/sys/module/lpm_levels/parameters/sleep_disabled", "0");

                // Unpark all cores
                for cpu in 0..32 {
                    let online_path = format!("/sys/devices/system/cpu/cpu{}/online", cpu);
                    Self::write_opt(&online_path, "1");
                }

                "CpuIdle Balanced preset applied (All C-states + ARMPLL/Buck gating active, all cores unparked)."
                    .to_string()
            }

            unknown => format!(
                "Error: Unknown cpuidle preset '{}'. Valid: latency, battery, balanced.",
                unknown
            ),
        }
    }

    /// Sets explicit disable flag (1: disabled, 0: enabled) for a specific C-state index across all CPUs.
    pub fn set_state(state_idx: usize, disabled: bool) -> String {
        let val = if disabled { "1" } else { "0" };
        let mut count = 0;
        for cpu in 0..32 {
            let path = format!(
                "/sys/devices/system/cpu/cpu{}/cpuidle/state{}/disable",
                cpu, state_idx
            );
            if Self::write_opt(&path, val) {
                count += 1;
            }
        }
        format!(
            "C-state {} updated to {} across {} CPU cores.",
            state_idx,
            if disabled { "DISABLED" } else { "ENABLED" },
            count
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_kernel_cpuidle_control_modes_dynamically() {
        let armpll_help = "armpll mode : on  ======== Command Usage ======== echo [mode] > /proc/cpuidle/control/armpll_mode mode: \t0: on \t1: gating \t2: off";
        let buck_help = "Vproc/Vproc_sram buck mode : off  ======== Command Usage ======== echo [mode] > /proc/cpuidle/control/buck_mode mode: \t0: on \t1: low power \t2: off";

        assert_eq!(CpuIdleController::parse_control_mode_code(armpll_help, &["off", "gating"]).as_deref(), Some("2"));
        assert_eq!(CpuIdleController::parse_control_mode_code(armpll_help, &["gating"]).as_deref(), Some("1"));
        assert_eq!(CpuIdleController::parse_control_mode_code(armpll_help, &["on"]).as_deref(), Some("0"));
        assert_eq!(CpuIdleController::parse_control_mode_code(buck_help, &["off", "low power"]).as_deref(), Some("2"));
        assert_eq!(CpuIdleController::parse_control_mode_code(buck_help, &["low power"]).as_deref(), Some("1"));
    }
}
