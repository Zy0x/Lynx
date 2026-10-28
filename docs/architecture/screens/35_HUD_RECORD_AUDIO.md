# HUD recorder audio settings

## 1. Identity and structure

The Audio tab in `HudRecorderControls.kt` contains only the existing source grid and audio note. Controls, Video and Advanced are sibling tabs.

## 2. State and flow

The page observes recorder audio-source and capture-state flows. Selection invokes the existing `setAudioSource` callback. Navigating here requests no permission and starts no capture.

## 3. Parameters and defaults

Existing source indices remain Off, Microphone, Internal and Mixed. The saved selection is preserved. Sources reflow into at most two columns and are disabled while capture is busy.

## 4. Backend mapping

The recorder owns audio permission handling, capture support and muxing. UI selection is not evidence that internal/mixed audio is supported by the current Android version or application.

## 5. Validation and boundaries

No microphone/notification permission is granted by this task. Release compilation does not establish actual audio capture or synchronization; those require explicit physical verification.
