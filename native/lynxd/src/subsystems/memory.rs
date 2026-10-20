use std::fmt::Write as FmtWrite;
use std::path::Path;
use std::process::Command;

use crate::hardware::capability::HardwareCapability;
use crate::state::ConfigStore;
use crate::subsystems::tui::{is_english, ui};
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

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
        Self::write_opt("/proc/sys/vm/vfs_cache_pressure", "100");

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

        let default_swap = if mem.total_ram_mb <= 4500 { "90" } else { "80" };
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
        // Tombstones and ANRs are diagnostic evidence, not reclaimable process memory.
        if !cache || !compact {
            return ui(
                "Sebagian permintaan pembersihan memori ditolak kernel.",
                "The kernel rejected some memory cleanup requests.",
            )
            .to_string();
        }
        ui("Permintaan pelepasan cache dan pemadatan dikirim. Swap dan alokasi proses aktif dipertahankan.", "Cache release and compaction were requested. Swap and live process allocations were preserved.").to_string()
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
}
