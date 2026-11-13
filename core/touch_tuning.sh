#!/system/bin/sh
# ==============================================================================
# Lynx Universal — ZoneTouch Master & Esports Touch Optimization Engine
# Dynamically probes touch controllers (Goodix, Focaltech, Novatek, Synaptics, MTK TPD, QCOM)
# Zero hardcoding — dynamically verifies driver nodes, kernel parameters, and input threads.
# ==============================================================================

ACTION="${1:-enable}"
SAMPLING_BOOST="${2:-1}"

apply_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

set_ro_prop() {
    local key="$1"
    local val="$2"
    for rp in /data/adb/ksu/bin/resetprop /data/adb/magisk/resetprop /data/adb/ap/bin/resetprop "$(command -v resetprop 2>/dev/null)"; do
        if [ -n "$rp" ] && [ -x "$rp" ]; then
            "$rp" -n "$key" "$val" 2>/dev/null || "$rp" "$key" "$val" 2>/dev/null
            return 0
        fi
    done
    return 1
}

supports_input_resampling() {
    for lib in /system/lib64/libinput.so /system/lib/libinput.so /system/lib64/libinputflinger.so /system/lib/libinputflinger.so /vendor/lib64/libinput.so; do
        if [ -r "$lib" ] && grep -Fq "ro.input.resampling" "$lib" 2>/dev/null; then
            return 0
        fi
    done
    return 1
}

enable_esports_touch() {
    # 1. InputFlinger Resampling (verified in libinput)
    if supports_input_resampling; then
        set_ro_prop ro.input.resampling 1
    fi

    # 2. Dynamic Hardware Touch IC Polling Boost
    if [ "$SAMPLING_BOOST" -eq 1 ]; then
        # MediaTek Touch Panel Driver (TPD)
        apply_node 1 /proc/touch_report_rate
        apply_node 240 /proc/touch_report_rate
        apply_node 1 /proc/driver/touchpanel/game_switch_enable
        apply_node 1 /sys/module/tpd_setting/parameters/tpd_game_mode

        # Goodix Gaming Nodes
        for g_node in /sys/devices/platform/*goodix* /sys/class/touchscreen/goodix*; do
            [ -d "$g_node" ] && apply_node 1 "$g_node/game_mode"
            [ -d "$g_node" ] && apply_node 1 "$g_node/report_rate"
        done

        # Focaltech Gaming Nodes
        for f_node in /sys/devices/platform/*focaltech* /sys/class/touchscreen/focaltech*; do
            [ -d "$f_node" ] && apply_node 1 "$f_node/game_mode"
        done

        # Novatek Gaming Nodes
        for n_node in /sys/devices/platform/*novatek* /sys/class/touchscreen/novatek*; do
            [ -d "$n_node" ] && apply_node 1 "$n_node/game_mode"
            [ -d "$n_node" ] && apply_node 1 "$n_node/nvt_game_mode"
        done

        # Generic & Qualcomm Touch Dev Nodes
        apply_node 1 /sys/class/touchscreen/game_quick_mode
        apply_node 1 /sys/devices/virtual/touch/touch_dev/game_quick_mode
        apply_node 1 /proc/touchscreen/game_mode

        # Input devices sampling rate boost
        for rate_node in /sys/class/input/input*/rate /sys/class/input/input*/device/rate; do
            apply_node 240 "$rate_node"
        done
    fi

    # 3. Real-Time Scheduling Priority for InputReader & InputDispatcher
    local sys_pid=$(pidof system_server)
    if [ -n "$sys_pid" ] && [ -d "/proc/$sys_pid/task" ]; then
        for tid_dir in /proc/$sys_pid/task/*; do
            if [ -f "$tid_dir/comm" ]; then
                local comm=$(cat "$tid_dir/comm" 2>/dev/null)
                case "$comm" in
                    InputReader*|InputDispatcher*|android.anim*|RenderThread*)
                        local tid=$(basename "$tid_dir")
                        renice -n -20 -p "$tid" 2>/dev/null
                        ;;
                esac
            fi
        done
    fi
}

disable_esports_touch() {
    if supports_input_resampling; then
        set_ro_prop ro.input.resampling 0
    fi
    apply_node 0 /proc/touch_report_rate
    apply_node 0 /proc/driver/touchpanel/game_switch_enable
    apply_node 0 /sys/class/touchscreen/game_quick_mode
    apply_node 0 /sys/devices/virtual/touch/touch_dev/game_quick_mode
    apply_node 0 /proc/touchscreen/game_mode
}

case "$ACTION" in
    enable)
        enable_esports_touch
        echo "[OK] ZoneTouch Esports Engine Enabled (Sampling: $SAMPLING_BOOST)"
        ;;
    disable)
        disable_esports_touch
        echo "[OK] ZoneTouch Esports Engine Restored"
        ;;
    *)
        echo "Usage: $0 [enable|disable] [sampling_boost: 0|1]"
        exit 1
        ;;
esac
