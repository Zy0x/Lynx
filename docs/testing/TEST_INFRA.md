# TEST_INFRA.MD — Lynx Universal Fast Charging Subsystem Test Infrastructure
> **Component**: Lynx Universal Charging Throttle Elimination Subsystem  
> **Status**: Ready & Operational  
> **Test Framework**: Automated Opaque-Box E2E Testing Engine (`tests/e2e/`)  
> **Execution Targets**: Dual-Mode (Hermetic Virtual Sysfs Sandbox & Live Device ADB)  

---

## 1. Overview & Objectives

The test infrastructure provides an automated, opaque-box, reproducible end-to-end verification framework for the **Lynx Universal Charging Throttle Elimination** project. It guarantees continuous verification of all requirements set forth in `ORIGINAL_REQUEST.md` and `PROJECT.md`:
1. **R1**: Screen-on charging throttle bypass (sustained $I_{\text{ta}} \ge 3000\text{ mA}$ with active display).
2. **R2**: Thermal trip-point & algorithm neutralization ($T_{\text{pcb}}$ Level 7 at 45°C and $T_{\text{bat}}$ Level 6/8 derating bypass).
3. **R3**: Continuous maximum pump current enforcement ($>6,000,000\mu\text{A}$ / $>6.0\text{ A}$ net battery current during CC phase).
4. **R4**: Disconnect fail-safe restoration within 2 seconds ($<2.0\text{s}$) and silicon over-voltage protection (OVP) integrity.

---

## 2. 4-Tier Test Architecture

The test suite is structured into 4 hierarchical tiers designed to prevent regression, detect boundary violations, and ensure real-world stability:

```
tests/e2e/
├── framework.py                       # Unified test engine, BaseTarget, MockSandboxTarget, LiveAdbTarget
├── run_e2e_tests.py                   # Master CLI runner with filtering & reporting
├── run_e2e_tests.sh                   # POSIX / Android shell wrapper
│
├── tier1_feature_coverage/            # Tier 1: Feature Coverage (>=5 test cases per feature in isolation)
│   ├── test_r1_screen_on_throttle.py  # 6 tests for R1 Screen-On throttle bypass
│   ├── test_r2_thermal_neutralization.py # 6 tests for R2 Thermal trip-point neutralization
│   ├── test_r3_pump_current.py        # 6 tests for R3 RT9759 2:1 pump current enforcement
│   └── test_r4_disconnect_failsafe.py # 6 tests for R4 Disconnect fail-safe & OVP
│
├── tier2_boundaries/                  # Tier 2: Boundary & Corner Cases (8 test cases)
│   └── test_boundary_corner_cases.py  # SoC boundaries, float unplug, exact thermal trip thresholds
│
├── tier3_cross_feature/               # Tier 3: Cross-Feature Combinations (5 test cases)
│   └── test_cross_feature_matrix.py   # Multi-variable stress, mode pipeline, daemon contention
│
└── tier4_real_world/                  # Tier 4: Real-World Workload Scenarios (4 test cases)
    └── test_real_world_scenarios.py   # 3D Gaming, 4K Video, Overnight bypass latching
```

Total Automated Tests: **41 Tests** across all 4 tiers.

---

## 3. Requirements Mapping & Derivation Matrix

Every test case in the suite is mapped to an authoritative source of expected outputs:

| Req | Test Count | Target Specification | Authoritative Source |
| :--- | :---: | :--- | :--- |
| **R1** | 6 | $I_{\text{ta}} \ge 3000\text{ mA}$ sustained with `fb_on = 1`, `lcd_on > 0`; `BN_TestMode = 1` | `ORIGINAL_REQUEST.md § R1 & Acceptance Criteria § 28`; `spec_miner_survey_2/handoff.md § 1.1-1.2` |
| **R2** | 6 | Neutralize $T_{\text{pcb}}$ Level 7 (1500mA derate at 45°C), $T_{\text{bat}}$ Level 6 (750mA derate at 42°C), ABCCT cdev56 lock 0 | `ORIGINAL_REQUEST.md § R2 & Acceptance Criteria § 29`; `PROJECT.md § Feature 2-4`; `spec_miner_survey_2/handoff.md § 1.1-1.3` |
| **R3** | 6 | RT9759 2:1 pump active (`enable_sc = 1`), `input_current = 33170`, `sc_ibat_limit >= 8000`, `current_now > 6,000,000 uA` | `ORIGINAL_REQUEST.md § R3 & Acceptance Criteria § 27`; `PROJECT.md § Architecture line 29`; `explorer_survey_3/handoff.md § 1.1-1.2` |
| **R4** | 6 | Silicon hardware OVP intact ($V_{\text{bat}} \le 4.50\text{V}$); Disconnect detection $<500\text{ms}$; Full reversion $<2.0\text{s}$; Zero leakage | `ORIGINAL_REQUEST.md § R4 & Acceptance Criteria § 30`; `PROJECT.md § Feature 6-8`; `explorer_survey_3/handoff.md § 1.3-1.5, § 2` |
| **T2** | 8 | SoC transitions (<80% CC vs >80% CV), capacitor float, strict 2s timing, exact thermal points | `spec_miner_survey_2/handoff.md § 3 Caveat 1`; `explorer_survey_3/handoff.md § 1.4` |
| **T3** | 5 | Screen + thermal load, full mode pipeline (Cool/Std/Hyper/Unplug), daemon contention | `AGENTS.md § 4.1-4.2`; `PROJECT.md § Interface Contracts` |
| **T4** | 4 | Heavy 3D Gaming simulation (120 FPS), 4K video recording thermals, overnight bypass | `ORIGINAL_REQUEST.md § R1, R3`; `native/lynxd/src/hardware/charging.rs lines 1187-1193` |

---

## 4. Complete Test Catalog

### Tier 1: Feature Coverage (24 Test Cases)

#### Feature 1: Screen-On Charging Throttle Bypass (R1)
1. `TC-T1-F1-01`: Baseline Screen-Off Fast Charging Negotiation (`lcd_on=0`, `fb_on=0`, `ita=3000mA` negotiated).
2. `TC-T1-F1-02`: Unbypassed Screen-On Throttling Verification (`fb_on=1`, `lcd_on=241` clamped to `game_limit_ita=1500mA`).
3. `TC-T1-F1-03`: Lynx Hyper Mode Screen-On Throttle Bypass (`BN_TestMode=1`, `BatteryNotify=0`, `ita >= 3000mA` sustained).
4. `TC-T1-F1-04`: Display Refresh Rate Variation Under Fast Charging (60Hz, 90Hz, 120Hz display modes under active hyper charging).
5. `TC-T1-F1-05`: Rapid Screen On/Off Toggle Hysteresis Protection (5 cycles in 500ms intervals maintaining hyper mode lock).
6. `TC-T1-F1-06`: Interactive Touch Event Stream with Sustained Charging (continuous UI touch input with sustained adapter current).

#### Feature 2: Thermal Trip-Point & Algorithm Neutralization (R2)
7. `TC-T1-F2-01`: PCB Thermal Derating Neutralization (`tpcb` Level 7 / 45°C override via `pcb_thermal_debug`).
8. `TC-T1-F2-02`: Battery Thermal Level 6/8 Neutralization (`tbat` Level 6 / 42°C override via `Battery_Temperature = 28` locked `0444`).
9. `TC-T1-F2-03`: MediaTek ABCCT PID Controller Neutralization (`/proc/driver/thermal/clabcct` disabled, `cooling_device56 (abcct)` locked to 0).
10. `TC-T1-F2-04`: DV2 Direct Pump Thermal Table Neutralization via `dv2_debug`.
11. `TC-T1-F2-05`: Userspace Thermal Daemon Neutralization (`thermalloadalgod` / `thermal-engine` suspended via `SIGSTOP`).
12. `TC-T1-F2-06`: Multi-Zone High-Temperature Stress Sustained Charging (simultaneous PCB 48°C, Bat 44°C, DVCHG 75°C without current degradation).

#### Feature 3: Continuous Maximum Pump Current Enforcement (R3)
13. `TC-T1-F3-01`: RT9759 2:1 Switched-Capacitor Pump Hardware Engagement (`enable_sc = 1`).
14. `TC-T1-F3-02`: Maximum Adapter Input Current Lock (`input_current = 33170`, `Ibus >= 3000 mA`).
15. `TC-T1-F3-03`: RT9759 sc_ibat_limit Register Enforcement (`sc_ibat_limit >= 8000`, `12000`).
16. `TC-T1-F3-04`: Net Battery Current Delivery (`current_now > 6,000,000 uA` / > 6.0 A during CC phase).
17. `TC-T1-F3-05`: 2:1 Switched-Capacitor Doubling Ratio Verification ($I_{\text{BAT}} \approx 2 \times I_{\text{BUS}} \times 0.97$).
18. `TC-T1-F3-06`: Continuous High Current Telemetry Sampling Window (unwavering high current over multi-sample window).

#### Feature 4: Disconnect Fail-Safe Restoration & OVP Integrity (R4)
19. `TC-T1-F4-01`: Silicon OVP Independence & Voltage Ceiling Integrity (no writes to RT9759 regmap OVP registers, $V_{\text{bat}} \le 4.50\text{V}$).
20. `TC-T1-F4-02`: Fast Triple-Vector Disconnect Detection ($<500\text{ms}$ response upon cable removal).
21. `TC-T1-F4-03`: Complete Hardware Node Reversion within 2 Seconds (`enable_sc = 0`, `BN_TestMode = 0`, `sw_jeita = 1`, `input_current = 24576`).
22. `TC-T1-F4-04`: Thermal Safety & Daemon Unfreezing on Disconnect (`pcb_thermal_debug`, `Battery_Temperature = 65535` unlocked, `SIGCONT`).
23. `TC-T1-F4-05`: Zero Parasitic Quiescent Current & Deep Sleep Shield ($I_Q < 5\mu\text{A}$, normal deep sleep resume).
24. `TC-T1-F4-06`: Repeated Connect-Disconnect Cycle Stress (10 consecutive insertions/removals with zero leaked locks).

---

### Tier 2: Boundary & Corner Cases (8 Test Cases)
25. `TC-T2-B01`: SoC 80% Boundary: CC Phase (>6A) to CV Phase Tapering Transition.
26. `TC-T2-B02`: High SoC Initial Attachment (>85% SoC).
27. `TC-T2-B03`: Critical Low SoC (<5%) Pre-Charge to Direct Pump Handoff.
28. `TC-T2-B04`: Decoupling Capacitor Voltage Float Disconnect Boundary (VBUS floats >3.5V for 1.2s while charger_type drops to 0).
29. `TC-T2-B05`: Strict 2.0-Second Unplug Restoration Time Boundary ($<2000\text{ms}$).
30. `TC-T2-B06`: Exact Thermal Boundary Transitions (40.0°C, 42.0°C, 45.0°C).
31. `TC-T2-B07`: Extreme Hardware Temperature Safety Cutoff (>55.0°C emergency guard).
32. `TC-T2-B08`: Non-RFC Standard 5V Charger Fallback Verification (direct pump stays disabled).

---

### Tier 3: Cross-Feature Combinations (5 Test Cases)
33. `TC-T3-C01`: Simultaneous Screen-On and Multi-Domain Thermal Stress.
34. `TC-T3-C02`: Full Charging Mode Pipeline: Cool -> Standard -> Hyper -> Disconnect.
35. `TC-T3-C03`: Gaming Workload with Mid-Session Cable Unplug & Re-Insertion.
36. `TC-T3-C04`: Multi-Daemon Mutex Lock Contention Mitigation.
37. `TC-T3-C05`: Rapid Charge Pump Modulation (Hyper -> Cool -> Hyper in <5s).

---

### Tier 4: Real-World Scenarios (4 Test Cases)
38. `TC-T4-R01`: Heavy 3D Gaming Simulation with Sustained >6.0A Fast Charging (120 FPS display, continuous CC phase).
39. `TC-T4-R02`: 4K Video Recording & Active Camera Thermal Dissipation.
40. `TC-T4-R03`: Overnight Lifecycle: Fast Charge -> Smart Tapering -> 100% Bypass Latch.
41. `TC-T4-R04`: Hot Tropical Ambient (38°C Ambient + Fast Charge Heat).

---

## 5. Execution Environments

### Mode 1: Virtual Sysfs Mock Sandbox (Hermetic / CI)
- Completely isolated and self-contained temporary directory mimicking Android sysfs/proc tree.
- No root privileges, no physical phone, and no ADB required.
- Cross-platform compatible with Windows (NTFS path colon translation) and Linux/POSIX.
- Ideal for fast local development and automated CI pipelines.

### Mode 2: Live Android Device Target (ADB)
- Connects to physical root test device (Infinix-X698 MT6781) via `adb shell "su -c ..."`
- Reads physical sysfs nodes, inspects kernel `dmesg`, and executes `/data/adb/modules/Lynx/system/bin/lynxd`.
- Validates real hardware registers and Coulomb counter telemetry.

---

## 6. How to Run the Tests

```bash
# 1. Run all 41 test cases (Default: Hermetic Sandbox)
python tests/e2e/run_e2e_tests.py

# 2. Run with verbose diagnostic output
python tests/e2e/run_e2e_tests.py -v

# 3. Filter by Tier
python tests/e2e/run_e2e_tests.py --tier 1
python tests/e2e/run_e2e_tests.py --tier 2
python tests/e2e/run_e2e_tests.py --tier 3
python tests/e2e/run_e2e_tests.py --tier 4

# 4. Filter by Feature
python tests/e2e/run_e2e_tests.py --feature r1
python tests/e2e/run_e2e_tests.py --feature r2
python tests/e2e/run_e2e_tests.py --feature r3
python tests/e2e/run_e2e_tests.py --feature r4

# 5. Run on Live Android Device
python tests/e2e/run_e2e_tests.py --target live
python tests/e2e/run_e2e_tests.py --target live --serial 192.168.1.13:5555

# 6. Run via Shell Script
./tests/e2e/run_e2e_tests.sh
```

---

## 7. Reports & CI Integration

The test runner outputs standard structured reports:
- **JSON**: `test-results/e2e_results.json` (contains detailed state dictionary, exact duration per test, and failure causes).
- **JUnit XML**: `test-results/e2e_junit.xml` (seamless integration into GitHub Actions, GitLab CI, and Jenkins).
