package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
                RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
            } else {
                RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
            }
            Surface(
                modifier = Modifier
                    .width(viewport.panelWidth.dp)
                    .heightIn(max = viewport.panelHeight.dp),
                shape = panelShape,
                color = HudBackground,
                border = BorderStroke(1.dp, HudBorder)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HudPanelHeader(
                        appLabel = appLabel,
                        battery = snapshot[HudMetric.BATTERY_LEVEL],
                        batteryTemp = snapshot[HudMetric.BATTERY_TEMP],
                        charging = snapshot.charging,
                        onDrag = onDrag,
                        onDrag2D = onDrag2D,
                        onDragEnd = onDragEnd,
                        onClose = { onOpen(false) }
                    )

                    HudAppDock(
                        apps = apps,
                        onManage = { manageApps = !manageApps },
                        onApp = {
                            onOpen(false)
                            onApp(it)
                        }
                    )

                    HorizontalDivider(color = HudBorder, thickness = 0.8.dp)

                    if (manageApps) {
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HudAction(
                                label = hudString(R.string.hud_apps_done),
                                onClick = { manageApps = false },
                                modifier = Modifier.fillMaxWidth()
                            )
                            HudAppManager(prefs, preferences)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            HudTopTabBar(
                                selected = page,
                                onSelect = { page = it }
                            )
                        }

                        pageState.SaveableStateProvider(page.name) {
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(scrolls.getValue(page))
                                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                when (page) {
                                    HudPage.Summary -> HudOverview(
                                        snapshot = snapshot,
                                        action = action,
                                        onProfile = onProfile,
                                        onCapture = onCapture,
                                        onTools = { page = HudPage.Tools },
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
                                        color = HudCyan,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
