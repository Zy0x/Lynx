use std::collections::BTreeMap;
use std::fs;
use super::Metric;

pub type Metrics = BTreeMap<String, Metric>;

pub fn number(raw: &str) -> Option<f64> {
    raw.trim().trim_end_matches('%').trim().parse::<f64>().ok().filter(|v| v.is_finite())
}

pub fn ged_mhz(raw: &str) -> Option<f64> {
    let cols: Vec<_> = raw.split_whitespace().collect();
    // GED reports an OPP index followed by the current frequency in kHz.
    number(cols.last()?).filter(|v| *v > 0.0).map(|v| v / 1000.0)
}

fn gpu_clock(raw: &str, kind: &str, scale: f64) -> Option<f64> {
    match kind {
        "ged" => ged_mhz(raw),
        "real" => raw.find("(real) freq:").and_then(|pos| raw[pos+12..].split_whitespace().next()).and_then(number).map(|v| v * scale),
        _ => number(raw).map(|v| {
            if scale == 1.0 {
                if v > 10_000_000.0 { v / 1_000_000.0 }
                else if v > 10_000.0 { v / 1_000.0 }
                else { v }
            } else {
                v * scale
            }
        }),
    }.filter(|v| *v >= 0.0)
}

pub fn cpu_ticks(raw: &str) -> Option<(u64,u64)> {
    let cols: Vec<u64> = raw.lines().find(|s| s.starts_with("cpu "))?.split_whitespace()
        .skip(1).take(8).map(str::parse::<u64>).collect::<Result<_,_>>().ok()?;
    if cols.len() < 4 { return None; }
    Some((cols.iter().sum(), cols[3] + cols.get(4).copied().unwrap_or(0)))
}

pub fn load(previous: (u64,u64), current: (u64,u64)) -> Option<f64> {
    let total = current.0.checked_sub(previous.0)?;
    let idle = current.1.checked_sub(previous.1)?;
    if total == 0 || idle > total { None } else { Some((total-idle) as f64 * 100.0 / total as f64) }
}

fn read(path: &str, unit: &'static str, scale: f64, now: u64) -> Metric {
    match fs::read_to_string(path) {
        Ok(raw) => match number(&raw) {
            Some(v) => Metric::measured(v * scale, unit, path, now),
            None => Metric::missing(unit, path, now, "unsupported"),
        },
        Err(e) => Metric::missing(unit, path, now, if e.kind() == std::io::ErrorKind::PermissionDenied { "permission_denied" } else { "unsupported" }),
    }
}

pub struct Hardware {
    previous_cpu: Option<(u64,u64)>,
    pub metrics: Metrics,
    pub policies: Vec<(String, Metric)>,
    pub charging: Option<bool>,
    thermal: Vec<(String,String)>,
    gpu: Vec<(String, &'static str, f64)>,
}

impl Hardware {
    pub fn new() -> Self {
        let mut gpu = vec![
            ("/sys/class/kgsl/kgsl-3d0/clock_mhz".into(), "plain", 1.0),
            ("/sys/class/kgsl/kgsl-3d0/gpuclk".into(), "plain", 0.000001),
            ("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq".into(), "plain", 1.0),
            ("/sys/kernel/ged/hal/current_freqency".into(), "ged", 1.0),
            ("/proc/gpufreq/gpufreq_var_dump".into(), "real", 0.001),
        ];
        if let Ok(entries) = fs::read_dir("/sys/class/devfreq") {
            for p in entries.flatten() {
                let canonical = fs::canonicalize(p.path()).unwrap_or(p.path()).to_string_lossy().to_lowercase();
                if ["gpu", "mali", "kgsl"].iter().any(|s| canonical.contains(s)) {
                    gpu.push((p.path().join("cur_freq").to_string_lossy().into(), "plain", 1.0));
                }
            }
        }
        let thermal = fs::read_dir("/sys/class/thermal").into_iter().flatten().flatten().filter_map(|p| {
            if !p.file_name().to_string_lossy().starts_with("thermal_zone") { return None; }
            Some((p.path().to_string_lossy().into(), fs::read_to_string(p.path().join("type")).ok()?.trim().to_lowercase()))
        }).collect();
        Self { previous_cpu: None, metrics: Metrics::new(), policies: Vec::new(), charging: None, thermal, gpu }
    }

    pub fn sample(&mut self, now: u64, temperatures: bool) {
        let ticks = fs::read_to_string("/proc/stat").ok().and_then(|s| cpu_ticks(&s));
        let usage = self.previous_cpu.zip(ticks).and_then(|(a,b)| load(a,b));
        self.previous_cpu = ticks;
        self.metrics.insert("cpu_load".into(), usage.map(|v| Metric::measured(v,"%","/proc/stat",now))
            .unwrap_or_else(|| Metric::missing("%","/proc/stat",now,if ticks.is_some() {"warming_up"} else {"unsupported"})));
        self.policies.clear();
        if let Ok(entries) = fs::read_dir("/sys/devices/system/cpu/cpufreq") {
            for p in entries.flatten() {
                let name = p.file_name().to_string_lossy().into_owned();
                if name.starts_with("policy") {
                    self.policies.push((name,read(&p.path().join("scaling_cur_freq").to_string_lossy(),"MHz",0.001,now)));
                }
            }
        }
        self.policies.sort_by_key(|(p,_)| p.trim_start_matches("policy").parse::<u32>().unwrap_or(0));
        let peak = self.policies.iter().filter_map(|(_,m)| m.value.map(|v| (v,m))).max_by(|a,b| a.0.total_cmp(&b.0));
        self.metrics.insert("cpu_clock".into(), peak.map(|(_,m)| m.clone()).unwrap_or_else(|| Metric::missing("MHz","cpufreq",now,"unsupported")));

        let mut freq = Metric::missing("MHz","GPU current clock",now,"unsupported");
        for (path, kind, scale) in &self.gpu {
            match fs::read_to_string(path) {
                Ok(raw) => {
                    let v = gpu_clock(&raw,kind,*scale);
                    if let Some(v) = v.filter(|v| *v >= 0.0) { freq = Metric::measured(v,"MHz",path,now); break; }
                }
                Err(e) if e.kind() == std::io::ErrorKind::PermissionDenied => freq = Metric::missing("MHz",path,now,"permission_denied"),
                _ => {},
            }
        }
        self.metrics.insert("gpu_clock".into(),freq);
        let mut gpu_load = Metric::missing("%","GPU utilization",now,"unsupported");
        for path in ["/sys/kernel/ged/hal/gpu_utilization", "/sys/module/ged/parameters/gpu_loading", "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage", "/sys/class/kgsl/kgsl-3d0/gpubusy"] {
            if let Ok(raw) = fs::read_to_string(path) {
                let cols: Vec<f64> = raw.split_whitespace().filter_map(number).collect();
                let v = if path.ends_with("gpubusy") {
                    if cols.len() >= 2 && cols[1] > 0.0 && cols[0] <= cols[1] { Some(cols[0]*100.0/cols[1]) } else { None }
                } else { cols.first().copied() };
                if let Some(v) = v.filter(|v| (0.0..=100.0).contains(v)) { gpu_load = Metric::measured(v,"%",path,now); break; }
            }
        }
        self.metrics.insert("gpu_load".into(),gpu_load);
        for (key,node,unit,scale) in [
            ("battery_level","capacity","%",1.0), ("battery_current","current_now","mA",0.001),
            ("battery_voltage","voltage_now","mV",0.001),
        ] {
            let mut m = read(&format!("/sys/class/power_supply/battery/{node}"),unit,scale,now);
            if m.value.is_none() {
                let bms = read(&format!("/sys/class/power_supply/bms/{node}"),unit,scale,now);
                if bms.value.is_some() { m = bms; }
            }
            self.metrics.insert(key.into(),m);
        }
        let watts = self.metrics["battery_voltage"].value.zip(self.metrics["battery_current"].value).map(|(v,i)| v*i.abs()/1_000_000.0);
        self.metrics.insert("battery_power".into(),watts.map(|v| Metric::measured(v,"W","battery voltage × |current|",now))
            .unwrap_or_else(|| Metric::missing("W","battery",now,"unsupported")));
        self.charging = fs::read_to_string("/sys/class/power_supply/battery/status").ok().or_else(|| fs::read_to_string("/sys/class/power_supply/bms/status").ok())
            .and_then(|s| match s.trim() {
                "Charging" => Some(true), "Discharging" | "Not charging" | "Full" => Some(false), _ => None
            });
        let mem = fs::read_to_string("/proc/meminfo").unwrap_or_default();
        let kb = |key: &str| mem.lines().find(|l| l.starts_with(key)).and_then(|l| l.split_whitespace().nth(1)).and_then(number);
        let total = kb("MemTotal:");
        let used = total.zip(kb("MemAvailable:")).filter(|(t,a)| t>=a).map(|(t,a)| (t-a)/1_048_576.0);
        for (key,value) in [("ram_used",used),("ram_total",total.map(|v|v/1_048_576.0))] {
            self.metrics.insert(key.into(),value.map(|v| Metric::measured(v,"GiB","/proc/meminfo",now)).unwrap_or_else(|| Metric::missing("GiB","/proc/meminfo",now,"unsupported")));
        }
        if temperatures {
            let mut btemp = read("/sys/class/power_supply/battery/temp","°C",0.1,now);
            if btemp.value.is_none() { btemp = read("/sys/class/power_supply/bms/temp","°C",0.1,now); }
            self.metrics.insert("battery_temp".into(),btemp);
            for (key, patterns) in [("cpu_temp",vec!["cpu","apc"]),("soc_temp",vec!["soc","tsens","msm","pm","quiet"]),("gpu_temp",vec!["gpu","mali","kgsl","g3d"])] {
                let mut best = Metric::missing("°C","thermal_zone/type",now,"unsupported");
                for (path,kind) in &self.thermal {
                    if !patterns.iter().any(|p| kind.contains(p)) { continue; }
                    let mut m = read(&format!("{path}/temp"),"°C",0.001,now);
                    m.source = format!("{} ({kind})", m.source);
                    if let Some(v) = m.value.filter(|v| (-40.0..=150.0).contains(v)) {
                        if best.value.map_or(true, |b| v>b) { best = m; }
                    }
                }
                self.metrics.insert(key.into(),best);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn zero_load_is_valid_and_guest_ticks_are_not_counted_twice() {
        assert_eq!(cpu_ticks("cpu 1 2 3 4 5 6 7 8 999 999"),Some((36,9)));
        assert_eq!(load((100,50),(200,150)),Some(0.0));
        assert_eq!(load((200,150),(100,50)),None);
        assert_eq!(load((1,1),(1,1)),None);
    }
    #[test]
    fn ged_index_is_never_a_clock() {
        assert_eq!(ged_mhz("3 850000"),Some(850.0));
        assert_eq!(ged_mhz("700000"),Some(700.0));
        assert_eq!(ged_mhz("0 0"),None);
        assert_eq!(ged_mhz("error"),None);
    }
    #[test]
    fn current_clock_sources_have_explicit_units_and_missing_is_not_zero() {
        assert_eq!(gpu_clock("850000000", "plain",0.000001),Some(850.0));
        assert_eq!(gpu_clock("target freq: 900000\n(real) freq: 650000", "real",0.001),Some(650.0));
        assert_eq!(gpu_clock("g_fixed_freq = 950000", "real",0.001),None);
        assert_eq!(gpu_clock("-1", "plain",0.000001),None);
        let absent = read("/this-node-does-not-exist/lynx_hud", "°C",0.001,100);
        assert_eq!(absent.value,None);
        assert_eq!(absent.status,"unsupported");
    }
}
