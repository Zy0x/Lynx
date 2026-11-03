#!/system/bin/sh
# Infinix Note 11S (X698 MT6781) - Early Pre-Zygote Sensor Hardware Synchronizer
# Author: ɴᴏɪʀ
# Runs BEFORE zygote forks system_server.

# Function to safely apply sensor permissions without triggering the sysfs 'subsystem' symlink trap
apply_sensor_perms() {
    # 1. Character device nodes
    chmod 0660 /dev/m_*_misc /dev/sensorlist 2>/dev/null

    # 2. Targeted sysfs attribute files only (avoids the reverse 'subsystem' symlink trap)
    chmod 0660 /sys/class/sensor/*/*active /sys/class/sensor/*/*batch \
               /sys/class/sensor/*/*flush /sys/class/sensor/*/*cali \
               /sys/class/sensor/*/*devnum /sys/class/sensor/*/*enablenodata \
               /sys/class/sensor/*/*delay 2>/dev/null

    # 3. Directory traversal permissions (UNCONDITIONALLY applied LAST)
    chmod 755 /sys/class/sensor /sys/devices/virtual/sensor 2>/dev/null
    chmod 755 /sys/devices/virtual/sensor/* 2>/dev/null

    # 4. Correct system:system ownership
    chown -R system:system /dev/m_*_misc /dev/sensorlist /sys/class/sensor /sys/devices/virtual/sensor 2>/dev/null
}

# Apply immediately upon post-fs-data entry
apply_sensor_perms

# Wait for SCP kernel driver to expose LSM6DSM hardware nodes (~2.2s after cold boot)
_i=0
while [ "$_i" -lt 60 ]; do
    apply_sensor_perms

    if [ -c /dev/m_acc_misc ] && [ -c /dev/m_als_misc ] && [ -f /sys/class/sensor/m_acc_misc/accactive ] && [ -f /sys/class/sensor/m_gyro_misc/gyroactive ]; then
        apply_sensor_perms

        # If sensor HAL was spawned prematurely before sysfs was readable, bounce it cleanly BEFORE system_server starts
        if [ -z "$(pidof system_server 2>/dev/null)" ]; then
            SENSOR_PID=$(pidof android.hardware.sensors@2.0-service-mediatek 2>/dev/null)
            if [ -n "$SENSOR_PID" ]; then
                kill -9 "$SENSOR_PID" 2>/dev/null
            fi
        fi
        exit 0
    fi

    if [ -n "$(pidof system_server 2>/dev/null)" ]; then
        apply_sensor_perms
        exit 0
    fi

    _i=$(( _i + 1 ))
    sleep 0.1
done

exit 0
