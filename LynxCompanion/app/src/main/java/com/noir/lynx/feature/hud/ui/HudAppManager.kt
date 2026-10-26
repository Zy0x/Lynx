package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = hudString(R.string.hud_apps_description),
            fontSize = 11.sp,
            color = HudMuted
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(ordered, key = { it.packageName }) { app ->
                val chosen = app.packageName in selected
                Surface(
                    color = if (chosen) HudCyan.copy(alpha = 0.1f) else HudElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.8.dp, if (chosen) HudCyan.copy(alpha = 0.4f) else HudBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .padding(start = 6.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .toggleable(chosen, role = Role.Checkbox) { enabled ->
                                        store.setDockPackages(
                                            saved.toMutableList().apply {
                                                if (enabled && app.packageName !in this) add(app.packageName)
                                                else if (!enabled) remove(app.packageName)
                                            }
                                        )
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = chosen,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = HudCyan,
                                        checkmarkColor = Color.Black,
                                        uncheckedColor = HudMuted
                                    )
                                )
                                Image(
                                    bitmap = app.icon.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Text(
                                    text = app.label,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 11.5.sp,
                                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (chosen) Color.White else Color(0xFFCBD5E1)
                                )
                            }
                            IconButton(
                                onClick = { editing = if (editing == app.packageName) null else app.packageName },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = hudString(R.string.hud_app_customize, app.label),
                                    modifier = Modifier.size(16.dp),
                                    tint = if (editing == app.packageName) HudCyan else HudMuted
                                )
                            }
                        }
                        if (editing == app.packageName) {
                            HudAppOptions(app, prefs, store, selected, saved)
                        }
                    }
                }
            }
        }
        if (apps.isEmpty()) {
            Text(hudString(R.string.hud_apps_empty), fontSize = 11.sp, color = HudMuted)
        }
    }
}

@Composable
private fun HudAppOptions(
    app: HudApp,
    prefs: HudPreferences,
    store: HudPreferencesStore,
    selected: List<String>,
    saved: List<String>
) {
    val size = prefs.appSizes[app.packageName]
    val width = size?.width ?: prefs.floatingWidthPct
    val height = size?.height ?: prefs.floatingHeightPct
    val sliderColors = SliderDefaults.colors(
        thumbColor = HudCyan,
        activeTrackColor = HudCyan,
        inactiveTrackColor = HudCard
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val index = selected.indexOf(app.packageName)
        if (index >= 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = hudString(R.string.hud_app_order, index + 1),
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    color = HudMuted
                )
                listOf(-1 to Icons.Default.ArrowUpward, 1 to Icons.Default.ArrowDownward).forEach { (delta, icon) ->
                    IconButton(
                        enabled = index + delta in selected.indices,
                        modifier = Modifier.size(36.dp),
                        onClick = {
                            val next = saved.toMutableList()
                            val from = next.indexOf(app.packageName)
                            val to = next.indexOf(selected[index + delta])
                            java.util.Collections.swap(next, from, to)
                            store.setDockPackages(next)
                        }
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = hudString(if (delta < 0) R.string.hud_app_move_up else R.string.hud_app_move_down),
                            modifier = Modifier.size(16.dp),
                            tint = HudCyan
                        )
                    }
                }
            }
        }
        HudPreferenceToggle(hudString(R.string.hud_app_custom_size), size != null) {
            store.setAppSize(app.packageName, if (it) HudAppSize(width, height) else null)
        }
        if (size != null) {
            Text(hudString(R.string.hud_window_size_value, width, height), fontSize = 11.sp, color = HudCyan)
            Text(hudString(R.string.hud_floating_width), fontSize = 11.sp, color = HudMuted)
            Slider(
                value = width.toFloat(),
                onValueChange = { store.setAppSize(app.packageName, HudAppSize(it.toInt(), height)) },
                valueRange = 35f..95f,
                colors = sliderColors
            )
            Text(hudString(R.string.hud_floating_height), fontSize = 11.sp, color = HudMuted)
            Slider(
                value = height.toFloat(),
                onValueChange = { store.setAppSize(app.packageName, HudAppSize(width, it.toInt())) },
                valueRange = 35f..90f,
                colors = sliderColors
            )
        }
    }
}
