# BRIEFING — 2026-10-14T13:54:00Z

## Mission
Investigate hardware safety, over-voltage protections (vbatovp, vbusovp), net current telemetry (>6000 mA), and unplug fail-safe restoration within 2 seconds for Transsion/MediaTek fast charging in Lynx Universal.

## 🔒 My Identity
- Archetype: explorer
- Roles: Hardware Driver & Disconnect Fail-Safe Explorer
- Working directory: e:\Data\GitHub\Lynx\.agents\teamwork\explorer_survey_3
- Original parent: 86fb0d31-9bcd-477f-bf21-1a94aecf3bd2
- Milestone: charging_throttle_elimination_survey

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Must communicate via send_message to parent (86fb0d31-9bcd-477f-bf21-1a94aecf3bd2)
- Zero-touch on reference devices, local codebase read-only investigation
- Preserve all hardware protections (vbatovp, vbusovp) strictly functional
- Disconnect fail-safe restoration within 2 seconds to safe OEM baselines

## Current Parent
- Conversation ID: 86fb0d31-9bcd-477f-bf21-1a94aecf3bd2
- Updated: not yet

## Investigation State
- **Explored paths**:
  - `native/lynxd/src/hardware/charging.rs`
  - `native/lynxd/src/daemon/runner.rs`
  - `core/Charging-Controller.sh`
  - `archive/legacy_sh_backup/core/Charging-Controller.sh`
  - `archive/local_backups/x698_charging_20261012/`
  - `tools/` (diag_charging, test_register_writes, test_hyper_unlock, probe_charger_hardware, find_regmap)
  - `LynxCompanion/` (`ExtremeChargingScript.kt`, `StandardChargingScript.kt`)
  - `platforms/mtk/`
- **Key findings**:
  - `current_now` on MTK Transsion measures net battery current in uA (+ for charging, - for discharging) via MT6358/MT6360 fuel gauge across Rsense.
  - Sustaining >6,000,000 uA (>6.0A) net current requires 2:1 pump (RT9759 at 5-0066) driven at >=3300 mA input adapter current in low-battery CC phase (<80% SoC).
  - Hardware OVP (`vbatovp` ~4.65V, `vbusovp` ~11.5V) is hardwired analog comparator logic in RT9759/PMIC silicon, completely independent and untouched by sysfs derating overrides.
  - Disconnect detection vulnerability identified: checking Vbus voltage before battery status causes up to 1.5s capacitor decay lag. Triple-vector check (`!has_online_rail && (status == "Discharging" || Charger_Type == "0")`) eliminates this lag.
  - Reversion gaps identified in `handle_disconnect()`: missing restoration of `clabcct`, `clabcct_lcmoff`, `tzbts_param`, `dv2_debug`, `pcb_thermal_debug`, and `Battery_Temperature=65535`.
- **Unexplored areas**: None for survey scope.

## Key Decisions Made
- Deliver detailed findings in `analysis.md` and 5-component handoff in `handoff.md`.
- Propose triple-vector disconnect detection and comprehensive restoration matrix.

## Artifact Index
- DISPATCH.md — Initial dispatch instructions
- BRIEFING.md — Persistent agent state
- progress.md — Liveness heartbeat
- analysis.md — Detailed technical findings
- handoff.md — 5-component handoff report
