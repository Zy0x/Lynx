use std::fs;
use std::path::Path;
use std::process::Command;

pub struct SwapController;

impl SwapController {
    /// Reads and parses /proc/swaps, returning a structured JSON string.
    pub fn info_json() -> String {
        let content = fs::read_to_string("/proc/swaps").unwrap_or_default();
        let mut entries = Vec::new();

        for line in content.lines().skip(1) {
            let parts: Vec<&str> = line.split_whitespace().collect();
            if parts.len() >= 5 {
                let filename = parts[0];
                let swap_type = parts[1];
                let size_kb = parts[2].parse::<u64>().unwrap_or(0);
                let used_kb = parts[3].parse::<u64>().unwrap_or(0);
                let priority = parts[4].parse::<i32>().unwrap_or(0);
                let is_zram = filename.contains("zram");

                entries.push(format!(
                    "{{\"filename\":\"{}\",\"type\":\"{}\",\"size_kb\":{},\"used_kb\":{},\"priority\":{},\"is_zram\":{}}}",
                    filename, swap_type, size_kb, used_kb, priority, is_zram
                ));
            }
        }

        format!("{{\"swaps\":[{}]}}", entries.join(","))
    }

    /// Disables all active swap files (excluding ZRAM devices).
    pub fn disable_all_swaps() -> Result<(), String> {
        let content = fs::read_to_string("/proc/swaps").map_err(|e| e.to_string())?;
        for line in content.lines().skip(1) {
            let parts: Vec<&str> = line.split_whitespace().collect();
            if !parts.is_empty() {
                let swapfile = parts[0];
                if !swapfile.contains("zram") {
                    let _ = Command::new("swapoff").arg(swapfile).output();
                }
            }
        }
        Ok(())
    }

    fn parse_size_to_bytes(size_str: &str) -> Result<u64, String> {
        let s = size_str.trim().to_uppercase();
        if s.ends_with("GB") || s.ends_with('G') {
            let num_str = s.trim_end_matches("GB").trim_end_matches('G');
            let num: f64 = num_str.parse().map_err(|_| format!("Invalid gigabytes value '{}'", size_str))?;
            Ok((num * 1024.0 * 1024.0 * 1024.0) as u64)
        } else if s.ends_with("MB") || s.ends_with('M') {
            let num_str = s.trim_end_matches("MB").trim_end_matches('M');
            let num: f64 = num_str.parse().map_err(|_| format!("Invalid megabytes value '{}'", size_str))?;
            Ok((num * 1024.0 * 1024.0) as u64)
        } else if s.ends_with('B') {
            let num_str = s.trim_end_matches('B');
            let num: u64 = num_str.parse().map_err(|_| format!("Invalid bytes value '{}'", size_str))?;
            Ok(num)
        } else {
            let num: u64 = s.parse().map_err(|_| format!("Invalid swap size format '{}'", size_str))?;
            Ok(num)
        }
    }

    /// Creates and enables a new SWAP file at /data/swap with the specified size string (e.g. "2G", "1024M").
    pub fn set(size_str: &str) -> Result<String, String> {
        let bytes = Self::parse_size_to_bytes(size_str)?;
        let size_mb = (bytes / (1024 * 1024)).max(1);

        // 1. Disable existing swap files
        let _ = Self::disable_all_swaps();

        // 2. Remove old /data/swap if exists
        let swap_path = "/data/swap";
        if Path::new(swap_path).exists() {
            let _ = fs::remove_file(swap_path);
        }

        // 3. Allocate file (try fast fallocate first, fallback to dd)
        let fallocate_ok = Command::new("fallocate")
            .args(&["-l", &bytes.to_string(), swap_path])
            .output()
            .map(|o| o.status.success())
            .unwrap_or(false);

        if !fallocate_ok {
            let status = Command::new("dd")
                .args(&[
                    "if=/dev/zero",
                    &format!("of={}", swap_path),
                    "bs=1M",
                    &format!("count={}", size_mb),
                ])
                .status()
                .map_err(|e| format!("Failed to allocate swap with dd: {}", e))?;

            if !status.success() {
                return Err("Failed to create swap file with dd.".to_string());
            }
        }

        // 4. Permissions (chmod 600)
        let _ = Command::new("chmod").args(&["600", swap_path]).output();

        // 5. Initialize mkswap
        let mkswap_res = Command::new("mkswap")
            .arg(swap_path)
            .output()
            .map_err(|e| format!("Failed to run mkswap: {}", e))?;

        if !mkswap_res.status.success() {
            let _ = fs::remove_file(swap_path);
            return Err("mkswap failed to format /data/swap.".to_string());
        }

        // 6. Enable swapon with priority 3
        let swapon_res = Command::new("swapon")
            .args(&[swap_path, "-p", "3"])
            .output()
            .map_err(|e| format!("Failed to run swapon: {}", e))?;

        if !swapon_res.status.success() {
            return Err("swapon failed to activate /data/swap.".to_string());
        }

        Ok(format!("SWAP file successfully created and enabled at {} (Size: {} MB).", swap_path, size_mb))
    }

    /// Enables the existing /data/swap file.
    pub fn enable() -> Result<String, String> {
        let swap_path = "/data/swap";
        if !Path::new(swap_path).exists() {
            return Err("No SWAP file found at /data/swap. Run 'set' first.".to_string());
        }

        let res = Command::new("swapon")
            .args(&[swap_path, "-p", "3"])
            .output()
            .map_err(|e| format!("swapon failed: {}", e))?;

        if res.status.success() {
            Ok("SWAP file /data/swap successfully enabled.".to_string())
        } else {
            Err("Failed to enable /data/swap.".to_string())
        }
    }

    /// Disables all active swap files.
    pub fn disable() -> Result<String, String> {
        Self::disable_all_swaps()?;
        Ok("All SWAP files disabled successfully.".to_string())
    }

    /// Disables and removes /data/swap.
    pub fn remove() -> Result<String, String> {
        let _ = Self::disable_all_swaps();
        let swap_path = "/data/swap";
        if Path::new(swap_path).exists() {
            fs::remove_file(swap_path).map_err(|e| format!("Failed to delete {}: {}", swap_path, e))?;
            Ok(format!("Deleted SWAP file {}.", swap_path))
        } else {
            Ok("No SWAP file found to remove.".to_string())
        }
    }
}
