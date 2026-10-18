use std::path::{Path, PathBuf};
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

#[derive(Debug, Clone)]
pub struct MemlatNodeInfo {
    pub path: String,
    pub cur_freq: String,
    pub min_freq: String,
    pub governor: String,
}

pub struct MemlatController;

impl MemlatController {
    /// Detects all active memory interconnect bus devfreq nodes.
    pub fn detect_nodes() -> Vec<PathBuf> {
        let mut nodes = Vec::new();
        let candidates = [
            "/sys/class/devfreq/soc:qcom,cpu-llcc-ddr-bw",
            "/sys/class/devfreq/soc:qcom,cpu-cpu-llcc-bw",
            "/sys/devices/platform/soc/soc:qcom,gpubw/devfreq/soc:qcom,gpubw",
            "/sys/kernel/helio-dvfsrc/dvfsrc_req_ddr_opp",
        ];

        for &c in &candidates {
            let p = Path::new(c);
            if p.exists() {
                nodes.push(p.to_path_buf());
            }
        }
        nodes
    }

    /// Boosts memory bus floor to eliminate DRAM cache miss latency stalls during gaming.
    pub fn apply_gaming_boost() -> Result<(), String> {
        let nodes = Self::detect_nodes();
        for node in nodes {
            let gov_path = node.join("governor");
            if gov_path.exists() {
                let _ = SysfsWriter::write(&gov_path, "performance", WriteMode::ForcePermission);
            }

            let max_freq_path = node.join("max_freq");
            let min_freq_path = node.join("min_freq");
            if max_freq_path.exists() && min_freq_path.exists() {
                if let Ok(max_val) = SysfsReader::read_trimmed(&max_freq_path) {
                    let _ = SysfsWriter::write(&min_freq_path, &max_val, WriteMode::ForcePermission);
                }
            }
        }
        Ok(())
    }

    /// Restores memory bus back to dynamic power-saving scaling.
    pub fn restore_balanced() -> Result<(), String> {
        let nodes = Self::detect_nodes();
        for node in nodes {
            let gov_path = node.join("governor");
            if gov_path.exists() {
                let _ = SysfsWriter::write(&gov_path, "powersave", WriteMode::ForcePermission);
            }

            let min_freq_path = node.join("min_freq");
            if min_freq_path.exists() {
                let _ = SysfsWriter::write(&min_freq_path, "0", WriteMode::ForcePermission);
            }
        }
        Ok(())
    }
}
