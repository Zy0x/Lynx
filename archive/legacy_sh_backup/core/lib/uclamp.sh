#!/system/bin/sh
# ==============================================================================
# Lynx Universal - UCLAMP & Schedutil Tuning Library
# Optimizes Energy Aware Scheduling (EAS), Utilization Clamping, and MGLRU.
# ==============================================================================

apply_uclamp_game() {
    local min_ratio=70
    if [ -f "/data/adb/modules/Lynx/config.json" ]; then
        local custom_ratio
        custom_ratio=$(grep -o '"game_min_ratio": [0-9]*' "/data/adb/modules/Lynx/config.json" 2>/dev/null | awk '{print $2}')
        [ -n "$custom_ratio" ] && min_ratio="$custom_ratio"
    fi

    # 1. UCLAMP Min Allocation (Top-App Task Clamping)
    # Different kernels use 0-100 (percentage) or 0-1024 (scheduler scale)
    for uclamp_node in \
        "/dev/cpuset/top-app/cpu.uclamp.min" \
        "/proc/sys/kernel/sched_util_clamp_min"; do
        if [ -e "$uclamp_node" ]; then
            chmod 644 "$uclamp_node" 2>/dev/null
            # Check maximum range scale by reading current value or testing 100 vs 1024
            local max_scale=100
            [ -e "/dev/cpuset/top-app/cpu.uclamp.max" ] && max_scale=$(cat "/dev/cpuset/top-app/cpu.uclamp.max" 2>/dev/null)
            if [ "$max_scale" -gt 100 ] 2>/dev/null; then
                local scaled_val=$(( (min_ratio * 1024) / 100 ))
                echo "$scaled_val" > "$uclamp_node" 2>/dev/null
            else
                echo "$min_ratio" > "$uclamp_node" 2>/dev/null
            fi
        fi
    done

    # 2. Schedutil Ultra-Fast Rate Limits (Sub-millisecond latency)
    for rate_up in /sys/devices/system/cpu/cpufreq/policy*/schedutil/up_rate_limit_us \
                   /sys/devices/system/cpu/cpu*/cpufreq/schedutil/up_rate_limit_us; do
        if [ -e "$rate_up" ]; then
            chmod 644 "$rate_up" 2>/dev/null
            echo "500" > "$rate_up" 2>/dev/null
        fi
    done

    for rate_down in /sys/devices/system/cpu/cpufreq/policy*/schedutil/down_rate_limit_us \
                     /sys/devices/system/cpu/cpu*/cpufreq/schedutil/down_rate_limit_us; do
        if [ -e "$rate_down" ]; then
            chmod 644 "$rate_down" 2>/dev/null
            echo "20000" > "$rate_down" 2>/dev/null
        fi
    done

    # 3. Multi-Gen LRU (MGLRU) Enablement for Stutter-Free Memory Management
    if [ -e "/sys/kernel/mm/lru_gen/enabled" ]; then
        echo "7" > "/sys/kernel/mm/lru_gen/enabled" 2>/dev/null || echo "y" > "/sys/kernel/mm/lru_gen/enabled" 2>/dev/null
    fi
}

apply_uclamp_balance() {
    # 1. Reset UCLAMP Min to 0 / Idle Headroom
    for uclamp_node in \
        "/dev/cpuset/top-app/cpu.uclamp.min" \
        "/proc/sys/kernel/sched_util_clamp_min"; do
        if [ -e "$uclamp_node" ]; then
            chmod 644 "$uclamp_node" 2>/dev/null
            echo "0" > "$uclamp_node" 2>/dev/null
        fi
    done

    # 2. Reset Schedutil to Balanced Latencies
    for rate_up in /sys/devices/system/cpu/cpufreq/policy*/schedutil/up_rate_limit_us \
                   /sys/devices/system/cpu/cpu*/cpufreq/schedutil/up_rate_limit_us; do
        if [ -e "$rate_up" ]; then
            chmod 644 "$rate_up" 2>/dev/null
            echo "1000" > "$rate_up" 2>/dev/null
        fi
    done

    for rate_down in /sys/devices/system/cpu/cpufreq/policy*/schedutil/down_rate_limit_us \
                     /sys/devices/system/cpu/cpu*/cpufreq/schedutil/down_rate_limit_us; do
        if [ -e "$rate_down" ]; then
            chmod 644 "$rate_down" 2>/dev/null
            echo "4000" > "$rate_down" 2>/dev/null
        fi
    done
}
