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
) &

exec "$MODPATH/system/bin/lynxd" boot
