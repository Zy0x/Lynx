use std::fmt::Write as FmtWrite;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::OnceLock;
use std::time::{SystemTime, UNIX_EPOCH};

#[cfg(unix)]
use std::fs;
#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;

use crate::hardware::capability::{HardwareCapability, PolicyCapability};
use crate::hardware::DeviceTier;
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

static LAST_CPU_TOUCH_BOOST_MS: AtomicU64 = AtomicU64::new(0);
static IS_CPU_TOUCH_BOOSTED: AtomicBool = AtomicBool::new(false);

#[derive(Clone)]
struct CachedTouchCluster {
    min_path: PathBuf,
    max_path: PathBuf,
    max_freq: u64,
    touch_min_str: String,
    is_big_cluster: bool,
}

static TOUCH_CLUSTERS_CACHE: OnceLock<Vec<CachedTouchCluster>> = OnceLock::new();

pub struct ClusterController;

/// Selects an OPP frequency from sorted `available_freqs` at fractional quantile `(num / den)`,
/// falling back to linear interpolation between `min_freq..max_freq`. Zero hardcoded SoC kHz values.
pub(crate) fn pick_opp_quantile_freq(
    available_freqs: &[u64],
    min_freq: u64,
    max_freq: u64,
    num: usize,
    den: usize,
) -> u64 {
    if !available_freqs.is_empty() {
        let idx = ((available_freqs.len().saturating_sub(1)) * num) / den.max(1);
        return available_freqs[idx];
    }
    let span = max_freq.saturating_sub(min_freq);
    min_freq + (span.saturating_mul(num as u64)) / (den.max(1) as u64)
}

/// Computes dynamic CPU touch-hold window based on hardware tier & live touch motion continuity.
pub(crate) fn touch_decay_window_ms(tier: &DeviceTier, actively_moving: bool) -> u64 {
    if actively_moving {
        650
    } else if tier.is_low_end_ram || tier.is_emmc_primary {
        450
    } else {
        600
    }
}

impl ClusterController {
    /// Exposes current touch-boost window state so `SchedulerGuardian` yields cleanly during swipes.
    pub fn is_touch_boosted() -> bool {
        IS_CPU_TOUCH_BOOSTED.load(Ordering::Relaxed)
    }

    fn cached_touch_clusters() -> &'static [CachedTouchCluster] {
        TOUCH_CLUSTERS_CACHE
            .get_or_init(|| {
                let cpu = HardwareCapability::resolve_cpu();
                let tier = DeviceTier::cached_live();
                let is_single_cluster = cpu.policies.len() <= 1;

                cpu.policies
                    .into_iter()
                    .map(|pol: PolicyCapability| {
                        let is_big = !is_single_cluster
                            && (pol.index > 0
                                || pol.role.contains("Big")
                                || pol.role.contains("Performa")
                                || pol.role.contains("Prime"));
                        let (q_num, q_den) = if !is_big {
                            if is_single_cluster { (22, 100) } else { (15, 100) }
                        } else if tier.is_low_end_ram || tier.is_emmc_primary {
                            (30, 100)
                        } else {
                            (45, 100)
                        };
                        let touch_min = pick_opp_quantile_freq(
                            &pol.available_freqs,
                            pol.min_freq,
                            pol.max_freq,
                            q_num,
                            q_den,
                        );
                        let min_path = pol.path.join("scaling_min_freq");
                        let max_path = pol.path.join("scaling_max_freq");
                        CachedTouchCluster {
                            min_path,
                            max_path,
                            max_freq: pol.max_freq,
                            touch_min_str: touch_min.to_string(),
                            is_big_cluster: is_big || is_single_cluster,
                        }
                    })
                    .collect()
            })
            .as_slice()
    }

    fn set_perm(path: &Path, mode: u32) {
        #[cfg(unix)]
        {
            let _ = fs::set_permissions(path, fs::Permissions::from_mode(mode));
        }
        #[cfg(not(unix))]
        {
            let _ = (path, mode);
        }
    }

    /// Emits the exact JSON schema expected by LynxRepository & WebUI for CPU cluster topology.
    pub fn topology_json() -> String {
        let cpu = HardwareCapability::resolve_cpu();
        let mut out = String::from("{\"clusters\":[");

        for (idx, pol) in cpu.policies.iter().enumerate() {
            if idx > 0 {
                out.push(',');
            }
            let freqs_str: Vec<String> = pol.available_freqs.iter().map(|f| f.to_string()).collect();
            let govs_str: Vec<String> = pol.available_governors.iter().map(|g| format!("\"{}\"", g)).collect();

            let _ = write!(
                out,
                "{{\"id\":{},\"role\":\"{}\",\"cpus\":\"{}\",\"cur_min\":{},\"cur_max\":{},\"cur_gov\":\"{}\",\"avail_freqs\":[{}],\"avail_govs\":[{}],\"is_locked\":{}}}",
                pol.index,
                pol.role,
                pol.related_cpus,
                pol.current_min_freq,
                pol.current_max_freq,
                pol.current_governor,
                freqs_str.join(","),
                govs_str.join(","),
                pol.is_locked
            );
        }

        out.push_str("]}");
        out
    }

    pub fn set_freq(policy_id: usize, min_khz: Option<u64>, max_khz: Option<u64>) -> Result<String, String> {
        let cpu = HardwareCapability::resolve_cpu();
        let pol = cpu
            .policies
            .iter()
            .find(|p| p.index == policy_id)
            .ok_or_else(|| format!("Error: Policy policy{} does not exist.", policy_id))?;

        let mut target_min = min_khz.unwrap_or(pol.current_min_freq).max(pol.min_freq);
        let mut target_max = max_khz.unwrap_or(pol.current_max_freq).min(pol.max_freq);
        if target_min > target_max {
            target_max = target_min;
        }

        // Snap to closest valid OPP frequency if table is present
        if !pol.available_freqs.is_empty() {
            target_min = pol
                .available_freqs
                .iter()
                .copied()
                .filter(|&f| f >= target_min)
                .min()
                .unwrap_or(pol.max_freq);
            target_max = pol
                .available_freqs
                .iter()
                .copied()
                .filter(|&f| f <= target_max)
                .max()
                .unwrap_or(pol.min_freq);
            if target_min > target_max {
                target_min = target_max;
            }
        }

        let min_path = pol.path.join("scaling_min_freq");
        let max_path = pol.path.join("scaling_max_freq");
        let was_locked = pol.is_locked;

        Self::set_perm(&min_path, 0o644);
        Self::set_perm(&max_path, 0o644);

        // Raise ceiling first to avoid kernel clamping floor
        let _ = SysfsWriter::write(&max_path, pol.max_freq.to_string(), WriteMode::ForcePermission);
        let _ = SysfsWriter::write(&min_path, target_min.to_string(), WriteMode::ForcePermission);
        let _ = SysfsWriter::write(&max_path, target_max.to_string(), WriteMode::ForcePermission);

        // Synchronize MediaTek PPM limits if present
        for ppm_max in &[
            "/proc/ppm/policy/hard_userlimit_max_cpu_freq",
            "/proc/ppm/policy/userlimit_max_cpu_freq",
        ] {
            if Path::new(ppm_max).exists() {
                let _ = SysfsWriter::write(
                    ppm_max,
                    format!("{} {}", pol.mtk_cluster_index, target_max),
                    WriteMode::ForcePermission,
                );
            }
        }
        for ppm_min in &[
            "/proc/ppm/policy/hard_userlimit_min_cpu_freq",
            "/proc/ppm/policy/userlimit_min_cpu_freq",
        ] {
            if Path::new(ppm_min).exists() {
                let _ = SysfsWriter::write(
                    ppm_min,
                    format!("{} {}", pol.mtk_cluster_index, target_min),
                    WriteMode::ForcePermission,
                );
            }
        }
        if target_min == target_max && Path::new("/proc/ppm/policy_status").exists() {
            let _ = SysfsWriter::write("/proc/ppm/policy_status", "2 0", WriteMode::ForcePermission);
        }

        if was_locked {
            Self::set_perm(&min_path, 0o444);
            Self::set_perm(&max_path, 0o444);
        }

        Ok(format!(
            "Cluster policy{} frequencies updated (Min: {}, Max: {}).",
            policy_id, target_min, target_max
        ))
    }

    pub fn lock_freq(policy_id: usize, min_khz: Option<u64>, max_khz: Option<u64>) -> Result<String, String> {
        let pol_dir = format!("/sys/devices/system/cpu/cpufreq/policy{}", policy_id);
        let pol_path = Path::new(&pol_dir);
        if !pol_path.is_dir() {
            return Err(format!("Error: Policy policy{} does not exist.", policy_id));
        }
        let min_path = pol_path.join("scaling_min_freq");
        let max_path = pol_path.join("scaling_max_freq");
        Self::set_perm(&min_path, 0o644);
        Self::set_perm(&max_path, 0o644);

        let _ = Self::set_freq(policy_id, min_khz, max_khz)?;

        Self::set_perm(&min_path, 0o444);
        Self::set_perm(&max_path, 0o444);

        let cur_min = SysfsReader::read_int::<u64>(&min_path).unwrap_or(0);
        let cur_max = SysfsReader::read_int::<u64>(&max_path).unwrap_or(0);
        Ok(format!(
            "Cluster policy{} frequency range locked (Min: {}, Max: {}, Read-Only Guard Enabled).",
            policy_id, cur_min, cur_max
        ))
    }

    pub fn unlock_freq(policy_id: usize) -> Result<String, String> {
        let cpu = HardwareCapability::resolve_cpu();
        let pol = cpu
            .policies
            .iter()
            .find(|p| p.index == policy_id)
            .ok_or_else(|| format!("Error: Policy policy{} does not exist.", policy_id))?;

        let min_path = pol.path.join("scaling_min_freq");
        let max_path = pol.path.join("scaling_max_freq");
        Self::set_perm(&min_path, 0o644);
        Self::set_perm(&max_path, 0o644);

        let _ = SysfsWriter::write(&max_path, pol.max_freq.to_string(), WriteMode::ForcePermission);
        let _ = SysfsWriter::write(&min_path, pol.min_freq.to_string(), WriteMode::ForcePermission);

        for ppm_node in &[
            "/proc/ppm/policy/hard_userlimit_max_cpu_freq",
            "/proc/ppm/policy/hard_userlimit_min_cpu_freq",
            "/proc/ppm/policy/userlimit_max_cpu_freq",
            "/proc/ppm/policy/userlimit_min_cpu_freq",
        ] {
            if Path::new(ppm_node).exists() {
                let _ = SysfsWriter::write(
                    ppm_node,
                    format!("{} -1", pol.mtk_cluster_index),
                    WriteMode::ForcePermission,
                );
            }
        }
        if Path::new("/proc/ppm/policy_status").exists() {
            let _ = SysfsWriter::write("/proc/ppm/policy_status", "2 1", WriteMode::ForcePermission);
        }

        Ok(format!(
            "Cluster policy{} frequency lock released (Restored Min: {}, Max: {}).",
            policy_id, pol.min_freq, pol.max_freq
        ))
    }

    pub fn set_gov(policy_id: usize, target_gov: &str) -> Result<String, String> {
        let cpu = HardwareCapability::resolve_cpu();
        let pol = cpu
            .policies
            .iter()
            .find(|p| p.index == policy_id)
            .ok_or_else(|| format!("Error: Policy policy{} does not exist.", policy_id))?;

        if !pol.available_governors.is_empty() && !pol.available_governors.iter().any(|g| g == target_gov) {
            return Err(format!(
                "Error: Governor {} is not supported on policy{}.",
                target_gov, policy_id
            ));
        }

        let gov_path = pol.path.join("scaling_governor");
        SysfsWriter::write(&gov_path, target_gov, WriteMode::ForcePermission)
            .map_err(|e| format!("Failed to set governor on policy{}: {}", policy_id, e))?;

        Ok(format!("Cluster policy{} governor set to {}.", policy_id, target_gov))
    }

    /// Sets schedutil rate limit presets across all CPU clusters.
    pub fn set_schedutil_preset(preset: &str) -> String {
        let (up_us, down_us) = match preset.to_lowercase().as_str() {
            "responsive" | "game" | "fast" => ("500", "20000"),
            "powersave" | "battery" | "slow" => ("4000", "2000"),
            _ => ("1000", "4000"), // balanced
        };

        let mut count = 0usize;
        for id in 0..16 {
            let up_path = format!("/sys/devices/system/cpu/cpufreq/policy{}/schedutil/up_rate_limit_us", id);
            let down_path = format!("/sys/devices/system/cpu/cpufreq/policy{}/schedutil/down_rate_limit_us", id);

            if Path::new(&up_path).exists() {
                let _ = SysfsWriter::write(&up_path, up_us, WriteMode::ForcePermission);
                count += 1;
            }
            if Path::new(&down_path).exists() {
                let _ = SysfsWriter::write(&down_path, down_us, WriteMode::ForcePermission);
                count += 1;
            }
        }

        // Also check legacy global schedutil folder
        let global_up = "/sys/devices/system/cpu/cpufreq/schedutil/up_rate_limit_us";
        let global_down = "/sys/devices/system/cpu/cpufreq/schedutil/down_rate_limit_us";
        if Path::new(global_up).exists() {
            let _ = SysfsWriter::write(global_up, up_us, WriteMode::ForcePermission);
            count += 1;
        }
        if Path::new(global_down).exists() {
            let _ = SysfsWriter::write(global_down, down_us, WriteMode::ForcePermission);
            count += 1;
        }

        format!("Schedutil preset '{}' applied (up: {}us, down: {}us, {} nodes updated).", preset, up_us, down_us, count)
    }

    /// Sub-millisecond physical touch boost notification called from touchscreen interrupt thread.
    /// Elevates top-app EAS schedtune boost and discovered performance cluster floors dynamically.
    pub fn on_hardware_touch() {
        let now_ms = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as u64;

        let last = LAST_CPU_TOUCH_BOOST_MS.load(Ordering::Relaxed);
        LAST_CPU_TOUCH_BOOST_MS.store(now_ms, Ordering::Relaxed);

        if now_ms.saturating_sub(last) < 40 && IS_CPU_TOUCH_BOOSTED.load(Ordering::Relaxed) {
            return; // Debounce rapid multi-finger touches within 40ms
        }

        IS_CPU_TOUCH_BOOSTED.store(true, Ordering::Relaxed);

        let tier = DeviceTier::cached_live();
        let (base_top, _, base_fg) = tier.stune_targets("performance");
        let base_top_val: u32 = base_top.parse().unwrap_or(20);
        let touch_boost_val = base_top_val.max(if tier.is_low_end_ram { 25 } else { 30 });

        // Exclusive top-app UI priority boost (never promote background foreground workers to Big cores)
        let _ = SysfsWriter::write(
            "/dev/stune/top-app/schedtune.boost",
            touch_boost_val.to_string(),
            WriteMode::Direct,
        );
        let _ = SysfsWriter::write("/dev/stune/top-app/schedtune.prefer_high_cap", "1", WriteMode::Direct);
        let _ = SysfsWriter::write("/dev/stune/foreground/schedtune.boost", base_fg, WriteMode::Direct);

        // Elevate discovered Big/Performance clusters dynamically using hardware OPP quantiles
        for pol in Self::cached_touch_clusters() {
            if pol.is_big_cluster {
                let _ = SysfsWriter::write(&pol.min_path, &pol.touch_min_str, WriteMode::Direct);
            }
        }
    }

    /// Autonomous CPU Cluster Dynamic Governor & Ceiling Guardian tick executed on every daemon cycle.
    /// 1. Un-clamps performance cluster scaling_max_freq only if restricted below hardware capability.
    /// 2. Decays touch boost cleanly via motion-adaptive hysteresis back to tier-appropriate baseline.
    pub fn tick_dynamic_scaler(is_screen_on: bool, current_profile: &str) {
        let now_ms = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as u64;

        // 1. Lightweight Big-cluster max ceiling guard (zero sysfs dir-scan or OPP parsing per tick)
        let clusters = Self::cached_touch_clusters();
        let mut neutralized = false;
        for pol in clusters {
            if !pol.is_big_cluster {
                continue;
            }
            if let Ok(cur_max) = SysfsReader::read_int::<u64>(&pol.max_path) {
                if cur_max < pol.max_freq && (current_profile == "performance" || current_profile == "extreme") {
                    if !neutralized {
                        crate::subsystems::ThermalController::neutralize_quiet_therm();
                        neutralized = true;
                    }
                    let is_locked = std::fs::metadata(&pol.max_path)
                        .map(|m| m.permissions().readonly())
                        .unwrap_or(false);
                    if !is_locked {
                        let _ = SysfsWriter::write(&pol.max_path, pol.max_freq.to_string(), WriteMode::ForcePermission);
                    }
                }
            }
        }

        // 2. Adaptive Touch Boost Decay (fast cooldown when idle, stretched smoothly while flinging)
        let was_boosted = IS_CPU_TOUCH_BOOSTED.load(Ordering::Relaxed);
        if was_boosted {
            let last_touch = LAST_CPU_TOUCH_BOOST_MS.load(Ordering::Relaxed);
            let tier = DeviceTier::cached_live();
            let actively_moving = crate::daemon::TouchDetector::is_touch_moving(200)
                || crate::daemon::TouchDetector::is_rapid_touch(180, 2);
            let hold_ms = touch_decay_window_ms(&tier, actively_moving);
            if !is_screen_on || now_ms.saturating_sub(last_touch) >= hold_ms {
                IS_CPU_TOUCH_BOOSTED.store(false, Ordering::Relaxed);
                let cpu = HardwareCapability::resolve_cpu();
                Self::restore_baseline_floor(&cpu, current_profile);
            }
        }
    }

    pub fn restore_baseline_floor(cpu: &crate::hardware::capability::CpuCapability, current_profile: &str) {
        let tier = crate::hardware::DeviceTier::cached_live();
        let (target_boost, _, fg_boost) = tier.stune_targets(current_profile);
        let prefer_high = match current_profile {
            "extreme" => "1",
            "performance" => if tier.is_low_end_ram { "0" } else { "1" },
            _ => "0",
        };
        let _ = SysfsWriter::write("/dev/stune/top-app/schedtune.boost", target_boost, WriteMode::Direct);
        let _ = SysfsWriter::write("/dev/stune/top-app/schedtune.prefer_high_cap", prefer_high, WriteMode::Direct);
        let _ = SysfsWriter::write("/dev/stune/top-app/schedtune.prefer_idle", "1", WriteMode::Direct);
        let _ = SysfsWriter::write("/dev/stune/foreground/schedtune.boost", fg_boost, WriteMode::Direct);
        let _ = SysfsWriter::write("/dev/stune/foreground/schedtune.prefer_high_cap", "0", WriteMode::Direct);
        let _ = SysfsWriter::write("/dev/stune/foreground/schedtune.prefer_high_cap", prefer_high, WriteMode::Direct);

        if let Some(prof) = crate::profile::get_profile_by_name(current_profile) {
            for pol in &cpu.policies {
                if pol.is_locked {
                    continue;
                }
                let is_little = pol.role.contains("Little")
                    || pol.role.contains("Efisiensi")
                    || (cpu.policies.len() > 1 && pol.index == 0);

                let (min_pct, _) = tier.cpu_bounds(
                    current_profile,
                    is_little,
                    prof.cpu.min_percent,
                    prof.cpu.max_percent,
                );
                let raw_min_hz = crate::hardware::HardwareTranslator::snap_frequency(
                    pol.min_freq,
                    pol.max_freq,
                    min_pct,
                    &pol.available_freqs,
                    true,
                );
                let min_path = pol.path.join("scaling_min_freq");
                let _ = SysfsWriter::write(&min_path, raw_min_hz.to_string(), WriteMode::Direct);
            }
        }
    }
}

