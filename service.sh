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

# MediaTek SCP SensorHub Race-Condition Auto-Heal Guard (APatch / Late SCP Init)
# Under APatch, vendor.sensors-hal-2-0 can start before /dev/m_*_misc nodes finish
# registering from SCP, leaving the HAL with 0 open sensor FDs until restarted.
(
    while [ "$(getprop sys.boot_completed)" != "1" ]; do
        sleep 2
    done
    sleep 3
    if [ -c /dev/m_acc_misc ]; then
        SENSOR_PID=$(pidof android.hardware.sensors@2.0-service-mediatek 2>/dev/null)
        if [ -n "$SENSOR_PID" ]; then
            if ! ls -l "/proc/$SENSOR_PID/fd" 2>/dev/null | grep -q "/dev/m_acc_misc"; then
                stop vendor.sensors-hal-2-0 2>/dev/null
                sleep 1
                start vendor.sensors-hal-2-0 2>/dev/null
            fi
        fi
    fi

    # Universal Dynamic Wi-Fi Silicon & Kernel Network Low-Latency Engine (Zero-Hardcoding)
    if [ -d /proc/net/wlan ] && [ -f "$MODPATH/platforms/mtk/system/vendor/firmware/wifi.cfg" ] && [ -f /vendor/firmware/wifi.cfg ]; then
        mount -o bind "$MODPATH/platforms/mtk/system/vendor/firmware/wifi.cfg" /vendor/firmware/wifi.cfg 2>/dev/null || true
    fi

    # 1. Dynamic MediaTek Gen4m / ConnInfra / Legacy WLAN Probing
    if [ -f /proc/net/wlan/dbgLevel ]; then
        if grep -q ":" /proc/net/wlan/dbgLevel 2>/dev/null; then
            echo "0xff:0x03" > /proc/net/wlan/dbgLevel 2>/dev/null
        else
            echo 0 > /proc/net/wlan/dbgLevel 2>/dev/null
        fi
    fi
    [ -f /proc/net/wlan/autoPerfCfg ] && echo "ForceEnable:1" > /proc/net/wlan/autoPerfCfg 2>/dev/null
    [ -f /proc/net/wlan/setCAM ] && echo 1 > /proc/net/wlan/setCAM 2>/dev/null
    [ -f /proc/net/wlan/pkt_filter ] && echo 0 > /proc/net/wlan/pkt_filter 2>/dev/null
    if [ -f /proc/net/wlan/cfg ]; then
        CFG_DUMP=$(cat /proc/net/wlan/cfg 2>/dev/null)
        for kv in "PowerSave 0" "KeepFullPwr 1" "AdvPws 0" "DisOnlineScan 1" "Sta2gBw 1" "TxopPsTx 0" "VhtAmsduInAmpduTx 1" "HtAmsduInAmpduTx 1" "VhtAmsduInAmpduRx 1" "HtAmsduInAmpduRx 1" "ThreadSched 1"; do
            k=${kv%% *}
            if [ -z "$CFG_DUMP" ] || echo "$CFG_DUMP" | grep -qE "(D:)?${k}\|"; then
                echo "$kv" > /proc/net/wlan/cfg 2>/dev/null
            fi
        done
    fi

    # 2. Dynamic Multi-Vendor Kernel Module Parameter Discovery (QCOM, Exynos/Tensor, Broadcom, Unisoc)
    for mod_dir in /sys/module/*wlan*/parameters /sys/module/*qca*/parameters /sys/module/*cnss*/parameters /sys/module/dhd/parameters /sys/module/bcmdhd/parameters /sys/module/scsc_wlan/parameters /sys/module/sprdwl*/parameters; do
        [ -d "$mod_dir" ] || continue
        for p_zero in con_mode gEnablePacketFilter dynamic_ps auto_power packet_filter dhd_slpauto dhd_watchdog_ms dhd_pkt_filter_enable slp_enable; do
            [ -f "$mod_dir/$p_zero" ] && echo 0 > "$mod_dir/$p_zero" 2>/dev/null
        done
        for p_one in rx_mode op_mode; do
            [ -f "$mod_dir/$p_one" ] && echo 1 > "$mod_dir/$p_one" 2>/dev/null
        done
    done

    # 3. Dynamic Multi-Vendor Wireless Kernel Thread Real-Time Boost
    for t in $(ps -A -o pid,comm 2>/dev/null | awk '/main_thread|hif_thread|rx_thread|cds_ol_rx|cds_mc_thread|dhd_dpc|dhd_rxf|hip4_bh|hip5_bh|sprdwl_/ {print $1}'); do
        renice -n -20 -p "$t" 2>/dev/null
        chrt -f -p 50 "$t" 2>/dev/null || true
    done

    # 4. Dynamic CPU Bitmask Discovery (4, 6, 8, 10, 12+ Cores)
    CPU_MASK=0
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
    [ "$CPU_MASK" -le 0 ] && CPU_MASK=255
    CPU_HEX=$(printf "%x" "$CPU_MASK")

    # 5. Dynamic RAM-Proportional Socket Buffer & NAPI Budget Scaling
    MEM_KB=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2}')
    [ -z "$MEM_KB" ] && MEM_KB=4000000
    if [ "$MEM_KB" -ge 8000000 ]; then
        BUF_MAX=33554432; BUF_DEF=524288; BACKLOG=32768; BUDGET=800; RPS_ENTRIES=65536
    elif [ "$MEM_KB" -ge 4000000 ]; then
        BUF_MAX=16777216; BUF_DEF=262144; BACKLOG=16384; BUDGET=600; RPS_ENTRIES=32768
    elif [ "$MEM_KB" -ge 2500000 ]; then
        BUF_MAX=8388608; BUF_DEF=131072; BACKLOG=8192; BUDGET=400; RPS_ENTRIES=16384
    else
        BUF_MAX=4194304; BUF_DEF=65536; BACKLOG=4096; BUDGET=300; RPS_ENTRIES=8192
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
    echo "4096 87380 $BUF_MAX" > /proc/sys/net/ipv4/tcp_rmem 2>/dev/null
    echo "4096 65536 $BUF_MAX" > /proc/sys/net/ipv4/tcp_wmem 2>/dev/null
    echo 3 > /proc/sys/net/ipv4/tcp_fastopen 2>/dev/null
    echo 1 > /proc/sys/net/ipv4/tcp_low_latency 2>/dev/null
    echo 0 > /proc/sys/net/ipv4/tcp_autocorking 2>/dev/null
    echo 0 > /proc/sys/net/ipv4/tcp_slow_start_after_idle 2>/dev/null
    echo 16384 > /proc/sys/net/ipv4/tcp_notsent_lowat 2>/dev/null
    echo 1 > /proc/sys/net/ipv4/tcp_mtu_probing 2>/dev/null

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
    done
) &

exec "$MODPATH/system/bin/lynxd" boot
