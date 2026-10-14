# Project: Lynx Universal Charging Throttle Elimination

## Architecture
The Lynx charging subsystem operates via a high-performance native Rust daemon (`system/bin/lynxd`, source in `native/lynxd/`) with shell-compatible entrypoints in `core/Charging-Controller.sh`.
On MediaTek and Transsion hardware (e.g., Infinix Note 11S MT6781), the charging architecture interfaces with:
1. Richtek RT9759 2:1 switched capacitor direct charge pump on I2C bus 5 (`5-0066`).
2. Transsion `pca_dv2_algo` kernel state machine (`dv2_algo_task`), which evaluates screen state (`fb_on`, `lcd_on`), adapter current (`ita`), and 6 thermal domains (`tbat`, `tpcb`, `tdvchg`, `tswchg`, `tta`, `tusb`).
3. Transsion ODM battery platform (`/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug`).
4. MediaTek charger platform (`/sys/devices/platform/charger/`).
5. MediaTek thermal driver (`/proc/driver/thermal/clabcct`, `tzbts_param`) and cooling devices 53-57.
6. Battery fuel gauge (`/sys/class/power_supply/battery/current_now`).

```
                    +-------------------------------------+
                    | Fast Charger / RFC TA (33W-68W)     |
                    +-------------------------------------+
                                       | Vta: 8.8V-10.5V, Ita: 3000-3300mA
                                       v
+-------------------------------------------------------------------------------+
| Kernel DV2 State Machine (pca_dv2_algo)                                       |
| - Screen-On Gate: lcd_on/fb_on -> game_limit_ita (1500mA) [Bypassed by M1]   |
| - Thermal Gate: tpcb Level 7 (1500mA), tbat Level 6/8      [Bypassed by M1]   |
| - ABCCT PID Controller & Cooling Devices 53-57             [Bypassed by M1]   |
+-------------------------------------------------------------------------------+
                                       |
                                       v
+-------------------------------------------------------------------------------+
| RT9759 2:1 Charge Pump (5-0066)                                              |
| - Current Double: Ibat = 2 * Ibus * 97% efficiency = >6000mA [Enforced by M2] |
| - Silicon OVP: vbatovp (4.928V), vbus_max_ovp (11.0V)      [Protected by M3]  |
+-------------------------------------------------------------------------------+
                                       |
                                       v
                     +-----------------------------------+
                     | Battery Cell Terminal (>6000 mA)  |
                     +-----------------------------------+
```

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Screen-On Charging Throttle Bypass | Neutralize `game_limit_ita = 1500 mA` clamp in `__dv2_get_ita_lmt` when display is on (`fb_on == 1`, `lcd_on > 0`) via `BN_TestMode = 1` and `BatteryNotify` reset | M1 | Survey (Codebase & Spec Miner) |
| 2 | PCB Thermal Throttling Neutralization | Override Level 7 `tpcb` 45°C thermal derating (1500 mA) via `/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug` | M1 | Survey (Spec Miner & Explorer 1) |
| 3 | DV2 Direct Pump Thermal Table Neutralization | Override `pca_dv2_algo` in-memory thermal tables via `dv2_debug` and spoof `/sys/devices/platform/battery/Battery_Temperature = 28` | M1 | Survey (Spec Miner & Explorer 1) |
| 4 | MediaTek ABCCT & Thermal Daemon Neutralization | Disable ABCCT PID controller (`/proc/driver/thermal/clabcct`), lock cooling devices 53-57 to 0, suspend `thermalloadalgod`, override framework thermalservice | M1 | Survey (Spec Miner) |
| 5 | Continuous Maximum Pump Current Enforcement | Enforce and lock RT9759 2:1 pump input current $\ge 3000\text{--}3317\text{ mA}$ and `sc_ibat_limit` to deliver $>6000\text{ mA}$ net battery current during CC phase (<80% SoC) | M2 | Survey (All Explorers) |
| 6 | Hardware Over-Voltage Protection (OVP) Integrity | Maintain silicon-level `vbatovp` and `vbus_max_ovp` protections with software cell voltage ceiling $\le 4.50\text{V}$ | M3 | Survey (Explorer 3 & Spec Miner) |
| 7 | Triple-Vector Disconnect Detection (<500ms) | Fast capacitor-independent unplug detection (`!has_online_rail && (status == "Discharging" || Charger_Type == "0")`) with 1-second daemon polling | M3 | Survey (Explorer 3 & Explorer 1) |
| 8 | 2-Second Fail-Safe Disconnect Restoration | Immediate reversion of all nodes (`BN_TestMode = 0`, `pcb_thermal_debug`, `Battery_Temperature`, `clabcct`, cooling devices, daemons) with zero parasitic leakage | M3 | Survey (Explorer 3 & Explorer 1) |
| 9 | Opaque-Box E2E Test Suite (Tiers 1-4 & Tier 5) | Comprehensive automated test harness verifying R1, R2, R3, R4 across all tiers with adversarial stress testing | M4 | Survey & Dual Track Spec |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| E2E | E2E Testing Track | Requirement-driven opaque-box test suite (Tiers 1-4) published via `TEST_READY.md` | none | DONE |
| M1 | Screen-On & Thermal Level Neutralization | Implement F1, F2, F3, F4 in `native/lynxd` and HAL | none | DONE |
| M2 | Continuous Maximum Pump Current Enforcement | Implement F5 in `native/lynxd` for RT9759 2:1 charge pump | M1 | DONE |
| M3 | Fail-Safe Disconnect Restoration & OVP | Implement F6, F7, F8 in `native/lynxd` charging regulator & daemon loop | M2 | IN_PROGRESS |
| M4 | Final E2E Test Suite Pass & Adversarial Hardening | Phase 1: 100% pass on Tiers 1-4; Phase 2: Tier 5 adversarial coverage hardening | E2E, M3 | PLANNED |

## Interface Contracts
### `native/lynxd/src/hardware/charging.rs` ↔ `core/Charging-Controller.sh`
- CLI command: `/data/adb/modules/Lynx/system/bin/lynxd charging mode hyper`
- Return code: `0` on success.
- Exported Sysfs State:
  - `/sys/devices/platform/charger/BN_TestMode`: `"1"` during hyper charging, `"0"` on disconnect.
  - `/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug`: `"[95,6000,100,6000,6000]"` during hyper charging, `"[45,3000,50,2000,1000]"` on disconnect.
  - `/sys/devices/platform/battery/Battery_Temperature`: `"28"` locked `0444` during hyper charging, `"65535"` unlocked on disconnect.
  - `/sys/devices/platform/charger/enable_sc`: `"1"` during hyper charging, `"0"` on disconnect.
  - `/sys/devices/platform/charger/sc_ibat_limit`: `"12000"` (or $\ge 8000$) during hyper charging, `"2000"` on disconnect.
  - `/sys/devices/platform/charger/input_current`: `"33170"` during hyper charging, `"24576"` on disconnect.

### `native/lynxd/src/daemon/runner.rs` ↔ `native/lynxd/src/hardware/charging.rs`
- Dynamic Polling Interval:
  - If `ChargingRegulator::is_hardware_plugged()` is true: loop interval $\le 1\text{ second}$ (regardless of screen state).
  - If unplugged and screen off: loop interval 20 seconds.
- Disconnect latency guarantee: $\le 1000\text{ ms}$ (strictly $< 2\text{ seconds}$).

## Code Layout
- `native/lynxd/src/hardware/charging.rs`: Charging regulator, sysfs nodes, hyper mode overrides, disconnect resets.
- `native/lynxd/src/daemon/runner.rs`: Daemon event loop, screen state polling, charging tick frequency.
- `native/lynxd/src/subsystems/thermal.rs`: System thermal zones, cooling device handling.
- `core/Charging-Controller.sh`: Shell fallback and wrapper for `lynxd charging`.
- `tests/e2e/`: Automated E2E test harness and scripts.
