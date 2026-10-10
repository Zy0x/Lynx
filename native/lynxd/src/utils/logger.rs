pub struct Logger;

impl Logger {
    #[inline]
    pub fn info(msg: impl std::fmt::Display) {
        println!("[INFO] {}", msg);
    }

    #[inline]
    pub fn warn(msg: impl std::fmt::Display) {
        eprintln!("[WARN] {}", msg);
    }

    #[inline]
    pub fn error(msg: impl std::fmt::Display) {
        eprintln!("[ERROR] {}", msg);
    }
}
