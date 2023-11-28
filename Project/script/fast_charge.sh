# Force Fast Charging
chmod 777 /sys/class/power_supply/bms/*
chmod 777 /sys/class/power_supply/usb/*
CurrentLimit=max
if ((CurrentLimit >= 3500000)); then
    ProtectCharge=$((CurrentLimit - 700000))
elif ((CurrentLimit > 2400000)); then
    ProtectCharge=$((CurrentLimit - 500000))
else
    ProtectCharge=$((CurrentLimit - 200000))
fi
while true; do
    battery_status=$(cat /sys/class/power_supply/battery/status)
    battery_level=$(cat /sys/class/power_supply/battery/capacity)
    if [ "$battery_status" = "Full" ] && [ "$battery_level" = "100" ]; then
        echo '50' > /sys/class/power_supply/bms/temp_cold
        echo '150' > /sys/class/power_supply/bms/temp_cool
        echo '510' > /sys/class/power_supply/bms/temp_hot
        echo '490' > /sys/class/power_supply/bms/temp_warm
    else
        echo '120' > /sys/class/power_supply/bms/temp_cool
        echo '600' > /sys/class/power_supply/bms/temp_hot
        echo '580' > /sys/class/power_supply/bms/temp_warm
        echo '50' > /sys/class/power_supply/bms/temp_cold
        echo "$CurrentLimit" > /sys/class/power_supply/usb/pd_current_max
        echo '9000000' > /sys/class/power_supply/usb/voltage_max
        echo '9000000' > /sys/class/power_supply/usb/voltage_min
        echo "$CurrentLimit" > /sys/class/power_supply/battery/max_charge_current
        echo "$CurrentLimit" > /sys/class/power_supply/battery/constant_charge_current
        echo "$CurrentLimit" > /sys/class/power_supply/battery/constant_charge_current_max
        echo '1' > /sys/class/power_supply/battery/lrc_enable
        echo '0' > /sys/class/power_supply/battery/lrc_not_startup
        echo '100' > /sys/class/power_supply/battery/lrc_socmax
        echo '90' > /sys/class/power_supply/battery/lrc_socmin
        echo "$CurrentLimit" > /sys/class/power_supply/usb/current_max
        echo "$CurrentLimit" > /sys/class/power_supply/usb/hw_current_max
        echo "$CurrentLimit" > /sys/class/power_supply/usb/input_current_settled
        echo "$ProtectCharge" > /sys/class/power_supply/usb/current_max
    fi
    sleep 1
done &