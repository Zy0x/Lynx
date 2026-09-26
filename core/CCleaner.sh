#!/system/bin/sh
# Lynx Universal - Gentle Memory Compactor & Storage Maintenance
# Fully POSIX compliant for standard Android /system/bin/sh

LOG_FILE="/storage/emulated/0/Lynx/cleaner.log"

log_msg() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOG_FILE" 2>/dev/null
}

log_msg "Starting Lynx Storage & Memory Maintenance..."

# 1. Gentle Memory Compaction (Eliminates Launcher Freeze)
if [ -e "/proc/sys/vm/compact_memory" ]; then
    echo "1" > /proc/sys/vm/compact_memory
    log_msg "Contiguous physical memory compaction completed."
fi

# 2. Block I/O Tuning for UFS & eMMC
for queue in /sys/block/*/queue; do
    [ -d "$queue" ] || continue
    case "$queue" in
        *loop*|*ram*) continue ;;
    esac

    echo "512" > "$queue/read_ahead_kb" 2>/dev/null
    echo "128" > "$queue/nr_requests" 2>/dev/null
    echo "0" > "$queue/iostats" 2>/dev/null
    echo "0" > "$queue/add_random" 2>/dev/null

    if grep -q "none" "$queue/scheduler" 2>/dev/null; then
        echo "none" > "$queue/scheduler" 2>/dev/null
    elif grep -q "noop" "$queue/scheduler" 2>/dev/null; then
        echo "noop" > "$queue/scheduler" 2>/dev/null
    fi
done

# 3. Clean Crash Logs and Tombstones
rm -rf /data/tombstones/* /data/system/dropbox/* /data/anr/* 2>/dev/null
log_msg "Purged crash logs and ANR dumps."

# 4. Storage Fstrim Discard
for mount_pt in /data /cache /system /vendor /product /metadata /system_ext; do
    [ -d "$mount_pt" ] && fstrim -v "$mount_pt" 2>/dev/null
done
log_msg "Storage Fstrim discard completed."

# Visual Toast Feedback
am start -a android.intent.action.MAIN -e toasttext "🧹 Cᴀᴄʜᴇ & Mᴇᴍᴏʀʏ Oᴘᴛɪᴍɪᴢᴇᴅ" -n bellavita.toast/.MainActivity >/dev/null 2>&1
