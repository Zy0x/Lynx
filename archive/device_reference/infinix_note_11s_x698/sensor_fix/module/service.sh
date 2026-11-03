#!/system/bin/sh
# Infinix Note 11S (X698 MT6781) - Sensor Watchdog & Late-Boot Safety Guard
# Author: ɴᴏɪʀ

(
    while [ "$(getprop sys.boot_completed)" != "1" ]; do
        sleep 2
    done
    sleep 2

    # MediaTek Sensor Sysfs Traversal & Permission Safety Guard
    chmod 0660 /dev/m_*_misc /dev/sensorlist 2>/dev/null
    chmod 0660 /sys/class/sensor/*/*active /sys/class/sensor/*/*batch \
               /sys/class/sensor/*/*flush /sys/class/sensor/*/*cali \
               /sys/class/sensor/*/*devnum /sys/class/sensor/*/*enablenodata \
               /sys/class/sensor/*/*delay 2>/dev/null
    chmod 755 /sys/class/sensor /sys/devices/virtual/sensor 2>/dev/null
    chmod 755 /sys/devices/virtual/sensor/* 2>/dev/null
    chown -R system:system /dev/m_*_misc /dev/sensorlist /sys/class/sensor /sys/devices/virtual/sensor 2>/dev/null
) &
