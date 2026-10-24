package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.R
import com.noir.lynx.feature.hud.*
import com.noir.lynx.feature.hud.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HudSidebar(snapshot: HudSnapshot, viewport: HudViewport, open: Boolean, preferences: HudPreferencesStore,
    action: HudActionState, apps: List<HudApp>, rates: List<Float>,
    onOpen: (Boolean) -> Unit, onDrag: (Float) -> Unit, onProfile: (String) -> Unit,
    onHz: (Float) -> Unit, onCapture: () -> Unit, onApp: (HudApp) -> Unit, onStop: () -> Unit,
    onBoostRam: () -> Unit = {}) {
    val prefs by preferences.changes.collectAsState(initial = preferences.read())
    var page by remember { mutableStateOf(HudPage.Summary) }
    var menuOpen by remember { mutableStateOf(false) }
    var manageApps by remember { mutableStateOf(false) }
    // Keep scroll states above both the open/closed branch and the responsive navigation branch.
    val scrolls = mapOf(HudPage.Summary to rememberScrollState(), HudPage.Details to rememberScrollState(), HudPage.Tools to rememberScrollState())
    val pageState = rememberSaveableStateHolder()
    val context = LocalContext.current
    var appLabel by remember { mutableStateOf("") }
    LaunchedEffect(snapshot.packageName) {
        appLabel = ""
        appLabel = withContext(Dispatchers.IO) { runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(snapshot.packageName, 0)).toString()
        }.getOrDefault(snapshot.packageName) }
    }
    HudTheme {
        val labelWidth = hudLabelWidth(HudPage.values().map { hudString(it.label) })
        val layout = HudLayoutPolicy.resolve(viewport.panelWidth, labelWidth)
        LaunchedEffect(layout.useRail, open) { menuOpen = false }
        if (!open) HudMiniOverlay(snapshot, prefs, viewport, { onOpen(true) }, onDrag)
        else Surface(Modifier.width(viewport.panelWidth.dp).heightIn(max = viewport.panelHeight.dp),
            shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp), color = HudBackground,
            border = BorderStroke(1.dp, HudBorder)) {
            Column {
                HudPanelHeader(appLabel, snapshot[HudMetric.BATTERY_LEVEL], onDrag) { onOpen(false) }
                HudAppDock(apps, { manageApps = !manageApps }) { onOpen(false); onApp(it) }
                HorizontalDivider(color = HudBorder, thickness = 0.8.dp)
                if (manageApps) Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(HudLayoutPolicy.CONTENT_PADDING.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HudAction(hudString(R.string.hud_apps_done), onClick = { manageApps = false }, modifier = Modifier.fillMaxWidth())
                    HudAppManager(prefs, preferences)
                } else Row(Modifier.weight(1f, fill = false)) {
                    if (layout.useRail) {
                        HudNavigationRail(page, layout.railWidth) { page = it }
                        Box(Modifier.fillMaxHeight().width(0.8.dp).background(HudBorder))
                    }
                    Column(Modifier.weight(1f)) {
                        if (!layout.useRail) Box(Modifier.padding(horizontal = HudLayoutPolicy.CONTENT_PADDING.dp)) {
                            HudDisclosure(hudString(page.label), menuOpen) { menuOpen = !menuOpen }
                        }
                        if (!layout.useRail && menuOpen) {
                            HudPageMenu(page, { page = it; menuOpen = false }, Modifier.weight(1f, fill = false))
                        } else pageState.SaveableStateProvider(page.name) {
                            Column(Modifier.weight(1f, fill = false).verticalScroll(scrolls.getValue(page))
                                .padding(HudLayoutPolicy.CONTENT_PADDING.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                when (page) {
                                    HudPage.Summary -> HudOverview(snapshot, action, onProfile, onCapture, { page = HudPage.Tools }, onBoostRam)
                                    HudPage.Details -> HudDetails(snapshot)
                                    HudPage.Tools -> HudTools(prefs, preferences, rates, snapshot[HudMetric.DISPLAY_HZ], action, onHz, onCapture, onStop, onBoostRam)
                                }
                                action.message?.let { Text(hudString(it), fontSize = 12.sp, color = HudCyan) }
                            }
                        }
                    }
                }
            }
        }
    }
}
