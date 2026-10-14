#!/usr/bin/env python3
"""
Tier 1: Feature Coverage - Requirement R3: Continuous Maximum Pump Current Enforcement (>6A)
Verifies that RT9759 2:1 switched capacitor charge pump and input adapter current (ita)
are locked at maximum hardware capacity (3000-3300 mA input), delivering sustained net
current exceeding 6,000,000 uA (>6.0 A) directly into the battery cells during CC phase.

Authoritative Sources:
- ORIGINAL_REQUEST.md: Section R3 & Acceptance Criteria § 27
- PROJECT.md: Feature 5 & Architecture lines 27-36 & Interface Contracts lines 69-71
- explorer_survey_3/handoff.md: Section 1.1-1.2 and Section 2 Step 1 (I_net = 2 * I_bus * 0.97 - I_sys > 6000mA)
"""

import sys
import time
from typing import Dict, Any, List
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from framework import BaseTarget, E2ETestCase


class TestR3PumpEngagement(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F3-01",
            tier=1,
            feature="R3_ContinuousPumpCurrent",
            title="RT9759 2:1 Switched-Capacitor Pump Engagement",
            authoritative_source="PROJECT.md § Interface Contracts line 69 (enable_sc = 1)",
            description="Verifies that enable_sc is set to 1 when entering hyper charging mode with fast charger connected."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")

        enable_sc = target.read_node("sys/devices/platform/charger/enable_sc")
        assert enable_sc == "1", f"Expected enable_sc='1' in Hyper Mode, got '{enable_sc}'"

        pe20 = target.read_node("sys/devices/platform/charger/pe20")
        pe40 = target.read_node("sys/devices/platform/charger/pe40")
        assert pe20 == "1" and pe40 == "1", f"Pump Express protocols not enabled: pe20={pe20}, pe40={pe40}"

        return "RT9759 direct charge pump successfully engaged (enable_sc=1, pe20=1, pe40=1)", {
            "enable_sc": enable_sc,
            "pe20": pe20,
            "pe40": pe40
        }


class TestR3InputCurrentLock(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F3-02",
            tier=1,
            feature="R3_ContinuousPumpCurrent",
            title="Maximum Adapter Input Current Lock (input_current = 33170)",
            authoritative_source="PROJECT.md § Interface Contracts line 71 & native/lynxd/src/hardware/charging.rs line 547",
            description="Verifies that input_current is locked at 33170 (3317 mA) to supply full power to the RT9759 2:1 pump."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")

        input_cur = target.read_node("sys/devices/platform/charger/input_current")
        assert input_cur is not None and int(input_cur) >= 30000, f"Expected input_current >= 30000, got {input_cur}"

        chg1 = target.read_node("sys/devices/platform/charger/chg1_current")
        chg2 = target.read_node("sys/devices/platform/charger/chg2_current")
        assert chg1 is not None and int(chg1) >= 30000, f"chg1_current below threshold: {chg1}"
        assert chg2 is not None and int(chg2) >= 30000, f"chg2_current below threshold: {chg2}"

        return "Maximum adapter input current locked at >= 30000 (33170 mA max)", {
            "input_current": input_cur,
            "chg1_current": chg1,
            "chg2_current": chg2
        }


class TestR3PumpCurrentLimitLock(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F3-03",
            tier=1,
            feature="R3_ContinuousPumpCurrent",
            title="RT9759 sc_ibat_limit Register Enforcement (>= 8000 mA)",
            authoritative_source="PROJECT.md § Interface Contracts line 70 & explorer_survey_3/handoff.md § 1.1 line 27",
            description="Verifies that sc_ibat_limit is locked at >= 8000 (or 12000) mA to prevent pump output current clamping."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")

        sc_limit = target.read_node("sys/devices/platform/charger/sc_ibat_limit")
        assert sc_limit is not None and int(sc_limit) >= 8000, f"Expected sc_ibat_limit >= 8000, got {sc_limit}"

        return "RT9759 sc_ibat_limit locked at >= 8000 mA", {
            "sc_ibat_limit": sc_limit
        }


class TestR3NetBatteryCurrentDelivery(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F3-04",
            tier=1,
            feature="R3_ContinuousPumpCurrent",
            title="Net Battery Current Delivery (> 6,000,000 uA in CC Phase)",
            authoritative_source="ORIGINAL_REQUEST.md § Acceptance Criteria § 27 & explorer_survey_3/handoff.md § 1.1 line 21",
            description="Verifies that net charging current at /sys/class/power_supply/battery/current_now exceeds 6,000,000 uA (>6.0 A)."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")

        cur_now = target.read_node("sys/class/power_supply/battery/current_now")
        assert cur_now is not None, "current_now node not found"
        raw_cur = int(cur_now)
        assert raw_cur >= 6000000, f"Expected current_now >= 6,000,000 uA (>6.0 A), got {raw_cur} uA ({raw_cur / 1000000:.3f} A)"

        return f"Net battery current delivered: {raw_cur} uA ({raw_cur / 1000000:.3f} A) > 6,000,000 uA", {
            "current_now_uA": raw_cur,
            "current_now_A": raw_cur / 1000000.0
        }


class TestR3PumpDoublingRatio(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F3-05",
            tier=1,
            feature="R3_ContinuousPumpCurrent",
            title="2:1 Switched-Capacitor Doubling Ratio Verification",
            authoritative_source="explorer_survey_3/handoff.md § 1.2 lines 56-57 & PROJECT.md § Architecture line 29",
            description="Verifies the mathematical physical relationship: Ibat ≈ 2 * Ibus * 0.97 efficiency."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")

        ibus = target.read_node("sys/bus/i2c/drivers/rt9759/5-0066/Ibus")
        ibat = target.read_node("sys/bus/i2c/drivers/rt9759/5-0066/Ibat")
        assert ibus is not None and ibat is not None, "RT9759 Ibus or Ibat node missing"

        ibus_val = int(ibus)
        ibat_val = int(ibat)

        # Expected doubling ratio: 2 * ibus * ~0.95 to 1.00
        ratio = ibat_val / float(ibus_val) if ibus_val > 0 else 0.0
        assert 1.85 <= ratio <= 2.05, f"Expected 2:1 pump current ratio ~1.94, got {ratio:.3f} (Ibus={ibus_val}, Ibat={ibat_val})"

        return f"2:1 switched-capacitor doubling ratio verified: {ratio:.3f}x (Ibus={ibus_val} uA, Ibat={ibat_val} uA)", {
            "Ibus_uA": ibus_val,
            "Ibat_uA": ibat_val,
            "measured_ratio": ratio
        }


class TestR3HighCurrentSustain(E2ETestCase):
    def __init__(self):
        super().__init__(
            test_id="TC-T1-F3-06",
            tier=1,
            feature="R3_ContinuousPumpCurrent",
            title="Continuous High Current Telemetry Sampling Window",
            authoritative_source="ORIGINAL_REQUEST.md § R3 (Sustained Net Current >6.0A)",
            description="Samples charging current continuously across multiple ticks to ensure no thermal drift or stepping occurred."
        )

    def execute(self, target: BaseTarget) -> (str, Dict[str, Any]):
        target.execute_command("lynxd charging mode hyper")

        samples = []
        for _ in range(5):
            time.sleep(0.05)
            c = target.read_node("sys/class/power_supply/battery/current_now")
            assert c is not None and int(c) >= 6000000, f"Current stepped down during continuous sampling: {c} uA"
            samples.append(int(c))

        avg_cur = sum(samples) / len(samples)
        return f"Continuous sampling verified: average current {avg_cur:.0f} uA across {len(samples)} samples", {
            "samples": samples,
            "average_uA": avg_cur
        }


def get_r3_test_cases() -> List[E2ETestCase]:
    return [
        TestR3PumpEngagement(),
        TestR3InputCurrentLock(),
        TestR3PumpCurrentLimitLock(),
        TestR3NetBatteryCurrentDelivery(),
        TestR3PumpDoublingRatio(),
        TestR3HighCurrentSustain(),
    ]


if __name__ == "__main__":
    from framework import MockSandboxTarget
    target = MockSandboxTarget()
    for tc in get_r3_test_cases():
        res = tc.run(target)
        print(f"[{res.status}] {res.test_id}: {res.title} - {res.message}")
    target.cleanup()
