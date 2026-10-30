# Responsive HUD windows, FPS pin and gestures

## 1. Identity and Compose structure

Physical review of RC260 found wrapped numeric units in the two-cell pinned capsule, a tall home composition clipping its final action row in landscape, an inert collapsed-handle tap, and window width not being updated by the orientation clamp. Native MainActivity also requested portrait explicitly, producing a letterboxed/narrow settings view on a rotated display.

`HudViewport.kt` owns safe geometry. `HudPanelShell` uses the complete panel width without a protruding extra ear. The labeled rail is top-aligned and has 60 dp minimum destinations (RC272 refinement). The home readout puts FPS/CPU/GPU in one measured row when space permits; landscape quick actions use up to three columns, portrait up to two. Larger text reflows to fewer columns. Drawer and mini surfaces are opaque to prevent underlying text from bleeding through.

`HudMiniOverlay` shows one complete reading per pager page. FPS is always the first page, followed by selected optional metrics in catalog order. `HudMiniPolicy` supplies that shared selection to both Compose and touch arbitration, including the case of only one non-FPS optional selection. A 48×72 dp handle target contains the 12×48 dp visual grip. A tap opens the drawer; swiping its grip inward opens it, while swiping a multi-page capsule pages metrics.

Native `GameHudSubscreen` uses a compact master and Appearance / Pin FPS / Windows / Record groups; the short localized labels keep all four primary groups visible in portrait. MainActivity's orientation is unspecified so it follows Android/user policy; the application does not enable auto-rotate or change orientation settings itself.

## 2. State, gestures and process synchronization

`HudSidebarHostView` owns arbitration between tap, horizontal metric paging, inward grip swipe and drag. It forwards content interactions to Compose; expanded-header outward swipe closes the drawer. Back/Close touch regions are excluded from the expanded-header gesture zone. Cancelled touches never synthesize an open action. Drag-end saves the existing dock/orientation position preferences.

`HudPreferenceSync` connects one strongly held SharedPreferences listener and a package-scoped, non-exported receiver per process. Typed key patches synchronize both live caches for the app and `:hud`, including pin/metric changes and raw service dock/position preferences. Same-process echoes are ignored by origin PID; applying a remote patch suppresses rebroadcast. Only existing HUD/auto-skip preference namespaces are accepted. The receiver never invokes a root controller, launches a service or changes hardware. A newly connected store requests an initial snapshot from the already-running peer before continuing with live patches. This corrects an app cache that was created before the overlay changed pin state. Snapshot requests use the same same-UID package boundary and allowed-key filter. Disk persistence remains the existing preference file.

The service uses the existing `HudAutoSkipWindow`, display-bounds and foreground-notification helpers instead of retaining duplicate window implementations. The badge additionally observes the existing actual-executing state. Process-liveness checks move unchanged to `HudServiceLiveness`. Macro input, foreground/package guards and hardware controllers are unchanged.

## 3. Geometry and configuration

Panel width is `min(safeWidth - 8, 360)` dp in portrait and `min(safeWidth - 8, 440)` dp in landscape. Portrait height is `min(safeHeight * .65, 440)` dp. Its header follows the saved handle position, clamped inside the safe display (RC272 refinement). Landscape uses safe height and anchors at the safe top. Every clamp recomputes both requested pixel width and position, so an already-open drawer resizes on rotation. Manual safe insets are applied once; the host disables the additional WindowManager fit-inset pass.

Pin width is measured from localized labels and numeric strings with explicit padding, capped at 216 dp while reserving the 48 dp grip and gap. A value and unit occupy one line in one page rather than competing for half a row. Optional selection remains stored unchanged; pinning an empty optional selection still exposes FPS. The pin action label explicitly says Pin FPS. Gesture exclusion covers only the grip, not the entire metric capsule.

## 4. Hardware and Android boundary

Telemetry still uses `HudSnapshot` and its existing freshness/status/unit rules. FPS on the Lynx activity is not a game-performance benchmark. UI navigation and pinning introduce no kernel writes, tuning values, root module deployment or thermal changes. WindowManager owns overlay placement; Android display insets and physical dimensions determine bounds. Existing classic layout styles remain available.

## 5. Validation and limits

Release fixtures cover nonempty FPS fallback, catalog ordering, a single optional metric producing two pages, panel safe-width/height bounds and orientation-specific caps. Runtime locale-format fixtures remain required. UI verification must separately cover grip tap, outside collapse, left/right docking and drag, capsule swipe without drawer opening, app-to-overlay and overlay-to-app pin synchronization, and rotation while the drawer is already open. No game input or tuning action is needed for these checks.

The user explicitly switched active testing from RN7 to Infinix X698 during this task. RN7 was no longer operated after that instruction. Infinix identity is verified via the repository discovery helper before installation. Screenshots use `adb exec-out screencap -p` directly to the PC, with no remote image/dump file. The original Infinix rotation lock and user rotation are both 0, and ignore-orientation-request is false; temporary landscape inspection restores them in a finally block. The pre-build forced-portrait inspection is distinct from native orientation support in the corrected APK. Rendering results and any remaining unverified cases must be recorded after the final physical review.


### Final physical review — RC264 / Companion 3.2.98 (2026-10-29)

The Release APK was built from saved revision `8970956` in an isolated checkout plus this HUD/version patch, avoiding unrelated concurrent charging work in the shared tree. `assembleRelease` and `testReleaseUnitTest` succeeded: 96 tests, zero failures/errors/skips. The packaged `lib/arm64-v8a/liblynxhud.so`, `assets/lynxd` and `assets/bin/lynxd` are present and byte-identical (1,129,472 bytes; SHA-256 `67c46cb124e5f2ec115430009a5534334ed54c461592e33c3e6bd7b8003f4284`). APK SHA-256: `3696d538978c26333248dfe9356da455099d0fe01584a63edffcda85b73343c7`.

Installed and reviewed directly on Infinix X698 / mt6781, Android 14, 1080×2460, density 416, font scale 1.0:

| Scenario | Observed result |
| --- | --- |
| Portrait open | Frame `[0,1211][936,2459]`; six quick actions visible in two columns, readable labeled rail and FPS/CPU/GPU summary. |
| Rotation while open | Landscape frame `[0,62][1144,1080]`; six quick actions in three columns; rotating back restores portrait bounds. |
| Handle/capsule tap | Opens the panel; tapping outside collapses it. |
| Mini pager swipe | Pages a single complete metric without opening the drawer; collapsed frame remains `[0,424][428,611]`. |
| Hold and drag/redock | Left to right produces `[652,424][1080,611]`; returning left restores `[0,424][428,611]`. |
| Native pin on/off | Collapsed width changes between 428 px pinned and 125 px grip-only. |
| Overlay pin to native | Native switch reflects the overlay change, including first opening of the settings store. |
| Native groups and subpages | Four primary settings tabs fit in portrait. Monitor and Boost navigation, Boost content scroll, Back and Close work without overlapping controls. |
| End state | HUD/pin off, dock/vertical position restored; accelerometer rotation 0, user rotation 0, ignore-orientation-request false. |

No Lynx fatal entry was observed in the bounded AndroidRuntime crash buffer inspected at the end. Screenshots were streamed directly to the PC and removed after visual analysis; no device screenshot or UI dump files were created. This validates layout/interaction on the listed device and configuration. Larger font sizes, other physical devices and actual gameplay performance were not part of this physical review; no tuning or charging action was executed.

The full locale validator remains failing with 1,242 existing findings when compared against the exact saved `8970956` baseline: zero added and zero removed by this final HUD patch. Default/Indonesian/English catalogs contain both new tab keys. All three manually changed Kotlin files remain below 700 lines (83, 81 and 92 lines). The repository-wide line audit still finds oversized files outside this HUD patch. The shared repository subsequently integrated these HUD edits into `1ed45b0` alongside RC265 charging work; the installed/reviewed APK and hash above refer specifically to isolated Companion 3.2.98.


## 6. Anchored drawer refinement — RC272 / Companion 3.3.6

The previous portrait drawer always opened at the bottom of the safe area, disconnected from the collapsed handle. The new portrait placement aligns the 56 dp header center with the 72 dp grip center using the saved orientation-specific handle fraction, then clamps the entire panel within the safe display. Closing restores the saved collapsed position; expanding never saves the expanded panel position as the handle location. Landscape continues to occupy the safe display height. Portrait height is now capped at 440 dp; widths remain 360/440 dp.

The outer shape attaches to the selected dock with 6 dp edge corners and 24 dp interior corners. A 220 ms entrance moves inward from the selected edge and fades in, using a Compose graphics layer without changing measured window bounds. Android's window still intercepts the expanded rectangle throughout that entrance. The animation does not run for telemetry updates or page selection. Closing remains immediate so no invisible expanded window intercepts input.

The navigation rail uses top-aligned 60 dp minimum destinations with a restrained mint surface/border and an edge marker for the active tab. It no longer fills the entire rail with a tall pill or centers four tabs in a large empty column. Navigation remains on the dock side and content scroll stays independent. A thin divider separates the header from the content. Graphite surfaces reduce the blue contrast between tiles; FPS/CPU/GPU readings use equal measured columns when each numeric value fits and stack when they do not. Quick action touch targets remain at least 56 dp high.

This refinement changes window geometry and presentation only. Existing profile, root, charging, recording and automation handlers remain unchanged. Tests cover handle-following geometry, monotonic placement through the safe area, clamping at the bottom and landscape invariance across densities. Final Release and physical review results are recorded below after validation.
