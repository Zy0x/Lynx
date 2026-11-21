use crate::hardware::capability::HardwareCapability;
use crate::subsystems::tui::{is_english, ui};
use crate::sysfs::{SysfsWriter, WriteMode};
use std::path::Path;
use std::process::Command;
use std::thread::sleep;
use std::time::Duration;

pub struct ZramController;

impl ZramController {
    fn max_allowed_zram_bytes(total_ram_mb: u64, current_disksize_bytes: u64) -> u64 {
        let ratio_cap = (total_ram_mb.saturating_mul(1024 * 1024).saturating_mul(3)) / 4;
        ratio_cap.max(current_disksize_bytes)
    }

    fn read_mem_available_kb() -> u64 {
        let Ok(meminfo) = std::fs::read_to_string("/proc/meminfo") else {
            return 0;
        };
        let mut avail_kb = None;
        let mut free_kb = 0u64;
        let mut cached_kb = 0u64;
        for line in meminfo.lines() {
            let mut parts = line.split_whitespace();
            let Some(key) = parts.next() else { continue };
            let Some(val) = parts.next().and_then(|v| v.parse::<u64>().ok()) else { continue };
            match key {
                "MemAvailable:" => {
                    avail_kb = Some(val);
                    break;
                }
                "MemFree:" => free_kb = val,
                "Cached:" => cached_kb = val,
                _ => {}
            }
        }
        avail_kb.unwrap_or(free_kb.saturating_add(cached_kb))
    }

    /// Ensures zram0 is either unused or has >= 512 MB MemAvailable headroom for safe swapoff.
    fn prepare_live_zram() -> Result<bool, String> {
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
            let used_kb = fields
                .get(3)
                .and_then(|n| n.parse::<u64>().ok())
                .ok_or_else(|| {
                    ui(
                        "Pemakaian ZRAM tidak dapat diverifikasi.",
                        "ZRAM usage could not be verified.",
                    )
                    .to_string()
                })?;
            if used_kb > 0 {
                let avail_kb = Self::read_mem_available_kb();
                const SAFETY_HEADROOM_KB: u64 = 524_288; // 512 MB
                if avail_kb > 0 && avail_kb < used_kb.saturating_add(SAFETY_HEADROOM_KB) {
                    let used_mb = used_kb / 1024;
                    let avail_mb = avail_kb / 1024;
                    return Err(if is_english() {
                        format!(
                            "Insufficient free RAM for safe live swapoff (ZRAM used: {} MB, MemAvailable: {} MB). Free background memory first.",
                            used_mb, avail_mb
                        )
                    } else {
                        format!(
                            "MemAvailable tidak cukup untuk membongkar ZRAM aktif ({} MB terpakai, tersedia {} MB). Bersihkan RAM latar dahulu.",
                            used_mb, avail_mb
                        )
                    });
                }
            }
            return Ok(true);
        }
        Ok(false)
    }

    fn reset_zram_with_retry() -> Result<(), String> {
        for attempt in 0..6 {
            if SysfsWriter::write("/sys/block/zram0/reset", "1", WriteMode::Direct).is_ok() {
                return Ok(());
            }
            if attempt < 5 {
                sleep(Duration::from_millis(80));
            }
        }
        Err(ui(
            "Reset ZRAM gagal; konfigurasi dihentikan.",
            "ZRAM reset failed; configuration stopped.",
        )
        .to_string())
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

        let active = Self::prepare_live_zram()?;

        if let Some(algo) = algo_arg {
            if !algo.is_empty()
                && !mem.zram_supported_algos.is_empty()
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
        }

        let target_bytes = match size_arg {
            Some(size_str) if !size_str.is_empty() => {
                let parsed = Self::parse_size_bytes(size_str).ok_or_else(|| {
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
                if parsed == 0 {
                    drop(_guard);
                    return Self::disable_zram();
                }
                let max_bytes = Self::max_allowed_zram_bytes(mem.total_ram_mb, mem.zram_disksize_bytes);
                if parsed > max_bytes {
                    return Err(if is_english() {
                        format!(
                            "ERROR: Requested ZRAM size ({} bytes) exceeds 75% RAM safety limit ({} MB).",
                            parsed, mem.total_ram_mb
                        )
                    } else {
                        format!(
                            "ERROR: Ukuran ZRAM ({} byte) melebihi batas aman 75% RAM ({} MB).",
                            parsed, mem.total_ram_mb
                        )
                    });
                }
                Some(parsed)
            }
            _ => {
                if let Some(algo) = algo_arg.filter(|a| !a.is_empty()) {
                    if algo != mem.zram_active_algo && mem.zram_disksize_bytes > 0 {
                        Some(mem.zram_disksize_bytes)
                    } else {
                        None
                    }
                } else {
                    None
                }
            }
        };

        if let Some(bytes) = target_bytes {
            Self::stop_unused_zram(active)?;
            Self::reset_zram_with_retry()?;
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
            let _ = SysfsWriter::write(
                "/sys/block/zram0/disksize",
                bytes.to_string(),
                WriteMode::Direct,
            );
            let actual_bytes = std::fs::read_to_string("/sys/block/zram0/disksize")
                .ok()
                .and_then(|s| s.trim().parse::<u64>().ok())
                .unwrap_or(0);
            if actual_bytes == 0 {
                return Err(ui(
                    "Ukuran ZRAM gagal diterapkan.",
                    "Failed to apply the ZRAM size.",
                )
                .to_string());
            }
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
            if actual_bytes != bytes {
                let actual_mb = actual_bytes / (1024 * 1024);
                return Ok(if is_english() {
                    format!(
                        "Algorithm updated; kernel enforced ZRAM disksize at {} MB.",
                        actual_mb
                    )
                } else {
                    format!(
                        "Algoritma diterapkan; kernel mengunci ukuran ZRAM pada {} MB.",
                        actual_mb
                    )
                });
            }
        } else if let Some(algo) = algo_arg.filter(|a| !a.is_empty()) {
            if algo != mem.zram_active_algo {
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
        let active = Self::prepare_live_zram()?;
        Self::stop_unused_zram(active)?;
        Self::reset_zram_with_retry()?;
        Ok(ui(
            "ZRAM berhasil dinonaktifkan.",
            "ZRAM disabled successfully.",
        )
        .to_string())
    }

    /// Triggers hardware ZRAM page compaction via sysfs to merge chunk allocations
    /// and reclaim physical memory without dropping active swap pages.
    pub fn compact() -> bool {
        let p = "/sys/block/zram0/compact";
        if Path::new(p).exists() {
            SysfsWriter::write(p, "1", WriteMode::ForcePermission).is_ok()
        } else {
            false
        }
    }
}
