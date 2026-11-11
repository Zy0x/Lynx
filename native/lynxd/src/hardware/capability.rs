use std::fs;
use std::path::{Path, PathBuf};

#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;

use crate::hardware::detector::{ScoreBoard, Vendor};
use crate::hardware::MtkHardware;
use crate::sysfs::SysfsReader;

#[derive(Debug, Clone)]
pub struct PolicyCapability {
    pub index: usize,
    pub mtk_cluster_index: usize,
    pub role: String,
    pub related_cpus: String,
    pub path: PathBuf,
    pub gov_tunable_dirs: Vec<PathBuf>,
    pub min_freq: u64,
    pub max_freq: u64,
    pub available_freqs: Vec<u64>,
    pub available_governors: Vec<String>,
    pub current_governor: String,
    pub current_min_freq: u64,
    pub current_max_freq: u64,
    pub is_locked: bool,
}

#[derive(Debug, Clone, Default)]
pub struct CpuCapability {
    pub policies: Vec<PolicyCapability>,
    pub total_cores: usize,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum GpuDriver {
    MtkGed,
    MtkGpufreq,
    QcomKgsl,
    GenericDevfreq,
    Unsupported,
}

impl GpuDriver {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::MtkGed => "MediaTek GED",
            Self::MtkGpufreq => "MediaTek GPUFreq",
            Self::QcomKgsl => "Qualcomm KGSL",
            Self::GenericDevfreq => "Generic Devfreq",
            Self::Unsupported => "Unsupported / Unexposed",
        }
    }
}

#[derive(Debug, Clone)]
pub struct GpuOppEntry {
    pub index: usize,
    pub freq_khz: u64,
    pub freq_mhz: u64,
}

#[derive(Debug, Clone)]
pub struct GpuCapability {
    pub driver: GpuDriver,
    pub is_supported: bool,
    pub vendor_name: String,
    pub platform_tag: String,
    pub devfreq_path: Option<PathBuf>,
    pub opp_table: Vec<GpuOppEntry>,
    pub available_freqs_mhz: Vec<u64>,
    pub available_governors: Vec<String>,
    pub min_freq_mhz: u64,
    pub max_freq_mhz: u64,
    pub cur_freq_mhz: u64,
    pub cur_governor: String,
    pub load_pct: u32,
    pub boost_level: u32,
    pub num_pwrlevels: usize,
}

impl GpuCapability {
    /// Universally resolves the lowest available hardware OPP entry (minimum frequency).
    pub fn lowest_opp(&self) -> Option<&GpuOppEntry> {
        self.opp_table.iter().min_by_key(|e| e.freq_mhz)
    }

    /// Universally resolves the highest available hardware OPP entry (peak frequency).
    pub fn highest_opp(&self) -> Option<&GpuOppEntry> {
        self.opp_table.iter().max_by_key(|e| e.freq_mhz)
    }

    /// Universally resolves the median/mid available hardware OPP entry.
    pub fn mid_opp(&self) -> Option<&GpuOppEntry> {
        self.opp_at_quantile(0.50)
    }

    /// Resolves an OPP entry at a given quantile (0.0 = minimum, 1.0 = maximum).
    /// Mathematical guarantee: always sorted ascending by frequency, immune to vendor ordering.
    pub fn opp_at_quantile(&self, q: f32) -> Option<&GpuOppEntry> {
        if self.opp_table.is_empty() {
            return None;
        }
        let mut sorted: Vec<&GpuOppEntry> = self.opp_table.iter().collect();
        sorted.sort_by_key(|e| e.freq_mhz);
        let clamped = q.clamp(0.0, 1.0);
        let idx = ((sorted.len().saturating_sub(1)) as f32 * clamped).round() as usize;
        sorted.get(idx).copied().or_else(|| self.opp_table.first())
    }

    /// Tier 0: Idle OPP (minimum frequency, e.g. 300 MHz)
    pub fn idle_opp(&self) -> Option<&GpuOppEntry> {
        self.lowest_opp()
    }

    /// Tier 1: Light interaction OPP (~22% quartile, e.g. ~446-478 MHz for tap, photo preview)
    pub fn light_opp(&self) -> Option<&GpuOppEntry> {
        self.opp_at_quantile(0.22)
    }

    /// Tier 2: Fluid interaction OPP (~44% quantile, e.g. ~588-617 MHz for scrolling, rapid fling, video)
    pub fn fluid_opp(&self) -> Option<&GpuOppEntry> {
        self.opp_at_quantile(0.44)
    }

    /// Tier 3: Peak OPP (100% quantile, e.g. 950 MHz for heavy 3D rendering)
    pub fn peak_opp(&self) -> Option<&GpuOppEntry> {
        self.highest_opp()
    }
}

#[derive(Debug, Clone, Default)]
pub struct SchedulerCapability {
    pub uclamp_min_node: Option<PathBuf>,
    pub uclamp_max_node: Option<PathBuf>,
    pub top_app_uclamp_min: Option<PathBuf>,
    pub uclamp_max_scale: u32,
    pub core_ctl_available: bool,
}

#[derive(Debug, Clone, Default)]
pub struct MemoryCapability {
    pub total_ram_mb: u64,
    pub mglru_supported: bool,
    pub zram_available: bool,
    pub zram_disksize_bytes: u64,
    pub zram_active_algo: String,
    pub zram_supported_algos: Vec<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BlockDeviceType {
    Ufs,
    Emmc,
    Sd,
    Usb,
    Unknown,
    Nvme,
    Loop,
    Virtual,
}

impl BlockDeviceType {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Ufs => "ufs",
            Self::Emmc => "emmc",
            Self::Sd => "sd",
            Self::Usb => "usb",
            Self::Unknown => "unknown",
            Self::Nvme => "nvme",
            Self::Loop => "loop",
            Self::Virtual => "virtual",
        }
    }
}

#[derive(Debug, Clone)]
pub struct BlockDeviceCapability {
    pub name: String,
    pub queue_path: PathBuf,
    pub device_type: BlockDeviceType,
    pub available_schedulers: Vec<String>,
    pub current_scheduler: String,
}

#[derive(Debug, Clone, Default)]
pub struct StorageCapability {
    pub devices: Vec<BlockDeviceCapability>,
}

#[derive(Debug, Clone, Default)]
pub struct NetworkCapability {
    pub available_tcp_algos: Vec<String>,
    pub current_tcp_algo: String,
    pub best_tcp_algo: String,
}

#[derive(Debug, Clone)]
pub struct HardwareCapability {
    pub vendor: Vendor,
    pub cpu: CpuCapability,
    pub gpu: GpuCapability,
    pub scheduler: SchedulerCapability,
    pub memory: MemoryCapability,
    pub storage: StorageCapability,
    pub network: NetworkCapability,
}

impl HardwareCapability {
    /// Probe the device's real hardware structures and capabilities dynamically without writing anything.
    pub fn resolve() -> Self {
        let (vendor, _) = ScoreBoard::evaluate();
        let cpu = Self::resolve_cpu();
        let gpu = Self::resolve_gpu(vendor);
        let scheduler = Self::resolve_scheduler();
        let memory = Self::resolve_memory();
        let storage = Self::resolve_storage();
        let network = Self::resolve_network();

        Self {
            vendor,
            cpu,
            gpu,
            scheduler,
            memory,
            storage,
            network,
        }
    }

    pub fn resolve_cpu() -> CpuCapability {
        let mut policies = Vec::new();
        let mut total_cores = 0;

        let mut present_indices = Vec::new();
        for idx in 0..16 {
            let cpu_dir = format!("/sys/devices/system/cpu/cpu{}", idx);
            if Path::new(&cpu_dir).exists() {
                total_cores += 1;
            }
            let pol_dir = format!("/sys/devices/system/cpu/cpufreq/policy{}", idx);
            if Path::new(&pol_dir).exists() {
                present_indices.push(idx);
            }
        }

        let total_policies = present_indices.len();

        for (ord, &idx) in present_indices.iter().enumerate() {
            let pol_dir = format!("/sys/devices/system/cpu/cpufreq/policy{}", idx);
            let pol_path = PathBuf::from(&pol_dir);

            let related_cpus = SysfsReader::read_trimmed(format!("{}/affected_cpus", pol_dir))
                .or_else(|_| SysfsReader::read_trimmed(format!("{}/related_cpus", pol_dir)))
                .unwrap_or_else(|_| idx.to_string());

            let mut available_freqs = Vec::new();
            if let Ok(raw_f) = SysfsReader::read_trimmed(format!("{}/scaling_available_frequencies", pol_dir)) {
                for token in raw_f.split_whitespace() {
                    if let Ok(f) = token.parse::<u64>() {
                        available_freqs.push(f);
                    }
                }
            }
            available_freqs.sort_unstable();
            available_freqs.dedup();

            let min_freq = SysfsReader::read_int::<u64>(format!("{}/cpuinfo_min_freq", pol_dir))
                .ok()
                .or_else(|| available_freqs.first().copied())
                .unwrap_or(300_000);
            let max_freq = SysfsReader::read_int::<u64>(format!("{}/cpuinfo_max_freq", pol_dir))
                .ok()
                .or_else(|| available_freqs.last().copied())
                .unwrap_or(2_000_000);

            let mut available_governors = Vec::new();
            if let Ok(raw_govs) = SysfsReader::read_trimmed(format!("{}/scaling_available_governors", pol_dir)) {
                for token in raw_govs.split_whitespace() {
                    available_governors.push(token.to_string());
                }
            }

            let current_governor = SysfsReader::read_trimmed(format!("{}/scaling_governor", pol_dir))
                .unwrap_or_else(|_| "schedutil".to_string());
            let current_min_freq = SysfsReader::read_int::<u64>(format!("{}/scaling_min_freq", pol_dir))
                .unwrap_or(min_freq);
            let current_max_freq = SysfsReader::read_int::<u64>(format!("{}/scaling_max_freq", pol_dir))
                .unwrap_or(max_freq);

            let mut gov_tunable_dirs = Vec::new();
            for gname in &["schedutil", "walt", "sugov_ext"] {
                let gpath = pol_path.join(gname);
                if gpath.is_dir() {
                    gov_tunable_dirs.push(gpath);
                }
            }

            let role = if total_policies <= 1 {
                "Kluster Utama".to_string()
            } else if total_policies == 2 {
                if ord == 0 {
                    "Efisiensi Little".to_string()
                } else {
                    "Performa Big".to_string()
                }
            } else if ord == 0 {
                "Efisiensi Little".to_string()
            } else if ord == total_policies - 1 {
                "Prime Super".to_string()
            } else {
                "Performa Mid".to_string()
            };

            let is_locked = Self::is_node_readonly(&pol_path.join("scaling_max_freq"));

            policies.push(PolicyCapability {
                index: idx,
                mtk_cluster_index: ord,
                role,
                related_cpus,
                path: pol_path,
                gov_tunable_dirs,
                min_freq,
                max_freq,
                available_freqs,
                available_governors,
                current_governor,
                current_min_freq,
                current_max_freq,
                is_locked,
            });
        }

        CpuCapability {
            policies,
            total_cores,
        }
    }

    fn is_node_readonly(path: &Path) -> bool {
        #[cfg(unix)]
        {
            if let Ok(meta) = fs::metadata(path) {
                let mode = meta.permissions().mode() & 0o777;
                return (mode & 0o222) == 0;
            }
        }
        let _ = path;
        false
    }

    /// Scans Qualcomm Adreno GPU frequencies across all available sysfs nodes and device-tree OPP tables.
    /// This guarantees detection of custom kernel OC frequencies (e.g. 700 MHz, 720 MHz, 750 MHz on SDM660).
    fn resolve_qcom_kgsl_frequencies() -> (Vec<u64>, Vec<GpuOppEntry>) {
        let mut freqs_set = std::collections::BTreeSet::new();

        // 1. Gather all sysfs frequency sources (merge all, do not stop on first)
        let sysfs_candidates = [
            "/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies",
            "/sys/class/kgsl/kgsl-3d0/freq_table_mhz",
            "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies",
            "/sys/devices/platform/soc/5000000.qcom,kgsl-3d0/devfreq/5000000.qcom,kgsl-3d0/available_frequencies",
        ];

        for path in &sysfs_candidates {
            if let Ok(content) = SysfsReader::read_trimmed(path) {
                for token in content.split_whitespace() {
                    if let Ok(v) = token.parse::<u64>() {
                        let mhz = Self::normalize_freq_to_mhz(v);
                        if mhz >= 50 && mhz <= 2000 {
                            freqs_set.insert(mhz);
                        }
                    }
                }
            }
        }

        // Check devfreq wildcard nodes
        if let Ok(entries) = std::fs::read_dir("/sys/class/devfreq") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                if name.contains("kgsl") {
                    let avail_path = entry.path().join("available_frequencies");
                    if let Ok(content) = SysfsReader::read_trimmed(&avail_path) {
                        for token in content.split_whitespace() {
                            if let Ok(v) = token.parse::<u64>() {
                                let mhz = Self::normalize_freq_to_mhz(v);
                                if mhz >= 50 && mhz <= 2000 {
                                    freqs_set.insert(mhz);
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Scan Device Tree nodes for Adreno OPPs & powerlevel bins
        let dt_bases = [
            "/proc/device-tree/soc/qcom,kgsl-3d0@5000000",
            "/proc/device-tree/soc/qcom,kgsl-3d0",
            "/proc/device-tree/qcom,kgsl-3d0",
        ];

        for dt_base in &dt_bases {
            let base_p = Path::new(dt_base);
            if !base_p.is_dir() {
                continue;
            }

            if let Ok(bins_entries) = std::fs::read_dir(base_p) {
                for bin_ent in bins_entries.flatten() {
                    let bin_path = bin_ent.path();
                    let bin_name = bin_ent.file_name().to_string_lossy().to_string();
                    if bin_name.contains("pwrlevel") {
                        if bin_path.is_dir() {
                            if let Ok(sub_entries) = std::fs::read_dir(&bin_path) {
                                for sub_ent in sub_entries.flatten() {
                                    let sub_path = sub_ent.path();
                                    if sub_path.is_dir() {
                                        let freq_file = sub_path.join("qcom,gpu-freq");
                                        if freq_file.is_file() {
                                            if let Ok(bytes) = std::fs::read(&freq_file) {
                                                if bytes.len() >= 4 {
                                                    let raw_hz = u32::from_be_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]) as u64;
                                                    let mhz = Self::normalize_freq_to_mhz(raw_hz);
                                                    if mhz >= 50 && mhz <= 2000 {
                                                        freqs_set.insert(mhz);
                                                    }
                                                }
                                            }
                                        } else if let Ok(nested_entries) = std::fs::read_dir(&sub_path) {
                                            for nest_ent in nested_entries.flatten() {
                                                let nest_freq = nest_ent.path().join("qcom,gpu-freq");
                                                if nest_freq.is_file() {
                                                    if let Ok(bytes) = std::fs::read(&nest_freq) {
                                                        if bytes.len() >= 4 {
                                                            let raw_hz = u32::from_be_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]) as u64;
                                                            let mhz = Self::normalize_freq_to_mhz(raw_hz);
                                                            if mhz >= 50 && mhz <= 2000 {
                                                                freqs_set.insert(mhz);
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Check live hardware clocks
        let live_clocks = [
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
            "/sys/class/kgsl/kgsl-3d0/max_clock_mhz",
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
        ];
        for clk_path in &live_clocks {
            if let Ok(v) = SysfsReader::read_int::<u64>(clk_path) {
                let mhz = Self::normalize_freq_to_mhz(v);
                if mhz >= 50 && mhz <= 2000 {
                    freqs_set.insert(mhz);
                }
            }
        }

        // 4. Overclock (OC) ground truth for Qualcomm Adreno 512 / SDM660 (San-Kernel-Aegis / lavender)
        // If binsoc node exists in device-tree, the kernel explicitly contains OC powerlevel bins up to 750 MHz!
        let has_binsoc = Path::new("/proc/device-tree/soc/qcom,kgsl-3d0@5000000/qcom,gpu-pwrlevel-binsoc").is_dir()
            || Path::new("/proc/device-tree/soc/qcom,kgsl-3d0/qcom,gpu-pwrlevel-binsoc").is_dir();
        if has_binsoc {
            freqs_set.insert(750);
            freqs_set.insert(700);
            freqs_set.insert(647);
        }

        let mut available_freqs_mhz: Vec<u64> = freqs_set.into_iter().collect();
        available_freqs_mhz.sort_unstable();

        // Build OPP table descending (Power Level 0 = highest MHz, Power Level N = lowest MHz)
        let mut freqs_desc = available_freqs_mhz.clone();
        freqs_desc.sort_unstable_by(|a, b| b.cmp(a));
        let opp_table: Vec<GpuOppEntry> = freqs_desc
            .into_iter()
            .enumerate()
            .map(|(idx, mhz)| GpuOppEntry {
                index: idx,
                freq_khz: mhz * 1000,
                freq_mhz: mhz,
            })
            .collect();

        (available_freqs_mhz, opp_table)
    }

    pub fn resolve_gpu(vendor: Vendor) -> GpuCapability {
        // 1. Qualcomm Adreno KGSL
        if Path::new("/sys/class/kgsl/kgsl-3d0").is_dir() {
            let cur_raw = SysfsReader::read_int::<u64>("/sys/class/kgsl/kgsl-3d0/gpuclk")
                .or_else(|_| SysfsReader::read_int::<u64>("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq"))
                .unwrap_or(0);
            let cur_freq_mhz = Self::normalize_freq_to_mhz(cur_raw);

            let cur_governor = SysfsReader::read_trimmed("/sys/class/kgsl/kgsl-3d0/devfreq/governor")
                .unwrap_or_else(|_| "msm-adreno-tz".to_string());

            let load_pct = Self::read_kgsl_load();
            let boost_level = SysfsReader::read_int::<u32>("/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost")
                .or_else(|_| SysfsReader::read_int::<u32>("/sys/class/kgsl/kgsl-3d0/adrenoboost"))
                .unwrap_or(0);

            let (available_freqs_mhz, opp_table) = Self::resolve_qcom_kgsl_frequencies();

            let hw_max_mhz = available_freqs_mhz.last().copied().unwrap_or(cur_freq_mhz);
            let mut max_freq_mhz = hw_max_mhz;
            let mut min_freq_mhz = available_freqs_mhz.first().copied().unwrap_or(0);
            if min_freq_mhz > max_freq_mhz && max_freq_mhz > 0 {
                std::mem::swap(&mut min_freq_mhz, &mut max_freq_mhz);
            }

            let is_cfg_locked = crate::state::ConfigStore::get("gpu.is_locked").unwrap_or_default() == "true";
            let locked_f = crate::state::ConfigStore::get("gpu.locked_freq_mhz")
                .and_then(|s| s.parse::<u64>().ok())
                .unwrap_or(0);
            if is_cfg_locked && locked_f > 0 {
                min_freq_mhz = locked_f;
                max_freq_mhz = locked_f;
            }

            let mut available_governors = Vec::new();
            if let Ok(govs_raw) = SysfsReader::read_trimmed("/sys/class/kgsl/kgsl-3d0/devfreq/available_governors") {
                for g in govs_raw.split_whitespace() {
                    available_governors.push(g.to_string());
                }
            }
            if available_governors.is_empty() {
                available_governors = vec![
                    "msm-adreno-tz".to_string(),
                    "performance".to_string(),
                    "powersave".to_string(),
                    "simple_ondemand".to_string(),
                ];
            }

            let num_pwrlevels = SysfsReader::read_int::<usize>("/sys/class/kgsl/kgsl-3d0/num_pwrlevels")
                .unwrap_or_else(|_| opp_table.len().max(1))
                .max(opp_table.len());

            #[cfg(target_os = "android")]
            {
                static GPU_DIAG_ONCE: std::sync::Once = std::sync::Once::new();
                GPU_DIAG_ONCE.call_once(|| {
                    let diag = format!(
                        "{{\"vendor\":\"Qualcomm Adreno\",\"cur_freq_mhz\":{},\"min_freq_mhz\":{},\"max_freq_mhz\":{},\"num_pwrlevels\":{},\"avail_freqs\":{:?}}}\n",
                        cur_freq_mhz, min_freq_mhz, max_freq_mhz, num_pwrlevels, available_freqs_mhz
                    );
                    let diag_path = Path::new("/data/local/tmp/lynxd_gpu_diag.json");
                    if std::fs::write(diag_path, &diag).is_ok() {
                        use std::os::unix::fs::PermissionsExt;
                        let _ = std::fs::set_permissions(diag_path, std::fs::Permissions::from_mode(0o666));
                    }
                });
            }

            return GpuCapability {
                driver: GpuDriver::QcomKgsl,
                is_supported: true,
                vendor_name: "Qualcomm Adreno".to_string(),
                platform_tag: "adreno".to_string(),
                devfreq_path: Some(PathBuf::from("/sys/class/kgsl/kgsl-3d0/devfreq")),
                opp_table,
                available_freqs_mhz,
                available_governors,
                min_freq_mhz,
                max_freq_mhz,
                cur_freq_mhz,
                cur_governor,
                load_pct,
                boost_level,
                num_pwrlevels,
            };
        }

        // 2. MediaTek Mali / GED / GPUFreq v1 & v2
        if MtkHardware::is_ged_available()
            || MtkHardware::is_gpufreq_available()
            || Path::new("/proc/gpufreqv2").is_dir()
            || Path::new("/sys/module/ged").is_dir()
        {
            let mut opp_table = Self::parse_mtk_gpu_opp_table();
            let mut available_freqs_mhz: Vec<u64> = opp_table.iter().map(|e| e.freq_mhz).collect();
            available_freqs_mhz.sort_unstable();
            available_freqs_mhz.dedup();

            let cur_freq_mhz = Self::read_mtk_gpu_cur_mhz();

            // Probe live kernel hardware nodes directly for potential Overclock (OC) kernels (e.g. KPM lynx_oc)
            let live_upbound_mhz = SysfsReader::read_int::<u64>("/sys/module/ged/parameters/gpu_cust_upbound_freq")
                .map(Self::normalize_freq_to_mhz)
                .unwrap_or(0);

            let hw_opp_max = available_freqs_mhz.last().copied().unwrap_or(0);
            let true_max_mhz = hw_opp_max.max(live_upbound_mhz).max(cur_freq_mhz);

            if true_max_mhz > hw_opp_max && true_max_mhz > 0 {
                opp_table.insert(0, GpuOppEntry {
                    index: 0,
                    freq_khz: true_max_mhz * 1000,
                    freq_mhz: true_max_mhz,
                });
                available_freqs_mhz.push(true_max_mhz);
                available_freqs_mhz.sort_unstable();
                available_freqs_mhz.dedup();
            }

            let mut min_freq_mhz = available_freqs_mhz.first().copied().unwrap_or(0);
            let mut max_freq_mhz = true_max_mhz;

            if min_freq_mhz > max_freq_mhz && max_freq_mhz > 0 {
                std::mem::swap(&mut min_freq_mhz, &mut max_freq_mhz);
            }

            let is_cfg_locked = crate::state::ConfigStore::get("gpu.is_locked").unwrap_or_default() == "true";
            let locked_f = crate::state::ConfigStore::get("gpu.locked_freq_mhz")
                .and_then(|s| s.parse::<u64>().ok())
                .unwrap_or(0);
            if is_cfg_locked && locked_f > 0 {
                min_freq_mhz = locked_f;
                max_freq_mhz = locked_f;
            }

            let mut mtk_devfreq_path = None;
            let mut cur_governor = String::new();
            let mut available_governors = Vec::new();

            // 1. Probe real Linux devfreq governor first (e.g. /sys/class/devfreq/*mali*)
            if let Some(devpath) = Self::find_generic_gpu_devfreq() {
                if let Ok(gov) = SysfsReader::read_trimmed(devpath.join("governor")) {
                    if !gov.is_empty() {
                        cur_governor = gov;
                        mtk_devfreq_path = Some(devpath.clone());
                        if let Ok(govs_raw) = SysfsReader::read_trimmed(devpath.join("available_governors")) {
                            for g in govs_raw.split_whitespace() {
                                available_governors.push(g.to_string());
                            }
                        }
                    }
                }
            }

            // 2. Probe /sys/kernel/gpu/gpu_governor or Mali power_policy
            if cur_governor.is_empty() {
                if let Ok(gov) = SysfsReader::read_trimmed("/sys/kernel/gpu/gpu_governor") {
                    if !gov.is_empty() {
                        cur_governor = gov;
                    }
                }
            }

            // 3. Fallback to MediaTek GED proprietary mode register without hardcoded guessing
            if cur_governor.is_empty() {
                if let Ok(ged_mode) = SysfsReader::read_trimmed("/sys/kernel/ged/hal/dvfs_loading_mode") {
                    cur_governor = format!("ged_mode_{}", ged_mode);
                    available_governors = vec![
                        "ged_mode_0".to_string(),
                        "ged_mode_1".to_string(),
                        "ged_mode_2".to_string(),
                    ];
                } else if let Ok(pol) = SysfsReader::read_trimmed("/sys/module/ged/parameters/cpu_boost_policy") {
                    cur_governor = pol;
                } else {
                    cur_governor = "ged".to_string();
                }
            }

            if available_governors.is_empty() {
                available_governors.push(cur_governor.clone());
            }

            let load_pct = Self::read_mtk_gpu_load();
            let boost_level = SysfsReader::read_int::<u32>("/sys/module/ged/parameters/boost_amp")
                .ok()
                .filter(|&v| v > 0)
                .or_else(|| SysfsReader::read_int::<u32>("/sys/module/ged/parameters/ged_boost_enable").ok())
                .unwrap_or(0);

            let driver = if MtkHardware::is_ged_available() {
                GpuDriver::MtkGed
            } else {
                GpuDriver::MtkGpufreq
            };

            return GpuCapability {
                driver,
                is_supported: true,
                vendor_name: "MediaTek Mali".to_string(),
                platform_tag: "mali_ged".to_string(),
                devfreq_path: mtk_devfreq_path,
                opp_table,
                available_freqs_mhz,
                available_governors,
                min_freq_mhz,
                max_freq_mhz,
                cur_freq_mhz,
                cur_governor,
                load_pct,
                boost_level,
                num_pwrlevels: 0,
            };
        }

        // 3. Generic Devfreq / ARM Mali / Samsung Xclipse RDNA
        if let Some(devpath) = Self::find_generic_gpu_devfreq() {
            let path_str = devpath.to_string_lossy().to_lowercase();
            let (vendor_name, platform_tag) = if path_str.contains("sgpu") {
                ("Samsung Xclipse AMD RDNA".to_string(), "rdna".to_string())
            } else if path_str.contains("mali") {
                ("ARM Mali Devfreq".to_string(), "mali".to_string())
            } else {
                ("Generic Devfreq GPU".to_string(), "generic".to_string())
            };

            let cur_freq_mhz = SysfsReader::read_int::<u64>(devpath.join("cur_freq"))
                .map(Self::normalize_freq_to_mhz)
                .unwrap_or(0);

            let mut available_freqs_mhz = Vec::new();
            let mut opp_table = Vec::new();
            if let Ok(avail_raw) = SysfsReader::read_trimmed(devpath.join("available_frequencies")) {
                for (idx, token) in avail_raw.split_whitespace().enumerate() {
                    if let Ok(v) = token.parse::<u64>() {
                        let mhz = Self::normalize_freq_to_mhz(v);
                        if mhz > 0 {
                            available_freqs_mhz.push(mhz);
                            opp_table.push(GpuOppEntry {
                                index: idx,
                                freq_khz: mhz * 1000,
                                freq_mhz: mhz,
                            });
                        }
                    }
                }
            }
            available_freqs_mhz.sort_unstable();
            available_freqs_mhz.dedup();

            let mut min_freq_mhz = SysfsReader::read_int::<u64>(devpath.join("min_freq"))
                .map(Self::normalize_freq_to_mhz)
                .unwrap_or(0);
            let mut max_freq_mhz = SysfsReader::read_int::<u64>(devpath.join("max_freq"))
                .map(Self::normalize_freq_to_mhz)
                .unwrap_or(0);

            let hw_devfreq_max = available_freqs_mhz.last().copied().unwrap_or(0);
            max_freq_mhz = max_freq_mhz.max(hw_devfreq_max).max(cur_freq_mhz);

            if max_freq_mhz > hw_devfreq_max && max_freq_mhz > 0 {
                opp_table.insert(0, GpuOppEntry {
                    index: 0,
                    freq_khz: max_freq_mhz * 1000,
                    freq_mhz: max_freq_mhz,
                });
                available_freqs_mhz.push(max_freq_mhz);
                available_freqs_mhz.sort_unstable();
                available_freqs_mhz.dedup();
            }

            if min_freq_mhz == 0 {
                min_freq_mhz = available_freqs_mhz.first().copied().unwrap_or(0);
            }
            if min_freq_mhz > max_freq_mhz && max_freq_mhz > 0 {
                std::mem::swap(&mut min_freq_mhz, &mut max_freq_mhz);
            }

            let is_cfg_locked = crate::state::ConfigStore::get("gpu.is_locked").unwrap_or_default() == "true";
            let locked_f = crate::state::ConfigStore::get("gpu.locked_freq_mhz")
                .and_then(|s| s.parse::<u64>().ok())
                .unwrap_or(0);
            if is_cfg_locked && locked_f > 0 {
                min_freq_mhz = locked_f;
                max_freq_mhz = locked_f;
            }
            let cur_governor = SysfsReader::read_trimmed(devpath.join("governor"))
                .unwrap_or_else(|_| "simple_ondemand".to_string());
            let load_pct = SysfsReader::read_int::<u32>(devpath.join("load")).unwrap_or(0);

            let mut available_governors = Vec::new();
            if let Ok(govs_raw) = SysfsReader::read_trimmed(devpath.join("available_governors")) {
                for g in govs_raw.split_whitespace() {
                    available_governors.push(g.to_string());
                }
            }
            if available_governors.is_empty() {
                available_governors = vec![
                    "simple_ondemand".to_string(),
                    "performance".to_string(),
                    "powersave".to_string(),
                ];
            }

            return GpuCapability {
                driver: GpuDriver::GenericDevfreq,
                is_supported: true,
                vendor_name,
                platform_tag,
                devfreq_path: Some(devpath),
                opp_table,
                available_freqs_mhz,
                available_governors,
                min_freq_mhz,
                max_freq_mhz,
                cur_freq_mhz,
                cur_governor,
                load_pct,
                boost_level: 0,
                num_pwrlevels: 0,
            };
        }

        let _ = vendor;
        GpuCapability {
            driver: GpuDriver::Unsupported,
            is_supported: false,
            vendor_name: "Unknown GPU".to_string(),
            platform_tag: "generic".to_string(),
            devfreq_path: None,
            opp_table: Vec::new(),
            available_freqs_mhz: Vec::new(),
            available_governors: Vec::new(),
            min_freq_mhz: 0,
            max_freq_mhz: 0,
            cur_freq_mhz: 0,
            cur_governor: "unknown".to_string(),
            load_pct: 0,
            boost_level: 0,
            num_pwrlevels: 0,
        }
    }

    /// Low-overhead runtime GPU sampler for high-frequency daemon ticks and touch wake.
    /// Caches static hardware OPP geometry once while reading live frequency, load, and boost cleanly.
    pub fn sample_gpu_fast(vendor: Vendor) -> GpuCapability {
        static GPU_BASE_CAP: std::sync::OnceLock<GpuCapability> = std::sync::OnceLock::new();

        if let Some(base) = GPU_BASE_CAP.get() {
            let mut sampled = base.clone();
            match sampled.driver {
                GpuDriver::QcomKgsl => {
                    let cur_raw = SysfsReader::read_int::<u64>("/sys/class/kgsl/kgsl-3d0/gpuclk")
                        .or_else(|_| SysfsReader::read_int::<u64>("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq"))
                        .unwrap_or(sampled.cur_freq_mhz);
                    sampled.cur_freq_mhz = Self::normalize_freq_to_mhz(cur_raw);
                    sampled.load_pct = Self::read_kgsl_load();
                    sampled.boost_level = SysfsReader::read_int::<u32>("/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost")
                        .or_else(|_| SysfsReader::read_int::<u32>("/sys/class/kgsl/kgsl-3d0/adrenoboost"))
                        .unwrap_or(sampled.boost_level);
                }
                GpuDriver::MtkGed | GpuDriver::MtkGpufreq => {
                    let cur = Self::read_mtk_gpu_cur_mhz();
                    if cur > 0 {
                        sampled.cur_freq_mhz = cur;
                    }
                    sampled.load_pct = Self::read_mtk_gpu_load();
                }
                GpuDriver::GenericDevfreq => {
                    if let Some(ref devpath) = sampled.devfreq_path {
                        if let Ok(cur_raw) = SysfsReader::read_int::<u64>(devpath.join("cur_freq")) {
                            sampled.cur_freq_mhz = Self::normalize_freq_to_mhz(cur_raw);
                        }
                        sampled.load_pct = SysfsReader::read_int::<u32>(devpath.join("load"))
                            .unwrap_or(sampled.load_pct);
                    }
                }
                GpuDriver::Unsupported => {}
            }
            return sampled;
        }

        let resolved = Self::resolve_gpu(vendor);
        if resolved.is_supported && !resolved.opp_table.is_empty() {
            let _ = GPU_BASE_CAP.set(resolved.clone());
        }
        resolved
    }

    pub fn normalize_freq_to_mhz(raw: u64) -> u64 {
        if raw > 1_000_000 {
            raw / 1_000_000
        } else if raw > 10_000 {
            raw / 1_000
        } else {
            raw
        }
    }

    fn read_kgsl_load() -> u32 {
        if let Ok(s) = SysfsReader::read_trimmed("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage") {
            let digits: String = s.chars().filter(|c| c.is_ascii_digit()).collect();
            if let Ok(v) = digits.parse::<u32>() {
                return v;
            }
        }
        if let Ok(s) = SysfsReader::read_trimmed("/sys/class/kgsl/kgsl-3d0/gpubusy") {
            let parts: Vec<&str> = s.split_whitespace().collect();
            if parts.len() >= 2 {
                if let (Ok(busy), Ok(total)) = (parts[0].parse::<u64>(), parts[1].parse::<u64>()) {
                    if total > 0 {
                        return ((busy * 100) / total) as u32;
                    }
                }
            }
        }
        if let Ok(v) = SysfsReader::read_int::<u32>("/sys/class/kgsl/kgsl-3d0/devfreq/load") {
            return v.min(100);
        }
        if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_lowercase();
                if name.contains("kgsl") {
                    if let Ok(v) = SysfsReader::read_int::<u32>(entry.path().join("load")) {
                        return v.min(100);
                    }
                }
            }
        }
        0
    }

    fn parse_mtk_gpu_opp_table() -> Vec<GpuOppEntry> {
        let mut entries = Vec::new();
        for path in &[
            "/proc/gpufreqv2/gpu_working_opp_table",
            "/proc/gpufreqv2/gpufreq_opp_dump",
            "/proc/gpufreq/gpufreq_opp_dump",
        ] {
            if let Ok(content) = fs::read_to_string(path) {
                for line in content.lines() {
                    // Format example: [0] freq = 950000, volt = 80000, ...
                    let idx_opt = line.find('[').and_then(|start| {
                        line[start + 1..].find(']').and_then(|end| {
                            line[start + 1..start + 1 + end].trim().parse::<usize>().ok()
                        })
                    });
                    let freq_opt = line.find("freq =").and_then(|pos| {
                        let after = &line[pos + 6..];
                        let digits: String = after
                            .trim_start()
                            .chars()
                            .take_while(|c| c.is_ascii_digit())
                            .collect();
                        digits.parse::<u64>().ok()
                    });

                    if let (Some(index), Some(freq_raw)) = (idx_opt, freq_opt) {
                        let freq_khz = if freq_raw > 10_000 { freq_raw } else { freq_raw * 1000 };
                        let freq_mhz = freq_khz / 1000;
                        if freq_mhz > 0 {
                            entries.push(GpuOppEntry {
                                index,
                                freq_khz,
                                freq_mhz,
                            });
                        }
                    }
                }
                if !entries.is_empty() {
                    break;
                }
            }
        }
        entries
    }

    fn read_mtk_gpu_cur_mhz() -> u64 {
        if let Ok(s) = SysfsReader::read_trimmed("/sys/kernel/ged/hal/current_freqency") {
            let parts: Vec<&str> = s.split_whitespace().collect();
            let candidate = if parts.len() >= 2 { parts[1] } else { parts.first().copied().unwrap_or("0") };
            if let Ok(v) = candidate.parse::<u64>() {
                if v > 0 {
                    return Self::normalize_freq_to_mhz(v);
                }
            }
        }
        for path in &["/proc/gpufreq/gpufreq_opp_freq", "/proc/gpufreqv2/gpufreq_opp_freq"] {
            if let Ok(content) = fs::read_to_string(path) {
                if let Some(pos) = content.find("freq =") {
                    let digits: String = content[pos + 6..]
                        .trim_start()
                        .chars()
                        .take_while(|c| c.is_ascii_digit())
                        .collect();
                    if let Ok(v) = digits.parse::<u64>() {
                        if v > 0 {
                            return Self::normalize_freq_to_mhz(v);
                        }
                    }
                }
            }
        }
        if let Ok(content) = fs::read_to_string("/proc/gpufreq/gpufreq_var_dump") {
            if let Some(pos) = content.find("freq:") {
                let digits: String = content[pos + 5..]
                    .trim_start()
                    .chars()
                    .take_while(|c| c.is_ascii_digit())
                    .collect();
                if let Ok(v) = digits.parse::<u64>() {
                    if v > 0 {
                        return Self::normalize_freq_to_mhz(v);
                    }
                }
            }
        }
        0
    }

    fn read_mtk_gpu_load() -> u32 {
        if let Ok(s) = SysfsReader::read_trimmed("/sys/kernel/ged/hal/gpu_utilization") {
            if let Some(first) = s.split_whitespace().next() {
                if let Ok(v) = first.parse::<f32>() {
                    return v as u32;
                }
            }
        }
        for node in &[
            "/sys/module/ged/parameters/gpu_loading",
            "/sys/class/misc/mali0/device/utilisation",
        ] {
            if let Ok(s) = SysfsReader::read_trimmed(node) {
                let digits: String = s.chars().filter(|c| c.is_ascii_digit()).collect();
                if let Ok(v) = digits.parse::<u32>() {
                    return v;
                }
            }
        }
        0
    }

    pub fn find_generic_gpu_devfreq() -> Option<PathBuf> {
        let devfreq_root = Path::new("/sys/class/devfreq");
        if let Ok(entries) = fs::read_dir(devfreq_root) {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_lowercase();
                if name.contains("sgpu") || name.contains("gpu") || name.contains("mali") {
                    let p = entry.path();
                    if p.is_dir() {
                        return Some(p);
                    }
                }
            }
        }
        None
    }

    pub fn resolve_scheduler() -> SchedulerCapability {
        let uclamp_min = PathBuf::from("/proc/sys/kernel/sched_util_clamp_min");
        let uclamp_max = PathBuf::from("/proc/sys/kernel/sched_util_clamp_max");
        let top_app_uclamp = PathBuf::from("/dev/cpuset/top-app/cpu.uclamp.min");

        let uclamp_min_node = if uclamp_min.exists() { Some(uclamp_min) } else { None };
        let uclamp_max_node = if uclamp_max.exists() { Some(uclamp_max) } else { None };
        let top_app_uclamp_min = if top_app_uclamp.exists() { Some(top_app_uclamp) } else { None };

        let mut uclamp_max_scale = 1024u32;
        if let Ok(s) = SysfsReader::read_trimmed("/dev/cpuset/top-app/cpu.uclamp.max") {
            if let Ok(v) = s.parse::<u32>() {
                if v == 100 {
                    uclamp_max_scale = 100;
                }
            }
        }

        let core_ctl_available = Path::new("/sys/devices/system/cpu/cpu0/core_ctl").exists()
            || Path::new("/sys/devices/system/cpu/cpu4/core_ctl").exists()
            || Path::new("/sys/devices/system/cpu/cpu6/core_ctl").exists();

        SchedulerCapability {
            uclamp_min_node,
            uclamp_max_node,
            top_app_uclamp_min,
            uclamp_max_scale,
            core_ctl_available,
        }
    }

    pub fn resolve_memory() -> MemoryCapability {
        let mut total_ram_mb = 4096u64;
        if let Ok(meminfo) = fs::read_to_string("/proc/meminfo") {
            for line in meminfo.lines() {
                if line.starts_with("MemTotal:") {
                    let parts: Vec<&str> = line.split_whitespace().collect();
                    if parts.len() >= 2 {
                        if let Ok(kb) = parts[1].parse::<u64>() {
                            total_ram_mb = kb / 1024;
                        }
                    }
                    break;
                }
            }
        }

        let mglru_supported = Path::new("/sys/kernel/mm/lru_gen/enabled").exists();
        let zram_available = Path::new("/sys/block/zram0").exists();
        let zram_disksize_bytes = SysfsReader::read_int::<u64>("/sys/block/zram0/disksize").unwrap_or(0);

        let mut zram_active_algo = String::new();
        let mut zram_supported_algos = Vec::new();
        if let Ok(raw_comp) = SysfsReader::read_trimmed("/sys/block/zram0/comp_algorithm") {
            for token in raw_comp.split_whitespace() {
                if token.starts_with('[') && token.ends_with(']') {
                    let clean = token.trim_matches(|c| c == '[' || c == ']').to_string();
                    zram_active_algo = clean.clone();
                    zram_supported_algos.push(clean);
                } else {
                    zram_supported_algos.push(token.to_string());
                }
            }
        }

        MemoryCapability {
            total_ram_mb,
            mglru_supported,
            zram_available,
            zram_disksize_bytes,
            zram_active_algo,
            zram_supported_algos,
        }
    }

    pub fn resolve_storage() -> StorageCapability {
        let mut devices = Vec::new();
        let block_root = Path::new("/sys/block");
        if let Ok(entries) = fs::read_dir(block_root) {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                let queue_path = entry.path().join("queue");
                if !queue_path.is_dir() {
                    continue;
                }

                let device_type = super::storage::StoragePolicy::classify(&entry.path());

                let mut available_schedulers = Vec::new();
                let mut current_scheduler = String::new();
                if let Ok(raw_sched) = SysfsReader::read_trimmed(queue_path.join("scheduler")) {
                    for token in raw_sched.split_whitespace() {
                        if token.starts_with('[') && token.ends_with(']') {
                            let clean = token.trim_matches(|c| c == '[' || c == ']').to_string();
                            current_scheduler = clean.clone();
                            available_schedulers.push(clean);
                        } else {
                            available_schedulers.push(token.to_string());
                        }
                    }
                }

                devices.push(BlockDeviceCapability {
                    name,
                    queue_path,
                    device_type,
                    available_schedulers,
                    current_scheduler,
                });
            }
        }
        devices.sort_by(|a, b| a.name.cmp(&b.name));
        StorageCapability { devices }
    }

    pub fn resolve_network() -> NetworkCapability {
        let mut available_tcp_algos = Vec::new();
        if let Ok(raw) = SysfsReader::read_trimmed("/proc/sys/net/ipv4/tcp_available_congestion_control") {
            for token in raw.split_whitespace() {
                available_tcp_algos.push(token.to_string());
            }
        }
        let current_tcp_algo = SysfsReader::read_trimmed("/proc/sys/net/ipv4/tcp_congestion_control")
            .unwrap_or_else(|_| "cubic".to_string());

        let mut best_tcp_algo = "cubic".to_string();
        for pref in &["bbrv3", "bbr2", "bbr", "westwood", "cubic"] {
            if available_tcp_algos.iter().any(|a| a == pref) {
                best_tcp_algo = (*pref).to_string();
                break;
            }
        }

        NetworkCapability {
            available_tcp_algos,
            current_tcp_algo,
            best_tcp_algo,
        }
    }
}
