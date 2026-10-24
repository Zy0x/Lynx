package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.*

@Composable
fun HudMiniSettings(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember(context) { HudPreferencesStore(context) }
    val prefs by store.changes.collectAsState(initial = store.read())
    HudTheme { Column(modifier) { HudMetricSelection(prefs, store) } }
}

@Composable
internal fun HudMetricSelection(prefs: HudPreferences, store: HudPreferencesStore, showPin: Boolean = true) {
    if (showPin) HudPreferenceToggle(hudString(R.string.hud_pin), prefs.pinned, store::pin)
    val selected = HudMetric.values().filter { it.key in prefs.selected }
    HudExpandableSection(hudString(R.string.hud_preview), hudString(R.string.hud_selected_count, selected.size)) {
        Text(hudString(R.string.hud_mini_help), fontSize = 12.sp, color = HudMuted)
        if (selected.isEmpty()) Text(hudString(R.string.hud_empty_metrics), fontSize = 12.sp, color = HudMuted)
        selected.take(4).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { metric -> Column(Modifier.weight(1f)) {
                    Text(hudString(metric.label), fontSize = 12.sp, color = HudMuted)
                    Text("—", fontSize = 14.sp)
                } }
            }
        }
    }
    hudMetricGroups.forEach { (title, metrics) ->
        val count = metrics.count { it.key in prefs.selected }
        HudExpandableSection(hudString(title), hudString(R.string.hud_metric_group_count, count, metrics.size)) {
            val labels = metrics.map { hudString(it.label) }
            HudAdaptiveGrid(labels, maximum = 2, minCell = hudLabelWidth(labels) + 44f) { index ->
                val metric = metrics[index]
                HudMetricToggle(labels[index], metric.key in prefs.selected, { store.select(metric.key, it) }, Modifier.weight(1f))
            }
        }
    }
    HudAction(hudString(R.string.hud_reset), onClick = store::reset, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun HudMetricToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    Row(modifier.heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null, modifier = Modifier.padding(end = 4.dp))
        Text(label, fontSize = 12.sp, color = if (checked) MaterialTheme.colorScheme.onSurface else HudMuted)
    }
}

@Composable
internal fun HudPreferenceToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp, color = HudMuted)
        Switch(checked, onCheckedChange = null)
    }
}
