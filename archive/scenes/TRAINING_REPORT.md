# Lynx Vision AI — Scene Training & Calibration Report
> Evaluated **11 captured scenes**.

## 1. Classification Summary
| Scene Type | Count | Percentage | Autonomous Action |
| :--- | :--- | :--- | :--- |
| **`START_SCREEN`** | 4 | 36.4% | 🛡️ Siaga Hening (0-Tap Suppressed) |
| **`WHITE_LOADING`** | 1 | 9.1% | 🛡️ Siaga Hening (0-Tap Suppressed) |
| **`OPEN_WORLD`** | 1 | 9.1% | 🛡️ Siaga Hening (0-Tap Player Control) |
| **`BLACK_INTERSTITIAL`** | 5 | 45.5% | ⚡ Prompt Confirmation Tap (Coords Narration) |

## 2. Per-Scene Telemetry & Features
| Filename | Predicted Scene | Confidence | Top-Left Dialogue Pixels | Prompt Gold Pixels | Black % | White % |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `scene_20261028_012101_full.png` | **`START_SCREEN`** | 0.92 | 0 | 0 | 10.8% | 3.0% |
| `scene_20261028_012707_full.png` | **`WHITE_LOADING`** | 0.99 | 0 | 0 | 0.4% | 99.0% |
| `scene_20261028_012723_full.png` | **`START_SCREEN`** | 0.92 | 0 | 0 | 0.7% | 0.6% |
| `scene_20261028_012726_full.png` | **`START_SCREEN`** | 0.92 | 0 | 0 | 0.7% | 0.7% |
| `scene_20261028_012727_full.png` | **`START_SCREEN`** | 0.92 | 0 | 0 | 0.7% | 0.6% |
| `scene_20261028_022228_full.png` | **`OPEN_WORLD`** | 0.90 | 0 | 0 | 0.3% | 1.0% |
| `scene_20261028_025228_full.png` | **`BLACK_INTERSTITIAL`** | 0.99 | 0 | 182 | 99.2% | 0.3% |
| `scene_20261028_032655_full.png` | **`BLACK_INTERSTITIAL`** | 0.99 | 0 | 182 | 97.8% | 1.0% |
| `scene_20261028_033521_full.png` | **`BLACK_INTERSTITIAL`** | 0.99 | 0 | 182 | 97.5% | 1.2% |
| `scene_20261028_033609_full.png` | **`BLACK_INTERSTITIAL`** | 0.99 | 0 | 182 | 99.0% | 0.4% |
| `scene_20261028_033638_full.png` | **`BLACK_INTERSTITIAL`** | 0.99 | 0 | 182 | 98.0% | 0.9% |

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
```

## 4. Retraining Guidelines
Whenever the user collects new scenes in `/sdcard/Lynx/scenes/`:
```bash
python tools/train_scene_classifier.py --pull --evaluate
```
