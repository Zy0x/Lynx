#!/system/bin/sh
# Lynx Universal - Late Start Service Router (100% Native Rust Core)
MODPATH=${0%/*}
[ -d "$MODPATH" ] || MODPATH="/data/adb/modules/Lynx"

# VMA Limit Expansion Shield (RAM-Proportional Anti-Scudo NO MEMORY Abort)
if [ -f /proc/sys/vm/max_map_count ]; then
    TOTAL_RAM_KB=$(grep MemTotal /proc/meminfo | awk '{print $2}')
    if [ -n "$TOTAL_RAM_KB" ] && [ "$TOTAL_RAM_KB" -gt 6000000 ]; then
        echo 1048576 > /proc/sys/vm/max_map_count 2>/dev/null
    else
        echo 524288 > /proc/sys/vm/max_map_count 2>/dev/null
    fi
fi

exec "$MODPATH/system/bin/lynxd" boot
