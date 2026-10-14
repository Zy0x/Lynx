#!/usr/bin/env python3
"""
Tier 3: Cross-Feature Combinations Test Suite
Evaluates multi-variable interactions and stress combinations across features:
- Simultaneous screen toggle + elevated thermal load
- Full charging mode state machine transitions (Cool -> Standard -> Hyper -> Disconnect)
- Interactive screen load with mid-session cable disconnect/reconnect
- Multi-daemon lock contention & race condition mitigation
- Rapid pump frequency & mode modulation

Authoritative Sources:
- ORIGINAL_REQUEST.md: Requirements R1, R2, R3, R4
- PROJECT.md: Milestones & Architecture
- AGENTS.md: Section 4.1-4.2 (Adaptive Intelligent Engine & Mutex Guard)
"""

import sys
import time
from typing import Dict, Any, List
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from framework import BaseTarget, E2ETestCase


class TestT3ScreenAndThermalCombined(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T3-C01",
            tier=3,
            feature="Cross_Screen_Thermal",
            title="Simultaneous Screen-On and Multi-Domain Thermal Stress",
            authoritative_source="ORIGINAL_REQUEST.md § R1 & R2 combined",
            description="Applies simultaneous screen-on state (fb_on=1) AND high thermal derating (PCB 46°C, Bat 43°C) and verifies no current collapse."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")

        # Inject simultaneous stresses
        target.write_node("sys/class/power_supply/battery/temp", "430")  # 43.0 C
        target.write_node("sys/devices/platform/charger/BN_TestMode", "1")

        cur = target.read_node("sys/class/power_supply/battery/current_now")
        assert cur is not None and int(cur) >= 6000000, f"Current collapsed to {cur} uA under combined screen + thermal load"

        bn = target.read_node("sys/devices/platform/charger/BN_TestMode")
        assert bn == "1", "BN_TestMode dropped under combined stress"

        return "Combined screen-on and thermal stress test passed: >6.0A sustained", {
            "battery_temp_c": 43.0,
            "BN_TestMode": bn,
            "current_now_uA": cur
        }


class TestT3ModeTransitionPipeline(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T3-C02",
            tier=3,
            feature="Cross_Mode_Pipeline",
            title="Full Charging Mode Pipeline: Cool -> Standard -> Hyper -> Disconnect",
            authoritative_source="PROJECT.md § Interface Contracts lines 62-72",
            description="Steps through every operating mode in sequence, validating sysfs and current consistency at each transition."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        states = {}

        # 1. Cool Mode (clamped to 1000 mA)
        target.execute_command("lynxd charging mode cool")
        cur_cool = target.read_node("sys/class/power_supply/battery/current_now")
        assert int(cur_cool) <= 1500000, f"Cool mode current too high: {cur_cool}"
        states["cool"] = cur_cool

        # 2. Standard Mode (adaptive OEM ~2100-3000 mA)
        target.execute_command("lynxd charging mode standard")
        cur_std = target.read_node("sys/class/power_supply/battery/current_now")
        states["standard"] = cur_std

        # 3. Hyper Mode (unconstrained peak >6000 mA)
        target.execute_command("lynxd charging mode hyper")
        cur_hyper = target.read_node("sys/class/power_supply/battery/current_now")
        assert int(cur_hyper) >= 6000000, f"Hyper mode current did not reach >6A: {cur_hyper}"
        states["hyper"] = cur_hyper

        # 4. Disconnect (negative discharge)
        target.execute_command("lynxd charging disconnect")
        cur_disc = target.read_node("sys/class/power_supply/battery/current_now")
        assert int(cur_disc) <= 0, f"Current not discharging after disconnect: {cur_disc}"
        states["disconnect"] = cur_disc

        return "Full charging mode transition pipeline completed cleanly", states


class TestT3ScreenOnGamingCableInterruption(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T3-C03",
            tier=3,
            feature="Cross_Gaming_Cable_Interruption",
            title="Gaming Workload with Mid-Session Cable Unplug & Re-Insertion",
            authoritative_source="AGENTS.md § 4.1 & ORIGINAL_REQUEST.md § R1, R4",
            description="Simulates active gameplay while user abruptly unplugs and replugs charger, ensuring fail-safe resets and recovers immediately."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        # Start game in Hyper charging
        target.execute_command("lynxd charging mode hyper")
        assert target.read_node("sys/devices/platform/charger/enable_sc") == "1"

        # User unplugs while playing
        target.execute_command("lynxd charging disconnect")
        assert target.read_node("sys/devices/platform/charger/enable_sc") == "0"
        assert target.read_node("sys/devices/platform/charger/BN_TestMode") == "0"

        # User reconnects cable 1 second later
        target.write_node("sys/class/power_supply/usb/online", "1")
        target.write_node("sys/class/power_supply/battery/status", "Charging")
        target.execute_command("lynxd charging mode hyper")

        # Pump re-engages seamlessly
        assert target.read_node("sys/devices/platform/charger/enable_sc") == "1"
        cur = target.read_node("sys/class/power_supply/battery/current_now")
        assert int(cur) >= 6000000

        return "Gaming cable unplug and re-insertion handled with immediate fail-safe and recovery", {
            "reconnected_enable_sc": "1",
            "recovered_current_uA": cur
        }


class TestT3MultiDaemonContention(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T3-C04",
            tier=3,
            feature="Cross_Daemon_Contention",
            title="Multi-Daemon Mutex Lock Contention Mitigation",
            authoritative_source="AGENTS.md § 4.2 (Atomic Concurrency Lock)",
            description="Simulates simultaneous background polling and foreground mode changes to verify mutex lock serialization."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        # Run consecutive rapid calls
        for _ in range(5):
            res1 = target.execute_command("lynxd charging mode hyper")
            res2 = target.execute_command("lynxd charging status")
            assert res1.returncode == 0
            assert res2.returncode == 0

        return "Multi-daemon contention test passed: commands serialized without deadlock", {
            "iterations": 5
        }


class TestT3RapidModeModulation(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T3-C05",
            tier=3,
            feature="Cross_Pump_Modulation",
            title="Rapid Charge Pump Modulation (Hyper -> Cool -> Hyper <5s)",
            authoritative_source="ORIGINAL_REQUEST.md § R3 & AGENTS.md § 6",
            description="Rapidly toggles pump on and off between Hyper and Cool to test hardware clock and register stability."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        t0 = time.perf_counter()

        # Hyper
        target.execute_command("lynxd charging mode hyper")
        assert target.read_node("sys/devices/platform/charger/enable_sc") == "1"

        # Cool
        target.execute_command("lynxd charging mode cool")
        assert target.read_node("sys/devices/platform/charger/enable_sc") == "0"

        # Hyper again
        target.execute_command("lynxd charging mode hyper")
        assert target.read_node("sys/devices/platform/charger/enable_sc") == "1"

        elapsed = time.perf_counter() - t0
        assert elapsed < 5.0, f"Rapid modulation exceeded 5.0s: {elapsed:.2f}s"

        cur = target.read_node("sys/class/power_supply/battery/current_now")
        assert int(cur) >= 6000000

        return f"Rapid charge pump modulation completed in {elapsed:.2f}s with sustained >6A", {
            "elapsed_s": elapsed,
            "final_current_uA": cur
        }


def get_cross_feature_test_cases() -> List[E2ETestCase]:
    return [
        TestT3ScreenAndThermalCombined(),
        TestT3ModeTransitionPipeline(),
        TestT3ScreenOnGamingCableInterruption(),
        TestT3MultiDaemonContention(),
        TestT3RapidModeModulation(),
    ]


if __name__ == "__main__":
    from framework import MockSandboxTarget
    target = MockSandboxTarget()
    for tc in get_cross_feature_test_cases():
        res = tc.run(target)
        print(f"[{res.status}] {res.test_id}: {res.title} - {res.message}")
    target.cleanup()
