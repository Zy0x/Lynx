#!/system/bin/sh
# Lynx Universal - Universal Memory & VM Subsystem Optimizer
# Hardened POSIX compliance for Android /system/bin/sh (Toybox/mksh)

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

help_ram() {
    echo "Usage: Lxcore -ram [apply|help]"
    echo ""
    echo "Universal Memory optimizations:"
    echo "  - Activates Linux Kernel MGLRU (Multi-Gen LRU) where supported"
    echo "  - Optimizes Linux Virtual Memory (VM) dirty page cache & flushing"
    echo "  - Tunes kernel swappiness and memory compaction efficiency"
    echo "  - Replaces legacy kernel LMK with modern userspace PSI/MGLRU tuning"
}

main_ram() {
    log_msg "Starting Universal Memory & VM optimization..."

    # 1. Enable Linux Kernel Multi-Gen LRU (MGLRU) if kernel supports it
    if [ -f "/sys/kernel/mm/lru_gen/enabled" ]; then
        write_node "y" "/sys/kernel/mm/lru_gen/enabled"
        write_node "1000" "/sys/kernel/mm/lru_gen/min_ttl_ms" 2>/dev/null
        log_msg "MGLRU activated."
    fi

    # 2. Virtual Memory (VM) Subsystem Tunings
    write_node "10" "/proc/sys/vm/dirty_background_ratio"
    write_node "20" "/proc/sys/vm/dirty_ratio"
    write_node "500" "/proc/sys/vm/dirty_expire_centisecs"
    write_node "200" "/proc/sys/vm/dirty_writeback_centisecs"
    write_node "100" "/proc/sys/vm/extfrag_threshold"
    write_node "0" "/proc/sys/vm/oom_kill_allocating_task"
    write_node "0" "/proc/sys/vm/oom_dump_tasks"
    write_node "80" "/proc/sys/vm/overcommit_ratio"
    write_node "0" "/proc/sys/kernel/sched_schedstats"

    # 3. Memory Compaction
    write_node "1" "/proc/sys/vm/compact_unevictable_allowed"
    
    # 4. Swappiness (read from config.json if available, fallback to 80)
    local swap_val=80
    if [ -f "/data/adb/modules/Lynx/config.json" ]; then
        local cfg_sw
        cfg_sw=$(grep -o '"swappiness"[ \t]*:[ \t]*[0-9]*' /data/adb/modules/Lynx/config.json 2>/dev/null | awk -F: '{print $2}' | tr -d ' ')
        [ -n "$cfg_sw" ] && [ "$cfg_sw" -gt 0 ] 2>/dev/null && swap_val="$cfg_sw"
    fi
    write_node "$swap_val" "/proc/sys/vm/swappiness"
    write_node "$swap_val" "/dev/memcg/memory.swappiness"
    write_node "$swap_val" "/dev/memcg/apps/memory.swappiness"
    write_node "20" "/dev/memcg/system/memory.swappiness"

    # 5. Process Reclaim (if present in custom kernels)
    if [ -d "/sys/module/process_reclaim/parameters" ]; then
        write_node "0" "/sys/module/process_reclaim/parameters/enable_process_reclaim"
    fi

    log_msg "Universal Memory & VM optimization applied successfully."
}

# Alias for backwards compatibility with legacy callers
main_ramv2() {
    main_ram
}