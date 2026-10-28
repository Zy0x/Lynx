# HUD Tools: Automation

## 1. Identity and structure

Tools → Automation composes `HudAutomationControls` and the extracted `HudSceneDiagnostics`. The previous duplicate Auto-Skip master switch in the diagnostic disclosure is removed; the automation controls remain its single settings entry point. Back returns to Tools.

## 2. State and flow

The host supplies preferences, actual skipping/paused/tapping state, and the existing snapshot result. Preference changes still use `HudPreferencesStore`. Scene capture invokes the existing explicit controller action. Navigation performs no capture or game input.

## 3. Controls and defaults

Existing Auto-Skip speed, enabled state and choice positioning retain their stored values and bounds, documented in `19_HUD_AUTOMATION_CONTROLS.md`. Diagnostics starts collapsed and exposes capture only while the assistant is enabled. Localized status distinguishes paused, actively skipping and standby.

## 4. Hardware and storage mapping

No sysfs path is added. The existing controller/recognizer owns screen inspection and input. Explicit user-triggered scene diagnostics retain their product output under `/sdcard/Lynx/scenes/`; this is distinct from development test artifacts, which must use Debug on an authorized test device.

## 5. Safety and verification

The redesign preserves package guards and existing scene-verdict semantics. It reports matching-pixel/threshold/zone telemetry without changing recognition thresholds. No diagnostics capture, automated game interaction or device storage operation is exercised in final validation for this task.

## Compact detail follow-up

RC260 separates Controls, Settings and Diagnostics into compact tabs. Only one body is composed at a time. The master/status appears in Controls, speed and choice position in Settings, and the explicit diagnostic capture in Diagnostics. Internal saveable drafts survive group changes; explicit navigation returns outer scroll to the top.
