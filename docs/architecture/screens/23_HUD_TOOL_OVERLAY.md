# HUD Tools: Overlay and windows

## 1. Identity and structure

Tools → Overlay opens the metric-selection and floating-window disclosures in `HudTools.kt`. `HudMetricSelection` and `HudFloatingSettings` retain their existing implementations. Back returns to the category grid.

## 2. State and flow

The page receives `HudPreferences` and `HudPreferencesStore`; updates still use the shared preferences change flow. The mini FPS pin remains reachable from the home quick actions. No telemetry collector or application window is started by navigation.

## 3. Controls and defaults

Both sections start collapsed. The metric group displays the existing selected count and available metric choices. Floating windows preserve the saved standalone/ROM backend and existing backend options. No preferences are reset by the redesign.

## 4. Hardware mapping

Metric configuration filters existing snapshot presentation. External application windows continue through the existing Freeform/ROM adapter; details and API limits are in `12_LYNX_FREEFORM_ENGINE.md`. This page adds no kernel nodes or shell commands.

## 5. Safety and verification

Capability checks, root access and unsupported states remain owned by the existing backends. Categories do not imply backend support. Local Release validation does not establish IME, floating-app or touch behavior on a physical device.

## Compact detail follow-up

RC260 separates Mini and Floating windows into compact tabs. `HudMetricSelection` additionally chooses one metric category at a time rather than stacking all category disclosures. `HudFloatingSettings` has Engine / Size / Applications / Information groups. Size uses a stacked label/value, two-column presets and 48 dp custom-size sliders. Existing preference defaults, size bounds and backends remain unchanged; these shared components also apply in Companion settings.
