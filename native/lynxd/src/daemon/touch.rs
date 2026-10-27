use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;
use std::time::Duration;

static LAST_TOUCH_MS: AtomicU64 = AtomicU64::new(0);
static TOUCH_ACTIVE: AtomicBool = AtomicBool::new(false);

pub struct TouchDetector {
    running: Arc<AtomicBool>,
}

impl TouchDetector {
    /// Discovers physical touchscreen event device in `/dev/input/` with zero hardcoding.
    pub fn find_touchscreen_device() -> Option<PathBuf> {
        // Priority 1: Check driver / device name in /sys/class/input/event*/device/name
        if let Ok(entries) = std::fs::read_dir("/sys/class/input") {
            for entry in entries.flatten() {
                let name_path = entry.path().join("device/name");
                if let Ok(dev_name) = std::fs::read_to_string(&name_path) {
                    let name_lower = dev_name.trim().to_lowercase();
                    if name_lower.contains("touch")
                        || name_lower.contains("tpd")
                        || name_lower.contains("ts")
                        || name_lower.contains("touchscreen")
                        || name_lower.contains("fts")
                        || name_lower.contains("goodix")
                        || name_lower.contains("synaptics")
                        || name_lower.contains("focaltech")
                        || name_lower.contains("ilitek")
                        || name_lower.contains("novatek")
                        || name_lower.contains("sec_touch")
                    {
                        let file_name = entry.file_name();
                        let dev_node = PathBuf::from("/dev/input").join(file_name);
                        if dev_node.exists() {
                            return Some(dev_node);
                        }
                    }
                }
            }
        }

        // Priority 2: Check input devices with non-zero absolute axis (EV_ABS / multi-touch)
        for idx in (0..=8).rev() {
            let abs_path = format!("/sys/class/input/event{}/device/capabilities/abs", idx);
            if let Ok(abs_str) = std::fs::read_to_string(&abs_path) {
                let trimmed = abs_str.trim();
                if !trimmed.is_empty() && trimmed != "0" {
                    let p = PathBuf::from(format!("/dev/input/event{}", idx));
                    if p.exists() {
                        return Some(p);
                    }
                }
            }
        }

        None
    }

    /// Spawns the zero-CPU blocking touch listener thread.
    pub fn start() -> Option<Self> {
        let dev_path = Self::find_touchscreen_device()?;
        let running = Arc::new(AtomicBool::new(true));
        let running_clone = running.clone();
        let path_clone = dev_path.clone();

        std::thread::Builder::new()
            .name("lynx-touch".to_string())
            .spawn(move || {
                let mut buf = [0u8; 24]; // Standard Linux 64-bit input_event struct size
                while running_clone.load(Ordering::Relaxed) {
                    match std::fs::File::open(&path_clone) {
                        Ok(mut file) => {
                            use std::io::Read;
                            while running_clone.load(Ordering::Relaxed) {
                                match file.read_exact(&mut buf) {
                                    Ok(()) => {
                                        // On 64-bit Linux / aarch64:
                                        // struct input_event {
                                        //   struct timeval time; // 16 bytes (8s + 8us)
                                        //   __u16 type;          // 2 bytes (offset 16)
                                        //   __u16 code;          // 2 bytes (offset 18)
                                        //   __s32 value;         // 4 bytes (offset 20)
                                        // }
                                        let ev_type = u16::from_ne_bytes([buf[16], buf[17]]);
                                        // EV_KEY (1) or EV_ABS (3)
                                        if ev_type == 1 || ev_type == 3 {
                                            let now_ms = std::time::SystemTime::now()
                                                .duration_since(std::time::UNIX_EPOCH)
                                                .unwrap_or_default()
                                                .as_millis() as u64;
                                            LAST_TOUCH_MS.store(now_ms, Ordering::Relaxed);
                                            TOUCH_ACTIVE.store(true, Ordering::Relaxed);

                                            // Immediate zero-latency touch boost notification
                                            crate::subsystems::GpuController::on_hardware_touch();
                                        }
                                    }
                                    Err(_) => {
                                        // Input device suspended / display off
                                        std::thread::sleep(Duration::from_millis(500));
                                        break;
                                    }
                                }
                            }
                        }
                        Err(_) => {
                            std::thread::sleep(Duration::from_millis(1000));
                        }
                    }
                }
            })
            .ok()?;

        Some(Self { running })
    }

    /// Checks if a hardware touch event was registered within the last `window_ms` milliseconds.
    pub fn is_touch_recent(window_ms: u64) -> bool {
        let last = LAST_TOUCH_MS.load(Ordering::Relaxed);
        if last == 0 {
            return false;
        }
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as u64;
        now.saturating_sub(last) <= window_ms
    }

    pub fn last_touch_ms() -> u64 {
        LAST_TOUCH_MS.load(Ordering::Relaxed)
    }

    pub fn stop(&self) {
        self.running.store(false, Ordering::Relaxed);
    }
}
