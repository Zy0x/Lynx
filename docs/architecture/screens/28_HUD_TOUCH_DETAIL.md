# HUD Touch detail

## 1. Identity and structure

`HudTouchPage.kt` presents Controls / Zones / Test tabs. Controls contains only the existing driver boost switch and backend identity. Zones opens the existing `HudZoneTouchControls` directly, without nesting it inside a second Touch card. Test contains the event pad and measured statistic grid.

## 2. State and flow

Capability and enabled state come from `HudTouchStatus`. The event sample is retained above the tab boundary. The pad only processes UI pointer events while Test is composed; switching tabs does not request a touch-driver write.

## 3. Parameters and defaults

Controls opens first. Existing esports, sampling, touch-slop and edge-zone preferences retain their saved values. Edge deadzone remains 0–32 pixels. Sliders and selectable actions use at least 48 dp targets. The test's rate, interval and finger-count readings use a maximum of two measured columns.

## 4. Hardware mapping

Existing capability/zone-touch managers remain responsible for platform settings and recognized touch nodes. Pointer historical/current timestamps retain the existing event-interval calculation. UI event rate is not digitizer hardware sampling rate or touch-to-photon latency.

## 5. Boundaries and validation

Unsupported/locked driver controls remain disabled. The layout does not change touch calculations, hardware discovery or protection settings. Local tests/build do not establish physical touch responsiveness or event-pad rendering.
