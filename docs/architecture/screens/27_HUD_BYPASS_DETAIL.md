# HUD Bypass detail

## 1. Identity and structure

`HudBypassPage.kt` replaces the old body in `HudLab`. Compact Controls / Readings tabs compose one group at a time. Controls contains the master switch, threshold and thermal-cut status; Readings contains an adaptive current/temperature grid and the existing capability note.

## 2. State and flow

Inputs remain `HudBypassStatus`, `HudSnapshot` and the existing busy flag/callbacks. Capability refresh runs on entry to this detail, rather than entry to unrelated Boost categories. Tab selection never toggles bypass. Snapshot collection is unchanged.

## 3. Parameters and defaults

Controls opens first. The threshold retains its existing 20–90 percent range and existing session default/readback. Current and battery temperature use snapshot units. Statistics reflow into at most two columns, with unavailable values shown as an em dash.

## 4. Hardware mapping

The existing bypass session owns recognized writable battery bypass/charge-bypass interfaces, capability probing, threshold behavior and restoration. This presentation change adds no node, charging-current target, thermal limit or write path.

## 5. Boundaries and validation

The master is enabled only for READY capability and an idle action controller. Readings are distinct from proof of charger-to-SoC power flow. Release build/tests are local; no physical bypass action is exercised in this task.
