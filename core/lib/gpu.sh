#!/system/bin/sh
# Lynx Universal - Universal GPU & Compositor Subsystem Optimizer
# Hardened POSIX compliance for Android /system/bin/sh (Toybox/mksh)
# Strictly universal graphics prioritizations (SoC-specifics delegated to platforms/ HAL)

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

help_gpu() {
    echo "Usage: Lxcore -gpu [apply|help]"
    echo ""
    echo "Universal GPU optimizations:"
    echo "  - Elevates SurfaceFlinger & HW Composer services to high-priority cgroups"
    echo "  - Reduces frame latency and eliminates UI micro-stutters"
}

main_gpu() {
    log_msg "Starting Universal GPU & Compositor optimization..."

    # 1. Elevate Critical Graphics Processes (Pure POSIX loop - No Bashisms)
    for proc in \
        "surfaceflinger" \
        "system_server" \
        "android.hardware.graphics.composer@2.0-service" \
        "android.hardware.graphics.composer@2.1-service" \
        "android.hardware.graphics.composer@2.2-service" \
        "android.hardware.graphics.composer@2.3-service" \
        "android.hardware.graphics.composer@2.4-service" \
        "android.hardware.graphics.composer@3.0-service" \
        "android.hardware.graphics.composer@3.1-service" \
        "android.hardware.composer.hwc3-service" \
        "vendor.qti.hardware.display.composer-service" \
        "vendor.mediatek.hardware.pq@2.0-service" \
        "vendor.mediatek.hardware.mms@1.0-service"
    do
        for pid in $(pidof "$proc" 2>/dev/null); do
            [ -n "$pid" ] && write_node "$pid" "/dev/memcg/system/cgroup.procs"
            [ -n "$pid" ] && write_node "$pid" "/dev/cpuset/top-app/cgroup.procs"
        done
    done

    # 2. Wildcard pgrep for any other composer variants
    for pid in $(pgrep -f "android.hardware.graphics.composer" 2>/dev/null); do
        write_node "$pid" "/dev/cpuset/top-app/cgroup.procs"
    done

    log_msg "GPU & Compositor optimization applied successfully."
}