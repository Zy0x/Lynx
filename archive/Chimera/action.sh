#!/system/bin/sh

# Android Root Cache Cleaner Script
# Description: Clears application and system cache, and temporary files without deleting user data
# Notes: Requires root access (su). Reboot recommended after execution.
# Compatibility: Android 4.0 and above

# Function to handle errors and exit
error_exit() {
    echo "[ERROR] $1"
    exit 1
}

# Verify root access
if [ "$(id -u)" -ne 0 ]; then
    error_exit "This script requires root access. Run with 'su'."
fi

echo "[INFO] Starting cache cleanup process..."
sleep 1

# Calculate initial free space on /data
INITIAL_FREE=$(df -h /data | awk 'NR==2 {print $4}' 2>/dev/null || echo "Unavailable")
echo "[INFO] Initial free space: $INITIAL_FREE"

# 1. Clear application cache using pm trim-caches
echo "[INFO] Clearing application cache..."
if command -v pm >/dev/null 2>&1; then
    pm trim-caches 999G 2>/dev/null || echo "[WARNING] Failed to use pm trim-caches, proceeding with manual cleanup."
else
    echo "[WARNING] 'pm' command not found, proceeding with manual cleanup."
fi

# 2. Manually clear application cache for thorough cleanup
echo "[INFO] Checking and clearing application cache manually..."
for pkg in $(pm list packages 2>/dev/null | cut -d: -f2); do
    cache_dir="/data/data/$pkg/cache"
    ext_cache_dir="/sdcard/Android/data/$pkg/cache"
    if [ -d "$cache_dir" ]; then
        rm -rf "$cache_dir"/* 2>/dev/null && echo "[INFO] Cleared cache for $pkg."
    fi
    if [ -d "$ext_cache_dir" ]; then
        rm -rf "$ext_cache_dir"/* 2>/dev/null
    fi
done

# 3. Clear Dalvik/ART cache (requires reboot for optimization)
if [ -d "/data/dalvik-cache" ]; then
    echo "[INFO] Clearing Dalvik/ART cache..."
    rm -rf /data/dalvik-cache/* 2>/dev/null || echo "[WARNING] Failed to clear Dalvik/ART cache."
fi

# 4. Clear camera/gallery thumbnails
if [ -d "/sdcard/DCIM/.thumbnails" ]; then
    echo "[INFO] Clearing gallery thumbnails..."
    rm -rf /sdcard/DCIM/.thumbnails/* 2>/dev/null || echo "[WARNING] Failed to clear gallery thumbnails."
fi

# 5. Clear system cache
for dir in /cache /data/cache; do
    if [ -d "$dir" ]; then
        echo "[INFO] Clearing $dir..."
        rm -rf "$dir"/* 2>/dev/null || echo "[WARNING] Failed to clear $dir."
    fi
done

# 6. Clear tombstones and system logs (optional)
for dir in /data/tombstones /data/anr /data/system/dropbox; do
    if [ -d "$dir" ]; then
        echo "[INFO] Clearing $dir..."
        rm -rf "$dir"/* 2>/dev/null || echo "[WARNING] Failed to clear $dir."
    fi
done

# Synchronize changes to storage
echo "[INFO] Synchronizing changes to storage..."
sync 2>/dev/null || echo "[WARNING] Failed to synchronize changes."

# Calculate final free space
FINAL_FREE=$(df -h /data | awk 'NR==2 {print $4}' 2>/dev/null || echo "Unavailable")

# Display results
echo "[INFO] Cache cleanup completed!"
echo "[INFO] Initial free space: $INITIAL_FREE"
echo "[INFO] Current free space: $FINAL_FREE"
echo "[INFO] Reboot recommended to optimize Dalvik/ART cache."
echo "[INFO] Reboot command: reboot"