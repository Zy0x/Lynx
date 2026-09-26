# 💾 Module 07: Storage, I/O Scheduler & File System Engine
> **Subsystem**: Storage & Virtual Memory Compactor (`core/CCleaner.sh`, `core/lib/io.sh`, `core/lib/sqlite.sh`)  
> **Target Filesystems**: Ext4, F2FS, EROFS, UFS 2.1 – 4.0, eMMC 5.1  

---

## 1. Problem Statement & Memory Fragmentation

### 1.1 The Destructive Drop Caches Antipattern
Legacy performance scripts routinely executed:
```bash
sync && echo 3 > /proc/sys/vm/drop_caches
am kill-all
```
On high-end UFS 4.0 devices with 12–16GB RAM, this antipattern causes brief stutter. However, on devices with **4GB RAM and eMMC or UFS 2.1 storage** (e.g., Helio G85, Snapdragon 660/680), dropping clean page caches forces the Linux kernel to immediately re-read launcher icons, font caches, and ART bytecode from slow flash storage. This causes **catastrophic 2 to 3-second UI freezes and launcher crashes**.

### 1.2 Gentle Virtual Memory Compaction
The Linux kernel provides an efficient alternative: **Memory Compaction** (`/proc/sys/vm/compact_memory`).
Compaction shifts allocated physical pages together, creating contiguous free page blocks for memory allocations (satisfying high-order allocations required by games) without dumping cached disk pages.

---

## 2. Kernel Node & Storage Reference Matrix

| Subsystem Path | Default Value | Tuned Value | Function |
| :--- | :--- | :--- | :--- |
| `/proc/sys/vm/compact_memory` | `0` | Write `1` | Triggers asynchronous contiguous page compaction. |
| `/sys/block/*/queue/read_ahead_kb` | `128` | `512` | Read-ahead buffer size for flash memory. |
| `/sys/block/*/queue/nr_requests` | `64` | `128` | Queue depth for block device requests. |
| `/sys/block/*/queue/rotational` | `0` | `0` | Confirms flash media (non-spinning disk). |
| `/sys/block/*/queue/iostats` | `1` | `0` | Disables I/O statistics collection to reduce CPU overhead. |
| `/sys/block/*/queue/add_random` | `1` | `0` | Prevents disk entropy contribution to avoid I/O stalls. |
| `/sys/block/*/queue/scheduler` | `mq-deadline` / `none` | `none` / `noop` | Elevators: `none` for fast NVMe/UFS, `mq-deadline` for eMMC. |

---

## 3. Storage Optimization Protocols

### 3.1 Weekly / Boot Fstrim Automation
Flash memory cannot overwrite existing NAND blocks without erasing them first. `fstrim` informs the UFS/eMMC controller which LBAs are no longer in use, allowing the internal garbage collector to prepare clean erase blocks:
```bash
run_fstrim() {
    for part in /system /vendor /data /cache /product /system_ext /metadata; do
        if [ -d "$part" ]; then
            fstrim -v "$part" 2>/dev/null
        fi
    done
}
```

### 3.2 SQLite3 WAL (Write-Ahead Logging) Optimization
Android applications store messages, settings, and media indexes in SQLite databases (`.db`). Over months of use, these files become fragmented. Lynx runs vacuum and integrity checkpoints:
```bash
optimize_sqlite() {
    for db in $(find /data/data -name "*.db" 2>/dev/null); do
        sqlite3 "$db" "PRAGMA wal_checkpoint(TRUNCATE);" 2>/dev/null
        sqlite3 "$db" "VACUUM;" 2>/dev/null
    done
}
```

---

## 4. Production Implementation (`core/CCleaner.sh`)

```bash
#!/system/bin/sh
# Lynx Universal - Gentle Memory Compactor & Cache Cleaner
# Fully POSIX compliant for /system/bin/sh

LOG_FILE="/storage/emulated/0/Lynx/cleaner.log"

log_msg() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOG_FILE"
}

log_msg "Starting Lynx Maintenance..."

# 1. Gentle Memory Compaction (Zero Launcher Freeze)
if [ -e "/proc/sys/vm/compact_memory" ]; then
    echo "1" > /proc/sys/vm/compact_memory
    log_msg "Contiguous physical memory compaction completed."
fi

# 2. Block I/O Tuning for UFS & eMMC
for queue in /sys/block/*/queue; do
    [ -d "$queue" ] || continue
    # Skip loop and ram devices
    case "$queue" in
        *loop*|*ram*) continue ;;
    esac

    echo "512" > "$queue/read_ahead_kb" 2>/dev/null
    echo "128" > "$queue/nr_requests" 2>/dev/null
    echo "0" > "$queue/iostats" 2>/dev/null
    echo "0" > "$queue/add_random" 2>/dev/null
    
    # Modern UFS prefers 'none', older eMMC prefers 'mq-deadline' or 'noop'
    if grep -q "none" "$queue/scheduler" 2>/dev/null; then
        echo "none" > "$queue/scheduler" 2>/dev/null
    elif grep -q "noop" "$queue/scheduler" 2>/dev/null; then
        echo "noop" > "$queue/scheduler" 2>/dev/null
    fi
done

# 3. Clean System Tombstones and Crash Dumps
rm -rf /data/tombstones/* /data/system/dropbox/* /data/anr/* 2>/dev/null
log_msg "Purged crash logs and ANR dumps."

# 4. Perform Fstrim
for mount_pt in /data /cache /system /vendor /product; do
    [ -d "$mount_pt" ] && fstrim -v "$mount_pt" 2>/dev/null
done
log_msg "Storage Fstrim discard complete."
```
