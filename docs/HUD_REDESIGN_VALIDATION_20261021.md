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

## HUD settings navigation crash repair

The user subsequently reported an immediate crash when opening HUD settings. Read-only crash-buffer inspection and `dumpsys activity exit-info com.noir.lynx` confirmed a foreground Java crash at 05:13:42 on 2026-10-21, PID 13594. The exception was `java.util.UnknownFormatConversionException: Conversion = 'l'`, through `Resources.getString`, `AppLocale.getString`, `hudString`, and `HudMetricSelection`. The `hud_metric_low` label is the literal `1% low` in all three resource catalogs.

`AppLocale` previously always selected the resource formatting overload, even with an empty argument array. The XML `formatted=false` flag does not prevent that runtime formatter call. The shared localization path now reads the raw resource and uses the pure `formatLocalizedString` helper: without arguments it preserves the literal text; with arguments it uses the resource configuration locale and the standard Java formatter. No catalog text, metric selection, profile, or hardware setting was changed by this repair.

Regression coverage exercises all HUD strings without arguments in the default, Indonesian, and English catalogs, explicitly including `1% low`; indexed parameters, escaped percent output, locale-specific decimals, and the existing parameterized catalog checks exercise the same runtime helper. The global locale validator still reports pre-existing repository findings, with no finding in the changed localization files or HUD domain. Thirteen pre-existing Kotlin files remain over the source-size budget; the changed source files remain below 400 lines.

No activity launch or UI automation is authorized for Redmi by this repair session; device diagnostics are read-only. A successful local regression test or APK installation does not establish physical HUD navigation or telemetry accuracy.

Repair validation and installation results:

- `:app:testReleaseUnitTest --tests com.noir.lynx.core.locale.LocaleFormatCatalogTest --tests com.noir.lynx.feature.hud.*`: 9 tests passed (3 locale, 4 snapshot/layout, 2 display session).
- `:app:assembleRelease`: passed, including Release lint, R8, resource shrinking, signing, and packaging. The existing metadata remains `3.1.85` / `20261229`; this fix makes no version change.
- Updated the same identity-verified Redmi (`lavender` / `sdm660`) using streamed `adb install -r` under the user's installation/repair request, retaining the no-launch boundary. Installation returned `Success` and installed metadata readback matched.
- APK size: 5,531,407 bytes. APK SHA-256: `ba56781947b5f9799ca29fa03df3a196b8e15768c95dae6b1cc71e4d33543964`; the installed base.apk hash matched exactly.
- Matching Release mapping SHA-256: `9ada75f93ba41003f1665c86f59f8e0b2bd26f36cc88f4e08aa5fb239ef486cd`. Matching APK, mapping, and test XML are retained locally outside Git.
- The packaged manifest retains the no-MY_PACKAGE_REPLACED guard and has no debuggable flag. No activity-launch command was issued; `pidof com.noir.lynx` returned no process after the update.
- `git diff --check`: passed. No device test files, root commands, module updates, or hardware writes were performed.
