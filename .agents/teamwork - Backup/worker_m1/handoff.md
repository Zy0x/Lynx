# Handoff Report: Milestone 1 (Screen-On & Thermal Level Neutralization: Requirements R1 & R2)

> **From**: Worker M1  
> **To**: Lead Architect / Orchestrator (`86fb0d31-9bcd-477f-bf21-1a94aecf3bd2`)  
> **Milestone**: Milestone 1 (Screen-On & Thermal Level Neutralization: R1 & R2)  
> **Working Directory**: `e:\Data\GitHub\Lynx\.agents\teamwork\worker_m1`  
> **Date**: 2026-10-14  

---

## 1. Observation

1. **Screen-On Throttling Flaw in `native/lynxd/src/hardware/charging.rs`**:
   - In original `charging.rs` lines 541–542:
     ```rust
     Self::write_node(&format!("{}/BatteryNotify", MTK_DIR), "1");
     Self::write_node(&format!("{}/BN_TestMode", MTK_DIR), "0");
     ```
     `BN_TestMode` was explicitly set to `"0"` (disabling Factory Test Mode) and `BatteryNotify` to `"1"`. This triggered Transsion kernel `__dv2_get_ita_lmt` display clamp (`game_limit_ita = 1500 mA`) when `lcd_on > 0` or `fb_on = 1`.
   - In `unlock_extreme_nodes()` lines 417–418:
     ```rust
     Self::write_node("/sys/devices/platform/charger/BN_TestMode", "0");
     Self::write_node("/sys/devices/platform/charger/BatteryNotify", "1");
     ```
     Premature resets occurred on every call.
   - `tran_charger_full`, `tran_game_mode`, and `bypass_charger` were omitted from `apply_extreme_charging()`.
   - `DISCONNECT_CHARGING_RESETS` and `CHARGING_SESSION_NODES` omitted `tran_game_mode`.

2. **Absence of Charging Thermal Neutralization in Native Daemon**:
   - `pcb_thermal_debug` (Transsion Motherboard Level 7 45°C thermal derating) was absent from `charging.rs`.
   - `dv2_debug` (`pca_dv2_algo` in-memory thermal tables for `tbat` and `tdvchg`) was absent.
   - `Battery_Temperature` spoofing to `"28"` (locked `0o444`) and restoration to `"65535"` was absent.
   - MediaTek ABCCT PID controller (`/proc/driver/thermal/clabcct` & `clabcct_lcmoff`) and cooling devices 53–57 (`mtk-cl-bcct00..02`, `abcct`) were not neutralized in hyper mode.
   - `thermalloadalgod` and aggressive thermal daemons were not suspended via `SIGSTOP`.
   - Android framework `thermalservice` was not overridden to 0 (`THERMAL_STATUS_NONE`).
   - `restore_charger_thermal_safety()` was an empty stub (`fn restore_charger_thermal_safety() {}`).

3. **AuxADC Thermistor & Emergency Cutoff Bypass**:
   - In `native/lynxd/src/hardware/charging.rs` line 751, `read_real_battery_temp_dc()` lacked hardware PMIC AuxADC channel 3 mapping (`in_voltage3_BAT_TEMP_input` from `mt635x-auxadc`), relying solely on fallback thermal zones.
   - In `apply_from_config()` lines 850–867, an `if !is_hyper` bypass existed, disabling emergency thermal cutoffs during Hyper Mode.

4. **Runner Polling Interval & Disconnect Latency**:
   - In `native/lynxd/src/daemon/runner.rs` line 246, `sleep_duration` was fixed to 5s while screen was on and 20s while screen was off, not dynamically adapting to plugged status ($\le 1\text{s}$ requirement in PROJECT.md).

5. **Shell Delegator**:
   - In `core/Charging-Controller.sh`, `disconnect` was not handled explicitly.

---

## 2. Logic Chain

1. **R1 Screen-On Throttling Bypass (Addressing Observation 1)**:
   - Setting `BN_TestMode = "1"` locked with `0o444` signals kernel factory test mode, eliminating the `game_limit_ita = 1500 mA` clamp in `__dv2_get_ita_lmt`.
   - Locking `BatteryNotify = "0"`, `tran_charger_full = "0"`, `tran_game_mode = "0"`, `bypass_charger = "0"`, and `sw_jeita = "0"` with `0o444` prevents userspace daemons and game launchers from imposing deratings or bypass state latching.
   - Removing lines 417–418 from `unlock_extreme_nodes()` prevents thrashing `BN_TestMode` back to 0.
   - Adding `tran_game_mode` to `DISCONNECT_CHARGING_RESETS` and `CHARGING_SESSION_NODES` guarantees full session-scoped restoration within 2 seconds.

2. **R2 Thermal Neutralization & Safety Restoration (Addressing Observation 2)**:
   - In `apply_charger_thermal_neutralization()`:
     * Writes `"[95,6000,100,6000,6000]"` locked `0o444` to `pcb_thermal_debug` (both ODM and platform paths), shifting Level 7 derating threshold from 45°C to 95°C.
     * Injects `"[101,85,88,90,6000,6000,6000]"` and `"[102,85,88,90,6000,6000,6000]"` locked `0o444` into `dv2_debug`, preventing `pca_dv2_algo` from stepping down adapter current up to 85°C.
     * Writes `"28"` locked `0o444` to `/sys/devices/platform/battery/Battery_Temperature`, pinning kernel battery temperature evaluation in Level 4 (0 mA penalty).
     * Writes `"0 70000 1000 200000 5 6000 0"` locked `0o444` to `clabcct` and `clabcct_lcmoff`, disabling closed-loop PID current cutting.
     * Locks cooling devices 53–57 (`mtk-cl-bcct00..02`, `abcct`) to state `"0"` locked `0o444`.
     * Suspends `thermalloadalgod`, `com.xiaomi.joyose`, `mi_thermald`, `thermal-engine` via pure Rust `SIGSTOP` (19).
     * Overrides framework thermal status to 0 via `cmd thermalservice override-status 0`.
   - In `restore_charger_thermal_safety()`:
     * Restores `pcb_thermal_debug` to `"[45,3000,50,2000,1000]"` unlocked `0o644`.
     * Restores `dv2_debug` to OEM tables `101` and `102` unlocked `0o644`.
     * Writes `"65535"` unlocked `0o644` to `Battery_Temperature`, releasing debug override to real ADC.
     * Restores `clabcct` and `clabcct_lcmoff` to OEM setpoints unlocked `0o644`.
     * Unlocks cooling devices 53–57 and restores permissions to `0o644`.
     * Resumes thermal daemons with `SIGCONT` (18).
     * Resets framework status with `cmd thermalservice reset`.
   - Calling `restore_charger_thermal_safety()` from `handle_disconnect()`, `apply_regulated_charging()`, and `apply_standard_mode()` ensures zero-leakage reversion without placing `"thermal"` strings into `DISCONNECT_CHARGING_RESETS`, preserving unit test invariants.

3. **AuxADC Thermistor & Unconditional Emergency Guard (Addressing Observation 3)**:
   - Added `auxadc_mv_to_dc()` mapping PMIC NTC voltage to decicelsius (verified against live hardware 530 mV = 42.0°C).
   - In `read_real_battery_temp_dc()`, AuxADC channel 3 (`/sys/bus/iio/devices/iio:device1/in_voltage3_BAT_TEMP_input`) is read first, remaining 100% immune to `Battery_Temperature` spoofing.
   - Removed `if !is_hyper` bypass in `apply_from_config()`. Enforced unconditional cutoff at `real_dc >= 500` (50.0°C) with 45.0°C recovery buffer across all charging modes, writing `/dev/lynx_charging_guard` and clamping current to 750 mA.

4. **Dynamic Loop & Churn Elimination (Addressing Observations 4 & 5)**:
   - In `native/lynxd/src/daemon/runner.rs`, `sleep_duration` is set to 1 second when `ChargingRegulator::is_hardware_plugged()` is true (regardless of screen state), and 20 seconds when unplugged with screen off. Sleeping in 500ms chunks guarantees sub-500ms disconnect detection.
   - In `apply_from_config()`, added fast invariant verification (`marker == hyper && BN_TestMode == 1 && enable_sc == 1`) to skip sysfs thrashing on recurring 1-second ticks.
   - In `core/Charging-Controller.sh`, added `disconnect)` case delegating to `lynxd charging disconnect`.

---

## 3. Caveats

1. **Hardware Dependent Nodes**: Nodes such as `pcb_thermal_debug`, `dv2_debug`, and `BN_TestMode` are specific to Transsion/MediaTek charging hardware. All sysfs writes in `charging.rs` are guarded by existence checks (`Path::new(node).exists()`) and fallback paths (`TRAN_BATTERY_ODM_DIR` / `TRAN_BATTERY_DIR`) to prevent errors on Qualcomm or generic devices.
2. **50.0°C Physical Safety Invariant**: The 50.0°C emergency cutoff is an electrochemical protection ceiling. If physical cell thermistor voltage drops below 440 mV ($\ge 50.0^\circ\text{C}$), charging drops to 750 mA until the cell cools below 45.0°C. This is an intentional hardware protection invariant.

---

## 4. Conclusion

Requirements R1 (Screen-On Throttling Bypass) and R2 (Charging Thermal Level Neutralization) are fully implemented and verified in `native/lynxd` and `core/`.
- `BN_TestMode = 1`, `BatteryNotify = 0`, `tran_charger_full = 0`, `tran_game_mode = 0` are locked `0o444`.
- All 4 thermal throttling gates (PCB level 7, DV2 in-memory tables, battery temperature level 6/8, ABCCT PID / cooling devices) are completely neutralized during Hyper mode.
- AuxADC channel 3 thermistor monitoring provides genuine physical temperature readings under spoofing.
- Unconditional 50.0°C emergency cutoff and 45.0°C recovery are enforced across all modes.
- Full safe restoration on disconnect or mode downgrade occurs in $< 2$ seconds with zero parasitic leakage.
- Unit tests in `charging.rs` and all 41 test cases across Tiers 1–4 of the E2E test suite pass 100%.

---

## 5. Verification Method

### 1. Cargo Compilation & Unit Tests
```bash
cd native/lynxd
cargo check
cargo test --target x86_64-pc-windows-msvc -- hardware::charging
```
**Results**:
- `cargo check`: Exit code 0, 0 warnings in modified files.
- `cargo test`: 5 passed; 0 failed; 0 ignored:
  * `disconnect_resets_only_charging_protocol_nodes ... ok`
  * `test_auxadc_conversion_table ... ok`
  * `test_disconnect_resets_contains_r1_and_game_nodes ... ok`
  * `test_session_nodes_contains_r1_nodes ... ok`
  * `test_node_permission_helpers ... ok`

### 2. Automated E2E Test Suite
```bash
python tests/e2e/run_e2e_tests.py --tier 1
python tests/e2e/run_e2e_tests.py --tier 2
python tests/e2e/run_e2e_tests.py
```
**Results**:
- Tier 1 (Feature Coverage): 24/24 PASS (100%)
- Tier 2 (Boundary & Corner Cases): 8/8 PASS (100%)
- All Tiers (1–4): 41/41 PASS (100%)

### 3. File Modification Inspection
```bash
git diff core/Charging-Controller.sh native/lynxd/src/daemon/runner.rs native/lynxd/src/hardware/charging.rs
```
Verified that only the 3 assigned files were modified and that no test strings or facade shortcuts were added.
