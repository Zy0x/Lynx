use std::fmt::Write as FmtWrite;
use std::path::Path;
use std::process::Command;

use crate::hardware::capability::HardwareCapability;
use crate::state::ConfigStore;
use crate::subsystems::tui::{is_english, ui};
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

pub struct MemoryController;

impl MemoryController {
    /// Never page an in-use ZRAM device back into RAM as a cleanup or resize shortcut.
    fn require_unused_zram() -> Result<bool, String> {
        let swaps = std::fs::read_to_string("/proc/swaps").map_err(|_| {
            ui(
                "Status swap tidak dapat dibaca; perubahan ZRAM dibatalkan.",
                "Cannot read swap status; ZRAM changes were cancelled.",
            )
            .to_string()
        })?;
        for line in swaps.lines().skip(1) {
            let fields: Vec<_> = line.split_whitespace().collect();
            if fields
                .first()
                .and_then(|p| Path::new(p).file_name())
                .and_then(|n| n.to_str())
                != Some("zram0")
            {
                continue;
            }
            let used = fields
                .get(3)
                .and_then(|n| n.parse::<u64>().ok())
                .ok_or_else(|| {
                    ui(
                        "Pemakaian ZRAM tidak dapat diverifikasi.",
                        "ZRAM usage could not be verified.",
                    )
                    .to_string()
                })?;
            if used > 0 {
                return Err(ui("ZRAM masih menyimpan halaman aktif. Resize atau penonaktifan live dibatalkan untuk menghindari tekanan memori; pembersihan cache tidak mengosongkan alokasi sistem yang masih dipakai.", "ZRAM still contains live pages. Live resize or disabling was cancelled to avoid memory pressure; cache cleanup cannot free system allocations that are still in use.").to_string());
            }
            return Ok(true);
        }
        Ok(false)
    }

    fn stop_unused_zram(active: bool) -> Result<(), String> {
        if active
            && !Command::new("swapoff")
                .arg("/dev/block/zram0")
                .output()
                .map(|out| out.status.success())
                .unwrap_or(false)
        {
            return Err(ui(
                "swapoff gagal; reset ZRAM dibatalkan.",
                "swapoff failed; ZRAM reset was cancelled.",
            )
            .to_string());
        }
        Ok(())
    }

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
            "{{\"available\":{},\"disksize_bytes\":{},\"active_algo\":\"{}\",\"supported_algos\":[{}],\"total_ram_mb\":{},\"max_safe_bytes\":{}}}",
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
        let s = raw.trim().to_uppercase();
        if let Some(num) = s.strip_suffix("GB").or_else(|| s.strip_suffix('G')) {
            num.trim()
                .parse::<f64>()
                .ok()
                .map(|v| (v * 1_073_741_824.0) as u64)
        } else if let Some(num) = s.strip_suffix("MB").or_else(|| s.strip_suffix('M')) {
            num.trim()
                .parse::<f64>()
                .ok()
                .map(|v| (v * 1_048_576.0) as u64)
        } else if let Some(num) = s.strip_suffix('B') {
            num.trim().parse::<u64>().ok()
        } else {
            s.parse::<u64>().ok()
        }
    }

    pub fn set_zram(size_arg: Option<&str>, algo_arg: Option<&str>) -> Result<String, String> {
        let _guard = crate::apply::ModeLock::acquire().map_err(|_| {
            ui(
                "Pengontrol memori sedang sibuk.",
                "The memory controller is busy.",
            )
            .to_string()
        })?;
        let mem = HardwareCapability::resolve_memory();
        if !mem.zram_available {
            return Err(ui(
                "ERROR: Perangkat ZRAM (/sys/block/zram0) tidak tersedia pada kernel ini.",
                "ERROR: ZRAM device (/sys/block/zram0) is not available on this kernel.",
            )
            .to_string());
        }

        let active = Self::require_unused_zram()?;

        if let Some(algo) = algo_arg {
            if !algo.is_empty() {
                if !mem.zram_supported_algos.is_empty()
                    && !mem.zram_supported_algos.iter().any(|a| a == algo)
                {
                    return Err(if is_english() {
                        format!(
                            "ERROR: Algorithm '{}' is not supported. Supported: {}",
                            algo,
                            mem.zram_supported_algos.join(" ")
                        )
                    } else {
                        format!(
                            "ERROR: Algoritma '{}' tidak didukung. Pilihan yang didukung: {}",
                            algo,
                            mem.zram_supported_algos.join(" ")
                        )
                    });
                }
                if size_arg.is_none() && mem.zram_disksize_bytes > 0 && algo != mem.zram_active_algo
                {
                    return Err(ui("Mengubah algoritma memerlukan reset dan ukuran ZRAM yang tidak sedang digunakan.", "Changing the algorithm requires resetting an unused ZRAM device and specifying its size.").to_string());
                }
                if size_arg.is_none() && algo != mem.zram_active_algo {
                    SysfsWriter::write("/sys/block/zram0/comp_algorithm", algo, WriteMode::Direct)
                        .map_err(|_| {
                            ui(
                                "Penulisan algoritma ZRAM gagal.",
                                "Failed to write the ZRAM algorithm.",
                            )
                            .to_string()
                        })?;
                }
            }
        }

        if let Some(size_str) = size_arg {
            if !size_str.is_empty() {
                let bytes = Self::parse_size_bytes(size_str).ok_or_else(|| {
                    if is_english() {
                        format!(
                            "ERROR: Invalid ZRAM size '{}'. Use e.g. 2G or 2048M.",
                            size_str
                        )
                    } else {
                        format!(
                            "ERROR: Ukuran ZRAM '{}' tidak valid. Contoh: 2G atau 2048M.",
                            size_str
                        )
                    }
                })?;

                let max_bytes = (mem.total_ram_mb * 1024 * 1024) / 2;
                if bytes > max_bytes {
                    return Err(if is_english() {
                        format!("ERROR: Requested ZRAM size ({} bytes) exceeds 50% of physical RAM ({} MB).", bytes, mem.total_ram_mb)
                    } else {
                        format!("ERROR: Ukuran ZRAM yang diminta ({} byte) melebihi 50% RAM fisik ({} MB).", bytes, mem.total_ram_mb)
                    });
                }

                if bytes == 0 {
                    return Err(ui(
                        "Ukuran ZRAM harus lebih besar dari nol.",
                        "ZRAM size must be greater than zero.",
                    )
                    .to_string());
                }
                Self::stop_unused_zram(active)?;
                SysfsWriter::write("/sys/block/zram0/reset", "1", WriteMode::Direct).map_err(
                    |_| {
                        ui(
                            "Reset ZRAM gagal; konfigurasi dihentikan.",
                            "ZRAM reset failed; configuration stopped.",
                        )
                        .to_string()
                    },
                )?;
                let algo = algo_arg
                    .filter(|algo| !algo.is_empty())
                    .unwrap_or(&mem.zram_active_algo);
                if !algo.is_empty() {
                    SysfsWriter::write("/sys/block/zram0/comp_algorithm", algo, WriteMode::Direct)
                        .map_err(|_| {
                            ui(
                                "Penulisan algoritma ZRAM gagal.",
                                "Failed to write the ZRAM algorithm.",
                            )
                            .to_string()
                        })?;
                }
                SysfsWriter::write_verify(
                    "/sys/block/zram0/disksize",
                    bytes.to_string(),
                    WriteMode::Direct,
                )
                .map_err(|_| {
                    ui(
                        "Ukuran ZRAM gagal diterapkan.",
                        "Failed to apply the ZRAM size.",
                    )
                    .to_string()
                })?;
                for command in ["mkswap", "swapon"] {
                    if !Command::new(command)
                        .arg("/dev/block/zram0")
                        .output()
                        .map(|out| out.status.success())
                        .unwrap_or(false)
                    {
                        return Err(if is_english() {
                            format!("ZRAM configuration stopped: {} failed.", command)
                        } else {
                            format!("Konfigurasi ZRAM dihentikan: {} gagal.", command)
                        });
                    }
                }
            }
        }

        Ok(ui(
            "Konfigurasi ZRAM berhasil diperbarui.",
            "ZRAM configuration updated successfully.",
        )
        .to_string())
    }

    pub fn disable_zram() -> Result<String, String> {
        let _guard = crate::apply::ModeLock::acquire().map_err(|_| {
            ui(
                "Pengontrol memori sedang sibuk.",
                "The memory controller is busy.",
            )
            .to_string()
        })?;
        let active = Self::require_unused_zram()?;
        Self::stop_unused_zram(active)?;
        SysfsWriter::write("/sys/block/zram0/reset", "1", WriteMode::Direct)
            .map_err(|_| ui("Reset ZRAM gagal.", "ZRAM reset failed.").to_string())?;
        Ok(ui(
            "ZRAM berhasil dinonaktifkan.",
            "ZRAM disabled successfully.",
        )
        .to_string())
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
                Self::write_opt("/proc/sys/vm/vfs_cache_pressure", "100");
                Self::write_opt("/proc/sys/vm/swappiness", "80");
                Self::write_opt("/proc/sys/vm/dirty_expire_centisecs", "500");
                Self::write_opt("/proc/sys/vm/dirty_writeback_centisecs", "200");
                Self::write_opt("/proc/sys/vm/stat_interval", "1");
                Ok(ui(
                    "Preset VM diterapkan: Balanced (proporsi bawaan Linux, paging seimbang).",
                    "VM preset applied: Balanced (Stock Linux proportions, balanced paging).",
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
