#!/system/bin/sh
# Lynx Universal - Early Pre-Zygote Boot Hook (post-fs-data)
# Runs BEFORE zygote forks system_server.

# MediaTek SCP SensorHub Pre-Zygote Hardware Synchronizer:
# In MTK Helio G96, kernel SCP initializes asynchronously (~2.2s after cold boot).
# To guarantee 100% natural sensor operation without lockscreen crashes:
# 1. Wait for kernel SCP driver to expose character device nodes.
# 2. Set ownership (system:system) and permissions (0660) immediately.
# 3. If vendor.sensors-hal-2-0 spawned before nodes were ready, restart it
#    BEFORE system_server starts, ensuring all 8 hardware sensor FDs are open.
if [ -d /proc/ppm ] || [ -d /proc/ged ] || [ -c /dev/ged ] || [ -f /sys/firmware/devicetree/base/compatible ] && grep -qi "mediatek" /sys/firmware/devicetree/base/compatible 2>/dev/null; then
    _i=0
    while [ "$_i" -lt 35 ]; do
        # Absolute safety guard: NEVER touch HAL once system_server exists
        if [ -n "$(pidof system_server 2>/dev/null)" ]; then
            exit 0
        fi

        if [ -c /dev/m_acc_misc ] && [ -c /dev/m_als_misc ]; then
            # Ensure permissions on all physical sensor nodes
            chmod 0660 /dev/m_*_misc /dev/sensorlist 2>/dev/null
            chown system:system /dev/m_*_misc /dev/sensorlist 2>/dev/null

            # Wake up sysfs controls
            for node in /sys/class/sensor/m_acc_misc/accactive /sys/class/sensor/m_gyro_misc/gyroactive /sys/class/sensor/m_als_misc/alsactive /sys/class/sensor/m_ps_misc/psactive; do
                [ -f "$node" ] && echo 100 > "$node" 2>/dev/null
            done

            SENSOR_PID=$(pidof android.hardware.sensors@2.0-service-mediatek 2>/dev/null)
            if [ -n "$SENSOR_PID" ]; then
                # Check if HAL already bound real hardware
                if ! ls -l "/proc/$SENSOR_PID/fd" 2>/dev/null | grep -q "/dev/m_acc_misc"; then
                    # HAL started too early with 0 sensors; restart it BEFORE system_server starts!
                    if [ -z "$(pidof system_server 2>/dev/null)" ]; then
                        kill -9 "$SENSOR_PID" 2>/dev/null
                    fi
                fi
            fi
            exit 0
        fi
        _i=$(( _i + 1 ))
        sleep 0.1
    done
fi

exit 0
