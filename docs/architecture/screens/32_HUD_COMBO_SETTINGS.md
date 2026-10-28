# HUD Combo settings

## 1. Identity and structure

`HudComboSettings.kt` provides full-width rename/save controls, the floating-trigger toggle, a measured execution-mode grid, optional loop-delay slider and Delete. It is reached through the Combo Settings tab.

## 2. State and flow

The selected `MacroCombo` is the source of truth. The name draft is saveable by slot/name. Explicit Save commits a trimmed nonempty name through the existing manager; unsaved text does not rename the slot.

## 3. Parameters and defaults

Name editing retains the existing 20-character input bound. Modes remain ONE_SHOT and HOLD_LOOP. The existing slider UI range remains 50–1000 ms; imported values outside that slider range remain displayed as their actual saved value until the user moves the bounded slider. Floating-trigger and delay defaults are unchanged.

## 4. Persistence boundary

Updates use `HudComboManager.updateCombo`; deletion uses its existing remove API. This page never writes kernel nodes or sends game input. Stored coordinate and sequence formats remain unchanged.

## 5. Safety and verification

Mutation controls are disabled during an active sequence. Delete is shown only when more than one slot exists. Save rejects blank/unchanged names. Local Release validation does not claim physical macro execution testing.
