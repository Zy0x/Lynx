use std::fmt::Write as FmtWrite;
use std::fs;
use std::path::Path;
use std::process::Command;

use crate::state::ConfigStore;
use crate::utils::Logger;

pub struct AdbKeepAliveConfig {
    pub enabled: bool,
    pub port: u16,
}

pub struct AdbController;

impl AdbController {
    /// Loads ADB Keepalive configuration from config.json.
    /// Defaults to enabled: false, port: 5555.
    pub fn load_config() -> AdbKeepAliveConfig {
        let enabled = ConfigStore::get("adb_keepalive.enabled")
            .map(|v| v == "true" || v == "1")
            .unwrap_or(false);

        let port = ConfigStore::get("adb_keepalive.port")
            .and_then(|v| v.parse::<u16>().ok())
            .filter(|&p| p >= 1024)
            .unwrap_or(5555);

        AdbKeepAliveConfig { enabled, port }
    }

    /// Saves ADB Keepalive configuration to config.json.
    pub fn save_config(enabled: bool, port: u16) -> Result<(), String> {
        if !(1024..=65535).contains(&port) {
            return Err(format!("Invalid port {}: must be between 1024 and 65535", port));
        }

        ConfigStore::set_batch(&[
            (
                "adb_keepalive.enabled".to_string(),
                if enabled { "true".to_string() } else { "false".to_string() },
                Some("bool".to_string()),
            ),
            (
                "adb_keepalive.port".to_string(),
                port.to_string(),
                Some("int".to_string()),
            ),
        ])
    }

    /// Read an Android system property.
    pub fn getprop(prop: &str) -> String {
        if let Ok(out) = Command::new("getprop").arg(prop).output() {
            return String::from_utf8_lossy(&out.stdout).trim().to_string();
        }
        String::new()
    }

    /// Sets property using both setprop and resetprop (persists across reboots and screen-off).
    fn setprop_persistent(key: &str, val: &str) {
        // 1. Direct setprop
        let _ = Command::new("setprop").args([key, val]).status();

        // 2. Magisk/KSU/APatch resetprop to modify persistent_properties and memory
        for bin in &[
            "/data/adb/magisk/resetprop",
            "/data/adb/ksu/bin/resetprop",
            "/data/adb/ap/bin/resetprop",
            "resetprop",
        ] {
            if Path::new(bin).exists() || *bin == "resetprop" {
                if Command::new(bin).args(["-n", key, val]).status().is_ok() {
                    break;
                }
            }
        }
    }

    /// Checks if a TCP port is currently listening on the kernel level (/proc/net/tcp and /proc/net/tcp6).
    /// Ultra-fast: sub-0.1ms without spawning child processes.
    pub fn is_port_listening(port: u16) -> bool {
        let hex_port = format!(":{:04X}", port);
        for net_file in &["/proc/net/tcp", "/proc/net/tcp6"] {
            if let Ok(content) = fs::read_to_string(net_file) {
                for line in content.lines() {
                    // Check local_address and state 0A (TCP_LISTEN)
                    if line.contains(&hex_port) && line.contains(" 0A ") {
                        return true;
                    }
                }
            }
        }
        false
    }

    /// Checks if adbd process is currently running.
    pub fn is_adbd_running() -> bool {
        if let Ok(out) = Command::new("pidof").arg("adbd").output() {
            let s = String::from_utf8_lossy(&out.stdout).trim().to_string();
            return !s.is_empty();
        }
        false
    }

    /// Discovers active Wi-Fi IP address.
    pub fn get_ip_address() -> String {
        let dhcp_ip = Self::getprop("dhcp.wlan0.ipaddress");
        if !dhcp_ip.is_empty() {
            return dhcp_ip;
        }
        if let Ok(out) = Command::new("ip").args(["-4", "addr", "show", "wlan0"]).output() {
            let text = String::from_utf8_lossy(&out.stdout);
            for line in text.lines() {
                let trimmed = line.trim();
                if trimmed.starts_with("inet ") {
                    if let Some(part) = trimmed.split_whitespace().nth(1) {
                        if let Some(ip) = part.split('/').next() {
                            return ip.to_string();
                        }
                    }
                }
            }
        }
        "127.0.0.1".to_string()
    }

    /// Applies ADB Wireless Keep-Alive & Locks the specified port.
    pub fn apply(port: u16) -> Result<String, String> {
        if !(1024..=65535).contains(&port) {
            return Err(format!("Invalid port: {}", port));
        }

        let port_str = port.to_string();
        Logger::info(format!("[ADB-KEEPALIVE] Locking wireless ADB to port {}...", port));

        // 1. Set runtime and persistent Android properties
        Self::setprop_persistent("persist.adb.tcp.port", &port_str);
        let _ = Command::new("setprop")
            .args(["service.adb.tcp.port", &port_str])
            .stdout( std::process::Stdio::null())
            .stderr( std::process::Stdio::null())
            .status();

        // 2. Android power management / Wi-Fi sleep policy overrides
        let _ = Command::new("settings")
            .args(["put", "global", "wifi_sleep_policy", "2"])
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status();
        let _ = Command::new("settings")
            .args(["put", "global", "adb_allowed_connection_time", "0"])
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status();

        // 3. Doze whitelist to prevent suspend and network cut
        let _ = Command::new("cmd")
            .args(["deviceidle", "whitelist", "+com.android.shell"])
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status();
        let _ = Command::new("dumpsys")
            .args(["deviceidle", "whitelist", "+com.android.shell"])
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status();
        let _ = Command::new("cmd")
            .args(["deviceidle", "whitelist", "+com.android.providers.settings"])
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status();

        // 4. Start or restart adbd atomically via init (PID 1) ONLY if not already listening on target port.
        // Never use sequential `stop adbd` -> `start adbd` because `stop adbd` kills the child process group before `start adbd` executes!
        if !Self::is_adbd_running() {
            Logger::info(format!(
                "[ADB-KEEPALIVE] Starting adbd on port {}...",
                port
            ));
            let _ = Command::new("setprop")
                .args(["ctl.start", "adbd"])
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null())
                .status();
            let _ = Command::new("start")
                .arg("adbd")
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null())
                .status();
        } else if !Self::is_port_listening(port) {
            Logger::info(format!(
                "[ADB-KEEPALIVE] Atomically restarting adbd via init to bind port {}...",
                port
            ));
            let _ = Command::new("setprop")
                .args(["ctl.restart", "adbd"])
                .stdout(std::process::Stdio::null())
                .stderr(std::process::Stdio::null())
                .status();
        }

        // 5. Marker in tmpfs /dev/ for instant lock tracking
        let _ = fs::write("/dev/lynx_adb_port", port_str.as_bytes());

        // 6. Ensure dedicated cgroup-detached 1s watchdog is active
        Self::ensure_watchdog_running();

        let ip = Self::get_ip_address();
        let msg = format!(
            "Wireless ADB locked successfully on port {}. Connect via: adb connect {}:{}",
            port, ip, port
        );
        Logger::info(format!("[ADB-KEEPALIVE] {}", msg));
        Ok(msg)
    }

    /// Disables ADB Wireless Keep-Alive and resets ports.
    pub fn disable() -> Result<String, String> {
        Logger::info("[ADB-KEEPALIVE] Disabling wireless ADB port lock...");

        let _ = fs::remove_file("/dev/lynx_adb_port");
        Self::stop_watchdog();

        Self::setprop_persistent("persist.adb.tcp.port", "-1");
        let _ = Command::new("setprop")
            .args(["service.adb.tcp.port", "-1"])
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status();

        let _ = Self::save_config(false, 5555);

        let _ = Command::new("setprop")
            .args(["ctl.restart", "adbd"])
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status();

        let msg = "Wireless ADB keep-alive disabled and port reset.".to_string();
        Logger::info(format!("[ADB-KEEPALIVE] {}", msg));
        Ok(msg)
    }

    /// Spawns the dedicated cgroup-detached ADB watchdog process if not already running.
    pub fn ensure_watchdog_running() {
        let pid_path = Path::new("/dev/lynxd_adb.pid");
        if pid_path.exists() {
            if let Ok(s) = fs::read_to_string(pid_path) {
                if let Ok(existing_pid) = s.trim().parse::<u32>() {
                    if Path::new(&format!("/proc/{}", existing_pid)).exists() {
                        return;
                    }
                }
            }
        }
        let exe = std::env::current_exe()
            .unwrap_or_else(|_| std::path::PathBuf::from("/data/adb/modules/Lynx/system/bin/lynxd"));
        let _ = Command::new(exe)
            .args(["adb", "watchdog"])
            .stdin(std::process::Stdio::null())
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .spawn();
    }

    /// Stops the dedicated ADB watchdog process if running.
    pub fn stop_watchdog() {
        let pid_path = Path::new("/dev/lynxd_adb.pid");
        if pid_path.exists() {
            if let Ok(s) = fs::read_to_string(pid_path) {
                if let Ok(pid) = s.trim().parse::<u32>() {
                    #[cfg(unix)]
                    {
                        extern "C" {
                            fn kill(pid: i32, sig: i32) -> i32;
                        }
                        unsafe {
                            kill(pid as i32, 15);
                        }
                    }
                }
            }
            let _ = fs::remove_file(pid_path);
        }
    }

    /// Dedicated 1-second watchdog loop, detached from `adbd` session and cgroups.
    /// Uses zero-fork `/proc/net/tcp` inspection (0.02ms) when healthy, and auto-heals
    /// within 1 second if Android stops `adbd` on screen-off.
    pub fn run_watchdog_loop() {
        crate::daemon::DaemonLifecycle::detach_from_adbd_cgroup();
        let pid_path = Path::new("/dev/lynxd_adb.pid");
        let my_pid = std::process::id();
        if pid_path.exists() {
            if let Ok(s) = fs::read_to_string(pid_path) {
                if let Ok(existing_pid) = s.trim().parse::<u32>() {
                    if existing_pid != my_pid
                        && Path::new(&format!("/proc/{}", existing_pid)).exists()
                    {
                        return;
                    }
                }
            }
        }
        let _ = fs::write(pid_path, my_pid.to_string());
        Logger::info(format!(
            "[ADB-WATCHDOG] Dedicated cgroup-detached ADB Keep-Alive watchdog started [PID: {}]",
            my_pid
        ));

        loop {
            std::thread::sleep(std::time::Duration::from_millis(1000));

            let port = if let Ok(p_str) = fs::read_to_string("/dev/lynx_adb_port") {
                p_str.trim().parse::<u16>().unwrap_or(0)
            } else {
                let cfg = Self::load_config();
                if !cfg.enabled {
                    break;
                }
                let _ = fs::write("/dev/lynx_adb_port", cfg.port.to_string());
                cfg.port
            };

            if !(1024..=65535).contains(&port) {
                break;
            }

            // Fast-path: 0.02ms in-process read of /proc/net/tcp (zero forks!)
            if !Self::is_port_listening(port) {
                Logger::warn(format!(
                    "[ADB-WATCHDOG] Port {} closed on screen-off/sleep! Restoring immediately...",
                    port
                ));
                let port_str = port.to_string();
                Self::setprop_persistent("persist.adb.tcp.port", &port_str);
                let _ = Command::new("setprop")
                    .args(["service.adb.tcp.port", &port_str])
                    .stdout(std::process::Stdio::null())
                    .stderr(std::process::Stdio::null())
                    .status();
                if !Self::is_adbd_running() {
                    let _ = Command::new("setprop")
                        .args(["ctl.start", "adbd"])
                        .stdout(std::process::Stdio::null())
                        .stderr(std::process::Stdio::null())
                        .status();
                    let _ = Command::new("start")
                        .arg("adbd")
                        .stdout(std::process::Stdio::null())
                        .stderr(std::process::Stdio::null())
                        .status();
                } else {
                    let _ = Command::new("setprop")
                        .args(["ctl.restart", "adbd"])
                        .stdout(std::process::Stdio::null())
                        .stderr(std::process::Stdio::null())
                        .status();
                }
                std::thread::sleep(std::time::Duration::from_millis(1500));
            }
        }

        let _ = fs::remove_file(pid_path);
        Logger::info("[ADB-WATCHDOG] Watchdog exited cleanly.");
    }

    /// Applies configuration directly from config.json (used at boot time).
    pub fn apply_from_config() {
        let cfg = Self::load_config();
        if cfg.enabled {
            Logger::info(format!(
                "[ADB-KEEPALIVE] Auto-applying persistent port lock from config.json (Port: {})...",
                cfg.port
            ));
            let _ = Self::apply(cfg.port);
        }
    }

    /// Autonomous watchdog tick (called by daemon runner).
    pub fn tick() {
        let port = if let Ok(p_str) = fs::read_to_string("/dev/lynx_adb_port") {
            p_str.trim().parse::<u16>().unwrap_or(0)
        } else {
            let cfg = Self::load_config();
            if !cfg.enabled {
                return;
            }
            let _ = fs::write("/dev/lynx_adb_port", cfg.port.to_string());
            cfg.port
        };

        if !(1024..=65535).contains(&port) {
            return;
        }

        Self::ensure_watchdog_running();

        if !Self::is_port_listening(port) {
            Logger::warn(format!(
                "[ADB-WATCHDOG] Port {} drop detected during daemon tick. Auto-healing...",
                port
            ));
            let _ = Self::apply(port);
        }
    }

    /// Status representation formatted as JSON for CLI, WebUI, and Companion App.
    pub fn status_json() -> String {
        let cfg = Self::load_config();
        let svc_port = Self::getprop("service.adb.tcp.port");
        let persist_port = Self::getprop("persist.adb.tcp.port");
        let is_listening = Self::is_port_listening(cfg.port);
        let is_adbd = Self::is_adbd_running();
        let ip = Self::get_ip_address();

        let mut out = String::new();
        let _ = write!(
            out,
            "{{\"enabled\":{},\"configured_port\":{},\"service_port\":\"{}\",\"persist_port\":\"{}\",\"is_listening\":{},\"is_adbd_running\":{},\"ip_address\":\"{}\"}}",
            cfg.enabled,
            cfg.port,
            svc_port,
            persist_port,
            is_listening,
            is_adbd,
            ip
        );
        out
    }
}
