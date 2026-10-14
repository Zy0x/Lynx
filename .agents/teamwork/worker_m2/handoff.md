# Handoff Report: Requirement R3 — Continuous Maximum Pump Current Enforcement (>6A Battery Current)

> **From**: Worker M2 (Charge Pump & Charging Worker)  
> **To**: Lead Architect / Orchestrator (`4a93ea5f-bf3a-41d2-8075-760f372161d2`)  
> **Milestone**: Milestone 2 (Requirement R3: Continuous Maximum Pump Current Enforcement)  
> **Working Directory**: `e:\Data\GitHub\Lynx\.agents\teamwork\worker_m2`  
> **Date**: 2026-10-14  

---

## 1. Observation

### 1.1 Invariant Verification Gap in `native/lynxd/src/hardware/charging.rs`
Prior to this task, `apply_from_config()` in `charging.rs` line 1133–1140 evaluated:
```rust
let invariants_intact = if is_mtk {
    let bn_test = Self::read_node(&format!("{}/BN_TestMode", MTK_DIR)).unwrap_or_default();
    let enable_sc = Self::read_node(&format!("{}/enable_sc", MTK_DIR)).unwrap_or_default();
    bn_test == "1" && enable_sc == "1"
} else {
...
```
This check verified only `BN_TestMode` (R1) and `enable_sc` (oscillator clock), but completely omitted R3 parameters:
- `input_current` (must be $\ge 30000$, nominally `33170`)
- `sc_ibat_limit` (must be $\ge 8000$, nominally `12000`)
- `chg1_current` & `chg2_current` (must be $\ge 30000$)
- `pe20` & `pe40` (must be `"1"`)
If a kernel driver routine or vendor power HAL downgraded `input_current` to baseline (1500 or 2000) while leaving `enable_sc == "1"`, `invariants_intact` returned `true`, skipping re-assertion during recurring daemon ticks.

### 1.2 CC to CV Transition & Smart Tapering Omission
Prior to this task:
1. `apply_extreme_charging()` took `_target_soc: u32` as an unused argument (leading underscore), forcing full 12000 mA even when entering hyper mode at high battery SoC ($\ge 80\%$) or high cell terminal voltage ($V_{BAT} \ge 4470\text{ mV}$).
2. In `apply_from_config()`, no CC to CV transition check was evaluated below Tier 3 (100% AutoCut). Battery states between 80% and 99% remained un-tapered in Hyper Mode, and `/dev/lynx_charging_state` was never populated with `state=tapering...`, causing `smart_tapering_active` in `status_json()` to permanently evaluate to `false`.

### 1.3 Polling Responsiveness on Cable Insertion in `native/lynxd/src/daemon/runner.rs`
In `runner.rs` lines 259–279:
When unplugged and screen off, the daemon slept for 20 seconds (40 chunks of 500 ms).
Inside the chunk loop, `ChargingRegulator::is_hardware_plugged()` was checked, but if a cable was connected while screen was off, the loop did not break. While `tick()` was called every 1 second, the outer loop remained trapped in the 20-second sleep duration until all 40 chunks elapsed, delaying full telemetry updates and transition to the 1-second primary loop.

### 1.4 Standalone Shell Fallback in `core/Charging-Controller.sh`
In `core/Charging-Controller.sh`, the script delegated 100% via `exec "$LYNXD" charging ...`. When `$LYNXD` was not compiled or not executable (e.g. minimal recovery or early boot), commands exited with code 127 and applied no sysfs overrides.

---

## 2. Logic Chain

### 2.1 Complete R3 Invariant Tuple Enforcement
To guarantee sustained $>6.0\text{A}$ net charging current during the Constant Current (CC) phase without silent kernel driver rollback, `invariants_intact` on MediaTek platforms must verify the entire R3 tuple:
$$\text{invariants\_intact} = (BN\_TestMode == 1) \land (enable\_sc == 1) \land (input\_current \ge 30000) \land (sc\_ibat\_limit \ge 8000) \land (chg1 \ge 30000) \land (chg2 \ge 30000) \land (pe20 == 1) \land (pe40 == 1)$$
Implemented in `native/lynxd/src/hardware/charging.rs` lines 1200–1220. If any node drifts or falls below these thresholds, `invariants_intact` returns `false`, causing the daemon to re-assert `Self::apply_hyper_mode()`, re-writing registers and re-applying `0o444` write-protection.

### 2.2 Pure Domain Calculation & Electrochemical CC/CV Tapering
1. **Mathematical Doubling Physics**:
   Added `calculate_pump_output_ma` and `calculate_net_battery_current_ua`:
   $$I_{PUMP} = 2 \times I_{BUS} \times \eta \quad (\eta = 0.97)$$
   $$I_{NET} = I_{PUMP} - I_{SYS}$$
   For $I_{BUS} = 3317\text{ mA}$ (step `33170`) and $I_{SYS} \approx 350\text{ mA}$:
   $$I_{PUMP} = 2 \times 3317 \times 0.97 = 6435\text{ mA}$$
   $$I_{NET} = 6435 - 350 = 6085\text{ mA} = 6,085,000\ \mu\text{A} > 6,000,000\ \mu\text{A} \ (6.0\text{ A})$$
2. **Electrochemical Boundaries (`determine_taper_state`)**:
   - $\ge 100\%$ SoC: AutoCut bypass (`("bypass_100", 0)`).
   - $95\%\text{--}99\%$ SoC: Step down to 750 mA (`("tapering_95", 750)`).
   - $80\%\text{--}94\%$ SoC (or $V_{BAT} \ge 4470\text{ mV}$): Step down to 3500 mA (`("tapering_80", 3500)`), disengaging 2:1 pump (`enable_sc = 0`) to preserve $V_{BAT} \le 4.50\text{V}$ ceiling.
   - $<80\%$ SoC and $V_{BAT} < 4470\text{ mV}$: Full peak CC phase (`("hyper", 12000)`).
3. **Integration in `apply_from_config()`**:
   Inserted Tier 6 evaluation when `cfg.smart_tapering_enabled` is `true`. Writes `state=tapering_80` or `state=tapering_95` to `/dev/lynx_charging_state`, which activates `"smart_tapering_active": true` in `status_json()`.
4. **Integration in `apply_extreme_charging()`**:
   Utilizes `target_soc` and battery voltage `v_clean_mv`. If `cur_cap >= target_soc` or `cur_cap >= 80` or `v_clean_mv >= 4470`, clamps current to $\le 3500\text{ mA}$ (or 750 mA), sets `enable_sc = 0`, and writes tapered limits, preventing cell over-voltage on high-SoC entry.

### 2.3 Edge-Triggered Plug Detection in `runner.rs`
In `native/lynxd/src/daemon/runner.rs`, inside the 500 ms sleep loop:
```rust
let currently_plugged = ChargingRegulator::is_hardware_plugged();
if currently_plugged != is_plugged {
    if !currently_plugged {
        ChargingRegulator::handle_disconnect();
    }
    break;
}
```
When a charger is plugged in while screen is off, `currently_plugged != is_plugged` immediately breaks the 500ms chunk loop, allowing the daemon to transition from 20-second deep sleep to 1-second high-frequency polling within $\le 500\text{ ms}$.

### 2.4 Standalone Direct Sysfs Fallbacks in `core/Charging-Controller.sh`
Added `fallback_hyper` and `fallback_disconnect` shell routines with `write_node_lock` (chmod 0444) and `unlock_node` (chmod 0666). If `[ -x "$LYNXD" ]`, the script delegates via `exec "$LYNXD" ...`. If `$LYNXD` is not executable, the shell fallbacks directly enforce all R1, R2, R3, and R4 sysfs states and OEM baselines.

---

## 3. Caveats

1. **Host-Side Mock Sandbox vs. Live Device Testing**:
   Unit tests (`cargo test`) and the 41-case opaque-box E2E test suite execute in hermetic environments. On live physical hardware (e.g. Infinix Note 11S MT6781), net current $>6.0\text{A}$ requires a physical RFC fast charger (33W/45W/68W) or USB-PD PPS adapter capable of negotiating $\ge 3000\text{ mA}$ input at $\approx 9.3\text{V}$.
2. **Silicon OVP Independence**:
   No user-space sysfs writes ever touch the raw I2C regmap (`rt_regmap_rt9759`), ensuring silicon-level `vbatovp` ($4.928\text{V}$) and `vbus_max_ovp` ($11.0\text{V}$) remain 100% under autonomous hardware comparator protection.

---

## 4. Conclusion

All requirements for Milestone 2 (Requirement R3: Continuous Maximum Pump Current Enforcement) are fully implemented and verified:
1. `invariants_intact` monitors all 8 parameters of the R3 tuple on MediaTek (`BN_TestMode`, `enable_sc`, `input_current >= 30000`, `sc_ibat_limit >= 8000`, `chg1_current >= 30000`, `chg2_current >= 30000`, `pe20 == 1`, `pe40 == 1`).
2. CC to CV Smart Tapering at $\ge 80\%$ SoC (or $V_{BAT} \ge 4470\text{ mV}$) cleanly steps down to 3500 mA (`state=tapering_80`), 750 mA (`state=tapering_95`), and 0 mA bypass (`state=bypass_100`).
3. `target_soc` is enforced in `apply_extreme_charging()` to prevent high-SoC over-voltage.
4. `runner.rs` immediately shifts from 20s screen-off sleep to 1s polling upon cable connection via edge-triggered break.
5. `Charging-Controller.sh` provides complete standalone sysfs fallback routines for `hyper` and `disconnect`.
6. 100% of native cargo unit tests (50/50) and E2E tests (41/41) pass.

---

## 5. Verification Method

### 5.1 Cargo Check & Unit Tests
Run inside `native/lynxd`:
```cmd
cargo test --target x86_64-pc-windows-msvc -- hardware::charging
```
**Output**:
```
running 7 tests
test hardware::charging::tests::test_auxadc_conversion_table ... ok
test hardware::charging::tests::test_cc_cv_soc_boundaries_and_tapering_thresholds ... ok
test hardware::charging::tests::disconnect_resets_only_charging_protocol_nodes ... ok
test hardware::charging::tests::test_rt9759_pump_math_and_net_current ... ok
test hardware::charging::tests::test_session_nodes_contains_r1_nodes ... ok
test hardware::charging::tests::test_disconnect_resets_contains_r1_and_game_nodes ... ok
test hardware::charging::tests::test_node_permission_helpers ... ok

test result: ok. 7 passed; 0 failed; 0 ignored; 0 measured; finished in 0.00s
```

Run all unit tests in native daemon:
```cmd
cargo test --target x86_64-pc-windows-msvc
```
**Output**:
```
test result: ok. 50 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out; finished in 0.01s
```

### 5.2 Opaque-Box E2E Test Suite
Run from repository root `e:\Data\GitHub\Lynx`:
```cmd
python tests/e2e/run_e2e_tests.py --tier 1
python tests/e2e/run_e2e_tests.py
```
**Output**:
```
================================================================================
  TEST EXECUTION SUMMARY
================================================================================
  Total Tests : 41
  Passed      : 41
  Failed      : 0
  Errors      : 0
  Skipped     : 0
  Duration    : 1.318 seconds

  JSON Report saved to: test-results/e2e_results.json
  JUnit XML saved to  : test-results/e2e_junit.xml

  [OVERALL: PASS] 100% of charging throttle elimination tests passed!
```

### 5.3 Invalidation Conditions
- Any drift of `input_current < 30000`, `sc_ibat_limit < 8000`, `enable_sc != 1`, or `BN_TestMode != 1` failing to re-assert Hyper Mode.
- Failure to taper to 3500 mA / 750 mA when battery capacity reaches $\ge 80\%$ or $\ge 95\%$.
- Any battery cell terminal voltage exceeding the $4.50\text{V}$ ceiling ($> 4,500,000\ \mu\text{V}$).
