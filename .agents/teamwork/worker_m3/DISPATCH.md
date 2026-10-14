## 2026-10-14T15:55:50Z
You are worker_m3 (Fail-Safe Disconnect & Safety Worker).
Your working directory is: e:\Data\GitHub\Lynx\.agents\teamwork\worker_m3

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

MANDATORY FIRST STEP:
Read the authoritative user request at:
e:\Data\GitHub\Lynx\.agents\teamwork\ORIGINAL_REQUEST.md
and the master project spec at:
e:\Data\GitHub\Lynx\.agents\teamwork\PROJECT.md

Read the Explorer handoff reports before writing code:
- e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m3_1\handoff.md
- e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m3_2\handoff.md
- e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m3_3\handoff.md

Exclusive Write Ownership:
You have exclusive write ownership over:
- `native/lynxd/src/hardware/charging.rs`
- `native/lynxd/src/daemon/runner.rs`
- `core/Charging-Controller.sh`

Implementation Scope for Milestone 3 (Requirement R4):
1. In `native/lynxd/src/hardware/charging.rs`:
   - Implement pure decision function `evaluate_plugged_state(has_online_rail: bool, batt_status: &str, charger_type: &str, vbus_mv: i64) -> bool` with unit testability.
   - Refactor `is_hardware_plugged()` using the Triple-Vector invariant:
     If `!has_online_rail && (is_discharging || is_unknown_charger)` -> immediately return `false`. This completely eliminates decoupling capacitor float delay. Check `usb/online`, `ac/online`, `charger/online`, and `main/online`.
   - Expand `DISCONNECT_CHARGING_RESETS` to explicitly include OEM limits:
     `input_current = "24576"`, `sc_ibat_limit = "2000"`, `chg1_current = "24576"`, `chg2_current = "24576"`, `boost_current = "0"`, `constant_charge_current_max = "4500000"`, `constant_charge_current = "4500000"`, `input_current_limit = "4500000"`.
   - In `handle_disconnect()`:
     * Call `Self::unlock_extreme_nodes()` to ensure all candidate sysfs nodes are restored to `0666` (or OEM `0644`).
     * Call `Self::restore_charging_session_baseline()` first, followed by `DISCONNECT_CHARGING_RESETS` last, ensuring fail-safe resets prevail over stale session entries.
     * Ensure `Battery_Temperature` is restored to `"65535"` with `0666` permissions.
     * Ensure `thermal-engine-v2` is included in the `signal_processes(..., false)` unfreeze list alongside `thermalloadalgod`, `com.xiaomi.joyose`, `mi_thermald`, `thermal-engine`.
   - Add unit tests for `evaluate_plugged_state` covering decouple capacitor float, bypass charging, and active charging.
2. In `native/lynxd/src/daemon/runner.rs`:
   - In the sleep loop: when `is_plugged == true`, sleep in 500ms chunks for sub-500ms disconnect detection; when `is_plugged == false` and `current_screen == ScreenState::Off`, sleep without polling sysfs every 500ms, preserving undisturbed deep sleep.
   - Remove the restrictive file existence check around `handle_disconnect()` so that disconnect is always processed when `!currently_plugged`.
3. In `core/Charging-Controller.sh`:
   - In `fallback_disconnect()`, send `killall -CONT` to `thermalloadalgod com.xiaomi.joyose mi_thermald thermal-engine thermal-engine-v2` to unfreeze daemons.
4. Build, Compile & Verify:
   - Run `cargo test --target x86_64-pc-windows-msvc` inside `native/lynxd`.
   - Run `python tests/e2e/run_e2e_tests.py --tier 1` and `python tests/e2e/run_e2e_tests.py --tier 2`.
   - Run `python tests/e2e/run_e2e_tests.py` (ensure 41/41 pass).
   - Compile release binary: `cargo build --release --target aarch64-linux-android` inside `native/lynxd`, copy to `system/bin/lynxd`, and deploy to active test device (`Infinix-X698`) if connected.

Deliverables:
- Write comprehensive handoff report to `e:\Data\GitHub\Lynx\.agents\teamwork\worker_m3\handoff.md`.
- Send completion message to orchestrator.
