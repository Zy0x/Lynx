# Handoff Report: Requirement R4 Disconnect Detection & Float Voltage Mitigation

**Explorer**: `explorer_m3_1` (Disconnect Detection Explorer)  
**Date**: 2026-10-14  
**Target Milestone**: Milestone 3 (Fail-Safe Disconnect Restoration & Over-Voltage Protection)  
**Scope**: Disconnect detection mechanism, triple-vector detection logic, decoupling capacitor float voltage mitigation (`TC-T2-B04`), and daemon runner chunk sleep breakout latency.

---

## 1. Observation

### 1.1 Direct Observation of `is_hardware_plugged()` in `native/lynxd/src/hardware/charging.rs`
Observed verbatim at `native/lynxd/src/hardware/charging.rs` lines 1262–1293:
```rust
    pub fn is_hardware_plugged() -> bool {
        // 1. Primary check: USB, AC, or Charger subsystem reporting online
        let usb_online = Self::read_node(&format!("{}/online", USB_DIR)).unwrap_or_default();
        let ac_online = Self::read_node(&format!("{}/ac/online", PS_DIR)).unwrap_or_default();
        let chg_online = Self::read_node(&format!("{}/charger/online", PS_DIR)).unwrap_or_default();

        if usb_online == "1" || ac_online == "1" || chg_online == "1" {
            return true;
        }

        // 2. Hardware ADC / PMIC adapter voltage (> 3500 mV indicates physical adapter is connected)
        let adpv = Self::read_i64(&format!("{}/ADC_Charger_Voltage", MTK_DIR))
            .or_else(|| Self::read_i64("/sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger"))
            .or_else(|| Self::read_i64(&format!("{}/voltage_now", USB_DIR)))
            .or_else(|| Self::read_i64(&format!("{}/charger_voltage", BATT_DIR)))
            .or_else(|| Self::read_i64(&format!("{}/voltage_now", MAIN_DIR)))
            .unwrap_or(0);
        let adpv_clean = if adpv > 100_000 { adpv / 1000 } else { adpv };

        if adpv_clean >= 3500 {
            return true;
        }

        // 3. Battery status verification
        let batt_status = Self::read_node(&format!("{}/status", BATT_DIR)).unwrap_or_default();
        if batt_status == "Charging" {
            return true;
        }

        // Cable physically unplugged: No online rails, no adapter voltage, and battery discharging
        false
    }
```
**Specific Deficiencies Identified**:
1. **VBUS Decoupling Capacitor Blind Spot**: Step 2 checks `adpv_clean >= 3500` and immediately returns `true` *before* checking battery status (`batt_status`) or charger type (`Charger_Type`). When a physical cable is unplugged, decoupling capacitors holding residual charge ($V_{BUS} > 3.5\text{V}$, e.g. $3.8\text{V} - 4.5\text{V}$) cause `is_hardware_plugged()` to falsely report `true` for 100–1500 ms until the capacitors discharge.
2. **Missing `MAIN_DIR/online` Rail**: Lines 1264–1266 check `usb/online`, `ac/online`, and `charger/online`, but omit `/sys/class/power_supply/main/online` (`MAIN_DIR`), which is the primary charging rail on Qualcomm PMIC platforms (e.g., SMB1351/SMB1390 / PM8150).
3. **Missing Charger Type Vector**: Neither MTK `/sys/devices/platform/charger/Charger_Type` nor Qualcomm `/sys/class/power_supply/usb/type` is inspected. Upon physical disconnect, hardware transceivers drop `Charger_Type` to `"0"` (`CHARGER_UNKNOWN`) and `usb/type` to `"Unknown"` within $<10\text{ ms}$, but this signal is completely ignored by `is_hardware_plugged()`.

---

### 1.2 Direct Observation of Decoupling Capacitor Float Test `TC-T2-B04`
Observed at `tests/e2e/tier2_boundaries/test_boundary_corner_cases.py` lines 122–155:
```python
class TestT2CapacitorFloatUnplug(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T2-B04",
            tier=2,
            feature="Boundary_Unplug_Timing",
            title="Decoupling Capacitor Voltage Float Disconnect Boundary",
            authoritative_source="explorer_survey_3/handoff.md § 1.4 lines 84-99 & § 2 Step 3",
            description="Simulates VBUS decoupling capacitor holding voltage >3.5V after physical unplug while Charger_Type drops to 0."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")

        # Simulate floating voltage (VBUS = 3.8V) but physical unplug occurred (status=Discharging, Charger_Type=0)
        target.write_node("sys/devices/platform/charger/ADC_Charger_Voltage", "3800")
        target.write_node("sys/devices/platform/charger/Charger_Type", "0")
        target.write_node("sys/class/power_supply/usb/online", "0")
        target.write_node("sys/class/power_supply/battery/status", "Discharging")

        t0 = time.perf_counter()
        target.execute_command("lynxd charging disconnect")
        duration = time.perf_counter() - t0
        ...
```
`TC-T2-B04` explicitly proves that when `ADC_Charger_Voltage` is floating at $3800\text{ mV}$, `online` is `0`, `Charger_Type` is `0`, and battery `status` is `Discharging`.

---

### 1.3 Direct Observation of `native/lynxd/src/daemon/runner.rs` Polling Loop & Breakout
Observed at `native/lynxd/src/daemon/runner.rs` lines 245–295:
```rust
            let is_plugged = ChargingRegulator::is_hardware_plugged();
            let sleep_duration = if is_plugged {
                Duration::from_secs(1)
            } else {
                match current_screen {
                    ScreenState::Off => Duration::from_secs(20), // Deep Sleep Shield: 20 seconds
                    _ => Duration::from_secs(5),                // Active screen: 5 seconds
                }
            };

            // Sleep in small 500ms chunks to respond immediately to SIGTERM/SIGINT and cable disconnect
            let chunks = (sleep_duration.as_millis() / 500) as usize;
            for idx in 0..chunks {
                if !DaemonLifecycle::is_running() {
                    break;
                }
                let currently_plugged = ChargingRegulator::is_hardware_plugged();
                if currently_plugged != is_plugged {
                    // Edge trigger: hardware plug state changed! Break sleep loop immediately
                    // to re-evaluate polling interval (e.g. shift from 20s sleep to 1s fast polling).
                    if !currently_plugged {
                        if std::path::Path::new("/dev/lynx_extreme_charging").exists()
                            || std::path::Path::new("/dev/lynx_bypass_active").exists()
                            || std::path::Path::new("/dev/lynx_charging_guard").exists()
                        {
                            ChargingRegulator::handle_disconnect();
                        }
                    }
                    break;
                }

                if !currently_plugged {
                    if std::path::Path::new("/dev/lynx_extreme_charging").exists()
                        || std::path::Path::new("/dev/lynx_bypass_active").exists()
                        || std::path::Path::new("/dev/lynx_charging_guard").exists()
                    {
                        ChargingRegulator::handle_disconnect();
                    }
                } else if idx > 0 && idx % 2 == 0 {
                    ChargingRegulator::tick();
                }
                if idx > 0 && idx % 2 == 0 {
                    crate::subsystems::AdbController::tick();
                }
                sleep(Duration::from_millis(500));
            }
```
**Specific Deficiencies Identified**:
1. **Sleep Chunk Latency**: The loop uses $500\text{ ms}$ chunks with an active polling frequency of $1\text{ s}$ when plugged (`chunks = 2`). The edge-trigger detection has a maximum latency of $500\text{ ms}$ from physical unplug to loop breakout, which is well within the 2.0-second limit.
2. **Restrictive File Existence Gate (Bug)**: In lines 270–275 and 281–285, `ChargingRegulator::handle_disconnect()` is gated behind:
   `Path::new("/dev/lynx_extreme_charging").exists() || Path::new("/dev/lynx_bypass_active").exists() || Path::new("/dev/lynx_charging_guard").exists()`.
   If the device was in `cool` mode or `standard` mode (which write `/dev/lynx_standard_charging` at lines 760 and 691 of `charging.rs`), or if only `/dev/lynx_charging_baseline` exists, `handle_disconnect()` is **NEVER CALLED** upon cable removal!
   Because `handle_disconnect()` already contains its own comprehensive, idempotent cleanup check (`needs_session_cleanup` at line 1311), gating it with this narrow subset in `runner.rs` creates an unsafe disconnect leak for standard and cool modes.

---

### 1.4 Direct Observation of Interface Contracts & Resets in `charging.rs`
Observed in `PROJECT.md` lines 69–71:
- `/sys/devices/platform/charger/enable_sc`: `"1"` during hyper charging, `"0"` on disconnect.
- `/sys/devices/platform/charger/sc_ibat_limit`: `"12000"` (or $\ge 8000$) during hyper charging, `"2000"` on disconnect.
- `/sys/devices/platform/charger/input_current`: `"33170"` during hyper charging, `"24576"` on disconnect.

Observed in `native/lynxd/src/hardware/charging.rs` lines 29–45 (`DISCONNECT_CHARGING_RESETS`):
```rust
const DISCONNECT_CHARGING_RESETS: [(&str, &str); 15] = [
    ("/sys/class/power_supply/battery/input_suspend", "0"),
    ("/sys/class/power_supply/battery/charging_enabled", "1"),
    ("/sys/devices/platform/charger/pe20", "0"),
    ("/sys/devices/platform/charger/pe40", "0"),
    ("/sys/devices/platform/charger/enable_sc", "0"),
    ("/sys/devices/platform/charger/bypass_charger", "0"),
    ("/sys/devices/platform/charger/tran_charger_full", "0"),
    ("/sys/devices/platform/charger/tran_game_mode", "0"),
    ("/sys/devices/platform/charger/BN_TestMode", "0"),
    ("/sys/devices/platform/charger/BatteryNotify", "1"),
    ("/sys/devices/platform/charger/sw_jeita", "1"),
    ("/sys/devices/platform/charger/pdc_max_watt", "33"),
    ("/sys/class/power_supply/battery/fastcharge_mode", "0"),
    ("/sys/class/power_supply/battery/fast_charge", "0"),
    ("/sys/class/qcom-battery/direct_charging", "0"),
];
```
Notice `DISCONNECT_CHARGING_RESETS` is missing explicit entries for:
- `/sys/devices/platform/charger/sc_ibat_limit` (`"2000"`)
- `/sys/devices/platform/charger/input_current` (`"24576"`)
While they exist in `CHARGING_SESSION_NODES` (restored via baseline), adding them directly to `DISCONNECT_CHARGING_RESETS` ensures guaranteed fallback compliance if `lynxd` is started mid-session without pre-existing baseline files.

---

## 2. Logic Chain

### Step 1: Electrical Physics of Decoupling Capacitors & Floating VBUS Voltage
1. Motherboards and USB daughterboards place multiple ceramic multi-layer capacitors (MLCCs, total $C \approx 10\text{–}100\,\mu\text{F}$) across VBUS and GND for ESD suppression and high-frequency buck filtering.
2. When the physical USB-C cable is disconnected:
   - The circuit connection to the external wall adapter is severed instantly ($t = 0\,\mu\text{s}$).
   - The RT9759 charge pump and PMIC buck converter disable switching FETs, presenting high impedance ($R_{IN} > 1\text{ M}\Omega$).
   - The charge stored in the decoupling capacitors ($Q = C \cdot V_{BUS}$) has no low-impedance discharge path. The voltage decays exponentially through parasitic PCB leakage and high-impedance ADC sensing dividers:
     $$V(t) = V_0 \cdot e^{-t / (R_{LEAK} \cdot C)}$$
   - With $R_{LEAK} \cdot C \approx 100\text{–}300\text{ ms}$ (and up to $1.2\text{–}1.5\text{ s}$ under light leakage), $V_{BUS}$ floats above $3.5\text{V}$ ($3500\text{–}4500\text{ mV}$) long after physical disconnection.
3. Therefore, **measuring VBUS voltage alone ($ADC\_Charger\_Voltage \ge 3500\text{ mV}$) CANNOT be used as proof of a physical cable connection.**

### Step 2: Instantaneous USB Handshake Detachment Signals
1. Unlike high-capacitance power rails, USB data lines (D+/D-) and USB Type-C Configuration Channel (CC1/CC2) pins have virtually zero decoupling capacitance ($<100\text{ pF}$).
2. Upon physical unplug, the CC termination resistance ($R_d / R_p$) is broken in $<1\text{ ms}$.
3. The PMIC and Type-C PHY controller hardware interrupts fire within $<10\text{ ms}$:
   - All power supply `online` rails (`usb/online`, `ac/online`, `charger/online`, `main/online`) transition to `0`.
   - The MediaTek charger driver resets `/sys/devices/platform/charger/Charger_Type` to `0` (`CHARGER_UNKNOWN`).
   - The Qualcomm USB driver resets `/sys/class/power_supply/usb/type` to `"Unknown"` or empty.
   - The fuel gauge Coulomb counter senses current flowing out of the battery cell to power system idle loads, setting `/sys/class/power_supply/battery/status` to `"Discharging"`.

### Step 3: Synthesis of the Triple-Vector Disconnect Invariant
By combining the three independent physical telemetry vectors:
- **Vector 1**: Power Supply Rails Online State (`has_online_rail = usb || ac || charger || main == 1`).
- **Vector 2**: Battery Status (`is_discharging = status == "Discharging"`).
- **Vector 3**: Charger Type (`is_unknown_charger = Charger_Type == "0" || type == "Unknown" || type.is_empty()`).

We establish the authoritative **Disconnect Invariant**:
$$\text{If } \neg\text{has\_online\_rail} \land (\text{is\_discharging} \lor \text{is\_unknown\_charger}) \implies \text{Disconnected (return false)}$$

This decision rule is completely immune to decoupling capacitor float voltage:
- When a cable is unplugged: $\text{has\_online\_rail}$ is `false`, $\text{is\_discharging}$ is `true`, and $\text{is\_unknown\_charger}$ is `true`. Even if $ADC\_Charger\_Voltage = 3800\text{ mV}$, the function immediately returns `false` with **zero latency**!
- During **Hardware Bypass Mode**: The cable is plugged into the wall, but battery current to the cell is cut off (`status == "Discharging"`). Here, $\text{has\_online\_rail}$ is `true` and $\text{is\_unknown\_charger}$ is `false`. The function continues to report `true`, **preventing false disconnects during bypass charging**.

### Step 4: Daemon Runner Chunk Sleep Breakout Latency Analysis
1. In `native/lynxd/src/daemon/runner.rs`, while charging is active, `sleep_duration` is $1\text{ second}$.
2. The loop iterates in $500\text{ ms}$ chunks (`chunks = 2`).
3. On every $500\text{ ms}$ tick:
   - `currently_plugged = ChargingRegulator::is_hardware_plugged()` is evaluated.
   - If the cable is unplugged, `currently_plugged` becomes `false`.
   - `currently_plugged != is_plugged` detects the edge transition.
   - `handle_disconnect()` is executed immediately, and the loop executes `break`.
4. The maximum delay from the moment the user pulls the cable to the completion of `handle_disconnect()` is strictly:
   $$t_{breakout} \le 500\text{ ms} \ll 2.0\text{ s}$$
   This satisfies Requirement R4 and Acceptance Criteria line 30 with a $4\times$ safety margin.
5. Removing the restrictive file-check gate in `runner.rs` ensures this $500\text{ ms}$ latency applies across **all** charging profiles (`hyper`, `cool`, `standard`, `custom`).

---

## 3. Caveats

1. **Read-Only Investigation**: In accordance with the Explorer archetype, no source files outside `.agents/teamwork/explorer_m3_1/` were modified.
2. **Platform Path Differences**: Qualcomm devices lack `/sys/devices/platform/charger/Charger_Type`. The implementation must check both `Charger_Type` and `/sys/class/power_supply/usb/type` to support Qualcomm and MediaTek platforms interchangeably.
3. **Hardware Bypass Coexistence**: In true hardware bypass charging, some vendor kernels report battery status as `"Not charging"` rather than `"Discharging"`. The rule `!has_online_rail && (is_discharging || is_unknown_charger)` safely handles both, because `has_online_rail` is `true` while the adapter is connected.

---

## 4. Conclusion & Actionable Recommendations for Worker M3

### Recommendation 1: Refactor `is_hardware_plugged()` in `native/lynxd/src/hardware/charging.rs`
Separate the pure decision logic into a testable helper function `evaluate_plugged_state(...)` and implement the triple-vector disconnect rule:

```rust
    /// Pure decision logic for plugged state. Fully unit-testable without sysfs dependencies.
    pub fn evaluate_plugged_state(
        has_online_rail: bool,
        batt_status: &str,
        charger_type: &str,
        vbus_mv: i64,
    ) -> bool {
        let is_discharging = batt_status.eq_ignore_ascii_case("discharging");
        let is_unknown_charger = charger_type == "0"
            || charger_type.eq_ignore_ascii_case("unknown")
            || charger_type.eq_ignore_ascii_case("none")
            || charger_type.is_empty();

        // 1. Fail-Safe Disconnect: If no power supply rails are online AND either the battery
        // is discharging or the charger protocol has dropped to unknown/0, the cable is
        // physically disconnected. Decoupling capacitor float voltage (>3.5V) must NOT keep
        // the driver in plugged state!
        if !has_online_rail && (is_discharging || is_unknown_charger) {
            return false;
        }

        // 2. Verified online power supply rail
        if has_online_rail {
            return true;
        }

        // 3. Verified battery charging current into cell
        if batt_status.eq_ignore_ascii_case("charging") {
            return true;
        }

        // 4. Actively driven adapter voltage fallback (>4500 mV on non-discharging battery)
        if vbus_mv >= 4500 && !is_unknown_charger && !is_discharging {
            return true;
        }

        false
    }

    pub fn is_hardware_plugged() -> bool {
        // Vector 1: Primary check - Online power supply rails across USB, AC, Charger, MAIN
        let usb_online = Self::read_node(&format!("{}/online", USB_DIR)).unwrap_or_default();
        let ac_online = Self::read_node(&format!("{}/ac/online", PS_DIR)).unwrap_or_default();
        let chg_online = Self::read_node(&format!("{}/charger/online", PS_DIR)).unwrap_or_default();
        let main_online = Self::read_node(&format!("{}/online", MAIN_DIR)).unwrap_or_default();

        let has_online_rail = usb_online == "1" || ac_online == "1" || chg_online == "1" || main_online == "1";

        // Vector 2: Battery status
        let batt_status = Self::read_node(&format!("{}/status", BATT_DIR)).unwrap_or_default();

        // Vector 3: Charger Type / Protocol ID
        let chg_type = Self::read_node(&format!("{}/Charger_Type", MTK_DIR))
            .or_else(|| Self::read_node(&format!("{}/type", USB_DIR)))
            .unwrap_or_default();

        // Hardware ADC / PMIC adapter voltage
        let adpv = Self::read_i64(&format!("{}/ADC_Charger_Voltage", MTK_DIR))
            .or_else(|| Self::read_i64("/sys/devices/platform/odm/odm:tran_battery/Pump_Express_VCharger"))
            .or_else(|| Self::read_i64(&format!("{}/voltage_now", USB_DIR)))
            .or_else(|| Self::read_i64(&format!("{}/charger_voltage", BATT_DIR)))
            .or_else(|| Self::read_i64(&format!("{}/voltage_now", MAIN_DIR)))
            .unwrap_or(0);
        let adpv_clean = if adpv > 100_000 { adpv / 1000 } else { adpv };

        Self::evaluate_plugged_state(has_online_rail, &batt_status, &chg_type, adpv_clean)
    }
```

---

### Recommendation 2: Fix `native/lynxd/src/daemon/runner.rs` Disconnect Edge Trigger
At `native/lynxd/src/daemon/runner.rs` lines 266–286, remove the restrictive file existence check so `handle_disconnect()` is always invoked when `!currently_plugged`:

```rust
                let currently_plugged = ChargingRegulator::is_hardware_plugged();
                if currently_plugged != is_plugged {
                    // Edge trigger: hardware plug state changed! Break sleep loop immediately
                    // to re-evaluate polling interval (e.g. shift from 20s sleep to 1s fast polling).
                    if !currently_plugged {
                        ChargingRegulator::handle_disconnect();
                    }
                    break;
                }

                if !currently_plugged {
                    ChargingRegulator::handle_disconnect();
                } else if idx > 0 && idx % 2 == 0 {
                    ChargingRegulator::tick();
                }
```

---

### Recommendation 3: Add Explicit Register Resets to `DISCONNECT_CHARGING_RESETS`
In `native/lynxd/src/hardware/charging.rs` lines 29–45, update `DISCONNECT_CHARGING_RESETS` to include `sc_ibat_limit = 2000` and `input_current = 24576` per Interface Contracts:
```rust
const DISCONNECT_CHARGING_RESETS: [(&str, &str); 17] = [
    ("/sys/class/power_supply/battery/input_suspend", "0"),
    ("/sys/class/power_supply/battery/charging_enabled", "1"),
    ("/sys/devices/platform/charger/pe20", "0"),
    ("/sys/devices/platform/charger/pe40", "0"),
    ("/sys/devices/platform/charger/enable_sc", "0"),
    ("/sys/devices/platform/charger/sc_ibat_limit", "2000"),
    ("/sys/devices/platform/charger/input_current", "24576"),
    ("/sys/devices/platform/charger/bypass_charger", "0"),
    ("/sys/devices/platform/charger/tran_charger_full", "0"),
    ("/sys/devices/platform/charger/tran_game_mode", "0"),
    ("/sys/devices/platform/charger/BN_TestMode", "0"),
    ("/sys/devices/platform/charger/BatteryNotify", "1"),
    ("/sys/devices/platform/charger/sw_jeita", "1"),
    ("/sys/devices/platform/charger/pdc_max_watt", "33"),
    ("/sys/class/power_supply/battery/fastcharge_mode", "0"),
    ("/sys/class/power_supply/battery/fast_charge", "0"),
    ("/sys/class/qcom-battery/direct_charging", "0"),
];
```

---

### Recommendation 4: Add Unit Tests in `charging.rs`
Add unit tests verifying:
1. `TC-T2-B04` decouple capacitor float condition (`has_online_rail: false`, `batt_status: "Discharging"`, `charger_type: "0"`, `vbus_mv: 3800`) returns `false`.
2. Hardware bypass condition (`has_online_rail: true`, `batt_status: "Discharging"`, `charger_type: "9"`, `vbus_mv: 9000`) returns `true`.
3. Qualcomm decouple capacitor float condition (`has_online_rail: false`, `batt_status: "Discharging"`, `charger_type: "Unknown"`, `vbus_mv: 4200`) returns `false`.
4. Active charging condition (`has_online_rail: true`, `batt_status: "Charging"`, `charger_type: "9"`, `vbus_mv: 9000`) returns `true`.

---

## 5. Verification Method

### 5.1 Rust Unit Test Execution
Execute the test suite in `native/lynxd`:
```powershell
cargo test --manifest-path native/lynxd/Cargo.toml
```
All existing 50 tests plus newly added unit tests for `evaluate_plugged_state` must pass with 0 failures.

### 5.2 E2E Disconnect Test Execution
Execute Tier 1 and Tier 2 E2E suites:
```powershell
python tests/e2e/run_e2e_tests.py --tier 1
python tests/e2e/run_e2e_tests.py --tier 2
```
Specifically verify:
- `TC-T1-F4-02`: Fast Triple-Vector Disconnect Detection ($<500\text{ ms}$).
- `TC-T1-F4-03`: Complete Hardware Node Reversion within 2 Seconds.
- `TC-T2-B04`: Decoupling Capacitor Voltage Float Disconnect Boundary.
- `TC-T2-B05`: Strict 2.0-Second Unplug Restoration Time Boundary.
All tests must report `[PASS]`.
