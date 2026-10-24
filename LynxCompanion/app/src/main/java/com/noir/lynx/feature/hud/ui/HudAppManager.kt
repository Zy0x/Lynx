package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudApp
import com.noir.lynx.feature.hud.data.*

/** The same current-user catalog and preferences serve the overlay and Companion settings. */
@Composable
internal fun HudAppManager(prefs: HudPreferences, store: HudPreferencesStore) {
    val context = LocalContext.current
    val catalog = remember(context) { HudAppCatalog(context) }
    val apps by catalog.apps.collectAsState(initial = emptyList())
    val selected = HudDockPolicy.packages(prefs.dockPackages, apps.map { it.packageName }.toSet())
    val saved = prefs.dockPackages ?: selected
    val ordered = remember(apps, selected) {
        val byPackage = apps.associateBy { it.packageName }
        selected.mapNotNull(byPackage::get) + apps.filter { it.packageName !in selected }
    }
    var editing by remember { mutableStateOf<String?>(null) }
    Text(hudString(R.string.hud_apps_description), fontSize = 12.sp, color = HudMuted)
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
        items(ordered, key = { it.packageName }) { app ->
            val chosen = app.packageName in selected
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).heightIn(min = 48.dp).toggleable(chosen, role = Role.Checkbox) { enabled ->
                    store.setDockPackages(saved.toMutableList().apply {
                        if (enabled && app.packageName !in this) add(app.packageName) else if (!enabled) remove(app.packageName)
                    })
                }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Checkbox(chosen, null)
                    Image(app.icon.asImageBitmap(), null, Modifier.size(24.dp))
                    Text(app.label, Modifier.weight(1f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton({ editing = if (editing == app.packageName) null else app.packageName }, Modifier.size(48.dp)) {
                    Icon(Icons.Default.Tune, hudString(R.string.hud_app_customize, app.label), Modifier.size(18.dp), tint = HudCyan)
                }
            }
            if (editing == app.packageName) HudAppOptions(app, prefs, store, selected, saved)
        }
    }
    if (apps.isEmpty()) Text(hudString(R.string.hud_apps_empty), fontSize = 12.sp, color = HudMuted)
}

@Composable
private fun HudAppOptions(app: HudApp, prefs: HudPreferences, store: HudPreferencesStore, selected: List<String>, saved: List<String>) {
    val size = prefs.appSizes[app.packageName]
    val width = size?.width ?: prefs.floatingWidthPct
    val height = size?.height ?: prefs.floatingHeightPct
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val index = selected.indexOf(app.packageName)
        if (index >= 0) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(hudString(R.string.hud_app_order, index + 1), Modifier.weight(1f), fontSize = 12.sp, color = HudMuted)
            listOf(-1 to Icons.Default.ArrowUpward, 1 to Icons.Default.ArrowDownward).forEach { (delta, icon) ->
                IconButton(enabled = index + delta in selected.indices, modifier = Modifier.size(48.dp), onClick = {
                    val next = saved.toMutableList()
                    val from = next.indexOf(app.packageName)
                    val to = next.indexOf(selected[index + delta])
                    java.util.Collections.swap(next, from, to); store.setDockPackages(next)
                }) {
                    Icon(icon, hudString(if (delta < 0) R.string.hud_app_move_up else R.string.hud_app_move_down), Modifier.size(18.dp))
                }
            }
        }
        HudPreferenceToggle(hudString(R.string.hud_app_custom_size), size != null) {
            store.setAppSize(app.packageName, if (it) HudAppSize(width, height) else null)
        }
        if (size != null) {
            Text(hudString(R.string.hud_window_size_value, width, height), fontSize = 12.sp, color = HudMuted)
            Text(hudString(R.string.hud_floating_width), fontSize = 12.sp, color = HudMuted)
            Slider(width.toFloat(), { store.setAppSize(app.packageName, HudAppSize(it.toInt(), height)) }, valueRange = 35f..95f)
            Text(hudString(R.string.hud_floating_height), fontSize = 12.sp, color = HudMuted)
            Slider(height.toFloat(), { store.setAppSize(app.packageName, HudAppSize(width, it.toInt())) }, valueRange = 35f..90f)
        }
    }
}
