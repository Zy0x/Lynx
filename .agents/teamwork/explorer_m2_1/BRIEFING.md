# BRIEFING — 2026-10-14T15:12:00Z

## Mission
Investigate Requirement R3 (Continuous Maximum Pump Current Enforcement >6A Battery Current) in native/lynxd and scripts, analyzing RT9759 2:1 charge pump control, MTK/Transsion sysfs nodes, hyper charging enforcement, gaps, and recommendations for Worker M2.

## 🔒 My Identity
- Archetype: explorer
- Roles: investigator, analyzer, synthesizer
- Working directory: e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m2_1
- Original parent: 4a93ea5f-bf3a-41d2-8075-760f372161d2
- Milestone: M2

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Write only to e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m2_1
- No source code or tests in .agents/teamwork/

## Current Parent
- Conversation ID: 4a93ea5f-bf3a-41d2-8075-760f372161d2
- Updated: 2026-10-14T15:10:27Z

## Investigation State
- **Explored paths**:
  * native/lynxd/src/hardware/charging.rs (apply_extreme_charging, apply_hyper_mode, status_json, apply_from_config, reapply_lock, tick, probe_hardware_ceiling_ma)
  * native/lynxd/src/main.rs (handle_charging, charging CLI routing)
  * native/lynxd/src/daemon/runner.rs (1s plugged tick, sleep chunk loops)
  * core/Charging-Controller.sh (lynxd delegation facade)
  * tests/e2e/tier1_feature_coverage/test_r3_pump_current.py (TC-T1-F3-01 to F3-06)
  * tests/e2e/tier1_feature_coverage/test_r4_disconnect_failsafe.py
  * tests/e2e/framework.py (MockSandboxTarget sysfs topology & simulation)
  * .agents/teamwork/explorer_survey_3/handoff.md, spec_miner_survey_2/handoff.md, worker_m1/handoff.md, explorer_m2_2/handoff.md, explorer_m2_3/handoff.md
- **Key findings**:
  * RT9759 2:1 switched-capacitor charge pump operates on I2C bus 5 (`5-0066`), orchestrated by MTK charger driver via sysfs (`enable_sc`, `sc_ibat_limit`, `input_current`).
  * In Hyper Mode, `apply_extreme_charging` locks `input_current = 33170` (3317 mA input), `sc_ibat_limit = 12000`, `enable_sc = 1`, `pe20 = 1`, `pe40 = 1`, `BN_TestMode = 1`, `BatteryNotify = 0`, `sw_jeita = 0` with `0o444` permission lock.
  * Physics doubling equation: $I_{\text{net}} = 2 \times I_{\text{bus}} \times 0.97 - I_{\text{sys}} \approx 2 \times 3317 \times 0.97 - 350 = 6085\text{ mA} > 6,000,000\mu\text{A}$.
  * Identified 3 critical implementation gaps for Worker M2: (1) Hyper Mode invariant check in `apply_from_config` misses `input_current` and `sc_ibat_limit`, (2) Lack of 80% SoC CC/CV boundary transition and smart tapering above 80%, (3) Daemon chunk sleep edge-responsiveness when cable is inserted while screen is off.
- **Unexplored areas**: none (all core files and peer handoffs synthesized).

## Key Decisions Made
- Reconciled findings across all 3 M2 explorers and prior surveys.
- Structured comprehensive handoff report covering all 4 core mission requirements.

## Artifact Index
- DISPATCH.md — record of incoming dispatch messages
- BRIEFING.md — persistent working memory
- progress.md — liveness heartbeat
- handoff.md — final structured report for Orchestrator and Worker M2
