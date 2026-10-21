use std::collections::VecDeque;

/// Extract the refresh rate (Hz) from the first line of SurfaceFlinger latency dump.
pub fn refresh_rate(raw: &str) -> Option<f64> {
    let first = raw.lines().next()?.trim();
    let period_ns = first.parse::<f64>().ok().filter(|v| *v > 0.0)?;
    let hz = 1_000_000_000.0 / period_ns;
    if (20.0..=360.0).contains(&hz) {
        Some(hz)
    } else {
        None
    }
}

/// Clean Android 14/15 SurfaceFlinger layer format:
/// "RequestedLayerState{8ceb8ea com.noir.lynx/com.noir.lynx.ui.MainActivity#2152 parentId=2143}"
/// into: "8ceb8ea com.noir.lynx/com.noir.lynx.ui.MainActivity#2152"
pub fn clean_layer_name(raw: &str) -> String {
    let mut s = raw.trim();
    if let Some(stripped) = s.strip_prefix("RequestedLayerState{") {
        s = stripped;
    } else if let Some(stripped) = s.strip_prefix("Layer{") {
        s = stripped;
    }
    for marker in [" parentId=", " relativeParentId=", " z=", " !handle"] {
        if let Some(idx) = s.find(marker) {
            s = &s[..idx];
        }
    }
    s = s.trim_end_matches('}').trim();
    s.to_string()
}

/// Parse actual-present timestamps (column two), excluding unresolved fences.
pub fn timestamps(raw: &str) -> Vec<u64> {
    let mut values: Vec<u64> = raw.lines().filter_map(|line| {
        let cols: Vec<_> = line.split_whitespace().collect();
        if cols.len() != 3 { return None; }
        cols[1].parse::<u64>().ok().filter(|v| *v > 0 && *v < i64::MAX as u64)
    }).collect();
    values.sort_unstable();
    values.dedup();
    values
}

#[derive(Default)]
pub struct Frames {
    last: u64,
    epoch: u64,
    points: VecDeque<u64>,
    intervals: VecDeque<f64>,
    pub gap: bool,
}

impl Frames {
    pub fn reset(&mut self) { *self = Self::default(); }

    pub fn ingest(&mut self, times: &[u64], now_ns: u64) {
        self.gap = false;
        if times.is_empty() { return; }
        // No overlap means the producer wrapped its ring while we were away.
        if self.last > 0 && times[0] > self.last {
            self.reset();
            self.gap = true;
        }
        for &time in times {
            if time <= self.last || time > now_ns { continue; }
            if self.epoch == 0 { self.epoch = time; }
            if self.last > 0 {
                self.intervals.push_back((time - self.last) as f64 / 1_000_000.0);
                while self.intervals.len() > 6000 { self.intervals.pop_front(); }
            }
            self.points.push_back(time);
            self.last = time;
        }
        while self.points.front().map_or(false, |v| now_ns.saturating_sub(*v) >= 1_000_000_000) {
            self.points.pop_front();
        }
    }

    pub fn active(&self, now_ns: u64) -> bool {
        self.last > 0 && now_ns.saturating_sub(self.last) <= 1_500_000_000
    }
    pub fn fps(&self, now_ns: u64) -> Option<f64> {
        if !self.active(now_ns) || self.gap || now_ns.saturating_sub(self.epoch) < 1_000_000_000 { return None; }
        Some(self.points.len() as f64)
    }
    pub fn latest(&self) -> Option<f64> { self.intervals.back().copied() }
    pub fn average(&self) -> Option<f64> {
        if self.intervals.is_empty() { None } else { Some(self.intervals.iter().sum::<f64>() / self.intervals.len() as f64) }
    }
    pub fn low(&self) -> Option<f64> {
        if self.intervals.len() < 15 || self.gap { return None; }
        let mut sorted: Vec<_> = self.intervals.iter().copied().collect();
        sorted.sort_by(|a,b| b.total_cmp(a));
        let n = (sorted.len() as f64 * 0.01).ceil() as usize;
        Some(1000.0 / (sorted[..n].iter().sum::<f64>() / n as f64))
    }
    pub fn history(&self) -> Vec<f64> {
        let vals: Vec<_> = self.intervals.iter().rev().take(240).copied().collect::<Vec<_>>().into_iter().rev().collect();
        vals.chunks(4).map(|c| c.iter().sum::<f64>() / c.len() as f64).collect()
    }
}

pub fn top_package(raw: &str) -> Option<String> {
    let line = raw.lines().find(|s| s.contains("topResumedActivity"))
        .or_else(|| raw.lines().find(|s| s.contains("mResumedActivity")))?;
    line.split_whitespace().find_map(|s| {
        let pkg = s.split('/').next()?;
        if s.contains('/') && pkg.contains('.') && pkg.chars().all(|c| c.is_ascii_alphanumeric() || c == '_' || c == '.') {
            Some(pkg.to_owned())
        } else { None }
    })
}

pub fn belongs_to(layer: &str, pkg: &str) -> bool {
    if [
        "Snapshot", "Starting ", "ScreenDecor", "LynxFloatingHud", "Toast",
        "InputSink", "Background for", "Bounds for", "ActivityRecord{"
    ].iter().any(|s| layer.contains(s)) { return false; }
    layer.match_indices(pkg).any(|(i, _)| {
        let end = i + pkg.len();
        let boundary = |c: char| !c.is_ascii_alphanumeric() && c != '_' && c != '.';
        (i == 0 || layer[..i].chars().last().map_or(false, boundary)) &&
            (end == layer.len() || layer[end..].chars().next().map_or(false, boundary))
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn parses_only_actual_unique_resolved_frames() {
        assert_eq!(timestamps("8333333\n1 22 8\n2 22 9\n0 0 0\n1 9223372036854775807 3\n"), vec![22]);
    }
    #[test]
    fn parses_refresh_rate_from_latency_header() {
        assert!((refresh_rate("16666666\n0 0 0").unwrap() - 60.0).abs() < 0.1);
        assert!((refresh_rate("11111111\n0 0 0").unwrap() - 90.0).abs() < 0.1);
        assert!((refresh_rate("8333333\n0 0 0").unwrap() - 120.0).abs() < 0.1);
        assert_eq!(refresh_rate("0\n0 0 0"), None);
    }
    #[test]
    fn cleans_android_14_15_layer_names() {
        assert_eq!(
            clean_layer_name("RequestedLayerState{8ceb8ea com.noir.lynx/com.noir.lynx.ui.MainActivity#2152 parentId=2143}"),
            "8ceb8ea com.noir.lynx/com.noir.lynx.ui.MainActivity#2152"
        );
        assert_eq!(
            clean_layer_name("RequestedLayerState{com.android.chrome/com.google.android.apps.chrome.Main#2251 parentId=2202}"),
            "com.android.chrome/com.google.android.apps.chrome.Main#2251"
        );
        assert_eq!(
            clean_layer_name("com.android.chrome/com.google.android.apps.chrome.Main#0"),
            "com.android.chrome/com.google.android.apps.chrome.Main#0"
        );
    }
    #[test]
    fn fixed_cadences_and_deduplication() {
        for hz in [30,60,90,120] {
            let mut frames = Frames::default();
            let times: Vec<_> = (1..=hz*2).map(|i| 1_000_000_000 + i as u64 * 1_000_000_000 / hz as u64).collect();
            let now = *times.last().unwrap();
            frames.ingest(&times, now);
            let fps = frames.fps(now).unwrap();
            assert!((fps - hz as f64).abs() <= 1.0);
            frames.ingest(&times, now);
            assert_eq!(fps, frames.fps(now).unwrap());
            assert!((frames.latest().unwrap() - 1000.0 / hz as f64).abs() < 0.001);
        }
    }
    #[test]
    fn gaps_and_stale_data_are_unavailable() {
        let mut f = Frames::default();
        f.ingest(&[1_000_000_000, 1_010_000_000], 1_010_000_000);
        f.ingest(&[2_000_000_000, 2_010_000_000], 2_010_000_000);
        assert!(f.gap);
        assert_eq!(f.fps(2_010_000_000), None);
        assert!(!f.active(4_000_000_000));
    }
    #[test]
    fn low_uses_slowest_one_percent_not_fps_percentiles() {
        let mut f = Frames::default();
        let mut t = 1_000_000_000;
        let mut times = vec![t];
        for i in 0..100 { t += if i == 99 { 100_000_000 } else { 10_000_000 }; times.push(t); }
        f.ingest(&times, t);
        assert_eq!(f.low(), Some(10.0));
    }
    #[test]
    fn foreground_and_layer_identity_are_exact() {
        assert_eq!(top_package("topResumedActivity=ActivityRecord{abc u0 com.game.test/.Main t3}"), Some("com.game.test".into()));
        assert!(belongs_to("SurfaceView[com.game.test/.Main](BLAST)#9", "com.game.test"));
        assert!(!belongs_to("SurfaceView[com.game.testing/.Main]", "com.game.test"));
        assert!(!belongs_to("Snapshot com.game.test", "com.game.test"));
    }
}
