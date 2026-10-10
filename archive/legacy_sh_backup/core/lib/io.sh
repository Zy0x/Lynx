#!/system/bin/sh
# Lynx Universal - Universal Block I/O Subsystem Optimizer
# Hardened POSIX compliance for Android /system/bin/sh (Toybox/ash)
# Zero bashisms, safe node checks, dynamic UFS/eMMC discovery

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

# Dynamic Scheduler Selection (Priority Chain)
select_best_scheduler() {
    local queue="$1"
    local target_type="$2" # ufs | emmc | loop
    local avail
    avail=$(cat "$queue/scheduler" 2>/dev/null)

    if [ "$target_type" = "ufs" ]; then
        for s in none mq-deadline bfq kyber noop; do
            case " $avail " in
                *" $s "*|*"[$s]"*)
                    echo "$s"
                    return 0
                    ;;
            esac
        done
    else
        for s in mq-deadline bfq kyber noop none; do
            case " $avail " in
                *" $s "*|*"[$s]"*)
                    echo "$s"
                    return 0
                    ;;
            esac
        done
    fi
}

apply_default_io() {
    # 1. Base parameters across all storage queues
    for queue in /sys/block/*/queue; do
        [ -d "$queue" ] || continue
        write_node "0" "$queue/add_random"
        write_node "0" "$queue/iostats"
        write_node "1" "$queue/rq_affinity"
        write_node "128" "$queue/nr_requests"
    done

    # 2. Internal UFS Storage (sd*)
    for int in /sys/block/sd*/queue; do
        [ -d "$int" ] || continue
        local best_sched
        best_sched=$(select_best_scheduler "$int" "ufs")
        [ -n "$best_sched" ] && write_node "$best_sched" "$int/scheduler"
        write_node "128" "$int/read_ahead_kb"
        write_node "128" "$int/nr_requests"
    done

    # 3. eMMC Storage (mmcblk*)
    for ext in /sys/block/mmcblk*/queue; do
        [ -d "$ext" ] || continue
        local best_sched
        best_sched=$(select_best_scheduler "$ext" "emmc")
        [ -n "$best_sched" ] && write_node "$best_sched" "$ext/scheduler"
        write_node "512" "$ext/read_ahead_kb"
        write_node "128" "$ext/nr_requests"
        write_node "1" "$ext/rq_affinity"

        # Tunables if available
        write_node "0" "$ext/iosched/slice_idle"
        write_node "0" "$ext/iosched/slice_idle_us"
        write_node "0" "$ext/iosched/group_idle"
        write_node "0" "$ext/iosched/group_idle_us"
        write_node "1" "$ext/iosched/low_latency"
    done

    # 4. Loop Devices
    for loop in /sys/block/loop*/queue; do
        [ -d "$loop" ] || continue
        if grep -q "none" "$loop/scheduler" 2>/dev/null; then
            write_node "none" "$loop/scheduler"
        fi
        write_node "128" "$loop/read_ahead_kb"
    done

    # 5. Virtual Memory Caching (RAM, DM, ZRAM)
    for q in /sys/block/ram*/queue /sys/block/dm*/queue; do
        [ -d "$q" ] || continue
        write_node "0" "$q/rotational"
        write_node "write back" "$q/write_cache"
    done

    write_node "write back" /sys/block/zram0/queue/write_cache
}

apply_block_scheduler() {
    local block_type="$1"
    local scheduler="$2"

    for queue in /sys/block/${block_type}*/queue; do
        [ -d "$queue" ] || continue
        write_node "$scheduler" "$queue/scheduler"
    done
}

apply_specific_block_scheduler() {
    local block_device="$1"
    local scheduler="$2"
    local target_q="/sys/block/$block_device/queue"

    if [ -d "$target_q" ]; then
        write_node "$scheduler" "$target_q/scheduler"
    fi
}

help_io() {
    echo "Usage: Lxcore -io [apply|help|<block>-<scheduler>]"
    echo ""
    echo "Universal Storage I/O optimizations:"
    echo "  - Dynamic scheduler discovery: none (UFS) / mq-deadline (eMMC)"
    echo "  - Optimized read_ahead_kb buffers for low-latency gaming asset streaming"
    echo "  - Reduces disk I/O queue bottlenecking"
}

main_io() {
    case "$2" in
        apply)
            apply_default_io
            shift 2 2>/dev/null || shift
            while [ $# -gt 0 ]; do
                case "$1" in
                    *-*)
                        local block="${1%-*}"
                        local scheduler="${1#*-}"
                        if echo "$block" | grep -qE '^[a-zA-Z]+$'; then
                            apply_block_scheduler "$block" "$scheduler"
                        else
                            apply_specific_block_scheduler "$block" "$scheduler"
                        fi
                        ;;
                esac
                shift
            done
            ;;
        help|"")
            help_io
            ;;
        *)
            shift
            while [ $# -gt 0 ]; do
                case "$1" in
                    *-*)
                        local block="${1%-*}"
                        local scheduler="${1#*-}"
                        if echo "$block" | grep -qE '^[a-zA-Z]+$'; then
                            apply_block_scheduler "$block" "$scheduler"
                        else
                            apply_specific_block_scheduler "$block" "$scheduler"
                        fi
                        ;;
                esac
                shift
            done
            ;;
    esac
}