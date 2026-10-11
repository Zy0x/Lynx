package com.noir.lynx.feature.battery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.AccentGreen
import com.noir.lynx.designsystem.theme.BgCard
import com.noir.lynx.designsystem.theme.BorderGlass
import com.noir.lynx.designsystem.theme.TextSecondary

/**
 * Real-time Bezier Sparkline Waveform for charging current stream (Live 1s stream) with i18n support.
 */
@Composable
fun ChargingSparklineWaveform(
    samples: List<Float>,
    currentMa: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgCard,
        border = BorderStroke(0.8.dp, BorderGlass),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(accentColor, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.battery_current_realtime),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
                Text(
                    text = if (currentMa > 0) "+$currentMa mA" else "$currentMa mA",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (currentMa > 0) AccentGreen else AccentCyan
                )
            }
            Spacer(Modifier.height(6.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                if (samples.size < 2) {
                    drawLine(
                        color = accentColor.copy(alpha = 0.25f),
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 2f
                    )
                    return@Canvas
                }

                val minVal = (samples.minOrNull() ?: 0f) - 100f
                val maxVal = (samples.maxOrNull() ?: 1000f) + 100f
                val range = (maxVal - minVal).coerceAtLeast(200f)

                val points = samples.mapIndexed { index, value ->
                    val x = (index.toFloat() / (samples.size - 1).toFloat()) * size.width
                    val y = size.height - (((value - minVal) / range) * size.height)
                    Offset(x, y.coerceIn(4f, size.height - 4f))
                }

                val strokePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val cur = points[i]
                        val cx = (prev.x + cur.x) / 2f
                        cubicTo(cx, prev.y, cx, cur.y, cur.x, cur.y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(strokePath)
                    lineTo(points.last().x, size.height)
                    lineTo(points.first().x, size.height)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(accentColor.copy(alpha = 0.25f), Color.Transparent),
                        startY = 0f,
                        endY = size.height
                    )
                )

                drawPath(
                    path = strokePath,
                    color = accentColor,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                val lastPoint = points.last()
                drawCircle(
                    color = accentColor,
                    radius = 3.5.dp.toPx(),
                    center = lastPoint
                )
                drawCircle(
                    color = Color.White,
                    radius = 1.8.dp.toPx(),
                    center = lastPoint
                )
            }
        }
    }
}
