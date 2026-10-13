use std::fs;
use std::path::Path;

use crate::state::ConfigStore;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CatalogProfile {
    pub id: String,
    pub name: String,
    pub base_profile: String,
    pub cpu_profile: Option<String>,
    pub gpu_profile: Option<String>,
}

fn profile_file() -> std::path::PathBuf {
    let config = ConfigStore::resolve_config_path();
    config.parent().unwrap_or(Path::new("/data/adb/modules/Lynx")).join("kernel_profiles.json")
}

fn json_string(object: &str, key: &str) -> Option<String> {
    let marker = format!("\"{}\"", key);
    let rest = object.get(object.find(&marker)? + marker.len()..)?.trim_start();
    let rest = rest.strip_prefix(':')?.trim_start();
    let rest = rest.strip_prefix('"')?;
    let end = rest.find('"')?;
    Some(rest[..end].to_string())
}

/// Reads only profile identity and base policy; arbitrary settings are never interpreted as commands.
pub fn read_catalog() -> Vec<CatalogProfile> {
    let Ok(raw) = fs::read_to_string(profile_file()) else { return Vec::new() };
    parse_catalog(&raw)
}

fn parse_catalog(raw: &str) -> Vec<CatalogProfile> {
    let Some(version_marker) = raw.find("\"schema_version\"") else { return Vec::new() };
    let Some(version_sep) = raw[version_marker..].find(':').map(|offset| version_marker + offset + 1) else { return Vec::new() };
    let version: String = raw[version_sep..].trim_start().chars().take_while(char::is_ascii_digit).collect();
    if version.parse::<u32>().ok() != Some(1) { return Vec::new(); }
    let Some(marker) = raw.find("\"profiles\"") else { return Vec::new() };
    let Some(start) = raw[marker..].find('[').map(|offset| marker + offset) else { return Vec::new() };
    let mut depth = 0i32;
    let mut in_string = false;
    let mut escaped = false;
    let mut object_start = None;
    let mut profiles = Vec::new();
    for (index, ch) in raw[start..].char_indices() {
        if in_string {
            if escaped { escaped = false; }
            else if ch == '\\' { escaped = true; }
            else if ch == '"' { in_string = false; }
            continue;
        }
        match ch {
            '"' => in_string = true,
            '{' => { if depth == 0 { object_start = Some(start + index); } depth += 1; }
            '}' => {
                depth -= 1;
                if depth == 0 {
                    if let Some(begin) = object_start.take() {
                        let object = &raw[begin..=start + index];
                        if let (Some(id), Some(name), Some(base_profile)) = (
                            json_string(object, "id"), json_string(object, "name"), json_string(object, "base_profile")
                        ) {
                            if valid_id(&id) && supported_base(&base_profile) {
                                let cpu_profile = json_string(object, "cpu").filter(|value| supported_cpu(value));
                                let gpu_profile = json_string(object, "gpu").filter(|value| supported_gpu(value));
                                profiles.push(CatalogProfile { id, name, base_profile, cpu_profile, gpu_profile });
                            }
                        }
                    }
                }
            }
            ']' if depth == 0 => break,
            _ => {}
        }
    }
    profiles
}

pub fn resolve_base(id: &str) -> Option<&'static str> {
    let profile = read_catalog().into_iter().find(|profile| profile.id == id)?;
    Some(match profile.base_profile.as_str() {
        "powersave" => "powersave",
        "balance" | "balanced" => "balanced",
        "performance" => "performance",
        "extreme" => "extreme",
        _ => return None,
    })
}

pub fn resolve_profile(id: &str) -> Option<CatalogProfile> {
    read_catalog().into_iter().find(|profile| profile.id == id)
}

fn valid_id(id: &str) -> bool {
    let mut chars = id.chars();
    matches!(chars.next(), Some('a'..='z')) &&
        chars.all(|ch| ch.is_ascii_lowercase() || ch.is_ascii_digit() || ch == '_' || ch == '-') && id.len() <= 40
}

fn supported_base(base: &str) -> bool {
    matches!(base, "powersave" | "balance" | "balanced" | "performance" | "extreme")
}

fn supported_cpu(preset: &str) -> bool {
    matches!(preset, "powersave" | "battery" | "balance" | "balanced" | "performance" | "extreme")
}

fn supported_gpu(preset: &str) -> bool {
    matches!(preset, "powersave" | "battery" | "balance" | "balanced" | "performance" | "esports" | "extreme")
}

#[cfg(test)]
mod tests {
    use super::{json_string, parse_catalog, supported_base, supported_cpu, supported_gpu, valid_id};

    #[test]
    fn accepts_only_safe_profile_ids_and_bases() {
        assert!(valid_id("daily_mix_1"));
        assert!(!valid_id("../../profile"));
        assert!(supported_base("balance"));
        assert!(!supported_base("/system/bin/sh"));
        assert!(supported_cpu("performance"));
        assert!(!supported_cpu("/system/bin/sh"));
        assert!(supported_gpu("esports"));
        assert!(!supported_gpu("/system/bin/sh"));
    }

    #[test]
    fn reads_profile_string_fields_without_evaluating_payload() {
        let object = r#"{"id":"my_profile","name":"My profile","base_profile":"balance","settings":{"x":"y"}}"#;
        assert_eq!(json_string(object, "id").as_deref(), Some("my_profile"));
        assert_eq!(json_string(object, "base_profile").as_deref(), Some("balance"));
    }

    #[test]
    fn loads_catalog_entries_and_ignores_unsafe_base_values() {
        let raw = r#"{"schema_version":1,"profiles":[{"id":"travel","name":"Travel","base_profile":"powersave","domain_profiles":{"cpu":"performance","gpu":"battery"},"settings":{"memory.swappiness":"60"}},{"id":"bad","name":"Bad","base_profile":"../../x"}]}"#;
        let profiles = parse_catalog(raw);
        assert_eq!(profiles.len(), 1);
        assert_eq!(profiles[0].id, "travel");
        assert_eq!(profiles[0].base_profile, "powersave");
        assert_eq!(profiles[0].cpu_profile.as_deref(), Some("performance"));
        assert_eq!(profiles[0].gpu_profile.as_deref(), Some("battery"));
    }
}
