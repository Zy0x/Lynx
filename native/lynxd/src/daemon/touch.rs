use std::fs;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU64, AtomicUsize, Ordering};
use std::sync::Arc;
use std::time::Duration;

static LAST_TOUCH_MS: AtomicU64 = AtomicU64::new(0);
static LAST_MOTION_MS: AtomicU64 = AtomicU64::new(0);
static TOUCH_ACTIVE: AtomicBool = AtomicBool::new(false);
static IS_FINGER_DOWN: AtomicBool = AtomicBool::new(false);

// Ring buffer of last 16 touch event timestamps (in ms) for rapid gesture / velocity detection
const TOUCH_HISTORY_LEN: usize = 16;
static TOUCH_HISTORY: [AtomicU64; TOUCH_HISTORY_LEN] = [
    AtomicU64::new(0), AtomicU64::new(0), AtomicU64::new(0), AtomicU64::new(0),
    AtomicU64::new(0), AtomicU64::new(0), AtomicU64::new(0), AtomicU64::new(0),
    AtomicU64::new(0), AtomicU64::new(0), AtomicU64::new(0), AtomicU64::new(0),
    AtomicU64::new(0), AtomicU64::new(0), AtomicU64::new(0), AtomicU64::new(0),
];
static TOUCH_HISTORY_HEAD: AtomicUsize = AtomicUsize::new(0);
static TOUCH_TOTAL_EVENTS: AtomicU64 = AtomicU64::new(0);

pub struct TouchDetector {
    running: Arc<AtomicBool>,
}

impl TouchDetector {
    /// Discovers all physical touchscreen and digitizer input event devices in `/dev/input/`
    /// across Qualcomm, MediaTek, Samsung Exynos, Google Tensor, and generic Android platforms.
    /// Returns a list of valid device paths (supporting foldables, dual-displays, stylus digitizers).
    pub fn find_touchscreen_devices() -> Vec<PathBuf> {
        let mut devices = Vec::new();

        // 25+ Touchscreen Driver Identifiers across major OEMs & vendors
        const TOUCH_KEYWORDS: &[&str] = &[
            "touch", "tpd", "ts", "touchscreen", "fts", "goodix", "synaptics",
            "focaltech", "ilitek", "novatek", "sec_touch", "atmel", "cyttsp",
            "elan", "himax", "melfas", "msg2xxx", "zinitix", "chipone",
            "parade", "wacom", "stmicro", "gt9xx", "ft5x", "hx83100",
            "nt36xxx", "gslx680", "ft6336", "mxt",
        ];

        // Priority 1: Check driver / device name in /sys/class/input/event*/device/name
        if let Ok(entries) = fs::read_dir("/sys/class/input") {
            for entry in entries.flatten() {
                let file_name = entry.file_name();
                let file_name_str = file_name.to_string_lossy();
                if !file_name_str.starts_with("event") {
                    continue;
                }

                let name_path = entry.path().join("device/name");
                if let Ok(dev_name) = fs::read_to_string(&name_path) {
                    let name_lower = dev_name.trim().to_lowercase();
                    if TOUCH_KEYWORDS.iter().any(|&kw| name_lower.contains(kw)) {
                        let dev_node = PathBuf::from("/dev/input").join(&file_name);
                        if dev_node.exists() && !devices.contains(&dev_node) {
                            devices.push(dev_node);
                        }
                    }
                }
            }
        }

        // Priority 2: Check input devices with non-zero absolute axis (EV_ABS / multi-touch capabilities)
        if let Ok(entries) = fs::read_dir("/sys/class/input") {
            for entry in entries.flatten() {
                let file_name = entry.file_name();
                let file_name_str = file_name.to_string_lossy();
                if !file_name_str.starts_with("event") {
                    continue;
                }

                let abs_path = entry.path().join("device/capabilities/abs");
                if let Ok(abs_str) = fs::read_to_string(&abs_path) {
                    let trimmed = abs_str.trim();
                    if !trimmed.is_empty() && trimmed != "0" {
                        let dev_node = PathBuf::from("/dev/input").join(&file_name);
                        if dev_node.exists() && !devices.contains(&dev_node) {
                            devices.push(dev_node);
                        }
                    }
                }
            }
        }

        // Priority 3: Fallback check for /dev/input/event0 .. event16 if still empty
        if devices.is_empty() {
            for idx in 0..=16 {
                let p = PathBuf::from(format!("/dev/input/event{}", idx));
                if p.exists() {
                    devices.push(p);
                    break;
                }
            }
        }

        devices.sort();
        devices
    }

    /// Spawns zero-CPU blocking touch listener threads for all discovered touchscreen devices.
    pub fn start() -> Option<Self> {
        let dev_paths = Self::find_touchscreen_devices();
        if dev_paths.is_empty() {
            return None;
        }

        let running = Arc::new(AtomicBool::new(true));

        for (idx, path) in dev_paths.into_iter().enumerate() {
            let running_clone = running.clone();
            let path_clone = path.clone();

            let _ = std::thread::Builder::new()
                .name(format!("lynx-touch-{}", idx))
                .spawn(move || {
                    let mut buf = [0u8; 24]; // Standard Linux 64-bit input_event struct size
                    while running_clone.load(Ordering::Relaxed) {
                        match fs::File::open(&path_clone) {
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
                                            let ev_code = u16::from_ne_bytes([buf[18], buf[19]]);
                                            let ev_value = i32::from_ne_bytes([buf[20], buf[21], buf[22], buf[23]]);

                                            // EV_KEY (1) or EV_ABS (3)
                                            if ev_type == 1 || ev_type == 3 {
                                                let now_ms = std::time::SystemTime::now()
                                                    .duration_since(std::time::UNIX_EPOCH)
                                                    .unwrap_or_default()
                                                    .as_millis() as u64;

                                                if ev_type == 1 && ev_code == 330 {
                                                    // BTN_TOUCH: finger down / up
                                                    IS_FINGER_DOWN.store(ev_value > 0, Ordering::Relaxed);
                                                } else if ev_type == 3 {
                                                    // EV_ABS: finger moving / coordinates update
                                                    LAST_MOTION_MS.store(now_ms, Ordering::Relaxed);
                                                    IS_FINGER_DOWN.store(true, Ordering::Relaxed);
                                                }

                                                LAST_TOUCH_MS.store(now_ms, Ordering::Relaxed);
                                                TOUCH_ACTIVE.store(true, Ordering::Relaxed);
                                                TOUCH_TOTAL_EVENTS.fetch_add(1, Ordering::Relaxed);

                                                // Record in circular history buffer
                                                let head = TOUCH_HISTORY_HEAD.fetch_add(1, Ordering::Relaxed) % TOUCH_HISTORY_LEN;
                                                TOUCH_HISTORY[head].store(now_ms, Ordering::Relaxed);

                                                // Immediate sub-1ms hardware touch boost notification
                                                crate::subsystems::GpuController::on_hardware_touch();
                                                crate::subsystems::DisplayController::on_hardware_touch();
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
                });
        }

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

    /// Checks if active touch motion / swipe / drag was registered within `window_ms`.
    pub fn is_touch_moving(window_ms: u64) -> bool {
        let last = LAST_MOTION_MS.load(Ordering::Relaxed);
        if last == 0 {
            return false;
        }
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as u64;
        now.saturating_sub(last) <= window_ms
    }

    /// Checks if a finger is currently held down on the physical panel.
    pub fn is_finger_down() -> bool {
        IS_FINGER_DOWN.load(Ordering::Relaxed)
    }

    /// Detects rapid gesture / fling / multi-finger swipe by checking if at least `min_events`
    /// occurred within `window_ms` (e.g. 3 events within 150ms).
    pub fn is_rapid_touch(window_ms: u64, min_events: usize) -> bool {
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as u64;

        let mut count = 0;
        for slot in &TOUCH_HISTORY {
            let t = slot.load(Ordering::Relaxed);
            if t > 0 && now.saturating_sub(t) <= window_ms {
                count += 1;
                if count >= min_events {
                    return true;
                }
            }
        }
        false
    }

    pub fn last_touch_ms() -> u64 {
        LAST_TOUCH_MS.load(Ordering::Relaxed)
    }

    pub fn total_events() -> u64 {
        TOUCH_TOTAL_EVENTS.load(Ordering::Relaxed)
    }

    pub fn stop(&self) {
        self.running.store(false, Ordering::Relaxed);
    }
}
