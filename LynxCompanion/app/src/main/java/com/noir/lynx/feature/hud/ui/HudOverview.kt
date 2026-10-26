package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    val ram = snapshot[HudMetric.RAM_USED]
    val isCleaningRam = action.actionType == "ram" && action.busy

    // 1. Luxury Hero FPS & Oscilloscope Card
    HudSection(
        title = hudString(R.string.hud_group_frame),
        badge = if (fps.status == "valid") "LIVE SYNC" else null,
        accentColor = HudCyan,
        showPlayIcon = true
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Main FPS Metric Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = fps.text(),
                        fontSize = 32.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "FPS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HudCyan,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }

                // Status indicator pill
                Surface(
                    color = Color(0xFF161B26),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF222B3D))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .then(
                                    if (fps.status == "valid") Modifier else Modifier
                                )
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawCircle(if (fps.status == "valid") HudCyan else HudOrange)
                            }
                        }
                        Text(
                            text = if (fps.status == "valid") "REALTIME" else "WAITING",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = if (fps.status == "valid") HudCyan else HudMuted
                        )
                    }
                }
            }

            // Secondary Stats Pill Row (Frame Time & Display Refresh Rate)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HudMiniStatPill(
                    label = hudString(R.string.hud_metric_frame_time),
                    value = if (frameTime.status == "valid") hudString(R.string.hud_value, frameTime.text(), frameTime.unit) else "—",
                    accentColor = HudBlue,
                    modifier = Modifier.weight(1f)
                )
                HudMiniStatPill(
                    label = hudString(R.string.hud_metric_display_hz),
                    value = if (displayHz.status == "valid") hudString(R.string.hud_value, displayHz.text(), displayHz.unit) else "—",
                    accentColor = HudCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            // Real-Time Frame Time Oscilloscope Sparkline
            val history = snapshot.history.filter { it.isFinite() && it > 0f }
            if (frameTime.status == "valid" && history.size > 1) {
                val high = history.maxOrNull()!!.coerceAtLeast(1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val strokePath = Path()
                        val fillPath = Path()

                        history.forEachIndexed { i, v ->
                            val x = i.toFloat() / history.lastIndex * size.width
                            val y = size.height - (v / high).coerceIn(0.05f, 0.95f) * size.height
                            if (i == 0) {
                                strokePath.moveTo(x, y)
                                fillPath.moveTo(x, size.height)
                                fillPath.lineTo(x, y)
                            } else {
                                strokePath.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                        }
                        fillPath.lineTo(size.width, size.height)
                        fillPath.close()

                        // Gradient fill underneath curve
                        drawPath(
                            fillPath,
                            Brush.verticalGradient(
                                listOf(HudCyan.copy(alpha = 0.22f), Color.Transparent)
                            )
                        )
                        // Neon stroke line
                        drawPath(
                            strokePath,
                            HudCyan,
                            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }

    // 2. Hardware Tri-Cluster (CPU, GPU, RAM)
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cpu: @Composable (Modifier) -> Unit = { modifier ->
            HudProcessorCard(
                title = hudString(R.string.hud_cpu),
                load = snapshot[HudMetric.CPU_LOAD],
                clock = snapshot[HudMetric.CPU_CLOCK],
                temp = snapshot[HudMetric.CPU_TEMP].takeIf { it.status == "valid" } ?: snapshot[HudMetric.SOC_TEMP],
                ringColor = HudBlue,
                onClick = onTools,
                modifier = modifier,
                tempLabel = hudString(if (snapshot[HudMetric.CPU_TEMP].status == "valid") R.string.hud_cpu else R.string.hud_soc_short)
            )
        }
        val gpu: @Composable (Modifier) -> Unit = { modifier ->
            HudProcessorCard(
                title = hudString(R.string.hud_gpu),
                load = snapshot[HudMetric.GPU_LOAD],
                clock = snapshot[HudMetric.GPU_CLOCK],
                temp = snapshot[HudMetric.GPU_TEMP],
                ringColor = HudCyan,
                onClick = onTools,
                modifier = modifier,
                tempLabel = hudString(R.string.hud_gpu)
            )
        }
        val ramCard: @Composable (Modifier) -> Unit = { modifier ->
            HudMemoryCard(
                title = hudString(R.string.hud_metric_ram),
                used = ram,
                isCleaning = isCleaningRam,
                onClick = onBoostRam,
                modifier = modifier
            )
        }

        if (maxWidth.value >= 265f * fontScale) {
            // High-density 3-column row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                cpu(Modifier.weight(1f))
                gpu(Modifier.weight(1f))
                ramCard(Modifier.weight(1f))
            }
        } else {
            // Adaptive 2-column + full-width RAM row
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    cpu(Modifier.weight(1f))
                    gpu(Modifier.weight(1f))
                }
                ramCard(Modifier.fillMaxWidth())
            }
        }
    }

    // 3. Sidekick Profile Switcher Card
    HudProfiles(
        current = snapshot.profile,
        busy = action.busy,
        onProfile = onProfile
    )
}

@Composable
private fun HudMiniStatPill(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF131722),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF1E2638))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = HudMuted
            )
            Text(
                text = value,
                fontSize = 12.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
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
    val gaugeSize = (38f * LocalDensity.current.fontScale).dp
    val pct = ((load.value?.takeIf { load.status == "valid" } ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)

    Surface(
        onClick = onClick,
        modifier = modifier,
        color = HudCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF1E2638))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier.size(gaugeSize),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 2.8.dp.toPx()
                    drawArc(
                        color = Color(0xFF222836),
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
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE8EAED)
            )
            Text(
                text = if (clock.status == "valid") "${clock.text()} MHz" else "—",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
            Text(
                text = if (temp.status == "valid") hudString(R.string.hud_sensor_temp_value, tempLabel, temp.text()) else "—",
                fontSize = 10.5.sp,
                color = HudMuted
            )
        }
    }
}

@Composable
private fun HudMemoryCard(
    title: String,
    used: HudReading,
    isCleaning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gaugeSize = (38f * LocalDensity.current.fontScale).dp

    // Infinite sweep animation for RAM Cleaning pulse
    val infiniteTransition = rememberInfiniteTransition(label = "RamCleanSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RamSweepAngle"
    )

    Surface(
        onClick = onClick,
        modifier = modifier,
        color = HudCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (isCleaning) HudCyan.copy(alpha = 0.6f) else Color(0xFF1E2638))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier.size(gaugeSize),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 2.8.dp.toPx()
                    drawArc(
                        color = Color(0xFF222836),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    if (isCleaning) {
                        // Dynamic animated sweep ring during RAM clean
                        drawArc(
                            color = HudCyan,
                            startAngle = sweepAngle,
                            sweepAngle = 120f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    } else {
                        // Static percentage / estimate arc
                        val pct = ((used.value ?: 2.5) / 6.0).toFloat().coerceIn(0.2f, 0.9f)
                        drawArc(
                            color = HudCyan,
                            startAngle = -90f,
                            sweepAngle = 360f * pct,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = if (isCleaning) HudCyan else Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE8EAED)
            )
            Text(
                text = if (used.status == "valid") "${used.text()} GB" else "—",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
            Text(
                text = if (isCleaning) hudString(R.string.hud_pending) else hudString(R.string.hud_clean_ram_short),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (isCleaning) HudCyan else HudBlue
            )
        }
    }
}
