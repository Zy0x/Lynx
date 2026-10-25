use std::fmt::Write as FmtWrite;
use std::fs;
use std::path::Path;
use std::process::Command;

use crate::hardware::capability::HardwareCapability;
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

pub struct NetworkController;

impl NetworkController {
    fn write_opt(path: impl AsRef<Path>, val: impl AsRef<str>) {
        let p = path.as_ref();
        if p.exists() {
            let _ = SysfsWriter::write(p, val, WriteMode::ForcePermission);
        }
    }

    /// Dynamically compute hex bitmask of all online CPU cores (supports 4, 6, 8, 10, 12+ cores).
    fn resolve_online_cpu_mask_hex() -> String {
        let mut mask: u64 = 0;
        for idx in 0..32 {
            let cpu_dir = format!("/sys/devices/system/cpu/cpu{}", idx);
            if !Path::new(&cpu_dir).exists() {
                continue;
            }
            let online_file = format!("{}/online", cpu_dir);
            if Path::new(&online_file).exists() {
                if SysfsReader::read_trimmed(&online_file).unwrap_or_else(|_| "1".to_string()) == "0" {
                    continue;
                }
            }
            mask |= 1u64 << idx;
        }
        if mask == 0 {
            mask = 0xff;
        }
        format!("{:x}", mask)
    }

    /// Dynamically scale TCP/socket buffers & netdev budgets based on physical RAM size.
    fn resolve_ram_scaled_params() -> (u64, u64, u64, u64, u64) {
        let mem = HardwareCapability::resolve_memory();
        if mem.total_ram_mb >= 7500 {
            // 8GB+ RAM
            (16_777_216, 262_144, 16_384, 600, 32_768)
        } else if mem.total_ram_mb >= 3800 {
            // 4GB - 6GB RAM
            (8_388_608, 131_072, 8_192, 450, 16_384)
        } else {
            // < 4GB RAM
            (4_194_304, 65_536, 4_096, 300, 8_192)
        }
    }

    /// Dynamically discover all active physical/wireless/cellular interfaces and steer RX/TX queues.
    fn apply_dynamic_rps_xps(enable: bool) {
        let cpu_hex = if enable {
            Self::resolve_online_cpu_mask_hex()
        } else {
            "0".to_string()
        };
        let (_, _, _, _, total_flows) = Self::resolve_ram_scaled_params();
        Self::write_opt(
            "/proc/sys/net/core/rps_sock_flow_entries",
            if enable { total_flows.to_string() } else { "0".to_string() },
        );

        let net_root = Path::new("/sys/class/net");
        let Ok(entries) = fs::read_dir(net_root) else {
            return;
        };

        for entry in entries.flatten() {
            let iface = entry.file_name().to_string_lossy().to_string();
            if iface == "lo"
                || iface.starts_with("dummy")
                || iface.starts_with("sit")
                || iface.starts_with("ip6tnl")
                || iface.starts_with("bond")
                || iface.starts_with("tun")
                || iface.starts_with("tap")
                || iface.starts_with("ifb")
            {
                continue;
            }

            let queues_dir = entry.path().join("queues");
            let Ok(q_entries) = fs::read_dir(&queues_dir) else {
                continue;
            };

            let mut rx_dirs = Vec::new();
            let mut tx_dirs = Vec::new();
            for qe in q_entries.flatten() {
                let qname = qe.file_name().to_string_lossy().to_string();
                if qname.starts_with("rx-") {
                    rx_dirs.push(qe.path());
                } else if qname.starts_with("tx-") {
                    tx_dirs.push(qe.path());
                }
            }

            let flow_per_rx = if enable && !rx_dirs.is_empty() {
                (total_flows / (rx_dirs.len() as u64)).max(1024)
            } else {
                0
            };

            for rx in rx_dirs {
                Self::write_opt(rx.join("rps_cpus"), &cpu_hex);
                Self::write_opt(rx.join("rps_flow_cnt"), flow_per_rx.to_string());
            }
            for tx in tx_dirs {
                Self::write_opt(tx.join("xps_cpus"), &cpu_hex);
            }
        }
    }

    /// Dynamically probe and configure vendor-specific Wi-Fi driver capabilities without hardcoding.
    fn apply_dynamic_wifi_silicon(low_latency: bool) {
        // 1. MediaTek Gen3/Gen4m (/proc/net/wlan) — inspect supported keys dynamically before writing
        let dbg_path = Path::new("/proc/net/wlan/dbgLevel");
        if dbg_path.exists() {
            let raw_dbg = SysfsReader::read_trimmed(dbg_path).unwrap_or_default();
            if raw_dbg.contains(':') {
                Self::write_opt(dbg_path, "0xff:0x03");
            } else {
                Self::write_opt(dbg_path, "0");
            }
        }

        Self::write_opt(
            "/proc/net/wlan/autoPerfCfg",
            if low_latency { "ForceEnable:1" } else { "ForceEnable:0" },
        );
        Self::write_opt("/proc/net/wlan/setCAM", if low_latency { "1" } else { "0" });
        Self::write_opt("/proc/net/wlan/pkt_filter", if low_latency { "0" } else { "1" });

        let cfg_path = Path::new("/proc/net/wlan/cfg");
        if cfg_path.exists() {
            let supported = SysfsReader::read_trimmed(cfg_path).unwrap_or_default();
            let candidates: &[(&str, &str)] = if low_latency {
                &[
                    ("Sta2gBw", "1"),
                    ("TxopPsTx", "0"),
                    ("VhtAmsduInAmpduTx", "1"),
                    ("HtAmsduInAmpduTx", "1"),
                    ("VhtAmsduInAmpduRx", "1"),
                    ("HtAmsduInAmpduRx", "1"),
                    ("PowerSave", "0"),
                    ("AdvPws", "0"),
                    ("DisOnlineScan", "1"),
                    ("ThreadSched", "1"),
                ]
            } else {
                &[
                    ("Sta2gBw", "1"),
                    ("PowerSave", "1"),
                    ("DisOnlineScan", "0"),
                ]
            };

            for (key, val) in candidates {
                let marker = format!("D:{}|", key);
                let marker_alt = format!("{}|", key);
                if supported.is_empty() || supported.contains(&marker) || supported.contains(&marker_alt) {
                    Self::write_opt(cfg_path, format!("{} {}", key, val));
                }
            }
        }

        // 2. Qualcomm WCNSS / QCACLD / CNSS (/sys/module/*wlan*/parameters)
        if let Ok(mod_entries) = fs::read_dir("/sys/module") {
            for entry in mod_entries.flatten() {
                let mname = entry.file_name().to_string_lossy().to_string();
                let params = entry.path().join("parameters");
                if !params.is_dir() {
                    continue;
                }
                if mname.contains("wlan") || mname.contains("qca") || mname.contains("cnss") {
                    Self::write_opt(params.join("power_save"), if low_latency { "0" } else { "1" });
                    Self::write_opt(params.join("con_mode"), "0");
                    Self::write_opt(params.join("rx_mode"), "1");
                    Self::write_opt(params.join("gEnablePacketFilter"), if low_latency { "0" } else { "1" });
                    Self::write_opt(params.join("dynamic_ps"), if low_latency { "0" } else { "1" });
                } else if mname == "dhd" || mname == "bcmdhd" {
                    Self::write_opt(params.join("dhd_slpauto"), if low_latency { "0" } else { "1" });
                    Self::write_opt(params.join("dhd_watchdog_ms"), if low_latency { "0" } else { "10" });
                    Self::write_opt(params.join("dhd_pkt_filter_enable"), if low_latency { "0" } else { "1" });
                    Self::write_opt(params.join("op_mode"), if low_latency { "1" } else { "0" });
                } else if mname == "scsc_wlan" {
                    Self::write_opt(params.join("slp_enable"), if low_latency { "0" } else { "1" });
                } else if mname.starts_with("sprdwl") {
                    Self::write_opt(params.join("auto_power"), if low_latency { "0" } else { "1" });
                    Self::write_opt(params.join("rx_mode"), "1");
                    Self::write_opt(params.join("packet_filter"), if low_latency { "0" } else { "1" });
                }
            }
        }

        // 3. Unisoc /proc/sprdwlan
        Self::write_opt("/proc/sprdwlan/auto_power", if low_latency { "0" } else { "1" });
        Self::write_opt("/proc/sprdwlan/dynamic_ps", if low_latency { "0" } else { "1" });

        // 4. Dynamic wireless kthread scheduling across MediaTek, Qualcomm, Exynos/Tensor, Broadcom, Unisoc
        if let Ok(proc_entries) = fs::read_dir("/proc") {
            for pe in proc_entries.flatten() {
                let fname = pe.file_name().to_string_lossy().to_string();
                if !fname.chars().all(|c| c.is_ascii_digit()) {
                    continue;
                }
                let Ok(comm) = SysfsReader::read_trimmed(pe.path().join("comm")) else {
                    continue;
                };
                let is_wifi_kthread = matches!(
                    comm.as_str(),
                    "main_thread"
                        | "hif_thread"
                        | "rx_thread"
                        | "cds_ol_rx_threa"
                        | "cds_mc_thread"
                        | "dhd_dpc"
                        | "dhd_rxf"
                        | "hip4_bh"
                        | "hip5_bh"
                        | "sprdwl_rx"
                        | "sprdwl_tx"
                ) || comm.starts_with("napi/wlan");

                if is_wifi_kthread {
                    if low_latency {
                        let _ = Command::new("renice").args(["-n", "-20", "-p", &fname]).status();
                        let _ = Command::new("chrt").args(["-f", "-p", "50", &fname]).status();
                    } else {
                        let _ = Command::new("chrt").args(["-o", "-p", "0", &fname]).status();
                        let _ = Command::new("renice").args(["-n", "-10", "-p", &fname]).status();
                    }
                }
            }
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
            for qdisc in &["fq_codel", "fq", "cake"] {
                if SysfsWriter::write("/proc/sys/net/core/default_qdisc", qdisc, WriteMode::ForcePermission).is_ok() {
                    let cur = SysfsReader::read_trimmed("/proc/sys/net/core/default_qdisc").unwrap_or_default();
                    if cur == *qdisc {
                        break;
                    }
                }
            }
        }

        let (max_buf, def_buf, backlog, budget, _) = Self::resolve_ram_scaled_params();
        let max_s = max_buf.to_string();
        let def_s = def_buf.to_string();

        Self::write_opt("/proc/sys/net/core/rmem_max", &max_s);
        Self::write_opt("/proc/sys/net/core/wmem_max", &max_s);
        Self::write_opt("/proc/sys/net/core/rmem_default", &def_s);
        Self::write_opt("/proc/sys/net/core/wmem_default", &def_s);
        Self::write_opt("/proc/sys/net/core/netdev_max_backlog", backlog.to_string());
        Self::write_opt("/proc/sys/net/core/netdev_budget", budget.to_string());
        Self::write_opt("/proc/sys/net/core/netdev_budget_usecs", "8000");
        Self::write_opt("/proc/sys/net/ipv4/tcp_rmem", format!("4096 87380 {}", max_buf));
        Self::write_opt("/proc/sys/net/ipv4/tcp_wmem", format!("4096 65536 {}", max_buf));
        Self::write_opt("/proc/sys/net/ipv4/tcp_ecn", "1");
        Self::write_opt("/proc/sys/net/ipv4/tcp_fastopen", "3");
        Self::write_opt("/proc/sys/net/ipv4/tcp_timestamps", "0");
        Self::write_opt("/proc/sys/net/ipv4/tcp_tw_reuse", "1");
        Self::write_opt("/proc/sys/net/ipv4/tcp_fin_timeout", "15");
        Self::write_opt("/proc/sys/net/ipv4/tcp_slow_start_after_idle", "0");
        Self::write_opt("/proc/sys/net/ipv4/tcp_mtu_probing", "1");
        Self::write_opt("/proc/sys/net/ipv4/tcp_notsent_lowat", "16384");

        Self::apply_dynamic_rps_xps(true);
        Self::apply_dynamic_wifi_silicon(false);

        format!("Universal network stack optimized (TCP Congestion: {}).", net.best_tcp_algo)
    }

    pub fn apply_game() -> String {
        let _ = Self::apply_universal();
        let _ = Command::new("cmd").args(["wifi", "set-power-save-mode", "0"]).status();
        let _ = Command::new("cmd").args(["wifi", "force-low-latency-mode", "enabled"]).status();

        if let Ok(entries) = fs::read_dir("/sys/class/net") {
            for e in entries.flatten() {
                let name = e.file_name().to_string_lossy().to_string();
                if name.starts_with("wlan") || name.starts_with("swlan") || name.starts_with("wifi") || name.starts_with("sta") {
                    let _ = Command::new("iw").args(["dev", &name, "set", "power_save", "off"]).status();
                }
            }
        }

        Self::apply_dynamic_rps_xps(true);
        Self::apply_dynamic_wifi_silicon(true);

        Self::write_opt("/proc/sys/net/ipv4/tcp_low_latency", "1");
        Self::write_opt("/proc/sys/net/ipv4/tcp_autocorking", "0");
        Self::write_opt("/proc/sys/net/ipv4/tcp_notsent_lowat", "16384");

        "Gaming low-latency network & Wi-Fi DTIM bypass enabled.".to_string()
    }

    pub fn apply_balance() -> String {
        let _ = Command::new("cmd").args(["wifi", "set-power-save-mode", "1"]).status();
        let _ = Command::new("cmd").args(["wifi", "force-low-latency-mode", "disabled"]).status();

        if let Ok(entries) = fs::read_dir("/sys/class/net") {
            for e in entries.flatten() {
                let name = e.file_name().to_string_lossy().to_string();
                if name.starts_with("wlan") || name.starts_with("swlan") || name.starts_with("wifi") || name.starts_with("sta") {
                    let _ = Command::new("iw").args(["dev", &name, "set", "power_save", "on"]).status();
                }
            }
        }

        Self::apply_dynamic_wifi_silicon(false);
        Self::write_opt("/proc/sys/net/ipv4/tcp_low_latency", "0");
        Self::write_opt("/proc/sys/net/ipv4/tcp_autocorking", "1");

        "Balanced power-efficient network & Wi-Fi mode restored.".to_string()
    }
}
