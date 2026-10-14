#!/usr/bin/env python3
"""
Lynx Universal Fast Charging Subsystem - Master E2E Automated Test Runner
Executes 4-Tier Automated Test Suite for Requirements R1, R2, R3, R4.
Supports non-interactive CI execution and live on-device ADB testing.
"""

import os
import sys
import time
import argparse
from pathlib import Path
from typing import List

# Setup import path
BASE_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE_DIR))

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

from framework import (
    BaseTarget,
    MockSandboxTarget,
    LiveAdbTarget,
    E2ETestCase,
    TestSuiteResult,
    TestCaseResult
)

# Import test case loaders
from tier1_feature_coverage.test_r1_screen_on_throttle import get_r1_test_cases
from tier1_feature_coverage.test_r2_thermal_neutralization import get_r2_test_cases
from tier1_feature_coverage.test_r3_pump_current import get_r3_test_cases
from tier1_feature_coverage.test_r4_disconnect_failsafe import get_r4_test_cases
from tier2_boundaries.test_boundary_corner_cases import get_boundary_test_cases
from tier3_cross_feature.test_cross_feature_matrix import get_cross_feature_test_cases
from tier4_real_world.test_real_world_scenarios import get_real_world_test_cases


# Terminal ANSI Colors
COLOR_RESET = "\033[0m"
COLOR_BOLD = "\033[1m"
COLOR_GREEN = "\033[32m"
COLOR_RED = "\033[31m"
COLOR_YELLOW = "\033[33m"
COLOR_CYAN = "\033[36m"
COLOR_DIM = "\033[2m"


def collect_all_test_cases() -> List[E2ETestCase]:
    cases: List[E2ETestCase] = []
    cases.extend(get_r1_test_cases())
    cases.extend(get_r2_test_cases())
    cases.extend(get_r3_test_cases())
    cases.extend(get_r4_test_cases())
    cases.extend(get_boundary_test_cases())
    cases.extend(get_cross_feature_test_cases())
    cases.extend(get_real_world_test_cases())
    return cases


def print_banner(target_name: str, total_tests: int):
    print(f"\n{COLOR_CYAN}{COLOR_BOLD}================================================================================{COLOR_RESET}")
    print(f"{COLOR_CYAN}{COLOR_BOLD}  LYNX UNIVERSAL - CHARGING THROTTLE ELIMINATION E2E TEST SUITE{COLOR_RESET}")
    print(f"{COLOR_CYAN}{COLOR_BOLD}  Requirements: R1 (Screen-On) | R2 (Thermal) | R3 (>6A Pump) | R4 (Disconnect){COLOR_RESET}")
    print(f"{COLOR_CYAN}  Target: {COLOR_BOLD}{target_name}{COLOR_RESET} | Total Test Cases: {COLOR_BOLD}{total_tests}{COLOR_RESET}")
    print(f"{COLOR_CYAN}{COLOR_BOLD}================================================================================{COLOR_RESET}\n")


def print_test_result(r: TestCaseResult, verbose: bool):
    if r.status == "PASS":
        status_tag = f"{COLOR_GREEN}{COLOR_BOLD}[PASS]{COLOR_RESET}"
    elif r.status == "FAIL":
        status_tag = f"{COLOR_RED}{COLOR_BOLD}[FAIL]{COLOR_RESET}"
    elif r.status == "SKIP":
        status_tag = f"{COLOR_YELLOW}{COLOR_BOLD}[SKIP]{COLOR_RESET}"
    else:
        status_tag = f"{COLOR_RED}{COLOR_BOLD}[ERROR]{COLOR_RESET}"

    time_str = f"{COLOR_DIM}({r.duration_s * 1000:.1f}ms){COLOR_RESET}"
    print(f"  {status_tag} {COLOR_BOLD}{r.test_id}{COLOR_RESET}: {r.title} {time_str}")

    if verbose or r.status in ("FAIL", "ERROR"):
        print(f"         {COLOR_DIM}Source: {r.authoritative_source}{COLOR_RESET}")
        if r.message:
            msg_color = COLOR_GREEN if r.status == "PASS" else COLOR_RED
            print(f"         {msg_color}{r.message}{COLOR_RESET}")
        if r.details:
            print(f"         {COLOR_DIM}Details: {r.details}{COLOR_RESET}")


def run_suite(
    target: BaseTarget,
    target_name: str,
    selected_tier: Optional[int] = None,
    selected_feature: Optional[str] = None,
    verbose: bool = False,
    json_path: Optional[str] = None,
    xml_path: Optional[str] = None,
) -> int:
    all_cases = collect_all_test_cases()

    # Filter
    filtered_cases = []
    for c in all_cases:
        if selected_tier is not None and c.tier != selected_tier:
            continue
        if selected_feature is not None and selected_feature.lower() not in c.feature.lower():
            continue
        filtered_cases.append(c)

    print_banner(target_name, len(filtered_cases))

    suite_result = TestSuiteResult(suite_name="Lynx_Charging_E2E_Suite")
    suite_start = time.perf_counter()

    current_tier = None
    for case in filtered_cases:
        if case.tier != current_tier:
            current_tier = case.tier
            print(f"\n{COLOR_CYAN}--- TIER {current_tier}: {get_tier_name(current_tier)} ---{COLOR_RESET}")

        result = case.run(target)
        suite_result.results.append(result)
        suite_result.total += 1

        if result.status == "PASS":
            suite_result.passed += 1
        elif result.status == "FAIL":
            suite_result.failed += 1
        elif result.status == "SKIP":
            suite_result.skipped += 1
        else:
            suite_result.errors += 1

        print_test_result(result, verbose)

    suite_result.duration_s = time.perf_counter() - suite_start

    # Summary
    print(f"\n{COLOR_CYAN}{COLOR_BOLD}================================================================================{COLOR_RESET}")
    print(f"{COLOR_CYAN}{COLOR_BOLD}  TEST EXECUTION SUMMARY{COLOR_RESET}")
    print(f"{COLOR_CYAN}{COLOR_BOLD}================================================================================{COLOR_RESET}")
    print(f"  Total Tests : {suite_result.total}")
    print(f"  Passed      : {COLOR_GREEN}{suite_result.passed}{COLOR_RESET}")
    print(f"  Failed      : {COLOR_RED if suite_result.failed > 0 else COLOR_RESET}{suite_result.failed}{COLOR_RESET}")
    print(f"  Errors      : {COLOR_RED if suite_result.errors > 0 else COLOR_RESET}{suite_result.errors}{COLOR_RESET}")
    print(f"  Skipped     : {suite_result.skipped}")
    print(f"  Duration    : {suite_result.duration_s:.3f} seconds\n")

    if json_path:
        suite_result.write_json(json_path)
        print(f"  JSON Report saved to: {json_path}")

    if xml_path:
        suite_result.write_junit_xml(xml_path)
        print(f"  JUnit XML saved to  : {xml_path}")

    if suite_result.failed > 0 or suite_result.errors > 0:
        print(f"\n{COLOR_RED}{COLOR_BOLD}  [OVERALL: FAIL] Some tests failed. Inspect diagnostic output above.{COLOR_RESET}\n")
        return 1
    else:
        print(f"\n{COLOR_GREEN}{COLOR_BOLD}  [OVERALL: PASS] 100% of charging throttle elimination tests passed!{COLOR_RESET}\n")
        return 0


def get_tier_name(tier: int) -> str:
    names = {
        1: "Feature Coverage (R1, R2, R3, R4 Isolated Verification)",
        2: "Boundary & Corner Cases (SoC Transitions, Unplug Latency, Thermals)",
        3: "Cross-Feature Combinations (Simultaneous Multi-Stress & Pipelines)",
        4: "Real-World Scenarios (Gaming, 4K Video, Overnight Bypass)",
    }
    return names.get(tier, f"Tier {tier}")


def main():
    parser = argparse.ArgumentParser(description="Lynx Universal Fast Charging E2E Automated Test Runner")
    parser.add_argument("--tier", type=int, choices=[1, 2, 3, 4], help="Run only specified Tier (1-4)")
    parser.add_argument("--feature", type=str, help="Filter test cases by feature name substring (e.g. r1, r2, r3, r4)")
    parser.add_argument("--target", type=str, choices=["mock", "live"], default="mock", help="Execution target (default: mock)")
    parser.add_argument("--serial", type=str, help="ADB device serial/IP for live target (e.g. 192.168.1.13:5555)")
    parser.add_argument("--verbose", "-v", action="store_true", help="Enable verbose test output")
    parser.add_argument("--json-report", type=str, default="test-results/e2e_results.json", help="Path for JSON test report")
    parser.add_argument("--xml-report", type=str, default="test-results/e2e_junit.xml", help="Path for JUnit XML test report")

    args = parser.parse_args()

    # Target initialization
    if args.target == "live":
        try:
            target = LiveAdbTarget(serial=args.serial)
            target_desc = f"Live Android Device (ADB: {args.serial or 'default'})"
        except Exception as e:
            print(f"{COLOR_RED}Error connecting to live ADB device: {e}{COLOR_RESET}")
            print(f"{COLOR_YELLOW}Falling back to Virtual Sysfs Mock Sandbox...{COLOR_RESET}")
            target = MockSandboxTarget()
            target_desc = "Virtual Sysfs Mock Sandbox (Fallback)"
    else:
        target = MockSandboxTarget()
        target_desc = "Virtual Sysfs Mock Sandbox (Hermetic POSIX/Sysfs)"

    try:
        ret = run_suite(
            target=target,
            target_name=target_desc,
            selected_tier=args.tier,
            selected_feature=args.feature,
            verbose=args.verbose,
            json_path=args.json_report,
            xml_path=args.xml_report,
        )
    finally:
        target.cleanup()

    sys.exit(ret)


if __name__ == "__main__":
    main()
