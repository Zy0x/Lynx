use std::io::{BufRead, Read, Write};
use std::process::{Command, Stdio};
use std::sync::{Arc, atomic::{AtomicBool, Ordering}};
use std::time::{Duration, Instant};
use super::{now_ms, Metric, hardware::Hardware, frames::{Frames, timestamps, top_package, belongs_to}};
use crate::utils::json::quote;

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

pub fn run() {
    let alive = Arc::new(AtomicBool::new(true));
    let stdin_alive = alive.clone();
    std::thread::spawn(move || {
        // Closing the owning service's pipe terminates the read-only collector.
        for line in std::io::stdin().lock().lines() {
            if line.is_err() || line.unwrap_or_default().trim() == "stop" { break; }
        }
        stdin_alive.store(false, Ordering::Relaxed);
    });
    let mut hw = Hardware::new();
    let mut frames = Frames::default();
    let mut package = String::new();
    let mut layer = String::new();
    let mut layers: Vec<String> = Vec::new();
    let mut next_layers = 0;
    let mut next_hw = 0;
    let mut next_temp = 0;
    let mut stdout = std::io::stdout().lock();
    while alive.load(Ordering::Relaxed) {
        let started = Instant::now();
        let now = now_ms();
        let target = query(&["activity","activities"]).ok().and_then(|s| top_package(&s)).unwrap_or_default();
        if target != package {
            frames.reset(); layer.clear(); layers.clear(); next_layers = 0; package = target;
        }
        let mut frame_status = "warming_up";
        if package.is_empty() || package == "com.android.systemui" { frame_status = "unsupported"; }
        else {
            if now >= next_layers {
                layers = query(&["SurfaceFlinger","--list"]).unwrap_or_default().lines()
                    .filter(|s| belongs_to(s,&package)).take(12).map(str::to_owned).collect();
                next_layers = now + 2000;
            }
            let mut candidates = Vec::new();
            let probe_layers: Vec<_> = if !layer.is_empty() && now + 500 < next_layers {
                vec![layer.clone()]
            } else { layers.iter().take(4).cloned().collect() };
            for name in &probe_layers {
                match query(&["SurfaceFlinger","--latency",name]) {
                    Ok(raw) => { let ts = timestamps(&raw); if let Some(last) = ts.last() {
                        let sample_now = now_ms()*1_000_000;
                        if *last <= sample_now && sample_now - last <= 1_500_000_000 { candidates.push((*last,name.clone(),ts)); }
                    } },
                    Err(status) => frame_status = status,
                }
            }
            candidates.sort_by(|a,b| b.0.cmp(&a.0));
            // A SurfaceView with submitted frames takes precedence over its activity container.
            if candidates.iter().any(|c|c.1.contains("SurfaceView")) { candidates.retain(|c|c.1.contains("SurfaceView")); }
            if candidates.len()>1 && candidates[0].0 == candidates[1].0 {
                frames.reset(); layer.clear(); frame_status = "unsupported";
            } else if let Some((_,name,ts)) = candidates.first() {
                if *name != layer { frames.reset(); layer = name.clone(); }
                frames.ingest(ts,now_ms()*1_000_000);
                frame_status = if frames.gap { "warming_up" } else { "valid" };
            } else { frames.reset(); layer.clear(); if frame_status != "permission_denied" { frame_status = "unsupported"; } }
        }
        let stamp = now_ms();
        if stamp >= next_hw {
            hw.sample(stamp, stamp>=next_temp);
            next_hw = stamp+1000;
            if stamp>=next_temp { next_temp=stamp+2000; }
        }
        let source = format!("SurfaceFlinger actualPresentTime: {layer}");
        let mut metrics = hw.metrics.clone();
        let fresh = frame_status == "valid" && frames.active(stamp*1_000_000);
        for (key,value,unit) in [
            ("fps",frames.fps(stamp*1_000_000),"FPS"),
            ("frame_time",frames.latest(),"ms"),
            ("frame_average",frames.average(),"ms"),
            ("fps_low",frames.low(),"FPS"),
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
        let profile = std::fs::read_to_string("/data/adb/lynx/active_profile").ok()
            .map(|s|quote(s.trim())).unwrap_or("null".into());
        if writeln!(stdout,"{{\"version\":1,\"at_ms\":{stamp},\"package\":{},\"layer\":{},\"profile\":{profile},\"charging\":{charging},\"metrics\":{{{metrics_json}}},\"policies\":[{policies_json}],\"history\":[{history_json}],\"collection_ms\":{}}}",quote(&package),quote(&layer),started.elapsed().as_millis()).is_err() { break; }
        if stdout.flush().is_err() { break; }
        let remaining = Duration::from_millis(500).saturating_sub(started.elapsed());
        std::thread::sleep(remaining);
    }
}
