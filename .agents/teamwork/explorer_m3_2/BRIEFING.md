# BRIEFING — 2026-10-14T15:53:30Z

## Mission
Investigate Requirement R4: Complete hardware node reversion upon charger disconnect and zero parasitic quiescent leakage.

## 🔒 My Identity
- Archetype: explorer
- Roles: investigator, synthesizer
- Working directory: e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m3_2
- Original parent: 4a93ea5f-bf3a-41d2-8075-760f372161d2
- Milestone: M3 (Hardening & Hardware Node Reversion)

## 🔒 Key Constraints
- Read-only investigation — do NOT implement or modify project code
- Keep messages concise, deliver findings in handoff.md
- Use File for reports, Message for coordination
- Strictly adhere to 5-component handoff structure

## Current Parent
- Conversation ID: 4a93ea5f-bf3a-41d2-8075-760f372161d2
- Updated: 2026-10-14T15:47:00Z

## Investigation State
- **Explored paths**:
  - `native/lynxd/src/hardware/charging.rs`
  - `native/lynxd/src/daemon/runner.rs`
  - `core/Charging-Controller.sh`
  - `tests/e2e/tier1_feature_coverage/test_r4_disconnect_failsafe.py`
  - `tests/e2e/framework.py`
  - `tests/adversarial_m1_2_harness.py`
  - `test-results/adversarial_m1_2_results.json`
- **Key findings**:
  1. `input_current` and `sc_ibat_limit` (and `chg1_current`, `chg2_current`) are MISSING from `DISCONNECT_CHARGING_RESETS` in `charging.rs`. If baseline is missing or dirty, these critical nodes never revert to OEM (24576 / 2000) and stay locked `0444`.
  2. `handle_disconnect()` does not invoke `unlock_extreme_nodes()`, leaving non-reset locked nodes at `0444`, preventing vendor HAL writes.
  3. MediaTek `clabcct` prompt vs implementation discrepancy: Prompt lists `0 70000 1000 200000 5 6000 0` as OEM restore, but that is actually the hyper mode override; OEM is `1 42000 1000 200000 5 2000 0` or simple `1`.
  4. In `native/lynxd/src/daemon/runner.rs`, the 500ms chunked sleep loop executes `is_hardware_plugged()` (5 sysfs reads) every 500ms even when unplugged and `ScreenState::Off`, disrupting Linux C-states / deep sleep.
- **Unexplored areas**: None within R4 scope.

## Key Decisions Made
- Analyzed all 10 nodes specified in task mission.
- Formulated 4 concrete recommendations for Worker M3.

## Artifact Index
- DISPATCH.md — Task dispatch record
- BRIEFING.md — Persistent situational awareness
- progress.md — Liveness heartbeat
- handoff.md — Final handoff report
