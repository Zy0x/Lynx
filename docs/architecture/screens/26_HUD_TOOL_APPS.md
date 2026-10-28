# HUD Tools: Applications

## 1. Identity and structure

Tools → Applications opens the existing `HudAppDock` and `HudAppManager` in their own scrolling page. This removes the dock/management list from the home telemetry/action stack. Back returns to the Tools menu; the main rail remains fixed.

## 2. State and flow

The service supplies the installed dock applications. The page reads current HUD preferences and uses the existing preference store for selection. Its scroll position is separate from other Tools pages. Launch delegates to the existing callback after closing the drawer.

## 3. Controls and defaults

Installed-app selection, persisted dock order and explicit empty-dock behavior are preserved. `HudDockPolicy` remains the migration/default source; no packages are newly selected by this change.

## 4. Hardware mapping

Package discovery and the existing floating-app backend own launch behavior. No sysfs/procfs controls or vendor overlays are added. See `12_LYNX_FREEFORM_ENGINE.md` for backend boundaries.

## 5. Safety and verification

Entering the destination does not launch an app or request a root hardware change. Unsupported launches continue to report through the existing controller. Application launch, keyboard interaction and physical rendering are outside the local-only validation performed for this task.
