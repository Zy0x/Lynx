use std::collections::BTreeSet;
use std::fmt::Write as FmtWrite;
use std::fs;
use std::path::{Path, PathBuf};

#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;

use crate::sysfs::SysfsReader;

pub struct TunableScanner;

impl TunableScanner {
    fn is_excluded_node(path_str: &str) -> bool {
        let lower = path_str.to_lowercase();
        const EXCLUDED_SUBSTRS: &[&str] = &[
            "cpuinfo",
            "cur_freq",
            "affected_cpus",
            "related_cpus",
            "available_",
            "subsystem",
            "uevent",
            "modalias",
            "driver_override",
            "/stat",
            "/stats",
            "debug_stat",
            "io_stat",
            "mm_stat",
            "capacity",
            "charge_counter",
            "voltage_now",
            "current_now",
            "/temp",
            "chunk_sectors",
            "/dax",
            "discard_",
            "hw_sector_size",
            "logical_block_size",
            "max_segments",
            "max_hw_sectors",
            "minimum_io_size",
            "optimal_io_size",
            "physical_block_size",
            "/reset",
            "set_sched_",
            "compact_memory",
            "drop_caches",
            "mem_limit",
            "hint_enable",
            "hint_load_thresh",
            "kpi",
            "utilization",
            "previous_freqency",
            "current_freqency",
            "bqid",
            "table",
            "fpsgo_status",
            "/info",
            "systrace_mask",
            "fbt_info",
            "/loop",
            "/ram0",
            "/ram1",
            "time_in_state",
            "trans_table",
            "scaling_setspeed",
        ];
        EXCLUDED_SUBSTRS.iter().any(|pat| lower.contains(pat))
    }

    fn ensure_writable(path: &Path) -> bool {
        if !path.is_file() {
            return false;
        }
        if SysfsReader::is_writable(path) {
            return true;
        }
        #[cfg(unix)]
        {
            if fs::set_permissions(path, fs::Permissions::from_mode(0o644)).is_ok() {
                return SysfsReader::is_writable(path);
            }
        }
        false
    }

    fn escape_json(raw: &str) -> String {
        let mut out = String::with_capacity(raw.len());
        for c in raw.chars() {
            match c {
                '"' => out.push_str("\\\""),
                '\\' => out.push_str("\\\\"),
                '\n' | '\r' | '\t' => out.push(' '),
                c if c.is_control() => {}
                c => out.push(c),
            }
        }
        out.trim().to_string()
    }

    pub fn inspect_node_json(path_str: &str) -> Option<String> {
        if path_str.starts_with("/proc/ppm/policy_status:") {
            let idx = path_str.strip_prefix("/proc/ppm/policy_status:")?;
            if let Ok(content) = fs::read_to_string("/proc/ppm/policy_status") {
                let prefix = format!("[{}]", idx);
                for line in content.lines() {
                    let trimmed = line.trim();
                    if trimmed.starts_with(&prefix) {
                        let after_bracket = trimmed[prefix.len()..].trim();
                        let pname = after_bracket.split(':').next().unwrap_or("Policy").trim();
                        let val = if after_bracket.to_lowercase().contains("enabled") {
                            "1"
                        } else {
                            "0"
                        };
                        return Some(format!(
                            "{{\"path\":\"/proc/ppm/policy_status:{}\",\"name\":\"PPM: {}\",\"category\":\"CPU & Scheduler\",\"value\":\"{}\",\"writable\":true,\"type\":\"bool\",\"options\":[{{\"value\":\"0\",\"label\":\"0 - Nonaktif\"}},{{\"value\":\"1\",\"label\":\"1 - Aktif\"}}],\"help\":\"Kebijakan PPM MediaTek. Nilai: 1=Aktif, 0=Nonaktif.\"}}",
                            idx,
                            Self::escape_json(pname),
                            val
                        ));
                    }
                }
            }
            return None;
        }

        if Self::is_excluded_node(path_str) {
            return None;
        }

        let path = Path::new(path_str);
        if !Self::ensure_writable(path) {
            return None;
        }

        let content = SysfsReader::read_trimmed(path).ok()?;
        let mut help_parts = Vec::new();
        let mut val_line = String::new();

        for line in content.lines() {
            let t = line.trim();
            if t.is_empty() {
                continue;
            }
            if let Some(comment) = t.strip_prefix('#') {
                help_parts.push(comment.trim());
            } else if val_line.is_empty() {
                val_line = t.to_string();
            }
        }

        if val_line.is_empty() {
            return None;
        }

        // Reject multi-column diagnostic dumps
        let lower_val = val_line.to_lowercase();
        if lower_val.contains("<unsupported>")
            || lower_val.contains("from : to")
            || lower_val.contains("unavailable")
            || val_line.len() > 160
        {
            return None;
        }

        // Normalize status strings into boolean values
        if lower_val.ends_with("is enabled") || lower_val.ends_with(":1") || lower_val.ends_with(" 1") {
            val_line = "1".to_string();
        } else if lower_val.ends_with("is disabled") || lower_val.ends_with(":0") || lower_val.ends_with(" 0") {
            val_line = "0".to_string();
        }

        let base = path.file_name()?.to_string_lossy().to_string();
        let parent_dir = path.parent()?;
        let parent_name = parent_dir.file_name().map(|s| s.to_string_lossy().to_string()).unwrap_or_default();

        let mut typ = "text";
        let mut options_json = String::from("[]");

        if let (Some(open_b), Some(close_b)) = (val_line.find('['), val_line.find(']')) {
            if close_b > open_b {
                typ = "choice";
                let active = val_line[open_b + 1..close_b].trim().to_string();
                let cleaned = val_line.replace(['[', ']'], " ");
                let opts: Vec<String> = cleaned
                    .split_whitespace()
                    .map(|tok| format!("\"{}\"", Self::escape_json(tok)))
                    .collect();
                options_json = format!("[{}]", opts.join(","));
                val_line = active;
            }
        } else if matches!(val_line.as_str(), "0" | "1" | "Y" | "N" | "y" | "n" | "enabled" | "disabled") {
            typ = "bool";
        } else if val_line.chars().all(|c| c.is_ascii_digit() || c == '-') {
            typ = "int";
        } else {
            // Check sibling available_* nodes dynamically
            let avail_candidates = match base.as_str() {
                "scaling_governor" | "governor" => vec![
                    parent_dir.join("scaling_available_governors"),
                    parent_dir.join("available_governors"),
                ],
                "tcp_congestion_control" => vec![PathBuf::from("/proc/sys/net/ipv4/tcp_available_congestion_control")],
                _ => vec![
                    parent_dir.join(format!("scaling_available_{}s", base)),
                    parent_dir.join(format!("available_{}s", base)),
                    parent_dir.join(format!("available_{}", base)),
                ],
            };

            for cand in avail_candidates {
                if let Ok(avail_raw) = SysfsReader::read_trimmed(&cand) {
                    let opts: Vec<String> = avail_raw
                        .split_whitespace()
                        .map(|tok| format!("\"{}\"", Self::escape_json(tok)))
                        .collect();
                    if !opts.is_empty() {
                        typ = "choice";
                        options_json = format!("[{}]", opts.join(","));
                        break;
                    }
                }
            }
        }

        let name = match parent_name.as_str() {
            "queue" | "vm" | "parameters" | "kernel" | "ipv4" | "" => base.clone(),
            _ => format!("{}/{}", parent_name, base),
        };

        let lower_path = path_str.to_lowercase();
        let category = if ["cpu", "sched", "ppm", "eara", "cpufreq", "hps", "core_ctl", "eas", "perfmgr"]
            .iter()
            .any(|k| lower_path.contains(k))
        {
            "CPU & Scheduler"
        } else if ["gpu", "kgsl", "ged", "gpufreq", "mali", "fbt_cpu", "fpsgo"]
            .iter()
            .any(|k| lower_path.contains(k))
        {
            "GPU & Graphics"
        } else if ["devfreq", "bus", "cci", "llcc", "ddr", "cpubw", "gpubw", "snoc"]
            .iter()
            .any(|k| lower_path.contains(k))
        {
            "Interconnect & Bus"
        } else if ["vm", "ksm", "zram", "lru", "swap", "hugepage", "lowmemorykiller", "process_reclaim"]
            .iter()
            .any(|k| lower_path.contains(k))
        {
            "Memory & VM"
        } else if ["block", "queue", "iosched"].iter().any(|k| lower_path.contains(k)) {
            "Storage & I/O"
        } else if ["charge", "power", "battery", "thermal"].iter().any(|k| lower_path.contains(k)) {
            "Power & Thermal"
        } else if ["net", "tcp"].iter().any(|k| lower_path.contains(k)) {
            "Network & Ping"
        } else if ["touch", "display", "kcal", "klapse", "vibrator", "sound"]
            .iter()
            .any(|k| lower_path.contains(k))
        {
            "Display & Touch"
        } else {
            "General"
        };

        let help_text = Self::escape_json(&help_parts.join(" "));
        let safe_val = Self::escape_json(&val_line);

        Some(format!(
            "{{\"path\":\"{}\",\"name\":\"{}\",\"category\":\"{}\",\"value\":\"{}\",\"writable\":true,\"type\":\"{}\",\"options\":{},\"help\":\"{}\"}}",
            Self::escape_json(path_str),
            Self::escape_json(&name),
            category,
            safe_val,
            typ,
            options_json,
            help_text
        ))
    }

    fn add_dir_files(candidates: &mut BTreeSet<String>, dir: impl AsRef<Path>) {
        if let Ok(entries) = fs::read_dir(dir.as_ref()) {
            for entry in entries.flatten() {
                let p = entry.path();
                if p.is_file() {
                    candidates.insert(p.to_string_lossy().to_string());
                }
            }
        }
    }

    pub fn scan_all_json() -> String {
        let mut candidates = BTreeSet::new();

        // 1. CPU & Scheduler Policies
        for idx in 0..16 {
            let pol = format!("/sys/devices/system/cpu/cpufreq/policy{}", idx);
            if Path::new(&pol).is_dir() {
                Self::add_dir_files(&mut candidates, &pol);
                for sub in &["schedutil", "walt", "sugov_ext"] {
                    let sub_dir = format!("{}/{}", pol, sub);
                    if Path::new(&sub_dir).is_dir() {
                        Self::add_dir_files(&mut candidates, &sub_dir);
                    }
                }
            }
            let core_ctl = format!("/sys/devices/system/cpu/cpu{}/core_ctl", idx);
            if Path::new(&core_ctl).is_dir() {
                Self::add_dir_files(&mut candidates, &core_ctl);
            }
        }

        // 2. Kernel, Vendor, GPU, VM & Power Directories
        for dir in &[
            "/sys/devices/system/cpu/sched",
            "/sys/devices/system/cpu/core_ctl",
            "/sys/devices/system/cpu/eas",
            "/sys/devices/system/cpu/perf",
            "/proc/cpufreq",
            "/proc/hps",
            "/proc/vendor_sched",
            "/sys/module/ged/parameters",
            "/sys/kernel/ged/hal",
            "/sys/kernel/fpsgo/common",
            "/sys/kernel/fpsgo/fbt",
            "/sys/kernel/fpsgo/fstb",
            "/sys/module/fbt_cpu/parameters",
            "/sys/kernel/gpu",
            "/sys/class/kgsl/kgsl-3d0",
            "/sys/class/kgsl/kgsl-3d0/devfreq",
            "/sys/module/cpu_boost/parameters",
            "/sys/module/msm_performance/parameters",
            "/sys/module/lpm_levels/parameters",
            "/proc/sys/vm",
            "/sys/kernel/mm/lru_gen",
            "/sys/kernel/mm/ksm",
            "/sys/kernel/mm/transparent_hugepage",
            "/sys/kernel/mm/swap",
            "/sys/module/lowmemorykiller/parameters",
            "/sys/module/process_reclaim/parameters",
            "/sys/devices/platform/charger",
            "/sys/devices/platform/mt_charger",
            "/sys/module/msm_thermal/parameters",
            "/sys/module/msm_thermal/core_control",
            "/proc/touchpanel",
            "/proc/perfmgr/boost_ctrl/eas_ctrl",
            "/proc/perfmgr/syslimiter",
        ] {
            if Path::new(dir).is_dir() {
                Self::add_dir_files(&mut candidates, dir);
            }
        }

        // Devfreq Interconnect Buses (DDR, CCI, L3 - excluding kgsl GPU handled by GPU tab)
        if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
            for entry in entries.flatten() {
                let p = entry.path();
                let p_str = p.to_string_lossy();
                if p.is_dir() && !p_str.contains("kgsl") {
                    for node in &["governor", "min_freq", "max_freq", "polling_interval"] {
                        let np = p.join(node);
                        if np.is_file() {
                            candidates.insert(np.to_string_lossy().to_string());
                        }
                    }
                }
            }
        }

        // 3. /proc/sys/kernel selected pure deep tunables (excluding printk handled by System tab)
        if let Ok(entries) = fs::read_dir("/proc/sys/kernel") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                if name.starts_with("sched_")
                    || name.starts_with("uclamp_")
                    || matches!(
                        name.as_str(),
                        "timer_migration"
                            | "randomize_va_space"
                            | "perf_cpu_time_max_percent"
                            | "pid_max"
                            | "threads-max"
                            | "panic"
                            | "panic_on_oops"
                    )
                {
                    candidates.insert(entry.path().to_string_lossy().to_string());
                }
            }
        }

        // 4. Block Storage Devices (/sys/block/*) queue tunables
        if let Ok(entries) = fs::read_dir("/sys/block") {
            for entry in entries.flatten() {
                let bname = entry.file_name().to_string_lossy().to_string();
                if bname.starts_with("loop") || bname.starts_with("ram") || bname.starts_with("zram") {
                    continue;
                }
                let bpath = entry.path();
                let qpath = bpath.join("queue");
                if qpath.is_dir() {
                    for qnode in &[
                        "scheduler",
                        "read_ahead_kb",
                        "nr_requests",
                        "iostats",
                        "nomerges",
                        "rq_affinity",
                        "add_random",
                        "rotational",
                        "io_poll",
                        "io_poll_delay",
                        "wbt_lat_usec",
                    ] {
                        candidates.insert(qpath.join(qnode).to_string_lossy().to_string());
                    }
                    let iosched = qpath.join("iosched");
                    if iosched.is_dir() {
                        Self::add_dir_files(&mut candidates, &iosched);
                    }
                }
            }
        }

        // 5. Explicit pure deep nodes (FS, Network, VM, Vendor HPS/FPSGO)
        for explicit in &[
            "/proc/sys/fs/file-max",
            "/proc/sys/fs/dir-notify-enable",
            "/proc/sys/fs/inotify/max_user_watches",
            "/proc/sys/fs/inotify/max_user_instances",
            "/proc/sys/fs/pipe-max-size",
            "/proc/sys/vm/stat_interval",
            "/proc/sys/vm/laptop_mode",
            "/proc/sys/vm/block_dump",
            "/proc/sys/vm/drop_caches",
            "/proc/sys/vm/overcommit_memory",
            "/proc/sys/vm/overcommit_ratio",
            "/proc/sys/vm/page-cluster",
            "/proc/sys/vm/extra_free_kbytes",
            "/proc/sys/vm/watermark_scale_factor",
            "/proc/sys/vm/zone_reclaim_mode",
            "/proc/sys/vm/oom_kill_allocating_task",
            "/proc/sys/vm/panic_on_oom",
            "/proc/sys/vm/compaction_proactiveness",
            "/sys/kernel/mm/ksm/run",
            "/sys/kernel/mm/ksm/pages_to_scan",
            "/sys/kernel/mm/ksm/sleep_millisecs",
            "/sys/kernel/mm/transparent_hugepage/enabled",
            "/sys/kernel/mm/transparent_hugepage/defrag",
            "/proc/perfmgr/tchbst",
            "/proc/hps/up_threshold",
            "/proc/hps/down_threshold",
            "/proc/hps/rush_boost_enabled",
            "/proc/hps/heavy_task_enabled",
            "/proc/hps/power_mode",
            "/sys/kernel/fpsgo/common/fpsgo_enable",
            "/sys/kernel/fpsgo/fstb/fstb_self_ctrl",
            "/sys/module/ged/parameters/boost_gpu_enable",
            "/sys/module/ged/parameters/ged_smart_boost",
            "/sys/module/ged/parameters/gpu_dvfs_enable",
            "/proc/sys/net/ipv4/tcp_ecn",
            "/proc/sys/net/ipv4/tcp_sack",
            "/proc/sys/net/ipv4/tcp_tw_reuse",
            "/proc/sys/net/ipv4/tcp_low_latency",
            "/proc/sys/net/ipv4/tcp_fin_timeout",
            "/proc/sys/net/ipv4/tcp_window_scaling",
            "/proc/sys/net/ipv4/tcp_timestamps",
            "/proc/sys/net/ipv4/tcp_syncookies",
            "/proc/sys/net/ipv4/tcp_autocorking",
            "/proc/sys/net/ipv4/tcp_max_syn_backlog",
            "/proc/sys/net/ipv4/tcp_keepalive_time",
            "/proc/sys/net/ipv4/tcp_keepalive_intvl",
            "/proc/sys/net/ipv4/tcp_keepalive_probes",
            "/proc/sys/net/ipv4/ip_forward",
            "/proc/sys/kernel/random/read_wakeup_threshold",
            "/proc/sys/kernel/random/write_wakeup_threshold",
            "/sys/module/workqueue/parameters/power_efficient",
        ] {
            candidates.insert((*explicit).to_string());
        }

        let mut out = String::from("[\n");
        let mut first = true;

        for cand in &candidates {
            if let Some(item_json) = Self::inspect_node_json(cand) {
                if !first {
                    out.push_str(",\n");
                }
                first = false;
                let _ = write!(out, "{}", item_json);
            }
        }

        out.push_str("\n]");
        out
    }
}
