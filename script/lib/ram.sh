# RAM Management
main_ram() {
    log_msg "Starting RAM optimization..."
    for rec in /sys/module/process_reclaim/parameters/; do
        echo "0" > $rec/enable_process_reclaim
        echo "70" > $rec/pressure_max
        echo "50" > $rec/pressure_min
        echo "512" > $rec/per_swap_size
    done
    echo '10' > /dev/memcg/memory.swappiness
    echo '5' > /dev/memcg/system/memory.swappiness
    echo '20' > /dev/memcg/apps/memory.swappiness
    for clear in $(cat /dev/memcg/system/cgroup.procs); do
        echo $clear > /dev/memcg/cgroup.procs
    done
    echo "10" > /proc/sys/vm/dirty_background_ratio
    echo "20" > /proc/sys/vm/dirty_ratio
    echo "500" > /proc/sys/vm/dirty_expire_centisecs
    echo "200" > /proc/sys/vm/dirty_writeback_centisecs
    echo "200" > /proc/sys/vm/extfrag_threshold
    echo "80" > /proc/sys/vm/swappiness
    echo "3" > /proc/sys/vm/page-cluster
    echo "0" > /proc/sys/vm/oom_kill_allocating_task
    echo "8192" > /proc/sys/vm/min_free_kbytes
    echo "0" > /proc/sys/kernel/sched_schedstats
    echo "29615" > /proc/sys/vm/extra_free_kbytes
    echo "8" > /sys/block/zram0/max_comp_streams
    echo "0" > /proc/sys/vm/oom_dump_tasks
    echo "80" > /proc/sys/vm/overcommit_ratio
    echo "0" > /sys/module/vmpressure/parameters/allocstall_threshold
    echo "100" > /sys/module/vmpressure/parameters/vmpressure_scale_max
    echo "1" > /sys/module/lowmemorykiller/parameters/enable_lmk
    echo "0" > /sys/module/lowmemorykiller/parameters/enable_adaptive_lmk
    chmod 666 /sys/module/lowmemorykiller/parameters/minfree
    chown root /sys/module/lowmemorykiller/parameters/minfree
    echo "14535,29070,43605,58112,72675,87210" > /sys/module/lowmemorykiller/parameters/minfree
    chmod 444 /sys/module/lowmemorykiller/parameters/minfree
    log_msg "RAM optimization applied successfully."
}

help_ram() {
    log_msg "Displaying help for RAM optimization..."
    echo "Usage: $0 -ram [apply|help]"
    echo ""
    echo "Options:"
    echo "  apply     Apply all RAM-related optimizations using the latest version (RAMv2)."
    echo "  help      Show this help message."
    echo ""
    echo "Description of applied RAM optimizations:"
    echo "  - Disables process reclaim to reduce unnecessary memory reclamation."
    echo "  - Sets memory pressure thresholds (max: 70, min: 50)."
    echo "  - Configures per-swap size to 512 KB."
    echo "  - Adjusts swappiness values for system, apps, and overall memory (set to 100)."
    echo "  - Clears unused memory from cgroup processes."
    echo "  - Optimizes virtual memory settings:"
    echo "    - Dirty background ratio: 10%"
    echo "    - Dirty ratio: 40%"
    echo "    - Dirty expire time: 1500 centiseconds"
    echo "    - Dirty writeback time: 200 centiseconds"
    echo "    - External fragmentation threshold: 200"
    echo "    - Swappiness: 100"
    echo "    - Page cluster: 3"
    echo "    - Minimum free memory: 8192 KB"
    echo "    - Extra free memory: 29615 KB"
    echo "  - Configures ZRAM compression streams to 8."
    echo "  - Disables OOM (Out of Memory) killer task dumping."
    echo "  - Sets overcommit memory ratio to 100%."
    echo "  - Configures vmpressure parameters for better memory handling."
    echo "  - Enables Low Memory Killer (LMK) with custom minfree thresholds:"
    echo "    - Thresholds: 14535, 29070, 43605, 58112, 72675, 87210"
    echo "  - Disables adaptive LMK."
}

main_ramv2() {
    log_msg "Starting RAM optimization..."

    # Disable process reclaim
    for rec in /sys/module/process_reclaim/parameters/*; do
        if [[ -f "$rec" ]]; then
            case "$(basename "$rec")" in
                enable_process_reclaim)
                    echo "0" > "$rec" 2>/dev/null || log_msg "Failed to disable process reclaim"
                    ;;
                pressure_max)
                    echo "70" > "$rec" 2>/dev/null || log_msg "Failed to set pressure_max"
                    ;;
                pressure_min)
                    echo "50" > "$rec" 2>/dev/null || log_msg "Failed to set pressure_min"
                    ;;
                per_swap_size)
                    echo "512" > "$rec" 2>/dev/null || log_msg "Failed to set per_swap_size"
                    ;;
            esac
        fi
    done

    # Set swappiness values
    for file in \
        "/dev/memcg/memory.swappiness" \
        "/dev/memcg/system/memory.swappiness" \
        "/dev/memcg/apps/memory.swappiness"; do
        if [[ -f "$file" ]]; then
            echo "80" > "$file" 2>/dev/null || log_msg "Failed to set swappiness for $file"
        fi
    done

    # Clear unused memory from cgroup processes
    if [[ -f "/dev/memcg/system/cgroup.procs" ]]; then
        for clear in $(cat /dev/memcg/system/cgroup.procs 2>/dev/null); do
            echo "$clear" > /dev/memcg/cgroup.procs 2>/dev/null || log_msg "Failed to clear cgroup process $clear"
        done
    fi

    # Virtual memory optimizations
    for param in \
        "/proc/sys/vm/dirty_background_ratio 10" \
        "/proc/sys/vm/dirty_ratio 40" \
        "/proc/sys/vm/dirty_expire_centisecs 1500" \
        "/proc/sys/vm/dirty_writeback_centisecs 200" \
        "/proc/sys/vm/extfrag_threshold 200" \
        "/proc/sys/vm/swappiness 80" \
        "/proc/sys/vm/page-cluster 3" \
        "/proc/sys/vm/oom_kill_allocating_task 0" \
        "/proc/sys/vm/min_free_kbytes 8192" \
        "/proc/sys/kernel/sched_schedstats 0" \
        "/proc/sys/vm/extra_free_kbytes 29615"; do
        file=${param% *}
        value=${param#* }
        if [[ -f "$file" ]]; then
            echo "$value" > "$file" 2>/dev/null || log_msg "Failed to set $file to $value"
        fi
    done

    # ZRAM optimizations
    if [[ -f "/sys/block/zram0/max_comp_streams" ]]; then
        echo "8" > /sys/block/zram0/max_comp_streams 2>/dev/null || log_msg "Failed to set ZRAM max_comp_streams"
    fi

    # Additional memory management settings
    for param in \
        "/proc/sys/vm/oom_dump_tasks 0" \
        "/proc/sys/vm/overcommit_ratio 100" \
        "/sys/module/vmpressure/parameters/allocstall_threshold 0" \
        "/sys/module/vmpressure/parameters/vmpressure_scale_max 100"; do
        file=${param% *}
        value=${param#* }
        if [[ -f "$file" ]]; then
            echo "$value" > "$file" 2>/dev/null || log_msg "Failed to set $file to $value"
        fi
    done

    # Low Memory Killer (LMK) settings
    if [[ -f "/sys/module/lowmemorykiller/parameters/enable_lmk" ]]; then
        echo "1" > /sys/module/lowmemorykiller/parameters/enable_lmk 2>/dev/null || log_msg "Failed to enable LMK"
        echo "0" > /sys/module/lowmemorykiller/parameters/enable_adaptive_lmk 2>/dev/null || log_msg "Failed to disable adaptive LMK"
        chmod 666 /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null || log_msg "Failed to change permissions for minfree"
        chown root /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null || log_msg "Failed to change ownership for minfree"
        echo "14535,29070,43605,58112,72675,87210" > /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null || log_msg "Failed to set LMK minfree thresholds"
        chmod 444 /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null || log_msg "Failed to lock minfree file"
    fi

    log_msg "RAM optimization applied successfully."
}