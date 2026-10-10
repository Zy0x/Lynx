#!/system/bin/sh
mkdir -p /storage/emulated/0/Debug 2>/dev/null
exec > /storage/emulated/0/Debug/charging_direct_analysis.txt 2>&1

echo "=== KALLSYMS DV2 SYMBOLS ==="
grep -E "dv2|pca_|tpcb" /proc/kallsyms | head -n 40

echo "=== DV2_DEBUG EXPERIMENTS ==="
echo "testing dv2_debug echo..."
echo "help" > /sys/devices/platform/pca_dv2_algo/dv2_debug 2>&1
echo "dv2_debug after help: $(cat /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null)"
echo "1" > /sys/devices/platform/pca_dv2_algo/dv2_debug 2>&1
echo "dv2_debug after 1: $(cat /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null)"

echo "=== DMESG FROM DV2_DEBUG ==="
dmesg | tail -n 20

echo "=== SEARCH TRAN_GAME_MODE IN KERNEL SYMBOLS ==="
grep -i "tran_game" /proc/kallsyms

echo "=== SEARCH TRAN_BATTERY NODES & VALUES ==="
for f in /sys/devices/platform/odm/odm:tran_battery/*; do
    [ -f "$f" ] && echo "$(basename "$f"): $(cat "$f" 2>/dev/null)"
done

echo "=== SEARCH ALL CHARGER PLATFORM NODES ==="
for f in /sys/devices/platform/charger/*; do
    [ -f "$f" ] && echo "$(basename "$f"): $(cat "$f" 2>/dev/null)"
done

echo "=== CHECK CURRENT VALUES ==="
echo "current_now: $(cat /sys/class/power_supply/battery/current_now 2>/dev/null)"
echo "BatteryNotify: $(cat /sys/devices/platform/charger/BatteryNotify 2>/dev/null)"
echo "BN_TestMode: $(cat /sys/devices/platform/charger/BN_TestMode 2>/dev/null)"
