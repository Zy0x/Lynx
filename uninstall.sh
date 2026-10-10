#!/system/bin/sh
# Lynx Universal - Complete System Uninstallation & Restoration (100% Native Rust Core)
MODPATH=${0%/*}
[ -d "$MODPATH" ] || MODPATH="/data/adb/modules/Lynx"
exec "$MODPATH/system/bin/lynxd" uninstall
