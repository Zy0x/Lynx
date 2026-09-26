#!/system/bin/sh
# Lynx Universal - Action Button Trigger for KernelSU & APatch
# Pure POSIX /system/bin/sh compliance (Android Toybox/ash)
# Replaces dangerous legacy dalvik wiping with safe CCleaner & Memory Compactor

# 1. Root verification
if [ "$(id -u 2>/dev/null)" -ne 0 ]; then
    echo "❌ Error: Root access required (uid=0)."
    exit 1
fi

MODDIR="${0%/*}"
[ -d "$MODDIR" ] || MODDIR="/data/adb/modules/Lynx"

echo "=========================================="
echo "      LYNX DEITY — MAINTENANCE ACTION     "
echo "=========================================="
echo ""

# 2. Storage & Memory Maintenance
if [ -f "$MODDIR/core/CCleaner.sh" ]; then
    echo "🧹 Executing Lynx Storage & Memory Maintenance..."
    sh "$MODDIR/core/CCleaner.sh"
    echo "✅ Memory compaction & storage trim complete!"
else
    echo "🧹 Compacting physical memory..."
    [ -e "/proc/sys/vm/compact_memory" ] && echo 1 > /proc/sys/vm/compact_memory
    
    echo "🗑️ Clearing crash dumps & tombstones..."
    rm -rf /data/tombstones/* /data/system/dropbox/* /data/anr/* 2>/dev/null
    
    echo "💾 Running fstrim on /data..."
    fstrim -v /data 2>/dev/null
    echo "✅ Quick cleanup complete!"
fi

echo ""
echo "=========================================="
echo "    ✨ System Optimization Finished!      "
echo "=========================================="
exit 0