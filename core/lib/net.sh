#!/system/bin/sh
# Lynx Universal - Legacy Net CLI Compatibility Wrapper
# Delegates to consolidated core/lib/network.sh
MODPATH="/data/adb/modules/Lynx"
[ -d "$MODPATH" ] || MODPATH="${0%/*/*}"
. "$MODPATH/core/lib/network.sh"