#!/bin/bash

# Help function for Network Optimization
help_net() {
    echo "Usage: Lxcore -net [apply|help]"
    echo ""
    echo "Options:"
    echo "  apply                 Apply network optimizations with automatic algorithm selection."
    echo "  apply algo=<ALGORITHM> Apply network optimizations and set a specific TCP congestion control algorithm."
    echo "  help                  Show this help message."
    echo ""
    echo "Available TCP Congestion Control Algorithms:"
    cat /proc/sys/net/ipv4/tcp_available_congestion_control | tr ' ' '\n' | awk '{print "  - " $1}'
    echo ""
    echo "Examples:"
    echo "  Lxcore -net apply                     # Apply network optimizations with automatic algorithm selection"
    echo "  Lxcore -net apply algo=bbrv3         # Apply network optimizations and set the BBRv3 algorithm"
    echo "  Lxcore -net help                     # Show this help message"
}

# Function to set the TCP congestion control algorithm
set_tcp_algorithm() {
    local algo="$1"
    available_algos=$(cat /proc/sys/net/ipv4/tcp_available_congestion_control)
    if [[ ! $available_algos == *"$algo"* ]]; then
        log_msg "ERROR: Algorithm '$algo' is not available."
        echo "Available algorithms are: $available_algos"
        return 1
    fi
    echo "$algo" > /proc/sys/net/ipv4/tcp_congestion_control
    sysctl -w net.ipv4.tcp_congestion_control="$algo"
    log_msg "Network algorithm changed to '$algo'."
}

# Function to automatically select a preferred TCP congestion control algorithm
select_preferred_algorithm() {
    available_algos=$(cat /proc/sys/net/ipv4/tcp_available_congestion_control)
    preferred=("c2tcp" "bbrv3" "cubic" "westwood")
    for algo in "${preferred[@]}"; do
        if [[ $available_algos == *"$algo"* ]]; then
            log_msg "Preferred algorithm selected: $algo"
            set_tcp_algorithm "$algo"
            return 0
        fi
    done
    log_msg "No preferred algorithm found. Using default algorithm: cubic"
    set_tcp_algorithm "cubic"
}

# Function to apply network optimizations
main_net() {
    log_msg "Starting network optimization..."

    # Apply sysctl tweaks
    sysctl_params=(
        "net.ipv4.tcp_ecn=1"
        "net.ipv4.tcp_timestamps=0"
        "net.ipv4.tcp_rfc1337=1"
        "net.ipv4.tcp_tw_reuse=1"
        "net.ipv4.tcp_tw_recycle=1"
        "net.ipv4.tcp_keepalive_probes=5"
        "net.ipv4.tcp_probe_threshold=6"
        "net.ipv4.tcp_keepalive_intvl=15"
        "net.ipv4.tcp_fin_timeout=7"
        "net.ipv4.tcp_pacing_ca_ratio=80"
        "net.ipv4.tcp_pacing_ss_ratio=150"
        "net.core.wmem_max=12582912"
        "net.core.rmem_max=12582912"
        "net.core.rmem_default=31457280"
        "net.core.wmem_default=31457280"
        "net.ipv4.tcp_probe_interval=400"
        "net.ipv4.udp_rmem_min=16384"
        "net.ipv4.tcp_wmem=8192 65536 16777216"
        "net.ipv4.tcp_rmem=8192 87380 16777216"
        "net.ipv4.udp_wmem_min=16384"
        "net.ipv4.tcp_mem=65536 131072 262144"
        "net.ipv4.udp_mem=65536 131072 262144"
        "net.ipv4.tcp_synack_retries=2"
        "net.core.netdev_max_backlog=65536"
        "net.ipv4.tcp_max_tw_buckets=1440000"
        "net.ipv4.tcp_syn_retries=2"
        "net.ipv4.tcp_keepalive_time=300"
        "net.ipv4.tcp_no_metrics_save=1"
        "net.ipv4.conf.all.send_redirects=0"
        "net.ipv4.conf.all.log_martians=1"
        "net.ipv4.conf.all.secure_redirects=0"
        "net.ipv4.tcp_retries1=3"
        "net.ipv4.tcp_retries2=15"
        "net.unix.max_dgram_qlen=50"
        "net.ipv6.ip6frag_low_thresh=196608"
        "net.ipv6.ip6frag_high_thresh=262144"
        "net.ipv4.ipfrag_low_thresh=196608"
        "net.ipv4.ipfrag_high_thresh=262144"
        "net.core.default_qdisc=fq"
        "net.ipv4.tcp_notsent_lowat=16384"
        "net.core.somaxconn=4096"
        "net.core.optmem_max=25165824"
        "net.netfilter.nf_conntrack_max=10000000"
        "net.netfilter.nf_conntrack_tcp_loose=0"
        "net.netfilter.nf_conntrack_tcp_timeout_established=1800"
        "net.netfilter.nf_conntrack_tcp_timeout_close_wait=10"
        "net.netfilter.nf_conntrack_tcp_timeout_fin_wait=20"
        "net.netfilter.nf_conntrack_tcp_timeout_last_ack=20"
        "net.netfilter.nf_conntrack_tcp_timeout_syn_recv=20"
        "net.netfilter.nf_conntrack_tcp_timeout_syn_sent=20"
        "net.netfilter.nf_conntrack_tcp_timeout_time_wait=10"
        "net.ipv4.ip_local_port_range=16384 65535"
        "net.ipv4.ip_no_pmtu_disc=0"
        "net.ipv4.route.flush=1"
        "net.ipv4.tcp_sack=1"
        "net.ipv4.tcp_fack=1"
        "net.ipv4.tcp_window_scaling=1"
        "net.ipv4.tcp_syncookies=0"
        "net.ipv4.tcp_low_latency=1"
    )
    for param in "${sysctl_params[@]}"; do
        key="${param%%=*}"
        value="${param#*=}"
        if ! sysctl -w "$key=$value" > /dev/null 2>&1; then
            log_msg "ERROR: Failed to set sysctl parameter '$key' to '$value'."
        fi
    done

    log_msg "Network optimization applied successfully."
}

# Main function for -net command
# Main function for -net command
main_net_command() {
    case "$2" in
        apply)
            if [[ "$3" == algo=* ]]; then
                algo="${3#*=}"
                log_msg "Applying network optimizations with specified algorithm: $algo"
                if ! set_tcp_algorithm "$algo"; then
                    log_msg "ERROR: Failed to set the specified algorithm '$algo'. Aborting network optimization."
                    return 1
                fi
            else
                log_msg "Applying network optimizations with automatic algorithm selection..."
                select_preferred_algorithm
            fi

            # Apply network optimizations only if algorithm is successfully set
            main_net
            ;;
        help)
            help_net
            ;;
        *)
            echo "Unknown option. Use 'Lxcore -net help' for usage."
            exit 1
            ;;
    esac
}