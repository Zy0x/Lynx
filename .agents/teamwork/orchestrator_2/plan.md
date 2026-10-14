# Project Orchestrator 2 Plan: Lynx Universal Fast Charging Throttle Elimination

## Objectives
Deliver Milestones 2, 3, 4, and 5 per `ORIGINAL_REQUEST.md` and `PROJECT.md`:
- **Milestone 2**: Continuous Maximum Pump Current Enforcement (>6A Battery Current, RT9759 2:1 pump locking, R3).
- **Milestone 3**: Fail-Safe Disconnect Restoration & Over-Voltage Protection (R4).
- **Milestone 4**: Full E2E Verification & Adversarial Hardening across all acceptance criteria (Tiers 1-5).
- **Milestone 5**: Final Victory Report and Handoff to Sentinel.

## Step-by-Step Execution Plan

### Step 1: Orchestration Initialization [IN_PROGRESS]
- Setup `DISPATCH.md`, `BRIEFING.md`, `progress.md`, `plan.md`, `GATE_STATUS.md`.
- Start recurring heartbeat cron (10-minute cadence).

### Step 2: Milestone 2 — Continuous Maximum Pump Current Enforcement (>6A Battery Current, R3)
1. **Exploration**:
   - Spawn Explorers (3) to investigate RT9759 2:1 pump locking, `input_current` (33170 / 3300mA), `sc_ibat_limit` (>=8000mA / 12000mA), and `enable_sc = 1` in `native/lynxd/src/hardware/charging.rs` and `core/Charging-Controller.sh`.
   - Wait for reports and synthesize findings.
2. **Implementation**:
   - Spawn Worker (`worker_m2`) with mandatory integrity warning, explorer findings, and exclusive ownership of charging regulator files.
   - Worker implements continuous >6A battery current enforcement, builds native binaries (`cargo build --release`), and verifies with E2E suite (`python tests/e2e/run_e2e_tests.py --feature r3`).
3. **Verification & Adversarial Challenge**:
   - Spawn Reviewers (2) to independently inspect code and run tests.
   - Spawn Challengers (2) to stress test pump current stability, 2:1 ratio, and CC phase boundary.
   - Spawn Forensic Auditor to verify genuine implementation without hardcoded or facade bypasses.
4. **Milestone Gate**:
   - Collect all verdicts into `GATE_STATUS.md`.
   - Advance to Milestone 3 if and only if build passes, both reviewers approve, both challengers confirm correctness, and auditor reports CLEAN.

### Step 3: Milestone 3 — Fail-Safe Disconnect Restoration & Over-Voltage Protection (R4)
1. **Exploration**:
   - Spawn Explorers to investigate silicon OVP (`vbatovp`, `vbus_max_ovp`), triple-vector disconnect detection (`!has_online_rail && (status == "Discharging" || Charger_Type == "0")`), and 2-second safe reversion of all nodes.
2. **Implementation**:
   - Spawn Worker (`worker_m3`) to implement disconnect fail-safe and OVP protection.
   - Verify with E2E suite (`python tests/e2e/run_e2e_tests.py --feature r4`).
3. **Verification**:
   - Reviewers (2), Challengers (2), Forensic Auditor.
   - Gate verification.

### Step 4: Milestone 4 — Full E2E Verification & Adversarial Coverage Hardening
1. **Phase 1**: Execute full 41/41 test suite in `tests/e2e/run_e2e_tests.py` across all tiers (Tiers 1-4).
2. **Phase 2**: Adversarial coverage hardening (Tier 5):
   - Spawn Challengers with `test-coverage-audit` to generate white-box adversarial edge cases.
   - Worker fixes any gaps.
   - Reviewers & Auditor verify.

### Step 5: Milestone 5 — Final Synthesis & Report to Sentinel
1. Synthesize all telemetry evidence, gate verdicts, and test results.
2. Verify all acceptance criteria from `ORIGINAL_REQUEST.md`.
3. Deliver comprehensive final report to Sentinel via `send_message`.
