#!/system/bin/sh
# ==============================================================================
# Lynx Universal - Low-Latency Audio Pipeline
# Accelerates footstep/gunshot response via AAudio MMAP while preventing BT crackling.
# ==============================================================================

is_bluetooth_audio_active() {
    # Check if Bluetooth A2DP audio device is connected and playing
    if dumpsys audio 2>/dev/null | grep -iE "a2dp.*connected|device.*bluetooth_a2dp" | grep -qv "state=0"; then
        return 0
    fi
    return 1
}

apply_audio_latency_game() {
    # 1. Fast-track mixer acceleration
    setprop af.fast_track_multiplier 1 2>/dev/null
    setprop aaudio.hw_burst_min_usec "" 2>/dev/null

    # 2. Check Bluetooth Audio Endpoint
    if is_bluetooth_audio_active; then
        # Bluetooth active: MMAP Auto with safe ALSA burst
        setprop aaudio.mmap_policy 2 2>/dev/null
    else
        # Built-in speaker / 3.5mm Jack / Low-latency USB-C: Ultra-low latency MMAP Exclusive
        setprop aaudio.mmap_policy 2 2>/dev/null
        setprop aaudio.mmap_exclusive_policy 2 2>/dev/null
    fi
}

apply_audio_latency_balance() {
    setprop aaudio.hw_burst_min_usec "" 2>/dev/null
    setprop aaudio.mmap_policy 1 2>/dev/null
    setprop aaudio.mmap_exclusive_policy 1 2>/dev/null
    setprop af.fast_track_multiplier 2 2>/dev/null
}

