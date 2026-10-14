# Original User Request

## 2026-10-14T13:41:33Z

Project to eliminate all charging throttles (screen-on limits, thermal trip points, and charging algorithm governor constraints) in the Lynx module and kernel HAL to unlock constant high-current (>6000 mA) direct charge pump fast charging on MediaTek & Transsion hardware.

Working directory: e:\Data\GitHub\Lynx
Integrity mode: development

## Requirements

### R1. Screen-On Charging Throttling Bypass
Eliminate the kernel and hardware abstraction restriction where active display or framebuffer (`lcd_on > 0`, `fb_on = 1`) forces adapter input current (`ita`) down to the restricted game/screen-on limit (`game_limit_ita = 1500 mA`). The system must sustain full negotiated adapter current regardless of display power state.

### R2. Charging Thermal Throttling & Trip-Point Neutralization
Bypass or neutralize all thermal throttling stages (`tbat`, `tpcb`, `tdvchg`, `tswchg`) within the fast charging algorithms (MediaTek PE / Transsion Direct Charge Pump dv2 algorithm) and user-space thermal daemons. The charging controller must prevent thermal step-down levels (such as Level 6 `tbat` and Level 7 `tpcb`) from throttling the adapter current (`ita`) below the maximum negotiated threshold.

### R3. Continuous Maximum Pump Current Enforcement (>6A Battery Current)
Force and lock the input adapter current (`ita`) and charge pump driver (RT9759 2:1 charge pump) at maximum hardware capacity (3000 mA – 3300 mA input) during Constant Current (CC) phase below 80% SoC, delivering sustained net current exceeding 6000 mA (>6.0 A) directly into the battery cells.

### R4. Fail-Safe Disconnect Restoration & Over-Voltage Protection
Ensure critical battery over-voltage protections (`vbatovp`, `vbusovp`) remain intact to prevent hardware damage, and guarantee that upon charger disconnection (`unplugged`), all kernel overrides and thermal trip points immediately revert to OEM baselines with zero parasitic thermal leakage or battery drain.

## Acceptance Criteria

### Telemetry & Current Verification
- [ ] Live battery telemetry (`/sys/class/power_supply/battery/current_now`) measures net charging current > 6,000,000 uA (> 6,000 mA / 6.0 A) during the low-battery CC phase with Transsion 45W/33W fast charger.
- [ ] Kernel charging log (`dmesg`) confirms `__dv2_get_ita_lmt` sustains `ita >= 3000 mA` without throttling down to 1500 mA while the screen is on (`lcd_on > 0`, `fb_on = 1`).
- [ ] Thermal level checks (`tbat_level`, `tpcb_level`) in `dv2_algo_task` do not throttle `ita` below 3000 mA even when device temperature exceeds 40°C.
- [ ] Unplugging the charger restores all system nodes, BCCT governors, and thermal settings to safe OEM baselines within 2 seconds.
