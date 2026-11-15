#!/system/bin/sh
echo "FCC_BAT: $(cat /sys/class/power_supply/battery/constant_charge_current_max 2>/dev/null)" > /data/local/tmp/quick_diag.txt
echo "USB_CUR: $(cat /sys/class/power_supply/usb/current_max 2>/dev/null)" >> /data/local/tmp/quick_diag.txt
echo "USB_VOLT: $(cat /sys/class/power_supply/usb/voltage_now 2>/dev/null)" >> /data/local/tmp/quick_diag.txt
echo "BATT_CUR: $(cat /sys/class/power_supply/battery/current_now 2>/dev/null)" >> /data/local/tmp/quick_diag.txt
echo "INPUT_SETTLED: $(cat /sys/class/power_supply/usb/input_current_settled 2>/dev/null)" >> /data/local/tmp/quick_diag.txt
echo "BOOST: $(cat /sys/class/thermal/thermal_message/boost 2>/dev/null)" >> /data/local/tmp/quick_diag.txt
echo "TEMP_STATE: $(cat /sys/class/thermal/thermal_message/temp_state 2>/dev/null)" >> /data/local/tmp/quick_diag.txt
echo "AICL: $(cat /sys/class/power_supply/battery/rerun_aicl 2>/dev/null)" >> /data/local/tmp/quick_diag.txt
