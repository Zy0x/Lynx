# BRIEFING — 2026-10-14T13:54:00Z

## Mission
Investigate Lynx codebase for charging throttle bypass, thermal throttle neutralization, pump current enforcement, and fail-safe disconnect mechanisms.

## 🔒 My Identity
- Archetype: explorer
- Roles: codebase investigation, technical analysis, synthesis
- Working directory: e:\Data\GitHub\Lynx\.agents\teamwork\explorer_survey_1
- Original parent: 86fb0d31-9bcd-477f-bf21-1a94aecf3bd2
- Milestone: Charging Throttle Elimination Survey

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Do NOT modify project source files
- Adhere to Teamwork protocol and AGENTS.md rules

## Current Parent
- Conversation ID: 86fb0d31-9bcd-477f-bf21-1a94aecf3bd2
- Updated: 2026-10-14T13:54:00Z

## Investigation State
- **Explored paths**:
  - `ORIGINAL_REQUEST.md`: Identified R1 (screen-on bypass), R2 (thermal neutralization), R3 (pump current >6A), R4 (disconnect restore).
  - `core/Charging-Controller.sh`: Confirmed facade delegating to `lynxd charging <action>`.
  - `system/bin/lynx`, `system/bin/Lxcore`, `core/Smart-AI.sh`: Confirmed migration to 100% native Rust `lynxd`.
  - `native/lynxd/src/hardware/charging.rs`: Analyzed `ChargingRegulator`, `apply_extreme_charging`, `handle_disconnect`, `status_json`.
  - `native/lynxd/src/subsystems/thermal.rs`: Inspected `ThermalController` and battery zone exclusions.
  - `native/lynxd/src/daemon/runner.rs`: Inspected `DaemonRunner` loop, screen detector, tick intervals (5s screen-on vs 20s screen-off).
  - `changelog.md`: Traced versions 3.0.28 to 3.0.33 documenting `BN_TestMode`, `pcb_thermal_debug`, `dv2_algo`, `abcct`.
  - `archive/legacy_sh_backup/core/Charging-Controller.sh`: Extracted exact hardware nodes and override patterns.
  - `LynxCompanion/` & `webroot/`: Analyzed UI hooks and confirmation of `lynxd` as single source of truth.
- **Key findings**:
  - R1: `BN_TestMode = 1` bypasses Transsion screen-on clamp (`game_limit_ita = 1500 mA`), but `charging.rs` currently mistakenly writes `BN_TestMode = 0`.
  - R2: `pcb_thermal_debug` overrides Level 7 `tpcb` thermal clamp (45°C) to 95°C / 6000mA, but is completely missing in `lynxd`. Battery temperature spoofing (`Battery_Temperature = 28`) neutralizes Level 6 `tbat` throttling while Lynx real thermistor probe protects against true danger.
  - R3: RT9759 2:1 charge pump achieves >6.0A net battery current ($I_{\text{bat}} = 2 \times I_{\text{ta}} \times \eta \approx 6000-6336\text{ mA}$) when $I_{\text{ta}} \ge 3000\text{ mA}$ is sustained below 80% SoC.
  - R4: Disconnect latency during screen-off is 20s (violates <2s rule). Fix: reduce loop sleep to 1s when plugged in. Disconnect tables must restore `BN_TestMode = 0`, `pcb_thermal_debug`, `Battery_Temperature`, and cdev permissions.
- **Unexplored areas**: None for codebase survey; live testing on physical device to be performed by implementer/verifier.

## Key Decisions Made
- Survey completed and structured analysis documented in `analysis.md` and `handoff.md`.

## Artifact Index
- DISPATCH.md — Recorded dispatch instructions
- BRIEFING.md — Working memory & state
- progress.md — Liveness heartbeat & task tracking
- analysis.md — Comprehensive technical analysis
- handoff.md — 5-component structured handoff report
