# BRIEFING — 2026-10-14T15:03:30Z

## Mission
Investigate Requirement R3 regarding net battery charging current telemetry, 2:1 doubling physics/math, Constant Current (CC) phase (<80% SoC) and Constant Voltage (CV) handoff, existing native/lynxd handling, and concrete implementation recommendations for Worker M2.

## 🔒 My Identity
- Archetype: explorer
- Roles: Battery Current Explorer
- Working directory: e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m2_2
- Original parent: 4a93ea5f-bf3a-41d2-8075-760f372161d2
- Milestone: Milestone 2 (Continuous Maximum Pump Current Enforcement)

## 🔒 Key Constraints
- Read-only investigation — do NOT implement in source code (only write reports and briefing in my folder)
- Follow Handoff Protocol (5 components)
- Verify claims against actual code, tests, and hardware reference

## Current Parent
- Conversation ID: 4a93ea5f-bf3a-41d2-8075-760f372161d2
- Updated: 2026-10-14T15:03:30Z

## Investigation State
- **Explored paths**:
  - `ORIGINAL_REQUEST.md` & `PROJECT.md`
  - `worker_m1/handoff.md`
  - `explorer_survey_3/handoff.md` & `spec_miner_survey_2/handoff.md`
  - `tests/e2e/tier1_feature_coverage/test_r3_pump_current.py`
  - `tests/e2e/tier2_boundaries/test_boundary_corner_cases.py`
  - `tests/e2e/tier3_cross_feature/test_cross_feature_matrix.py`
  - `tests/e2e/tier4_real_world/test_real_world_scenarios.py`
  - `native/lynxd/src/hardware/charging.rs`
  - `native/lynxd/src/daemon/runner.rs`
  - `native/lynxd/src/state/config_store.rs`
  - `native/lynxd/src/main.rs`
  - `core/Charging-Controller.sh`
- **Key findings**:
  1. Telemetry at `/sys/class/power_supply/battery/current_now` is in $\mu\text{A}$, read across fuel gauge shunt $R_{SENSE}$, measuring NET current ($I_{NET} = I_{PUMP} - I_{SYS}$).
  2. 2:1 Switched Capacitor pump converts $9\text{V} / 3300\text{mA}$ input ($30.6\text{W}$) into battery terminal current ($I_{PUMP} = 2 \times I_{BUS} \times 0.97 \approx 6402\text{mA}$). With active screen/system load ($I_{SYS} \approx 350\text{–}400\text{mA}$), net current is $6035\text{–}6052\text{mA}$, exceeding $6,000,000\ \mu\text{A}$.
  3. Below 80% SoC ($V_{BAT} < 4.47\text{V}$), cell is in Constant Current (CC) phase accepting $>6\text{A}$ without overvoltage. Above 80% SoC, internal polarization dictates Constant Voltage (CV) saturation and current tapering to avoid tripping $V_{BAT\_MAX} \le 4.50\text{V}$ and hardware OVP (`vbatovp: 4.65–4.928V`).
  4. In `native/lynxd/src/hardware/charging.rs`, `apply_from_config()` lacks smart tapering integration in Hyper Mode; `high_current_target_percent` and `smart_tapering_enabled` are parsed in `config_store.rs` but ignored during hyper mode loop ticks.
  5. Concrete recommendations drafted for Worker M2 to enforce R3 charge pump nodes, integrate smart tapering and voltage ceiling guard, and add unit tests.
- **Unexplored areas**: None within M2-2 scope.

## Key Decisions Made
- Structure comprehensive handoff report with exact formulas, sysfs paths, line numbers, and concrete code blueprints for Worker M2.

## Artifact Index
- DISPATCH.md — Received dispatch instructions
- plan.md — High-level plan
- handoff.md — Comprehensive analysis report (to be written)
- progress.md — Liveness heartbeat
