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
    pub target_profile: Option<String>,
}

pub struct RulesEngine {
    custom_games: Vec<String>,
    app_rules: Vec<(String, String)>, // (packageName lowercase, targetProfile lowercase)
}

impl RulesEngine {
    pub fn new() -> Self {
        let mut engine = Self {
            custom_games: Vec::new(),
            app_rules: Vec::new(),
        };
        engine.load_default_custom_lists();
        engine
    }

    /// Loads custom target apps from user storage, module root, TSV rules, app_rules.json, and config.json.
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

        // Check app_rules.json (Per-App rules from Companion APK - highest priority)
        let json_candidates = [
            "/data/adb/modules/Lynx/core/app_rules.json",
            "/data/adb/lynx/app_rules.json",
            "/storage/emulated/0/Lynx/app_rules.json",
            "./core/app_rules.json",
        ];
        for &rule_path in &json_candidates {
            let p = PathBuf::from(rule_path);
            if p.exists() {
                self.load_app_rules_json(&p);
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

    pub fn load_app_rules_json(&mut self, path: &Path) {
        let Ok(content) = fs::read_to_string(path) else { return };
        for obj_block in content.split('{') {
            let Some(end_idx) = obj_block.find('}') else { continue };
            let block = &obj_block[..end_idx];

            let pkg = extract_json_string(block, "packageName");
            if let Some(pkg) = pkg {
                let pkg_lower = pkg.trim().to_lowercase();
                if pkg_lower.is_empty() {
                    continue;
                }
                let profile = extract_json_string(block, "targetProfile")
                    .unwrap_or_else(|| "performance".to_string())
                    .trim()
                    .to_lowercase();
                let enabled = extract_json_bool(block, "enabled").unwrap_or(true);

                if enabled {
                    if !self.custom_games.contains(&pkg_lower) {
                        self.custom_games.push(pkg_lower.clone());
                    }
                    self.app_rules.retain(|(p, _)| p != &pkg_lower);
                    self.app_rules.push((pkg_lower, profile));
                } else {
                    self.custom_games.retain(|p| p != &pkg_lower);
                    self.app_rules.retain(|(p, _)| p != &pkg_lower);
                }
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
                    if !pkg.is_empty() {
                        if !self.custom_games.contains(&pkg) {
                            self.custom_games.push(pkg.clone());
                        }
                        if parts.len() >= 2 {
                            let prof = parts[1].trim().to_lowercase();
                            if !prof.is_empty() {
                                self.app_rules.retain(|(p, _)| p != &pkg);
                                self.app_rules.push((pkg, prof));
                            }
                        }
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
        // 1. Exact match takes absolute priority
        let exact_rule = self.app_rules.iter().find(|(p, _)| p == &pkg_lower);
        // 2. Substring match finds the longest matching pattern (most specific match)
        let substring_rule = self
            .app_rules
            .iter()
            .filter(|(p, _)| pkg_lower.contains(p.as_str()))
            .max_by_key(|(p, _)| p.len());

        let custom_target_profile = exact_rule
            .or(substring_rule)
            .map(|(_, prof)| prof.clone());

        // ── Tier 1: Exact Benchmark Signatures ──────────────────────────────
        const BENCHMARKS: &[&str] = &[
            "com.antutu.abenchmark",
            "com.antutu.benchmark.full",
            "com.antutu.benchmark.full.lite",
            "com.antutu.benchmark",
            "com.primatelabs.geekbench5",
            "com.primatelabs.geekbench6",
            "com.futuremark.pcmark.android.benchmark",
            "com.futuremark.3dmark.android",
            "com.ludashi.benchmark",
            "com.andromeda.androbench2",
            "skynet.cputhrottlingtest",
            "com.greenecomputing.flint",
        ];
        let is_benchmark = BENCHMARKS.contains(&pkg_lower.as_str())
            || BENCHMARKS.iter().any(|b| pkg_lower.starts_with(b));
        if is_benchmark {
            // If the user configured an EXACT rule for this benchmark package, respect it.
            // Otherwise, default to "extreme" so generic substring rules (like legacy "antutu|performance")
            // never downgrade a recognized benchmark!
            let target = exact_rule
                .map(|(_, prof)| prof.clone())
                .or(custom_target_profile)
                .unwrap_or_else(|| "extreme".to_string());
            let final_target = if target == "performance" && exact_rule.is_none() {
                "extreme".to_string()
            } else {
                target
            };
            return ForegroundApp {
                package: package.to_string(),
                category: AppCategory::Benchmark,
                confidence: Confidence::High,
                target_profile: Some(final_target),
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
                target_profile: custom_target_profile,
            };
        }

        // ── Tier 3: User Custom Applist (applist_perf.txt / app_rules.json) ─
        for custom in &self.custom_games {
            if pkg_lower.contains(custom) {
                return ForegroundApp {
                    package: package.to_string(),
                    category: AppCategory::Game,
                    confidence: Confidence::High,
                    target_profile: custom_target_profile,
                };
            }
        }

        // ── Tier 4: Exact Known Media / Video Streaming Signatures ──────────
        const MEDIA_APPS: &[&str] = &[
            "com.spotify.music",
            "com.google.android.youtube",
            "app.revanced.android.youtube",
            "com.google.android.apps.youtube.music",
            "com.netflix.mediaclient",
            "com.apple.android.music",
            "org.videolan.vlc",
            "tv.twitch.android.app",
            "com.mxtech.videoplayer.ad",
            "com.mxtech.videoplayer.pro",
            "com.disney.disneyplus",
            "com.amazon.avod.thirdpartyclient",
            "com.twitter.android",
            "com.zhiliaoapp.musically",
        ];
        if MEDIA_APPS.contains(&pkg_lower.as_str()) {
            return ForegroundApp {
                package: package.to_string(),
                category: AppCategory::Media,
                confidence: Confidence::High,
                target_profile: custom_target_profile,
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
                target_profile: custom_target_profile,
            };
        }

        // ── Tier 6: Keyword / Publisher Heuristics ──────────────────────────
        if pkg_lower.contains("benchmark") || pkg_lower.contains("throttling") {
            return ForegroundApp {
                package: package.to_string(),
                category: AppCategory::Benchmark,
                confidence: Confidence::Medium,
                target_profile: custom_target_profile.or_else(|| Some("extreme".to_string())),
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
                target_profile: custom_target_profile,
            };
        }

        // ── Tier 7: Default Normal Application ──────────────────────────────
        ForegroundApp {
            package: package.to_string(),
            category: AppCategory::Normal,
            confidence: Confidence::Low,
            target_profile: custom_target_profile,
        }
    }
}

fn extract_json_string(block: &str, key: &str) -> Option<String> {
    let key_pattern = format!("\"{}\"", key);
    let key_pos = block.find(&key_pattern)?;
    let rem = &block[key_pos + key_pattern.len()..];
    let colon_pos = rem.find(':')?;
    let val_rem = &rem[colon_pos + 1..];
    let first_quote = val_rem.find('"')?;
    let second_quote = val_rem[first_quote + 1..].find('"')?;
    Some(val_rem[first_quote + 1..first_quote + 1 + second_quote].to_string())
}

fn extract_json_bool(block: &str, key: &str) -> Option<bool> {
    let key_pattern = format!("\"{}\"", key);
    let key_pos = block.find(&key_pattern)?;
    let rem = &block[key_pos + key_pattern.len()..];
    let colon_pos = rem.find(':')?;
    let val_rem = rem[colon_pos + 1..].trim_start();
    if val_rem.starts_with("true") {
        Some(true)
    } else if val_rem.starts_with("false") {
        Some(false)
    } else {
        None
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

        let twitter = engine.categorize("com.twitter.android");
        assert_eq!(twitter.category, AppCategory::Media);
        assert_eq!(twitter.confidence, Confidence::High);

        let tiktok = engine.categorize("com.zhiliaoapp.musically");
        assert_eq!(tiktok.category, AppCategory::Media);
        assert_eq!(tiktok.confidence, Confidence::High);
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

    #[test]
    fn test_benchmark_exact_match_over_substring_rule() {
        let mut engine = RulesEngine::new();
        engine.app_rules.push(("antutu".to_string(), "performance".to_string()));
        engine.app_rules.push(("com.antutu.abenchmark".to_string(), "extreme".to_string()));

        let app = engine.categorize("com.antutu.ABenchMark");
        assert_eq!(app.category, AppCategory::Benchmark);
        assert_eq!(app.target_profile, Some("extreme".to_string()));
    }

    #[test]
    fn test_benchmark_generic_substring_never_downgrades_to_performance() {
        let mut engine = RulesEngine::new();
        engine.app_rules.push(("antutu".to_string(), "performance".to_string()));

        let app = engine.categorize("com.antutu.ABenchMark");
        assert_eq!(app.category, AppCategory::Benchmark);
        assert_eq!(app.target_profile, Some("extreme".to_string()));
    }
}
