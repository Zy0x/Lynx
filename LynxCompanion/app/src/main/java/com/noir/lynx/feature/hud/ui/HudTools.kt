package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noir.lynx.R
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.autoskip.GenshinSkipEngine
import com.noir.lynx.feature.hud.data.*
import kotlinx.coroutines.delay

@Composable
internal fun HudTools(
    selectedPage: HudToolPage?,
    onSelectPage: (HudToolPage) -> Unit,
    onApps: () -> Unit,
    prefs: HudPreferences,
    store: HudPreferencesStore,
    rates: List<Float>,
    activeHz: HudReading,
    action: HudActionState,
    onHz: (Float) -> Unit,
    onStop: () -> Unit,
    brightnessLocked: Boolean = false,
    onToggleBrightnessLock: (Boolean) -> Unit = {},
    onToggleAutoSkip: (Boolean) -> Unit = { store.setAutoSkipEnabled(it) },
    onSetAutoSkipSpeed: (Long) -> Unit = { store.setAutoSkipSpeed(it) },
    onSetAutoSkipChoiceBottom: (Boolean) -> Unit = { store.setAutoSkipChoiceBottom(it) },
    isSkipping: Boolean = false,
    isPaused: Boolean = false,
    isTappingLive: Boolean = false,
    onSnapshotScene: () -> Unit = {},
    sceneResult: GenshinSkipEngine.SceneSnapshotResult? = null,
    snapshot: HudSnapshot = HudSnapshot(),
    bypassStatus: HudBypassStatus = HudBypassStatus(),
    onToggleBypass: (Boolean) -> Unit = {},
    onRefreshBypass: () -> Unit = {},
    onProbeNetwork: suspend () -> HudNetworkSample = { HudNetworkSample() },
    touchStatus: HudTouchStatus = HudTouchStatus(),
    shieldStatus: HudShieldStatus = HudShieldStatus(),
    onToggleTouchBoost: (Boolean) -> Unit = {},
    onToggleHeadsUpShield: (Boolean) -> Unit = {},
    onSelectFocusLevel: (Int) -> Unit = {}
) {
    var timerRemainingSec by rememberSaveable { mutableStateOf(0) }
    var timerActivePreset by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(timerRemainingSec > 0) {
        while (timerRemainingSec > 0) {
            delay(1_000L)
            timerRemainingSec = (timerRemainingSec - 1).coerceAtLeast(0)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selectedPage == null) {
            HudToolNavigation(onSelectPage, onApps)
            HudAction(
                label = hudString(R.string.hud_stop),
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                isDanger = true
            )
        } else {
            when (selectedPage) {
                // 🎮 Alat Bermain (Gaming Add-ins)
                HudToolPage.GameShift -> HudGameShiftControls()
                HudToolPage.Focus -> HudFocusPage(
                    shieldStatus = shieldStatus,
                    busy = action.busy,
                    onToggleHeadsUpShield = onToggleHeadsUpShield,
                    onSelectFocusLevel = onSelectFocusLevel
                )
                HudToolPage.Touch -> HudTouchPage(
                    touchStatus = touchStatus,
                    busy = action.busy,
                    onToggleTouchBoost = onToggleTouchBoost
                )
                HudToolPage.Network -> HudNetworkPage(onProbeNetwork = onProbeNetwork)
                HudToolPage.Bypass -> HudBypassPage(
                    snapshot = snapshot,
                    bypassStatus = bypassStatus,
                    busy = action.busy,
                    onToggleBypass = onToggleBypass,
                    onRefreshBypass = onRefreshBypass
                )
                HudToolPage.Combo -> HudComboControls()
                HudToolPage.Recorder -> HudRecorderControls()
                HudToolPage.Console -> HudConsoleControls(
                    brightnessLocked = brightnessLocked,
                    onBrightness = onToggleBrightnessLock
                )
                HudToolPage.Session -> HudSessionControls(snapshot = snapshot)

                // ⚙️ Alat Sistem (System & Display Tools)
                HudToolPage.Display -> {
                    HudToolDisplay(rates, activeHz, action, onHz, brightnessLocked, onToggleBrightnessLock)
                }
                HudToolPage.Overlay -> {
                    HudSubpages(listOf(R.string.hud_mini_title, R.string.hud_floating_short)) { section ->
                        if (section == 0) {
                            HudExpandableSection(
                                title = hudString(R.string.hud_mini_title),
                                detail = hudString(R.string.hud_selected_count, prefs.selected.size),
                                initiallyOpen = false
                            ) {
                                HudMetricSelection(prefs, store, showPin = false)
                            }
                        } else {
                            HudExpandableSection(
                                title = hudString(R.string.hud_floating_short),
                                detail = hudString(
                                    if (prefs.floatingEngine == "standalone") R.string.floating_engine_root else R.string.floating_engine_rom
                                ),
                                initiallyOpen = false
                            ) {
                                HudFloatingSettings(prefs, store)
                            }
                        }
                    }
                }
                HudToolPage.Automation -> {
                    HudSubpages(listOf(R.string.hud_detail_controls, R.string.hud_detail_options, R.string.hud_detail_diagnostics)) { section ->
                        if (section != 2) {
                            HudAutomationControls(
                                showOptions = section == 1,
                                prefs = prefs,
                                busy = action.busy,
                                isSkipping = isSkipping,
                                isPaused = isPaused,
                                isTappingLive = isTappingLive,
                                onEnabled = onToggleAutoSkip,
                                onSpeed = onSetAutoSkipSpeed,
                                onChoice = onSetAutoSkipChoiceBottom
                            )
                        } else {
                            HudSceneDiagnostics(prefs.autoSkipEnabled, isPaused, isTappingLive, onSnapshotScene, sceneResult)
                        }
                    }
                }
                HudToolPage.Timer -> {
                    HudToolTimer(timerRemainingSec, timerActivePreset) { seconds ->
                        timerActivePreset = seconds
                        timerRemainingSec = seconds
                    }
                }
            }
        }
    }
}
