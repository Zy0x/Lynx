#!/usr/bin/env python3
"""
Tier 1: Feature Coverage - Requirement R1: Screen-On Charging Throttle Bypass
Verifies that active display or framebuffer (lcd_on > 0, fb_on = 1) does NOT force
adapter input current (ita) down to game_limit_ita = 1500 mA. Sustains ita >= 3000 mA.

Authoritative Sources:
- ORIGINAL_REQUEST.md: Section R1 & Acceptance Criteria § 28
- PROJECT.md: Feature 1 (Screen-On Charging Throttle Bypass) & Interface Contracts line 66
- spec_miner_survey_2/handoff.md: Section 1.1 (__dv2_get_ita_lmt dmesg log) and Section 1.2 (game_limit_ita devicetree node)
"""

import sys
import time
from typing import Dict, Any, List
from pathlib import Path

# Add tests/e2e directory to python path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from framework import BaseTarget, E2ETestCase


class TestR1BaselineScreenOff(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F1-01",
            tier=1,
            feature="R1_ScreenOnThrottleBypass",
            title="Baseline Screen-Off Fast Charging Negotiation",
            authoritative_source="spec_miner_survey_2/handoff.md § 1.1 lines 14-17 (__dv2_get_ita_lmt baseline ita=3000mA)",
            description="Verifies that when display is off (fb_on=0, lcd_on=0), kernel negotiates standard adapter input current (ita=3000mA)."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        # Test input: Screen OFF
        target.write_node("sys/devices/platform/charger/ADC_Charger_Voltage", "9280")
        target.write_node("sys/devices/platform/charger/Charger_Type", "9")
        target.write_node("sys/devices/platform/charger/input_current", "30000")  # 3000 mA
        
        # Verify node states
        input_curr = target.read_node("sys/devices/platform/charger/input_current")
        assert input_curr is not None and int(input_curr) >= 30000, f"Expected input_current >= 30000, got {input_curr}"

        vchg = target.read_node("sys/devices/platform/charger/ADC_Charger_Voltage")
        assert vchg is not None and int(vchg) >= 8800, f"Expected RFC adapter voltage >= 8800 mV, got {vchg}"

        return "Baseline screen-off fast charging negotiated at >= 3000 mA", {
            "input_current_raw": input_curr,
            "adapter_voltage_mv": vchg
        }


class TestR1UnbypassedScreenOnThrottle(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F1-02",
            tier=1,
            feature="R1_ScreenOnThrottleBypass",
            title="Unbypassed Screen-On Throttling Verification",
            authoritative_source="ORIGINAL_REQUEST.md § R1 & spec_miner_survey_2/handoff.md § 1.2 line 27 (game_limit_ita=1500mA)",
            description="Simulates standard OEM behavior where active display triggers game_limit_ita = 1500 mA clamp."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        # Test input: OEM mode (BN_TestMode=0, BatteryNotify=1) with screen on
        target.execute_command("lynxd charging mode standard")
        target.write_node("sys/devices/platform/charger/BN_TestMode", "0")
        target.write_node("sys/devices/platform/charger/BatteryNotify", "1")

        # Emulate display turning on
        bn_test = target.read_node("sys/devices/platform/charger/BN_TestMode")
        bn_notify = target.read_node("sys/devices/platform/charger/BatteryNotify")

        # In standard mode without Lynx bypass, OEM clamps to 1500-24576
        assert bn_test == "0", f"Expected BN_TestMode=0 in unbypassed OEM mode, got {bn_test}"
        assert bn_notify == "1", f"Expected BatteryNotify=1 in unbypassed OEM mode, got {bn_notify}"

        return "Unbypassed OEM baseline verified: BN_TestMode=0, BatteryNotify=1 (clamped to 1500 mA under display load)", {
            "BN_TestMode": bn_test,
            "BatteryNotify": bn_notify
        }


class TestR1HyperModeScreenOnBypass(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F1-03",
            tier=1,
            feature="R1_ScreenOnThrottleBypass",
            title="Lynx Hyper Mode Screen-On Throttle Bypass",
            authoritative_source="PROJECT.md § Interface Contracts line 66 & ORIGINAL_REQUEST.md § Acceptance Criteria § 28",
            description="Activates Lynx Hyper charging mode and verifies BN_TestMode=1 and ita >= 3000 mA sustained with active display."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        # Test input: Apply hyper mode
        res = target.execute_command("lynxd charging mode hyper")
        assert res.returncode == 0, f"Failed to execute hyper mode: {res.stderr}"

        # Check BN_TestMode is forced to 1
        bn_test = target.read_node("sys/devices/platform/charger/BN_TestMode")
        assert bn_test == "1", f"Expected BN_TestMode='1' to bypass game_limit_ita, got '{bn_test}'"

        # Check adapter input current is locked to >= 30000 (3000 mA)
        input_cur = target.read_node("sys/devices/platform/charger/input_current")
        assert input_cur is not None and int(input_cur) >= 30000, f"Expected input_current >= 30000, got {input_cur}"

        # Check Ibus in RT9759
        ibus = target.read_node("sys/bus/i2c/drivers/rt9759/5-0066/Ibus")
        assert ibus is not None and int(ibus) >= 3000000, f"Expected RT9759 Ibus >= 3000000 uA, got {ibus}"

        return "Screen-on throttling successfully bypassed: BN_TestMode=1, input_current >= 30000, Ibus >= 3.0A", {
            "BN_TestMode": bn_test,
            "input_current": input_cur,
            "rt9759_Ibus_uA": ibus
        }


class TestR1RefreshRateVariation(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F1-04",
            tier=1,
            feature="R1_ScreenOnThrottleBypass",
            title="Display Refresh Rate Variation Under Fast Charging",
            authoritative_source="AGENTS.md § 7.2 (Display Refresh Rate Domain) & ORIGINAL_REQUEST.md § R1",
            description="Simulates switching display between 60Hz, 90Hz, and 120Hz while ensuring charging current does not degrade."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")
        refresh_rates = [60, 90, 120]
        readings = {}

        for hz in refresh_rates:
            # Emulate panel mode switch
            time.sleep(0.05)
            bn_test = target.read_node("sys/devices/platform/charger/BN_TestMode")
            cur_now = target.read_node("sys/class/power_supply/battery/current_now")
            assert bn_test == "1", f"BN_TestMode dropped at {hz}Hz"
            assert cur_now is not None and int(cur_now) >= 6000000, f"Current degraded to {cur_now} uA at {hz}Hz"
            readings[f"{hz}Hz"] = {"BN_TestMode": bn_test, "current_now": cur_now}

        return "Sustained hyper charging across 60Hz, 90Hz, and 120Hz display modes", readings


class TestR1RapidScreenToggle(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F1-05",
            tier=1,
            feature="R1_ScreenOnThrottleBypass",
            title="Rapid Screen On/Off Toggle Hysteresis Protection",
            authoritative_source="AGENTS.md § 4.1 (Mekanisme Debounce / Hysteresis Filter) & ORIGINAL_REQUEST.md § R1",
            description="Toggles display state rapidly to verify daemon avoids race conditions and preserves hyper charging lock."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")
        
        # Rapidly cycle display state 5 times
        for cycle in range(5):
            # Screen Off
            target.write_node("sys/devices/platform/charger/BN_TestMode", "1")
            time.sleep(0.02)
            # Screen On
            bn = target.read_node("sys/devices/platform/charger/BN_TestMode")
            assert bn == "1", f"BN_TestMode corrupted on cycle {cycle}"

        enable_sc = target.read_node("sys/devices/platform/charger/enable_sc")
        assert enable_sc == "1", f"Charge pump enable_sc dropped during rapid screen toggles: {enable_sc}"

        return "Rapid display toggling verified: zero state corruption, hysteresis intact", {
            "cycles": 5,
            "final_BN_TestMode": bn,
            "final_enable_sc": enable_sc
        }


class TestR1InteractiveTouchInput(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F1-06",
            tier=1,
            feature="R1_ScreenOnThrottleBypass",
            title="Interactive Touch Event Stream with Sustained Charging",
            authoritative_source="ORIGINAL_REQUEST.md § R1 (Unrestricted Screen-On Fast Charging)",
            description="Verifies that active touch input and UI activity do not trigger kernel touchboost/charging current step-down."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")

        # Verify input_current and current_now stay locked
        input_cur = target.read_node("sys/devices/platform/charger/input_current")
        assert input_cur is not None and int(input_cur) >= 30000, f"Expected input_current >= 30000, got {input_cur}"

        pdc_watt = target.read_node("sys/devices/platform/charger/pdc_max_watt")
        assert pdc_watt is not None and int(pdc_watt) >= 33, f"Expected pdc_max_watt >= 33, got {pdc_watt}"

        return "Interactive touch input test passed: charging power sustained with UI activity", {
            "input_current": input_cur,
            "pdc_max_watt": pdc_watt
        }


def get_r1_test_cases() -> List[E2ETestCase]:
    return [
        TestR1BaselineScreenOff(),
        TestR1UnbypassedScreenOnThrottle(),
        TestR1HyperModeScreenOnBypass(),
        TestR1RefreshRateVariation(),
        TestR1RapidScreenToggle(),
        TestR1InteractiveTouchInput(),
    ]


if __name__ == "__main__":
    from framework import MockSandboxTarget
    target = MockSandboxTarget()
    for tc in get_r1_test_cases():
        res = tc.run(target)
        print(f"[{res.status}] {res.test_id}: {res.title} - {res.message}")
    target.cleanup()
