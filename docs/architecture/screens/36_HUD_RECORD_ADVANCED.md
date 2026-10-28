# HUD recorder advanced settings

## 1. Identity and structure

The Advanced tab in `HudRecorderControls.kt` contains Compatibility, Stealth and the compatibility note. These no longer share a long expanded form with audio and bitrate grids.

## 2. State and flow

Compatibility, software-fallback, stealth and busy state remain recorder-owned flows. While capture is busy, the UI displays effective software/compatibility state and the existing handler refuses compatibility changes.

## 3. Parameters and defaults

Both controls preserve stored boolean preferences. No default, encoder target, recording limit, FPS target or overlay-capture policy changes in this layout work.

## 4. Backend mapping

Existing `setCompatibility` and `setStealthOverlay` methods remain the only mutation paths. These do not add a protected-content bypass or alter kernel/thermal settings.

## 5. Validation and boundaries

Readback/status handling stays with the recorder. Physical compatibility-mode capture, overlay omission and failure recovery have not been exercised in this task. Local Release validation is reported separately from historical recorder trials.
