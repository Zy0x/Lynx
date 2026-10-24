package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val extra = when (current) { "auto" -> R.string.hud_auto; "extreme" -> R.string.hud_extreme; "dormant" -> R.string.hud_dormant; else -> null }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (extra != null) hudString(R.string.hud_profile_current, hudString(extra)) else hudString(R.string.hud_profile),
                Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HudCyan)
            if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            IconButton(onClick = { more = !more }, modifier = Modifier.size(48.dp), enabled = !busy) {
                Icon(Icons.Default.MoreHoriz, hudString(R.string.hud_more), Modifier.size(18.dp), tint = HudMuted)
            }
        }
        val widths = labels.map { (hudLabelWidth(listOf(it)) + 12f).coerceAtLeast(48f) }
        BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
            if (HudLayoutPolicy.fits(maxWidth.value, widths, 2f)) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                choices.indices.forEach { index ->
                    HudChoice(labels[index], current == choices[index].first, { onProfile(choices[index].first) }, Modifier.weight(widths[index]), !busy)
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                choices.indices.forEach { index ->
                    HudChoice(labels[index], current == choices[index].first, { onProfile(choices[index].first) }, Modifier.fillMaxWidth(), !busy)
                }
            }
        }
        if (current == null) Text(hudString(R.string.hud_profile_unavailable), fontSize = 12.sp, color = HudMuted)
        if (busy) Text(hudString(R.string.hud_applying), fontSize = 12.sp, color = HudMuted)
        if (more) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HudChoice(hudString(R.string.hud_auto), current == "auto", { more = false; onProfile("auto") }, Modifier.weight(1f), !busy)
            HudChoice(hudString(R.string.hud_extreme), current == "extreme", { more = false; extreme = true }, Modifier.weight(1f), !busy)
        }
        if (extreme) HudSection(hudString(R.string.hud_extreme_title)) {
            Text(hudString(R.string.hud_extreme_warning), fontSize = 12.sp, color = HudMuted)
            val actions = listOf(hudString(R.string.hud_cancel), hudString(R.string.hud_confirm))
            HudAdaptiveGrid(actions, maximum = 2) { index ->
                HudAction(actions[index], !busy, { extreme = false; if (index == 1) onProfile("extreme") }, Modifier.weight(1f))
            }
        }
    }
}
