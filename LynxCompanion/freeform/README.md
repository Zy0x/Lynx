# Lynx Freeform Android library

APK-owned external-app windows using libsu root IPC and Surface-backed virtual displays. No HUD, Compose, Rust daemon, Lynx root module, LMO package or LSPosed dependency. Android 10–17 is the target range; device/ROM compatibility is not universally verified.

## Reuse as a Gradle module

Copy this directory, including `LICENSE`, into the host Android project. Add `include(":freeform")` to settings and `implementation(project(":freeform"))` to the host. The build needs Android Gradle Plugin 8.2.2 (or an independently validated compatible version), Kotlin 1.9.22, JDK 17, compile SDK 34+, Google/Maven Central and JitPack (`https://jitpack.io`). `consumer-rules.pro` preserves root/AIDL classes when the host shrinks its Release APK.

The host must obtain overlay permission, allow root, and declare package visibility for the target apps it launches. The library does not request broad package visibility or install any root module. Pass a context with the host's chosen locale. Construct and call the lifecycle API on the main thread:

```kotlin
val freeform = LynxFreeform(context, serviceScope, FreeformConfig(
    appearance = FreeformAppearance(showToolbar = false, cornerRadiusDp = 12),
    renderBudget = RenderBudget(maxPixels = 1_500_000, maxEdge = 1600),
    maxWindows = 4
))
freeform.launch("com.example.chat", widthPercent = 55, heightPercent = 50)
// Forward display/inset/viewport changes and close windows on screen-off.
freeform.viewportChanged()
freeform.closeWindows()
// Mandatory when the owning service/session ends; parent cancellation also closes it.
freeform.close()
```

Collect `freeform.events` for opening, actual first-frame readiness, resize, minimization, failure and closure. READY requires a verified owned-display task and a received frame; it does not certify IME or app-specific behavior. Events include actual display IDs only after verification and the display profile that successfully created the display. Parent coroutine cancellation closes owned windows; the host must still forward screen-off and display/inset changes promptly.

## UI and customization

Default chrome is a single 48dp header at normal/130%/200% fonts with 12sp title and no reserved footer. Its menu stays within the host window and provides back, minimize, maximize/restore, portrait/landscape shape, resize and a bottom-toolbar toggle. Choosing resize temporarily reveals a 48dp corner target; it disappears when the drag ends. No permanent overlay intercepts the app's bottom-corner controls in compact mode. Enlarged custom fonts grow the header instead of shrinking text. Toolbar mode reserves an additional 48dp and keeps its resize control in that toolbar.

`FreeformAppearance` exposes accent/surface colors, 0–32dp radius, 12–18sp title and initial toolbar visibility. `RenderBudget` supports 262,144–4,000,000 pixels and a 640–2048-pixel edge; allocations also pass root-side size validation. The library stores no global or HUD preferences. The host owns persistence, root permission UX and any additional controls. Colors must remain readable in the host theme. Runtime toolbar selection lasts for the current window.

## AAR and dependencies

Build `:freeform:assembleRelease :freeform:testReleaseUnitTest`. The Release AAR is generated under `build/outputs/aar/`. A raw AAR does **not** contain transitive libraries: consumers importing the file directly must also declare libsu service/core 5.2.2, kotlinx-coroutines-android 1.7.3, AndroidX annotation 1.7.1 and Kotlin stdlib 1.9.22 when not already supplied by the host. Using the source Gradle module retains normal dependency resolution. Do not omit the consumer rules from the artifact.

The library is GPL-3.0 as distributed with Lynx; retain the included license and project attribution. No source from LMO, Mi-Freeform or scrcpy is embedded in this library.

## Compatibility boundaries

Known task/rotation API signatures are selected by shape, not guessed Binder numbers. Creation tries primary, trusted-only, then basic display flags when the system rejects a capability; duplicate profiles are skipped. Unrelated errors are not retried, and activity launch is never repeated across profiles. All profiles retain own-content and destroy-on-removal behavior, without ALWAYS_UNLOCKED, global desktop-mode writes or main-screen rotation changes. Task, display identity and resize readback remain mandatory.

Root cannot guarantee vendor policy, hidden-interface availability, external-app resize behavior, keyboard routing or protected content. Report unsupported operations rather than claiming success. The current tests cover host policies and IPC ordering, not a complete Android 10–17 device matrix. No Xposed helper is shipped.
