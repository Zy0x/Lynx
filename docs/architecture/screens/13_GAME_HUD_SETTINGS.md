# Game HUD settings

## 1. Screen identity and components

`feature/hud/ui/GameHudSubscreen.kt` is a compact native Compose settings screen, using the HUD's OLED theme without changing the global card design system. It contains a master HUD control plus expandable display format, floating applications and Mini HUD sections. Headers, labels and choices use at least 12sp text and 48dp action targets. Padding/gaps replace the former large cards, repeated subtitles and icon containers. Advanced options are disclosed without resetting preferences.

`HudFloatingSettings` is shared with the sidebar's Tools page. `HudMetricSelection` groups metrics by frame, processor, memory/temperature and battery; each group shows its selected count. Preview is optional and limited to four placeholder entries, clearly labelled as preview. All eighteen real metric toggles remain available. `HudAdaptiveGrid` measures labels and chooses columns; large fonts stack choices rather than shrinking text. The six classic style selectors retain IDs 1–6 and do not alter their overlay designs.

## 2. Reactive state and lifecycle

`LynxUiState` provides active service status, mode and classic style. Existing ViewModel callbacks toggle the service and set mode/style. `HudPreferencesStore.changes` synchronizes floating enable/engine/size, pin and metric selections with the sidebar. No additional preference file is introduced. Expand/collapse states are session saveable.

Overlay permission is re-read on lifecycle resume, so returning from Android permission settings updates the page without requiring a service toggle. Reads use the Android permission API; Compose does not run a root shell, poll hardware or read module profile files.

## 3. Configuration

Sidebar mode remains 0; classic remains 1. The master control uses `toggleGameHud`. Floating engine keys remain `standalone` and `system_freeform`; the former delegates to the reusable `:freeform` library, the latter to explicit ROM/LMO integration. Size presets remain compact 55×50%, standard 70×65%, and large 85×80%. Custom keeps the current dimensions when selected, then exposes width 35–95% and height 35–90%, in the existing 5-point slider steps. Existing stored sizes and engine selection are preserved.

Mini metrics keep their stable keys. Reset restores only FPS/frame-time selection; pin and floating configuration remain unchanged. The compact UI uses metric catalog names/units, including battery current/power rather than incorrectly labelling them charger input. Floating engine explanations remain available in an expandable information section.

## 4. Hardware boundary

This screen writes existing UI preferences and delegates explicit actions through the existing ViewModel/controller. It does not write sysfs, install a root module, change thermal protections, or enable global freeform. Root display/input compatibility belongs to `:freeform`; telemetry source/statistics belong to the HUD repository and native collector. Display Hz controls remain in sidebar Tools and retain supported-mode and readback checks.

## 5. Verification and error boundaries

Grid policy tests cover small/scaled measured widths. App and library catalogs are independently validated in default/id/en; format placeholders are exercised in Release tests. Permission absence exposes the Android permission action. Actual service state drives the toggle. Missing profile/Hz readings remain unavailable instead of synthesizing Balance or 60Hz.

Release build and parser/policy fixtures do not establish pixel-perfect rendering, touch/scroll behavior or complete Android 10–17 compatibility. Physical X698 checks remain necessary; Redmi keeps its authorized install-only/no-launch boundary. Full results and artifact checksums are recorded in the floating audit.
