# X698 Genshin Extreme trials — 2026-10-15

## Scope

Physical Infinix X698 / mt6781, existing Extreme profile, Genshin Impact. Tests used
ADB identity interlocks, existing supported OPPs, reversible node writes, readback
checks, and boot-bound recovery scripts under `/storage/emulated/0/Debug/`.
No APK build/install, module installation/write, version change, voltage override,
filesystem remount, cache deletion, or thermal-policy disable was performed.

Initially the user was playing and changing scenes. After explicit confirmation,
the character and camera were left still. Only that later sequence is used for
the comparison below. Early camera sweeps and changing-scene gameplay remain
exploratory evidence. Two overlapping collectors were excluded explicitly, and a
shared host mutex was added to prevent another overlap.

## Method

- Sample the game's actual SurfaceView presentation history about once a second;
  deduplicate actual presentation timestamps and exclude two seconds at each edge.
- Report presentation interval p95/p99 and intervals over 50 ms. These are not
  direct touch latency, shader compile duration, or full-frame GPU execution time.
- Collect CPU temperature, active CPU policy ceilings, GPU utilization, process
  I/O, PSI, and active MediaTek PPM limits. Thermal ceilings can change even when
  a profile requests maximum frequency.
- Restore each candidate before the next test. Subsequent comparisons used
  SurfaceFlinger/sysfs only to avoid kernel-tracing overhead in frame measurements.
- GPU-specific traces were collected separately. The first 8 MiB GPU trace filled
  its buffer and had patch errors; it was excluded from job timing. Repeated
  32 MiB traces completed without trace errors.

Local raw evidence, screenshots, Perfetto dependencies, and recovery metadata are
in `tools/extreme_trial_20261015/`, excluded from Git. Source tools and this report
remain available for future agents; no personal screenshots need to be committed.

## Controlled results

30-second collection windows yield approximately 24.5 seconds of usable frame
history after trimming. Settings and graphics quality were not changed in-game.

| Candidate / phase | FPS | p95 ms | p99 ms | Intervals >50 ms |
| --- | ---: | ---: | ---: | ---: |
| GPU 800 MHz, repeat 1 | 33.77 | 58.16 | 104.14 | 48 / 833 |
| GPU 800 MHz, repeat 2 | 37.51 | 41.60 | 76.79 | 26 / 922 |
| GPU 850 MHz | 38.86 | 41.56 | 58.18 | 13 / 952 |
| GPU 900 MHz | 41.76 | 33.25 | 41.58 | 6 / 1034 |
| Original GPU 950 MHz, baseline 1 | 40.95 | 33.25 | 41.56 | 2 / 1008 |
| Original GPU 950 MHz, baseline 2 | 40.33 | 33.25 | 33.28 | 0 / 993 |
| Original GPU 950 MHz, baseline 3 | 39.19 | 33.26 | 33.29 | 0 / 968 |
| Wakeup granularity 1 ms, repeat 1 | 41.88 | 33.25 | 33.27 | 0 / 1036 |
| Wakeup granularity 1 ms, repeat 2 | 41.52 | 33.25 | 33.27 | 0 / 1018 |
| Migration cost 500 us | 38.02 | 33.26 | 41.58 | 4 / 932 |

The 800 MHz improvement observed in the earlier scene did **not** reproduce here.
Do not select it using the earlier result alone. The 900 MHz result does not show
a reliable stutter advantage over 950 MHz.

The 1 ms wakeup candidate produced a small FPS increase, but baseline CPU ceilings
varied, and p95/p99 and >50 ms counts were already comparable in the later baseline.
This is insufficient evidence for a universal scheduler recommendation. A further
trial should match policy ceilings and repeat longer routes before promotion.

`prefer_idle=1` in the earlier camera sequence returned 37.42 FPS versus 37.38–37.71
for surrounding baselines, with essentially identical p95 intervals. It was restored.
The `always_on` GPU trial crossed resume/thermal and scene changes; its 14.67 FPS
result cannot establish causation and was not used to rank policies. Original
`coarse_demand` was restored.

## Thermal finding

Under sustained play, active `PPM_POLICY_THERMAL` imposed a CPU power budget while
GPU requests still targeted 950 MHz. The big CPU policy ceiling was observed near
0.9–1.0 GHz in an earlier hot interval, and around 1.3–1.4 GHz in later controlled
windows. Requested maximum clocks alone therefore do not describe actual CPU
performance. Lowering the GPU can provide headroom in some scenes, but the tested
fixed lower clocks did not consistently improve this controlled scene.

CPU temperatures during controlled samples were generally around 67–70 °C. Collectors
were configured to abort at CPU temperature >=80 °C or battery temperature >=45 °C. Initial battery
readings at/below zero were not treated as valid evidence of a cool battery.

## Kernel GPU job timing

The driver emitted `gpu_job_enqueue` and `gpu_sched_switch`; game context 103 was
attributed through the submitting thread/process metadata. Full captures had no
trace errors. Durations are reconstructed between consecutive switches per slot;
incomplete final events are excluded.

| Fragment job metric | 900 MHz | 950 MHz |
| --- | ---: | ---: |
| Sampled job-slot executions | 13,444 | 13,610 |
| Execution p95 | 3.1542 ms | 2.9800 ms |
| Execution p99 | 6.0472 ms | 5.6676 ms |
| Estimated enqueue-to-first-execution p95 | 11.7594 ms | 8.6060 ms |

The enqueue estimate converts the GPU event timestamp using recorded
BOOTTIME–MONOTONIC snapshots, consistent with observed clock offsets. No negative
waits were produced. The GPU clock-domain assignment is an inference, and the
wait includes dependency stalls. Job timing is not full-frame GPU duration or
touch-to-photon latency. These short captures favor retaining 950 MHz for GPU
latency in this scene; they do not establish zero stuttering elsewhere.

The generic `gpu_slice` table was empty, so reporting an invented zero GPU latency
from that table would have been incorrect. Raw driver events supplied the evidence.

## EGL cache trial and decision

The first library probe incorrectly relied on binary `grep`, which did not find
NUL-separated ELF strings on this device. No properties were changed by that failed
attempt. The probe was corrected to `strings | grep -Fxq`, then the trial succeeded.

Both debug properties read back as `true` / `33554432`, and the application created
`com.android.opengl.shaders_cache.multifile`. The original 1,721,956-byte monolithic
file was preserved. The new cache used approximately 1.03–1.09 MiB in the observed
menu/world workload, below even the platform's 2 MiB monolithic total budget.
This does not establish which engine-specific shaders use this callback or whether
larger individual entries would be useful in a different workload.

A subsequent warm multifile launch still showed the game logo around 40 seconds,
and the start screen by 65 seconds. After restoring the original properties, the
start screen was already visible by 40 seconds. These are coarse visual milestones,
not isolated shader compilation timings; memory/cache, thermal and startup state
also varied. No defensible startup speedup was established, so multifile was not
retained as a performance preset. The two original overrides were restored to empty
and the game was relaunched to use the original cache mode. Both cache formats were
left intact. No deletion was used to manufacture an improvement.

## Retained configuration and handoff

- GPU requests remain 950 MHz with the original `coarse_demand` policy.
- `prefer_idle` remains 0, wakeup granularity 2,000,000 ns, and migration cost
  50,000 ns. Short candidate results do not justify changing all users' schedulers.
- The measured storage-only read-ahead candidate was reapplied at 256 KiB on the
  verified MountX SD card. The earlier integrity-verification trials support lower
  request waiting at similar throughput, not a proven gameplay/FPS gain.
- All other trial settings were restored. Tracing services were returned to their
  initial stopped state. Native Extreme logic and module files were not replaced.
- These are runtime lab choices, not boot persistence: another profile application
  or reboot can replace them. `docs/X698_GENSHIN_TUNING_20261015.json` is a decision
  record for the next agent, not an automatically loaded module configuration.

Final readbacks confirmed GPU floor/ceiling requests at 950,000 kHz, scheduler
values restored, both EGL overrides empty, both tracing services stopped,
`tracing_on=0`, and SD read-ahead still 256 KiB. Genshin returned to the same world
location and character after the launch trials. Retrieved diagnostic trace copies
were removed from the device; local evidence and device recovery scripts remain.

Python source compilation and Android shell syntax checks cover the lab helpers.
No application build or full application test suite was run. Longer traversal and
combat repeats under matched thermal ceilings are still needed for broad gameplay
claims; no zero-stutter or bottleneck-free guarantee is supported.

## Tools and reproduction

- `tools/extreme_latency_trial.py`: bounded single-candidate trial, scene screenshots,
  shared mutex, temperature guard, recovery journal, and automatic restoration.
- `tools/analyze_extreme_latency.py`: deduplicated frame statistics, thermal/policy
  summaries, and trace-quality reporting.
- `tools/analyze_gpu_job_trace.py`: attributed job timing, clock checks, and rejection
  of incomplete traces or negative queue estimates.
- `tools/x698_gpu_trial_profile.py`: opt-in host helper for later 800/850/900 MHz
  experiments, with dynamic OPP validation and explicit restore. It does not add
  any candidate to boot or universal Extreme presets.
- `tools/genshin_launch_probe.py`: bounded launch snapshots that preserve caches.

## References

- [Android sustained performance management](https://source.android.com/docs/core/power/performance)
- [Android thermal adaptation](https://developer.android.com/games/optimize/adpf/thermal)
- [SurfaceFlinger presentation-history implementation](https://android.googlesource.com/platform/frameworks/native/+/8c9cd3c537f7d4781862e2d95b32247fa5bb3d66/services/surfaceflinger/FrameTracker.cpp)
- [Perfetto GPU data sources](https://perfetto.dev/docs/data-sources/gpu)
- [Kernel GPU tracepoint semantics](https://android.googlesource.com/kernel/common/+/cdf37850b4298e8e783620a9fa7286924d6a6e96/include/trace/events/gpu.h)
- [AOSP EGL monolithic/multifile cache implementation](https://android.googlesource.com/platform/frameworks/native/+/master/opengl/libs/EGL/egl_cache.cpp)
