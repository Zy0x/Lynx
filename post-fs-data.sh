#!/system/bin/sh
# Lynx Universal - Early Pre-Zygote Boot Hook (post-fs-data)
# Runs BEFORE zygote forks system_server.

# MediaTek SCP SensorHub Pre-Zygote Hardware Synchronizer:
# In MTK Helio G96, kernel SCP initializes asynchronously (~2.2s after cold boot).
# CRITICAL SYMLINK TRAP RESOLUTION:
#   Sysfs directory /sys/devices/virtual/sensor/* contains a 'subsystem' symlink
#   pointing back to /sys/class/sensor. If a wildcard 'chmod 0660 .../*/*' is used,
#   chmod follows 'subsystem' and resets /sys/class/sensor back to 0660, stripping
#   the execute bit (+x) and breaking UID 1000 access!
#   SOLUTION: Specifically target attribute files (*active, *batch, etc.) and
#   apply chmod 755 to directories LAST and unconditionally!

apply_sensor_perms() {
    # 1. Character device nodes
    chmod 0660 /dev/m_*_misc /dev/sensorlist 2>/dev/null

    # 2. Specific sysfs attribute files only (DO NOT use /*/* which hits 'subsystem' symlink)
    chmod 0660 /sys/class/sensor/*/*active /sys/class/sensor/*/*batch \
               /sys/class/sensor/*/*flush /sys/class/sensor/*/*cali \
               /sys/class/sensor/*/*devnum /sys/class/sensor/*/*enablenodata \
               /sys/class/sensor/*/*delay 2>/dev/null

    # 3. Traversal permissions on directories (UNCONDITIONALLY applied)
    chmod 755 /sys/class/sensor /sys/devices/virtual/sensor 2>/dev/null
    chmod 755 /sys/devices/virtual/sensor/* 2>/dev/null

    # 4. Ownership to system:system
    chown -R system:system /dev/m_*_misc /dev/sensorlist /sys/class/sensor /sys/devices/virtual/sensor 2>/dev/null
}

# Run immediately at earliest hook point
apply_sensor_perms

IS_MTK=false
if [ -d /proc/ppm ] || [ -d /proc/ged ] || [ -c /dev/ged ]; then
    IS_MTK=true
elif [ -f /sys/firmware/devicetree/base/compatible ] && grep -qi "mediatek" /sys/firmware/devicetree/base/compatible 2>/dev/null; then
    IS_MTK=true
fi

if [ "$IS_MTK" = "true" ]; then
    _i=0
    while [ "$_i" -lt 60 ]; do
        apply_sensor_perms

        if [ -c /dev/m_acc_misc ] && [ -c /dev/m_als_misc ] && [ -f /sys/class/sensor/m_acc_misc/accactive ] && [ -f /sys/class/sensor/m_gyro_misc/gyroactive ]; then
            # Re-apply perms to guarantee newly created nodes are accessible
            apply_sensor_perms

            # Restart HAL once sysfs nodes and dev nodes are fully prepared
            # Ensures HAL constructors re-run and cleanly read accactive (1000) and gyroactive (7505747)
            if [ -z "$(pidof system_server 2>/dev/null)" ]; then
                SENSOR_PID=$(pidof android.hardware.sensors@2.0-service-mediatek 2>/dev/null)
                if [ -n "$SENSOR_PID" ]; then
                    kill -9 "$SENSOR_PID" 2>/dev/null
                fi
            fi
            exit 0
        fi

        # If system_server already appeared, apply perms and exit safely
        if [ -n "$(pidof system_server 2>/dev/null)" ]; then
            apply_sensor_perms
            exit 0
        fi

        _i=$(( _i + 1 ))
        sleep 0.1
    done
fi

exit 0
