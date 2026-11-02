#!/system/bin/sh
# Lynx Universal - Early Pre-Zygote Boot Hook (post-fs-data)
# Runs BEFORE zygote forks system_server.
# Fully detached (</dev/null >/dev/null 2>&1 &) so post-fs-data returns in <1ms.

(
    # 1. Wake physical sensor character device nodes immediately
    for node in /sys/class/sensor/m_acc_misc/accactive /sys/class/sensor/m_gyro_misc/gyroactive /sys/class/sensor/m_als_misc/alsactive /sys/class/sensor/m_ps_misc/psactive; do
        if [ -f "$node" ]; then
            echo 100 > "$node" 2>/dev/null
        fi
    done

    # 2. Pre-arm Sensor Hub Ring-0 sync via KPM before Android Sensor HAL initializes
    if [ -x /data/adb/ap/bin/kpatch ]; then
        /data/adb/ap/bin/kpatch 12345678 ctl0 lynx_sensor_hub 2 2>/dev/null
    fi
) </dev/null >/dev/null 2>&1 &

exit 0
