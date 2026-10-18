pub mod logger;
pub mod json;

pub use logger::Logger;

pub fn update_module_prop_description(desc: &str) {
    let desc_owned = desc.to_string();
    std::thread::spawn(move || {
        let prop_path = std::path::Path::new("/data/adb/modules/Lynx/module.prop");
        if prop_path.exists() {
            if let Ok(content) = std::fs::read_to_string(prop_path) {
                let target_line = format!("description={}", desc_owned);
                if content.lines().any(|l| l == target_line) {
                    return;
                }
                let new_lines: Vec<String> = content
                    .lines()
                    .map(|line| {
                        if line.starts_with("description=") {
                            format!("description={}", desc_owned)
                        } else {
                            line.to_string()
                        }
                    })
                    .collect();
                let _ = std::fs::write(prop_path, new_lines.join("\n") + "\n");
            }
        }
    });
}

