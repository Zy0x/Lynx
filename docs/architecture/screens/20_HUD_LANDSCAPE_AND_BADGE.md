# Landscape HUD and edge-constrained Auto-skip badge

## 1. Identity and layout

`HudSummaryContent` preserves the portrait order and gives landscape a separate compact composition: FPS/frame time/Display Hz first, CPU/GPU cards next, then profiles. The shortcut grid and installed-app dock remain available in an in-panel Quick actions disclosure. Text measurements decide whether metric cells share a row; narrow widths and larger fonts stack them without clipping. Sidebar width no longer has an unconditional 240 dp minimum that could exceed a smaller safe viewport.

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

Portrait now explicitly prefers the inline page menu, giving its entire usable width to the content instead of reserving a side rail. At a 321 dp drawer the usable body is 301 dp before card padding. Landscape retains the measured rail only while at least 176 dp of content remains. Header text shows the active app for top-level menu pages; the page selector identifies the page without repeating its title. Add-in child headings and Back behavior remain intact. The new policy test checks portrait widths 240/280/321/336 dp with short and long labels.
