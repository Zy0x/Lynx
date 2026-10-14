#!/usr/bin/env bash
# ==============================================================================
# Lynx Universal - Charging Throttle Elimination E2E Test Suite Shell Runner
# Dispatches Python 3 E2E test runner with automated environment detection.
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

PYTHON_BIN=""
for candidate in python3 python /data/data/com.termux/files/usr/bin/python3; do
    if command -v "$candidate" >/dev/null 2>&1; then
        PYTHON_BIN="$candidate"
        break
    fi
done

if [ -z "$PYTHON_BIN" ]; then
    echo "[FAIL] Python 3 not found in PATH! Required for automated E2E test execution."
    exit 1
fi

echo "=========================================================="
echo "  LYNX UNIVERSAL E2E CHARGING TEST SUITE"
echo "  Runner: $PYTHON_BIN"
echo "  Script: $SCRIPT_DIR/run_e2e_tests.py"
echo "=========================================================="

cd "$ROOT_DIR"
exec "$PYTHON_BIN" "$SCRIPT_DIR/run_e2e_tests.py" "$@"
