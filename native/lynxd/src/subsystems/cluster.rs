use std::fmt::Write as FmtWrite;
use std::path::Path;

#[cfg(unix)]
use std::fs;
#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;

use crate::hardware::capability::HardwareCapability;
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

pub struct ClusterController;

impl ClusterController {
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
}

