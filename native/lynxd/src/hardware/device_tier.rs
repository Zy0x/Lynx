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
        Self::classify(
            cap.memory.total_ram_mb,
            primary_type,
            has_emmc && !has_ufs,
            has_mountx_sd,
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
        Self::classify(mem.total_ram_mb, primary_type, has_emmc && !has_ufs, has_mountx_sd)
    }

    /// Computes hardware-aware VM, writeback, watermark, and F2FS checkpoint targets.
    /// Strictly protects half-duplex eMMC 5.1 & <=4.5 GB ZRAM-heavy devices from reclaim/IO storms
    /// while giving high-RAM UFS platforms generous writeback and allocation buffers.
    pub fn vm_profile_targets(&self, profile_name: &str) -> VmProfileTargets {
        let profile = profile_name.trim().to_ascii_lowercase();
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
                swappiness: if self.is_low_end_ram { 45 } else { 30 },
                vfs_cache_pressure: 50,
                dirty_background_ratio: dirty_bg,
                dirty_ratio,
                dirty_writeback_cs: writeback_cs,
                dirty_expire_cs: expire_cs,
                watermark_scale_factor: if self.is_low_end_ram { 60 } else { 200 },
                extra_free_kbytes: if self.is_low_end_ram { 32768 } else { 65536 },
                f2fs_cp_interval_secs: if self.is_emmc_primary { 150 } else { 300 },
                allow_drop_caches_full: !self.is_emmc_primary,
            },
            "performance" | "gaming" | "game" => VmProfileTargets {
                swappiness: if self.is_low_end_ram { 60 } else { 50 },
                // Keep dentry/inode caches hot in RAM on eMMC & <=4.5GB to avoid FBE cold lookups
                vfs_cache_pressure: if self.is_emmc_primary {
                    50
                } else if self.is_low_end_ram {
                    60
                } else if self.is_mid_ram {
                    80
                } else {
                    50
                },
                dirty_background_ratio: dirty_bg,
                dirty_ratio,
                dirty_writeback_cs: writeback_cs,
                dirty_expire_cs: expire_cs,
                // Scale kswapd watermarks proportionally: ~120MB forced reserve thrashes 4GB ZRAM
                watermark_scale_factor: if self.is_low_end_ram {
                    40
                } else if self.is_emmc_primary || self.is_mid_ram {
                    90
                } else {
                    150
                },
                extra_free_kbytes: if self.is_low_end_ram {
                    24576
                } else if self.is_mid_ram {
                    49152
                } else {
                    65536
                },
                // Shorter F2FS checkpoint interval on eMMC prevents giant sync stalls on full /data
                f2fs_cp_interval_secs: if self.is_emmc_primary {
                    120
                } else if self.is_low_end_ram {
                    180
                } else {
                    300
                },
                allow_drop_caches_full: !self.is_emmc_primary && !self.is_low_end_ram,
            },
            "powersave" | "battery" => VmProfileTargets {
                swappiness: 60,
                vfs_cache_pressure: if self.is_emmc_primary { 50 } else { 100 },
                dirty_background_ratio: dirty_bg,
                dirty_ratio,
                dirty_writeback_cs: if self.is_emmc_primary { 200 } else { 500 },
                dirty_expire_cs: if self.is_emmc_primary { 600 } else { 1500 },
                watermark_scale_factor: 30,
                extra_free_kbytes: 0,
                f2fs_cp_interval_secs: 60,
                allow_drop_caches_full: !self.is_emmc_primary,
            },
            _ => VmProfileTargets {
                swappiness: if self.is_low_end_ram { 60 } else { 50 },
                vfs_cache_pressure: if self.is_emmc_primary {
                    50
                } else if self.is_low_end_ram {
                    100
                } else {
                    80
                },
                dirty_background_ratio: dirty_bg,
                dirty_ratio,
                dirty_writeback_cs: writeback_cs,
                dirty_expire_cs: expire_cs,
                watermark_scale_factor: if self.is_low_end_ram {
                    30
                } else if self.is_emmc_primary {
                    60
                } else {
                    100
                },
                extra_free_kbytes: if self.is_low_end_ram { 16384 } else { 20480 },
                f2fs_cp_interval_secs: 60,
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
    fn rn7_emmc_low_end_performance_differs_from_high_end_ufs() {
        let rn7 = DeviceTier::classify(3663, Some(BlockDeviceType::Emmc), false, false);
        let flagship = DeviceTier::classify(8192, Some(BlockDeviceType::Ufs), false, false);

        assert!(rn7.is_low_end_ram);
        assert!(rn7.is_emmc_primary);
        assert!(!flagship.is_low_end_ram);
        assert!(flagship.is_ufs_primary);

        let rn7_vm = rn7.vm_profile_targets("performance");
        let ufs_vm = flagship.vm_profile_targets("performance");

        assert_eq!(rn7_vm.vfs_cache_pressure, 50);
        assert_eq!(rn7_vm.dirty_ratio, 10);
        assert_eq!(rn7_vm.dirty_background_ratio, 5);
        assert_eq!(rn7_vm.watermark_scale_factor, 40);
        assert_eq!(rn7_vm.extra_free_kbytes, 24576);
        assert_eq!(rn7_vm.f2fs_cp_interval_secs, 120);
        assert!(!rn7_vm.allow_drop_caches_full);

        assert_eq!(ufs_vm.dirty_ratio, 20);
        assert_eq!(ufs_vm.watermark_scale_factor, 150);
        assert_eq!(ufs_vm.extra_free_kbytes, 65536);
        assert_eq!(ufs_vm.f2fs_cp_interval_secs, 300);
        assert!(ufs_vm.allow_drop_caches_full);
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

        // Proportional GPU normalization works equally across 450 MHz Mali and 1000 MHz Adreno
        assert_eq!(normalized_gpu_compute_pct(450, 450, 50), 50);
        assert_eq!(normalized_gpu_compute_pct(500, 1000, 50), 25);
        assert_eq!(normalized_gpu_compute_pct(375, 750, 80), 40);
    }
}
