# Touch scroll baseline — 2026-10-11

Local changes based on commit `e11c9fbedcdbbc2d1e2092861e4dbd8ca88bf244` (RC22).
Version metadata is unchanged. No module installation is part of this test.

## Change

- Use Compose's `ScrollableDefaults.flingBehavior()` for all five existing list callers, retaining disabled stretch overscroll.
- Remove the custom Stokes-Coulomb frame loop. Its duration clamp of 0.15–2.5 seconds violates the analytical stop time: 100 px/s stops at 0.072 seconds but the old loop continues into negative velocity; 22,000 px/s stops at 3.302 seconds but the old loop cuts it short.
- Keep battery chart pointer handlers keyed by `Unit`, and read current points using `rememberUpdatedState`. Telemetry updates no longer cancel an in-flight handler merely because the points list changes. Horizontal scrubbing and tap selection remain available; vertical scrolling stays with the parent.
- Enable the existing upstream `VelocityTrackerAddPointsFix` before composing UI. The exact 1.6.1 sources downloaded from Google Maven show its default is `false`. The corrected path tracks original MotionEvent coordinates, ignores UP samples, and clears stale velocity after a pause. Reference: https://android.googlesource.com/platform/frameworks/support/+/62aa23a474378d0284f409c80680f96debea4e96%5E%21/

## Recovery

Backup directory: `archive/local_backups/scroll_before_20261011_RC22/`.

- `source_HEAD.zip`: complete tracked source snapshot before edits.
- `MainActivity.kt` and `LynxTuningSubscreens.kt`: original copies of the two edited files.
- `LynxKernelManager_RC22.apk`: original repository APK.
- `device_before.apk`: APK pulled from the verified X698 before installing the test build.
- `commit.txt`: original Git revision.

The two original APKs have identical SHA256:
`B2C19E00F99FD9CB9658774BA1D5F11B3B9D5C50CEAF1D38428370083258E1B0`.

On an explicit recovery request, restore only the changed source files and the repository APK from this directory. Rediscover and verify the X698 using `tools/connect_device.ps1 -Quiet`, then reinstall `device_before.apk` with `adb -s <verified-serial> install -r`. Do not uninstall or clear app data. Do not use a blanket Git reset, and preserve any later unrelated changes. The source ZIP is a secondary recovery copy.

## Validation scope

Release compilation and device smoke tests are recorded in the backup directory. Automated ADB gestures are only regression checks: they cannot establish that real capacitive touch feels smooth. Final acceptance requires physical finger dragging and flicking on the X698, including diagonal movement, chart gestures, list boundaries, and touching the list again during a fling.

No Redmi Note 7 access, root module changes, reboot, or tuning writes are part of the test commands. Device test output files are confined to `/storage/emulated/0/Debug/`.

## First baseline results (native fling, before velocity tracker fix)

- `assembleRelease`: successful; release vital lint: no issues; APK signature verification: successful.
- X698 identity: `Infinix-X698` / `mt6781`, display mode 120 Hz.
- Twelve injected swipes on the dashboard: old RC22 sample 66 frames, median 40 ms, p95 48 ms; native baseline sample 139 frames, median 38 ms, p95 46 ms. Different fling trajectories produce different frame counts, so these are exploratory samples, not a controlled benchmark or proof of smoothness.
- Physical-touch capture: 48 gestures with at least two Y samples; many gestures lasted roughly 50–100 ms. Raw kernel movement directions were preserved in the summarized final 80 ms. This does not prove the entire hardware path is fault-free or show the app's computed fling direction.
- Physical session frame sample: 518 frames, median 29 ms, p95 48 ms. Rendering delays remain significant.
- User rejected this first baseline: direction reversals and stutter persist, especially when reversing a scroll immediately.
- Opening battery information, horizontal chart gestures, and vertical/diagonal swipes completed without a crash or ANR match in the inspected logcat tail.
- Rollback was exercised: `device_before.apk` reinstalled successfully with `adb install -r`, without uninstalling or clearing data. The next build restores the test candidate afterward.

The velocity tracker fix is a follow-up candidate and must receive its own physical-touch acceptance test; the first baseline results must not be attributed to that candidate.

## Second candidate results (raw MotionEvent velocity tracking)

- Offline release build successful; release vital lint reports no issues; APK signature verification successful; X698 update installation successful.
- Dashboard launch, battery information navigation, and injected up/down drags completed. No crash or ANR match in the inspected logcat tail.
- A second 40-second physical-input capture is stored as `physical_touch_velocity_fix.txt`. Associated frame sample: 362 frames, median 32 ms, p95 48 ms. Gesture/content differences prevent using this as an A/B performance comparison.
- User confirmed this candidate follows the finger and is a substantial improvement over prior versions, while requesting smoother/faster scrolling and reporting occasional exits to the previous page. Rendering delays remain.
- Preserve this accepted candidate independently in `archive/local_backups/scroll_best_velocity_fix_20261011/`: APK, both changed source files, and their SHA256 hashes. It can be restored separately from the original RC22 snapshot.

## Third candidate scope

- Separate the three battery information cards into keyed lazy items so off-screen cards can be culled. Keep charging polling and dialogs outside lazy item lifetimes, active for the whole charging page.
- Reselecting the current bottom navigation tab preserves its subpage. Previously reselecting Tuning Engine set `selectedCategory = null` and returned to the hub.
- Add small navigation-source logs (`LynxNavigation`) for actual system back, header back, and tab changes. Android's system back gesture remains available. Logs contain category/tab identifiers only.
- Frame-stage analysis from the second candidate's 120 retained frame records showed median traversal/layout ~0.055 ms, render command stage ~14.322 ms, and issue-to-GPU-completion ~26.916 ms. This motivates testing culling rather than claiming that recomposition is the proven bottleneck.

Third candidate release build and vital lint passed, APK signature verified, and update installed on the verified X698. Device UI checks passed for active-tab reselection preserving the battery subpage, system Back returning to the hub, and the explicit header Back returning to the hub. The first Back test was inconclusive when device focus changed to another app; it was repeated with a Lynx-focus check before sending input. Final physical-touch feedback and its frame capture are recorded separately below when available.

## Accepted result

The user confirmed the third candidate is better. Scrolling follows the finger and remains usable/smooth, with slight lag still noticeable in dense areas containing live graphs or updating progress. This is an accepted improvement, not evidence that all rendering latency has been eliminated.

The final physical session recorded 484 frames, median 31 ms, p95 57 ms. In the retained 106 valid frame records, median traversal/layout was ~0.052 ms, render command stage ~13.881 ms, and issue-to-GPU-completion ~21.521 ms. These are device diagnostics under different gesture/content conditions; they are not controlled performance comparisons. Navigation log output was empty on this device, so those logs were not used to infer the cause of any prior page exit.

Final accepted source/APK copies are stored in `archive/local_backups/scroll_validated_20261011/`, in addition to the original RC22 and second-candidate backups. All work remains local: no commit/push, version changes, module installation, reboot, or Redmi Note 7 interaction.
