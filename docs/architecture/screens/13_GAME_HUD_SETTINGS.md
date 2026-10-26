# Game HUD settings

## 1. Screen identity and components

`feature/hud/ui/GameHudSubscreen.kt` is a compact native Compose settings screen, using the HUD's OLED theme without changing the global card design system. It contains a master HUD control plus expandable display format, floating applications and Mini HUD sections. Headers, labels and choices use at least 12sp text and 48dp action targets. Padding/gaps replace the former large cards, repeated subtitles and icon containers. Advanced options are disclosed without resetting preferences.

`HudFloatingSettings` is shared with the sidebar's Tools page. `HudMetricSelection` groups metrics by frame, processor, memory/temperature and battery; each group shows its selected count. Preview is optional and limited to four placeholder entries, clearly labelled as preview. All eighteen real metric toggles remain available. `HudAdaptiveGrid` measures labels and chooses columns; large fonts stack choices rather than shrinking text. The six classic style selectors retain IDs 1–6 and do not alter their overlay designs.

`HudAppManager` adds a bounded launcher-app list, reachable through the dock's Add action even when the dock is empty, or through floating settings in Companion/Tools. Rows select membership and expose per-app size/reorder controls; the catalog is sorted by label with selected apps first. Every action has a 48dp touch target and every label is localized. The list has its own scroll inside the bounded panel; it does not create an external dialog or full-screen overlay.

## 2. Reactive state and lifecycle

`LynxUiState` provides active service status, mode and classic style. Existing ViewModel callbacks toggle the service and set mode/style. `HudPreferencesStore.changes` synchronizes floating enable/engine/size, pin and metric selections with the sidebar. No additional preference file is introduced. Expand/collapse states are session saveable.

Overlay permission is re-read on lifecycle resume, so returning from Android permission settings updates the page without requiring a service toggle. Reads use the Android permission API; Compose does not run a root shell, poll hardware or read module profile files.

`HudAppCatalog` enumerates enabled `MAIN/LAUNCHER` apps for the current Android user through PackageManager. Package add/remove/change broadcasts refresh it. `HudController` combines that catalog with the shared preference flow; selection/ordering changes immediately update the dock. Lynx excludes its own launcher. An installed/launchable app is selectable, not a promise that the app or ROM allows virtual displays. Other Android users/work-profile data are not cloned or silently selected.

## 3. Configuration

Sidebar mode remains 0; classic remains 1. The master control uses `toggleGameHud`. Floating engine keys remain `standalone` and `system_freeform`; the former delegates to the reusable `:freeform` library, the latter to explicit ROM/LMO integration. Size presets remain compact 55×50%, standard 70×65%, and large 85×80%. Custom keeps the current dimensions when selected, then exposes width 35–95% and height 35–90%, in the existing 5-point slider steps. Existing stored sizes and engine selection are preserved.

Mini metrics keep their stable keys. Reset restores only FPS/frame-time selection; pin and floating configuration remain unchanged. The compact UI uses metric catalog names/units, including battery current/power rather than incorrectly labelling them charger input. Floating engine explanations remain available in an expandable information section.

`hud_dock_packages_v1` stores an ordered JSON array; absence uses available legacy favorites, whereas an explicit empty array stays empty. Uninstalled selections are retained in storage and filtered from the visible dock, so reinstalling can recover their positions. `hud_app_sizes_v1` stores width/height overrides keyed by package (35–95% / 35–90%); disabling an override restores the global size. Removing a dock item does not erase its size or any app data. Both standalone and ROM backends receive the resolved per-app dimensions.

## 4. Hardware boundary

This screen writes existing UI preferences and delegates explicit actions through the existing ViewModel/controller. It does not write sysfs, install a root module, change thermal protections, or enable global freeform. Root display/input compatibility belongs to `:freeform`; telemetry source/statistics belong to the HUD repository and native collector. Display Hz controls remain in sidebar Tools and retain supported-mode and readback checks.

## 5. Verification and error boundaries

Grid policy tests cover small/scaled measured widths. App and library catalogs are independently validated in default/id/en; format placeholders are exercised in Release tests. Permission absence exposes the Android permission action. Actual service state drives the toggle. Missing profile/Hz readings remain unavailable instead of synthesizing Balance or 60Hz.

Release build and parser/policy fixtures do not establish pixel-perfect rendering, touch/scroll behavior or complete Android 10–17 compatibility. Physical X698 checks remain necessary; Redmi keeps its authorized install-only/no-launch boundary. Full results and artifact checksums are recorded in the floating audit.

The recorder uses `HudRecorderMasterCard` and the shared `HudRecorderControls` used in the Add-in subpage. Resolution and start/stop remain visible; audio, bitrate, overlay privacy and codec choices expand under Recording settings. The previous target-FPS picker was removed because the backend never consumed the saved preference. Existing preferences and backend APIs remain compatible. A recorder created for this screen is disposed when the screen leaves; a recorder borrowed from the HUD service is not closed by the screen.
