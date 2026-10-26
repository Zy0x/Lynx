# Compact HUD layout validation

## Artifact and source

- UI source is included in `b8ecb12` (the shared working tree was committed by the concurrent workflow).
- Release APK SHA-256: `95cf276d49a841d84b7e372423f9384fef51ed4631ee33107b0453d022b3e940`.
- Installed on identity-verified Infinix X698 / mt6781. The installed `base.apk` checksum matches.
- APK, Release mapping, screenshots, logs and source hashes are retained outside Git. No version metadata was changed by this layout work.

## Automated checks

- `:app:assembleRelease`: passed, with R8 and resource shrinking enabled.
- `:app:testReleaseUnitTest`: all 63 tests passed. An earlier unrelated StandardChargingScript failure was absent in the final snapshot.
- HUD and locale-format subset: all 33 tests passed.
- Android default/id/en catalog key and formatting checks: passed.
- Full locale validator: not clean (1292 findings across the repository). Reviewed HUD findings include installed app names and Compose animation debug labels; these are not untranslated user-facing text. No validator rules were weakened.
- HUD layout policy covers safe widths 280/320/360/412 dp and font scale 100/130/200%, including the exact 176 dp content threshold.
- All modern HUD UI files are under 700 lines. Eleven unrelated or pre-existing Kotlin files elsewhere remain over the repository budget, including the unchanged overlay host service.
- Static audit: modern HUD UI has no ellipsis, explicit single-line clipping, or disabled wrapping. This is not a claim that every possible device/font combination has been visually tested.

## Physical X698 checks

At normal font scale, verified the six labelled shortcuts, full Ringkasan rail label, complete dock app names, Details page, CPU policy expansion (policy0/policy6), GPU zero utilization and unavailable GPU temperature, and compact Auto-skip standby/settings controls. Labels and values were visible without truncation in these screenshots. Scrolling remains inside the bounded panel.

Font settings were temporarily requested at 1.3 and 2.0 and restored to the original 1.0 in `finally` blocks. Android reported a 2.0 configuration, but the existing rendered view did not visibly change. A cold-start check could not finish after the device entered Dozing. Therefore enlarged-font physical rendering and the live rail-to-menu transition are **not verified**. Rotation preferences were not changed. Input was guarded to foreground Lynx; no game interaction or live dialogue replay was performed.

Package exit history during the checks contained package-update/explicit force-stop events, with no newly observed application crash entry. This does not certify the kernel or unrelated root tuning changes. No real notification access or microphone permission was granted. Older signed video-only recorder tests are documented separately; the concurrent audio recorder changes were not retested here.

## Device hygiene

Screenshots and configuration dumps were streamed directly to the PC. The earlier recorder probes used unique Debug destinations, pulled and immediately deleted their MP4s, and the probe APK was uninstalled. This layout check created no remote screenshot/dump files. Redmi was untouched. No module ZIP, root-module directory or hardware-protection changes were performed by these validation tools.
