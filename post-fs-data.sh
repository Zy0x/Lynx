#!/system/bin/sh
# Lynx Universal - Early Pre-Zygote Boot Hook (post-fs-data)
# Runs BEFORE zygote64 forks system_server.
# Fully detached (</dev/null >/dev/null 2>&1 &) so post-fs-data returns in <1ms.

(
    _i=0
    while [ "$_i" -lt 40 ]; do
        # Hard safety cutoff: NEVER restart sensors HAL once system_server or SensorService exists!
        if [ -n "$(pidof system_server 2>/dev/null)" ] || service check sensorservice 2>/dev/null | grep -q "found"; then
            exit 0
        fi
        if [ -c /dev/m_acc_misc ]; then
            SENSOR_PID=$(pidof android.hardware.sensors@2.0-service-mediatek 2>/dev/null)
            if [ -n "$SENSOR_PID" ]; then
                if ! ls -l "/proc/$SENSOR_PID/fd" 2>/dev/null | grep -q "/dev/m_acc_misc"; then
                    if [ -z "$(pidof system_server 2>/dev/null)" ] && ! service check sensorservice 2>/dev/null | grep -q "found"; then
                        stop vendor.sensors-hal-2-0 2>/dev/null
                        start vendor.sensors-hal-2-0 2>/dev/null
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
