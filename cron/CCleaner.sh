#!/bin/bash

LOG_FILE="/storage/emulated/0/Lynx/Lynx.log"

# Logging function
log_msg() {
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] $1"
    echo "$msg"
    # Ensure log directory exists
    mkdir -p "$(dirname "$LOG_FILE")" 2>/dev/null || {
        echo "Error: Cannot create log directory $(dirname "$LOG_FILE")"
    }
    # Check if log file is writable
    if [ -d "$(dirname "$LOG_FILE")" ] && touch "$LOG_FILE" 2>/dev/null; then
        echo "$msg" >> "$LOG_FILE" 2>/dev/null || echo "Warning: Failed to write to $LOG_FILE"
    else
        echo "Warning: Log file $LOG_FILE is not writable"
    fi
}

# Check for root access
if [ "$(id -u)" -ne 0 ]; then
    log_msg "Error: This script requires root access. Please run it with 'su'."
    exit 1
fi

log_msg "Starting cache clearing process..."
sleep 2

# Clean Cache App
log_msg "Cleaning Cache App..."
for dir in /cache /data/cache; do
    if [ -d "$dir" ]; then
        rm -rf "$dir"/* 2>/dev/null && log_msg "Cleared $dir" || log_msg "Error: Failed to clear $dir"
    else
        log_msg "Warning: Directory $dir does not exist"
    fi
    sleep 2
done

# Clean Dalvik-Cache
log_msg "Clearing Dalvik cache..."
if [ -d /data/dalvik-cache ]; then
    rm -rf /data/dalvik-cache/* 2>/dev/null && log_msg "Cleared /data/dalvik-cache" || log_msg "Error: Failed to clear /data/dalvik-cache"
else
    log_msg "Warning: Directory /data/dalvik-cache does not exist"
fi
sleep 2

# Cleaning tombstones (optional)
log_msg "Cleaning tombstones..."
if [ -d /data/tombstones ]; then
    rm -rf /data/tombstones/* 2>/dev/null && log_msg "Cleared /data/tombstones" || log_msg "Error: Failed to clear /data/tombstones"
else
    log_msg "Warning: Directory /data/tombstones does not exist"
fi
sleep 2

# Clearing system logs (optional)
log_msg "Clearing system logs..."
for dir in /data/anr /data/system/dropbox; do
    if [ -d "$dir" ]; then
        rm -rf "$dir"/* 2>/dev/null && log_msg "Cleared $dir" || log_msg "Error: Failed to clear $dir"
    else
        log_msg "Warning: Directory $dir does not exist"
    fi
    sleep 2
done

# Post notification
su -lp 2000 -c "cmd notification post -S bigtext -t 'Lʏɴx - Dᴇɪᴛʏ' 'Lʏɴx' '🧹 Cache Cleaned'" 2>/dev/null
log_msg "Cache Cleaned!"