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
    sceneResult: GenshinSkipEngine.SceneSnapshotResult? = null
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
            HudAction(hudString(R.string.hud_stop), onClick = onStop, modifier = Modifier.fillMaxWidth(), isDanger = true)
        }
        if (selectedPage == HudToolPage.Display) {
            HudToolDisplay(rates, activeHz, action, onHz, brightnessLocked, onToggleBrightnessLock)
        }
        if (selectedPage == HudToolPage.Overlay) {
            HudExpandableSection(
                title = hudString(R.string.hud_mini_title),
                detail = hudString(R.string.hud_selected_count, prefs.selected.size),
                initiallyOpen = false
            ) {
                HudMetricSelection(prefs, store, showPin = false)
            }

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
        if (selectedPage == HudToolPage.Automation) {
            HudAutomationControls(
                prefs = prefs,
                busy = action.busy,
                isSkipping = isSkipping,
                isPaused = isPaused,
                isTappingLive = isTappingLive,
                onEnabled = onToggleAutoSkip,
                onSpeed = onSetAutoSkipSpeed,
                onChoice = onSetAutoSkipChoiceBottom
            )

            HudSceneDiagnostics(prefs.autoSkipEnabled, isPaused, isTappingLive, onSnapshotScene, sceneResult)
        }
        if (selectedPage == HudToolPage.Timer) {
            HudToolTimer(timerRemainingSec, timerActivePreset) { seconds ->
                timerActivePreset = seconds
                timerRemainingSec = seconds
            }
        }
    }
}
