#!/system/bin/sh
# ==============================================================================
# Lynx Universal - Phase 8G: RC1 Installation & App Regression Gate
# Target: Infinix Note 11S (X698, mt6781, Android 14)
# ==============================================================================

LOG_DIR="/sdcard/Debug"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/phase8g_regression_report.log"

: > "$LOG_FILE"

log() {
    echo "$@" | tee -a "$LOG_FILE"
}

log "================================================================"
log "  LYNX UNIVERSAL — PHASE 8G: RC1 INSTALLATION & APP REGRESSION"
log "  Device   : $(getprop ro.product.model) ($(getprop ro.product.device))"
log "  Platform : $(getprop ro.board.platform) (Kernel $(uname -r))"
log "  Date     : $(date)"
log "================================================================"

PASSED=0
FAILED=0

assert_test() {
    local name="$1"
    local result="$2"
    if [ "$result" -eq 0 ]; then
        log "  [PASS] $name"
        PASSED=$((PASSED + 1))
    else
        log "  [FAIL] $name"
        FAILED=$((FAILED + 1))
    fi
}

MODPATH="/data/adb/modules/Lynx"
LYNXD="$MODPATH/system/bin/lynxd"

# ── Gate 8G-1: Installation & APK Package Integrity ─────────────────
echo ""
echo "=== Gate 8G-1: Installation & APK Package Integrity ==="

# 1.1 Verify Module Tree & lynxd binary
[ -d "$MODPATH" ] && [ -x "$LYNXD" ]
assert_test "1.1 lynxd binary executable in root module directory" $?

# 1.2 Verify APK installed on system
pm list packages | grep -q "com.noir.lynx"
assert_test "1.2 LynxKernelManager package registered (com.noir.lynx)" $?

# Dump package once for safe parsing
dumpsys package com.noir.lynx > /data/local/tmp/lynx_pkg_dump.txt 2>/dev/null

# 1.3 Verify Package UID & Permissions
grep -E -q "userId=|appId=" /data/local/tmp/lynx_pkg_dump.txt
assert_test "1.3 Package assigned valid Android UID" $?

# ── Gate 8G-2: Direct Telemetry & Status Parity ─────────────────────
echo ""
echo "=== Gate 8G-2: Direct Telemetry & Status Parity ==="

# 2.1 Direct Native Telemetry Call (JSON output)
TEL_OUT=$("$LYNXD" telemetry 2>/dev/null)
echo "$TEL_OUT" | grep -q '"cpu"' && echo "$TEL_OUT" | grep -q '"temp"'
assert_test "2.1 lynxd telemetry direct call outputs valid JSON metrics" $?

# 2.2 Status Snapshot (status --json)
STAT_OUT=$("$LYNXD" status --json 2>/dev/null)
echo "$STAT_OUT" | grep -q '"daemon"' && echo "$STAT_OUT" | grep -q '"profile"' && echo "$STAT_OUT" | grep -q '"health"'
assert_test "2.2 lynxd status --json outputs complete engine schema" $?

# 2.3 Profile Transition Cycle: Balance -> Performance -> Extreme -> Auto -> Balance
"$LYNXD" profile apply balance >/dev/null 2>&1
P1=$("$LYNXD" status --json 2>/dev/null | grep -o '"active":"[^"]*"' | cut -d'"' -f4)
[ "$P1" = "balanced" ]
assert_test "2.3a Profile apply balance succeeds ($P1)" $?

"$LYNXD" profile apply performance >/dev/null 2>&1
P2=$("$LYNXD" status --json 2>/dev/null | grep -o '"active":"[^"]*"' | cut -d'"' -f4)
[ "$P2" = "performance" ]
assert_test "2.3b Profile apply performance succeeds ($P2)" $?

"$LYNXD" profile apply extreme >/dev/null 2>&1
P3=$("$LYNXD" status --json 2>/dev/null | grep -o '"active":"[^"]*"' | cut -d'"' -f4)
[ "$P3" = "extreme" ]
assert_test "2.3c Profile apply extreme succeeds ($P3)" $?

"$LYNXD" profile apply balance >/dev/null 2>&1
P4=$("$LYNXD" status --json 2>/dev/null | grep -o '"active":"[^"]*"' | cut -d'"' -f4)
[ "$P4" = "balanced" ]
assert_test "2.3d Profile restore balance succeeds ($P4)" $?

# ── Gate 8G-3: Decoupling & Background Daemon Survival ──────────────
echo ""
echo "=== Gate 8G-3: Decoupling & Background Daemon Survival ==="

# 3.1 Start Native Daemon
killall lynxd 2>/dev/null
sleep 1
"$LYNXD" daemon run >> /data/adb/lynx/lynxd.log 2>&1 &
sleep 2
DAEMON_PID=$(pidof lynxd)
[ -n "$DAEMON_PID" ]
assert_test "3.1 lynxd daemon started successfully (PID: $DAEMON_PID)" $?

# 3.2 Simulate APK Force-Kill
am force-stop com.noir.lynx 2>/dev/null
sleep 1
DAEMON_PID_AFTER=$(pidof lynxd)
[ -n "$DAEMON_PID_AFTER" ] && [ "$DAEMON_PID" = "$DAEMON_PID_AFTER" ]
assert_test "3.2 lynxd daemon survived APK force-stop (Uninterrupted PID: $DAEMON_PID_AFTER)" $?

# ── Gate 8G-4: Component Survival Audit (R8 Safety) ─────────────────
echo ""
echo "=== Gate 8G-4: Component Survival Audit (R8 Safety) ==="

BASE_APK=$(pm path com.noir.lynx | head -n 1 | cut -d: -f2)

# 4.1 LynxFloatingHudService survived R8
unzip -p "$BASE_APK" classes.dex | grep -a -q "LynxFloatingHudService"
assert_test "4.1 LynxFloatingHudService preserved across R8 optimization" $?

# 4.2 LynxAppAutomationService survived R8
unzip -p "$BASE_APK" classes.dex | grep -a -q "LynxAppAutomationService"
assert_test "4.2 LynxAppAutomationService preserved across R8 optimization" $?

# 4.3 LynxThermalGuardService survived R8
unzip -p "$BASE_APK" classes.dex | grep -a -q "LynxThermalGuardService"
assert_test "4.3 LynxThermalGuardService preserved across R8 optimization" $?

# 4.4 Quick Settings Tiles survived (Profile, HUD, HBM)
grep -q "LynxProfileTileService" /data/local/tmp/lynx_pkg_dump.txt && \
grep -q "LynxHudTileService" /data/local/tmp/lynx_pkg_dump.txt && \
grep -q "LynxHbmTileService" /data/local/tmp/lynx_pkg_dump.txt
assert_test "4.4 Quick Settings Tiles registered (Profile, HUD, HBM)" $?

# 4.5 Broadcast Receivers survived (Boot & Power)
grep -q "LynxBootReceiver" /data/local/tmp/lynx_pkg_dump.txt && \
grep -q "LynxPowerReceiver" /data/local/tmp/lynx_pkg_dump.txt
assert_test "4.5 Broadcast Receivers registered (Boot & Power)" $?

# ── Summary ─────────────────────────────────────────────────────────
log ""
log "================================================================"
log "  PHASE 8G AUDIT COMPLETE: $PASSED Passed, $FAILED Failed"
log "  Report logged to: $LOG_FILE"
log "================================================================"

if [ "$FAILED" -eq 0 ]; then
    exit 0
else
    exit 1
fi
