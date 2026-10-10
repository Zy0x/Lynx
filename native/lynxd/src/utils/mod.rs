pub mod logger;

pub use logger::Logger;

pub fn update_module_prop_description(desc: &str) {
    let prop_path = std::path::Path::new("/data/adb/modules/Lynx/module.prop");
    if prop_path.exists() {
        if let Ok(content) = std::fs::read_to_string(prop_path) {
            let new_lines: Vec<String> = content
                .lines()
                .map(|line| {
                    if line.starts_with("description=") {
                        format!("description={}", desc)
                    } else {
                        line.to_string()
                    }
                })
                .collect();
            let _ = std::fs::write(prop_path, new_lines.join("\n") + "\n");
        }
    }
}
