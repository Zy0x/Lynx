//! Dynamic, zero-hardcoding Zygote and USAP pool integrity guard.
//!
//! Protects Android systems against Zygote `POLLHUP` busy-spin deadlocks caused by
//! conflicting root modules (mount-namespace mutation or `usap32`/`usap64` worker
//! signaling) or stale persistent USAP overrides against ROM policy, while keeping
//! native USAP untouched whenever the ROM enables it safely.

use crate::utils::json::quote;
use crate::utils::Logger;
use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::{Mutex, OnceLock};
use std::time::{Duration, Instant};

const PROP_USAP_RUNTIME: &str = "persist.device_config.runtime_native.usap_pool_enabled";
const PROP_DALVIK_USAP: &str = "dalvik.vm.usap_pool_enabled";
const UNSET_BASELINE_MARKER: &str = "__UNSET__";

const CPU_SPIN_THRESHOLD_PCT: f64 = 70.0;
const TICK_INTERVAL_SECS: u64 = 10;
const MODULE_SCAN_INTERVAL_SECS: u64 = 60;
const COLD_PROBE_MS: u64 = 220;
const MAX_SCRIPT_BYTES: u64 = 256 * 1024;
const MAX_FILES_PER_MODULE: usize = 16;

const DEFAULT_ROM_PROP_FILES: &[&str] = &[
    "/system/build.prop",
    "/system_ext/etc/build.prop",
    "/product/etc/build.prop",
    "/odm/etc/build.prop",
    "/vendor/build.prop",
];

#[derive(Debug, Clone)]
pub struct GuardPaths {
    pub modpath: PathBuf,
    pub modules_root: PathBuf,
    pub lynx_data_dir: PathBuf,
    pub proc_root: PathBuf,
    pub socket_dir: PathBuf,
    pub rom_prop_files: Vec<PathBuf>,
}

impl Default for GuardPaths {
    fn default() -> Self {
        Self {
            modpath: PathBuf::from("/data/adb/modules/Lynx"),
            modules_root: PathBuf::from("/data/adb/modules"),
            lynx_data_dir: PathBuf::from("/data/adb/lynx"),
            proc_root: PathBuf::from("/proc"),
            socket_dir: PathBuf::from("/dev/socket"),
            rom_prop_files: DEFAULT_ROM_PROP_FILES.iter().map(PathBuf::from).collect(),
        }
    }
}

impl GuardPaths {
    fn override_file(&self) -> PathBuf {
        self.lynx_data_dir.join("user_usap_pool")
    }

    fn circuit_trip_file(&self) -> PathBuf {
        self.lynx_data_dir.join("usap_circuit_tripped")
    }

    fn baseline_file(&self) -> PathBuf {
        self.modpath.join("stock_state/usap_pool_enabled")
    }

    fn legacy_migration_file(&self) -> PathBuf {
        self.modpath.join(".migrate_clear_legacy_usap")
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum UsapAction {
    Preserve,
    SetEnabled(bool),
    DeletePersistent,
}

impl UsapAction {
    pub fn as_str(&self) -> &'static str {
        match self {
            UsapAction::Preserve => "preserve",
            UsapAction::SetEnabled(true) => "enable",
            UsapAction::SetEnabled(false) => "disable",
            UsapAction::DeletePersistent => "delete_persistent",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct UsapDecision {
    pub action: UsapAction,
    pub reason: String,
    pub trip_circuit: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RomUsapPolicy {
    pub dalvik_prop: Option<bool>,
    pub runtime_default_prop: Option<bool>,
    pub source: String,
}

impl RomUsapPolicy {
    pub fn effective(&self) -> Option<bool> {
        self.dalvik_prop.or(self.runtime_default_prop)
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ModuleConflict {
    pub module_id: String,
    pub file: String,
    pub reason: String,
}

#[derive(Debug, Clone)]
pub struct ZygoteProcessHealth {
    pub pid: u32,
    pub comm: String,
    pub pipe_fds: usize,
    pub live_usap_children: usize,
    pub orphaned_pipes: usize,
    pub cpu_pct: f64,
    pub spinning: bool,
}

#[derive(Debug, Clone)]
pub struct UsapDiscoveryInput {
    pub user_override: Option<bool>,
    pub circuit_tripped: bool,
    pub live_spin_detected: bool,
    pub legacy_migration_pending: bool,
    pub rom_default_usap: Option<bool>,
    pub current_persist_usap: Option<bool>,
    pub current_dalvik_usap: Option<bool>,
    pub conflicts: Vec<ModuleConflict>,
    pub baseline_value: Option<String>,
}

#[derive(Debug, Clone, Copy)]
struct ZygoteCpuSample {
    total_ticks: u64,
    sampled_at: Instant,
}

#[derive(Default)]
struct ZygoteMonitorState {
    prev_samples: HashMap<u32, ZygoteCpuSample>,
    last_tick_at: Option<Instant>,
    last_module_scan_at: Option<Instant>,
    cached_conflicts: Vec<ModuleConflict>,
    circuit_tripped_logged: bool,
}

static MONITOR_STATE: OnceLock<Mutex<ZygoteMonitorState>> = OnceLock::new();

fn monitor_state() -> &'static Mutex<ZygoteMonitorState> {
    MONITOR_STATE.get_or_init(|| Mutex::new(ZygoteMonitorState::default()))
}

pub struct ZygoteGuard;

impl ZygoteGuard {
    /// Early boot reconciliation designed for `post-fs-data` before Zygote forks.
    pub fn reconcile_early_boot() -> String {
        Self::reconcile_with_mode("early_boot", false)
    }

    /// Boot reconciliation invoked by `LifecycleHooks::run_boot`.
    pub fn reconcile_boot() -> String {
        Self::reconcile_with_mode("boot", true)
    }

    /// Installer reconciliation invoked during module flash/upgrade.
    pub fn reconcile_install() -> String {
        Self::reconcile_with_mode("install", false)
    }

    /// Periodic daemon guardian tick (screen-on only, throttled, near-zero overhead).
    pub fn tick(screen_on: bool) {
        if !screen_on {
            return;
        }

        let now = Instant::now();
        let (should_run, scan_modules, mut prev_samples) = {
            let mut guard = match monitor_state().lock() {
                Ok(g) => g,
                Err(poisoned) => poisoned.into_inner(),
            };
            if let Some(last) = guard.last_tick_at {
                if now.duration_since(last).as_secs() < TICK_INTERVAL_SECS {
                    return;
                }
            }
            guard.last_tick_at = Some(now);
            let rescan = guard
                .last_module_scan_at
                .map(|t| now.duration_since(t).as_secs() >= MODULE_SCAN_INTERVAL_SECS)
                .unwrap_or(true);
            (true, rescan, guard.prev_samples.clone())
        };

        if !should_run {
            return;
        }

        let paths = GuardPaths::default();
        let conflicts = if scan_modules {
            let found = scan_module_conflicts_in(&paths.modules_root);
            if let Ok(mut guard) = monitor_state().lock() {
                guard.last_module_scan_at = Some(now);
                guard.cached_conflicts = found.clone();
            }
            found
        } else {
            monitor_state()
                .lock()
                .map(|g| g.cached_conflicts.clone())
                .unwrap_or_default()
        };

        let procs = inspect_zygote_processes(&paths.proc_root, &mut prev_samples, false);
        if let Ok(mut guard) = monitor_state().lock() {
            guard.prev_samples = prev_samples;
        }

        let live_spin = procs.iter().any(|p| p.spinning);
        let input = gather_discovery_input(&paths, conflicts, live_spin);
        let decision = evaluate_usap_policy(&input);

        if decision.action != UsapAction::Preserve || decision.trip_circuit {
            let _ = apply_decision(&paths, &input, &decision);
        }
    }

    /// Restores the pre-Lynx USAP persistent property baseline during module uninstall.
    pub fn restore_uninstall() -> Result<String, String> {
        let paths = GuardPaths::default();
        let baseline_path = paths.baseline_file();
        let mut restored = "none".to_string();

        if let Ok(raw) = fs::read_to_string(&baseline_path) {
            let val = raw.trim();
            match val {
                "true" | "false" => {
                    let ok = apply_property(PROP_USAP_RUNTIME, val);
                    restored = format!("set:{} (ok={})", val, ok);
                }
                UNSET_BASELINE_MARKER => {
                    let ok = delete_property(PROP_USAP_RUNTIME);
                    restored = format!("deleted (ok={})", ok);
                }
                _ => {}
            }
            let _ = fs::remove_file(&baseline_path);
        }

        let _ = fs::remove_file(paths.legacy_migration_file());
        let _ = fs::remove_file(paths.circuit_trip_file());
        Ok(restored)
    }

    /// Clears circuit-breaker latch and explicit override so ROM/auto evaluation runs fresh.
    pub fn reset_policy() -> String {
        let paths = GuardPaths::default();
        let _ = fs::remove_file(paths.circuit_trip_file());
        let _ = fs::remove_file(paths.override_file());
        if let Ok(mut guard) = monitor_state().lock() {
            guard.circuit_tripped_logged = false;
        }
        Self::reconcile_with_mode("reset", true)
    }

    /// Sets explicit user preference (`Some(true)`/`Some(false)`) and reconciles safely.
    pub fn set_user_override(enabled: bool) -> String {
        let paths = GuardPaths::default();
        let _ = fs::create_dir_all(&paths.lynx_data_dir);
        let value = if enabled { "1\n" } else { "0\n" };
        let _ = fs::write(paths.override_file(), value);
        if enabled {
            let _ = fs::remove_file(paths.circuit_trip_file());
        }
        Self::reconcile_with_mode("user_override", true)
    }

    /// Returns complete live diagnostic JSON for CLI / Companion inspection.
    pub fn status_json() -> String {
        let paths = GuardPaths::default();
        let mut samples = monitor_state()
            .lock()
            .map(|g| g.prev_samples.clone())
            .unwrap_or_default();
        let procs = inspect_zygote_processes(&paths.proc_root, &mut samples, true);
        if let Ok(mut guard) = monitor_state().lock() {
            guard.prev_samples = samples;
        }

        let conflicts = scan_module_conflicts_in(&paths.modules_root);
        let live_spin = procs.iter().any(|p| p.spinning);
        let input = gather_discovery_input(&paths, conflicts, live_spin);
        let decision = evaluate_usap_policy(&input);
        let sockets = detect_usap_sockets(&paths.socket_dir);

        render_status_json(&input, &decision, &procs, &sockets, false, "status")
    }

    /// CLI entrypoint (`lynxd zygote-guard ...` or `lynxd system zygote-guard ...`).
    pub fn cli(args: &[String]) -> i32 {
        let sub = args.first().map(|s| s.as_str()).unwrap_or("status");
        match sub {
            "status" | "info" => {
                println!("{}", Self::status_json());
                0
            }
            "reconcile" | "check" => {
                let stage = if args.iter().any(|a| a == "--early") {
                    "early_boot"
                } else if args.iter().any(|a| a == "--install") {
                    "install"
                } else {
                    "cli"
                };
                let probe = stage != "early_boot" && stage != "install";
                println!("{}", Self::reconcile_with_mode(stage, probe));
                0
            }
            "reset" | "allow-rom" | "clear-trip" => {
                println!("{}", Self::reset_policy());
                0
            }
            "disable" | "off" => {
                println!("{}", Self::set_user_override(false));
                0
            }
            "enable" | "on" => {
                println!("{}", Self::set_user_override(true));
                0
            }
            "restore" => match Self::restore_uninstall() {
                Ok(msg) => {
                    println!("{{\"ok\":true,\"restored\":{}}}", quote(&msg));
                    0
                }
                Err(e) => {
                    eprintln!("{}", e);
                    1
                }
            },
            other => {
                eprintln!(
                    "Unknown zygote-guard subcommand '{}'. Use: status|reconcile|reset|enable|disable|restore",
                    other
                );
                1
            }
        }
    }

    fn reconcile_with_mode(stage: &str, probe_live_proc: bool) -> String {
        let paths = GuardPaths::default();
        let conflicts = scan_module_conflicts_in(&paths.modules_root);

        let procs = if probe_live_proc {
            let mut samples = monitor_state()
                .lock()
                .map(|g| g.prev_samples.clone())
                .unwrap_or_default();
            let list = inspect_zygote_processes(&paths.proc_root, &mut samples, true);
            if let Ok(mut guard) = monitor_state().lock() {
                guard.prev_samples = samples;
                guard.last_module_scan_at = Some(Instant::now());
                guard.cached_conflicts = conflicts.clone();
            }
            list
        } else {
            Vec::new()
        };

        let live_spin = procs.iter().any(|p| p.spinning);
        let input = gather_discovery_input(&paths, conflicts, live_spin);
        let decision = evaluate_usap_policy(&input);
        let applied = apply_decision(&paths, &input, &decision);
        let sockets = detect_usap_sockets(&paths.socket_dir);

        if input.legacy_migration_pending && paths.legacy_migration_file().exists() {
            let _ = fs::remove_file(paths.legacy_migration_file());
        }

        render_status_json(&input, &decision, &procs, &sockets, applied, stage)
    }
}

fn gather_discovery_input(
    paths: &GuardPaths,
    conflicts: Vec<ModuleConflict>,
    live_spin_detected: bool,
) -> UsapDiscoveryInput {
    let rom_policy = discover_rom_usap_policy(&paths.rom_prop_files);
    let current_dalvik_usap = read_prop_bool(PROP_DALVIK_USAP);
    let current_persist_usap = read_prop_bool(PROP_USAP_RUNTIME);
    let rom_default_usap = rom_policy.effective().or(current_dalvik_usap);

    let user_override = fs::read_to_string(paths.override_file())
        .ok()
        .and_then(|s| parse_bool_str(&s));
    let baseline_value = fs::read_to_string(paths.baseline_file())
        .ok()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty());

    UsapDiscoveryInput {
        user_override,
        circuit_tripped: paths.circuit_trip_file().exists(),
        live_spin_detected,
        legacy_migration_pending: paths.legacy_migration_file().exists(),
        rom_default_usap,
        current_persist_usap,
        current_dalvik_usap,
        conflicts,
        baseline_value,
    }
}

fn apply_decision(
    paths: &GuardPaths,
    input: &UsapDiscoveryInput,
    decision: &UsapDecision,
) -> bool {
    if decision.trip_circuit {
        let _ = fs::create_dir_all(&paths.lynx_data_dir);
        let reason_line = format!("{}\n", decision.reason);
        let _ = fs::write(paths.circuit_trip_file(), reason_line);
        let should_log = monitor_state()
            .lock()
            .map(|mut g| {
                let first = !g.circuit_tripped_logged;
                g.circuit_tripped_logged = true;
                first
            })
            .unwrap_or(true);
        if should_log {
            Logger::warn(format!(
                "[ZYGOTE-GUARD] Live USAP POLLHUP busy-spin detected; tripping safety breaker ({})",
                decision.reason
            ));
        }
    }

    match decision.action {
        UsapAction::Preserve => false,
        UsapAction::SetEnabled(target) => {
            if input.legacy_migration_pending {
                let baseline_seed = input.rom_default_usap;
                capture_baseline_if_missing(paths, baseline_seed);
            } else {
                capture_baseline_if_missing(paths, input.current_persist_usap);
            }
            let target_str = if target { "true" } else { "false" };
            let ok = apply_property(PROP_USAP_RUNTIME, target_str);
            if ok {
                Logger::info(format!(
                    "[ZYGOTE-GUARD] Set {}={} (reason: {}, conflicts: {})",
                    PROP_USAP_RUNTIME,
                    target_str,
                    decision.reason,
                    format_conflict_summary(&input.conflicts)
                ));
            }
            ok
        }
        UsapAction::DeletePersistent => {
            if input.legacy_migration_pending {
                capture_baseline_if_missing(paths, None);
            } else {
                capture_baseline_if_missing(paths, input.current_persist_usap);
            }
            let ok = delete_property(PROP_USAP_RUNTIME);
            if ok {
                Logger::info(format!(
                    "[ZYGOTE-GUARD] Cleared legacy {} override (reason: {})",
                    PROP_USAP_RUNTIME, decision.reason
                ));
            }
            ok
        }
    }
}

fn format_conflict_summary(conflicts: &[ModuleConflict]) -> String {
    if conflicts.is_empty() {
        return "none".to_string();
    }
    conflicts
        .iter()
        .map(|c| format!("{}:{}", c.module_id, c.reason))
        .collect::<Vec<_>>()
        .join(",")
}

fn capture_baseline_if_missing(paths: &GuardPaths, current_persist: Option<bool>) {
    let baseline = paths.baseline_file();
    if baseline.exists() {
        return;
    }
    if let Some(parent) = baseline.parent() {
        let _ = fs::create_dir_all(parent);
    }
    let val = match current_persist {
        Some(true) => "true\n",
        Some(false) => "false\n",
        None => "__UNSET__\n",
    };
    let _ = fs::write(baseline, val);
}

/// Evaluates the pure, deterministic USAP decision matrix from discovered hardware & runtime signals.
pub fn evaluate_usap_policy(input: &UsapDiscoveryInput) -> UsapDecision {
    if input.live_spin_detected {
        return UsapDecision {
            action: UsapAction::SetEnabled(false),
            reason: "live_zygote_usap_pollhup_spin_detected".to_string(),
            trip_circuit: true,
        };
    }

    if input.circuit_tripped {
        let action = if input.current_persist_usap == Some(false) {
            UsapAction::Preserve
        } else {
            UsapAction::SetEnabled(false)
        };
        return UsapDecision {
            action,
            reason: "circuit_breaker_tripped".to_string(),
            trip_circuit: false,
        };
    }

    if input.user_override == Some(false) {
        let action = if input.current_persist_usap == Some(false) {
            UsapAction::Preserve
        } else {
            UsapAction::SetEnabled(false)
        };
        return UsapDecision {
            action,
            reason: "user_override_disabled".to_string(),
            trip_circuit: false,
        };
    }

    if !input.conflicts.is_empty() && input.user_override != Some(true) {
        let action = if input.current_persist_usap == Some(false) {
            UsapAction::Preserve
        } else {
            UsapAction::SetEnabled(false)
        };
        return UsapDecision {
            action,
            reason: "active_module_usap_conflict".to_string(),
            trip_circuit: false,
        };
    }

    if input.user_override == Some(true) {
        let action = if input.current_persist_usap == Some(true) {
            UsapAction::Preserve
        } else {
            UsapAction::SetEnabled(true)
        };
        return UsapDecision {
            action,
            reason: "user_override_enabled".to_string(),
            trip_circuit: false,
        };
    }

    if input.legacy_migration_pending {
        if input.rom_default_usap == Some(true) {
            return UsapDecision {
                action: UsapAction::Preserve,
                reason: "rom_native_usap_preserved".to_string(),
                trip_circuit: false,
            };
        }
        if input.baseline_value.as_deref() == Some(UNSET_BASELINE_MARKER) {
            return UsapDecision {
                action: UsapAction::DeletePersistent,
                reason: "legacy_usap_override_reconciled".to_string(),
                trip_circuit: false,
            };
        }
        if input.current_persist_usap != Some(false) {
            return UsapDecision {
                action: UsapAction::SetEnabled(false),
                reason: "legacy_usap_override_reconciled".to_string(),
                trip_circuit: false,
            };
        }
    }

    if input.rom_default_usap == Some(false) {
        if input.current_persist_usap == Some(true) {
            return UsapDecision {
                action: UsapAction::SetEnabled(false),
                reason: "rom_policy_alignment".to_string(),
                trip_circuit: false,
            };
        }
        return UsapDecision {
            action: UsapAction::Preserve,
            reason: "rom_policy_aligned".to_string(),
            trip_circuit: false,
        };
    }

    UsapDecision {
        action: UsapAction::Preserve,
        reason: "safe_native_preserved".to_string(),
        trip_circuit: false,
    }
}

/// Discovers read-only ROM build property defaults across system partitions in override order.
pub fn discover_rom_usap_policy(prop_files: &[PathBuf]) -> RomUsapPolicy {
    let mut dalvik_prop: Option<bool> = None;
    let mut runtime_default_prop: Option<bool> = None;
    let mut source = String::from("none");

    for path in prop_files {
        let Ok(content) = fs::read_to_string(path) else {
            continue;
        };
        let parsed = parse_rom_prop_text(&content);
        if let Some(v) = parsed.0 {
            dalvik_prop = Some(v);
            source = path.display().to_string();
        }
        if let Some(v) = parsed.1 {
            runtime_default_prop = Some(v);
            if parsed.0.is_none() {
                source = path.display().to_string();
            }
        }
    }

    RomUsapPolicy {
        dalvik_prop,
        runtime_default_prop,
        source,
    }
}

/// Parses `dalvik.vm.usap_pool_enabled` and `persist.device_config.runtime_native.usap_pool_enabled` from `.prop` text.
pub fn parse_rom_prop_text(content: &str) -> (Option<bool>, Option<bool>) {
    let mut dalvik = None;
    let mut runtime = None;

    for raw in content.lines() {
        let line = raw.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let Some((k, v)) = line.split_once('=') else {
            continue;
        };
        let key = k.trim();
        let val = parse_bool_str(v.trim());
        if key == PROP_DALVIK_USAP {
            if val.is_some() {
                dalvik = val;
            }
        } else if key == PROP_USAP_RUNTIME && val.is_some() {
            runtime = val;
        }
    }

    (dalvik, runtime)
}

/// Scans all enabled modules under `modules_root` for Zygote namespace or USAP termination hazards.
pub fn scan_module_conflicts_in(modules_root: &Path) -> Vec<ModuleConflict> {
    let Ok(entries) = fs::read_dir(modules_root) else {
        return Vec::new();
    };

    let mut module_dirs: Vec<PathBuf> = entries
        .filter_map(|e| e.ok().map(|item| item.path()))
        .filter(|p| p.is_dir())
        .collect();
    module_dirs.sort();

    let mut conflicts = Vec::new();
    for mod_dir in module_dirs {
        let Some(id_os) = mod_dir.file_name() else {
            continue;
        };
        let mod_id = id_os.to_string_lossy().to_string();
        if mod_id == "Lynx" || mod_id.starts_with('.') {
            continue;
        }
        if mod_dir.join("disable").exists() || mod_dir.join("remove").exists() {
            continue;
        }

        let scripts = collect_candidate_module_scripts(&mod_dir);
        for script_path in scripts {
            let Ok(meta) = fs::metadata(&script_path) else {
                continue;
            };
            if !meta.is_file() || meta.len() > MAX_SCRIPT_BYTES {
                continue;
            }
            let Ok(bytes) = fs::read(&script_path) else {
                continue;
            };
            let text = String::from_utf8_lossy(&bytes);
            let stripped = strip_shell_comments(&text);
            if let Some(reason) = detect_script_usap_conflict(&stripped) {
                let rel = script_path
                    .strip_prefix(&mod_dir)
                    .map(|p| p.display().to_string())
                    .unwrap_or_else(|_| script_path.display().to_string());
                conflicts.push(ModuleConflict {
                    module_id: mod_id.clone(),
                    file: rel.replace('\\', "/"),
                    reason: reason.to_string(),
                });
                break;
            }
        }
    }

    conflicts
}

fn collect_candidate_module_scripts(mod_dir: &Path) -> Vec<PathBuf> {
    let mut out = Vec::new();
    for hook in [
        "post-fs-data.sh",
        "service.sh",
        "boot-completed.sh",
        "post-mount.sh",
        "action.sh",
    ] {
        let candidate = mod_dir.join(hook);
        if candidate.is_file() {
            out.push(candidate);
        }
    }

    for subdir in ["scripts", "common", "core", "bin"] {
        let dir = mod_dir.join(subdir);
        let Ok(entries) = fs::read_dir(&dir) else {
            continue;
        };
        for entry in entries.filter_map(|e| e.ok()) {
            if out.len() >= MAX_FILES_PER_MODULE {
                return out;
            }
            let path = entry.path();
            if path.is_file()
                && path
                    .extension()
                    .and_then(|s| s.to_str())
                    .map(|ext| ext.eq_ignore_ascii_case("sh"))
                    .unwrap_or(false)
            {
                out.push(path);
            }
        }
    }

    out
}

/// Removes shell comments cleanly while keeping active command text intact.
pub fn strip_shell_comments(script: &str) -> String {
    let mut out = String::with_capacity(script.len());
    for raw_line in script.lines() {
        let trimmed = raw_line.trim_start();
        if trimmed.starts_with('#') {
            continue;
        }
        let mut active_end = raw_line.len();
        let bytes = raw_line.as_bytes();
        for i in 0..bytes.len() {
            if bytes[i] == b'#' && (i == 0 || bytes[i - 1].is_ascii_whitespace() || bytes[i - 1] == b';') {
                active_end = i;
                break;
            }
        }
        out.push_str(&raw_line[..active_end]);
        out.push('\n');
    }
    out
}

/// Classifies whether active shell code mutates Zygote mount namespaces or terminates USAP workers.
pub fn detect_script_usap_conflict(stripped_script: &str) -> Option<&'static str> {
    let lower = stripped_script.to_ascii_lowercase();

    let mentions_usap =
        lower.contains("usap64") || lower.contains("usap32") || lower.contains("usap_pool");
    if mentions_usap {
        let has_signal_or_ns = [
            "killall",
            "pkill",
            "kill ",
            "\tkill",
            ";kill",
            "&kill",
            "|kill",
            "sigterm",
            "sigkill",
            "-term",
            "-kill",
            " -9",
            " -15",
            "nsenter",
            "unshare",
        ]
        .iter()
        .any(|tok| lower.contains(tok));
        if has_signal_or_ns {
            return Some("usap_worker_signaling_or_namespace");
        }
    }

    let mentions_zygote = lower.contains("zygote64") || lower.contains("zygote");
    if mentions_zygote {
        let has_ns_mutation = ["nsenter", "/ns/mnt", "unshare"]
            .iter()
            .any(|tok| lower.contains(tok));
        if has_ns_mutation {
            return Some("zygote_mount_namespace_mutation");
        }
    }

    None
}

fn inspect_zygote_processes(
    proc_root: &Path,
    prev_samples: &mut HashMap<u32, ZygoteCpuSample>,
    allow_cold_probe: bool,
) -> Vec<ZygoteProcessHealth> {
    let clk_tck = query_clk_tck();
    let now = Instant::now();
    let mut zygotes: Vec<(u32, String, u64)> = Vec::new();
    let mut usap_children: HashMap<u32, usize> = HashMap::new();

    let Ok(entries) = fs::read_dir(proc_root) else {
        return Vec::new();
    };

    for entry in entries.filter_map(|e| e.ok()) {
        let Some(pid_str) = entry.file_name().to_str().map(str::to_string) else {
            continue;
        };
        let Ok(pid) = pid_str.parse::<u32>() else {
            continue;
        };
        let pid_dir = entry.path();
        let Ok(comm_raw) = fs::read_to_string(pid_dir.join("comm")) else {
            continue;
        };
        let comm = comm_raw.trim();
        let identity = if matches!(comm, "zygote" | "zygote64" | "usap32" | "usap64") {
            Some(comm.to_string())
        } else if comm == "main" || comm.starts_with("app_process") {
            fs::read(pid_dir.join("cmdline"))
                .ok()
                .and_then(|bytes| classify_proc_identity(comm, Some(&bytes)))
        } else {
            None
        };
        let Some(identity_name) = identity else {
            continue;
        };
        let is_zygote = identity_name == "zygote" || identity_name == "zygote64";
        let is_usap = identity_name == "usap32" || identity_name == "usap64";
        let Ok(stat_raw) = fs::read_to_string(pid_dir.join("stat")) else {
            continue;
        };
        let Some((state, ppid, total_ticks)) = parse_proc_stat(&stat_raw) else {
            continue;
        };
        if state == 'Z' {
            continue;
        }
        if is_zygote {
            zygotes.push((pid, identity_name, total_ticks));
        } else if is_usap {
            *usap_children.entry(ppid).or_insert(0) += 1;
        }
    }

    zygotes.sort_by_key(|(pid, _, _)| *pid);

    // Count pipe FDs first; if an un-sampled Zygote has orphaned pipes in CLI mode,
    // take a single brief probe sample to confirm whether it is busy-spinning.
    let mut pipe_counts: HashMap<u32, usize> = HashMap::new();
    let mut needs_cold_probe = false;
    for (zpid, _, _) in &zygotes {
        let pipes = count_pipe_fds(&proc_root.join(zpid.to_string()).join("fd"));
        let children = usap_children.get(zpid).copied().unwrap_or(0);
        if pipes > children && !prev_samples.contains_key(zpid) {
            needs_cold_probe = true;
        }
        pipe_counts.insert(*zpid, pipes);
    }

    if allow_cold_probe && needs_cold_probe && !zygotes.is_empty() {
        for (zpid, _, ticks) in &zygotes {
            prev_samples.insert(
                *zpid,
                ZygoteCpuSample {
                    total_ticks: *ticks,
                    sampled_at: now,
                },
            );
        }
        std::thread::sleep(Duration::from_millis(COLD_PROBE_MS));
    }

    let sample_now = Instant::now();
    let mut results = Vec::with_capacity(zygotes.len());
    let mut next_samples = HashMap::with_capacity(zygotes.len());

    for (zpid, comm, initial_ticks) in zygotes {
        let current_ticks = if allow_cold_probe && needs_cold_probe {
            fs::read_to_string(proc_root.join(zpid.to_string()).join("stat"))
                .ok()
                .and_then(|s| parse_proc_stat(&s))
                .map(|(_, _, t)| t)
                .unwrap_or(initial_ticks)
        } else {
            initial_ticks
        };

        let pipe_fds = pipe_counts.get(&zpid).copied().unwrap_or(0);
        let live_usap = usap_children.get(&zpid).copied().unwrap_or(0);
        let orphaned_pipes = pipe_fds.saturating_sub(live_usap);

        let cpu_pct = if let Some(prev) = prev_samples.get(&zpid) {
            let dt = sample_now.duration_since(prev.sampled_at).as_secs_f64();
            if dt >= 0.05 && current_ticks >= prev.total_ticks {
                ((current_ticks - prev.total_ticks) as f64 / clk_tck / dt) * 100.0
            } else {
                0.0
            }
        } else {
            0.0
        };

        next_samples.insert(
            zpid,
            ZygoteCpuSample {
                total_ticks: current_ticks,
                sampled_at: sample_now,
            },
        );

        results.push(ZygoteProcessHealth {
            pid: zpid,
            comm,
            pipe_fds,
            live_usap_children: live_usap,
            orphaned_pipes,
            cpu_pct,
            spinning: orphaned_pipes > 0 && cpu_pct >= CPU_SPIN_THRESHOLD_PCT,
        });
    }

    *prev_samples = next_samples;
    results
}

/// Resolves Zygote/USAP process identity across both `/proc/<pid>/comm` and `argv[0]` in `cmdline`.
pub fn classify_proc_identity(comm: &str, cmdline_bytes: Option<&[u8]>) -> Option<String> {
    let trimmed_comm = comm.trim();
    if matches!(trimmed_comm, "zygote" | "zygote64" | "usap32" | "usap64") {
        return Some(trimmed_comm.to_string());
    }
    let bytes = cmdline_bytes?;
    let first_arg = bytes.split(|&b| b == 0).next().unwrap_or_default();
    let arg0 = String::from_utf8_lossy(first_arg);
    let base = arg0.trim().rsplit('/').next().unwrap_or("").trim();
    if matches!(base, "zygote" | "zygote64" | "usap32" | "usap64") {
        Some(base.to_string())
    } else {
        None
    }
}

/// Parses `(state, ppid, utime + stime)` from a `/proc/<pid>/stat` line.
pub fn parse_proc_stat(stat_raw: &str) -> Option<(char, u32, u64)> {
    let rparen = stat_raw.rfind(')')?;
    let after = stat_raw.get(rparen + 1..)?.trim_start();
    let fields: Vec<&str> = after.split_ascii_whitespace().collect();
    if fields.len() <= 12 {
        return None;
    }
    let state = fields[0].chars().next()?;
    let ppid = fields[1].parse::<u32>().ok()?;
    let utime = fields[11].parse::<u64>().ok()?;
    let stime = fields[12].parse::<u64>().ok()?;
    Some((state, ppid, utime.saturating_add(stime)))
}

fn count_pipe_fds(fd_dir: &Path) -> usize {
    let Ok(entries) = fs::read_dir(fd_dir) else {
        return 0;
    };
    let mut pipes = 0usize;
    for entry in entries.filter_map(|e| e.ok()) {
        if let Ok(target) = fs::read_link(entry.path()) {
            if let Some(target_str) = target.to_str() {
                if is_pipe_link_target(target_str) {
                    pipes += 1;
                }
            }
        }
    }
    pipes
}

/// Checks whether a `/proc/<pid>/fd/*` symlink target represents an anonymous pipe.
pub fn is_pipe_link_target(target: &str) -> bool {
    target.starts_with("pipe:[")
}

fn detect_usap_sockets(socket_dir: &Path) -> Vec<String> {
    let Ok(entries) = fs::read_dir(socket_dir) else {
        return Vec::new();
    };
    let mut sockets: Vec<String> = entries
        .filter_map(|e| e.ok())
        .filter_map(|e| {
            let name = e.file_name().to_string_lossy().to_string();
            if name.starts_with("usap_pool") {
                Some(name)
            } else {
                None
            }
        })
        .collect();
    sockets.sort();
    sockets
}

fn query_clk_tck() -> f64 {
    #[cfg(any(target_os = "android", target_os = "linux"))]
    {
        extern "C" {
            fn sysconf(name: i32) -> std::os::raw::c_long;
        }
        #[cfg(target_os = "android")]
        const SC_CLK_TCK: i32 = 6;
        #[cfg(not(target_os = "android"))]
        const SC_CLK_TCK: i32 = 2;
        let ticks = unsafe { sysconf(SC_CLK_TCK) };
        if ticks > 0 {
            return ticks as f64;
        }
    }
    100.0
}

fn parse_bool_str(raw: &str) -> Option<bool> {
    match raw.trim().to_ascii_lowercase().as_str() {
        "true" | "1" | "yes" | "on" => Some(true),
        "false" | "0" | "no" | "off" => Some(false),
        _ => None,
    }
}

fn read_prop_bool(key: &str) -> Option<bool> {
    for bin in ["/system/bin/getprop", "getprop"] {
        if bin.starts_with('/') && !Path::new(bin).exists() {
            continue;
        }
        if let Ok(out) = Command::new(bin).arg(key).output() {
            if out.status.success() {
                let s = String::from_utf8_lossy(&out.stdout);
                let parsed = parse_bool_str(&s);
                if parsed.is_some() || !s.trim().is_empty() {
                    return parsed;
                }
                return None;
            }
        }
    }
    for bin in resetprop_candidates() {
        if bin.starts_with('/') && !Path::new(bin).exists() {
            continue;
        }
        if let Ok(out) = Command::new(bin).arg(key).output() {
            if out.status.success() {
                let s = String::from_utf8_lossy(&out.stdout);
                return parse_bool_str(&s);
            }
        }
    }
    None
}

fn resetprop_candidates() -> &'static [&'static str] {
    &[
        "/data/adb/ksu/bin/resetprop",
        "/data/adb/ap/bin/resetprop",
        "/data/adb/magisk/resetprop",
        "/debug_ramdisk/resetprop",
        "/sbin/resetprop",
        "resetprop",
    ]
}

fn apply_property(key: &str, val: &str) -> bool {
    let mut set_ok = false;
    for bin in ["/system/bin/setprop", "setprop"] {
        if bin.starts_with('/') && !Path::new(bin).exists() {
            continue;
        }
        if let Ok(status) = Command::new(bin).args([key, val]).status() {
            if status.success() {
                set_ok = true;
                break;
            }
        }
    }
    let mut reset_ok = false;
    for bin in resetprop_candidates() {
        if bin.starts_with('/') && !Path::new(bin).exists() {
            continue;
        }
        if let Ok(status) = Command::new(bin).args(["-p", key, val]).status() {
            if status.success() {
                reset_ok = true;
                break;
            }
        }
    }
    set_ok || reset_ok
}

fn delete_property(key: &str) -> bool {
    for bin in resetprop_candidates() {
        if bin.starts_with('/') && !Path::new(bin).exists() {
            continue;
        }
        if let Ok(status) = Command::new(bin).args(["-p", "--delete", key]).status() {
            if status.success() {
                return true;
            }
        }
    }
    false
}

fn opt_bool_json(val: Option<bool>) -> &'static str {
    match val {
        Some(true) => "true",
        Some(false) => "false",
        None => "null",
    }
}

fn render_status_json(
    input: &UsapDiscoveryInput,
    decision: &UsapDecision,
    procs: &[ZygoteProcessHealth],
    sockets: &[String],
    applied: bool,
    stage: &str,
) -> String {
    let conflicts_json = input
        .conflicts
        .iter()
        .map(|c| {
            format!(
                "{{\"module_id\":{},\"file\":{},\"reason\":{}}}",
                quote(&c.module_id),
                quote(&c.file),
                quote(&c.reason)
            )
        })
        .collect::<Vec<_>>()
        .join(",");

    let procs_json = procs
        .iter()
        .map(|p| {
            format!(
                "{{\"pid\":{},\"comm\":{},\"pipe_fds\":{},\"live_usap_children\":{},\"orphaned_pipes\":{},\"cpu_pct\":{:.1},\"spinning\":{}}}",
                p.pid,
                quote(&p.comm),
                p.pipe_fds,
                p.live_usap_children,
                p.orphaned_pipes,
                p.cpu_pct,
                p.spinning
            )
        })
        .collect::<Vec<_>>()
        .join(",");

    let sockets_json = sockets
        .iter()
        .map(|s| quote(s))
        .collect::<Vec<_>>()
        .join(",");

    format!(
        "{{\"stage\":{},\"action\":{},\"reason\":{},\"applied\":{},\"trip_circuit\":{},\"circuit_tripped\":{},\"live_spin_detected\":{},\"user_override\":{},\"rom_default_usap\":{},\"current_persist_usap\":{},\"current_dalvik_usap\":{},\"conflicts\":[{}],\"zygote_processes\":[{}],\"usap_sockets\":[{}]}}",
        quote(stage),
        quote(decision.action.as_str()),
        quote(&decision.reason),
        applied,
        decision.trip_circuit,
        input.circuit_tripped,
        input.live_spin_detected,
        opt_bool_json(input.user_override),
        opt_bool_json(input.rom_default_usap),
        opt_bool_json(input.current_persist_usap),
        opt_bool_json(input.current_dalvik_usap),
        conflicts_json,
        procs_json,
        sockets_json
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn base_input() -> UsapDiscoveryInput {
        UsapDiscoveryInput {
            user_override: None,
            circuit_tripped: false,
            live_spin_detected: false,
            legacy_migration_pending: false,
            rom_default_usap: None,
            current_persist_usap: None,
            current_dalvik_usap: None,
            conflicts: Vec::new(),
            baseline_value: None,
        }
    }

    fn temp_dir(prefix: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!(
            "{}_{}",
            prefix,
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap_or_default()
                .as_nanos()
        ));
        fs::create_dir_all(&dir).unwrap();
        dir
    }

    #[test]
    fn policy_preserves_native_rom_usap_when_safe_and_conflict_free() {
        let mut input = base_input();
        input.rom_default_usap = Some(true);
        input.current_persist_usap = Some(true);
        let d = evaluate_usap_policy(&input);
        assert_eq!(d.action, UsapAction::Preserve);
        assert_eq!(d.reason, "safe_native_preserved");
        assert!(!d.trip_circuit);
    }

    #[test]
    fn policy_aligns_stale_override_against_stock_rom_false() {
        let mut input = base_input();
        input.rom_default_usap = Some(false);
        input.current_persist_usap = Some(true);
        let d = evaluate_usap_policy(&input);
        assert_eq!(d.action, UsapAction::SetEnabled(false));
        assert_eq!(d.reason, "rom_policy_alignment");
    }

    #[test]
    fn policy_disables_usap_on_discovered_module_conflict() {
        let mut input = base_input();
        input.rom_default_usap = Some(true);
        input.current_persist_usap = Some(true);
        input.conflicts.push(ModuleConflict {
            module_id: "any_random_hider".to_string(),
            file: "service.sh".to_string(),
            reason: "usap_worker_signaling_or_namespace".to_string(),
        });
        let d = evaluate_usap_policy(&input);
        assert_eq!(d.action, UsapAction::SetEnabled(false));
        assert_eq!(d.reason, "active_module_usap_conflict");
    }

    #[test]
    fn policy_live_spin_trips_circuit_breaker_overriding_user_enable() {
        let mut input = base_input();
        input.user_override = Some(true);
        input.rom_default_usap = Some(true);
        input.current_persist_usap = Some(true);
        input.live_spin_detected = true;
        let d = evaluate_usap_policy(&input);
        assert_eq!(d.action, UsapAction::SetEnabled(false));
        assert!(d.trip_circuit);
        assert_eq!(d.reason, "live_zygote_usap_pollhup_spin_detected");
    }

    #[test]
    fn policy_legacy_migration_cleans_stale_true_back_to_unset() {
        let mut input = base_input();
        input.legacy_migration_pending = true;
        input.current_persist_usap = Some(true);
        input.baseline_value = Some(UNSET_BASELINE_MARKER.to_string());
        let d = evaluate_usap_policy(&input);
        assert_eq!(d.action, UsapAction::DeletePersistent);
        assert_eq!(d.reason, "legacy_usap_override_reconciled");
    }

    #[test]
    fn scanner_detects_usap_signal_and_ignores_comments_and_disabled_modules() {
        let root = temp_dir("lynx_zygote_modscan");

        let mod_unsafe = root.join("custom_ns_module");
        fs::create_dir_all(&mod_unsafe).unwrap();
        fs::write(
            mod_unsafe.join("service.sh"),
            "#!/system/bin/sh\n# comment\nkillall -TERM usap64 usap32 2>/dev/null\n",
        )
        .unwrap();

        let mod_disabled = root.join("disabled_hider");
        fs::create_dir_all(&mod_disabled).unwrap();
        fs::write(mod_disabled.join("disable"), "").unwrap();
        fs::write(mod_disabled.join("service.sh"), "killall -9 usap64\n").unwrap();

        let mod_benign = root.join("audio_tweak");
        fs::create_dir_all(&mod_benign).unwrap();
        fs::write(
            mod_benign.join("service.sh"),
            "# note: killall usap64 is commented out\necho ok\n",
        )
        .unwrap();

        let mod_ns = root.join("mnt_adjuster");
        fs::create_dir_all(&mod_ns).unwrap();
        fs::write(
            mod_ns.join("post-fs-data.sh"),
            "nsenter -t $(pidof zygote64) -m -- umount /data/adb\n",
        )
        .unwrap();

        let found = scan_module_conflicts_in(&root);
        let ids: Vec<&str> = found.iter().map(|c| c.module_id.as_str()).collect();
        assert_eq!(ids, vec!["custom_ns_module", "mnt_adjuster"]);

        let _ = fs::remove_dir_all(&root);
    }

    #[test]
    fn rom_prop_discovery_respects_partition_override_hierarchy() {
        let root = temp_dir("lynx_zygote_props");
        let sys = root.join("system.prop");
        let vend = root.join("vendor.prop");
        fs::write(&sys, "# stock\ndalvik.vm.usap_pool_enabled=true\n").unwrap();
        fs::write(&vend, "dalvik.vm.usap_pool_enabled=false\n").unwrap();

        let policy = discover_rom_usap_policy(&[sys, vend]);
        assert_eq!(policy.effective(), Some(false));

        let _ = fs::remove_dir_all(&root);
    }

    #[test]
    fn proc_stat_parser_handles_ppid_and_ticks() {
        let sample = "1602 (zygote64) S 1 1602 1602 0 -1 4194624 10 20 30 40 500 250 0 0 20 0";
        let (state, ppid, ticks) = parse_proc_stat(sample).unwrap();
        assert_eq!(state, 'S');
        assert_eq!(ppid, 1);
        assert_eq!(ticks, 750);
    }

    #[cfg(unix)]
    #[test]
    fn proc_health_scanner_distinguishes_healthy_vs_pollhup_spin() {
        use std::os::unix::fs::symlink;

        let proc_root = temp_dir("lynx_zygote_proc_health");
        let zdir = proc_root.join("2000");
        fs::create_dir_all(zdir.join("fd")).unwrap();
        // Verify Android real-world pattern where comm stays `main` while cmdline argv[0] is `zygote64`
        fs::write(zdir.join("comm"), "main\n").unwrap();
        fs::write(zdir.join("cmdline"), b"zygote64\0--start-system-server\0").unwrap();
        fs::write(
            zdir.join("stat"),
            "2000 (main) S 1 2000 2000 0 -1 0 0 0 0 0 100 50 0 0 20 0",
        )
        .unwrap();
        symlink("pipe:[1001]", zdir.join("fd/10")).unwrap();
        symlink("pipe:[1002]", zdir.join("fd/11")).unwrap();
        symlink("socket:[9999]", zdir.join("fd/12")).unwrap();

        // 1. Healthy matching USAP workers (mixing direct comm & main+cmdline) -> 0 orphaned pipes, not spinning.
        for (pid, name, use_cmdline) in [(2010, "usap64", true), (2011, "usap32", false)] {
            let udir = proc_root.join(pid.to_string());
            fs::create_dir_all(&udir).unwrap();
            if use_cmdline {
                fs::write(udir.join("comm"), "main\n").unwrap();
                fs::write(udir.join("cmdline"), format!("{}\0", name)).unwrap();
            } else {
                fs::write(udir.join("comm"), format!("{}\n", name)).unwrap();
            }
            fs::write(
                udir.join("stat"),
                format!("{} ({}) S 2000 {} 2000 0 -1 0 0 0 0 0 5 5 0 0 20 0", pid, name, pid),
            )
            .unwrap();
        }

        let mut samples = HashMap::new();
        let h1 = inspect_zygote_processes(&proc_root, &mut samples, false);
        assert_eq!(h1.len(), 1);
        assert_eq!(h1[0].pipe_fds, 2);
        assert_eq!(h1[0].live_usap_children, 2);
        assert_eq!(h1[0].orphaned_pipes, 0);
        assert!(!h1[0].spinning);

        // 2. Kill USAP workers while keeping CPU delta tiny -> orphaned pipes, NOT spinning yet.
        let _ = fs::remove_dir_all(proc_root.join("2010"));
        let _ = fs::remove_dir_all(proc_root.join("2011"));
        samples.insert(
            2000,
            ZygoteCpuSample {
                total_ticks: 150,
                sampled_at: Instant::now() - Duration::from_secs(2),
            },
        );
        fs::write(
            zdir.join("stat"),
            "2000 (zygote64) S 1 2000 2000 0 -1 0 0 0 0 0 101 50 0 0 20 0",
        )
        .unwrap();
        let h2 = inspect_zygote_processes(&proc_root, &mut samples, false);
        assert_eq!(h2[0].orphaned_pipes, 2);
        assert!(!h2[0].spinning);

        // 3. Orphaned pipes + 95% single-core CPU burn -> confirmed POLLHUP spin.
        samples.insert(
            2000,
            ZygoteCpuSample {
                total_ticks: 151,
                sampled_at: Instant::now() - Duration::from_secs(1),
            },
        );
        fs::write(
            zdir.join("stat"),
            "2000 (zygote64) R 1 2000 2000 0 -1 0 0 0 0 0 200 46 0 0 20 0",
        )
        .unwrap();
        let h3 = inspect_zygote_processes(&proc_root, &mut samples, false);
        assert_eq!(h3[0].orphaned_pipes, 2);
        assert!(h3[0].cpu_pct >= 70.0);
        assert!(h3[0].spinning);

        let _ = fs::remove_dir_all(&proc_root);
    }
}
