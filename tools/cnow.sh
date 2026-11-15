#!/system/bin/sh
echo "CUR: $(cat /sys/class/power_supply/battery/current_now 2>/dev/null)" > /data/local/tmp/cnow.txt
echo "VOLT: $(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null)" >> /data/local/tmp/cnow.txt
echo "FCC: $(cat /sys/class/power_supply/battery/constant_charge_current_max 2>/dev/null)" >> /data/local/tmp/cnow.txt
echo "STEP: $(cat /sys/class/power_supply/battery/step_charging_enabled 2>/dev/null)" >> /data/local/tmp/cnow.txt
echo "AICL: $(cat /sys/class/power_supply/battery/rerun_aicl 2>/dev/null)" >> /data/local/tmp/cnow.txt
echo "STATUS: $(cat /sys/class/power_supply/battery/status 2>/dev/null)" >> /data/local/tmp/cnow.txt
echo "CHARGING_ENABLED: $(cat /sys/class/power_supply/battery/charging_enabled 2>/dev/null)" >> /data/local/tmp/cnow.txt
