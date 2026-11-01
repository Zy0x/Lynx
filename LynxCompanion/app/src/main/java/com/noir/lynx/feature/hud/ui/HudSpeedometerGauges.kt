package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
 * Cyber-Aero Supercar Digital Tachometer Speedometer (250° Racing Sweep).
 * Inspired by luxury supercar digital rev-counters (Porsche 911 GT3 / McLaren) and high-end gaming cockpits.
 *
 * Features:
 * - Ambient radial hub vignette separating the dial from wallpaper with deep contrast.
 * - Continuous dual-layer glowing plasma arc with smooth neon bloom and brush gradient.
 * - Precision graduated scale rail with 17 micro-ticks and hyper-crimson Redline sector (80%–100%).
 * - Luminous laser needle blade indicator with phosphor glow diamond tip.
 * - Uncaged digital cluster center with bold athletic monospace typography and floating status capsule.
 */
@Composable
internal fun HudArcTachometer(
    progress: Float,
    primaryValue: String,
    unit: String = "",
    subLabel: String? = null,
    overrideColor: Color? = null,
    isLarge: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "tachometer_progress"
    )
    val accentColor = overrideColor ?: hudWorkloadColor(animatedProgress)

    val widthDp = if (isLarge) 124.dp else 110.dp
    val heightDp = if (isLarge) 90.dp else 82.dp

    Box(
        modifier = modifier.size(width = widthDp, height = heightDp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Center of the 250° horseshoe arc
            val centerX = w / 2f
            val centerY = h * 0.50f

            val outerRadius = min(w / 2f, h * 0.74f) - 3.dp.toPx()
            val barThickness = if (isLarge) 6.5.dp.toPx() else 5.5.dp.toPx()
            val innerRadius = outerRadius - barThickness
            val arcCenterRadius = (outerRadius + innerRadius) / 2f
            val railRadius = innerRadius - 3.5.dp.toPx()

            val startAngle = 145f
            val totalSweep = 250f

            // 1. Ambient Radial Hub Backplate (Provides depth & isolation from wallpaper)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xCC101622), Color(0x600A0D14), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = innerRadius * 0.98f
                ),
                radius = innerRadius * 0.98f,
                center = Offset(centerX, centerY)
            )

            // 2. Inactive Track Bezel with Redline Zone
            // Normal Inactive Track (0% to 80%)
            drawArc(
                color = Color(0x22FFFFFF),
                startAngle = startAngle,
                sweepAngle = totalSweep * 0.80f,
                useCenter = false,
                topLeft = Offset(centerX - arcCenterRadius, centerY - arcCenterRadius),
                size = Size(arcCenterRadius * 2f, arcCenterRadius * 2f),
                style = Stroke(width = barThickness, cap = StrokeCap.Round)
            )
            // Redline Inactive Sector (80% to 100%)
            drawArc(
                color = Color(0x35FF1E56),
                startAngle = startAngle + totalSweep * 0.80f,
                sweepAngle = totalSweep * 0.20f,
                useCenter = false,
                topLeft = Offset(centerX - arcCenterRadius, centerY - arcCenterRadius),
                size = Size(arcCenterRadius * 2f, arcCenterRadius * 2f),
                style = Stroke(width = barThickness, cap = StrokeCap.Round)
            )

            // 3. Precision Graduated Scale Rail & Ticks
            drawArc(
                color = Color(0x30FFFFFF),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = Offset(centerX - railRadius, centerY - railRadius),
                size = Size(railRadius * 2f, railRadius * 2f),
                style = Stroke(width = 0.9.dp.toPx())
            )

            val tickCount = 17
            for (i in 0 until tickCount) {
                val fraction = i.toFloat() / (tickCount - 1)
                val tickAngle = startAngle + (totalSweep * fraction)
                val tickRad = Math.toRadians(tickAngle.toDouble())
                val cosT = cos(tickRad).toFloat()
                val sinT = sin(tickRad).toFloat()

                val isMajor = (i % 4 == 0) // 0%, 25%, 50%, 75%, 100%
                val isRedline = fraction >= 0.80f
                val tickLen = if (isMajor) 4.5.dp.toPx() else 2.5.dp.toPx()
                val r1 = railRadius - tickLen
                val r2 = railRadius

                val tickColor = when {
                    fraction >= 0.98f -> Color(0xFFFF1E56)
                    isRedline -> Color(0xFFFF5252).copy(alpha = 0.85f)
                    isMajor -> Color(0x75FFFFFF)
                    else -> Color(0x28FFFFFF)
                }

                drawLine(
                    color = tickColor,
                    start = Offset(centerX + r1 * cosT, centerY + r1 * sinT),
                    end = Offset(centerX + r2 * cosT, centerY + r2 * sinT),
                    strokeWidth = if (isMajor) 1.2.dp.toPx() else 0.8.dp.toPx()
                )
            }

            // 4. Dynamic Active Plasma Arc with Dual-Layer Glow
            val activeSweep = (totalSweep * animatedProgress).coerceAtLeast(0.1f)
            if (activeSweep > 1f) {
                // Outer Bloom / Neon Glow Layer
                drawArc(
                    color = accentColor.copy(alpha = 0.28f),
                    startAngle = startAngle,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    topLeft = Offset(centerX - arcCenterRadius, centerY - arcCenterRadius),
                    size = Size(arcCenterRadius * 2f, arcCenterRadius * 2f),
                    style = Stroke(width = barThickness + 4.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Core Active Plasma Stroke with Dynamic Color Grading
                val gradientColors = when {
                    animatedProgress < 0.55f -> listOf(
                        Color(0xFF00B0FF),
                        Color(0xFF00F5FF)
                    )
                    animatedProgress < 0.82f -> listOf(
                        Color(0xFF00F5FF),
                        Color(0xFFFFB300)
                    )
                    else -> listOf(
                        Color(0xFFFFB300),
                        Color(0xFFFF1E56)
                    )
                }
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = gradientColors,
                        center = Offset(centerX, centerY)
                    ),
                    startAngle = startAngle,
                    sweepAngle = activeSweep,
                    useCenter = false,
                    topLeft = Offset(centerX - arcCenterRadius, centerY - arcCenterRadius),
                    size = Size(arcCenterRadius * 2f, arcCenterRadius * 2f),
                    style = Stroke(width = barThickness, cap = StrokeCap.Round)
                )
            }

            // 5. Laser Needle Blade Pointer with Luminescent Core
            val currentAngle = startAngle + activeSweep
            val curRad = Math.toRadians(currentAngle.toDouble())
            val cosCur = cos(curRad).toFloat()
            val sinCur = sin(curRad).toFloat()

            // Needle Ray from inner hub to outer arc
            val needleStartR = railRadius * 0.42f
            val tipX = centerX + (arcCenterRadius * cosCur)
            val tipY = centerY + (arcCenterRadius * sinCur)

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, accentColor.copy(alpha = 0.70f)),
                    start = Offset(centerX + needleStartR * cosCur, centerY + needleStartR * sinCur),
                    end = Offset(tipX, tipY)
                ),
                start = Offset(centerX + needleStartR * cosCur, centerY + needleStartR * sinCur),
                end = Offset(tipX, tipY),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Luminous Diamond Tip on the Arc Track
            drawCircle(
                color = accentColor.copy(alpha = 0.50f),
                radius = 5.5.dp.toPx(),
                center = Offset(tipX, tipY)
            )
            drawCircle(
                color = Color.White,
                radius = 2.4.dp.toPx(),
                center = Offset(tipX, tipY)
            )
        }

        // 6. Uncaged Modern Digital Tachometer Core (No bulky dark box!)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = primaryValue,
                    fontSize = if (isLarge) 23.sp else 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    letterSpacing = (-0.5).sp,
                    lineHeight = if (isLarge) 24.sp else 19.sp
                )
                if (unit.isNotBlank()) {
                    Spacer(Modifier.width(2.5.dp))
                    Text(
                        text = unit,
                        fontSize = if (isLarge) 9.5.sp else 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = accentColor,
                        lineHeight = 12.sp,
                        modifier = Modifier.padding(bottom = 1.5.dp)
                    )
                }
            }
            if (!subLabel.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, accentColor.copy(alpha = 0.38f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
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
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = accentColor,
                            lineHeight = 8.5.sp
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
