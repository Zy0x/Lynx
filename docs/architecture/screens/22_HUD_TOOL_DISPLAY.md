# HUD Tools: Display and brightness

## 1. Identity and structure

Tools → Display opens `HudToolDisplay.kt`. Refresh selection starts expanded; brightness lock remains a separate disclosure. Header Back returns to the Tools category grid; the rail remains accessible.

## 2. State and flow

The host supplies supported rates, the current `HudReading`, requested-rate action state and brightness lock state. The page invokes existing `onHz` and `onToggleBrightnessLock` callbacks. It adds no polling.

## 3. Controls and defaults

Refresh choices come exclusively from the supplied display capabilities; there is no fixed 60/90/120 Hz list here. Choice selection compares actual readback within 0.05 Hz, and busy actions disable writes. The requested value remains distinct from actual display Hz. Brightness lock preserves the current session value and starts collapsed. Choices use at least 48 dp touch targets and reflow with measured labels.

## 4. Hardware mapping

The existing display controller owns Android physical display mode requests and readback. Brightness lock uses the existing console/display session. This page introduces no direct sysfs/procfs access or chipset-specific tuning.

## 5. Safety and verification

No supported rates produces the existing unsupported state. Opening this page does not change refresh rate or brightness. Capability guards, failed-write reporting and restoration remain in the existing controller. Release compilation and host tests are local; actual display switching remains unverified in this task at the user's request.

## Compact detail follow-up

RC260 separates Refresh and Brightness into compact tabs, retaining one corresponding disclosure/body at a time. Refresh choices use a maximum of two columns. Brightness starts expanded inside its selected group. Dynamic supported rates, actual/requested separation and controller callbacks are unchanged.
