# HUD Combo sharing

## 1. Identity and structure

`HudComboShare.kt` places Export, a bounded TouchShare input field and Import vertically. It replaces the crowded export/import row and expanding import form. The draft survives internal tab changes through `HudSubpages` saveable state.

## 2. State and flow

The page receives the selected combo and existing update callback. Export copies the existing serialized code to Android's clipboard. Import calls `TouchShareParser.importFromString` with the selected slot ID. Result messages use the current localized resources.

## 3. Parameters and defaults

The input starts empty, uses at most four visible lines and scrolls internally for longer codes. Export requires recorded points; Import requires nonblank input and an idle runner. No format or parser bounds are changed.

## 4. Data boundary

The existing LYNX-COMBO serialization and parser remain authoritative. Import replaces the selected slot through the manager, preserving its destination ID. No shell, file or network operation is added.

## 5. Safety and verification

Invalid input reports the existing failure message. Import is disabled while a sequence runs. Export and import require explicit actions and never execute a sequence. Existing parser fixtures and Release compilation are local; clipboard interactions are not physically tested.
