# Set TCP parameters
net=$(cat /proc/sys/net/ipv4/tcp_available_congestion_control)
preferred=("c2tcp" "bbrv3" "cubic" "westwood")
selected_algo=""
for algo in "${preferred[@]}"; do
  if [[ $net == *$algo* ]]; then
    selected_algo="$algo"
    break
  fi
done
echo "$selected_algo" > /proc/sys/net/ipv4/tcp_congestion_control
echo "1" > /sys/module/tcp_cubic/parameters/hystart
echo "1" > /sys/module/tcp_cubic/parameters/hystart_detect
sysctl -w net.ipv4.tcp_congestion_control="$selected_algo"
sysctl -w net.ipv4.tcp_ecn=1
sysctl -w net.ipv4.tcp_timestamps=0
sysctl -w net.ipv4.tcp_rfc1337=1
sysctl -w net.ipv4.tcp_tw_reuse=1
sysctl -w net.ipv4.tcp_tw_recycle=1
sysctl -w net.ipv4.tcp_keepalive_probes=5
sysctl -w net.ipv4.tcp_probe_threshold=6
sysctl -w net.ipv4.tcp_keepalive_intvl=15
sysctl -w net.ipv4.tcp_fin_timeout=7
sysctl -w net.ipv4.tcp_pacing_ca_ratio=80
sysctl -w net.ipv4.tcp_pacing_ss_ratio=150
sysctl -w net.core.wmem_max=12582912
sysctl -w net.core.rmem_max=12582912
sysctl -w net.core.rmem_default=31457280
sysctl -w net.core.wmem_default=31457280
sysctl -w net.ipv4.tcp_probe_interval=400
sysctl -w net.ipv4.udp_rmem_min=16384
sysctl -w net.ipv4.tcp_wmem="8192 65536 16777216"
sysctl -w net.ipv4.tcp_rmem="8192 87380 16777216"
sysctl -w net.ipv4.udp_wmem_min=16384
sysctl -w net.ipv4.tcp_mem="65536 131072 262144"
sysctl -w net.ipv4.udp_mem="65536 131072 262144"
sysctl -w net.ipv4.tcp_synack_retries=2
sysctl -w net.core.netdev_max_backlog=65536
sysctl -w net.ipv4.tcp_max_tw_buckets=1440000
sysctl -w net.ipv4.tcp_syn_retries=2
sysctl -w net.ipv4.tcp_keepalive_time=300
sysctl -w net.ipv4.tcp_no_metrics_save=1

# Set network parameters
sysctl -w net.ipv4.conf.all.send_redirects=0
sysctl -w net.ipv4.conf.all.log_martians=1
sysctl -w net.ipv4.conf.all.secure_redirects=0
sysctl -w net.ipv4.tcp_retries1=3
sysctl -w net.ipv4.tcp_retries2=15
sysctl -w net.unix.max_dgram_qlen=50
sysctl -w net.ipv6.ip6frag_low_thresh=196608
sysctl -w net.ipv6.ip6frag_high_thresh=262144
sysctl -w net.ipv4.ipfrag_low_thresh=196608
sysctl -w net.ipv4.ipfrag_high_thresh=262144
sysctl -w net.core.default_qdisc=fq
sysctl -w net.ipv4.tcp_notsent_lowat=16384
sysctl -w net.core.somaxconn=4096
sysctl -w net.core.optmem_max=25165824
sysctl -w net.netfilter.nf_conntrack_max=10000000
sysctl -w net.netfilter.nf_conntrack_tcp_loose=0
sysctl -w net.netfilter.nf_conntrack_tcp_timeout_established=1800
sysctl -w net.netfilter.nf_conntrack_tcp_timeout_close_wait=10
sysctl -w net.netfilter.nf_conntrack_tcp_timeout_fin_wait=20
sysctl -w net.netfilter.nf_conntrack_tcp_timeout_last_ack=20
sysctl -w net.netfilter.nf_conntrack_tcp_timeout_syn_recv=20
sysctl -w net.netfilter.nf_conntrack_tcp_timeout_syn_sent=20
sysctl -w net.netfilter.nf_conntrack_tcp_timeout_time_wait=10
sysctl -w net.ipv4.ip_local_port_range="16384 65535"
sysctl -w net.ipv4.ip_no_pmtu_disc=0
sysctl -w net.ipv4.route.flush=1
sysctl -w net.ipv4.tcp_sack=1
sysctl -w net.ipv4.tcp_fack=1
sysctl -w net.ipv4.tcp_window_scaling=1
sysctl -w net.ipv4.tcp_syncookies=0
sysctl -w net.ipv4.tcp_low_latency=1