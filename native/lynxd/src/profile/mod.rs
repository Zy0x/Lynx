pub mod balanced;
pub mod extreme;
pub mod performance;
pub mod policy;
pub mod powersave;
pub mod profile;
pub mod validator;
pub mod verifier;
pub mod user_catalog;

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
    if let Some(custom) = user_catalog::resolve_profile(name) {
        return profile_from_catalog(custom);
    }
    let resolved = match name.to_ascii_lowercase().as_str() {
        "powersave" => "powersave",
        "balanced" | "balance" => "balanced",
        "performance" | "perf" => "performance",
        "extreme" | "ext" => "extreme",
        _ => return None,
    };
    built_in_profile(resolved)
}

fn profile_from_catalog(custom: user_catalog::CatalogProfile) -> Option<Profile> {
    let mut profile = built_in_profile(&custom.base_profile)?;
    if let Some(cpu_name) = custom.cpu_profile.as_deref() {
        let cpu = built_in_profile(cpu_policy_name(cpu_name)?)?;
        profile.cpu = cpu.cpu;
        profile.scheduler = cpu.scheduler;
    }
    if let Some(gpu_name) = custom.gpu_profile.as_deref() {
        profile.gpu = built_in_profile(gpu_policy_name(gpu_name)?)?.gpu;
    }
    Some(profile)
}

fn built_in_profile(name: &str) -> Option<Profile> {
    match name {
        "powersave" => Some(powersave::get_profile()),
        "balanced" | "balance" => Some(balanced::get_profile()),
        "performance" | "perf" => Some(performance::get_profile()),
        "extreme" | "ext" => Some(extreme::get_profile()),
        _ => None,
    }
}

fn cpu_policy_name(name: &str) -> Option<&'static str> {
    match name {
        "powersave" | "battery" => Some("powersave"),
        "balance" | "balanced" => Some("balanced"),
        "performance" => Some("performance"),
        "extreme" => Some("extreme"),
        _ => None,
    }
}

fn gpu_policy_name(name: &str) -> Option<&'static str> {
    match name {
        "powersave" | "battery" => Some("powersave"),
        "balance" | "balanced" => Some("balanced"),
        "performance" | "esports" => Some("performance"),
        "extreme" => Some("extreme"),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::{profile_from_catalog, user_catalog::CatalogProfile};

    #[test]
    fn user_profile_uses_selected_cpu_and_gpu_policies_over_base() {
        let profile = profile_from_catalog(CatalogProfile {
            id: "portable_mix".to_string(),
            name: "Portable Mix".to_string(),
            base_profile: "balance".to_string(),
            cpu_profile: Some("performance".to_string()),
            gpu_profile: Some("battery".to_string()),
        }).unwrap();
        assert_eq!(profile.cpu.min_percent, 35);
        assert_eq!(profile.gpu.mode.as_str(), "PowerEfficient");
        assert_eq!(profile.thermal.critical_temperature, 49);
    }
}
