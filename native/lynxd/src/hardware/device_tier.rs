use std::path::Path;

use super::capability::{BlockDeviceType, HardwareCapability};
use super::storage::StoragePolicy;

/// Hardware resource tier differentiating Low-End RAM (<= 4.5 GB) and
/// Half-Duplex eMMC storage from high-RAM UFS/NVMe platforms.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct DeviceTier {
    pub total_ram_mb: u64,
    pub is_low_end_ram: bool,
    pub is_mid_ram: bool,
    pub is_emmc_primary: bool,
    pub is_ufs_primary: bool,
    pub has_mountx_sd: bool,
    pub has_mglru: bool,
    pub has_extra_free_node: bool,
}

/// Unified VM & filesystem checkpoint targets for a given profile and device tier.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct VmProfileTargets {
    pub swappiness: u32,
    pub vfs_cache_pressure: u32,
    pub dirty_background_ratio: u32,
    pub dirty_ratio: u32,
    pub dirty_writeback_cs: u32,
    pub dirty_expire_cs: u32,
    pub min_free_kbytes: u32,
    pub watermark_scale_factor: u32,
    pub extra_free_kbytes: u32,
    pub f2fs_cp_interval_secs: u32,
    pub allow_drop_caches_full: bool,
}

impl DeviceTier {
    pub const LOW_END_RAM_MAX_MB: u64 = 4500;
    pub const MID_RAM_MAX_MB: u64 = 6500;

    /// Pure hardware tier classification (unit-testable without live sysfs).
    pub fn classify(
        total_ram_mb: u64,
        primary_storage: Option<BlockDeviceType>,
        has_emmc_only_fallback: bool,
        has_mountx_sd: bool,
    ) -> Self {
        Self::classify_with_vm_caps(
            total_ram_mb,
            primary_storage,
            has_emmc_only_fallback,
            has_mountx_sd,
            false,
            true,
        )
    }

    /// Extended pure classification including kernel VM capabilities (MGLRU & `extra_free_kbytes`).
    pub fn classify_with_vm_caps(
        total_ram_mb: u64,
        primary_storage: Option<BlockDeviceType>,
        has_emmc_only_fallback: bool,
        has_mountx_sd: bool,
        has_mglru: bool,
        has_extra_free_node: bool,
    ) -> Self {
        let is_low_end_ram = total_ram_mb > 0 && total_ram_mb <= Self::LOW_END_RAM_MAX_MB;
        let is_mid_ram =
            total_ram_mb > Self::LOW_END_RAM_MAX_MB && total_ram_mb <= Self::MID_RAM_MAX_MB;
        let is_emmc_primary = match primary_storage {
            Some(BlockDeviceType::Emmc) => true,
            Some(_) => false,
            None => has_emmc_only_fallback,
        };
        let is_ufs_primary = matches!(
            primary_storage,
            Some(BlockDeviceType::Ufs | BlockDeviceType::Nvme)
        );
        Self {
            total_ram_mb,
            is_low_end_ram,
            is_mid_ram,
            is_emmc_primary,
            is_ufs_primary,
            has_mountx_sd,
            has_mglru,
            has_extra_free_node,
        }
    }

    /// Detects device tier from already-probed hardware capabilities.
    pub fn detect(cap: &HardwareCapability) -> Self {
        let primary_type = StoragePolicy::primary_storage(&cap.storage).map(|(_, t)| t);
        let has_emmc = cap
            .storage
            .devices
            .iter()
            .any(|d| d.device_type == BlockDeviceType::Emmc);
        let has_ufs = cap
            .storage
            .devices
            .iter()
            .any(|d| d.device_type == BlockDeviceType::Ufs);
        let has_mountx_sd = StoragePolicy::mounted_sd(&cap.storage).is_some()
            || Path::new("/dev/lynx_mountx_active").exists();
        Self::classify_with_vm_caps(
            cap.memory.total_ram_mb,
            primary_type,
            has_emmc && !has_ufs,
            has_mountx_sd,
            cap.memory.mglru_supported,
            Path::new("/proc/sys/vm/extra_free_kbytes").exists(),
        )
    }

    /// Detects device tier directly from live memory & storage capabilities.
    pub fn detect_live() -> Self {
        let mem = HardwareCapability::resolve_memory();
        let storage = HardwareCapability::resolve_storage();
        let primary_type = StoragePolicy::primary_storage(&storage).map(|(_, t)| t);
        let has_emmc = storage
            .devices
            .iter()
            .any(|d| d.device_type == BlockDeviceType::Emmc);
        let has_ufs = storage
            .devices
            .iter()
            .any(|d| d.device_type == BlockDeviceType::Ufs);
        let has_mountx_sd = StoragePolicy::mounted_sd(&storage).is_some()
            || Path::new("/dev/lynx_mountx_active").exists();
        Self::classify_with_vm_caps(
            mem.total_ram_mb,
            primary_type,
            has_emmc && !has_ufs,
            has_mountx_sd,
            mem.mglru_supported,
            Path::new("/proc/sys/vm/extra_free_kbytes").exists(),
        )
    }

    /// Aligns a KB reserve to 4 MiB blocks within `[min_kb, max_kb]`.
    fn align_kb(raw_kb: u64, min_kb: u64, max_kb: u64) -> u32 {
        (((raw_kb + 2048) / 4096) * 4096).clamp(min_kb, max_kb) as u32
    }

    /// Derives monotonic `vm.min_free_kbytes` (~0.32% of RAM, 4 MiB-aligned in 16..=32 MiB).
    pub fn compute_min_free_kbytes(total_ram_mb: u64) -> u32 {
        if total_ram_mb == 0 {
            return 16384;
        }
        let raw_kb = (total_ram_mb.saturating_mul(32)) / 10;
        Self::align_kb(raw_kb, 16384, 32768)
    }

    /// Computes dynamic `(watermark_scale_factor, extra_free_kbytes)` headroom from physical RAM,
    /// storage duplex behavior, and MGLRU availability so large-RAM devices avoid `allocstall` freezes
    /// without pushing `WMARK_HIGH` into kswapd over-reclaim or squeezing <=4.5 GB ZRAM devices.
    fn compute_reclaim_headroom(&self, profile: &str, min_free_kb: u32) -> (u32, u32) {
        let ram_mb = if self.total_ram_mb > 0 { self.total_ram_mb } else { 4096 };
        let mfk = min_free_kb as u64;

        if self.is_low_end_ram {
            let extra_free = match profile {
                "extreme" | "performance" | "gaming" | "game" => {
                    if self.has_mountx_sd || self.is_emmc_primary { 8192 } else { 12288 }
                }
                "powersave" | "battery" => 8192,
                _ => 12288,
            };
            let wsf = match profile {
                "extreme" => if self.has_mountx_sd { 25 } else { 30 },
                "performance" | "gaming" | "game" => if self.has_mountx_sd || self.is_emmc_primary { 25 } else { 30 },
                _ => 30,
            };
            return (wsf, extra_free);
        }

        // Secondary microSD presence on full-duplex UFS/NVMe systems must not halve /data VM buffers.
        let is_halved_io = self.is_emmc_primary;
        let extra_free = match profile {
            "extreme" | "performance" | "gaming" | "game" => {
                if is_halved_io {
                    Self::align_kb((mfk * 3) / 4, 16384, 24576)
                } else {
                    Self::align_kb(mfk, 20480, 32768)
                }
            }
            "powersave" | "battery" => Self::align_kb(mfk / 2, 8192, 16384),
            _ => Self::align_kb((mfk * 2) / 3, 12288, 24576),
        };

        let (permille, min_step_mb, max_step_mb): (u64, u64, u64) = match profile {
            "extreme" => if is_halved_io { (60, 32, 54) } else { (75, 36, 64) },
            "performance" | "gaming" | "game" => if is_halved_io { (55, 28, 48) } else { (65, 32, 58) },
            "powersave" | "battery" => (35, 18, 36),
            _ => {
                if self.has_mglru {
                    if is_halved_io { (38, 20, 38) } else { (45, 22, 44) }
                } else if is_halved_io {
                    (30, 18, 32)
                } else {
                    (35, 18, 36)
                }
            }
        };

        // Compute step in KiB to avoid integer MB truncation error (e.g. 7812 MB stays exact wsf=75).
        let base_step_kb = (ram_mb.saturating_mul(1024).saturating_mul(permille) / 10_000)
            .clamp(min_step_mb * 1024, max_step_mb * 1024);
        // When kernel omits /proc/sys/vm/extra_free_kbytes (e.g. GKI), shift half of EFK cushion into wsf.
        let efk_comp_kb = if self.has_extra_free_node { 0 } else { (extra_free as u64) / 2 };
        let effective_step_kb = (base_step_kb + efk_comp_kb).min(72 * 1024);
        let ram_kb = ram_mb.saturating_mul(1024);
        let wsf = ((effective_step_kb.saturating_mul(10_000) + ram_kb / 2) / ram_kb).clamp(25, 85) as u32;

        (wsf, extra_free)
    }

    /// Computes hardware-aware VM, writeback, watermark, and F2FS checkpoint targets.
    /// Protects half-duplex eMMC 5.1 & <=4.5 GB ZRAM-heavy devices from reclaim/IO storms
    /// while giving high-RAM UFS/NVMe platforms non-stalling async `kswapd` headroom.
    pub fn vm_profile_targets(&self, profile_name: &str) -> VmProfileTargets {
        let profile = profile_name.trim().to_ascii_lowercase();
        let min_free_kbytes = Self::compute_min_free_kbytes(self.total_ram_mb);
        let (watermark_scale_factor, extra_free_kbytes) =
            self.compute_reclaim_headroom(profile.as_str(), min_free_kbytes);

        let (dirty_bg, dirty_ratio, writeback_cs, expire_cs) = if self.is_emmc_primary {
            if self.has_mountx_sd && matches!(profile.as_str(), "performance" | "extreme") {
                (8, 15, 500, 1000)
            } else {
                // Keep dirty buffers small on half-duplex eMMC to eliminate 45MB+ writeback stalls
                (5, 10, 150, 400)
            }
        } else if self.has_mountx_sd && matches!(profile.as_str(), "performance" | "extreme") {
            (10, 20, 500, 1000)
        } else if self.is_low_end_ram {
            (5, 15, 200, 500)
        } else if matches!(profile.as_str(), "extreme" | "performance") {
            (10, 20, 200, 500)
        } else {
            (8, 16, 200, 500)
        };

        match profile.as_str() {
            "extreme" => VmProfileTargets {
                swappiness: if self.has_mountx_sd || !self.is_low_end_ram { 10 } else { 35 },
                vfs_cache_pressure: if self.has_mountx_sd { 10 } else { 15 },
                dirty_background_ratio: dirty_bg,
                dirty_ratio,
                dirty_writeback_cs: writeback_cs,
                dirty_expire_cs: expire_cs,
                min_free_kbytes,
                watermark_scale_factor,
                extra_free_kbytes,
                f2fs_cp_interval_secs: if self.is_emmc_primary { 180 } else { 600 },
                allow_drop_caches_full: !self.is_emmc_primary && !self.has_mountx_sd,
            },
            "performance" | "gaming" | "game" => VmProfileTargets {
                swappiness: if self.has_mountx_sd { 10 } else if self.is_low_end_ram { 35 } else { 15 },
                // Keep dentry/inode caches hot in RAM on eMMC & SD to avoid cold lookups
                vfs_cache_pressure: if self.has_mountx_sd {
                    10
                } else if self.is_emmc_primary {
                    25
                } else {
                    20
                },
                dirty_background_ratio: dirty_bg,
                dirty_ratio,
                dirty_writeback_cs: writeback_cs,
                dirty_expire_cs: expire_cs,
                min_free_kbytes,
                watermark_scale_factor,
                extra_free_kbytes,
                // Shorter F2FS checkpoint interval on eMMC prevents giant sync stalls on full /data
                f2fs_cp_interval_secs: if self.is_emmc_primary {
                    150
                } else if self.is_low_end_ram {
                    240
                } else {
                    450
                },
                allow_drop_caches_full: !self.is_emmc_primary && !self.is_low_end_ram && !self.has_mountx_sd,
            },
            "powersave" | "battery" => VmProfileTargets {
                swappiness: if self.is_low_end_ram { 100 } else { 60 },
                vfs_cache_pressure: if self.is_emmc_primary { 50 } else { 80 },
                dirty_background_ratio: dirty_bg,
                dirty_ratio,
                dirty_writeback_cs: if self.is_emmc_primary { 200 } else { 500 },
                dirty_expire_cs: if self.is_emmc_primary { 600 } else { 1500 },
                min_free_kbytes,
                watermark_scale_factor,
                extra_free_kbytes,
                f2fs_cp_interval_secs: if self.is_emmc_primary { 60 } else { 120 },
                allow_drop_caches_full: !self.is_emmc_primary,
            },
            "balance" | "balanced" | _ => VmProfileTargets {
                swappiness: if self.is_low_end_ram {
                    80
                } else if self.is_mid_ram || self.is_emmc_primary {
                    50
                } else {
                    40
                },
                vfs_cache_pressure: if self.is_emmc_primary {
                    50
                } else if self.is_low_end_ram {
                    100
                } else {
                    50
                },
                dirty_background_ratio: dirty_bg,
                dirty_ratio,
                dirty_writeback_cs: writeback_cs,
                dirty_expire_cs: expire_cs,
                min_free_kbytes,
                watermark_scale_factor,
                extra_free_kbytes,
                f2fs_cp_interval_secs: if self.is_emmc_primary { 90 } else { 300 },
                allow_drop_caches_full: !self.is_emmc_primary,
            },
        }
    }

    /// Computes dynamic (min_percent, max_percent) frequency bounds for performance mode.
    /// Uncaps Little cores so FBE crypto & UI workers are never starved, while avoiding
    /// static high frequency floors on 14nm/low-end SoCs that trigger early thermal throttling.
    pub fn cpu_bounds(
        &self,
        profile_name: &str,
        is_little: bool,
        default_min_pct: u8,
        default_max_pct: u8,
    ) -> (u8, u8) {
        if profile_name == "performance" {
            if is_little {
                // Allow Little cluster to scale freely from min to 100% without floor drag or cap
                (0, default_max_pct.max(100))
            } else if self.is_low_end_ram || self.is_emmc_primary {
                // Keep Big cluster dynamic floor modest on 14nm/low-end silicon while respecting thermal modifiers
                (default_min_pct.min(15), default_max_pct)
            } else {
                (default_min_pct.min(25), default_max_pct)
            }
        } else {
            (default_min_pct, default_max_pct)
        }
    }

    /// Target GPU floor percentage for `GpuMode::High` (`performance` profile).
    /// Replaces permanent >=95% GPU redline lock so Adreno/Mali can scale coolly.
    pub fn gpu_high_floor_percent(&self) -> u64 {
        if self.is_low_end_ram || self.is_emmc_primary {
            40
        } else {
            55
        }
    }

    /// Effective `uclamp.min` percent for top-app tasks in performance profile.
    pub fn performance_uclamp_min_percent(&self, configured_pct: u8) -> u8 {
        if self.is_low_end_ram {
            configured_pct.min(25)
        } else {
            configured_pct.min(40)
        }
    }

    /// Returns cached boot hardware tier (`OnceLock`) for zero-cost sub-second hot paths.
    pub fn cached_live() -> Self {
        static CACHED: std::sync::OnceLock<DeviceTier> = std::sync::OnceLock::new();
        *CACHED.get_or_init(Self::detect_live)
    }

    /// Schedtune targets `(top_app_boost, top_app_util_min, foreground_boost)` for a profile.
    pub fn stune_targets(&self, profile_name: &str) -> (&'static str, &'static str, &'static str) {
        match profile_name {
            "extreme" => ("50", "30", "30"),
            "performance" => {
                if self.is_low_end_ram {
                    ("20", "15", "10")
                } else {
                    ("25", "20", "15")
                }
            }
            "powersave" => ("0", "0", "0"),
            _ => ("20", "12", "15"),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn universal_vm_scaling_across_ram_and_storage_tiers() {
        let rn7 = DeviceTier::classify(3663, Some(BlockDeviceType::Emmc), false, false);
        let ram6g = DeviceTier::classify_with_vm_caps(6000, Some(BlockDeviceType::Ufs), false, false, true, true);
        let ufs8g = DeviceTier::classify_with_vm_caps(7812, Some(BlockDeviceType::Ufs), false, false, true, true);
        let ufs12g = DeviceTier::classify_with_vm_caps(12000, Some(BlockDeviceType::Ufs), false, false, true, true);
        let gki_no_efk = DeviceTier::classify_with_vm_caps(7812, Some(BlockDeviceType::Ufs), false, false, true, false);

        assert!(rn7.is_low_end_ram);
        assert!(rn7.is_emmc_primary);
        assert!(!ufs8g.is_low_end_ram);
        assert!(ufs8g.is_ufs_primary);

        let rn7_perf = rn7.vm_profile_targets("performance");
        assert_eq!(rn7_perf.min_free_kbytes, 16384);
        assert_eq!(rn7_perf.watermark_scale_factor, 25);
        assert_eq!(rn7_perf.extra_free_kbytes, 8192);
        assert_eq!(rn7_perf.f2fs_cp_interval_secs, 150);
        assert!(!rn7_perf.allow_drop_caches_full);

        let rn7_bal = rn7.vm_profile_targets("balance");
        assert_eq!(rn7_bal.min_free_kbytes, 16384);
        assert_eq!(rn7_bal.watermark_scale_factor, 30);
        assert_eq!(rn7_bal.extra_free_kbytes, 12288);
        assert_eq!(rn7_bal.swappiness, 80);
        assert_eq!(rn7_bal.vfs_cache_pressure, 50);

        let ram6g_perf = ram6g.vm_profile_targets("performance");
        assert_eq!(ram6g_perf.min_free_kbytes, 20480);
        assert_eq!(ram6g_perf.extra_free_kbytes, 20480);
        assert_eq!(ram6g_perf.watermark_scale_factor, 65);

        let ufs8g_ext = ufs8g.vm_profile_targets("extreme");
        assert_eq!(ufs8g_ext.min_free_kbytes, 24576);
        assert_eq!(ufs8g_ext.watermark_scale_factor, 75);
        assert_eq!(ufs8g_ext.extra_free_kbytes, 24576);
        assert_eq!(ufs8g_ext.f2fs_cp_interval_secs, 600);
        assert!(ufs8g_ext.allow_drop_caches_full);

        let ufs8g_bal = ufs8g.vm_profile_targets("balance");
        assert_eq!(ufs8g_bal.min_free_kbytes, 24576);
        assert_eq!(ufs8g_bal.watermark_scale_factor, 45);
        assert_eq!(ufs8g_bal.extra_free_kbytes, 16384);
        assert_eq!(ufs8g_bal.f2fs_cp_interval_secs, 300);

        // 12GB UFS caps step size instead of over-scanning huge RAM
        let ufs12g_ext = ufs12g.vm_profile_targets("extreme");
        assert_eq!(ufs12g_ext.min_free_kbytes, 32768);
        assert_eq!(ufs12g_ext.extra_free_kbytes, 32768);
        assert_eq!(ufs12g_ext.watermark_scale_factor, 53);

        let ufs8g_sd = DeviceTier::classify_with_vm_caps(7812, Some(BlockDeviceType::Ufs), false, true, true, true);
        let ufs8g_sd_ext = ufs8g_sd.vm_profile_targets("extreme");
        assert_eq!(ufs8g_sd_ext.watermark_scale_factor, 75);
        assert_eq!(ufs8g_sd_ext.extra_free_kbytes, 24576);

        // Mainline/GKI kernel lacking extra_free_kbytes automatically compensates via wsf (bounded <= 85)
        let gki_ext = gki_no_efk.vm_profile_targets("extreme");
        assert!(gki_ext.watermark_scale_factor > ufs8g_ext.watermark_scale_factor);
        assert!(gki_ext.watermark_scale_factor <= 85);
    }

    #[test]
    fn performance_cpu_bounds_never_cap_little_and_keep_cool_floor() {
        let rn7 = DeviceTier::classify(3663, Some(BlockDeviceType::Emmc), false, false);
        assert_eq!(rn7.cpu_bounds("performance", true, 35, 100), (0, 100));
        assert_eq!(rn7.cpu_bounds("performance", false, 35, 100), (15, 100));
        assert_eq!(rn7.gpu_high_floor_percent(), 40);
    }

    #[test]
    fn universal_touch_and_gpu_scaling_helpers_are_soc_agnostic() {
        use crate::subsystems::cluster::{pick_opp_quantile_freq, touch_decay_window_ms};
        use crate::subsystems::gpu::normalized_gpu_compute_pct;

        let sample_big_opps = vec![1_113_600, 1_401_600, 1_747_200, 1_958_400, 2_150_400, 2_457_600];
        let low_end_floor = pick_opp_quantile_freq(&sample_big_opps, 1_113_600, 2_457_600, 30, 100);
        let flagship_floor = pick_opp_quantile_freq(&sample_big_opps, 1_113_600, 2_457_600, 45, 100);
        assert_eq!(low_end_floor, 1_401_600);
        assert!(flagship_floor >= low_end_floor);

        // Fallback linear interpolation when kernel hides scaling_available_frequencies
        let fallback_floor = pick_opp_quantile_freq(&[], 500_000, 2_500_000, 30, 100);
        assert_eq!(fallback_floor, 1_100_000);

        let rn7 = DeviceTier::classify(3663, Some(BlockDeviceType::Emmc), false, false);
        let ufs_flagship = DeviceTier::classify(8192, Some(BlockDeviceType::Ufs), false, false);
        assert_eq!(touch_decay_window_ms(&rn7, false), 450);
        assert_eq!(touch_decay_window_ms(&ufs_flagship, false), 600);
        assert_eq!(touch_decay_window_ms(&rn7, true), 650);
        assert_eq!(touch_decay_window_ms(&ufs_flagship, true), 1200);

        // Proportional GPU normalization works equally across 450 MHz Mali and 1000 MHz Adreno
        assert_eq!(normalized_gpu_compute_pct(450, 450, 50), 50);
        assert_eq!(normalized_gpu_compute_pct(500, 1000, 50), 25);
        assert_eq!(normalized_gpu_compute_pct(375, 750, 80), 40);
    }
}
