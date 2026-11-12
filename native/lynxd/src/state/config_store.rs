use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};

#[cfg(unix)]
use std::os::unix::fs::PermissionsExt;

#[derive(Debug, Clone)]
pub struct ChargingConfigState {
    pub active_profile: String,
    pub charging_mode: String,
    pub bypass_enabled: bool,
    pub extreme_charging_enabled: bool,
    pub temp_cutoff_c: i32,
    pub limit_current_ma: i32,
    pub is_unconstrained_max_hw: bool,
    pub custom_limit_current_ma: i32,
    pub auto_cut_enabled: bool,
    pub max_battery_percent: i32,
    pub high_current_target_percent: i32,
    pub emergency_temp_guard_enabled: bool,
    pub thermal_lockout_bypass_enabled: bool,
    pub smart_tapering_enabled: bool,
    pub night_sleep_guard_enabled: bool,
    pub dual_cell_multiplier: i32,
}

impl Default for ChargingConfigState {
    fn default() -> Self {
        Self {
            active_profile: "balance".to_string(),
            charging_mode: "standard".to_string(),
            bypass_enabled: false,
            extreme_charging_enabled: false,
            temp_cutoff_c: 45,
            limit_current_ma: 6000,
            is_unconstrained_max_hw: false,
            custom_limit_current_ma: 6000,
            auto_cut_enabled: false,
            max_battery_percent: 80,
            high_current_target_percent: 90,
            emergency_temp_guard_enabled: true,
            thermal_lockout_bypass_enabled: false,
            smart_tapering_enabled: true,
            night_sleep_guard_enabled: false,
            dual_cell_multiplier: 1,
        }
    }
}

pub struct ConfigStore;

impl ConfigStore {
    pub fn resolve_config_path() -> PathBuf {
        let candidates = [
            "/data/adb/modules/Lynx/config.json",
            "/data/adb/lynx/config.json",
            "/data/user/0/com.noir.lynx/files/config.json",
            "/data/user/0/com.noir.lynx.debug/files/config.json",
        ];
        for c in candidates {
            let p = Path::new(c);
            if p.exists() {
                return p.to_path_buf();
            }
        }
        PathBuf::from("/data/adb/modules/Lynx/config.json")
    }

    fn lock_path() -> PathBuf {
        if Path::new("/dev").exists() {
            PathBuf::from("/dev/lynx_config.lock")
        } else {
            PathBuf::from("/data/local/tmp/lynx_config.lock")
        }
    }

pub const DEFAULT_CONFIG_TEMPLATE: &str = r#"{
  "version": 1,
  "module_version": "3.0.0",
  "release_type": "beta",
  "active_profile": "balance",
  "setup_pending": false,
  "media": {
    "decoder_mode": "system",
    "decoder_apply_on_boot": false
  },
  "target_soc": "generic",
  "is_spoofed": false,
  "engine": {
    "native_enabled": true,
    "legacy_fallback": true,
    "failure_threshold": 3,
    "consecutive_failures": 0,
    "last_failure_reason": "",
    "failure_history": []
  },
  "hardware": {
    "soc_type": "generic",
    "soc_name": "Generic Linux",
    "is_overclocked": false
  },
  "overclock": {
    "enabled": false,
    "cpu_floor_ratio": 85,
    "zram_size_mb": 2048,
    "swappiness": 50,
    "custom_scaling_min": 0,
    "custom_scaling_max": 0
  },
  "memory": {
    "zram_enabled": true,
    "zram_size_mb": 2048,
    "comp_algorithm": "auto",
    "swappiness": 50,
    "mglru_enabled": true
  },
  "charging": {
    "charging_mode": "standard",
    "bypass_enabled": false,
    "extreme_charging_enabled": false,
    "temp_cutoff_c": 45,
    "limit_current_ma": 2000,
    "max_battery_percent": 80,
    "auto_cut_enabled": false
  },
  "uclamp": {
    "game_min_ratio": 70,
    "game_max_ratio": 100,
    "balance_min_ratio": 20,
    "schedutil_up_rate_us": 500,
    "schedutil_down_rate_us": 20000
  },
  "display_touch": {
    "touchboost": true,
    "refresh_rate_lock": 120,
    "surfaceflinger_offset_ns": 2000000,
    "skia_vulkan_enabled": false,
    "nvt_micro_gain": 4,
    "nvt_edge_guard_px": 48,
    "nvt_spi_speed_hz": 15000000,
    "nvt_game_mode": true,
    "nvt_hop_fix": true,
    "nvt_jitter_level": 0,
    "nvt_smooth_level": 0,
    "ktch_enabled": true,
    "ktch_duration_ms": 280,
    "ktch_eas_boost": 80,
    "back_gesture_overlay": "narrow",
    "back_gesture_scale_left": 0.18,
    "back_gesture_scale_right": 0.18,
    "back_gesture_ml_enabled": true,
    "back_gesture_ml_threshold": 0.96,
    "view_touch_slop": 4,
    "input_resampling": false,
    "debug_touch_freq_boost": true,
    "touch_irq_big_cores": true
  },
  "network": {
    "wifi_ping_stabilizer": true,
    "tcp_congestion": "bbr",
    "fq_codel_enabled": true,
    "wifi_power_save_in_game": false
  },
  "adb_keepalive": {
    "enabled": true,
    "port": 5555
  },
  "audio": {
    "low_latency_mmap": true,
    "fast_track_enabled": true,
    "bt_crackle_guard": true
  },
  "oem_neutralizer": {
    "joyose_neutralize": true,
    "gos_neutralize": true,
    "freeze_method": "sigstop"
  },
  "thermal": {
    "full_bypass": false,
    "custom_temp_limit_c": 50,
    "trip_point_override_c": 150
  },
  "game_manager": {
    "auto_detect_games": true,
    "custom_apps": []
  },
  "features": {
    "touchboost": true,
    "network_boost": true,
    "audio_mmap": true,
    "oem_neutralizer": true,
    "unity_trick": false,
    "sched_features": true
  },
  "diagnostics": {
    "log_level": "verbose",
    "last_bugreport": ""
  }
}
"#;

    pub fn read_raw() -> Result<String, String> {
        let path = Self::resolve_config_path();
        let content = match fs::read_to_string(&path) {
            Ok(c) => c,
            Err(_) => {
                let fallback = Self::DEFAULT_CONFIG_TEMPLATE.to_string();
                if let Some(parent) = path.parent() {
                    let _ = fs::create_dir_all(parent);
                }
                let _ = fs::write(&path, &fallback);
                return Ok(fallback);
            }
        };

        if content.contains('\0') || content.trim().is_empty() || !content.trim().starts_with('{') {
            let fallback = Self::DEFAULT_CONFIG_TEMPLATE.to_string();
            let _ = fs::write(&path, &fallback);
            Ok(fallback)
        } else {
            Ok(content)
        }
    }

    /// Fast O(N) single-pass lookup of a top-level or `section.field` key in `config.json`.
    pub fn get(key: &str) -> Option<String> {
        let content = Self::read_raw().ok()?;
        Self::get_from_str(&content, key)
    }

    pub fn get_from_str(content: &str, key: &str) -> Option<String> {
        if let Some((sec, fld)) = key.split_once('.') {
            let sec_pat = format!("\"{}\"", sec);
            let fld_pat = format!("\"{}\"", fld);
            let mut in_sec = false;
            let mut brace_depth = 0i32;

            for line in content.lines() {
                let trimmed = line.trim();
                if !in_sec {
                    if let Some(pos) = trimmed.find(&sec_pat) {
                        let after = trimmed[pos + sec_pat.len()..].trim_start();
                        if after.starts_with(':') && after.contains('{') {
                            if after.contains('}') {
                                // Inline object on same line: e.g. "charging": { "charging_mode": "standard" }
                                if let Some(fpos) = after.find(&fld_pat) {
                                    let after_fld = after[fpos + fld_pat.len()..].trim_start();
                                    if after_fld.starts_with(':') {
                                        let val_raw = after_fld[1..].trim_start();
                                        let end = val_raw.find(|c| c == ',' || c == '}').unwrap_or(val_raw.len());
                                        let val = val_raw[..end].trim().trim_matches('"');
                                        return Some(val.to_string());
                                    }
                                }
                                continue;
                            }
                            in_sec = true;
                            brace_depth = 1;
                            continue;
                        }
                    }
                } else {
                    if let Some(val) = Self::extract_field_value(trimmed, &fld_pat) {
                        return Some(val);
                    }
                    for ch in trimmed.chars() {
                        if ch == '{' {
                            brace_depth += 1;
                        } else if ch == '}' {
                            brace_depth -= 1;
                            if brace_depth <= 0 {
                                in_sec = false;
                                break;
                            }
                        }
                    }
                }
            }
            None
        } else {
            let key_pat = format!("\"{}\"", key);
            for line in content.lines() {
                let trimmed = line.trim();
                if let Some(val) = Self::extract_field_value(trimmed, &key_pat) {
                    if val != "{" && !val.starts_with('{') {
                        return Some(val);
                    }
                }
            }
            None
        }
    }

    fn extract_field_value(trimmed_line: &str, quoted_key: &str) -> Option<String> {
        if !trimmed_line.starts_with(quoted_key) {
            return None;
        }
        let after_key = trimmed_line[quoted_key.len()..].trim_start();
        if !after_key.starts_with(':') {
            return None;
        }
        let mut val_part = after_key[1..].trim();
        if val_part.ends_with(',') {
            val_part = val_part[..val_part.len() - 1].trim();
        }
        if val_part.starts_with('"') && val_part.ends_with('"') && val_part.len() >= 2 {
            val_part = &val_part[1..val_part.len() - 1];
        }
        Some(val_part.to_string())
    }

    fn format_json_value(raw_val: &str, type_hint: Option<&str>) -> String {
        match type_hint {
            Some("str") | Some("string") | Some("true") => format!("\"{}\"", raw_val.trim_matches('"')),
            Some("bool") | Some("val") | Some("int") | Some("false") => raw_val.to_string(),
            _ => {
                // Auto-infer JSON value formatting
                if raw_val == "true" || raw_val == "false" || raw_val == "null" {
                    raw_val.to_string()
                } else if raw_val.parse::<i64>().is_ok() || raw_val.parse::<f64>().is_ok() {
                    raw_val.to_string()
                } else if raw_val.starts_with('"') && raw_val.ends_with('"') {
                    raw_val.to_string()
                } else {
                    format!("\"{}\"", raw_val)
                }
            }
        }
    }

    /// Convenience helper to atomically update a single key in `config.json`.
    pub fn set(key: &str, val: &str, type_hint: &str) -> Result<(), String> {
        Self::set_batch(&[(key.to_string(), val.to_string(), Some(type_hint.to_string()))])
    }

    /// Atomically updates one or multiple keys in `config.json` in a single in-memory pass.
    /// Zero temporary file collisions (uses PID suffix + `/dev/lynx_config.lock`).
    pub fn set_batch(updates: &[(String, String, Option<String>)]) -> Result<(), String> {
        if updates.is_empty() {
            return Ok(());
        }

        let _lock = super::config_lock::ConfigLock::acquire(&Self::lock_path())?;
        Self::set_batch_locked(updates)
    }

    fn set_batch_locked(updates: &[(String, String, Option<String>)]) -> Result<(), String> {
        let path = Self::resolve_config_path();
        let mut content = match fs::read_to_string(&path) {
            Ok(c) => c,
            Err(_) => Self::DEFAULT_CONFIG_TEMPLATE.to_string(),
        };

        if content.contains('\0') || content.trim().is_empty() || !content.trim().starts_with('{') {
            content = Self::DEFAULT_CONFIG_TEMPLATE.to_string();
        }

        for (key, raw_val, hint) in updates {
            let formatted_val = Self::format_json_value(raw_val, hint.as_deref());
            content = Self::apply_single_mutation(&content, key, &formatted_val);
        }

        let pid = std::process::id();
        let tmp_path = path.with_extension(format!("json.tmp.{}", pid));

        {
            let mut file = fs::OpenOptions::new()
                .write(true)
                .create(true)
                .truncate(true)
                .open(&tmp_path)
                .map_err(|e| format!("Failed to create temp config {}: {}", tmp_path.display(), e))?;
            file.write_all(content.as_bytes())
                .map_err(|e| format!("Failed to write temp config: {}", e))?;
            let _ = file.flush();
            let _ = file.sync_all();
        }

        #[cfg(unix)]
        {
            let _ = fs::set_permissions(&tmp_path, fs::Permissions::from_mode(0o644));
        }

        fs::rename(&tmp_path, &path).map_err(|e| {
            let _ = fs::remove_file(&tmp_path);
            format!("Failed to rename temp config to {}: {}", path.display(), e)
        })?;

        // 2-Way Profile Sync: update /data/adb/lynx/active_profile, /data/local/tmp/lynxd_profile, and system property lynx.mode
        for (k, v, _) in updates {
            if k == "active_profile" {
                Self::sync_profile_markers(v, true);
            } else if k == "baseline_profile" {
                Self::sync_baseline_marker(v);
            }
        }

        Ok(())
    }

    /// Persists an explicit manual baseline profile selection without modifying runtime overrides.
    pub fn sync_baseline_marker(profile: &str) {
        let clean_p = profile.trim().trim_matches('"');
        if clean_p.is_empty() || clean_p.eq_ignore_ascii_case("auto") || clean_p.eq_ignore_ascii_case("dormant") {
            return;
        }
        Self::write_marker_if_changed(Path::new("/data/adb/lynx/baseline_profile"), clean_p);
    }

    /// Synchronizes active profile state files only when their content differs from `profile`,
    /// eliminating redundant flash/eMMC writes while keeping CLI, daemon, and HUD aligned.
    pub fn sync_profile_markers(profile: &str, update_prop: bool) {
        let clean_p = profile.trim().trim_matches('"');
        if clean_p.is_empty() {
            return;
        }
        Self::write_marker_if_changed(Path::new("/data/adb/lynx/active_profile"), clean_p);
        Self::write_marker_if_changed(Path::new("/data/local/tmp/lynxd_profile"), clean_p);
        Self::update_status_snapshot_profile(clean_p);
        if update_prop {
            #[cfg(target_os = "android")]
            {
                let _ = std::process::Command::new("/system/bin/setprop")
                    .args(&["lynx.mode", clean_p])
                    .status();
            }
        }
    }

    fn update_status_snapshot_profile(profile: &str) {
        for status_path in &[
            Path::new("/dev/lynxd_status.json"),
            Path::new("/data/local/tmp/lynxd_status.json"),
        ] {
            if let Ok(content) = fs::read_to_string(status_path) {
                if let Some(pos) = content.find("\"profile\":\"") {
                    let prefix_end = pos + 11;
                    if let Some(rel_end) = content[prefix_end..].find('"') {
                        let old_prof = &content[prefix_end..prefix_end + rel_end];
                        if old_prof != profile {
                            let mut updated = String::with_capacity(content.len() + 16);
                            updated.push_str(&content[..prefix_end]);
                            updated.push_str(profile);
                            updated.push_str(&content[prefix_end + rel_end..]);
                            let _ = fs::write(status_path, updated);
                        }
                    }
                }
            }
        }
    }

    fn write_marker_if_changed(path: &Path, target: &str) {
        if let Ok(existing) = fs::read_to_string(path) {
            if existing.trim() == target {
                return;
            }
        }
        if let Some(parent) = path.parent() {
            let _ = fs::create_dir_all(parent);
        }
        if fs::write(path, target).is_ok() {
            #[cfg(unix)]
            {
                let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o666));
            }
        }
    }

    #[inline]
    fn is_orphaned_root_line(trimmed_line: &str) -> bool {
        const LEAKED_FIELD_PREFIXES: &[&str] = &[
            "\"bypass_enabled\":",
            "\"charging_mode\":",
            "\"extreme_charging_enabled\":",
            "\"limit_current_ma\":",
            "\"is_unconstrained_max_hw\":",
            "\"high_current_target_percent\":",
            "\"temp_cutoff_c\":",
            "\"thermal_lockout_bypass_enabled\":",
            "\"emergency_temp_guard_enabled\":",
            "\"night_sleep_guard_enabled\":",
            "\"smart_tapering_enabled\":",
            "\"auto_cut_enabled\":",
            "\"max_battery_percent\":",
            "\"custom_limit_current_ma\":",
            "\"refresh_rate_lock\":",
            "\"touchboost\":",
        ];
        LEAKED_FIELD_PREFIXES.iter().any(|prefix| trimmed_line.starts_with(prefix))
    }

    fn apply_single_mutation(content: &str, key: &str, formatted_val: &str) -> String {
        let mut out = String::with_capacity(content.len() + 64);

        if let Some((sec, fld)) = key.split_once('.') {
            let sec_pat = format!("\"{}\"", sec);
            let fld_pat = format!("\"{}\"", fld);
            let mut in_sec = false;
            let mut sec_handled = false;
            let mut sec_lines: Vec<String> = Vec::new();
            let mut found_in_sec = false;

            let lines: Vec<&str> = content.lines().collect();
            for (idx, line) in lines.iter().enumerate() {
                let trimmed = line.trim();
                if !in_sec && !sec_handled {
                    // Automatically filter out orphaned root lines that previously leaked outside sections
                    if Self::is_orphaned_root_line(trimmed) {
                        continue;
                    }

                    if let Some(pos) = trimmed.find(&sec_pat) {
                        let after = trimmed[pos + sec_pat.len()..].trim_start();
                        if after.starts_with(':') && after.contains('{') {
                            if after.contains('}') {
                                // Inline single-line object: e.g. "charging": { "charging_mode": "standard", "bypass_enabled": false },
                                let brace_open = line.find('{').unwrap();
                                let brace_close = line.rfind('}').unwrap();
                                let indent_len = line.len() - line.trim_start().len();
                                let indent = &line[..indent_len];
                                let inner = line[brace_open + 1..brace_close].trim();
                                let comma_suffix = if line[brace_close + 1..].trim().ends_with(',') { "," } else { "" };

                                let mut fields: Vec<(String, String)> = Vec::new();
                                let mut updated = false;

                                for part in inner.split(',') {
                                    let part_trimmed = part.trim();
                                    if part_trimmed.is_empty() {
                                        continue;
                                    }
                                    if let Some((k_raw, v_raw)) = part_trimmed.split_once(':') {
                                        let k = k_raw.trim().trim_matches('"');
                                        let v = v_raw.trim();
                                        if k == fld {
                                            fields.push((k.to_string(), formatted_val.to_string()));
                                            updated = true;
                                        } else {
                                            fields.push((k.to_string(), v.to_string()));
                                        }
                                    }
                                }
                                if !updated {
                                    fields.push((fld.to_string(), formatted_val.to_string()));
                                }

                                out.push_str(&format!("{}\"{}\": {{\n", indent, sec));
                                for (i, (k, v)) in fields.iter().enumerate() {
                                    let c = if i < fields.len() - 1 { "," } else { "" };
                                    out.push_str(&format!("{}  \"{}\": {}{}\n", indent, k, v, c));
                                }
                                out.push_str(&format!("{}}}{}\n", indent, comma_suffix));
                                sec_handled = true;
                                continue;
                            }

                            in_sec = true;
                            out.push_str(line);
                            out.push('\n');
                            continue;
                        }
                    }
                    if idx == lines.len() - 1 && trimmed == "}" {
                        let trimmed_out = out.trim_end();
                        if !trimmed_out.ends_with(',') && !trimmed_out.ends_with('{') {
                            out = format!("{},\n", trimmed_out);
                        }
                        out.push_str(&format!(
                            "  \"{}\": {{\n    \"{}\": {}\n  }}\n",
                            sec, fld, formatted_val
                        ));
                        out.push_str(line);
                        out.push('\n');
                        sec_handled = true;
                        continue;
                    }
                    out.push_str(line);
                    out.push('\n');
                } else if in_sec {
                    if trimmed.starts_with('}') {
                        in_sec = false;
                        sec_handled = true;
                        if !found_in_sec {
                            // Insert new field at top of section with comma if section has existing fields
                            let has_other_fields = sec_lines.iter().any(|l| l.contains(':'));
                            let comma = if has_other_fields { "," } else { "" };
                            out.push_str(&format!("    \"{}\": {}{}\n", fld, formatted_val, comma));
                        }
                        for sl in &sec_lines {
                            out.push_str(sl);
                            out.push('\n');
                        }
                        out.push_str(line);
                        out.push('\n');
                    } else if trimmed.starts_with(&fld_pat)
                        && trimmed[fld_pat.len()..].trim_start().starts_with(':')
                    {
                        found_in_sec = true;
                        let indent_len = line.len() - line.trim_start().len();
                        let indent = &line[..indent_len];
                        let comma = if trimmed.ends_with(',') { "," } else { "" };
                        sec_lines.push(format!("{}\"{}\": {}{}", indent, fld, formatted_val, comma));
                    } else {
                        sec_lines.push(line.to_string());
                    }
                } else {
                    // If section was already handled and not in section, clean up any leaked orphaned root fields
                    if Self::is_orphaned_root_line(trimmed) {
                        continue;
                    }
                    out.push_str(line);
                    out.push('\n');
                }
            }
            out
        } else {
            let key_pat = format!("\"{}\"", key);
            let mut found = false;
            let lines: Vec<&str> = content.lines().collect();
            for (idx, line) in lines.iter().enumerate() {
                let trimmed = line.trim();
                if !found
                    && trimmed.starts_with(&key_pat)
                    && trimmed[key_pat.len()..].trim_start().starts_with(':')
                    && !trimmed.ends_with('{')
                {
                    found = true;
                    let indent_len = line.len() - line.trim_start().len();
                    let indent = &line[..indent_len];
                    let comma = if trimmed.ends_with(',') { "," } else { "" };
                    out.push_str(&format!("{}\"{}\": {}{}\n", indent, key, formatted_val, comma));
                } else if idx == lines.len() - 1 && trimmed == "}" && !found {
                    let trimmed_out = out.trim_end();
                    if !trimmed_out.ends_with(',') && !trimmed_out.ends_with('{') {
                        out = format!("{},\n", trimmed_out);
                    }
                    out.push_str(&format!("  \"{}\": {}\n", key, formatted_val));
                    out.push_str(line);
                    out.push('\n');
                } else {
                    out.push_str(line);
                    out.push('\n');
                }
            }
            out
        }
    }

    /// Loads the complete charging configuration from `config.json` in a single fast read.
    pub fn load_charging_config() -> ChargingConfigState {
        let mut cfg = ChargingConfigState::default();
        let content = match Self::read_raw() {
            Ok(c) => c,
            Err(_) => return cfg,
        };

        if let Some(p) = Self::get_from_str(&content, "active_profile") {
            cfg.active_profile = p;
        }
        if let Some(m) = Self::get_from_str(&content, "charging.charging_mode") {
            cfg.charging_mode = m;
        }
        if let Some(v) = Self::get_from_str(&content, "charging.bypass_enabled") {
            cfg.bypass_enabled = v == "true";
        }
        if let Some(v) = Self::get_from_str(&content, "charging.extreme_charging_enabled") {
            cfg.extreme_charging_enabled = v == "true";
        }
        if let Some(v) = Self::get_from_str(&content, "charging.temp_cutoff_c") {
            if let Ok(n) = v.parse::<i32>() {
                cfg.temp_cutoff_c = n;
            }
        }
        if let Some(v) = Self::get_from_str(&content, "charging.limit_current_ma") {
            if let Ok(n) = v.parse::<i32>() {
                cfg.limit_current_ma = n;
            }
        }
        if let Some(v) = Self::get_from_str(&content, "charging.is_unconstrained_max_hw") {
            cfg.is_unconstrained_max_hw = v == "true";
        }
        if let Some(v) = Self::get_from_str(&content, "charging.custom_limit_current_ma") {
            if let Ok(n) = v.parse::<i32>() {
                cfg.custom_limit_current_ma = n;
            }
        }
        if let Some(v) = Self::get_from_str(&content, "charging.auto_cut_enabled") {
            cfg.auto_cut_enabled = v == "true";
        }
        if let Some(v) = Self::get_from_str(&content, "charging.max_battery_percent") {
            if let Ok(n) = v.parse::<i32>() {
                cfg.max_battery_percent = n;
            }
        }
        if let Some(v) = Self::get_from_str(&content, "charging.high_current_target_percent") {
            if let Ok(n) = v.parse::<i32>() {
                cfg.high_current_target_percent = n;
            }
        }
        if let Some(v) = Self::get_from_str(&content, "charging.emergency_temp_guard_enabled") {
            cfg.emergency_temp_guard_enabled = v != "false";
        }
        if let Some(v) = Self::get_from_str(&content, "charging.thermal_lockout_bypass_enabled") {
            cfg.thermal_lockout_bypass_enabled = v != "false";
        }
        if let Some(v) = Self::get_from_str(&content, "charging.smart_tapering_enabled") {
            cfg.smart_tapering_enabled = v != "false";
        }
        if let Some(v) = Self::get_from_str(&content, "charging.night_sleep_guard_enabled") {
            cfg.night_sleep_guard_enabled = v == "true";
        }
        if let Some(v) = Self::get_from_str(&content, "charging.dual_cell_multiplier") {
            if let Ok(n) = v.parse::<i32>() {
                cfg.dual_cell_multiplier = n.max(1);
            }
        }

        cfg
    }
}
