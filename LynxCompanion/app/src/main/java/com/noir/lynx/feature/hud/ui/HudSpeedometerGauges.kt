package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Dynamic Redline color interpolation based on workload stress (Cyan -> Amber -> Crimson). */
internal fun hudWorkloadColor(progress: Float): Color = when {
    progress < 0.60f -> Color(0xFF00E5FF) // Electric Ice Cyan (Optimal)
    progress < 0.85f -> Color(0xFFFB923C) // Solar Amber (Elevated Gaming Load)
    else -> Color(0xFFFF4D4D)             // Hyper Crimson (Peak Redline / Stress)
}

/**
 * Semi-Circular Tachometer Speedometer Gauge (180° Sweep).
 * Designed for luxury gaming cockpit telemetry with glowing needle and digital readout.
 */
@Composable
internal fun HudArcTachometer(
    progress: Float,
    primaryValue: String,
    unit: String = "",
    subLabel: String? = null,
    overrideColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 260),
        label = "tachometer_progress"
    )
    val accentColor = overrideColor ?: hudWorkloadColor(animatedProgress)

    Box(
        modifier = modifier.size(width = 104.dp, height = 66.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 7.dp.toPx()
            val arcPadding = strokeWidth / 2f + 2.dp.toPx()
            val arcWidth = size.width - (arcPadding * 2f)
            val arcHeight = (size.height - 4.dp.toPx()) * 2f
            val arcSize = Size(arcWidth, arcHeight)
            val topLeft = Offset(arcPadding, arcPadding)

            // 1. Subtle Background Radial Track (180° Sweep)
            drawArc(
                color = Color(0x26FFFFFF),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. Radial Tick Marks (0%, 25%, 50%, 75%, 100%)
            val centerX = arcPadding + (arcWidth / 2f)
            val centerY = arcPadding + (arcHeight / 2f)
            val outerRadius = arcWidth / 2f
            val innerRadius = outerRadius - strokeWidth - 3.dp.toPx()

            for (i in 0..4) {
                val tickAngle = Math.toRadians((180f + (i * 45f)).toDouble())
                val startX = centerX + (innerRadius * cos(tickAngle)).toFloat()
                val startY = centerY + (innerRadius * sin(tickAngle)).toFloat()
                val endX = centerX + ((innerRadius + 4.dp.toPx()) * cos(tickAngle)).toFloat()
                val endY = centerY + ((innerRadius + 4.dp.toPx()) * sin(tickAngle)).toFloat()
                drawLine(
                    color = Color(0x38FFFFFF),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 1.2.dp.toPx()
                )
            }

            // 3. Active Progress Sweep Arc
            val activeSweep = (180f * animatedProgress).coerceAtLeast(0.1f)
            drawArc(
                color = accentColor,
                startAngle = 180f,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 4. Glowing Needle Tip Dot
            val needleAngleRad = Math.toRadians((180f + activeSweep).toDouble())
            val dotRadius = arcWidth / 2f
            val dotX = centerX + (dotRadius * cos(needleAngleRad)).toFloat()
            val dotY = centerY + (dotRadius * sin(needleAngleRad)).toFloat()

            // Outer glow halo
            drawCircle(
                color = accentColor.copy(alpha = 0.40f),
                radius = 5.dp.toPx(),
                center = Offset(dotX, dotY)
            )
            // Bright solid core
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = Offset(dotX, dotY)
            )
        }

        // 5. Digital Center Readout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((-1).dp),
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = primaryValue,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
                if (unit.isNotBlank()) {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = unit,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = accentColor
                    )
                }
            }
            if (!subLabel.isNullOrBlank()) {
                Text(
                    text = subLabel,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = accentColor.copy(alpha = 0.90f)
                )
            }
        }
    }
}

/**
 * Smooth Bezier Waveform for in-game Frame Time & Oscilloscope telemetry.
 */
@Composable
internal fun HudWaveformCanvas(
    history: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF00E5FF),
    baselineMs: Float = 16.6f
) {
    if (history.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "— Standby Telemetry —",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = HudMuted
            )
        }
        return
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        val width = size.width
        val height = size.height
        val minMs = (history.minOrNull() ?: 0f).coerceAtLeast(0f)
        val maxMs = (history.maxOrNull() ?: 33.3f).coerceAtLeast(minMs + 8f)
        val range = (maxMs - minMs).coerceAtLeast(1f)

        // 1. Target Baseline (e.g. 16.6 ms for 60Hz)
        val baselineY = height - ((baselineMs - minMs) / range).coerceIn(0f, 1f) * height
        drawLine(
            color = Color(0x28FFFFFF),
            start = Offset(0f, baselineY),
            end = Offset(width, baselineY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        )

        // 2. Smooth Interpolated Path
        val path = Path()
        val fillPath = Path()
        val stepX = width / (history.size - 1).coerceAtLeast(1)

        val points = history.mapIndexed { index, ms ->
            val x = index * stepX
            val y = height - ((ms - minMs) / range).coerceIn(0.05f, 0.95f) * height
            Offset(x, y)
        }

        path.moveTo(points.first().x, points.first().y)
        fillPath.moveTo(points.first().x, height)
        fillPath.lineTo(points.first().x, points.first().y)

        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val midX = (p0.x + p1.x) / 2f
            path.quadraticBezierTo(p0.x, p0.y, midX, (p0.y + p1.y) / 2f)
            fillPath.quadraticBezierTo(p0.x, p0.y, midX, (p0.y + p1.y) / 2f)
        }
        val lastPoint = points.last()
        path.lineTo(lastPoint.x, lastPoint.y)
        fillPath.lineTo(lastPoint.x, lastPoint.y)
        fillPath.lineTo(lastPoint.x, height)
        fillPath.close()

        // Draw Ambient Waveform Fill
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.22f), Color.Transparent),
                startY = 0f,
                endY = height
            )
        )

        // Draw Waveform Stroke
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
