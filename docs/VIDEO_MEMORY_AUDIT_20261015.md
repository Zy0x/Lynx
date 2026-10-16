# X698 video playback and framework restart audit

## Findings on 2026-10-15

- The user's failing Telegram/Nagram clip triggered `media.swcodec` crashes at 08:37:31 and 08:37:38. Tombstones 30/31 point to `libcodec2_soft_avcdec.so`, `ih264d_format_convert`, and `__memcpy_aarch64_simd`. Twitter UID 11009 also recorded software AVC/AAC codec failures (`-32`, plus AVC flushing errors). This is direct evidence of a software H.264 decoding failure for the affected workload, not proof of a GPU hardware fault.
- Before the restart, RAM availability was about 1.9 GiB. ZRAM logical usage was about 4.18 GiB, while physical compressed allocation was about 582 MiB. `system_server` PID 2948 owned about 3.46 GiB of swap; its native heap reported roughly 6.55 GiB allocated. A snapshot contained 23,090 VMAs.
- At 08:41:56, PID 2948 aborted on `InputDispatcher` with `Scudo ERROR: internal map failure (NO MEMORY) requesting 8KB`. Its tombstone recorded 65,510 mappings against `vm.max_map_count=65530`, strongly implicating the per-process VMA limit. The failing allocation stack does not identify the origin of all prior allocations.
- Kernel boot ID and uptime stayed unchanged. `system_server` became PID 7642, zygotes changed from the original 2304/2306 to 7542/7543, and SurfaceFlinger PID 3046 survived. This was a framework/zygote restart. The old `kernel_panic` boot reason belongs to the preceding kernel boot and does not describe this event.
- The restart occurred during diagnostics. No reboot, zygote kill, or system_server kill was requested by the diagnostic commands. Instrumentation can add pressure to an already unhealthy process; no controlled allocation profile was captured to establish the leak's exact caller or exclude all observer effects.

## Completed trials

1. A framework `RUNNING_LOW` trim request did not reduce the old system_server's approximately 3.46 GiB swap allocation. No swapoff, ZRAM reset, cache deletion, GPU changes, or global VM tuning was performed live.
2. A generated 3-second, 1280x720 H.264 clip completed 90 frames with C2 software in byte-buffer and ImageReader surface modes, MediaTek OMX in both modes, and Google OMX in surface mode. These synthetic successes do not reproduce or invalidate the user's real-clip failure.
3. A nonpersistent `debug.stagefright.ccodec=0` trial initially left the cached codec list unchanged. Restarting only `mediaserver32` refreshed it; `c2.android.avc.decoder` disappeared and the legacy MediaTek/Google OMX entries remained. The system_server PID stayed 7642 through this intentional media-player restart.
4. After reopening Nagram and replaying the same failing clip, the user confirmed successful playback. `media.resource_manager` showed `OMX.google.h264.decoder` active for Nagram UID 10575. This verifies an effective compatibility workaround for the tested clip, not a universal decoder fix.
5. Fresh system_server samples after the framework restart were around 6,700 mappings and 50–60 MiB swap. The VMA limit was not raised. A longer allocation investigation is needed if native allocations grow again.

## Current live scope and recovery

The successful temporary codec property remains active for this kernel boot. It can remove Codec2-only formats such as AV1 and change audio codec selection; broader media compatibility is unverified. It was not installed as a boot default and no root module files were changed by this diagnostic session.

Original property state and a boot-bound recovery script are in `/storage/emulated/0/Debug/lynx_video_memory_20261015/`. The original property was empty. Refresh the media player service's codec list and reopen affected applications after restoring it. A cold reboot also resets this nonpersistent property unless another component applies it again.

Raw logs, package/process details, memory maps and tombstones remain local under ignored `tools/video_memory_audit_20261015/`. Do not publish these captures; share this redacted report. The synthetic codec probe is `tools/VideoCodecProbe.java`; only that diagnostic JAR was compiled, not the Companion APK or production daemon.

## Source hardening and requested feature

- MemoryController rejects live ZRAM reconfiguration when it still contains pages, checks swapoff before reset, avoids the side-effecting hot_add read, and preserves tombstones/ANRs during memory cleanup. Companion's algorithm action delegates to the guarded native path. These production source changes have not been installed or exercised on live ZRAM.
- The user subsequently requested a professional decoder control with optional boot persistence and additional fallback strategies. Source implementation is complete in a separate media subsystem, Companion Tools card and WebUI Tools section; factory defaults remain system mode with boot application disabled. See `MEDIA_DECODER_CONTROL.md` for the four strategy profiles, restore behavior, status contract and remaining build/device validation. This compatibility control does not repair the framework native allocation problem.

## Primary references

- [ZRAM logical versus physical memory statistics](https://www.kernel.org/doc/html/v5.9/admin-guide/blockdev/zram.html)
- [Per-process max_map_count and allocator mappings](https://www.kernel.org/doc/html/latest/admin-guide/sysctl/vm.html#max-map-count)
- [AOSP Codec2 listing modes](https://android.googlesource.com/platform/frameworks/av/+/65ffc2e92a2493175a78dd138a25b12ad23c43bb/media/codec2/sfplugin/Codec2InfoBuilder.cpp)
- [AOSP OMX rank properties](https://android.googlesource.com/platform/frameworks/av/+/master/media/libstagefright/OmxInfoBuilder.cpp)
- [AOSP DEAD_OBJECT status](https://android.googlesource.com/platform/system/core/+/459df8f/include/utils/Errors.h)

## Follow-up observation

At 09:13:31 the same kernel boot and system_server PID 7642 remained active, with 6,843 VMAs, about 57 MiB process swap, about 3.18 GiB MemAvailable and 3.14 GiB SwapFree. No newer tombstone was observed. These figures follow the earlier framework restart and do not establish that the decoder workaround repaired the independent allocation growth. The synthetic diagnostic JAR and clip were removed from the authorized Debug directory; the boot-bound recovery helper remains. The initial shell marker probe was inconclusive; the compiled native controller later verified the markers and reported all four supported strategy candidates.
