# HUD recorder video settings

## 1. Identity and structure

The Video tab in `HudRecorderControls.kt` contains quality and bitrate choices in measured grids of at most two columns. Start/stop, audio and advanced toggles have separate tabs; no expanding settings pile is used.

## 2. State and flow

Quality, bitrate, compatibility and recording busy state come from the existing recorder flows. Tab changes never start or stop capture. Both Companion and overlay use this shared component.

## 3. Parameters and defaults

Existing Light/Balanced/Native quality indices and 4/8/16/24 Mbps UI options remain unchanged. Stored values are preserved. Video choices are disabled during capture; bitrate is additionally disabled in compatibility mode. These are UI options, not new guarantees about actual encoded bitrate or FPS.

## 4. Backend mapping

Explicit choices call the existing recorder preference setters. Encoder selection, output dimensions, root command, MediaStore publishing and compatibility fallback remain in the recorder backend.

## 5. Validation and boundaries

Release build/tests validate integration only. Actual encoder support, resolution, bitrate, audio and sustained capture are outside this local-only task. No recording is started on a physical device.
