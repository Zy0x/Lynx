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

## Final Release validation

The frozen validation source contained 470 hashed source/build files; all remained unchanged during the final build. `:app:testReleaseUnitTest :app:assembleRelease` completed successfully with R8 and resource shrinking (5m 55s). All 48 JVM tests passed, with zero failures, errors or skipped tests. The same floating/native sources passed 84 Rust host tests. Android catalog parity and format-placeholder checks passed. The full repository locale validator still reports 1,304 existing findings in unrelated UI/WebUI/comment text; the repository-wide validator is not green. Eleven existing unrelated Kotlin files exceed the 699-line budget; the new floating files stay within 400 lines.

The Release APK retains the existing versionName `3.2.26` and versionCode `20261270`. It was signed with the existing device-compatible certificate (SHA-256 `0cad9868093f1e5a85a01eccc28fa13645f6972cd710668f312b7d3b72505184`); APK v2/v3 signatures verified. ZIP inspection confirmed the APK-owned ARM64 ELF collector and the root service/AIDL/window classes. The unsupported `--activity-new-task` flag is absent. Exact mapping, JVM results, source manifest, APK and logs are retained outside Git at `D:/temp/lynx-floating-reference-20261023/`.

Following reference discovery and `lavender` / `sdm660` identity verification, this APK was installed on Redmi Note 7 under the user's explicit install-without-opening exception. Installation succeeded. Reading the installed public APK directly through ADB stdout produced the same SHA-256 as the signed artifact: `699b4ca42897f569681305f3c700ec58e910db92ab6cd875d00608cb3b7db5b9`. No application launch or UI automation was performed, and no remote test artifacts were written.

This validates compilation, host behavior, packaging, signing and installation only. It does not validate live external-app rendering, input/IME, vendor display policy or Android 10–16 compatibility. Lynx Virtual is Lynx's APK-owned root implementation inspired by the virtual-display approach; LMO source/APK is not embedded. The explicitly separate ROM backend may use the already installed LMO or native ROM freeform.

## Independent engine hardening follow-up

The [dedicated engine architecture](architecture/screens/12_LYNX_FREEFORM_ENGINE.md) now records viewport-sized rendering, bounded allocation/density, serialized and conflated display resizing, stale-result suppression, gesture cancellation, frame-coalesced window movement and native window chrome. Minimized windows retain their TextureView allocation while detaching output. New controls include changing portrait/landscape shape without changing the main display's rotation preference. Root task interfaces are selected/cached by known signature, owned-display rotation has explicit adapters, IME policy failure is nonfatal and visible, and display/input/launch/frame/resize failures have localized messages. LMO remains a reference; no third-party freeform APK or source file was embedded.

The final frozen snapshot contained 481 hashed source/build files and remained unchanged during validation. Release `:app:testReleaseUnitTest :app:assembleRelease` succeeded in 5m 8s with R8/resource shrinking. All 57 JVM tests and 84 Rust host tests passed. New fixtures cover render allocation/density, Android-family interface shapes/flags, rapid resize requests, stale failures and closure during IPC. Android catalog/format validation passed. The full locale validator reports 1,300 findings outside the changed engine; there are no findings naming the new/changed floating engine files. Ten unrelated existing Kotlin files exceed 699 lines; all floating files remain under 400 lines. These repository-wide failures remain outstanding.

The APK preserves the existing `3.2.28` / `20261272` metadata from the concurrent repository baseline; this work did not increment metadata. APK v2/v3 signatures verify with the previously installed certificate. ELF/DEX inspection confirmed the ARM64 collector, root AIDL service and new renderer/resize/chrome/policy classes. The exact APK, mapping, test results, logs and source manifest remain outside Git in `D:/temp/lynx-floating-engine-20261024/`.

Reference discovery initially found no connected device and later identified Redmi at its changed network address. After verifying `lavender` / `sdm660` and checking for a newer installed version, Release 3.2.28 was installed successfully under the user's install-without-opening authorization. The installed public APK was streamed to the PC and exactly matched SHA-256 `c7941c01aa1a13dfb2871e5475f796b1b8e42279781026d3d6df7021862fc725`. No app launch, UI automation, root-module install, hardware write or remote test-file creation occurred. X698 discovery returned no target.

Android 10–17 remains a compatibility target. The Android 17 primary documentation and a draft scrcpy desktop-launch proposal were reviewed; no Android 17 framework binary or device was tested. Live visual stability, input/IME, app-specific behavior, memory/power and performance remain unverified. Host tests and a successful install do not establish universal device support or measured performance gains.
