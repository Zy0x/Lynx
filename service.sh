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

    # Disable heavy window blurs only if not already disabled (avoids spawning Java `settings` on every boot)
    [ "$(settings get global disable_window_blurs 2>/dev/null)" != "1" ] && settings put global disable_window_blurs 1 2>/dev/null
    setprop persist.sys.sf.disable_blurs 1 2>/dev/null

    # Preserve genuine OEM /vendor/firmware/wifi.cfg RF/EDCCA/antenna calibration on MediaTek gen4m
    # and avoid runtime /proc/net/wlan/{cfg,autoPerfCfg,setCAM} writes that can stall MT6631 FW.

    # Qualcomm Snapdragon thermal-engine override (skip if post-fs-data already bind-mounted)
    if [ -f "$MODPATH/platforms/qcom/system/vendor/etc/thermal-engine.conf" ] && [ -f /vendor/etc/thermal-engine.conf ]; then
        if ! grep -q " /vendor/etc/thermal-engine\.conf " /proc/1/mountinfo 2>/dev/null; then
            mount -o bind "$MODPATH/platforms/qcom/system/vendor/etc/thermal-engine.conf" /vendor/etc/thermal-engine.conf 2>/dev/null || true
        fi
    fi

    # Dynamic Multi-Vendor Kernel Module Parameter Discovery (Safe Baseline)
    for mod_dir in /sys/module/*wlan*/parameters /sys/module/*qca*/parameters /sys/module/*cnss*/parameters /sys/module/dhd/parameters /sys/module/bcmdhd/parameters /sys/module/scsc_wlan/parameters /sys/module/sprdwl*/parameters; do
        [ -d "$mod_dir" ] || continue
        for p_one in dynamic_ps auto_power packet_filter gEnablePacketFilter dhd_slpauto dhd_pkt_filter_enable slp_enable; do
            [ -f "$mod_dir/$p_one" ] && echo 1 > "$mod_dir/$p_one" 2>/dev/null
        done
    done

    # Dynamic CPU Bitmask Discovery (4, 6, 8, 10, 12+ Cores) for RPS/XPS queue steering
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
    [ "$CPU_MASK" -le 0 ] && CPU_MASK=255
    CPU_HEX=$(printf "%x" "$CPU_MASK")

    MEM_KB=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2}')
    [ -z "$MEM_KB" ] && MEM_KB=4000000
    if [ "$MEM_KB" -ge 8000000 ]; then
        RPS_ENTRIES=65536
    elif [ "$MEM_KB" -ge 4000000 ]; then
        RPS_ENTRIES=32768
    elif [ "$MEM_KB" -ge 2500000 ]; then
        RPS_ENTRIES=16384
    else
        RPS_ENTRIES=8192
    fi

    # Android DNS caching & data stall timeout threshold (TCP/UDP sysctls are applied natively by lynxd boot)
    setprop net.dns_cache_size 1024 2>/dev/null
    setprop net.dns_cache_ttl 7200 2>/dev/null
    setprop persist.device_config.connectivity.data_stall_consecutive_dns_timeout_threshold 3 2>/dev/null

    # Dynamic Interface Queue Enumeration (Wi-Fi, Cellular RMNET/CCCI, Ethernet, USB)
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
        # Steer SoftIRQ RPS/XPS onto non-Big Little cores (e.g. 0x3e on 8-core) so Wi-Fi IRQs never preempt CPU 6-7
        RPS_HEX="$CPU_HEX"
        [ "$CPU_MASK" -eq 255 ] && RPS_HEX="3e"
        for rx in "$if_path"/queues/rx-*; do
            [ -f "$rx/rps_cpus" ] && echo "$RPS_HEX" > "$rx/rps_cpus" 2>/dev/null
            [ -f "$rx/rps_flow_cnt" ] && echo "$FLOW_PER_Q" > "$rx/rps_flow_cnt" 2>/dev/null
        done
        for tx in "$if_path"/queues/tx-*; do
            [ -f "$tx/xps_cpus" ] && echo "$RPS_HEX" > "$tx/xps_cpus" 2>/dev/null
        done
        [ -f "$if_path/gro_flush_timeout" ] && echo 0 > "$if_path/gro_flush_timeout" 2>/dev/null
        case "$iface" in
            wlan*|wlp*|mlan*|eth*|rmnet*|ccmni*)
                tc qdisc replace dev "$iface" root fq 2>/dev/null || tc qdisc replace dev "$iface" root fq_codel 2>/dev/null
                ;;
        esac
    done
) &

exec "$MODPATH/system/bin/lynxd" boot
