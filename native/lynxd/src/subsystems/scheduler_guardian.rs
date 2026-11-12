#[cfg(unix)]
use std::fs;
use std::path::Path;
#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;

use crate::hardware::MtkHardware;
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};
use crate::utils::Logger;
use crate::subsystems::tui::ui;

/// Autonomous Guardian for CPU Scheduler, EAS Schedtune & Vendor Boost Priority.
/// Prevents Android framework (OomAdjuster / WindowManager) and vendor daemons (MTK perfservice)
/// from wiping top-app boost or inverting priority when notifications / background events occur.
pub struct SchedulerGuardian;

impl SchedulerGuardian {
    pub const STUNE_TOP_BOOST: &'static str = "/dev/stune/top-app/schedtune.boost";
    pub const STUNE_TOP_IDLE: &'static str = "/dev/stune/top-app/schedtune.prefer_idle";
    pub const STUNE_TOP_HIGH_CAP: &'static str = "/dev/stune/top-app/schedtune.prefer_high_cap";
    pub const STUNE_FG_BOOST: &'static str = "/dev/stune/foreground/schedtune.boost";

    pub const PERFMGR_TA_BOOST: &'static str = MtkHardware::PERFMGR_TA_BOOST;
    pub const PERFMGR_FG_BOOST: &'static str = MtkHardware::PERFMGR_FG_BOOST;
    pub const PERFMGR_UCLAMP_MIN: &'static str = MtkHardware::PERFMGR_UCLAMP_MIN;
    pub const PERFMGR_TA_UCLAMP_MIN: &'static str = MtkHardware::PERFMGR_TA_UCLAMP_MIN;

    /// Sets node permission safely on Unix.
    pub fn set_node_mode(_path_str: &str, _mode: u32) {
        #[cfg(unix)]
        {
            let p = Path::new(_path_str);
            if p.exists() {
                let perm = fs::Permissions::from_mode(_mode);
                let _ = fs::set_permissions(p, perm);
            }
        }
    }

    /// Enforces permission lock or restoration depending on whether the active profile is high-performance/extreme.
    ///
    /// When lock == true (Performance / Extreme):
    ///   - /dev/stune/top-app/schedtune.boost -> 0o644 (owned by root, un-overwritable by system_server UID 1000)
    ///   - /dev/stune/top-app/schedtune.prefer_idle -> 0o644
    ///   - /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost -> 0o444 (read-only, un-overwritable by MTK perfservice)
    ///   - /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_uclamp_min -> 0o444
    ///
    /// When lock == false (Balance / Powersave):
    ///   - /dev/stune/top-app/schedtune.boost -> 0o666 (world-writable default)
    ///   - /dev/stune/top-app/schedtune.prefer_idle -> 0o666
    ///   - /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_boost -> 0o664 (system-writable default)
    ///   - /proc/perfmgr/boost_ctrl/eas_ctrl/perfserv_ta_uclamp_min -> 0o664
    pub fn enforce_permission_shield(lock: bool) {
        if lock {
            Self::set_node_mode(Self::STUNE_TOP_BOOST, 0o644);
            Self::set_node_mode(Self::STUNE_TOP_IDLE, 0o644);
            Self::set_node_mode(Self::STUNE_TOP_HIGH_CAP, 0o644);
            Self::set_node_mode(Self::PERFMGR_TA_BOOST, 0o444);
            Self::set_node_mode(Self::PERFMGR_TA_UCLAMP_MIN, 0o444);
        } else {
            Self::set_node_mode(Self::STUNE_TOP_BOOST, 0o666);
            Self::set_node_mode(Self::STUNE_TOP_IDLE, 0o666);
            Self::set_node_mode(Self::STUNE_TOP_HIGH_CAP, 0o666);
            Self::set_node_mode(Self::PERFMGR_TA_BOOST, 0o664);
            Self::set_node_mode(Self::PERFMGR_TA_UCLAMP_MIN, 0o664);
        }
    }

    /// Periodic guardian check executed in daemon active loop.
    /// Protects against notification priority inversion, heads-up window degradation, and rogue framework resets.
    /// Derives scheduler protection targets dynamically from the device hardware tier
    /// so `SchedulerGuardian` stays aligned with `ClusterController` and never fights baseline floors.
    /// Returns `(target_stune_boost, target_ta_boost, target_fg_boost, enforce_high_cap, threshold)`.
    pub(crate) fn resolve_tick_policy(
        active_profile: &str,
        tier: &crate::hardware::DeviceTier,
    ) -> Option<(i32, i32, i32, bool, i64)> {
        if matches!(active_profile, "powersave" | "dormant") {
            return None;
        }
        let (top_str, _, fg_str) = tier.stune_targets(active_profile);
        let top_boost: i32 = top_str.parse().unwrap_or(20);
        let fg_boost: i32 = fg_str.parse().unwrap_or(15);
        let enforce_high_cap = match active_profile {
            "extreme" => true,
            "performance" => !tier.is_low_end_ram,
            _ => false,
        };
        let target_ta_boost = match active_profile {
            "extreme" => 100,
            "performance" => if tier.is_low_end_ram { 45 } else { 60 },
            _ => 10,
        };
        let threshold = ((top_boost as i64) * 3) / 4;
        Some((top_boost, target_ta_boost, fg_boost, enforce_high_cap, threshold))
    }

    pub fn tick(active_profile: &str, screen_on: bool) {
        if !screen_on {
            return;
        }

        let tier = crate::hardware::DeviceTier::cached_live();
        let Some((target_stune_boost, target_ta_boost, target_fg_boost, enforce_high_cap, threshold)) =
            Self::resolve_tick_policy(active_profile, &tier)
        else {
            return; // Powersave/dormant allows OS defaults
        };

        let is_locked_mode = matches!(active_profile, "performance" | "extreme");
        let touch_active = crate::subsystems::ClusterController::is_touch_boosted();
        let effective_stune_target = if touch_active {
            target_stune_boost.max(25)
        } else {
            target_stune_boost
        };

        // 1. Check & Protect Schedtune Top-App Boost & High-Cap
        if Path::new(Self::STUNE_TOP_BOOST).exists() {
            if let Ok(cur_val) = SysfsReader::read_int::<i64>(Self::STUNE_TOP_BOOST) {
                // If wiped out below safe threshold (e.g. 1 or 0 by OomAdjuster on app switch / notifications)
                if cur_val < threshold {
                    if is_locked_mode {
                        Self::set_node_mode(Self::STUNE_TOP_BOOST, 0o666);
                    }
                    let _ = SysfsWriter::write(
                        Path::new(Self::STUNE_TOP_BOOST),
                        &effective_stune_target.to_string(),
                        WriteMode::ForcePermission,
                    );
                    if is_locked_mode {
                        Self::set_node_mode(Self::STUNE_TOP_BOOST, 0o644);
                    }

                    // Protect prefer_high_cap for flagship/extreme or active touch gestures
                    if (enforce_high_cap || touch_active) && Path::new(Self::STUNE_TOP_HIGH_CAP).exists() {
                        let cur_cap = SysfsReader::read_int::<i64>(Self::STUNE_TOP_HIGH_CAP).unwrap_or(0);
                        if cur_cap == 0 {
                            if is_locked_mode {
                                Self::set_node_mode(Self::STUNE_TOP_HIGH_CAP, 0o666);
                            }
                            let _ = SysfsWriter::write(
                                Path::new(Self::STUNE_TOP_HIGH_CAP),
                                "1",
                                WriteMode::ForcePermission,
                            );
                            if is_locked_mode {
                                Self::set_node_mode(Self::STUNE_TOP_HIGH_CAP, 0o644);
                            }
                        }
                    }

                    // Always ensure prefer_idle is 1 for instant core wake
                    if Path::new(Self::STUNE_TOP_IDLE).exists() {
                        let cur_idle = SysfsReader::read_int::<i64>(Self::STUNE_TOP_IDLE).unwrap_or(0);
                        if cur_idle == 0 {
                            let _ = SysfsWriter::write(
                                Path::new(Self::STUNE_TOP_IDLE),
                                "1",
                                WriteMode::ForcePermission,
                            );
                        }
                    }

                    Logger::info(ui(
                        &format!(
                            "[SCHED-GUARDIAN] Terdeteksi penurunan boost top-app ({} -> {}). Mengunci kembali EAS margin!",
                            cur_val, effective_stune_target
                        ),
                        &format!(
                            "[SCHED-GUARDIAN] Detected top-app schedtune boost degradation ({} -> {}). Re-asserting EAS margin!",
                            cur_val, effective_stune_target
                        ),
                    ));
                }
            }
        }

        // 2. Check & Protect MediaTek Perfmgr EAS Priority
        if Path::new(Self::PERFMGR_TA_BOOST).exists() {
            let cur_ta = SysfsReader::read_int::<i64>(Self::PERFMGR_TA_BOOST).unwrap_or(0);
            let cur_fg = SysfsReader::read_int::<i64>(Self::PERFMGR_FG_BOOST).unwrap_or(0);

            // Priority inversion condition: ta_boost wiped to 0 or fg_boost > ta_boost
            if cur_ta < (target_ta_boost - 15) as i64 || (cur_fg > cur_ta && cur_fg > 30) {
                Self::set_node_mode(Self::PERFMGR_TA_BOOST, 0o664);
                let _ = SysfsWriter::write(
                    Path::new(Self::PERFMGR_TA_BOOST),
                    &target_ta_boost.to_string(),
                    WriteMode::ForcePermission,
                );
                Self::set_node_mode(Self::PERFMGR_TA_BOOST, 0o444);

                if cur_fg > target_fg_boost as i64 {
                    Self::set_node_mode(Self::PERFMGR_FG_BOOST, 0o664);
                    let _ = SysfsWriter::write(
                        Path::new(Self::PERFMGR_FG_BOOST),
                        &target_fg_boost.to_string(),
                        WriteMode::ForcePermission,
                    );
                }

                Logger::info(ui(
                    &format!(
                        "[SCHED-GUARDIAN] Terdeteksi inversi prioritas MediaTek EAS (TA: {}, FG: {}). Memulihkan prioritas game (TA: {})!",
                        cur_ta, cur_fg, target_ta_boost
                    ),
                    &format!(
                        "[SCHED-GUARDIAN] Detected MediaTek EAS priority inversion (TA: {}, FG: {}). Restored game priority (TA: {})!",
                        cur_ta, cur_fg, target_ta_boost
                    ),
                ));
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::hardware::{BlockDeviceType, DeviceTier};

    #[test]
    fn guardian_policy_aligns_with_device_tier_without_tug_of_war() {
        let low_end = DeviceTier::classify(3663, Some(BlockDeviceType::Emmc), false, false);
        let flagship = DeviceTier::classify(8192, Some(BlockDeviceType::Ufs), false, false);

        for tier in [&low_end, &flagship] {
            for profile in ["balance", "performance", "extreme"] {
                let (top_boost, _, fg_boost, enforce_high_cap, threshold) =
                    SchedulerGuardian::resolve_tick_policy(profile, tier)
                        .expect("non-powersave profile must resolve policy");
                let (tier_top, _, tier_fg) = tier.stune_targets(profile);
                assert_eq!(top_boost.to_string(), tier_top);
                assert_eq!(fg_boost.to_string(), tier_fg);
                assert!((top_boost as i64) >= threshold);

                if profile == "performance" && tier.is_low_end_ram {
                    assert!(!enforce_high_cap);
                }
            }
        }

        assert!(SchedulerGuardian::resolve_tick_policy("powersave", &low_end).is_none());
        assert!(SchedulerGuardian::resolve_tick_policy("dormant", &flagship).is_none());
    }
}
