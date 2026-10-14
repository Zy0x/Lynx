# Comprehensive Technical Analysis: Lynx Universal Charging Throttle Elimination

> **Author**: Explorer Survey Agent 1  
> **Date**: 2026-10-14  
> **Target Device Focus**: MediaTek Dimensity / Helio Architecture (specifically Infinix Note 11S `X698`, `mt6781`, Android 14, RT9759 Direct Charge Pump) & Universal HAL  
> **Integrity Mode**: Read-Only Codebase Investigation  

---

## Executive Summary

The Lynx Universal root module provides power and performance management across Qualcomm and MediaTek architectures. Recent architectural evolution migrated the runtime from monolithic shell scripts (`Charging-Controller.sh`, `Smart-AI.sh`, `telemetry.sh`) into a compiled native Rust daemon (`system/bin/lynxd`) running as a late-start root service (`service.sh`). 

The current charging subsystem (`native/lynxd/src/hardware/charging.rs`) implements four primary operational modes (*Cool*, *Standard*, *Hyper/Extreme*, and *Bypass*) and autonomous management through periodic daemon cycles (`DaemonRunner::run()`). However, during active screen usage (*Screen-On*) and component heating, hardware and firmware throttle charging currents from ~3300 mA adapter input down to 1500 mA (cutting net battery current from >6000 mA to <3000 mA).

This investigation examines the root causes of charging throttling across display, thermal, and pump regulator layers, evaluates the current Lynx codebase, and charts exact technical hooks to satisfy requirements **R1** (Screen-On Bypass), **R2** (Thermal Neutralization), **R3** (Pump Current Enforcement >6A), and **R4** (Fail-Safe Disconnect Restoration).

---

## 1. Current Architecture of Charging, Battery Monitoring, and Thermal Control in Lynx

### 1.1 Facade & Native Engine Split
- **Facade Delegators**:
  - `core/Charging-Controller.sh`: A thin 31-line POSIX shell facade delegating 100% of calls to `$LYNXD charging <mode|status|apply|tick>` (`core/Charging-Controller.sh:8-30`).
  - `system/bin/lynx`: A 3-line shell script invoking `lynxd tui "$@"` (`system/bin/lynx:1-3`).
  - `system/bin/Lxcore`: Legacy CLI tool, now completely retired and superseded by `system/bin/lynxd`.
  - `core/Smart-AI.sh`: Historically monitored foreground apps via `dumpsys window`. Now completely replaced by native Rust subsystems in `native/lynxd/src/daemon/foreground/` and `src/daemon/runner.rs`.
- **Runtime Daemon (`system/bin/lynxd`)**:
  - Launched at boot by `service.sh` via `exec "$MODPATH/system/bin/lynxd" boot` (`service.sh:5`).
  - Executes `DaemonRunner::run()` (`src/daemon/runner.rs:22`).
  - Every daemon iteration executes `ChargingRegulator::tick()` (`src/daemon/runner.rs:207`).

### 1.2 The Charging Regulator (`native/lynxd/src/hardware/charging.rs`)
The charging subsystem is organized under `ChargingRegulator`:
1. **Physical Power Supply Node Discovery**:
   - Universal Power Supply class: `/sys/class/power_supply/battery`, `usb`, `main`.
   - MediaTek Platform Charger: `/sys/devices/platform/charger/`.
   - Qualcomm Battery: `/sys/class/qcom-battery/`.
   - Transsion Custom Battery & ODM: `/sys/devices/platform/odm/odm:tran_battery/`, `/sys/devices/platform/tran_battery/`.
   - RT9759 Charge Pump Driver: `/sys/bus/i2c/drivers/rt9759/`.
2. **Current Modes**:
   - **Cool Mode** (`apply_cool_mode`, line 665): Restricts current to 1000–2000 mA, clamps `pdc_max_watt` to 5W, and forces `Charging_CallState = 2`.
   - **Standard Mode** (`apply_standard_mode`, line 673): Restores `pdc_max_watt = 33`, `sw_jeita = 1`, unlocks sysfs permissions to `0o666`, and returns control to OEM kernel algorithms.
   - **Hyper / Extreme Mode** (`apply_extreme_charging`, line 498):
     - Unlocks extreme nodes (`unlock_extreme_nodes`, line 352).
     - Disables JEITA clamping: `sw_jeita = 0` (locked with `chmod 444`).
     - Activates MediaTek direct pump rails: `pe20 = 1`, `pe40 = 1`, `enable_sc = 1`, `pdc_max_watt = 120`.
     - Pushes `input_current = 33170`, `sc_ibat_limit = 12000`, `chg1_current = 33170`, `chg2_current = 33170` (locked with `chmod 444`).
     - Freezes `bcct` cooling devices: `set_bcct_throttle(false)`.
   - **Bypass Mode** (`apply_bypass_charging`, line 421): Decouples battery charging (`input_suspend = 1`, `charging_enabled = 0`, `pdc_max_watt = 0`, `tran_charger_full = 1`, `bypass_charger = 1`, `Charging_CallState = 1`).
3. **Session Baseline Capture & Restoration**:
   - Captures baseline sysfs node states into `/dev/lynx_charging_baseline/` on first cable connection (`capture_charging_session_baseline`, line 1042).
   - Restores values upon disconnect (`handle_disconnect`, line 1008).

### 1.3 Thermal Control Architecture (`native/lynxd/src/subsystems/thermal.rs`)
- `ThermalController::set_mode()` manages platform thermal throttling across conservative, balanced, stable, gaming, and extreme modes (`thermal.rs:136`).
- `ThermalController::freeze_oem_throttlers()` stops aggressive OEM thermal daemons (`mi_thermald`, `thermal-engine`, `ituxd`, `thermalloadalgod`, `com.samsung.android.game.gos`, `com.xiaomi.joyose`) via `killall -STOP` (`thermal.rs:102`).
- **Critical Architectural Boundary**: In `set_limit_raw()` (`thermal.rs:280`), `ThermalController` explicitly **skips** battery zones:
  ```rust
  let t_type = Self::read_trimmed(format!("{}/type", tz_dir)).unwrap_or_default();
  if Self::is_battery_zone(&t_type) {
      // NEVER tamper with battery / charger thermal safety
      continue;
  }
  ```
  This intentional guard protected against dangerous battery thermal runaway, but leaves charging-specific thermal algorithms (`dv2_algo_task`, `__dv2_check_tpcb_level`) unmanaged during gaming and screen-on conditions.

---

## 2. Requirements Gap Analysis & Integration Hooks

```
                   ┌───────────────────────────────────────────────┐
                   │               LYNX ENGINE (lynxd)             │
                   └───────────────────────┬───────────────────────┘
                                           │
         ┌─────────────────────────────────┼────────────────────────────────┐
         ▼                                 ▼                                ▼
┌───────────────────────┐       ┌───────────────────────┐       ┌───────────────────────┐
│     R1: SCREEN-ON     │       │     R2: THERMAL       │       │    R3: PUMP CURRENT   │
│   THROTTLE BYPASS     │       │     NEUTRALIZATION    │       │     ENFORCEMENT       │
├───────────────────────┤       ├───────────────────────┤       ├───────────────────────┤
│ • BN_TestMode = 1     │       │ • pcb_thermal_debug   │       │ • RT9759 2:1 Pump     │
│   (Lock chmod 444)    │       │   [95,6000,100,6000]  │       │ • enable_sc = 1       │
│ • Suppress lcd_on     │       │ • Spoof Battery_Temp  │       │ • input_current=33170 │
│   clamp (1500mA)      │       │   (28°C) + real sensor│       │ • sc_ibat_limit=12000 │
│ • Zero-Latency Resync │       │ • Freeze all cdevs    │       │ • Sustained >6000 mA  │
└───────────────────────┘       └───────────────────────┘       └───────────────────────┘
                                           │
                                           ▼
                                ┌───────────────────────┐
                                │     R4: FAIL-SAFE     │
                                │   DISCONNECT RESTORE  │
                                ├───────────────────────┤
                                │ • Restore OEM params  │
                                │ • Unplug < 2 seconds  │
                                │ • Retain vbat/vbusOVP │
                                └───────────────────────┘
```

### 2.1 R1: Screen-On Charging Throttling Bypass
- **The Mechanism**:
  On MediaTek and Transsion platforms (e.g. Infinix Note 11S `mt6781`), fast charging is governed by the proprietary `pca_dv2_algo` driver (`dv2_algo_task`). When the display is active (`lcd_on > 0`, `fb_on = 1`), the kernel algorithm evaluates `__dv2_get_ita_lmt` and forces the adapter input current (`ita`) down to `game_limit_ita = 1500 mA`. 
  Because RT9759 operates as a 2:1 charge pump ($I_{\text{bat}} \approx 2 \times I_{\text{ta}}$), capping $I_{\text{ta}}$ to 1500 mA halves battery current to $\le 3000\text{ mA}$, dropping charging power from ~33W to ~15W.
- **The Core Flaw in Current Code**:
  In `native/lynxd/src/hardware/charging.rs` lines 417, 542, 622, and 690:
  ```rust
  Self::write_node(&format!("{}/BN_TestMode", MTK_DIR), "0");
  ```
  `lynxd` explicitly writes `"0"` to `BN_TestMode`!
  `BN_TestMode` is the vendor kernel factory test mode switch (`/sys/devices/platform/charger/BN_TestMode`). Setting `BN_TestMode = 1` commands the kernel to bypass battery notifications, screen-on derating, and `game_limit_ita` clamps! Setting it to `0` reactivates the 1500mA screen-on clamp.
- **Required Extension**:
  1. In `apply_extreme_charging()`: Set `BN_TestMode = 1` and lock it with `chmod 444` (`write_node_lock`).
  2. Also lock `/sys/devices/platform/charger/BatteryNotify = 0` and `/sys/devices/platform/charger/tran_charger_full = 0` to prevent screen state triggers.
  3. Ensure `ScreenDetector` (`src/daemon/screen.rs`) notifies charging regulation of screen transitions to maintain zero-latency re-enforcement.

### 2.2 R2: Charging Thermal Throttling & Trip-Point Neutralization
- **The Mechanism**:
  `dv2_algo_task` continuously samples four thermal domains:
  - `tbat`: Battery thermistor. Reaching Level 6 triggers step-down throttling.
  - `tpcb`: Motherboard PCB temperature. Reaching Level 7 (nominally 44°C–45°C) triggers `__dv2_check_tpcb_level`, immediately slashing `ita` to 1500 mA.
  - `tdvchg`: RT9759 direct pump die temperature.
  - `tswchg`: RT9471 switching charger die temperature.
- **Missing Hooks in Current `lynxd`**:
  1. **`pcb_thermal_debug`**:
     Transsion kernels expose an ODM sysfs interface:
     `/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug` (and `/sys/devices/platform/tran_battery/pcb_thermal_debug`).
     Writing `"[95,6000,100,6000,6000]"` or `"[90,6000,95,6000,6000]"` overrides the internal PCB trip point matrix (`Tpcb_store_ok = 1`), shifting the Level 7 threshold from 45°C to 95°C and the current ceiling to 6000 mA.
     *Current Status*: Completely missing from `native/lynxd/src/hardware/charging.rs`!
  2. **Battery Temperature Spoofing (`Battery_Temperature`)**:
     The MediaTek charger driver reads `/sys/devices/platform/battery/Battery_Temperature`. Writing `28` (28.0°C) and locking with `chmod 444` neutralizes `tbat` step-downs in `dv2_algo_task`.
     *Safety Assurance*: `ChargingRegulator::read_real_battery_temp_dc()` already inspects physical sensors (`mtktswmt`, `mtktsbtsmdpa`, `mtktsap`), ensuring Lynx internal emergency cutoffs are never blinded by this spoofing.
  3. **Cooling Devices Freeze**:
     `set_bcct_throttle(false)` currently only targets cooling devices whose names match `"bcct"` or `"abcct"`. It must be extended to iterate through all cooling devices matching `chg`, `battery`, `current`, `cdev`, and write `0` locked with `chmod 444`.
  4. **Kernel Driver Hooks (`clabcct`)**:
     Reset and lock `/proc/driver/thermal/clabcct` and `clabcct_lcmoff`.

### 2.3 R3: Continuous Maximum Pump Current Enforcement (>6A Battery Current)
- **The Mechanism & Physics**:
  - Direct 2:1 Switched Capacitor Pump (RT9759):
    $$I_{\text{bat}} = 2 \times I_{\text{ta}} \times \eta \approx 2 \times 3300\text{ mA} \times 0.96 \approx 6336\text{ mA} > 6.0\text{ A}$$
  - For this to engage, the following conditions must simultaneously hold:
    - Charger negotiation: Pump Express 4.0 / RFC Direct Charge (`pe40 = 1`, `enable_sc = 1`, `pdc_max_watt = 120`).
    - Input adapter current register: `input_current = 33170` (3317.0 mA).
    - Pump current limit register: `sc_ibat_limit = 12000` (or 8000 mA).
    - Dual channel current registers: `chg1_current = 33170`, `chg2_current = 33170`.
    - Universal Linux Power Supply current rails:
      - `constant_charge_current_max` = 12000000 uA
      - `constant_charge_current` = 12000000 uA
      - `input_current_limit` = 12000000 uA
      - `current_max` = 12000000 uA (MAIN) & 5000000 uA (USB)
    - All above nodes locked with `chmod 444`.
  - **Operating Domain**: Below 80% State of Charge (SoC). At $\ge 80\%$, the battery cell reaches its Constant Voltage (CV) saturation curve ($V_{\text{bat}} > 4.47\text{ V}$), where current must naturally taper down electrochemically to protect the cell chemistry.

### 2.4 R4: Fail-Safe Disconnect Restoration & Over-Voltage Protection
- **The Mechanism**:
  1. Over-Voltage Protection (`vbatovp`, `vbusovp`): Hardwired analog comparator registers inside the PMIC (MT6358) and RT9759 IC. Overriding sysfs charging currents and thermal trip points does NOT disable PMIC hardware voltage comparators, so over-voltage safety remains intact.
  2. Disconnect Detection:
     - `ChargingRegulator::is_hardware_plugged()` evaluates USB/AC `online == "1"`, ADC charger voltage $\ge 3500\text{ mV}$, and `status == "Charging"`.
     - When all return false, `handle_disconnect()` is invoked.
  3. Current Deficiencies in Disconnect Restoration:
     - **Timing Defect (20s vs 2s)**: When the screen is OFF, `DaemonRunner::run()` sleeps for 20 seconds (`sleep_duration = Duration::from_secs(20)`). If a user unplugs the cable while the screen is off, `handle_disconnect()` will not execute for up to 20 seconds! Acceptance criterion demands restoration within **2 seconds**.
     - **Missing Nodes in Session Restoration**:
       - `pcb_thermal_debug` is not in `CHARGING_SESSION_NODES` or `DISCONNECT_CHARGING_RESETS`. It must be restored to `[45,3000,50,2000,1000]`.
       - `Battery_Temperature` is not restored to `65535` (genuine kernel reporting).
       - `cooling_device*` permissions must be restored to `0o666` across all charging cdevs.

---

## 3. Potential Conflicts & Mitigations

| Subsystem / Feature | Potential Conflict | Mitigation & Resolution in Lynx Architecture |
| :--- | :--- | :--- |
| **Bypass Charging (Direct Motherboard)** | Hyper mode writes `charging_enabled = 1`, `input_suspend = 0`, and `enable_sc = 1`, which would clobber motherboard bypass. | `ChargingRegulator::apply_from_config()` already evaluates `cfg.bypass_enabled` (Tier 2) and `cur_cap >= 100` (Tier 3) **before** evaluating Hyper Mode (Tier 7). Bypass priority must be strictly preserved. |
| **AutoCut & Smart Battery Limit** | Unrestricted >6A charging could overcharge battery past user limit (e.g. 80%). | Enforce maximum pump current **only below 80% SoC**. Once capacity reaches the user limit or 80%+, engage smart tapering or AutoCut bypass. |
| **Emergency Thermal Guard vs Hyper Mode** | Hyper mode currently disables `emergency_temp_guard_enabled` checks (`charging.rs:851`). If battery exceeds 50°C, thermal runaway is possible. | Retain a hard, non-bypassable hardware emergency cutoff at **$\ge 50.0^\circ\text{C}$ real physical battery temperature** (`read_real_battery_temp_dc()`) that forces immediate step-down or cutoff regardless of mode. |
| **Deep Sleep Power Drain vs Fast Polling** | Polling every 1s when screen is off prevents CPU deep sleep ($C$-states). But polling every 20s violates the 2s disconnect restoration rule. | **Dynamic Charging-Aware Interval**: If `is_hardware_plugged() == true`, phone is on AC power, so deep sleep power saving is irrelevant; poll every **1 second**. When unplugged, immediately return to **20 seconds** sleep interval. |
| **Android Framework `dumpsys` Freezes** | Calling `dumpsys battery set` freezes framework state updates (`UPDATES STOPPED`). | Lynx already uses `dumpsys battery reset` exclusively, preserving live Android BatteryService uevent sync. |

---

## 4. Architectural Synthesis & Roadmap for Implementers

1. **`native/lynxd/src/hardware/charging.rs` Enhancements**:
   - Update `apply_extreme_charging()`:
     - Set `/sys/devices/platform/charger/BN_TestMode` to `"1"` (locked `0o444`).
     - Set `/sys/devices/platform/charger/BatteryNotify` to `"0"` (locked `0o444`).
     - Inject `"[95,6000,100,6000,6000]"` into `/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug` and lock `0o444`.
     - Spoof `/sys/devices/platform/battery/Battery_Temperature` to `"28"` and lock `0o444`.
     - Broaden `set_bcct_throttle(false)` to cover all `chg`, `battery`, `current`, `cdev*` cooling devices.
   - Update `handle_disconnect()`:
     - Revert `BN_TestMode` to `"0"`.
     - Revert `BatteryNotify` to `"1"`.
     - Revert `pcb_thermal_debug` to `"[45,3000,50,2000,1000]"` and restore `0o644`.
     - Revert `Battery_Temperature` to `"65535"` and restore `0o644`.
     - Unlock all cooling device permissions back to `0o666`.
2. **`native/lynxd/src/daemon/runner.rs` Polling Optimization**:
   - In `DaemonRunner::run()`, adjust sleep duration:
     ```rust
     let sleep_duration = if ChargingRegulator::is_hardware_plugged() {
         Duration::from_secs(1) // Sub-2s disconnect detection while plugged
     } else if current_screen == ScreenState::Off {
         Duration::from_secs(20) // Deep Sleep Shield when unplugged
     } else {
         Duration::from_secs(5) // Active screen monitoring
     };
     ```
3. **Synchronization with Companion App & WebUI**:
   - `LynxCompanion/` and `webroot/` delegate directly to `lynxd charging apply` and read telemetry from `lynxd charging status` (`status_json()`). Any enhancement in `charging.rs` is instantly reflected across all interfaces without frontend refactoring.
