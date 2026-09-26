#!/system/bin/sh
# Lynx Universal - Cache Cleaner Wrapper
# Delegates to core/CCleaner.sh for safe memory compaction and log purging

MODPATH="/data/adb/modules/Lynx"
[ -d "$MODPATH" ] || MODPATH="${0%/*/*}"

help_cache() {
    echo "Usage: Lxcore -cache [apply|help]"
    echo ""
    echo "Executes gentle memory compaction, tombstones purging, and fstrim."
}

main_cache() {
    if [ -f "$MODPATH/core/CCleaner.sh" ]; then
        sh "$MODPATH/core/CCleaner.sh"
    else
        echo "1" > /proc/sys/vm/compact_memory 2>/dev/null
    fi
}