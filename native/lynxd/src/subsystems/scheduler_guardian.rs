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
    pub fn set_node_mode(path_str: &str, mode: u32) {
        #[cfg(unix)]
        {
            let p = Path::new(path_str);
            if p.exists() {
                let perm = fs::Permissions::from_mode(mode);
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
    pub fn tick(active_profile: &str, screen_on: bool) {
        if !screen_on || !matches!(active_profile, "performance" | "extreme") {
            return;
        }

        let is_extreme = active_profile == "extreme";
        let target_stune_boost = if is_extreme { 50 } else { 35 };
        let target_ta_boost = if is_extreme { 100 } else { 60 };
        let target_fg_boost = if is_extreme { 50 } else { 35 };

        // 1. Check & Protect Schedtune Top-App Boost
        if Path::new(Self::STUNE_TOP_BOOST).exists() {
            if let Ok(cur_val) = SysfsReader::read_int::<i64>(Self::STUNE_TOP_BOOST) {
                // If wiped out below safe gaming threshold (e.g. 0 by OomAdjuster on heads-up notification)
                if cur_val < (target_stune_boost - 10) as i64 {
                    // Unlock temporarily to write, write, then re-lock
                    Self::set_node_mode(Self::STUNE_TOP_BOOST, 0o666);
                    let _ = SysfsWriter::write(
                        Path::new(Self::STUNE_TOP_BOOST),
                        &target_stune_boost.to_string(),
                        WriteMode::ForcePermission,
                    );
                    Self::set_node_mode(Self::STUNE_TOP_BOOST, 0o644);

                    Logger::info(ui(
                        format!(
                            "[SCHED-GUARDIAN] Terdeteksi penurunan boost top-app ({} -> {}). Mengunci kembali EAS margin!",
                            cur_val, target_stune_boost
                        ),
                        format!(
                            "[SCHED-GUARDIAN] Detected top-app schedtune boost degradation ({} -> {}). Re-asserting EAS margin!",
                            cur_val, target_stune_boost
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
                    format!(
                        "[SCHED-GUARDIAN] Terdeteksi inversi prioritas MediaTek EAS (TA: {}, FG: {}). Memulihkan prioritas game (TA: {})!",
                        cur_ta, cur_fg, target_ta_boost
                    ),
                    format!(
                        "[SCHED-GUARDIAN] Detected MediaTek EAS priority inversion (TA: {}, FG: {}). Restored game priority (TA: {})!",
                        cur_ta, cur_fg, target_ta_boost
                    ),
                ));
            }
        }
    }
}
