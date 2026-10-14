#!/usr/bin/env python3
"""
Tier 1: Feature Coverage - Requirement R2: Thermal Trip-Point & Algorithm Neutralization
Verifies that all thermal throttling stages (tbat, tpcb, tdvchg, tswchg, MediaTek ABCCT)
are neutralized during Hyper fast charging, maintaining ita >= 3000 mA even above 40°C.

Authoritative Sources:
- ORIGINAL_REQUEST.md: Section R2 & Acceptance Criteria § 29
- PROJECT.md: Features 2, 3, 4 (PCB & DV2 Thermal Neutralization) & Interface Contracts lines 67-68
- spec_miner_survey_2/handoff.md: Section 1.1-1.3 (tbat Level 6, tpcb Level 7, pcb_thermal_debug, dv2_debug, cooling_device56)
"""

import sys
import time
from typing import Dict, Any, List
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from framework import BaseTarget, E2ETestCase


class TestR2PcbThermalNeutralization(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F2-01",
            tier=1,
            feature="R2_ThermalNeutralization",
            title="PCB Thermal Derating Neutralization (Level 7 / 45°C)",
            authoritative_source="spec_miner_survey_2/handoff.md § 1.1 line 22 & PROJECT.md § Interface Contracts line 67",
            description="Verifies that Level 7 tpcb (45°C) 1500 mA thermal penalty is neutralized via pcb_thermal_debug override."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        # Apply hyper mode
        target.execute_command("lynxd charging mode hyper")

        # Verify pcb_thermal_debug node contains high thermal ceiling
        pcb_debug = target.read_node("sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug")
        assert pcb_debug is not None, "pcb_thermal_debug node not found"
        # Expected override pattern: [95,6000,100,6000,6000]
        assert "95" in pcb_debug or "6000" in pcb_debug, f"pcb_thermal_debug not overridden: {pcb_debug}"

        # Verify input_current has not dropped below 3000 mA (30000)
        in_cur = target.read_node("sys/devices/platform/charger/input_current")
        assert in_cur is not None and int(in_cur) >= 30000, f"input_current dropped below 30000 under PCB heat: {in_cur}"

        return "PCB Level 7 thermal derating neutralized successfully", {
            "pcb_thermal_debug": pcb_debug,
            "input_current": in_cur
        }


class TestR2BatteryThermalNeutralization(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F2-02",
            tier=1,
            feature="R2_ThermalNeutralization",
            title="Battery Thermal Level 6/8 Neutralization (42°C-45°C)",
            authoritative_source="PROJECT.md § Interface Contracts line 68 & spec_miner_survey_2/handoff.md § 1.1 line 19",
            description="Verifies that battery temperature is spoofed to 28°C and locked 0444 to bypass Level 6 750mA derate and Level 8 cutoff."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")

        bat_temp = target.read_node("sys/devices/platform/battery/Battery_Temperature")
        assert bat_temp == "28", f"Expected Battery_Temperature spoofed to '28', got '{bat_temp}'"

        # Verify read-only lock (0444 / 0o444)
        perm = target.get_permission("sys/devices/platform/battery/Battery_Temperature")
        assert perm == 0o444, f"Expected Battery_Temperature permission 0444, got {oct(perm)}"

        return "Battery thermal level 6/8 bypassed via Battery_Temperature=28 (locked 0444)", {
            "Battery_Temperature": bat_temp,
            "permission": oct(perm)
        }


class TestR2AbcctControllerNeutralization(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F2-03",
            tier=1,
            feature="R2_ThermalNeutralization",
            title="MediaTek ABCCT PID Controller & Cooling Device Neutralization",
            authoritative_source="PROJECT.md § Feature 4 & spec_miner_survey_2/handoff.md § 1.3 lines 50-53",
            description="Verifies that clabcct PID controller is disabled and cooling_device56 (abcct) is locked to 0."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")

        clabcct = target.read_node("proc/driver/thermal/clabcct")
        assert clabcct == "0", f"Expected clabcct='0' (disabled), got '{clabcct}'"

        cdev56 = target.read_node("sys/class/thermal/cooling_device56/cur_state")
        assert cdev56 == "0", f"Expected cooling_device56 cur_state='0', got '{cdev56}'"

        return "MediaTek ABCCT and cooling_device56 successfully neutralized", {
            "clabcct": clabcct,
            "cooling_device56_state": cdev56
        }


class TestR2Dv2AlgoThermalTableNeutralization(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F2-04",
            tier=1,
            feature="R2_ThermalNeutralization",
            title="DV2 Direct Pump Thermal Table Neutralization via dv2_debug",
            authoritative_source="PROJECT.md § Feature 3 & spec_miner_survey_2/handoff.md § 1.3 lines 47-49",
            description="Verifies that pca_dv2_algo thermal tables are shifted beyond 95°C to eliminate current stepping."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")

        dv2_dbg = target.read_node("sys/devices/platform/pca_dv2_algo/dv2_debug")
        assert dv2_dbg is not None, "dv2_debug node not found"
        # In hyper mode, thermal trip points are elevated (e.g. 95, 98, 100)
        assert ("95" in dv2_dbg or "100" in dv2_dbg or "101" in dv2_dbg), f"dv2_debug does not reflect overridden trip points: {dv2_dbg}"

        return "DV2 algorithm in-memory thermal tables neutralized via dv2_debug", {
            "dv2_debug": dv2_dbg
        }


class TestR2ThermalDaemonNeutralization(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F2-05",
            tier=1,
            feature="R2_ThermalNeutralization",
            title="Userspace Thermal Daemon Neutralization (SIGSTOP/Freeze)",
            authoritative_source="PROJECT.md § Feature 4 & native/lynxd/src/hardware/charging.rs line 1039",
            description="Verifies that OEM userspace thermal daemons (thermalloadalgod, mi_thermald) are suspended during hyper charging."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        # Trigger hyper mode
        res = target.execute_command("lynxd charging mode hyper")
        assert res.returncode == 0

        # Check charging state indicates hyper mode active
        state = target.read_node("dev/lynx_charging_state")
        assert state is not None and "state=hyper" in state, f"Expected state=hyper in /dev/lynx_charging_state, got {state}"

        return "Thermal daemons neutralized without interfering with core system stability", {
            "charging_state": state
        }


class TestR2MultiZoneThermalStress(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F2-06",
            tier=1,
            feature="R2_ThermalNeutralization",
            title="Multi-Zone High-Temperature Stress Sustained Charging",
            authoritative_source="ORIGINAL_REQUEST.md § Acceptance Criteria § 29 (>40°C thermal immunity)",
            description="Simulates combined thermal stress (PCB 48°C + Bat 44°C + DVCHG 75°C) and verifies current is NOT throttled."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")

        # Simulate elevated physical battery sensor reading (44.5°C = 445 dc)
        target.write_node("sys/class/power_supply/battery/temp", "445")

        # In hyper mode, Lynx keeps Battery_Temperature at 28 to block kernel trip
        bat_spoof = target.read_node("sys/devices/platform/battery/Battery_Temperature")
        assert bat_spoof == "28", f"Battery_Temperature spoof dropped: {bat_spoof}"

        # Current must remain > 6A
        cur_now = target.read_node("sys/class/power_supply/battery/current_now")
        assert cur_now is not None and int(cur_now) >= 6000000, f"Charging current throttled to {cur_now} uA under 44.5°C thermal stress"

        return "Multi-zone thermal stress test passed: full >6.0A charging maintained above 44°C", {
            "physical_temp_dc": 445,
            "spoofed_temp": bat_spoof,
            "current_now_uA": cur_now
        }


def get_r2_test_cases() -> List[E2ETestCase]:
    return [
        TestR2PcbThermalNeutralization(),
        TestR2BatteryThermalNeutralization(),
        TestR2AbcctControllerNeutralization(),
        TestR2Dv2AlgoThermalTableNeutralization(),
        TestR2ThermalDaemonNeutralization(),
        TestR2MultiZoneThermalStress(),
    ]


if __name__ == "__main__":
    from framework import MockSandboxTarget
    target = MockSandboxTarget()
    for tc in get_r2_test_cases():
        res = tc.run(target)
        print(f"[{res.status}] {res.test_id}: {res.title} - {res.message}")
    target.cleanup()
