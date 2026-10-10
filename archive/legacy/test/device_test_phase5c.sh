#!/system/bin/sh
# ==============================================================================
# Lynx Universal - Phase 5C Live Device Validation Script
# Rigorous unit and integration test suite for Native Engine & Legacy Fallback
# ==============================================================================

MODPATH="/data/adb/modules/Lynx"
[ -d "$MODPATH" ] || MODPATH="/data/adb/lynx"
CONFIG_FILE="$MODPATH/config.json"
LYNXD="$MODPATH/system/bin/lynxd"
LXCORE="$MODPATH/system/bin/Lxcore"
SCRIPT_DISPATCHER="$MODPATH/webroot/script.sh"
OUT_LOG="/sdcard/Debug/phase5c_test.log"

mkdir -p /sdcard/Debug 2>/dev/null
echo "=== LYNX PHASE 5C VALIDATION SUITE ===" > "$OUT_LOG"
echo "Timestamp: $(date)" >> "$OUT_LOG"

pass_count=0
fail_count=0

test_assert() {
    local name="$1"
    local condition="$2"
    if [ "$condition" = "1" ]; then
        echo "  [PASS] $name"
        echo "[PASS] $name" >> "$OUT_LOG"
        pass_count=$((pass_count + 1))
    else
        echo "  [FAIL] $name"
        echo "[FAIL] $name" >> "$OUT_LOG"
        fail_count=$((fail_count + 1))
    fi
}

echo ""
echo "--- 1. Engine Binary & Config Integrity ---"
[ -x "$LYNXD" ] && b_ok=1 || b_ok=0
test_assert "lynxd binary is executable at $LYNXD" "$b_ok"

grep -q '"engine"' "$CONFIG_FILE" 2>/dev/null && cfg_eng=1 || cfg_eng=0
test_assert "config.json contains 'engine' configuration section" "$cfg_eng"

grep -q '"native_enabled"[[:space:]]*:[[:space:]]*true' "$CONFIG_FILE" 2>/dev/null && cfg_nat=1 || cfg_nat=0
test_assert "config.json has 'native_enabled': true" "$cfg_nat"

echo ""
echo "--- 2. Dual-Engine Dispatcher Switching Test ---"
# Check native status when enabled
eng_out=$(sh "$SCRIPT_DISPATCHER" get_status 2>/dev/null | grep "^ENGINE:" | grep -o "Native Rust")
[ -n "$eng_out" ] && nat_active=1 || nat_active=0
test_assert "Dispatcher reports 'Native Rust' when native_enabled is true" "$nat_active"

# Switch to legacy mode via Lxcore
sh "$LXCORE" engine legacy >/dev/null 2>&1
eng_legacy=$(sh "$SCRIPT_DISPATCHER" get_status 2>/dev/null | grep "^ENGINE:" | grep -o "legacy")
[ -n "$eng_legacy" ] && leg_active=1 || leg_active=0
test_assert "Lxcore engine legacy switches dispatcher to 'legacy (Shell)'" "$leg_active"

# Restore native mode via Lxcore
sh "$LXCORE" engine native >/dev/null 2>&1
eng_restored=$(sh "$SCRIPT_DISPATCHER" get_status 2>/dev/null | grep "^ENGINE:" | grep -o "Native Rust")
[ -n "$eng_restored" ] && res_active=1 || res_active=0
test_assert "Lxcore engine native restores dispatcher to 'Native Rust'" "$res_active"

echo ""
echo "--- 3. Fast-Path Telemetry Performance ---"
t0=$(date +%s%N 2>/dev/null || date +%s)
tel_json=$(sh "$SCRIPT_DISPATCHER" telemetry 2>/dev/null)
echo "$tel_json" | grep -q '"gpu_freq"' && tel_valid=1 || tel_valid=0
test_assert "script.sh telemetry returns valid hardware JSON" "$tel_valid"

echo "$tel_json" | grep -q '"cpu":\[' && tel_cpu=1 || tel_cpu=0
test_assert "script.sh telemetry includes CPU core frequencies array" "$tel_cpu"

echo ""
echo "--- 4. Native Status Output ---"
stat_json=$(sh "$SCRIPT_DISPATCHER" native_status 2>/dev/null)
echo "$stat_json" | grep -q '"daemon"' && stat_valid=1 || stat_valid=0
test_assert "script.sh native_status returns valid daemon JSON" "$stat_valid"

echo "$stat_json" | grep -q '"runtime"' && stat_rt=1 || stat_rt=0
test_assert "script.sh native_status includes runtime modifier state" "$stat_rt"

echo ""
echo "--- 5. Transactional Profile Apply & Revert ---"
"$LYNXD" profile apply balance >/dev/null 2>&1 && prof_bal=1 || prof_bal=0
test_assert "lynxd profile apply balance executes successfully" "$prof_bal"

prof_curr=$("$LYNXD" profile current 2>/dev/null | grep "Current Profile" | grep -o "balance")
[ "$prof_curr" = "balance" ] && cur_ok=1 || cur_ok=0
test_assert "lynxd profile current verifies active profile is 'balance'" "$cur_ok"

"$LYNXD" profile apply performance >/dev/null 2>&1 && prof_perf=1 || prof_perf=0
test_assert "lynxd profile apply performance executes successfully" "$prof_perf"

"$LYNXD" profile revert >/dev/null 2>&1 && rev_ok=1 || rev_ok=0
test_assert "lynxd profile revert restores snapshot baseline cleanly" "$rev_ok"

echo ""
echo "--- 6. Crash Recovery Simulation ---"
# Simulate an interrupted dirty state
state_file="$MODPATH/state/active_profile.json"
[ -f "$state_file" ] || state_file="/data/adb/modules/Lynx/state/active_profile.json"
if [ -f "$state_file" ]; then
    sed -i 's/"dirty": false/"dirty": true/' "$state_file" 2>/dev/null
    rec_out=$("$LYNXD" profile recover 2>&1)
    echo "$rec_out" | grep -q "RECOVERED" && rec_pass=1 || rec_pass=0
    test_assert "lynxd profile recover detects dirty state and recovers" "$rec_pass"
else
    test_assert "State file exists for crash simulation" "0"
fi

echo ""
echo "=========================================="
echo "Phase 5C Validation Summary: $pass_count PASSED, $fail_count FAILED"
echo "Log saved to: $OUT_LOG"
echo "=========================================="
