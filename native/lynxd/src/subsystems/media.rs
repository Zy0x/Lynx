use std::fs;
use std::path::Path;
use std::process::Command;
use std::thread::sleep;
use std::time::Duration;

use super::media_catalog::{self as catalog, quote};
use super::media_transaction::{MediaOperationGuard, MediaTransaction};
use crate::apply::ModeLock;
use crate::state::{ConfigStore, ProfileStateManager};

pub(super) const DIR: &str = "/dev/lynx_media";
pub(super) const KEYS: [&str; 3] = [
    "debug.stagefright.ccodec",
    "debug.stagefright.omx_default_rank",
    "debug.stagefright.omx_default_rank.sw-other",
];
pub const MODES: [&str; 4] = ["system", "omx_compat", "prefer_hardware", "prefer_software"];

pub struct MediaController;

pub(super) fn getprop(key: &str) -> Result<String, String> {
    Command::new("getprop")
        .arg(key)
        .output()
        .ok()
        .filter(|o| o.status.success())
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .ok_or_else(|| "property_read_failed".into())
}

pub(super) fn setprop(key: &str, value: &str) -> Result<(), String> {
    if Command::new("setprop")
        .args([key, value])
        .output()
        .map(|o| o.status.success())
        .unwrap_or(false)
        && getprop(key).as_deref() == Ok(value)
    {
        Ok(())
    } else {
        Err("property_write_failed".into())
    }
}

impl MediaController {
    fn targets(mode: &str, baseline: &[String]) -> Vec<String> {
        let mut target = baseline.to_vec();
        match mode {
            "omx_compat" => target[0] = "0".into(),
            "prefer_hardware" => {
                target[0] = "4".into();
                target[1] = "0".into();
                target[2] = "528".into();
            }
            "prefer_software" => {
                target[0] = "4".into();
                target[1] = "256".into();
                target[2] = "0".into();
            }
            _ => {}
        }
        target
    }

    fn boot_id() -> Result<String, String> {
        fs::read_to_string("/proc/sys/kernel/random/boot_id")
            .map(|v| v.trim().to_string())
            .map_err(|_| "boot_identity_unavailable".into())
    }

    fn baseline(create: bool) -> Result<Vec<String>, String> {
        let boot = Self::boot_id()?;
        let path = Path::new(DIR).join("original");
        if let Ok(raw) = fs::read_to_string(&path) {
            let mut lines = raw.lines();
            if lines.next() == Some(boot.as_str()) {
                let values: Vec<_> = lines.map(str::to_string).collect();
                if values.len() == KEYS.len() {
                    return Ok(values);
                }
                return Err("backup_invalid".into());
            }
        }
        if !create {
            return Err("backup_unavailable".into());
        }
        let values: Vec<_> = KEYS
            .iter()
            .map(|key| getprop(key))
            .collect::<Result<_, _>>()?;
        fs::create_dir_all(DIR).map_err(|_| "backup_failed")?;
        ProfileStateManager::write_atomic(&path, &format!("{}\n{}\n", boot, values.join("\n")))
            .map_err(|_| "backup_failed")?;
        Ok(values)
    }

    fn supported(mode: &str) -> bool {
        if mode == "system" {
            return true;
        }
        if !MODES.contains(&mode) || !catalog::property_supported() {
            return false;
        }
        if matches!(mode, "prefer_hardware" | "prefer_software")
            && !catalog::rank_properties_supported()
        {
            return false;
        }
        let codecs = catalog::declared_codecs();
        codecs.iter().any(|c| {
            c.name.starts_with("OMX.")
                && c.mime == "video/avc"
                && match mode {
                    "prefer_hardware" => !c.software,
                    "prefer_software" => c.software,
                    _ => true,
                }
        })
    }

    fn apply(mode: &str) -> Result<bool, String> {
        if !Self::supported(mode) {
            return Err("mode_unsupported".into());
        }
        let baseline = match Self::baseline(mode != "system") {
            Ok(values) => values,
            Err(error) if mode == "system" && error == "backup_unavailable" => return Ok(false),
            Err(error) => return Err(error),
        };
        let target = Self::targets(mode, &baseline);
        let previous: Vec<_> = KEYS
            .iter()
            .map(|key| getprop(key))
            .collect::<Result<_, _>>()?;
        let transaction = MediaTransaction::capture()?;
        let changed = previous != target;
        for (key, value) in KEYS.iter().zip(&target) {
            if getprop(key).as_ref() != Ok(value) && setprop(key, value).is_err() {
                return Err(transaction.error_with_rollback("property_write_failed"));
            }
        }
        if ProfileStateManager::write_atomic(&Path::new(DIR).join("mode"), mode).is_err() {
            return Err(transaction.error_with_rollback("state_write_failed"));
        }
        if fs::write(Path::new(DIR).join("pending"), "1").is_err() {
            return Err(transaction.error_with_rollback("state_write_failed"));
        }
        Ok(changed)
    }

    /// Restart only the media player process, never zygote, system_server or a hardware HAL.
    fn restart_player() -> Result<(), String> {
        catalog::require_idle_audio()?;
        let calls = Command::new("dumpsys")
            .args(["-t", "3", "telephony.registry"])
            .output()
            .map_err(|_| "call_state_unavailable")?;
        if !calls.status.success() {
            return Err("call_state_unavailable".into());
        }
        let calls = String::from_utf8_lossy(&calls.stdout);
        let states: Vec<_> = calls
            .lines()
            .filter_map(|l| l.trim().strip_prefix("mCallState="))
            .collect();
        if states.iter().any(|s| s.trim() != "0") {
            return Err("call_active_or_unknown".into());
        }
        let pids = catalog::player_pids();
        if pids.is_empty() {
            return Err("player_service_unavailable".into());
        }
        for pid in &pids {
            let comm = fs::read_to_string(format!("/proc/{}/comm", pid)).unwrap_or_default();
            let executable = fs::read_link(format!("/proc/{}/exe", pid))
                .ok()
                .and_then(|path| {
                    path.file_name()
                        .map(|n| n == "mediaserver" || n == "mediaserver32")
                })
                .unwrap_or(false);
            if !matches!(comm.trim(), "mediaserver" | "mediaserver32") || !executable {
                return Err("player_identity_changed".into());
            }
            if !Command::new("kill")
                .args(["-TERM", &pid.to_string()])
                .output()
                .map(|o| o.status.success())
                .unwrap_or(false)
            {
                return Err("player_restart_failed".into());
            }
        }
        for _ in 0..20 {
            sleep(Duration::from_millis(250));
            let current = catalog::player_pids();
            if !current.is_empty() && current.iter().all(|pid| !pids.contains(pid)) {
                let _ = fs::remove_file(Path::new(DIR).join("pending"));
                return Ok(());
            }
        }
        Err("player_restart_unconfirmed".into())
    }

    pub fn status() -> String {
        let mode = ConfigStore::get("media.decoder_mode").unwrap_or_else(|| "system".into());
        let boot = ConfigStore::get("media.decoder_apply_on_boot").as_deref() == Some("true");
        let session =
            fs::read_to_string(Path::new(DIR).join("mode")).unwrap_or_else(|_| "unmanaged".into());
        let verified = Self::baseline(false)
            .ok()
            .filter(|_| MODES.contains(&session.trim()))
            .map(|baseline| {
                KEYS.iter()
                    .zip(Self::targets(session.trim(), &baseline))
                    .all(|(key, target)| getprop(key).ok().as_ref() == Some(&target))
            })
            .unwrap_or(false);
        let supported: Vec<_> = MODES
            .iter()
            .filter(|mode| Self::supported(mode))
            .map(|mode| quote(mode))
            .collect();
        let props: Vec<_> = KEYS
            .iter()
            .map(|key| {
                format!(
                    "{}:{}",
                    quote(key),
                    quote(&getprop(key).unwrap_or_default())
                )
            })
            .collect();
        format!(concat!("{{\"ok\":true,\"configured_mode\":{},\"apply_on_boot\":{},\"session_mode\":{},",
            "\"pending_restart\":{},\"restore_available\":{},\"session_property_verified\":{},\"supported_modes\":[{}],\"properties\":{{{}}},",
            "\"catalog_source\":\"xml_declarations\",\"declared_codecs\":{},\"active_decoders\":{}}}"),
            quote(&mode), boot, quote(session.trim()), Path::new(DIR).join("pending").exists(), Self::baseline(false).is_ok(), verified,
            supported.join(","), props.join(","), catalog::declarations_json(&catalog::declared_codecs()), catalog::active_decoders_json())
    }

    fn configure(mode: &str, boot: bool, restart: bool) -> Result<(), String> {
        let _lock = ModeLock::acquire().map_err(|_| "busy")?;
        let _media_lock = MediaOperationGuard::acquire()?;
        if !MODES.contains(&mode) {
            return Err("invalid_mode".into());
        }
        let transaction = MediaTransaction::capture()?;
        Self::apply(mode)?;
        if ConfigStore::set_batch(&[
            ("media.decoder_mode".into(), mode.into(), Some("str".into())),
            (
                "media.decoder_apply_on_boot".into(),
                (boot && mode != "system").to_string(),
                Some("bool".into()),
            ),
        ])
        .is_err()
        {
            return Err(transaction.error_with_rollback("config_write_failed"));
        }
        if restart {
            Self::restart_player()?;
        }
        Ok(())
    }

    pub fn apply_boot() -> Result<(), String> {
        if ConfigStore::get("media.decoder_apply_on_boot").as_deref() != Some("true") {
            return Ok(());
        }
        let mode = ConfigStore::get("media.decoder_mode").unwrap_or_else(|| "system".into());
        if mode == "system" {
            return Ok(());
        }
        let _lock = ModeLock::acquire().map_err(|_| "busy")?;
        let _media_lock = MediaOperationGuard::acquire()?;
        Self::apply(&mode)?;
        Self::restart_player()?;
        Ok(())
    }

    pub fn restore_session() -> Result<(), String> {
        let _lock = ModeLock::acquire().map_err(|_| "busy")?;
        let _media_lock = MediaOperationGuard::acquire()?;
        let mode = match fs::read_to_string(Path::new(DIR).join("mode")) {
            Ok(mode) => mode,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
            Err(_) => return Err("session_read_failed".into()),
        };
        if !MODES.contains(&mode.trim()) {
            return Err("session_invalid".into());
        }
        let baseline = Self::baseline(false)?;
        let expected = Self::targets(mode.trim(), &baseline);
        // Preserve subsequent external changes during automatic uninstall restoration.
        for (key, value) in KEYS.iter().zip(&expected) {
            if getprop(key).as_ref() != Ok(value) {
                return Err("session_modified_externally".into());
            }
        }
        if Self::apply("system")? || Path::new(DIR).join("pending").exists() {
            Self::restart_player()?;
        }
        Ok(())
    }

    pub fn cli(args: &[String]) -> i32 {
        let result = match args.first().map(String::as_str).unwrap_or("status") {
            "status" if args.len() <= 1 => {
                println!("{}", Self::status());
                return 0;
            }
            "set"
                if (args.len() == 3 || (args.len() == 4 && args[3] == "--restart-player"))
                    && MODES.contains(&args[1].as_str())
                    && matches!(args[2].as_str(), "true" | "false") =>
            {
                Self::configure(
                    &args[1],
                    args[2] == "true",
                    args.iter().any(|v| v == "--restart-player"),
                )
            }
            "restart-player" if args.len() == 1 => ModeLock::acquire()
                .map_err(|_| "busy".to_string())
                .and_then(|_lock| {
                    MediaOperationGuard::acquire().and_then(|_guard| Self::restart_player())
                }),
            "restore" if args.len() == 1 || (args.len() == 2 && args[1] == "--restart-player") => {
                Self::configure(
                    "system",
                    false,
                    args.iter().any(|v| v == "--restart-player"),
                )
            }
            _ => Err("invalid_arguments".into()),
        };
        match result {
            Ok(()) => {
                println!("{{\"ok\":true}}");
                0
            }
            Err(error) => {
                println!("{{\"ok\":false,\"error\":{}}}", quote(&error));
                1
            }
        }
    }
}

#[cfg(test)]
#[path = "media_tests.rs"]
mod tests;
