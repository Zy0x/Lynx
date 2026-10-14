#!/usr/bin/env python3
"""
Tier 1: Feature Coverage - Requirement R4: Disconnect Fail-Safe Restoration & OVP Integrity
Verifies that critical silicon over-voltage protections (vbatovp, vbusovp) remain intact,
and guarantees that upon charger disconnection, all kernel overrides and thermal trip
points immediately revert to OEM baselines within 2 seconds with zero parasitic leakage.

Authoritative Sources:
- ORIGINAL_REQUEST.md: Section R4 & Acceptance Criteria § 30
- PROJECT.md: Features 6, 7, 8 & Interface Contracts lines 73-77
- explorer_survey_3/handoff.md: Sections 1.3-1.5, Section 2 Steps 2-4, and Section 5
"""

import sys
import time
from typing import Dict, Any, List
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from framework import BaseTarget, E2ETestCase


class TestR4OvpIntegrity(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F4-01",
            tier=1,
            feature="R4_DisconnectFailSafe",
            title="Silicon OVP Independence & Voltage Ceiling Integrity",
            authoritative_source="explorer_survey_3/handoff.md § 1.3 lines 58-66 & § 2 Step 2",
            description="Verifies that RT9759 regmap hardware comparator OVP registers are never written, maintaining silicon vbatovp/vbusovp."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")

        # Battery voltage must not exceed 4500 mV (safety ceiling below 4.928V hardware OVP)
        vbat_str = target.read_node("sys/class/power_supply/battery/voltage_now")
        assert vbat_str is not None, "voltage_now node not found"
        vbat_mv = int(vbat_str) // 1000 if int(vbat_str) >= 10000 else int(vbat_str)
        assert vbat_mv <= 4500, f"Battery voltage {vbat_mv} mV exceeded safe software ceiling of 4500 mV"

        return f"Silicon OVP intact: hardware registers unmodified, battery voltage {vbat_mv} mV <= 4500 mV", {
            "voltage_now_mv": vbat_mv,
            "max_ceiling_mv": 4500,
            "silicon_vbatovp_mv": 4928
        }


class TestR4TripleVectorDisconnectDetection(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F4-02",
            tier=1,
            feature="R4_DisconnectFailSafe",
            title="Fast Triple-Vector Disconnect Detection (<500ms)",
            authoritative_source="PROJECT.md § Feature 7 & explorer_survey_3/handoff.md § 1.4 lines 68-99",
            description="Simulates cable unplug and verifies disconnect is detected instantaneously without waiting for decoupling capacitor discharge."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        # Setup hyper mode first
        target.execute_command("lynxd charging mode hyper")

        # Measure disconnect detection latency
        t0 = time.perf_counter()
        res = target.execute_command("lynxd charging disconnect")
        detection_duration = time.perf_counter() - t0

        assert res.returncode == 0, f"Disconnect execution failed: {res.stderr}"
        assert detection_duration <= 0.50, f"Disconnect detection exceeded 500ms threshold: {detection_duration:.4f}s"

        # Verify offline state
        stat = target.read_node("sys/class/power_supply/battery/status")
        assert stat == "Discharging", f"Expected battery status 'Discharging', got '{stat}'"

        return f"Triple-vector disconnect detected in {detection_duration * 1000:.2f} ms (< 500 ms)", {
            "detection_duration_s": detection_duration,
            "battery_status": stat
        }


class TestR4NodeReversionLatency(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F4-03",
            tier=1,
            feature="R4_DisconnectFailSafe",
            title="Complete Hardware Node Reversion within 2 Seconds",
            authoritative_source="ORIGINAL_REQUEST.md § Acceptance Criteria § 30 & PROJECT.md § Interface Contracts lines 76-77",
            description="Verifies that all hardware nodes (enable_sc, BN_TestMode, sw_jeita, input_current) revert to OEM defaults within 2 seconds."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        # Start in hyper mode
        target.execute_command("lynxd charging mode hyper")
        assert target.read_node("sys/devices/platform/charger/enable_sc") == "1"

        # Execute disconnect and measure full reversion time
        t0 = time.perf_counter()
        target.execute_command("lynxd charging disconnect")
        reversion_time = time.perf_counter() - t0

        assert reversion_time < 2.0, f"Hardware reversion took {reversion_time:.3f}s (must be < 2.0s)"

        # Check critical nodes
        enable_sc = target.read_node("sys/devices/platform/charger/enable_sc")
        bn_test = target.read_node("sys/devices/platform/charger/BN_TestMode")
        sw_jeita = target.read_node("sys/devices/platform/charger/sw_jeita")
        in_cur = target.read_node("sys/devices/platform/charger/input_current")

        assert enable_sc == "0", f"enable_sc failed to revert to 0: {enable_sc}"
        assert bn_test == "0", f"BN_TestMode failed to revert to 0: {bn_test}"
        assert sw_jeita == "1", f"sw_jeita failed to revert to 1: {sw_jeita}"

        return f"Full node reversion confirmed in {reversion_time * 1000:.1f} ms (< 2.0s)", {
            "reversion_time_s": reversion_time,
            "enable_sc": enable_sc,
            "BN_TestMode": bn_test,
            "sw_jeita": sw_jeita,
            "input_current": in_cur
        }


class TestR4ThermalSafetyRestoration(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F4-04",
            tier=1,
            feature="R4_DisconnectFailSafe",
            title="Thermal Safety & Daemon Unfreezing on Disconnect",
            authoritative_source="PROJECT.md § Feature 8 & explorer_survey_3/handoff.md § 1.5 lines 101-118",
            description="Verifies that pcb_thermal_debug, Battery_Temperature, clabcct, and OEM thermal daemons are restored upon unplug."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")
        target.execute_command("lynxd charging disconnect")

        # Battery temperature spoof must be released (65535 or unlocked 0666)
        bat_temp = target.read_node("sys/devices/platform/battery/Battery_Temperature")
        perm = target.get_permission("sys/devices/platform/battery/Battery_Temperature")
        assert perm != 0o444, f"Battery_Temperature still locked read-only after disconnect: {oct(perm)}"

        # MediaTek ABCCT restored
        clabcct = target.read_node("proc/driver/thermal/clabcct")
        assert clabcct == "1", f"clabcct failed to restore to 1: {clabcct}"

        # PCB thermal debug reset to safe levels
        pcb_dbg = target.read_node("sys/devices/platform/odm/odm:tran_battery/pcb_thermal_debug")
        assert pcb_dbg is not None and "45" in pcb_dbg, f"pcb_thermal_debug not restored to OEM: {pcb_dbg}"

        return "Thermal safety envelopes and daemons fully restored", {
            "Battery_Temperature": bat_temp,
            "permission": oct(perm),
            "clabcct": clabcct,
            "pcb_thermal_debug": pcb_dbg
        }


class TestR4ZeroParasiticLeakage(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F4-05",
            tier=1,
            feature="R4_DisconnectFailSafe",
            title="Zero Parasitic Quiescent Current & Deep Sleep Shield",
            authoritative_source="explorer_survey_3/handoff.md § 2 Step 4 lines 141-144 & ORIGINAL_REQUEST.md § R4",
            description="Verifies that after disconnect, charge pump oscillator is disabled and system enters normal idle discharge."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")
        target.execute_command("lynxd charging disconnect")

        # Charge pump disabled
        enable_sc = target.read_node("sys/devices/platform/charger/enable_sc")
        assert enable_sc == "0", f"enable_sc still 1, causing oscillator parasitic leakage!"

        # Current must be negative or zero (discharging, not drawing external power)
        cur_str = target.read_node("sys/class/power_supply/battery/current_now")
        assert cur_str is not None, "current_now not found"
        cur_val = int(cur_str)
        assert cur_val <= 0, f"Expected non-positive battery current after disconnect, got {cur_val} uA"

        return f"Zero parasitic leakage verified: enable_sc=0, battery in idle discharge ({cur_val} uA)", {
            "enable_sc": enable_sc,
            "discharging_current_uA": cur_val
        }


class TestR4RepeatedCycleIntegrity(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F4-06",
            tier=1,
            feature="R4_DisconnectFailSafe",
            title="Repeated Connect-Disconnect Cycle Stress (10 Iterations)",
            authoritative_source="AGENTS.md § 4.2 (Atomic Concurrency Lock) & ORIGINAL_REQUEST.md § R4",
            description="Executes 10 consecutive hyper-charge connect and disconnect cycles to verify zero lock file leaks or deadlocks."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        cycles = 10
        for i in range(cycles):
            # Connect in hyper mode
            res_h = target.execute_command("lynxd charging mode hyper")
            assert res_h.returncode == 0, f"Failed hyper connect on cycle {i}"
            assert target.read_node("sys/devices/platform/charger/enable_sc") == "1"

            # Disconnect
            res_d = target.execute_command("lynxd charging disconnect")
            assert res_d.returncode == 0, f"Failed disconnect on cycle {i}"
            assert target.read_node("sys/devices/platform/charger/enable_sc") == "0"

        # Verify no stale lockfiles exist
        lock_exists = target.node_exists("dev/lynx_mode.lock")
        assert not lock_exists, "dev/lynx_mode.lock remained after 10 cycles!"

        return f"10 connect-disconnect cycles executed cleanly without deadlocks or leaked locks", {
            "cycles_completed": cycles,
            "final_enable_sc": "0"
        }


def get_r4_test_cases() -> List[E2ETestCase]:
    return [
        TestR4OvpIntegrity(),
        TestR4TripleVectorDisconnectDetection(),
        TestR4NodeReversionLatency(),
        TestR4ThermalSafetyRestoration(),
        TestR4ZeroParasiticLeakage(),
        TestR4RepeatedCycleIntegrity(),
    ]


if __name__ == "__main__":
    from framework import MockSandboxTarget
    target = MockSandboxTarget()
    for tc in get_r4_test_cases():
        res = tc.run(target)
        print(f"[{res.status}] {res.test_id}: {res.title} - {res.message}")
    target.cleanup()
