# Lynx Vision AI — Scene Training & Calibration Report (V4 Multi-Topology Edition)
> Evaluated **30 captured Genshin scenes** across standard dialogues, dimmed cutscene controls, special CG narrations, black interstitials, open world, menus, and start/loading screens.

## 1. Classification Summary
| Scene Type | Count | Percentage | Autonomous Action | Vision Signature / Anti-False-Positive Shield |
| :--- | :--- | :--- | :--- | :--- |
| **`OPEN_WORLD`** | 12 | 40.0% | 🛡️ Siaga Hening (0-Tap Player Control) | Active Green HP Bar Shield / Zero Disc Pairs |
| **`START_SCREEN`** | 4 | 13.3% | 🛡️ Siaga Hening (0-Tap Suppressed) | Celestia Sky Profile & Zero Dialogue Anchors |
| **`WHITE_LOADING`** | 1 | 3.3% | 🛡️ Siaga Hening (0-Tap Suppressed) | White Ratio >= 85% |
| **`BLACK_INTERSTITIAL`** | 5 | 16.7% | ⚡ Prompt Confirmation Tap (Coords Narration) | Black Header >= 80% + Margins >= 80% + Gold Prompt/Diamond |
| **`STANDARD_DIALOGUE`** | 7 | 23.3% | ⚡ Fast Cycling (SafeAdvance, Choices, Narration) | 2D Cream Discs (Adjacent + Split/Dimmed Siblings + Autoplay Strokes) |
| **`SPECIAL_NARRATION`** | 1 | 3.3% | ⚡ Prompt Confirmation Tap (Coords Narration) | Multi-Row Amber-Gold Prompt Text + Isolated Centered Diamond + No HP Bar |

## 2. Per-Scene Telemetry & Features
| Filename | Group | Predicted Scene | Discs (Adj/Split/Single) | Prompt Gold | Diamond | HP Bar |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `Screenshot_20261025-051421_Genshin Impact.png` | **NON_DIALOG** | **`OPEN_WORLD`** | 0 (0/0/0) | 0 (0r) | 369 | 0 |
| `Screenshot_20261025-051429_Genshin Impact.png` | **NON_DIALOG** | **`OPEN_WORLD`** | 0 (0/0/0) | 0 (0r) | 0 | 0 |
| `Screenshot_20261112-021815_Genshin Impact.png` | **NON_DIALOG** | **`OPEN_WORLD`** | 1 (0/0/0) | 0 (0r) | 0 | 0 |
| `scene_20261028_012101_full.png` | **NON_DIALOG** | **`START_SCREEN`** | 0 (0/0/0) | 0 (0r) | 307 | 0 |
| `scene_20261028_012707_full.png` | **NON_DIALOG** | **`WHITE_LOADING`** | 0 (0/0/0) | 0 (0r) | 0 | 0 |
| `scene_20261028_012723_full.png` | **NON_DIALOG** | **`START_SCREEN`** | 0 (0/0/0) | 0 (0r) | 307 | 0 |
| `scene_20261028_012726_full.png` | **NON_DIALOG** | **`START_SCREEN`** | 0 (0/0/0) | 0 (0r) | 307 | 0 |
| `scene_20261028_012727_full.png` | **NON_DIALOG** | **`START_SCREEN`** | 0 (0/0/0) | 0 (0r) | 307 | 0 |
| `scene_20261028_022228_full.png` | **NON_DIALOG** | **`OPEN_WORLD`** | 0 (0/0/0) | 2 (0r) | 0 | 3943 |
| `scene_20261028_025228_full.png` | **DIALOG** | **`BLACK_INTERSTITIAL`** | 0 (0/0/0) | 2854 (23r) | 88 | 0 |
| `scene_20261028_032655_full.png` | **DIALOG** | **`BLACK_INTERSTITIAL`** | 0 (0/0/0) | 2854 (23r) | 90 | 0 |
| `scene_20261028_033521_full.png` | **DIALOG** | **`BLACK_INTERSTITIAL`** | 0 (0/0/0) | 2854 (23r) | 70 | 0 |
| `scene_20261028_033609_full.png` | **DIALOG** | **`BLACK_INTERSTITIAL`** | 0 (0/0/0) | 2854 (23r) | 70 | 0 |
| `scene_20261028_033638_full.png` | **DIALOG** | **`BLACK_INTERSTITIAL`** | 0 (0/0/0) | 2854 (23r) | 82 | 0 |
| `scene_20261108_194201_full.png` | **DIALOG** | **`STANDARD_DIALOGUE`** | 4 (2/4/0) | 0 (0r) | 70 | 0 |
| `scene_20261112_021838_full.png` | **NON_DIALOG** | **`OPEN_WORLD`** | 1 (0/0/0) | 0 (0r) | 0 | 0 |
| `scene_20261112_022416_full.png` | **NON_DIALOG** | **`OPEN_WORLD`** | 0 (0/0/0) | 6 (0r) | 0 | 3930 |
| `scene_20261112_022431_full.png` | **DIALOG** | **`STANDARD_DIALOGUE`** | 3 (2/1/0) | 0 (0r) | 0 | 0 |
| `scene_20261113_145636_full.png` | **NON_DIALOG** | **`OPEN_WORLD`** | 0 (0/0/0) | 0 (0r) | 0 | 3914 |
| `scene_20261116_153051_full.png` | **NON_DIALOG** | **`OPEN_WORLD`** | 0 (0/0/0) | 0 (0r) | 0 | 4003 |
| `scene_20261116_154903_full.png` | **NON_DIALOG** | **`OPEN_WORLD`** | 0 (0/0/0) | 0 (0r) | 0 | 4052 |
| `scene_20261116_155030_full.png` | **NON_DIALOG** | **`OPEN_WORLD`** | 0 (0/0/0) | 0 (0r) | 0 | 3082 |
| `scene_20261117_193234_full.png` | **NON_DIALOG** | **`OPEN_WORLD`** | 0 (0/0/0) | 4 (0r) | 0 | 3454 |
| `scene_20261117_193813_full.png` | **NON_DIALOG** | **`OPEN_WORLD`** | 0 (0/0/0) | 6 (0r) | 395 | 3454 |
| `scene_20261117_200613_full.png` | **DIALOG** | **`STANDARD_DIALOGUE`** | 2 (0/1/0) | 0 (0r) | 74 | 0 |
| `scene_20261117_200736_full.png` | **DIALOG** | **`SPECIAL_NARRATION`** | 0 (0/0/0) | 3014 (23r) | 106 | 0 |
| `scene_20261117_200803_full.png` | **DIALOG** | **`STANDARD_DIALOGUE`** | 2 (0/1/0) | 0 (0r) | 141 | 0 |
| `scene_20261117_200819_full.png` | **DIALOG** | **`STANDARD_DIALOGUE`** | 2 (0/1/0) | 0 (0r) | 18 | 0 |
| `scene_20261117_200847_full.png` | **DIALOG** | **`STANDARD_DIALOGUE`** | 2 (0/1/0) | 0 (0r) | 113 | 0 |
| `scene_20261117_200916_full.png` | **DIALOG** | **`STANDARD_DIALOGUE`** | 2 (0/1/0) | 0 (0r) | 80 | 0 |
