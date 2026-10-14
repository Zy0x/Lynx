use std::fs;
use std::path::PathBuf;

use crate::utils::Logger;
use crate::subsystems::tui::{is_english, ui};

pub struct ApplistController;

impl ApplistController {
    fn candidate_paths() -> Vec<PathBuf> {
        vec![
            PathBuf::from("/data/adb/lynx/applist_perf.txt"),
            PathBuf::from("/data/adb/modules/Lynx/core/applist_perf.txt"),
            PathBuf::from("/storage/emulated/0/Lynx/applist_perf.txt"),
        ]
    }

    fn primary_path() -> PathBuf {
        let p = PathBuf::from("/data/adb/lynx/applist_perf.txt");
        if let Some(parent) = p.parent() {
            let _ = fs::create_dir_all(parent);
        }
        p
    }

    /// Reads all unique packages from user and system app lists.
    pub fn list() -> Vec<String> {
        let mut apps = Vec::new();
        for path in Self::candidate_paths() {
            if let Ok(content) = fs::read_to_string(&path) {
                for line in content.lines() {
                    let trimmed = line.trim();
                    if !trimmed.is_empty() && !trimmed.starts_with('#') {
                        if !apps.contains(&trimmed.to_string()) {
                            apps.push(trimmed.to_string());
                        }
                    }
                }
            }
        }
        apps
    }

    /// Atomically adds a package to the primary applist.
    pub fn add(pkg: &str) -> Result<String, String> {
        let safe_pkg: String = pkg.chars().filter(|c| c.is_alphanumeric() || *c == '.' || *c == '_').collect();
        if safe_pkg.is_empty() {
            return Err(ui("Nama paket kosong atau tidak valid", "Empty or invalid package name").to_string());
        }

        let mut current = Self::list();
        if current.contains(&safe_pkg) {
            return Ok(if is_english() {
                format!("Package '{}' already present in performance list.", safe_pkg)
            } else {
                format!("Paket '{}' sudah ada dalam daftar performa.", safe_pkg)
            });
        }

        current.push(safe_pkg.clone());
        current.sort();

        let primary = Self::primary_path();
        let mut content = String::from("# Lynx Universal Performance Game/App List\n");
        for app in &current {
            content.push_str(app);
            content.push('\n');
        }

        fs::write(&primary, &content).map_err(|e| {
            if is_english() { format!("Failed to write applist: {}", e) }
            else { format!("Gagal menulis daftar aplikasi: {}", e) }
        })?;
        Logger::info(if is_english() { format!("Added '{}' to Lynx performance applist.", safe_pkg) } else { format!("'{}' ditambahkan ke daftar performa Lynx.", safe_pkg) });
        Ok(if is_english() {
            format!("Successfully added '{}' to performance list.", safe_pkg)
        } else {
            format!("Berhasil menambahkan '{}' ke daftar performa.", safe_pkg)
        })
    }

    /// Atomically removes a package from all known applists.
    pub fn remove(pkg: &str) -> Result<String, String> {
        let safe_pkg: String = pkg.chars().filter(|c| c.is_alphanumeric() || *c == '.' || *c == '_').collect();
        if safe_pkg.is_empty() {
            return Err(ui("Nama paket kosong atau tidak valid", "Empty or invalid package name").to_string());
        }

        let mut removed = false;
        for path in Self::candidate_paths() {
            if path.exists() {
                if let Ok(content) = fs::read_to_string(&path) {
                    let mut lines = Vec::new();
                    let mut file_modified = false;
                    for line in content.lines() {
                        let trimmed = line.trim();
                        if trimmed == safe_pkg {
                            file_modified = true;
                            removed = true;
                        } else {
                            lines.push(line.to_string());
                        }
                    }
                    if file_modified {
                        let new_content = lines.join("\n") + "\n";
                        let _ = fs::write(&path, new_content);
                    }
                }
            }
        }

        if removed {
            Logger::info(if is_english() { format!("Removed '{}' from Lynx performance applist.", safe_pkg) } else { format!("'{}' dihapus dari daftar performa Lynx.", safe_pkg) });
            Ok(if is_english() {
                format!("Successfully removed '{}' from performance list.", safe_pkg)
            } else {
                format!("Berhasil menghapus '{}' dari daftar performa.", safe_pkg)
            })
        } else {
            Ok(if is_english() {
                format!("Package '{}' was not found in performance list.", safe_pkg)
            } else {
                format!("Paket '{}' tidak ditemukan dalam daftar performa.", safe_pkg)
            })
        }
    }

    pub fn check(pkg: &str) -> bool {
        let list = Self::list();
        list.contains(&pkg.to_string())
    }
}
