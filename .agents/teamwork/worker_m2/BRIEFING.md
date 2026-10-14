# BRIEFING — 2026-10-14T15:24:00Z

## Mission
Implement Milestone 2 (R3: Charge Pump & 33W Fast Charging Controller) in lynxd Rust daemon and shell fallback, ensuring complete invariant verification, CC/CV Smart Tapering (80%/95%/100%), target_soc safety in apply_extreme_charging, edge-triggered plug detection in runner.rs, and sysfs fallback in Charging-Controller.sh.

## 🔒 My Identity
- Archetype: worker
- Roles: implementer, qa, specialist
- Working directory: e:\Data\GitHub\Lynx\.agents\teamwork\worker_m2
- Original parent: 4a93ea5f-bf3a-41d2-8075-760f372161d2
- Milestone: Milestone 2 (R3: Charge Pump & Charging)

## 🔒 Key Constraints
- Exclusive write ownership over:
  * `native/lynxd/src/hardware/charging.rs`
  * `native/lynxd/src/daemon/runner.rs`
  * `core/Charging-Controller.sh`
- DO NOT hardcode test results or create facade implementations.
- Full real logic maintaining actual state.
- All cargo unit tests and E2E tests must pass (100% pass rate).

## Current Parent
- Conversation ID: 4a93ea5f-bf3a-41d2-8075-760f372161d2
- Updated: 2026-10-14T15:24:00Z

## Task Summary
- **What to build**:
  1. Expand `invariants_intact` in `charging.rs` to check MTK R3 tuple (`bn_test`, `enable_sc`, `in_cur >= 30000`, `sc_limit >= 8000`, `chg1 >= 30000`, `chg2 >= 30000`, `pe20`, `pe40`).
  2. Implement CC/CV smart tapering at $\ge 80\%$ SoC (or $V_{BAT} \ge 4470\text{ mV}$):
     - 80% to 94%: step down to 3500 mA (`tapering_80`), preserve $V_{BAT} \le 4.50\text{ V}$.
     - 95% to 99%: step down to 750 mA (`tapering_95`).
     - 100%: AutoCut engages bypass charging.
  3. Utilize `target_soc` in `apply_extreme_charging()` to prevent over-voltage when entering hyper mode at high SoC.
  4. Add unit tests for 2:1 pump math and CC/CV SoC thresholds.
  5. In `runner.rs`, add edge-triggered break in the 500ms sleep loop on plug state transition (`currently_plugged != is_plugged`).
  6. In `core/Charging-Controller.sh`, implement direct sysfs fallback commands for `hyper` and `disconnect` when `$LYNXD` is not executable.
  7. Run cargo tests and E2E tests.
- **Success criteria**: 100% test pass rate on unit and E2E tests.
- **Interface contracts**: `PROJECT.md` and `charging.rs`.

## Key Decisions Made
- Added pure functions `calculate_pump_output_ma`, `calculate_net_battery_current_ua`, and `determine_taper_state` to `ChargingRegulator` for deterministic testing and reuse across `apply_from_config()` and `apply_extreme_charging()`.
- Implemented edge-triggered plug state break in `runner.rs` so that connecting a charger during 20s screen-off sleep immediately transitions to 1s fast polling.
- Provided complete standalone sysfs fallback routines (`fallback_hyper` and `fallback_disconnect`) with permission locking and restoration in `core/Charging-Controller.sh`.

## Artifact Index
- `.agents/teamwork/worker_m2/DISPATCH.md` — Assignment instructions
- `.agents/teamwork/worker_m2/BRIEFING.md` — Situational awareness and state
- `.agents/teamwork/worker_m2/progress.md` — Liveness and step tracking
- `.agents/teamwork/worker_m2/handoff.md` — Final deliverable report

## Change Tracker
- **Files modified**:
  * `native/lynxd/src/hardware/charging.rs`: Added pump math helpers, `determine_taper_state`, `target_soc` clamping, Tier 6 Smart Tapering in `apply_from_config`, expanded MTK `invariants_intact` tuple, and added unit tests.
  * `native/lynxd/src/daemon/runner.rs`: Added `currently_plugged != is_plugged` edge-triggered break in 500ms chunk loop.
  * `core/Charging-Controller.sh`: Added `fallback_hyper` and `fallback_disconnect` standalone sysfs write routines with permission management.
- **Build status**: `cargo test` passes 50/50 unit tests; E2E suite passes 41/41 tests (100% pass rate).
- **Pending issues**: None

## Quality Status
- **Build/test result**: PASS (cargo test: 50 passed; E2E tests: 41/41 passed, duration 1.318s)
- **Lint status**: Clean (no new compiler warnings)
- **Tests added/modified**: `test_rt9759_pump_math_and_net_current`, `test_cc_cv_soc_boundaries_and_tapering_thresholds`

## Loaded Skills
- None
