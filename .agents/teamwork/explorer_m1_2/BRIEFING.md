# BRIEFING — 2026-10-14T14:09:00Z

## Mission
Formulate the exact code-level implementation blueprint for R2: Charging Thermal Throttling & Trip-Point Neutralization across MTK and QCOM platforms, focusing on Transsion odm/odm:tran_battery, pca_dv2_algo, Battery_Temperature, MTK ABCCT PID, cooling devices 53-57, thermalloadalgod, and cmd thermalservice.

## 🔒 My Identity
- Archetype: explorer
- Roles: Investigation, Synthesis, Architecture Blueprinting
- Working directory: e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m1_2
- Original parent: 86fb0d31-9bcd-477f-bf21-1a94aecf3bd2
- Milestone: Milestone 1 (Screen-On & Thermal Level Neutralization: R1 & R2)

## 🔒 Key Constraints
- Read-only investigation — do NOT implement directly in production source code.
- Provide comprehensive code-level implementation blueprint with before/after snippets, exact paths, sysfs permissions, fallback handling, and test/verification procedures.
- Strictly adhere to AGENTS.md safety protocols and project standards.

## Current Parent
- Conversation ID: 86fb0d31-9bcd-477f-bf21-1a94aecf3bd2
- Updated: 2026-10-14T14:09:00Z

## Investigation State
- **Explored paths**:
  - `native/lynxd/src/hardware/charging.rs` (lines 1-1256)
  - `native/lynxd/src/subsystems/thermal.rs` (lines 1-120)
  - `native/lynxd/src/main.rs` (lines 700-815)
  - `core/Charging-Controller.sh` (lines 1-31)
  - `archive/legacy_sh_backup/core/Charging-Controller.sh` (lines 300-480)
  - `spec_miner_survey_2/analysis.md` and `handoff.md`
  - `explorer_survey_1/handoff.md`
- **Key findings**:
  - `pcb_thermal_debug` accepts `"[95,6000,100,6000,6000]"` to neutralize Level 7 `tpcb` derating; must be locked `0444`. Reverts to `"[45,3000,50,2000,1000]"` on disconnect.
  - `dv2_debug` accepts command `101` (`tbat`) and `102` (`tdvchg`) to override in-memory tables; must be locked `0444`. Reverts to `[101,43,45,50,500,1000,1500]` and `[102,70,75,80,300,600,900]`.
  - `Battery_Temperature` spoofing to `"28"` locked `0444` neutralizes Level 6 `tbat` throttling; reverts to `"65535"` on disconnect.
  - MediaTek ABCCT PID overridden via `clabcct` & `clabcct_lcmoff` (`"0 70000 1000 200000 5 6000 0"`).
  - Cooling devices 53-57 (`cur_state`) locked to `0`.
  - Pure Rust `signal_processes(..., true)` pauses `thermalloadalgod` with SIGSTOP (19); disconnect resumes with SIGCONT (18).
  - Framework status locked with `cmd thermalservice override-status 0`; reset with `cmd thermalservice reset`.
  - Populating `restore_charger_thermal_safety()` protects unit test `disconnect_resets_only_charging_protocol_nodes` and guarantees <2s disconnect reversion.
- **Unexplored areas**: None. Blueprint is complete.

## Key Decisions Made
- Authored comprehensive code blueprint in `analysis.md` with complete before/after snippets for `charging.rs`.
- Decoupled charging protocol resets from thermal safety restores to satisfy unit tests.
- Reversion verified for both cable disconnect AND user-initiated mode switching (Hyper -> Standard/Cool).

## Artifact Index
- DISPATCH.md — Initial dispatch message log
- BRIEFING.md — Persistent memory
- analysis.md — Detailed technical strategy and code-level blueprint for R2
- handoff.md — 5-component handoff report
