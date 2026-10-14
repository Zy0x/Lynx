# Technical Analysis & Implementation Blueprint: R1 Screen-On Charging Throttling Bypass

> **Author**: Explorer M1-1  
> **Milestone**: Milestone 1 (Screen-On & Thermal Level Neutralization: R1 & R2)  
> **Target Subsystem**: `native/lynxd/src/hardware/charging.rs`, `core/Charging-Controller.sh`, `native/lynxd/src/main.rs`  
> **Hardware Target**: MediaTek Helio G96 (`mt6781`) / Transsion Direct Charge Pump (RT9759 2:1) & Universal Android Platforms  

---

## 1. Executive Summary

Requirement **R1** demands the total elimination of the kernel and hardware abstraction restriction where an active display or framebuffer (`lcd_on > 0`, `fb_on = 1`) forces the adapter input current (`ita`) down to the restricted game/screen-on limit (`game_limit_ita = 1500 mA`).

Our investigation identified:
1. **The Exact Driver Mechanism**: Transsion's `pca_dv2_algo` state machine (`dv2_algo_task`) queries the display state in `__dv2_get_ita_lmt`. When `fb_on == 1` or `lcd_on > 0`, it clamps `ita_lmt` to `1500 mA` unless factory test mode (`BN_TestMode == 1`) is asserted.
2. **The Critical Code Defect in lynxd**: In `native/lynxd/src/hardware/charging.rs` line 542, `apply_extreme_charging()` explicitly writes `"0"` to `/sys/devices/platform/charger/BN_TestMode` using unlocked `write_node`, actively enforcing the screen-on restriction rather than bypassing it!
3. **Missing Permission Locks & Sibling Nodes**:
   - `BatteryNotify` is written as `"1"` without a lock; it must be written as `"0"` locked `0444` to suppress thermal step notification triggers.
   - `tran_charger_full` is completely omitted in `apply_extreme_charging()`. If bypass mode was previously engaged, `tran_charger_full` remains locked at `"1"`, completely cutting off battery charging.
   - `tran_game_mode` and `bypass_charger` must be explicitly locked to `"0"`.
   - `pe40`, `pe20`, and `enable_sc` must be locked to `"1"` (`0444`) to prevent the kernel state machine from disabling the RT9759 direct pump when display turns on.
4. **Disconnect Restoration & Session Baseline**: `DISCONNECT_CHARGING_RESETS` and `CHARGING_SESSION_NODES` must be updated to track and restore all R1 nodes on cable disconnect within 2 seconds.

---

## 2. Investigation Findings & Kernel Architecture

### 2.1 The Kernel DV2 Throttling Pipeline (`dmesg` Evidence)
On physical hardware (`Infinix-X698`, MediaTek Helio G96 `mt6781`, Android 14), kernel logs reveal the exact throttling sequence:
```
[ 1595.788362] (2)[301:dv2_algo_task][PCA]__dv2_get_ita_lmt __dv2_get_ita_lmt vbat(4040) icp_lmt(3000) gap(0)
[ 1595.788370] (2)[301:dv2_algo_task][PCA]__dv2_get_ita_lmt tv_sec(783) lcd_on(241) fb_on(1) tran_game_mode(0 1500)
[ 1595.788376] (2)[301:dv2_algo_task][PCA]__dv2_get_ita_lmt ita(org,tta,tbat,tdvchg,prlmt,throt,tpcb)=1500(3000,0,750,0,3000,-1,1500)
[ 1595.788381] (2)[301:dv2_algo_task][PCA]__dv2_get_idvchg_lmt idvchg_lmt(ita_lmt,idvchg_cc)=1500(1500,3000)
```
- In `/sys/firmware/devicetree/base/odm/mtk_pe50/game_limit_ita`, the devicetree binary encodes `1500` (mA).
- When the display is active (`lcd_on > 0`, `fb_on == 1`), `__dv2_get_ita_lmt` checks `BN_TestMode`:
  - If `BN_TestMode == 0`: `ita` is capped at `1500 mA`. With an RT9759 2:1 charge pump ($I_{\text{bat}} \approx 2 \times I_{\text{ta}}$), battery net current cannot exceed $\approx 2400\text{ mA}$.
  - If `BN_TestMode == 1`: The kernel driver treats the session as Factory Test Mode, completely ignoring `lcd_on`, `fb_on`, and `tran_game_mode`, permitting the full negotiated adapter current ($I_{\text{ta}} \ge 3000\text{--}3317\text{ mA}$, yielding $I_{\text{bat}} > 6000\text{ mA}$).

### 2.2 Analysis of Current Code in `native/lynxd/src/hardware/charging.rs`

#### Defect 1: Inverted `BN_TestMode` Value in `apply_extreme_charging()`
Lines 538–544 currently state:
```rust
// Lines 538-544 in native/lynxd/src/hardware/charging.rs
Self::write_node(&format!("{}/pe20", MTK_DIR), "1");
Self::write_node(&format!("{}/pe40", MTK_DIR), "1");
Self::write_node(&format!("{}/enable_sc", MTK_DIR), "1");
Self::write_node(&format!("{}/BatteryNotify", MTK_DIR), "1");
Self::write_node(&format!("{}/BN_TestMode", MTK_DIR), "0"); // <-- DEFECT: Writes 0 instead of 1!
Self::write_node_lock(&format!("{}/sw_jeita", MTK_DIR), "0");
```
- `BN_TestMode` is explicitly written as `"0"`.
- Furthermore, it uses `write_node`, which sets permissions to `0o666` and does NOT lock the node with `0o444`.

#### Defect 2: Premature Reset in `unlock_extreme_nodes()`
Lines 417–418 in `unlock_extreme_nodes()` state:
```rust
Self::write_node("/sys/devices/platform/charger/BN_TestMode", "0");
Self::write_node("/sys/devices/platform/charger/BatteryNotify", "1");
```
Because `apply_extreme_charging()` calls `unlock_extreme_nodes()` at line 526, it resets `BN_TestMode` to `"0"`, and then line 542 writes `"0"` again.

#### Defect 3: Missing `tran_charger_full` Clearing in `apply_extreme_charging()`
In `apply_bypass_charging()` (line 438):
```rust
Self::write_node_lock(&format!("{}/tran_charger_full", MTK_DIR), "1");
```
`tran_charger_full = 1` simulates a 100% full battery to the charger driver to halt cell charging for bypass. However, `apply_extreme_charging()` does not clear `tran_charger_full`. If a user previously engaged hardware bypass (or smart cutoff hit limit), `tran_charger_full` remains locked at `"1"`, preventing high-current charging.

#### Defect 4: Missing Immutable Permission Locks (`chmod 0444`)
`write_node_lock` works as:
```rust
#[inline]
pub fn write_node_lock(path: &str, val: &str) {
    if !Path::new(path).exists() {
        return;
    }
    Self::set_perm(path, 0o666);
    let _ = fs::write(path, val);
    Self::set_perm(path, 0o444);
}
```
Setting `0o444` removes `S_IWUSR`, `S_IWGRP`, and `S_IWOTH`. Android userspace thermal daemons (`thermalloadalgod`, `android.hardware.thermal@2.0-service.mtk`) running under UID 1000 (`system`) lack `CAP_FOWNER` to chmod files owned by root/kernel, and lack `CAP_DAC_OVERRIDE` to write to `0444` sysfs files. Consequently, the kernel VFS blocks their attempts to revert `BN_TestMode` with `-EACCES`.

### 2.3 Interaction with `core/Charging-Controller.sh` and CLI
- `core/Charging-Controller.sh` lines 21–23:
  ```sh
  hyper|extreme)
      exec "$LYNXD" charging mode hyper 2>/dev/null
      ;;
  ```
- `native/lynxd/src/main.rs` lines 730–736:
  `charging mode hyper` sets `charging.extreme_charging_enabled = true` and calls `ChargingRegulator::apply_from_config()`.
- `apply_from_config()` evaluates `is_hyper = true` and invokes `Self::apply_hyper_mode()`, which directly calls `Self::apply_extreme_charging(100, true, 12000)`.
- The CLI command `/data/adb/modules/Lynx/system/bin/lynxd charging mode hyper` is thus the direct entrypoint executing `apply_extreme_charging()`.

---

## 3. Blueprint & Concrete Recommendations for Worker M1

### 3.1 Node Matrix for R1 (Screen-On Bypass)

| Sysfs Node Path | Extreme/Hyper Value | Mode Lock | Disconnect Value | Disconnect Perm | Purpose |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `/sys/devices/platform/charger/BN_TestMode` | `"1"` | `0444` | `"0"` | `0644` | Enters factory test mode; neutralizes `lcd_on`/`fb_on` clamp in `__dv2_get_ita_lmt` |
| `/sys/devices/platform/charger/BatteryNotify` | `"0"` | `0444` | `"1"` | `0644` | Disables screen-state/thermal notification dispatch to throttling daemons |
| `/sys/devices/platform/charger/tran_charger_full` | `"0"` | `0444` | `"0"` | `0644` | Unlatches full battery simulation from prior bypass sessions |
| `/sys/devices/platform/charger/tran_game_mode` | `"0"` | `0444` | `"0"` | `0644` | Prevents kernel game mode thermal derating from kicking in |
| `/sys/devices/platform/charger/bypass_charger` | `"0"` | `0444` | `"0"` | `0644` | Deactivates hardware bypass switch so current enters the battery cell |
| `/sys/devices/platform/charger/enable_sc` | `"1"` | `0444` | `"0"` | `0644` | Enforces RT9759 switched capacitor direct charge pump activation |
| `/sys/devices/platform/charger/pe40` | `"1"` | `0444` | `"0"` | `0644` | Locks MediaTek Pump Express 4.0 / RFC direct charge handshake |
| `/sys/devices/platform/charger/pe20` | `"1"` | `0444` | `"0"` | `0644` | Locks Pump Express 2.0 fallback |
| `/sys/devices/platform/charger/sw_jeita` | `"0"` | `0444` | `"1"` | `0644` | Disarms software JEITA current throttling |

---

### 3.2 Exact Code Modifications in `native/lynxd/src/hardware/charging.rs`

#### A. Update `DISCONNECT_CHARGING_RESETS` (Lines 23–38)
Ensure `tran_game_mode` is tracked in disconnect resets:
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

#### B. Update `CHARGING_SESSION_NODES` (Lines 42–91)
Add `tran_game_mode` so its baseline is captured and restored:
```rust
    "/sys/devices/platform/charger/tran_charger_full",
    "/sys/devices/platform/charger/bypass_charger",
    "/sys/devices/platform/charger/tran_game_mode",
    "/sys/devices/platform/charger/BN_TestMode",
    "/sys/devices/platform/charger/BatteryNotify",
    "/sys/devices/platform/charger/sw_jeita",
```

#### C. Clean Up `unlock_extreme_nodes()` (Lines 416–419)
Remove lines 417–418 from `unlock_extreme_nodes()`:
```rust
// BEFORE:
        for &node in NODES {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
            }
        }

        Self::write_node("/sys/devices/platform/charger/BN_TestMode", "0");
        Self::write_node("/sys/devices/platform/charger/BatteryNotify", "1");

// AFTER:
        for &node in NODES {
            if Path::new(node).exists() {
                Self::set_perm(node, 0o666);
            }
        }
```

#### D. Ensure Session Baseline Capture in `apply_extreme_charging()`
Add idempotent baseline capture at the entry of `apply_extreme_charging()`:
```rust
    pub fn apply_extreme_charging(_target_soc: u32, _allow_lockout_bypass: bool, requested_ma: u32) {
        Self::capture_charging_session_baseline();

        let was_bypassed = Path::new("/dev/lynx_bypass_active").exists();
        let _ = fs::remove_file("/dev/lynx_bypass_active");
        let _ = fs::remove_file("/dev/lynx_standard_charging");
...
```

#### E. Overhaul `apply_extreme_charging()` MediaTek Enforcements (Lines 535–551)
Replace the existing lines 538–544 with immutable locked writes:
```rust
// BEFORE:
        Self::set_bcct_throttle(false);

        Self::write_node(&format!("{}/pe20", MTK_DIR), "1");
        Self::write_node(&format!("{}/pe40", MTK_DIR), "1");
        Self::write_node(&format!("{}/enable_sc", MTK_DIR), "1");
        Self::write_node(&format!("{}/BatteryNotify", MTK_DIR), "1");
        Self::write_node(&format!("{}/BN_TestMode", MTK_DIR), "0");
        Self::write_node_lock(&format!("{}/sw_jeita", MTK_DIR), "0");

// AFTER:
        Self::set_bcct_throttle(false);

        // Disarm Transsion / MediaTek Screen-On & Game Throttling (R1)
        Self::write_node_lock(&format!("{}/BN_TestMode", MTK_DIR), "1");
        Self::write_node_lock(&format!("{}/BatteryNotify", MTK_DIR), "0");
        Self::write_node_lock(&format!("{}/tran_charger_full", MTK_DIR), "0");
        Self::write_node_lock(&format!("{}/tran_game_mode", MTK_DIR), "0");
        Self::write_node_lock(&format!("{}/bypass_charger", MTK_DIR), "0");
        Self::write_node_lock(&format!("{}/sw_jeita", MTK_DIR), "0");

        // Enforce Direct Charge Pump & Protocols with Immutable Locks
        Self::write_node_lock(&format!("{}/pe20", MTK_DIR), "1");
        Self::write_node_lock(&format!("{}/pe40", MTK_DIR), "1");
        Self::write_node_lock(&format!("{}/enable_sc", MTK_DIR), "1");

        // Transsion CallState clamp reset (ensure voice calls do not suppress current)
        Self::write_node("/sys/devices/platform/odm/odm:tran_battery/Charging_CallState", "0");
        Self::write_node("/sys/devices/platform/tran_battery/Charging_CallState", "0");
```

---

## 4. Verification & Validation Strategy

### 4.1 Unit Testing & Compilation
1. Verify unit test passes for `DISCONNECT_CHARGING_RESETS`:
   ```bash
   cd e:\Data\GitHub\Lynx\native\lynxd
   cargo test --target x86_64-pc-windows-msvc -- hardware::charging::tests::disconnect_resets_only_charging_protocol_nodes
   ```
2. Check ARM64 compilation for Android:
   ```bash
   cargo check --target aarch64-linux-android
   ```

### 4.2 Target Device Verification (Infinix Note 11S X698)
1. **Connect & Deploy**:
   ```powershell
   .\tools\connect_device.cmd
   ```
2. **Engage Hyper Mode**:
   ```bash
   adb shell su -c "/data/adb/modules/Lynx/system/bin/lynxd charging mode hyper"
   ```
3. **Verify Node Values & Permissions (`0444`)**:
   ```bash
   adb shell su -c "ls -l /sys/devices/platform/charger/BN_TestMode /sys/devices/platform/charger/BatteryNotify /sys/devices/platform/charger/tran_charger_full"
   ```
   *Expected Output*:
   `-r--r--r-- 1 root root ... BN_TestMode` (cat returns `1`)  
   `-r--r--r-- 1 root root ... BatteryNotify` (cat returns `0`)  
   `-r--r--r-- 1 root root ... tran_charger_full` (cat returns `0`)
4. **Verify Kernel `dmesg` with Screen Active**:
   Ensure screen is ON, awake, and unlocked:
   ```bash
   adb shell su -c "dmesg | grep -iE '__dv2_get_ita_lmt|game_limit_ita' | tail -n 10"
   ```
   *Expected Output*:
   `ita` is not clamped down to 1500 mA when `lcd_on > 0` and `fb_on = 1`.
5. **Verify Disconnect Reversion**:
   Unplug charging cable, wait 2 seconds:
   ```bash
   adb shell su -c "cat /sys/devices/platform/charger/BN_TestMode; cat /sys/devices/platform/charger/BatteryNotify"
   ```
   *Expected Output*:
   `BN_TestMode` returns `0`, `BatteryNotify` returns `1`.
