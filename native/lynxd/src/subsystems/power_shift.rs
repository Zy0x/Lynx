use std::sync::atomic::{AtomicBool, Ordering};

static POWER_SHIFT_ACTIVE: AtomicBool = AtomicBool::new(false);

#[derive(Debug, Clone)]
pub struct PowerShiftDecision {
    pub is_gpu_bound: bool,
    pub is_cpu_bound: bool,
    pub cpu_load_pct: u32,
    pub gpu_load_pct: u32,
    pub recommended_cpu_ceiling_pct: u32,
    pub power_saved_watts_est: f32,
}

pub struct PowerShiftController;

impl PowerShiftController {
    pub fn is_enabled() -> bool {
        POWER_SHIFT_ACTIVE.load(Ordering::Relaxed)
    }

    pub fn set_enabled(enabled: bool) {
        POWER_SHIFT_ACTIVE.store(enabled, Ordering::Relaxed);
    }

    /// Evaluates current CPU and GPU loads and computes dynamic thermal power shifting.
    pub fn evaluate(cpu_load_pct: u32, gpu_load_pct: u32) -> PowerShiftDecision {
        let is_gpu_bound = gpu_load_pct >= 85 && cpu_load_pct <= 55;
        let is_cpu_bound = cpu_load_pct >= 85 && gpu_load_pct <= 40;

        let (recommended_cpu_ceiling_pct, power_saved_watts_est) = if is_gpu_bound {
            // Cap Big cores to 70% OPP sweet-spot: saves ~1.8W thermal budget for GPU
            (70, 1.8)
        } else if is_cpu_bound {
            // Unrestricted CPU power
            (100, 0.0)
        } else {
            // Balanced
            (85, 0.5)
        };

        PowerShiftDecision {
            is_gpu_bound,
            is_cpu_bound,
            cpu_load_pct,
            gpu_load_pct,
            recommended_cpu_ceiling_pct,
            power_saved_watts_est,
        }
    }
}
