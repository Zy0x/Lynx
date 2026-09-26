#!/system/bin/sh
# Lynx Universal - Universal System & Kernel Core Tuning
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)
# Strictly universal: RCU acceleration, telemetry & logger suppression, autogroups

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

help_system() {
    echo "Usage: Lxcore -system [apply|help]"
    echo ""
    echo "Universal System & Kernel Core optimizations:"
    echo "  - Enables RCU expedited synchronization"
    echo "  - Suppresses kernel debugging, tracing, and telemetry overhead"
    echo "  - Tunes real-time scheduler periods and autogroups"
    echo "  - Disables panic on oops and suppresses ramdumps"
}

main_system() {
    log_msg "Starting Universal System & Kernel Core optimization..."

    # 1. Kernel Scheduler Core Tunings
    write_node "0" "/proc/sys/kernel/sched_tunable_scaling"
    write_node "0" "/proc/sys/kernel/sched_child_runs_first"
    write_node "0" "/proc/sys/kernel/timer_migration"
    write_node "1" "/proc/sys/kernel/sched_autogroup_enabled"
    write_node "15" "/proc/sys/kernel/sched_min_task_util_for_boost"
    write_node "0" "/proc/sys/kernel/sched_min_task_util_for_colocation"
    write_node "950000" "/proc/sys/kernel/sched_rt_runtime_us"
    write_node "1000000" "/proc/sys/kernel/sched_rt_period_us"
    write_node "500000" "/proc/sys/kernel/sched_migration_cost_ns"

    # 2. RCU Expedited Synchronization
    write_node "1" "/sys/kernel/rcu_expedited"
    write_node "1" "/sys/module/rcupdate/parameters/rcu_cpu_stall_suppress"
    write_node "0" "/sys/module/rcupdate/parameters/rcu_cpu_stall_timeout"

    # 3. Tracing & Profiling Suppression (Saves CPU cycles & RAM)
    write_node "0" "/proc/sys/kernel/tracing/tracing_on"
    write_node "0" "/sys/kernel/debug/tracing/tracing_on"
    write_node "0" "/sys/kernel/tracing/tracing_on"
    settings put global debug.perfetto.profiler.enabled 0 2>/dev/null
    settings put global watchdog_enabled 0 2>/dev/null

    # 4. Printk & Kernel Panic Overhead Reduction
    write_node "0 0 0 0" "/proc/sys/kernel/printk"
    write_node "0" "/proc/sys/kernel/panic_on_oops"
    write_node "0" "/proc/sys/kernel/panic"

    # 5. Universal Telemetry & Logger Daemon Suppression
    for bl in /sys/module/bluetooth/parameters/disable_ertm /sys/module/bluetooth/parameters/disable_esco; do
        write_node "Y" "$bl"
    done

    stop logcatd tcpdump statsd traced idd-logreader idd-logreadermain \
         dumpstate aplogd vendor_tcpdump vendor.tcpdump 2>/dev/null

    log_msg "System optimization applied successfully."
}