# HUD performance details

## 1. Identity and structure

`HudDetails` presents frame statistics, separate `HudProcessorDetails` CPU/GPU cards, collapsed RAM/sensor and battery groups, and `HudDetailDiagnostics`. Cards use the shared OLED surfaces, thin dividers, 12 sp labels, stable numeric typography and a restrained load bar. Actual clock and load appear beside each other only when their measured text fits; otherwise they stack. Labels and values wrap without ellipsizing.

## 2. Reactive data

All readings come from the existing fresh `HudSnapshot`. No new shell calls, hardware polling, fabricated clocks or temperature substitutions are introduced. The frame chart requires a valid frame-time reading. Valid zero utilization remains zero. CPU policy readings retain their driver IDs.

## 3. Controls and defaults

Frame statistics and CPU/GPU cards are directly visible. CPU policy clocks, RAM/sensor details, battery and diagnostics expand in place. The page uses the existing per-page saved scroll state and bounded panel. All disclosures have at least 48 dp touch targets.

## 4. Physical mapping

The UI uses native CPU delta load, per-policy current frequency, discovered GPU clock/load nodes, identified sensor values and battery readings. It does not change their source selection, unit conversion or stream contract. CPU temperature is not renamed from a SoC sensor. Diagnostics retain the package, layer, collection duration, each source, availability state and age relative to the latest stream sample.

## 5. Error boundaries and verification

Unavailable values display an em dash without a misleading numeric unit. Clock/load availability failures remain visible in the processor cards; full statuses remain in diagnostics. Long source paths wrap. The existing Release layout-policy tests cover the 176 dp content threshold and scaled width matrix; they do not replace physical rendering checks. No tuning controls or hardware writes are exposed on this page.
