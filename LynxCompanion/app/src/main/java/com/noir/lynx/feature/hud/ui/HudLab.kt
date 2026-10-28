package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.noir.lynx.feature.hud.data.*

@Composable
internal fun HudLab(
    snapshot: HudSnapshot,
    bypassStatus: HudBypassStatus,
    busy: Boolean,
    onToggleBypass: (Boolean) -> Unit,
    onRefreshBypass: () -> Unit,
    onProbeNetwork: suspend () -> HudNetworkSample,
    touchStatus: HudTouchStatus = HudTouchStatus(),
    shieldStatus: HudShieldStatus = HudShieldStatus(),
    onToggleTouchBoost: (Boolean) -> Unit = {},
    onToggleHeadsUpShield: (Boolean) -> Unit = {},
    onSelectFocusLevel: (Int) -> Unit = {},
    selectedAddin: Int = -1,
    onSelectAddin: (Int) -> Unit = {},
    brightnessLocked: Boolean = false,
    onToggleBrightnessLock: (Boolean) -> Unit = {}
) {
    when (selectedAddin) {
        0 -> HudFocusPage(shieldStatus, busy, onToggleHeadsUpShield, onSelectFocusLevel)
        1 -> HudBypassPage(snapshot, bypassStatus, busy, onToggleBypass, onRefreshBypass)
        2 -> HudTouchPage(touchStatus, busy, onToggleTouchBoost)
        3 -> HudNetworkPage(onProbeNetwork)
        4 -> HudComboControls()
        5 -> HudRecorderControls()
        6 -> HudConsoleControls(brightnessLocked, onToggleBrightnessLock)
        7 -> HudSessionControls(snapshot)
        8 -> HudGameShiftControls()
        else -> HudAddinNavigation(onSelectAddin)
    }
}
