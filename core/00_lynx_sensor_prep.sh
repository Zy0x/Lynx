#!/system/bin/sh
# Lynx Universal - MediaTek Sensor Pre-Zygote Activator
# Runs in post-fs-data BEFORE Zygote forks system_server (Zero risk of framework crash)

LOG="/data/adb/lynx_sensor_pfsd.log"
echo "[$(date)] Sensor prep post-fs-data started..." > $LOG

# 1. Wake physical sensor character device nodes immediately
for node in /sys/class/sensor/m_acc_misc/accactive /sys/class/sensor/m_gyro_misc/gyroactive /sys/class/sensor/m_als_misc/alsactive /sys/class/sensor/m_ps_misc/psactive; do
    if [ -f "$node" ]; then
        echo 100 > "$node" 2>/dev/null
        echo "[$(date)] Poked $node -> 100" >> $LOG
    fi
done

# 2. Monitor HAL startup and ensure it binds /dev/m_acc_misc BEFORE system_server launches
(
    _i=0
    while [ "$_i" -lt 30 ]; do
        # Absolute safety cutoff: NEVER touch sensors HAL once system_server exists!
        if [ -n "$(pidof system_server 2>/dev/null)" ] || service check sensorservice 2>/dev/null | grep -q "found"; then
            echo "[$(date)] system_server detected. Safety cutoff triggered. Exiting." >> $LOG
            exit 0
        fi

        SENSOR_PID=$(pidof android.hardware.sensors@2.0-service-mediatek 2>/dev/null)
        if [ -n "$SENSOR_PID" ]; then
            if ls -l "/proc/$SENSOR_PID/fd" 2>/dev/null | grep -q "/dev/m_acc_misc"; then
                echo "[$(date)] Sensor HAL (PID $SENSOR_PID) already has /dev/m_acc_misc bound! Success." >> $LOG
                exit 0
            else
                echo "[$(date)] Sensor HAL (PID $SENSOR_PID) missing /dev/m_acc_misc. Refreshing HAL pre-zygote..." >> $LOG
                # Re-poke sysfs nodes
                for node in /sys/class/sensor/m_acc_misc/accactive /sys/class/sensor/m_gyro_misc/gyroactive /sys/class/sensor/m_als_misc/alsactive /sys/class/sensor/m_ps_misc/psactive; do
                    [ -f "$node" ] && echo 100 > "$node" 2>/dev/null
                done
                kill -9 "$SENSOR_PID" 2>/dev/null
                sleep 0.5
                NEW_PID=$(pidof android.hardware.sensors@2.0-service-mediatek 2>/dev/null)
                echo "[$(date)] Sensor HAL refreshed -> new PID $NEW_PID." >> $LOG
                exit 0
            fi
        fi

        _i=$(( _i + 1 ))
        sleep 0.2
    done
) &

exit 0
