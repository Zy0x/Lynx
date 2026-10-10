#!/system/bin/sh
# Lynx Universal - Action Button Trigger for KernelSU & APatch (100% Native Rust Core)
MODPATH=${0%/*}
[ -d "$MODPATH" ] || MODPATH="/data/adb/modules/Lynx"
exec "$MODPATH/system/bin/lynxd" action