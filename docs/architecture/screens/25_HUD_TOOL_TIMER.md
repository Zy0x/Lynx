# HUD Tools: Timer

## 1. Identity and structure

Tools → Timer opens `HudToolTimer.kt`. The timer starts expanded, with remaining time, Reset and a measured two-column preset grid. Back returns to Tools.

## 2. State and flow

`HudTools` retains saveable remaining seconds and selected preset. Its existing one-second coroutine decrements the value while the Tools host remains composed. This is a local UI countdown, not a persistent background alarm; switching to a different main tab retains state but does not run a background alarm service.

## 3. Controls and defaults

Default is zero/ready. Presets remain 30, 60, 120 and 180 seconds. Reset selects zero and is enabled only while time remains. Preset rows reflow for text width and preserve at least 48 dp touch targets.

## 4. Hardware mapping

No kernel, Android settings, root, wakelock or external storage access is used.

## 5. Safety and verification

The value is clamped at zero. Opening the page does not start a timer. Release build/tests cover integration; elapsed-time behavior and large-font rendering have not been physically verified in this task.

## Compact detail follow-up

RC260 places the remaining-time value above a full-width Reset action so scaled text cannot collide with a fixed-width reset button. Preset behavior, range, countdown state and local-only lifetime remain unchanged.
