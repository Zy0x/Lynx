# HUD session summary

## 1. Identity and UI

`HudSessionControls` is a dedicated Add-ins subpage with duration, initial battery level, current battery level and the foreground package. It uses readable stacked fields in narrow portrait content.

## 2. State and lifetime

The session starts when `HudGamingAddins` is created, using SystemClock elapsed realtime. UI updates the displayed elapsed duration once per second only while visible. The first valid native battery sample is retained as the initial level; the HUD snapshot supplies the current reading. Stopping/restarting the HUD starts a new session.

## 3. Parameters

This is HUD-session duration, explicitly labelled, rather than an invented measure of active game time. No guessed battery drain, average FPS, synthetic temperature or hidden activity history is computed. Unknown data stays as an em dash.

## 4. Data mapping

Battery values come from the existing validated native `BATTERY_LEVEL` reading. Foreground identity comes from the same HudSnapshot consumed by the other pages. No shell, sysfs write or additional foreground polling is performed by this page.

## 5. Validation and error handling

Only valid 0–100 percent readings establish a baseline, including a valid zero. Displayed values retain the snapshot availability semantics. The initial battery and monotonic start survive page navigation for the current HUD lifetime; they do not pretend to be a historical game-session database.
