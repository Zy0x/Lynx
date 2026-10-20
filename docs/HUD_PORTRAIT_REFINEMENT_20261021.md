# Compact HUD portrait refinement

## Source and boundaries

The implementation was isolated from shared CPU/slider/navigation edits in a separate worktree based on `c808e66`. It retains that revision's version metadata, preference keys, native collector, sampling/statistics, and hardware controller. WebUI and all six classic floating styles are unchanged.

The user's portrait reference establishes a labeled left rail, top application dock, compact instrumentation, and profile controls. The selected width remains min(80% of safe width, 336 dp), with a maximum portrait height of 70%. Landscape retains min(44%, 384 dp) and safe-height limits.

## Presentation behavior

- The rail is at least 72 dp wide and grows to the measured widest 12 sp semibold label plus 16 dp. Both orientations use the same Overview/Details/Tools structure. Below 176 dp of remaining content width after padding, a labeled inline page menu replaces the rail. It never creates an extra popup or fullscreen capture window.
- Header drag recognition covers only the brand/app area. The close control remains separate. The installed-app dock uses 48 dp targets and disappears when empty.
- Page selection and three independently remembered scroll states outlive panel collapse and navigation-mode changes. Per-page saveable state retains configuration/disclosure state throughout the HUD service session.
- Gauge size, timing columns, profile orientation, preview columns, switches, and detail label/value rows use measured text and available space. Font size remains unchanged; narrow content reflows vertically. Numbers retain monospace presentation and non-valid readings retain their unavailable marker/status.
- Profile overflow and Extreme confirmation are inline. Primary profile choices gain the width previously occupied by the overflow button. Screenshot and Tools shortcuts reuse existing actions. Tools receives actual Display Hz separately from supported and requested rates.
- All 18 metrics remain available in four Details groups. Sources, timestamps/ages, package/layer, and explanatory notes move into expandable diagnostics. No metric source or statistics formula is changed.
- Fourteen new resource keys are provided in default, Indonesian, and English catalogs. Switches carry their metric labels for accessibility; selected navigation and profile choices expose selection semantics. Selection and gauge animations use 200 ms.

## Verification scope

Release fixtures cover the exact 176 dp navigation boundary, measured label growth, safe-window widths 280/320/360/412 dp in both orientations, font-scale measurement inputs at 100/130/200%, and gauge/profile cell reflow. These pure geometry tests do not assert Android font rendering or physical touch routing.

The repository-wide locale validator still reports existing findings outside HUD. The final scoped HUD audit has zero findings. The initial two scoped matches were animation trace labels, which now reuse the localized choice/navigation label.

Auto-discovery returned no X698. The reference connector verified that Redmi is reachable; it remains restricted to APK installation without app launch. Actual rendering, cutouts, near-bottom positioning, drag versus scroll, outside touch pass-through, all-toggle visual behavior, and real font enlargement require X698 validation and are not claimed as tested. No emulator is installed on this host.

The initial isolated `:app:assembleRelease` passed lint, R8, shrinking, signing, and packaging on the unchanged `3.1.85` baseline. All 13 targeted Release tests passed (4 layout policy, 4 snapshot, 2 display-session, 3 locale), including the expanded narrow-landscape geometry matrix. The collector SHA-256 is unchanged: `6d9549cab44d66a57bf17a7d3476f2c98d30750b693e01040921f331dc16b5cc`.

Before installation, read-only package inspection found that another workstream had already updated Redmi to `3.1.86` / `20261230`. The older baseline artifact was not installed. Integration therefore uses a snapshot of the latest shared CPU/pager working changes and existing version metadata, followed by another Release build. Those external working changes are excluded from HUD commits.
