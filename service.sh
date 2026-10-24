#!/system/bin/sh
# Lynx Universal - Late Start Service Router (100% Native Rust Core)
MODPATH=${0%/*}
[ -d "$MODPATH" ] || MODPATH="/data/adb/modules/Lynx"

# VMA Limit Expansion Shield (Anti-Scudo NO MEMORY Abort & Framework Restart Prevention)
if [ -f /proc/sys/vm/max_map_count ]; then
    echo 524288 > /proc/sys/vm/max_map_count 2>/dev/null
fi

exec "$MODPATH/system/bin/lynxd" boot
