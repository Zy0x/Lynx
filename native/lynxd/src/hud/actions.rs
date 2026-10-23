use crate::sysfs::writer::{SysfsWriter, WriteMode};

/// A write-only kernel request has no measurable success readback. Report acceptance only.
pub fn release_cache() {
    let accepted = SysfsWriter::write("/proc/sys/vm/drop_caches", "3", WriteMode::Direct).is_ok();
    println!("{{\"accepted\":{accepted}}}");
    if !accepted { std::process::exit(1); }
}
