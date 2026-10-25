#!/system/bin/sh
# Lynx Universal - Early Pre-Zygote Boot Hook (post-fs-data)
# Runs BEFORE zygote64 and system_server (SensorService) are spawned.

# MediaTek SCP SensorHub Pre-Zygote Synchronizer:
# Under APatch, vendor.sensors-hal-2-0 (class main) can start ~0.5s before the SCP
# microcontroller finishes registering /dev/m_acc_misc & /dev/m_als_misc (t ~ 2.0s).
# By polling in a non-blocking subshell during post-fs-data (t ~ 1.5s..6.0s), we
# restart vendor.sensors-hal-2-0 the exact 200ms after /dev/m_acc_misc appears —
# long BEFORE system_server starts SensorService and ~15s BEFORE the Lock Screen appears.
(
    _i=0
    while [ "$_i" -lt 35 ]; do
        # Abort immediately if system_server has already registered SensorService
        if service check sensorservice 2>/dev/null | grep -q "found"; then
            exit 0
        fi
        if [ -c /dev/m_acc_misc ]; then
            SENSOR_PID=$(pidof android.hardware.sensors@2.0-service-mediatek 2>/dev/null)
            if [ -n "$SENSOR_PID" ]; then
                if ! ls -l "/proc/$SENSOR_PID/fd" 2>/dev/null | grep -q "/dev/m_acc_misc"; then
                    if ! service check sensorservice 2>/dev/null | grep -q "found"; then
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
) &
