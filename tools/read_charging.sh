#!/system/bin/sh
echo "=== BATTERY NODES ==="
for f in /sys/class/power_supply/battery/*; do
    if [ -f "$f" ]; then
        echo "$f: $(cat "$f" 2>/dev/null)"
    fi
done

echo "=== CHARGER PLATFORM NODES ==="
for f in /sys/devices/platform/charger/*; do
    if [ -f "$f" ]; then
        echo "$f: $(cat "$f" 2>/dev/null)"
    fi
done

echo "=== USB NODES ==="
for f in /sys/class/power_supply/usb/*; do
    if [ -f "$f" ]; then
        echo "$f: $(cat "$f" 2>/dev/null)"
    fi
done

echo "=== THERMAL NODES FOR CHARGING ==="
cat /proc/driver/thermal/clabcct 2>/dev/null
cat /proc/driver/thermal/clabcct_lcmoff 2>/dev/null
cat /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug 2>/dev/null
cat /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null
cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger 2>/dev/null
cat /sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger 2>/dev/null
