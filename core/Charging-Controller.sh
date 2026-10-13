#!/system/bin/sh
# Lynx Universal - Charging Controller Facade (Delegates 100% to lynxd)
MODPATH="/data/adb/modules/Lynx"
[ -d "$MODPATH" ] || MODPATH="${0%/*/*}"
LYNXD="$MODPATH/system/bin/lynxd"
[ -x "$LYNXD" ] || LYNXD="/system/bin/lynxd"

case "$1" in
    apply)
        exec "$LYNXD" charging apply 2>/dev/null
        ;;
    status)
        exec "$LYNXD" charging status 2>/dev/null
        ;;
    standard)
        exec "$LYNXD" charging mode standard 2>/dev/null
        ;;
    cool)
        exec "$LYNXD" charging mode cool 2000 2>/dev/null
        ;;
    hyper|extreme)
        exec "$LYNXD" charging mode hyper 2>/dev/null
        ;;
    custom)
        exec "$LYNXD" charging mode custom "$2" 2>/dev/null
        ;;
    *)
        exec "$LYNXD" charging tick 2>/dev/null
        ;;
esac
