#!/system/bin/sh
for i in 53 54 55 56; do
    echo 0 > /sys/class/thermal/cooling_device${i}/cur_state
done
for i in 53 54 55 56; do
    echo "dev$i: $(cat /sys/class/thermal/cooling_device${i}/type): cur=$(cat /sys/class/thermal/cooling_device${i}/cur_state)"
done
