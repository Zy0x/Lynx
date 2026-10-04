#!/system/bin/sh
mkdir -p /storage/emulated/0/Debug 2>/dev/null
exec > /storage/emulated/0/Debug/charging_diag.txt 2>&1

echo "=== TIMESTAMP ==="
date

echo "=== DUMPSYS BATTERY ==="
dumpsys battery

echo "=== SCREEN STATE ==="
dumpsys power | grep -iE 'Display Power|mHoldingDisplaySuspendBlocker|mWakefulness'

echo "=== POWER SUPPLY BATTERY UEVENT ==="
cat /sys/class/power_supply/battery/uevent

echo "=== POWER SUPPLY USB UEVENT ==="
cat /sys/class/power_supply/usb/uevent

echo "=== CHARGER PLATFORM NODES ==="
for f in /sys/devices/platform/charger/*; do
    [ -f "$f" ] && echo "$(basename "$f"): $(cat "$f" 2>/dev/null)"
done

echo "=== ODM TRAN BATTERY / CHARGER ==="
find /sys/devices/platform/odm/ -type f 2>/dev/null | while read f; do
    echo "$f: $(cat "$f" 2>/dev/null)"
done

echo "=== RT9759 REGISTERS ==="
for r in /sys/bus/i2c/drivers/rt9759/*; do
    if [ -d "$r" ]; then
        echo "Dir: $r"
        for node in Ibus Vbus rfc_dcp_ta chip_vendor reg_dump status; do
            [ -e "$r/$node" ] && echo "$node: $(cat "$r/$node" 2>/dev/null)"
        done
    fi
done

echo "=== THERMAL ZONES ==="
for tz in /sys/class/thermal/thermal_zone*; do
    [ -d "$tz" ] || continue
    type=$(cat "$tz/type" 2>/dev/null)
    temp=$(cat "$tz/temp" 2>/dev/null)
    mode=$(cat "$tz/mode" 2>/dev/null)
    echo "$(basename $tz) [$type]: temp=$temp mode=$mode"
done

echo "=== COOLING DEVICES ==="
for cd in /sys/class/thermal/cooling_device*; do
    [ -d "$cd" ] || continue
    type=$(cat "$cd/type" 2>/dev/null)
    cur=$(cat "$cd/cur_state" 2>/dev/null)
    max=$(cat "$cd/max_state" 2>/dev/null)
    echo "$(basename $cd) [$type]: cur=$cur / max=$max"
done

echo "=== RUNNING PROCESSES (CHARGE & THERMAL) ==="
ps -ef | grep -iE 'charge|batt|thermal' | grep -v grep

echo "=== CHARGING PROPS ==="
getprop | grep -iE 'charge|batt|thermal|tran'

echo "=== KERNEL DMESG CHARGER LOGS (LAST 100) ==="
dmesg | grep -iE 'charger|charge|pump|rt9759|pe40|pe20|sc_|dv2|ita_lmt|tran_game|fastchg' | tail -n 100

