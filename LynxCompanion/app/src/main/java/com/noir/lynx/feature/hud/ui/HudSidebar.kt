package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.*
import com.noir.lynx.feature.hud.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HudSidebar(snapshot: HudSnapshot, viewport: HudViewport, open: Boolean, preferences: HudPreferencesStore,
    action: HudActionState, apps: List<HudApp>, rates: List<Float>,
    onOpen: (Boolean) -> Unit, onDrag: (Float) -> Unit, onProfile: (String) -> Unit,
    onHz: (Float) -> Unit, onCapture: () -> Unit, onApp: (HudApp) -> Unit, onStop: () -> Unit) {
    val prefs by preferences.changes.collectAsState(initial = preferences.read())
    var tab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    var appLabel by remember { mutableStateOf("") }
    LaunchedEffect(snapshot.packageName) {
        appLabel = withContext(Dispatchers.IO) { runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(snapshot.packageName,0)).toString()
        }.getOrDefault(snapshot.packageName) }
    }
    HudTheme {
        if (!open) {
            HudMiniOverlay(snapshot,prefs,viewport,{ onOpen(true) },onDrag)
        } else Surface(
            Modifier.width(viewport.panelWidth.dp).heightIn(max = viewport.panelHeight.dp),
            shape = RoundedCornerShape(topEnd = 20.dp,bottomEnd = 20.dp), color = HudBackground,
            border = BorderStroke(1.dp,HudMuted.copy(alpha = 0.15f))) {
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 12.dp)
                    .pointerInput(Unit) { detectDragGestures { change, amount -> change.consume(); onDrag(amount.y) } },
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(hudString(R.string.hud_title), color = HudCyan, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text(appLabel.ifBlank { hudString(R.string.hud_unknown_app) }, fontSize = 12.sp, color = HudMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(hudString(R.string.hud_value,snapshot[HudMetric.BATTERY_LEVEL].text(),"%"), fontSize = 12.sp, color = HudMuted)
                    IconButton(onClick = { onOpen(false) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close,hudString(R.string.hud_close),modifier = Modifier.size(20.dp))
                    }
                }
                HudAppDock(apps) { onOpen(false); onApp(it) }
                val tabs = listOf(R.string.hud_summary,R.string.hud_details,R.string.hud_tools)
                val icons = listOf(Icons.Default.Speed,Icons.Default.ShowChart,Icons.Default.Tune)
                if (!viewport.landscape) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    tabs.forEachIndexed { index, label ->
                        TextButton(onClick = { tab = index }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(2.dp)) {
                            Text(hudString(label), fontSize = 12.sp, color = if (tab==index) HudCyan else HudMuted)
                        }
                    }
                }
                HorizontalDivider(color = HudMuted.copy(alpha = 0.12f))
                Row(Modifier.weight(1f,fill = false)) {
                    if (viewport.landscape) Column(Modifier.width(48.dp)) {
                        tabs.forEachIndexed { index,label ->
                            IconButton(onClick = { tab = index }, modifier = Modifier.size(48.dp)) {
                                Icon(icons[index],hudString(label),tint = if (tab==index) HudCyan else HudMuted)
                            }
                        }
                    }
                    key(tab) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            when(tab) {
                                0 -> HudOverview(snapshot,action,onProfile)
                                1 -> HudDetails(snapshot)
                                else -> HudTools(prefs,preferences,rates,action,onHz,onCapture,onStop)
                            }
                            action.message?.let { Text(hudString(it),fontSize = 12.sp,color = HudCyan) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudAppDock(apps: List<HudApp>, onApp: (HudApp) -> Unit) {
    if (apps.isEmpty()) {
        Text(hudString(R.string.hud_no_apps),Modifier.padding(horizontal = 12.dp,vertical = 8.dp),fontSize = 12.sp,color = HudMuted)
    } else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp,vertical = 4.dp),horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        apps.forEach { app ->
            Box(Modifier.size(48.dp).clickable { onApp(app) },contentAlignment = Alignment.Center) {
                Image(app.icon.asImageBitmap(),app.label,modifier = Modifier.size(34.dp))
            }
        }
    }
}
