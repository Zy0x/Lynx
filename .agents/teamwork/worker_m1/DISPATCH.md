## 2026-10-14T14:19:37Z
You are Worker M1 for Milestone 1 (Screen-On & Thermal Level Neutralization: Requirements R1 & R2).
Your working directory is: e:\Data\GitHub\Lynx\.agents\teamwork\worker_m1
Project root is: e:\Data\GitHub\Lynx

Mandatory requirements:
- Read original request at: e:\Data\GitHub\Lynx\.agents\teamwork\ORIGINAL_REQUEST.md
- Read project specification at: e:\Data\GitHub\Lynx\.agents\teamwork\PROJECT.md
- Read test readiness declaration at: e:\Data\GitHub\Lynx\TEST_READY.md
- Read detailed explorer blueprints:
  - `e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m1_1\handoff.md` and `analysis.md` (R1 Screen-On Throttling Bypass)
  - `e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m1_2\handoff.md` and `analysis.md` (R2 Thermal Level Neutralization)
  - `e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m1_3\handoff.md` and `analysis.md` (Daemon Architecture & Safety Cutoff)

File Write Ownership:
You EXCLUSIVELY own:
- `native/lynxd/src/hardware/charging.rs`
- `native/lynxd/src/daemon/runner.rs`
- `core/Charging-Controller.sh`

MANDATORY INTEGRITY WARNING:
> DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Implementation Tasks:
1. Node permission locking utilities in `native/lynxd/src/hardware/charging.rs`:
   - Implement/refactor `write_node_lock` (writes value, sets mode to `0o444`), `unlock_node` (restores `0o644`/`0o666`), and `set_perm`.
2. Implement R1 Screen-On Charging Throttling Bypass:
   - In `apply_extreme_charging()`: fix the defect where `BN_TestMode` was written as `"0"`. Write `"1"` to `/sys/devices/platform/charger/BN_TestMode` locked `0o444`.
   - Write `"0"` to `/sys/devices/platform/charger/BatteryNotify` locked `0o444`.
   - Write `"0"` to `tran_charger_full` locked `0o444`.
   - Write `"0"` to `tran_game_mode` locked `0o444`.
   - Clean up premature resets in `unlock_extreme_nodes()`.
   - Update `CHARGING_SESSION_NODES` and `DISCONNECT_CHARGING_RESETS` to include `tran_game_mode`.
3. Implement R2 Charging Thermal Throttling & Trip-Point Neutralization:
   - In `apply_extreme_charging()` (via `apply_charger_thermal_neutralization()`):
     * Inject `"[95,6000,100,6000,6000]"` into `/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug` (and fallback path) locked `0o444`.
     * Inject DV2 table overrides into `/sys/devices/platform/pca_dv2_algo/dv2_debug`: `"[101,85,88,90,6000,6000,6000]"` and `"[102,85,88,90,6000,6000,6000]"` locked `0o444`.
     * Spoof `/sys/devices/platform/battery/Battery_Temperature` to `"28"` locked `0o444`.
     * Disable closed-loop ABCCT PID: write `"0 70000 1000 200000 5 6000 0"` to `/proc/driver/thermal/clabcct` and `/proc/driver/thermal/clabcct_lcmoff` locked `0o444`.
     * Lock cooling devices 53–57 (`mtk-cl-bcct00..02`, `abcct`, `abcct_lcmoff`) to state `"0"` locked `0o444`.
     * Pause userspace thermal daemons (`thermalloadalgod`, `com.xiaomi.joyose`, `mi_thermald`, `thermal-engine`) with SIGSTOP (19).
     * Override Android framework status: execute `cmd thermalservice override-status 0`.
   - In `restore_charger_thermal_safety()`:
     * Restore `pcb_thermal_debug` to `"[45,3000,50,2000,1000]"`.
     * Restore `Battery_Temperature` to `"65535"` unlocked to `0o644`.
     * Restore `clabcct` and `clabcct_lcmoff` to OEM defaults.
     * Unlock cooling devices 53–57 and restore permissions to `0o644`.
     * Resume thermal daemons with SIGCONT (18).
     * Reset framework thermal service: `cmd thermalservice reset`.
4. Real Battery Thermistor & Emergency 50.0°C Guard:
   - Map AuxADC channel 3 (`/sys/bus/iio/devices/iio:device1/in_voltage3_BAT_TEMP_input` from `mt635x-auxadc`) in `read_real_battery_temp_dc()`.
   - Enforce unconditional emergency temperature cutoff at >= 50.0°C (`real_dc >= 500`) with 45.0°C recovery across all charging modes (remove the `if !is_hyper` bypass).
5. Clean Coordination:
   - Remove `unlock_extreme_nodes()` churn from `apply_extreme_charging()` entry.
   - Add fast invariant validation in `apply_from_config()` so recurring 1-second ticks do not thrash sysfs permissions.

Build & Verification:
- Compile `native/lynxd/` using cargo: `cargo check` and `cargo test`.
- Run E2E test runner: `python tests/e2e/run_e2e_tests.py --tier 1` and `python tests/e2e/run_e2e_tests.py --tier 2`.
- Document all implementation details, commands, and test outputs in `e:\Data\GitHub\Lynx\.agents\teamwork\worker_m1\handoff.md`.
- Send completion message to orchestrator via `send_message`.


## 2026-10-14T14:30:23Z
**Context**: Milestone 1 Implementation (R1 & R2)
**Content**: Checking in on implementation progress in native/lynxd/src/hardware/charging.rs.
**Action**: Please provide a brief status update and update your progress.md.
