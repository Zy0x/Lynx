package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.data.*

@Composable
internal fun HudTools(prefs: HudPreferences, store: HudPreferencesStore, rates: List<Float>, action: HudActionState,
    onHz: (Float) -> Unit, onCapture: () -> Unit, onStop: () -> Unit) {
    var configure by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HudAction(hudString(R.string.hud_capture), !action.busy, onCapture, Modifier.fillMaxWidth())
        Text(hudString(R.string.hud_refresh), fontSize = 14.sp)
        if (rates.isEmpty()) Text(hudString(R.string.hud_unsupported), color = HudMuted, fontSize = 12.sp)
        rates.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { hz -> HudAction(hudString(R.string.hud_value,HudReading(hz.toDouble(),"Hz",status = "valid").text(),"Hz"), !action.busy, { onHz(hz) }, Modifier.weight(1f)) }
            }
        }
        action.requestedHz?.let { Text(hudString(R.string.hud_requested_hz,HudReading(it.toDouble(),"Hz",status = "valid").text()), fontSize = 12.sp, color = HudMuted) }
        Text(hudString(R.string.hud_mode_active), color = HudMuted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(hudString(R.string.hud_pin), Modifier.weight(1f), fontSize = 14.sp)
            Switch(prefs.pinned, store::pin)
        }
        HudAction(hudString(R.string.hud_configure), onClick = { configure = !configure }, modifier = Modifier.fillMaxWidth())
        if (configure) HudMetricSelection(prefs,store)
        HudAction(hudString(R.string.hud_stop), onClick = onStop, modifier = Modifier.fillMaxWidth())
    }
}
