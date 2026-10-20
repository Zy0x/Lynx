package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.data.*

@Composable
internal fun HudOverview(snapshot: HudSnapshot, action: HudActionState, onProfile: (String) -> Unit, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(hudString(R.string.hud_metric_fps), color = HudMuted, fontSize = 12.sp)
                HudValue(snapshot[HudMetric.FPS], large = true)
                if (!compact) Text(statusText(snapshot[HudMetric.FPS].status), color = HudMuted, fontSize = 12.sp)
            }
            if (compact) {
                listOf(HudMetric.FRAME_TIME,HudMetric.DISPLAY_HZ).forEach { metric ->
                    Column(Modifier.weight(1f)) {
                        Text(hudString(metric.label),color = HudMuted,fontSize = 12.sp)
                        Text(snapshot[metric].text(),fontSize = 18.sp)
                        Text(snapshot[metric].unit,color = HudMuted,fontSize = 12.sp)
                    }
                }
            } else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(HudMetric.FRAME_TIME, HudMetric.DISPLAY_HZ).forEach { metric ->
                    Column {
                        Text(hudString(metric.label), color = HudMuted, fontSize = 12.sp)
                        Text(hudString(R.string.hud_value, snapshot[metric].text(), snapshot[metric].unit), fontSize = 18.sp, color = Color.White)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Gauge(hudString(R.string.hud_cpu), snapshot[HudMetric.CPU_LOAD], snapshot[HudMetric.CPU_CLOCK], Modifier.weight(1f), compact)
            Gauge(hudString(R.string.hud_gpu), snapshot[HudMetric.GPU_LOAD], snapshot[HudMetric.GPU_CLOCK], Modifier.weight(1f), compact)
        }
        HudProfiles(snapshot.profile, action.busy, onProfile)
    }
}

@Composable
private fun Gauge(label: String, load: HudReading, clock: HudReading, modifier: Modifier, compact: Boolean) {
    val sweep by animateFloatAsState(targetValue = load.value?.takeIf { load.status == "valid" }?.toFloat()?.coerceIn(0f,100f) ?: 0f,
        animationSpec = tween(200), label = "hud_gauge_sweep")
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = HudCard) {
        Column(Modifier.padding(if (compact) 4.dp else 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(if (compact) 60.dp else 80.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                    drawArc(HudMuted.copy(alpha = 0.16f), 140f, 260f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    if (load.status == "valid") drawArc(HudCyan, 140f, sweep*2.6f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(hudString(R.string.hud_value, load.text(), load.unit), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(label, fontSize = 12.sp, color = HudMuted)
                }
            }
            Text(hudString(R.string.hud_value, clock.text(), clock.unit), fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun HudProfiles(current: String?, busy: Boolean, onProfile: (String) -> Unit) {
    var more by remember { mutableStateOf(false) }
    var extreme by remember { mutableStateOf(false) }
    val choices = listOf("powersave" to R.string.hud_save, "balance" to R.string.hud_balanced, "performance" to R.string.hud_performance)
    Column {
        val profileName = when(current) { "powersave" -> R.string.hud_save; "balance" -> R.string.hud_balanced; "performance" -> R.string.hud_performance; "auto" -> R.string.hud_auto; "extreme" -> R.string.hud_extreme; "dormant" -> R.string.hud_dormant; else -> R.string.hud_unsupported }
        Text(hudString(R.string.hud_profile_current, hudString(profileName)), fontSize = 12.sp, color = HudMuted)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            choices.forEach { (id,label) ->
                TextButton(onClick = { onProfile(id) }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(2.dp), colors = ButtonDefaults.textButtonColors(contentColor = if (current == id) HudCyan else HudMuted)) {
                    Text(hudString(label), fontSize = 12.sp, fontWeight = if (current == id) FontWeight.Bold else FontWeight.Normal)
                }
            }
            Box {
                IconButton(onClick = { more = true }, enabled = !busy, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreHoriz, hudString(R.string.hud_more)) }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(text = { Text(hudString(R.string.hud_auto)) }, onClick = { more = false; onProfile("auto") })
                    DropdownMenuItem(text = { Text(hudString(R.string.hud_extreme)) }, onClick = { more = false; extreme = true })
                }
            }
        }
        if (extreme) {
            // Render confirmation inside the bounded overlay, without a focus-stealing dialog window.
            Surface(color = Color(0xFF30231C), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(hudString(R.string.hud_extreme_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(hudString(R.string.hud_extreme_warning), fontSize = 12.sp)
                    Row {
                        HudAction(hudString(R.string.hud_cancel), onClick = { extreme = false })
                        HudAction(hudString(R.string.hud_confirm), enabled = !busy, onClick = { extreme = false; onProfile("extreme") })
                    }
                }
            }
        }
    }
}
