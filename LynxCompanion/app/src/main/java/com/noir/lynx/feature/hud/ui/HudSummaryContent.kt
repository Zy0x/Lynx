package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
    Text(hudString(R.string.hud_quick_actions), fontSize = 12.sp, color = HudMuted)
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
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val fps = snapshot[HudMetric.FPS]
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(fps.text(), fontSize = 28.sp, fontFamily = FontFamily.Monospace, color = HudCyan,
                    modifier = Modifier.weight(1f))
                Text(hudString(R.string.hud_fps_short), fontSize = 12.sp, color = HudMuted)
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    hudString(R.string.hud_monitor), tint = HudMuted, modifier = Modifier.size(18.dp)
                )
            }
            val metrics = listOf(HudMetric.CPU_LOAD, HudMetric.GPU_LOAD)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.forEachIndexed { index, metric ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(hudString(if (index == 0) R.string.hud_cpu else R.string.hud_gpu), fontSize = 12.sp, color = HudMuted)
                        val reading = snapshot[metric]
                        Text(if (reading.status == "valid") hudString(R.string.hud_value, reading.text(), reading.unit) else "—",
                            fontSize = 12.sp, color = Color.White)
                    }
                }
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
