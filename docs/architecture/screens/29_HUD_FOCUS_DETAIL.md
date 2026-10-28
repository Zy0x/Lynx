# HUD Focus detail

## 1. Identity and structure

`HudFocusPage.kt` separates Focus from Glow. Focus shows banner suppression and a two-column DND mode grid. Glow reuses `HudGlowControls`; its permission/help/color/preview controls no longer push DND choices down the page.

## 2. State and flow

Focus observes the existing `HudShieldStatus`. Glow retains its existing preference and notification-listener flows. Selection does not grant notification access or change a mode.

## 3. Parameters and defaults

Focus opens first. DND mode values remain Off=0, Priority=1, Silent=2 and Alarms=3. The shield master uses actual readback. Glow enabled/color values are preserved; its explicit preview action remains separate.

## 4. Hardware mapping

The existing focus session owns notification/DND settings, verification and ownership journals. Glow uses its existing notification listener and overlay adapter. No kernel or sysfs control is added.

## 5. Boundaries and validation

Root/capability and busy guards remain. This task does not grant permissions, preview notifications or test DND on a device. Existing compare-before-restore behavior is unchanged.
