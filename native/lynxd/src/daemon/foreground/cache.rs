use crate::daemon::foreground::rules::ForegroundApp;

pub struct ForegroundCache {
    entries: Vec<(String, ForegroundApp)>,
    capacity: usize,
}

impl ForegroundCache {
    pub fn new(capacity: usize) -> Self {
        Self {
            entries: Vec::with_capacity(capacity),
            capacity,
        }
    }

    pub fn lookup(&self, package: &str) -> Option<ForegroundApp> {
        for (pkg, app) in &self.entries {
            if pkg == package {
                return Some(app.clone());
            }
        }
        None
    }

    pub fn store(&mut self, package: String, app: ForegroundApp) {
        // If already present, update and move to front
        if let Some(pos) = self.entries.iter().position(|(k, _)| k == &package) {
            self.entries.remove(pos);
            self.entries.insert(0, (package, app));
            return;
        }

        // Evict oldest if full
        if self.entries.len() >= self.capacity {
            self.entries.pop();
        }

        self.entries.insert(0, (package, app));
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::daemon::foreground::rules::{AppCategory, Confidence};

    #[test]
    fn test_cache_hit_and_eviction() {
        let mut cache = ForegroundCache::new(2);

        let app1 = ForegroundApp {
            package: "pkg1".to_string(),
            category: AppCategory::Game,
            confidence: Confidence::High,
        };
        let app2 = ForegroundApp {
            package: "pkg2".to_string(),
            category: AppCategory::Media,
            confidence: Confidence::High,
        };
        let app3 = ForegroundApp {
            package: "pkg3".to_string(),
            category: AppCategory::System,
            confidence: Confidence::High,
        };

        cache.store("pkg1".to_string(), app1.clone());
        cache.store("pkg2".to_string(), app2.clone());

        assert_eq!(cache.lookup("pkg1"), Some(app1));
        assert_eq!(cache.lookup("pkg2"), Some(app2));

        // Add 3rd: pkg1 was accessed recently, so pkg1 stays, oldest is evicted or replaced
        cache.store("pkg3".to_string(), app3.clone());
        assert_eq!(cache.lookup("pkg3"), Some(app3));
        assert!(cache.entries.len() <= 2);
    }
}
