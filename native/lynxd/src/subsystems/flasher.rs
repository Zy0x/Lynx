use std::fs::{self, File};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::{SystemTime, UNIX_EPOCH};

const BACKUP_DIR: &str = "/sdcard/Lynx/backups";
const TMP_DIR: &str = "/dev/lynx_flasher_tmp";

pub struct FlasherController;

impl FlasherController {
    fn detect_slot_suffix() -> String {
        if let Ok(cmdline) = fs::read_to_string("/proc/cmdline") {
            for token in cmdline.split_whitespace() {
                if let Some(val) = token.strip_prefix("androidboot.slot_suffix=") {
                    return val.trim().to_string();
                }
            }
        }
        if let Ok(out) = Command::new("getprop").arg("ro.boot.slot_suffix").output() {
            let s = String::from_utf8_lossy(&out.stdout).trim().to_string();
            if !s.is_empty() && s != "false" {
                return s;
            }
        }
        String::new()
    }

    pub fn find_block_dev(part_name: &str) -> Option<String> {
        let slot = Self::detect_slot_suffix();
        let target_part = if !slot.is_empty() {
            format!("{}{}", part_name, slot)
        } else {
            part_name.to_string()
        };

        let candidates = [
            format!("/dev/block/by-name/{}", target_part),
            format!("/dev/block/by-name/{}", part_name),
            format!("/dev/block/bootdevice/by-name/{}", target_part),
            format!("/dev/block/bootdevice/by-name/{}", part_name),
            format!("/dev/block/mapper/{}", target_part),
            format!("/dev/block/mapper/{}", part_name),
        ];

        for c in &candidates {
            if Path::new(c).exists() {
                return Some(c.clone());
            }
        }

        if let Ok(entries) = fs::read_dir("/dev/block/platform") {
            for entry in entries.flatten() {
                let p = entry.path();
                let by_name = p.join("by-name");
                if by_name.is_dir() {
                    let t1 = by_name.join(&target_part);
                    if t1.exists() {
                        return Some(t1.to_string_lossy().to_string());
                    }
                    let t2 = by_name.join(part_name);
                    if t2.exists() {
                        return Some(t2.to_string_lossy().to_string());
                    }
                }
                if let Ok(sub_entries) = fs::read_dir(&p) {
                    for sub in sub_entries.flatten() {
                        let sub_by_name = sub.path().join("by-name");
                        if sub_by_name.is_dir() {
                            let t1 = sub_by_name.join(&target_part);
                            if t1.exists() {
                                return Some(t1.to_string_lossy().to_string());
                            }
                            let t2 = sub_by_name.join(part_name);
                            if t2.exists() {
                                return Some(t2.to_string_lossy().to_string());
                            }
                        }
                    }
                }
            }
        }

        None
    }

    fn copy_device_or_file(src: &str, dst: &str) -> Result<u64, String> {
        if let (Ok(mut reader), Ok(mut writer)) = (File::open(src), File::create(dst)) {
            let mut buf = vec![0u8; 65536];
            let mut total = 0u64;
            loop {
                match reader.read(&mut buf) {
                    Ok(0) => break,
                    Ok(n) => {
                        writer.write_all(&buf[..n]).map_err(|e| e.to_string())?;
                        total += n as u64;
                    }
                    Err(e) => return Err(e.to_string()),
                }
            }
            let _ = writer.sync_all();
            if total > 0 {
                return Ok(total);
            }
        }

        let status = Command::new("dd")
            .arg(format!("if={}", src))
            .arg(format!("of={}", dst))
            .arg("bs=4096")
            .status()
            .map_err(|e| e.to_string())?;

        if !status.success() {
            return Err(format!("dd failed copying {} to {}", src, dst));
        }
        let len = fs::metadata(dst).map(|m| m.len()).unwrap_or(0);
        Ok(len)
    }

    pub fn backup_boot() -> Result<String, String> {
        let _ = fs::create_dir_all(BACKUP_DIR);
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        let boot_dev = Self::find_block_dev("boot")
            .ok_or_else(|| "Error: Partisi 'boot' tidak ditemukan.".to_string())?;

        let boot_out = format!("{}/boot_{}.img", BACKUP_DIR, now);
        let mut out_lines = Vec::new();
        out_lines.push(format!("Mencadangkan boot partition dari {} ...", boot_dev));

        match Self::copy_device_or_file(&boot_dev, &boot_out) {
            Ok(bytes) if bytes > 0 => {
                out_lines.push(format!("[OK] Berhasil mencadangkan boot: {} ({} bytes)", boot_out, bytes));
            }
            _ => {
                let _ = fs::remove_file(&boot_out);
                return Err("Error: Gagal mencadangkan boot image.".to_string());
            }
        }

        if let Some(init_boot_dev) = Self::find_block_dev("init_boot") {
            let init_boot_out = format!("{}/init_boot_{}.img", BACKUP_DIR, now);
            out_lines.push(format!("Mencadangkan init_boot partition dari {} ...", init_boot_dev));
            if let Ok(bytes) = Self::copy_device_or_file(&init_boot_dev, &init_boot_out) {
                if bytes > 0 {
                    out_lines.push(format!(
                        "[OK] Berhasil mencadangkan init_boot: {} ({} bytes)",
                        init_boot_out, bytes
                    ));
                } else {
                    let _ = fs::remove_file(&init_boot_out);
                }
            } else {
                let _ = fs::remove_file(&init_boot_out);
            }
        }

        out_lines.push("Pencadangan partisi kernel selesai.".to_string());
        Ok(out_lines.join("\n"))
    }

    pub fn list_backups_json() -> String {
        let _ = fs::create_dir_all(BACKUP_DIR);
        let mut items = Vec::new();

        if let Ok(entries) = fs::read_dir(BACKUP_DIR) {
            let mut files: Vec<PathBuf> = entries
                .flatten()
                .map(|e| e.path())
                .filter(|p| p.is_file() && p.extension().map(|e| e == "img").unwrap_or(false))
                .collect();
            files.sort();
            files.reverse();

            for path in files {
                let name = path
                    .file_name()
                    .map(|n| n.to_string_lossy().to_string())
                    .unwrap_or_default();
                let full_path = path.to_string_lossy().to_string();
                let meta = fs::metadata(&path).ok();
                let size = meta.as_ref().map(|m| m.len()).unwrap_or(0);
                let ts = meta
                    .and_then(|m| m.modified().ok())
                    .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
                    .map(|d| d.as_secs().to_string())
                    .unwrap_or_default();

                items.push(format!(
                    "{{\"name\":\"{}\",\"path\":\"{}\",\"size\":{},\"date\":\"{}\"}}",
                    name, full_path, size, ts
                ));
            }
        }

        format!("{{\"backups\":[{}]}}", items.join(","))
    }

    pub fn restore_boot(backup_file: &str) -> Result<String, String> {
        let p = Path::new(backup_file);
        if !p.is_file() {
            return Err(format!("Error: File backup tidak ditemukan: {}", backup_file));
        }

        let filename = p
            .file_name()
            .map(|n| n.to_string_lossy().to_string())
            .unwrap_or_default();

        let target_part = if filename.starts_with("init_boot") {
            "init_boot"
        } else if filename.starts_with("vendor_boot") {
            "vendor_boot"
        } else {
            "boot"
        };

        let target_dev = Self::find_block_dev(target_part).ok_or_else(|| {
            format!("Error: Perangkat partisi {} tidak ditemukan.", target_part)
        })?;

        let status = Command::new("dd")
            .arg(format!("if={}", backup_file))
            .arg(format!("of={}", target_dev))
            .arg("bs=4096")
            .status()
            .map_err(|e| e.to_string())?;

        if !status.success() {
            return Err(format!("Error: Gagal menulis ke partisi {}.", target_dev));
        }

        let _ = Command::new("sync").status();
        Ok(format!(
            "Memulihkan {} ke {}...\n[OK] Partisi {} berhasil dipulihkan dari {}.",
            filename, target_dev, target_part, filename
        ))
    }

    pub fn flash_anykernel(zip_path: &str) -> Result<String, String> {
        let p = Path::new(zip_path);
        if !p.is_file() {
            return Err(format!("Error: Berkas zip tidak ditemukan: {}", zip_path));
        }

        let mut logs = Vec::new();
        logs.push("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━".to_string());
        logs.push("  Lynx Kernel Manager - Native AnyKernel3 Flasher".to_string());
        logs.push("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━".to_string());
        if let Some(fname) = p.file_name() {
            logs.push(format!("Target ZIP: {}", fname.to_string_lossy()));
        }

        logs.push("Mengamankan kernel lama...".to_string());
        match Self::backup_boot() {
            Ok(b_msg) => logs.push(b_msg),
            Err(e) => logs.push(format!("Peringatan: Auto-backup gagal ({}), melanjutkan...", e)),
        }

        let _ = fs::remove_dir_all(TMP_DIR);
        let _ = fs::create_dir_all(TMP_DIR);

        logs.push("Mengekstrak AnyKernel3 package...".to_string());
        let mut extracted = false;
        for bin in &[
            "unzip",
            "/data/adb/magisk/busybox",
            "/data/adb/ksu/bin/busybox",
            "/data/adb/ap/bin/busybox",
        ] {
            let mut cmd = if *bin == "unzip" {
                Command::new("unzip")
            } else if Path::new(bin).exists() {
                let mut c = Command::new(bin);
                c.arg("unzip");
                c
            } else {
                continue;
            };

            if let Ok(st) = cmd.args(["-o", "-q", zip_path, "-d", TMP_DIR]).status() {
                if st.success() {
                    extracted = true;
                    break;
                }
            }
        }

        if !extracted {
            let _ = fs::remove_dir_all(TMP_DIR);
            return Err("Error: Gagal mengekstrak berkas ZIP AnyKernel3.".to_string());
        }

        let updater_bin = format!("{}/META-INF/com/google/android/update-binary", TMP_DIR);
        let anykernel_sh = format!("{}/anykernel.sh", TMP_DIR);

        let status = if Path::new(&updater_bin).is_file() {
            #[cfg(unix)]
            {
                use std::os::unix::fs::PermissionsExt;
                if let Ok(meta) = fs::metadata(&updater_bin) {
                    let mut perms = meta.permissions();
                    perms.set_mode(0o755);
                    let _ = fs::set_permissions(&updater_bin, perms);
                }
            }
            logs.push("Menjalankan update-binary...".to_string());
            Command::new("sh")
                .current_dir(TMP_DIR)
                .args([&updater_bin, "3", "1", zip_path])
                .status()
        } else if Path::new(&anykernel_sh).is_file() {
            #[cfg(unix)]
            {
                use std::os::unix::fs::PermissionsExt;
                if let Ok(meta) = fs::metadata(&anykernel_sh) {
                    let mut perms = meta.permissions();
                    perms.set_mode(0o755);
                    let _ = fs::set_permissions(&anykernel_sh, perms);
                }
            }
            logs.push("Menjalankan anykernel.sh...".to_string());
            Command::new("sh")
                .current_dir(TMP_DIR)
                .arg(&anykernel_sh)
                .status()
        } else {
            let _ = fs::remove_dir_all(TMP_DIR);
            return Err("Error: Berkas AnyKernel3 tidak valid (tidak ada update-binary atau anykernel.sh).".to_string());
        };

        let _ = fs::remove_dir_all(TMP_DIR);
        let _ = Command::new("sync").status();

        match status {
            Ok(st) if st.success() => {
                logs.push("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━".to_string());
                logs.push("[OK] Flashing Kernel Berhasil! Silakan reboot perangkat.".to_string());
                logs.push("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━".to_string());
                Ok(logs.join("\n"))
            }
            Ok(st) => Err(format!(
                "{}\n[ERROR] Flashing Kernel Gagal (Exit code: {}).",
                logs.join("\n"),
                st.code().unwrap_or(1)
            )),
            Err(e) => Err(format!("{}\n[ERROR] Flashing Kernel Gagal: {}", logs.join("\n"), e)),
        }
    }
}
