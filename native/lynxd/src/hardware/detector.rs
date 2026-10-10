use std::fs;
use std::path::Path;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Vendor {
    MediaTek,
    Qualcomm,
    Generic,
}

impl Vendor {
    pub fn as_platform_id(&self) -> &'static str {
        match self {
            Self::MediaTek => "mtk",
            Self::Qualcomm => "qcom",
            Self::Generic => "generic",
        }
    }
}

impl std::fmt::Display for Vendor {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::MediaTek => write!(f, "MediaTek"),
            Self::Qualcomm => write!(f, "Qualcomm"),
            Self::Generic => write!(f, "Generic Linux"),
        }
    }
}

#[derive(Debug, Default, Clone)]
pub struct ScoreBoard {
    pub mtk_score: u32,
    pub qcom_score: u32,
    pub dt_compatible: String,
    pub detected_signals: Vec<String>,
}

impl ScoreBoard {
    pub fn evaluate() -> (Vendor, Self) {
        let mut board = ScoreBoard::default();

        // 1. Read Device Tree Compatible string from Kernel
        let mut dt_str = String::new();
        for dt_path in &[
            "/sys/firmware/devicetree/base/compatible",
            "/proc/device-tree/compatible",
        ] {
            if let Ok(bytes) = fs::read(dt_path) {
                dt_str = String::from_utf8_lossy(&bytes)
                    .to_ascii_lowercase()
                    .replace('\0', " ");
                break;
            }
        }
        board.dt_compatible = dt_str.trim().to_string();

        if board.dt_compatible.contains("mediatek") {
            board.mtk_score += 3;
            board.detected_signals.push("DTB: mediatek (+3)".to_string());
        }
        if board.dt_compatible.contains("qcom") || board.dt_compatible.contains("qualcomm") {
            board.qcom_score += 3;
            board.detected_signals.push("DTB: qualcomm (+3)".to_string());
        }

        // 2. Character Device Nodes (Kernel GPU & Driver level)
        if Path::new("/dev/ged").exists() {
            board.mtk_score += 3;
            board.detected_signals.push("Device Node: /dev/ged (+3)".to_string());
        }
        if Path::new("/dev/kgsl-3d0").exists() {
            board.qcom_score += 3;
            board.detected_signals.push("Device Node: /dev/kgsl-3d0 (+3)".to_string());
        }

        // 3. SysFS Subsystems & Drivers
        if Path::new("/proc/ppm").exists() {
            board.mtk_score += 2;
            board.detected_signals.push("Driver: /proc/ppm (+2)".to_string());
        }
        if Path::new("/proc/gpufreq").exists() {
            board.mtk_score += 2;
            board.detected_signals.push("Driver: /proc/gpufreq (+2)".to_string());
        }
        if Path::new("/proc/perfmgr").exists() {
            board.mtk_score += 1;
            board.detected_signals.push("Driver: /proc/perfmgr (+1)".to_string());
        }

        if Path::new("/sys/class/kgsl").exists() {
            board.qcom_score += 2;
            board.detected_signals.push("Driver: /sys/class/kgsl (+2)".to_string());
        }
        if Path::new("/sys/devices/soc0").exists() {
            board.qcom_score += 2;
            board.detected_signals.push("Driver: /sys/devices/soc0 (+2)".to_string());
        }
        if Path::new("/sys/module/cpu_boost").exists() {
            board.qcom_score += 1;
            board.detected_signals.push("Driver: /sys/module/cpu_boost (+1)".to_string());
        }

        // 4. Decision Matrix
        let vendor = if board.mtk_score > board.qcom_score && board.mtk_score >= 3 {
            Vendor::MediaTek
        } else if board.qcom_score > board.mtk_score && board.qcom_score >= 3 {
            Vendor::Qualcomm
        } else {
            Vendor::Generic
        };

        (vendor, board)
    }
}
