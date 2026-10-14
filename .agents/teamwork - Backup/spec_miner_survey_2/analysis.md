# Technical Specification & Kernel Interface Analysis: MediaTek & Transsion Direct Charge Pump (dv2) Throttle Elimination

## 1. Executive Summary

This specification document provides an exhaustive, authoritative reverse-engineering analysis of the fast charging and direct charge pump architecture on MediaTek Helio/Dimensity hardware running Transsion (Infinix / Tecno) operating system kernels.

Probing was conducted on ground-truth hardware: **Infinix Note 11S (`Infinix-X698`, MediaTek Helio G96 `mt6781`, Android 14, Kernel 4.14.x)**.

### Key Discoveries
1. **The Screen-On Charging Throttle Mechanism**:
   The Transsion direct charge pump algorithm (`pca_dv2_algo`) enforces a hard constraint `game_limit_ita = 1500 mA` stored in devicetree (`/sys/firmware/devicetree/base/odm/mtk_pe50/game_limit_ita`). Whenever the display is powered (`fb_on == 1` or `lcd_on > 0`), the kernel routine `__dv2_get_ita_lmt` clamps the maximum adapter input current (`ita`) to `1500 mA`, cutting charging wattage by 50% (from ~30W to ~13W) and restricting battery current to ~2.4A instead of >6.0A.
2. **Thermal Step-Down Matrix**:
   `dv2_algo_task` contains 6 distinct thermal monitoring domains (`tbat`, `tpcb`, `tdvchg`, `tswchg`, `tta`, `tusb`), each with a 9-element threshold array (`level_def`) and a 9-element current derating array (`curlmt`).
   - `tpcb` Level 7 triggers at **45°C**, slashing `ita` by **1500 mA**.
   - `tbat` Level 6 triggers at **42°C**, slashing `ita` by **750 mA**, and Level 8 shuts down charging at **45°C**.
3. **RT9759 2:1 Switched Capacitor Charge Pump**:
   Located on I2C bus 5 at address `0x66` (`5-0066`, `richtek,rt9759`). It operates with 2:1 current multiplication ($I_{\text{bat}} = 2 \times I_{\text{bus}}$). To deliver >6000 mA into the battery cells, $I_{\text{bus}}$ ($I_{\text{ta}}$) must be maintained at 3000 mA – 3300 mA at 8.8V – 10.5V. When throttled to 1500 mA, battery current drops to ~2400 mA.
4. **Complete Override Points Discovered**:
   - Screen-on & notify bypass: `/sys/devices/platform/charger/BN_TestMode = 1` and `BatteryNotify = 0`.
   - PCB thermal clamp override: `/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug = [85,6000,90,5000,4500]`.
   - Charge pump thermal table overrides: `/sys/devices/platform/pca_dv2_algo/dv2_debug` (`101` for `tbat`, `102` for `tdvchg`).
   - MediaTek ABCCT PID disable: `/proc/driver/thermal/clabcct` and `/proc/driver/thermal/clabcct_lcmoff`.
   - Cooling devices 53-57 (`abcct`, `abcct_lcmoff`, `mtk-cl-bcct00..02`).
   - Framework thermal override: `cmd thermalservice override-status 0`.

---

## 2. Kernel Charging Architecture Overview

```
                          +-------------------------------------------------+
                          |  Transsion Fast Charger / USB-PD PPS Adapter    |
                          |  (RFC: Richtek Fast Charge Protocol, 33W-68W)   |
                          +-------------------------------------------------+
                                                  |
                                                  | Vta: 8.8V - 10.5V
                                                  | Ita: 3000mA - 3300mA
                                                  v
+-----------------------------------------------------------------------------------+
| Linux Kernel MediaTek & Transsion Charging Subsystem                              |
|                                                                                   |
|  +--------------------+   I2C7 0x1b    +--------------------------------------+   |
|  | rfc_ta Driver      |<-------------->| pca_rfc_ta (Richtek TA Controller)   |   |
|  +--------------------+                +--------------------------------------+   |
|                                                           |                       |
|                                                           v                       |
|  +-----------------------------------------------------------------------------+  |
|  | pca_dv2_algo (Direct Voltage 2:1 Algorithm Engine - kthread: dv2_algo_task) |  |
|  |                                                                             |  |
|  |  +------------------+   +-------------------+   +------------------------+  |  |
|  |  | __dv2_get_ita_lmt|   | Thermal Evaluator |   | Battery Voltage IR-Cmp |  |  |
|  |  |                  |   | - tbat (Bat Temp) |   | - vbat_cv (4470 mV)    |  |  |
|  |  | Inputs:          |   | - tpcb (PCB Temp) |   | - rbat (5 mOhm)        |  |  |
|  |  | - lcd_on / fb_on |   | - tdvchg (Pump)   |   | - vclamp (10 mV)       |  |  |
|  |  | - game_limit_ita |   | - tswchg (Buck)   |   +------------------------+  |  |
|  |  |   (1500 mA)      |   | - tta (Adapter)   |                               |  |
|  |  | - Thermal Levels |   | - tusb (USB Port) |                               |  |
|  |  +------------------+   +-------------------+                               |  |
|  +-----------------------------------------------------------------------------+  |
|                                   |                                               |
|               +-------------------+--------------------+                          |
|               | (High Current Path: 2:1 Pump)          | (Standard 5V Path: Buck) |
|               v                                        v                          |
|  +--------------------------------------+  +-----------------------------------+  |
|  | RT9759 Charge Pump Driver (5-0066)   |  | RT9471 Switching Charger (5-0053) |  |
|  | - 2:1 Switched Capacitor Divider     |  | - Standard Buck Converter         |  |
|  | - Pin = Vbus * Ibus                  |  | - Max Current: 1.5A - 2.0A        |  |
|  | - Pout = Vbat * Ibat                 |  | - Used during startup / standby   |  |
|  | - Current Double: Ibat = 2 * Ibus    |  +-----------------------------------+  |
|  | - Efficiency: >97.5%                 |                                         |
|  +--------------------------------------+                                         |
|                   |                                                               |
+-------------------|---------------------------------------------------------------+
                    v
       +-------------------------+
       | Battery Cells (>6000mA) |
       +-------------------------+
```

---

## 3. Deep Dive: Transsion Direct Charge Pump dv2 Algorithm

### 3.1 Kernel Symbols & Execution Context
The direct charge pump algorithm runs as a high-priority kernel thread:
- **Thread Name**: `dv2_algo_task`
- **Entrypoint**: `__dv2_algo_threadfn`
- **Timer Routine**: `__dv2_algo_timer_cb` (enqueued via `alarmtimer_enqueue`)
- **Loop Frequency**: 500 ms – 1000 ms active, 10000 ms standby (`polling_interval = 10000`)
- **State Machine States**:
  - `START`: Initialization and handshake with RFC TA.
  - `SS_DVCHG`: Soft-start ramp of charge pump voltage and current (`idvchg_ss_init: 1200 mA`, step `250 mA`).
  - `CC_CV`: Active Constant Current / Constant Voltage regulation.
  - `STOP`: Pump shutdown and fallback to switching charger (`__dv2_stop`).

### 3.2 The Adapter Input Current Limitation Function: `__dv2_get_ita_lmt`
Observed verbatim from kernel log:
```
[PCA]__dv2_get_ita_lmt __dv2_get_ita_lmt vbat(4040) icp_lmt(3000) gap(0)
[PCA]__dv2_get_ita_lmt tv_sec(783) lcd_on(241) fb_on(1) tran_game_mode(0 1500)
[PCA]__dv2_get_ita_lmt ita(org,tta,tbat,tdvchg,prlmt,throt,tpcb)=1500(3000,0,750,0,3000,-1,1500)
[PCA]__dv2_get_idvchg_lmt idvchg_lmt(ita_lmt,idvchg_cc)=1500(1500,3000)
```

#### Formula & Logic:
$$\text{ita\_lmt} = \min\Big(\text{ita\_org} - \text{derating}_{\text{thermal}}, \text{limit}_{\text{screen}}, \text{limit}_{\text{profile}}, \text{limit}_{\text{user}}\Big)$$

Where:
- $\text{ita\_org} = 3000\text{ mA}$ (from devicetree `ita_level = [3000, 2250, 2000, 1500]`, chosen by cable resistance `rcable_level = [400, 450, 550, 700]` m$\Omega$).
- $\text{derating}_{\text{thermal}} = \max(\text{tbat}, \text{tpcb}, \text{tdvchg}, \text{tswchg}, \text{tta})$.
- $\text{limit}_{\text{screen}}$:
  $$\text{limit}_{\text{screen}} = \begin{cases} 1500\text{ mA} (\text{game\_limit\_ita}), & \text{if } \text{lcd\_on} > 0 \lor \text{fb\_on} == 1 \\ \infty (\text{unconstrained}), & \text{if screen is off} \end{cases}$$
- In the observed log, even though the adapter can supply 3000 mA, `fb_on(1)` and `tpcb(44,7)` force `ita_lmt = 1500 mA`.

### 3.3 Screen State Interlock (`lcd_on`, `fb_on`, `tran_game_mode`)
- **Screen Trigger**: `fb_notifier_callback` registers `dv2_notifier_call` to the Linux Framebuffer / DRM notifier chain (`fb_notifier_call_chain`).
- When the display powers on, `fb_on` transitions to `1`, and `lcd_on` records the timestamp / active seconds counter.
- **Factory Test Bypass Node**:
  `/sys/devices/platform/charger/BN_TestMode`
  - Writing `1` to `BN_TestMode` forces `BatteryNotify = 0`, neutralizing the `lcd_on` and `fb_on` throttle gate inside `__dv2_get_ita_lmt`.

---

## 4. Thermal Step-Down Specification & Threshold Matrix

The thermal management in `pca_dv2_algo` is defined across 6 domains in `/sys/firmware/devicetree/base/odm/mtk_pe50/`. Each domain has a 9-element temperature boundary array (`level_def`) and a 9-element current derating array (`curlmt`):

### 4.1 Battery Cell Temperature Domain (`tbat`)
- **Source Sensor**: `mt635x-auxadc` channel 3 (`BAT_TEMP`) & `mtktsbattery`
- **Devicetree Nodes**: `tbat_level_def`, `tbat_curlmt`, `tbat_recovery_area`
- **Hysteresis Recovery Window**: 3°C

| Level Index | Threshold (°C) | Derating / Current Action | Status Description |
| :---: | :---: | :---: | :---: |
| 0 | 0 | -1 (Cutoff) | Severe Cold Shutoff |
| 1 | 0 | -1 (Cutoff) | Severe Cold Shutoff |
| 2 | 13 | -1 (Cutoff) | Cold Guard (Lithium Plating Protection) |
| 3 | 15 | Clamp to 1000 mA | Low Temperature Slow Charge |
| 4 | 25 | 0 mA derating | **Normal Optimal Charging Window (25°C - 41°C)** |
| 5 | 41 | Reduce by 250 mA | Warm Step 1 |
| 6 | 42 | Reduce by 750 mA | Warm Step 2 (Observed in log: `tbat: 750`) |
| 7 | 43 | Reduce by 1250 mA | Hot Step 3 |
| 8 | 45 | -1 (Cutoff) | **Over-Temperature Critical Trip (Stop Direct Charging)** |

### 4.2 Motherboard PCB Temperature Domain (`tpcb`)
- **Source Sensor**: `mtktsbtsmdpa` / NTC thermistor near PMIC and CPU
- **Devicetree Nodes**: `tpcb_level_def`, `tpcb_curlmt`

| Level Index | Threshold (°C) | Derating / Current Action | Status Description |
| :---: | :---: | :---: | :---: |
| 0 - 3 | 0 | 0 mA derating | Unconstrained Low Temp |
| 4 | 25 | 0 mA derating | **Normal PCB Operating Temp** |
| 5 | 43 | Reduce by 500 mA | PCB Warm Step 1 |
| 6 | 44 | Reduce by 1000 mA | PCB Warm Step 2 |
| 7 | 45 | Reduce by 1500 mA | **PCB Throttling Level 7 (Observed: cuts 3000mA -> 1500mA)** |
| 8 | 90 | -1 (Cutoff) | Critical Hardware Safety Shutdown |

### 4.3 Charge Pump IC Temperature Domain (`tdvchg`)
- **Source Sensor**: RT9759 internal die thermistor (`__rt9759_get_adc TDie`)
- **Devicetree Nodes**: `tdvchg_level_def`, `tdvchg_curlmt`
- **Hysteresis Recovery Window**: 3°C

| Level Index | Threshold (°C) | Derating / Current Action | Status Description |
| :---: | :---: | :---: | :---: |
| 0 - 3 | 0 - 5 | -1 / 300 mA clamp | Pre-charge verification |
| 4 | 25 | 0 mA derating | **Normal Operating Range (< 70°C)** |
| 5 | 70 | Reduce by 300 mA | Die Warm Step 1 |
| 6 | 75 | Reduce by 600 mA | Die Warm Step 2 |
| 7 | 80 | Reduce by 900 mA | Die Warm Step 3 |
| 8 | 85 | -1 (Cutoff) | **RT9759 Die Over-Temperature Protection (OTP)** |

### 4.4 Switching Charger IC Temperature Domain (`tswchg`)
- **Source Sensor**: RT9471 / primary buck PMIC internal thermistor
- **Devicetree Nodes**: `tswchg_level_def`, `tswchg_curlmt`
- **Thresholds**: `[0, 0, 0, 5, 25, 65, 70, 75, 80]` °C
- **Derating**: `[-1, -1, -1, 200, 0, 200, 300, 400, -1]` mA

### 4.5 Travel Adapter Temperature Domain (`tta`)
- **Source Sensor**: RFC TA query via I2C (`rfc_prop_ta_get_temp1`, `temp2`)
- **Thresholds**: `[0, 0, 0, 0, 25, 70, 80, 90, 95]` °C
- **Derating**: `[0, 0, 0, 0, 0, 300, 600, 900, -1]` mA

---

## 5. RT9759 2:1 Charge Pump Driver Specification

### 5.1 Hardware Identity & Bus Mapping
- **Driver**: `rt9759`
- **Bus Address**: I2C Bus 5, 7-bit Slave Address `0x66` (`/sys/bus/i2c/drivers/rt9759/5-0066/`)
- **Device Tree Node**: `/sys/firmware/devicetree/base/i2c5@11016000/rt9759_slave@66`
- **Compatible String**: `richtek,rt9759`

### 5.2 Operating Principles (Switched Capacitor 2:1)
The RT9759 is a high-efficiency switched capacitor charge pump converter:
- **Voltage Transformation**: $V_{\text{bat}} = \frac{1}{2} V_{\text{bus}}$
- **Current Transformation**: $I_{\text{bat}} = 2 \times I_{\text{bus}} \times \eta$ ($\eta \approx 97.5\%$)
- **Operating Ratings**:
  - Input Voltage ($V_{\text{bus}}$): 6.8V – 10.5V (Nominal 8.8V – 9.6V)
  - Output Voltage ($V_{\text{bat}}$): 3.4V – 4.47V
  - Maximum Rated Battery Current ($I_{\text{bat}}$): 7000 mA (`ibat_max: 7000`)
  - Target Input Current ($I_{\text{ta}}$ / $I_{\text{bus}}$): 3000 mA – 3300 mA

### 5.3 Hardware Protection Thresholds (Non-Bypassable Safety)
The RT9759 hardware register trip points protect against catastrophic failure:
- `vbus_max_ovp`: 11,000 mV (Hardware over-voltage cutoff on input).
- `vbatovp`: 4,928 mV (Cell over-voltage hardware protection).
- `ibusocp`: 1,800 mA (Clamped state) / 3,900 mA (Unclamped state).
- `ibatocp`: 7,800 mA hardware limit.
- `tdieotp`: 85°C silicon thermal shutdown.

---

## 6. Comprehensive Sysfs Node & Control Parameter Catalog

### 6.1 Transsion Direct Charge Pump Platform (`/sys/devices/platform/pca_dv2_algo/`)
| Node Name | R/W | Type / Format | Function / Role | Override Syntax |
| :--- | :---: | :--- | :--- | :--- |
| `dv2_debug` | R/W | String format: `[num0,num1,num2,num3,num4,num5,num6]` | Direct override of DV2 thermal table arrays in memory | Command 101 (tbat): `echo "[101,85,88,90,6000,6000,6000]" > dv2_debug`<br>Command 102 (tdvchg): `echo "[102,85,88,90,6000,6000,6000]" > dv2_debug` |

### 6.2 Transsion ODM Battery Platform (`/sys/devices/platform/odm/odm:tran_battery/`)
| Node Name | R/W | Type / Format | Function / Role | Override Syntax |
| :--- | :---: | :--- | :--- | :--- |
| `pcb_thermal_debug` | R/W | String format: `[num1,num2,num3,num4,num5]` | Overrides Transsion PCB thermal thresholds and current clamp steps | `echo "[85,6000,90,5000,4500]" > pcb_thermal_debug` |
| `Pump_Express_ICharger` | R | Integer (mA / step) | Live measured adapter current drawn by charge pump | Read-only telemetry |
| `Pump_Express_VCharger` | R | Integer (mV) | Live measured adapter voltage supplied to charge pump | Read-only telemetry |
| `Charging_CallState` | R/W | Integer (0, 1, 2) | Telephony in-call charging limiter | `0` = unrestricted fast charge<br>`1` = suspend charging<br>`2` = clamp to 1.0A |
| `OTG_CTL` | R/W | Integer (0, 1) | Reverse charging / OTG power deliver mode | `0` = normal sink, `1` = OTG host |

### 6.3 MediaTek Generic Charger Platform (`/sys/devices/platform/charger/`)
| Node Name | R/W | Default | Function / Role | Unthrottled Value |
| :--- | :---: | :---: | :--- | :--- |
| `BN_TestMode` | R/W | `0` | Battery Notify Test Mode. Bypasses screen-on & OEM thermal notify clamps | `1` |
| `BatteryNotify` | R/W | `1` | Kernel-to-userspace notification trigger for charging throttles | `0` (or locked) |
| `sw_jeita` | R/W | `1` | MediaTek Software JEITA thermal step-down governor | `0` |
| `enable_sc` | R/W | `1` | Switched Capacitor (Direct Charge Pump) Master Switch | `1` |
| `sc_ibat_limit` | R/W | `2000` | Battery current limit for switched capacitor mode (mA) | `6000` – `12000` |
| `input_current` | R/W | `24576` | Main PMIC input current limit register (internal steps) | `24576` (=6000mA) |
| `chg1_current` | R/W | `24576` | Primary charge path current limit | `24576` |
| `chg2_current` | R/W | `24576` | Secondary charge path current limit | `24576` |
| `pe20` | R/W | `1` | Pump Express 2.0 protocol enable | `1` |
| `pe40` | R/W | `1` | Pump Express 4.0 protocol enable | `1` |
| `pdc_max_watt` | R/W | `33` | Max negotiated Power Delivery / PPS wattage | `68` – `120` |
| `bypass_charger` | R/W | `0` | Hardware bypass charging (Vsys from adapter, battery disconnected) | `0` (Normal) / `1` (Bypass) |
| `tran_charger_full` | R/W | `0` | Battery full charge suppression flag | `0` |
| `ADC_Charger_Voltage` | R | mV | Real-time ADC measured adapter input voltage | Telemetry |
| `Charger_Type` | R | Integer | 9 = PD/Fast Charger, 4 = DCP, 1 = SDP, 2 = CDP | Telemetry |

### 6.4 MediaTek Thermal Driver Interfaces (`/proc/driver/thermal/`)
| Node Name | R/W | Format | Function / Role | Unthrottled Value |
| :--- | :---: | :--- | :--- | :--- |
| `clabcct` | R/W | 7 integers (Policy, Temp, Kp, Ki, Kd, Max, Min) | Adaptive Battery Charging Current Throttling PID controller | `0 70000 1000 200000 5 6000 0` |
| `clabcct_lcmoff` | R/W | 9 integers | ABCCT parameters when screen is turned off | `0 70000 1000 200000 5 6000 0` |
| `tzbts_param` | R/W | 5 parameters | Battery thermistor NTC calibration table & pullup resistance | `100000 1800 4397119 7 0` |

### 6.5 Thermal Cooling Devices (`/sys/class/thermal/cooling_device*`)
| Device | Node Type | Max State | Function | Unthrottled State |
| :--- | :--- | :---: | :--- | :---: |
| `cooling_device53` | `mtk-cl-bcct02` | 1 | Battery charging current throttle step 2 | `0` |
| `cooling_device54` | `mtk-cl-bcct01` | 1 | Battery charging current throttle step 1 | `0` |
| `cooling_device55` | `mtk-cl-bcct00` | 1 | Battery charging current throttle step 0 | `0` |
| `cooling_device56` | `abcct` | 1 | Adaptive battery charging current throttle | `0` |
| `cooling_device57` | `abcct_lcmoff` | 1 | ABCCT when screen is off | `0` |

### 6.6 Universal Linux Power Supply Class (`/sys/class/power_supply/battery/`)
| Node Name | R/W | Units | Purpose | Unthrottled Value |
| :--- | :---: | :---: | :--- | :--- |
| `constant_charge_current_max` | R/W | $\mu\text{A}$ | PMIC fast charge constant current ceiling | `6000000` ($6.0\text{ A}$) |
| `constant_charge_current` | R/W | $\mu\text{A}$ | PMIC active target constant charge current | `6000000` |
| `current_max` | R/W | $\mu\text{A}$ | Maximum allowed current rail | `6000000` |
| `input_current_limit` | R/W | $\mu\text{A}$ | Input adapter current limit | `6000000` |
| `charge_control_limit_max` | R/W | Int | Android OS charging throttle ceiling | `0` |
| `charge_control_limit` | R/W | Int | Android OS active charge throttle | `0` |
| `input_suspend` | R/W | Boolean | Hardware charging suspend | `0` |
| `charging_enabled` | R/W | Boolean | Master battery charging gate | `1` |

---

## 7. Features Discovered

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| 1 | DV2 Algorithm | Screen-On Throttle Clamping | Clamps `ita` to 1500 mA when display is on (`lcd_on > 0`, `fb_on == 1`) | Display frame buffer state | `ita_lmt = 1500` mA | None (silent clamp in `__dv2_get_ita_lmt`) | `dmesg` & Devicetree `game_limit_ita` |
| 2 | DV2 Algorithm | Cable Resistance Qualification | Selects maximum `ita` step (3000, 2250, 2000, 1500 mA) based on measured cable impedance | Measured $R_{\text{cable}}$ (m$\Omega$) | `ita_level` assignment | Downgrades to 1500 mA if resistance > 700 m$\Omega$ | Devicetree `rcable_level` & `ita_level` |
| 3 | Thermal Control | PCB Level 7 Clamp | Cuts `ita` by 1500 mA when motherboard thermistor reaches 45°C | $T_{\text{pcb}} \ge 45^\circ\text{C}$ | `tpcb = 1500` mA derating | Cuts current in half | `dmesg` `__dv2_check_tpcb_level` & Devicetree `tpcb_curlmt` |
| 4 | Thermal Control | Battery Level 6 Clamp | Cuts `ita` by 750 mA when battery cell reaches 42°C | $T_{\text{bat}} \ge 42^\circ\text{C}$ | `tbat = 750` mA derating | Drops charging speed | `dmesg` `__dv2_check_tbat_level` & Devicetree `tbat_curlmt` |
| 5 | Thermal Control | Battery Critical Cutoff | Disables direct charge pump and drops to standard 5V buck when battery reaches 45°C | $T_{\text{bat}} \ge 45^\circ\text{C}$ | `DV2_TBAT = -1`, pump disabled | Fallback to 1.5A switching charger | Devicetree `tbat_level_def[8]` |
| 6 | Thermal Control | Charge Pump Die OTP | Disables charge pump when RT9759 die reaches 85°C | $T_{\text{die}} \ge 85^\circ\text{C}$ | IRQ trigger, pump disabled | Hardware pump shutoff | Devicetree `tdvchg_level_def[8]` & kallsyms `rt9759_tdieotp_irq_handler` |
| 7 | Sysfs Interface | Transsion Test Mode (`BN_TestMode`) | Factory testing override that suppresses battery notifications and display deratings | Write `1` to `BN_TestMode` | `BatteryNotify = 0`, screen clamp bypassed | Ignored if node missing | Sysfs `/sys/devices/platform/charger/BN_TestMode` |
| 8 | Sysfs Interface | PCB Thermal Debug Override | Allows custom temperature thresholds and currents for PCB thermal stages | `[Tpcb1, Icurr1, Tpcb2, Icurr2, Imin]` | `Tpcb_store_ok = 1` | "no para" if syntax invalid | Sysfs `odm:tran_battery/pcb_thermal_debug` |
| 9 | Sysfs Interface | DV2 Thermal Table Override | Overrides in-kernel battery and charge pump thermal step-down tables | Command 101 (`tbat`) / 102 (`tdvchg`) with 7-element array | In-memory table replacement | Reverts to OEM on reboot | Sysfs `pca_dv2_algo/dv2_debug` |
| 10 | Hardware Driver | RT9759 2:1 Current Multiplication | Direct 2:1 switched capacitor charge pump converting 8.8V@3.3A to 4.4V@6.6A | $V_{\text{bus}}, I_{\text{bus}}$ | $V_{\text{bat}}, I_{\text{bat}} = 2 \times I_{\text{bus}}$ | OVP/OCP/UCP IRQ triggers | Kernel driver `rt9759` & I2C `5-0066` |
| 11 | Protocol Driver | Richtek Fast Charge (RFC TA) | High-voltage direct charging communication protocol between phone and adapter | I2C commands to `rfc_ta` | Voltage / current negotiation up to 10.5V | -121 EREMOTEIO if TA disconnected | Devicetree `pca_rfc_ta` & I2C `7-001b` |
| 12 | Thermal Control | MediaTek ABCCT PID Governor | Adaptive current throttling based on target temperature (42°C screen-on, 43°C screen-off) | Battery temperature vs target | Proportional current throttling | Can choke current to 600 mA | `/proc/driver/thermal/clabcct` |
| 13 | Userspace Daemon | thermalloadalgod Netlink Control | Userspace thermal management daemon listening on netlink socket | Kernel thermal uevents | Netlink throttle commands to `chg_nl_data_handler` | Can be paused with `SIGSTOP` | `ps -A` & `/proc/1903/fd/` socket inspection |
| 14 | Android Framework | Thermalservice Status Override | System command to force Android framework thermal state to nominal | `cmd thermalservice override-status 0` | Locks framework thermal status to 0 (NONE) | Reset via `cmd thermalservice reset` | `cmd thermalservice` help & dumpsys |

---

## 8. Edge Cases & Observed System Behaviors

| # | Feature / Scenario | Input Condition | Observed Behavior | Root Cause / Mitigation |
|---|-------------------|-----------------|-------------------|-------------------------|
| 1 | Screen-On Transition | User turns on display (`fb_on: 1`, `lcd_on > 0`) | Current drops immediately from ~6000 mA to ~2400 mA ($I_{\text{ta}}$ cuts 3000 -> 1500 mA) | Hardcoded `game_limit_ita` in `__dv2_get_ita_lmt`. Mitigated by `BN_TestMode = 1`. |
| 2 | Heavy Gaming / High Ambient Temp | Motherboard PCB reaches 45°C | $I_{\text{ta}}$ cut by 1500 mA (`tpcb: 1500`) even with screen off | PCB Level 7 thermal clamp. Mitigated by `pcb_thermal_debug [85,6000,90,5000,4500]`. |
| 3 | Battery Cell Warmup | Battery reaches 42°C | $I_{\text{ta}}$ cut by 750 mA (`tbat: 750`) | Battery Level 6 clamp. Mitigated by `dv2_debug [101,85,88,90,6000,6000,6000]`. |
| 4 | Cable Unplug During High Current | Physical USB-C cable disconnected while charging at 6A | `is_hardware_plugged` detects offline rails and adapter ADC < 3500 mV; RT9759 triggers `ibusucpf` (under-current fault) | Daemon must restore all cooling devices, reset trip points, and resume thermal daemons within 2s to prevent standby battery drain. |
| 5 | Battery Saturation Phase (CV Phase) | Battery SoC > 75%, $V_{\text{bat}} \ge 4.47\text{ V}$ | Net current naturally tapers down from 6000 mA to 3000 mA, then 1500 mA | Electrochemical saturation (`vbat_cv = 4470 mV`). Constant Current (CC) phase complete; cannot force 6A without exceeding cell voltage rating. |
| 6 | Standard 5V Charger (Non-RFC / Non-PD) | Plugged into standard 5V 2A charger ($V_{\text{bus}} \approx 5000\text{ mV}$) | Direct pump fails to start ($V_{\text{bus}} < 6800\text{ mV}$ minimum for RT9759); system uses standard switching charger | Operating as designed; direct charge pump requires 9V-11V input. |
| 7 | Integer Overflow Bug on Input Current | Writing `4294967295` to `/sys/devices/platform/charger/input_current` | Kernel driver rejects value and resets input current to `0 mA` | Value must be set to `24576` (= 6000 mA step) or valid register step. |
| 8 | Laptop / PC Data Port Connection | Plugged into USB SDP or CDP port | Detected via `Charger_Type` (1 or 2); current must be clamped to $\le 1500\text{ mA}$ | Prevents tripping laptop USB port over-current protection. |
| 9 | Hardware Over-Voltage Condition | $V_{\text{bus}} > 11.0\text{ V}$ or $V_{\text{bat}} > 4.928\text{ V}$ | RT9759 hardware IRQ immediately isolates switching FETs | Critical hardware safety protection must never be bypassed. |
| 10 | Thermal Daemon Deadlock | Freezing `thermalloadalgod` with `SIGSTOP` while phone is unplugged | Device cannot throttle CPU/GPU during heavy gaming on battery, causing extreme thermal dissipation | `handle_disconnect()` must send `SIGCONT` (18) to all frozen daemons upon cable unplug. |

---

## 9. Comprehensive Override & Execution Blueprint

To completely eliminate charging throttles and achieve continuous >6000 mA direct charge pump delivery below 80% SoC, the implementation must execute the following sequence:

```bash
# 1. Neutralize Transsion Display & Notification Throttle Gates
echo 1 > /sys/devices/platform/charger/BN_TestMode
echo 0 > /sys/devices/platform/charger/BatteryNotify
echo 0 > /sys/devices/platform/charger/tran_charger_full
echo 0 > /sys/devices/platform/charger/sw_jeita

# 2. Override Transsion Motherboard PCB Thermal Clamp (Push threshold to 85C, allow 6000mA)
echo "[85,6000,90,5000,4500]" > /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug 2>/dev/null
echo "[85,6000,90,5000,4500]" > /sys/devices/platform/tran_battery/pcb_thermal_debug 2>/dev/null

# 3. Override Direct Charge Pump DV2 Thermal Tables
echo "[101,85,88,90,6000,6000,6000]" > /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null
echo "[102,85,88,90,6000,6000,6000]" > /sys/devices/platform/pca_dv2_algo/dv2_debug 2>/dev/null

# 4. Disable MediaTek ABCCT Thermal Derater (Screen-On & Screen-Off)
echo "0 70000 1000 200000 5 6000 0" > /proc/driver/thermal/clabcct 2>/dev/null
echo "0 70000 1000 200000 5 6000 0" > /proc/driver/thermal/clabcct_lcmoff 2>/dev/null

# 5. Lock ABCCT and BCCT Cooling Devices to State 0
for c in /sys/class/thermal/cooling_device*; do
    if [ -f "$c/type" ]; then
        t=$(cat "$c/type" 2>/dev/null)
        case "$t" in
            *bcct*|*abcct*)
                chmod 666 "$c/cur_state" 2>/dev/null
                echo 0 > "$c/cur_state" 2>/dev/null
                chmod 444 "$c/cur_state" 2>/dev/null
                ;;
        esac
    fi
done

# 6. Unlock MediaTek Fast Charge & Charge Pump Rails
echo 68 > /sys/devices/platform/charger/pdc_max_watt 2>/dev/null
echo 24576 > /sys/devices/platform/charger/input_current 2>/dev/null
echo 24576 > /sys/devices/platform/charger/chg1_current 2>/dev/null
echo 24576 > /sys/devices/platform/charger/chg2_current 2>/dev/null
echo 12000 > /sys/devices/platform/charger/sc_ibat_limit 2>/dev/null
echo 1 > /sys/devices/platform/charger/enable_sc 2>/dev/null
echo 1 > /sys/devices/platform/charger/pe20 2>/dev/null
echo 1 > /sys/devices/platform/charger/pe40 2>/dev/null

# 7. Set Linux Power Supply Class Ceilings
BATT="/sys/class/power_supply/battery"
echo 0 > "$BATT/input_suspend" 2>/dev/null
echo 1 > "$BATT/charging_enabled" 2>/dev/null
echo 0 > "$BATT/charge_control_limit_max" 2>/dev/null
echo 0 > "$BATT/charge_control_limit" 2>/dev/null
echo 6000000 > "$BATT/constant_charge_current_max" 2>/dev/null
echo 6000000 > "$BATT/constant_charge_current" 2>/dev/null
echo 6000000 > "$BATT/current_max" 2>/dev/null
echo 6000000 > "$BATT/input_current_limit" 2>/dev/null

# 8. Pause Userspace Thermal Throttler Daemon & Override Android Framework Status
cmd thermalservice override-status 0 2>/dev/null
pkill -STOP -f thermalloadalgod 2>/dev/null
```
