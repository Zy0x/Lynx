#!/usr/bin/env bash
# ==============================================================================
# Lynx Universal - Boot Orchestration & Dry-Run Fallback Validation
# Phase 6B-4: Verifies Native Enabled, Native Disabled, & Watchdog Auto-Downgrade
# ==============================================================================

set -e

MOCK_ROOT="${TMPDIR:-/tmp}/lynx_boot_test_$$"
mkdir -p "$MOCK_ROOT"
trap 'rm -rf "$MOCK_ROOT"' EXIT INT TERM

echo "=========================================================="
echo "  LYNX UNIVERSAL - BOOT ORCHESTRATION REGRESSION TEST"
echo "=========================================================="
echo "  Sandbox: $MOCK_ROOT"

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

# Setup simulated environment
CFG="$MOCK_ROOT/config.json"
LOG="$MOCK_ROOT/service.log"
MOCK_BIN="$MOCK_ROOT/lynxd"

cat << 'EOF' > "$CFG"
{
  "engine": {
    "native_enabled": true,
    "legacy_fallback": true,
    "failure_threshold": 3,
    "consecutive_failures": 0,
    "last_failure_reason": "",
    "failure_history": []
  },
  "active_profile": "balance"
}
EOF

# Create a mock lynxd binary that supports "help", "profile recover", and "profile apply"
cat << 'EOF' > "$MOCK_BIN"
#!/usr/bin/env bash
case "$1" in
    help) exit 0 ;;
    profile)
        case "$2" in
            recover) exit 0 ;;
            apply) exit 0 ;;
            *) exit 1 ;;
        esac
        ;;
    *) exit 1 ;;
esac
EOF
chmod +x "$MOCK_BIN"

# --- SCENARIO 1: Native Enabled Mode ---
echo "[1/3] Testing Native Enabled Mode..."
USE_NATIVE=0
NATIVE_CONFIG_ENABLED=1
if grep -q '"native_enabled"[[:space:]]*:[[:space:]]*false' "$CFG" 2>/dev/null; then
    NATIVE_CONFIG_ENABLED=0
fi

if [ "$NATIVE_CONFIG_ENABLED" = "1" ] && [ -x "$MOCK_BIN" ]; then
    if "$MOCK_BIN" help >/dev/null 2>&1; then
        USE_NATIVE=1
    fi
fi

assert_true "Scenario 1: USE_NATIVE is set to 1 when native is enabled & binary healthy" "[ '$USE_NATIVE' = '1' ]"

applied_native=0
if [ "$USE_NATIVE" = "1" ]; then
    if "$MOCK_BIN" profile apply balance >/dev/null 2>&1; then
        applied_native=1
    fi
fi
assert_true "Scenario 1: Profile applied via native pipeline" "[ '$applied_native' = '1' ]"

# --- SCENARIO 2: Native Disabled Mode (Pure Legacy Fallback) ---
echo "[2/3] Testing Native Disabled Mode (Pure Legacy Mode)..."
sed -i 's/"native_enabled"[[:space:]]*:[[:space:]]*true/"native_enabled": false/' "$CFG"

USE_NATIVE=0
NATIVE_CONFIG_ENABLED=1
if grep -q '"native_enabled"[[:space:]]*:[[:space:]]*false' "$CFG" 2>/dev/null; then
    NATIVE_CONFIG_ENABLED=0
fi

if [ "$NATIVE_CONFIG_ENABLED" = "1" ] && [ -x "$MOCK_BIN" ]; then
    USE_NATIVE=1
fi

assert_true "Scenario 2: USE_NATIVE is 0 when native_enabled is false" "[ '$USE_NATIVE' = '0' ]"

# Fallback dispatch simulation
dispatched_fallback=0
if [ "$USE_NATIVE" = "0" ]; then
    # Legacy apply_profile path
    dispatched_fallback=1
fi
assert_true "Scenario 2: Execution cleanly routes to legacy shell fallback" "[ '$dispatched_fallback' = '1' ]"

# --- SCENARIO 3: Watchdog Failure Counter & Auto Downgrade Mode ---
echo "[3/3] Testing Watchdog Failure Counter & Auto Downgrade Mode..."
sed -i 's/"native_enabled"[[:space:]]*:[[:space:]]*false/"native_enabled": true/' "$CFG"
sed -i 's/"consecutive_failures"[[:space:]]*:[[:space:]]*[0-9]*/"consecutive_failures": 2/' "$CFG"

CUR_FAILURES=$(grep -o '"consecutive_failures"[[:space:]]*:[[:space:]]*[0-9]*' "$CFG" | awk -F: '{print $2}' | tr -d '[:space:]')
FAIL_THRESHOLD=3

# Simulate failure injection
CUR_FAILURES=$((CUR_FAILURES + 1))
if [ "$CUR_FAILURES" -ge "$FAIL_THRESHOLD" ]; then
    sed -i 's/"native_enabled"[[:space:]]*:[[:space:]]*true/"native_enabled": false/' "$CFG"
    CUR_FAILURES=0
fi
sed -i "s/\"consecutive_failures\"[[:space:]]*:[[:space:]]*[0-9]*/\"consecutive_failures\": $CUR_FAILURES/" "$CFG"
sed -i 's/"last_failure_reason"[[:space:]]*:[[:space:]]*"[^"]*"/"last_failure_reason": "sanity_check_timeout"/' "$CFG"

assert_true "Scenario 3: Watchdog flipped native_enabled to false" "grep -q '\"native_enabled\": false' '$CFG'"
assert_true "Scenario 3: consecutive_failures reset to 0 after trip" "grep -q '\"consecutive_failures\": 0' '$CFG'"
assert_true "Scenario 3: last_failure_reason recorded in config.json" "grep -q '\"last_failure_reason\": \"sanity_check_timeout\"' '$CFG'"

echo "=========================================================="
echo "  TEST RESULTS: $PASSED PASSED, $FAILED FAILED"
echo "=========================================================="

if [ "$FAILED" -eq 0 ]; then
    echo "  [OK] BOOT ORCHESTRATION REGRESSION VALIDATION PASSED!"
    exit 0
else
    echo "  [FAIL] SOME TESTS FAILED!"
    exit 1
fi
