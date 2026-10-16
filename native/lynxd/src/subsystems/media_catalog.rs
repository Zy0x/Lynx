use std::collections::BTreeMap;
use std::fs;
use std::path::Path;
use std::process::Command;

#[derive(Clone)]
pub struct CodecDeclaration {
    pub name: String,
    pub mime: String,
    pub software: bool,
}

pub use crate::utils::json::quote;

fn attribute(tag: &str, name: &str) -> Option<String> {
    let key = format!("{}=", name);
    let rest = tag.split_once(&key)?.1.trim_start();
    let delimiter = rest.chars().next()?;
    if delimiter != '\'' && delimiter != '"' {
        return None;
    }
    Some(rest[1..].split(delimiter).next()?.to_string())
}

/// XML declarations are candidates, never proof of a working codec implementation.
pub fn declared_codecs() -> Vec<CodecDeclaration> {
    let mut codecs = BTreeMap::new();
    for dir in [
        "/vendor/etc",
        "/odm/etc",
        "/product/etc",
        "/system_ext/etc",
        "/system/etc",
        "/apex/com.android.media.swcodec/etc",
    ] {
        let entries = match fs::read_dir(dir) {
            Ok(entries) => entries,
            Err(_) => continue,
        };
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if !name.starts_with("media_codecs") || !name.ends_with(".xml") {
                continue;
            }
            let content = match fs::read_to_string(entry.path()) {
                Ok(content) => content,
                Err(_) => continue,
            };
            // Ignore examples and disabled entries inside XML comments.
            let mut clean = String::new();
            let mut rest = content.as_str();
            while let Some((before, after)) = rest.split_once("<!--") {
                clean.push_str(before);
                rest = after.split_once("-->").map(|(_, tail)| tail).unwrap_or("");
            }
            clean.push_str(rest);
            let mut encoder = false;
            for token in clean.split('<') {
                if token.starts_with("Encoders") {
                    encoder = true;
                }
                if token.starts_with("/Encoders") {
                    encoder = false;
                }
                if encoder || !token.starts_with("MediaCodec ") {
                    continue;
                }
                let tag = token.split('>').next().unwrap_or("");
                if attribute(tag, "enabled").as_deref() == Some("false") {
                    continue;
                }
                let name = match attribute(tag, "name") {
                    Some(name) => name,
                    None => continue,
                };
                if !name.starts_with("OMX.") && !name.starts_with("c2.") {
                    continue;
                }
                let mime = attribute(tag, "type").unwrap_or_default();
                if !mime.starts_with("video/") {
                    continue;
                }
                let software = name.starts_with("OMX.google.") || name.starts_with("c2.android.");
                codecs.entry(name.clone()).or_insert(CodecDeclaration {
                    name,
                    mime,
                    software,
                });
            }
        }
    }
    codecs.into_values().collect()
}

pub fn declarations_json(codecs: &[CodecDeclaration]) -> String {
    format!(
        "[{}]",
        codecs
            .iter()
            .map(|c| format!(
                "{{\"name\":{},\"mime\":{},\"software\":{}}}",
                quote(&c.name),
                quote(&c.mime),
                c.software
            ))
            .collect::<Vec<_>>()
            .join(",")
    )
}

/// Query only on explicit refresh; never add decoder discovery to a polling loop.
pub fn active_decoders_json() -> String {
    let output = match Command::new("dumpsys")
        .args(["-t", "3", "media.resource_manager"])
        .output()
    {
        Ok(output) if output.status.success() => output,
        _ => return "[]".into(),
    };
    let mut names = std::collections::BTreeSet::new();
    for line in String::from_utf8_lossy(&output.stdout).lines() {
        if let Some(name) = line.trim().strip_prefix("Name: ") {
            if name.starts_with("OMX.") || name.starts_with("c2.") {
                names.insert(name.to_string());
            }
        }
    }
    format!(
        "[{}]",
        names
            .iter()
            .map(|name| quote(name))
            .collect::<Vec<_>>()
            .join(",")
    )
}

pub fn player_pids() -> Vec<u32> {
    let mut pids = Vec::new();
    if let Ok(entries) = fs::read_dir("/proc") {
        for entry in entries.flatten() {
            let pid = match entry.file_name().to_string_lossy().parse::<u32>() {
                Ok(pid) => pid,
                Err(_) => continue,
            };
            if fs::read_to_string(entry.path().join("comm"))
                .map(|n| matches!(n.trim(), "mediaserver" | "mediaserver32"))
                .unwrap_or(false)
                && fs::read_link(entry.path().join("exe"))
                    .ok()
                    .and_then(|path| {
                        path.file_name()
                            .map(|n| n == "mediaserver" || n == "mediaserver32")
                    })
                    .unwrap_or(false)
            {
                pids.push(pid);
            }
        }
    }
    pids
}

pub fn property_supported() -> bool {
    // Inspect the implementation, rather than assuming every Android build supports the knob.
    [
        "/system/lib64/libsfplugin_ccodec.so",
        "/system/lib/libsfplugin_ccodec.so",
    ]
    .iter()
    .filter(|p| Path::new(p).is_file())
    .any(|p| {
        fs::read(p)
            .map(|bytes| {
                bytes
                    .windows(b"debug.stagefright.ccodec".len())
                    .any(|s| s == b"debug.stagefright.ccodec")
            })
            .unwrap_or(false)
    })
}

pub fn rank_properties_supported() -> bool {
    [
        "/system/lib64/libstagefright.so",
        "/system/lib/libstagefright.so",
    ]
    .iter()
    .any(|path| {
        fs::read(path)
            .map(|bytes| {
                [
                    b"debug.stagefright.omx_default_rank".as_slice(),
                    b"debug.stagefright.omx_default_rank.sw-other".as_slice(),
                ]
                .iter()
                .all(|key| bytes.windows(key.len()).any(|window| window == *key))
            })
            .unwrap_or(false)
    })
}

pub fn require_idle_audio() -> Result<(), String> {
    let output = Command::new("dumpsys")
        .args(["-t", "3", "audio"])
        .output()
        .map_err(|_| "audio_state_unavailable")?;
    if !output.status.success() {
        return Err("audio_state_unavailable".into());
    }
    validate_audio_state(&String::from_utf8_lossy(&output.stdout))
}

pub(super) fn validate_audio_state(raw: &str) -> Result<(), String> {
    let mut normal = false;
    for line in raw.lines() {
        let line = line.trim();
        if line.starts_with("- Actual mode =")
            || line.starts_with("- Requested mode =")
            || line.starts_with("mAudioModeOwner:")
            || line.starts_with("mMode=")
        {
            if line.contains("MODE_IN_CALL")
                || line.contains("MODE_IN_COMMUNICATION")
                || line.contains("MODE_RINGTONE")
                || line.contains("MODE_CALL_SCREENING")
            {
                return Err("call_active_or_unknown".into());
            }
            normal |= line.contains("MODE_NORMAL");
        }
    }
    if normal {
        Ok(())
    } else {
        Err("audio_state_unavailable".into())
    }
}
