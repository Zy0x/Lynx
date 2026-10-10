#!/system/bin/sh
# Lynx Universal - Qualcomm Snapdragon Exclusive Sysfs Tweaks
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)
# Strictly Qualcomm-only: KGSL debugging suppression, CPU Boost parameters, WALT, Ramdumps

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

log_qcom() {
    [ -n "$1" ] && echo "[Qualcomm-HAL] $1" >> "/storage/emulated/0/Lynx/Lynx.log" 2>/dev/null
}

# ── 1. Adreno KGSL Diagnostic & Snapshot Overhead Suppression ─────────
KGSL="/sys/class/kgsl/kgsl-3d0"
if [ -d "$KGSL" ]; then
    # Disable crash dumps and panics on GPU faults (prevents sudden reboots)
    write_node "0" "$KGSL/snapshot/snapshot_crashdumper"
    write_node "0" "$KGSL/snapshot/force_panic"
    write_node "0" "$KGSL/dispatch/fault_throttle_burst"
    
    # Disable KGSL debug logging in debugfs
    for dbg in /sys/kernel/debug/kgsl/kgsl-3d0 /d/kgsl/kgsl-3d0; do
        if [ -d "$dbg" ]; then
            write_node "0" "$dbg/log_level_cmd"
            write_node "0" "$dbg/log_level_ctxt"
            write_node "0" "$dbg/log_level_drv"
            write_node "0" "$dbg/log_level_mem"
            write_node "0" "$dbg/log_level_pwr"
        fi
    done
fi

# ── 2. Qualcomm CPU-Boost & Core Control Jitter Suppression ───────────
if [ -d "/sys/module/cpu_boost/parameters" ]; then
    write_node "0" "/sys/module/cpu_boost/parameters/sched_boost_on_input"
    write_node "0" "/sys/module/cpu_boost/parameters/input_boost_ms"
    for i in 0 1 2 3 4 5 6 7; do
        write_node "${i}:0" "/sys/module/cpu_boost/parameters/input_boost_freq"
    done
fi

# ── 3. Qualcomm WALT (Window Assisted Load Tracking) Fast Ramp ────────
write_node "1" "/proc/sys/kernel/sched_walt_io_is_busy"
write_node "1" "/proc/sys/kernel/sched_walt_cross_window_migration"

# ── 4. Qualcomm DRM Display Core Performance Debugfs ──────────────────
if [ -e "/d/dri/0/debug/core_perf/perf_mode" ]; then
    write_node "1" "/d/dri/0/debug/core_perf/perf_mode"
fi

# ── 5. Qualcomm Subsystem Restart Ramdumps Suppression ─────────────────
if [ -d "/sys/module/subsystem_restart/parameters" ]; then
    write_node "0" "/sys/module/subsystem_restart/parameters/enable_ramdumps"
    write_node "0" "/sys/module/subsystem_restart/parameters/enable_mini_ramdumps"
fi

# ── 6. Qualcomm WLAN CNSS Diagnostic Logging Suppression ───────────────
stop cnss_diag vendor.cnss_diag 2>/dev/null
if [ -d "/data/vendor/wlan_logs" ] || [ ! -e "/data/vendor/wlan_logs" ]; then
    rm -rf /data/vendor/wlan_logs 2>/dev/null
    touch /data/vendor/wlan_logs 2>/dev/null
    chmod 000 /data/vendor/wlan_logs 2>/dev/null
fi

log_qcom "Qualcomm-exclusive sysfs optimizations initialized."
