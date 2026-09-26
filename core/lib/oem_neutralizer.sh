#!/system/bin/sh
# ==============================================================================
# Lynx Universal - OEM Throttling Neutralizer
# Intercepts and freezes aggressive OEM thermal/FPS clamp services during gaming.
# ==============================================================================

FROZEN_PID_FILE="/dev/lynx_frozen_pids"
[ -d "/dev" ] || FROZEN_PID_FILE="/data/local/tmp/lynx_frozen_pids"

# Target processes known to clamp FPS / overwrite kernel sysfs clocks
OEM_TARGET_PROCESSES="
mi_thermald
thermal-engine
thermal-engine-v2
ituxd
com.samsung.android.game.gos
"

freeze_oem_throttlers() {
    local joyose_neutralize="true"
    if [ -f "/data/adb/modules/Lynx/config.json" ]; then
        if grep -q '"joyose_neutralize": false' "/data/adb/modules/Lynx/config.json" 2>/dev/null; then
            joyose_neutralize="false"
        fi
    fi

    [ "$joyose_neutralize" = "false" ] && return 0

    : > "$FROZEN_PID_FILE"

    for proc_name in $OEM_TARGET_PROCESSES; do
        local pids
        pids=$(pidof "$proc_name" 2>/dev/null)
        if [ -n "$pids" ]; then
            for pid in $pids; do
                if kill -STOP "$pid" 2>/dev/null; then
                    echo "$pid" >> "$FROZEN_PID_FILE"
                fi
            done
        fi
    done

    # Neutralize Xiaomi Joyose FPS limiter while preserving Game Turbo Overlay
    # Joyose monitors game fps and injects drops after 5-10 minutes.
    local joyose_pid
    joyose_pid=$(pgrep -f "com.xiaomi.joyose" 2>/dev/null)
    if [ -n "$joyose_pid" ]; then
        for jpid in $joyose_pid; do
            if kill -STOP "$jpid" 2>/dev/null; then
                echo "$jpid" >> "$FROZEN_PID_FILE"
            fi
        done
    fi
}

unfreeze_oem_throttlers() {
    [ -f "$FROZEN_PID_FILE" ] || return 0

    while IFS= read -r pid; do
        [ -n "$pid" ] && kill -CONT "$pid" 2>/dev/null
    done < "$FROZEN_PID_FILE"

    rm -f "$FROZEN_PID_FILE" 2>/dev/null
}
