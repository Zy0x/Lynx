## 2026-10-14T15:46:13Z
You are explorer_m3_2 (Node Reversion Explorer).
Your working directory is: e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m3_2

MANDATORY FIRST STEP:
Read the authoritative user request at:
e:\Data\GitHub\Lynx\.agents\teamwork\ORIGINAL_REQUEST.md
and the master project spec at:
e:\Data\GitHub\Lynx\.agents\teamwork\PROJECT.md

Scope & Mission:
Investigate Requirement R4 regarding complete hardware node reversion and zero parasitic leakage:
1. Examine `handle_disconnect()` in `native/lynxd/src/hardware/charging.rs`.
2. Verify every node that was modified during hyper charging:
   - `enable_sc` -> `"0"`
   - `input_current` -> `"24576"`
   - `sc_ibat_limit` -> `"2000"`
   - `BN_TestMode` -> `"0"`, `BatteryNotify` -> `"1"`, `sw_jeita` -> `"1"`
   - `pcb_thermal_debug` -> restored to OEM `[45,3000,50,2000,1000]`
   - `Battery_Temperature` -> unlocked and restored to `65535`
   - MediaTek `clabcct` -> restored to OEM `0 70000 1000 200000 5 6000 0`
   - MediaTek cooling devices 53-57 -> unlocked and restored to OEM
   - Frozen thermal daemons (`thermalloadalgod`) -> resumed via SIGCONT
   - Markers cleaned up: `/dev/lynx_extreme_charging`, `/dev/lynx_charging_guard`, `/dev/lynx_bypass_active`.
3. Check for any parasitic quiescent leakage or sleep blocking:
   - Verify that all sysfs node permissions are unlocked (`0666` or OEM) so kernel and vendor HALs can write to them.
   - Verify deep sleep shield (`ScreenState::Off -> 20s sleep`) takes over cleanly with zero wake locks.
4. Provide concrete recommendations for Worker M3.

Deliverables:
- Write comprehensive report to:
  `e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m3_2\handoff.md`
- Send completion message to parent.
