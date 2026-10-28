package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noir.lynx.R
import com.noir.lynx.feature.hud.*
import com.noir.lynx.feature.hud.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HudSidebar(
    snapshot: HudSnapshot,
    viewport: HudViewport,
    open: Boolean,
    preferences: HudPreferencesStore,
    action: HudActionState,
    apps: List<HudApp>,
    rates: List<Float>,
    onOpen: (Boolean) -> Unit,
    onDrag: (Float) -> Unit,
    onProfile: (String) -> Unit,
    onHz: (Float) -> Unit,
    onCapture: () -> Unit,
    onApp: (HudApp) -> Unit,
    onStop: () -> Unit,
    onBoostRam: () -> Unit = {},
    dockRight: Boolean = false,
    onDrag2D: ((Float, Float) -> Unit)? = null,
    onDragEnd: () -> Unit = {},
    bypassStatus: HudBypassStatus = HudBypassStatus(),
    focusActive: Boolean = false,
    onToggleFocus: () -> Unit = {},
    onToggleBypass: (Boolean) -> Unit = {},
    onRefreshBypass: () -> Unit = {},
    onProbeNetwork: suspend () -> HudNetworkSample = { HudNetworkSample() },
    touchStatus: HudTouchStatus = HudTouchStatus(),
    shieldStatus: HudShieldStatus = HudShieldStatus(),
    brightnessLocked: Boolean = false,
    onToggleTouchBoost: (Boolean) -> Unit = {},
    onToggleHeadsUpShield: (Boolean) -> Unit = {},
    onSelectFocusLevel: (Int) -> Unit = {},
    onToggleBrightnessLock: (Boolean) -> Unit = {},
    isSkipping: Boolean = false,
    isPaused: Boolean = false,
    isTappingLive: Boolean = false,
    onToggleAutoSkip: () -> Unit = {},
    onSnapshotScene: () -> Unit = {},
    sceneResult: com.noir.lynx.feature.hud.autoskip.GenshinSkipEngine.SceneSnapshotResult? = null
) {
    val prefs by preferences.changes.collectAsState(initial = preferences.read())
    SideEffect {
        val level = snapshot[HudMetric.BATTERY_LEVEL]
        com.noir.lynx.feature.hud.addins.HudGamingAddins.current?.acceptBattery(
            level.value?.takeIf { level.status == "valid" && it in 0.0..100.0 }?.toInt())
    }
    var page by rememberSaveable { mutableStateOf(HudPage.Summary) }
    var manageApps by rememberSaveable { mutableStateOf(false) }
    var addinPage by rememberSaveable { mutableIntStateOf(-1) }
    var toolPage by rememberSaveable { mutableStateOf<HudToolPage?>(null) }
    val addinScrolls = (0..8).associateWith { rememberScrollState() }
    val toolScrolls = HudToolPage.values().associateWith { rememberScrollState() }
    val scrolls = HudPage.values().associateWith { rememberScrollState() }
    val appScroll = rememberScrollState()
    val pageState = rememberSaveableStateHolder()
    val context = LocalContext.current
    var appLabel by remember { mutableStateOf("") }
    LaunchedEffect(snapshot.packageName) {
        appLabel = ""
        appLabel = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(snapshot.packageName, 0)
                ).toString()
            }.getOrDefault(snapshot.packageName)
        }
    }
    HudTheme {
        if (!open) {
            HudMiniOverlay(
                snapshot = snapshot, prefs = prefs, viewport = viewport, onOpen = { onOpen(true) },
                onDrag = onDrag, dockRight = dockRight, onDrag2D = onDrag2D, onDragEnd = onDragEnd,
                isSkipping = isSkipping, isPaused = isPaused, onToggleAutoSkip = onToggleAutoSkip
            )
        } else {
            val title = when {
                manageApps -> hudString(R.string.hud_apps_manage)
                page == HudPage.Lab && addinPage >= 0 -> hudString(hudAddinTitles[addinPage])
                page == HudPage.Tools && toolPage != null -> hudString(toolPage!!.title)
                page == HudPage.Summary -> appLabel
                else -> hudString(page.label)
            }
            HudPanelShell(
                viewport = viewport, dockRight = dockRight, page = page, title = title,
                snapshot = snapshot, action = action, bypassActive = bypassStatus.enabled || bypassStatus.active,
                canNavigateBack = manageApps || page != HudPage.Summary,
                onBack = {
                    when {
                        manageApps -> manageApps = false
                        page == HudPage.Lab && addinPage >= 0 -> addinPage = -1
                        page == HudPage.Tools && toolPage != null -> toolPage = null
                        else -> page = HudPage.Summary
                    }
                },
                onSelect = {
                    manageApps = false
                    addinPage = -1
                    toolPage = null
                    page = it
                },
                onClose = { onOpen(false) }
            ) {
                if (manageApps) {
                    Column(Modifier.fillMaxWidth().verticalScroll(appScroll).padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HudAppDock(apps, true, { manageApps = false }, { onOpen(false); onApp(it) })
                        HudAppManager(prefs, preferences)
                    }
                } else pageState.SaveableStateProvider(page.name) {
                    val scroll = when {
                        page == HudPage.Lab && addinPage >= 0 -> addinScrolls.getValue(addinPage)
                        page == HudPage.Tools && toolPage != null -> toolScrolls.getValue(toolPage!!)
                        else -> scrolls.getValue(page)
                    }
                    CompositionLocalProvider(LocalHudPageScroll provides scroll) {
                        Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (page) {
                                HudPage.Summary -> HudHomePage(
                                    snapshot, action, prefs, preferences, focusActive, onProfile,
                                    { page = HudPage.Details }, onCapture, onBoostRam, onToggleFocus, { onOpen(false) }
                                )
                                HudPage.Details -> HudDetails(snapshot)
                                HudPage.Lab -> HudLab(
                                    snapshot = snapshot, bypassStatus = bypassStatus, busy = action.busy,
                                    onToggleBypass = onToggleBypass, onRefreshBypass = onRefreshBypass,
                                    onProbeNetwork = onProbeNetwork, touchStatus = touchStatus, shieldStatus = shieldStatus,
                                    onToggleTouchBoost = onToggleTouchBoost, onToggleHeadsUpShield = onToggleHeadsUpShield,
                                    onSelectFocusLevel = onSelectFocusLevel, selectedAddin = addinPage,
                                    onSelectAddin = { addinPage = it }, brightnessLocked = brightnessLocked,
                                    onToggleBrightnessLock = onToggleBrightnessLock
                                )
                                HudPage.Tools -> HudTools(
                                    selectedPage = toolPage, onSelectPage = { toolPage = it },
                                    onApps = { manageApps = true }, prefs = prefs, store = preferences,
                                    rates = rates, activeHz = snapshot[HudMetric.DISPLAY_HZ], action = action,
                                    onHz = onHz, onStop = onStop, brightnessLocked = brightnessLocked,
                                    onToggleBrightnessLock = onToggleBrightnessLock,
                                    onToggleAutoSkip = preferences::setAutoSkipEnabled,
                                    onSetAutoSkipSpeed = preferences::setAutoSkipSpeed,
                                    onSetAutoSkipChoiceBottom = preferences::setAutoSkipChoiceBottom,
                                    isSkipping = isSkipping, isPaused = isPaused, isTappingLive = isTappingLive,
                                    onSnapshotScene = onSnapshotScene, sceneResult = sceneResult
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
