#!/system/bin/sh
for cd in /sys/class/thermal/cooling_device*; do
    name=$(basename "$cd")
    type=$(cat "$cd/type" 2>/dev/null)
    cur=$(cat "$cd/cur_state" 2>/dev/null)
    max=$(cat "$cd/max_state" 2>/dev/null)
    echo "$name: $type (cur=$cur, max=$max)"
done
echo "=== TZ20 (quiet-therm-step) TRIPS ==="
for tp in /sys/class/thermal/thermal_zone20/trip_point_*; do
    echo "$(basename $tp): $(cat $tp 2>/dev/null)"
done
echo "=== TZ29 (battery) TRIPS ==="
for tp in /sys/class/thermal/thermal_zone29/trip_point_*; do
    echo "$(basename $tp): $(cat $tp 2>/dev/null)"
done
