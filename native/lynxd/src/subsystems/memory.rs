use std::fmt::Write as FmtWrite;
use std::path::Path;
use std::process::Command;

use crate::hardware::capability::HardwareCapability;
use crate::state::ConfigStore;
use crate::subsystems::tui::{is_english, ui};
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};
use crate::Logger;

pub struct MemoryController;

impl MemoryController {
    fn write_opt(path: impl AsRef<Path>, val: impl AsRef<str>) {
        let p = path.as_ref();
        if p.exists() {
            let _ = SysfsWriter::write(p, val, WriteMode::ForcePermission);
        }
    }

    /// Applies universal MGLRU and RAM-proportional VM parameters.
    pub fn apply_ram() -> String {
        let mem = HardwareCapability::resolve_memory();

        if mem.mglru_supported {
            Self::write_opt("/sys/kernel/mm/lru_gen/enabled", "y");
            Self::write_opt("/sys/kernel/mm/lru_gen/min_ttl_ms", "1000");
        }

        // Dynamic RAM-proportional dirty ratios
        let (dirty_bg, dirty_ratio) = if mem.total_ram_mb <= 4500 {
            ("5", "15")
        } else if mem.total_ram_mb <= 6500 {
            ("8", "20")
        } else {
            ("10", "20")
        };

        Self::write_opt("/proc/sys/vm/dirty_background_ratio", dirty_bg);
        Self::write_opt("/proc/sys/vm/dirty_ratio", dirty_ratio);
        Self::write_opt("/proc/sys/vm/dirty_expire_centisecs", "500");
        Self::write_opt("/proc/sys/vm/dirty_writeback_centisecs", "200");
        Self::write_opt("/proc/sys/vm/extfrag_threshold", "100");
        Self::write_opt("/proc/sys/vm/oom_kill_allocating_task", "0");
        Self::write_opt("/proc/sys/vm/oom_dump_tasks", "0");
        Self::write_opt("/proc/sys/vm/overcommit_ratio", "80");
        Self::write_opt("/proc/sys/kernel/sched_schedstats", "0");
        Self::write_opt("/proc/sys/vm/compact_unevictable_allowed", "1");
        let max_maps = if mem.total_ram_mb <= 4500 { "524288" } else { "1048576" };
        Self::write_opt("/proc/sys/vm/max_map_count", max_maps);

        // Anti-stutter direct reclaim prevention:
        // Wake kswapd early in the background before RAM becomes depleted.
        let (watermark, min_free_kb) = if mem.total_ram_mb <= 4500 {
            ("30", "16384")
        } else if mem.total_ram_mb <= 6500 {
            ("40", "24576")
        } else {
            ("50", "32768")
        };
        Self::write_opt("/proc/sys/vm/watermark_scale_factor", watermark);
        Self::write_opt("/proc/sys/vm/min_free_kbytes", min_free_kb);

        // ── Multitasking & ActivityManager / LMKD Tiering ────────────────────
        // Strictly guards <= 4GB and <= 6GB RAM devices from lag & OOM stuttering,
        // while unleashing true seamless multitasking & recent app retention on 8GB+ RAM devices.
        let (max_cached, bg_limit, vfs_pressure, phantom, swap_low_pct, kill_heaviest, kill_timeout, default_swap) =
            if mem.total_ram_mb <= 4500 {
                // Tier 1: <= 4GB RAM (Redmi Note 7, etc.) - Strict conservation to prevent lag & direct reclaim
                ("32", "32", "100", "32", "20", "true", "100", "60")
            } else if mem.total_ram_mb <= 6500 {
                // Tier 2: 6GB RAM - Moderate balanced multitasking headroom
                ("48", "48", "80", "2147483647", "10", "false", "150", "65")
            } else if mem.total_ram_mb <= 10500 {
                // Tier 3: 8GB RAM (Infinix Note 11s, etc.) - Seamless multitasking, apps retained in memory
                ("64", "64", "60", "2147483647", "5", "false", "250", "50")
            } else {
                // Tier 4: >= 12GB RAM - Desktop-grade retention
                ("96", "96", "50", "2147483647", "3", "false", "300", "40")
            };

        Self::write_opt("/proc/sys/vm/vfs_cache_pressure", vfs_pressure);

        // ActivityManager cached process headroom & phantom process protection
        let _ = Command::new("device_config")
            .args(["put", "activity_manager", "max_cached_processes", max_cached])
            .output();
        let _ = Command::new("device_config")
            .args(["put", "activity_manager", "max_phantom_processes", phantom])
            .output();
        let _ = Command::new("device_config")
            .args(["put", "activity_manager", "max_empty_time_millis", "7200000"])
            .output();

        let _ = Command::new("setprop").args(["persist.sys.fw.bg_apps_limit", bg_limit]).output();
        let _ = Command::new("resetprop").args(["ro.sys.fw.bg_apps_limit", bg_limit]).output();

        // LMKD (Low Memory Killer Daemon) Tuning
        let _ = Command::new("resetprop").args(["ro.lmk.swap_free_low_percentage", swap_low_pct]).output();
        let _ = Command::new("resetprop").args(["ro.lmk.kill_heaviest_task", kill_heaviest]).output();
        let _ = Command::new("resetprop").args(["ro.lmk.kill_timeout_ms", kill_timeout]).output();

        // Runtime dynamic Dalvik VM Heap enforcement via resetprop
        let (growth_limit, heap_size, start_size, max_free) = if mem.total_ram_mb <= 4500 {
            ("192m", "512m", "8m", "16m")
        } else if mem.total_ram_mb <= 6500 {
            ("256m", "640m", "16m", "24m")
        } else if mem.total_ram_mb <= 10500 {
            ("384m", "768m", "16m", "32m")
        } else {
            ("512m", "1024m", "24m", "48m")
        };

        let _ = Command::new("resetprop").args(["dalvik.vm.heapgrowthlimit", growth_limit]).output();
        let _ = Command::new("resetprop").args(["dalvik.vm.heapsize", heap_size]).output();
        let _ = Command::new("resetprop").args(["dalvik.vm.heapstartsize", start_size]).output();
        let _ = Command::new("resetprop").args(["dalvik.vm.heapmaxfree", max_free]).output();
        let _ = Command::new("resetprop").args(["dalvik.vm.heaptargetutilization", "0.75"]).output();

        let swap_val = ConfigStore::get("swappiness")
            .or_else(|| ConfigStore::get("memory.swappiness"))
            .filter(|v| !v.is_empty() && v != "0")
            .unwrap_or_else(|| default_swap.to_string());

        Self::write_opt("/proc/sys/vm/swappiness", &swap_val);
        Self::write_opt("/dev/memcg/memory.swappiness", &swap_val);
        Self::write_opt("/dev/memcg/apps/memory.swappiness", &swap_val);
        Self::write_opt("/dev/memcg/system/memory.swappiness", "20");
        Self::write_opt(
            "/sys/module/process_reclaim/parameters/enable_process_reclaim",
            "0",
        );

        format!(
            "Universal Memory & VM optimization applied (RAM: {} MB, MGLRU: {}, Swappiness: {}).",
            mem.total_ram_mb,
            if mem.mglru_supported { "Active" } else { "N/A" },
            swap_val
        )
    }

    pub fn zram_info_json() -> String {
        let mem = HardwareCapability::resolve_memory();
        let algos: Vec<String> = mem
            .zram_supported_algos
            .iter()
            .map(|a| format!("\"{}\"", a))
            .collect();
        let max_safe_bytes = (mem.total_ram_mb * 1024 * 1024) / 2;

        let mut out = String::new();
        let _ = write!(
            out,
            "{{\"available\":{},\"live_reconfigure_requires_unused\":true,\"disksize_bytes\":{},\"active_algo\":\"{}\",\"supported_algos\":[{}],\"total_ram_mb\":{},\"max_safe_bytes\":{}}}",
            mem.zram_available,
            mem.zram_disksize_bytes,
            mem.zram_active_algo,
            algos.join(","),
            mem.total_ram_mb,
            max_safe_bytes
        );
        out
    }

    pub fn parse_size_bytes(raw: &str) -> Option<u64> {
        super::zram::ZramController::parse_size_bytes(raw)
    }

    pub fn set_zram(size: Option<&str>, algorithm: Option<&str>) -> Result<String, String> {
        super::zram::ZramController::set_zram(size, algorithm)
    }

    pub fn disable_zram() -> Result<String, String> {
        super::zram::ZramController::disable_zram()
    }

    pub fn clean_memory() -> String {
        let _ = Command::new("sync").status();
        let cache = SysfsWriter::write("/proc/sys/vm/drop_caches", "3", WriteMode::Direct).is_ok();
        let compact =
            SysfsWriter::write("/proc/sys/vm/compact_memory", "1", WriteMode::Direct).is_ok();
        let zram_compact = super::zram::ZramController::compact();
        // Evict idle background cached processes to immediately release stale anonymous pages from ZRAM
        let _ = Command::new("am").arg("kill-all").status();

        if !cache && !compact && !zram_compact {
            return ui(
                "Sebagian permintaan pembersihan memori ditolak kernel.",
                "The kernel rejected some memory cleanup requests.",
            )
            .to_string();
        }
        ui(
            "Cache dilepas, ZRAM dipadatkan, dan proses cached dievaksi demi mencegah kejenuhan swap dan framework restart.",
            "Cache released, ZRAM compacted, and cached processes evicted to prevent swap saturation and framework restarts.",
        )
        .to_string()
    }

    /// Reads kernel VM parameters from /proc/sys/vm/ and returns JSON for WebUI / Companion App.
    pub fn get_vm_tunables_json() -> String {
        let read_num = |path: &str, default: i64| -> i64 {
            SysfsReader::read_trimmed(Path::new(path))
                .ok()
                .and_then(|s| s.parse::<i64>().ok())
                .unwrap_or(default)
        };

        let dirty_ratio = read_num("/proc/sys/vm/dirty_ratio", 20);
        let dirty_bg_ratio = read_num("/proc/sys/vm/dirty_background_ratio", 10);
        let vfs_pressure = read_num("/proc/sys/vm/vfs_cache_pressure", 100);
        let swappiness = read_num("/proc/sys/vm/swappiness", 80);
        let dirty_expire = read_num("/proc/sys/vm/dirty_expire_centisecs", 500);
        let dirty_wb = read_num("/proc/sys/vm/dirty_writeback_centisecs", 200);
        let stat_interval = read_num("/proc/sys/vm/stat_interval", 1);

        format!(
            "{{\"dirty_ratio\":{},\"dirty_background_ratio\":{},\"vfs_cache_pressure\":{},\"swappiness\":{},\"dirty_expire_centisecs\":{},\"dirty_writeback_centisecs\":{},\"stat_interval\":{}}}",
            dirty_ratio, dirty_bg_ratio, vfs_pressure, swappiness, dirty_expire, dirty_wb, stat_interval
        )
    }

    /// Sets an individual VM parameter with strict whitelist protection.
    pub fn set_vm_tunable(param: &str, val: &str) -> Result<String, String> {
        let allowed = [
            "dirty_ratio",
            "dirty_background_ratio",
            "vfs_cache_pressure",
            "swappiness",
            "dirty_expire_centisecs",
            "dirty_writeback_centisecs",
            "stat_interval",
            "overcommit_ratio",
            "compact_unevictable_allowed",
            "extfrag_threshold",
        ];

        let clean_param = param.trim();
        if !allowed.contains(&clean_param) {
            return Err(if is_english() {
                format!(
                    "Parameter '{}' is not in the VM tunables whitelist.",
                    clean_param
                )
            } else {
                format!(
                    "Parameter '{}' tidak ada dalam daftar izin VM tunables.",
                    clean_param
                )
            });
        }

        let clean_val = val.trim();
        if clean_val.is_empty() || !clean_val.chars().all(|c| c.is_ascii_digit() || c == '-') {
            return Err(if is_english() {
                format!("Invalid numeric value '{}'.", clean_val)
            } else {
                format!("Nilai numerik '{}' tidak valid.", clean_val)
            });
        }

        let target_path = format!("/proc/sys/vm/{}", clean_param);
        Self::write_opt(&target_path, clean_val);

        Ok(if is_english() {
            format!("VM {} set to {}.", clean_param, clean_val)
        } else {
            format!("VM {} diatur ke {}.", clean_param, clean_val)
        })
    }

    /// Applies predefined VM tuning profiles.
    pub fn apply_vm_preset(preset: &str) -> Result<String, String> {
        match preset.to_lowercase().as_str() {
            "gaming" | "game" => {
                Self::write_opt("/proc/sys/vm/dirty_ratio", "10");
                Self::write_opt("/proc/sys/vm/dirty_background_ratio", "5");
                Self::write_opt("/proc/sys/vm/vfs_cache_pressure", "120");
                Self::write_opt("/proc/sys/vm/swappiness", "60");
                Self::write_opt("/proc/sys/vm/dirty_expire_centisecs", "300");
                Self::write_opt("/proc/sys/vm/dirty_writeback_centisecs", "100");
                Self::write_opt("/proc/sys/vm/stat_interval", "1");
                Ok(ui("Preset VM diterapkan: Gaming (buffer dirty rendah, writeback agresif, tekanan VFS 120).", "VM preset applied: Gaming (Low dirty buffers, aggressive writeback, vfs pressure 120).").to_string())
            }
            "balanced" | "balance" => {
                Self::write_opt("/proc/sys/vm/dirty_ratio", "20");
                Self::write_opt("/proc/sys/vm/dirty_background_ratio", "10");
                Self::write_opt("/proc/sys/vm/vfs_cache_pressure", "80");
                Self::write_opt("/proc/sys/vm/swappiness", "70");
                Self::write_opt("/proc/sys/vm/dirty_expire_centisecs", "500");
                Self::write_opt("/proc/sys/vm/dirty_writeback_centisecs", "200");
                Self::write_opt("/proc/sys/vm/stat_interval", "1");
                Self::write_opt("/proc/sys/vm/watermark_boost_factor", "0");
                Self::write_opt("/proc/sys/vm/compaction_proactiveness", "20");
                Ok(ui(
                    "Preset VM diterapkan: Balanced (proporsi optimal multitasking & streaming video).",
                    "VM preset applied: Balanced (Optimized for fluid multitasking & video playback).",
                )
                .to_string())
            }
            "battery" | "powersave" => {
                Self::write_opt("/proc/sys/vm/dirty_ratio", "30");
                Self::write_opt("/proc/sys/vm/dirty_background_ratio", "15");
                Self::write_opt("/proc/sys/vm/vfs_cache_pressure", "80");
                Self::write_opt("/proc/sys/vm/swappiness", "90");
                Self::write_opt("/proc/sys/vm/dirty_expire_centisecs", "1500");
                Self::write_opt("/proc/sys/vm/dirty_writeback_centisecs", "500");
                Self::write_opt("/proc/sys/vm/stat_interval", "5");
                Ok(ui("Preset VM diterapkan: Battery (writeback tertunda, interval flush lebih renggang).", "VM preset applied: Battery (Delayed writeback, relaxed flush intervals).").to_string())
            }
            other => Err(if is_english() {
                format!(
                    "Unknown VM preset: '{}'. Valid options: gaming, balanced, battery.",
                    other
                )
            } else {
                format!(
                    "Preset VM '{}' tidak dikenal. Pilihan valid: gaming, balanced, battery.",
                    other
                )
            }),
        }
    }

    /// Autonomous Memory & Swap Watchdog:
    /// 1. Monitors system_server VMA mapping count. If mappings exceed safe limits (>= 40,000 VMAs),
    ///    triggers gentle memory compaction.
    /// 2. Monitors Swap / ZRAM saturation from /proc/meminfo. If Swap is critically full (free < 12.5% or free < 250MB),
    ///    autonomously triggers hardware ZRAM compaction & evicts dead cached processes to shield against
    ///    Android framework Watchdog freeze (SIGABRT / soft reboot).
    pub fn check_vma_health() {
        Self::check_swap_pressure();
        Self::check_system_server_vma();
    }

    fn check_swap_pressure() {
        if let Ok(content) = std::fs::read_to_string("/proc/meminfo") {
            let mut swap_total = 0u64;
            let mut swap_free = 0u64;
            for line in content.lines() {
                if line.starts_with("SwapTotal:") {
                    swap_total = line
                        .split_whitespace()
                        .nth(1)
                        .and_then(|s| s.parse().ok())
                        .unwrap_or(0);
                } else if line.starts_with("SwapFree:") {
                    swap_free = line
                        .split_whitespace()
                        .nth(1)
                        .and_then(|s| s.parse().ok())
                        .unwrap_or(0);
                }
            }

            // If swap device is active (> 512 MB) and critically saturated (free < 12.5% or free < 250 MB)
            if swap_total > 524_288 && (swap_free < swap_total / 8 || swap_free < 256_000) {
                // Cooldown guard to avoid spamming am kill-all every minute (180s cooldown)
                static LAST_RECLAIM: std::sync::atomic::AtomicU64 =
                    std::sync::atomic::AtomicU64::new(0);
                let now = std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .map(|d| d.as_secs())
                    .unwrap_or(0);
                let last = LAST_RECLAIM.load(std::sync::atomic::Ordering::Relaxed);
                if now.saturating_sub(last) >= 180 {
                    LAST_RECLAIM.store(now, std::sync::atomic::Ordering::Relaxed);
                    Logger::warn(if is_english() {
                        format!(
                            "[DAEMON] Critical Swap Saturation (Total: {} kB, Free: {} kB)! Triggering emergency ZRAM compaction & cached process eviction to prevent framework restart.",
                            swap_total, swap_free
                        )
                    } else {
                        format!(
                            "[DAEMON] Tekanan Swap Kritis (Total: {} kB, Bebas: {} kB)! Memicu pemadatan ZRAM & eviksi cached process darurat demi mencegah restart framework.",
                            swap_total, swap_free
                        )
                    });

                    let _ = super::zram::ZramController::compact();
                    let _ = SysfsWriter::write("/proc/sys/vm/compact_memory", "1", WriteMode::ForcePermission);
                    let _ = Command::new("am").arg("kill-all").status();
                }
            }
        }
    }

    fn check_system_server_vma() {
        let pid_str = match std::fs::read_to_string("/dev/lynx_system_server_pid") {
            Ok(p) => p.trim().to_string(),
            Err(_) => {
                if let Ok(out) = Command::new("pidof").arg("system_server").output() {
                    let s = String::from_utf8_lossy(&out.stdout).trim().to_string();
                    if let Some(first_pid) = s.split_whitespace().next() {
                        let _ = std::fs::write("/dev/lynx_system_server_pid", first_pid);
                        first_pid.to_string()
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            }
        };

        let maps_path = format!("/proc/{}/maps", pid_str);
        if let Ok(file) = std::fs::File::open(&maps_path) {
            use std::io::{BufRead, BufReader};
            let reader = BufReader::new(file);
            let vma_count = reader.lines().count();

            if vma_count >= 40000 {
                Self::write_opt("/proc/sys/vm/compact_memory", "1");
            }
        } else {
            let _ = std::fs::remove_file("/dev/lynx_system_server_pid");
        }
    }
}
