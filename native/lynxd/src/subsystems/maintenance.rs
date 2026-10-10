use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;

use crate::daemon::screen::{ScreenDetector, ScreenState};
use crate::sysfs::{SysfsWriter, WriteMode};

pub struct MaintenanceController;

impl MaintenanceController {
    /// Determines whether the device is currently under user workload (active gaming or screen ON).
    pub fn is_device_busy() -> bool {
        if Path::new("/dev/lynx_active_game").exists() {
            return true;
        }
        let detector = ScreenDetector::probe();
        matches!(detector.detect(), ScreenState::On)
    }

    fn remove_dir_contents(dir_path: impl AsRef<Path>) -> usize {
        let p = dir_path.as_ref();
        if !p.exists() || !p.is_dir() {
            return 0;
        }

        let mut count = 0;
        if let Ok(entries) = fs::read_dir(p) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.is_file() || path.is_symlink() {
                    if fs::remove_file(&path).is_ok() {
                        count += 1;
                    }
                } else if path.is_dir() {
                    if fs::remove_dir_all(&path).is_ok() {
                        count += 1;
                    }
                }
            }
        }
        count
    }

    /// Prunes system tombstones, ANR traces, dropbox crash records, and local temporary files.
    pub fn prune_system_clutter() -> usize {
        let mut total = 0;
        total += Self::remove_dir_contents("/data/tombstones");
        total += Self::remove_dir_contents("/data/anr");
        total += Self::remove_dir_contents("/data/system/dropbox");
        total += Self::remove_dir_contents("/data/local/tmp");
        total
    }

    /// Trims flash storage blocks across active data and cache mount points.
    pub fn trim_flash_storage() -> Vec<String> {
        let mut reports = Vec::new();
        for mount in &["/data", "/cache"] {
            if let Ok(output) = Command::new("fstrim").args(&["-v", mount]).output() {
                let msg = String::from_utf8_lossy(&output.stdout).trim().to_string();
                if !msg.is_empty() {
                    reports.push(format!("{}: {}", mount, msg));
                }
            }
        }
        reports
    }

    /// Performs gentle zero-stutter kernel memory compaction.
    pub fn compact_memory() -> bool {
        let p = "/proc/sys/vm/compact_memory";
        if Path::new(p).exists() {
            SysfsWriter::write(p, "1", WriteMode::ForcePermission).is_ok()
        } else {
            false
        }
    }

    fn find_db_files(root: &Path, max_depth: usize, current_depth: usize, acc: &mut Vec<PathBuf>) {
        if current_depth > max_depth {
            return;
        }
        if let Ok(entries) = fs::read_dir(root) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.is_dir() {
                    Self::find_db_files(&path, max_depth, current_depth + 1, acc);
                } else if path.is_file() {
                    if let Some(ext) = path.extension() {
                        if ext == "db" {
                            acc.push(path);
                        }
                    }
                }
            }
        }
    }

    /// Optimizes and defragments SQLite databases in /data/system and /data/data.
    pub fn vacuum_sqlite_databases() -> usize {
        let bin_candidates = [
            "/data/adb/modules/Lynx/system/bin/sqlite3",
            "/system/bin/sqlite3",
            "sqlite3",
        ];
        let sqlite_bin = bin_candidates.iter().find(|&&b| Path::new(b).exists() || b == "sqlite3");
        let bin = match sqlite_bin {
            Some(&b) => b,
            None => return 0,
        };

        let mut db_files = Vec::new();
        Self::find_db_files(Path::new("/data/system"), 2, 0, &mut db_files);

        // Scan databases in /data/data (top 20 high-frequency apps to prevent I/O choke)
        if let Ok(entries) = fs::read_dir("/data/data") {
            for entry in entries.flatten().take(25) {
                let db_dir = entry.path().join("databases");
                if db_dir.exists() {
                    Self::find_db_files(&db_dir, 1, 0, &mut db_files);
                }
            }
        }

        let mut optimized = 0;
        for db in db_files {
            if let Some(p_str) = db.to_str() {
                let res = Command::new(bin)
                    .args(&[p_str, "PRAGMA optimize; VACUUM; REINDEX;"])
                    .output();
                if res.map(|o| o.status.success()).unwrap_or(false) {
                    optimized += 1;
                }
            }
        }
        optimized
    }

    /// Executes complete system maintenance: clutter prune, flash storage TRIM, SQLite vacuum, and RAM compaction.
    pub fn run(force: bool) -> String {
        if !force && Self::is_device_busy() {
            return "{\"status\":\"skipped\",\"reason\":\"Device is currently busy (game active or screen is ON).\"}".to_string();
        }

        let clutter_removed = Self::prune_system_clutter();
        let trim_reports = Self::trim_flash_storage();
        let ram_compacted = Self::compact_memory();
        let dbs_vacuumed = Self::vacuum_sqlite_databases();

        format!(
            "{{\"status\":\"success\",\"clutter_removed\":{},\"trim_reports\":[{}],\"ram_compacted\":{},\"databases_vacuumed\":{}}}",
            clutter_removed,
            trim_reports.iter().map(|s| format!("\"{}\"", s)).collect::<Vec<_>>().join(","),
            ram_compacted,
            dbs_vacuumed
        )
    }
}
