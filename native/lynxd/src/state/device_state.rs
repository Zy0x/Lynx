use std::fmt::Write as FmtWrite;
use std::fs;
use std::path::Path;
use std::time::{SystemTime, UNIX_EPOCH};

use crate::daemon::DaemonLifecycle;
use crate::hardware::capability::HardwareCapability;
use crate::hardware::{ScoreBoard, Vendor};
use crate::sysfs::SysfsReader;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ThermalLevel {
    Normal,     // < 38°C
    Warm,       // 38°C - 43°C
    Hot,        // 43°C - 48°C
    Throttling, // > 48°C
}

impl ThermalLevel {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Normal => "Normal",
            Self::Warm => "Warm",
            Self::Hot => "Hot",
            Self::Throttling => "Throttling",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WorkloadLevel {
    Idle,     // GPU < 20% and CPU low
    Moderate, // GPU 20% - 65%
    Heavy,    // GPU > 65% or sustained maximum CPU
}

impl WorkloadLevel {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Idle => "Idle",
            Self::Moderate => "Moderate",
            Self::Heavy => "Heavy",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PowerState {
    Discharging,
    ChargingStandard, // <= 1500 mA
    FastCharging,     // > 1500 mA
    Full,
}

impl PowerState {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Discharging => "Discharging",
            Self::ChargingStandard => "ChargingStandard",
            Self::FastCharging => "FastCharging",
            Self::Full => "Full",
        }
    }
}

#[derive(Debug, Clone, Default)]
pub struct DeviceHealth {
    pub cpu_available: bool,
    pub gpu_available: bool,
    pub battery_available: bool,
    pub memory_available: bool,
}

// ── RAW HARDWARE READINGS ───────────────────────────────────────────────────

#[derive(Debug, Clone, Default)]
pub struct CpuRaw {
    pub freqs: Vec<u64>,
    pub governors: Vec<String>,
}

#[derive(Debug, Clone, Default)]
pub struct GpuRaw {
    pub freq_mhz: u32,
    pub busy_percent: u32,
    pub vendor_name: String,
    pub cur_governor: String,
    pub boost_level: u32,
    pub available_governors: Vec<String>,
}

#[derive(Debug, Clone, Default)]
pub struct ThermalZoneEntry {
    pub id: u32,
    pub zone_type: String,
    pub temp_c: f32,
}

#[derive(Debug, Clone)]
pub struct BatteryRaw {
    pub capacity: u32,
    pub temp_raw: i32,
    pub current_ma: i32,
    pub volt_mv: u32,
    pub status: String,
}

impl Default for BatteryRaw {
    fn default() -> Self {
        Self {
            capacity: 50,
            temp_raw: 350,
            current_ma: 0,
            volt_mv: 4000,
            status: "Discharging".to_string(),
        }
    }
}

#[derive(Debug, Clone, Default)]
pub struct MemoryRaw {
    pub ram_total_kb: u64,
    pub ram_avail_kb: u64,
    pub swap_total_kb: u64,
    pub swap_free_kb: u64,
    pub zram_total_kb: u64,
    pub zram_used_kb: u64,
}

#[derive(Debug, Clone, Default)]
pub struct RawState {
    pub cpu: CpuRaw,
    pub gpu: GpuRaw,
    pub battery: BatteryRaw,
    pub memory: MemoryRaw,
    pub thermal_zones: Vec<ThermalZoneEntry>,
}

// ── DERIVED STATE FOR DECISION ENGINE ───────────────────────────────────────

#[derive(Debug, Clone)]
pub struct DerivedState {
    pub thermal_level: ThermalLevel,
    pub workload_level: WorkloadLevel,
    pub power_state: PowerState,
    pub temp_celsius: String,
    pub temp_celsius_float: f32,
    pub ram_used_mb: u64,
    pub ram_total_mb: u64,
    pub ram_usage_percent: f32,
    pub zram_used_mb: u64,
    pub zram_total_mb: u64,
    pub swap_used_mb: u64,
    pub swap_total_mb: u64,
}

impl Default for DerivedState {
    fn default() -> Self {
        Self {
            thermal_level: ThermalLevel::Normal,
            workload_level: WorkloadLevel::Idle,
            power_state: PowerState::Discharging,
            temp_celsius: "35.0".to_string(),
            temp_celsius_float: 35.0,
            ram_used_mb: 2048,
            ram_total_mb: 4096,
            ram_usage_percent: 50.0,
            zram_used_mb: 0,
            zram_total_mb: 0,
            swap_used_mb: 0,
            swap_total_mb: 0,
        }
    }
}

// ── UNIFIED DEVICE STATE ────────────────────────────────────────────────────

#[derive(Debug, Clone, Default)]
pub struct DeviceState {
    pub timestamp: u64,
    pub health: DeviceHealth,
    pub raw: RawState,
    pub derived: DerivedState,
}

impl DeviceState {
    /// Captures the complete live state of the device directly from Linux/Android kernel sysfs nodes.
    /// Operates with zero forks, executing in under 2 milliseconds.
    pub fn capture() -> Self {
        let timestamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        let (vendor, _) = ScoreBoard::evaluate();

        let (cpu, cpu_ok) = Self::capture_cpu();
        let (gpu, gpu_ok) = Self::capture_gpu(vendor);
        let (battery, battery_ok) = Self::capture_battery();
        let (memory, memory_ok) = Self::capture_memory();
        let thermal_zones = Self::capture_thermal_zones();

        let health = DeviceHealth {
            cpu_available: cpu_ok,
            gpu_available: gpu_ok,
            battery_available: battery_ok,
            memory_available: memory_ok,
        };

        let raw = RawState {
            cpu,
            gpu,
            battery,
            memory,
            thermal_zones,
        };

        let derived = Self::derive_state(&raw);

        Self {
            timestamp,
            health,
            raw,
            derived,
        }
    }

    fn derive_state(raw: &RawState) -> DerivedState {
        // Temperature derivation
        let temp_float = if raw.battery.temp_raw > 1000 {
            raw.battery.temp_raw as f32 / 1000.0
        } else if raw.battery.temp_raw > 100 {
            raw.battery.temp_raw as f32 / 10.0
        } else {
            raw.battery.temp_raw as f32
        };

        let temp_celsius = format!("{:.1}", temp_float);

        let thermal_level = if temp_float >= 48.0 {
            ThermalLevel::Throttling
        } else if temp_float >= 43.0 {
            ThermalLevel::Hot
        } else if temp_float >= 38.0 {
            ThermalLevel::Warm
        } else {
            ThermalLevel::Normal
        };

        // Workload derivation
        let workload_level = if raw.gpu.busy_percent > 65 {
            WorkloadLevel::Heavy
        } else if raw.gpu.busy_percent >= 20 {
            WorkloadLevel::Moderate
        } else {
            WorkloadLevel::Idle
        };

        // Power state derivation
        let power_state = if raw.battery.status.eq_ignore_ascii_case("Full") {
            PowerState::Full
        } else if raw.battery.current_ma > 1500 {
            PowerState::FastCharging
        } else if raw.battery.current_ma > 0 {
            PowerState::ChargingStandard
        } else {
            PowerState::Discharging
        };

        // Memory calculations
        let ram_total_mb = raw.memory.ram_total_kb / 1024;
        let ram_used_mb = raw.memory.ram_total_kb.saturating_sub(raw.memory.ram_avail_kb) / 1024;
        let ram_usage_percent = if ram_total_mb > 0 {
            (ram_used_mb as f32 / ram_total_mb as f32) * 100.0
        } else {
            0.0
        };

        let zram_total_mb = raw.memory.zram_total_kb / 1024;
        let zram_used_mb = raw.memory.zram_used_kb / 1024;
        let swap_total_mb = raw.memory.swap_total_kb / 1024;
        let swap_used_mb = raw.memory.swap_total_kb.saturating_sub(raw.memory.swap_free_kb) / 1024;

        DerivedState {
            thermal_level,
            workload_level,
            power_state,
            temp_celsius,
            temp_celsius_float: temp_float,
            ram_used_mb,
            ram_total_mb,
            ram_usage_percent,
            zram_used_mb,
            zram_total_mb,
            swap_used_mb,
            swap_total_mb,
        }
    }

    fn capture_cpu() -> (CpuRaw, bool) {
        let mut freqs = Vec::with_capacity(8);
        let mut governors = Vec::with_capacity(4);
        let mut found_any = false;

        for idx in 0..16 {
            let cpu_dir = format!("/sys/devices/system/cpu/cpu{}", idx);
            if !Path::new(&cpu_dir).exists() {
                continue;
            }

            let online = SysfsReader::read_int::<u32>(format!("{}/online", cpu_dir)).unwrap_or(1);
            if online == 0 {
                freqs.push(0);
                found_any = true;
            } else {
                let cur_freq_node = format!("{}/cpufreq/scaling_cur_freq", cpu_dir);
                let freq = SysfsReader::read_int::<u64>(&cur_freq_node)
                    .or_else(|_| SysfsReader::read_int::<u64>(format!("{}/cpufreq/cpuinfo_cur_freq", cpu_dir)))
                    .unwrap_or(0);
                freqs.push(freq);
                found_any = true;
            }

            let gov_node = format!("/sys/devices/system/cpu/cpufreq/policy{}/scaling_governor", idx);
            if let Ok(gov) = SysfsReader::read_trimmed(&gov_node) {
                if !governors.contains(&gov) {
                    governors.push(gov);
                }
            }
        }

        (CpuRaw { freqs, governors }, found_any)
    }

    fn capture_gpu(vendor: Vendor) -> (GpuRaw, bool) {
        let gpu = HardwareCapability::resolve_gpu(vendor);
        let found = gpu.is_supported || gpu.cur_freq_mhz > 0 || gpu.load_pct > 0;
        (
            GpuRaw {
                freq_mhz: gpu.cur_freq_mhz as u32,
                busy_percent: gpu.load_pct.min(100),
                vendor_name: gpu.vendor_name,
                cur_governor: gpu.cur_governor,
                boost_level: gpu.boost_level,
                available_governors: gpu.available_governors,
            },
            found,
        )
    }

    fn capture_thermal_zones() -> Vec<ThermalZoneEntry> {
        let mut zones = Vec::with_capacity(16);
        for id in 0..64 {
            let tz_dir = format!("/sys/class/thermal/thermal_zone{}", id);
            let p = Path::new(&tz_dir);
            if !p.exists() {
                continue;
            }
            let z_type = SysfsReader::read_trimmed(format!("{}/type", tz_dir))
                .unwrap_or_else(|_| format!("zone{}", id));
            let z_raw = SysfsReader::read_int::<i64>(format!("{}/temp", tz_dir)).unwrap_or(0);
            let temp_c = if z_raw.abs() > 1000 {
                z_raw as f32 / 1000.0
            } else if z_raw.abs() > 100 {
                z_raw as f32 / 10.0
            } else {
                z_raw as f32
            };
            zones.push(ThermalZoneEntry {
                id,
                zone_type: z_type,
                temp_c,
            });
        }
        zones
    }

    fn capture_battery() -> (BatteryRaw, bool) {
        let mut found_any = false;

        let capacity = SysfsReader::read_int::<u32>("/sys/class/power_supply/battery/capacity")
            .or_else(|_| SysfsReader::read_int::<u32>("/sys/class/power_supply/bms/capacity"))
            .map(|c| {
                found_any = true;
                c
            })
            .unwrap_or(50);

        let temp_raw = SysfsReader::read_int::<i32>("/sys/class/power_supply/battery/temp")
            .or_else(|_| SysfsReader::read_int::<i32>("/sys/class/power_supply/bms/temp"))
            .map(|t| {
                found_any = true;
                t
            })
            .unwrap_or(350);

        let cur_raw = SysfsReader::read_int::<i32>("/sys/class/power_supply/battery/current_now")
            .or_else(|_| SysfsReader::read_int::<i32>("/sys/class/power_supply/battery/BatteryAverageCurrent"))
            .or_else(|_| SysfsReader::read_int::<i32>("/sys/class/power_supply/bms/current_now"))
            .map(|c| {
                found_any = true;
                c
            })
            .unwrap_or(0);

        let current_ma = if cur_raw.abs() > 10_000 {
            cur_raw / 1000
        } else {
            cur_raw
        };

        let volt_raw = SysfsReader::read_int::<u32>("/sys/class/power_supply/battery/voltage_now")
            .or_else(|_| SysfsReader::read_int::<u32>("/sys/class/power_supply/bms/voltage_now"))
            .map(|v| {
                found_any = true;
                v
            })
            .unwrap_or(4_000_000);

        let volt_mv = if volt_raw > 100_000 {
            volt_raw / 1000
        } else {
            volt_raw
        };

        let status = SysfsReader::read_trimmed("/sys/class/power_supply/battery/status")
            .or_else(|_| SysfsReader::read_trimmed("/sys/class/power_supply/bms/status"))
            .map(|s| {
                found_any = true;
                s
            })
            .unwrap_or_else(|_| "Discharging".to_string());

        (
            BatteryRaw {
                capacity,
                temp_raw,
                current_ma,
                volt_mv,
                status,
            },
            found_any,
        )
    }

    fn capture_memory() -> (MemoryRaw, bool) {
        let mut ram_total_kb = 4_194_304u64;
        let mut ram_avail_kb = 2_097_152u64;
        let mut swap_total_kb = 0u64;
        let mut swap_free_kb = 0u64;
        let mut found_meminfo = false;

        if let Ok(content) = fs::read_to_string("/proc/meminfo") {
            found_meminfo = true;
            let mut mem_free_kb = 0u64;
            let mut buffers_kb = 0u64;
            let mut cached_kb = 0u64;
            let mut has_avail = false;

            for line in content.lines() {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if parts.len() >= 2 {
                    match parts[0] {
                        "MemTotal:" => {
                            if let Ok(v) = parts[1].parse::<u64>() {
                                ram_total_kb = v;
                            }
                        }
                        "MemAvailable:" => {
                            if let Ok(v) = parts[1].parse::<u64>() {
                                ram_avail_kb = v;
                                has_avail = true;
                            }
                        }
                        "MemFree:" => {
                            if let Ok(v) = parts[1].parse::<u64>() {
                                mem_free_kb = v;
                            }
                        }
                        "Buffers:" => {
                            if let Ok(v) = parts[1].parse::<u64>() {
                                buffers_kb = v;
                            }
                        }
                        "Cached:" => {
                            if let Ok(v) = parts[1].parse::<u64>() {
                                cached_kb = v;
                            }
                        }
                        "SwapTotal:" => {
                            if let Ok(v) = parts[1].parse::<u64>() {
                                swap_total_kb = v;
                            }
                        }
                        "SwapFree:" => {
                            if let Ok(v) = parts[1].parse::<u64>() {
                                swap_free_kb = v;
                            }
                        }
                        _ => {}
                    }
                }
            }

            if !has_avail {
                ram_avail_kb = mem_free_kb + buffers_kb + cached_kb;
            }
        }

        let mut zram_total_kb = swap_total_kb;
        let mut zram_used_kb = swap_total_kb.saturating_sub(swap_free_kb);

        if let Ok(swaps_content) = fs::read_to_string("/proc/swaps") {
            for line in swaps_content.lines() {
                if line.contains("zram") {
                    let parts: Vec<&str> = line.split_whitespace().collect();
                    if parts.len() >= 4 {
                        if let (Ok(tot_kb), Ok(usd_kb)) = (parts[2].parse::<u64>(), parts[3].parse::<u64>()) {
                            zram_total_kb = tot_kb;
                            zram_used_kb = usd_kb;
                            break;
                        }
                    }
                }
            }
        }

        if zram_total_kb == 0 {
            if let Ok(disksize_bytes) = SysfsReader::read_int::<u64>("/sys/block/zram0/disksize") {
                zram_total_kb = disksize_bytes / 1024;
                if let Ok(used_bytes) = SysfsReader::read_int::<u64>("/sys/block/zram0/mem_used_total") {
                    zram_used_kb = used_bytes / 1024;
                }
            }
        }

        (
            MemoryRaw {
                ram_total_kb,
                ram_avail_kb,
                swap_total_kb,
                swap_free_kb,
                zram_total_kb,
                zram_used_kb,
            },
            found_meminfo,
        )
    }

    /// Serializes the state into the exact JSON schema required by Lynx WebUI and Lynx Companion App.
    /// Hand-crafted with pre-allocated buffer for zero-overhead, sub-millisecond execution.
    /// Backward-compatible while providing timestamp, health, derived state, GPU details, and thermal zones.
    pub fn to_json(&self) -> String {
        self.to_unified_json(None, None, None, None)
    }

    /// Emits the comprehensive Unified Hardware Telemetry Snapshot used by WebUI and Android Companion App.
    pub fn to_unified_json(
        &self,
        active_profile: Option<&str>,
        modifier: Option<&str>,
        modifier_desc: Option<&str>,
        daemon_pid: Option<u32>,
    ) -> String {
        let mut json = String::with_capacity(1024);

        let _ = write!(json, "{{\"timestamp\":{},\"cpu\":[", self.timestamp);

        for (i, f) in self.raw.cpu.freqs.iter().enumerate() {
            if i > 0 {
                json.push(',');
            }
            let _ = write!(json, "{}", f);
        }

        let govs_quoted: Vec<String> = self
            .raw
            .gpu
            .available_governors
            .iter()
            .map(|g| format!("\"{}\"", g))
            .collect();
        let govs_str = govs_quoted.join(",");

        let is_daemon_running = daemon_pid.is_some() || DaemonLifecycle::is_running();
        let pid_val = daemon_pid.or_else(|| DaemonLifecycle::check_status());
        let pid_str = match pid_val {
            Some(pid) => format!("{}", pid),
            None => "null".to_string(),
        };

        let prof_str = active_profile.unwrap_or("balance");
        let mod_str = modifier.unwrap_or("None");
        let mod_desc_str = modifier_desc.unwrap_or("Full Base Profile");

        let _ = write!(
            json,
            "],\"gpu_freq\":{},\"gpu_busy\":{},\"gpu_vendor\":\"{}\",\"gpu_cur_gov\":\"{}\",\"gpu_boost\":{},\"gpu_avail_govs\":[{}],\"temp\":\"{}\",\"batt_level\":{},\"batt_current_ma\":{},\"batt_volt_mv\":{},\"batt_status\":\"{}\",\"ram_used_mb\":{},\"ram_total_mb\":{},\"zram_used_mb\":{},\"zram_total_mb\":{},\"swap_used_mb\":{},\"swap_total_mb\":{},\"daemon\":{{\"running\":{},\"pid\":{}}},\"runtime\":{{\"profile\":\"{}\",\"modifier\":\"{}\",\"modifier_desc\":\"{}\"}},\"health\":{{\"cpu\":{},\"gpu\":{},\"battery\":{},\"memory\":{}}},\"derived\":{{\"thermal_level\":\"{}\",\"workload_level\":\"{}\",\"power_state\":\"{}\"}},\"thermal_zones\":[",
            self.raw.gpu.freq_mhz,
            self.raw.gpu.busy_percent,
            self.raw.gpu.vendor_name,
            self.raw.gpu.cur_governor,
            self.raw.gpu.boost_level,
            govs_str,
            self.derived.temp_celsius,
            self.raw.battery.capacity,
            self.raw.battery.current_ma,
            self.raw.battery.volt_mv,
            self.raw.battery.status,
            self.derived.ram_used_mb,
            self.derived.ram_total_mb,
            self.derived.zram_used_mb,
            self.derived.zram_total_mb,
            self.derived.swap_used_mb,
            self.derived.swap_total_mb,
            is_daemon_running,
            pid_str,
            prof_str,
            mod_str,
            mod_desc_str,
            self.health.cpu_available,
            self.health.gpu_available,
            self.health.battery_available,
            self.health.memory_available,
            self.derived.thermal_level.as_str(),
            self.derived.workload_level.as_str(),
            self.derived.power_state.as_str(),
        );

        for (i, tz) in self.raw.thermal_zones.iter().enumerate() {
            if i > 0 {
                json.push(',');
            }
            let _ = write!(
                json,
                "{{\"id\":{},\"type\":\"{}\",\"temp\":{:.0},\"temp_c\":{:.1}}}",
                tz.id, tz.zone_type, tz.temp_c, tz.temp_c
            );
        }

        json.push_str("]}");
        json
    }

    /// Formats the state into a clean human-readable table for CLI terminal inspection.
    pub fn display_pretty(&self) -> String {
        let mut out = String::new();
        out.push_str("==================================================\n");
        out.push_str("          LYNX HARDWARE TELEMETRY SNAPSHOT        \n");
        out.push_str("==================================================\n");

        let _ = write!(
            out,
            "• Metadata:\n  Timestamp: {} | Sensor Health: CPU({}) GPU({}) Batt({}) Mem({})\n",
            self.timestamp,
            if self.health.cpu_available { "OK" } else { "FAIL" },
            if self.health.gpu_available { "OK" } else { "FAIL" },
            if self.health.battery_available { "OK" } else { "FAIL" },
            if self.health.memory_available { "OK" } else { "FAIL" },
        );

        out.push_str("• CPU Core Frequencies:\n  ");
        for (idx, freq) in self.raw.cpu.freqs.iter().enumerate() {
            if *freq == 0 {
                let _ = write!(out, "[C{}: offline] ", idx);
            } else {
                let _ = write!(out, "[C{}: {} MHz] ", idx, freq / 1000);
            }
            if (idx + 1) % 4 == 0 {
                out.push_str("\n  ");
            }
        }
        if !self.raw.cpu.governors.is_empty() {
            let _ = write!(out, "\n  Governors: {}\n", self.raw.cpu.governors.join(", "));
        } else {
            out.push('\n');
        }

        let _ = write!(
            out,
            "• GPU Subsystem:\n  Clock: {} MHz | Load: {}% | Derived Workload: {}\n",
            self.raw.gpu.freq_mhz,
            self.raw.gpu.busy_percent,
            self.derived.workload_level.as_str()
        );

        let _ = write!(
            out,
            "• Battery & Power:\n  Level: {}% | Temp: {}°C ({}) | Current: {} mA | Voltage: {} mV | State: {}\n",
            self.raw.battery.capacity,
            self.derived.temp_celsius,
            self.derived.thermal_level.as_str(),
            self.raw.battery.current_ma,
            self.raw.battery.volt_mv,
            self.derived.power_state.as_str()
        );

        let _ = write!(
            out,
            "• Memory & Swap:\n  RAM: {} / {} MB ({:.1}%) | ZRAM: {} / {} MB\n",
            self.derived.ram_used_mb,
            self.derived.ram_total_mb,
            self.derived.ram_usage_percent,
            self.derived.zram_used_mb,
            self.derived.zram_total_mb
        );
        out.push_str("==================================================");

        out
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_to_unified_json_schema() {
        let mut state = DeviceState::default();
        state.timestamp = 1710000000;
        state.raw.cpu.freqs = vec![2000000, 2050000];
        state.raw.gpu.freq_mhz = 850;
        state.raw.gpu.busy_percent = 25;
        state.raw.gpu.vendor_name = "Mali-G57".to_string();
        state.raw.gpu.cur_governor = "simple_ondemand".to_string();
        state.raw.gpu.available_governors = vec!["simple_ondemand".to_string(), "performance".to_string()];
        state.raw.thermal_zones = vec![
            ThermalZoneEntry { id: 0, zone_type: "soc_max".to_string(), temp_c: 42.5 },
            ThermalZoneEntry { id: 1, zone_type: "battery".to_string(), temp_c: 36.0 },
        ];

        let json = state.to_unified_json(Some("performance"), Some("ThermalCooling"), Some("-15% floor"), Some(1234));
        assert!(json.contains("\"timestamp\":1710000000"));
        assert!(json.contains("\"cpu\":[2000000,2050000]"));
        assert!(json.contains("\"gpu_freq\":850"));
        assert!(json.contains("\"gpu_busy\":25"));
        assert!(json.contains("\"gpu_vendor\":\"Mali-G57\""));
        assert!(json.contains("\"gpu_cur_gov\":\"simple_ondemand\""));
        assert!(json.contains("\"daemon\":{\"running\":true,\"pid\":1234}"));
        assert!(json.contains("\"profile\":\"performance\""));
        assert!(json.contains("\"modifier\":\"ThermalCooling\""));
        assert!(json.contains("\"thermal_zones\":[{\"id\":0,\"type\":\"soc_max\",\"temp\":42,\"temp_c\":42.5},{\"id\":1,\"type\":\"battery\",\"temp\":36,\"temp_c\":36.0}]"));
    }
}
