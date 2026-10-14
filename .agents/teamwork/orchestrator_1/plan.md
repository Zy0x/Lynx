# Orchestrator Execution Plan: Lynx Universal Charging Throttle Elimination

## Objective
Satisfy all requirements in `ORIGINAL_REQUEST.md`:
1. R1: Screen-On Charging Throttling Bypass (`lcd_on`, `fb_on`, `game_limit_ita` neutralization).
2. R2: Charging Thermal Throttling & Trip-Point Neutralization (`tbat`, `tpcb`, `tdvchg`, `tswchg` thermal levels).
3. R3: Continuous Maximum Pump Current Enforcement (>6A battery current, RT9759 2:1 charge pump, 3000-3300mA input).
4. R4: Fail-Safe Disconnect Restoration & Over-Voltage Protection (`vbatovp`, `vbusovp`, 2-second restore on unplug).

## Step-by-Step Plan
1. **Phase 0: Survey**
   - Dispatch 3 parallel Explorers / Spec Miners to investigate:
     - Explorer 1 (Core & Daemon Architecture): `core/Charging-Controller.sh`, `core/Smart-AI.sh`, `system/bin/Lxcore`, `native/lynxd/`.
     - Explorer 2 (MTK / Transsion Kernel Charging & Thermal HAL): `platforms/mtk/`, sysfs power_supply nodes, MTK PE/dv2 algorithms, thermal zones and trip points.
     - Explorer 3 (Hardware Driver & Disconnect Fail-Safe): RT9759 2:1 charge pump registers/sysfs, battery current sensing, unplug event detection, and OVP fail-safes.
   - Aggregate findings and create `PROJECT.md` with full Architecture, Feature Inventory, Milestones, and Interface Contracts.

2. **Phase 1: Test Suite & Infrastructure (E2E Testing Track)**
   - Dispatch E2E Testing Orchestrator / Test Writers to design:
     - Tier 1: Feature Coverage (R1-R4 isolated tests)
     - Tier 2: Boundary & Corner Cases (SoC transitions, unplug timing, thermal boundaries)
     - Tier 3: Cross-Feature Combinations (screen toggle + high thermal + heavy workload)
     - Tier 4: Real-World Workloads (gaming + fast charging scenarios)
   - Publish `TEST_INFRA.md` and `TEST_READY.md`.

3. **Phase 2: Implementation Milestones**
   - Milestone 1: Screen-On & Thermal Level Neutralization (R1 & R2)
   - Milestone 2: Charge Pump Driver & Continuous High-Current Pump Enforcement (R3)
   - Milestone 3: Fail-Safe Disconnect & Safety Restoration Engine (R4)
   - Each milestone executed via: Explorer -> Worker -> Reviewers (2) -> Challengers (2) -> Forensic Auditor -> Gate.

4. **Phase 3: E2E Verification & Adversarial Hardening**
   - Verify 100% pass on E2E test suite.
   - Phase 2 Adversarial Coverage Hardening (Tier 5) with Challengers.

5. **Phase 4: Synthesis, Documentation & Sentinel Reporting**
   - Synthesize all artifacts, update changelog/docs, verify criteria, write final handoff.md and send completion message to Sentinel.
