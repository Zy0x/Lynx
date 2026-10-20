package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
                Text(hudString(R.string.hud_configure), fontSize = 16.sp)
                HudMetricSelection(prefs, store)
            }
        }
    }
}

@Composable
internal fun HudMetricSelection(prefs: HudPreferences, store: HudPreferencesStore) {
    Text(hudString(R.string.hud_mini_help), fontSize = 12.sp, color = HudMuted)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(hudString(R.string.hud_pin), Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = prefs.pinned, onCheckedChange = store::pin)
    }
    Text(hudString(R.string.hud_preview), fontSize = 12.sp, color = HudCyan)
    val selected = HudMetric.values().filter { it.key in prefs.selected }
    if (selected.isEmpty()) Text(hudString(R.string.hud_empty_metrics), fontSize = 12.sp, color = HudMuted)
    selected.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { metric ->
                Column(Modifier.weight(1f)) {
                    Text(hudString(metric.label), fontSize = 12.sp, color = HudMuted)
                    Text("—", fontSize = 18.sp)
                }
            }
        }
    }
    HudMetric.values().forEach { metric ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(hudString(metric.label), Modifier.weight(1f), fontSize = 14.sp)
            Switch(checked = metric.key in prefs.selected, onCheckedChange = { store.select(metric.key,it) })
        }
    }
    HudAction(hudString(R.string.hud_reset), onClick = store::reset)
}
