mod frames;
mod hardware;
mod stream;
mod actions;

pub use stream::run;
pub use actions::release_cache;

use crate::utils::json::quote;

#[derive(Clone, Debug)]
pub struct Metric {
    pub value: Option<f64>,
    pub unit: &'static str,
    pub source: String,
    pub at_ms: u64,
    pub status: &'static str,
}

impl Metric {
    pub fn missing(unit: &'static str, source: &str, now: u64, status: &'static str) -> Self {
        Self { value: None, unit, source: source.into(), at_ms: now, status }
    }
    pub fn measured(value: f64, unit: &'static str, source: &str, now: u64) -> Self {
        Self { value: Some(value), unit, source: source.into(), at_ms: now, status: "valid" }
    }
    pub fn json(&self) -> String {
        let value = self.value.filter(|v| v.is_finite()).map(|v| format!("{v:.4}")).unwrap_or("null".into());
        format!("{{\"value\":{value},\"unit\":{},\"source\":{},\"at_ms\":{},\"status\":{}}}",
            quote(self.unit), quote(&self.source), self.at_ms, quote(self.status))
    }
}

pub fn now_ms() -> u64 {
    clock_ns(7).unwrap_or_else(fallback_ns) / 1_000_000
}

pub fn frame_now_ns() -> u64 {
    // SurfaceFlinger fences use CLOCK_MONOTONIC, which excludes deep suspend.
    clock_ns(1).unwrap_or_else(fallback_ns)
}

fn clock_ns(clock: i32) -> Option<u64> {
    #[cfg(target_os = "android")]
    {
        #[repr(C)]
        struct Timespec { sec: i64, nsec: i64 }
        extern "C" { fn clock_gettime(clock: i32, time: *mut Timespec) -> i32; }
        let mut time = Timespec { sec: 0, nsec: 0 };
        if unsafe { clock_gettime(clock, &mut time) } == 0 {
            return Some(time.sec as u64 * 1_000_000_000 + time.nsec as u64);
        }
    }
    let _ = clock;
    None
}

fn fallback_ns() -> u64 {
    static EPOCH: std::sync::OnceLock<std::time::Instant> = std::sync::OnceLock::new();
    EPOCH.get_or_init(std::time::Instant::now).elapsed().as_nanos() as u64
}
