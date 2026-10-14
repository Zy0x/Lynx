# BRIEFING — 2026-10-14T15:53:15Z

## Mission
Investigate Requirement R4: Fail-Safe Disconnect Restoration & Over-Voltage Protection specifically for disconnect detection (triple-vector detection, capacitor float voltage TC-T2-B04, runner chunk sleep latency).

## 🔒 My Identity
- Archetype: explorer
- Roles: Disconnect Detection Explorer
- Working directory: e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m3_1
- Original parent: 4a93ea5f-bf3a-41d2-8075-760f372161d2
- Milestone: M3

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Deliverables in e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m3_1\handoff.md
- Send completion message to parent via send_message

## Current Parent
- Conversation ID: 4a93ea5f-bf3a-41d2-8075-760f372161d2
- Updated: 2026-10-14T15:53:15Z

## Investigation State
- **Explored paths**:
  - `native/lynxd/src/hardware/charging.rs` (lines 1262-1293 `is_hardware_plugged()`, lines 29-45 `DISCONNECT_CHARGING_RESETS`, lines 891-945 `restore_charger_thermal_safety()`, lines 1307-1339 `handle_disconnect()`)
  - `native/lynxd/src/daemon/runner.rs` (lines 245-295 chunk sleep loop & edge breakout)
  - `core/Charging-Controller.sh` (lines 110-145 `fallback_disconnect()`)
  - `tests/e2e/tier2_boundaries/test_boundary_corner_cases.py` (lines 122-155 `TC-T2-B04`)
  - `tests/e2e/tier1_feature_coverage/test_r4_disconnect_failsafe.py` (lines 50-81 `TC-T1-F4-02`)
  - `tests/e2e/framework.py` (lines 450-495 `_sim_disconnect()`)
- **Key findings**:
  - Current `is_hardware_plugged()` erroneously returns `true` when decoupling capacitors hold residual voltage >= 3500 mV on `ADC_Charger_Voltage` because it checks voltage before checking battery status, and ignores `Charger_Type` and `main/online`.
  - Triple-vector disconnect rule: `!has_online_rail && (is_discharging || is_unknown_charger)` provides zero-delay disconnect detection that is immune to capacitor float while preserving hardware bypass mode.
  - `runner.rs` achieves <= 500 ms breakout latency via 500 ms chunk sleep, but has a restrictive file-check guard (`extreme_charging`, `bypass_active`, `charging_guard`) that prevents calling `handle_disconnect()` during standard/cool charging modes.
  - `DISCONNECT_CHARGING_RESETS` is missing `sc_ibat_limit = 2000` and `input_current = 24576` defined in `PROJECT.md` Interface Contracts.
- **Unexplored areas**: None, scope fully analyzed.

## Key Decisions Made
- Formulate complete before->after recommendations and pure unit test design for Worker M3.

## Artifact Index
- DISPATCH.md — dispatch message
- BRIEFING.md — situational awareness
- progress.md — heartbeat
- handoff.md — comprehensive 5-component report
