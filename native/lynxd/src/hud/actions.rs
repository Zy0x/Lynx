use crate::subsystems::memory::MemoryController;

/// Game-safe native memory clean: evicts background cached apps and compacts RAM/ZRAM without dropping game cache.
pub fn release_cache(pkg: Option<&str>) {
    let msg = MemoryController::clean_memory_safe(pkg);
    println!("{{\"accepted\":true,\"detail\":\"{msg}\"}}");
}
