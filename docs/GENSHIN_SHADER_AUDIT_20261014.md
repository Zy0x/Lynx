# Genshin shader compilation inspection — 2026-10-14

## Scope and observations

Read-only inspection of the verified Infinix X698 / mt6781, Genshin PID 20910.
Charging, thermal controls, CPU policies, game files, and caches were not modified.
No APK build, install, module write, game restart, cache purge, or live EGL-property
change was performed. Existing SD read-ahead remained 256 KiB.

The two screenshots in `tools/io_trial_20261014/shader/` show compilation advancing
from 27.53% to 39.01%. They establish progress, not a speedup caused by this work.

During the 8.48-second sample, using the device's verified `CLK_TCK=100`:

| Observation | Result |
| --- | --- |
| Total game thread CPU time / elapsed time | 142.93% of one CPU |
| Two busiest `UnityGfxDeviceW` threads | 64.27% and 52.24% |
| Process physical read delta | 0 bytes |
| Process physical write accounting delta | 8,888,320 bytes, approximately 1.00 MiB/s |
| SD block counters | All counters unchanged |
| I/O PSI full avg10 at end of sample | 0.00% |
| CPU policies at sampled endpoints | 2,000,000 and 2,050,000 kHz, matching policy maxima |
| Game and inspected graphics thread allowed CPUs | 0–7 |
| Battery temperature | 28 °C |
| CPU sensor, successive spot reads | Approximately 65.6–68.0 °C |

These observations point to engine/driver graphics compilation work rather than
SD bandwidth saturation during this interval. They do not prove that every phase
is CPU-bound, exclude all thermal transients, or identify shader compiler call
stacks. Additional queue depth or CPU clock requests have no demonstrated benefit.

An open game descriptor points to:
`/data/user_de/0/com.miHoYo.GenshinImpact/cache/com.android.opengl.shaders_cache`.
Its size at the later check was 1,831,124 bytes. The process maps Mali OpenGL ES
driver `libGLES_mali.so`. This particular cache lives on internal `/data`, not the
MountX SD partition. Other engine caches may exist. The external `UnityShaderBin`
directory was not assumed to contain reusable driver-compiled pipelines.

## Corrected Companion behavior

- Removed the supposed shader prewarm operation. It actually forced ART
  `speed-profile` compilation on the first twelve third-party packages and always
  printed success, even when individual compilation commands failed. ART compiles
  Android bytecode; this cannot prewarm Genshin's native GPU shaders.
- Removed the blanket shader-cache deletion operation and its UI. Its wildcard
  included the active Genshin cache above and could force shaders to be rebuilt.
- Replaced these actions with read-only cache inspection, honest partial-scan
  guidance, and matching Indonesian/English/default resources and plural summaries.
  No claim of automatic anti-stutter activation remains in this feature.
- Added maintenance rules in `agents.md` to prevent the same mistakes returning.

These source changes prevent misleading or counterproductive actions. They have
not been installed on the phone and do not accelerate its current compilation.

## Optional candidate for later launches: multifile EGL cache

`tools/genshin_shader_cache_trial.sh` saves a separate, unmeasured experiment:

```text
debug.egl.blobcache.multifile=true
debug.egl.blobcache.multifile_limit=33554432
```

Both property names and multifile cache symbols were found in the device's actual
`/system/lib64/libEGL.so`. AOSP documents their implementation in `egl_cache.cpp`.
Presence supports considering a trial; it does not prove that this device's ROM
will honor every behavior identically or that it improves this game's load time.
32 MiB is a bounded experimental budget, not an established optimal value.

The script supports `status|apply|restore`, requires root and X698 identity, checks
for library property names, refuses mutation while Genshin's main process is
running, saves original values before changing them, and verifies property readback.
Recovery metadata is kept under `/storage/emulated/0/Debug/lynx_shader_trial_20261014/`.
The script never deletes caches or stops games. No candidate properties were applied.

Important properties of this trial:

- The controls affect EGL cache initialization across apps; they are not per-game
  settings. Already initialized processes generally retain their existing mode.
- A newly selected cache format may initially be cold. It cannot eliminate the
  first compilation and may add storage/metadata overhead.
- Potential benefits concern reuse and cache retention. Evaluate at least two
  comparable later launches after population, plus gameplay frame times.
- Let the current compilation finish first. Keep game version, graphics settings,
  driver, profile, and thermal conditions stable; compare warm baseline launches
  against warm candidate launches. Do not delete existing caches to manufacture
  a cold-cache comparison or restart an ongoing compilation.
- Restore the saved properties if results regress. The trial is not boot-persistent;
  do not promote it into production presets until repeatable benefits are measured.

## Checks and limitations

Android `sh -n` accepted the candidate script. Its read-only `status` operation
reported both debug properties empty before the trial. New locale keys and plural
placeholders match across fallback, Indonesian, and English catalogs. Removed
shader mutation methods have no remaining Kotlin call sites.

The full localization validator still reports issues in concurrently edited
navigation, automation, MountX, and profile UI files outside this shader change.
No full-repository validation or APK runtime-success claim is made.

## Primary references

- [ART JIT and profile-guided compilation](https://source.android.com/docs/core/runtime/jit-compiler)
- [Unity shader warmup APIs](https://docs.unity3d.com/2019.4/Documentation/ScriptReference/Shader.WarmupAllShaders.html)
- [AOSP EGL cache implementation](https://android.googlesource.com/platform/frameworks/native/+/master/opengl/libs/EGL/egl_cache.cpp)
- [Vulkan pipeline cache lifecycle](https://docs.vulkan.org/guide/latest/pipeline_cache.html)

Vulkan documentation explains the cache reuse principle; the observed active
descriptor in this session was an OpenGL cache. These APIs are engine/driver
facilities, not substitutes for a generic shell `cmd package compile` command.
