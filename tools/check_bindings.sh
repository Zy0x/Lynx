#!/system/bin/sh
for tz in /sys/class/thermal/thermal_zone*; do
    for cdev_link in "$tz"/cdev*; do
        if [ -e "$cdev_link" ]; then
            target=$(readlink -f "$cdev_link" 2>/dev/null)
            if echo "$target" | grep -q "cooling_device3"; then
                echo "$(basename $tz) binds to cooling_device3 via $(basename $cdev_link)"
            fi
        fi
    done
done
echo "=== THERMAL ENGINE PROCESSES ==="
ps -ef | grep -iE "thermal|thermald"
