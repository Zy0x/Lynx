# Responsive HUD windows, FPS pin and gestures

## 1. Identity and Compose structure

Physical review of RC260 found wrapped numeric units in the two-cell pinned capsule, a tall home composition clipping its final action row in landscape, an inert collapsed-handle tap, and window width not being updated by the orientation clamp. Native MainActivity also requested portrait explicitly, producing a letterboxed/narrow settings view on a rotated display.

`HudViewport.kt` owns safe geometry. `HudPanelShell` uses the complete panel width without a protruding extra ear. The labeled rail is centered and has 52 dp minimum destinations. The home readout puts FPS/CPU/GPU in one measured row when space permits; landscape quick actions use up to three columns, portrait up to two. Larger text reflows to fewer columns. Drawer and mini surfaces are opaque to prevent underlying text from bleeding through.

`HudMiniOverlay` shows one complete reading per pager page. FPS is always the first page, followed by selected optional metrics in catalog order. `HudMiniPolicy` supplies that shared selection to both Compose and touch arbitration, including the case of only one non-FPS optional selection. A 48×72 dp handle target contains the 12×48 dp visual grip. A tap opens the drawer; swiping its grip inward opens it, while swiping a multi-page capsule pages metrics.

Native `GameHudSubscreen` uses a compact master and Mode / Mini / Windows / Recorder groups. MainActivity's orientation is unspecified so it follows Android/user policy; the application does not enable auto-rotate or change orientation settings itself.

## 2. State, gestures and process synchronization

`HudSidebarHostView` owns arbitration between tap, horizontal metric paging, inward grip swipe and drag. It forwards content interactions to Compose; expanded-header outward swipe closes the drawer. Back/Close touch regions are excluded from the expanded-header gesture zone. Cancelled touches never synthesize an open action. Drag-end saves the existing dock/orientation position preferences.

`HudPreferenceSync` connects one strongly held SharedPreferences listener and a package-scoped, non-exported receiver per process. Typed key patches synchronize both live caches for the app and `:hud`, including pin/metric changes and raw service dock/position preferences. Same-process echoes are ignored by origin PID; applying a remote patch suppresses rebroadcast. Only existing HUD/auto-skip preference namespaces are accepted. The receiver never invokes a root controller, launches a service or changes hardware. Disk persistence remains the existing preference file; a restarted process loads that file normally.

The service uses the existing `HudAutoSkipWindow`, display-bounds and foreground-notification helpers instead of retaining duplicate window implementations. The badge additionally observes the existing actual-executing state. Process-liveness checks move unchanged to `HudServiceLiveness`. Macro input, foreground/package guards and hardware controllers are unchanged.

## 3. Geometry and configuration

Panel width is `min(safeWidth - 8, 360)` dp in portrait and `min(safeWidth - 8, 440)` dp in landscape. Portrait height is `min(safeHeight * .65, 480)` dp and anchors at the safe bottom. Landscape uses safe height and anchors at the safe top. Every clamp recomputes both requested pixel width and position, so an already-open drawer resizes on rotation. Manual safe insets are applied once; the host disables the additional WindowManager fit-inset pass.

Pin width is measured from localized labels and numeric strings with explicit padding, capped at 216 dp while reserving the 48 dp grip and gap. A value and unit occupy one line in one page rather than competing for half a row. Optional selection remains stored unchanged; pinning an empty optional selection still exposes FPS. The pin action label explicitly says Pin FPS. Gesture exclusion covers only the grip, not the entire metric capsule.

## 4. Hardware and Android boundary

Telemetry still uses `HudSnapshot` and its existing freshness/status/unit rules. FPS on the Lynx activity is not a game-performance benchmark. UI navigation and pinning introduce no kernel writes, tuning values, root module deployment or thermal changes. WindowManager owns overlay placement; Android display insets and physical dimensions determine bounds. Existing classic layout styles remain available.

## 5. Validation and limits

Release fixtures cover nonempty FPS fallback, catalog ordering, a single optional metric producing two pages, panel safe-width/height bounds and orientation-specific caps. Runtime locale-format fixtures remain required. UI verification must separately cover grip tap, outside collapse, left/right docking and drag, capsule swipe without drawer opening, app-to-overlay and overlay-to-app pin synchronization, and rotation while the drawer is already open. No game input or tuning action is needed for these checks.

The user explicitly switched active testing from RN7 to Infinix X698 during this task. RN7 was no longer operated after that instruction. Infinix identity is verified via the repository discovery helper before installation. Screenshots use `adb exec-out screencap -p` directly to the PC, with no remote image/dump file. The original Infinix rotation lock and user rotation are both 0, and ignore-orientation-request is false; temporary landscape inspection restores them in a finally block. The pre-build forced-portrait inspection is distinct from native orientation support in the corrected APK. Rendering results and any remaining unverified cases must be recorded after the final physical review.
