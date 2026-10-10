use std::fs;
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AppCategory {
    Game,
    Benchmark,
    Media,
    System,
    Normal,
}

impl AppCategory {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Game => "Game",
            Self::Benchmark => "Benchmark",
            Self::Media => "Media",
            Self::System => "System",
            Self::Normal => "Normal",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Confidence {
    High,   // Exact match in embedded database or user applist
    Medium, // Known publisher or game keyword match
    Low,    // Fallback heuristic
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ForegroundApp {
    pub package: String,
    pub category: AppCategory,
    pub confidence: Confidence,
}

pub struct RulesEngine {
    custom_games: Vec<String>,
}

impl RulesEngine {
    pub fn new() -> Self {
        let mut engine = Self {
            custom_games: Vec::new(),
        };
        engine.load_default_custom_lists();
        engine
    }

    /// Loads custom target apps from user storage, module root, TSV rules, and config.json.
    pub fn load_default_custom_lists(&mut self) {
        let candidates = [
            "/storage/emulated/0/Lynx/applist_perf.txt",
            "/data/adb/modules/Lynx/core/applist_perf.txt",
            "/data/adb/lynx/applist_perf.txt",
            "./core/applist_perf.txt",
        ];

        for &c in &candidates {
            let p = PathBuf::from(c);
            if p.exists() {
                self.load_custom_file(&p);
                break;
            }
        }

        // Check app_rules.tsv (Per-App rules from Companion APK or manual config)
        let tsv_candidates = [
            "/data/adb/lynx/app_rules.tsv",
            "/data/adb/modules/Lynx/app_rules.tsv",
            "./app_rules.tsv",
        ];
        for &tsv in &tsv_candidates {
            let p = PathBuf::from(tsv);
            if p.exists() {
                self.load_tsv_file(&p);
            }
        }

        // Check custom_apps array inside config.json
        let cfg_candidates = [
            "/data/adb/modules/Lynx/config.json",
            "/data/adb/lynx/config.json",
            "./config.json",
        ];
        for &cfg in &cfg_candidates {
            let p = PathBuf::from(cfg);
            if p.exists() {
                self.load_config_custom_apps(&p);
            }
        }
    }

    pub fn load_custom_file(&mut self, path: &Path) {
        if let Ok(content) = fs::read_to_string(path) {
            for line in content.lines() {
                let trimmed = line.trim();
                if !trimmed.is_empty() && !trimmed.starts_with('#') {
                    let lower = trimmed.to_lowercase();
                    if !self.custom_games.contains(&lower) {
                        self.custom_games.push(lower);
                    }
                }
            }
        }
    }

    pub fn load_tsv_file(&mut self, path: &Path) {
        if let Ok(content) = fs::read_to_string(path) {
            for line in content.lines() {
                let trimmed = line.trim();
                if trimmed.is_empty() || trimmed.starts_with('#') {
                    continue;
                }
                let parts: Vec<&str> = trimmed.split('|').collect();
                if !parts.is_empty() {
                    let pkg = parts[0].trim().to_lowercase();
                    if !pkg.is_empty() && !self.custom_games.contains(&pkg) {
                        self.custom_games.push(pkg);
                    }
                }
            }
        }
    }

    pub fn load_config_custom_apps(&mut self, path: &Path) {
        if let Ok(content) = fs::read_to_string(path) {
            if let Some(pos) = content.find("\"custom_apps\"") {
                let rem = &content[pos..];
                if let (Some(b_start), Some(b_end)) = (rem.find('['), rem.find(']')) {
                    let array_str = &rem[b_start + 1..b_end];
                    for token in array_str.split(',') {
                        let clean = token.replace('"', "").replace('\\', "").trim().to_lowercase();
                        if !clean.is_empty() && !self.custom_games.contains(&clean) {
                            self.custom_games.push(clean);
                        }
                    }
                }
            }
        }
    }

    /// Deterministically categorizes an application package.
    pub fn categorize(&self, package: &str) -> ForegroundApp {
        let pkg_lower = package.to_lowercase();

        // ── Tier 1: Exact Benchmark Signatures ──────────────────────────────
        const BENCHMARKS: &[&str] = &[
            "com.antutu.abenchmark",
            "com.antutu.benchmark.full",
            "com.primatelabs.geekbench5",
            "com.primatelabs.geekbench6",
            "com.futuremark.pcmark.android.benchmark",
            "com.futuremark.3dmark.android",
            "com.ludashi.benchmark",
            "com.andromeda.androbench2",
            "skynet.cputhrottlingtest",
            "com.greenecomputing.flint",
        ];
        if BENCHMARKS.contains(&pkg_lower.as_str()) {
            return ForegroundApp {
                package: package.to_string(),
                category: AppCategory::Benchmark,
                confidence: Confidence::High,
            };
        }

        // ── Tier 2: Exact Known Game Signatures ─────────────────────────────
        const KNOWN_GAMES: &[&str] = &[
            "com.mobile.legends",
            "com.tencent.ig",
            "com.pubg.krmobile",
            "com.pubg.imobile",
            "com.vng.pubgmobile",
            "com.dts.freefireth",
            "com.dts.freefiremax",
            "com.mihoyo.genshinimpact",
            "com.hoyoverse.hkrpgoversea",
            "com.hoyoverse.nap",
            "com.epicgames.fortnite",
            "com.mojang.minecraftpe",
            "com.riotgames.league.wildrift",
            "com.activision.callofduty.shooter",
            "com.garena.game.codm",
            "com.roblox.client",
            "com.carxtech.sr",
            "com.ea.gp.apexleadsmobile",
            "com.kurogame.wutheringwaves.global",
        ];
        if KNOWN_GAMES.contains(&pkg_lower.as_str()) {
            return ForegroundApp {
                package: package.to_string(),
                category: AppCategory::Game,
                confidence: Confidence::High,
            };
        }

        // ── Tier 3: User Custom Applist (applist_perf.txt) ──────────────────
        for custom in &self.custom_games {
            if pkg_lower.contains(custom) {
                return ForegroundApp {
                    package: package.to_string(),
                    category: AppCategory::Game,
                    confidence: Confidence::High,
                };
            }
        }

        // ── Tier 4: Exact Known Media Signatures ────────────────────────────
        const MEDIA_APPS: &[&str] = &[
            "com.spotify.music",
            "com.google.android.youtube",
            "com.netflix.mediaclient",
            "com.apple.android.music",
            "org.videolan.vlc",
        ];
        if MEDIA_APPS.contains(&pkg_lower.as_str()) {
            return ForegroundApp {
                package: package.to_string(),
                category: AppCategory::Media,
                confidence: Confidence::High,
            };
        }

        // ── Tier 5: System Services & UI ────────────────────────────────────
        if pkg_lower.starts_with("com.android.systemui")
            || pkg_lower.starts_with("com.android.settings")
            || pkg_lower.starts_with("com.android.launcher")
            || pkg_lower.starts_with("com.google.android.apps.nexuslauncher")
            || pkg_lower == "android"
        {
            return ForegroundApp {
                package: package.to_string(),
                category: AppCategory::System,
                confidence: Confidence::High,
            };
        }

        // ── Tier 6: Keyword / Publisher Heuristics ──────────────────────────
        if pkg_lower.contains("benchmark") || pkg_lower.contains("throttling") {
            return ForegroundApp {
                package: package.to_string(),
                category: AppCategory::Benchmark,
                confidence: Confidence::Medium,
            };
        }

        if pkg_lower.contains("game")
            || pkg_lower.contains("pubg")
            || pkg_lower.contains("codm")
            || pkg_lower.contains("genshin")
            || pkg_lower.contains("moonton")
            || pkg_lower.contains("tencent")
            || pkg_lower.contains("garena")
            || pkg_lower.contains("hoyoverse")
            || pkg_lower.contains("gameloft")
            || pkg_lower.contains("konami")
        {
            return ForegroundApp {
                package: package.to_string(),
                category: AppCategory::Game,
                confidence: Confidence::Medium,
            };
        }

        // ── Tier 7: Default Normal Application ──────────────────────────────
        ForegroundApp {
            package: package.to_string(),
            category: AppCategory::Normal,
            confidence: Confidence::Low,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_categorize_known_games() {
        let engine = RulesEngine::new();
        let app = engine.categorize("com.mobile.legends");
        assert_eq!(app.category, AppCategory::Game);
        assert_eq!(app.confidence, Confidence::High);

        let app2 = engine.categorize("com.tencent.ig");
        assert_eq!(app2.category, AppCategory::Game);
        assert_eq!(app2.confidence, Confidence::High);
    }

    #[test]
    fn test_categorize_benchmarks() {
        let engine = RulesEngine::new();
        let app = engine.categorize("com.primatelabs.geekbench6");
        assert_eq!(app.category, AppCategory::Benchmark);
        assert_eq!(app.confidence, Confidence::High);
    }

    #[test]
    fn test_categorize_media() {
        let engine = RulesEngine::new();
        let app = engine.categorize("com.spotify.music");
        assert_eq!(app.category, AppCategory::Media);
        assert_eq!(app.confidence, Confidence::High);
    }

    #[test]
    fn test_categorize_system() {
        let engine = RulesEngine::new();
        let app = engine.categorize("com.android.settings");
        assert_eq!(app.category, AppCategory::System);
        assert_eq!(app.confidence, Confidence::High);
    }

    #[test]
    fn test_categorize_normal() {
        let engine = RulesEngine::new();
        let app = engine.categorize("org.wikipedia");
        assert_eq!(app.category, AppCategory::Normal);
        assert_eq!(app.confidence, Confidence::Low);
    }

    #[test]
    fn test_load_tsv_and_config_custom_apps() {
        let mut engine = RulesEngine::new();

        let temp_dir = std::env::temp_dir();
        let tsv_path = temp_dir.join("test_app_rules.tsv");
        let cfg_path = temp_dir.join("test_config.json");

        std::fs::write(&tsv_path, "com.custom.gameone|performance\ncom.custom.gametwo|extreme\n").unwrap();
        std::fs::write(&cfg_path, "{\"game_manager\":{\"custom_apps\":[\"com.custom.gamethree\",\"com.custom.gamefour\"]}}").unwrap();

        engine.load_tsv_file(&tsv_path);
        engine.load_config_custom_apps(&cfg_path);

        let a1 = engine.categorize("com.custom.gameone");
        assert_eq!(a1.category, AppCategory::Game);

        let a2 = engine.categorize("com.custom.gamethree");
        assert_eq!(a2.category, AppCategory::Game);

        let _ = std::fs::remove_file(&tsv_path);
        let _ = std::fs::remove_file(&cfg_path);
    }
}
