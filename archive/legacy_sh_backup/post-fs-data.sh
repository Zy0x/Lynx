#!/system/bin/sh
# Lynx Universal - Early Boot Stage (post-fs-data)
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)

MODPATH=${0%/*}
[ -d "$MODPATH" ] || MODPATH="/data/adb/modules/Lynx"

# 1. Ensure executable permissions for core binaries
[ -f "$MODPATH/system/bin/busybox" ] && chmod 755 "$MODPATH/system/bin/busybox"
[ -f "$MODPATH/system/bin/Lxcore" ] && chmod 755 "$MODPATH/system/bin/Lxcore"
[ -f "$MODPATH/system/bin/lynx" ] && chmod 755 "$MODPATH/system/bin/lynx"

# 2. SELinux policy rules reload confirmation (handled automatically by Magisk/KernelSU)
# Modern Treble HAL graphics initialization is preserved without legacy egl.cfg override.
