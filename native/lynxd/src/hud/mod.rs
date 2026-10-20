mod frames;
mod hardware;
mod stream;

pub use stream::run;

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
    #[cfg(target_os = "android")]
    {
        #[repr(C)]
        struct Timespec { sec: i64, nsec: i64 }
        extern "C" { fn clock_gettime(clock: i32, time: *mut Timespec) -> i32; }
        let mut time = Timespec { sec: 0, nsec: 0 };
        // CLOCK_BOOTTIME shares Android elapsedRealtime's epoch, including suspend.
        if unsafe { clock_gettime(7, &mut time) } == 0 {
            return time.sec as u64 * 1000 + time.nsec as u64 / 1_000_000;
        }
    }
    static EPOCH: std::sync::OnceLock<std::time::Instant> = std::sync::OnceLock::new();
    EPOCH.get_or_init(std::time::Instant::now).elapsed().as_millis() as u64
}
