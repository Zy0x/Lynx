## 2026-10-14T13:44:47Z
You are Spec Miner 2 for the Lynx Universal Charging Throttle Elimination project.
Your working directory is: e:\Data\GitHub\Lynx\.agents\teamwork\spec_miner_survey_2
Project root is: e:\Data\GitHub\Lynx
Mandatory requirement: Read the original request at: e:\Data\GitHub\Lynx\.agents\teamwork\ORIGINAL_REQUEST.md

Objective:
Mine and specify the exact kernel interfaces, sysfs nodes, driver parameters, and algorithms related to MediaTek & Transsion fast charging and direct charge pump technology, specifically:
- Transsion Direct Charge Pump dv2 algorithm (`dv2_algo_task`, `__dv2_get_ita_lmt`, `game_limit_ita = 1500 mA`, screen state `lcd_on`, `fb_on`).
- Thermal step-down levels: `tbat_level`, `tpcb_level`, `tdvchg`, `tswchg` (especially Level 6 `tbat` and Level 7 `tpcb`) and user-space thermal daemons.
- RT9759 2:1 charge pump driver, input adapter current (`ita` target 3000 mA - 3300 mA), and battery current delivery (>6000 mA).
- Kernel sysfs nodes and control parameters across `/sys/class/power_supply/`, `/sys/devices/platform/`, thermal zones, and MTK power HAL.

Deliverables:
Document exact sysfs paths, driver attributes, kernel symbols, algorithms, and override mechanisms in `e:\Data\GitHub\Lynx\.agents\teamwork\spec_miner_survey_2\analysis.md` and `e:\Data\GitHub\Lynx\.agents\teamwork\spec_miner_survey_2\handoff.md`. Notify the orchestrator when finished using `send_message`.
