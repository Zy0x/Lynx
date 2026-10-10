#!/system/bin/sh
# Lynx Universal - Consolidated Low-Latency Network & TCP Subsystem
# Merges legacy net.sh & modern network_boost.sh into a pure POSIX module
# Pure POSIX /system/bin/sh compliance (No bashisms, no arrays)

write_node() {
    local val="$1"
    local node="$2"
    if [ -e "$node" ]; then
        chmod 644 "$node" 2>/dev/null
        echo "$val" > "$node" 2>/dev/null
    fi
}

help_net() {
    echo "Usage: Lxcore -net [apply|game|balance|help|algo=<ALGORITHM>]"
    echo ""
    echo "Options:"
    echo "  apply                 Apply universal high-throughput network tunings"
    echo "  game                  Apply ultra-low-latency Wi-Fi and TCP gaming stack"
    echo "  balance               Restore standard power-efficient Wi-Fi and socket buffers"
    echo "  algo=<ALGO>           Set specific TCP congestion algorithm (e.g. bbr, cubic)"
    echo "  help                  Show this help message"
    echo ""
    echo "Available Congestion Algorithms:"
    if [ -f "/proc/sys/net/ipv4/tcp_available_congestion_control" ]; then
        for a in $(cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null); do
            echo "  - $a"
        done
    fi
}

# ── 1. Dynamic TCP Congestion Algorithm Discovery ─────────────────────
select_best_tcp_algo() {
    local avail
    avail=$(cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null)
    for target in bbrv3 bbr westwood cubic; do
        case " $avail " in
            *" $target "*)
                echo "$target"
                return 0
                ;;
        esac
    done
    echo "cubic"
}

set_tcp_algo() {
    local target="$1"
    local avail
    avail=$(cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null)
    case " $avail " in
        *" $target "*)
            sysctl -w net.ipv4.tcp_congestion_control="$target" >/dev/null 2>&1
            write_node "$target" "/proc/sys/net/ipv4/tcp_congestion_control"
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

# ── 2. Universal Network Stack Tunables ───────────────────────────────
apply_universal_network() {
    # Dynamic Congestion Control
    local best_algo
    best_algo=$(select_best_tcp_algo)
    set_tcp_algo "$best_algo"

    # Queue Discipline (FQ-CoDel for bufferbloat elimination)
    if [ -f "/proc/sys/net/core/default_qdisc" ]; then
        echo "fq_codel" > /proc/sys/net/core/default_qdisc 2>/dev/null || \
        echo "fq" > /proc/sys/net/core/default_qdisc 2>/dev/null
    fi

    # Socket Buffer Windows
    sysctl -w net.core.rmem_max=16777216 >/dev/null 2>&1
    sysctl -w net.core.wmem_max=16777216 >/dev/null 2>&1
    sysctl -w net.core.rmem_default=262144 >/dev/null 2>&1
    sysctl -w net.core.wmem_default=262144 >/dev/null 2>&1
    sysctl -w net.ipv4.tcp_rmem="4096 87380 16777216" >/dev/null 2>&1
    sysctl -w net.ipv4.tcp_wmem="4096 65536 16777216" >/dev/null 2>&1

    # TCP Protocol Optimizations
    sysctl -w net.ipv4.tcp_ecn=1 >/dev/null 2>&1
    sysctl -w net.ipv4.tcp_fastopen=3 >/dev/null 2>&1
    sysctl -w net.ipv4.tcp_timestamps=0 >/dev/null 2>&1
    sysctl -w net.ipv4.tcp_tw_reuse=1 >/dev/null 2>&1
    sysctl -w net.ipv4.tcp_fin_timeout=15 >/dev/null 2>&1
}

# ── 3. Mode-Specific Network States (Game vs Balance) ─────────────────
apply_network_game() {
    apply_universal_network

    # Inhibit Wi-Fi Power Save (Eliminates 50-200ms DTIM packet delay spikes)
    cmd wifi set-power-save-mode 0 2>/dev/null
    cmd wifi force-low-latency-mode enabled 2>/dev/null

    for ps_node in \
        /sys/module/wlan/parameters/power_save \
        /sys/module/bcmdhd/parameters/op_mode \
        /sys/class/net/wlan0/queues/rx-0/rps_cpus; do
        write_node "0" "$ps_node"
    done

    # Ultra-Low Latency Sockets
    sysctl -w net.ipv4.tcp_low_latency=1 >/dev/null 2>&1
    sysctl -w net.ipv4.tcp_autocorking=0 >/dev/null 2>&1
    sysctl -w net.ipv4.tcp_notsent_lowat=16384 >/dev/null 2>&1
}

apply_network_balance() {
    # Restore Wi-Fi Power Save for Energy Conservation
    cmd wifi set-power-save-mode 1 2>/dev/null
    cmd wifi force-low-latency-mode disabled 2>/dev/null

    for ps_node in /sys/module/wlan/parameters/power_save; do
        write_node "1" "$ps_node"
    done

    # Restore Standard Sockets
    sysctl -w net.ipv4.tcp_low_latency=0 >/dev/null 2>&1
    sysctl -w net.ipv4.tcp_autocorking=1 >/dev/null 2>&1
}

main_net_command() {
    case "$2" in
        help)
            help_net
            ;;
        game)
            apply_network_game
            ;;
        balance)
            apply_network_balance
            ;;
        algo=*)
            algo_val="${2#algo=}"
            set_tcp_algo "$algo_val"
            ;;
        apply|"")
            apply_universal_network
            ;;
        *)
            help_net
            ;;
    esac
}
