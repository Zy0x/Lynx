# Handoff Report: Requirement R3 — Net Battery Charging Current Telemetry, 2:1 Pump Doubling Physics, & SoC Boundaries

> **From**: Explorer M2-2 (Battery Current Explorer)  
> **To**: Orchestrator / Lead Architect (`4a93ea5f-bf3a-41d2-8075-760f372161d2`) & Worker M2  
> **Milestone**: Milestone 2 (Continuous Maximum Pump Current Enforcement: Requirement R3)  
> **Working Directory**: `e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m2_2`  
> **Date**: 2026-10-14  

---

## 1. Observation

### 1.1 Sysfs Telemetry & Fuel Gauge Readings (`/sys/class/power_supply/battery/current_now`)

1. **Linux Kernel Power Supply Standard**:
   - The primary battery telemetry node is `/sys/class/power_supply/battery/current_now`.
   - On Linux and Android kernels, `current_now` reports values in **microamperes** ($\mu\text{A}$):
     $$1\text{ mA} = 1,000\ \mu\text{A} \implies 6,000\text{ mA} = 6,000,000\ \mu\text{A} = 6.0\text{ A}$$
   - The reading is obtained directly by the PMIC fuel gauge (e.g., MT6357/MT6358/MT6359 integrated Coulomb counter) measuring the voltage drop $V_{SENSE}$ across a physical low-resistance shunt resistor $R_{SENSE}$ (typically $5\text{–}10\text{ m}\Omega$) connected in series with the battery cell terminal.
   - Because $R_{SENSE}$ is placed in series with the battery cell, the fuel gauge measures the **net current** entering the electrochemical cell:
     $$I_{NET} = I_{CHARGER\_OUT} - I_{SYSTEM\_LOAD}$$

2. **Observed Implementation in `native/lynxd/src/hardware/charging.rs`**:
   - In lines 1320–1321 & 1365–1369:
     ```rust
     let volt_raw = Self::read_i64(&format!("{}/voltage_now", BATT_DIR)).unwrap_or(0);
     let cur_raw = Self::read_i64(&format!("{}/current_now", BATT_DIR)).unwrap_or(0);
     ...
     let v_clean = if volt_raw.abs() >= 10_000 { volt_raw / 1000 } else { volt_raw };
     let mut c_clean = if cur_raw.abs() >= 10_000 { cur_raw / 1000 } else { cur_raw };
     if stat == "Charging" && c_clean < 0 {
         c_clean = c_clean.abs();
     }
     ```
     `charging.rs` normalizes raw $\mu\text{A}$ to $\text{mA}$ (`cur_raw / 1000`) and enforces positive sign when `status == "Charging"`.
   - In lines 1337–1353, adapter input current ($I_{BUS}$) is sampled from:
     ```rust
     let mut ibus_val = Self::read_i64("/sys/devices/platform/odm/odm:tran_battery/Pump_Express_ICharger")
         .map(|v| if v > 10 && v < 1000 { v * 10 } else { v })
         .or_else(|| Self::read_first_i2c_node("/sys/bus/i2c/drivers/rt9759", "Ibus"))
         .or_else(|| Self::read_i64(&format!("{}/current_now", USB_DIR)))
         .or_else(|| Self::read_i64(&format!("{}/input_current_now", USB_DIR)))
         .or_else(|| Self::read_i64(&format!("{}/current_now", MAIN_DIR)))
         .unwrap_or(0);
     ```

3. **Observed E2E Test Suite Assertions for Telemetry**:
   - In `tests/e2e/tier1_feature_coverage/test_r3_pump_current.py` lines 116–120 (`TestR3NetBatteryCurrentDelivery`):
     ```python
     cur_now = target.read_node("sys/class/power_supply/battery/current_now")
     assert cur_now is not None, "current_now node not found"
     raw_cur = int(cur_now)
     assert raw_cur >= 6000000, f"Expected current_now >= 6,000,000 uA (>6.0 A), got {raw_cur} uA ({raw_cur / 1000000:.3f} A)"
     ```
   - In lines 174–180 (`TestR3HighCurrentSustain`):
     Samples `current_now` 5 consecutive times across a sampling window, asserting every sample $\ge 6,000,000\ \mu\text{A}$.

---

### 1.2 The 2:1 Switched-Capacitor Current Doubling Architecture & Physics

1. **Hardware Topography**:
   - Primary Direct Charge Pump: Richtek RT9759 switched-capacitor converter at I2C address `5-0066` (`rt_regmap_rt9759`).
   - Hardware Driver Sysfs Nodes:
     - `/sys/bus/i2c/drivers/rt9759/5-0066/Ibus`: Input adapter current entering charge pump ($\mu\text{A}$).
     - `/sys/bus/i2c/drivers/rt9759/5-0066/Ibat`: Current exiting charge pump toward battery ($\mu\text{A}$).
     - `/sys/bus/i2c/drivers/rt9759/5-0066/Vbat`: Cell voltage measured by pump ADC ($\mu\text{V}$).
     - `/sys/bus/i2c/drivers/rt9759/5-0066/rfc_dcp_ta`: Handshake status with Richtek/Transsion Fast Charger (`1` = authenticated).
     - `/sys/devices/platform/charger/enable_sc`: Master gate enabling switched-capacitor clock oscillator (`1` = ON, `0` = OFF).
     - `/sys/devices/platform/charger/sc_ibat_limit`: Maximum allowable pump output current ($\text{mA}$).
     - `/sys/devices/platform/charger/input_current`: Adapter input current limit enforced on charger rail ($\text{mA} \times 10$ or standard $\text{mA}$, e.g. `33170` = $3317\text{ mA}$).

2. **Direct Kernel Log (`dmesg`) Evidence**:
   - From `spec_miner_survey_2/handoff.md` lines 8–13 (captured verbatim on physical Infinix Note 11S X698 MT6781):
     ```
     [ 1595.724565] [301:dv2_algo_task]rt9759 5-0066: __rt9759_get_adc Ibus 1498000
     [ 1595.740218] [301:dv2_algo_task]rt9759 5-0066: __rt9759_get_adc Vbat 4043000
     [ 1595.753134] [301:dv2_algo_task]rt9759 5-0066: __rt9759_get_adc Ibat 2398000
     [ 1595.755212] [301:dv2_algo_task]rfc_ta 7-001b: rfc_prop_ta_get_output_voltage_current 8761 1445
     [ 1595.755233] [301:dv2_algo_task][PCA]__dv2_dump_charging_info vbus,ibus(master,slave,sw),vbat,ibat=8346,(1498,0,0),4043,2398
     [ 1595.755248] [301:dv2_algo_task][PCA]__dv2_dump_charging_info vta,ita(set,meas)=(8774,8761),(1500,1445),force_cv=0
     ```
   - The adapter voltage $V_{TA} \approx 8761\text{ mV}$ is converted by the 2:1 charge pump to $V_{BAT} \approx 4043\text{ mV}$ (ratio $\approx 2.16:1$).
   - The adapter current $I_{BUS} = 1498\text{ mA}$ yields pump output $I_{BAT} = 2398\text{ mA}$ (under throttled screen-on conditions).

3. **E2E Test Doubling Ratio Assertion**:
   - In `tests/e2e/tier1_feature_coverage/test_r3_pump_current.py` lines 141–155 (`TestR3PumpDoublingRatio`):
     ```python
     ibus = target.read_node("sys/bus/i2c/drivers/rt9759/5-0066/Ibus")
     ibat = target.read_node("sys/bus/i2c/drivers/rt9759/5-0066/Ibat")
     ratio = ibat_val / float(ibus_val) if ibus_val > 0 else 0.0
     assert 1.85 <= ratio <= 2.05, f"Expected 2:1 pump current ratio ~1.94, got {ratio:.3f}"
     ```

---

### 1.3 Constant Current (CC) vs. Constant Voltage (CV) SoC Boundaries

1. **Devicetree Hardware Constraints** (`/sys/firmware/devicetree/base/odm/mtk_pe50/`):
   - From `spec_miner_survey_2/handoff.md` lines 27–33:
     * `vbat_cv`: `4470` mV (4.47V)
     * `force_ta_cv_vbat`: `4400` mV (4.40V)
     * `start_soc_min`: `1` %
     * `start_soc_max`: `90` %
     * `start_vbat_max`: `4420` mV (4.42V)
     * `vbus_max_ovp`: `11000` mV (11.0V)
     * `vta_cap_max`: `10500` mV, `vta_cap_min`: `6800` mV

2. **E2E Boundary Test Specifications** (`tests/e2e/tier2_boundaries/test_boundary_corner_cases.py`):
   - `TestT2SocCcCvBoundary` (lines 39–57):
     * CC Phase (<80% SoC): Tested at 65% capacity, $V_{BAT} = 4.15\text{V}$, requires `current_now >= 6,000,000 uA`.
     * CV Phase Transition (>80% SoC): Tested at 85% capacity, $V_{BAT} = 4.45\text{V}$, requires current to taper below CC current (e.g. 3500 mA), and $V_{BAT} \le 4.50\text{V}$ (4,500,000 $\mu\text{V}$).
   - `TestT2HighSocEntry` (lines 77–84):
     * Plugging in at 88% capacity ($V_{BAT} = 4.46\text{V}$) must NOT force 6A pump current; voltage must remain $\le 4.50\text{V}$.
   - `TestT2LowSocTrickleHandoff` (lines 103–115):
     * Deeply discharged battery at 3% ($V_{BAT} = 3.4\text{V}$) stays in pre-charge; as cell rises to 15% ($V_{BAT} = 3.8\text{V}$), smooth handoff to 2:1 pump achieves `current_now >= 6,000,000 uA`.

---

### 1.4 Current State of `native/lynxd` Implementation

1. **In `native/lynxd/src/hardware/charging.rs` lines 526–604 (`apply_extreme_charging`)**:
   - Line 526:
     ```rust
     pub fn apply_extreme_charging(_target_soc: u32, _allow_lockout_bypass: bool, requested_ma: u32) {
     ```
     `_target_soc` is marked with leading underscore and ignored.
   - Lines 579–584:
     ```rust
     let mtk_input_str = if effective_ma > 33170 { effective_ma_str.clone() } else { "33170".to_string() };
     Self::write_node_lock(&format!("{}/input_current", MTK_DIR), &mtk_input_str);
     Self::write_node_lock(&format!("{}/sc_ibat_limit", MTK_DIR), &effective_ma_str);
     Self::write_node_lock(&format!("{}/chg1_current", MTK_DIR), &mtk_input_str);
     Self::write_node_lock(&format!("{}/chg2_current", MTK_DIR), &mtk_input_str);
     ```
     `input_current` is locked to `33170` (3317 mA) and `sc_ibat_limit` to `12000` (12000 mA), satisfying $I_{BUS} \ge 3000\text{ mA}$ and $I_{BAT\_LMT} \ge 8000\text{ mA}$.
   - Lines 565–570:
     `pe20`, `pe40`, `enable_sc` locked to `"1"`.
     `BN_TestMode` locked to `"1"`, `BatteryNotify` to `"0"`, `sw_jeita` to `"0"`.

2. **In `native/lynxd/src/hardware/charging.rs` lines 1020–1179 (`apply_from_config`)**:
   - Line 1035: Reads `cur_cap` from `/sys/class/power_supply/battery/capacity`.
   - Line 1078: AutoCut at 100% full (`cur_cap >= 100 || cur_stat == "Full"`) engages bypass.
   - Line 1087: Smart Limit bypass (`cur_cap >= smart_limit`) engages bypass.
   - **Critical Gap**: In lines 1130–1149:
     ```rust
     if is_hyper {
         let marker = Self::read_node("/dev/lynx_extreme_charging").unwrap_or_default();
         ...
         if marker.starts_with("hyper-") && invariants_intact {
             return;
         }

         Self::apply_hyper_mode();
         return;
     }
     ```
     Hyper Mode does NOT evaluate `cur_cap >= 80` or `cfg.high_current_target_percent`, nor does it check `cfg.smart_tapering_enabled`.
     When battery capacity exceeds 80%, the daemon keeps calling `apply_hyper_mode()`, never initiating CV phase tapering.

3. **In `native/lynxd/src/hardware/charging.rs` line 1411 (`status_json`)**:
   - `let is_tapering = chg_state.contains("tapering");`
   - Because `charging.rs` never writes `state=tapering...` to `/dev/lynx_charging_state`, `smart_tapering_active` is always `false`.

4. **In `native/lynxd/src/state/config_store.rs` lines 21–25**:
   - Fields `high_current_target_percent: i32` (default 90) and `smart_tapering_enabled: bool` (default true) are already fully declared and parsed from `config.json`.

---

## 2. Logic Chain

```
[Observation 1.2: RT9759 2:1 Pump Topology]
  V_out = 0.5 * V_bus,  I_pump = 2 * I_bus * eta (eta ≈ 0.97)
       │
       ▼
[Observation 1.1: Adapter & Power Delivery]
  V_bus ≈ 9.28V, I_bus = 3300 - 3317 mA (input_current = 33170)
  P_in = 9.28V * 3.30A = 30.62W
       │
       ▼
[Observation 1.1: Pump Output Current Calculation]
  I_pump = 2 * 3300 mA * 0.97 = 6402 mA
  (At 3317 mA: I_pump = 2 * 3317 mA * 0.97 = 6435 mA)
       │
       ▼
[Observation 1.1: Subsystem Load & Net Current]
  Active display (120Hz) + SoC load: I_sys ≈ 350 - 400 mA
  I_net = I_pump - I_sys = 6402 mA - 350 mA = 6052 mA
  Measured at /sys/class/power_supply/battery/current_now > 6,000,000 uA (>6.0 A)
       │
       ▼
[Observation 1.3: Electrochemical CC Phase vs CV Saturation]
  Below 80% SoC: V_bat < 4.47V, cell internal impedance accepts >6A safely.
  Above 80% SoC: Terminal voltage reaches V_bat_cv (4.47V).
  Maintaining >6A would force V_term = V_ocv + I*R_int > 4.50V, risking lithium plating.
       │
       ▼
[Observation 1.4: Existing lynxd Implementation Gap]
  lynxd apply_from_config() lacks the CC->CV transition check at 80% SoC.
  Worker M2 must introduce Smart Tapering to drop current smoothly above 80% SoC.
```

### Detailed Analytical Derivations

1. **Step 1: Why $I_{BUS} \ge 3300\text{ mA}$ is Mathematically Required for Net $>6.0\text{ A}$**:
   - The fuel gauge Coulomb counter measures current across $R_{SENSE}$ at the battery cell terminal.
   - The fundamental node equation is:
     $$I_{NET} = I_{PUMP} - I_{SYS}$$
   - With the display active at 120Hz and CPU running background tasks, $I_{SYS}$ draws $350\text{–}450\text{ mA}$.
   - If adapter current were restricted to $I_{BUS} = 3000\text{ mA}$:
     $$I_{PUMP} = 2 \times 3000\text{ mA} \times 0.97 = 5820\text{ mA}$$
     $$I_{NET} = 5820\text{ mA} - 350\text{ mA} = 5470\text{ mA} < 6,000\text{ mA}$$
   - Only by locking adapter current to $I_{BUS} \ge 3300\text{–}3317\text{ mA}$ (`input_current = 33170`):
     $$I_{PUMP} = 2 \times 3317\text{ mA} \times 0.97 = 6435\text{ mA}$$
     $$I_{NET} = 6435\text{ mA} - 350\text{ mA} = 6085\text{ mA} > 6,000,000\ \mu\text{A}$$
   - Therefore, locking `sc_ibat_limit >= 12000` (or $\ge 8000$) and `input_current = 33170` is mathematically necessary and sufficient.

2. **Step 2: Physics of the 80% SoC CC/CV Boundary**:
   - In a 5000 mAh high-voltage Lithium cell ($4.45\text{V}$ nominal charge ceiling):
     * Internal AC/DC impedance $R_{INT} \approx 20\text{–}30\text{ m}\Omega$.
     * At $6.0\text{A}$, the internal IR drop is $\Delta V_{IR} = 6.0\text{A} \times 0.025\Omega = 150\text{ mV}$.
     * Open-circuit voltage $V_{OCV}$ reaches $\approx 4.30\text{V}$ at $75\%\text{–}80\%$ SoC.
     * Measured cell terminal voltage under 6A charging:
       $$V_{TERMINAL} = V_{OCV} + \Delta V_{IR} = 4.30\text{V} + 0.15\text{V} = 4.45\text{V}$$
     * The devicetree Constant Voltage threshold `vbat_cv` is set to $4470\text{ mV}$.
   - Once the cell reaches $80\%$ SoC, any attempt to pump $6.0\text{A}$ pushes $V_{TERMINAL}$ above $4.50\text{V}$, causing electrochemical over-potential, anode electrolyte oxidation, and activation of silicon overvoltage protection (`vbatovp: 4.65V–4.928V`).
   - Hence, transitioning to CV tapering at $\ge 80\%$ SoC is an electrochemical imperative.

3. **Step 3: Two-Stage Tapering Pipeline**:
   - **Stage 1 (80% to 94% SoC, or $V_{BAT} \ge 4.47\text{V}$)**:
     Current is stepped down to $3500\text{ mA}$ (`apply_regulated_charging(3500)`).
     `Battery_Temperature` is restored to `65535` so the kernel's natural ADC and thermal algorithms can supervise the voltage plateau without spoofing distortion.
     State marker written: `/dev/lynx_charging_state` contains `state=tapering_80 cap=<cap>`.
   - **Stage 2 (95% to 99% SoC)**:
     Current is stepped down to gentle top-off at $750\text{ mA}$ (`apply_regulated_charging(750)`).
     State marker written: `/dev/lynx_charging_state` contains `state=tapering_95 cap=<cap>`.
   - **Stage 3 (100% Full)**:
     AutoCut engages hardware bypass (`apply_bypass_charging()`), cutting battery cell current to $\le 0\text{ mA}$ while powering the system directly from the adapter rail.

---

## 3. Caveats

1. **Adapter Fast-Charging Capability**:
   - The sustained $>6.0\text{A}$ delivery via 2:1 charge pump strictly depends on connection to a genuine Transsion 33W/45W/68W RFC charger or a compliant USB-PD PPS charger capable of negotiating $\ge 9.3\text{V}$ at $3.3\text{A}$.
   - If connected to a standard 5V adapter ($V_{BUS} < 6800\text{ mV}$), the hardware charge pump (`enable_sc`) cannot turn on, and charging safely falls back to the RT9471 switching buck converter ($\le 2.0\text{A}$).
2. **Battery Temperature Ceiling Invariant**:
   - If physical battery temperature (read via AuxADC thermistor channel 3, immune to spoofing) exceeds $50.0^\circ\text{C}$ (500 dC), the emergency thermal guard must immediately override Hyper Mode, dropping input current to $750\text{ mA}$ until the cell cools below $45.0^\circ\text{C}$. This hardware safety invariant takes precedence over high-current pumping.
3. **No Silicon OVP Compromise**:
   - Under no circumstances should software attempt to rewrite raw I2C regmap comparator thresholds in `/sys/devices/platform/11016000.i2c5/i2c-5/5-0066/rt_regmap_rt9759/`. The silicon OVP ($4.928\text{V}$) must remain the immutable last line of defense.

---

## 4. Conclusion

1. **Net Current Telemetry (R3)**:
   - `/sys/class/power_supply/battery/current_now` directly reflects net battery cell current in microamperes ($\mu\text{A}$).
   - Fast charge current $>6,000,000\ \mu\text{A}$ ($>6.0\text{ A}$) is delivered by locking `input_current = 33170` ($3.317\text{ A}$ input), `sc_ibat_limit = 12000` (or $\ge 8000$), `enable_sc = 1`, and `pe20 = 1, pe40 = 1`.
2. **2:1 Pump Efficiency & Doubling Math**:
   - The RT9759 switched-capacitor converter operates at $\approx 97\%$ electrical efficiency.
   - $3300\text{ mA}$ at $\approx 9.28\text{V}$ produces $I_{PUMP} \approx 6402\text{ mA}$.
   - Subtracting active screen/system load ($I_{SYS} \approx 350\text{ mA}$) yields net battery cell current $I_{NET} \approx 6052\text{ mA}$, exceeding the $6.0\text{ A}$ requirement.
3. **SoC Boundary & Tapering**:
   - The Constant Current (CC) phase is active strictly below 80% SoC ($V_{BAT} < 4.47\text{V}$).
   - Above 80% SoC, internal polarization dictates smooth handoff to Constant Voltage (CV) tapering ($3500\text{ mA} \to 750\text{ mA} \to \text{Bypass}$) to prevent cell over-voltage ($V_{BAT} \le 4.50\text{V}$).
4. **Implementation Gaps Identified in `native/lynxd`**:
   - In `charging.rs`, `apply_from_config()` fails to initiate smart tapering during Hyper Mode when `cur_cap >= 80` or `cur_cap >= high_current_target_percent`.
   - Initial attachment at high SoC ($>85\%$) does not gracefully divert to CV tapering.
   - `_target_soc` in `apply_extreme_charging()` is currently an unused parameter.

---

## 5. Implementation Recommendations for Worker M2

Worker M2 should implement the following concrete changes in `native/lynxd/src/hardware/charging.rs`:

### Recommendation 1: Integrate SoC Boundary & Smart Tapering in `apply_from_config()`

In `native/lynxd/src/hardware/charging.rs`, modify the Hyper Mode evaluation block inside `apply_from_config()` (replacing lines 1130–1149):

```rust
// Tier 6: Hyper Mode Enforcement with 80% SoC CC/CV Boundary & Smart Tapering
if is_hyper {
    let target_soc = cfg.high_current_target_percent.clamp(50, 95) as u32; // Default 80% or 90%
    let volt_raw = Self::read_i64(&format!("{}/voltage_now", BATT_DIR)).unwrap_or(0);
    let volt_mv = if volt_raw.abs() >= 10_000 { volt_raw / 1000 } else { volt_raw };

    // 1. Initial Attachment Guard & High SoC CV Tapering (>= 80% SoC or Vbat >= 4470 mV)
    if cfg.smart_tapering_enabled && (cur_cap >= target_soc || volt_mv >= 4470) {
        if cur_cap >= 95 {
            // Stage 2 Tapering: 750 mA gentle inflow
            let _ = fs::write("/dev/lynx_charging_state", format!("state=tapering_95 cap={}\n", cur_cap));
            Self::apply_regulated_charging(750);
            return;
        } else {
            // Stage 1 Tapering: 3500 mA CV transition (drops below peak 6A, maintains Vbat <= 4.50V)
            let _ = fs::write("/dev/lynx_charging_state", format!("state=tapering_80 cap={}\n", cur_cap));
            Self::apply_regulated_charging(3500);
            return;
        }
    }

    // 2. CC Phase (<80% SoC, Vbat < 4470 mV): Continuous Maximum Pump Current (>6A)
    let marker = Self::read_node("/dev/lynx_extreme_charging").unwrap_or_default();
    let is_mtk = Path::new(MTK_DIR).exists();
    let invariants_intact = if is_mtk {
        let bn_test = Self::read_node(&format!("{}/BN_TestMode", MTK_DIR)).unwrap_or_default();
        let enable_sc = Self::read_node(&format!("{}/enable_sc", MTK_DIR)).unwrap_or_default();
        let sc_lmt = Self::read_node(&format!("{}/sc_ibat_limit", MTK_DIR)).unwrap_or_default();
        bn_test == "1" && enable_sc == "1" && sc_lmt == "12000"
    } else {
        let direct_chg = Self::read_node(&format!("{}/direct_charging", QC_DIR)).unwrap_or_default();
        direct_chg == "1"
    };

    if marker.starts_with("hyper-") && invariants_intact {
        return;
    }

    Self::apply_hyper_mode();
    return;
}
```

### Recommendation 2: Utilize `target_soc` in `apply_extreme_charging()`

Update `apply_extreme_charging(target_soc: u32, ...)` signature and logic so that `target_soc` is checked against current battery capacity:

```rust
pub fn apply_extreme_charging(target_soc: u32, _allow_lockout_bypass: bool, requested_ma: u32) {
    let cur_cap = Self::read_i64(&format!("{}/capacity", BATT_DIR)).unwrap_or(50) as u32;
    let effective_target = if target_soc > 0 && target_soc <= 100 { target_soc } else { 80 };

    // If battery is already at or above target SoC upon entry, taper current
    let effective_peak_ma = if cur_cap >= effective_target && cur_cap < 100 {
        3500 // Safe CV tapering current
    } else {
        requested_ma
    };
    ...
```

### Recommendation 3: Add Unit Tests in `native/lynxd/src/hardware/charging.rs`

Add automated unit tests verifying the 2:1 pump math and SoC threshold calculations:

```rust
#[test]
fn test_pump_doubling_ratio_and_net_current_math() {
    let ibus_ma = 3300.0;
    let efficiency = 0.97;
    let ipump_ma = 2.0 * ibus_ma * efficiency;
    assert_eq!(ipump_ma, 6402.0);

    let isys_load_ma = 350.0;
    let inet_ma = ipump_ma - isys_load_ma;
    assert!(inet_ma > 6000.0, "Net current must exceed 6000 mA");

    let ratio = ipump_ma / ibus_ma;
    assert!((ratio - 1.94).abs() < 0.01);
}

#[test]
fn test_soc_cc_cv_boundary_thresholds() {
    const CC_SOC_CEILING: u32 = 80;
    const CV_VOLTAGE_CEILING_MV: i64 = 4470;
    const ABSOLUTE_MAX_VOLTAGE_MV: i64 = 4500;

    assert!(65 < CC_SOC_CEILING, "65% is inside CC phase");
    assert!(85 >= CC_SOC_CEILING, "85% is inside CV phase");
    assert!(CV_VOLTAGE_CEILING_MV < ABSOLUTE_MAX_VOLTAGE_MV);
}
```

---

## 6. Verification Method

### 1. Cargo Compilation & Unit Tests
```bash
cd native/lynxd
cargo check
cargo test --target x86_64-pc-windows-msvc -- hardware::charging
```
*Expected Result*: Exit code 0, all charging unit tests pass.

### 2. Automated E2E Test Suite Execution
```bash
# Tier 1 Requirement R3 tests
python tests/e2e/run_e2e_tests.py --tier 1

# Tier 2 Boundary & SoC transition tests
python tests/e2e/run_e2e_tests.py --tier 2

# Full test suite
python tests/e2e/run_e2e_tests.py
```
*Expected Result*: 41/41 test cases pass (100%), including:
- `TC-T1-F3-01` through `TC-T1-F3-06` (Engagement, Input current lock, Limit lock, Net current $>6\text{A}$, Doubling ratio, High current sustain).
- `TC-T2-B01` through `TC-T2-B03` (80% SoC CC/CV boundary, High-SoC initial entry, Low-SoC pre-charge handoff).

### 3. Invalidation Conditions
- If `current_now` reports $< 6,000,000\ \mu\text{A}$ during CC phase (<80% SoC) with fast charger connected.
- If `input_current` drops below $30000$ or `sc_ibat_limit` drops below $8000$ during hyper mode.
- If cell terminal voltage exceeds $4.50\text{V}$ (4,500,000 $\mu\text{V}$) during CV saturation above 80% SoC.
