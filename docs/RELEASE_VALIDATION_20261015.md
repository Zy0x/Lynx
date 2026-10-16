# Release validation on X698 — 2026-10-15

## Scope and installation

The user explicitly requested Release builds, installation, navigation/feature verification, and immediate repairs. The identity-verified X698 (`mt6781`) was the only test target. Redmi Note 7 was not accessed. Existing version metadata and user configuration were preserved; no commit/push or automatic version bump was performed by this work.

Companion was built with Release R8/resource shrinking, installed with `adb install -r`, and its installed APK hash was compared with the local artifact. The native Android Release executable and WebUI assets were updated in the existing module, with the replaced components archived at `/storage/emulated/0/Debug/lynx_release_validation_20261015/module_before.tar`. This was a component update, not a full module ZIP flash. The module's installed boot router already invokes `lynxd boot`. The Lynx daemon alone was restarted after the update; kernel boot identity and system_server PID remained unchanged.

All diagnostic output, UI dumps, screenshots, probe binaries, and the synthetic clip use the authorized Debug directory on the device. Private captures stay in the ignored local `tools/video_memory_audit_20261015/release_validation/` directory. The UI audit checks process/foreground identity before dumps, taps, and swipes and stops if Lynx is no longer foreground.

## Repairs made during verification

- Build helper: optional cache-disabled retries, explicit Release unit-test invocation, correct task argument arrays, and failure propagation instead of reporting a successful process exit after a Gradle failure.
- Companion: localized newly added cooldown and hardware-verification labels, accessibility category labels, and dynamic cooldown feedback. Preserved custom profile IDs when no built-in localized label exists.
- Standalone charging fallbacks: removed forced current/power ceilings and thermal-service suspension from the maximum negotiation fallback, and removed guessed thermal/current restoration from the standard fallback. Existing unit-test safety assertions were preserved. Module-backed native charging behavior was not replaced by these fallbacks. Failed or unsupported standalone writes no longer end in unconditional success.
- CPU idle endpoint: properly JSON-quoted multiline kernel values. The previous raw `armpll_mode` help text made JSON invalid; the repaired endpoint parsed correctly on X698.
- WebUI: restored original-language text during English-to-Indonesian locale changes; replaced CPU topology loading placeholders before using in-place refresh; parsed Linux CPU lists with spaces, commas, and ranges; increased the cluster lock touch target to 48 px.
- Media CLI: rejected misspelled flags, unexpected arguments, invalid modes, and invalid booleans before changing properties.
- Live Companion crash at 09:51:27: DropBox and ApplicationExitInfo identified `UnknownFormatConversionException` in CPU hero localization. Seven formatted resource patterns contained bare percent signs. Fixed all three Android catalogs and generator input templates with escaped `%%`, strengthened the locale validator, and added a JVM formatter test over every parameterized catalog entry. Empty logcat output had hidden this failure; process continuity and DropBox provided the evidence.

## Completed checks

- Native host Release suite: 64 tests passed, including media preferences, unknown/call audio-state rejection, JSON escaping, and invalid CLI arguments.
- Companion Release suite: 29 tests passed, including existing charging assertions and catalog formatter validation.
- Complete Android/WebUI locale validator: passed. Media catalog, placeholder and plural validator: passed.
- WebUI locale round trip, saved override, unsupported-language fallback and named placeholders: passed.
- WebUI CPU topology initial-render/refresh/CPU-list regression tests: passed.
- Browser inspection covered Dashboard, SoC Tuner, Engine, Tools and the four media strategies using captured read-only native responses. This was a fixture-backed browser check, not a live hardware write test. Standalone media-unavailable controls were disabled. No JavaScript error was observed in the fixture-backed origin.
- Twenty-seven native read/query commands completed with exit code zero. JSON endpoints parsed after the CPU idle repair; `probe`, profile/applist and daemon human-readable output are not JSON contracts.
- In-use ZRAM resize was rejected with a nonzero exit code, preserving disk size and compression algorithm. Live pages were not swapped off or reset.

## Live UI and media results

The first UI pass stopped when another application became foreground. After the user confirmed the device could remain idle, the audit reached Dashboard, Profiles and HUD, then uncovered the CPU localization crash described above. The repaired APK is installed and the complete navigation audit is being repeated. Final navigation and media trial results must be recorded here before claiming complete coverage.

## Limits

Opening a page does not exercise every destructive or hardware-dependent action. Kernel flashing/restoration, module uninstall, a full reboot, unsupported hardware paths, DRM, every media format and application, and the unresolved system_server allocation-growth trigger are separate validation scopes. No claim is made that all possible errors or memory leaks are eliminated. Existing unrelated Kotlin source file-budget debt remains outside the newly added media files.
