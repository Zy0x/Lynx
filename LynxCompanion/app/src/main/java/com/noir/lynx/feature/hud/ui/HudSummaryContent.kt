package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.data.*

/** Keep the launch page short; detailed readings and configuration have their own destinations. */
@Composable
internal fun HudSummaryContent(
    snapshot: HudSnapshot, action: HudActionState,
    onProfile: (String) -> Unit, onDetails: () -> Unit, quickAccess: @Composable () -> Unit
) {
    HudGameReadings(snapshot, onDetails)
    HudExpandableSection(
        hudString(R.string.hud_profile),
        detail = hudString(hudProfileLabel(snapshot.profile))
    ) { HudProfiles(snapshot.profile, action.busy, onProfile) }
    Text(hudString(R.string.hud_quick_actions), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = HudMuted)
    quickAccess()
}

@Composable
private fun HudGameReadings(snapshot: HudSnapshot, onDetails: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onDetails,
        color = HudCard,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        val metrics = listOf(HudMetric.FPS, HudMetric.CPU_LOAD, HudMetric.GPU_LOAD)
        val labels = listOf(hudString(R.string.hud_fps_unit), hudString(R.string.hud_cpu), hudString(R.string.hud_gpu))
        val values = metrics.map { metric -> snapshot[metric].let {
            if (it.status == "valid") it.text() + if (metric == HudMetric.FPS) "" else " %" else "—"
        } }
        val widths = values.map { hudLabelWidth(listOf(it), 20, numeric = true) + 8f }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
            val cell: @Composable (Int, Modifier) -> Unit = { index, modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(labels[index], fontSize = 11.sp, color = HudMuted)
                    Text(values[index], fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace,
                        color = if (index == 0) HudCyan else Color.White)
                }
            }
            if (widths.all { it <= (maxWidth.value - 16f) / 3f }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    metrics.indices.forEach { cell(it, Modifier.weight(1f)) }
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.indices.forEach { cell(it, Modifier.fillMaxWidth()) }
            }
        }
    }
}

internal fun hudProfileLabel(profile: String?): Int = when (profile) {
    "powersave" -> R.string.hud_save
    "balance" -> R.string.hud_balanced
    "performance" -> R.string.hud_performance
    "auto" -> R.string.hud_auto
    "extreme" -> R.string.hud_extreme
    "dormant" -> R.string.hud_dormant
    else -> R.string.hud_profile_unavailable
}
