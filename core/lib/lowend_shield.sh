#!/system/bin/sh
# Lynx Universal - Low-End Survival & Battery Saver Engine
# Pure POSIX /system/bin/sh compatible

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# 1. Detect Physical RAM (KB)
get_mem_total_kb() {
    awk '/MemTotal/ {print $2}' /proc/meminfo 2>/dev/null || echo "4194304"
}

# 2. Detect Primary Storage Type (eMMC vs UFS)
get_storage_type() {
    if [ -d "/sys/block/mmcblk0" ]; then
        echo "emmc"
    elif [ -d "/sys/block/sda" ]; then
        echo "ufs"
    else
        echo "unknown"
    fi
}

# 3. Low-End Survival Package (LMKD, Cpuset Isolation, Read-Ahead)
apply_lowend_shield() {
    local mem_kb
    mem_kb=$(get_mem_total_kb)
    local storage
    storage=$(get_storage_type)

    # A. Storage I/O Read-Ahead Optimization
    if [ "$storage" = "emmc" ]; then
        # 512 KB burst read-ahead for slow eMMC 5.1
        for q in /sys/block/mmcblk*/queue/read_ahead_kb; do
            write_node "512" "$q"
        done
    else
        # 128 KB for high-speed low-latency UFS
        for q in /sys/block/sd*/queue/read_ahead_kb; do
            write_node "128" "$q"
        done
    fi

    # B. Memory & LMKD Tuning for RAM <= 4 GB (<= 4194304 KB)
    if [ "$mem_kb" -le 4194304 ]; then
        # Prevent Launcher Redraws: Protect Launcher & SystemUI
        for p in $(pidof com.android.systemui 2>/dev/null; pgrep -f "launcher" 2>/dev/null; pgrep -f "miui.home" 2>/dev/null); do
            [ -f "/proc/$p/oom_score_adj" ] && write_node "-800" "/proc/$p/oom_score_adj"
        done

        # Tune LMKD Parameters
        write_node "1" "/sys/module/lowmemorykiller/parameters/enable_adaptive_lmk"
        write_node "18432,23040,27648,32256,55296,80640" "/sys/module/lowmemorykiller/parameters/minfree"
        
        # Virtual Memory Compaction
        write_node "80" "/proc/sys/vm/vfs_cache_pressure"
        write_node "15" "/proc/sys/vm/dirty_ratio"
        write_node "5" "/proc/sys/vm/dirty_background_ratio"

        # C. Cpuset Task Isolation (Constrain background tasks to Little Cores)
        if [ -d "/dev/cpuset" ]; then
            # Top-App gets all cores for max FPS
            write_node "0-7" "/dev/cpuset/top-app/cpus"
            # Background & System-Background restricted to Little Cores (0-3)
            write_node "0-3" "/dev/cpuset/background/cpus"
            write_node "0-3" "/dev/cpuset/system-background/cpus"
            write_node "0-3" "/dev/cpuset/restricted/cpus"
        fi
    fi
}

# 4. Battery Saver & Deep Sleep Idle Shield (Daily Balance)
apply_idle_battery_saver() {
    # A. Re-enable Power-Efficient Workqueues
    write_node "Y" "/sys/module/workqueue/parameters/power_efficient"

    # B. CPU Idle Frequency Reset (Allow cores to rest at hardware floor)
    for policy in /sys/devices/system/cpu/cpufreq/policy*; do
        [ -d "$policy" ] || continue
        min_freq=$(cat "$policy/cpuinfo_min_freq" 2>/dev/null)
        [ -n "$min_freq" ] && write_node "$min_freq" "$policy/scaling_min_freq"
    done

    # C. Trim Kernel Wakelocks & WLAN Wakeup
    write_node "1" "/sys/module/wlan/parameters/auto_power_save"
    write_node "1" "/sys/module/bcmdhd/parameters/wlan_power_saving"
}
