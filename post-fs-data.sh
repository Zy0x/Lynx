#!/system/bin/sh
# Lynx Universal - Early Pre-Zygote Boot Hook (post-fs-data)
# Runs BEFORE zygote forks system_server.

# MediaTek SCP SensorHub Pre-Zygote Hardware Synchronizer:
# In MTK Helio G96, kernel SCP initializes asynchronously (~2.2s after cold boot).
# To guarantee 100% natural sensor operation without lockscreen crashes:
# 1. Wait for kernel SCP driver to expose character device nodes AND sysfs scaling nodes.
# 2. Set ownership (system:system) and permissions (0660) immediately.
# 3. Restart vendor.sensors-hal-2-0 BEFORE system_server starts, ensuring
#    HAL constructors successfully read accactive (1000) and gyroactive (7505747).
if [ -d /proc/ppm ] || [ -d /proc/ged ] || [ -c /dev/ged ] || [ -f /sys/firmware/devicetree/base/compatible ] && grep -qi "mediatek" /sys/firmware/devicetree/base/compatible 2>/dev/null; then
    _i=0
    while [ "$_i" -lt 40 ]; do
        # Absolute safety guard: NEVER touch HAL once system_server exists
        if [ -n "$(pidof system_server 2>/dev/null)" ]; then
            exit 0
        fi

        if [ -c /dev/m_acc_misc ] && [ -c /dev/m_als_misc ] && [ -f /sys/class/sensor/m_acc_misc/accactive ] && [ -f /sys/class/sensor/m_gyro_misc/gyroactive ]; then
            # Ensure full permissions on all physical sensor character nodes and sysfs divisor/active nodes
            chmod 0660 /dev/m_*_misc /dev/sensorlist /sys/class/sensor/m_*_misc/* 2>/dev/null
            chown system:system /dev/m_*_misc /dev/sensorlist /sys/class/sensor/m_*_misc/* 2>/dev/null

            # Restart HAL once sysfs nodes and dev nodes are fully prepared
            # This ensures HAL constructors re-run and cleanly read accactive (1000) and gyroactive (7505747)
            SENSOR_PID=$(pidof android.hardware.sensors@2.0-service-mediatek 2>/dev/null)
            if [ -n "$SENSOR_PID" ]; then
                if [ -z "$(pidof system_server 2>/dev/null)" ]; then
                    kill -9 "$SENSOR_PID" 2>/dev/null
                fi
            fi
            exit 0
        fi
        _i=$(( _i + 1 ))
        sleep 0.1
    done
fi

exit 0
