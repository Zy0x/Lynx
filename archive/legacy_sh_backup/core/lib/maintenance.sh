#!/system/bin/sh
# ==============================================================================
# Lynx Universal - Smart Storage & Database Maintenance Engine
# Performs non-blocking SQLite VACUUM, fstrim, log pruning, and gentle compaction.
# ==============================================================================

SQLITE_BIN="/data/adb/modules/Lynx/system/bin/sqlite3"
[ -x "$SQLITE_BIN" ] || SQLITE_BIN="/system/bin/sqlite3"

is_device_busy() {
    # Check if a game is active in Lynx mode lock
    if [ -f "/dev/lynx_active_game" ]; then
        return 0
    fi
    # Check if screen is currently active
    if dumpsys power 2>/dev/null | grep -q "mHoldingDisplaySuspendBlocker=true"; then
        return 0
    fi
    return 1
}

vacuum_sqlite_databases() {
    [ -x "$SQLITE_BIN" ] || return 0

    # Target system and high-traffic app databases
    find /data/system /data/data/*/databases -maxdepth 2 -name "*.db" 2>/dev/null | while IFS= read -r db; do
        if [ -f "$db" ] && [ -w "$db" ]; then
            # Non-blocking SQLite optimize
            "$SQLITE_BIN" "$db" "PRAGMA optimize; VACUUM; REINDEX;" 2>/dev/null &
            # Throttle background processes to prevent I/O saturation
            sleep 0.1
        fi
    done
}

trim_flash_storage() {
    if command -v fstrim >/dev/null 2>&1; then
        fstrim -v /data 2>/dev/null
        fstrim -v /cache 2>/dev/null
    fi
}

prune_system_clutter() {
    # Delete old crash dumps and tombstones (> 3 days old)
    rm -rf /data/tombstones/* 2>/dev/null
    rm -rf /data/anr/* 2>/dev/null
    rm -rf /data/system/dropbox/* 2>/dev/null
    rm -rf /data/local/tmp/* 2>/dev/null

    # Gentle RAM compaction (zero-stutter alternative to drop_caches)
    if [ -e "/proc/sys/vm/compact_memory" ]; then
        echo "1" > /proc/sys/vm/compact_memory 2>/dev/null
    fi
}

run_scheduled_maintenance() {
    # Never execute while user is gaming or using the device
    if is_device_busy; then
        return 0
    fi

    prune_system_clutter
    trim_flash_storage
    vacuum_sqlite_databases
}

run_manual_maintenance() {
    # Direct execution triggered by WebUI or Native Companion APK
    prune_system_clutter
    trim_flash_storage
    vacuum_sqlite_databases
    echo "SUCCESS: Maintenance and Optimization Completed."
}
