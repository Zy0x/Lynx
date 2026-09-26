#!/system/bin/sh
# ==============================================================================
# Lynx Universal - Display & Touch Latency Optimization Library
# Eliminates input delay, stabilizes SurfaceFlinger Vsync, and holds 90Hz/120Hz+.
# ==============================================================================

apply_display_touch_game() {
    # 1. SurfaceFlinger Low-Latency Phase Offsets
    setprop debug.sf.early_phase_offset_ns 500000 2>/dev/null
    setprop debug.sf.early_app_phase_offset_ns 500000 2>/dev/null
    setprop debug.sf.high_fps_late_app_phase_offset_ns 1000000 2>/dev/null
    setprop debug.sf.high_fps_early_phase_offset_ns 1000000 2>/dev/null

    # 2. Display Refresh Rate Holding (Prevent 120Hz/90Hz drop to 60Hz during touch pauses)
    local peak_rr
    peak_rr=$(settings get system peak_refresh_rate 2>/dev/null)
    if [ -n "$peak_rr" ] && [ "$peak_rr" != "null" ]; then
        # Back up original min refresh rate once
        if [ ! -f "/dev/lynx_orig_min_rr" ]; then
            local orig_min
            orig_min=$(settings get system min_refresh_rate 2>/dev/null)
            echo "${orig_min:-60.0}" > "/dev/lynx_orig_min_rr"
        fi
        settings put system min_refresh_rate "$peak_rr" 2>/dev/null
    fi

    # 3. Touch Sampling Rate & Game Mode Touch Nodes
    for touch_node in \
        /sys/class/touch/touch_dev/touch_game_mode \
        /sys/devices/virtual/touch/touch_dev/bump_sample_rate \
        /proc/touchscreen/game_mode \
        /sys/devices/platform/goodix_ts.*/game_mode \
        /sys/devices/platform/tp_wake_switch/game_mode \
        /sys/devices/virtual/input/input*/touch_game_mode; do
        if [ -e "$touch_node" ]; then
            chmod 644 "$touch_node" 2>/dev/null
            echo "1" > "$touch_node" 2>/dev/null
        fi
    done

    # 4. Safe Opt-In Skia Vulkan Rendering Check
    if [ -f "/data/adb/modules/Lynx/config.json" ]; then
        if grep -q '"skia_vulkan": true' "/data/adb/modules/Lynx/config.json" 2>/dev/null; then
            local sdk_ver
            sdk_ver=$(getprop ro.build.version.sdk 2>/dev/null)
            if [ "${sdk_ver:-0}" -ge 29 ] && [ -e "/dev/mali0" -o -e "/dev/kgsl-3d0" ]; then
                setprop debug.hwui.renderer skiavk 2>/dev/null
            fi
        fi
    fi
}

apply_display_touch_balance() {
    # 1. Restore Default SurfaceFlinger Offsets
    setprop debug.sf.early_phase_offset_ns "" 2>/dev/null
    setprop debug.sf.early_app_phase_offset_ns "" 2>/dev/null

    # 2. Restore Min Refresh Rate
    if [ -f "/dev/lynx_orig_min_rr" ]; then
        local orig_min
        orig_min=$(cat "/dev/lynx_orig_min_rr" 2>/dev/null)
        [ -n "$orig_min" ] && settings put system min_refresh_rate "$orig_min" 2>/dev/null
        rm -f "/dev/lynx_orig_min_rr" 2>/dev/null
    fi

    # 3. Reset Touch Boost Nodes
    for touch_node in \
        /sys/class/touch/touch_dev/touch_game_mode \
        /sys/devices/virtual/touch/touch_dev/bump_sample_rate \
        /proc/touchscreen/game_mode \
        /sys/devices/platform/goodix_ts.*/game_mode \
        /sys/devices/platform/tp_wake_switch/game_mode \
        /sys/devices/virtual/input/input*/touch_game_mode; do
        if [ -e "$touch_node" ]; then
            chmod 644 "$touch_node" 2>/dev/null
            echo "0" > "$touch_node" 2>/dev/null
        fi
    done
}
