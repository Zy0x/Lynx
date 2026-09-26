#!/system/bin/sh
# Lynx Universal - Targeted Profile: Xiaomi / Poco / Redmi Qualcomm
# Applied ONLY when kernel ground truth matches Xiaomi/Poco/Redmi hardware!
# Pure POSIX /system/bin/sh compliance

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# 1. Xiaomi mi_thermald / thermal_message overrides
THERMAL_MSG="/sys/class/thermal/thermal_message"
if [ -d "$THERMAL_MSG" ]; then
    # Mode 10 = Game Mode / High Performance in Xiaomi MIUI/HyperOS
    write_node "10" "$THERMAL_MSG/sconfig"
    write_node "1" "$THERMAL_MSG/boost"
    write_node "0" "$THERMAL_MSG/balance_mode"
fi

# 2. Touch Screen Sample Rate & Gaming Mode
TOUCH_DEV="/sys/class/touch/touch_dev"
if [ -d "$TOUCH_DEV" ]; then
    write_node "1" "$TOUCH_DEV/clicker_mode"
    write_node "1" "$TOUCH_DEV/bump_sample_rate"
fi
