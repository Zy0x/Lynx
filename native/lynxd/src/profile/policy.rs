#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Governor {
    Powersave,
    Schedutil,
    Performance,
    Conservative,
    Interactive,
}

impl Governor {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Powersave => "powersave",
            Self::Schedutil => "schedutil",
            Self::Performance => "performance",
            Self::Conservative => "conservative",
            Self::Interactive => "interactive",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum GpuMode {
    PowerEfficient,
    Balanced,
    High,
    Maximum,
}

impl GpuMode {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::PowerEfficient => "PowerEfficient",
            Self::Balanced => "Balanced",
            Self::High => "High",
            Self::Maximum => "Maximum",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CpuPolicy {
    pub governor: Governor,
    pub min_percent: u8,
    pub max_percent: u8,
    pub boost: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct GpuPolicy {
    pub mode: GpuMode,
    pub boost: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ThermalPolicy {
    pub allow_boost_temperature: u8,
    pub critical_temperature: u8,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SchedulerPolicy {
    pub prefer_latency: bool,
    pub prefer_power: bool,
    pub uclamp_min_percent: u8,
}
