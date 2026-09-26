#!/system/bin/sh
# Lynx Universal - Adaptive AI Core Daemon v5.5
# Hybrid Game Detection (applist + Android CATEGORY_GAME + Clones)
# Fully POSIX compliant for standard Android /system/bin/sh

MODDIR="/data/adb/modules/Lynx"
CONFIG_FILE="$MODDIR/config.json"
TARGET_SOC=$(cat "$MODDIR/target_soc" 2>/dev/null | tr -d '[:space:]')
if [ -z "$TARGET_SOC" ] || [ "$TARGET_SOC" = "generic" ]; then
    if [ -f "$MODDIR/core/lib/hw_probe.sh" ]; then
        . "$MODDIR/core/lib/hw_probe.sh"
        hw_soc=$(detect_real_soc)
        [ "$hw_soc" != "generic" ] && TARGET_SOC="$hw_soc"
    fi
fi
[ -z "$TARGET_SOC" ] && TARGET_SOC="generic"
PLATFORM_DIR="$MODDIR/platforms/$TARGET_SOC"
[ -d "$PLATFORM_DIR" ] || PLATFORM_DIR="$MODDIR/platforms/generic"


APPLIST_FILE="/storage/emulated/0/Lynx/applist_perf.txt"
[ -f "$APPLIST_FILE" ] || APPLIST_FILE="$MODDIR/core/applist_perf.txt"

LOCK_FILE="/dev/lynx_mode.lock"

CURRENT_MODE="balance"
EXIT_COOLDOWN=0
COOLDOWN_BUFFER=3 # Seconds to hold performance after leaving game

# 1. Ultra-fast top app retrieval (sub-30ms)
get_top_app() {
    local raw
    raw=$(dumpsys activity activities 2>/dev/null | grep -m1 "topResumedActivity" | grep -oE '[a-zA-Z0-9._]+/[a-zA-Z0-9._]+' | head -n1 | cut -d'/' -f1)
    if [ -z "$raw" ]; then
        raw=$(dumpsys window 2>/dev/null | grep -m1 -E "mCurrentFocus|mFocusedApp" | grep -oE '[a-zA-Z0-9._]+/[a-zA-Z0-9._]+' | head -n1 | cut -d'/' -f1)
    fi
    # Strip user prefixes or clone app tags (e.g. u10_a123 or package:1)
    echo "$raw" | cut -d':' -f1
}

# 2. Screen power state check
is_screen_on() {
    dumpsys power 2>/dev/null | grep -q "mHoldingDisplaySuspendBlocker=true"
}

# 3. Hybrid Game Detection: Static List + Config JSON + System CATEGORY_GAME
is_target_app() {
    local pkg="$1"
    [ -z "$pkg" ] && return 1

    # A. Check static list
    if [ -f "$APPLIST_FILE" ] && grep -Fxq "$pkg" "$APPLIST_FILE"; then
        return 0
    fi

    # B. Check custom apps list in config.json
    if [ -f "$CONFIG_FILE" ] && grep -q "\"$pkg\"" "$CONFIG_FILE" 2>/dev/null; then
        return 0
    fi

    # C. Dynamic Android Framework Category Inspection
    if dumpsys package "$pkg" 2>/dev/null | grep -qE "category=0|category=GAME|appCategory=0"; then
        return 0
    fi

    return 1
}

# 4. Safe Mode Switcher with Atomic Mutex Guard
switch_mode() {
    local target="$1"
    [ "$CURRENT_MODE" = "$target" ] && return 0

    if [ -f "$LOCK_FILE" ]; then
        return 0
    fi
    touch "$LOCK_FILE"

    if [ "$target" = "performance" ]; then
        touch "/dev/lynx_active_game" 2>/dev/null
        [ -f "$MODDIR/core/lib/lowend_shield.sh" ] && . "$MODDIR/core/lib/lowend_shield.sh" && apply_lowend_shield
        [ -f "$MODDIR/core/lib/oem_neutralizer.sh" ] && . "$MODDIR/core/lib/oem_neutralizer.sh" && freeze_oem_throttlers
        [ -f "$MODDIR/core/lib/uclamp.sh" ] && . "$MODDIR/core/lib/uclamp.sh" && apply_uclamp_game
        [ -f "$MODDIR/core/lib/display_touch.sh" ] && . "$MODDIR/core/lib/display_touch.sh" && apply_display_touch_game
        [ -f "$MODDIR/core/lib/network.sh" ] && . "$MODDIR/core/lib/network.sh" && apply_network_game
        [ -f "$MODDIR/core/lib/audio_latency.sh" ] && . "$MODDIR/core/lib/audio_latency.sh" && apply_audio_latency_game
        [ -f "$MODDIR/core/lib/sched_features.sh" ] && . "$MODDIR/core/lib/sched_features.sh" && apply_sched_lib_game
        sh "$PLATFORM_DIR/perf.sh" >/dev/null 2>&1
        CURRENT_MODE="performance"
        setprop lynx.mode performance
        am start -a android.intent.action.MAIN -e toasttext "⚡ Lʏɴx: Pᴇʀꜰᴏʀᴍᴀɴᴄᴇ Mᴏᴅᴇ" -n bellavita.toast/.MainActivity >/dev/null 2>&1
    else
        rm -f "/dev/lynx_active_game" 2>/dev/null
        sh "$PLATFORM_DIR/balance.sh" >/dev/null 2>&1
        [ -f "$MODDIR/core/lib/sched_features.sh" ] && . "$MODDIR/core/lib/sched_features.sh" && apply_sched_lib_balance
        [ -f "$MODDIR/core/lib/oem_neutralizer.sh" ] && . "$MODDIR/core/lib/oem_neutralizer.sh" && unfreeze_oem_throttlers
        [ -f "$MODDIR/core/lib/uclamp.sh" ] && . "$MODDIR/core/lib/uclamp.sh" && apply_uclamp_balance
        [ -f "$MODDIR/core/lib/display_touch.sh" ] && . "$MODDIR/core/lib/display_touch.sh" && apply_display_touch_balance
        [ -f "$MODDIR/core/lib/network.sh" ] && . "$MODDIR/core/lib/network.sh" && apply_network_balance
        [ -f "$MODDIR/core/lib/audio_latency.sh" ] && . "$MODDIR/core/lib/audio_latency.sh" && apply_audio_latency_balance
        [ -f "$MODDIR/core/lib/lowend_shield.sh" ] && . "$MODDIR/core/lib/lowend_shield.sh" && apply_idle_battery_saver
        CURRENT_MODE="balance"
        setprop lynx.mode balance
        am start -a android.intent.action.MAIN -e toasttext "⚖️ Lʏɴx: Bᴀʟᴀɴᴄᴇ Mᴏᴅᴇ" -n bellavita.toast/.MainActivity >/dev/null 2>&1
    fi

    rm -f "$LOCK_FILE"
}

# AI Daemon Main Loop
while true; do
    # Check if module is in dormant mode
    if [ -f "$CONFIG_FILE" ]; then
        active_prof=$(awk -F'"' '/"active_profile"[ \t]*:/ {print $4}' "$CONFIG_FILE" 2>/dev/null)
        if [ "$active_prof" = "dormant" ]; then
            sleep 15
            continue
        elif [ "$active_prof" != "auto" ] && [ -n "$active_prof" ]; then
            # Not in Auto AI mode (manual profile locked by user in WebUI/APK)
            sleep 5
            continue
        fi
    fi

    # Screen-State Deep Sleep Shield
    if ! is_screen_on; then
        if [ "$CURRENT_MODE" != "balance" ]; then
            switch_mode "balance"
            EXIT_COOLDOWN=0
        fi
        sleep 20
        continue
    fi

    # Query Foreground Package
    top_app=$(get_top_app)

    if is_target_app "$top_app"; then
        # Instant 0ms trigger into performance
        EXIT_COOLDOWN=$COOLDOWN_BUFFER
        switch_mode "performance"
    else
        # App is not in target list
        if [ "$EXIT_COOLDOWN" -gt 0 ]; then
            # Hold performance state during cooldown window (hysteresis)
            EXIT_COOLDOWN=$((EXIT_COOLDOWN - 1))
        else
            switch_mode "balance"
        fi
    fi

    sleep 1
done
