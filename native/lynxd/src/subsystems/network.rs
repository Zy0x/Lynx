use std::collections::HashMap;
use std::fmt::Write as FmtWrite;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

use crate::hardware::capability::HardwareCapability;
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

const PERSISTENT_BASELINE_PATH: &str = "/data/adb/lynx/network_baseline.conf";
const VOLATILE_BASELINE_PATH: &str = "/dev/.lynx_net_baseline.conf";
const QDISC_CACHE_PATH: &str = "/dev/.lynx_qdisc_caps";
const RADIO_OVERRIDE_LOCK_MARKER: &str = "/dev/.lynx_net_active_preset";

pub struct NetworkController;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum RadioVendor {
    MediaTekProc,
    QualcommCnss,
    BroadcomDhd,
    ExynosScsc,
    UnisocSprd,
    HalGeneric,
    Unsupported,
}

impl RadioVendor {
    fn id(self) -> &'static str {
        match self {
            RadioVendor::MediaTekProc => "mediatek_proc",
            RadioVendor::QualcommCnss => "qualcomm_cnss",
            RadioVendor::BroadcomDhd => "broadcom_dhd",
            RadioVendor::ExynosScsc => "exynos_scsc",
            RadioVendor::UnisocSprd => "unisoc_sprd",
            RadioVendor::HalGeneric => "hal_generic",
            RadioVendor::Unsupported => "unsupported",
        }
    }

    fn supports_vendor_sysfs(self) -> bool {
        matches!(
            self,
            RadioVendor::MediaTekProc
                | RadioVendor::QualcommCnss
                | RadioVendor::BroadcomDhd
                | RadioVendor::ExynosScsc
                | RadioVendor::UnisocSprd
        )
    }
}

#[derive(Debug, Clone)]
struct RadioProbe {
    vendor: RadioVendor,
    label: String,
    module_paths: Vec<PathBuf>,
}

#[derive(Debug, Clone)]
struct IfaceEntry {
    name: String,
    kind: String,
    operstate: String,
    mtu: u32,
    rx_queues: usize,
    tx_queues: usize,
    rps_writable: bool,
    qdisc: String,
}

#[derive(Debug, Clone, Default)]
struct ActiveRouteInfo {
    iface: String,
    kind: String,
    gateway_ip: String,
    src_ip: String,
}

#[derive(Debug, Clone, Default)]
struct ApplyOutcome {
    attempted: usize,
    verified: usize,
    unsupported: usize,
    notes: Vec<String>,
}

impl ApplyOutcome {
    fn record_write(&mut self, path: impl AsRef<Path>, val: &str) {
        let p = path.as_ref();
        if !p.exists() {
            self.unsupported += 1;
            return;
        }
        self.attempted += 1;
        if SysfsWriter::write(p, val, WriteMode::ForcePermission).is_ok() {
            let actual = SysfsReader::read_trimmed(p).unwrap_or_default();
            if normalize_ws(&actual) == normalize_ws(val) {
                self.verified += 1;
            }
        }
    }

    fn record_setting(&mut self, namespace: &str, key: &str, val: &str) {
        self.attempted += 1;
        if run_cmd_ok("settings", &["put", namespace, key, val]) {
            if let Some(cur) = run_cmd_stdout("settings", &["get", namespace, key]) {
                if cur.trim() == val.trim() {
                    self.verified += 1;
                }
            }
        }
    }

    fn record_cmd_ok(&mut self, bin: &str, args: &[&str]) {
        self.attempted += 1;
        if run_cmd_ok(bin, args) {
            self.verified += 1;
        }
    }

    fn merge(&mut self, other: ApplyOutcome) {
        self.attempted += other.attempted;
        self.verified += other.verified;
        self.unsupported += other.unsupported;
        self.notes.extend(other.notes);
    }

    fn status_code(&self) -> &'static str {
        if self.verified > 0 {
            if self.verified == self.attempted {
                "VERIFIED"
            } else {
                "PARTIAL"
            }
        } else if self.attempted == 0 && self.unsupported > 0 {
            "UNSUPPORTED"
        } else if self.attempted > 0 {
            "UNVERIFIED"
        } else {
            "UNSUPPORTED"
        }
    }

    fn to_json(&self, ok_msg: &str) -> String {
        let ok = self.verified > 0 || self.attempted > 0;
        let msg = if self.notes.is_empty() {
            ok_msg.to_string()
        } else {
            format!("{} ({})", ok_msg, self.notes.join("; "))
        };
        format!(
            "{{\"ok\":{},\"status\":{},\"attempted\":{},\"verified\":{},\"unsupported\":{},\"message\":{}}}",
            ok,
            json_str(self.status_code()),
            self.attempted,
            self.verified,
            self.unsupported,
            json_str(&msg)
        )
    }
}

fn normalize_ws(raw: &str) -> String {
    raw.split_whitespace().collect::<Vec<_>>().join(" ")
}

fn json_str(raw: &str) -> String {
    let mut out = String::with_capacity(raw.len() + 2);
    out.push('"');
    for ch in raw.chars() {
        match ch {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if c.is_control() => {
                let _ = write!(out, "\\u{:04x}", c as u32);
            }
            c => out.push(c),
        }
    }
    out.push('"');
    out
}

fn which_exists(bin: &str) -> bool {
    let common_dirs = [
        "/system/bin",
        "/system/xbin",
        "/vendor/bin",
        "/product/bin",
        "/apex/com.android.runtime/bin",
    ];
    for dir in &common_dirs {
        let candidate = Path::new(dir).join(bin);
        if candidate.is_file() {
            return true;
        }
    }
    Command::new("which")
        .arg(bin)
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status()
        .map(|s| s.success())
        .unwrap_or(false)
}

fn run_cmd_ok(bin: &str, args: &[&str]) -> bool {
    Command::new(bin)
        .args(args)
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status()
        .map(|s| s.success())
        .unwrap_or(false)
}

fn run_cmd_stdout(bin: &str, args: &[&str]) -> Option<String> {
    let out = Command::new(bin)
        .args(args)
        .stderr(Stdio::null())
        .output()
        .ok()?;
    let txt = String::from_utf8_lossy(&out.stdout).trim().to_string();
    if txt.is_empty() {
        None
    } else {
        Some(txt)
    }
}

fn get_android_sdk() -> u32 {
    run_cmd_stdout("getprop", &["ro.build.version.sdk"])
        .and_then(|s| s.parse::<u32>().ok())
        .unwrap_or(0)
}

fn active_baseline_path() -> PathBuf {
    let persistent_dir = Path::new("/data/adb/lynx");
    if persistent_dir.is_dir() || Path::new("/data/adb").is_dir() {
        let _ = fs::create_dir_all(persistent_dir);
        if persistent_dir.is_dir() {
            return PathBuf::from(PERSISTENT_BASELINE_PATH);
        }
    }
    PathBuf::from(VOLATILE_BASELINE_PATH)
}

fn baseline_entries_paths() -> &'static [(&'static str, &'static str)] {
    &[
        ("sysctl:net.ipv4.tcp_congestion_control", "/proc/sys/net/ipv4/tcp_congestion_control"),
        ("sysctl:net.core.default_qdisc", "/proc/sys/net/core/default_qdisc"),
        ("sysctl:net.core.rmem_max", "/proc/sys/net/core/rmem_max"),
        ("sysctl:net.core.wmem_max", "/proc/sys/net/core/wmem_max"),
        ("sysctl:net.core.rmem_default", "/proc/sys/net/core/rmem_default"),
        ("sysctl:net.core.wmem_default", "/proc/sys/net/core/wmem_default"),
        ("sysctl:net.ipv4.tcp_rmem", "/proc/sys/net/ipv4/tcp_rmem"),
        ("sysctl:net.ipv4.tcp_wmem", "/proc/sys/net/ipv4/tcp_wmem"),
        ("sysctl:net.ipv4.tcp_low_latency", "/proc/sys/net/ipv4/tcp_low_latency"),
        ("sysctl:net.ipv4.tcp_fastopen", "/proc/sys/net/ipv4/tcp_fastopen"),
        ("sysctl:net.ipv4.tcp_autocorking", "/proc/sys/net/ipv4/tcp_autocorking"),
        ("sysctl:net.ipv4.tcp_notsent_lowat", "/proc/sys/net/ipv4/tcp_notsent_lowat"),
        ("sysctl:net.ipv4.tcp_slow_start_after_idle", "/proc/sys/net/ipv4/tcp_slow_start_after_idle"),
        ("sysctl:net.ipv4.tcp_ecn", "/proc/sys/net/ipv4/tcp_ecn"),
        ("sysctl:net.ipv4.tcp_tw_reuse", "/proc/sys/net/ipv4/tcp_tw_reuse"),
        ("sysctl:net.ipv4.tcp_fin_timeout", "/proc/sys/net/ipv4/tcp_fin_timeout"),
        ("sysctl:net.ipv4.tcp_mtu_probing", "/proc/sys/net/ipv4/tcp_mtu_probing"),
        ("sysctl:net.ipv4.tcp_reordering", "/proc/sys/net/ipv4/tcp_reordering"),
        ("sysctl:net.core.netdev_max_backlog", "/proc/sys/net/core/netdev_max_backlog"),
        ("sysctl:net.core.netdev_budget", "/proc/sys/net/core/netdev_budget"),
        ("sysctl:net.core.netdev_budget_usecs", "/proc/sys/net/core/netdev_budget_usecs"),
        ("sysctl:net.core.somaxconn", "/proc/sys/net/core/somaxconn"),
        ("sysctl:net.core.rps_sock_flow_entries", "/proc/sys/net/core/rps_sock_flow_entries"),
        ("sysctl:net.ipv4.udp_mem", "/proc/sys/net/ipv4/udp_mem"),
        ("sysctl:net.ipv4.udp_rmem_min", "/proc/sys/net/ipv4/udp_rmem_min"),
        ("sysctl:net.ipv4.udp_wmem_min", "/proc/sys/net/ipv4/udp_wmem_min"),
        ("sysctl:net.ipv6.conf.all.disable_ipv6", "/proc/sys/net/ipv6/conf/all/disable_ipv6"),
    ]
}

fn baseline_setting_keys() -> &'static [&'static str] {
    &[
        "wifi_scan_always_enabled",
        "ble_scan_always_enabled",
        "wifi_scan_throttle_enabled",
        "wifi_framework_scan_interval_ms",
        "wifi_sleep_policy",
        "mobile_data_always_on",
        "wifi_watchdog_poor_network_test_enabled",
        "network_avoid_bad_wifi",
    ]
}

fn serialize_baseline_map(entries: &HashMap<String, String>) -> String {
    let mut keys: Vec<&String> = entries.keys().collect();
    keys.sort();
    let mut out = String::new();
    for k in keys {
        if let Some(v) = entries.get(k) {
            let safe_v = normalize_ws(v);
            if !safe_v.is_empty() {
                let _ = writeln!(out, "{}={}", k, safe_v);
            }
        }
    }
    out
}

fn parse_baseline_map(raw: &str) -> HashMap<String, String> {
    let mut map = HashMap::new();
    for line in raw.lines() {
        let trimmed = line.trim();
        if trimmed.is_empty() || trimmed.starts_with('#') {
            continue;
        }
        if let Some((k, v)) = trimmed.split_once('=') {
            map.insert(k.trim().to_string(), v.trim().to_string());
        }
    }
    map
}

fn classify_iface(name: &str) -> &'static str {
    let lower = name.to_ascii_lowercase();
    let sys_wireless = Path::new("/sys/class/net").join(name).join("wireless").exists();
    if sys_wireless
        || lower.starts_with("wlan")
        || lower.starts_with("swlan")
        || lower.starts_with("wifi")
        || lower.starts_with("wlp")
        || lower.starts_with("mlan")
        || lower.starts_with("sta")
    {
        "wifi"
    } else if lower.starts_with("rmnet")
        || lower.starts_with("ccmni")
        || lower.starts_with("seth")
        || lower.starts_with("wwan")
        || lower.starts_with("ipa")
        || lower.starts_with("v4-rmnet")
        || lower.starts_with("v4-ccmni")
    {
        "cellular"
    } else if lower.starts_with("rndis") || lower.starts_with("ncm") || lower.starts_with("usb") {
        "usb"
    } else if lower.starts_with("eth") || lower.starts_with("enp") {
        "ethernet"
    } else if lower.starts_with("tun")
        || lower.starts_with("tap")
        || lower.starts_with("wg")
        || lower.starts_with("ppp")
    {
        "vpn"
    } else {
        "other"
    }
}

fn should_skip_iface(name: &str) -> bool {
    name == "lo"
        || name.starts_with("dummy")
        || name.starts_with("sit")
        || name.starts_with("ip6tnl")
        || name.starts_with("ifb")
        || name.starts_with("bond")
        || name.starts_with("p2p")
}

fn is_iface_up(iface_path: &Path, operstate: &str) -> bool {
    if operstate.eq_ignore_ascii_case("up") {
        return true;
    }
    if operstate.eq_ignore_ascii_case("down") {
        return false;
    }
    // Virtual / P2P cellular netdevs (rmnet_data*, ccmni*, tun*) often report operstate="unknown" even when IFF_UP (0x1)
    if let Ok(flags_raw) = SysfsReader::read_trimmed(iface_path.join("flags")) {
        let hex = flags_raw.trim().trim_start_matches("0x").trim_start_matches("0X");
        if let Ok(flags) = u32::from_str_radix(hex, 16) {
            return (flags & 0x1) != 0;
        }
    }
    false
}

fn compute_cpu_mask_hex(prefer_avoid_cpu0: bool) -> String {
    let mut mask: u64 = 0;
    if let Ok(present_str) = SysfsReader::read_trimmed("/sys/devices/system/cpu/present") {
        for part in present_str.split(',') {
            let range: Vec<&str> = part.split('-').collect();
            if range.len() == 2 {
                if let (Ok(start), Ok(end)) =
                    (range[0].trim().parse::<u32>(), range[1].trim().parse::<u32>())
                {
                    for idx in start..=end.min(63) {
                        mask |= 1u64 << idx;
                    }
                }
            } else if range.len() == 1 {
                if let Ok(idx) = range[0].trim().parse::<u32>() {
                    if idx < 64 {
                        mask |= 1u64 << idx;
                    }
                }
            }
        }
    }
    if mask == 0 {
        for idx in 0..32 {
            let cpu_dir = format!("/sys/devices/system/cpu/cpu{}", idx);
            if Path::new(&cpu_dir).exists() {
                mask |= 1u64 << idx;
            }
        }
    }
    if mask == 0 {
        mask = 0xff;
    }
    if prefer_avoid_cpu0 && mask.count_ones() >= 4 && (mask & !1u64) != 0 {
        mask &= !1u64;
    }
    // On heterogeneous big.LITTLE SoCs, keep network RPS/XPS softirqs strictly inside the
    // non-prime/LITTLE cluster so Wi-Fi packets never trigger cross-cluster IPIs (TCPOFOQueue
    // out-of-order floods) or preempt high-FPS game render threads on Big/Prime cores.
    let cpu = HardwareCapability::resolve_cpu();
    if cpu.policies.len() >= 2 {
        if let Some(max_freq) = cpu.policies.iter().map(|p| p.max_freq).max() {
            let mut prime_mask = 0u64;
            for p in &cpu.policies {
                if p.max_freq == max_freq {
                    for tok in p.related_cpus.split_whitespace() {
                        if let Ok(c) = tok.parse::<u32>() {
                            if c < 64 {
                                prime_mask |= 1u64 << c;
                            }
                        }
                    }
                }
            }
            let non_prime = mask & !prime_mask;
            if non_prime != 0 {
                mask = non_prime;
            }
        }
    }
    format!("{:x}", mask)
}

fn parse_ip_route_line(line: &str) -> Option<(String, String, String)> {
    // Handles outputs like:
    // "1.1.1.1 via 192.168.0.1 dev wlan0 src 192.168.0.181 uid 0"
    // "1.1.1.1 dev rmnet_data0 table 1021 src 10.x.x.x uid 0"
    let tokens: Vec<&str> = line.split_whitespace().collect();
    let mut dev = String::new();
    let mut via = String::new();
    let mut src = String::new();
    let mut i = 0;
    while i < tokens.len() {
        match tokens[i] {
            "dev" if i + 1 < tokens.len() => {
                dev = tokens[i + 1].to_string();
                i += 2;
            }
            "via" if i + 1 < tokens.len() => {
                via = tokens[i + 1].to_string();
                i += 2;
            }
            "src" if i + 1 < tokens.len() => {
                src = tokens[i + 1].to_string();
                i += 2;
            }
            _ => i += 1,
        }
    }
    if dev.is_empty() {
        None
    } else {
        Some((dev, via, src))
    }
}

fn parse_ping_summary(stdout: &str) -> (bool, f32, f32, f32) {
    let mut loss_pct = 100.0f32;
    let mut avg_ms = 0.0f32;
    let mut jitter_ms = 0.0f32;

    for line in stdout.lines() {
        if line.contains("packet loss") {
            for part in line.split(',') {
                if part.contains("packet loss") {
                    if let Some(num_str) = part
                        .trim()
                        .split('%')
                        .next()
                        .and_then(|s| s.split_whitespace().last())
                    {
                        if let Ok(parsed) = num_str.parse::<f32>() {
                            loss_pct = parsed.clamp(0.0, 100.0);
                        }
                    }
                }
            }
        } else if line.contains("rtt ") || line.contains("round-trip ") {
            if let Some((_, rhs)) = line.split_once('=') {
                let stats_part = rhs.trim().split_whitespace().next().unwrap_or("");
                let nums: Vec<f32> = stats_part
                    .split('/')
                    .filter_map(|s| s.parse::<f32>().ok())
                    .collect();
                if nums.len() >= 2 {
                    avg_ms = nums[1];
                }
                if nums.len() >= 4 {
                    jitter_ms = nums[3];
                }
            }
        }
    }

    let reachable = loss_pct < 100.0 && avg_ms > 0.0;
    (reachable, avg_ms, jitter_ms, loss_pct)
}

impl NetworkController {
    fn write_opt(path: impl AsRef<Path>, val: impl AsRef<str>) {
        let p = path.as_ref();
        if p.exists() {
            let _ = SysfsWriter::write(p, val, WriteMode::ForcePermission);
        }
    }

    fn capture_baseline_if_missing() {
        let path = active_baseline_path();
        if path.exists() {
            return;
        }
        let mut map = HashMap::new();
        for (key, sysctl_path) in baseline_entries_paths() {
            if let Ok(val) = SysfsReader::read_trimmed(sysctl_path) {
                if !val.is_empty() {
                    map.insert((*key).to_string(), val);
                }
            }
        }
        for s_key in baseline_setting_keys() {
            if let Some(val) = run_cmd_stdout("settings", &["get", "global", s_key]) {
                let clean = val.trim().to_string();
                if !clean.is_empty() && clean != "null" {
                    map.insert(format!("setting:global.{}", s_key), clean);
                }
            }
        }
        if !map.is_empty() {
            let _ = fs::write(path, serialize_baseline_map(&map));
        }
    }

    fn load_baseline() -> HashMap<String, String> {
        let path = active_baseline_path();
        fs::read_to_string(path)
            .map(|c| parse_baseline_map(&c))
            .unwrap_or_default()
    }

    fn resolve_ram_scaled_params() -> (&'static str, u64, u64, u64, u64, u64, u64, &'static str, u64) {
        let mem = HardwareCapability::resolve_memory();
        if mem.total_ram_mb >= 7500 {
            (
                "8 GB+ RAM",
                33_554_432,
                524_288,
                32_768,
                800,
                32_768,
                4096,
                "393216 524288 786432",
                2048,
            )
        } else if mem.total_ram_mb >= 3800 {
            (
                "4–6 GB RAM",
                16_777_216,
                262_144,
                16_384,
                600,
                16_384,
                2048,
                "196608 262144 393216",
                1024,
            )
        } else if mem.total_ram_mb >= 2500 {
            (
                "3 GB RAM",
                8_388_608,
                131_072,
                8_192,
                400,
                16_384,
                1024,
                "98304 131072 196608",
                1024,
            )
        } else {
            (
                "< 2.5 GB RAM",
                4_194_304,
                65_536,
                4_096,
                300,
                8_192,
                1024,
                "98304 131072 196608",
                512,
            )
        }
    }

    #[cfg(unix)]
    fn is_tunable_param(path: &Path) -> bool {
        use std::os::unix::fs::PermissionsExt;
        fs::metadata(path)
            .map(|m| m.is_file() && (m.permissions().mode() & 0o222) != 0)
            .unwrap_or(false)
    }

    #[cfg(not(unix))]
    fn is_tunable_param(path: &Path) -> bool {
        fs::metadata(path)
            .map(|m| m.is_file() && !m.permissions().readonly())
            .unwrap_or(false)
    }

    fn detect_radio_hardware() -> RadioProbe {
        Self::probe_radio_hardware_in(
            Path::new("/sys/module"),
            Path::new("/proc/net/wlan"),
            Path::new("/proc/sprdwlan"),
            get_android_sdk(),
            which_exists("iw"),
        )
    }

    fn probe_radio_hardware_in(
        sys_module_root: &Path,
        mtk_proc_root: &Path,
        sprd_proc_root: &Path,
        sdk: u32,
        has_iw: bool,
    ) -> RadioProbe {
        let has_mtk_proc = mtk_proc_root.join("cfg").exists()
            || mtk_proc_root.join("setCAM").exists()
            || mtk_proc_root.join("autoPerfCfg").exists();
        if has_mtk_proc {
            return RadioProbe {
                vendor: RadioVendor::MediaTekProc,
                label: "MediaTek Wireless Engine (/proc/net/wlan)".to_string(),
                module_paths: vec![mtk_proc_root.to_path_buf()],
            };
        }

        const QCA_TUNABLE_KEYS: &[&str] = &[
            "power_save",
            "dynamic_ps",
            "gEnablePacketFilter",
            "gEnablePowerSaveOffload",
            "gPowerSaveMode",
        ];
        const DHD_TUNABLE_KEYS: &[&str] = &["dhd_slpauto", "op_mode", "dhd_pkt_filter_enable"];
        const SPRD_TUNABLE_KEYS: &[&str] = &["auto_power", "dynamic_ps", "packet_filter"];

        let mut qca_dirs = Vec::new();
        let mut dhd_dirs = Vec::new();
        let mut scsc_dirs = Vec::new();
        let mut sprd_dirs = Vec::new();
        let mut detected_qcom_module = false;

        if let Ok(mod_entries) = fs::read_dir(sys_module_root) {
            for entry in mod_entries.flatten() {
                let mname = entry.file_name().to_string_lossy().to_string();
                let params = entry.path().join("parameters");
                if !params.is_dir() {
                    continue;
                }
                if mname.contains("wlan") || mname.contains("qca") || mname.contains("cnss") {
                    if QCA_TUNABLE_KEYS
                        .iter()
                        .any(|k| Self::is_tunable_param(&params.join(k)))
                    {
                        qca_dirs.push(params);
                    } else if ["con_mode", "fwpath", "country_code"]
                        .iter()
                        .any(|k| params.join(k).exists())
                    {
                        detected_qcom_module = true;
                    }
                } else if (mname == "dhd" || mname == "bcmdhd")
                    && DHD_TUNABLE_KEYS
                        .iter()
                        .any(|k| Self::is_tunable_param(&params.join(k)))
                {
                    dhd_dirs.push(params);
                } else if mname == "scsc_wlan"
                    && Self::is_tunable_param(&params.join("slp_enable"))
                {
                    scsc_dirs.push(params);
                } else if mname.starts_with("sprdwl")
                    && SPRD_TUNABLE_KEYS
                        .iter()
                        .any(|k| Self::is_tunable_param(&params.join(k)))
                {
                    sprd_dirs.push(params);
                }
            }
        }

        if !qca_dirs.is_empty() {
            return RadioProbe {
                vendor: RadioVendor::QualcommCnss,
                label: "Qualcomm WCNSS / CNSS Driver".to_string(),
                module_paths: qca_dirs,
            };
        }
        if !dhd_dirs.is_empty() {
            return RadioProbe {
                vendor: RadioVendor::BroadcomDhd,
                label: "Broadcom DHD Wireless Driver".to_string(),
                module_paths: dhd_dirs,
            };
        }
        if !scsc_dirs.is_empty() {
            return RadioProbe {
                vendor: RadioVendor::ExynosScsc,
                label: "Samsung Exynos / Tensor SCSC Driver".to_string(),
                module_paths: scsc_dirs,
            };
        }
        if !sprd_dirs.is_empty() || sprd_proc_root.is_dir() {
            if sprd_proc_root.is_dir() {
                sprd_dirs.push(sprd_proc_root.to_path_buf());
            }
            return RadioProbe {
                vendor: RadioVendor::UnisocSprd,
                label: "Unisoc SPRDWLAN Driver".to_string(),
                module_paths: sprd_dirs,
            };
        }

        if sdk >= 29 || has_iw || detected_qcom_module {
            let nl = if has_iw { " + nl80211" } else { "" };
            let label = if detected_qcom_module {
                format!("Qualcomm WLAN (HAL API {}){}", sdk, nl)
            } else if has_iw {
                format!("Android Wi-Fi HAL (API {}) + nl80211", sdk)
            } else {
                format!("Android Wi-Fi HAL Only (API {})", sdk)
            };
            return RadioProbe {
                vendor: RadioVendor::HalGeneric,
                label,
                module_paths: Vec::new(),
            };
        }

        RadioProbe {
            vendor: RadioVendor::Unsupported,
            label: "Driver Radio Tidak Terbuka (Kernel Built-in)".to_string(),
            module_paths: Vec::new(),
        }
    }

    fn probe_available_qdiscs() -> Vec<String> {
        if let Ok(cached) = fs::read_to_string(QDISC_CACHE_PATH) {
            let list: Vec<String> = cached
                .split_whitespace()
                .map(|s| s.to_string())
                .collect();
            if !list.is_empty() {
                return list;
            }
        }

        let qdisc_path = Path::new("/proc/sys/net/core/default_qdisc");
        if !qdisc_path.exists() {
            return Vec::new();
        }
        let orig = SysfsReader::read_trimmed(qdisc_path).unwrap_or_default();
        let mut available = Vec::new();
        if !orig.is_empty() {
            available.push(orig.clone());
        }

        for cand in &["fq_codel", "fq", "cake", "pfifo_fast"] {
            if available.iter().any(|x| x == cand) {
                continue;
            }
            if SysfsWriter::write(qdisc_path, cand, WriteMode::ForcePermission).is_ok() {
                let actual = SysfsReader::read_trimmed(qdisc_path).unwrap_or_default();
                if actual == *cand {
                    available.push((*cand).to_string());
                }
            }
        }

        if !orig.is_empty() {
            let _ = SysfsWriter::write(qdisc_path, &orig, WriteMode::ForcePermission);
        }

        if !available.is_empty() {
            let _ = fs::write(QDISC_CACHE_PATH, available.join(" "));
        }
        available
    }

    fn read_iface_qdisc(iface: &str, has_tc: bool, default_qdisc: &str) -> String {
        if has_tc {
            if let Some(out) = run_cmd_stdout("tc", &["qdisc", "show", "dev", iface]) {
                // Example: "qdisc fq_codel 0: root refcnt 2 limit 10240p ..."
                for line in out.lines() {
                    let tokens: Vec<&str> = line.split_whitespace().collect();
                    if tokens.len() >= 2 && tokens[0] == "qdisc" && (line.contains(" root") || tokens.len() >= 2) {
                        return tokens[1].to_string();
                    }
                }
            }
        }
        if default_qdisc.is_empty() {
            "unknown".to_string()
        } else {
            format!("default:{}", default_qdisc)
        }
    }

    fn discover_interfaces(has_tc: bool, default_qdisc: &str) -> Vec<IfaceEntry> {
        let mut ifaces = Vec::new();
        let Ok(entries) = fs::read_dir("/sys/class/net") else {
            return ifaces;
        };

        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if should_skip_iface(&name) {
                continue;
            }
            let base = entry.path();
            let kind = classify_iface(&name).to_string();
            let operstate = SysfsReader::read_trimmed(base.join("operstate"))
                .unwrap_or_else(|_| "unknown".to_string());
            let mtu = SysfsReader::read_trimmed(base.join("mtu"))
                .ok()
                .and_then(|s| s.parse::<u32>().ok())
                .unwrap_or(1500);

            let mut rx_queues = 0usize;
            let mut tx_queues = 0usize;
            let mut rps_writable = false;
            if let Ok(q_entries) = fs::read_dir(base.join("queues")) {
                for qe in q_entries.flatten() {
                    let qname = qe.file_name().to_string_lossy().to_string();
                    if qname.starts_with("rx-") {
                        rx_queues += 1;
                        if qe.path().join("rps_cpus").exists() {
                            rps_writable = true;
                        }
                    } else if qname.starts_with("tx-") {
                        tx_queues += 1;
                    }
                }
            }

            let is_up = is_iface_up(&base, &operstate);
            let effective_state = if is_up { "up".to_string() } else { operstate.clone() };
            let qdisc = if is_up {
                Self::read_iface_qdisc(&name, has_tc, default_qdisc)
            } else {
                String::new()
            };

            ifaces.push(IfaceEntry {
                name,
                kind,
                operstate: effective_state,
                mtu,
                rx_queues,
                tx_queues,
                rps_writable,
                qdisc,
            });
        }

        ifaces.sort_by(|a, b| {
            let rank = |k: &str| match k {
                "wifi" => 0,
                "ethernet" => 1,
                "usb" => 2,
                "cellular" => 3,
                "vpn" => 4,
                _ => 5,
            };
            let up_cmp = (b.operstate == "up").cmp(&(a.operstate == "up"));
            up_cmp
                .then_with(|| rank(&a.kind).cmp(&rank(&b.kind)))
                .then_with(|| a.name.cmp(&b.name))
        });

        ifaces
    }

    fn resolve_active_route(ifaces: &[IfaceEntry]) -> ActiveRouteInfo {
        if let Some(route_out) = run_cmd_stdout("ip", &["-4", "route", "get", "1.1.1.1"]) {
            if let Some(first_line) = route_out.lines().next() {
                if let Some((dev, via, src)) = parse_ip_route_line(first_line) {
                    return ActiveRouteInfo {
                        kind: classify_iface(&dev).to_string(),
                        iface: dev,
                        gateway_ip: via,
                        src_ip: src,
                    };
                }
            }
        }

        if let Some(table_out) = run_cmd_stdout("ip", &["-4", "route", "show", "table", "all"]) {
            for line in table_out.lines() {
                if line.starts_with("default ") {
                    if let Some((dev, via, src)) = parse_ip_route_line(line) {
                        let k = classify_iface(&dev);
                        if k != "vpn" {
                            return ActiveRouteInfo {
                                iface: dev,
                                kind: k.to_string(),
                                gateway_ip: via,
                                src_ip: src,
                            };
                        }
                    }
                }
            }
        }

        if let Some(up_phys) = ifaces
            .iter()
            .find(|i| i.operstate == "up" && i.kind != "vpn" && i.kind != "other")
        {
            return ActiveRouteInfo {
                iface: up_phys.name.clone(),
                kind: up_phys.kind.clone(),
                gateway_ip: String::new(),
                src_ip: String::new(),
            };
        }

        ActiveRouteInfo::default()
    }

    fn apply_dynamic_rps_xps(enable: bool, avoid_cpu0: bool) -> ApplyOutcome {
        let mut outcome = ApplyOutcome::default();
        let baseline = Self::load_baseline();
        let (_, _, _, _, _, total_flows, _, _, _) = Self::resolve_ram_scaled_params();
        let cpu_hex = if enable {
            compute_cpu_mask_hex(avoid_cpu0)
        } else {
            "0".to_string()
        };

        let rps_global = if enable {
            total_flows.to_string()
        } else {
            baseline
                .get("sysctl:net.core.rps_sock_flow_entries")
                .cloned()
                .unwrap_or_else(|| "0".to_string())
        };
        outcome.record_write("/proc/sys/net/core/rps_sock_flow_entries", &rps_global);

        let Ok(entries) = fs::read_dir("/sys/class/net") else {
            return outcome;
        };

        let mut steerable_ifaces = 0usize;
        for entry in entries.flatten() {
            let iface = entry.file_name().to_string_lossy().to_string();
            if should_skip_iface(&iface) {
                continue;
            }
            let kind = classify_iface(&iface);
            if kind == "vpn" || kind == "other" {
                continue;
            }

            let queues_dir = entry.path().join("queues");
            if let Ok(q_entries) = fs::read_dir(&queues_dir) {
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
                    let rps_p = rx.join("rps_cpus");
                    if rps_p.exists() {
                        steerable_ifaces += 1;
                    }
                    outcome.record_write(rps_p, &cpu_hex);
                    outcome.record_write(rx.join("rps_flow_cnt"), &flow_per_rx.to_string());
                }
                for tx in tx_dirs {
                    outcome.record_write(tx.join("xps_cpus"), &cpu_hex);
                }
            }

            let gro_path = entry.path().join("gro_flush_timeout");
            if gro_path.exists() {
                // Keep GRO flush timeout 0 to prevent micro-batching stalls on interactive gaming Wi-Fi
                outcome.record_write(gro_path, "0");
            }
        }

        if steerable_ifaces == 0 {
            outcome
                .notes
                .push("RPS/XPS per-queue tidak diekspos driver kernel".to_string());
        }
        outcome
    }

    fn apply_qdisc_stack(target_qdisc: Option<&str>, enable: bool, current_tcp_algo: &str) -> ApplyOutcome {
        let mut outcome = ApplyOutcome::default();
        let baseline = Self::load_baseline();
        let available_qdiscs = Self::probe_available_qdiscs();
        let has_tc = which_exists("tc");

        let chosen = if enable {
            if let Some(explicit) = target_qdisc {
                explicit.to_string()
            } else if current_tcp_algo.starts_with("bbr")
                && available_qdiscs.iter().any(|q| q == "fq")
            {
                "fq".to_string()
            } else {
                ["fq_codel", "fq", "cake"]
                    .iter()
                    .find(|c| available_qdiscs.iter().any(|q| q == **c))
                    .map(|s| (*s).to_string())
                    .unwrap_or_else(|| "fq_codel".to_string())
            }
        } else {
            baseline
                .get("sysctl:net.core.default_qdisc")
                .cloned()
                .unwrap_or_else(|| "pfifo_fast".to_string())
        };

        outcome.record_write("/proc/sys/net/core/default_qdisc", &chosen);

        if has_tc {
            if let Ok(entries) = fs::read_dir("/sys/class/net") {
                for entry in entries.flatten() {
                    let iface = entry.file_name().to_string_lossy().to_string();
                    if should_skip_iface(&iface) {
                        continue;
                    }
                    let kind = classify_iface(&iface);
                    if !matches!(kind, "wifi" | "cellular" | "usb" | "ethernet") {
                        continue;
                    }
                    let oper = SysfsReader::read_trimmed(entry.path().join("operstate"))
                        .unwrap_or_default();
                    if !is_iface_up(&entry.path(), &oper) {
                        continue;
                    }
                    if enable {
                        outcome.record_cmd_ok(
                            "tc",
                            &["qdisc", "replace", "dev", &iface, "root", &chosen],
                        );
                    } else {
                        let _ = run_cmd_ok("tc", &["qdisc", "del", "dev", &iface, "root"]);
                    }
                }
            }
        } else if enable {
            outcome
                .notes
                .push("Binary tc tidak tersedia di ROM (hanya default_qdisc kernel)".to_string());
        }

        outcome
    }

    fn renice_wifi_kthreads(low_latency: bool) {
        let nice_val = if low_latency { "-20" } else { "-10" };
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
                    let _ = run_cmd_ok("renice", &["-n", nice_val, "-p", &fname]);
                }
            }
        }
    }

    fn apply_hal_low_latency(enabled: bool) -> ApplyOutcome {
        let mut outcome = ApplyOutcome::default();
        let sdk = get_android_sdk();
        let has_iw = which_exists("iw");
        let has_wpa = which_exists("wpa_cli");

        if sdk >= 29 {
            outcome.record_cmd_ok(
                "cmd",
                &[
                    "wifi",
                    "force-low-latency-mode",
                    if enabled { "enabled" } else { "disabled" },
                ],
            );
            outcome.record_cmd_ok("cmd", &["wifi", "set-power-save-mode", if enabled { "0" } else { "1" }]);
        } else {
            let _ = run_cmd_ok("cmd", &["wifi", "set-power-save-mode", if enabled { "0" } else { "1" }]);
        }

        if let Ok(entries) = fs::read_dir("/sys/class/net") {
            for e in entries.flatten() {
                let name = e.file_name().to_string_lossy().to_string();
                if classify_iface(&name) == "wifi" {
                    let oper = SysfsReader::read_trimmed(e.path().join("operstate")).unwrap_or_default();
                    if oper != "up" {
                        continue;
                    }
                    if has_iw {
                        outcome.record_cmd_ok(
                            "iw",
                            &["dev", &name, "set", "power_save", if enabled { "off" } else { "on" }],
                        );
                    } else if has_wpa {
                        outcome.record_cmd_ok(
                            "wpa_cli",
                            &["-i", &name, "SET", "power_save", if enabled { "0" } else { "1" }],
                        );
                    }
                }
            }
        }

        if Path::new("/proc/sys/net/ipv4/tcp_low_latency").exists() {
            outcome.record_write("/proc/sys/net/ipv4/tcp_low_latency", if enabled { "1" } else { "0" });
        }

        outcome
    }

    fn apply_vendor_wifi_hooks(enable: bool, gaming_scan_off: bool) -> ApplyOutcome {
        let mut outcome = ApplyOutcome::default();
        let probe = Self::detect_radio_hardware();

        if !probe.vendor.supports_vendor_sysfs() {
            outcome.unsupported += 1;
            outcome
                .notes
                .push("Driver kernel tidak mengekspor parameter radio sysfs vendor".to_string());
            return outcome;
        }

        match probe.vendor {
            RadioVendor::MediaTekProc => {
                // MediaTek gen4m / MT6631 firmware safety rule:
                // Live mutations to `/proc/net/wlan/cfg` (Rx2OsLoop, Rx2ReorderLoop, AmsduInAmpdu,
                // BaSize, ThreadSched), `autoPerfCfg`, or `setCAM` while wlan0 is associated can
                // corrupt BA reorder / FW state (RSSI wraparound & 0 Mbps hang until full reboot).
                // Keep ONLY non-destructive debug-log suppression here; low-latency mode & power
                // save are handled safely via Android's standard `cmd wifi` / `iw` HAL path.
                let dbg_path = Path::new("/proc/net/wlan/dbgLevel");
                if dbg_path.exists() {
                    outcome.attempted += 1;
                    let raw_dbg = SysfsReader::read_trimmed(dbg_path).unwrap_or_default();
                    let val = if raw_dbg.contains(':') { "0xff:0x03" } else { "0" };
                    if SysfsWriter::write(dbg_path, val, WriteMode::ForcePermission).is_ok() {
                        outcome.verified += 1;
                    }
                }
                let _ = gaming_scan_off;
            }
            RadioVendor::QualcommCnss => {
                let ps = if enable { "0" } else { "1" };
                for params in &probe.module_paths {
                    outcome.record_write(params.join("power_save"), ps);
                    outcome.record_write(params.join("dynamic_ps"), ps);
                    outcome.record_write(params.join("gEnablePacketFilter"), ps);
                    outcome.record_write(params.join("gEnablePowerSaveOffload"), ps);
                    outcome.record_write(params.join("gPowerSaveMode"), ps);
                }
            }
            RadioVendor::BroadcomDhd => {
                let off_on = if enable { "0" } else { "1" };
                for params in &probe.module_paths {
                    outcome.record_write(params.join("dhd_slpauto"), off_on);
                    outcome.record_write(params.join("dhd_watchdog_ms"), if enable { "0" } else { "10" });
                    outcome.record_write(params.join("dhd_pkt_filter_enable"), off_on);
                    outcome.record_write(params.join("op_mode"), if enable { "1" } else { "0" });
                }
            }
            RadioVendor::ExynosScsc => {
                let val = if enable { "0" } else { "1" };
                for params in &probe.module_paths {
                    outcome.record_write(params.join("slp_enable"), val);
                }
            }
            RadioVendor::UnisocSprd => {
                let off_on = if enable { "0" } else { "1" };
                for params in &probe.module_paths {
                    outcome.record_write(params.join("auto_power"), off_on);
                    outcome.record_write(params.join("dynamic_ps"), off_on);
                    outcome.record_write(params.join("packet_filter"), off_on);
                    if enable {
                        outcome.record_write(params.join("rx_mode"), "1");
                    }
                }
                let _ = run_cmd_ok(
                    "setprop",
                    &["persist.vendor.sprd.wlan.lowpower", if enable { "0" } else { "1" }],
                );
                let _ = run_cmd_ok(
                    "setprop",
                    &["persist.vendor.sprd.wlan.performance", if enable { "1" } else { "0" }],
                );
            }
            RadioVendor::HalGeneric | RadioVendor::Unsupported => {}
        }

        Self::renice_wifi_kthreads(enable);
        outcome
    }

    fn apply_scan_guard(enabled: bool) -> ApplyOutcome {
        let mut outcome = ApplyOutcome::default();
        let baseline = Self::load_baseline();
        if enabled {
            outcome.record_setting("global", "wifi_scan_always_enabled", "0");
            outcome.record_setting("global", "ble_scan_always_enabled", "0");
            outcome.record_setting("global", "wifi_scan_throttle_enabled", "1");
            outcome.record_setting("global", "wifi_framework_scan_interval_ms", "300000");
        } else {
            let wifi_orig = baseline
                .get("setting:global.wifi_scan_always_enabled")
                .map(|s| s.as_str())
                .unwrap_or("1");
            let ble_orig = baseline
                .get("setting:global.ble_scan_always_enabled")
                .map(|s| s.as_str())
                .unwrap_or("1");
            let throttle_orig = baseline
                .get("setting:global.wifi_scan_throttle_enabled")
                .map(|s| s.as_str())
                .unwrap_or("1");
            let interval_orig = baseline
                .get("setting:global.wifi_framework_scan_interval_ms")
                .map(|s| s.as_str())
                .unwrap_or("15000");
            outcome.record_setting("global", "wifi_scan_always_enabled", wifi_orig);
            outcome.record_setting("global", "ble_scan_always_enabled", ble_orig);
            outcome.record_setting("global", "wifi_scan_throttle_enabled", throttle_orig);
            outcome.record_setting("global", "wifi_framework_scan_interval_ms", interval_orig);
        }
        outcome
    }

    fn apply_tcp_buffers(enabled: bool) -> ApplyOutcome {
        let mut outcome = ApplyOutcome::default();
        let baseline = Self::load_baseline();
        let (_, max_buf, def_buf, _, _, _, _, udp_mem, _) = Self::resolve_ram_scaled_params();

        if enabled {
            let max_s = max_buf.to_string();
            let def_s = def_buf.to_string();
            outcome.record_write("/proc/sys/net/core/rmem_max", &max_s);
            outcome.record_write("/proc/sys/net/core/wmem_max", &max_s);
            outcome.record_write("/proc/sys/net/core/rmem_default", &def_s);
            outcome.record_write("/proc/sys/net/core/wmem_default", &def_s);
            outcome.record_write(
                "/proc/sys/net/ipv4/tcp_rmem",
                &format!("4096 {} {}", def_buf, max_buf),
            );
            outcome.record_write(
                "/proc/sys/net/ipv4/tcp_wmem",
                &format!("4096 {} {}", def_buf / 2, max_buf),
            );
            outcome.record_write("/proc/sys/net/ipv4/udp_rmem_min", "16384");
            outcome.record_write("/proc/sys/net/ipv4/udp_wmem_min", "16384");
            outcome.record_write("/proc/sys/net/ipv4/udp_mem", udp_mem);

            let prop_val = format!("524288,1048576,{},262144,524288,{}", max_buf, max_buf);
            for k in &["default", "wifi", "lte", "5g"] {
                let pkey = format!("net.tcp.buffersize.{}", k);
                let _ = run_cmd_ok("setprop", &[&pkey, &prop_val]);
            }
        } else {
            let rmem_max = baseline
                .get("sysctl:net.core.rmem_max")
                .map(|s| s.as_str())
                .unwrap_or("212992");
            let wmem_max = baseline
                .get("sysctl:net.core.wmem_max")
                .map(|s| s.as_str())
                .unwrap_or("212992");
            let rmem_def = baseline
                .get("sysctl:net.core.rmem_default")
                .map(|s| s.as_str())
                .unwrap_or("212992");
            let wmem_def = baseline
                .get("sysctl:net.core.wmem_default")
                .map(|s| s.as_str())
                .unwrap_or("212992");
            let tcp_rmem = baseline
                .get("sysctl:net.ipv4.tcp_rmem")
                .map(|s| s.as_str())
                .unwrap_or("4096 131072 6291456");
            let tcp_wmem = baseline
                .get("sysctl:net.ipv4.tcp_wmem")
                .map(|s| s.as_str())
                .unwrap_or("4096 16384 4194304");
            outcome.record_write("/proc/sys/net/core/rmem_max", rmem_max);
            outcome.record_write("/proc/sys/net/core/wmem_max", wmem_max);
            outcome.record_write("/proc/sys/net/core/rmem_default", rmem_def);
            outcome.record_write("/proc/sys/net/core/wmem_default", wmem_def);
            outcome.record_write("/proc/sys/net/ipv4/tcp_rmem", tcp_rmem);
            outcome.record_write("/proc/sys/net/ipv4/tcp_wmem", tcp_wmem);
            if let Some(umem) = baseline.get("sysctl:net.ipv4.udp_mem") {
                outcome.record_write("/proc/sys/net/ipv4/udp_mem", umem);
            }
            for k in &["default", "wifi", "lte", "5g"] {
                let pkey = format!("net.tcp.buffersize.{}", k);
                let _ = run_cmd_ok("setprop", &[&pkey, ""]);
            }
        }
        outcome
    }

    fn apply_tcp_fastopen(enabled: bool, low_latency_cork: bool) -> ApplyOutcome {
        let mut outcome = ApplyOutcome::default();
        let baseline = Self::load_baseline();
        let (_, _, _, backlog, budget, _, syn_backlog, _, somaxconn) = Self::resolve_ram_scaled_params();

        if enabled {
            outcome.record_write("/proc/sys/net/ipv4/tcp_fastopen", "3");
            outcome.record_write(
                "/proc/sys/net/ipv4/tcp_autocorking",
                if low_latency_cork { "0" } else { "1" },
            );
            outcome.record_write(
                "/proc/sys/net/ipv4/tcp_notsent_lowat",
                if low_latency_cork { "131072" } else { "262144" },
            );
            outcome.record_write("/proc/sys/net/ipv4/tcp_slow_start_after_idle", "0");
            outcome.record_write("/proc/sys/net/ipv4/tcp_tw_reuse", "1");
            outcome.record_write("/proc/sys/net/ipv4/tcp_sack", "1");
            outcome.record_write("/proc/sys/net/ipv4/tcp_dsack", "1");
            outcome.record_write("/proc/sys/net/ipv4/tcp_fin_timeout", "15");
            // Keep tcp_mtu_probing=0 & passive RFC tcp_ecn=2 to prevent SYN drops/blackholes on ISP/home NATs
            outcome.record_write("/proc/sys/net/ipv4/tcp_mtu_probing", "0");
            outcome.record_write("/proc/sys/net/ipv4/tcp_ecn", "2");
            outcome.record_write("/proc/sys/net/core/netdev_max_backlog", &backlog.to_string());
            outcome.record_write("/proc/sys/net/core/netdev_budget", &budget.to_string());
            outcome.record_write("/proc/sys/net/core/somaxconn", &somaxconn.to_string());
            outcome.record_write("/proc/sys/net/ipv4/tcp_max_syn_backlog", &syn_backlog.to_string());
            outcome.record_write("/proc/sys/net/ipv4/tcp_syn_retries", "3");
            outcome.record_write("/proc/sys/net/ipv4/tcp_synack_retries", "3");
            let _ = run_cmd_ok("setprop", &["net.dns_cache_size", "1024"]);
            let _ = run_cmd_ok("setprop", &["net.dns_cache_ttl", "7200"]);
        } else {
            let restore = |k: &str, fallback: &str| -> String {
                baseline
                    .get(k)
                    .cloned()
                    .unwrap_or_else(|| fallback.to_string())
            };
            outcome.record_write(
                "/proc/sys/net/ipv4/tcp_fastopen",
                &restore("sysctl:net.ipv4.tcp_fastopen", "1"),
            );
            outcome.record_write(
                "/proc/sys/net/ipv4/tcp_autocorking",
                &restore("sysctl:net.ipv4.tcp_autocorking", "1"),
            );
            outcome.record_write(
                "/proc/sys/net/ipv4/tcp_notsent_lowat",
                &restore("sysctl:net.ipv4.tcp_notsent_lowat", "4294967295"),
            );
            outcome.record_write(
                "/proc/sys/net/ipv4/tcp_slow_start_after_idle",
                &restore("sysctl:net.ipv4.tcp_slow_start_after_idle", "1"),
            );
            outcome.record_write(
                "/proc/sys/net/ipv4/tcp_fin_timeout",
                &restore("sysctl:net.ipv4.tcp_fin_timeout", "60"),
            );
            outcome.record_write(
                "/proc/sys/net/ipv4/tcp_mtu_probing",
                &restore("sysctl:net.ipv4.tcp_mtu_probing", "0"),
            );
            outcome.record_write(
                "/proc/sys/net/core/netdev_max_backlog",
                &restore("sysctl:net.core.netdev_max_backlog", "1000"),
            );
            outcome.record_write(
                "/proc/sys/net/core/somaxconn",
                &restore("sysctl:net.core.somaxconn", "128"),
            );
        }
        outcome
    }

    fn apply_ipv6_disable(disable: bool) -> ApplyOutcome {
        let mut outcome = ApplyOutcome::default();
        let val = if disable { "1" } else { "0" };
        outcome.record_write("/proc/sys/net/ipv6/conf/all/disable_ipv6", val);
        outcome.record_write("/proc/sys/net/ipv6/conf/default/disable_ipv6", val);
        if let Ok(entries) = fs::read_dir("/proc/sys/net/ipv6/conf") {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                if name == "all" || name == "default" || name == "lo" {
                    continue;
                }
                let p = entry.path().join("disable_ipv6");
                if p.exists() {
                    outcome.record_write(p, val);
                }
            }
        }
        outcome
    }

    pub fn info_json() -> String {
        Self::capture_baseline_if_missing();
        let cap = HardwareCapability::resolve_network();
        let has_iw = which_exists("iw");
        let has_iwpriv = which_exists("iwpriv");
        let has_wl = which_exists("wl");
        let has_tc = which_exists("tc");
        let has_wpa_cli = which_exists("wpa_cli");
        let android_sdk = get_android_sdk();

        let radio = Self::detect_radio_hardware();
        let available_qdiscs = Self::probe_available_qdiscs();
        let ifaces = Self::discover_interfaces(has_tc, &cap.current_qdisc);
        let route = Self::resolve_active_route(&ifaces);
        let active_iface_entry = ifaces.iter().find(|i| i.name == route.iface);
        let active_mtu = active_iface_entry.map(|i| i.mtu).unwrap_or(1500);
        let active_qdisc = active_iface_entry
            .map(|i| i.qdisc.clone())
            .filter(|q| !q.is_empty())
            .unwrap_or_else(|| {
                if cap.current_qdisc.is_empty() {
                    "unknown".to_string()
                } else {
                    cap.current_qdisc.clone()
                }
            });

        let (ram_tier_label, expected_max_buf, _, _, _, _, _, _, _) = Self::resolve_ram_scaled_params();
        let rmem_max_val = SysfsReader::read_trimmed("/proc/sys/net/core/rmem_max")
            .ok()
            .and_then(|s| s.parse::<u64>().ok())
            .unwrap_or(0);
        let effective_rmem_max_mb = (rmem_max_val as f64) / (1024.0 * 1024.0);

        let supports_tcp_low_latency = Path::new("/proc/sys/net/ipv4/tcp_low_latency").exists();
        let supports_tcp_fastopen = Path::new("/proc/sys/net/ipv4/tcp_fastopen").exists();
        let supports_notsent_lowat = Path::new("/proc/sys/net/ipv4/tcp_notsent_lowat").exists();
        let supports_autocorking = Path::new("/proc/sys/net/ipv4/tcp_autocorking").exists();
        let supports_rps_xps = ifaces.iter().any(|i| i.rps_writable)
            || Path::new("/proc/sys/net/core/rps_sock_flow_entries").exists();

        let tcp_low_lat_on = SysfsReader::read_trimmed("/proc/sys/net/ipv4/tcp_low_latency")
            .map(|v| v == "1")
            .unwrap_or(false);
        let mut wifi_ps_off = false;
        if has_iw {
            if let Some(wlan) = ifaces.iter().find(|i| i.kind == "wifi" && i.operstate == "up") {
                if let Some(iw_out) = run_cmd_stdout("iw", &["dev", &wlan.name, "get", "power_save"]) {
                    wifi_ps_off = iw_out.to_ascii_lowercase().contains("off");
                }
            }
        }
        let low_latency_active = tcp_low_lat_on || wifi_ps_off;
        let scan_guard_active = run_cmd_stdout("settings", &["get", "global", "wifi_scan_always_enabled"])
            .map(|v| v.trim() == "0")
            .unwrap_or(false);
        let rps_flows = SysfsReader::read_trimmed("/proc/sys/net/core/rps_sock_flow_entries")
            .ok()
            .and_then(|s| s.parse::<u64>().ok())
            .unwrap_or(0);
        let qdisc_guard_active = matches!(
            cap.current_qdisc.as_str(),
            "fq_codel" | "fq" | "cake"
        ) || (rps_flows > 0);
        let tcp_buffers_scaled = rmem_max_val >= (expected_max_buf / 2).max(4_194_304);
        let tcp_fastopen_active = SysfsReader::read_trimmed("/proc/sys/net/ipv4/tcp_fastopen")
            .map(|v| v == "3")
            .unwrap_or(false)
            && SysfsReader::read_trimmed("/proc/sys/net/ipv4/tcp_slow_start_after_idle")
                .map(|v| v == "0")
                .unwrap_or(true);
        let ipv6_disabled = SysfsReader::read_trimmed("/proc/sys/net/ipv6/conf/all/disable_ipv6")
            .map(|v| v == "1")
            .unwrap_or(false);
        let mobile_standby_active =
            run_cmd_stdout("settings", &["get", "global", "mobile_data_always_on"])
                .map(|v| v.trim() == "1")
                .unwrap_or(false);

        let hw_radio_active = match radio.vendor {
            RadioVendor::MediaTekProc => SysfsReader::read_trimmed("/proc/net/wlan/cfg")
                .map(|s| s.contains("PowerSave|0"))
                .unwrap_or_else(|_| {
                    SysfsReader::read_trimmed("/proc/net/wlan/setCAM")
                        .map(|s| s.contains('1'))
                        .unwrap_or(false)
                }),
            RadioVendor::QualcommCnss => radio.module_paths.iter().any(|p| {
                [
                    "power_save",
                    "dynamic_ps",
                    "gEnablePacketFilter",
                    "gEnablePowerSaveOffload",
                    "gPowerSaveMode",
                ]
                .iter()
                .any(|k| SysfsReader::read_trimmed(p.join(k)).map(|v| v == "0").unwrap_or(false))
            }),
            RadioVendor::BroadcomDhd => radio.module_paths.iter().any(|p| {
                SysfsReader::read_trimmed(p.join("dhd_slpauto"))
                    .map(|v| v == "0")
                    .unwrap_or(false)
                    || SysfsReader::read_trimmed(p.join("dhd_pkt_filter_enable"))
                        .map(|v| v == "0")
                        .unwrap_or(false)
                    || SysfsReader::read_trimmed(p.join("op_mode"))
                        .map(|v| v == "1")
                        .unwrap_or(false)
            }),
            RadioVendor::ExynosScsc => radio
                .module_paths
                .iter()
                .any(|p| SysfsReader::read_trimmed(p.join("slp_enable")).map(|v| v == "0").unwrap_or(false)),
            RadioVendor::UnisocSprd => radio.module_paths.iter().any(|p| {
                ["auto_power", "dynamic_ps", "packet_filter"]
                    .iter()
                    .any(|k| SysfsReader::read_trimmed(p.join(k)).map(|v| v == "0").unwrap_or(false))
            }),
            RadioVendor::HalGeneric | RadioVendor::Unsupported => false,
        };

        let active_preset = fs::read_to_string(RADIO_OVERRIDE_LOCK_MARKER)
            .map(|s| s.trim().to_string())
            .unwrap_or_else(|_| "custom".to_string());

        let algos_json: Vec<String> = cap.available_tcp_algos.iter().map(|a| json_str(a)).collect();
        let qdiscs_json: Vec<String> = available_qdiscs.iter().map(|q| json_str(q)).collect();
        let ifaces_json: Vec<String> = ifaces
            .iter()
            .map(|i| {
                format!(
                    "{{\"name\":{},\"kind\":{},\"state\":{},\"mtu\":{},\"rx_queues\":{},\"tx_queues\":{},\"rps_writable\":{},\"qdisc\":{}}}",
                    json_str(&i.name),
                    json_str(&i.kind),
                    json_str(&i.operstate),
                    i.mtu,
                    i.rx_queues,
                    i.tx_queues,
                    i.rps_writable,
                    json_str(&i.qdisc)
                )
            })
            .collect();

        let fastopen_nodes_present = [
            supports_tcp_fastopen,
            supports_notsent_lowat,
            supports_autocorking,
            Path::new("/proc/sys/net/ipv4/tcp_slow_start_after_idle").exists(),
            Path::new("/proc/sys/net/ipv4/tcp_tw_reuse").exists(),
        ]
        .iter()
        .filter(|x| **x)
        .count();

        format!(
            concat!(
                "{{",
                "\"current_algo\":{},\"best_algo\":{},\"available_algos\":[{}],",
                "\"current_qdisc\":{},\"active_qdisc\":{},\"available_qdiscs\":[{}],",
                "\"active_iface\":{},\"active_transport\":{},\"active_mtu\":{},",
                "\"gateway_ip\":{},\"src_ip\":{},",
                "\"radio_vendor\":{},\"radio_label\":{},\"supports_hw_radio\":{},",
                "\"has_iw\":{},\"has_iwpriv\":{},\"has_wl\":{},\"has_tc\":{},\"has_wpa_cli\":{},\"android_sdk\":{},",
                "\"supports_hal_low_latency\":{},\"supports_tcp_low_latency\":{},\"supports_rps_xps\":{},",
                "\"fastopen_nodes_present\":{},\"ram_tier_label\":{},\"effective_rmem_max_mb\":{:.1},",
                "\"has_baseline\":{},\"active_preset\":{},",
                "\"low_latency_active\":{},\"scan_guard_active\":{},\"qdisc_guard_active\":{},",
                "\"tcp_buffers_scaled\":{},\"tcp_fastopen_active\":{},\"hw_radio_active\":{},",
                "\"ipv6_disabled\":{},\"mobile_standby_active\":{},",
                "\"interfaces\":[{}]",
                "}}"
            ),
            json_str(&cap.current_tcp_algo),
            json_str(&cap.best_tcp_algo),
            algos_json.join(","),
            json_str(&cap.current_qdisc),
            json_str(&active_qdisc),
            qdiscs_json.join(","),
            json_str(&route.iface),
            json_str(&route.kind),
            active_mtu,
            json_str(&route.gateway_ip),
            json_str(&route.src_ip),
            json_str(radio.vendor.id()),
            json_str(&radio.label),
            radio.vendor.supports_vendor_sysfs(),
            has_iw,
            has_iwpriv,
            has_wl,
            has_tc,
            has_wpa_cli,
            android_sdk,
            android_sdk >= 29,
            supports_tcp_low_latency,
            supports_rps_xps,
            fastopen_nodes_present,
            json_str(ram_tier_label),
            effective_rmem_max_mb,
            active_baseline_path().exists(),
            json_str(&active_preset),
            low_latency_active,
            scan_guard_active,
            qdisc_guard_active,
            tcp_buffers_scaled,
            tcp_fastopen_active,
            hw_radio_active,
            ipv6_disabled,
            mobile_standby_active,
            ifaces_json.join(",")
        )
    }

    pub fn set_tcp_algo(target: &str) -> Result<String, String> {
        Self::capture_baseline_if_missing();
        let net = HardwareCapability::resolve_network();
        if !net.available_tcp_algos.is_empty() && !net.available_tcp_algos.iter().any(|a| a == target) {
            return Err(format!(
                "Algorithm '{}' is not supported by kernel. Available: {}",
                target,
                net.available_tcp_algos.join(" ")
            ));
        }
        let p = Path::new("/proc/sys/net/ipv4/tcp_congestion_control");
        if !p.exists() {
            return Err("Kernel does not expose /proc/sys/net/ipv4/tcp_congestion_control".to_string());
        }
        SysfsWriter::write(p, target, WriteMode::ForcePermission)
            .map_err(|e| format!("Failed writing tcp_congestion_control: {}", e))?;
        let actual = SysfsReader::read_trimmed(p).unwrap_or_default();
        if actual != target {
            return Err(format!("Kernel rejected TCP congestion '{}' (active: '{}')", target, actual));
        }
        // Smart BBR pairing: if default_qdisc is plain pfifo_fast, pair with fq (or fq_codel)
        if target.starts_with("bbr") {
            let cur_q = SysfsReader::read_trimmed("/proc/sys/net/core/default_qdisc").unwrap_or_default();
            if cur_q == "pfifo_fast" || cur_q.is_empty() {
                let avail_q = Self::probe_available_qdiscs();
                if let Some(q) = ["fq", "fq_codel"].iter().find(|c| avail_q.iter().any(|x| x == **c)) {
                    let _ = Self::apply_qdisc_stack(Some(q), true, target);
                }
            }
        }
        Ok(format!("TCP congestion control set to '{}'.", target))
    }

    pub fn set_qdisc(target: &str) -> Result<String, String> {
        Self::capture_baseline_if_missing();
        let allowed = Self::probe_available_qdiscs();
        if !allowed.is_empty() && !allowed.iter().any(|q| q == target) {
            return Err(format!(
                "Queue discipline '{}' not supported. Available: {}",
                target,
                allowed.join(" ")
            ));
        }
        let cap = HardwareCapability::resolve_network();
        let outcome = Self::apply_qdisc_stack(Some(target), true, &cap.current_tcp_algo);
        let actual = SysfsReader::read_trimmed("/proc/sys/net/core/default_qdisc").unwrap_or_default();
        if actual != target && outcome.verified == 0 {
            return Err(format!("Kernel rejected default_qdisc '{}'", target));
        }
        Ok(outcome.to_json(&format!("Queue discipline diatur ke '{}'.", target)))
    }

    pub fn set_feature(key: &str, enabled: bool) -> String {
        Self::capture_baseline_if_missing();
        let cap = HardwareCapability::resolve_network();
        let (outcome, label) = match key {
            "low_latency" | "wifi_ping_stabilizer" => (
                Self::apply_hal_low_latency(enabled),
                if enabled {
                    "Wi-Fi Low-Latency HAL aktif"
                } else {
                    "Wi-Fi Low-Latency HAL dipulihkan"
                },
            ),
            "scan_guard" | "anti_jitter_scan" => (
                Self::apply_scan_guard(enabled),
                if enabled {
                    "Background Scan Guard aktif"
                } else {
                    "Background Scan Guard dipulihkan"
                },
            ),
            "qdisc_rps" | "fq_codel" | "fq_codel_enabled" => {
                let mut o = Self::apply_qdisc_stack(None, enabled, &cap.current_tcp_algo);
                o.merge(Self::apply_dynamic_rps_xps(enabled, enabled));
                (
                    o,
                    if enabled {
                        "Multi-Queue RPS & Anti-Bufferbloat Qdisc aktif"
                    } else {
                        "Qdisc & RPS dikembalikan ke baseline"
                    },
                )
            }
            "tcp_buffers" | "tcp_buffers_16m" => (
                Self::apply_tcp_buffers(enabled),
                if enabled {
                    "Socket Buffers adaptif RAM diterapkan"
                } else {
                    "Socket Buffers dipulihkan ke baseline OEM"
                },
            ),
            "tcp_fastopen" | "tcp_fast_open" => (
                Self::apply_tcp_fastopen(enabled, true),
                if enabled {
                    "TCP Fast Open & Anti-Stall aktif"
                } else {
                    "TCP Fast Open dipulihkan ke baseline OEM"
                },
            ),
            "hw_radio" | "mtk_hw_aggregation" | "mtk_hardware_aggregation" => (
                Self::apply_vendor_wifi_hooks(enabled, enabled),
                if enabled {
                    "Tuning driver radio hardware diterapkan"
                } else {
                    "Driver radio hardware dipulihkan"
                },
            ),
            "ipv6_disable" | "ipv6_disabled" => (
                Self::apply_ipv6_disable(enabled),
                if enabled {
                    "IPv6 dinonaktifkan (IPv4 priority guard)"
                } else {
                    "IPv6 dual-stack diaktifkan kembali"
                },
            ),
            "mobile_standby" | "cellular_standby" => {
                let mut o = ApplyOutcome::default();
                o.record_setting("global", "mobile_data_always_on", if enabled { "1" } else { "0" });
                (
                    o,
                    if enabled {
                        "Cellular Hot-Standby aktif"
                    } else {
                        "Cellular Hot-Standby dimatikan"
                    },
                )
            }
            _ => {
                return format!(
                    "{{\"ok\":false,\"status\":\"UNSUPPORTED\",\"attempted\":0,\"verified\":0,\"unsupported\":1,\"message\":{}}}",
                    json_str(&format!("Unknown feature key '{}'", key))
                );
            }
        };
        let _ = fs::write(RADIO_OVERRIDE_LOCK_MARKER, "custom");
        outcome.to_json(label)
    }

    pub fn apply_preset(preset: &str) -> String {
        Self::capture_baseline_if_missing();
        let norm = preset.trim().to_ascii_lowercase();
        let cap = HardwareCapability::resolve_network();
        let mut outcome = ApplyOutcome::default();

        match norm.as_str() {
            "oem" | "balance" | "balanced" | "default" => {
                let baseline = Self::load_baseline();
                if let Some(orig_algo) = baseline.get("sysctl:net.ipv4.tcp_congestion_control") {
                    outcome.record_write("/proc/sys/net/ipv4/tcp_congestion_control", orig_algo);
                }
                outcome.merge(Self::apply_hal_low_latency(false));
                outcome.merge(Self::apply_scan_guard(false));
                outcome.merge(Self::apply_qdisc_stack(None, false, &cap.current_tcp_algo));
                outcome.merge(Self::apply_dynamic_rps_xps(false, false));
                outcome.merge(Self::apply_tcp_buffers(false));
                outcome.merge(Self::apply_tcp_fastopen(false, false));
                outcome.merge(Self::apply_vendor_wifi_hooks(false, false));
                let budget = baseline
                    .get("sysctl:net.core.netdev_budget")
                    .map(|s| s.as_str())
                    .unwrap_or("300");
                let budget_us = baseline
                    .get("sysctl:net.core.netdev_budget_usecs")
                    .map(|s| s.as_str())
                    .unwrap_or("8000");
                outcome.record_write("/proc/sys/net/core/netdev_budget", budget);
                outcome.record_write("/proc/sys/net/core/netdev_budget_usecs", budget_us);
                let _ = fs::write(RADIO_OVERRIDE_LOCK_MARKER, "oem");
                outcome.to_json("Baseline OEM & hemat daya jaringan dipulihkan.")
            }
            "gaming" | "game" | "esports" => {
                if !cap.best_tcp_algo.is_empty() {
                    let _ = Self::set_tcp_algo(&cap.best_tcp_algo);
                }
                let updated_cap = HardwareCapability::resolve_network();
                outcome.merge(Self::apply_hal_low_latency(true));
                outcome.merge(Self::apply_scan_guard(true));
                outcome.merge(Self::apply_qdisc_stack(None, true, &updated_cap.current_tcp_algo));
                outcome.merge(Self::apply_dynamic_rps_xps(true, true));
                outcome.merge(Self::apply_tcp_fastopen(true, true));
                outcome.merge(Self::apply_vendor_wifi_hooks(true, true));
                outcome.record_write("/proc/sys/net/core/netdev_budget", "300");
                outcome.record_write("/proc/sys/net/core/netdev_budget_usecs", "2000");
                let _ = fs::write(RADIO_OVERRIDE_LOCK_MARKER, "gaming");
                outcome.to_json("Profil Gaming Low-Latency diterapkan.")
            }
            "throughput" | "download" | "max_bandwidth" => {
                if !cap.best_tcp_algo.is_empty() {
                    let _ = Self::set_tcp_algo(&cap.best_tcp_algo);
                }
                let updated_cap = HardwareCapability::resolve_network();
                let avail_q = Self::probe_available_qdiscs();
                let target_q = if avail_q.iter().any(|q| q == "fq") {
                    Some("fq")
                } else {
                    None
                };
                outcome.merge(Self::apply_hal_low_latency(false));
                outcome.merge(Self::apply_scan_guard(false));
                outcome.merge(Self::apply_qdisc_stack(target_q, true, &updated_cap.current_tcp_algo));
                outcome.merge(Self::apply_dynamic_rps_xps(true, false));
                outcome.merge(Self::apply_tcp_buffers(true));
                outcome.merge(Self::apply_tcp_fastopen(true, false));
                outcome.merge(Self::apply_vendor_wifi_hooks(true, false));
                let (_, _, _, _, budget, _, _, _, _) = Self::resolve_ram_scaled_params();
                outcome.record_write("/proc/sys/net/core/netdev_budget", &budget.to_string());
                outcome.record_write("/proc/sys/net/core/netdev_budget_usecs", "8000");
                let _ = fs::write(RADIO_OVERRIDE_LOCK_MARKER, "throughput");
                outcome.to_json("Profil Throughput & Buffer Adaptif RAM diterapkan.")
            }
            _ => Self::apply_universal(),
        }
    }

    pub fn probe_ping_json() -> String {
        let has_tc = which_exists("tc");
        let cap = HardwareCapability::resolve_network();
        let ifaces = Self::discover_interfaces(has_tc, &cap.current_qdisc);
        let route = Self::resolve_active_route(&ifaces);

        let target = if !route.gateway_ip.is_empty() {
            route.gateway_ip.clone()
        } else {
            "1.1.1.1".to_string()
        };

        let out = run_cmd_stdout("ping", &["-c", "3", "-i", "0.2", "-W", "1", &target]);
        let (reachable, avg_ms, jitter_ms, loss_pct) = out
            .as_deref()
            .map(parse_ping_summary)
            .unwrap_or((false, 0.0, 0.0, 100.0));

        format!(
            "{{\"reachable\":{},\"target\":{},\"iface\":{},\"transport\":{},\"avg_ms\":{:.2},\"jitter_ms\":{:.2},\"loss_pct\":{:.0}}}",
            reachable,
            json_str(&target),
            json_str(&route.iface),
            json_str(&route.kind),
            avg_ms,
            jitter_ms,
            loss_pct
        )
    }

    pub fn apply_universal() -> String {
        Self::capture_baseline_if_missing();
        let cap = HardwareCapability::resolve_network();
        if !cap.best_tcp_algo.is_empty() {
            let _ = Self::set_tcp_algo(&cap.best_tcp_algo);
        }
        let updated = HardwareCapability::resolve_network();
        let mut outcome = Self::apply_qdisc_stack(None, true, &updated.current_tcp_algo);
        outcome.merge(Self::apply_dynamic_rps_xps(true, false));
        outcome.merge(Self::apply_tcp_buffers(true));
        outcome.merge(Self::apply_tcp_fastopen(true, false));
        let _ = fs::write(RADIO_OVERRIDE_LOCK_MARKER, "throughput");
        outcome.to_json(&format!(
            "Universal network stack optimized (TCP Congestion: {}).",
            updated.current_tcp_algo
        ))
    }

    pub fn apply_game() -> String {
        Self::apply_preset("gaming")
    }

    pub fn apply_balance() -> String {
        Self::apply_preset("oem")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn classifies_network_interfaces_accurately() {
        assert_eq!(classify_iface("wlan0"), "wifi");
        assert_eq!(classify_iface("swlan0"), "wifi");
        assert_eq!(classify_iface("rmnet_data0"), "cellular");
        assert_eq!(classify_iface("ccmni1"), "cellular");
        assert_eq!(classify_iface("seth0"), "cellular");
        assert_eq!(classify_iface("rndis0"), "usb");
        assert_eq!(classify_iface("eth0"), "ethernet");
        assert_eq!(classify_iface("tun0"), "vpn");
        assert!(should_skip_iface("lo"));
        assert!(should_skip_iface("dummy0"));
        assert!(!should_skip_iface("wlan0"));
        assert!(!should_skip_iface("rmnet_data0"));
    }

    #[test]
    fn parses_ip_route_and_ping_stats_cleanly() {
        let wifi_route = parse_ip_route_line("1.1.1.1 via 192.168.0.1 dev wlan0 src 192.168.0.181 uid 0")
            .expect("wifi route parses");
        assert_eq!(wifi_route.0, "wlan0");
        assert_eq!(wifi_route.1, "192.168.0.1");
        assert_eq!(wifi_route.2, "192.168.0.181");

        let cell_route = parse_ip_route_line("1.1.1.1 dev rmnet_data1 table 1020 src 10.22.3.4 uid 0")
            .expect("cellular p2p route parses");
        assert_eq!(cell_route.0, "rmnet_data1");
        assert_eq!(cell_route.1, "");
        assert_eq!(cell_route.2, "10.22.3.4");

        let ping_ok = "PING 192.168.0.1 (192.168.0.1) 56(84) bytes of data.\n3 packets transmitted, 3 received, 0% packet loss, time 402ms\nrtt min/avg/max/mdev = 2.110/4.320/7.890/2.450 ms\n";
        let (reach, avg, jit, loss) = parse_ping_summary(ping_ok);
        assert!(reach);
        assert!((avg - 4.32).abs() < 0.01);
        assert!((jit - 2.45).abs() < 0.01);
        assert!((loss - 0.0).abs() < 0.01);

        let ping_fail = "3 packets transmitted, 0 received, 100% packet loss, time 2003ms\n";
        let (reach2, avg2, _, loss2) = parse_ping_summary(ping_fail);
        assert!(!reach2);
        assert_eq!(avg2, 0.0);
        assert_eq!(loss2, 100.0);
    }

    #[test]
    fn serializes_and_parses_baseline_roundtrip() {
        let mut map = HashMap::new();
        map.insert("sysctl:net.ipv4.tcp_rmem".to_string(), "4096\t131072\t6291456".to_string());
        map.insert("setting:global.wifi_scan_always_enabled".to_string(), "1".to_string());
        let encoded = serialize_baseline_map(&map);
        let decoded = parse_baseline_map(&encoded);
        assert_eq!(decoded.get("sysctl:net.ipv4.tcp_rmem").map(|s| s.as_str()), Some("4096 131072 6291456"));
        assert_eq!(decoded.get("setting:global.wifi_scan_always_enabled").map(|s| s.as_str()), Some("1"));
    }

    #[test]
    fn json_escapes_control_and_quotes() {
        assert_eq!(json_str("My \"Home\"\\Net\n"), "\"My \\\"Home\\\"\\\\Net\\n\"");
    }

    #[test]
    fn qcacld_con_mode_only_downgrades_cleanly_to_hal_generic() {
        let base = std::env::temp_dir().join(format!(
            "lynx_radio_test_{}",
            std::process::id()
        ));
        let _ = fs::remove_dir_all(&base);
        let qca_ro_params = base.join("sys_mod/wlan/parameters");
        fs::create_dir_all(&qca_ro_params).unwrap();
        fs::write(qca_ro_params.join("con_mode"), "0\n").unwrap();
        fs::write(qca_ro_params.join("fwpath"), "sta\n").unwrap();

        let probe_hal = NetworkController::probe_radio_hardware_in(
            &base.join("sys_mod"),
            &base.join("missing_mtk"),
            &base.join("missing_sprd"),
            35,
            false,
        );
        assert_eq!(probe_hal.vendor, RadioVendor::HalGeneric);
        assert!(!probe_hal.vendor.supports_vendor_sysfs());
        assert!(probe_hal.label.contains("Qualcomm WLAN (HAL API 35)"));

        // Adding a writable power_save parameter promotes to QualcommCnss
        fs::write(qca_ro_params.join("power_save"), "1\n").unwrap();
        let probe_qca = NetworkController::probe_radio_hardware_in(
            &base.join("sys_mod"),
            &base.join("missing_mtk"),
            &base.join("missing_sprd"),
            35,
            false,
        );
        assert_eq!(probe_qca.vendor, RadioVendor::QualcommCnss);
        assert!(probe_qca.vendor.supports_vendor_sysfs());

        let _ = fs::remove_dir_all(&base);
    }
}
