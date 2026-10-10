#!/system/bin/sh
# ==============================================================================
# Lynx Universal v7.0.0-RC1 - Field Trial Telemetry & Log Collector
# Non-intrusive read-only diagnostic packager for Phase 8 Field Testers
# ==============================================================================

OUT_DIR="/sdcard/Debug"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
TAR_FILE="$OUT_DIR/lynx_trial_${TIMESTAMP}.tar.gz"
WORK_DIR="/data/local/tmp/lynx_trial_${TIMESTAMP}"

mkdir -p "$OUT_DIR"
mkdir -p "$WORK_DIR"

echo "=================================================="
echo "  Lynx Universal Field Trial Collector"
echo "  Timestamp: $TIMESTAMP"
echo "=================================================="

# 1. Device Metadata
{
    echo "--- DEVICE INFO ---"
    getprop ro.product.model
    getprop ro.product.device
    getprop ro.board.platform
    getprop ro.soc.manufacturer
    getprop ro.build.version.release
    uname -a
    uptime
} > "$WORK_DIR/device_info.txt" 2>/dev/null

# 2. Module Config & State
[ -f "/data/adb/modules/Lynx/config.json" ] && cp -af "/data/adb/modules/Lynx/config.json" "$WORK_DIR/"
[ -f "/data/adb/modules/Lynx/module.prop" ] && cp -af "/data/adb/modules/Lynx/module.prop" "$WORK_DIR/"

# 3. Native & Boot Logs
[ -f "/storage/emulated/0/Lynx/Lynx.log" ] && cp -af "/storage/emulated/0/Lynx/Lynx.log" "$WORK_DIR/Lynx_boot.log"
[ -f "/data/adb/lynx/lynxd.log" ] && cp -af "/data/adb/lynx/lynxd.log" "$WORK_DIR/lynxd.log"
[ -f "/data/adb/lynx/transaction.log" ] && cp -af "/data/adb/lynx/transaction.log" "$WORK_DIR/transaction.log"
[ -f "/data/adb/lynx/active_profile.json" ] && cp -af "/data/adb/lynx/active_profile.json" "$WORK_DIR/active_profile.json"

# 4. Live Telemetry & CLI Status
if [ -x "/data/adb/modules/Lynx/system/bin/lynxd" ]; then
    /data/adb/modules/Lynx/system/bin/lynxd status --json > "$WORK_DIR/lynxd_status.json" 2>/dev/null
    /data/adb/modules/Lynx/system/bin/lynxd telemetry > "$WORK_DIR/lynxd_telemetry.json" 2>/dev/null
fi

if [ -x "/data/adb/modules/Lynx/system/bin/Lxcore" ]; then
    /data/adb/modules/Lynx/system/bin/Lxcore engine status > "$WORK_DIR/engine_status.txt" 2>/dev/null
fi

# 5. Package tarball
cd "$WORK_DIR"
tar -czf "$TAR_FILE" ./* 2>/dev/null
cd /
rm -rf "$WORK_DIR"

if [ -f "$TAR_FILE" ]; then
    echo "  [OK] Field trial diagnostic archive created:"
    echo "       $TAR_FILE"
    ls -lh "$TAR_FILE"
else
    echo "  [!] Failed to generate archive."
fi
echo "=================================================="
