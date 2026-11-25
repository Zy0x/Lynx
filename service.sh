#!/system/bin/sh
# Lynx Universal - Late Start Service Router (100% Native Rust Core)
MODPATH=${0%/*}
[ -d "$MODPATH" ] || MODPATH="/data/adb/modules/Lynx"

# VMA Limit Expansion Shield (RAM-Proportional Anti-Scudo NO MEMORY Abort)
if [ -f /proc/sys/vm/max_map_count ]; then
    TOTAL_RAM_KB=$(grep MemTotal /proc/meminfo | awk '{print $2}')
    if [ -n "$TOTAL_RAM_KB" ] && [ "$TOTAL_RAM_KB" -gt 6000000 ]; then
        echo 1048576 > /proc/sys/vm/max_map_count 2>/dev/null
    else
        echo 524288 > /proc/sys/vm/max_map_count 2>/dev/null
    fi
fi


(
    while [ "$(getprop sys.boot_completed)" != "1" ]; do
        sleep 2
    done
    sleep 3

    # Disable heavy window blurs for low-fillrate GPUs (e.g. Adreno 512) to prevent notification shade stutter
    settings put global disable_window_blurs 1 2>/dev/null
    setprop persist.sys.sf.disable_blurs 1 2>/dev/null

    # Preserve genuine OEM /vendor/firmware/wifi.cfg RF/EDCCA/antenna calibration on MediaTek gen4m
    # and avoid runtime /proc/net/wlan/{cfg,autoPerfCfg,setCAM} writes that can stall MT6631 FW.

    # Qualcomm Snapdragon thermal-engine override
    if [ -f "$MODPATH/platforms/qcom/system/vendor/etc/thermal-engine.conf" ]; then
        [ -f /vendor/etc/thermal-engine.conf ] && mount -o bind "$MODPATH/platforms/qcom/system/vendor/etc/thermal-engine.conf" /vendor/etc/thermal-engine.conf 2>/dev/null || true
        [ -f /system/vendor/etc/thermal-engine.conf ] && mount -o bind "$MODPATH/platforms/qcom/system/vendor/etc/thermal-engine.conf" /system/vendor/etc/thermal-engine.conf 2>/dev/null || true
    fi

    # 2. Dynamic Multi-Vendor Kernel Module Parameter Discovery (Safe Baseline)
    for mod_dir in /sys/module/*wlan*/parameters /sys/module/*qca*/parameters /sys/module/*cnss*/parameters /sys/module/dhd/parameters /sys/module/bcmdhd/parameters /sys/module/scsc_wlan/parameters /sys/module/sprdwl*/parameters; do
        [ -d "$mod_dir" ] || continue
        for p_one in dynamic_ps auto_power packet_filter gEnablePacketFilter dhd_slpauto dhd_pkt_filter_enable slp_enable; do
            [ -f "$mod_dir/$p_one" ] && echo 1 > "$mod_dir/$p_one" 2>/dev/null
        done
    done

    # 4. Dynamic CPU Bitmask Discovery (4, 6, 8, 10, 12+ Cores)
    CPU_MASK=0
    if [ -f /sys/devices/system/cpu/present ]; then
        PRESENT_CPUS=$(cat /sys/devices/system/cpu/present 2>/dev/null)
        IFS=','
        for part in $PRESENT_CPUS; do
            case "$part" in
                *-*)
                    start=${part%-*}
                    end=${part#*-}
                    i=$start
                    while [ "$i" -le "$end" ] && [ "$i" -lt 31 ]; do
                        CPU_MASK=$(( CPU_MASK | (1 << i) ))
                        i=$(( i + 1 ))
                    done
                    ;;
                *)
                    if [ -n "$part" ] && [ "$part" -lt 31 ]; then
                        CPU_MASK=$(( CPU_MASK | (1 << part) ))
                    fi
                    ;;
            esac
        done
        unset IFS
    fi
    if [ "$CPU_MASK" -le 0 ]; then
        for c in /sys/devices/system/cpu/cpu[0-9]*; do
            [ -f "$c/online" ] && [ "$(cat "$c/online" 2>/dev/null)" = "0" ] && continue
            c_num=${c##*cpu}
            case "$c_num" in
                ''|*[!0-9]*) continue ;;
            esac
            if [ "$c_num" -lt 31 ]; then
                CPU_MASK=$(( CPU_MASK | (1 << c_num) ))
            fi
        done
    fi
    [ "$CPU_MASK" -le 0 ] && CPU_MASK=255
    CPU_HEX=$(printf "%x" "$CPU_MASK")

    # 5. Dynamic RAM-Proportional Socket Buffer & NAPI Budget Scaling
    MEM_KB=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2}')
    [ -z "$MEM_KB" ] && MEM_KB=4000000
    if [ "$MEM_KB" -ge 8000000 ]; then
        BUF_MAX=33554432; BUF_DEF=524288; BACKLOG=32768; BUDGET=800; RPS_ENTRIES=65536; SYN_BACKLOG=4096; SOMAXCONN=2048; UDP_MEM="393216 524288 786432"
    elif [ "$MEM_KB" -ge 4000000 ]; then
        BUF_MAX=16777216; BUF_DEF=262144; BACKLOG=16384; BUDGET=600; RPS_ENTRIES=32768; SYN_BACKLOG=2048; SOMAXCONN=1024; UDP_MEM="196608 262144 393216"
    elif [ "$MEM_KB" -ge 2500000 ]; then
        BUF_MAX=8388608; BUF_DEF=131072; BACKLOG=8192; BUDGET=400; RPS_ENTRIES=16384; SYN_BACKLOG=1024; SOMAXCONN=1024; UDP_MEM="98304 131072 196608"
    else
        BUF_MAX=4194304; BUF_DEF=65536; BACKLOG=4096; BUDGET=300; RPS_ENTRIES=8192; SYN_BACKLOG=1024; SOMAXCONN=512; UDP_MEM="98304 131072 196608"
    fi

    echo fq_codel > /proc/sys/net/core/default_qdisc 2>/dev/null || echo fq > /proc/sys/net/core/default_qdisc 2>/dev/null
    if [ -f /proc/sys/net/ipv4/tcp_available_congestion_control ]; then
        AVAIL_CC=$(cat /proc/sys/net/ipv4/tcp_available_congestion_control 2>/dev/null)
        for cc in bbr3 bbr2 bbrplus bbr westwood cubic; do
            if echo " $AVAIL_CC " | grep -q " $cc "; then
                echo "$cc" > /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null
                break
            fi
        done
    fi

    echo "$RPS_ENTRIES" > /proc/sys/net/core/rps_sock_flow_entries 2>/dev/null
    echo "$BACKLOG" > /proc/sys/net/core/netdev_max_backlog 2>/dev/null
    echo "$BUDGET" > /proc/sys/net/core/netdev_budget 2>/dev/null
    echo 8000 > /proc/sys/net/core/netdev_budget_usecs 2>/dev/null
    echo "$BUF_MAX" > /proc/sys/net/core/rmem_max 2>/dev/null
    echo "$BUF_MAX" > /proc/sys/net/core/wmem_max 2>/dev/null
    echo "$BUF_DEF" > /proc/sys/net/core/rmem_default 2>/dev/null
    echo "$BUF_DEF" > /proc/sys/net/core/wmem_default 2>/dev/null
    echo "4096 $BUF_DEF $BUF_MAX" > /proc/sys/net/ipv4/tcp_rmem 2>/dev/null
    echo "4096 $(( BUF_DEF / 2 )) $BUF_MAX" > /proc/sys/net/ipv4/tcp_wmem 2>/dev/null
    echo 3 > /proc/sys/net/ipv4/tcp_fastopen 2>/dev/null
    echo 1 > /proc/sys/net/ipv4/tcp_timestamps 2>/dev/null
    echo 2 > /proc/sys/net/ipv4/tcp_adv_win_scale 2>/dev/null
    echo 1 > /proc/sys/net/ipv4/tcp_sack 2>/dev/null
    echo 1 > /proc/sys/net/ipv4/tcp_dsack 2>/dev/null
    echo 1 > /proc/sys/net/ipv4/tcp_window_scaling 2>/dev/null
    echo 1 > /proc/sys/net/ipv4/tcp_tw_reuse 2>/dev/null
    echo 15 > /proc/sys/net/ipv4/tcp_fin_timeout 2>/dev/null
    echo 0 > /proc/sys/net/ipv4/tcp_low_latency 2>/dev/null
    echo 1 > /proc/sys/net/ipv4/tcp_autocorking 2>/dev/null
    echo 0 > /proc/sys/net/ipv4/tcp_slow_start_after_idle 2>/dev/null
    echo 131072 > /proc/sys/net/ipv4/tcp_notsent_lowat 2>/dev/null
    echo 1 > /proc/sys/net/ipv4/tcp_mtu_probing 2>/dev/null

    # UDP buffer scaling (Gaming packet bursts, in-game voice chat, & HTTP/3 QUIC)
    echo 16384 > /proc/sys/net/ipv4/udp_rmem_min 2>/dev/null
    echo 16384 > /proc/sys/net/ipv4/udp_wmem_min 2>/dev/null
    echo "$UDP_MEM" > /proc/sys/net/ipv4/udp_mem 2>/dev/null

    # TCP SYN handshake, somaxconn, & keepalive tuning (Fast failover & anti-zombie connection)
    echo "$SOMAXCONN" > /proc/sys/net/core/somaxconn 2>/dev/null
    echo "$SYN_BACKLOG" > /proc/sys/net/ipv4/tcp_max_syn_backlog 2>/dev/null
    echo 3 > /proc/sys/net/ipv4/tcp_syn_retries 2>/dev/null
    echo 3 > /proc/sys/net/ipv4/tcp_synack_retries 2>/dev/null
    echo 300 > /proc/sys/net/ipv4/tcp_keepalive_time 2>/dev/null
    echo 15 > /proc/sys/net/ipv4/tcp_keepalive_intvl 2>/dev/null
    echo 4 > /proc/sys/net/ipv4/tcp_keepalive_probes 2>/dev/null

    # Android DNS caching & data stall timeout threshold
    setprop net.dns_cache_size 1024 2>/dev/null
    setprop net.dns_cache_ttl 7200 2>/dev/null
    setprop persist.device_config.connectivity.data_stall_consecutive_dns_timeout_threshold 3 2>/dev/null

    # 6. Dynamic Interface Queue Enumeration (Wi-Fi, Cellular RMNET/CCCI, Ethernet, USB)
    for if_path in /sys/class/net/*; do
        [ -d "$if_path" ] || continue
        iface=${if_path##*/}
        case "$iface" in
            lo|dummy*|sit*|ip6tnl*|bond*|tun*|tap*|ifb*|veth*|docker*|br-*|p2p*) continue ;;
        esac
        RX_CNT=0
        for rx in "$if_path"/queues/rx-*; do
            [ -d "$rx" ] && RX_CNT=$(( RX_CNT + 1 ))
        done
        [ "$RX_CNT" -le 0 ] && RX_CNT=1
        FLOW_PER_Q=$(( RPS_ENTRIES / RX_CNT ))
        [ "$FLOW_PER_Q" -lt 1024 ] && FLOW_PER_Q=1024
        for rx in "$if_path"/queues/rx-*; do
            [ -f "$rx/rps_cpus" ] && echo "$CPU_HEX" > "$rx/rps_cpus" 2>/dev/null
            [ -f "$rx/rps_flow_cnt" ] && echo "$FLOW_PER_Q" > "$rx/rps_flow_cnt" 2>/dev/null
        done
        for tx in "$if_path"/queues/tx-*; do
            [ -f "$tx/xps_cpus" ] && echo "$CPU_HEX" > "$tx/xps_cpus" 2>/dev/null
        done
        [ -f "$if_path/gro_flush_timeout" ] && echo 20000 > "$if_path/gro_flush_timeout" 2>/dev/null
        case "$iface" in
            wlan*|wlp*|mlan*|eth*|rmnet*|ccmni*)
                tc qdisc replace dev "$iface" root fq_codel 2>/dev/null || tc qdisc replace dev "$iface" root fq 2>/dev/null
                ;;
        esac
    done

    # MediaTek Wi-Fi silicon baseline tuning (CAM 1, performance monitor, burst loops)
    [ -f /proc/net/wlan/autoPerfCfg ] && echo "ForceEnable:1" > /proc/net/wlan/autoPerfCfg 2>/dev/null
    [ -f /proc/net/wlan/setCAM ] && echo "CAM 1" > /proc/net/wlan/setCAM 2>/dev/null
    if [ -f /proc/net/wlan/cfg ]; then
        echo "PowerSave 0" > /proc/net/wlan/cfg 2>/dev/null
        echo "Rx2OsLoop 16" > /proc/net/wlan/cfg 2>/dev/null
        echo "Rx2ReorderLoop 8" > /proc/net/wlan/cfg 2>/dev/null
        echo "DisOnlineScan 0" > /proc/net/wlan/cfg 2>/dev/null
        echo "VhtAmsduInAmpduTx 1" > /proc/net/wlan/cfg 2>/dev/null
        echo "VhtAmsduInAmpduRx 1" > /proc/net/wlan/cfg 2>/dev/null
        echo "HtAmsduInAmpduTx 1" > /proc/net/wlan/cfg 2>/dev/null
        echo "HtAmsduInAmpduRx 1" > /proc/net/wlan/cfg 2>/dev/null
        echo "TxBaSize 64" > /proc/net/wlan/cfg 2>/dev/null
        echo "RxVhtBaSize 64" > /proc/net/wlan/cfg 2>/dev/null
    fi
) &

exec "$MODPATH/system/bin/lynxd" boot
