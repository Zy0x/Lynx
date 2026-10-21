package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    HudTheme {
        Surface(modifier.fillMaxWidth(), color = HudCard, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hudString(R.string.hud_configure), fontSize = 14.sp)
                HudMetricSelection(prefs, store)
            }
        }
    }
}

@Composable
internal fun HudMetricSelection(prefs: HudPreferences, store: HudPreferencesStore, showPin: Boolean = true) {
    Text(hudString(R.string.hud_mini_help), fontSize = 12.sp, color = HudMuted)
    if (showPin) HudPreferenceToggle(hudString(R.string.hud_pin), prefs.pinned, store::pin)
    Text(hudString(R.string.hud_preview), fontSize = 12.sp, color = HudCyan)
    val selected = HudMetric.values().filter { it.key in prefs.selected }
    if (selected.isEmpty()) Text(hudString(R.string.hud_empty_metrics), fontSize = 12.sp, color = HudMuted)
    val cellWidth = hudLabelWidth(selected.map { hudString(it.label) }) + 8f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (HudLayoutPolicy.fits(maxWidth.value, listOf(cellWidth, cellWidth))) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            selected.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { metric ->
                        Column(Modifier.weight(1f)) {
                            Text(hudString(metric.label), fontSize = 12.sp, color = HudMuted)
                            Text("—", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
    HudMetric.values().forEach { metric ->
        HudPreferenceToggle(hudString(metric.label), metric.key in prefs.selected) { store.select(metric.key, it) }
    }
    HudAction(hudString(R.string.hud_reset), onClick = store::reset, modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun HudPreferenceToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val labelWidth = hudLabelWidth(listOf(label))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (HudLayoutPolicy.fits(maxWidth.value, listOf(labelWidth, 52f))) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), fontSize = 12.sp)
                Switch(checked, onCheckedChange, Modifier.semantics { contentDescription = label })
            }
        } else Column(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 12.sp)
            Switch(checked, onCheckedChange, Modifier.align(Alignment.End).semantics { contentDescription = label })
        }
    }
}
