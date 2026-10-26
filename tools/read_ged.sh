#!/system/bin/sh
for node in /sys/kernel/ged/hal/*; do
    echo "$node: $(cat $node 2>/dev/null)"
done
