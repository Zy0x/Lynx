#!/system/bin/sh
# Lynx Universal - Universal CPU Subsystem Optimizer
# Hardened POSIX compliance for Android /system/bin/sh (Toybox/ash)
# Strictly universal CPU tunables (SoC-specifics delegated to platforms/ HAL)

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

help_cpu() {
    echo "Usage: Lxcore -cpu [apply|help]"
    echo ""
    echo "Universal CPU optimizations:"
    echo "  - Schedutil rate limits (up: 500us, down: 20000us) across all CPU policies"
    echo "  - Power-efficient workqueue management"
    echo "  - Cpuset exclusive allocation tunings for top-app and foreground"
    echo "  - Kernel scheduler feature tuning (NO_GENTLE_FAIR_SLEEPERS, START_DEBIT)"
}

main_cpu() {
    log_msg "Starting Universal CPU optimization..."

    # 1. Power-efficient workqueues
    write_node "Y" "/sys/module/workqueue/parameters/power_efficient"

    # 2. Limit maximum CPU performance sampling overhead
    write_node "40" "/proc/sys/kernel/perf_cpu_time_max_percent"

    # 3. Universal Schedutil Rate Limits across all detected cpufreq policies
    for p in /sys/devices/system/cpu/cpufreq/policy*; do
        [ -d "$p" ] || continue
        for s_dir in "$p/schedutil" "$p/scaling_governor"; do
            if [ -d "$s_dir" ]; then
                write_node "500" "$s_dir/up_rate_limit_us"
                write_node "20000" "$s_dir/down_rate_limit_us"
                write_node "1" "$s_dir/iowait_boost_enable"
            fi
        done
    done

    # 4. Universal Cpuset allocations (POSIX loop)
    write_node "0" "/dev/cpuset/top-app/mem_exclusive"
    write_node "0" "/dev/cpuset/foreground/cpu_exclusive"
    write_node "0" "/dev/cpuset/top-app/memory_spread_slab"
    write_node "0" "/dev/cpuset/foreground/memory_spread_slab"
    write_node "0" "/dev/cpuset/top-app/memory_spread_page"
    write_node "0" "/dev/cpuset/foreground/memory_spread_page"
    write_node "0" "/dev/cpuset/cpu_exclusive"

    # 5. Core Control Jitter Prevention (Universal node loop)
    for ctl in /sys/devices/system/cpu/cpu*/core_ctl; do
        if [ -d "$ctl" ]; then
            write_node "500" "$ctl/offline_delay_ms"
        fi
    done

    # 6. Apply Kernel Scheduler Features & Game Libraries
    local sched_lib="$MODPATH/core/lib/sched_features.sh"
    [ -f "$sched_lib" ] && . "$sched_lib" && apply_sched_features

    log_msg "CPU optimization applied successfully."
}