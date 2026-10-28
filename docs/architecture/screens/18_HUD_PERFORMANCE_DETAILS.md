# HUD performance details

## 1. Identity and structure

`HudDetails` uses compact Frame / CPU / GPU / Memory and temperature / Battery / Diagnostics tabs, composing only one group at a time. CPU and GPU retain their separate readings and measured clock/load reflow. `HudDetailDiagnostics` groups metric sources and displays only the selected metric or policy source, availability and age. Package/layer/collection metadata stays in its own disclosure; explanatory notes remain available through the info action. Labels and values wrap without ellipsizing.

## 2. Reactive data

All readings come from the existing fresh `HudSnapshot`. No new shell calls, hardware polling, fabricated clocks or temperature substitutions are introduced. The frame chart requires a valid frame-time reading. Valid zero utilization remains zero. CPU policy readings retain their driver IDs.

## 3. Controls and defaults

Frame opens first. All other groups are one tab away; inactive groups no longer create a long vertical stack. CPU policy clocks retain their existing disclosure. Diagnostics uses category tabs and a two-column metric selector, including live CPU policies in the processor category. No tuning controls are exposed. Shared detail navigation/state behavior is documented in `37_HUD_DETAIL_NAVIGATION.md`.

## 4. Physical mapping

The UI uses native CPU delta load, per-policy current frequency, discovered GPU clock/load nodes, identified sensor values and battery readings. It does not change their source selection, unit conversion or stream contract. CPU temperature is not renamed from a SoC sensor. Diagnostics retain the package, layer, collection duration, each source, availability state and age relative to the latest stream sample.

## 5. Error boundaries and verification

Unavailable values display an em dash without a misleading numeric unit. Clock/load availability failures remain visible in the processor cards; full statuses remain in diagnostics. Long source paths wrap. The existing Release layout-policy tests cover the 176 dp content threshold and scaled width matrix; they do not replace physical rendering checks. No tuning controls or hardware writes are exposed on this page.
