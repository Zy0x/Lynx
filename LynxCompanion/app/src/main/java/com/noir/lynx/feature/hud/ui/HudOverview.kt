package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
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
    val fpsLow = snapshot[HudMetric.FPS_LOW]

    // 1. Hero Card: Frame & Display Telemetry
    HudSection(
        title = hudString(R.string.hud_group_frame),
        badge = if (displayHz.status == "valid") "${displayHz.text()} ${displayHz.unit}" else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left: Big FPS Hero Counter
            Surface(
                modifier = Modifier.weight(1.05f),
                color = HudElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.8.dp, if (fps.status == "valid") HudCyan.copy(alpha = 0.35f) else HudBorder)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = fps.text(),
                            fontSize = 30.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (fps.status == "valid") HudCyan else HudMuted
                        )
                        Text(
                            text = "FPS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = HudMuted,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Text(
                        text = if (fpsLow.status == "valid") {
                            "${hudString(HudMetric.FPS_LOW.label)}: ${fpsLow.text()} FPS"
                        } else {
                            hudString(R.string.hud_metric_fps)
                        },
                        fontSize = 10.5.sp,
                        color = HudMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: Frame Time & Display Hz Mini Tiles
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HudMetricTile(
                    label = hudString(HudMetric.FRAME_TIME.label),
                    reading = frameTime,
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = Color.White
                )
                HudMetricTile(
                    label = hudString(HudMetric.DISPLAY_HZ.label),
                    reading = displayHz,
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = HudBlue
                )
            }
        }

        // Optional Mini Frame Sparkline if history exists
        if (snapshot.history.size > 2) {
            MiniFrameSparkline(snapshot.history)
        }
    }

    // 2. Side-by-Side Processor Bento Cards (CPU & GPU)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HudProcessorBentoCard(
            title = hudString(R.string.hud_cpu),
            load = snapshot[HudMetric.CPU_LOAD],
            clock = snapshot[HudMetric.CPU_CLOCK],
            temp = snapshot[HudMetric.CPU_TEMP].takeIf { it.status == "valid" } ?: snapshot[HudMetric.SOC_TEMP],
            accentColor = HudCyan,
            modifier = Modifier.weight(1f)
        )
        HudProcessorBentoCard(
            title = hudString(R.string.hud_gpu),
            load = snapshot[HudMetric.GPU_LOAD],
            clock = snapshot[HudMetric.GPU_CLOCK],
            temp = snapshot[HudMetric.GPU_TEMP],
            accentColor = HudBlue,
            modifier = Modifier.weight(1f)
        )
    }

    // 3. Performance Profile Switcher Card
    HudProfiles(
        current = snapshot.profile,
        busy = action.busy,
        onProfile = onProfile
    )

    // 4. Quick Action Bar
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HudAction(
            label = hudString(R.string.hud_capture_short),
            enabled = !action.busy,
            onClick = onCapture,
            modifier = Modifier.weight(1f),
            icon = Icons.Default.CameraAlt,
            accentColor = HudCyan
        )
        HudAction(
            label = hudString(R.string.hud_boost_ram),
            enabled = !action.busy,
            onClick = onBoostRam,
            modifier = Modifier.weight(1f),
            icon = Icons.Default.CleaningServices,
            accentColor = HudBlue
        )
        HudAction(
            label = hudString(R.string.hud_tools),
            enabled = !action.busy,
            onClick = onTools,
            modifier = Modifier.weight(0.85f),
            icon = Icons.Default.Tune,
            accentColor = HudCyan
        )
    }
}

@Composable
private fun HudProcessorBentoCard(
    title: String,
    load: HudReading,
    clock: HudReading,
    temp: HudReading,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val loadPercent = ((load.value?.takeIf { load.status == "valid" } ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    Surface(
        modifier = modifier,
        color = HudCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.9.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(11.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor)
                    )
                    Text(
                        text = title,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
                Text(
                    text = if (load.status == "valid") "${load.text()}%" else "—",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (load.status == "valid") Color.White else HudMuted
                )
            }

            LinearProgressIndicator(
                progress = loadPercent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accentColor,
                trackColor = HudElevated
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (clock.status == "valid") "${clock.text()} MHz" else "— MHz",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE2E8F0),
                    maxLines = 1
                )
                if (temp.status == "valid") {
                    Text(
                        text = "${temp.text()}°C",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HudMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniFrameSparkline(history: List<Float>) {
    val high = (history.maxOrNull() ?: 1f).coerceAtLeast(1f)
    val low = history.minOrNull() ?: 0f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudElevated.copy(alpha = 0.65f),
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(0.6.dp, HudBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = hudString(R.string.hud_frame_chart),
                    fontSize = 10.sp,
                    color = HudMuted
                )
                Text(
                    text = hudString(R.string.hud_chart_range, "%.1f".format(low), "%.1f".format(high)),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HudCyan
                )
            }
            Spacer(Modifier.height(3.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
            ) {
                val path = Path()
                val fillPath = Path()
                history.forEachIndexed { i, v ->
                    val x = i.toFloat() / history.lastIndex.coerceAtLeast(1) * size.width
                    val y = size.height - (v / high).coerceIn(0f, 1f) * (size.height - 2.dp.toPx()) - 1.dp.toPx()
                    if (i == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, size.height)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(size.width, size.height)
                fillPath.close()
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(HudCyan.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
                drawPath(path = path, color = HudCyan, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
    }
}
