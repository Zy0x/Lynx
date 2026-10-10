#!/system/bin/sh
# Lynx Universal - Universal Process & Task Scheduling Optimizer
# Hardened POSIX compliance for Android /system/bin/sh (Toybox/mksh)

help_task() {
    echo "Usage: Lxcore -task [apply|help]"
    echo ""
    echo "Universal Task optimizations:"
    echo "  - Migrates critical UI & Compositor threads to top-app cpuset"
    echo "  - Safely falls back to SchedTune only on legacy kernels (pre-5.4)"
    echo "  - Renices compositor, surfaceflinger, and allocators to priority -20"
}

set_proc_cgroup() {
    local target_proc="$1"
    for pid in $(pidof "$target_proc" 2>/dev/null); do
        # 1. Cpuset top-app (modern Linux & Android)
        if [ -f "/dev/cpuset/top-app/cgroup.procs" ]; then
            echo "$pid" > /dev/cpuset/top-app/cgroup.procs 2>/dev/null
        elif [ -f "/dev/cpuset/top-app/tasks" ]; then
            echo "$pid" > /dev/cpuset/top-app/tasks 2>/dev/null
        fi

        # 2. Legacy SchedTune (kernel 4.x / 3.x only)
        if [ -f "/dev/stune/top-app/tasks" ]; then
            echo "$pid" > /dev/stune/top-app/tasks 2>/dev/null
        fi
    done
}

main_task() {
    log_msg "Starting Universal Task & Process scheduling optimization..."

    # 1. Critical System & UI Processes to top-app
    for p in \
        "surfaceflinger" \
        "system_server" \
        "servicemanager" \
        "android.phone" \
        "vendor.qti.hardware.display.composer-service" \
        "vendor.mediatek.hardware.pq@2.0-service" \
        "vendor.mediatek.hardware.mms@1.0-service" \
        "android.hardware.graphics.allocator@4.0-service-mediatek"
    do
        set_proc_cgroup "$p"
    done

    # 2. Wildcard match for hardware composer versions (2.0 to 3.x)
    for p in $(pgrep -f "android.hardware.graphics.composer" 2>/dev/null); do
        [ -f "/dev/cpuset/top-app/cgroup.procs" ] && echo "$p" > /dev/cpuset/top-app/cgroup.procs 2>/dev/null
    done

    # 3. High-Priority Renice (-20 for UI renderers, -5 for core daemons)
    for p in \
        "surfaceflinger" \
        "vendor.qti.hardware.display.composer-service" \
        "vendor.mediatek.hardware.pq@2.0-service" \
        "android.hardware.graphics.allocator@4.0-service-mediatek"
    do
        for pid in $(pidof "$p" 2>/dev/null); do
            renice -n -20 -p "$pid" 2>/dev/null
        done
    done

    for pid in $(pgrep -f "android.hardware.graphics.composer" 2>/dev/null); do
        renice -n -20 -p "$pid" 2>/dev/null
    done

    for p in "zygote64" "zygote" "webview_zygote" "ueventd"; do
        for pid in $(pidof "$p" 2>/dev/null); do
            renice -n -5 -p "$pid" 2>/dev/null
        done
    done

    log_msg "Universal Task & Process scheduling optimization applied."
}