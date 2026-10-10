#!/system/bin/sh
# Lynx Universal - Universal Dynamic Capability Discovery Protocol
# High-Level Adaptive Intelligence (Zero Hardcoding Guarantee)
# Optimized for pure POSIX /system/bin/sh

# 1. ZRAM Compression Algorithm Dynamic Selector
# Priority for High-Ratio: zstd -> lz4hc -> lz4 -> lzo-rle -> lzo
# Priority for Low-Latency: lz4 -> lzo-rle -> lzo
select_supported_zram_algo() {
    local node="/sys/block/zram0/comp_algorithm"
    if [ ! -f "$node" ]; then
        echo "lzo"
        return 0
    fi
    local supported
    supported=$(cat "$node" 2>/dev/null)
    [ -z "$supported" ] && { echo "lzo"; return 0; }

    for algo in "$@"; do
        case " $supported " in
            *" $algo "*)
                echo "$algo"
                return 0
                ;;
        esac
    done
    # Ultimate universal Linux kernel fallback
    echo "lzo"
}

# 2. Block Storage I/O Scheduler Dynamic Selector
# Priority for Fast Flash (UFS): none -> mq-deadline -> bfq
# Priority for eMMC: mq-deadline -> bfq -> deadline -> cfq -> noop
select_supported_iosched() {
    local queue_node="$1"
    shift
    local sched_node="$queue_node/scheduler"
    if [ ! -f "$sched_node" ]; then
        return 1
    fi
    local supported
    supported=$(tr -d '[]' < "$sched_node" 2>/dev/null)
    [ -z "$supported" ] && return 1

    for sched in "$@"; do
        case " $supported " in
            *" $sched "*)
                echo "$sched"
                return 0
                ;;
        esac
    done
    return 1
}

# 3. TCP Congestion Control Dynamic Selector
# Priority: bbr -> cubic -> westwood -> reno
select_supported_tcp_cong() {
    local node="/proc/sys/net/ipv4/tcp_available_congestion_control"
    if [ ! -f "$node" ]; then
        echo "cubic"
        return 0
    fi
    local supported
    supported=$(cat "$node" 2>/dev/null)
    [ -z "$supported" ] && { echo "cubic"; return 0; }

    for cong in "$@"; do
        case " $supported " in
            *" $cong "*)
                echo "$cong"
                return 0
                ;;
        esac
    done
    echo "cubic"
}

# 4. CPU Governor Dynamic Selector & Preservation
# Queries scaling_available_governors
select_supported_governor() {
    local policy_dir="$1"
    shift
    local node="$policy_dir/scaling_available_governors"
    if [ ! -f "$node" ]; then
        echo "schedutil"
        return 0
    fi
    local supported
    supported=$(cat "$node" 2>/dev/null)
    [ -z "$supported" ] && { echo "schedutil"; return 0; }

    for gov in "$@"; do
        case " $supported " in
            *" $gov "*)
                echo "$gov"
                return 0
                ;;
        esac
    done
    echo "schedutil"
}

# 5. Dynamic Physical RAM Sizing (KB)
# Reads MemTotal from /proc/meminfo
get_dynamic_zram_size_kb() {
    local mem_total
    mem_total=$(awk '/MemTotal/ {print $2}' /proc/meminfo 2>/dev/null)
    if [ -z "$mem_total" ] || [ "$mem_total" -le 0 ]; then
        echo "2097152" # 2 GB fallback
        return 0
    fi

    # For devices <= 3.5 GB (<= 3670016 KB): allocate 40% of RAM
    if [ "$mem_total" -le 3670016 ]; then
        echo $(( mem_total * 40 / 100 ))
    # For devices <= 6 GB: allocate 50% of RAM (max 3 GB)
    elif [ "$mem_total" -le 6291456 ]; then
        echo $(( mem_total * 50 / 100 ))
    # For devices >= 8 GB: allocate 35% of RAM (capped at 4 GB = 4194304 KB)
    else
        local target=$(( mem_total * 35 / 100 ))
        if [ "$target" -gt 4194304 ]; then
            echo "4194304"
        else
            echo "$target"
        fi
    fi
}
