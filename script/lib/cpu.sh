#!/bin/sh

help_cpu() {
    log_msg "Displaying help for CPU optimization..."
    echo "Usage: $0 -cpu [apply|help]"
    echo ""
    echo "Options:"
    echo "  apply     Apply all CPU-related optimizations."
    echo "  help      Show this help message."
    echo ""
    echo "Description of applied CPU optimizations:"
    echo "  - Enables power-efficient workqueues to improve energy efficiency."
    echo "  - Limits maximum CPU performance time to 40% of total capacity."
    echo "  - Configures core control settings for each CPU core (0-7):"
    echo "    - Disables 'not_prefered' flags for all cores."
    echo "  - Disables CPU input boost to reduce unnecessary power usage:"
    echo "    - sched_boost_on_input set to 0."
    echo "    - input_boost_ms set to 0."
    echo "    - input_boost_freq set to 0 for all CPUs (0-7)."
    echo "  - Enables core performance mode for better responsiveness."
    echo "  - Configures core_ctl for specific CPUs (0, 4, 6):"
    echo "    - Disables core_ctl."
    echo "    - Sets minimum and maximum active CPUs to 4."
    echo "    - Configures offline delay to 500 ms."
    echo "  - Adjusts cpuset memory and CPU exclusivity settings:"
    echo "    - Disables memory exclusivity for top-app and foreground tasks."
    echo "    - Disables memory spread for slab and page allocations."
    echo "    - Disables CPU exclusivity for cpusets."
}

main_cpu() {
    log_msg "Starting CPU optimization..."

    # Enable power-efficient workqueues
    echo "Y" > /sys/module/workqueue/parameters/power_efficient

    # Limit maximum CPU performance time
    echo "40" > /proc/sys/kernel/perf_cpu_time_max_percent

    # Disable not_preferred flag for all cores (0-7)
    for cpu in 0 1 2 3 4 5 6 7; do
        echo "1 1 1 1" > "/sys/devices/system/cpu/cpu${cpu}/core_ctl/not_prefered"
    done

    # Disable CPU input boost
    echo "0" > /sys/module/cpu_boost/parameters/sched_boost_on_input
    echo "0" > /sys/module/cpu_boost/parameters/input_boost_ms
    for i in {0..7}; do
        echo "${i}:0" > /sys/module/cpu_boost/parameters/input_boost_freq
    done

    # Enable core performance mode
    if [ -e "/d/dri/0/debug/core_perf/perf_mode" ]; then
        echo "1" > /d/dri/0/debug/core_perf/perf_mode
    fi

    # Configure core_ctl for CPUs 0, 4, 6
    for ctl in /sys/bus/cpu/devices/cpu{0,4,6}; do
        if [ -d "$ctl/core_ctl" ]; then
            echo "0" > $ctl/core_ctl/enable
            echo "4" > $ctl/core_ctl/min_cpus
            echo "4" > $ctl/core_ctl/max_cpus
            echo "500" > $ctl/core_ctl/offline_delay_ms
        fi
    done

    # Adjust cpuset settings
    echo "0" > /dev/cpuset/top-app/mem_exclusive
    echo "0" > /dev/cpuset/foreground/cpu_exclusive
    echo "0" > /dev/cpuset/top-app/memory_spread_slab
    echo "0" > /dev/cpuset/foreground/memory_spread_slab
    echo "0" > /dev/cpuset/top-app/memory_spread_page
    echo "0" > /dev/cpuset/foreground/memory_spread_page
    echo "0" > /dev/cpuset/cpu_exclusive

    log_msg "CPU optimization applied successfully."
}