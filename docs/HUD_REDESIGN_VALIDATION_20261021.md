# HUD redesign validation — 21 October 2026

## Original HUD validation artifact

- Local APK: `LynxKernelManager.apk`, Release / R8 / resource shrinking enabled.
- Package: `com.noir.lynx`; versionName `3.1.84`; versionCode `20261228`.
- APK size: 5,544,839 bytes.
- APK SHA-256: `7823b3c2c31f818e8c89903fbc3a377ab486cbf5f929de4c25563be74c5efa31`.
- APK-owned ARM64 collector SHA-256: `6d9549cab44d66a57bf17a7d3476f2c98d30750b693e01040921f331dc16b5cc`.
- Release mapping SHA-256: `86282e9d0387d9af67778f5d25ad843e209992d92ff030587988d602f09efeac`.
- Native payload is an AArch64 ELF with a nonzero executable entry point. Its bytes match the current Cargo release output. The packaged manifest sets extractNativeLibs=true and has no debuggable flag.
- No HUD-specific version bump was made. This records the metadata present in the shared working tree at build time.

## Local checks

| Check | Result |
| --- | --- |
| `cargo test --target x86_64-pc-windows-msvc` | 77 passed, including 8 HUD frame/hardware cases |
| `tools/build_apk.ps1 -Variant Release` | Passed; final APK copied to repository root, ignored by Git |
| `:app:testReleaseUnitTest --tests com.noir.lynx.feature.hud.*` | 6 passed on the final source |
| Full `:app:testReleaseUnitTest` | Initial run: 33 tests, 2 failures outside HUD; see below |
| `python -X utf8 tools/validate_locales.py` | Repository-wide audit fails on existing non-HUD findings |
| HUD-specific locale/accessibility/notification/comment checks | 0 findings across the new HUD, adapted classic presentation, display session and restored connector |
| UI root-I/O search | No Shell.cmd or ProcessBuilder calls in feature/hud/ui |
| Source budgets | New HUD files remain below 400 lines; host service below 400 lines. The repository still has 13 older Kotlin files over 699 lines outside this work |
| `git diff --check` | Passed |

The two full-suite failures were `SliderGestureSessionTest.unchangedReleaseDoesNotCommit` and `ExtremeChargingScriptTest.maximumModeRequestsOemNegotiationWithoutBypassingProtection`. Their tests and corresponding implementations were unchanged relative to the starting HUD revision `c034f61`. They were not reported as passing or silently rewritten to make this task green. The global locale audit likewise includes unrelated WebUI labels, older feedback strings, and comments; no WebUI changes were made for this task.

## Physical validation deferred

Auto-discovery found only the Redmi Note 7. The user confirmed that X698 is resting, so no APK was installed and no live tuning or screen automation was performed on either device. Redmi Note 7 was left unchanged; connector identity checks used read-only getprop calls only. No remote test files were created, and no module ZIP was installed or module directory updated.

These remain unverified until X698 is available: actual Compose rendering and insets in both orientations, touch pass-through/outside dismissal, all-metric pagination at large fonts, native-library execution under the installed root manager, live SurfaceFlinger layer selection and sensor units, screen-off collector termination, screenshot publication, profile/Hz readbacks and restoration, and measured collection overhead during gameplay. Static viewport fixtures and parser tests do not establish these device results.

## Subsequent user-authorized Redmi installation only

The user explicitly authorized this one APK installation on Redmi Note 7, without opening the app. The APK was rebuilt from isolated revision `dcfe8a4` with only the package-replacement trigger removed from the boot receiver and manifest. This avoids running daemon deployment, profile restoration, or other boot tuning merely because an APK update was installed. Normal cold-boot handling was preserved.

The first shared-tree build encountered unrelated in-progress CPU compilation errors. The isolated clean build then exceeded the existing 768 MiB Kotlin daemon limit. The successful build used `:app:assembleRelease -Pkotlin.compiler.execution.strategy=in-process` with the existing 1800 MiB Gradle heap; no project memory configuration was changed. Release lint, R8, shrinking, signing, and packaging passed.

- Installed version: `3.1.84` / `20261228`, package `com.noir.lynx`.
- Install result: `Success` from streamed `adb install -r`, after verifying `lavender` / `sdm660` through the reference connector and direct identity readback.
- Installed APK SHA-256: `63768f87098513d98430f1cbece3a2bf43207756fd3ecbcde7d45f9c39c20da5`; matched the PC artifact through a read-only installed-base.apk hash.
- Collector SHA-256: `6d9549cab44d66a57bf17a7d3476f2c98d30750b693e01040921f331dc16b5cc`.
- Matching Release mapping SHA-256: `af2c6d3f6fe223210b35433c0d1deec98be6e81843370df936fde1bc17486655`.
- The packaged manifest has no MY_PACKAGE_REPLACED filter and no debuggable flag.
- No activity was launched; `pidof com.noir.lynx` returned no process after installation. No UI automation, device test artifacts, module installation, module-directory writes, or live tuning commands were performed.

The installation-only authorization does not change the daily-driver read-only policy for future tests. Actual HUD rendering, telemetry, controls, and overhead remain untested on a physical device. The earlier deferred-test section describes the original HUD validation stage, before this subsequent explicit installation request.
