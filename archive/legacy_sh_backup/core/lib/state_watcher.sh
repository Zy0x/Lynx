#!/system/bin/sh
# Lynx Universal - Real-Time State Watcher (2-Way Sync Engine)
# Pure POSIX /system/bin/sh compatible

MODPATH="/data/adb/modules/Lynx"
[ -d "$MODPATH" ] || MODPATH="${0%/*/*/*}"
CONFIG_FILE="$MODPATH/config.json"
CORE="$MODPATH/core"
TARGET_SOC_FILE="$MODPATH/target_soc"
LOCK_FILE="/dev/lynx_mode.lock"

TARGET_SOC="generic"
[ -f "$TARGET_SOC_FILE" ] && TARGET_SOC=$(cat "$TARGET_SOC_FILE" 2>/dev/null | tr -d '[:space:]')
if [ -z "$TARGET_SOC" ] || [ "$TARGET_SOC" = "generic" ]; then
    if [ -f "$MODPATH/core/lib/hw_probe.sh" ]; then
        . "$MODPATH/core/lib/hw_probe.sh"
        hw_soc=$(detect_real_soc)
        [ "$hw_soc" != "generic" ] && TARGET_SOC="$hw_soc"
    fi
fi
[ -z "$TARGET_SOC" ] && TARGET_SOC="generic"

PLATFORM_DIR="$MODPATH/platforms/$TARGET_SOC"
[ -d "$PLATFORM_DIR" ] || PLATFORM_DIR="$MODPATH/platforms/generic"

# Read active profile from config.json
active_profile=$(awk -F'"' '/"active_profile"[ \t]*:/ {print $4}' "$CONFIG_FILE" 2>/dev/null)
[ -z "$active_profile" ] && exit 0

# Concurrency lock
if [ -f "$LOCK_FILE" ]; then
    # Another profile switch is currently executing
    exit 0
fi
touch "$LOCK_FILE"

# Start Charging Controller if transitioning out of dormant
if [ "$active_profile" != "dormant" ]; then
    if ! pgrep -f "Charging-Controller.sh" >/dev/null 2>&1; then
        [ -f "$CORE/Charging-Controller.sh" ] && nohup sh "$CORE/Charging-Controller.sh" >/dev/null 2>&1 &
    fi
fi


case "$active_profile" in
    dormant)
        sed -Ei "s/^description=\[.*\]/description=[ Dormant (Pending Setup) ]/" "$MODPATH/module.prop" 2>/dev/null
        pkill -f "Smart-AI.sh" 2>/dev/null
        ;;
    auto)
        sed -Ei "s/^description=\[.*\]/description=[ Auto (AI) Mode Active ]/" "$MODPATH/module.prop" 2>/dev/null
        pkill -f "Smart-AI.sh" 2>/dev/null
        nohup sh "$CORE/Smart-AI.sh" >/dev/null 2>&1 &
        am start -a android.intent.action.MAIN -e toasttext "Lynx: Auto (AI) Mode" -n bellavita.toast/.MainActivity >/dev/null 2>&1
        ;;
    powersave)
        sed -Ei "s/^description=\[.*\]/description=[ Powersave Mode Active ]/" "$MODPATH/module.prop" 2>/dev/null
        pkill -f "Smart-AI.sh" 2>/dev/null
        if [ -f "$CORE/apply_profile.sh" ]; then
            sh "$CORE/apply_profile.sh" powersave >/dev/null 2>&1
        elif [ -f "$PLATFORM_DIR/powersave.sh" ]; then
            sh "$PLATFORM_DIR/powersave.sh" >/dev/null 2>&1
        fi
        am start -a android.intent.action.MAIN -e toasttext "Lynx: Powersave Mode" -n bellavita.toast/.MainActivity >/dev/null 2>&1
        ;;
    balance)
        sed -Ei "s/^description=\[.*\]/description=[ Balance Mode Active ]/" "$MODPATH/module.prop" 2>/dev/null
        pkill -f "Smart-AI.sh" 2>/dev/null
        if [ -f "$CORE/apply_profile.sh" ]; then
            sh "$CORE/apply_profile.sh" balance >/dev/null 2>&1
        elif [ -f "$PLATFORM_DIR/balance.sh" ]; then
            sh "$PLATFORM_DIR/balance.sh" >/dev/null 2>&1
        fi
        am start -a android.intent.action.MAIN -e toasttext "Lynx: Balance Mode" -n bellavita.toast/.MainActivity >/dev/null 2>&1
        ;;
    performance)
        sed -Ei "s/^description=\[.*\]/description=[ Performance Mode Active ]/" "$MODPATH/module.prop" 2>/dev/null
        pkill -f "Smart-AI.sh" 2>/dev/null
        if [ -f "$CORE/apply_profile.sh" ]; then
            sh "$CORE/apply_profile.sh" performance >/dev/null 2>&1
        elif [ -f "$PLATFORM_DIR/perf.sh" ]; then
            sh "$PLATFORM_DIR/perf.sh" "perf" >/dev/null 2>&1
        fi
        am start -a android.intent.action.MAIN -e toasttext "Lynx: Performance Mode" -n bellavita.toast/.MainActivity >/dev/null 2>&1
        ;;
    extreme)
        sed -Ei "s/^description=\[.*\]/description=[ Extreme Mode Active ]/" "$MODPATH/module.prop" 2>/dev/null
        pkill -f "Smart-AI.sh" 2>/dev/null
        if [ -f "$CORE/apply_profile.sh" ]; then
            sh "$CORE/apply_profile.sh" extreme >/dev/null 2>&1
        elif [ -f "$PLATFORM_DIR/perf.sh" ]; then
            sh "$PLATFORM_DIR/perf.sh" "extreme" >/dev/null 2>&1
        fi
        am start -a android.intent.action.MAIN -e toasttext "Lynx: Extreme Mode (Cooler Recommended)" -n bellavita.toast/.MainActivity >/dev/null 2>&1
        ;;
esac

rm -f "$LOCK_FILE"
