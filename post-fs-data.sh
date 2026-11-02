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

    # 2. Wait up to 5s for /dev/m_acc_misc and ensure HAL binds real hardware before Zygote forks system_server
    _i=0
    while [ "$_i" -lt 25 ]; do
        # Absolute safety cutoff: NEVER touch sensors HAL once system_server exists!
        if [ -n "$(pidof system_server 2>/dev/null)" ] || service check sensorservice 2>/dev/null | grep -q "found"; then
            exit 0
        fi

        if [ -c /dev/m_acc_misc ]; then
            # Re-poke sysfs nodes to ensure hardware bus link
            for node in /sys/class/sensor/m_acc_misc/accactive /sys/class/sensor/m_gyro_misc/gyroactive /sys/class/sensor/m_als_misc/alsactive /sys/class/sensor/m_ps_misc/psactive; do
                [ -f "$node" ] && echo 100 > "$node" 2>/dev/null
            done

            SENSOR_PID=$(pidof android.hardware.sensors@2.0-service-mediatek 2>/dev/null)
            if [ -n "$SENSOR_PID" ]; then
                if ! ls -l "/proc/$SENSOR_PID/fd" 2>/dev/null | grep -q "/dev/m_acc_misc"; then
                    if [ -z "$(pidof system_server 2>/dev/null)" ] && ! service check sensorservice 2>/dev/null | grep -q "found"; then
                        kill -9 "$SENSOR_PID" 2>/dev/null
                    fi
                fi
                exit 0
            fi
        fi
        _i=$(( _i + 1 ))
        sleep 0.2
    done
) </dev/null >/dev/null 2>&1 &

exit 0
