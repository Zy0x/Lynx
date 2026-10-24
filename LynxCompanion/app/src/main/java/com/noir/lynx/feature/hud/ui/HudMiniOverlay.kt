package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/** Google Play Games Sidekick semi-oval translucent edge handle tab. */
@Composable
internal fun SidekickEdgeTab(
    dockRight: Boolean,
    modifier: Modifier = Modifier
) {
    val tabShape = if (dockRight) {
        RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
    } else {
        RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)
    }
    Surface(
        shape = tabShape,
        color = HudHandleSurface,
        modifier = modifier
            .width(15.dp)
            .height(54.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(22.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.88f))
            )
        }
    }
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
    val selected = HudMetric.values().filter { it.key in prefs.selected }
    val fontScale = LocalDensity.current.fontScale
    val compactHeight = viewport.heightDp * .25f < 32f * fontScale + 8f
    val count = if (fontScale > 1.3f) 1 else 2
    val pages = selected.chunked(count)
    val pager = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    val showMetrics = prefs.pinned && selected.isNotEmpty()
    val colsOnPage = min(selected.size.coerceAtLeast(1), count)
    val capsuleWidth = if (colsOnPage == 1) 92.dp else 162.dp
    val totalWidth = if (showMetrics) capsuleWidth + 20.dp else 32.dp
    val openDescription = hudString(R.string.hud_open)

    val edgeBar: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .width(if (showMetrics) 16.dp else 32.dp)
                .height(72.dp),
            contentAlignment = if (dockRight) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            SidekickEdgeTab(dockRight = dockRight)
        }
    }

    val metricsCapsule: @Composable () -> Unit = {
        Surface(
            modifier = Modifier.width(capsuleWidth),
            color = HudBackground,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 10.dp,
                    vertical = if (compactHeight) 3.dp else 6.dp
                )
            ) {
                HorizontalPager(
                    state = pager,
                    userScrollEnabled = pages.size > 1,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val pageMetrics = pages.getOrNull(page).orEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        pageMetrics.forEachIndexed { idx, metric ->
                            if (idx > 0) {
                                Box(
                                    modifier = Modifier
                                        .width(0.8.dp)
                                        .height(22.dp)
                                        .background(HudBorder)
                                )
                            }
                            val reading = snapshot[metric]
                            val combined = hudString(R.string.hud_value, reading.text(), reading.unit)
                            val label = hudString(metric.label)
                            val description = hudString(R.string.hud_metric_value_description, label, combined)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = description },
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (!compactHeight) {
                                    Text(
                                        text = label,
                                        fontSize = 9.5.sp,
                                        color = HudMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = combined,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (metric == HudMetric.FPS) HudCyan else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if (pages.size > 1 && !compactHeight) {
                    val pageText = hudString(R.string.hud_page, pager.currentPage + 1, pages.size)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .semantics { contentDescription = pageText },
                        horizontalArrangement = Arrangement.Center
                    ) {
                        pages.indices.forEach { i ->
                            Box(
                                Modifier
                                    .padding(horizontal = 2.dp)
                                    .size(
                                        width = if (i == pager.currentPage) 8.dp else 4.dp,
                                        height = 2.5.dp
                                    )
                                    .background(
                                        if (i == pager.currentPage) HudBlue else HudMuted.copy(alpha = 0.3f),
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
            .width(totalWidth)
            .heightIn(min = 72.dp, max = (viewport.heightDp * 0.25f).dp)
            .semantics { contentDescription = openDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (dockRight) Arrangement.End else Arrangement.Start
    ) {
        if (dockRight) {
            if (showMetrics) {
                metricsCapsule()
                Spacer(Modifier.width(3.dp))
            }
            edgeBar()
        } else {
            edgeBar()
            if (showMetrics) {
                Spacer(Modifier.width(3.dp))
                metricsCapsule()
            }
        }
    }
}
