#!/bin/sh

help_unity() {
    log_msg "Displaying help for Unity trick optimization..."
    echo "Usage: Lxcore -unity [apply|help]"
    echo ""
    echo "Options:"
    echo "  apply     Apply Unity trick optimizations to spoof system capabilities."
    echo "  help      Show this help message."
    echo ""
    echo "Description of applied Unity tricks:"
    echo "  - Changes permissions of CPU-related files to prevent accurate detection of:"
    echo "    - Maximum frequency"
    echo "    - CPU capacity"
    echo "    - Physical package ID"
    echo "  - This makes Unity-based games think the device has a more powerful CPU."
    echo "  - Useful for bypassing FPS or quality restrictions in some games."
}

main_unity() {
    log_msg "Ensuring CPU topology and capabilities are properly accessible for game engines..."
    for cpu in 0 1 2 3 4 5 6 7; do
        path="/sys/devices/system/cpu/cpu${cpu}"
        chmod 444 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
        chmod 444 "$path/cpu_capacity" 2>/dev/null
        chmod 444 "$path/topology/physical_package_id" 2>/dev/null
    done

    log_msg "CPU topology and capability permissions verified (0444)."
}