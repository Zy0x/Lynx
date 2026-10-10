use crate::core::error::SysfsError;

pub type Result<T> = std::result::Result<T, SysfsError>;
