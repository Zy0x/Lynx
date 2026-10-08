#!/system/bin/sh
for f in /sys/devices/platform/charger/*; do
    if [ -w "$f" ]; then
        echo "$f: $(cat $f 2>/dev/null)"
    fi
done
