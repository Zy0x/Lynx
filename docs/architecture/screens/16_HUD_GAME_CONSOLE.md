# HUD display and audio console

## 1. Identity and UI

The Display & Audio Add-ins subpage uses `HudConsoleControls`. It exposes actual media-volume steps, brightness-mode lock and a session screen-timeout request using shared 48 dp controls and 12 sp labels.

## 2. State and lifetime

`HudGameConsole` exposes an immutable StateFlow. Volume is refreshed only while the console is visible and after an explicit user change. Display-setting journals are restored on HUD teardown or recovered on the next HUD start. No display setting is written by Compose.

## 3. Parameters

The media slider uses AudioManager's actual maximum step and is disabled for fixed-volume devices. Its change is a deliberate persisted user preference. Brightness lock selects manual mode without changing brightness or pretending to measure touch response. The screen-timeout request is at least 30 minutes and never shortens an already longer timeout. It does not prevent the power button from turning the screen off.

## 4. Platform mapping

Audio uses `AudioManager.STREAM_MUSIC` with the normal MODIFY_AUDIO_SETTINGS permission. Root display writes are limited to `Settings.System.screen_brightness_mode` and `screen_off_timeout`, validated and read back. There is no direct backlight sysfs, thermal or hardware-protection manipulation.

## 5. Restoration and errors

A per-key write-ahead journal preserves the previous value and the exact last owned value. An external user change wins over teardown restoration. Failed writes do not replace a valid earlier ownership record. Missing root disables display controls; missing/fixed audio capability disables the media slider. The existing Tools brightness control uses this same controller, avoiding a second source of truth.

## Compact detail follow-up

The RC260 layout uses Audio / Display tabs. Audio contains only actual volume and its slider; Display contains brightness lock and the existing screen-timeout request. The restoration note remains visible. Switching tabs does not change settings or stop the existing session. Shared navigation uses `HudSubpages`; details are in `37_HUD_DETAIL_NAVIGATION.md`.
