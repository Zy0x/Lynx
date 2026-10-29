# Landscape HUD and edge-constrained Auto-skip badge

## 1. Identity and layout

`HudSummaryContent` now uses the shared compact game-panel home in both orientations: FPS and CPU/GPU load, a collapsed profile group, then quick actions. Detailed frame/clock readings remain in Monitor. Applications are in their own Tools destination. The grouped panel contract is documented in `21_LYNX_ENGINE_GAME_PANEL.md`. Sidebar width has no unconditional 240 dp minimum that could exceed a smaller safe viewport.

`HudAutoSkipWindow` owns only the external badge window and drag behavior. `HudSmartTriggerOverlay` retains a 38 dp visual within a 48 dp touch target. At the bounds, its visual aligns to the corresponding edge of that target, removing the previous transparent gap without moving any part of the window outside the display.

## 2. State and lifecycle

Landscape uses the existing snapshot, profile actions and per-page scroll state. No telemetry collection changes are introduced. Badge visibility still follows interactive state, enabled preferences and supported foreground package. StateFlow supplies the actual running/paused icon state. The badge owns its visibility job, ComposeView and WindowManager registration and disposes them on HUD teardown.

## 3. Position parameters

The old fixed 42 dp placement calculation is replaced by measured window width/height, with a matching 48 dp initial estimate. The existing portrait/landscape x/y fraction preference keys are retained. Drag is direct and uses the device touch slop. Fractions use the available travel distance so both 0 and 1 map exactly to their respective bounds. A tap still delegates to the existing controller; this component never executes game input itself.

## 4. Physical coordinate mapping

Badge coordinates are absolute display pixels with TOP/LEFT gravity. Android 11+ automatic fit insets are disabled for this window so insets are not applied twice. Visible status/navigation bars are reserved once. Physical cutout rectangles are local obstacles: the badge can reach the rest of that edge while avoiding the actual camera/notch. Android 10 uses the existing layout-in-screen/short-edge cutout policy and available root insets. Framework restrictions on older ROMs remain outside the pure geometry tests.

Panel safe bounds retain their conservative system-bars/cutout policy. `HudDisplayBounds` centralizes those reads, and `HudForegroundNotification` owns the unchanged localized service notification. This extraction also reduces the overlay host below the 700-line source budget.

## 5. Validation and boundaries

`HudBadgeGeometryTest` covers all four edges, measured non-square windows, visible-bar margins, local cutouts, portrait/landscape fraction round trips and repeated out-of-range drags. Exact right/bottom edges include the full measured window, not the old size. The app is built and tested in Release; native stream and root hardware controllers are unchanged. Physical landscape checks and artifact results are recorded in the HUD layout audit. Automated game interaction is not used to validate the badge.

## Portrait follow-up

Portrait and landscape retain a compact labeled rail while at least 176 dp of content remains. The rail is bounded to 64–88 dp; long labels wrap. Smaller windows use horizontally scrollable tabs. Portrait is bottom-anchored inside the existing safe bounds and capped at 600 dp tall. Child headings and Back follow the category hierarchy. Layout tests check rail boundaries, label growth, density-aware bottom positioning and landscape's unchanged top anchor.

## Responsive window follow-up

RC261 supersedes the earlier 336/384 dp and 600 dp panel limits: full-width drawer caps are now 360 dp portrait / 440 dp landscape; portrait height is capped at 480 dp. The compact FPS/CPU/GPU row and three-column landscape quick actions avoid placing the final action row below the viewport at normal text scale. The service now actually instantiates the extracted `HudAutoSkipWindow` and forwards current mode/executing state. See `38_HUD_RESPONSIVE_WINDOWS.md`.
