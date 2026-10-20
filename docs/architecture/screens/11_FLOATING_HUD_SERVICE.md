# Floating HUD: bounded sidebar and native telemetry

## 1. Identity and Compose structure

The primary overlay is hosted by `LynxFloatingHudService`. Presentation lives in `feature/hud/ui`, state and parsers in `feature/hud/data`, and event actions in `HudController`. The former XBoost components and their direct UI shell writes have been removed.

`HudSidebar` provides an OLED surface (#090D10), cyan accents (#58E5D1), an installed-app dock, and Overview / Details / Tools navigation. Both orientations use a labeled icon rail. `HudLayoutPolicy` reserves at least 72 dp for the rail, or the measured widest 12 sp semibold localized label plus 16 dp. If the remaining content width after two 8 dp paddings would be below 176 dp, a labeled page selector replaces the rail above the content. Its expanded list replaces the page body inside the same panel, without a popup window. No fullscreen scrim or fullscreen touch-capture window is created. The actual WindowManager window wraps the displayed panel or mini HUD. Outside touches collapse the panel while allowing the underlying app to receive its own event.

Panel width is min(80% of safe width, 336 dp) in portrait and min(44%, 384 dp) in landscape. Height is capped at 70% of safe height in portrait and the available safe height in landscape. Each tab scrolls independently. Header-only drag avoids fighting the content scroll gesture. Insets, display cutouts, actual measured window dimensions, and orientation-specific saved anchor fractions determine position. Opening a larger panel reclamps its complete bounds.

Main controls have at least 48 dp targets and main labels at least 12 sp. Navigation selection, profile selection, and gauge arcs animate over 200 ms while numeric readings remain unmodified. Actual text measurements, including the current font scale, determine when timing blocks, gauges, profile choices, and label/value rows must stack. Text is never scaled down to fit. The existing six classic window layouts keep their visual structure, using a compatibility projection and explicit unavailable formatting. Classic Extreme shortcuts route to the sidebar confirmation.

`HudPanelHeader` isolates drag recognition to the brand/app area, away from the close button and battery. Its installed-app dock is omitted when empty. `HudNavigation` contains the rail and inline fallback menu. Page selection and all three scroll states live above the open/closed and rail/menu branches; a per-page saveable state holder also retains diagnostics, tool configuration, and profile disclosure state during the service session.

Overview keeps the FPS, actual presentation interval, active display Hz, load, clock, and profile readings, with screenshot and Tools shortcuts. The profile overflow menu and Extreme confirmation render inline. Details groups frames/display, CPU/GPU and policy clocks, RAM/SoC temperatures, and battery readings. Non-valid statuses remain visible beside unavailable readings; sources, freshness, package/layer, and measurement notes are in an expandable diagnostics section. Tools receives the actual display reading separately from requested Hz and supported rates, and groups screenshot, refresh selection, Mini HUD configuration, and stop actions. Preference keys and the native stream contract are unchanged.

## 2. Reactive state and lifecycle

The APK builds `native/lynxd` for aarch64-linux-android and packages the generated executable as `lib/arm64-v8a/liblynxhud.so`, using legacy native-library extraction and preserving the executable. `HudStreamRepository` launches the APK-owned file from nativeLibraryDir through a single root process:

`liblynxhud.so hud stream`

This collector does not launch the tuning daemon, deploy files under the installed module, enable SurfaceFlinger timestats, change permissions, or write hardware nodes. Kotlin reads NDJSON on Dispatchers.IO and publishes immutable `HudSnapshot` state. The host refreshes freshness/presentation every 250 ms while interactive. Frame queries run no faster than 500 ms, hardware reads every 1000 ms, and thermal reads every 2000 ms. Slow queries do not overlap and have bounded output and timeouts. `collection_ms` reports real collector time, without promising a universal overhead figure.

SCREEN_OFF cancels collection, closes the owning pipes, collapses the panel, and invalidates readings. SCREEN_ON restarts a new collector with fresh CPU/frame baselines. No wakelock is held. Stopping the service or closing stdin ends the native collector; broken stdout also ends it. A disconnected stream becomes unavailable rather than retaining a live-looking last value. Startup hydration no longer heals profiles or deploys a daemon merely because the Companion was opened.

APK replacement does not dispatch the boot restorer: MY_PACKAGE_REPLACED is absent from both its manifest filter and its accepted actions. Installing an update therefore does not itself start the tuning daemon or restore a profile. Cold-boot restoration remains separate.

The NDJSON v1 snapshot carries package/layer identity, profile selection, charging status, named metrics, per-policy CPU readings, frame history, collection time, and a monotonic sample timestamp. Each metric carries nullable value, unit, source, at_ms, and one of valid / warming_up / stale / unsupported / permission_denied. At three source intervals without a fresh sample the value becomes unavailable. Display mode is supplied by the attached display and DisplayListener, separately from the native frame source.

Metric age uses CLOCK_BOOTTIME, matching Android elapsedRealtime including suspend. SurfaceFlinger presentation comparisons use CLOCK_MONOTONIC instead, so deep sleep does not introduce a permanent clock-offset error.

## 3. Configuration and actions

- Existing preference file: lynx_hud_prefs. Mode remains 0 (sidebar) / 1 (classic); classic style IDs remain 1–6. Existing pin_mini_fps is preserved.
- mini_metrics_v1 is an independent set of metric keys. Missing preference defaults to fps + frame_time. An explicitly empty set remains empty. All 18 displayed metrics can be toggled individually, with no maximum selection count.
- The native HUD settings screen and overlay observe the same preference listener. Choices take effect immediately and survive restart. Reset restores only metric selection, preserving other user preferences.
- Mini width is min(60% safe width, 240 dp), and height is capped at 25% safe height. Metrics paginate two per page, or one at larger font scales. With pin off or every metric deselected, only the 48 dp handle target remains.
- Main profiles: powersave / balance / performance. Auto and Extreme are in the additional menu. Extreme requires an inline warning and explicit confirmation. Actions are serialized. The UI reports success only after profile application and a native audit with nonzero checked items, no errors, and no clamps, plus matching selected-profile readback.
- Display choices come exclusively from supported modes at the current resolution. Fractional Hz is retained. `HudDisplaySession` uses the framework cmd display interface, never private Binder transaction IDs. It saves the previous preferred mode before a session request, checks the actual display mode separately, and restores the original preference on HUD stop. A persisted recovery record allows restoration at the next controller startup after process death. Later external preference edits are preserved. Unreadable backup or unsupported commands prevent the request. This is session-scoped, not a persistent module setting.
- Screenshot captures bytes through stdout, hides the overlay during capture, and publishes through MediaStore only on successful completion. Failed captures remove their pending MediaStore item. Normal user screenshots go to Pictures/Screenshots. Device test captures must instead stream directly to the PC or use Debug with immediate cleanup; the production capture button is not used to create test artifacts elsewhere.
- App shortcuts contain installed, launchable apps only. Opening a shortcut uses its normal launcher intent; no freeform capability is implied or forced. Empty lists and launch failures have explicit states.

## 4. Hardware ground truth and statistical definitions

FPS refers to presentation of the selected foreground app layer, not panel Hz, compositor-global activity, GPU execution time, or the overlay's own draw loop. The host window is explicitly titled LynxFloatingHud, excluded by the native layer selector. Foreground identity comes from topResumedActivity with mResumedActivity fallback. Package boundaries are matched exactly. Snapshot, starting, toast, and decoration layers are excluded. A producing SurfaceView is preferred to its activity container. Multiple active content-layer candidates fail closed rather than guessing.

Frame timestamps use SurfaceFlinger --latency actual-present column two. Zero, unresolved INT64_MAX fences, duplicates, and future timestamps are rejected. The rolling one-second frame count is FPS. Frame time is the difference between consecutive actual-present timestamps, never 1000 / sampled FPS. The chart shows means of four real consecutive intervals, at most 60 points. Statistics retain at most 6000 intervals. 1% low requires 100 intervals and is 1000 divided by the mean of the slowest ceil(N * 0.01) intervals. A lost ring-buffer overlap, changed package/layer, ambiguity, or an empty/stale buffer clears relevant statistics. Global timestats and synthetic fallback points are not used.

CPU clocks read cpufreq policy*/scaling_cur_freq in kHz, converted to MHz. The overview reports the highest observed policy clock, with all policies listed in Details. CPU load is the delta of /proc/stat's first eight counters; idle includes iowait and guest fields are not double-counted. The initial or reset delta is unavailable; measured zero load is valid.

GPU discovery uses KGSL gpuclk (Hz), GED current_freqency (OPP index followed by kHz), gpufreq_var_dump's explicitly named (real) freq (kHz), or dynamically identified GPU/Mali/KGSL devfreq cur_freq (Hz). Fixed-clock requests and OPP target tables are never live-clock fallbacks. Utilization uses GED utilization/loading or KGSL gpu_busy_percentage/gpubusy, with range and denominator checks. Generic unsupported utilization stays unavailable.

Thermal zone types determine CPU / SoC / GPU labels; their standard millidegree values convert to degrees Celsius. The named hottest matching readable sensor is shown, with its path/type retained. Battery temperature comes separately from battery/temp (tenths Celsius). Missing CPU or GPU sensors never inherit battery temperature. Battery voltage_now (microvolts) and current_now (microamps) use explicit unit conversions; current sign is retained, while charging comes from battery/status. Battery power is voltage times absolute current and is labelled battery power, not charger input power. RAM used is MemTotal minus MemAvailable, in GiB. No missing sensor defaults to an invented numeric reading.

Reference: [Android game presentation statistics](https://developer.android.com/topic/performance/issues/slow-session) and [AOSP preferred display mode commands](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/display/DisplayManagerShellCommand.java).

## 5. Safety, tests, and limitations

Pure Rust fixtures cover cadence, duplicate/pending timestamps, missing buffer overlap, frame statistics, package boundaries, GED parsing, and CPU delta/reset cases. Release JVM fixtures cover nullable versus real zero readings, per-source expiry, missing-sensor classic projection, safe viewport limits, fractional display rates, and rejection of malformed display readbacks.

Build with tools/build_apk.ps1 -Variant Release. Run :app:testReleaseUnitTest and Rust host tests. Global locale validation must be reported independently from HUD-specific findings, since unrelated catalogs and pre-existing UI can fail the repository-wide audit.

Physical verification requires tools/connect_device.ps1's strict X698 + mt6781 interlock. Device-list metadata alone is never sufficient authorization. Redmi Note 7 remains read-only. No root module ZIP is installed and no binary is deployed to its module directory. All test artifacts stay on the PC or in X698 Debug with immediate post-pull deletion. Do not change accelerometer_rotation or user_rotation. UI automation must stop if the foreground is not Lynx. Foreground games may be measured passively without automating or dumping their contents.

Actual vendor SurfaceFlinger support, root permission, sensor mapping, overlay rendering, screen-off shutdown, touch pass-through, and collector overhead must be verified on the physical X698. A successful build or parser fixture is not evidence that these live cases passed. Unsupported/read-protected sensors show an em dash; the implementation does not promise universal sensor availability.
