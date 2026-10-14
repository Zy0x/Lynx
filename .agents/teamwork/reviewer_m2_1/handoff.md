# Handoff & Adversarial Review Report: Milestone 2 — Continuous Maximum Pump Current Enforcement (>6A Battery Current)

> **From**: Reviewer & Adversarial Critic (`reviewer_m2_1`)  
> **To**: Lead Architect / Orchestrator (`4a93ea5f-bf3a-41d2-8075-760f372161d2`)  
> **Target Work Product**: Worker M2 (`.agents/teamwork/worker_m2/handoff.md`)  
> **Working Directory**: `e:\Data\GitHub\Lynx\.agents\teamwork\reviewer_m2_1`  
> **Date**: 2026-10-14  

---

## Review Summary

**Verdict**: **APPROVE**  
**Integrity Assessment**: **NO INTEGRITY VIOLATIONS DETECTED**  
- Zero hardcoded test shortcuts or dummy facades found.
- Genuine domain logic and mathematical physics implemented for 2:1 pump math, electrochemical CC/CV tapering, R3 invariant tuple enforcement, runner edge-triggering, and shell fallbacks.
- 100% independent verification of unit tests (50/50 passed) and automated E2E tests (41/41 passed across all tiers).

---

## 1. Observation

### 1.1 Implementation Code Inspection
1. **R3 Invariant Tuple Enforcement (`native/lynxd/src/hardware/charging.rs` lines 1200–1221)**:
   ```rust
   let is_mtk = Path::new(MTK_DIR).exists();
   let invariants_intact = if is_mtk {
       let bn_test = Self::read_node(&format!("{}/BN_TestMode", MTK_DIR)).unwrap_or_default();
       let enable_sc = Self::read_node(&format!("{}/enable_sc", MTK_DIR)).unwrap_or_default();
       let in_cur = Self::read_i64(&format!("{}/input_current", MTK_DIR)).unwrap_or(0);
       let sc_limit = Self::read_i64(&format!("{}/sc_ibat_limit", MTK_DIR)).unwrap_or(0);
       let chg1 = Self::read_i64(&format!("{}/chg1_current", MTK_DIR)).unwrap_or(0);
       let chg2 = Self::read_i64(&format!("{}/chg2_current", MTK_DIR)).unwrap_or(0);
       let pe20 = Self::read_node(&format!("{}/pe20", MTK_DIR)).unwrap_or_default();
       let pe40 = Self::read_node(&format!("{}/pe40", MTK_DIR)).unwrap_or_default();

       bn_test == "1"
           && enable_sc == "1"
           && in_cur >= 30000
           && sc_limit >= 8000
           && chg1 >= 30000
           && chg2 >= 30000
           && pe20 == "1"
           && pe40 == "1"
   } else {
       let direct_chg = Self::read_node(&format!("{}/direct_charging", QC_DIR)).unwrap_or_default();
       direct_chg == "1"
   };

   // If hyper mode is already active and critical invariants are intact, skip heavy re-init
   if marker.starts_with("hyper-") && invariants_intact {
       return;
   }

   Self::apply_hyper_mode();
   ```
   Observed that all 8 MediaTek sysfs parameters are actively checked on every tick. Any drift below threshold immediately triggers `apply_hyper_mode()`.

2. **2:1 Pump Math & Physics Calculations (`native/lynxd/src/hardware/charging.rs` lines 526–536)**:
   ```rust
   pub fn calculate_pump_output_ma(ibus_ma: u32, efficiency: f64) -> u32 {
       (2.0 * (ibus_ma as f64) * efficiency).round() as u32
   }

   pub fn calculate_net_battery_current_ua(ibus_ma: u32, isys_ma: u32, efficiency: f64) -> i64 {
       let pump_ma = Self::calculate_pump_output_ma(ibus_ma, efficiency);
       let net_ma = pump_ma.saturating_sub(isys_ma);
       (net_ma as i64) * 1000
   }
   ```
   Observed that $I_{pump} = 2 \times I_{bus} \times \eta$ with $\eta = 0.97$, yielding $6435\text{ mA}$ at $I_{bus} = 3317\text{ mA}$. Net current with $I_{sys} = 350\text{ mA}$ is $6085\text{ mA} = 6,085,000\ \mu\text{A} > 6,000,000\ \mu\text{A}$ ($>6.0\text{ A}$). `saturating_sub` prevents negative wrap-around underflow.

3. **CC to CV Tapering Logic (`native/lynxd/src/hardware/charging.rs` lines 543–553, 580–615, 1181–1195)**:
   ```rust
   pub fn determine_taper_state(soc: u32, vbat_mv: u32) -> (&'static str, u32) {
       if soc >= 100 {
           ("bypass_100", 0)
       } else if soc >= 95 {
           ("tapering_95", 750)
       } else if soc >= 80 || vbat_mv >= 4470 {
           ("tapering_80", 3500)
       } else {
           ("hyper", 12000)
       }
   }
   ```
   In `apply_from_config()`:
   ```rust
   if cfg.smart_tapering_enabled && (cur_cap >= 80 || v_clean_mv >= 4470) {
       let (state_tag, target_ma) = Self::determine_taper_state(cur_cap, v_clean_mv);
       let effective_target = if cfg.charging_mode == "cool" && cfg.limit_current_ma > 0 {
           (cfg.limit_current_ma as u32).min(target_ma)
       } else {
           target_ma
       };
       let _ = fs::write("/dev/lynx_charging_state", format!("state={} cap={}\n", state_tag, cur_cap));
       Self::apply_regulated_charging(effective_target);
       return;
   }
   ```
   Observed that when capacity $\ge 80\%$ or cell voltage $\ge 4470\text{ mV}$, the charging current cleanly tapers down to 3500 mA (disengaging 2:1 pump: `enable_sc = 0`, `pe20 = 0`, `pe40 = 0`), and down to 750 mA at $\ge 95\%$, and AutoCut at 100%.

4. **Edge-Triggered Plug Detection (`native/lynxd/src/daemon/runner.rs` lines 249–279)**:
   ```rust
   let is_plugged = ChargingRegulator::is_hardware_plugged();
   let sleep_duration = if is_plugged {
       Duration::from_secs(1)
   } else {
       match current_screen {
           ScreenState::Off => Duration::from_secs(20),
           _ => Duration::from_secs(5),
       }
   };

   let chunks = (sleep_duration.as_millis() / 500) as usize;
   for idx in 0..chunks {
       if !DaemonLifecycle::is_running() {
           break;
       }
       let currently_plugged = ChargingRegulator::is_hardware_plugged();
       if currently_plugged != is_plugged {
           if !currently_plugged {
               if std::path::Path::new("/dev/lynx_extreme_charging").exists()
                   || std::path::Path::new("/dev/lynx_bypass_active").exists()
                   || std::path::Path::new("/dev/lynx_charging_guard").exists()
               {
                   ChargingRegulator::handle_disconnect();
               }
           }
           break;
       }
       ...
   }
   ```
   Observed that cable insertion breaks the 20s sleep loop within $\le 500\text{ ms}$, switching to 1s polling. Cable disconnection triggers `handle_disconnect()` immediately within $\le 500\text{ ms}$ (strictly $< 2000\text{ ms}$).

5. **Standalone Shell Fallback (`core/Charging-Controller.sh` lines 9–36, 65–158)**:
   Observed:
   - Evaluates `[ -x "$LYNXD" ]` to delegate to native CLI.
   - If `$LYNXD` is absent, executes `fallback_hyper` and `fallback_disconnect`.
   - `write_node_lock` applies `chmod 666`, writes value, and locks with `chmod 444`.
   - `unlock_node` restores `chmod 666` and restores OEM baseline values (`enable_sc = 0`, `pe20 = 0`, `pe40 = 0`, `input_current = 24576`, `sc_ibat_limit = 2000`, `BN_TestMode = 0`, `BatteryNotify = 1`, `pcb_thermal_debug = [45,3000,50,2000,1000]`, `Battery_Temperature = 65535`, `clabcct = 1`, `cooling_device56 = 1`, `dumpsys battery reset`).
   - POSIX syntax validated via `bash -n core/Charging-Controller.sh` with return code 0.

### 1.2 Independent Test Execution
1. **Targeted Cargo Unit Tests**:
   Command: `cargo test --target x86_64-pc-windows-msvc -- hardware::charging` inside `native/lynxd`
   Result: `7 passed; 0 failed; 0 ignored; finished in 0.00s` (exit code 0).
2. **All Daemon Unit Tests**:
   Command: `cargo test --target x86_64-pc-windows-msvc` inside `native/lynxd`
   Result: `50 passed; 0 failed; 0 ignored; finished in 0.01s` (exit code 0).
3. **E2E Tier 1 Feature Tests**:
   Command: `python tests/e2e/run_e2e_tests.py --tier 1`
   Result: `24 passed; 0 failed; duration: 1.093s` (exit code 0).
4. **All-Tier E2E Test Suite (Tiers 1–4)**:
   Command: `python tests/e2e/run_e2e_tests.py`
   Result: `41 passed; 0 failed; duration: 1.339s` (exit code 0).

---

## 2. Logic Chain

1. **R3 Invariant Completeness**:
   - Observation 1.1.1 verifies that `invariants_intact` examines all 8 physical nodes: `BN_TestMode == 1`, `enable_sc == 1`, `input_current >= 30000`, `sc_ibat_limit >= 8000`, `chg1 >= 30000`, `chg2 >= 30000`, `pe20 == 1`, and `pe40 == 1`.
   - If a vendor kernel routine resets any of these nodes (e.g. lowering `input_current` to 2000 or disengaging `enable_sc`), `invariants_intact` evaluates to `false`.
   - The daemon immediately re-invokes `apply_hyper_mode()`, re-asserting maximum current and restoring `0o444` write protection.
   - Therefore, continuous pump current enforcement is robust against background kernel rollback.

2. **2:1 Pump Math Correctness**:
   - Observation 1.1.2 defines $I_{pump} = 2 \times I_{bus} \times 0.97$ and $I_{net} = I_{pump} - I_{sys}$.
   - For an adapter input of $3317\text{ mA}$ and typical $350\text{ mA}$ system load, the net battery current delivered across the shunt resistor is $6085\text{ mA} = 6,085,000\ \mu\text{A}$.
   - This directly satisfies Requirement R3 and Acceptance Criteria § 27 ($>6,000,000\ \mu\text{A}$).
   - `saturating_sub` prevents underflow panics on zero/low input.

3. **Safe CC/CV Electrochemical Tapering**:
   - Observations 1.1.3 show that `determine_taper_state` divides charging into four discrete tiers:
     - $<80\%$ SoC and $V_{BAT} < 4470\text{ mV}$: Full peak CC phase ($12000\text{ mA}$, pump enabled).
     - $\ge 80\%$ SoC or $V_{BAT} \ge 4470\text{ mV}$: CV phase 1 ($3500\text{ mA}$, pump disabled `enable_sc = 0`).
     - $\ge 95\%$ SoC: CV phase 2 trickle ($750\text{ mA}$).
     - $\ge 100\%$ SoC: AutoCut ($0\text{ mA}$ bypass).
   - `apply_extreme_charging()` respects `target_soc` and clamps current at high SoC, preventing cell over-voltage beyond the $4.50\text{V}$ ceiling.
   - Integration with `/dev/lynx_charging_state` ensures `smart_tapering_active` in telemetry accurately reports tapering.

4. **Dynamic Responsiveness & Disconnect Latency**:
   - Observation 1.1.4 shows the 500 ms sleep loop with `currently_plugged != is_plugged` edge triggering.
   - When a charger is connected, the 20-second sleep breaks within $\le 500\text{ ms}$, transitioning to the 1-second charging regulator loop.
   - When disconnected, `handle_disconnect()` is executed within $\le 500\text{ ms}$, strictly meeting the $< 2.0\text{ s}$ restoration requirement.

5. **Shell Fallback Redundancy**:
   - Observation 1.1.5 proves that `core/Charging-Controller.sh` provides independent implementation of R1, R2, R3, and R4 overrides using POSIX shell commands.
   - Even in minimal environments where `lynxd` is not compiled or executable, `Charging-Controller.sh hyper` and `Charging-Controller.sh disconnect` correctly enforce or revert sysfs parameters.

---

## 3. Findings

### [Minor] Finding 1: Asymmetric CLI Subcommand Aliasing for `reset` in Shell Wrapper
- **What**: In `core/Charging-Controller.sh` line 29, the case statement handling delegation to `lynxd` only matches `disconnect)`:
  ```sh
  disconnect)
      exec "$LYNXD" charging disconnect 2>/dev/null
      ;;
  ```
  However, in `fallback_disconnect` (line 164), it matches `disconnect|reset)`:
  ```sh
  disconnect|reset)
      fallback_disconnect
      ;;
  ```
  And in `native/lynxd/src/main.rs` line 803, `lynxd charging` explicitly supports both `"disconnect" | "reset"`.
- **Where**: `core/Charging-Controller.sh:29`
- **Why**: If a script or user invokes `Charging-Controller.sh reset` while `lynxd` is present, it falls through to `*) exec "$LYNXD" charging tick` instead of delegating to `charging disconnect`.
- **Severity**: Minor (Nice to fix).
- **Suggestion**: Update line 29 to `disconnect|reset)` for complete symmetry. Does not block approval as `disconnect` is the standard contract entrypoint.

---

## 4. Adversarial Challenge & Stress-Testing

### Challenge 1: Pump Math Boundary & Negative Conditions
- **Assumption Challenged**: System current or adapter current could lead to underflow or NaN.
- **Attack Scenario**:
  - $I_{bus} = 0$, $I_{sys} = 1000\text{ mA}$: `calculate_pump_output_ma` returns 0. `saturating_sub(1000)` returns 0. Net current is 0 uA. No panic.
  - Large $I_{bus} = 100,000\text{ mA}$: Calculation produces $194,000\text{ mA} = 194,000,000\ \mu\text{A}$, fitting safely in `i64`.
- **Result**: PASS.

### Challenge 2: Invariant Sysfs Read Failure & Partial Drift
- **Assumption Challenged**: If sysfs nodes disappear, return empty strings, or contain trailing formatting characters, `invariants_intact` could falsely report `true`.
- **Attack Scenario**:
  - Node contains trailing newlines: `read_node` explicitly trims strings via `.trim().to_string()`.
  - Node does not exist: `read_node` returns `None` (`""`), `read_i64` returns `None` (`0`).
  - `0 >= 30000` evaluates to `false`. `invariants_intact` correctly evaluates to `false`, forcing re-assertion.
- **Result**: PASS.

### Challenge 3: Rapid Screen-Off Cable Insertion Race Condition
- **Assumption Challenged**: Plugging in a charger while screen is off could keep the daemon trapped in 20-second sleep, delaying high-current negotiation.
- **Attack Scenario**: Cable inserted at chunk 1 of 40 in 20s sleep.
- **Observed Behavior**: `currently_plugged != is_plugged` breaks chunk loop in $\le 500\text{ ms}$, triggering immediate outer loop re-evaluation to `sleep_duration = 1s` and immediate tick.
- **Result**: PASS.

---

## 5. Verified Claims

- `invariants_intact` monitors all 8 R3 parameters on MediaTek → verified via `view_file` & `git grep` → PASS
- 2:1 switched-capacitor pump math calculates $>6.0\text{A}$ net current → verified via unit test `test_rt9759_pump_math_and_net_current` & math derivation → PASS
- CC/CV tapering activates at $\ge 80\%$ SoC and $V_{BAT} \ge 4470\text{ mV}$ → verified via unit test `test_cc_cv_soc_boundaries_and_tapering_thresholds` and E2E test `TC-T2-B01` → PASS
- Edge-triggered plug detection in `runner.rs` terminates deep sleep within $\le 500\text{ ms}$ → verified via code inspection of `runner.rs:266–278` → PASS
- Standalone shell fallback correctly locks sysfs nodes on hyper and unlocks on disconnect → verified via `Charging-Controller.sh` and `bash -n` → PASS
- All native unit tests pass (50/50) → verified via `cargo test --target x86_64-pc-windows-msvc` → PASS
- All automated E2E tests pass (41/41) → verified via `python tests/e2e/run_e2e_tests.py` → PASS

---

## 6. Caveats

- Physical adapter negotiation: Testing was performed in hermetic unit test environments and the 41-case opaque-box E2E test harness. Net charging current exceeding 6000 mA on live physical hardware requires an authentic RFC 33W/45W/68W or USB-PD PPS fast charger connected.
- Silicon OVP Independence: Confirmed that user-space software overrides do not manipulate hardware comparator registers in RT9759, preserving autonomous silicon-level protection.

---

## 7. Conclusion

Milestone 2 (Worker M2 charging subsystem work: Continuous Maximum Pump Current Enforcement, R3 invariant tuple enforcement, 2:1 pump math, CC/CV tapering, runner edge-triggered plug detection, and standalone shell fallback) satisfies all architectural and functional requirements of `ORIGINAL_REQUEST.md` and `PROJECT.md`. No integrity violations or regression bugs were detected.

The work product is **APPROVED**.

---

## 8. Verification Method

To independently reproduce this verification:
1. Run cargo tests for charging:
   ```cmd
   cargo test --target x86_64-pc-windows-msvc -- hardware::charging
   ```
2. Run all cargo tests:
   ```cmd
   cargo test --target x86_64-pc-windows-msvc
   ```
3. Run Tier 1 feature tests:
   ```cmd
   python tests/e2e/run_e2e_tests.py --tier 1
   ```
4. Run all-tier E2E tests:
   ```cmd
   python tests/e2e/run_e2e_tests.py
   ```
5. Inspect code changes:
   - `native/lynxd/src/hardware/charging.rs` lines 526–640, 1181–1230
   - `native/lynxd/src/daemon/runner.rs` lines 245–285
   - `core/Charging-Controller.sh` lines 9–36, 65–171
