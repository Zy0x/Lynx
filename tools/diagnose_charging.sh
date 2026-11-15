#!/system/bin/sh
echo "=== BATTERY PARAMETERS ===" > /data/local/tmp/diag.txt
for f in /sys/class/power_supply/battery/*; do
    if [ -f "$f" ]; then
        echo "$(basename "$f"): $(cat "$f" 2>/dev/null)" >> /data/local/tmp/diag.txt
    fi
done

echo "=== USB PARAMETERS ===" >> /data/local/tmp/diag.txt
for f in /sys/class/power_supply/usb/*; do
    if [ -f "$f" ]; then
        echo "$(basename "$f"): $(cat "$f" 2>/dev/null)" >> /data/local/tmp/diag.txt
    fi
done

echo "=== MAIN / BMS PARAMETERS ===" >> /data/local/tmp/diag.txt
for f in /sys/class/power_supply/main/*; do
    if [ -f "$f" ]; then
        echo "main/$(basename "$f"): $(cat "$f" 2>/dev/null)" >> /data/local/tmp/diag.txt
    fi
done

echo "=== THERMAL NODES ===" >> /data/local/tmp/diag.txt
echo "temp_state: $(cat /sys/class/thermal/thermal_message/temp_state 2>/dev/null)" >> /data/local/tmp/diag.txt
echo "boost: $(cat /sys/class/thermal/thermal_message/boost 2>/dev/null)" >> /data/local/tmp/diag.txt

echo "=== DMESG CHG LOGS ===" >> /data/local/tmp/diag.txt
dmesg 2>/dev/null | grep -iE "smb|chg|pm660|hvdcp|step|fcc" | tail -n 30 >> /data/local/tmp/diag.txt
