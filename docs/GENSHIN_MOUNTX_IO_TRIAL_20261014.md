# Genshin / MountX storage trial — 2026-10-14

## Outcome and current state

The original aggressive queue configuration did not establish a reliable overall win.
Smaller read-ahead settings are worth testing further: they reduced queued read latency
during the original live verification session without a demonstrated throughput increase.
No configuration has passed gameplay frame-time validation. No zero-stutter or absolute
optimality claim is supported by these measurements.

## Resumed live verification

The user subsequently resumed Genshin verification and reported 53% after 15 minutes.
Five additional approximately 21-second windows used only the verifier's existing I/O;
no synthetic read or write workload ran alongside it. All five windows retained the
requested kernel settings. The process and boot identity remained unchanged.

| Live configuration | SD read, MiB/s | Mean block read wait, ms | Mean queue depth |
| --- | ---: | ---: | ---: |
| Active settings: RA2048 / Q512 / affinity2 / batch64 | 22.031 | 262.920 | 14.005 |
| Only read-ahead changed to 256 KiB | 21.995 | 107.801 | 5.651 |
| Active settings repeated | 22.004 | 233.451 | 11.747 |
| RA256 / Q128 / affinity2 / batch8 | 21.997 | 107.014 | 5.377 |
| RA512 / Q128 / affinity2 / batch8 | 21.961 | 105.828 | 5.413 |

SD busy-time accounting was approximately 100% in each window. The smaller prefetch
queue lowered average queued-request wait without increasing sustained read throughput.
These statistics do not establish lower game frame times or a shorter full verification.
Device-wide I/O pressure also varied, so no global stall reduction is claimed.

The final applied adjustment changes **only read-ahead to 256 KiB**. Request count,
affinity, scheduler, batch size, VM, filesystem, CPU, network, and charging controls are
preserved. The extra request/batch changes did not demonstrate a separate benefit.
Readback at application time, approximately 10 seconds later, and approximately 20 seconds
later confirmed 256 KiB while Genshin remained running. No boot persistence was added;
a later performance-profile application may overwrite this value.

The game process's mount namespace maps its asset paths directly to F2FS on the SD
partition. That mapping is working; a missing MountX bind mount was not the explanation.

Live results: `tools/io_trial_20261014/live_resume/summary.json`.
The reversible script now saves/restores only the changed read-ahead value, guarded
by boot and card identity. This supersedes the earlier multi-setting candidate below.

## Earlier bounded session

Active testing was stopped when the user prioritized separate fast-charging work.
That session's queue baseline was restored: `deadline`, read-ahead `512` KiB,
`nr_requests=128`, `rq_affinity=1`, `fifo_batch=16`, and `iostats=0`.
F2FS remains at the post-restart baseline with `background_gc=on`, `discard`, and
`fsync_mode=posix`. No filesystem remount, CPU boost, thermal override, network setting,
global VM adjustment, module installation, or boot persistence was added by this trial.

The 128 MiB MicroSD scratch file and obsolete device-side probes were removed.
At that point a multi-setting candidate was prepared but not applied as a final profile.
It was subsequently narrowed to the read-ahead-only adjustment described above.

## Device and methodology

- Verified test device: Infinix X698, MT6781; the reference Redmi was not used.
- Storage: MountX on `/data/sdext2`, F2FS on SD partition `mmcblk0p3`.
- Initial live workload: Genshin file-integrity verification, 1,429 files reported by its log.
- The user explicitly approved a temporary MicroSD file under
  `/data/sdext2/Debug/lynx_io_trial/`, with a maximum size of 256 MB. Actual size: 128 MiB.
- The completed post-restart tests used buffered I/O, matching ordinary application I/O.
  Only the scratch file's clean cached pages were evicted. Global caches were not dropped.
- Each cold-read sample read 32 MiB and confirmed at least 32 MiB of physical reads through
  `/proc/self/io`; cache-only throughput samples were rejected.
- Each write sample wrote 16 MiB, included `sync_all` in elapsed time, evicted the scratch
  cache, then reread and compared all written bytes. All seven completed samples passed.
- A mixed sample issued 100 random 4 KiB reads alongside 8 MiB of writes synchronized in
  1 MiB increments. It measures storage contention, not rendered frames or game stuttering.
- Completed tests ran while Genshin was not running after the restart. Their results must
  not be treated as a speedup over the earlier, competing live-verification workload.
- Queue settings were read before and after each bounded phase; boot identity and exit
  traps protected the current boot's baseline. Results with reversion/accounting loss
  from earlier phases were marked invalid for block-latency comparisons.

## Completed buffered file measurements

All speeds below are MiB/s. Ranges are the minimum and maximum of only two runs,
not confidence intervals. The original queue settings received one completed sample.

| Configuration | Cold sequential read | Write including sync | Random read IOPS | Mixed read p95, ms |
| --- | ---: | ---: | ---: | ---: |
| Current-boot baseline: RA512, Q128, affinity1, batch16 | 18.33–21.79 | 6.07–10.36 | 1,947–2,246 | 51.17–155.52 |
| Original script's queue: RA2048, Q512, affinity2, batch64 | 20.81 | 6.03 | 911 | 161.19 |
| Candidate: RA512, Q128, affinity1, batch8 | 21.75 | 5.94 | 1,853 | 157.93 |
| Candidate: RA256, Q128, affinity1, batch8 | 21.68–21.76 | 6.12–19.42 | 2,500–2,810 | 50.83–164.98 |
| Noop: RA512, Q128, affinity1 | 21.79 | 6.12 | 2,049 | 163.04 |

The RA256 candidate showed promising sequential-read efficiency and random-read throughput.
Its mixed-write latency and write throughput varied substantially, so it is **not a proven
overall winner**. The 19.42 MiB/s write sample must not be advertised as a sustained write
rate or guaranteed improvement. Multiple settings changed together in these comparisons;
isolated effects of request count, affinity, and batch size were not established.

Raw results: `tools/io_trial_20261014/bounded/summary.json` and its phase text files.

## Earlier live-verification observations

Live verification generally consumed approximately 11 MiB/s of SD reads regardless of
read-ahead changes. With the original active 4096 KiB read-ahead, two short windows showed
mean block read waits of 577 and 591 ms. A 512 KiB window showed 254 ms; later 256–512 KiB
windows showed roughly 220–224 ms. These are device-level average request waits during
changing game work, not per-frame latency or a controlled full-verification completion time.

Some later windows experienced configuration changes or disabled I/O accounting.
Their block metrics were discarded. Initial measurements that lacked recurring setting
readback have weaker configuration guarantees than the completed bounded tests.

## Comparison with the supplied script

| Component | Assessment | Decision for the current candidate |
| --- | --- | --- |
| Hardcoded `mmcblk0` and substring mount matching | Can target the wrong device or bind-mount entry on other layouts | Resolve the exact mount and validate the SD parent device |
| `deadline` scheduler | Supported by this kernel; neither noop nor the original queue proved superior overall | Retain as a device-specific candidate |
| Read-ahead 2048 KiB | More physical prefetch than RA256; no demonstrated live-verification throughput benefit | Compare RA256/512 further; do not increase it automatically |
| `nr_requests=512` | No isolated benefit established; more allocated requests are not faster flash hardware | Keep the current baseline 128 |
| `rq_affinity=2` | No isolated benefit established | Keep the baseline 1 |
| `fifo_batch=64` | No reliable mixed-latency advantage; larger batches can increase latency variation | Use 8 only in the candidate, pending gameplay validation |
| `nomerges=0` | Already enabled merging; no before/after improvement measured | Preserve current value |
| `iostats=0` | Disables observability, not an established speed improvement here | Enable temporarily for measurement, then restore baseline |
| `add_random=0`, `rotational=0` | Already set on this SD queue | No redundant write required |
| `fsync_mode=nobarrier` | Changes flush/durability semantics; not a demonstrated read-verification optimization | Exclude; retain posix |
| `background_gc=off`, `nodiscard` | Alters free-space maintenance; no isolated benefit established | Exclude; preserve post-restart mount options |
| `gc_urgent=0` | Already zero | No redundant write required |
| `cp_interval=300`, `ipu_policy=7`, `min_fsync_blocks=64` | Not tested in isolation; these change checkpoint/allocation/sync behavior | Preserve post-restart 60 / 16 / 8 |
| Global VM dirty limits and shorter expiry | Can change background writeback timing, but the completed tests explicitly sync writes; no measured benefit established | Exclude from the candidate; evaluate separately under sustained background writes |
| `renice -20` on mmcqd | Does not increase media bandwidth; no isolated CPU-scheduling benefit established | Exclude pending evidence of CPU dispatch delays |
| Top-app schedtune boosts | CPU policy changes, with no isolated I/O benefit established in this trial | Exclude, especially during concurrent charging work |
| TCP knobs / receive-buffer cap | No role in local file-integrity reads; network downloads require separate measurement | Exclude from the storage profile |

The original script's VM, F2FS, CPU, and network sections were reviewed but were not
reapplied together after the restart. Therefore the completed comparison is a comparison
of queue configurations, not a benchmark of the entire original script.

## Restart and excluded results

The device restarted during the earlier, much longer direct-I/O experiment. After recovery,
Android reported `kernel_panic`, but retained console logs did not contain a useful panic
stack. Causality cannot be established from the retained evidence. That interrupted run,
earlier probe failures, and the initial scratch-creation number are excluded from results.
The direct-I/O experiment was not repeated. The subsequent bounded buffered tests completed
within the same boot and their written data passed verification.

## Remaining validation

1. After charging work finishes, isolate read-ahead changes under the same Genshin workload.
2. Compare complete verification duration with equivalent cold-cache and thermal conditions.
3. Record representative gameplay frame times, slow frames, I/O pressure, and temperatures;
   file-test p95 values cannot substitute for this step.
4. Repeat mixed write/read tests long enough to characterize write-speed variance without
   concurrent charging experiments. Retain a candidate only if throughput and latency wins repeat.
5. Resolve any competing runtime tuner before introducing persistent I/O settings.

## Kernel references

- [Block queue sysfs documentation](https://www.kernel.org/doc/html/v5.15/block/queue-sysfs.html)
- [Deadline scheduler tunables](https://www.kernel.org/doc/html/next/block/deadline-iosched.html)
- [F2FS mount options and sync semantics](https://www.kernel.org/doc/html/latest/filesystems/f2fs.html)
- [Network sysctls](https://www.kernel.org/doc/html/latest/networking/ip-sysctl.html)

Current upstream network documentation marks `tcp_low_latency` as a legacy no-op. The exact
vendor kernel implementation was not audited here; the narrower conclusion used in this
trial is that TCP settings do not tune local storage verification.

## Source checks

The buffered Rust diagnostic compiled for Android arm64. The Python trial tools passed
syntax compilation, and Android's shell accepted the candidate script with `sh -n`.
The candidate's read-only `status` command resolved the expected SD partition correctly.
No Companion APK build was performed.

The repository-wide localization validator and diff check currently report issues in
concurrently edited Companion files outside this trial. Those unrelated files were left
to their active work. No full-repository clean-check claim is made here.


## Saved handoff and shader follow-up

The measured read-ahead-only setting is retained in
`tools/genshin_sd_io_profile.sh` with apply/status/restore operations and boot/card
identity checks. It remains a local, opt-in experiment for later testing, with no
boot hook or module installation. The live read-ahead value was still 256 KiB during
the subsequent shader inspection. Do not replace this narrow candidate with the
original combined VM/F2FS/CPU/network script.

The shader-stage diagnosis and a separate, unmeasured EGL cache candidate are in
`docs/GENSHIN_SHADER_AUDIT_20261014.md`. Storage verification results do not establish
shader compilation or gameplay improvements.
