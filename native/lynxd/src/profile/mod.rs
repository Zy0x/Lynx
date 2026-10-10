pub mod balanced;
pub mod extreme;
pub mod performance;
pub mod policy;
pub mod powersave;
pub mod profile;
pub mod validator;
pub mod verifier;

pub use policy::{CpuPolicy, GpuMode, GpuPolicy, Governor, SchedulerPolicy, ThermalPolicy};
pub use profile::Profile;
pub use validator::ProfileValidator;
pub use verifier::ProfileVerifier;

pub fn list_profiles() -> Vec<Profile> {
    vec![
        powersave::get_profile(),
        balanced::get_profile(),
        performance::get_profile(),
        extreme::get_profile(),
    ]
}

pub fn get_profile_by_name(name: &str) -> Option<Profile> {
    match name.to_ascii_lowercase().as_str() {
        "powersave" => Some(powersave::get_profile()),
        "balanced" | "balance" => Some(balanced::get_profile()),
        "performance" | "perf" => Some(performance::get_profile()),
        "extreme" | "ext" => Some(extreme::get_profile()),
        _ => None,
    }
}
