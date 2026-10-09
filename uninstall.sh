#!/system/bin/sh
# Lynx Universal - Complete System Uninstallation & Restoration Script
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)

# 1. Terminate Background Daemons
pkill -f "Smart-AI.sh" 2>/dev/null
pkill -f "Charging-Controller.sh" 2>/dev/null
pkill -f "inotifyd.*state_watcher" 2>/dev/null
pkill -f "httpd.*127.0.0.1:8080" 2>/dev/null

# 2. Unfreeze any OEM Throttler Processes
if [ -f "/dev/lynx_frozen_pids" ]; then
    while IFS= read -r pid; do
        [ -n "$pid" ] && kill -CONT "$pid" 2>/dev/null
    done < "/dev/lynx_frozen_pids"
    rm -f "/dev/lynx_frozen_pids" 2>/dev/null
fi

# 3. Restore Battery Charging Nodes
if [ -e "/sys/class/power_supply/battery/charging_enabled" ]; then
    chmod 644 "/sys/class/power_supply/battery/charging_enabled" 2>/dev/null
    echo "1" > "/sys/class/power_supply/battery/charging_enabled" 2>/dev/null
fi
if [ -e "/sys/class/power_supply/battery/input_suspend" ]; then
    chmod 644 "/sys/class/power_supply/battery/input_suspend" 2>/dev/null
    echo "0" > "/sys/class/power_supply/battery/input_suspend" 2>/dev/null
fi
for bypass in "/sys/devices/platform/charger/bypass_charger" "/sys/class/power_supply/battery/device/smart_charging" "/sys/class/power_supply/battery/smart_charging_activation" "/sys/class/qcom-battery/direct_charging"; do
    [ -e "$bypass" ] && echo "0" > "$bypass" 2>/dev/null
done

# 4. Restore CPU Frequency & Capacity Permissions (Unity Trick Restoration)
for cpu in 0 1 2 3 4 5 6 7; do
    path="/sys/devices/system/cpu/cpu${cpu}"
    [ -e "$path/cpufreq/cpuinfo_max_freq" ] && chmod 444 "$path/cpufreq/cpuinfo_max_freq" 2>/dev/null
    [ -e "$path/cpu_capacity" ] && chmod 444 "$path/cpu_capacity" 2>/dev/null
done

# 5. Remove SWAP file if present
if [ -f "/data/swap" ]; then
    swapoff /data/swap 2>/dev/null
    rm -f /data/swap 2>/dev/null
fi

# 6. Reset Android Framework Services & Settings
cmd thermalservice reset 2>/dev/null
cmd wifi force-low-latency-mode disabled 2>/dev/null

# 6b. Restore Thermal OEM Baseline if present
MODDIR=${0%/*}
[ -d "$MODDIR" ] || MODDIR="/data/adb/modules/Lynx"
if [ -f "$MODDIR/platforms/mtk/thermal.sh" ]; then
    sh "$MODDIR/platforms/mtk/thermal.sh" enable 2>/dev/null
fi
if [ -f "$MODDIR/platforms/qcom/thermal.sh" ]; then
    sh "$MODDIR/platforms/qcom/thermal.sh" enable 2>/dev/null
fi
rm -f "$MODDIR/thermal_boot_mode.json" "$MODDIR/thermal_baseline.json" 2>/dev/null

settings put global window_animation_scale 1.0 2>/dev/null
settings put global transition_animation_scale 1.0 2>/dev/null
settings put global animator_duration_scale 1.0 2>/dev/null

# 7. Restore Core Kernel & VM Tunables to Baseline Snapshot / Stock Defaults
if [ -d "$MODDIR" ] && [ -d "$MODDIR/stock_state" ]; then
    for f in "$MODDIR/stock_state/"*; do
        [ -f "$f" ] || continue
        bname=$(basename "$f")
        val=$(cat "$f" 2>/dev/null)
        [ -z "$val" ] && continue
        case "$bname" in
            dirty_ratio|dirty_background_ratio|vfs_cache_pressure|swappiness)
                [ -e "/proc/sys/vm/$bname" ] && echo "$val" > "/proc/sys/vm/$bname" 2>/dev/null ;;
            sched_util_clamp_min|sched_util_clamp_max)
                [ -e "/proc/sys/kernel/$bname" ] && echo "$val" > "/proc/sys/kernel/$bname" 2>/dev/null ;;
        esac
    done
fi

for node in /proc/sys/vm/dirty_ratio; do [ -e "$node" ] && echo "20" > "$node" 2>/dev/null; done
for node in /proc/sys/vm/dirty_background_ratio; do [ -e "$node" ] && echo "10" > "$node" 2>/dev/null; done
for node in /proc/sys/vm/vfs_cache_pressure; do [ -e "$node" ] && echo "100" > "$node" 2>/dev/null; done
for node in /proc/sys/vm/swappiness; do [ -e "$node" ] && echo "60" > "$node" 2>/dev/null; done
for node in /proc/sys/kernel/sched_util_clamp_min; do [ -e "$node" ] && echo "0" > "$node" 2>/dev/null; done
for node in /proc/sys/kernel/sched_util_clamp_max; do [ -e "$node" ] && echo "1024" > "$node" 2>/dev/null; done

# 8. Clean Runtime Lockfiles, Storage, & Shared Memory
rm -f /dev/lynx_* 2>/dev/null
rm -rf /dev/lynx 2>/dev/null
rm -rf /storage/emulated/0/Lynx 2>/dev/null
rm -f /data/adb/service.d/lynx* 2>/dev/null
rm -f /data/adb/post-fs-data.d/lynx* 2>/dev/null
rm -rf /data/adb/lynx* 2>/dev/null

# 9. Total Uninstallation: Companion App & Notification Utilities
pm uninstall com.noir.lynx 2>/dev/null
pm uninstall com.noir.lynx.debug 2>/dev/null
pm uninstall bellavita.toast 2>/dev/null

# 10. Purge All Residual App Data & Cached Configurations
rm -rf /data/user/0/com.noir.lynx 2>/dev/null
rm -rf /data/user/0/com.noir.lynx.debug 2>/dev/null
rm -rf /data/data/com.noir.lynx 2>/dev/null
rm -rf /data/data/com.noir.lynx.debug 2>/dev/null

