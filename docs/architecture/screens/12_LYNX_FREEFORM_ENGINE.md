# Lynx Freeform engine

## 1. Identity and window structure

This is the external-application engine selected by the existing `standalone` preference, labelled Lynx Virtual. It ships inside the Companion APK and binds an APK-owned libsu root service. It does not require the LMO package, a root-module installation, ROM windowingMode 5, or LSPosed. The separate `system_freeform` preference retains the explicit ROM/LMO path.

`feature/floating/StandaloneAppWindow.kt` owns the overlay and TextureView lifecycle. `FloatingWindowChrome.kt` renders native View chrome directly around the Surface: application icon/title, localized state, minimize and close in the header; back, portrait/landscape shape, maximize/restore, and resize in the footer. Icons are scalable strokes rather than font glyphs. Controls retain 48dp targets; labels use 12sp and the header grows with font scaling. Rounded OLED chrome has a thin cyan border without blur. The minimized bubble uses the installed application icon. These are real external application Surfaces, not embedded web substitutes.

## 2. Session state and rendering

`StandaloneApps` owns up to four windows per HUD session and authenticates a private `IFloatingRoot` connection. Windows progress through opening, first-frame readiness, resizing, minimization and closure. Launch verification and arrival of a rendered frame are separate conditions. IME policy configuration failure is shown without discarding an otherwise working display; this is a policy result, not proof of a working keyboard.

`FloatingRenderPolicy` derives buffer dimensions from the actual content viewport instead of the full phone width. Allocations are aligned to even pixels, limited to a 1600-pixel edge and 1,500,000 pixels, and never upscale ordinary host viewports. Density follows render scaling; narrow windows receive a minimum 320dp app layout where the supported density range permits it. For a 720×900 host, the new request is 648,000 pixels instead of the former 1,458,000 pixels derived from a 1080-pixel screen width. This is an allocation comparison, not measured FPS or power improvement.

`FloatingResizeQueue` has one worker and a conflated pending request. It debounces for 150ms, allows a synchronous Binder operation to finish, and then applies the latest target. Superseded results do not update the current UI or close a newer request. A close suppresses late commits. Width, height and density are verified together by the root service. Drag resizing stretches the existing Surface while the finger moves and requests a display resize at release. WindowManager updates are coalesced to animation frames.

Input keeps pointer identities and scales to the committed buffer dimensions. Starting a resize cancels any current app gesture and suppresses input until the latest resize settles and a new DOWN begins. The bounded input queue still preserves pointer boundaries. Screen-off and HUD stop close owned windows and displays; there is no collector loop or wakelock added by this engine.

Minimization retains the expanded column/TextureView allocation while only the overlay becomes a 56dp bubble. Its output Surface is detached by the root service. This avoids squeezing the TextureView between the header and footer into a zero-height render area. Restore returns the column to the actual window bounds before requesting any required display resize.

## 3. Controls and configuration

Existing width/height and engine preferences remain unchanged. Render limits are internal allocation safeguards, not synthetic device capabilities. Maximize fills the current safe area and toggles back to the previous box. The shape button swaps the content aspect within the safe area; it never changes the user's main-screen auto-rotation preference. Minimize detaches display output and restore reattaches it. Closing removes only owned-display tasks, never force-stops an entire package.

`RootFrameworkPolicy` recognizes known getTasks interface shapes rather than assuming one overload per Android version. Unknown shapes fail closed. Android 13+ gets trusted, own-group and disabled-touch-feedback flags; Android 14+ adds own focus. The engine does not set ALWAYS_UNLOCKED, add desktop system decorations, or change global freeform settings. Per-display rotation locking attempts only the owned virtual display ID, with the Android 10 two-argument and newer three-argument signatures. It never falls back to locking the default display. The rotation setting disappears with the virtual display.

## 4. Framework and hardware boundary

The root bridge uses Android DisplayManager, ActivityTaskManager, WindowManager and InputManager. No CPU/GPU sysfs or hardware-protection nodes are modified. The renderer consumes Android's app Surface directly without a video encode/decode or network streaming pipeline. `RootAndroidAccess` caches reflected task/input methods and isolates interface adaptation from presentation. A successful shell exit is insufficient: a task must belong to the requested application on the owned display. Resize readback must match the requested dimensions and density.

Structured failure codes are translated through `FloatingFailure` in default Indonesian, Indonesian and English catalogs. Display creation, activity launch, task verification, permission, frame timeout, input and resize errors have separate messages. Raw exception text stays in logs. Unsupported framework/vendor policy is reported; no dummy task ID or unconditional success is generated.

## 5. Compatibility, provenance and verification

Android 10–17 is a development target, not an established support matrix. Root cannot guarantee that every vendor permits every hidden API, application launch policy or IME route. No LSPosed helper is bundled; adding one later requires evidence of a specific missing capability. Future interfaces must be inspected and tested before compatibility is claimed.

| Android family | Interface strategy | Physical validation in this cycle |
| --- | --- | --- |
| 10 / 11 | Known one-argument task query; owned-display two-argument rotation lock | Not performed |
| 12 | Known three-argument task query; explicit physical-display IME policy | Not performed |
| 13–16 | Runtime known-signature selection, version-gated display flags, two/three-argument owned rotation adapter | Not performed; Redmi 15 is reference/install-only |
| 17 | Known signatures may be selected only if actually present; unknown interfaces fail closed | Not performed; shipped hidden interfaces not inspected |

The [LMO source](https://github.com/ProjectInfinity-X/packages_apps_LMOFreeform) uses system UID and ROM display services. Its lifecycle and window interactions are useful references, but its APK alone cannot provide those services. [Mi-Freeform](https://github.com/sunshine0523/Mi-Freeform) is a GPL reference for independent floating interaction; its stated historical Android range is not Android 17 evidence. [scrcpy](https://github.com/Genymobile/scrcpy), under Apache 2.0, provides maintained framework/display/input references. Lynx retains its own Kotlin/libsu implementation; no LMO, Mi-Freeform or scrcpy source file/binary is embedded by this change. Any later direct source port must retain the upstream license and attribution.

The inspected [scrcpy Android 16 QPR1/17 desktop-launch proposal](https://github.com/Genymobile/scrcpy/pull/6722) remains a draft. It concerns apps launched by a virtual desktop launcher and proposes system decorations/freeform display-area transactions. Lynx launches each requested app directly fullscreen on its owned display. This draft is neither universal compatibility evidence nor justification for unconditionally adding desktop mode to Lynx.

[Android 17 behavior changes](https://developer.android.com/about/versions/17/behavior-changes-all) identify app memory limits and changed IME visibility restoration after configuration changes. These require memory baselines and keyboard/configuration tests with external apps; smaller buffers alone do not prove compliance. The inspected public Android 17 documentation is not a substitute for inspecting the shipped hidden interfaces. No Android 17 device or framework binary has been validated in this cycle.

Host tests cover allocation/density across portrait/landscape viewports, supported/unknown framework signatures, display flags, concurrent resize requests, stale failures and closure during IPC. Release build, locale checks and installation evidence are recorded in [the floating audit](../../FLOATING_APPS_AUDIT_20261024.md). Live rendering, responsiveness, memory, IME, multitouch, cutouts, app-specific behavior and Android 10–17 devices remain separate physical validation work. Redmi remains install-only without launching; X698 discovery is required for active tests.
