#!/system/bin/sh
for tz in /sys/class/thermal/thermal_zone*; do
    name=$(basename "$tz")
    type=$(cat "$tz/type" 2>/dev/null)
    temp=$(cat "$tz/temp" 2>/dev/null)
    echo "$name: $type ($temp)"
done
