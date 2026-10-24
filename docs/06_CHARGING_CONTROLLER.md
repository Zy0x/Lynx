# 🔋 Module 06: Charging Controller & Hardware Safety Engine
> **Subsystem**: Power, Charging Regulator & Direct Pump Controller (`native/lynxd/src/hardware/charging.rs`; legacy shell fallback archived at `archive/legacy/core/Charging-Controller.sh`)  
> **Standards**: POSIX Shell, Native Rust Engine, Hardware Thermal Safety, Switched-Capacitor Pump, AutoCut & Motherboard Bypass  
> **Deep Reference**: [`docs/references/TRANSSION_CHARGING_HARDWARE_PATH_REFERENCE.md`](./references/TRANSSION_CHARGING_HARDWARE_PATH_REFERENCE.md)  

---

## 1. Problem Statement & Battery Threat Model

### 1.1 The Dual Threat: Thermal Runaway vs. Aggressive Throttling
1. **Thermal Runaway Risk**: High-current fast charging (33W, 45W, 68W+) in sealed chassis can push battery temperatures past 48°C–52°C, causing lithium plating, internal gas swelling, and hardware failure.
2. **Aggressive OEM Throttling**: Manufacturers (such as Transsion / Infinix / Tecno, Xiaomi, and Samsung) employ multi-layer aggressive throttling mechanisms that prematurely degrade charging speeds down to 1.0A (5W) or 1.5A when:
   - The screen is turned on (`Charging_CallState=1`), even at low brightness.
   - High refresh rate (90Hz / 120Hz) is active.
   - Battery temperature reaches mild thresholds (41°C, 43°C, 45°C).
   - Motherboard PCB thermal sensors trigger derating (`pcb_thermal_debug`).
   - In-memory thermal tables throttle the direct charge pump (`dv2_debug`).

> [!CAUTION]
> **Non-Negotiable Hardware Thermal Ceiling**:
> While OEM software throttling stages (41°C–45°C) are safely neutralized in Hyper Mode, **the physical hardware emergency ceiling is strictly enforced**:
> - **45.0°C – 49.9°C**: Safe warning buffer.
> - **≥ 50.0°C (Emergency Cutoff Ceiling)**: The native daemon immediately drops the direct pump and clamps current to 750 mA until temperature cools below 45.0°C.
> - **Silicon OVP/OCP/UVP**: Hardware comparators inside the PMIC and charger IC (`vbatovp`, `vbusovp`) operate independently in silicon and are **never disabled**.

---

## 2. Universal & Vendor Hardware Node Matrix

| Subsystem | Sysfs Node Path | Supported Vendors | Description |
| :--- | :--- | :--- | :--- |
| **Battery Temp** | `/sys/class/power_supply/battery/temp` | Universal | Decicelsius (e.g., `420` = 42.0°C). |
| **Physical AuxADC** | `/sys/bus/iio/devices/iio:device1/in_voltage3_BAT_TEMP_input` | MediaTek MT635x | MT6358 Channel 3 NTC thermistor reading in mV. Ground truth temperature. |
| **Battery Capacity** | `/sys/class/power_supply/battery/capacity` | Universal | State of Charge percentage (0–100). |
| **Constant Current** | `.../battery/constant_charge_current` | Universal | Microamperes target charging current (e.g. `6000000` = 6000mA). |
| **Input Current Limit** | `.../battery/input_current_limit` | Universal | Upper limit for VBUS input draw. |
| **Input Suspend** | `.../battery/input_suspend` | Universal | `1` = Suspends VBUS draw (Bypass Mode). |
| **Charging Enabled** | `.../battery/charging_enabled` | Universal | `0` = Disables battery charging while keeping phone powered. |
| **Transsion CallState** | `/sys/devices/platform/odm/odm:tran_battery/Charging_CallState` | Transsion MTK | `0` = Normal, `1` = Screen-On / Call throttle clamp (1.0A), `2` = Cool clamp. |
| **Transsion PCB Derate**| `/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug` | Transsion MTK | PCB thermal derating array `[T1, I1, T2, I2, I3]`. |
| **Direct Pump Enable** | `/sys/devices/platform/charger/enable_sc` | MediaTek RT9759 | `1` = Engages 2:1 Switched-Capacitor Direct Pump. |
| **Direct Pump Limit** | `/sys/devices/platform/charger/sc_ibat_limit` | MediaTek RT9759 | Current limit into battery cell from direct pump (e.g. 12000 mA). |
| **Input Current Max** | `/sys/devices/platform/charger/input_current` | MediaTek MTK | Maximum adapter input current (33170 for 33W+ PE+). |
| **PDC Max Wattage** | `/sys/devices/platform/charger/pdc_max_watt` | MediaTek MTK | PD/PE negotiated power ceiling (120 for unconstrained). |
| **Battery Temp Override**| `/sys/devices/platform/battery/Battery_Temperature` | MediaTek MTK | Debug override node. Clamped `[-2, 50]`. Must be dynamically synced on unplug. |
| **Qualcomm Direct** | `/sys/class/qcom-battery/direct_charging` | Qualcomm SMB1390 | Qualcomm dual-pump direct charging toggle. |
| **Qualcomm Restrict** | `/sys/class/qcom-battery/restricted_charging` | Qualcomm | Qualcomm proprietary charging limiter switch. |

---

## 3. The 4-Tier Charging Engine Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       LYNX 4-TIER CHARGING PIPELINE                         │
├─────────────────────────────────────────────────────────────────────────────┤
│  [ Tier R1: Screen-On & Refresh Rate Throttle Elimination ]                 │
│  • Force Charging_CallState = 0 (Permission Lock 0444)                      │
│  • tran_game_mode = 0 | pdc_max_watt = 120                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│  [ Tier R2: Multi-Domain Thermal Neutralization ]                           │
│  • Overrides PCB thermal derating array: [95,6000,100,6000,6000] (Lock 0444)│
│  • Overrides DV2 direct pump tables: commands 101 & 102 (Lock 0444)         │
│  • Spoofs Battery_Temperature = 28 during hyper mode (Lock 0444)            │
│  • clabcct & clabcct_lcmoff = 0 | cooling_device53..57 cur_state = 0         │
│  • Suspends aggressive OEM thermal daemons (SIGSTOP: thermalloadalgod, etc.)│
│  • cmd thermalservice override-status 0                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│  [ Tier R3: Direct Charge Pump High Current (>6A) Delivery ]                │
│  • enable_sc = 1 | pe20 = 1 | pe40 = 1 | BN_TestMode = 1 | BatteryNotify = 0│
│  • input_current = 33170 | sc_ibat_limit = 12000                            │
│  • chg1_current = 33170 | chg2_current = 33170                             │
│  • RT9759 2:1 Switched-Capacitor Output Formula:                            │
│    I_pump = 2 * I_bus * 0.97 => 2 * 3.3A * 0.97 = 6.4A                      │
│    I_net = I_pump - I_sys => 6.4A - 0.35A = ~6.05A Bersih ke Baterai        │
├─────────────────────────────────────────────────────────────────────────────┤
│  [ Tier R4: Deterministic Fail-Safe Disconnect & Dynamic Temp Sync ]        │
│  • Triple-Vector Disconnect Invariant (<500ms latency)                      │
│  • Restores session baseline and unlocks sysfs permissions (0666)           │
│  • Resumes suspended thermal daemons (SIGCONT)                              │
│  • Dynamically syncs authentic physical AuxADC temp to Battery_Temperature  │
│  • dumpsys battery reset & cmd thermalservice reset                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Richtek RT9759 2:1 Switched-Capacitor Pump Mathematics

On Transsion 33W, 45W, and 68W chargers, adapter VBUS negotiates to **9.4V – 10.5V** with an input current ($I_{\text{bus}}$) of **3.3A (3300 mA)**:
$$I_{\text{pump}} = 2 \times I_{\text{bus}} \times \eta \quad (\eta \approx 0.97)$$
$$I_{\text{pump}} = 2 \times 3300\text{ mA} \times 0.97 = \mathbf{6402\text{ mA}}$$

Subtracting motherboard and display consumption ($I_{\text{sys}} \approx 300 - 400\text{ mA}$):
$$I_{\text{net}} = 6402\text{ mA} - 350\text{ mA} = \mathbf{6052\text{ mA } (6.05\text{ A})}$$

Net current flowing across the Kelvin-sensed $R_{\text{SENSE}}$ shunt into the battery cell consistently surpasses **6.0A**.

### 4.1 Electrochemical CC/CV Phase Tapering Profile:
- **< 80% SoC & $V_{\text{bat}} < 4470\text{ mV}$**: Constant Current (CC) Peak ($12000\text{ mA}$ target, $>6.0\text{A}$ delivered).
- **80% – 94% SoC (or $V_{\text{bat}} \ge 4470\text{ mV}$)**: Constant Voltage (CV) Step-Down ($3500\text{ mA}$ target).
- **95% – 99% SoC**: Saturation Trickle ($750\text{ mA}$).
- **100% SoC (or status "Full")**: Full AutoCut Motherboard Bypass ($0\text{ mA}$).

---

## 5. Critical Breakthrough: `Battery_Temperature` Signed Integer Behavior

### 5.1 The Root Cause of 0°C / -1°C Stuck Telemetry
In MediaTek BSP source code (`battery_meter.c`):
- Node: `/sys/devices/platform/battery/Battery_Temperature`
- Valid Range: `[-2, 50]` degrees Celsius.
- Writing `"65535"` was previously attempted under the assumption that it was an un-override flag (like `UI_SOC`).
- However, the kernel uses `simple_strtol()` cast to a signed 16-bit integer: **65535 evaluates to `-1`**.
- This locked `/sys/class/power_supply/battery/temp` permanently to **`-10` (-1.0°C)** or **`0` (0.0°C)**!
- The driver **never self-reverts to raw AuxADC** once written.

### 5.2 The Permanent Solution: Dynamic Physical AuxADC Synchronization
In `native/lynxd/src/hardware/charging.rs`:
```rust
pub fn sync_authentic_battery_temp() {
    if !Path::new(BATT_TEMP_NODE).exists() {
        return;
    }
    let real_dc = Self::read_real_battery_temp_dc();
    let target_c = (real_dc / 10).clamp(15, 50);

    // Skip redundant writes if temperature is unchanged
    if let Some(curr_c) = Self::read_i64(BATT_TEMP_NODE) {
        if curr_c == target_c {
            return;
        }
    }

    Self::set_perm(BATT_TEMP_NODE, 0o666);
    let _ = fs::write(BATT_TEMP_NODE, target_c.to_string());
    Self::set_perm(BATT_TEMP_NODE, 0o666);
}
```
When the cable is disconnected or during standard charging, the daemon periodically updates `Battery_Temperature` with the authentic physical temperature from AuxADC Channel 3 (`in_voltage3_BAT_TEMP_input`). Android's BatteryService and the Lynx Companion app smoothly track live battery temperature without freeze or reboot.

---

## 6. Verification & Test Suite Parity

All charging control and failsafe logic is covered by a 4-tier hermetic test suite:
- **Unit Tests (`cargo test`)**: 54 tests validating state evaluation, pump current math, capacitor float decoupling, AuxADC conversion, and clamped temperature syncing.
- **E2E Tests (`python tests/e2e/run_e2e_tests.py`)**: 41 tests passing 100% across feature isolation, boundary transitions, cross-domain stress, and real-world scenarios.
- **Physical Testbed Verification**: Verified live on Infinix Note 11S (`X698`, MT6781, Android 14) with Transsion PE+ 33W/45W fast chargers.
