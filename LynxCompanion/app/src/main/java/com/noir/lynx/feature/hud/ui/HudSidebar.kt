package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
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
    onProbeNetwork: suspend () -> HudNetworkSample = { HudNetworkSample() }
) {
    val prefs by preferences.changes.collectAsState(initial = preferences.read())
    var page by remember { mutableStateOf(HudPage.Summary) }
    var manageApps by remember { mutableStateOf(false) }
    val scrolls = mapOf(
        HudPage.Summary to rememberScrollState(),
        HudPage.Details to rememberScrollState(),
        HudPage.Lab to rememberScrollState(),
        HudPage.Tools to rememberScrollState()
    )
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
                snapshot = snapshot,
                prefs = prefs,
                viewport = viewport,
                onOpen = { onOpen(true) },
                onDrag = onDrag,
                dockRight = dockRight,
                onDrag2D = onDrag2D,
                onDragEnd = onDragEnd
            )
        } else {
            val panelShape = if (dockRight) {
                RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
            } else {
                RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            }
            val drawerWidthFloat = (viewport.panelWidth - 15f).coerceAtLeast(240f)
            val drawerWidth = drawerWidthFloat.dp
            val fontScale = LocalDensity.current.fontScale
            val layout = HudLayoutPolicy.resolve(drawerWidthFloat, 48f * fontScale)
            val inSubscreen = manageApps || page != HudPage.Summary

            val drawerSurface: @Composable () -> Unit = {
                Surface(
                    modifier = Modifier
                        .width(drawerWidth)
                        .heightIn(max = viewport.panelHeight.dp),
                    shape = panelShape,
                    color = HudBackground
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HudPanelHeader(
                            appLabel = when {
                                manageApps -> hudString(R.string.hud_apps_manage)
                                page == HudPage.Details -> hudString(R.string.hud_monitor)
                                page == HudPage.Lab -> hudString(R.string.hud_lab)
                                page == HudPage.Tools -> hudString(R.string.hud_tools)
                                else -> appLabel
                            },
                            battery = snapshot[HudMetric.BATTERY_LEVEL],
                            batteryTemp = snapshot[HudMetric.BATTERY_TEMP],
                            charging = snapshot.charging,
                            bypassActive = bypassStatus.enabled || bypassStatus.active,
                            canNavigateBack = inSubscreen,
                            onBack = {
                                manageApps = false
                                page = HudPage.Summary
                            },
                            onClose = { onOpen(false) }
                        )

                        if (!layout.useRail) {
                            HudTopTabBar(
                                selected = page,
                                onSelect = {
                                    manageApps = false
                                    page = it
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (layout.useRail && !dockRight) {
                                HudNavigationRail(
                                    selected = page,
                                    width = layout.railWidth,
                                    onSelect = {
                                        manageApps = false
                                        page = it
                                    }
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                if (manageApps) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        HudAppDock(
                                            apps = apps,
                                            managing = true,
                                            onManage = { manageApps = false },
                                            onApp = {
                                                onOpen(false)
                                                onApp(it)
                                            }
                                        )
                                        HudAppManager(prefs, preferences)
                                    }
                                } else {
                                    pageState.SaveableStateProvider(page.name) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .verticalScroll(scrolls.getValue(page))
                                                .padding(horizontal = 4.dp, vertical = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            when (page) {
                                                HudPage.Summary -> {
                                                    HudQuickTileRow(
                                                        pinned = prefs.pinned,
                                                        focusActive = focusActive,
                                                        busy = action.busy,
                                                        onCapture = onCapture,
                                                        onBoostRam = onBoostRam,
                                                        onTogglePin = { preferences.pin(!prefs.pinned) },
                                                        onToggleFocus = onToggleFocus
                                                    )
                                                    HudAppDock(
                                                        apps = apps,
                                                        managing = manageApps,
                                                        onManage = { manageApps = !manageApps },
                                                        onApp = {
                                                            onOpen(false)
                                                            onApp(it)
                                                        }
                                                    )
                                                    HudOverview(
                                                        snapshot = snapshot,
                                                        action = action,
                                                        onProfile = onProfile,
                                                        onCapture = onCapture,
                                                        onTools = { page = HudPage.Details },
                                                        onBoostRam = onBoostRam
                                                    )
                                                }
                                                HudPage.Details -> HudDetails(snapshot = snapshot)
                                                HudPage.Lab -> HudLab(
                                                    snapshot = snapshot,
                                                    bypassStatus = bypassStatus,
                                                    busy = action.busy,
                                                    onToggleBypass = onToggleBypass,
                                                    onRefreshBypass = onRefreshBypass,
                                                    onProbeNetwork = onProbeNetwork
                                                )
                                                HudPage.Tools -> HudTools(
                                                    prefs = prefs,
                                                    store = preferences,
                                                    rates = rates,
                                                    activeHz = snapshot[HudMetric.DISPLAY_HZ],
                                                    action = action,
                                                    onHz = onHz,
                                                    onCapture = onCapture,
                                                    onStop = onStop,
                                                    onBoostRam = onBoostRam
                                                )
                                            }
                                            action.message?.let { msgRes ->
                                                Text(
                                                    text = hudString(msgRes),
                                                    fontSize = 11.5.sp,
                                                    color = HudBlue,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (layout.useRail && dockRight) {
                                HudNavigationRail(
                                    selected = page,
                                    width = layout.railWidth,
                                    onSelect = {
                                        manageApps = false
                                        page = it
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Protruding Sidekick Handle Ear attached to the inner edge of the open drawer
            Row(
                modifier = Modifier.width(viewport.panelWidth.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dockRight) {
                    SidekickEdgeTab(
                        dockRight = true,
                        modifier = Modifier.clickable { onOpen(false) }
                    )
                    drawerSurface()
                } else {
                    drawerSurface()
                    SidekickEdgeTab(
                        dockRight = false,
                        modifier = Modifier.clickable { onOpen(false) }
                    )
                }
            }
        }
    }
}
