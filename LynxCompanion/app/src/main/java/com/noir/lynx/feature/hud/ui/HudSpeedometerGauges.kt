package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlin.math.min
import kotlin.math.sin

/** Dynamic Redline color interpolation based on workload stress (Cyan -> Amber -> Crimson). */
internal fun hudWorkloadColor(progress: Float): Color = when {
    progress < 0.58f -> Color(0xFF00E5FF) // Electric Ice Cyan (Optimal)
    progress < 0.82f -> Color(0xFFFB923C) // Solar Amber (Elevated Gaming Load)
    else -> Color(0xFFFF3366)             // Hyper Crimson (Peak Redline / Stress)
}

/**
 * Modern Segmented LED Cockpit Tachometer Speedometer (240° Extended Racing Sweep).
 * Inspired by supercar digital rev-counters and luxury gaming cockpit telemetry.
 *
 * Features:
 * - 16 Angled Segmented LED rev-counter bars with dynamic cyan -> amber -> crimson redline grading.
 * - Inactive segments remain visible as tactical dark LED elements.
 * - Concentric precision scale rail with 0%, 25%, 50%, 75%, 100% tick marks.
 * - Luminous diamond needle blade indicator with phosphor glow.
 * - Chamfered OLED digital center pod housing primary readout and status telemetry.
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
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "tachometer_progress"
    )
    val accentColor = overrideColor ?: hudWorkloadColor(animatedProgress)

    Box(
        modifier = modifier.size(width = 114.dp, height = 82.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Center of the 240° horseshoe arc
            val centerX = w / 2f
            val centerY = h * 0.49f

            val outerRadius = min(w / 2f, h * 0.72f) - 3.dp.toPx()
            val barThickness = 6.dp.toPx()
            val innerRadius = outerRadius - barThickness
            val railRadius = innerRadius - 3.5.dp.toPx()

            val startAngle = 150f
            val totalSweep = 240f
            val segmentCount = 16
            val segmentStep = totalSweep / segmentCount // 15° per segment slot
            val segmentBarAngle = segmentStep - 3.2f     // 11.8° bar, 3.2° gap

            // 1. Concentric Precision Scale Rail (Delicate hairline arc)
            drawArc(
                color = Color(0x28FFFFFF),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = Offset(centerX - railRadius, centerY - railRadius),
                size = Size(railRadius * 2f, railRadius * 2f),
                style = Stroke(width = 0.9.dp.toPx())
            )

            // 2. Scale Ticks (0%, 25%, 50%, 75%, 100%)
            val tickCount = 5
            for (i in 0 until tickCount) {
                val tickAngle = startAngle + (totalSweep * (i.toFloat() / (tickCount - 1)))
                val tickRad = Math.toRadians(tickAngle.toDouble())
                val cosT = cos(tickRad).toFloat()
                val sinT = sin(tickRad).toFloat()

                val r1 = railRadius - (if (i == 0 || i == 4) 4.5.dp.toPx() else 3.dp.toPx())
                val r2 = railRadius
                val tickColor = if (i == 4) Color(0xFFFF3366) else Color(0x40FFFFFF)

                drawLine(
                    color = tickColor,
                    start = Offset(centerX + r1 * cosT, centerY + r1 * sinT),
                    end = Offset(centerX + r2 * cosT, centerY + r2 * sinT),
                    strokeWidth = if (i == 4) 1.5.dp.toPx() else 1.dp.toPx()
                )
            }

            // 3. Segmented LED Tachometer Blocks
            val activeSegmentLimit = (animatedProgress * segmentCount)
            val arcCenterRadius = (outerRadius + innerRadius) / 2f

            for (k in 0 until segmentCount) {
                val segStart = startAngle + (k * segmentStep) + 1.6f
                val isSegmentActive = (k + 0.35f) <= activeSegmentLimit

                // Color gradient by zone:
                // k 0..9 (0%..56%): Cyan / Optimal
                // k 10..12 (57%..81%): Amber / Elevated
                // k 13..15 (82%..100%): Hyper Crimson / Redline
                val segmentActiveColor = when {
                    k < 10 -> overrideColor ?: Color(0xFF00E5FF)
                    k < 13 -> Color(0xFFFB923C)
                    else -> Color(0xFFFF3366)
                }

                val barColor = if (isSegmentActive) segmentActiveColor else Color(0x18FFFFFF)
                val barStroke = Stroke(
                    width = barThickness,
                    cap = StrokeCap.Round
                )

                drawArc(
                    color = barColor,
                    startAngle = segStart,
                    sweepAngle = segmentBarAngle,
                    useCenter = false,
                    topLeft = Offset(centerX - arcCenterRadius, centerY - arcCenterRadius),
                    size = Size(arcCenterRadius * 2f, arcCenterRadius * 2f),
                    style = barStroke
                )
            }

            // 4. Luminous Needle Indicator Blade / Diamond Tip
            val currentAngle = startAngle + (totalSweep * animatedProgress)
            val curRad = Math.toRadians(currentAngle.toDouble())
            val cosCur = cos(curRad).toFloat()
            val sinCur = sin(curRad).toFloat()

            // Point on active arc
            val tipX = centerX + (arcCenterRadius * cosCur)
            val tipY = centerY + (arcCenterRadius * sinCur)

            // Outer glow halo
            drawCircle(
                color = accentColor.copy(alpha = 0.45f),
                radius = 5.5.dp.toPx(),
                center = Offset(tipX, tipY)
            )
            // Diamond / Core highlight
            drawCircle(
                color = Color.White,
                radius = 2.6.dp.toPx(),
                center = Offset(tipX, tipY)
            )
        }

        // 5. Digital Cockpit Center Pod Plate (Beveled OLED Housing)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 8.dp)
                .background(
                    color = Color(0xB0080C14),
                    shape = RoundedCornerShape(7.dp)
                )
                .border(
                    width = 0.8.dp,
                    color = accentColor.copy(alpha = 0.32f),
                    shape = RoundedCornerShape(7.dp)
                )
                .padding(horizontal = 7.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
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
                        color = Color.White,
                        lineHeight = 18.sp
                    )
                    if (unit.isNotBlank()) {
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = unit,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = accentColor,
                            lineHeight = 12.sp
                        )
                    }
                }
                if (!subLabel.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(3.5.dp)
                                .background(accentColor, shape = RoundedCornerShape(2.dp))
                        )
                        Text(
                            text = subLabel,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = accentColor.copy(alpha = 0.95f),
                            lineHeight = 9.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern High-Tech Oscilloscope Telemetry Waveform.
 * Features:
 * - Oscilloscope Reticle Grid (baseline 16.6ms / 60Hz and 33.3ms / 30Hz guide lines).
 * - Multi-layer Glowing Bezier spline with vertical ambient phosphor gradient.
 * - Real-time CRT scanning beacon at latest telemetry frame.
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
                .height(48.dp),
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
            .height(48.dp)
    ) {
        val width = size.width
        val height = size.height
        val minMs = (history.minOrNull() ?: 0f).coerceAtLeast(0f)
        val maxMs = (history.maxOrNull() ?: 33.3f).coerceAtLeast(minMs + 8f)
        val range = (maxMs - minMs).coerceAtLeast(1f)

        // 1. Oscilloscope Reticle Grid Lines
        // Baseline (e.g. 16.6 ms for 60Hz target)
        val baselineY = height - ((baselineMs - minMs) / range).coerceIn(0f, 1f) * height
        drawLine(
            color = lineColor.copy(alpha = 0.35f),
            start = Offset(0f, baselineY),
            end = Offset(width, baselineY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        )

        // Upper reference line (33.3 ms / 30Hz limit)
        val upperLimitY = height - ((33.3f - minMs) / range).coerceIn(0f, 1f) * height
        if (upperLimitY != baselineY) {
            drawLine(
                color = Color(0x18FFFFFF),
                start = Offset(0f, upperLimitY),
                end = Offset(width, upperLimitY),
                strokeWidth = 0.8.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f), 0f)
            )
        }

        // Vertical division grid ticks
        val divCount = 4
        for (d in 1 until divCount) {
            val divX = width * (d.toFloat() / divCount)
            drawLine(
                color = Color(0x0EFFFFFF),
                start = Offset(divX, 0f),
                end = Offset(divX, height),
                strokeWidth = 0.8.dp.toPx()
            )
        }

        // 2. Smooth Interpolated Bezier Path
        val path = Path()
        val fillPath = Path()
        val stepX = width / (history.size - 1).coerceAtLeast(1)

        val points = history.mapIndexed { index, ms ->
            val x = index * stepX
            val y = height - ((ms - minMs) / range).coerceIn(0.08f, 0.92f) * height
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
                colors = listOf(lineColor.copy(alpha = 0.24f), Color.Transparent),
                startY = 0f,
                endY = height
            )
        )

        // Draw Outer Soft Glow Stroke
        drawPath(
            path = path,
            color = lineColor.copy(alpha = 0.32f),
            style = Stroke(width = 3.6.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw Core Crisp Stroke
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
        )

        // 3. Real-Time CRT Scanning Beacon at Latest Point
        drawCircle(
            color = lineColor.copy(alpha = 0.50f),
            radius = 4.5.dp.toPx(),
            center = lastPoint
        )
        drawCircle(
            color = Color.White,
            radius = 2.dp.toPx(),
            center = lastPoint
        )
    }
}
