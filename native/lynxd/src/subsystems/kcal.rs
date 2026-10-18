use std::path::Path;
use crate::hardware::qcom::QcomHardware;
use crate::sysfs::{SysfsReader, SysfsWriter, WriteMode};

#[derive(Debug, Clone)]
pub struct KcalStatus {
    pub supported: bool,
    pub enabled: bool,
    pub red: u32,
    pub green: u32,
    pub blue: u32,
    pub saturation: u32,
    pub contrast: u32,
    pub value: u32,
    pub hue: u32,
}

pub struct KcalController;

impl KcalController {
    pub fn is_supported() -> bool {
        Path::new(QcomHardware::KCAL_ENABLE).exists() || Path::new(QcomHardware::KCAL_RGB).exists()
    }

    pub fn read_status() -> KcalStatus {
        if !Self::is_supported() {
            return KcalStatus {
                supported: false,
                enabled: false,
                red: 256,
                green: 256,
                blue: 256,
                saturation: 256,
                contrast: 256,
                value: 256,
                hue: 0,
            };
        }

        let enabled = SysfsReader::read_trimmed(QcomHardware::KCAL_ENABLE)
            .map(|s| s == "1")
            .unwrap_or(false);

        let rgb_raw = SysfsReader::read_trimmed(QcomHardware::KCAL_RGB).unwrap_or_default();
        let parts: Vec<&str> = rgb_raw.split_whitespace().collect();
        let red = parts.get(0).and_then(|v| v.parse().ok()).unwrap_or(256);
        let green = parts.get(1).and_then(|v| v.parse().ok()).unwrap_or(256);
        let blue = parts.get(2).and_then(|v| v.parse().ok()).unwrap_or(256);

        let saturation = SysfsReader::read_trimmed(QcomHardware::KCAL_SAT)
            .ok()
            .and_then(|s| s.parse().ok())
            .unwrap_or(256);

        let contrast = SysfsReader::read_trimmed(QcomHardware::KCAL_CONT)
            .ok()
            .and_then(|s| s.parse().ok())
            .unwrap_or(256);

        let value = SysfsReader::read_trimmed(QcomHardware::KCAL_VAL)
            .ok()
            .and_then(|s| s.parse().ok())
            .unwrap_or(256);

        let hue = SysfsReader::read_trimmed(QcomHardware::KCAL_HUE)
            .ok()
            .and_then(|s| s.parse().ok())
            .unwrap_or(0);

        KcalStatus {
            supported: true,
            enabled,
            red,
            green,
            blue,
            saturation,
            contrast,
            value,
            hue,
        }
    }

    pub fn apply(enabled: bool, r: u32, g: u32, b: u32, sat: u32, cont: u32, val: u32, hue: u32) -> Result<(), String> {
        if !Self::is_supported() {
            return Err("KCAL is not supported on this kernel".into());
        }

        let rgb_str = format!("{} {} {}", r.min(256), g.min(256), b.min(256));
        let _ = SysfsWriter::write(QcomHardware::KCAL_RGB, &rgb_str, WriteMode::ForcePermission);
        let _ = SysfsWriter::write(QcomHardware::KCAL_SAT, &sat.clamp(128, 384).to_string(), WriteMode::ForcePermission);
        let _ = SysfsWriter::write(QcomHardware::KCAL_CONT, &cont.clamp(128, 384).to_string(), WriteMode::ForcePermission);
        let _ = SysfsWriter::write(QcomHardware::KCAL_VAL, &val.clamp(128, 384).to_string(), WriteMode::ForcePermission);
        let _ = SysfsWriter::write(QcomHardware::KCAL_HUE, &hue.to_string(), WriteMode::ForcePermission);

        let enable_val = if enabled { "1" } else { "0" };
        SysfsWriter::write(QcomHardware::KCAL_ENABLE, enable_val, WriteMode::ForcePermission)
            .map_err(|e| format!("Failed to set kcal_enable: {}", e))?;

        Ok(())
    }

    pub fn apply_preset(name: &str) -> Result<(), String> {
        match name {
            "vivid_oled" => Self::apply(true, 256, 256, 256, 280, 265, 256, 0),
            "cinematic_d65" => Self::apply(true, 256, 248, 240, 256, 256, 256, 0),
            "outdoor_boost" => Self::apply(true, 256, 256, 256, 270, 280, 280, 0),
            "flat_default" => Self::apply(true, 256, 256, 256, 256, 256, 256, 0),
            _ => Err(format!("Unknown KCAL preset: {}", name)),
        }
    }
}
