# Floating apps audit — 24 October 2026

## Device evidence and boundaries

Read-only reference discovery identified Redmi Note 7 (`lavender`, `sdm660`), SDK 35 / Android 15. PackageManager reports `com.libremobileos.freeform`, versionName 15, versionCode 35, min/target SDK 35, installed in `/system_ext/priv-app/LMOFreeform`. The APK is privileged and system-owned. Its START_FREEFORM receiver is registered. No receiver was invoked, app opened, setting changed, or device artifact created during this inspection.

The inspected [LMO source](https://github.com/ProjectInfinity-X/packages_apps_LMOFreeform) declares system UID and talks to custom display services integrated into the framework. `LMOFreeformServiceHolder` creates a Surface-backed display and injects display-specific input. This is useful architectural reference, but installing its APK alone does not supply those ROM services. Lynx implements its own root display/input bridge instead. No third-party APK/module was installed.

X698 auto-discovery returned no target. Live rendering, keyboard routing, touch pass-through, rotation/cutout behavior, app-specific launch policy, screen-off cleanup and collector overhead remain unverified on X698. Redmi retains the user's install-only/no-launch boundary for Lynx.

## Repository comparison

| Project | Documented scope | Use for Lynx |
| --- | --- | --- |
| [Mi-Freeform](https://github.com/sunshine0523/Mi-Freeform) | README claims Android 8.1–14; virtual display architecture | Older architecture reference; not evidence for 15/16 |
| [YAMF](https://github.com/duzhaokun123/YAMF) | Deprecated; Android 12/12L/13 in README; Xposed required | Not a maintained universal base |
| [reYAMF](https://github.com/JuanArton/reYAMF) | Android 13+, LSPosed required; README includes Android 15 crash fix | Modern injected-window reference; cannot satisfy optional LSPosed by itself |
| [Z-Flow](https://github.com/relimus/Z-Flow) | Successor linked from archived Flyme-FreeForm; Xposed activation | UI/lifecycle reference; no verified Android 10–16 matrix stated |
| [MoreDisplay](https://github.com/Ynkcc/MoreDisplay/blob/main/CHANGELOG.md) | Initial 9 October 2026 release; 10 October fix for missing Surface/vsync and package visibility | Recent capability reference, not proof of broad compatibility |
| [scrcpy virtual displays](https://github.com/Genymobile/scrcpy/blob/master/doc/virtual-display.md) | Own display creation, resizing, IME policy and destruction | Privileged framework API/lifecycle reference; no desktop client is embedded |

The reviewed repositories do not establish one maintained, root-only, ROM-independent implementation proven across every Android 10–16 device. Newer source and a broad minimum SDK are not physical compatibility evidence. Lynx therefore probes real operations and fails closed. No source code from these projects was copied into Lynx during this audit.

## Implementation and remaining validation

See [HUD architecture](architecture/screens/11_FLOATING_HUD_SERVICE.md#6-apk-owned-floating-applications) for the root AIDL boundary, Surface ownership, input queue, launch readback and teardown. Existing compact HUD styling and 85% portrait sidebar width are retained; WebUI, classic floating styles, hardware protection and version metadata were not changed by this work.

Host fixtures exercise allocation bounds, small/cutout viewport clamping, exact package matching, accepted-broadcast rejection, real freeform evidence and new LMO virtual-task evidence. Rust fixtures also restore the 100-interval requirement for 1% low and reject ambiguous producing content layers. Cache release reports kernel acceptance only. APK build, host tests, locale results and artifact checksum are recorded after the final validation run below.
