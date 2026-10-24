package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.*
import kotlin.math.min

data class HudViewport(val widthDp: Float = 360f, val heightDp: Float = 720f) {
    val landscape get() = widthDp > heightDp
    val panelWidth get() = min(widthDp * if (landscape) 0.44f else 0.85f, if (landscape) 384f else 336f)
    val panelHeight get() = heightDp * if (landscape) 1f else 0.7f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HudMiniOverlay(
    snapshot: HudSnapshot,
    prefs: HudPreferences,
    viewport: HudViewport,
    onOpen: () -> Unit,
    onDrag: (Float) -> Unit,
    dockRight: Boolean = false,
    onDrag2D: ((Float, Float) -> Unit)? = null,
    onDragEnd: () -> Unit = {}
) {
    val openCallback by rememberUpdatedState(onOpen)
    val dragYCallback by rememberUpdatedState(onDrag)
    val dragXYCallback by rememberUpdatedState(onDrag2D)
    val dragEndCallback by rememberUpdatedState(onDragEnd)

    val selected = HudMetric.values().filter { it.key in prefs.selected }
    val fontScale = LocalDensity.current.fontScale
    val compactHeight = viewport.heightDp * .25f < 32f * fontScale + 8f
    val count = if (fontScale > 1.3f) 1 else 2
    val pages = selected.chunked(count)
    val pager = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    val showMetrics = prefs.pinned && selected.isNotEmpty()
    val miniWidth = min(viewport.widthDp * 0.62f, 248f).dp
    var isDragging by remember { mutableStateOf(false) }

    val handleShape = if (dockRight) {
        RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
    } else {
        RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
    }

    val edgeHandle: @Composable () -> Unit = {
        val description = hudString(R.string.hud_open)
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(72.dp)
                .semantics { contentDescription = description }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { openCallback() })
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            dragEndCallback()
                        },
                        onDragCancel = {
                            isDragging = false
                            dragEndCallback()
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val xy = dragXYCallback
                            if (xy != null) {
                                xy(amount.x, amount.y)
                            } else {
                                dragYCallback(amount.y)
                            }
                        }
                    )
                },
            contentAlignment = if (dockRight) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Surface(
                shape = handleShape,
                color = if (isDragging) HudElevated else HudCard.copy(alpha = 0.92f),
                border = BorderStroke(
                    width = if (isDragging) 1.4.dp else 1.dp,
                    color = if (isDragging) HudCyan else HudCyan.copy(alpha = 0.55f)
                ),
                shadowElevation = if (isDragging) 8.dp else 4.dp,
                modifier = Modifier
                    .width(if (isDragging) 16.dp else 12.dp)
                    .height(if (isDragging) 68.dp else 60.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(26.dp)
                            .clip(CircleShape)
                            .background(HudCyan)
                    )
                    Spacer(Modifier.height(4.dp))
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 1.dp)
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(HudCyan.copy(alpha = 0.7f))
                        )
                    }
                }
            }
        }
    }

    val metricsCapsule: @Composable RowScope.() -> Unit = {
        Surface(
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { openCallback() })
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            dragEndCallback()
                        },
                        onDragCancel = {
                            isDragging = false
                            dragEndCallback()
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val xy = dragXYCallback
                            if (xy != null) {
                                xy(amount.x, amount.y)
                            } else {
                                dragYCallback(amount.y)
                            }
                        }
                    )
                },
            color = HudBackground.copy(alpha = 0.94f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (isDragging) HudCyan else HudCyan.copy(alpha = 0.35f)
            ),
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 10.dp,
                    vertical = if (compactHeight) 2.dp else 6.dp
                )
            ) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        pages.getOrNull(page)?.forEach { metric ->
                            val reading = snapshot[metric]
                            val combined = hudString(R.string.hud_value, reading.text(), reading.unit)
                            val label = hudString(metric.label)
                            BoxWithConstraints(Modifier.weight(1f)) {
                                val fit = hudLabelWidth(listOf(combined), 13, numeric = true) <= maxWidth.value
                                val description = hudString(R.string.hud_metric_value_description, label, combined)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 38.dp)
                                        .semantics { contentDescription = description },
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (!compactHeight) {
                                        Text(
                                            text = if (fit) label else hudString(R.string.hud_unit_metric, reading.unit, label),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = HudMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = if (fit || compactHeight) combined else reading.text(),
                                        fontSize = 13.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (reading == snapshot[HudMetric.FPS]) HudCyan else Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
                if (pages.size > 1 && !compactHeight) {
                    val pageText = hudString(R.string.hud_page, pager.currentPage + 1, pages.size)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp)
                            .semantics { contentDescription = pageText },
                        horizontalArrangement = Arrangement.Center
                    ) {
                        pages.indices.forEach { i ->
                            Box(
                                Modifier
                                    .padding(horizontal = 2.dp)
                                    .size(
                                        width = if (i == pager.currentPage) 10.dp else 4.dp,
                                        height = 3.dp
                                    )
                                    .background(
                                        if (i == pager.currentPage) HudCyan else HudMuted.copy(alpha = 0.3f),
                                        RoundedCornerShape(1.5.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .width(if (showMetrics) miniWidth else 32.dp)
            .heightIn(max = (viewport.heightDp * 0.25f).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (dockRight) {
            if (showMetrics) metricsCapsule()
            edgeHandle()
        } else {
            edgeHandle()
            if (showMetrics) metricsCapsule()
        }
    }
}
