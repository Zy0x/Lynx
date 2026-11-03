#!/system/bin/sh
# Lynx Universal - Early Boot Stage (post-fs-data)
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)

MODPATH=${0%/*}
[ -d "$MODPATH" ] || MODPATH="/data/adb/modules/Lynx"

# Ensure executable permissions for core binaries
[ -f "$MODPATH/system/bin/busybox" ] && chmod 755 "$MODPATH/system/bin/busybox" 2>/dev/null
[ -f "$MODPATH/system/bin/Lxcore" ] && chmod 755 "$MODPATH/system/bin/Lxcore" 2>/dev/null
[ -f "$MODPATH/system/bin/lynx" ] && chmod 755 "$MODPATH/system/bin/lynx" 2>/dev/null
[ -f "$MODPATH/system/bin/lynxd" ] && chmod 755 "$MODPATH/system/bin/lynxd" 2>/dev/null

exit 0
