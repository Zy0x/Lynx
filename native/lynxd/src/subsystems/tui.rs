use std::fs;
use std::io::{self, Write};
use std::process::Command;
use std::thread::sleep;
use std::time::Duration;

use crate::hardware::ScoreBoard;
use crate::state::ConfigStore;
use crate::subsystems::{GpuController, MaintenanceController, MemoryController, SystemController};

const C_CYAN: &str = "\x1b[1;36m";
const C_GREEN: &str = "\x1b[1;32m";
const C_YELLOW: &str = "\x1b[1;33m";
const C_RESET: &str = "\x1b[0m";

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
        println!("  {}PILIH PROFIL PERFORMA:{}", C_GREEN, C_RESET);
        println!("  [1] Auto (AI)       - Adaptive Foreground Watcher");
        println!("  [2] Balance         - Daily Energy-Aware Efficiency");
        println!("  [3] Performance     - Sustained High Clocks for Gaming");
        println!("  [4] Extreme         - Unrestricted (Phone Cooler Req.)");
        println!("  [5] Powersave       - Maximum Battery Life");
        println!("  [6] Standby/Dormant - Safe Baseline Mode");
        println!("  [0] Kembali\n");
        print!("  Pilih opsi [0-6]: ");
        match Self::read_line().as_str() {
            "1" => Self::apply_profile("auto"),
            "2" => Self::apply_profile("balance"),
            "3" => Self::apply_profile("performance"),
            "4" => Self::apply_profile("extreme"),
            "5" => Self::apply_profile("powersave"),
            "6" => Self::apply_profile("dormant"),
            _ => return,
        }
        println!("{}[OK] Profil berhasil diperbarui!{}", C_GREEN, C_RESET);
        sleep(Duration::from_millis(900));
    }

    fn menu_charging() {
        Self::show_header();
        let bypass = Self::get_cfg("charging.bypass_enabled", "false");
        let cur = Self::get_cfg("charging.limit_current_ma", "2400");
        println!("  {}PENGATURAN CHARGING CONTROLLER:{}", C_GREEN, C_RESET);
        println!("  • Bypass Charging Aktif : {}{}{}", C_YELLOW, bypass, C_RESET);
        println!("  • Batas Arus Gaming     : {}{} mA{}\n", C_YELLOW, cur, C_RESET);
        println!("  [1] Toggle Bypass Charging (ON/OFF)");
        println!("  [2] Set Batas Arus (1500mA - Aman)");
        println!("  [3] Set Batas Arus (2400mA - Cepat)");
        println!("  [4] Set Batas Arus (6000mA - Extreme 33W+)");
        println!("  [0] Kembali\n");
        print!("  Pilih opsi [0-4]: ");
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
        println!("{}[OK] Setelan charging diperbarui!{}", C_GREEN, C_RESET);
        sleep(Duration::from_millis(900));
    }

    fn menu_advanced() {
        Self::show_header();
        let wifi = Self::get_cfg("network.wifi_ping_stabilizer", "true");
        let therm = Self::get_cfg("thermal.full_bypass", "false");
        let touch = Self::get_cfg("display_touch.touchboost", "true");
        let joyose = Self::get_cfg("oem_neutralizer.joyose_neutralize", "false");
        println!("  {}ADVANCED ENGINE TOGGLES:{}", C_GREEN, C_RESET);
        println!("  [1] Wi-Fi Ping Stabilizer   : {}{}{}", C_YELLOW, wifi, C_RESET);
        println!("  [2] Full Thermal Bypass     : {}{}{}", C_YELLOW, therm, C_RESET);
        println!("  [3] TouchBoost & 120Hz Lock : {}{}{}", C_YELLOW, touch, C_RESET);
        println!("  [4] Netralkan Joyose/GOS    : {}{}{}", C_YELLOW, joyose, C_RESET);
        println!("  [0] Kembali\n");
        print!("  Pilih toggle [0-4]: ");
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
        println!("{}[OK] Status fitur berhasil diperbarui!{}", C_GREEN, C_RESET);
        sleep(Duration::from_millis(900));
    }

    fn menu_gpu() {
        Self::show_header();
        let info = GpuController::info_json();
        println!("  {}GPU TUNER & DVFS CONTROLLER:{}", C_GREEN, C_RESET);
        println!("  • Status JSON: {}\n", info);
        println!("  [1] Set Boost Level 0 (Hemat Daya / Default)");
        println!("  [2] Set Boost Level 1 (Normal Boost)");
        println!("  [3] Set Boost Level 2 (Hardcore Boost)");
        println!("  [0] Kembali\n");
        print!("  Pilih opsi [0-3]: ");
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
        println!("{}[OK] Pengaturan GPU berhasil diperbarui!{}", C_GREEN, C_RESET);
        sleep(Duration::from_millis(900));
    }

    pub fn run() {
        loop {
            Self::show_header();
            println!("  [1] Ganti Profil Performa");
            println!("  [2] Charging Controller & Motherboard Bypass");
            println!("  [3] Advanced Engine (Thermal, Touch, Wi-Fi, Joyose)");
            println!("  [4] Jalankan CCleaner (Memory & Cache Compaction)");
            println!("  [5] Optimasi SQLite & Storage TRIM");
            println!("  [6] Ekspor Laporan Diagnostik (.ZIP)");
            println!("  [7] Status Lengkap Sistem");
            println!("  [8] GPU Tuner & DVFS Controller");
            println!("  [0] Keluar\n");
            print!("  Pilih menu [0-8]: ");
            match Self::read_line().as_str() {
                "1" => Self::menu_profile(),
                "2" => Self::menu_charging(),
                "3" => Self::menu_advanced(),
                "4" => {
                    println!("{}Menjalankan pembersihan cache & RAM...{}", C_YELLOW, C_RESET);
                    println!("{}", MemoryController::clean_memory());
                    sleep(Duration::from_millis(1200));
                }
                "5" => {
                    println!("{}Menjalankan SQLite VACUUM & Storage TRIM...{}", C_YELLOW, C_RESET);
                    println!("{}", MaintenanceController::run(true));
                    sleep(Duration::from_millis(1200));
                }
                "6" => {
                    println!("{}Membuat laporan diagnostik...{}", C_YELLOW, C_RESET);
                    println!("{}", SystemController::export_diagnostic_zip());
                    sleep(Duration::from_millis(1500));
                }
                "7" => {
                    Self::show_header();
                    if let Ok(exe) = std::env::current_exe() {
                        let _ = Command::new(exe).arg("status").status();
                    }
                    print!("\nTekan [Enter] untuk kembali...");
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
