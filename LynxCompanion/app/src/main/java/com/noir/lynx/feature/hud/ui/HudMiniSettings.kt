package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HudMetricSelection(prefs, store)
        }
    }
}

@Composable
internal fun HudMetricSelection(prefs: HudPreferences, store: HudPreferencesStore, showPin: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showPin) {
            HudPreferenceToggle(hudString(R.string.hud_pin), prefs.pinned, store::pin)
        }
        Text(
            text = hudString(R.string.hud_mini_help),
            fontSize = 11.sp,
            color = HudMuted
        )
        hudMetricGroups.forEach { (title, metrics) ->
            val count = metrics.count { it.key in prefs.selected }
            HudExpandableSection(
                title = hudString(title),
                detail = hudString(R.string.hud_metric_group_count, count, metrics.size),
                initiallyOpen = title == R.string.hud_group_frame
            ) {
                val labels = metrics.map { hudString(it.label) }
                HudAdaptiveGrid(labels, maximum = 2, minCell = 80f) { index ->
                    val metric = metrics[index]
                    val isChecked = metric.key in prefs.selected
                    HudChoice(
                        label = labels[index],
                        selected = isChecked,
                        onClick = { store.select(metric.key, !isChecked) },
                        modifier = Modifier.weight(1f),
                        role = Role.Checkbox
                    )
                }
            }
        }
        HudAction(
            label = hudString(R.string.hud_reset),
            onClick = store::reset,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun HudPreferenceToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        color = HudElevated,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(0.8.dp, if (checked) HudCyan.copy(alpha = 0.4f) else HudBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (checked) Color.White else Color(0xFFCBD5E1)
            )
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.scale(0.82f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = HudCyan,
                    uncheckedThumbColor = HudMuted,
                    uncheckedTrackColor = HudCard,
                    uncheckedBorderColor = HudBorder
                )
            )
        }
    }
}
