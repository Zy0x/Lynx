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

    # MediaTek Gen4m Wi-Fi & Kernel Network Low-Latency Turbo Guard
    if [ -f "$MODPATH/platforms/mtk/system/vendor/firmware/wifi.cfg" ] && [ -f /vendor/firmware/wifi.cfg ]; then
        mount -o bind "$MODPATH/platforms/mtk/system/vendor/firmware/wifi.cfg" /vendor/firmware/wifi.cfg 2>/dev/null || true
    fi
    if [ -f /proc/net/wlan/dbgLevel ]; then
        echo "0xff:0x03" > /proc/net/wlan/dbgLevel 2>/dev/null
    fi
    if [ -f /proc/net/wlan/autoPerfCfg ]; then
        echo "ForceEnable:1" > /proc/net/wlan/autoPerfCfg 2>/dev/null
    fi
    if [ -f /proc/net/wlan/setCAM ]; then
        echo 1 > /proc/net/wlan/setCAM 2>/dev/null
    fi
    if [ -f /proc/net/wlan/cfg ]; then
        for kv in "PowerSave 0" "AdvPws 0" "DisOnlineScan 1" "Sta2gBw 1" "TxopPsTx 0" "VhtAmsduInAmpduTx 1" "HtAmsduInAmpduTx 1" "VhtAmsduInAmpduRx 1" "HtAmsduInAmpduRx 1" "ThreadSched 1"; do
            echo "$kv" > /proc/net/wlan/cfg 2>/dev/null
        done
    fi
    for t in $(ps -A -o pid,comm 2>/dev/null | awk '/main_thread|hif_thread|rx_thread/ {print $1}'); do
        renice -n -20 -p "$t" 2>/dev/null
        chrt -f -p 50 "$t" 2>/dev/null || true
    done
    echo fq_codel > /proc/sys/net/core/default_qdisc 2>/dev/null
    echo 32768 > /proc/sys/net/core/rps_sock_flow_entries 2>/dev/null
    echo 16384 > /proc/sys/net/core/netdev_max_backlog 2>/dev/null
    echo 600 > /proc/sys/net/core/netdev_budget 2>/dev/null
    echo 8000 > /proc/sys/net/core/netdev_budget_usecs 2>/dev/null
    echo 16777216 > /proc/sys/net/core/rmem_max 2>/dev/null
    echo 16777216 > /proc/sys/net/core/wmem_max 2>/dev/null
    echo "4096 87380 16777216" > /proc/sys/net/ipv4/tcp_rmem 2>/dev/null
    echo "4096 65536 16777216" > /proc/sys/net/ipv4/tcp_wmem 2>/dev/null
    echo 3 > /proc/sys/net/ipv4/tcp_fastopen 2>/dev/null
    echo 1 > /proc/sys/net/ipv4/tcp_low_latency 2>/dev/null
    echo 0 > /proc/sys/net/ipv4/tcp_autocorking 2>/dev/null
    echo 0 > /proc/sys/net/ipv4/tcp_slow_start_after_idle 2>/dev/null
    echo 16384 > /proc/sys/net/ipv4/tcp_notsent_lowat 2>/dev/null
    echo 1 > /proc/sys/net/ipv4/tcp_mtu_probing 2>/dev/null
    for rx in /sys/class/net/wlan0/queues/rx-*; do
        [ -f "$rx/rps_cpus" ] && echo ff > "$rx/rps_cpus" 2>/dev/null
        [ -f "$rx/rps_flow_cnt" ] && echo 4096 > "$rx/rps_flow_cnt" 2>/dev/null
    done
    for tx in /sys/class/net/wlan0/queues/tx-*; do
        [ -f "$tx/xps_cpus" ] && echo ff > "$tx/xps_cpus" 2>/dev/null
    done
) &

exec "$MODPATH/system/bin/lynxd" boot
