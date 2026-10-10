pub mod capability;
pub mod charging;
pub mod detector;
pub mod mtk;
pub mod qcom;
pub mod translator;

pub use capability::{
    BlockDeviceCapability, BlockDeviceType, CpuCapability, GpuCapability, GpuDriver, GpuOppEntry,
    HardwareCapability, MemoryCapability, NetworkCapability, PolicyCapability, SchedulerCapability,
    StorageCapability,
};
pub use charging::ChargingRegulator;
pub use detector::{ScoreBoard, Vendor};
pub use mtk::MtkHardware;
pub use qcom::QcomHardware;
pub use translator::{HardwareTranslator, PlannedOperation};

pub trait HardwareNode {
    fn name(&self) -> &'static str;
    fn primary_path(&self) -> &'static str;
    fn fallback_paths(&self) -> &'static [&'static str] {
        &[]
    }
    fn is_supported(&self) -> bool;
    fn vendor(&self) -> Vendor;
}
