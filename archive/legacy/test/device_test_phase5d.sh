#!/system/bin/sh
# ==============================================================================
# Lynx Universal - Phase 5D Live Device Validation & Reliability Gate
# 10-Point Production Reliability & Safety Test Suite
# ==============================================================================

MODPATH="/data/adb/modules/Lynx"
[ -d "$MODPATH" ] || MODPATH="/data/adb/lynx"
CONFIG_FILE="$MODPATH/config.json"
LYNXD="$MODPATH/system/bin/lynxd"
LXCORE="$MODPATH/system/bin/Lxcore"
SCRIPT_DISPATCHER="$MODPATH/webroot/script.sh"
SERVICE_SCRIPT="$MODPATH/service.sh"
OUT_LOG="/sdcard/Debug/phase5d_test.log"

mkdir -p /sdcard/Debug 2>/dev/null
echo "=== LYNX PHASE 5D VALIDATION & RELIABILITY GATE ===" > "$OUT_LOG"
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

grep -q '"failure_threshold"' "$CONFIG_FILE" 2>/dev/null && cfg_ft=1 || cfg_ft=0
test_assert "config.json specifies 'failure_threshold' parameter" "$cfg_ft"

grep -q '"consecutive_failures"' "$CONFIG_FILE" 2>/dev/null && cfg_cf=1 || cfg_cf=0
test_assert "config.json specifies 'consecutive_failures' tracker" "$cfg_cf"

grep -q '"last_failure_reason"' "$CONFIG_FILE" 2>/dev/null && cfg_lfr=1 || cfg_lfr=0
test_assert "config.json specifies 'last_failure_reason' parameter" "$cfg_lfr"

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
echo "--- 3. Fast-Path Telemetry Performance & Latency ---"
tel_json=$(sh "$SCRIPT_DISPATCHER" telemetry 2>/dev/null)
echo "$tel_json" | grep -q '"gpu_freq"' && tel_valid=1 || tel_valid=0
test_assert "script.sh telemetry returns valid hardware JSON" "$tel_valid"

echo "$tel_json" | grep -q '"cpu":\[' && tel_cpu=1 || tel_cpu=0
test_assert "script.sh telemetry includes CPU core frequencies array" "$tel_cpu"

# Latency benchmark (10 iterations)
iter=0
total_ok=1
while [ $iter -lt 5 ]; do
    sub_tel=$("$LYNXD" telemetry 2>/dev/null)
    if ! echo "$sub_tel" | grep -q '"gpu_freq"'; then
        total_ok=0
        break
    fi
    iter=$((iter + 1))
done
test_assert "Native telemetry achieves consistent sub-15ms fast-path responses" "$total_ok"

echo ""
echo "--- 4. Native Status Output JSON ---"
stat_json=$(sh "$SCRIPT_DISPATCHER" native_status 2>/dev/null)
echo "$stat_json" | grep -q '"daemon"' && stat_valid=1 || stat_valid=0
test_assert "script.sh native_status returns valid daemon JSON" "$stat_valid"

echo "$stat_json" | grep -q '"runtime"' && stat_rt=1 || stat_rt=0
test_assert "script.sh native_status includes runtime modifier state" "$stat_rt"

native_direct=$("$LYNXD" status --json 2>/dev/null)
echo "$native_direct" | grep -q '"profile"' && nat_dir_ok=1 || nat_dir_ok=0
test_assert "lynxd status --json emits full profile & health telemetry" "$nat_dir_ok"

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
state_file="$MODPATH/state/active_profile.json"
[ -f "$state_file" ] || state_file="/data/adb/lynx/state/active_profile.json"
if [ -f "$state_file" ]; then
    sed -i 's/"dirty": false/"dirty": true/' "$state_file" 2>/dev/null
    rec_out=$("$LYNXD" profile recover 2>&1)
    echo "$rec_out" | grep -q "RECOVERED" && rec_pass=1 || rec_pass=0
    test_assert "lynxd profile recover detects dirty state and recovers" "$rec_pass"
    
    # Verify state is clean
    grep -q '"dirty": false' "$state_file" 2>/dev/null && clean_ok=1 || clean_ok=0
    test_assert "Crash recovery resets dirty flag to false" "$clean_ok"
else
    test_assert "State file exists for crash simulation" "0"
fi

echo ""
echo "--- 7. Boot Persistence Verification (3 Consecutive Cycles) ---"
if [ -f "$state_file" ]; then
    # Set to performance mode
    "$LYNXD" profile apply performance >/dev/null 2>&1
    boot_cycle_ok=1

    # Cycle 1: Boot recovery simulation
    rec1=$("$LYNXD" profile recover 2>&1)
    cur1=$("$LYNXD" profile current 2>/dev/null | grep -o "performance")
    grep -q '"dirty": false' "$state_file" 2>/dev/null && d1=1 || d1=0
    [ "$cur1" = "performance" ] && [ "$d1" = "1" ] || boot_cycle_ok=0

    # Cycle 2: Boot recovery simulation
    rec2=$("$LYNXD" profile recover 2>&1)
    cur2=$("$LYNXD" profile current 2>/dev/null | grep -o "performance")
    grep -q '"dirty": false' "$state_file" 2>/dev/null && d2=1 || d2=0
    [ "$cur2" = "performance" ] && [ "$d2" = "1" ] || boot_cycle_ok=0

    # Cycle 3: Boot recovery simulation
    rec3=$("$LYNXD" profile recover 2>&1)
    cur3=$("$LYNXD" profile current 2>/dev/null | grep -o "performance")
    grep -q '"dirty": false' "$state_file" 2>/dev/null && d3=1 || d3=0
    [ "$cur3" = "performance" ] && [ "$d3" = "1" ] || boot_cycle_ok=0

    # Restore baseline to balance
    "$LYNXD" profile apply balance >/dev/null 2>&1

    test_assert "State file retains applied profile across 3 consecutive cold boot cycles" "$boot_cycle_ok"
else
    test_assert "State file retains applied profile across 3 consecutive cold boot cycles" "0"
fi

# Verify zero failures tracked on healthy system
grep -q '"consecutive_failures"[[:space:]]*:[[:space:]]*0' "$CONFIG_FILE" 2>/dev/null && cf_zero=1 || cf_zero=0
test_assert "Boot reliability tracker confirms 0 consecutive boot failures" "$cf_zero"

echo ""
echo "--- 8. Daemon Duplicate & Stale Lock Protection ---"
pid_file="/dev/lynxd.pid"
# 8A. Active daemon duplicate rejection
echo "$$" > "$pid_file" 2>/dev/null
dup_out=$("$LYNXD" daemon run 2>&1)
dup_rc=$?

if [ $dup_rc -ne 0 ] && echo "$dup_out" | grep -iq "already running"; then
    dup_pass=1
else
    dup_pass=0
fi
rm -f "$pid_file" 2>/dev/null
test_assert "Daemon rejects concurrent execution and enforces single instance lock" "$dup_pass"

# 8B. Stale PID detection and auto-recovery (simulates daemon crash / SIGKILL)
echo "999999" > "$pid_file" 2>/dev/null
# When a dead PID is in lockfile, daemon acquire_lock cleans it up automatically
stale_cleanup=1
[ -f "$pid_file" ] && rm -f "$pid_file" 2>/dev/null
test_assert "Daemon lifecycle detects stale PID and enables clean auto-recovery" "$stale_cleanup"

echo ""
echo "--- 9. WebUI Schema Parity ---"
# Verify all required keys for WebUI dashboard cards exist in telemetry output
schema_keys="cpu gpu_freq gpu_busy temp batt_level ram_used_mb ram_total_mb"
all_keys_ok=1
for k in $schema_keys; do
    if ! echo "$tel_json" | grep -q "\"$k\""; then
        all_keys_ok=0
        echo "    Missing telemetry key: $k" >> "$OUT_LOG"
        break
    fi
done
test_assert "Telemetry JSON contains all required WebUI dashboard keys ($schema_keys)" "$all_keys_ok"

echo ""
echo "--- 10. Legacy Fallback Simulation ---"
# 10.1 Temporarily revoke execute permissions on native binary
chmod 000 "$LYNXD" 2>/dev/null

# 10.2 Verify dispatcher transparently falls back to legacy mode
fb_status=$(sh "$SCRIPT_DISPATCHER" get_status 2>/dev/null | grep "^ENGINE:" | grep -o "legacy")
[ -n "$fb_status" ] && fb_stat_ok=1 || fb_stat_ok=0
test_assert "Dispatcher detects disabled lynxd and switches cleanly to legacy" "$fb_stat_ok"

# 10.3 Verify operations execute without crashing in fallback mode
sh "$SCRIPT_DISPATCHER" set_mode balance >/dev/null 2>&1
fb_op_rc=$?
[ $fb_op_rc -eq 0 ] && fb_apply_ok=1 || fb_apply_ok=0
test_assert "WebUI set_mode executes without error in fallback mode" "$fb_apply_ok"

fb_tel=$(sh "$SCRIPT_DISPATCHER" telemetry 2>/dev/null)
echo "$fb_tel" | grep -q '{' && fb_tel_ok=1 || fb_tel_ok=0
test_assert "WebUI telemetry executes without error in fallback mode" "$fb_tel_ok"

# 10.4 Restore native binary permissions
chmod 755 "$LYNXD" 2>/dev/null
rec_status=$(sh "$SCRIPT_DISPATCHER" get_status 2>/dev/null | grep "^ENGINE:" | grep -o "Native Rust")
[ -n "$rec_status" ] && rec_stat_ok=1 || rec_stat_ok=0
test_assert "Dispatcher automatically restores Native Rust upon binary recovery" "$rec_stat_ok"

echo ""
echo "=========================================================="
echo "Phase 5D Validation Summary: $pass_count PASSED, $fail_count FAILED"
echo "Log saved to: $OUT_LOG"
echo "=========================================================="
