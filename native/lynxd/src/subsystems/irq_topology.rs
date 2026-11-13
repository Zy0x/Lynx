use std::collections::BTreeSet;
use std::fs;
use std::path::Path;
use std::process::Command;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CpuClusterInfo {
    pub cpus: Vec<u32>,
    pub capacity: Option<u32>,
    pub max_freq_khz: Option<u64>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ClusterRanges {
    pub all_cpu_range: String,
    pub little_cpu_range: String,
    pub big_cpu_range: String,
    pub top_cpu_range: String,
    pub little_cpus: Vec<u32>,
    pub big_cpus: Vec<u32>,
    pub is_asymmetric: bool,
}

impl ClusterRanges {
    /// Derives CPU cluster ranges dynamically from online CPUs, capacity, and max frequency.
    pub fn from_clusters(online_cpus: &[u32], clusters: &[CpuClusterInfo]) -> Self {
        let mut all: Vec<u32> = online_cpus.to_vec();
        all.sort_unstable();
        all.dedup();
        if all.is_empty() {
            for c in clusters {
                all.extend_from_slice(&c.cpus);
            }
            all.sort_unstable();
            all.dedup();
        }
        let all_range = format_cpu_list(&all);
        if all.is_empty() || clusters.is_empty() {
            return Self {
                all_cpu_range: all_range.clone(),
                little_cpu_range: all_range.clone(),
                big_cpu_range: all_range.clone(),
                top_cpu_range: all_range,
                little_cpus: all.clone(),
                big_cpus: all,
                is_asymmetric: false,
            };
        }

        let online_set: BTreeSet<u32> = all.iter().copied().collect();
        let caps: Vec<u32> = clusters.iter().filter_map(|c| c.capacity).collect();
        let cap_varies = caps
            .iter()
            .copied()
            .max()
            .zip(caps.iter().copied().min())
            .map(|(hi, lo)| hi > lo)
            .unwrap_or(false);

        let scored: Vec<(Vec<u32>, (u64, u64))> = clusters
            .iter()
            .filter_map(|c| {
                let active: Vec<u32> = c
                    .cpus
                    .iter()
                    .copied()
                    .filter(|id| online_set.contains(id))
                    .collect();
                if active.is_empty() {
                    return None;
                }
                let cap_score = if cap_varies {
                    u64::from(c.capacity.unwrap_or(0))
                } else {
                    0
                };
                let freq_score = c.max_freq_khz.unwrap_or(0);
                Some((active, (cap_score, freq_score)))
            })
            .collect();

        let min_score = scored.iter().map(|(_, s)| *s).min();
        let max_score = scored.iter().map(|(_, s)| *s).max();
        match (min_score, max_score) {
            (Some(lo), Some(hi)) if hi > lo => {
                let mut little: Vec<u32> = Vec::new();
                let mut big: Vec<u32> = Vec::new();
                let mut top: Vec<u32> = Vec::new();
                for (cpus, score) in scored {
                    if score == lo {
                        little.extend(cpus);
                    } else {
                        big.extend(cpus.iter().copied());
                        if score == hi {
                            top.extend(cpus);
                        }
                    }
                }
                little.sort_unstable();
                little.dedup();
                big.sort_unstable();
                big.dedup();
                top.sort_unstable();
                top.dedup();
                if big.is_empty() {
                    big = all.clone();
                }
                if little.is_empty() {
                    little = all.clone();
                }
                if top.is_empty() {
                    top = big.clone();
                }
                Self {
                    all_cpu_range: all_range,
                    little_cpu_range: format_cpu_list(&little),
                    big_cpu_range: format_cpu_list(&big),
                    top_cpu_range: format_cpu_list(&top),
                    little_cpus: little,
                    big_cpus: big,
                    is_asymmetric: true,
                }
            }
            _ => Self {
                all_cpu_range: all_range.clone(),
                little_cpu_range: all_range.clone(),
                big_cpu_range: all_range.clone(),
                top_cpu_range: all_range,
                little_cpus: all.clone(),
                big_cpus: all,
                is_asymmetric: false,
            },
        }
    }

    /// Selects a non-CPU0 LITTLE core for IRQ offloading when available.
    pub fn little_offload_core(&self, slot: usize) -> u32 {
        let non_zero: Vec<u32> = self
            .little_cpus
            .iter()
            .copied()
            .filter(|&id| id != 0)
            .collect();
        if !non_zero.is_empty() {
            return non_zero[slot % non_zero.len()];
        }
        *self.little_cpus.first().unwrap_or(&0)
    }
}

pub struct IrqTopology;

impl IrqTopology {
    const TOUCH_IRQ_KEYWORDS: &'static [&'static str] = &[
        "novatek",
        "nvt",
        "goodix",
        "gt9",
        "focaltech",
        "fts",
        "synaptics",
        "syna",
        "rmi4",
        "ilitek",
        "himax",
        "sec_ts",
        "atmel",
        "mxt",
        "elan",
        "silead",
        "chipone",
        "raydium",
        "parade",
        "touchscreen",
        "touch_dev",
        "touchpanel",
        "tpd",
        "touch",
    ];

    const FP_EXCLUSIONS: &'static [&'static str] = &[
        "fingerprint",
        "goodix_fp",
        "silead_fp",
        "fpc1020",
        "fpc_",
        "focal_fp",
    ];

    /// Parses a Linux cpulist string such as "0-3,6-7" into sorted deduplicated CPU IDs.
    pub fn parse_cpu_list(spec: &str) -> Vec<u32> {
        let mut set = BTreeSet::new();
        for part in spec.trim().split(',') {
            let token = part.trim();
            if token.is_empty() {
                continue;
            }
            if let Some((start_s, end_s)) = token.split_once('-') {
                if let (Ok(start), Ok(end)) = (start_s.trim().parse::<u32>(), end_s.trim().parse::<u32>()) {
                    let (lo, hi) = if start <= end { (start, end) } else { (end, start) };
                    for id in lo..=hi.min(lo.saturating_add(255)) {
                        set.insert(id);
                    }
                }
            } else if let Ok(id) = token.parse::<u32>() {
                set.insert(id);
            }
        }
        set.into_iter().collect()
    }

    /// Formats sorted CPU IDs into compact Linux cpulist syntax (e.g., "6-7" or "0-3,6-7").
    pub fn format_cpu_list(cpus: &[u32]) -> String {
        format_cpu_list(cpus)
    }

    /// Converts a Linux cpulist string (e.g., "6-7") into a lowercase hex affinity mask ("c0").
    pub fn cpu_list_to_hex_mask(spec: &str) -> Option<String> {
        let cpus = Self::parse_cpu_list(spec);
        if cpus.is_empty() {
            return None;
        }
        let mut mask = 0u128;
        for cpu in cpus {
            if cpu < 128 {
                mask |= 1u128 << cpu;
            }
        }
        if mask == 0 {
            None
        } else {
            Some(format!("{:x}", mask))
        }
    }

    fn read_online_cpus() -> Vec<u32> {
        for path in &[
            "/sys/devices/system/cpu/online",
            "/sys/devices/system/cpu/present",
            "/sys/devices/system/cpu/possible",
        ] {
            if let Ok(raw) = fs::read_to_string(path) {
                let parsed = Self::parse_cpu_list(&raw);
                if !parsed.is_empty() {
                    return parsed;
                }
            }
        }
        let mut fallback = Vec::new();
        if let Ok(entries) = fs::read_dir("/sys/devices/system/cpu") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                if let Some(rest) = name.strip_prefix("cpu") {
                    if let Ok(id) = rest.parse::<u32>() {
                        fallback.push(id);
                    }
                }
            }
        }
        fallback.sort_unstable();
        if fallback.is_empty() {
            vec![0]
        } else {
            fallback
        }
    }

    fn read_cpu_capacity(cpu: u32) -> Option<u32> {
        let p = format!("/sys/devices/system/cpu/cpu{}/cpu_capacity", cpu);
        fs::read_to_string(p).ok()?.trim().parse::<u32>().ok()
    }

    fn read_policy_clusters() -> Vec<CpuClusterInfo> {
        let mut clusters = Vec::new();
        let policy_dir = Path::new("/sys/devices/system/cpu/cpufreq");
        if let Ok(entries) = fs::read_dir(policy_dir) {
            let mut dirs: Vec<_> = entries
                .flatten()
                .filter(|e| e.file_name().to_string_lossy().starts_with("policy"))
                .collect();
            dirs.sort_by_key(|e| e.file_name());
            for entry in dirs {
                let p = entry.path();
                let cpus_raw = fs::read_to_string(p.join("related_cpus"))
                    .or_else(|_| fs::read_to_string(p.join("affected_cpus")))
                    .unwrap_or_default();
                let cpus = Self::parse_cpu_list(&cpus_raw);
                if cpus.is_empty() {
                    continue;
                }
                let max_freq_khz = fs::read_to_string(p.join("cpuinfo_max_freq"))
                    .or_else(|_| fs::read_to_string(p.join("scaling_max_freq")))
                    .ok()
                    .and_then(|s| s.trim().parse::<u64>().ok());
                let capacity = cpus.iter().filter_map(|&id| Self::read_cpu_capacity(id)).max();
                clusters.push(CpuClusterInfo {
                    cpus,
                    capacity,
                    max_freq_khz,
                });
            }
        }
        clusters
    }

    /// Probes the live SoC CPU cluster layout without hardcoded core counts or indices.
    pub fn probe_clusters() -> ClusterRanges {
        let online = Self::read_online_cpus();
        let mut clusters = Self::read_policy_clusters();
        if clusters.is_empty() {
            let mut by_cap: std::collections::BTreeMap<u32, Vec<u32>> =
                std::collections::BTreeMap::new();
            for &cpu in &online {
                by_cap
                    .entry(Self::read_cpu_capacity(cpu).unwrap_or(0))
                    .or_default()
                    .push(cpu);
            }
            clusters = by_cap
                .into_iter()
                .map(|(cap, cpus)| CpuClusterInfo {
                    cpus,
                    capacity: (cap > 0).then_some(cap),
                    max_freq_khz: None,
                })
                .collect();
        }
        ClusterRanges::from_clusters(&online, &clusters)
    }

    pub fn is_touch_irq_name(line_lower: &str) -> bool {
        if Self::FP_EXCLUSIONS.iter().any(|ex| line_lower.contains(ex)) {
            return false;
        }
        Self::TOUCH_IRQ_KEYWORDS
            .iter()
            .any(|kw| line_lower.contains(kw))
    }

    /// Extracts specific SPI/I2C controller tokens (e.g. "11013000.spi3", "c80000.i2c", "i2c-3") from sysfs path.
    pub fn extract_bus_tokens_from_sysfs_path(path_str: &str) -> Vec<String> {
        let mut tokens = Vec::new();
        let mut push_unique = |tok: String| {
            if !tok.is_empty() && !tokens.contains(&tok) {
                tokens.push(tok);
            }
        };
        for seg in path_str.split(['/', '\\']) {
            let lower = seg.trim().to_ascii_lowercase();
            if lower.contains(".spi") || lower.contains(".i2c") {
                push_unique(lower.clone());
                if let Some((_, bus)) = lower.split_once('.') {
                    if bus.chars().last().is_some_and(|c| c.is_ascii_digit()) {
                        push_unique(bus.to_string());
                    }
                }
            } else if (lower.starts_with("i2c-") || lower.starts_with("spi"))
                && !lower.contains('_')
                && !lower.contains('.')
                && lower.chars().last().is_some_and(|c| c.is_ascii_digit())
            {
                push_unique(lower);
            }
        }
        tokens
    }

    /// Discovers SPI/I2C controller tokens associated with direct touchscreen input devices.
    pub fn discover_touch_controller_tokens() -> Vec<String> {
        let mut out = BTreeSet::new();
        let Ok(entries) = fs::read_dir("/sys/class/input") else {
            return Vec::new();
        };
        for entry in entries.flatten() {
            let fname = entry.file_name().to_string_lossy().to_string();
            if !fname.starts_with("input") {
                continue;
            }
            let input_dir = entry.path();
            let dev_name = fs::read_to_string(input_dir.join("name"))
                .unwrap_or_default()
                .trim()
                .to_ascii_lowercase();
            if Self::FP_EXCLUSIONS.iter().any(|ex| dev_name.contains(ex)) {
                continue;
            }
            let props_hex = fs::read_to_string(input_dir.join("properties"))
                .unwrap_or_default()
                .trim()
                .to_string();
            let is_direct = u64::from_str_radix(&props_hex, 16)
                .map(|mask| (mask & 0x2) != 0)
                .unwrap_or(false);
            if !is_direct && !Self::is_touch_irq_name(&dev_name) {
                continue;
            }
            if let Ok(real) = fs::canonicalize(input_dir.join("device")) {
                for tok in Self::extract_bus_tokens_from_sysfs_path(&real.to_string_lossy()) {
                    out.insert(tok);
                }
            }
        }
        out.into_iter().collect()
    }

    /// Parses `/proc/interrupts` content and returns `(irq_num, description)` pairs for touch/controller IRQs.
    pub fn find_touch_irqs_in_proc(
        interrupts_text: &str,
        controller_tokens: &[String],
    ) -> Vec<(u32, String)> {
        let mut found = Vec::new();
        for line in interrupts_text.lines() {
            let Some((irq_s, rest)) = line.split_once(':') else {
                continue;
            };
            let Ok(irq_num) = irq_s.trim().parse::<u32>() else {
                continue;
            };
            let lower = rest.to_ascii_lowercase();
            let matches_ctrl = controller_tokens.iter().any(|tok| lower.contains(tok));
            if matches_ctrl || Self::is_touch_irq_name(&lower) {
                found.push((irq_num, rest.trim().to_string()));
            }
        }
        found
    }

    /// Pins matching IRQ kernel threads and touch/bus worker kthreads using `taskset -p`.
    pub fn pin_matching_kthreads(irq_nums: &[u32], controller_tokens: &[String], target_mask: &str) -> usize {
        let mut pinned = 0usize;
        let Ok(entries) = fs::read_dir("/proc") else {
            return 0;
        };
        for entry in entries.flatten() {
            let pid_str = entry.file_name().to_string_lossy().to_string();
            if !pid_str.chars().all(|c| c.is_ascii_digit()) {
                continue;
            }
            let pid_path = entry.path();
            // Kernel threads expose empty cmdline
            let cmdline = fs::read(pid_path.join("cmdline")).unwrap_or_default();
            if !cmdline.is_empty() {
                continue;
            }
            let Ok(comm_raw) = fs::read_to_string(pid_path.join("comm")) else {
                continue;
            };
            let comm = comm_raw.trim().to_ascii_lowercase();
            let matches_irq = irq_nums
                .iter()
                .any(|n| comm.starts_with(&format!("irq/{}-", n)));
            let matches_ctrl = controller_tokens.iter().any(|tok| comm.contains(tok));
            let matches_touch_kthread = !Self::FP_EXCLUSIONS.iter().any(|ex| comm.contains(ex))
                && (comm.starts_with("nvt_")
                    || comm.starts_with("fts_")
                    || comm.starts_with("goodix_")
                    || comm.starts_with("sec_ts")
                    || comm.contains("touch"));
            if matches_irq || matches_ctrl || matches_touch_kthread {
                for bin in &["taskset", "/system/bin/taskset"] {
                    if let Ok(st) = Command::new(bin).args(["-p", target_mask, &pid_str]).status() {
                        if st.success() {
                            pinned += 1;
                            break;
                        }
                    }
                }
            }
        }
        pinned
    }

    /// Discovers touch + parent SPI/I2C IRQs and applies either big-cluster or all-CPU affinity
    /// across both `/proc/irq/*/smp_affinity_list` and matching kernel threads.
    pub fn apply_touch_irq_and_kthreads(big_cores: bool) -> (usize, usize) {
        let clusters = Self::probe_clusters();
        let target_list = if big_cores {
            clusters.big_cpu_range.as_str()
        } else {
            clusters.all_cpu_range.as_str()
        };
        let Ok(interrupts) = fs::read_to_string("/proc/interrupts") else {
            return (0, 0);
        };
        let tokens = Self::discover_touch_controller_tokens();
        let irqs = Self::find_touch_irqs_in_proc(&interrupts, &tokens);
        let mut irq_count = 0usize;
        let mut irq_nums = Vec::with_capacity(irqs.len());
        for (irq, _) in &irqs {
            irq_nums.push(*irq);
            let node = format!("/proc/irq/{}/smp_affinity_list", irq);
            if Path::new(&node).exists() && fs::write(&node, target_list).is_ok() {
                irq_count += 1;
            }
        }
        let kthread_count = if let Some(hex_mask) = Self::cpu_list_to_hex_mask(target_list) {
            Self::pin_matching_kthreads(&irq_nums, &tokens, &hex_mask)
        } else {
            0
        };
        (irq_count, kthread_count)
    }
}

fn format_cpu_list(cpus: &[u32]) -> String {
    if cpus.is_empty() {
        return String::new();
    }
    let mut sorted = cpus.to_vec();
    sorted.sort_unstable();
    sorted.dedup();
    let mut ranges = Vec::new();
    let mut start = sorted[0];
    let mut prev = sorted[0];
    for &c in &sorted[1..] {
        if c == prev + 1 {
            prev = c;
        } else {
            ranges.push(if start == prev {
                start.to_string()
            } else {
                format!("{}-{}", start, prev)
            });
            start = c;
            prev = c;
        }
    }
    ranges.push(if start == prev {
        start.to_string()
    } else {
        format!("{}-{}", start, prev)
    });
    ranges.join(",")
}

#[cfg(test)]
mod tests {
    use super::{ClusterRanges, CpuClusterInfo, IrqTopology};

    #[test]
    fn resolves_helio_g96_asymmetric_big_cluster_from_freq_or_capacity() {
        let online: Vec<u32> = (0..8).collect();
        let clusters = vec![
            CpuClusterInfo {
                cpus: (0..6).collect(),
                capacity: Some(500),
                max_freq_khz: Some(2_000_000),
            },
            CpuClusterInfo {
                cpus: (6..8).collect(),
                capacity: Some(1024),
                max_freq_khz: Some(2_050_000),
            },
        ];
        let ranges = ClusterRanges::from_clusters(&online, &clusters);
        assert!(ranges.is_asymmetric);
        assert_eq!(ranges.all_cpu_range, "0-7");
        assert_eq!(ranges.little_cpu_range, "0-5");
        assert_eq!(ranges.big_cpu_range, "6-7");
        assert_eq!(ranges.top_cpu_range, "6-7");
        assert_eq!(ranges.little_offload_core(0), 1);

        // Even without cpu_capacity, 2050 MHz vs 2000 MHz separates 6-7 cleanly
        let no_cap_clusters = vec![
            CpuClusterInfo {
                cpus: (0..6).collect(),
                capacity: None,
                max_freq_khz: Some(2_000_000),
            },
            CpuClusterInfo {
                cpus: (6..8).collect(),
                capacity: None,
                max_freq_khz: Some(2_050_000),
            },
        ];
        let ranges_nocap = ClusterRanges::from_clusters(&online, &no_cap_clusters);
        assert_eq!(ranges_nocap.big_cpu_range, "6-7");
    }

    #[test]
    fn resolves_sdm660_4plus4_and_tricluster_1plus3plus4() {
        let online: Vec<u32> = (0..8).collect();
        let sdm660 = vec![
            CpuClusterInfo {
                cpus: vec![0, 1, 2, 3],
                capacity: Some(716),
                max_freq_khz: Some(1_843_200),
            },
            CpuClusterInfo {
                cpus: vec![4, 5, 6, 7],
                capacity: Some(1024),
                max_freq_khz: Some(2_208_000),
            },
        ];
        let r660 = ClusterRanges::from_clusters(&online, &sdm660);
        assert_eq!(r660.little_cpu_range, "0-3");
        assert_eq!(r660.big_cpu_range, "4-7");

        let tri = vec![
            CpuClusterInfo {
                cpus: vec![0, 1, 2, 3],
                capacity: Some(360),
                max_freq_khz: Some(1_804_800),
            },
            CpuClusterInfo {
                cpus: vec![4, 5, 6],
                capacity: Some(860),
                max_freq_khz: Some(2_500_000),
            },
            CpuClusterInfo {
                cpus: vec![7],
                capacity: Some(1024),
                max_freq_khz: Some(3_050_000),
            },
        ];
        let rtri = ClusterRanges::from_clusters(&online, &tri);
        assert_eq!(rtri.little_cpu_range, "0-3");
        assert_eq!(rtri.big_cpu_range, "4-7");
        assert_eq!(rtri.top_cpu_range, "7");
    }

    #[test]
    fn resolves_symmetric_smp_without_shrinking_affinity() {
        let online = vec![0, 1, 2, 3];
        let smp = vec![CpuClusterInfo {
            cpus: vec![0, 1, 2, 3],
            capacity: Some(1024),
            max_freq_khz: Some(1_500_000),
        }];
        let rsmp = ClusterRanges::from_clusters(&online, &smp);
        assert!(!rsmp.is_asymmetric);
        assert_eq!(rsmp.all_cpu_range, "0-3");
        assert_eq!(rsmp.big_cpu_range, "0-3");
        assert_eq!(rsmp.little_cpu_range, "0-3");
    }

    #[test]
    fn converts_cpulist_to_hex_mask_accurately() {
        assert_eq!(IrqTopology::cpu_list_to_hex_mask("6-7").as_deref(), Some("c0"));
        assert_eq!(IrqTopology::cpu_list_to_hex_mask("4-7").as_deref(), Some("f0"));
        assert_eq!(IrqTopology::cpu_list_to_hex_mask("0-7").as_deref(), Some("ff"));
        assert_eq!(IrqTopology::cpu_list_to_hex_mask("0-3,6-7").as_deref(), Some("cf"));
        assert_eq!(IrqTopology::cpu_list_to_hex_mask(""), None);
    }

    #[test]
    fn discovers_bus_tokens_and_filters_fingerprint_irqs() {
        let tokens = IrqTopology::extract_bus_tokens_from_sysfs_path(
            "/sys/devices/platform/11013000.spi3/spi_master/spi3/spi3.0",
        );
        assert_eq!(tokens, vec!["11013000.spi3".to_string(), "spi3".to_string()]);

        let qcom_tokens = IrqTopology::extract_bus_tokens_from_sysfs_path(
            "/sys/devices/platform/soc/c80000.i2c/i2c-3/3-0038",
        );
        assert_eq!(qcom_tokens, vec!["c80000.i2c".to_string(), "i2c-3".to_string()]);

        let interrupts = "\
 91:  1234  0  GICv3  11013000.spi3
 92:  8891  0  mtk-eint  NVT-ts
 93:   420  0  mtk-eint  silead_fp
 94:   110  0  mtk-eint  goodix_fp_irq
 95:   555  0  qcom-gpio  synaptics_rmi4_i2c";
        let found = IrqTopology::find_touch_irqs_in_proc(interrupts, &tokens);
        let irq_ids: Vec<u32> = found.iter().map(|(id, _)| *id).collect();
        assert_eq!(irq_ids, vec![91, 92, 95]);
    }
}
