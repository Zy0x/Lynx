# Floating app task continuity and compact resize

## Scope and behavior

The native HUD now exposes direct resizing through a 16dp header glyph with a dedicated minimum-48dp target. Its hint fades to 18% after three seconds and returns during a resize or title tap. The target does not cover application content. Touch dimensions round upward to physical pixels. The compact window still reserves one header and no footer by default.

The independent `:freeform` library reuses the most recent running task of the requested package on display 0 for the host's Android user. It verifies that exact task ID on the owned virtual display. With no reusable running task, launcher flags are `NEW_TASK | RESET_TASK_IF_NEEDED`; the previous `MULTIPLE_TASK` flag is removed. Closing returns tasks instead of deleting them, and virtual displays omit destroy-content-on-removal. The main-display task regains focus after launch and cleanup. Reopening an already managed package reveals its existing Lynx window.

The dock is no longer restricted to seven hardcoded entries. `HudAppCatalog` enumerates enabled launcher applications for the current user and refreshes on package broadcasts. `HudAppManager`, available from the dock Add button and Companion/Tools floating settings, selects and orders apps and stores per-package initial sizes. Missing selections use the existing favorites as migration defaults; explicitly empty selections remain empty. Per-app overrides preserve global window size, Mini HUD metrics and pin. The library itself remains independent of HUD preferences and Compose.

## Validation method and boundaries

All APK/library builds and Android unit tests use Release. The source snapshot and evidence live outside Git at `D:/temp/lynx-freeform-session-20261025/`. Existing workspace metadata is retained at Companion 3.2.35 / versionCode 20261279; the work does not change module metadata, Rust contracts, charging/hardware policy, WebUI or classic OSD styles.

Physical probes use the identity-verified Infinix X698 / MT6781 / Android 14. They load the installed APK's classes through a temporary `app_process` fixture, inspect task/package metadata and measure unattached native Views. They do not inspect browser pages/history, capture other apps, inject UI input or launch Companion MainActivity. In-memory preferences exercise the installed serializers without writing the user's preferences. Temporary fixture files are confined to the authorized Debug directory and removed immediately; virtual displays are released and the prior main-display task is refocused.

MainActivity's pre-existing daemon bootstrap may replace module/tmp binaries on launch. Full Companion UI automation was therefore not performed in this cycle. The HUD/freeform runtime path does not use that bootstrap. Device module/tmp daemon hashes were checked unchanged. No root-module ZIP, KPM, sysfs, global rotation or hardware-protection change was deployed.

The checks below establish task identity, backend resizing, catalog behavior, serializer behavior and measured native control dimensions. They do not establish restored visual tab contents, all Chrome history, complete gesture/IME behavior, cold-start reliability on every ROM, or universal Android 10–17 compatibility.

## Results

- App Release JVM suite: 47 tests, no failures/errors/skips.
- Library Release JVM suite: 22 tests, no failures/errors/skips.
- Rust host suite: 84 tests passed.
- Default/Indonesian/English Android catalog and format checks: zero findings.
- Repository-wide locale validator: eight existing WebUI findings; no changed-HUD/library findings. The global validator is not reported green.
- X698 installed catalog: 104 enabled launcher apps, including Chrome and an installed package outside the old favorites; the Companion launcher is excluded.
- Installed preference serializers with memory-only storage: ordered/deduplicated dock, explicit empty dock, per-app bounds, override reset, and preserved pin/metrics/global size passed.
- Chrome's existing task 62 moved from display 0 to an owned virtual display and returned with the same ID/user. Actual display resize 600×700 at 180dpi read back correctly. Browser contents were not read.
- Native window chrome is measured at 240dp width for font scales 100%, 130% and 200%; the compact header, direct resize target, small glyph and hidden default footer are checked independently from visual screenshots.

The first ad-hoc task fixture exited 137 without a captured Java cause; subsequent exception-reporting fixtures passed the task transfer/readback checks. An initial raw-process View fixture lacked Zygote's font initialization; loading the system font map repaired the fixture. Its measurements then exposed physical-pixel rounding below 48dp, which was corrected in the chrome. A superseded validation build was deliberately stopped before packaging to include that correction; only a completed final build is used for delivery.

A task fixture that omitted the production IME/rotation preparation also failed its move readback on the final APK. It is not counted as a pass. Matching the root service's actual display preparation returned `ime=true` / `rotation=true` and passed three subsequent transfer/resize/return cycles. This distinction remains relevant to future cold-start and full-service tests. Focus metadata immediately after a task operation can be transient; settled WindowManager/ActivityTaskManager focus and the next cycle's initial main task confirmed restoration of task 66.

Final artifact checksums, installed-file verification and post-correction device results are recorded in the completion entry below.

## Completion entry

The final `:freeform:testReleaseUnitTest :app:testReleaseUnitTest :app:assembleRelease :freeform:assembleRelease` invocation completed successfully in 2m48s. Its 524-file source snapshot matched the working source after the build. All 69 JVM tests and 84 Rust host tests passed. Existing ten unrelated Kotlin files over the repository's line budget were observed; changed Kotlin files are at most 317 lines and no new monolith was introduced.

Release APK `LynxKernelManager-3.2.35-floating-session.apk` SHA-256: `e9998813e20a3da5c23224d921e367969aa2760296072e93de06034ddcc947ee`. V2/V3 signing verified against the existing update certificate. Installation on verified X698 succeeded; streamed installed `base.apk` matched that exact digest. The Release AAR SHA-256 is `0d41c38bbff535911b99648fce2b06e87ba16a93574d2d0ed0bb94f52485381a`.

On that installed APK, actual View measurements at 100%/130%/200% fonts gave a 48.08dp header and resize target, approximately 15.77dp visible glyph, hidden footer, and resize target parented inside the header. Catalog count remained 104 and all memory-only serializer checks passed. Chrome task 62 passed three prepared-display transfer/resize/return cycles with the same task/user. The main screen's prior foreground task was restored. All fixture displays/files were removed, and both pre-existing daemon-file digests were unchanged. APK, AAR, mapping, reports, probe sources and the reusable source export remain outside Git.

## Framework references

The launcher flag behavior is documented by [Android Intent](https://developer.android.com/reference/android/content/Intent#FLAG_ACTIVITY_MULTIPLE_TASK). The inspected task transfer interface is present in the [Android 10](https://github.com/aosp-mirror/platform_frameworks_base/blob/android10-release/core/java/android/app/IActivityTaskManager.aidl) and [Android 14](https://github.com/aosp-mirror/platform_frameworks_base/blob/android14-release/core/java/android/app/IActivityTaskManager.aidl) source. Android's [recents implementation](https://github.com/aosp-mirror/platform_frameworks_base/blob/android14-release/services/core/java/com/android/server/wm/ActivityTaskSupervisor.java) explains why main-display focus restoration is handled separately. No framework source file or third-party freeform binary is copied into Lynx.
