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
    onDragEnd: () -> Unit = {}
) {
    val prefs by preferences.changes.collectAsState(initial = preferences.read())
    var page by remember { mutableStateOf(HudPage.Summary) }
    var manageApps by remember { mutableStateOf(false) }
    val scrolls = mapOf(
        HudPage.Summary to rememberScrollState(),
        HudPage.Details to rememberScrollState(),
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
            val drawerWidth = (viewport.panelWidth - 15f).coerceAtLeast(240f).dp
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
                                page == HudPage.Details -> hudString(R.string.hud_details)
                                page == HudPage.Tools -> hudString(R.string.hud_tools)
                                else -> appLabel
                            },
                            battery = snapshot[HudMetric.BATTERY_LEVEL],
                            batteryTemp = snapshot[HudMetric.BATTERY_TEMP],
                            charging = snapshot.charging,
                            canNavigateBack = inSubscreen,
                            onBack = {
                                manageApps = false
                                page = HudPage.Summary
                            },
                            onClose = { onOpen(false) }
                        )

                        // Google Play Games Sidekick 4 Quick-Action Squircle Tiles
                        HudQuickTileRow(
                            pinned = prefs.pinned,
                            toolsActive = page == HudPage.Tools && !manageApps,
                            busy = action.busy,
                            onCapture = onCapture,
                            onBoostRam = onBoostRam,
                            onTogglePin = { preferences.pin(!prefs.pinned) },
                            onOpenTools = {
                                manageApps = false
                                page = if (page == HudPage.Tools) HudPage.Summary else HudPage.Tools
                            }
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

                        if (manageApps) {
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HudAppManager(prefs, preferences)
                            }
                        } else {
                            pageState.SaveableStateProvider(page.name) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .verticalScroll(scrolls.getValue(page))
                                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    when (page) {
                                        HudPage.Summary -> HudOverview(
                                            snapshot = snapshot,
                                            action = action,
                                            onProfile = onProfile,
                                            onCapture = onCapture,
                                            onTools = { page = HudPage.Details },
                                            onBoostRam = onBoostRam
                                        )
                                        HudPage.Details -> HudDetails(snapshot = snapshot)
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
