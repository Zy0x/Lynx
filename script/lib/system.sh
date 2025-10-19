#!/bin/sh

help_system() {
    log_msg "Displaying help for System optimization..."
    echo "Usage: Lxcore -system [apply|help]"
    echo ""
    echo "Options:"
    echo "  apply     Apply all system-related optimizations."
    echo "  help      Show this help message."
    echo ""
    echo "Description of applied system optimizations:"
    echo "  - Disables unnecessary kernel features to reduce overhead:"
    echo "    - sched_tunable_scaling, sched_child_runs_first, timer_migration set to 0."
    echo "    - Enables autogroup scheduling for better task grouping."
    echo "  - Adjusts real-time scheduling parameters:"
    echo "    - sched_rt_runtime_us set to 950000."
    echo "    - sched_rt_period_us set to 1000000."
    echo "    - sched_migration_cost_ns set to 500000."
    echo "  - Enables expedited RCU (Read-Copy-Update) for faster synchronization."
    echo "  - Disables kernel tracing to reduce logging overhead."
    echo "  - Configures round-robin timeslice to 10 ms."
    echo "  - Enables dynamic ravg window for better CPU load tracking."
    echo "  - Enables dynamic fsync for improved file syncing performance."
    echo "  - Disables HID magic mouse scroll acceleration and emulation."
    echo "  - Disables SPI CRC and MMC core parameters to reduce I/O overhead."
    echo "  - Configures LRU generation TTL to 5000 ms."
    echo "  - Enables low-power mode prediction but disables sleep."
    echo "  - Disables printk and kernel panic messages for cleaner logs."
    echo "  - Configures Uclamp settings for top-app, foreground, background, and system-background tasks."
    echo "  - Reduces scheduler latency to 500000 ns for lower delays."
    echo "  - Sets wakeup granularity to 25000 ns and minimum granularity to 100000 ns."
    echo "  - Reduces power management freeze timeout to 25000 ms."
    echo "  - Lowers GPU bandwidth polling interval to 10 ms."
    echo "  - Enables memory hierarchy and move charge at immigrate for better memory management."
    echo "  - Disables block I/O scheduler slice idle time."
    echo "  - Suppresses RCU CPU stall warnings."
}

main_system() {
    log_msg "Starting System optimization..."

    # Kernel scheduler tweaks
    echo "0" > /proc/sys/kernel/sched_tunable_scaling
    echo "0" > /proc/sys/kernel/sched_child_runs_first
    echo "0" > /proc/sys/kernel/timer_migration
    echo "1" > /proc/sys/kernel/sched_autogroup_enabled
    echo "15" > /proc/sys/kernel/sched_min_task_util_for_boost
    echo "0" > /proc/sys/kernel/sched_min_task_util_for_colocation
    echo "950000" > /proc/sys/kernel/sched_rt_runtime_us
    echo "1000000" > /proc/sys/kernel/sched_rt_period_us
    echo "500000" > /proc/sys/kernel/sched_migration_cost_ns

    # RCU & Tracing
    echo "1" > /sys/kernel/rcu_expedited
    echo "0" > /proc/sys/kernel/tracing/tracing_on
    echo "10" > /proc/sys/kernel/sched_rr_timeslice_ms
    echo "1" > /proc/sys/kernel/sched_dynamic_ravg_window_enable
    echo "4" > /proc/sys/kernel/sched_ravg_window_nr_ticks

    # Fsync
    echo "1" > /sys/kernel/dyn_fsync/Dyn_fsync_active

    # HID tweaks
    echo "Y" > /sys/module/hid_magicmouse/parameters/scroll_acceleration
    echo "N" > /sys/module/hid_magicmouse/parameters/emulate_3button
    echo "N" > /sys/module/hid_magicmouse/parameters/emulate_scroll_wheel
    echo "0" > /sys/module/hid_magicmouse/parameters/scroll_speed

    # Storage tweaks
    echo "0" > /sys/module/mmc_core/parameters/use_spi_crc
    echo "0" > /sys/module/mmc_core/parameters/removable
    echo "0" > /sys/module/mmc_core/parameters/crc

    # Memory management
    echo "5000" > /sys/kernel/mm/lru_gen/min_ttl_ms
    echo "1" > /sys/module/lpm_levels/parameters/lpm_prediction
    echo "0" > /sys/module/lpm_levels/parameters/sleep_disabled

    # Kernel logging & panic
    echo "0 0 0 0" > /proc/sys/kernel/printk
    echo "off" > /proc/sys/kernel/printk_devkmsg
    echo "0" > /proc/sys/kernel/panic
    echo "0" > /proc/sys/kernel/panic_on_oops
    echo "0" > /proc/sys/kernel/panic_on_warn
    echo "0" > /sys/module/kernel/parameters/panic
    echo "0" > /sys/module/kernel/parameters/panic_on_warn
    echo "0" > /sys/module/kernel/parameters/pause_on_oops
    echo "0" > /sys/devices/system/edac/qcom-llcc/panic_on_ue
    echo "0" > /sys/devices/system/edac/qcom-llcc/panic_on_ce
    echo "0" > /d/tracing/tracing_on

    # uClamp settings
    if [ -e /dev/stune/top-app/uclamp.max ]; then
        for ta in /dev/cpuset/*/top-app; do
            echo "max" > "$ta/uclamp.max"
            echo "10" > "$ta/uclamp.min"
            echo "1" > "$ta/uclamp.boosted"
            echo "1" > "$ta/uclamp.latency_sensitive"
        done
        for fd in /dev/cpuset/*/foreground; do
            echo "50" > "$fd/uclamp.max"
            echo "0" > "$fd/uclamp.min"
            echo "0" > "$fd/uclamp.boosted"
            echo "0" > "$fd/uclamp.latency_sensitive"
        done
        for bd in /dev/cpuset/*/background; do
            echo "max" > "$bd/uclamp.max"
            echo "20" > "$bd/uclamp.min"
            echo "0" > "$bd/uclamp.boosted"
            echo "0" > "$bd/uclamp.latency_sensitive"
        done
        for sb in /dev/cpuset/*/system-background; do
            echo "40" > "$sb/uclamp.max"
            echo "0" > "$sb/uclamp.min"
            echo "0" > "$sb/uclamp.boosted"
            echo "0" > "$sb/uclamp.latency_sensitive"
        done
        sysctl -w kernel.sched_util_clamp_min_rt_default=0
        sysctl -w kernel.sched_util_clamp_min=128
    fi

    # Scheduler tuning
    echo "500000" > /proc/sys/kernel/sched_latency_ns
    echo "25000" > /proc/sys/kernel/sched_wakeup_granularity_ns
    echo "100000" > /proc/sys/kernel/sched_min_granularity_ns

    # Power Management
    echo "25000" > /sys/power/pm_freeze_timeout

    # GPU Polling Interval
    echo "10" > /sys/class/devfreq/soc:qcom,gpubw/polling_interval
    echo "10" > /sys/class/devfreq/soc:qcom,l3-cdsp/polling_interval

    # EDAC
    echo "0" > /sys/devices/system/edac/cpu/panic_on_ue

    # Memory Hierarchy
    echo "1" > /dev/memcg/memory.use_hierarchy
    echo "1" > /dev/memcg/apps/memory.move_charge_at_immigrate

    # I/O Scheduler
    echo "0" > /sys/block/sda/queue/iosched/slice_idle

    # RCU Stall Suppression
    echo "1" > /sys/module/rcupdate/parameters/rcu_cpu_stall_suppress
    echo "0" > /sys/module/rcupdate/parameters/rcu_cpu_stall_timeout

    log_msg "System optimization applied successfully."
}