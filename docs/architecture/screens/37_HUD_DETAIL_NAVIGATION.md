# Compact HUD detail navigation

## 1. Identity and structure

`HudSubpages.kt` supplies compact horizontally scrollable, labeled tabs for detail groups. It composes a single selected body in a full-width column. Main side navigation remains owned by `HudPanelShell`. `HudStatGrid.kt` supplies at most two statistic columns; labels and numeric widths determine reflow.

Routes: Monitor has Frame / CPU / GPU / Memory and temperature / Battery / Diagnostics; Bypass has Controls / Readings; Touch has Controls / Zones / Test; Focus has Focus / Glow; Network has Connection / Settings / Test; Combo has Controls / Settings / Share; Recorder has Controls / Video / Audio / Advanced; Console has Audio / Display. Tools Display, Overlay and Automation have their own compact groups. Mini metric selection and floating settings use the same pattern in Companion and overlay.

## 2. State and reactive flow

Selected group is saveable local Compose state. A nested `SaveableStateHolder` preserves each tab's saveable form state while uncomposing inactive bodies. Existing live session/manager state remains above this boundary or in its existing controller. `LocalHudPageScroll` is supplied by the sidebar so explicit group changes reset outer scroll to the top; it is nullable for shared Companion uses. Selecting a tab invokes no hardware callback.

## 3. Parameters and defaults

The first group opens by default. Tabs have minimum 80 dp width and 48 dp height, grow with text and scroll horizontally rather than squeeze. Detail choice/statistic grids use actual 6 dp gaps and at most two columns; larger labels reflow to one. Combo slot targets have a 48 dp minimum, rename/import controls use full width, and timer Reset moves below the time readout.

## 4. Hardware mapping

No kernel nodes, tuning values, telemetry intervals, profile defaults or vendor adapters are added. `HudLab` becomes a small route dispatcher, and per-domain pages invoke existing callbacks. The read-only Monitor continues consuming the same snapshot. Diagnostics selects a metric/policy source rather than rendering every source block at once; availability, path and sample age remain accessible.

## 5. Error boundaries and validation

Unsupported/busy guards are retained. Combo mutations and start are disabled while another sequence runs; selected active execution retains Stop. Resource keys exist in default Indonesian, id and en. The layout-policy fixture covers the actual 6 dp gap boundary. Validation is Release-only and local because the user reserved Infinix for another agent. No APK installation, game interaction, sysfs write, rotation change or device screenshot is performed. Physical rendering, large-font navigation and control behavior remain unverified in this cycle.

Local validation on 2026-10-28 for RC260 / Companion 3.2.94: `tools/build_apk.ps1 -Variant Release -RunUnitTests` succeeded with R8/resource shrinking. All 92 tests in 21 suites passed, including eight layout-policy fixtures, three runtime locale-format fixtures and four Combo model fixtures; no failures, errors or skips. All 28 new detail/help resource keys have default/id/en parity. The repository-wide locale validator still fails on existing findings: tracked baseline 1248, updated tree 1236, with zero added findings. All touched HUD UI files are below 200 lines and each extracted composable is below the 250-line budget. The repository-wide size scan still reports inherited oversized sources outside the changed HUD UI. `git diff --check` passed.
