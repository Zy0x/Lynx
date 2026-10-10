use std::fmt::Write as FmtWrite;
use std::path::Path;
use std::process::Command;

use crate::hardware::capability::HardwareCapability;
use crate::sysfs::{SysfsWriter, WriteMode};

pub struct NetworkController;

impl NetworkController {
    fn write_opt(path: impl AsRef<Path>, val: impl AsRef<str>) {
        let p = path.as_ref();
        if p.exists() {
            let _ = SysfsWriter::write(p, val, WriteMode::ForcePermission);
        }
    }

    pub fn info_json() -> String {
        let net = HardwareCapability::resolve_network();
        let algos: Vec<String> = net.available_tcp_algos.iter().map(|a| format!("\"{}\"", a)).collect();
        let mut out = String::new();
        let _ = write!(
            out,
            "{{\"current_algo\":\"{}\",\"best_algo\":\"{}\",\"available_algos\":[{}]}}",
            net.current_tcp_algo,
            net.best_tcp_algo,
            algos.join(",")
        );
        out
    }

    pub fn set_tcp_algo(target: &str) -> Result<String, String> {
        let net = HardwareCapability::resolve_network();
        if !net.available_tcp_algos.is_empty() && !net.available_tcp_algos.iter().any(|a| a == target) {
            return Err(format!(
                "Algorithm '{}' is not supported by kernel. Available: {}",
                target,
                net.available_tcp_algos.join(" ")
            ));
        }
        Self::write_opt("/proc/sys/net/ipv4/tcp_congestion_control", target);
        Ok(format!("TCP congestion control set to '{}'.", target))
    }

    pub fn apply_universal() -> String {
        let net = HardwareCapability::resolve_network();
        if !net.best_tcp_algo.is_empty() {
            Self::write_opt("/proc/sys/net/ipv4/tcp_congestion_control", &net.best_tcp_algo);
        }

        if Path::new("/proc/sys/net/core/default_qdisc").exists() {
            if SysfsWriter::write("/proc/sys/net/core/default_qdisc", "fq_codel", WriteMode::ForcePermission).is_err() {
                Self::write_opt("/proc/sys/net/core/default_qdisc", "fq");
            }
        }

        Self::write_opt("/proc/sys/net/core/rmem_max", "16777216");
        Self::write_opt("/proc/sys/net/core/wmem_max", "16777216");
        Self::write_opt("/proc/sys/net/core/rmem_default", "262144");
        Self::write_opt("/proc/sys/net/core/wmem_default", "262144");
        Self::write_opt("/proc/sys/net/ipv4/tcp_rmem", "4096 87380 16777216");
        Self::write_opt("/proc/sys/net/ipv4/tcp_wmem", "4096 65536 16777216");
        Self::write_opt("/proc/sys/net/ipv4/tcp_ecn", "1");
        Self::write_opt("/proc/sys/net/ipv4/tcp_fastopen", "3");
        Self::write_opt("/proc/sys/net/ipv4/tcp_timestamps", "0");
        Self::write_opt("/proc/sys/net/ipv4/tcp_tw_reuse", "1");
        Self::write_opt("/proc/sys/net/ipv4/tcp_fin_timeout", "15");

        format!("Universal network stack optimized (TCP Congestion: {}).", net.best_tcp_algo)
    }

    pub fn apply_game() -> String {
        let _ = Self::apply_universal();
        let _ = Command::new("cmd").args(["wifi", "set-power-save-mode", "0"]).status();
        let _ = Command::new("cmd").args(["wifi", "force-low-latency-mode", "enabled"]).status();

        for node in &[
            "/sys/module/wlan/parameters/power_save",
            "/sys/module/bcmdhd/parameters/op_mode",
            "/sys/class/net/wlan0/queues/rx-0/rps_cpus",
        ] {
            Self::write_opt(node, "0");
        }

        Self::write_opt("/proc/sys/net/ipv4/tcp_low_latency", "1");
        Self::write_opt("/proc/sys/net/ipv4/tcp_autocorking", "0");
        Self::write_opt("/proc/sys/net/ipv4/tcp_notsent_lowat", "16384");

        "Gaming low-latency network & Wi-Fi DTIM bypass enabled.".to_string()
    }

    pub fn apply_balance() -> String {
        let _ = Command::new("cmd").args(["wifi", "set-power-save-mode", "1"]).status();
        let _ = Command::new("cmd").args(["wifi", "force-low-latency-mode", "disabled"]).status();

        Self::write_opt("/sys/module/wlan/parameters/power_save", "1");
        Self::write_opt("/proc/sys/net/ipv4/tcp_low_latency", "0");
        Self::write_opt("/proc/sys/net/ipv4/tcp_autocorking", "1");

        "Balanced power-efficient network & Wi-Fi mode restored.".to_string()
    }
}
