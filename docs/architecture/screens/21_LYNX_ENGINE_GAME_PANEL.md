# Lynx Engine grouped game panel

## 1. Identity and Compose structure

`feature/hud/ui/HudSidebar.kt` owns navigation. `HudPanelShell` keeps the header and four labeled tabs outside the content scroll: Summary, Monitor, Boost and Tools. The rail sits on the docked edge and aligns its destinations toward the bottom. It uses 64–88 dp and reserves at least 176 dp for content. Long translations wrap. Narrow windows use scrollable horizontal tabs with 48 dp minimum targets, without an expanding page dropdown.

`HudHomePage` presents a compact FPS/CPU/GPU readout, a collapsed profile group and a two-column action grid. The readout opens Monitor. Application launch/management moves to Tools. `HudAddinNavigation` uses two labeled groups of destination tiles; entering a tile opens the existing add-in page. `HudDestinationGrid` reflows to one column at larger font scales. Tiles in a row share height and wrap their text.

`HudTools` routes to [Display](22_HUD_TOOL_DISPLAY.md), [Overlay](23_HUD_TOOL_OVERLAY.md), [Automation](24_HUD_TOOL_AUTOMATION.md), [Timer](25_HUD_TOOL_TIMER.md), and [Apps](26_HUD_TOOL_APPS.md). Back returns from a child to its category menu, then to Summary. Selecting a rail tab resets its child route. Per-page and per-child scroll states remain independent. Action feedback consumes a bounded area below the page rather than covering controls.

## 2. State and reactive flow

The existing `HudSnapshot`, `HudActionState`, preferences flow and service-owned callbacks remain the data boundary. Snapshot cadence and root collectors are unchanged. Package labels are resolved on `Dispatchers.IO`. Recording state still comes from the existing recorder flow; only the visible recording duration uses a one-second UI clock. Navigation is local saveable Compose state. No root operation is performed by selecting a category.

## 3. Parameters and geometry

Portrait width remains `min(safeWidth * .85, 336)` dp, including the 15 dp edge ear. Portrait height is `min(safeHeight * .7, 600)` dp. `HudViewport.panelTop` positions the expanded portrait drawer at the bottom of the safe area; landscape retains its top anchor, 44% width capped at 384 dp, and full safe height. System rotation preferences are untouched. Quick action targets are at least 52 dp high; destinations are at least 76 dp; rail destinations are at least 60 dp. Font scale controls grid reflow rather than text truncation.

Profiles retain their existing controller and Extreme confirmation. Their controls are initially collapsed under the active profile name. Monitor retains detailed clocks, frame statistics, sensor sources and unavailable-state handling.

## 4. Hardware mapping

This presentation change adds no kernel nodes, tuning values or root commands. CPU/GPU load and FPS use existing snapshot readings. Missing or stale values remain unavailable. Display, bypass, focus, touch, recording and application actions still delegate to their existing controller/session implementations. Qualcomm, MediaTek and generic discovery behavior is unchanged.

## 5. Safety and validation

`HudLayoutPolicyTest` covers rail/content bounds, long labels, narrow-window fallback, density-aware bottom anchoring and landscape positioning. `HudPageTest` checks the navigation contract. Build and tests use Release. New navigation/help resources exist in default Indonesian, `id` and `en`.

The user reserved Infinix for another agent during this task. Final validation therefore uses local tools only; no APK install, device UI automation, rotation change or live hardware write forms part of the final validation. Physical portrait/landscape rendering, large-font wrapping, dock-right behavior, touch pass-through and child controls still need device verification. A failed repository-wide locale audit must be reported separately from this panel's catalog checks. Existing unrelated oversized Kotlin files remain baseline debt; the modified sidebar and extracted UI components stay below 700 lines.

Local validation on 2026-10-28 for V7 RC259 / Companion 3.2.93: `tools/build_apk.ps1 -Variant Release -RunUnitTests` succeeded with R8/resource shrinking and 91 tests, zero failures/errors/skips. `LocaleFormatCatalogTest`, the seven layout-policy tests and navigation contract passed. Fourteen new navigation/help keys have matching default/id/en coverage. The full locale validator remains failing: after normalizing moved files and excluding ignored local probe scripts, baseline has 1252 findings and the updated tree has 1248, with no newly introduced findings. `git diff --check` passed. All HUD UI sources are below 700 lines; the inherited service and unrelated oversized sources were not broadly refactored in this design task.

## Compact detail follow-up

RC260 extends the same compact grouping into detail pages; see `37_HUD_DETAIL_NAVIGATION.md`. The main rail and category routes retain the RC259 behavior, while per-domain internal tabs replace the remaining settings/telemetry stacks. Historical RC259 validation above does not establish physical testing of these new internal tab layouts.

## Responsive window follow-up

RC261 replaces the protruding ear on the expanded drawer with its existing Close/header gestures and uses all panel width for content/navigation. The rail is centered with 52 dp minimum targets; the home readout is one compact measured row. The current geometry and physical-test results supersede RC259/RC260 layout-only claims and are documented in `38_HUD_RESPONSIVE_WINDOWS.md`.
