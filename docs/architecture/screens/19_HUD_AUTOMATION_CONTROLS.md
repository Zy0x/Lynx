# HUD automation and compact shortcuts

## 1. Identity and UI

`HudPanelHeader` owns a consistent adaptive shortcut grid for capture, recording, memory cleanup, FPS meter, Auto-skip and Focus. Labels remain visible and wrap; the grid reduces columns for enlarged fonts. `HudAutomationControls` presents dialogue-assistant availability and enable state, with speed/choice settings disclosed below. `HudSmartTriggerOverlay` uses a 38 dp visual token inside a 48 dp gesture target with state-specific icon and color. It has no continuous idle animation.

## 2. State flow

Existing HUD preferences control whether the assistant is armed. `isSkipping` and `isPaused` distinguish execution from standby. Summary enable and Tools enable update the same preference; the existing controller observes it. The external floating token retains its existing pause/resume and drag handling.

## 3. Parameters

The existing speed choices remain 180/250/380 ms and the existing first/last conversation choice remains unchanged. The primary switch remains visible while detailed configuration starts collapsed. Pinning the FPS meter retains all metric choices and paging. Recording stops remain available while another action is busy.

## 4. Execution boundary

The UI introduces no tapping engine, shell execution or new game package support. Existing foreground/game guards, screen-off handling and custom-sequence mutual exclusion remain in the controller. Memory cleanup and capture retain their existing callbacks; a status tint is not a promise of better game performance.

## 5. Errors and accessibility

All new labels and the floating token's status/gesture description exist in default, Indonesian and English catalogs. Standby is explicitly distinct from active skipping. Missing hardware metrics remain unavailable. Physical UI checks stay within foreground Lynx; replaying dialogue automation inside a game is outside this UI validation.
