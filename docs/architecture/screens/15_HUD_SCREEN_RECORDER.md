# HUD screen recorder

## 1. Identity and UI

`HudRecorderControls` is an Add-ins subpage and is also reachable from the Summary Record shortcut. `HudRecordingBar` provides an always-accessible stop action inside the expanded panel. `HudRecordingIndicator` supplies a draggable 96 × 48 dp stop/timer window when the panel is closed. All controls follow the shared OLED, cyan, 12 sp label and 48 dp touch tokens.

## 2. State and lifetime

`HudScreenRecorder` owns a separate supervised IO scope so normal HUD teardown can finish a pending MP4 after the HUD scope is cancelled. StateFlow reports checking, countdown, recording, saving, saved, failed or unsupported. It never reports a saved video before validating its metadata and publishing the MediaStore entry. Stop is requested on HUD teardown, screen off or display-size changes. A bounded native watchdog verifies the originating app process and interrupts only the recorder writing the uniquely named owned file if that process disappears.

The native child PID, output identity and pending MediaStore URI are journaled. Restart recovery signals only a process whose command, output path and open-file name match that journal. Duplicate stop clicks cannot send a second interrupt and corrupt the container. Native segments have a 180-second bound; subsequent segments are created only by the live app, until the user stops recording.

## 3. Parameters

A 3-second countdown precedes capture. Light, Standard and High Detail profiles cap the long edge to 1280, 1920 or native bounds respectively; dimensions are aligned to 16 pixels and shown explicitly. Target bitrates are 4, 6 or 10 Mbps. Compatibility mode uses an Android software AVC encoder, a 1280-pixel long-edge cap and 2 Mbps. Its higher CPU cost is disclosed. Automatic software fallback is attempted only if a software encoder is enumerated and the CLI exposes codec selection. Successful fallback becomes a visible saved preference. Video has no audio; no microphone permission, fake audio option, protected-content bypass or fixed FPS claim is added.

## 4. Native and storage boundaries

The backend uses the stock `/system/bin/screenrecord` through root, independently of Lynx module installation, KPM, OEM Game Booster or LSPosed. Android 10+ scoped MediaStore ownership is required. Video destinations are `Movies/Lynx`, `video/mp4`, with `IS_PENDING=1` while recording. The root command opens a live app-owned file-descriptor proxy. Metadata must contain a positive duration and video width before the entry is published; failed entries are removed. Long recordings are separate MP4 segments, not an unimplemented merged video.

The reusable backend depends on Android, libsu, coroutines and the Keep annotation. UI and the floating stop indicator are separate adapters. A non-exported, in-process validation entry accepts only `/storage/emulated/0/Debug/LynxRecorderValidation_*.mp4`; it allows signed instrumentation to exercise the same lifecycle without creating testing files in Movies. It is not an Intent or external command endpoint.

## 5. Errors and validation

X698 rejected the default hardware encoder (`-38`) even from ADB shell, including aligned dimensions. Explicit Android software AVC successfully produced an MP4 in Debug; it was pulled and immediately removed. This is a device codec observation, not proof of a particular kernel/APatch defect. Unit fixtures cover portrait/landscape resolution bounds and active stop phases. Signed Release instrumentation validates the app backend and stop/finalization, and host MP4 parsing validates the actual output. Real notification access remains disabled as requested; recording does not require it.
