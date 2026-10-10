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
    Nvme,
    Loop,
    Virtual,
}

impl BlockDeviceType {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Ufs => "ufs",
            Self::Emmc => "emmc",
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

    pub fn resolve_gpu(vendor: Vendor) -> GpuCapability {
        // 1. Qualcomm Adreno KGSL
        if Path::new("/sys/class/kgsl/kgsl-3d0").is_dir() {
            let cur_raw = SysfsReader::read_int::<u64>("/sys/class/kgsl/kgsl-3d0/gpuclk")
                .or_else(|_| SysfsReader::read_int::<u64>("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq"))
                .unwrap_or(0);
            let cur_freq_mhz = Self::normalize_freq_to_mhz(cur_raw);

            let max_raw = SysfsReader::read_int::<u64>("/sys/class/kgsl/kgsl-3d0/max_gpuclk")
                .or_else(|_| SysfsReader::read_int::<u64>("/sys/class/kgsl/kgsl-3d0/max_clock_mhz"))
                .or_else(|_| SysfsReader::read_int::<u64>("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq"))
                .unwrap_or(0);
            let mut max_freq_mhz = Self::normalize_freq_to_mhz(max_raw);

            let min_raw = SysfsReader::read_int::<u64>("/sys/class/kgsl/kgsl-3d0/min_clock_mhz")
                .or_else(|_| SysfsReader::read_int::<u64>("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq"))
                .unwrap_or(0);
            let mut min_freq_mhz = Self::normalize_freq_to_mhz(min_raw);

            let cur_governor = SysfsReader::read_trimmed("/sys/class/kgsl/kgsl-3d0/devfreq/governor")
                .unwrap_or_else(|_| "msm-adreno-tz".to_string());

            let load_pct = Self::read_kgsl_load();
            let boost_level = SysfsReader::read_int::<u32>("/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost")
                .or_else(|_| SysfsReader::read_int::<u32>("/sys/class/kgsl/kgsl-3d0/adrenoboost"))
                .unwrap_or(0);

            let mut available_freqs_mhz = Vec::new();
            let mut opp_table = Vec::new();
            let avail_raw = SysfsReader::read_trimmed("/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies")
                .or_else(|_| SysfsReader::read_trimmed("/sys/class/kgsl/kgsl-3d0/freq_table_mhz"))
                .or_else(|_| SysfsReader::read_trimmed("/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies"))
                .unwrap_or_default();

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
            available_freqs_mhz.sort_unstable();
            available_freqs_mhz.dedup();

            if min_freq_mhz == 0 {
                min_freq_mhz = available_freqs_mhz.first().copied().unwrap_or(0);
            }
            if max_freq_mhz == 0 {
                max_freq_mhz = available_freqs_mhz.last().copied().unwrap_or(0);
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
                .unwrap_or_else(|_| opp_table.len().max(1));

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
            let opp_table = Self::parse_mtk_gpu_opp_table();
            let mut available_freqs_mhz: Vec<u64> = opp_table.iter().map(|e| e.freq_mhz).collect();
            available_freqs_mhz.sort_unstable();
            available_freqs_mhz.dedup();

            let cur_freq_mhz = Self::read_mtk_gpu_cur_mhz();
            let mut min_freq_mhz = 0u64;
            let mut max_freq_mhz = 0u64;

            for node in &[
                "/sys/module/ged/parameters/gpu_bottom_freq",
                "/sys/module/ged/parameters/gpu_cust_boost_freq",
            ] {
                if let Ok(v) = SysfsReader::read_int::<u64>(node) {
                    let mhz = Self::normalize_freq_to_mhz(v);
                    if mhz > min_freq_mhz {
                        min_freq_mhz = mhz;
                    }
                }
            }
            if let Ok(v) = SysfsReader::read_int::<u64>("/sys/module/ged/parameters/gpu_cust_upbound_freq") {
                let mhz = Self::normalize_freq_to_mhz(v);
                if mhz > 0 {
                    max_freq_mhz = mhz;
                }
            }

            if min_freq_mhz == 0 {
                min_freq_mhz = available_freqs_mhz.first().copied().unwrap_or(0);
            }
            if max_freq_mhz == 0 {
                max_freq_mhz = available_freqs_mhz.last().copied().unwrap_or(0);
            }

            let cur_governor = SysfsReader::read_trimmed("/sys/kernel/ged/hal/dvfs_loading_mode")
                .or_else(|_| SysfsReader::read_trimmed("/sys/module/ged/parameters/cpu_boost_policy"))
                .unwrap_or_else(|_| "ged".to_string());

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
                devfreq_path: None,
                opp_table,
                available_freqs_mhz,
                available_governors: vec!["0".to_string(), "1".to_string(), "2".to_string(), "ged".to_string()],
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
            let min_freq_mhz = SysfsReader::read_int::<u64>(devpath.join("min_freq"))
                .map(Self::normalize_freq_to_mhz)
                .unwrap_or(0);
            let max_freq_mhz = SysfsReader::read_int::<u64>(devpath.join("max_freq"))
                .map(Self::normalize_freq_to_mhz)
                .unwrap_or(0);
            let cur_governor = SysfsReader::read_trimmed(devpath.join("governor"))
                .unwrap_or_else(|_| "simple_ondemand".to_string());
            let load_pct = SysfsReader::read_int::<u32>(devpath.join("load")).unwrap_or(0);

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

                let device_type = if name.starts_with("sd") {
                    BlockDeviceType::Ufs
                } else if name.starts_with("mmcblk") {
                    BlockDeviceType::Emmc
                } else if name.starts_with("nvme") {
                    BlockDeviceType::Nvme
                } else if name.starts_with("loop") {
                    BlockDeviceType::Loop
                } else if name.starts_with("zram") || name.starts_with("dm-") || name.starts_with("ram") {
                    BlockDeviceType::Virtual
                } else {
                    continue;
                };

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
