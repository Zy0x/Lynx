use crate::hardware::capability::HardwareCapability;
use crate::subsystems::tui::{is_english, ui};
use crate::sysfs::{SysfsWriter, WriteMode};
use std::path::Path;
use std::process::Command;

pub struct ZramController;

impl ZramController {
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
