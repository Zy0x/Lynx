# Lynx Vision AI — Scene Training & Calibration Report (Triple-Shield Edition)
> Evaluated **11 captured scenes** with zero-false-positive gameplay safeguards.

## 1. Classification Summary
| Scene Type | Count | Percentage | Autonomous Action | Anti-False-Positive Shield |
| :--- | :--- | :--- | :--- | :--- |
| **`START_SCREEN`** | 4 | 36.4% | 🛡️ Siaga Hening (0-Tap Suppressed) | Sky Color Profile & 0 Matches |
| **`WHITE_LOADING`** | 1 | 9.1% | 🛡️ Siaga Hening (0-Tap Suppressed) | White Ratio >= 85% |
| **`OPEN_WORLD`** | 1 | 9.1% | 🛡️ Siaga Hening (0-Tap Player Control) | Green HP Bar / Colorful Terrain Shield |
| **`BLACK_INTERSTITIAL`** | 5 | 45.5% | ⚡ Prompt Confirmation Tap (Coords Narration) | Gold >= 15 && Margins >= 80% Black |

## 2. Per-Scene Telemetry & Features
| Filename | Predicted Scene | Confidence | TL Dialogue Pixels | Glyph Run | Prompt Gold | Margin Black % | Green HP Pixels |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `scene_20261028_012101_full.png` | **`START_SCREEN`** | 0.95 | 0 | 0 | 0 | 0.0% | 51 |
| `scene_20261028_012707_full.png` | **`WHITE_LOADING`** | 0.99 | 0 | 0 | 0 | 0.0% | 0 |
| `scene_20261028_012723_full.png` | **`START_SCREEN`** | 0.95 | 0 | 0 | 0 | 0.0% | 63 |
| `scene_20261028_012726_full.png` | **`START_SCREEN`** | 0.95 | 0 | 0 | 0 | 0.0% | 51 |
| `scene_20261028_012727_full.png` | **`START_SCREEN`** | 0.95 | 0 | 0 | 0 | 0.0% | 38 |
| `scene_20261028_022228_full.png` | **`OPEN_WORLD`** | 0.99 | 0 | 0 | 0 | 0.0% | 3152 |
| `scene_20261028_025228_full.png` | **`BLACK_INTERSTITIAL`** | 0.99 | 0 | 0 | 182 | 100.0% | 0 |
| `scene_20261028_032655_full.png` | **`BLACK_INTERSTITIAL`** | 0.99 | 0 | 0 | 182 | 100.0% | 0 |
| `scene_20261028_033521_full.png` | **`BLACK_INTERSTITIAL`** | 0.99 | 0 | 0 | 182 | 100.0% | 0 |
| `scene_20261028_033609_full.png` | **`BLACK_INTERSTITIAL`** | 0.99 | 0 | 0 | 182 | 100.0% | 0 |
| `scene_20261028_033638_full.png` | **`BLACK_INTERSTITIAL`** | 0.99 | 0 | 0 | 182 | 100.0% | 0 |

## 3. Calibrated Vision Bounds for Kotlin Engine
```kotlin
// 1. Dialogue warm cream signature (RGB)
val DIALOGUE_R = 200..250
val DIALOGUE_G = 195..245
val DIALOGUE_B = 180..235

// 2. Interstitial Narration prompt gold signature (RGB)
val PROMPT_GOLD_R = 170..255
val PROMPT_GOLD_G = 130..215
val PROMPT_GOLD_B = 0..35

// 3. Noise rejection: minimum dialogue glyph run length
val MIN_GLYPH_RUN_LENGTH = 3
val MIN_DIALOGUE_THRESHOLD_1080P = 18
val MIN_MARGIN_BLACK_RATIO = 0.80f
```

## 4. Retraining Guidelines
Whenever the user collects new scenes in `/sdcard/Lynx/scenes/`:
```bash
python tools/train_scene_classifier.py --pull --evaluate
```
