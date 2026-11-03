package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.ScrollState
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
    onProfile: (String) -> Unit,
    onHz: (Float) -> Unit,
    onCapture: () -> Unit,
    onApp: (HudApp) -> Unit,
    onStop: () -> Unit,
    onBoostRam: () -> Unit = {},
    dockRight: Boolean = false,
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
    var toolPage by rememberSaveable { mutableStateOf<HudToolPage?>(null) }
    var monitorSubpage by rememberSaveable { mutableStateOf<HudMonitorSubpage?>(null) }
    var boostSubpage by rememberSaveable { mutableStateOf<HudBoostSubpage?>(null) }
    val toolScrolls = HudToolPage.values().associateWith { rememberScrollState() }
    val monitorScrolls = HudMonitorSubpage.values().associateWith { rememberScrollState() }
    val boostScrolls = remember { HudBoostSubpage.values().associateWith { ScrollState(0) } }
    val scrolls = HudPage.values().associateWith { rememberScrollState() }
    val pageState = rememberSaveableStateHolder()
    val context = LocalContext.current
    var appLabel by remember { mutableStateOf("") }
    LaunchedEffect(snapshot.packageName) {
        val pkg = snapshot.packageName
        if (pkg.isBlank()) {
            appLabel = "Lynx"
            return@LaunchedEffect
        }
        val cached = HudAppCatalog.getCachedLabel(pkg)
        if (!cached.isNullOrBlank()) {
            appLabel = cached
        } else {
            appLabel = withContext(Dispatchers.IO) {
                runCatching {
                    val lbl = context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
                    HudAppCatalog.putCachedLabel(pkg, lbl)
                    lbl
                }.getOrDefault("Lynx")
            }
        }
    }
    HudTheme {
        if (!open) {
            HudMiniOverlay(
                snapshot = snapshot, prefs = prefs, viewport = viewport, onOpen = { onOpen(true) },
                dockRight = dockRight
            )
        } else {
            val title = when {
                manageApps -> hudString(R.string.hud_apps_manage)
                page == HudPage.Tools && toolPage != null -> hudString(toolPage!!.title)
                page == HudPage.Details && monitorSubpage != null -> hudString(monitorSubpage!!.titleRes)
                page == HudPage.Lab && boostSubpage != null -> hudString(boostSubpage!!.titleRes)
                page == HudPage.Summary -> appLabel
                else -> hudString(page.label)
            }
            HudPanelShell(
                viewport = viewport, dockRight = dockRight, page = page, title = title,
                snapshot = snapshot, action = action, bypassActive = bypassStatus.enabled || bypassStatus.active,
                canNavigateBack = manageApps || page != HudPage.Summary || monitorSubpage != null || toolPage != null || boostSubpage != null,
                appDock = if (!manageApps) {
                    {
                        HudAppDock(
                            apps = apps,
                            managing = false,
                            onManage = { manageApps = true },
                            onApp = { onOpen(false); onApp(it) }
                        )
                    }
                } else null,
                onBack = {
                    when {
                        manageApps -> manageApps = false
                        page == HudPage.Tools && toolPage != null -> toolPage = null
                        page == HudPage.Details && monitorSubpage != null -> monitorSubpage = null
                        page == HudPage.Lab && boostSubpage != null -> boostSubpage = null
                        else -> page = HudPage.Summary
                    }
                },
                onSelect = {
                    manageApps = false
                    toolPage = null
                    monitorSubpage = null
                    boostSubpage = null
                    page = it
                },
                onClose = { onOpen(false) }
            ) {
                if (manageApps) {
                    HudAppManager(
                        prefs = prefs,
                        store = preferences,
                        modifier = Modifier.fillMaxSize(),
                        headerContent = {
                            HudAppDock(apps, true, { manageApps = false }, { onOpen(false); onApp(it) })
                        }
                    )
                } else pageState.SaveableStateProvider(page.name) {
                    val scroll = when {
                        page == HudPage.Tools && toolPage != null -> toolScrolls.getValue(toolPage!!)
                        page == HudPage.Details && monitorSubpage != null -> monitorScrolls.getValue(monitorSubpage!!)
                        page == HudPage.Lab && boostSubpage != null -> boostScrolls.getValue(boostSubpage!!)
                        else -> scrolls.getValue(page)
                    }
                    CompositionLocalProvider(LocalHudPageScroll provides scroll) {
                        Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (page) {
                                HudPage.Summary -> HudHomePage(
                                    viewport.landscape, snapshot, action, prefs, preferences, focusActive, onProfile,
                                    { page = HudPage.Details }, onCapture, onBoostRam, onToggleFocus, { onOpen(false) }
                                )
                                HudPage.Details -> HudDetails(
                                    snapshot = snapshot,
                                    selectedSubpage = monitorSubpage,
                                    onSelectSubpage = { monitorSubpage = it }
                                )
                                HudPage.Lab -> HudLab(
                                    snapshot = snapshot,
                                    action = action,
                                    selectedSubpage = boostSubpage,
                                    onSelectSubpage = { boostSubpage = it },
                                    onBoostRam = onBoostRam,
                                    bypassStatus = bypassStatus,
                                    onToggleBypass = onToggleBypass,
                                    onRefreshBypass = onRefreshBypass,
                                    onProbeNetwork = onProbeNetwork,
                                    touchStatus = touchStatus,
                                    onToggleTouchBoost = onToggleTouchBoost
                                )
                                HudPage.Tools -> HudTools(
                                    selectedPage = toolPage,
                                    onSelectPage = { toolPage = it },
                                    onApps = { manageApps = true },
                                    prefs = prefs,
                                    store = preferences,
                                    rates = rates,
                                    activeHz = snapshot[HudMetric.DISPLAY_HZ],
                                    action = action,
                                    onHz = onHz,
                                    onStop = onStop,
                                    brightnessLocked = brightnessLocked,
                                    onToggleBrightnessLock = onToggleBrightnessLock,
                                    onToggleAutoSkip = preferences::setAutoSkipEnabled,
                                    onSetAutoSkipSpeed = preferences::setAutoSkipSpeed,
                                    onSetAutoSkipChoiceBottom = preferences::setAutoSkipChoiceBottom,
                                    isSkipping = isSkipping,
                                    isPaused = isPaused,
                                    isTappingLive = isTappingLive,
                                    onSnapshotScene = onSnapshotScene,
                                    sceneResult = sceneResult,
                                    snapshot = snapshot,
                                    shieldStatus = shieldStatus,
                                    onToggleHeadsUpShield = onToggleHeadsUpShield,
                                    onSelectFocusLevel = onSelectFocusLevel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
