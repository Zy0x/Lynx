use std::fmt::Write as FmtWrite;
use std::fs;
use std::path::Path;
use std::time::Instant;

use crate::hardware::capability::{BlockDeviceType, GpuDriver, HardwareCapability};
use crate::hardware::detector::Vendor;
use crate::hardware::{HardwareTranslator, MtkHardware};
use crate::profile::get_profile_by_name;
use crate::state::ProfileStateManager;
use crate::sysfs::SysfsReader;

#[derive(Debug, Clone)]
pub struct AuditItem {
    pub subsystem: &'static str,
    pub parameter: String,
    pub path: String,
    pub expected: String,
    pub actual: String,
    pub status: &'static str,
}

pub struct ProfileVerifier;

impl ProfileVerifier {
    pub fn resolve_active_profile_name(requested: Option<&str>) -> String {
        if let Some(req) = requested {
            if !req.trim().is_empty() {
                return match req.trim().to_ascii_lowercase().as_str() {
                    "perf" | "performance" => "performance".to_string(),
                    "ext" | "extreme" => "extreme".to_string(),
                    "ps" | "powersave" => "powersave".to_string(),
                    _ => "balanced".to_string(),
                };
            }
        }
        if let Ok(Some(state)) = ProfileStateManager::load_active() {
            return state.active_profile;
        }
        if let Ok(s) = SysfsReader::read_trimmed("/data/adb/lynx/active_profile") {
            if !s.is_empty() {
                return if s == "balance" { "balanced".to_string() } else { s };
            }
        }
        "balanced".to_string()
    }

    pub fn verify(requested: Option<&str>, json_only: bool) -> i32 {
        let start = Instant::now();
        let prof_name = Self::resolve_active_profile_name(requested);
        let profile = match get_profile_by_name(&prof_name) {
            Some(p) => p,
            None => get_profile_by_name("balanced").unwrap(),
        };

        let cap = HardwareCapability::resolve();
        let is_perf_or_ext = matches!(profile.name, "extreme" | "performance");
        let is_extreme = profile.name == "extreme";

        let mut items = Vec::new();

        // 1. Audit CPU Policies
        for pol in &cap.cpu.policies {
            let gov_str = profile.cpu.governor.as_str();
            let expected_gov = if is_extreme && pol.available_governors.iter().any(|g| g == "performance") {
                "performance"
            } else if pol.available_governors.iter().any(|g| g == gov_str) {
                gov_str
            } else {
                pol.current_governor.as_str()
            };

            let gov_path = pol.path.join("scaling_governor");
            let actual_gov = SysfsReader::read_trimmed(&gov_path).unwrap_or_else(|_| "unknown".to_string());
            let gov_status = if actual_gov == expected_gov {
                "VERIFIED"
            } else if pol.available_governors.contains(&actual_gov) {
                "FALLBACK"
            } else {
                "MISMATCH"
            };
            items.push(AuditItem {
                subsystem: "CPU",
                parameter: format!("Policy {} ({}) Governor", pol.index, pol.role),
                path: gov_path.display().to_string(),
                expected: expected_gov.to_string(),
                actual: actual_gov,
                status: gov_status,
            });

            let target_min = HardwareTranslator::snap_frequency(
                pol.min_freq,
                pol.max_freq,
                profile.cpu.min_percent,
                &pol.available_freqs,
                true,
            );
            let target_max = HardwareTranslator::snap_frequency(
                pol.min_freq,
                pol.max_freq,
                profile.cpu.max_percent,
                &pol.available_freqs,
                false,
            );

            let min_path = pol.path.join("scaling_min_freq");
            let actual_min = SysfsReader::read_int::<u64>(&min_path).unwrap_or(pol.current_min_freq);
            let min_status = if actual_min == target_min || (expected_gov == "performance" && actual_min == target_max) {
                "VERIFIED"
            } else if pol.available_freqs.contains(&actual_min) {
                "CLAMPED"
            } else {
                "MISMATCH"
            };
            items.push(AuditItem {
                subsystem: "CPU",
                parameter: format!("Policy {} Min Freq", pol.index),
                path: min_path.display().to_string(),
                expected: target_min.to_string(),
                actual: actual_min.to_string(),
                status: min_status,
            });

            let max_path = pol.path.join("scaling_max_freq");
            let actual_max = SysfsReader::read_int::<u64>(&max_path).unwrap_or(pol.current_max_freq);
            let max_status = if actual_max == target_max {
                "VERIFIED"
            } else if pol.available_freqs.contains(&actual_max) {
                "CLAMPED"
            } else {
                "MISMATCH"
            };
            items.push(AuditItem {
                subsystem: "CPU",
                parameter: format!("Policy {} Max Freq", pol.index),
                path: max_path.display().to_string(),
                expected: target_max.to_string(),
                actual: actual_max.to_string(),
                status: max_status,
            });
        }

        // 2. Audit Vendor & GPU
        if cap.vendor == Vendor::MediaTek {
            Self::audit_exact(
                &mut items,
                "MTK",
                "CPUFreq Power Mode",
                MtkHardware::CPUFREQ_POWER_MODE,
                match profile.name {
                    "extreme" | "performance" => "3",
                    "balanced" => "1",
                    _ => "0",
                },
            );
            Self::audit_exact(
                &mut items,
                "MTK",
                "CCI Interconnect Mode",
                MtkHardware::CPUFREQ_CCI_MODE,
                if is_perf_or_ext { "1" } else { "0" },
            );
            Self::audit_exact(
                &mut items,
                "GPU",
                "GED Boost GPU Enable",
                "/sys/module/ged/parameters/boost_gpu_enable",
                if is_perf_or_ext { "1" } else { "0" },
            );
        } else if cap.vendor == Vendor::Qualcomm && cap.gpu.driver == GpuDriver::QcomKgsl {
            Self::audit_exact(
                &mut items,
                "GPU",
                "Adreno KGSL Throttling",
                "/sys/class/kgsl/kgsl-3d0/throttling",
                if is_perf_or_ext { "0" } else { "1" },
            );
        }

        // 3. Audit Scheduler & Memory
        if let Some(ref uclamp_min) = cap.scheduler.uclamp_min_node {
            let expected_uclamp = ((profile.scheduler.uclamp_min_percent as u32 * 1024) / 100).to_string();
            Self::audit_exact(
                &mut items,
                "Scheduler",
                "EAS UClamp Min Floor",
                &uclamp_min.display().to_string(),
                &expected_uclamp,
            );
        }

        if cap.memory.mglru_supported {
            if let Ok(actual) = SysfsReader::read_trimmed("/sys/kernel/mm/lru_gen/enabled") {
                let ok = !actual.is_empty() && actual != "0" && actual != "n";
                items.push(AuditItem {
                    subsystem: "Memory",
                    parameter: "Linux MGLRU Status".to_string(),
                    path: "/sys/kernel/mm/lru_gen/enabled".to_string(),
                    expected: "enabled (>0)".to_string(),
                    actual,
                    status: if ok { "VERIFIED" } else { "MISMATCH" },
                });
            }
        }

        // 4. Audit Storage I/O
        for dev in &cap.storage.devices {
            if dev.device_type == BlockDeviceType::Virtual {
                continue;
            }
            let iostats_path = dev.queue_path.join("iostats");
            Self::audit_exact(
                &mut items,
                "Storage",
                &format!("Block {} iostats", dev.name),
                &iostats_path.display().to_string(),
                "0",
            );
        }

        // 5. Audit Network TCP
        if !cap.network.best_tcp_algo.is_empty() {
            Self::audit_exact(
                &mut items,
                "Network",
                "TCP Congestion Control",
                "/proc/sys/net/ipv4/tcp_congestion_control",
                &cap.network.best_tcp_algo,
            );
        }

        let elapsed_us = start.elapsed().as_micros();
        let verified_count = items.iter().filter(|i| i.status == "VERIFIED").count();
        let clamped_count = items.iter().filter(|i| i.status == "CLAMPED").count();
        let fallback_count = items.iter().filter(|i| i.status == "FALLBACK").count();
        let mismatch_count = items.iter().filter(|i| i.status == "MISMATCH").count();

        let json_report = Self::format_json(
            profile.name,
            &cap.vendor.to_string(),
            elapsed_us,
            verified_count,
            clamped_count,
            fallback_count,
            mismatch_count,
            &items,
        );

        let _ = fs::create_dir_all("/data/adb/lynx");
        let _ = fs::write("/data/adb/lynx/profile_audit.json", &json_report);
        if Path::new("/storage/emulated/0/Debug").is_dir() {
            let _ = fs::write("/storage/emulated/0/Debug/profile_audit.json", &json_report);
        }

        if json_only {
            println!("{}", json_report);
        } else {
            println!("==========================================================================");
            println!(
                " LYNX NATIVE PROFILE VERIFIER v0.3.0 | Profile: {} | SoC: {} | {:.2} ms",
                profile.name.to_uppercase(),
                cap.vendor,
                elapsed_us as f64 / 1000.0
            );
            println!("==========================================================================");
            for item in &items {
                println!(
                    " [{:<8}] {:<10} | {:<28} | exp: {:<10} | cur: {}",
                    item.status, item.subsystem, item.parameter, item.expected, item.actual
                );
            }
            println!("--------------------------------------------------------------------------");
            println!(
                " Summary: Total={} | Verified={} | Clamped={} | Fallback={} | Mismatch={}",
                items.len(),
                verified_count,
                clamped_count,
                fallback_count,
                mismatch_count
            );
            println!("==========================================================================");
        }

        if mismatch_count == 0 { 0 } else { 1 }
    }

    fn audit_exact(
        items: &mut Vec<AuditItem>,
        subsystem: &'static str,
        param: &str,
        path: &str,
        expected: &str,
    ) {
        if !Path::new(path).exists() {
            return;
        }
        let actual = SysfsReader::read_trimmed(path).unwrap_or_default();
        let lower = actual.to_lowercase();
        let status = if actual == expected
            || actual.contains(&format!("[{}]", expected))
            || actual.ends_with(&format!(" {}", expected))
            || actual.ends_with(&format!(":{}", expected))
            || (expected == "0" && lower.contains("normal"))
            || (expected == "1" && (lower.contains("low power") || lower.contains("performance")))
            || (expected == "3" && lower.contains("performance"))
        {
            "VERIFIED"
        } else {
            "CLAMPED"
        };
        items.push(AuditItem {
            subsystem,
            parameter: param.to_string(),
            path: path.to_string(),
            expected: expected.to_string(),
            actual,
            status,
        });
    }

    #[allow(clippy::too_many_arguments)]
    fn format_json(
        profile: &str,
        soc: &str,
        elapsed_us: u128,
        verified: usize,
        clamped: usize,
        fallback: usize,
        mismatch: usize,
        items: &[AuditItem],
    ) -> String {
        let mut out = String::new();
        let _ = write!(
            out,
            "{{\"profile\":\"{}\",\"soc\":\"{}\",\"elapsed_us\":{},\"total\":{},\"verified\":{},\"clamped\":{},\"fallback\":{},\"errors\":{},\"items\":[",
            profile,
            soc,
            elapsed_us,
            items.len(),
            verified,
            clamped,
            fallback,
            mismatch
        );
        for (idx, it) in items.iter().enumerate() {
            if idx > 0 {
                out.push(',');
            }
            let safe_actual = it.actual.replace('\\', "\\\\").replace('"', "\\\"");
            let safe_exp = it.expected.replace('\\', "\\\\").replace('"', "\\\"");
            let _ = write!(
                out,
                "{{\"subsystem\":\"{}\",\"parameter\":\"{}\",\"path\":\"{}\",\"expected\":\"{}\",\"actual\":\"{}\",\"status\":\"{}\"}}",
                it.subsystem, it.parameter, it.path, safe_exp, safe_actual, it.status
            );
        }
        out.push_str("]}");
        out
    }
}
