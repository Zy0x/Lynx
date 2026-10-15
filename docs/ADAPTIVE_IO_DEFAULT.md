# Adaptive storage I/O default

## Scope

The production source uses one policy in `native/lynxd/src/hardware/storage.rs` for active profile transitions and `lynxd io apply`. Existing `mountx-tune` / `flash-boost` commands delegate to the same policy. Dormant first boot is unchanged. This source change has not been built or installed.

| Detected medium | Default action |
| --- | --- |
| SD / SD combo | Cap read-ahead at 256 KiB; retain smaller values, including zero. Skip unreadable or read-only queues. |
| eMMC | Preserve kernel queue settings. |
| UFS / NVMe / USB | Preserve kernel queue settings. |
| Unknown / loop / virtual | Preserve kernel queue settings. |

The ceiling is a conservative policy based on the SD measurements in `GENSHIN_MOUNTX_IO_TRIAL_20261014.md`, not an automatically benchmarked optimum for every card. The recorded sequential workload stayed near 22 MiB/s while waiting and queue occupancy decreased. It does not establish faster shader compilation, increased write throughput, or universally stutter-free gameplay.

## Detection and recovery

- Enumerate actual `/sys/block` entries. Kernel `device/type` distinguishes `SD`, `SDcombo`, and `MMC`. Inspect controller subsystem/driver ancestry for UFS, NVMe, and USB; unrecognized transports stay unknown. Disk names such as `mmcblk0` and `sda` do not determine media type.
- Resolve mounted SD filesystems using `/proc/self/mountinfo` major:minor links under `/sys/dev/block`. Resolve partition parents and device-mapper slave relationships without parsing a partition suffix. Ambiguous multi-device mappings are excluded from the legacy single-device status endpoint.
- Preserve scheduler, request depth, affinity, accounting, write cache, filesystem GC/checkpoint/fsync settings, and global VM writeback. Explicit scheduler controls remain available.
- Before a changed read-ahead write, save its original value to volatile `/dev/lynx_io/` state, bound to boot ID, card CID, major:minor, and canonical device path. Missing identity or failed backup prevents the write. Default commands use readback verification; profile transactions use the existing post-commit verification and snapshot pipeline.
- `mountx-restore` / `flash-restore` restore only records matching the same card and boot, and only if the value still matches the recorded applied target or original. Later external changes are preserved. Selecting a profile reapplies the adaptive default.
- A later profile cannot reintroduce the old 2048 KiB prefetch or the bundled F2FS/VM/thread adjustments. Existing changes from an older installed build are not reset to guessed OEM values; restore a known backup or reboot before assessing this source revision.

## Interface contract

`io info` exposes actual `current_read_ahead_kb`, optional `target_read_ahead_kb`, detected `type`, and a technical `policy` identifier. A null target means preserve the kernel setting. Default/restore actions return structured JSON with verified counts and failure status. Companion uses ID/EN resources, shows actual scheduler/read-ahead values, and no longer claims that VM pacing or MMCQD realtime priority has been applied.

The WebUI consumes the same native device fields and verified scheduler result rather than the obsolete `avail`/`cur` schema or unconditional success messages. Its scheduler failure message is included in both locale catalogs.

## Validation still required by the build agent

No build, unit tests, device writes, installation, or performance rerun was performed for this source edit. Review and compile the native engine and Release Companion, then exercise SD/MMC identification, UFS versus USB SCSI disks, unknown transports, dm-backed mounts, multiple SD devices, missing/read-only nodes, smaller/zero prefetch, failed writes, backup failure, card replacement, reboot recovery rejection, and all active profiles. Confirm ID/EN resource placeholders and UI readbacks. Separate hardware measurements are required before changing defaults for other media.

Static inspection parsed all three Android resource XML files (17 policy keys each), formatted the new Rust modules, and found no whitespace errors in the working diff. Existing unrelated Kotlin file-budget violations remain in `ToolsTabContent.kt`, `LynxViewModel.kt`, `LynxRepository.kt`, `LegacyUiTextResources.kt`, `LynxAppAutomationService.kt`, and `ChargingHeroDisplayCard.kt`; these files were not extended by this change.

## Kernel references

- [MMC card type implementation](https://github.com/torvalds/linux/blob/master/drivers/mmc/core/bus.c)
- [Block queue sysfs semantics](https://www.kernel.org/doc/html/v5.15/block/queue-sysfs.html)
