pub mod logger;
pub mod json;

pub use logger::Logger;

use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

const DEFAULT_MODULE_PROP_PATH: &str = "/data/adb/modules/Lynx/module.prop";
const DEFAULT_MODULE_PROP_LOCK: &str = "/dev/lynx_modprop.lock";

/// Atomically updates the `description=` field in `/data/adb/modules/Lynx/module.prop`.
pub fn update_module_prop_description(desc: &str) {
    let _ = update_module_prop_description_sync(desc);
}

/// Synchronously updates `module.prop` under a cross-process lock, returning detailed status.
pub fn update_module_prop_description_sync(desc: &str) -> Result<(), String> {
    let prop_path = Path::new(DEFAULT_MODULE_PROP_PATH);
    let default_lock = Path::new(DEFAULT_MODULE_PROP_LOCK);
    let lock_path = if default_lock.parent().map(|p| p.exists()).unwrap_or(false) {
        default_lock.to_path_buf()
    } else {
        prop_path.with_extension("prop.lock")
    };
    update_module_prop_description_with_lock(prop_path, &lock_path, desc)
}

/// Serializes and atomically writes `description=<desc>` into `prop_path` using `lock_path`.
pub fn update_module_prop_description_with_lock(
    prop_path: &Path,
    lock_path: &Path,
    desc: &str,
) -> Result<(), String> {
    if !prop_path.exists() {
        return Ok(());
    }

    let _lock = crate::state::config_lock::ConfigLock::acquire(lock_path)?;
    let content = fs::read_to_string(prop_path)
        .map_err(|e| format!("failed to read {}: {}", prop_path.display(), e))?;

    let sanitized_desc = desc.replace(['\r', '\n'], " ");
    let rendered = build_updated_module_prop(&content, &sanitized_desc)?;

    let parent = prop_path
        .parent()
        .ok_or_else(|| format!("invalid module.prop path: {}", prop_path.display()))?;
    let nonce = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_nanos();
    let tmp_path: PathBuf = parent.join(format!(
        ".module.prop.tmp.{}.{}",
        std::process::id(),
        nonce
    ));

    let write_res = (|| -> std::io::Result<()> {
        let mut opts = fs::OpenOptions::new();
        opts.write(true).create(true).truncate(true);
        #[cfg(unix)]
        {
            use std::os::unix::fs::OpenOptionsExt;
            opts.mode(0o644);
        }
        let mut file = opts.open(&tmp_path)?;
        file.write_all(rendered.as_bytes())?;
        file.sync_all()?;
        Ok(())
    })();

    if let Err(err) = write_res {
        let _ = fs::remove_file(&tmp_path);
        return Err(format!("failed to write staged module.prop: {}", err));
    }

    if let Err(err) = fs::rename(&tmp_path, prop_path) {
        let _ = fs::remove_file(&tmp_path);
        return Err(format!("failed to atomically replace module.prop: {}", err));
    }

    Ok(())
}

fn build_updated_module_prop(content: &str, desc: &str) -> Result<String, String> {
    let trimmed = content.trim();
    if trimmed.is_empty() {
        return Err("refusing to update empty or truncated module.prop".to_string());
    }

    let mut has_id = false;
    let mut has_version = false;
    let mut replaced_desc = false;
    let mut lines: Vec<String> = Vec::new();
    let target_line = format!("description={}", desc);

    for raw_line in content.lines() {
        let line = raw_line.trim_end_matches('\r');
        if line.is_empty() {
            continue;
        }
        if line.starts_with("id=") {
            has_id = true;
        } else if line.starts_with("version=") {
            has_version = true;
        }

        if line.starts_with("description=") {
            lines.push(target_line.clone());
            replaced_desc = true;
        } else {
            lines.push(line.to_string());
        }
    }

    if !has_id || !has_version {
        return Err("refusing to update corrupted module.prop missing id or version".to_string());
    }

    if !replaced_desc {
        lines.push(target_line);
    }

    Ok(format!("{}\n", lines.join("\n")))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;

    #[test]
    fn module_prop_update_preserves_fields_and_replaces_description() {
        let dir = std::env::temp_dir().join(format!(
            "lynx_modprop_test_basic_{}",
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap_or_default()
                .as_nanos()
        ));
        fs::create_dir_all(&dir).unwrap();
        let prop = dir.join("module.prop");
        let lock = dir.join("module.prop.lock");
        let initial = "id=Lynx\nname=Lynx - Deity\nversion=7.0.0-RC415\nversionCode=1107\nauthor=noir\ndescription=Old Desc\n";
        fs::write(&prop, initial).unwrap();

        update_module_prop_description_with_lock(&prop, &lock, "[ Balance ] Active").unwrap();
        let out = fs::read_to_string(&prop).unwrap();
        assert!(out.contains("id=Lynx\n"));
        assert!(out.contains("version=7.0.0-RC415\n"));
        assert!(out.contains("description=[ Balance ] Active\n"));
        assert!(!out.contains("Old Desc"));

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn module_prop_update_rejects_empty_or_truncated_input() {
        let dir = std::env::temp_dir().join(format!(
            "lynx_modprop_test_empty_{}",
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap_or_default()
                .as_nanos()
        ));
        fs::create_dir_all(&dir).unwrap();
        let prop = dir.join("module.prop");
        let lock = dir.join("module.prop.lock");
        fs::write(&prop, "\n").unwrap();

        let err = update_module_prop_description_with_lock(&prop, &lock, "New").unwrap_err();
        assert!(err.contains("empty"));
        assert_eq!(fs::read_to_string(&prop).unwrap(), "\n");

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn module_prop_update_survives_concurrent_writers_without_truncation() {
        let dir = std::env::temp_dir().join(format!(
            "lynx_modprop_test_race_{}",
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap_or_default()
                .as_nanos()
        ));
        fs::create_dir_all(&dir).unwrap();
        let prop = Arc::new(dir.join("module.prop"));
        let lock = Arc::new(dir.join("module.prop.lock"));
        let initial = "id=Lynx\nname=Lynx - Deity\nversion=7.0.0-RC415\nversionCode=1107\nauthor=noir\ndescription=Initial\n";
        fs::write(prop.as_path(), initial).unwrap();

        let mut handles = Vec::new();
        for t in 0..8 {
            let p = Arc::clone(&prop);
            let l = Arc::clone(&lock);
            handles.push(std::thread::spawn(move || {
                for i in 0..25 {
                    let desc = format!("[ Thread {} Iter {} ] Active", t, i);
                    update_module_prop_description_with_lock(&p, &l, &desc).unwrap();
                    let snapshot = fs::read_to_string(p.as_path()).unwrap();
                    assert!(snapshot.contains("id=Lynx"));
                    assert!(snapshot.contains("version=7.0.0-RC415"));
                    assert!(snapshot.contains("description="));
                }
            }));
        }

        for h in handles {
            h.join().unwrap();
        }

        let _ = fs::remove_dir_all(&dir);
    }
}

