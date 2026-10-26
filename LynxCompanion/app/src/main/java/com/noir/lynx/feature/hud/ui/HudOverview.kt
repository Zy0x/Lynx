package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.data.*

@Composable
internal fun HudOverview(
    snapshot: HudSnapshot,
    action: HudActionState,
    onProfile: (String) -> Unit,
    onCapture: () -> Unit,
    onTools: () -> Unit,
    onBoostRam: () -> Unit = {}
) {
    val fps = snapshot[HudMetric.FPS]
    val frameTime = snapshot[HudMetric.FRAME_TIME]
    val displayHz = snapshot[HudMetric.DISPLAY_HZ]

    HudSection(title = hudString(R.string.hud_group_frame), accentColor = HudCyan) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(fps.text(), fontSize = 26.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text("FPS", fontSize = 12.sp, color = HudMuted, modifier = Modifier.padding(bottom = 4.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(hudString(R.string.hud_metric_frame_time), fontSize = 12.sp, color = HudMuted)
                Text(if (frameTime.status == "valid") hudString(R.string.hud_value, frameTime.text(), frameTime.unit) else "—", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = Color.White)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(hudString(R.string.hud_metric_display_hz), fontSize = 12.sp, color = HudMuted)
                Text(if (displayHz.status == "valid") hudString(R.string.hud_value, displayHz.text(), displayHz.unit) else "—", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = Color.White)
            }
        }
        val history = snapshot.history.filter { it.isFinite() && it > 0f }
        if (frameTime.status == "valid" && history.size > 1) {
            val high = history.maxOrNull()!!.coerceAtLeast(1f)
            Canvas(Modifier.fillMaxWidth().height(18.dp)) {
                val path = Path()
                history.forEachIndexed { i, v ->
                    val x = i.toFloat() / history.lastIndex * size.width
                    val y = size.height - (v / high).coerceIn(0f, 1f) * size.height
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, HudCyan, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cpu: @Composable (Modifier) -> Unit = { modifier ->
            HudProcessorCard(hudString(R.string.hud_cpu), snapshot[HudMetric.CPU_LOAD], snapshot[HudMetric.CPU_CLOCK],
                snapshot[HudMetric.CPU_TEMP].takeIf { it.status == "valid" } ?: snapshot[HudMetric.SOC_TEMP], HudBlue, onTools, modifier, hudString(if (snapshot[HudMetric.CPU_TEMP].status == "valid") R.string.hud_cpu else R.string.hud_soc_short))
        }
        val gpu: @Composable (Modifier) -> Unit = { modifier ->
            HudProcessorCard(hudString(R.string.hud_gpu), snapshot[HudMetric.GPU_LOAD], snapshot[HudMetric.GPU_CLOCK],
                snapshot[HudMetric.GPU_TEMP], HudCyan, onTools, modifier, hudString(R.string.hud_gpu))
        }
        if (maxWidth.value >= 192f * fontScale + 8f) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { cpu(Modifier.weight(1f)); gpu(Modifier.weight(1f)) }
        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { cpu(Modifier.fillMaxWidth()); gpu(Modifier.fillMaxWidth()) }
    }

    // 4. Sidekick Profile Switcher Card
    HudProfiles(
        current = snapshot.profile,
        busy = action.busy,
        onProfile = onProfile
    )
}

@Composable
private fun HudProcessorCard(
    title: String,
    load: HudReading,
    clock: HudReading,
    temp: HudReading,
    ringColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tempLabel: String = title
) {
    val gaugeSize = (40f * LocalDensity.current.fontScale).dp
    val pct = ((load.value?.takeIf { load.status == "valid" } ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = HudCard,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier.size(gaugeSize),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 2.5.dp.toPx()
                    drawArc(
                        color = Color(0xFF282E3A),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * pct,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = if (load.status == "valid") "${load.text()}%" else "—",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE8EAED),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(if (clock.status == "valid") "${clock.text()} MHz" else "—", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color.White)
            Text(if (temp.status == "valid") hudString(R.string.hud_sensor_temp_value, tempLabel, temp.text()) else "—", fontSize = 12.sp, color = HudMuted)

        }
    }
}
