## 2026-10-14T15:13:36Z
You are worker_m2 (Charge Pump & Charging Worker).
Your working directory is: e:\Data\GitHub\Lynx\.agents\teamwork\worker_m2

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

MANDATORY FIRST STEP:
Read the authoritative user request at:
e:\Data\GitHub\Lynx\.agents\teamwork\ORIGINAL_REQUEST.md
and the master project spec at:
e:\Data\GitHub\Lynx\.agents\teamwork\PROJECT.md

Read the Explorer handoff reports before writing code:
- e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m2_1\handoff.md
- e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m2_2\handoff.md
- e:\Data\GitHub\Lynx\.agents\teamwork\explorer_m2_3\handoff.md

Exclusive Write Ownership:
You have exclusive write ownership over:
- `native/lynxd/src/hardware/charging.rs`
- `native/lynxd/src/daemon/runner.rs`
- `core/Charging-Controller.sh`

Implementation Scope for Milestone 2 (Requirement R3):
1. In `native/lynxd/src/hardware/charging.rs`:
   - Expand `invariants_intact` in `apply_from_config()` to verify the complete R3 tuple on MediaTek:
     `bn_test == "1" && enable_sc == "1" && in_cur >= 30000 && sc_limit >= 8000 && chg1 >= 30000 && chg2 >= 30000 && pe20 == "1" && pe40 == "1"`
     If any node drifts or drops below these thresholds, re-assert `apply_hyper_mode()`.
   - Implement CC to CV Smart Tapering at $\ge 80\%$ SoC (or $V_{BAT} \ge 4470\text{ mV}$) in `apply_from_config()` when `cfg.smart_tapering_enabled` is true:
     * 80% to 94% SoC: Step down to 3500 mA (`state=tapering_80`), preserve $V_{BAT} \le 4.50\text{V}$.
     * 95% to 99% SoC: Step down to 750 mA (`state=tapering_95`).
     * 100%: AutoCut engages bypass charging.
   - Utilize `target_soc` in `apply_extreme_charging()` to prevent over-voltage when entering hyper mode at high SoC.
   - Add unit tests verifying 2:1 pump math and CC/CV SoC thresholds.
2. In `native/lynxd/src/daemon/runner.rs`:
   - Add edge-triggered break in the 500ms chunk sleep loop when `currently_plugged != is_plugged` so the daemon immediately shifts from 20s screen-off sleep to 1s high-frequency polling on cable connection.
3. In `core/Charging-Controller.sh`:
   - Implement direct sysfs fallback commands for `hyper` and `disconnect` when `$LYNXD` is not executable.
4. Build & Verify:
   - Run `cargo check` and `cargo test --target x86_64-pc-windows-msvc -- hardware::charging` inside `native/lynxd`.
   - Run `python tests/e2e/run_e2e_tests.py --tier 1` and `python tests/e2e/run_e2e_tests.py` to ensure all 41 E2E tests pass (100% pass rate).

Deliverables:
- Write comprehensive handoff report to `e:\Data\GitHub\Lynx\.agents\teamwork\worker_m2\handoff.md` with:
  * Observation
  * Logic Chain
  * Caveats
  * Conclusion
  * Verification Method (commands and exact test outputs)
- Send completion message to your orchestrator.
