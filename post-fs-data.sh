#!/system/bin/sh
# Lynx Universal - Early Pre-Zygote Boot Hook (post-fs-data)
# Runs BEFORE zygote forks system_server.

# MediaTek SCP SensorHub Pre-Zygote Hardware Synchronizer:
# In MTK Helio G96, kernel SCP initializes asynchronously (~2.2s after cold boot).
# BUG FIX v2: /sys/class/sensor kernel default = drw-rw---- (no execute bit).
#   The directory CANNOT be traversed by any process (incl. root shell test -f)
#   until chmod +x is applied. Therefore we apply chmod 755 to the DIRECTORY
#   unconditionally at the START of each poll iteration — BEFORE testing files.
# 1. Apply chmod 755 to sysfs sensor dirs on EVERY iteration (early and cheap).
# 2. Wait for kernel SCP driver to expose character device nodes AND sysfs scaling nodes.
# 3. Set ownership (system:system) and permissions (0660) immediately on files.
# 4. Restart vendor.sensors-hal-2-0 BEFORE system_server starts, ensuring
#    HAL constructors successfully read accactive (1000) and gyroactive (7505747).
IS_MTK=false
if [ -d /proc/ppm ] || [ -d /proc/ged ] || [ -c /dev/ged ]; then
    IS_MTK=true
elif [ -f /sys/firmware/devicetree/base/compatible ] && grep -qi "mediatek" /sys/firmware/devicetree/base/compatible 2>/dev/null; then
    IS_MTK=true
fi

if [ "$IS_MTK" = "true" ]; then
    _i=0
    while [ "$_i" -lt 60 ]; do
        # Absolute safety guard: NEVER touch HAL once system_server exists
        if [ -n "$(pidof system_server 2>/dev/null)" ]; then
            exit 0
        fi

        # CRITICAL: Apply +x on sysfs sensor directories FIRST, unconditionally,
        # so that subsequent [ -f ... ] tests can actually traverse into them.
        chmod 755 /sys/class/sensor /sys/devices/virtual/sensor 2>/dev/null
        chmod 755 /sys/devices/virtual/sensor/* 2>/dev/null

        if [ -c /dev/m_acc_misc ] && [ -c /dev/m_als_misc ] && [ -f /sys/class/sensor/m_acc_misc/accactive ] && [ -f /sys/class/sensor/m_gyro_misc/gyroactive ]; then
            # Full permission & ownership fix on all sensor sysfs nodes and dev nodes
            chmod 0660 /dev/m_*_misc /dev/sensorlist 2>/dev/null
            chmod 0660 /sys/class/sensor/m_*_misc/* 2>/dev/null
            chmod 0660 /sys/devices/virtual/sensor/*/* 2>/dev/null
            chown -R system:system /dev/m_*_misc /dev/sensorlist /sys/class/sensor /sys/devices/virtual/sensor 2>/dev/null

            # Restart HAL once sysfs nodes and dev nodes are fully prepared
            # Ensures HAL constructors re-run and cleanly read accactive (1000) and gyroactive (7505747)
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
