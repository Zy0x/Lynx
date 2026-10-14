use std::fs;
use std::io::{self, Write};
use std::process::Command;
use std::thread::sleep;
use std::time::Duration;
use std::sync::OnceLock;

use crate::hardware::ScoreBoard;
use crate::state::ConfigStore;
use crate::subsystems::{GpuController, MaintenanceController, MemoryController, SystemController};

const C_CYAN: &str = "\x1b[1;36m";
const C_GREEN: &str = "\x1b[1;32m";
const C_YELLOW: &str = "\x1b[1;33m";
const C_RESET: &str = "\x1b[0m";

pub(crate) fn is_english() -> bool {
    static ENGLISH: OnceLock<bool> = OnceLock::new();
    *ENGLISH.get_or_init(|| {
        let mut locale = std::env::var("LANG").unwrap_or_default();
        if locale.is_empty() {
            locale = Command::new("getprop").arg("persist.sys.locale").output()
                .ok().map(|output| String::from_utf8_lossy(&output.stdout).trim().to_string()).unwrap_or_default();
        }
        if locale.is_empty() {
            locale = Command::new("getprop").arg("ro.product.locale").output()
                .ok().map(|output| String::from_utf8_lossy(&output.stdout).trim().to_string()).unwrap_or_default();
        }
        locale.to_ascii_lowercase().starts_with("en")
    })
}

pub(crate) fn ui<'a>(indonesian: &'a str, english: &'a str) -> &'a str {
    if is_english() { english } else { indonesian }
}

pub struct TuiController;

impl TuiController {
    fn clear_screen() {
        print!("\x1b[2J\x1b[H");
        let _ = io::stdout().flush();
    }

    fn read_line() -> String {
        let _ = io::stdout().flush();
        let mut s = String::new();
        let _ = io::stdin().read_line(&mut s);
        s.trim().to_string()
    }

    fn get_cfg(key: &str, default: &str) -> String {
        ConfigStore::get(key).unwrap_or_else(|| default.to_string())
    }

    fn set_cfg(key: &str, val: &str) {
        let _ = ConfigStore::set_batch(&[(key.to_string(), val.to_string(), None)]);
    }

    fn show_header() {
        Self::clear_screen();
        let prof = Self::get_cfg("active_profile", "balance");
        let mut soc = fs::read_to_string("/data/adb/modules/Lynx/target_soc")
            .unwrap_or_default()
            .trim()
            .to_string();
        if soc.is_empty() || soc == "generic" || soc == "unknown" {
            soc = ScoreBoard::evaluate().0.as_platform_id().to_string();
        }
        println!("{}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━{}", C_CYAN, C_RESET);
        println!(
            "{}  L Y N X   [Codename: Deity]   v{}{}",
            C_CYAN,
            env!("CARGO_PKG_VERSION"),
            C_RESET
        );
        println!(
            "{}  Author: ɴᴏɪʀ  |  SoC: {}{}{}  |  Profile: {}{}{}",
            C_CYAN, C_GREEN, soc, C_CYAN, C_YELLOW, prof, C_RESET
        );
        println!("{}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━{}", C_CYAN, C_RESET);
    }

    fn apply_profile(profile: &str) {
        Self::set_cfg("active_profile", profile);
        if let Ok(exe) = std::env::current_exe() {
            if profile == "auto" {
                let _ = Command::new(&exe).args(["daemon", "stop"]).status();
                let _ = Command::new(&exe).args(["daemon", "run"]).spawn();
            } else if profile != "dormant" {
                let _ = Command::new(&exe).args(["profile", "apply", profile]).status();
            }
        }
    }

    fn menu_profile() {
        Self::show_header();
        println!("  {}{}:{}", C_GREEN, ui("PILIH PROFIL PERFORMA", "CHOOSE PERFORMANCE PROFILE"), C_RESET);
        println!("{}", ui("  [1] Auto (AI)       - Pemantau aplikasi aktif", "  [1] Auto (AI)       - Adaptive foreground watcher"));
        println!("{}", ui("  [2] Balance         - Efisiensi harian", "  [2] Balance         - Daily energy efficiency"));
        println!("  [3] Performance     - {}", ui("Clock tinggi berkelanjutan untuk gaming", "Sustained high clocks for gaming"));
        println!("  [4] Extreme         - {}", ui("Tanpa batas (disarankan phone cooler)", "Unrestricted (phone cooler recommended)"));
        println!("  [5] Powersave       - {}", ui("Daya tahan baterai maksimum", "Maximum battery life"));
        println!("  [6] Standby/Dormant - {}", ui("Mode dasar aman", "Safe baseline mode"));
        println!("  [0] {}\n", ui("Kembali", "Back"));
        print!("  {} [0-6]: ", ui("Pilih opsi", "Choose an option"));
        match Self::read_line().as_str() {
            "1" => Self::apply_profile("auto"),
            "2" => Self::apply_profile("balance"),
            "3" => Self::apply_profile("performance"),
            "4" => Self::apply_profile("extreme"),
            "5" => Self::apply_profile("powersave"),
            "6" => Self::apply_profile("dormant"),
            _ => return,
        }
        println!("{}[OK] {}!{}", C_GREEN, ui("Profil berhasil diperbarui", "Profile updated successfully"), C_RESET);
        sleep(Duration::from_millis(900));
    }

    fn menu_charging() {
        Self::show_header();
        let bypass = Self::get_cfg("charging.bypass_enabled", "false");
        let cur = Self::get_cfg("charging.limit_current_ma", "2400");
        println!("  {}{}:{}", C_GREEN, ui("PENGATURAN PENGISIAN", "CHARGING SETTINGS"), C_RESET);
        println!("  • {} : {}{}{}", ui("Bypass Charging Aktif", "Bypass Charging Active"), C_YELLOW, bypass, C_RESET);
        println!("  • {}     : {}{} mA{}\n", ui("Batas Arus Gaming", "Gaming Current Limit"), C_YELLOW, cur, C_RESET);
        println!("  [1] {} (ON/OFF)", ui("Alihkan Bypass Charging", "Toggle Bypass Charging"));
        println!("  [2] {} (1500mA - {})", ui("Atur Batas Arus", "Set Current Limit"), ui("Aman", "Safe"));
        println!("  [3] {} (2400mA - {})", ui("Atur Batas Arus", "Set Current Limit"), ui("Cepat", "Fast"));
        println!("  [4] {} (6000mA - Extreme 33W+)", ui("Atur Batas Arus", "Set Current Limit"));
        println!("  [0] {}\n", ui("Kembali", "Back"));
        print!("  {} [0-4]: ", ui("Pilih opsi", "Choose an option"));
        match Self::read_line().as_str() {
            "1" => {
                let next = if bypass == "true" { "false" } else { "true" };
                Self::set_cfg("charging.bypass_enabled", next);
            }
            "2" => {
                let _ = ConfigStore::set_batch(&[
                    ("charging.limit_current_ma".to_string(), "1500".to_string(), None),
                    ("charging.fast_charge_override".to_string(), "false".to_string(), None),
                ]);
            }
            "3" => {
                let _ = ConfigStore::set_batch(&[
                    ("charging.limit_current_ma".to_string(), "2400".to_string(), None),
                    ("charging.fast_charge_override".to_string(), "false".to_string(), None),
                ]);
            }
            "4" => {
                let _ = ConfigStore::set_batch(&[
                    ("charging.limit_current_ma".to_string(), "6000".to_string(), None),
                    ("charging.fast_charge_override".to_string(), "true".to_string(), None),
                ]);
            }
            _ => return,
        }
        if let Ok(exe) = std::env::current_exe() {
            let _ = Command::new(exe).args(["charging", "apply"]).status();
        }
        println!("{}[OK] {}!{}", C_GREEN, ui("Setelan pengisian diperbarui", "Charging settings updated"), C_RESET);
        sleep(Duration::from_millis(900));
    }

    fn menu_advanced() {
        Self::show_header();
        let wifi = Self::get_cfg("network.wifi_ping_stabilizer", "true");
        let therm = Self::get_cfg("thermal.full_bypass", "false");
        let touch = Self::get_cfg("display_touch.touchboost", "true");
        let joyose = Self::get_cfg("oem_neutralizer.joyose_neutralize", "false");
        println!("  {}{}:{}", C_GREEN, ui("KONTROL ENGINE LANJUTAN", "ADVANCED ENGINE CONTROLS"), C_RESET);
        println!("  [1] Wi-Fi Ping Stabilizer   : {}{}{}", C_YELLOW, wifi, C_RESET);
        println!("  [2] Full Thermal Bypass     : {}{}{}", C_YELLOW, therm, C_RESET);
        println!("  [3] TouchBoost & 120Hz Lock : {}{}{}", C_YELLOW, touch, C_RESET);
        println!("  [4] {}    : {}{}{}", ui("Netralkan Joyose/GOS", "Neutralize Joyose/GOS"), C_YELLOW, joyose, C_RESET);
        println!("  [0] {}\n", ui("Kembali", "Back"));
        print!("  {} [0-4]: ", ui("Pilih toggle", "Choose a toggle"));
        match Self::read_line().as_str() {
            "1" => {
                Self::set_cfg(
                    "network.wifi_ping_stabilizer",
                    if wifi == "true" { "false" } else { "true" },
                );
            }
            "2" => {
                Self::set_cfg(
                    "thermal.full_bypass",
                    if therm == "true" { "false" } else { "true" },
                );
            }
            "3" => {
                Self::set_cfg(
                    "display_touch.touchboost",
                    if touch == "true" { "false" } else { "true" },
                );
            }
            "4" => {
                Self::set_cfg(
                    "oem_neutralizer.joyose_neutralize",
                    if joyose == "true" { "false" } else { "true" },
                );
            }
            _ => return,
        }
        println!("{}[OK] {}!{}", C_GREEN, ui("Status fitur berhasil diperbarui", "Feature status updated successfully"), C_RESET);
        sleep(Duration::from_millis(900));
    }

    fn menu_gpu() {
        Self::show_header();
        let info = GpuController::info_json();
        println!("  {}{}:{}", C_GREEN, ui("PENGATUR GPU & PENGENDALI DVFS", "GPU TUNER & DVFS CONTROLLER"), C_RESET);
        println!("  • {}: {}\n", ui("Status JSON", "JSON Status"), info);
        println!("  [1] {} 0 ({})", ui("Atur Boost Level", "Set Boost Level"), ui("Hemat Daya / Bawaan", "Power Saver / Default"));
        println!("  [2] {} 1 ({})", ui("Atur Boost Level", "Set Boost Level"), ui("Boost Normal", "Normal Boost"));
        println!("  [3] {} 2 (Hardcore Boost)", ui("Atur Boost Level", "Set Boost Level"));
        println!("  [0] {}\n", ui("Kembali", "Back"));
        print!("  {} [0-3]: ", ui("Pilih opsi", "Choose an option"));
        match Self::read_line().as_str() {
            "1" => {
                let _ = GpuController::set_boost(0);
            }
            "2" => {
                let _ = GpuController::set_boost(1);
            }
            "3" => {
                let _ = GpuController::set_boost(2);
            }
            _ => return,
        }
        println!("{}[OK] {}!{}", C_GREEN, ui("Pengaturan GPU berhasil diperbarui", "GPU settings updated successfully"), C_RESET);
        sleep(Duration::from_millis(900));
    }

    pub fn run() {
        loop {
            Self::show_header();
            println!("  [1] {}", ui("Ganti Profil Performa", "Change Performance Profile"));
            println!("  [2] {}", ui("Pengontrol Pengisian & Bypass Motherboard", "Charging Controller & Motherboard Bypass"));
            println!("  [3] {}", ui("Engine Lanjutan (Thermal, Touch, Wi-Fi, Joyose)", "Advanced Engine (Thermal, Touch, Wi-Fi, Joyose)"));
            println!("  [4] {}", ui("Jalankan CCleaner (Pemadatan Memori & Cache)", "Run CCleaner (Memory & Cache Compaction)"));
            println!("  [5] {}", ui("Optimasi SQLite & Storage TRIM", "SQLite Optimization & Storage TRIM"));
            println!("  [6] {} (.ZIP)", ui("Ekspor Laporan Diagnostik", "Export Diagnostic Report"));
            println!("  [7] {}", ui("Status Lengkap Sistem", "Full System Status"));
            println!("  [8] GPU Tuner & DVFS Controller");
            println!("  [0] {}\n", ui("Keluar", "Exit"));
            print!("  {} [0-8]: ", ui("Pilih menu", "Choose a menu option"));
            match Self::read_line().as_str() {
                "1" => Self::menu_profile(),
                "2" => Self::menu_charging(),
                "3" => Self::menu_advanced(),
                "4" => {
                    println!("{}{}...{}", C_YELLOW, ui("Menjalankan pembersihan cache & RAM", "Cleaning cache and memory"), C_RESET);
                    println!("{}", MemoryController::clean_memory());
                    sleep(Duration::from_millis(1200));
                }
                "5" => {
                    println!("{}{}...{}", C_YELLOW, ui("Menjalankan SQLite VACUUM & Storage TRIM", "Running SQLite VACUUM & Storage TRIM"), C_RESET);
                    println!("{}", MaintenanceController::run(true));
                    sleep(Duration::from_millis(1200));
                }
                "6" => {
                    println!("{}{}...{}", C_YELLOW, ui("Membuat laporan diagnostik", "Creating diagnostic report"), C_RESET);
                    println!("{}", SystemController::export_diagnostic_zip());
                    sleep(Duration::from_millis(1500));
                }
                "7" => {
                    Self::show_header();
                    if let Ok(exe) = std::env::current_exe() {
                        let _ = Command::new(exe).arg("status").status();
                    }
                    print!("\n{}", ui("Tekan [Enter] untuk kembali...", "Press [Enter] to return..."));
                    let _ = Self::read_line();
                }
                "8" => Self::menu_gpu(),
                "0" | "q" | "exit" => {
                    Self::clear_screen();
                    return;
                }
                _ => {}
            }
        }
    }
}
