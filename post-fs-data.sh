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

# Dynamic Zygote & USAP integrity guard before Zygote initialization
if [ -x "$MODPATH/system/bin/lynxd" ]; then
    PATH="/data/adb/ksu/bin:/data/adb/ap/bin:/data/adb/magisk:/system/bin:$PATH" \
        "$MODPATH/system/bin/lynxd" zygote-guard reconcile --early >/dev/null 2>&1
fi

# Early Qualcomm Snapdragon thermal-engine conf bind-mount (zero throttle past 40°C)
if [ -f "$MODPATH/platforms/qcom/system/vendor/etc/thermal-engine.conf" ]; then
    [ -f /vendor/etc/thermal-engine.conf ] && mount --bind "$MODPATH/platforms/qcom/system/vendor/etc/thermal-engine.conf" /vendor/etc/thermal-engine.conf 2>/dev/null
    [ -f /system/vendor/etc/thermal-engine.conf ] && mount --bind "$MODPATH/platforms/qcom/system/vendor/etc/thermal-engine.conf" /system/vendor/etc/thermal-engine.conf 2>/dev/null
fi

exit 0
