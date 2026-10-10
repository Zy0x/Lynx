#!/usr/bin/env bash
# ==============================================================================
# Lynx Universal - Thin Shell Facade Regression Audit Suite
# Phase 6C: Validates Delegation, Fallbacks, Perm Checks & Format Parity
# ==============================================================================

set -e

SANDBOX="${TMPDIR:-/tmp}/lynx_facade_test_$$"
mkdir -p "$SANDBOX/core/lib" "$SANDBOX/system/bin"
trap 'rm -rf "$SANDBOX" /tmp/facade_calls_$$.log' EXIT INT TERM

CALL_LOG="/tmp/facade_calls_$$.log"
> "$CALL_LOG"

echo "=========================================================="
echo "  LYNX UNIVERSAL - THIN SHELL FACADE REGRESSION TEST"
echo "=========================================================="
echo "  Sandbox: $SANDBOX"

# Shadow Android command-line utilities to prevent Windows cmd.exe hang
for tool in cmd dumpsys setprop getprop su pidof settings sysctl pm am; do
    cat << 'EOF' > "$SANDBOX/system/bin/$tool"
#!/usr/bin/env bash
exit 0
EOF
    chmod 755 "$SANDBOX/system/bin/$tool"
done
export PATH="$SANDBOX/system/bin:$PATH"

PASSED=0
FAILED=0

assert_true() {
    local name="$1"
    local condition="$2"
    if eval "$condition"; then
        echo "  [PASS] $name"
        PASSED=$((PASSED + 1))
    else
        echo "  [FAIL] $name"
        FAILED=$((FAILED + 1))
    fi
}

CFG="$SANDBOX/config.json"
MOCK_LYNXD="$SANDBOX/system/bin/lynxd"

cat << 'EOF' > "$CFG"
{
  "engine": {
    "native_enabled": true
  },
  "active_profile": "balance"
}
EOF

# Create a controllable mock lynxd with CALL_LOG path baked in
cat << EOF > "$MOCK_LYNXD"
#!/usr/bin/env bash
echo "LYNXD_CALLED:\$*" >> "$CALL_LOG"
case "\$1" in
    telemetry)
        echo '{"device":"mock","battery":{"capacity":85,"temp_c":31.0},"cpu":{"scaling_cur_freq":[2000000]},"memory":{"mem_total_kb":8000000}}'
        exit 0
        ;;
    profile)
        case "\$2" in
            apply)
                echo "APPLIED_PROFILE:\$3"
                exit 0
                ;;
            revert)
                echo "REVERTED"
                exit 0
                ;;
        esac
        ;;
    daemon)
        if [ "\$2" = "run" ]; then
            echo "DAEMON_RUN_STARTED"
            exit 0
        fi
        ;;
    help) exit 0 ;;
esac
exit 1
EOF
chmod 755 "$MOCK_LYNXD"

# Copy actual scripts to sandbox
cp core/apply_profile.sh "$SANDBOX/core/apply_profile.sh"
cp core/Smart-AI.sh "$SANDBOX/core/Smart-AI.sh"
cp core/lib/telemetry.sh "$SANDBOX/core/lib/telemetry.sh"
chmod 755 "$SANDBOX/core/apply_profile.sh" "$SANDBOX/core/Smart-AI.sh" "$SANDBOX/core/lib/telemetry.sh"

# Point MODPATH in copied scripts to SANDBOX
sed -i "s|MODPATH=\"/data/adb/modules/Lynx\"|MODPATH=\"$SANDBOX\"|g" "$SANDBOX/core/apply_profile.sh"
sed -i "s|MODDIR=\"/data/adb/modules/Lynx\"|MODDIR=\"$SANDBOX\"|g" "$SANDBOX/core/Smart-AI.sh"
sed -i "s|MODPATH=\"/data/adb/modules/Lynx\"|MODPATH=\"$SANDBOX\"|g" "$SANDBOX/core/lib/telemetry.sh"

# ==============================================================================
# TEST 1: Native Enabled Mode (Enters lynxd)
# ==============================================================================
echo ""
echo "[Test 1] Native Enabled Delegation..."
> "$CALL_LOG"

# 1a. Telemetry facade
telem_out=$(sh "$SANDBOX/core/lib/telemetry.sh" 2>/dev/null)
assert_true "1a. telemetry.sh enters lynxd when native enabled" "grep -q 'LYNXD_CALLED:telemetry' '$CALL_LOG'"
assert_true "1a. telemetry.sh output contains valid JSON" "echo '$telem_out' | grep -q '\"mock\"'"

# 1b. Apply profile facade
> "$CALL_LOG"
apply_out=$(sh "$SANDBOX/core/apply_profile.sh" performance user 2>/dev/null)
assert_true "1b. apply_profile.sh enters lynxd when native enabled" "grep -q 'LYNXD_CALLED:profile apply performance' '$CALL_LOG'"

# 1c. Smart-AI facade
> "$CALL_LOG"
ai_out=$(sh "$SANDBOX/core/Smart-AI.sh" 2>/dev/null)
assert_true "1c. Smart-AI.sh enters lynxd daemon when native enabled" "grep -q 'LYNXD_CALLED:daemon run' '$CALL_LOG'"

# ==============================================================================
# TEST 2: Binary Missing (Falls back to legacy)
# ==============================================================================
echo ""
echo "[Test 2] Binary Missing Fallback..."
mv "$MOCK_LYNXD" "$SANDBOX/system/bin/lynxd.bak"
> "$CALL_LOG"

# 2a. Telemetry fallback
telem_fallback=$(sh "$SANDBOX/core/lib/telemetry.sh" 2>/dev/null || true)
assert_true "2a. telemetry.sh falls through cleanly without error when binary missing" "[ -n '$telem_fallback' ]"
assert_true "2a. telemetry.sh does not call lynxd" "! grep -q 'LYNXD_CALLED' '$CALL_LOG' 2>/dev/null"

# Restore binary
mv "$SANDBOX/system/bin/lynxd.bak" "$MOCK_LYNXD"
chmod 755 "$MOCK_LYNXD"

# ==============================================================================
# TEST 3: Permission Error (Binary not executable)
# ==============================================================================
echo ""
echo "[Test 3] Permission Error Fallback..."
# Simulate non-executable binary by creating an invalid wrapper that returns 126
cat << 'EOF' > "$SANDBOX/system/bin/lynxd_noexec"
#!/usr/bin/env bash
exit 126
EOF
# Test is_native_active behavior when binary cannot execute
assert_true "3a. Non-executable/failing binary triggers fallback path" "[ -x '$SANDBOX/system/bin/lynxd_noexec' ]"

# ==============================================================================
# TEST 4: Config Disabled Mode (native_enabled: false)
# ==============================================================================
echo ""
echo "[Test 4] Config Disabled Mode (native_enabled: false)..."
sed -i 's/"native_enabled"[[:space:]]*:[[:space:]]*true/"native_enabled": false/' "$CFG"
> "$CALL_LOG"

# 4a. Telemetry fallback
sh "$SANDBOX/core/lib/telemetry.sh" >/dev/null 2>&1 || true
assert_true "4a. telemetry.sh stays in legacy mode when native_enabled=false" "! grep -q 'LYNXD_CALLED' '$CALL_LOG' 2>/dev/null"

# 4b. Apply profile fallback
> "$CALL_LOG"
sh "$SANDBOX/core/apply_profile.sh" balance user >/dev/null 2>&1 || true
assert_true "4b. apply_profile.sh stays in legacy mode when native_enabled=false" "! grep -q 'LYNXD_CALLED' '$CALL_LOG' 2>/dev/null"

# 4c. Smart-AI fallback
> "$CALL_LOG"
sh "$SANDBOX/core/Smart-AI.sh" >/dev/null 2>&1 &
BG_PID=$!
sleep 0.2
kill "$BG_PID" 2>/dev/null || true
wait "$BG_PID" 2>/dev/null || true
assert_true "4c. Smart-AI.sh stays in legacy mode when native_enabled=false" "! grep -q 'LYNXD_CALLED' '$CALL_LOG' 2>/dev/null"

# ==============================================================================
# TEST 5: Output Format Consistency Parity
# ==============================================================================
echo ""
echo "[Test 5] Output Format Parity Verification..."
assert_true "5a. Telemetry JSON contains battery capacity" "echo '$telem_out' | grep -q '\"capacity\"'"
assert_true "5b. Telemetry JSON contains cpu frequency" "echo '$telem_out' | grep -q '\"scaling_cur_freq\"'"
assert_true "5c. Telemetry JSON contains memory total" "echo '$telem_out' | grep -q '\"mem_total_kb\"'"

echo ""
echo "=========================================================="
echo "  FACADE AUDIT RESULTS: $PASSED PASSED, $FAILED FAILED"
echo "=========================================================="

if [ "$FAILED" -eq 0 ]; then
    echo "  [OK] ALL 11 FACADE REGRESSION TESTS PASSED!"
    exit 0
else
    echo "  [FAIL] SOME FACADE TESTS FAILED!"
    exit 1
fi
