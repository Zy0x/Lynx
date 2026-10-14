# Lynx Universal - Fast Charging Throttle Elimination E2E Test Suite

Automated, opaque-box end-to-end (E2E) testing suite for verifying requirements R1, R2, R3, and R4 of the Lynx Universal Charging Subsystem.

## 1. Requirements Matrix

| Requirement | Description | Target Specification | Authoritative Source |
| :--- | :--- | :--- | :--- |
| **R1** | Screen-On Charging Throttle Bypass | Sustained `ita >= 3000 mA` with display active (`fb_on = 1`, `lcd_on > 0`) | `ORIGINAL_REQUEST.md § R1 & Acceptance Criteria § 28` |
| **R2** | Thermal Trip-Point Neutralization | Neutralize Level 7 `tpcb` (45°C) and Level 6 `tbat` (42°C), ABCCT cooling devices | `ORIGINAL_REQUEST.md § R2 & Acceptance Criteria § 29` |
| **R3** | Continuous Maximum Pump Current | Net battery current `> 6,000,000 uA` (>6.0 A) in CC phase (<80% SoC) via RT9759 2:1 pump | `ORIGINAL_REQUEST.md § R3 & Acceptance Criteria § 27` |
| **R4** | Disconnect Fail-Safe & OVP | Disconnect reversion strictly `< 2.0s` with zero parasitic leakage, hardware OVP intact | `ORIGINAL_REQUEST.md § R4 & Acceptance Criteria § 30` |

---

## 2. 4-Tier Test Architecture

```
tests/e2e/
├── framework.py                       # Unified test framework, BaseTarget, MockSandboxTarget, LiveAdbTarget
├── run_e2e_tests.py                   # Master CLI runner with filtering & reporting
├── run_e2e_tests.sh                   # POSIX shell executable wrapper
├── tier1_feature_coverage/            # Tier 1: Feature Coverage (>=5 test cases per feature in isolation)
│   ├── test_r1_screen_on_throttle.py  # 6 tests for R1 Screen-On throttle bypass
│   ├── test_r2_thermal_neutralization.py # 6 tests for R2 Thermal trip-point neutralization
│   ├── test_r3_pump_current.py        # 6 tests for R3 RT9759 2:1 pump current enforcement
│   └── test_r4_disconnect_failsafe.py # 6 tests for R4 Disconnect fail-safe & OVP
├── tier2_boundaries/                  # Tier 2: Boundary & Corner Cases (8 test cases)
│   └── test_boundary_corner_cases.py  # SoC boundaries, float unplug, exact thermal trip thresholds
├── tier3_cross_feature/               # Tier 3: Cross-Feature Combinations (5 test cases)
│   └── test_cross_feature_matrix.py   # Multi-variable stress, mode pipeline, daemon contention
└── tier4_real_world/                  # Tier 4: Real-World Workload Scenarios (4 test cases)
    └── test_real_world_scenarios.py   # 3D Gaming, 4K Video, Overnight bypass latching
```

Total Test Cases: **41 Automated Tests**

---

## 3. How to Run the Tests

### Fast Hermetic Sandbox (No root or Android device required):
```bash
# Run full suite
python tests/e2e/run_e2e_tests.py

# Run with verbose output and test timing
python tests/e2e/run_e2e_tests.py -v

# Run via bash wrapper
./tests/e2e/run_e2e_tests.sh
```

### Filtering by Tier or Feature:
```bash
# Run only Tier 1 (Feature Coverage)
python tests/e2e/run_e2e_tests.py --tier 1

# Run only Tier 2 (Boundary & Corner Cases)
python tests/e2e/run_e2e_tests.py --tier 2

# Run only R1 (Screen-On Throttle Bypass)
python tests/e2e/run_e2e_tests.py --feature r1

# Run only R3 (Pump Current)
python tests/e2e/run_e2e_tests.py --feature r3
```

### Live Android Device (ADB Mode):
```bash
# Automatically detects connected Infinix-X698 test device
python tests/e2e/run_e2e_tests.py --target live

# Specify explicit ADB serial / IP
python tests/e2e/run_e2e_tests.py --target live --serial 192.168.1.13:5555
```

---

## 4. Test Reports & Artifacts
The test runner automatically exports results into standard formats:
- **JSON Report**: `test-results/e2e_results.json`
- **JUnit XML Report**: `test-results/e2e_junit.xml` (compatible with CI engines like GitHub Actions and GitLab CI).
