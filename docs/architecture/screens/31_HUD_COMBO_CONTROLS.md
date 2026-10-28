# HUD Combo controls

## 1. Identity and structure

`HudComboControls.kt` replaces the former 500-line Combo body in `HudGamingCards`. A horizontal slot strip uses 48 dp minimum targets. Controls / Settings / Share compose separate bodies; recording/editing and start/stop actions are full-width. Settings and Share are documented separately.

## 2. State and flow

The existing manager's combo, active-run and foreground flows plus root availability remain authoritative. Selection stores a slot ID, falling back to the first existing slot. Child UI state is keyed by slot. Navigation does not record or execute touches.

## 3. Parameters and defaults

Controls opens first. Add retains the five-slot UI limit. Recorded-point count and actual foreground package identify the selected sequence. Existing persisted names and sequences are retained. No combo is added merely by opening the page.

## 4. Execution boundary

Only the existing `setEditing`, `executeCombo`, `stopExecution`, add/update/remove APIs are invoked by explicit actions. Package, screen, geometry and bounded execution guards remain in the manager. No input backend or timing algorithm changes.

## 5. Safety and verification

Record/edit is disabled while any sequence runs. Start is available only for an idle runner, root and a nonempty sequence; a running selected slot retains Stop. Another slot cannot start over an active run through this UI. Tests/build are local; no recording or game interaction is executed.
