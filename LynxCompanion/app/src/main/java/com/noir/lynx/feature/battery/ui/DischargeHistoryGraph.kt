package com.noir.lynx.feature.battery.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.noir.lynx.data.BatteryDrainPoint
import com.noir.lynx.data.DischargeStepItem
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.AccentGreen
import com.noir.lynx.designsystem.theme.BgElevated

/** Immutable boundary lets the graph skip battery-only updates when its history is unchanged. */
@Immutable
data class BatteryHistorySeries(
    val points: List<BatteryDrainPoint>,
    val steps: List<DischargeStepItem>
)

@Composable
fun DischargeHistoryGraph(
    series: BatteryHistorySeries,
    scrubbedIndex: Int?,
    onScrubbedIndexChange: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val points = series.points
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(155.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BgElevated.copy(alpha = 0.4f))
            .pointerInput(series) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val n = series.points.size
                        if (n < 2) return@detectHorizontalDragGestures
                        val graphWidth = size.width - 36.dp.toPx()
                        val stepX = graphWidth / (n - 1).coerceAtLeast(1)
                        val index = ((offset.x - 20.dp.toPx()) / stepX).toInt().coerceIn(0, n - 1)
                        onScrubbedIndexChange(index)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val n = series.points.size
                        if (n < 2) return@detectHorizontalDragGestures
                        val graphWidth = size.width - 36.dp.toPx()
                        val stepX = graphWidth / (n - 1).coerceAtLeast(1)
                        val index = ((change.position.x - 20.dp.toPx()) / stepX).toInt().coerceIn(0, n - 1)
                        onScrubbedIndexChange(index)
                    },
                    onDragEnd = { onScrubbedIndexChange(null) },
                    onDragCancel = { onScrubbedIndexChange(null) }
                )
            }
            .pointerInput(series) {
                detectTapGestures(
                    onTap = { offset ->
                        val n = series.points.size
                        if (n < 2) return@detectTapGestures
                        val graphWidth = size.width - 36.dp.toPx()
                        val stepX = graphWidth / (n - 1).coerceAtLeast(1)
                        val index = ((offset.x - 20.dp.toPx()) / stepX).toInt().coerceIn(0, n - 1)
                        onScrubbedIndexChange(index)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pointCount = points.size
            if (pointCount < 2) return@Canvas

            val padLeft = 20.dp.toPx()
            val padRight = 16.dp.toPx()
            val padTop = 16.dp.toPx()
            val padBottom = 24.dp.toPx()
            val graphWidth = size.width - padLeft - padRight
            val graphHeight = size.height - padTop - padBottom
            val maxLevel = (points.maxOfOrNull { it.level } ?: 100).coerceAtMost(100)
            val minLevel = ((points.minOfOrNull { it.level } ?: 100) - 2).coerceAtLeast(0)
            val levelSpan = (maxLevel - minLevel).coerceAtLeast(5).toFloat()
            val stepX = graphWidth / (pointCount - 1).coerceAtLeast(1)

            for (gridStep in 0..3) {
                val y = padTop + (gridStep / 3f) * graphHeight
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(padLeft, y),
                    end = Offset(size.width - padRight, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val coords = points.mapIndexed { index, point ->
                val x = padLeft + index * stepX
                val normalizedY = ((maxLevel - point.level).toFloat() / levelSpan).coerceIn(0f, 1f)
                Offset(x, padTop + normalizedY * graphHeight)
            }

            val stripY = size.height - 10.dp.toPx()
            val stripHeight = 5.dp.toPx()
            for (index in 0 until pointCount - 1) {
                val start = coords[index]
                val end = coords[index + 1]
                drawRect(
                    color = if (points[index + 1].isScreenOn) AccentGreen.copy(alpha = 0.85f) else AccentCyan.copy(alpha = 0.45f),
                    topLeft = Offset(start.x, stripY),
                    size = androidx.compose.ui.geometry.Size(end.x - start.x, stripHeight)
                )
            }

            val strokePath = Path().apply {
                moveTo(coords.first().x, coords.first().y)
                for (index in 1 until coords.size) lineTo(coords[index].x, coords[index].y)
            }
            val fillPath = Path().apply {
                addPath(strokePath)
                lineTo(coords.last().x, size.height - padBottom)
                lineTo(coords.first().x, size.height - padBottom)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(AccentCyan.copy(alpha = 0.25f), Color.Transparent),
                    startY = padTop,
                    endY = size.height - padBottom
                )
            )
            drawPath(
                path = strokePath,
                color = AccentCyan,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val activeIndex = (scrubbedIndex ?: pointCount - 1).coerceIn(0, pointCount - 1)
            val activePoint = points[activeIndex]
            val activeCoord = coords[activeIndex]
            val activeColor = if (activePoint.isScreenOn) AccentGreen else AccentCyan
            drawLine(
                color = activeColor.copy(alpha = 0.5f),
                start = Offset(activeCoord.x, padTop),
                end = Offset(activeCoord.x, size.height - padBottom),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
            drawCircle(activeColor.copy(alpha = 0.25f), 7.dp.toPx(), activeCoord)
            drawCircle(activeColor, 3.5.dp.toPx(), activeCoord)
        }
    }
}
