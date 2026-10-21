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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        selected.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { metric ->
                    Column(Modifier.weight(1f)) {
                        Text(hudString(metric.label), fontSize = 12.sp, color = HudMuted, maxLines = 1)
                        Text("—", fontSize = 16.sp)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
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
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp, color = HudMuted)
        Switch(checked, onCheckedChange, Modifier.semantics { contentDescription = label })
    }
}
