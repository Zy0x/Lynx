package com.noir.lynx.feature.battery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.data.BatteryInfoStats
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*

@Composable
fun RealDischargeStepHistoryCard(
    stats: BatteryInfoStats,
    currentLevel: Int,
    modifier: Modifier = Modifier
) {
    val points = stats.drainHistoryPoints
    // Keep gesture coroutines alive across telemetry updates, reading fresh data.
    val latestPoints by rememberUpdatedState(points)
    val steps = stats.dischargeSteps
    var scrubbedIndex by remember { mutableStateOf<Int?>(null) }
    var historyTab by remember { mutableIntStateOf(0) }
    var isStepsExpanded by remember { mutableStateOf(false) }
    var isAppsExpanded by remember { mutableStateOf(false) }

    LynxCard(
        title = if (historyTab == 0) stringResource(R.string.battery_drain_history_time) else stringResource(R.string.battery_app_drain),
        icon = if (historyTab == 0) Icons.Default.Timeline else Icons.Default.Apps,
        accentColor = AccentCyan,
        modifier = modifier
    ) {
        // 2-Segment Switcher: Minimalis & Touch-Friendly
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(10.dp),
            color = BgElevated.copy(alpha = 0.5f),
            border = BorderStroke(0.8.dp, BorderSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Segment 0: Grafik Waktu
                Surface(
                    onClick = { historyTab = 0 },
                    shape = RoundedCornerShape(8.dp),
                    color = if (historyTab == 0) AccentCyan.copy(alpha = 0.18f) else Color.Transparent,
                    border = if (historyTab == 0) BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = if (historyTab == 0) AccentCyan else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.battery_graph_time_tab),
                            fontSize = 11.5.sp,
                            fontWeight = if (historyTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (historyTab == 0) TextPrimary else TextSecondary
                        )
                    }
                }

                // Segment 1: Penggunaan Aplikasi
                Surface(
                    onClick = { historyTab = 1 },
                    shape = RoundedCornerShape(8.dp),
                    color = if (historyTab == 1) AccentCyan.copy(alpha = 0.18f) else Color.Transparent,
                    border = if (historyTab == 1) BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = if (historyTab == 1) AccentCyan else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.battery_app_usage_tab),
                            fontSize = 11.5.sp,
                            fontWeight = if (historyTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (historyTab == 1) TextPrimary else TextSecondary
                        )
                    }
                }
            }
        }

        if (historyTab == 0) {
            if (points.size >= 2) {
                val activePoint = scrubbedIndex?.let { points.getOrNull(it) } ?: points.lastOrNull()

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, if (scrubbedIndex != null) AccentCyan.copy(alpha = 0.8f) else BorderGlass),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (activePoint?.isScreenOn == true) AccentGreen else AccentCyan)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (scrubbedIndex != null) stringResource(R.string.battery_scrubbed_step_detail) else stringResource(R.string.battery_last_recorded_step),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.5.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = activePoint?.stepDurationText?.ifBlank { "${activePoint.level}%" } ?: "$currentLevel%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (activePoint?.isScreenOn == true) AccentGreen.copy(alpha = 0.15f) else AccentCyan.copy(alpha = 0.15f),
                            border = BorderStroke(0.6.dp, if (activePoint?.isScreenOn == true) AccentGreen else AccentCyan)
                        ) {
                            Text(
                                text = if (activePoint?.isScreenOn == true) stringResource(R.string.battery_screen_on_tag) else stringResource(R.string.battery_screen_off_tag),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activePoint?.isScreenOn == true) AccentGreen else AccentCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgElevated.copy(alpha = 0.4f))
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    val n = latestPoints.size
                                    if (n < 2) return@detectHorizontalDragGestures
                                    val w = size.width - 36.dp.toPx()
                                    val stepX = w / (n - 1).coerceAtLeast(1)
                                    val idx = ((offset.x - 20.dp.toPx()) / stepX).toInt().coerceIn(0, n - 1)
                                    scrubbedIndex = idx
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    val n = latestPoints.size
                                    if (n < 2) return@detectHorizontalDragGestures
                                    val w = size.width - 36.dp.toPx()
                                    val stepX = w / (n - 1).coerceAtLeast(1)
                                    val idx = ((change.position.x - 20.dp.toPx()) / stepX).toInt().coerceIn(0, n - 1)
                                    scrubbedIndex = idx
                                },
                                onDragEnd = { scrubbedIndex = null },
                                onDragCancel = { scrubbedIndex = null }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val n = latestPoints.size
                                    if (n < 2) return@detectTapGestures
                                    val w = size.width - 36.dp.toPx()
                                    val stepX = w / (n - 1).coerceAtLeast(1)
                                    val idx = ((offset.x - 20.dp.toPx()) / stepX).toInt().coerceIn(0, n - 1)
                                    scrubbedIndex = idx
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val padLeft = 20.dp.toPx()
                        val padRight = 16.dp.toPx()
                        val padTop = 16.dp.toPx()
                        val padBottom = 24.dp.toPx()

                        val graphWidth = size.width - padLeft - padRight
                        val graphHeight = size.height - padTop - padBottom

                        val maxLvl = (points.maxOfOrNull { it.level } ?: 100).coerceAtMost(100)
                        val minLvl = ((points.minOfOrNull { it.level } ?: currentLevel) - 2).coerceAtLeast(0)
                        val lvlSpan = (maxLvl - minLvl).coerceAtLeast(5).toFloat()

                        for (gridStep in 0..3) {
                            val y = padTop + (gridStep / 3f) * graphHeight
                            drawLine(
                                color = Color.White.copy(alpha = 0.05f),
                                start = Offset(padLeft, y),
                                end = Offset(size.width - padRight, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        val n = points.size
                        val stepX = graphWidth / (n - 1).coerceAtLeast(1)

                        val coords = points.mapIndexed { idx, p ->
                            val x = padLeft + idx * stepX
                            val normY = ((maxLvl - p.level).toFloat() / lvlSpan).coerceIn(0f, 1f)
                            val y = padTop + normY * graphHeight
                            Offset(x, y)
                        }

                        val stripY = size.height - 10.dp.toPx()
                        val stripHeight = 5.dp.toPx()
                        for (i in 0 until n - 1) {
                            val x1 = coords[i].x
                            val x2 = coords[i + 1].x
                            val isSot = points[i + 1].isScreenOn
                            drawRect(
                                color = if (isSot) AccentGreen.copy(alpha = 0.85f) else AccentCyan.copy(alpha = 0.45f),
                                topLeft = Offset(x1, stripY),
                                size = androidx.compose.ui.geometry.Size(x2 - x1, stripHeight)
                            )
                        }

                        val strokePath = Path().apply {
                            moveTo(coords[0].x, coords[0].y)
                            for (i in 1 until coords.size) {
                                lineTo(coords[i].x, coords[i].y)
                            }
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
                            style = Stroke(
                                width = 2.4.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // Indikator Titik Aktif: Hanya 1 kursor interaktif saat disentuh / titik terakhir
                        val activeIdx = scrubbedIndex ?: (n - 1)
                        val activeCoord = coords.getOrNull(activeIdx) ?: coords.last()
                        val activePointItem = points.getOrNull(activeIdx)
                        val activeColor = if (activePointItem?.isScreenOn == true) AccentGreen else AccentCyan

                        drawLine(
                            color = activeColor.copy(alpha = 0.5f),
                            start = Offset(activeCoord.x, padTop),
                            end = Offset(activeCoord.x, size.height - padBottom),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                        drawCircle(color = activeColor.copy(alpha = 0.25f), radius = 7.dp.toPx(), center = activeCoord)
                        drawCircle(color = activeColor, radius = 3.5.dp.toPx(), center = activeCoord)
                    }
                }

                Spacer(Modifier.height(10.dp))
            }

            if (steps.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.battery_no_discharge_recorded),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Surface(
                    onClick = { isStepsExpanded = !isStepsExpanded },
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated.copy(alpha = 0.5f),
                    border = BorderStroke(0.8.dp, if (isStepsExpanded) AccentCyan.copy(alpha = 0.5f) else BorderGlass),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = if (isStepsExpanded) AccentCyan else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.battery_log_duration_per_drop),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isStepsExpanded) TextPrimary else TextSecondary,
                                fontSize = 11.5.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "(${steps.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.5.sp,
                                color = TextSecondary.copy(alpha = 0.7f)
                            )
                        }

                        Icon(
                            imageVector = if (isStepsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isStepsExpanded) "Tutup" else "Buka",
                            tint = if (isStepsExpanded) AccentCyan else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isStepsExpanded,
                    enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                    exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        steps.take(15).forEach { step ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BgCard,
                                border = BorderStroke(0.7.dp, BorderGlass),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${step.fromLevel}% ➔ ${step.toLevel}%",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = if (step.isScreenOn) AccentGreen.copy(alpha = 0.15f) else AccentCyan.copy(alpha = 0.12f),
                                            border = BorderStroke(0.5.dp, if (step.isScreenOn) AccentGreen.copy(alpha = 0.6f) else AccentCyan.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = if (step.isScreenOn) stringResource(R.string.battery_screen_on_tag) else if (step.isDeviceIdle) stringResource(R.string.battery_deep_doze_tag) else stringResource(R.string.battery_screen_off_tag),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (step.isScreenOn) AccentGreen else AccentCyan,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = step.durationText,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (step.isScreenOn) AccentGreen else TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Segment 1: Penggunaan Aplikasi & Sistem (Minimalis & Elegan)
            val apps = stats.topDrainApps
            if (apps.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgCard,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.battery_no_app_drain_recorded),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                val maxDrainPct = apps.firstOrNull()?.drainPercent ?: 100f
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    apps.take(5).forEach { item ->
                        AppDrainRow(item, maxDrainPct)
                    }

                    if (apps.size > 5) {
                        AnimatedVisibility(
                            visible = isAppsExpanded,
                            enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                            exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                apps.drop(5).forEach { item ->
                                    AppDrainRow(item, maxDrainPct)
                                }
                            }
                        }

                        Surface(
                            onClick = { isAppsExpanded = !isAppsExpanded },
                            shape = RoundedCornerShape(8.dp),
                            color = BgElevated.copy(alpha = 0.45f),
                            border = BorderStroke(0.8.dp, if (isAppsExpanded) AccentCyan.copy(alpha = 0.5f) else BorderGlass),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isAppsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isAppsExpanded) stringResource(R.string.battery_show_less) else stringResource(R.string.battery_show_more_apps, apps.size - 5),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentCyan,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
