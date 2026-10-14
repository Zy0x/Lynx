# Handoff Report: R1 Screen-On Charging Throttling Bypass Blueprint

> **From**: Explorer M1-1  
> **To**: Orchestrator (`86fb0d31-9bcd-477f-bf21-1a94aecf3bd2`) / Worker M1  
> **Date**: 2026-10-14  
> **Milestone**: Milestone 1 (Screen-On & Thermal Level Neutralization: R1 & R2)  
> **Scope**: Implementation Blueprint for R1: Screen-On Charging Throttling Bypass  

---

## 1. Observation

1. **Kernel Throttling Trigger & Devicetree Clamp**:
   - `dmesg` on `Infinix-X698` (MediaTek Helio G96 `mt6781`, Android 14):
     ```
     [ 1595.788370] (2)[301:dv2_algo_task][PCA]__dv2_get_ita_lmt tv_sec(783) lcd_on(241) fb_on(1) tran_game_mode(0 1500)
     [ 1595.788376] (2)[301:dv2_algo_task][PCA]__dv2_get_ita_lmt ita(org,tta,tbat,tdvchg,prlmt,throt,tpcb)=1500(3000,0,750,0,3000,-1,1500)
     ```
   - `/sys/firmware/devicetree/base/odm/mtk_pe50/game_limit_ita` contains `1500` (mA).
   - Whenever `fb_on == 1` or `lcd_on > 0`, `__dv2_get_ita_lmt` enforces `game_limit_ita = 1500 mA` unless `BN_TestMode == 1`.

2. **Defect in `native/lynxd/src/hardware/charging.rs`**:
   - Lines 541–543:
     ```rust
     Self::write_node(&format!("{}/BatteryNotify", MTK_DIR), "1");
     Self::write_node(&format!("{}/BN_TestMode", MTK_DIR), "0");
     Self::write_node_lock(&format!("{}/sw_jeita", MTK_DIR), "0");
     ```
   - Line 542 writes `"0"` instead of `"1"` to `BN_TestMode`.
   - Lines 541 and 542 use `write_node` (no `chmod 0444` lock), leaving the nodes writable by Android thermal daemons (`thermalloadalgod`).
   - Lines 417–418 in `unlock_extreme_nodes()` prematurely reset `BN_TestMode` to `"0"` and `BatteryNotify` to `"1"`:
     ```rust
     Self::write_node("/sys/devices/platform/charger/BN_TestMode", "0");
     Self::write_node("/sys/devices/platform/charger/BatteryNotify", "1");
     ```

3. **Omitted Sibling Nodes in `apply_extreme_charging()`**:
   - `tran_charger_full` is written as `"1"` locked in `apply_bypass_charging()` (line 438), but is never cleared to `"0"` in `apply_extreme_charging()`.
   - `tran_game_mode` and `bypass_charger` are not explicitly written and locked to `"0"` in `apply_extreme_charging()`.
   - `pe40`, `pe20`, and `enable_sc` use unlocked `write_node`, allowing kernel state machines to disable direct pump mode.

4. **Disconnect Restoration & Session Nodes**:
   - `DISCONNECT_CHARGING_RESETS` (lines 23–38) contains `BN_TestMode` ("0"), `BatteryNotify` ("1"), `tran_charger_full` ("0"), but omits `tran_game_mode`.
   - `CHARGING_SESSION_NODES` (lines 42–91) omits `tran_game_mode`.
   - `capture_charging_session_baseline()` is called in `apply_from_config()`, but not guaranteed if `apply_extreme_charging()` is invoked directly via CLI (`lynxd charging extreme`).

5. **Shell Facade & CLI Architecture**:
   - `core/Charging-Controller.sh` lines 21–23 routes `hyper|extreme` via `exec "$LYNXD" charging mode hyper`.
   - `native/lynxd/src/main.rs` lines 730–736 sets `charging.extreme_charging_enabled = true` and invokes `ChargingRegulator::apply_from_config()`, which routes to `apply_hyper_mode()` -> `apply_extreme_charging(100, true, 12000)`.

---

## 2. Logic Chain

1. **Step 1 (Root Cause of R1 Throttle)**:
   - Observation 1 shows that Transsion's kernel `dv2_algo_task` clamps adapter current `ita` to `1500 mA` whenever `fb_on == 1` or `lcd_on > 0`.
   - Observation 2 reveals that `native/lynxd/src/hardware/charging.rs` line 542 writes `BN_TestMode = "0"`.
   - *Inference*: Writing `"0"` keeps the kernel driver in consumer mode, actively enforcing the 1500 mA screen-on clamp.
   - *Solution*: Writing `"1"` to `BN_TestMode` engages Factory Test Mode in the kernel driver, bypassing the `lcd_on` / `fb_on` clamp.

2. **Step 2 (Immutable Hardware Lock Necessity)**:
   - Observation 2 shows `write_node` leaves permissions as `0666` or default.
   - Userspace daemons (`thermalloadalgod` UID 1000) monitor charger events and write to sysfs nodes to enforce derating.
   - *Inference*: Using `write_node_lock` (which sets permissions to `0444`) strips write access for non-root users. Because UID 1000 lacks `CAP_FOWNER` and `CAP_DAC_OVERRIDE`, the kernel VFS rejects any daemon write attempts with `-EACCES`.

3. **Step 3 (Full Battery Simulation Deadlock Prevention)**:
   - Observation 3 shows `tran_charger_full` is set to `"1"` during hardware bypass charging, but is omitted from `apply_extreme_charging()`.
   - *Inference*: If a device enters bypass (e.g., at 80% limit), `tran_charger_full` is `"1"`. If extreme mode is later triggered without clearing `tran_charger_full`, the charger driver halts current delivery, believing the battery is full.
   - *Solution*: Explicitly enforce `tran_charger_full = 0` locked `0444` in `apply_extreme_charging()`.

4. **Step 4 (Disconnect Completeness & Non-Regressive Session Baseline)**:
   - Observation 4 shows `tran_game_mode` is missing from `DISCONNECT_CHARGING_RESETS` and `CHARGING_SESSION_NODES`.
   - Adding `tran_game_mode` guarantees that all overridden nodes are captured at session start and restored to OEM defaults within 2 seconds of cable removal.

---

## 3. Caveats

1. **Dual Requirement Interdependency (R1 & R2)**:
   While setting `BN_TestMode = 1` bypasses the screen-on clamp (`game_limit_ita = 1500 mA`), high-current charging (>6A) can still be throttled if motherboard PCB thermal trip points (Level 7 `tpcb = 1500 mA`) or battery thermal levels (Level 6/8 `tbat`) are triggered. Milestone 1 Worker must apply R1 and R2 together (`pcb_thermal_debug`, `dv2_debug`, ABCCT disarm) for full throughput.
2. **Third-Party Chargers & Cables**:
   `BN_TestMode = 1` unlocks the adapter input limit, but the physical input power is bounded by the charger's negotiated capability (Transsion RFC 33W–68W or USB-PD PPS $\ge 33\text{W}$). A standard 5V USB charger will remain on the RT9471 buck converter (~1500–2000 mA).
3. **Silicon OVP Invariance**:
   `BN_TestMode` disables software display clamps in the kernel algorithm; it does **not** modify or bypass RT9759 hardware over-voltage protection registers (`vbatovp: 4.928V`, `vbus_max_ovp: 11.0V`), preserving hardware safety.

---

## 4. Conclusion

The blueprint for R1 Screen-On Charging Throttling Bypass is fully formulated and ready for Worker M1 implementation.

The actionable changes for Worker M1 are:
1. In `native/lynxd/src/hardware/charging.rs`:
   - Line 542: Change `BN_TestMode` value from `"0"` to `"1"` and use `write_node_lock`.
   - Line 541: Change `BatteryNotify` value from `"1"` to `"0"` and use `write_node_lock`.
   - Add locked writes (`write_node_lock` `0444`) for `tran_charger_full` ("0"), `tran_game_mode` ("0"), `bypass_charger` ("0"), `enable_sc` ("1"), `pe40` ("1"), and `pe20` ("1").
   - Clean up lines 417–418 in `unlock_extreme_nodes()`.
   - Add idempotent `capture_charging_session_baseline()` call at entry of `apply_extreme_charging()`.
   - Add `tran_game_mode` to `CHARGING_SESSION_NODES` and `DISCONNECT_CHARGING_RESETS`.
2. Fully documented in `e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m1_1\analysis.md`.

---

## 5. Verification Method

1. **Compilation Check**:
   ```bash
   cd e:\Data\GitHub\Lynx\native\lynxd
   cargo check --target aarch64-linux-android
   cargo test --target x86_64-pc-windows-msvc -- hardware::charging::tests::disconnect_resets_only_charging_protocol_nodes
   ```
2. **Device Execution via ADB (`Infinix-X698`)**:
   ```bash
   adb shell su -c "/data/adb/modules/Lynx/system/bin/lynxd charging mode hyper"
   ```
3. **Verify Node Permissions and Values**:
   ```bash
   adb shell su -c "ls -l /sys/devices/platform/charger/BN_TestMode /sys/devices/platform/charger/BatteryNotify /sys/devices/platform/charger/tran_charger_full"
   ```
   - `BN_TestMode` must show mode `-r--r--r--` (`0444`) and content `1`.
   - `BatteryNotify` must show mode `-r--r--r--` (`0444`) and content `0`.
   - `tran_charger_full` must show mode `-r--r--r--` (`0444`) and content `0`.
4. **Verify Kernel `dmesg` While Display is Active**:
   With display turned on and unlocked:
   ```bash
   adb shell su -c "dmesg | grep -iE '__dv2_get_ita_lmt|game_limit_ita' | tail -n 10"
   ```
   `ita` must not be reduced to 1500 mA.
5. **Verify Disconnect Reversion**:
   Unplug cable, wait 2 seconds:
   ```bash
   adb shell su -c "cat /sys/devices/platform/charger/BN_TestMode; cat /sys/devices/platform/charger/BatteryNotify"
   ```
   `BN_TestMode` must return `0`, `BatteryNotify` must return `1`, permissions restored to `0644`.
