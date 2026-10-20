package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text

@Composable
internal fun HudProfiles(current: String?, busy: Boolean, onProfile: (String) -> Unit) {
    var more by rememberSaveable { mutableStateOf(false) }
    var extreme by rememberSaveable { mutableStateOf(false) }
    val choices = listOf("powersave" to R.string.hud_save, "balance" to R.string.hud_balanced, "performance" to R.string.hud_performance)
    val labels = choices.map { hudString(it.second) }
    val cellWidth = (hudLabelWidth(labels) + 8f).coerceAtLeast(48f)
    val name = when(current) {
        "powersave" -> R.string.hud_save; "balance" -> R.string.hud_balanced; "performance" -> R.string.hud_performance
        "auto" -> R.string.hud_auto; "extreme" -> R.string.hud_extreme; "dormant" -> R.string.hud_dormant
        else -> R.string.hud_unsupported
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(hudString(R.string.hud_profile_current, hudString(name)), Modifier.weight(1f), fontSize = 12.sp, color = HudMuted)
            val state = hudString(if (more) R.string.hud_expanded else R.string.hud_collapsed)
            IconButton(onClick = { more = !more }, enabled = !busy, modifier = Modifier.size(48.dp).semantics { stateDescription = state }) {
                Icon(Icons.Default.MoreHoriz, hudString(R.string.hud_more), Modifier.size(20.dp))
            }
        }
        Surface(color = HudCard, shape = RoundedCornerShape(12.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(2.dp).selectableGroup()) {
                if (HudLayoutPolicy.fits(maxWidth.value, List(3) { cellWidth }, 0f)) {
                    Row {
                        choices.forEachIndexed { index, (id, _) ->
                            HudChoice(labels[index], current == id, { onProfile(id) }, Modifier.weight(1f), !busy)
                        }
                    }
                } else Column {
                    choices.forEachIndexed { index, (id, _) ->
                        HudChoice(labels[index], current == id, { onProfile(id) }, Modifier.fillMaxWidth(), !busy)
                    }
                }
            }
        }
        if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = HudCyan)
            Text(hudString(R.string.hud_applying), fontSize = 12.sp, color = HudMuted)
        }
        if (more) Column(Modifier.fillMaxWidth().selectableGroup()) {
            HudChoice(hudString(R.string.hud_auto), current == "auto", { more = false; onProfile("auto") }, Modifier.fillMaxWidth(), !busy)
            HudChoice(hudString(R.string.hud_extreme), current == "extreme", { more = false; extreme = true }, Modifier.fillMaxWidth(), !busy)
        }
        if (extreme) Surface(color = Color(0xFF30231C), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hudString(R.string.hud_extreme_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(hudString(R.string.hud_extreme_warning), fontSize = 12.sp)
                HudAction(hudString(R.string.hud_cancel), onClick = { extreme = false }, modifier = Modifier.fillMaxWidth())
                HudAction(hudString(R.string.hud_confirm), enabled = !busy, onClick = { extreme = false; onProfile("extreme") }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
