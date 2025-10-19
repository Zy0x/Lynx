#!/bin/sh

help_gpu() {
    log_msg "Displaying help for GPU optimization..."
    echo "Usage: $0 -gpu [apply|help]"
    echo ""
    echo "Options:"
    echo "  apply     Apply all GPU-related optimizations."
    echo "  help      Show this help message."
    echo ""
    echo "Description of applied GPU optimizations:"
    echo "  - Disables GPU logging to reduce unnecessary overhead."
    echo "    - Sets log_level_cmd, log_level_ctxt, log_level_drv, log_level_mem, log_level_pwr to 0."
    echo "  - Moves specific processes to the system cgroup for better resource allocation:"
    echo "    - Processes include: system_server, surfaceflinger, and various graphics composer services."
    echo "  - Disables GPU snapshot crashdumper to prevent crashes from being logged."
    echo "  - Disables force panic on GPU faults to improve stability."
    echo "  - Reduces fault throttle burst to minimize GPU-related delays."
}

main_gpu() {
    log_msg "Starting GPU optimization..."

    # Disable GPU logging
    local gpu_debug_path="/sys/kernel/debug/kgsl/kgsl-3d0"
    if [ -d "$gpu_debug_path" ]; then
        echo "0" > "$gpu_debug_path/log_level_cmd"
        echo "0" > "$gpu_debug_path/log_level_ctxt"
        echo "0" > "$gpu_debug_path/log_level_drv"
        echo "0" > "$gpu_debug_path/log_level_mem"
        echo "0" > "$gpu_debug_path/log_level_pwr"
    fi

    # List of critical GPU-related processes
    local processes=(
        "system_server"
        "surfaceflinger"
        "android.hardware.graphics.composer@2.0-service"
        "android.hardware.graphics.composer@2.1-service"
        "android.hardware.graphics.composer@2.2-service"
        "android.hardware.graphics.composer@2.3-service"
        "android.hardware.graphics.composer@2.4-service"
        "vendor.qti.hardware.display.composer-service"
    )

    # Move each process to system memory cgroup
    for process_name in "${processes[@]}"; do
        local pid_proc=$(pidof "$process_name")
        if [ -n "$pid_proc" ]; then
            echo "$pid_proc" > /dev/memcg/system/cgroup.procs
        fi
    done

    # Disable GPU crash dump features
    echo "0" > /sys/class/kgsl/kgsl-3d0/snapshot/snapshot_crashdumper
    echo "0" > /sys/class/kgsl/kgsl-3d0/snapshot/force_panic
    echo "0" > /sys/class/kgsl/kgsl-3d0/dispatch/fault_throttle_burst

    log_msg "GPU optimization applied successfully."
}