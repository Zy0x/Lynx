# Technical Strategy & Implementation Blueprint: R2 Charging Thermal Throttling & Trip-Point Neutralization

> **Author**: Explorer M1-2  
> **Milestone**: Milestone 1 (Screen-On & Thermal Level Neutralization: R1 & R2)  
> **Target Subsystem**: `native/lynxd/src/hardware/charging.rs` & Hardware Abstraction Layer  
> **Target Hardware**: MediaTek Helio G96 (`mt6781`, Infinix X698) / Dimensity & Qualcomm Snapdragon  

---

## 1. Executive Summary & Problem Formulation

### 1.1 The R2 Problem Statement
During high-wattage direct charge pump fast charging (>30W input, >6000 mA cell current), intense heat is generated at both the motherboard PMIC/SoC junction and the RT9759 2:1 switched capacitor die. Under OEM kernel policy, four independent thermal gates intervene to derate or terminate high-current charging:

1. **Motherboard PCB Thermal Throttling (`tpcb`)**:
   - Monitored by Transsion kernel thread `dv2_algo_task` via `mtktsbtsmdpa` thermistor.
   - At **45°C**, `__dv2_check_tpcb_level` assigns **Level 7**, enforcing a **1500 mA derating deduction** (`tpcb = 1500`) directly from the negotiated input current $I_{\text{ta\_org}} = 3000\text{ mA}$, halving charging current to 1500 mA.
2. **Direct Charge Pump DV2 In-Memory Thermal Tables (`dv2_debug`)**:
   - `pca_dv2_algo` maintains in-memory thresholds for battery temperature (`tbat`) and charge pump die temperature (`tdvchg`).
   - At 43°C/45°C/50°C for battery and 70°C/75°C/80°C for the RT9759 die, progressive derating reductions (300mA, 600mA, 900mA, 1500mA) are subtracted from $I_{\text{ta}}$.
3. **Battery Cell Temperature Gate (`tbat`)**:
   - Monitored via `/sys/devices/platform/battery/Battery_Temperature` (ADC thermistor).
   - At **42°C**, `__dv2_check_tbat_level` assigns **Level 6** (`tbat: 750 mA derating`). At **45°C**, it hits **Level 8**, triggering complete shutdown of the direct charge pump (`DV2_TBAT = -1`) and dropping to the 5V RT9471 buck charger.
4. **MediaTek ABCCT PID Governor & Cooling Devices**:
   - Adaptive Battery Charging Current Throttling closed-loop PID controller (`/proc/driver/thermal/clabcct` & `clabcct_lcmoff`) actively clamps charging current to target setpoint (42°C screen-on, 43°C screen-off).
   - Cooling devices 53–57 (`mtk-cl-bcct00..02`, `abcct`, `abcct_lcmoff`) raise `cur_state = 1`, commanding the PMIC to throttle.
   - Userspace daemon `thermalloadalgod` sends netlink throttle messages to kernel `chg_nl_data_handler`.
   - Android Framework `thermalservice` broadcasts thermal throttle status to system apps.

### 1.2 Objective
Formulate the exact, code-level implementation blueprint in `native/lynxd/src/hardware/charging.rs` to neutralize all four thermal gates during Hyper Mode, while maintaining fail-safe restoration on disconnect (<2 seconds) and zero impact on physical hardware protections (OVP/OCP).

---

## 2. In-Depth Hardware & Driver Interface Specification

### 2.1 Transsion Motherboard PCB Thermal Debug (`pcb_thermal_debug`)
- **Sysfs Paths**:
  - Primary: `/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug`
  - Fallback: `/sys/devices/platform/tran_battery/pcb_thermal_debug`
- **Driver Driver Registration**: `drivers/power/supply/tran_battery/`
- **Syntax / Protocol**:
  Reading the node yields:
  ```
  Please: echo [num1,num2,num3,num4,num5] > pcb_thermal_debug Tpcb_step_one=0, Tpcb_deal_curr_one=0, Tpcb_step_two=0, Tpcb_deal_curr_two=0, Tmin_curr=0, Tpcb_store_ok=0
  ```
- **Parameter Mapping**:
  - `num1 = Tpcb_step_one`: Temperature threshold for Step 1 derating (OEM default: 43°C/45°C).
  - `num2 = Tpcb_deal_curr_one`: Target current for Step 1 in mA (OEM default: 3000 mA).
  - `num3 = Tpcb_step_two`: Temperature threshold for Step 2 derating (OEM default: 50°C).
  - `num4 = Tpcb_deal_curr_two`: Target current for Step 2 in mA (OEM default: 2000 mA).
  - `num5 = Tmin_curr`: Minimum allowable charging current floor in mA (OEM default: 1000 mA).
- **Hyper Mode Injection Payload**:
  `"[95,6000,100,6000,6000]"`
  - Delays Step 1 derating until motherboard PCB reaches 95°C.
  - Fixes all intermediate and floor current targets to 6000 mA.
  - Neutralizes `__dv2_check_tpcb_level` Level 7 (1500 mA penalty).
- **Permission Interlock**:
  Kernel and ODM daemons attempt to overwrite this node. Must write with `0o666` and lock with `0o444`.
- **Disconnect Restoration Payload**:
  `"[45,3000,50,2000,1000]"` unlocked to `0o644`.

---

### 2.2 Transsion DV2 In-Memory Thermal Tables (`dv2_debug`)
- **Sysfs Path**: `/sys/devices/platform/pca_dv2_algo/dv2_debug`
- **Driver**: `drivers/power/supply/pca_dv2_algo.c`
- **Syntax / Protocol**:
  Reading the node yields:
  ```
  Please: echo [num0,num1,num2,num3,num4,num5,num6] > dv2_debug tbat_warm=43, tbat_very_warm=45, tbat_hot=50, tbat_warm_cur=500, tbat_very_warm_cur=1000, tbat_hot_cur=1500, tdvchg_warm=70, tdvchg_very_warm=75, tdvchg_hot=80, tdvchg_warm_cur=300, tdvchg_very_warm_cur=600, tdvchg_hot_cur=900
  ```
- **Command Dispatcher (`num0`)**:
  - `num0 = 101`: Battery Temperature (`tbat`) table override.
    Parameters: `[101, tbat_warm, tbat_very_warm, tbat_hot, tbat_warm_cur, tbat_very_warm_cur, tbat_hot_cur]`
  - `num0 = 102`: Charge Pump Die Temperature (`tdvchg`) table override.
    Parameters: `[102, tdvchg_warm, tdvchg_very_warm, tdvchg_hot, tdvchg_warm_cur, tdvchg_very_warm_cur, tdvchg_hot_cur]`
- **Hyper Mode Injection Payloads**:
  - `"[101,85,88,90,6000,6000,6000]"` (elevates warm threshold from 43°C to 85°C, sets current to 6000 mA).
  - `"[102,85,88,90,6000,6000,6000]"` (elevates pump die warm threshold from 70°C to 85°C, sets current to 6000 mA).
- **Permission Interlock**:
  Written with `0o666`, locked with `0o444`.
- **Disconnect Restoration Payloads**:
  - `"[101,43,45,50,500,1000,1500]"`
  - `"[102,70,75,80,300,600,900]"`
  Unlocked to `0o644`.

---

### 2.3 Battery Temperature Spoofing (`Battery_Temperature`)
- **Sysfs Path**: `/sys/devices/platform/battery/Battery_Temperature`
- **Driver**: MediaTek Fuel Gauge & Battery Platform Driver
- **Behavior & Kernel Mechanism**:
  - Exposes integer temperature in °C (e.g. `42` = 42°C).
  - Writing `"28"` forces the kernel to report 28.0°C.
  - In `__dv2_check_tbat_level`, 28°C falls squarely into **Level 4 (25°C–41°C: 0 mA derating)**.
  - Level 5 (41°C: -250mA), Level 6 (42°C: -750mA), Level 7 (43°C: -1250mA), and Level 8 (45°C: shutdown) are completely prevented from triggering.
- **The Permission Overwrite Race Condition**:
  - The MediaTek kernel battery driver thread samples ADC thermistors every 10–30s and attempts to write the real temperature back to `Battery_Temperature`.
  - **Critical Requirement**: Must lock permissions to `chmod 0444` (`0o444`) immediately after writing `"28"`.
- **Sentinel Reversion on Disconnect**:
  - Writing `"65535"` into `Battery_Temperature` tells the MediaTek kernel driver to cancel the debug spoof and revert to genuine ADC thermistor reads.
  - Must unlock permissions with `chmod 0644` (`0o644`) before and after writing.
- **Hardware Watchdog Integrity**:
  - Spoofing `Battery_Temperature` to 28 does NOT blind Lynx!
  - `ChargingRegulator::read_real_battery_temp_dc()` in `charging.rs` lines 751–798 explicitly checks `if bdc != 280 && bdc > 150` and reads un-spoofed raw thermal zones (`mtktsbtsmdpa`, `mtktswmt`, `mtktsap`).
  - If real physical cell temperature reaches 50.0°C (`real_dc >= 500`), Lynx's autonomous emergency guard engages and clamps charging.

---

### 2.4 MediaTek ABCCT PID Throttling & Cooling Devices 53–57
- **ABCCT Driver Nodes**:
  - Screen-On PID: `/proc/driver/thermal/clabcct`
  - Screen-Off PID: `/proc/driver/thermal/clabcct_lcmoff`
  - Format: 7 integers: `Policy Temp Kp Ki Kd Max Min`
- **Hyper Mode Injection Payload**:
  `"0 70000 1000 200000 5 6000 0"`
  - `Policy = 0`: Disables closed-loop PID throttling.
  - `Temp = 70000`: Sets setpoint to 70°C.
  - `Max = 6000`: Sets maximum current limit to 6000 mA.
  - Locked with `0o444`.
- **Disconnect Restoration Payload**:
  - `clabcct`: `"1 42000 1000 200000 5 2000 0"`
  - `clabcct_lcmoff`: `"1 43000 1000 200000 5 2000 0"`
  - Unlocked to `0o644`.
- **Cooling Devices 53–57 (`mtk-cl-bcct00..02`, `abcct`, `abcct_lcmoff`)**:
  - In Hyper Mode: Write `"0"` to `cur_state`, lock with `0o444`.
  - On Disconnect: Unlock with `0o666`, write `"0"` (`unlock_bcct()`).

---

### 2.5 Userspace Thermal Daemons & Framework Thermal Service
1. **Userspace Thermal Daemon (`thermalloadalgod`)**:
   - `thermalloadalgod` communicates over netlink to kernel `chg_nl_data_handler`.
   - Freezing: `signal_processes(&["thermalloadalgod", "com.xiaomi.joyose", "mi_thermald", "thermal-engine", "thermal-engine-v2"], true)` (SIGSTOP 19).
   - Zero-fork pure Rust implementation in `ChargingRegulator::signal_processes`.
   - Resuming on Disconnect: `signal_processes(..., false)` (SIGCONT 18).
2. **Android Framework Thermal Service**:
   - Freezing: `cmd thermalservice override-status 0` (forces `THERMAL_STATUS_NONE`).
   - Resuming on Disconnect: `cmd thermalservice reset`.

---

## 3. Code-Level Implementation Blueprint

### 3.1 Constants to Add to `native/lynxd/src/hardware/charging.rs`
Insert near line 22:

```rust
const TRAN_BATTERY_ODM_DIR: &str = "/sys/devices/platform/odm/odm:tran_battery";
const TRAN_BATTERY_DIR: &str = "/sys/devices/platform/tran_battery";
const PCA_DV2_DIR: &str = "/sys/devices/platform/pca_dv2_algo";
const BATT_TEMP_NODE: &str = "/sys/devices/platform/battery/Battery_Temperature";
const CLABCCT_NODE: &str = "/proc/driver/thermal/clabcct";
const CLABCCT_LCMOFF_NODE: &str = "/proc/driver/thermal/clabcct_lcmoff";
```

### 3.2 Update `unlock_extreme_nodes()` in `charging.rs`
Expand `NODES` array (lines 353–415) to include R2 nodes so their permissions are unlocked before new values are written:

```rust
            "/sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug",
            "/sys/devices/platform/tran_battery/pcb_thermal_debug",
            "/sys/devices/platform/pca_dv2_algo/dv2_debug",
            "/sys/devices/platform/battery/Battery_Temperature",
            "/proc/driver/thermal/clabcct",
            "/proc/driver/thermal/clabcct_lcmoff",
```

*Note on R1 interaction*: Remove the lines `Self::write_node("/sys/devices/platform/charger/BN_TestMode", "0");` from `unlock_extreme_nodes()`, leaving `BN_TestMode` control to `apply_extreme_charging()`.

---

### 3.3 New Method: `apply_charger_thermal_neutralization()`
Add this method to `ChargingRegulator` in `charging.rs`:

```rust
    /// Neutralizes all charging thermal throttling stages and trip points (R2):
    /// 1. Overrides Transsion PCB thermal derating (pcb_thermal_debug -> [95,6000,100,6000,6000], locked 0444).
    /// 2. Overrides DV2 direct pump in-memory thermal tables (dv2_debug -> commands 101 & 102, locked 0444).
    /// 3. Spoofs Battery_Temperature to 28°C (locked 0444) to neutralize Level 6 tbat throttling.
    /// 4. Neutralizes MediaTek ABCCT PID controllers (clabcct & clabcct_lcmoff -> policy 0, locked 0444).
    /// 5. Locks cooling devices 53-57 (abcct, mtk-cl-bcct00..02) cur_state to 0 (locked 0444).
    /// 6. Overrides Qualcomm system_temp_level and thermal_input_current_limit.
    /// 7. Suspends aggressive OEM thermal daemons (thermalloadalgod, joyose, mi_thermald, thermal-engine).
    /// 8. Overrides Android framework thermal status to 0 (THERMAL_STATUS_NONE).
    pub fn apply_charger_thermal_neutralization() {
        // 1. Transsion Motherboard PCB Thermal Throttling Neutralization
        for node in &[
            format!("{}/pcb_thermal_debug", TRAN_BATTERY_ODM_DIR),
            format!("{}/pcb_thermal_debug", TRAN_BATTERY_DIR),
        ] {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
                let _ = fs::write(node, "[95,6000,100,6000,6000]");
                Self::set_perm(node, 0o444);
            }
        }

        // 2. Transsion Direct Charge Pump DV2 In-Memory Thermal Table Neutralization
        let dv2_debug = format!("{}/dv2_debug", PCA_DV2_DIR);
        if Path::new(&dv2_debug).exists() {
            Self::set_perm(&dv2_debug, 0o666);
            let _ = fs::write(&dv2_debug, "[101,85,88,90,6000,6000,6000]");
            let _ = fs::write(&dv2_debug, "[102,85,88,90,6000,6000,6000]");
            Self::set_perm(&dv2_debug, 0o444);
        }

        // 3. Battery Temperature Spoofing to 28°C
        if Path::new(BATT_TEMP_NODE).exists() {
            Self::set_perm(BATT_TEMP_NODE, 0o666);
            let _ = fs::write(BATT_TEMP_NODE, "28");
            Self::set_perm(BATT_TEMP_NODE, 0o444);
        }

        // 4. MediaTek ABCCT PID Thermal Derater Neutralization
        if Path::new(CLABCCT_LCMOFF_NODE).exists() {
            Self::set_perm(CLABCCT_LCMOFF_NODE, 0o666);
            let _ = fs::write(CLABCCT_LCMOFF_NODE, "0 70000 1000 200000 5 6000 0");
            Self::set_perm(CLABCCT_LCMOFF_NODE, 0o444);
        }
        if Path::new(CLABCCT_NODE).exists() {
            Self::set_perm(CLABCCT_NODE, 0o666);
            let _ = fs::write(CLABCCT_NODE, "0 70000 1000 200000 5 6000 0");
            Self::set_perm(CLABCCT_NODE, 0o444);
        }

        // 5. Freeze BCCT & ABCCT Cooling Devices (53-57) to state 0 locked
        Self::set_bcct_throttle(false);

        // 6. Qualcomm Snapdragon Platform Thermal Uncaps
        Self::write_node_lock(&format!("{}/system_temp_level", QC_DIR), "0");
        Self::write_node_lock(&format!("{}/system_temp_level", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/temp_state", BATT_DIR), "0");
        Self::write_node_lock(&format!("{}/thermal_input_current_limit", BATT_DIR), "-1");

        // 7. Suspend aggressive userspace thermal throttlers via SIGSTOP
        Self::signal_processes(
            &["thermalloadalgod", "com.xiaomi.joyose", "mi_thermald", "thermal-engine", "thermal-engine-v2"],
            true,
        );

        // 8. Override Android framework thermal status to 0 (THERMAL_STATUS_NONE)
        let _ = std::process::Command::new("cmd")
            .args(["thermalservice", "override-status", "0"])
            .output();
    }
```

---

### 3.4 Integration into `apply_extreme_charging()`
In `native/lynxd/src/hardware/charging.rs` lines 535–544:

```rust
        // 2. MediaTek Platform Enforcements & Dual Pump (Thermal & JEITA completely bypassed!)
        Self::write_node(&format!("{}/pe20", MTK_DIR), "1");
        Self::write_node(&format!("{}/pe40", MTK_DIR), "1");
        Self::write_node(&format!("{}/enable_sc", MTK_DIR), "1");
        Self::write_node(&format!("{}/BatteryNotify", MTK_DIR), "0");
        Self::write_node_lock(&format!("{}/BN_TestMode", MTK_DIR), "1");
        Self::write_node_lock(&format!("{}/sw_jeita", MTK_DIR), "0");

        // Execute Comprehensive R2 Thermal Throttling & Trip-Point Neutralization
        Self::apply_charger_thermal_neutralization();
```

---

### 3.5 Implementation of `restore_charger_thermal_safety()`
Replace empty method `restore_charger_thermal_safety()` (line 746) with:

```rust
    /// Restores all charging thermal controls, trip points, daemons, and framework status (R4 / R2 reversion):
    pub fn restore_charger_thermal_safety() {
        // 1. Restore Transsion PCB thermal thresholds to OEM defaults
        for node in &[
            format!("{}/pcb_thermal_debug", TRAN_BATTERY_ODM_DIR),
            format!("{}/pcb_thermal_debug", TRAN_BATTERY_DIR),
        ] {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
                let _ = fs::write(node, "[45,3000,50,2000,1000]");
                Self::set_perm(node, 0o644);
            }
        }

        // 2. Restore Transsion DV2 thermal tables to OEM defaults
        let dv2_debug = format!("{}/dv2_debug", PCA_DV2_DIR);
        if Path::new(&dv2_debug).exists() {
            Self::set_perm(&dv2_debug, 0o666);
            let _ = fs::write(&dv2_debug, "[101,43,45,50,500,1000,1500]");
            let _ = fs::write(&dv2_debug, "[102,70,75,80,300,600,900]");
            Self::set_perm(&dv2_debug, 0o644);
        }

        // 3. Restore Battery_Temperature to 65535 (releases debug override to genuine ADC)
        if Path::new(BATT_TEMP_NODE).exists() {
            Self::set_perm(BATT_TEMP_NODE, 0o666);
            let _ = fs::write(BATT_TEMP_NODE, "65535");
            Self::set_perm(BATT_TEMP_NODE, 0o644);
        }

        // 4. Restore MediaTek ABCCT PID controllers to OEM setpoints
        if Path::new(CLABCCT_LCMOFF_NODE).exists() {
            Self::set_perm(CLABCCT_LCMOFF_NODE, 0o666);
            let _ = fs::write(CLABCCT_LCMOFF_NODE, "1 43000 1000 200000 5 2000 0");
            Self::set_perm(CLABCCT_LCMOFF_NODE, 0o644);
        }
        if Path::new(CLABCCT_NODE).exists() {
            Self::set_perm(CLABCCT_NODE, 0o666);
            let _ = fs::write(CLABCCT_NODE, "1 42000 1000 200000 5 2000 0");
            Self::set_perm(CLABCCT_NODE, 0o644);
        }

        // 5. Unlock cooling devices permissions so kernel can modulate them
        Self::unlock_bcct();

        // 6. Resume suspended userspace thermal daemons via SIGCONT
        Self::signal_processes(
            &["thermalloadalgod", "com.xiaomi.joyose", "mi_thermald", "thermal-engine", "thermal-engine-v2"],
            false,
        );

        // 7. Reset Android framework thermal status
        let _ = std::process::Command::new("cmd")
            .args(["thermalservice", "reset"])
            .output();
    }
```

---

## 4. Preservation of Architectural Constraints & Unit Test Integrity

### 4.1 Unit Test Compliance: `disconnect_resets_only_charging_protocol_nodes`
In `native/lynxd/src/hardware/charging.rs` lines 1245–1255:
```rust
#[cfg(test)]
mod tests {
    use super::DISCONNECT_CHARGING_RESETS;

    #[test]
    fn disconnect_resets_only_charging_protocol_nodes() {
        assert!(DISCONNECT_CHARGING_RESETS.iter().all(|(path, _)| {
            let path = path.to_lowercase();
            !path.contains("thermal") && !path.contains("trip_point") && !path.contains("cooling_device")
        }));
    }
}
```
- **Architectural Safeguard**: `DISCONNECT_CHARGING_RESETS` is strictly tested to NOT contain strings with `"thermal"`, `"trip_point"`, or `"cooling_device"`.
- By implementing the thermal restoration inside `restore_charger_thermal_safety()` (which is called by `handle_disconnect()` and mode switches), `DISCONNECT_CHARGING_RESETS` remains pure and the unit test passes without modification.

### 4.2 Mode-Switching Invariant
Notice that `apply_regulated_charging()` (line 661) and `apply_standard_mode()` (line 679) both call `Self::restore_charger_thermal_safety()`.
Thus, when a user switches from Hyper Mode to Standard Mode or Cool Mode via WebUI, CLI, or companion APK, all thermal overrides are instantly restored to OEM behavior without requiring a cable unplug cycle.

---

## 5. Verification Matrix & Concrete Test Steps

### 5.1 Verification Commands on Target Device (Infinix-X698)
```bash
# 1. Connect target device
.\tools\connect_device.cmd

# 2. Engage Hyper Charging Mode
adb shell su -c "/data/adb/modules/Lynx/system/bin/lynxd charging mode hyper"

# 3. Verify PCB Thermal Debug override
adb shell su -c "cat /sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug"
# Expected output contains: Tpcb_step_one=95, Tpcb_deal_curr_one=6000, Tpcb_store_ok=1

# 4. Verify Battery_Temperature spoofing
adb shell su -c "cat /sys/devices/platform/battery/Battery_Temperature"
# Expected output: 28
# Verify node is locked read-only:
adb shell su -c "ls -l /sys/devices/platform/battery/Battery_Temperature"
# Expected permissions: -r--r--r-- (0444)

# 5. Verify Cooling Devices 53-57 locked to state 0
adb shell su -c "for c in /sys/class/thermal/cooling_device*; do type=\$(cat \$c/type 2>/dev/null); case \$type in *bcct*|*abcct*) echo \"\$c \$type: cur=\$(cat \$c/cur_state)\";; esac; done"
# Expected output: all bcct / abcct cooling devices report cur=0

# 6. Verify Userspace Daemon thermalloadalgod is paused (T state)
adb shell su -c "ps -ef | grep -E 'thermalloadalgod|thermal-engine'"
# Expected state: T (stopped) in ps or confirmed via /proc/<pid>/status 'State: T (stopped)'

# 7. Verify Framework Thermal Status
adb shell su -c "dumpsys thermalservice | grep -i 'Thermal Status:'"
# Expected output: Thermal Status: 0

# 8. Unplug cable and verify fail-safe restoration within 2 seconds
# Unplug cable physically or simulate via test mock:
adb shell su -c "/data/adb/modules/Lynx/system/bin/lynxd charging disconnect"
adb shell su -c "cat /sys/devices/platform/battery/Battery_Temperature"
# Expected output: 65535 or real temperature, permissions -rw-r--r-- (0644)
adb shell su -c "dumpsys thermalservice | grep -i 'Thermal Status:'"
# Expected output: reset to genuine framework status
```

---

## 6. Recommendations for the Worker

1. **Atomic Application**: Implement `apply_charger_thermal_neutralization()` and `restore_charger_thermal_safety()` together to maintain full symmetry.
2. **Permission Guarding**: Always check `Path::new(node).exists()` before attempting `chmod` or `write` to prevent logging noise on non-Transsion/Qualcomm hardware.
3. **No Unsolicited Commits**: Keep changes on local working tree; do not perform git commit/push until orchestrator verification passes.
4. **Build Verification**: Run `cargo check` and `cargo test --no-run` inside `native/lynxd` to confirm zero compilation errors and warnings.
