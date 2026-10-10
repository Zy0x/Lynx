#!/system/bin/sh
# Lynx Universal - MediaTek Dimensity & Helio Exclusive Sysfs Tweaks
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)
# Strictly MediaTek-only: CCCI modem logger, GED KPI, MTK Syslimiter, Perf PMU

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

log_mtk() {
    [ -n "$1" ] && echo "[MediaTek-HAL] $1" >> "/storage/emulated/0/Lynx/Lynx.log" 2>/dev/null
}

# ── 1. Disable CCCI Modem Debug Logging (Reduces I/O Overhead) ─────────
write_node "0" "/sys/kernel/ccci/debug"

# ── 2. MediaTek GED KPI & Logging Suppression ─────────────────────────
if [ -d "/sys/module/ged/parameters" ]; then
    write_node "0" "/sys/module/ged/parameters/ged_log_perf_trace_enable"
    write_node "0" "/sys/module/ged/parameters/ged_log_trace_enable"
    write_node "0" "/sys/module/ged/parameters/ged_monitor_3D_fence_debug"
    write_node "0" "/sys/module/ged/parameters/ged_monitor_3D_fence_systrace"
fi
if [ -d "/proc/ged" ]; then
    write_node "0" "/proc/ged/hal/ged_kpi"
fi

# ── 3. MediaTek Perfmgr Syslimiter Force Disable ──────────────────────
write_node "1" "/proc/perfmgr/syslimiter/syslimiter_force_disable"
write_node "0" "/proc/perfmgr/syslimiter/syslimiter_fps_60"
write_node "0" "/proc/perfmgr/syslimiter/syslimiter_fps_90"
write_node "0" "/proc/perfmgr/syslimiter/syslimiter_fps_120"
write_node "0" "/proc/perfmgr/syslimiter/syslimiter_fps_144"

# ── 4. MediaTek CPU Perf PMU & Power Level Settings ───────────────────
for pl in /sys/devices/system/cpu/perf; do
    [ -d "$pl" ] || continue
    write_node "1" "$pl/gpu_pmu_enable"
    write_node "1" "$pl/fuel_gauge_enable"
    write_node "1" "$pl/enable"
    write_node "1" "$pl/charger_enable"
done

log_mtk "MediaTek-exclusive sysfs optimizations initialized."
