use std::io::{BufRead, Read, Write};
use std::process::{Command, Stdio};
use std::sync::{Arc, atomic::{AtomicBool, Ordering}};
use std::time::{Duration, Instant};
use super::{now_ms, frame_now_ns, Metric, hardware::Hardware, frames::{Frames, timestamps, top_package, belongs_to, clean_layer_name, refresh_rate, unique_content_candidate}};
use crate::utils::json::quote;

/// Canonical profile normalizer for HUD stream output.
fn normalize_profile_str(p: &str) -> &'static str {
    match p.trim().to_lowercase().as_str() {
        "powersave" | "power_save" | "battery" | "hemat" => "powersave",
        "performance" | "perf" | "game" | "kinerja" => "performance",
        "extreme" | "ekstrem" => "extreme",
        "balance" | "balanced" | "seimbang" => "balance",
        "auto" => "auto",
        "dormant" => "dormant",
        _ => "balance",
    }
}

/// Resolves the current active performance profile with last-known-profile persistence.
/// Updates `last_known` in-place when a profile is successfully detected.
/// Falls back to `last_known` (not "balance") if all detection methods fail temporarily,
/// preventing glitch from empty/unreadable state files at HUD startup.
fn detect_active_profile(last_known: &mut String) -> String {
    // Priority 1: Direct atomic plaintext /data/adb/lynx/active_profile
    if let Ok(content) = std::fs::read_to_string("/data/adb/lynx/active_profile") {
        let p = content.trim();
        if !p.is_empty() {
            let norm = normalize_profile_str(p).to_string();
            *last_known = norm.clone();
            return norm;
        }
    }
    // Priority 2: Android live system property lynx.mode
    if let Ok(output) = Command::new("/system/bin/getprop").arg("lynx.mode").output() {
        let p = String::from_utf8_lossy(&output.stdout).trim().to_string();
        if !p.is_empty() && p != "0" {
            let norm = normalize_profile_str(&p).to_string();
            *last_known = norm.clone();
            return norm;
        }
    }
    // Priority 3: Persistent config.json
    for path in ["/data/adb/modules/Lynx/config.json", "/data/adb/lynx/config.json"] {
        if let Ok(content) = std::fs::read_to_string(path) {
            if let Some(pos) = content.find("\"active_profile\"") {
                if let Some(after) = content[pos..].split(':').nth(1) {
                    let cleaned = after.trim().trim_start_matches('"').split('"').next().unwrap_or("");
                    if !cleaned.is_empty() {
                        let norm = normalize_profile_str(cleaned).to_string();
                        *last_known = norm.clone();
                        return norm;
                    }
                }
            }
        }
    }
    // Priority 4: Rust ProfileStateManager active_profile.json
    if let Some(state) = crate::state::ProfileStateManager::load_active().ok().flatten() {
        if !state.dirty && state.status == "applied" && !state.active_profile.is_empty() {
            let norm = normalize_profile_str(&state.active_profile).to_string();
            *last_known = norm.clone();
            return norm;
        }
    }
    // CRITICAL: Return last_known instead of blindly defaulting to "balance".
    // This prevents the HUD from flashing "balance" during brief file unavailability
    // at startup while the profile files are being written or SELinux is resolving.
    if last_known.is_empty() {
        "balance".to_string()
    } else {
        last_known.clone()
    }
}

/// Execute one bounded system query directly; never invoke a shell pipeline.
fn query(args: &[&str]) -> Result<String, &'static str> {
    let mut child = Command::new("/system/bin/dumpsys").args(args).stdout(Stdio::piped()).stderr(Stdio::null())
        .spawn().map_err(|_| "permission_denied")?;
    let mut output = child.stdout.take().ok_or("unsupported")?;
    let reader = std::thread::spawn(move || { let mut s = String::new(); let _ = output.by_ref().take(2_000_000).read_to_string(&mut s); s });
    let start = Instant::now();
    let success = loop {
        match child.try_wait() {
            Ok(Some(s)) => break s.success(),
            Err(_) => break false,
            _ if start.elapsed() > Duration::from_millis(800) => { let _ = child.kill(); let _ = child.wait(); break false; },
            _ => std::thread::sleep(Duration::from_millis(10)),
        }
    };
    let raw = reader.join().unwrap_or_default();
    if !success || raw.contains("Permission Denial") { Err("permission_denied") } else { Ok(raw) }
}

/// Sub-millisecond, zero-fork direct kernel cgroup top app inspection.
fn detect_top_package_fast() -> Option<String> {
    let candidates = [
        "/dev/cpuset/top-app/cgroup.procs",
        "/dev/cpuset/top-app/tasks",
        "/dev/cpuset/foreground/cgroup.procs",
        "/sys/fs/cgroup/top-app/cgroup.procs",
        "/sys/fs/cgroup/cgroup.procs",
    ];
    let mut best: Option<(i32, String)> = None;
    for &node in &candidates {
        if let Ok(content) = std::fs::read_to_string(node) {
            for line in content.lines() {
                let pid_str = line.trim();
                if pid_str.is_empty() { continue; }
                let cmdline_path = format!("/proc/{}/cmdline", pid_str);
                if let Ok(bytes) = std::fs::read(&cmdline_path) {
                    if bytes.is_empty() { continue; }
                    let cmd_str = String::from_utf8_lossy(&bytes);
                    let token = cmd_str.split('\0').next().unwrap_or("").trim();
                    if token.contains('.') 
                        && !token.starts_with("android.hardware") 
                        && !token.starts_with("vendor.") 
                        && !token.ends_with(":isolated_process") 
                        && !token.ends_with(":sandboxed_process")
                        && token != "system_server" 
                        && !token.contains("inputmethod") 
                    {
                        let oom_path = format!("/proc/{}/oom_score_adj", pid_str);
                        let oom_adj = std::fs::read_to_string(&oom_path).ok().and_then(|s| s.trim().parse::<i32>().ok()).unwrap_or(999);
                        let clean = token.split(':').next().unwrap_or(token).to_string();
                        if oom_adj <= 0 {
                            return Some(clean);
                        } else if oom_adj < 200 {
                            if best.as_ref().map_or(true, |(score, _)| oom_adj < *score) {
                                best = Some((oom_adj, clean));
                            }
                        }
                    }
                }
            }
        }
    }
    best.map(|(_, pkg)| pkg)
}

pub fn run() {
    let alive = Arc::new(AtomicBool::new(true));
    let stdin_alive = alive.clone();
    std::thread::spawn(move || {
        let stdin = std::io::stdin();
        for line in stdin.lock().lines() {
            match line {
                Ok(l) if l.trim() == "stop" => {
                    stdin_alive.store(false, Ordering::Relaxed);
                    break;
                }
                Err(_) => {
                    // Stdin closed or non-interactive pipe (e.g. su -c /dev/null).
                    // Do NOT kill the collector! The collector will naturally terminate
                    // when stdout breaks (SIGPIPE / EPIPE) when the reader disconnects.
                    break;
                }
                _ => {}
            }
        }
    });
    let mut hw = Hardware::new();
    let mut last_known_profile = String::new();
    let mut frames = Frames::default();
    let mut package = String::new();
    let mut layer = String::new();
    let mut layers: Vec<String> = Vec::new();
    let mut next_layers = 0;
    let mut next_hw = 0;
    let mut next_temp = 0;
    let mut next_dumpsys_top = 0;
    let mut stdout = std::io::stdout().lock();
    while alive.load(Ordering::Relaxed) {
        let started = Instant::now();
        let now = now_ms();
        let mut target = detect_top_package_fast().unwrap_or_default();
        if target.is_empty() && now >= next_dumpsys_top {
            if let Some(t) = query(&["activity","activities"]).ok().and_then(|s| top_package(&s)) {
                target = t;
            }
            next_dumpsys_top = now + 3000;
        }
        if !target.is_empty() && target != package {
            frames.reset(); layer.clear(); layers.clear(); next_layers = 0; package = target;
        }
        let mut frame_status = "warming_up";
        let mut display_hz: Option<f64> = None;
        if package.is_empty() || package == "com.android.systemui" { frame_status = "unsupported"; }
        else {
            if layer.is_empty() && (now >= next_layers || layers.is_empty()) {
                layers = query(&["SurfaceFlinger","--list"]).unwrap_or_default().lines()
                    .filter(|s| belongs_to(s,&package)).map(clean_layer_name).take(8).collect();
                layers.sort_by(|a, b| {
                    let a_sv = a.contains("SurfaceView");
                    let b_sv = b.contains("SurfaceView");
                    b_sv.cmp(&a_sv)
                });
                next_layers = now + 4000;
            }
            let mut candidates = Vec::new();
            let probe_layers: Vec<_> = if !layer.is_empty() {
                vec![layer.clone()]
            } else { layers.iter().take(2).cloned().collect() };
            for name in &probe_layers {
                match query(&["SurfaceFlinger","--latency",name]) {
                    Ok(raw) => {
                        if let Some(hz) = refresh_rate(&raw) { display_hz = Some(hz); }
                        let ts = timestamps(&raw);
                        if let Some(last) = ts.last() {
                            let sample_now = frame_now_ns();
                            if *last <= sample_now && sample_now - last <= 1_500_000_000 { candidates.push((*last,name.clone(),ts)); }
                        }
                    },
                    Err(status) => frame_status = status,
                }
            }
            if display_hz.is_none() {
                if let Ok(raw) = query(&["SurfaceFlinger","--latency"]) {
                    display_hz = refresh_rate(&raw);
                }
            }
            if let Some((_,name,ts)) = unique_content_candidate(&candidates) {
                if *name != layer { frames.reset(); layer = name.clone(); }
                frames.ingest(ts,frame_now_ns());
                frame_status = if frames.gap { "warming_up" } else { "valid" };
            } else if !layer.is_empty() {
                // Layer is known and package is active, but producer is currently idle (e.g. static webpage, reading, paused menu)
                frames.ingest(&[], frame_now_ns());
                frame_status = "valid";
            } else {
                frames.reset(); layer.clear(); next_layers = 0;
                if frame_status != "permission_denied" { frame_status = "unsupported"; }
            }
        }
        let stamp = now_ms();
        if stamp >= next_hw {
            hw.sample(stamp, stamp>=next_temp);
            next_hw = stamp+1000;
            if stamp>=next_temp { next_temp=stamp+2000; }
        }
        let source = format!("SurfaceFlinger actualPresentTime: {layer}");
        let mut metrics = hw.metrics.clone();
        let hz_metric = display_hz.map(|hz| Metric::measured(hz, "Hz", "SurfaceFlinger refreshPeriod", stamp))
            .unwrap_or_else(|| Metric::missing("Hz", "SurfaceFlinger", stamp, "unsupported"));
        metrics.insert("display_hz".into(), hz_metric);
        let frame_stamp = frame_now_ns();
        let fresh = frame_status == "valid";
        let display_fps = if fresh {
            if frames.active(frame_stamp) {
                frames.fps(frame_stamp)
            } else if frames.initialized() && !frames.gap {
                Some(0.0) // App is active but idle on screen
            } else {
                None
            }
        } else {
            None
        };
        for (key,value,unit) in [
            ("fps", display_fps, "FPS"),
            ("frame_time", if fresh && frames.active(frame_stamp) { frames.latest() } else { None }, "ms"),
            ("frame_average", if fresh && frames.active(frame_stamp) { frames.average() } else { None }, "ms"),
            ("fps_low", if fresh && frames.active(frame_stamp) { frames.low() } else { None }, "FPS"),
        ] {
            let metric = if fresh {
                value.map(|v|Metric::measured(v,unit,&source,stamp)).unwrap_or_else(||Metric::missing(unit,&source,stamp,"warming_up"))
            } else { Metric::missing(unit,&source,stamp,frame_status) };
            metrics.insert(key.into(),metric);
        }
        let metrics_json = metrics.iter().map(|(k,v)|format!("{}:{}",quote(k),v.json())).collect::<Vec<_>>().join(",");
        let policies_json = hw.policies.iter().map(|(k,v)|format!("{{\"id\":{},\"clock\":{}}}",quote(k),v.json())).collect::<Vec<_>>().join(",");
        let history = if fresh { frames.history() } else { Vec::new() };
        let history_json = history.iter().map(|v|format!("{v:.4}")).collect::<Vec<_>>().join(",");
        let charging = hw.charging.map(|v| v.to_string()).unwrap_or("null".into());
        let profile_str = detect_active_profile(&mut last_known_profile);
        let profile = quote(&profile_str);
        if writeln!(stdout,"{{\"version\":1,\"at_ms\":{stamp},\"package\":{},\"layer\":{},\"profile\":{profile},\"charging\":{charging},\"metrics\":{{{metrics_json}}},\"policies\":[{policies_json}],\"history\":[{history_json}],\"collection_ms\":{}}}",quote(&package),quote(&layer),started.elapsed().as_millis()).is_err() { break; }
        if stdout.flush().is_err() { break; }
        let remaining = Duration::from_millis(500).saturating_sub(started.elapsed());
        std::thread::sleep(remaining);
    }
}
