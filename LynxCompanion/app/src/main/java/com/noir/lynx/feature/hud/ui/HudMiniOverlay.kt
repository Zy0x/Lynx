package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
internal fun HudMiniOverlay(snapshot: HudSnapshot, prefs: HudPreferences, viewport: HudViewport, onOpen: () -> Unit, onDrag: (Float) -> Unit) {
    val selected = HudMetric.values().filter { it.key in prefs.selected }
    val count = if (LocalDensity.current.fontScale > 1.3f) 1 else 2
    val pages = selected.chunked(count)
    val pager = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    val showMetrics = prefs.pinned && selected.isNotEmpty()
    val miniWidth = min(viewport.widthDp*0.6f, 240f).dp
    Row(Modifier.width(if (showMetrics) miniWidth else 48.dp).heightIn(max = (viewport.heightDp*0.25f).dp), verticalAlignment = Alignment.CenterVertically) {
        val description = hudString(R.string.hud_open)
        Box(
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = description }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onOpen() })
                }
                .pointerInput(Unit) {
                    var accDx = 0f
                    detectDragGestures(
                        onDragStart = { accDx = 0f },
                        onDragEnd = {
                            if (accDx > 12f) onOpen()
                            accDx = 0f
                        },
                        onDragCancel = { accDx = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            accDx += amount.x
                            onDrag(amount.y)
                            if (accDx > 16f) onOpen()
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Surface(
                shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp),
                color = HudCyan,
                shadowElevation = 3.dp,
                modifier = Modifier.width(4.5.dp).height(42.dp)
            ) {}
        }
        if (showMetrics) Surface(
            modifier = Modifier.weight(1f),
            color = HudCard,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.8.dp, HudBorder)
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pages.getOrNull(page)?.forEach { metric ->
                            Column(Modifier.weight(1f).clickable(onClick = onOpen).heightIn(min = 44.dp)) {
                                Text(hudString(metric.label), fontSize = 10.5.sp, color = HudMuted, maxLines = 1)
                                Text(
                                    hudString(R.string.hud_value, snapshot[metric].text(), snapshot[metric].unit),
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                if (pages.size > 1) {
                    val pageText = hudString(R.string.hud_page, pager.currentPage + 1, pages.size)
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp).semantics { contentDescription = pageText }, horizontalArrangement = Arrangement.Center) {
                        pages.indices.forEach { i ->
                            Box(
                                Modifier.padding(horizontal = 2.dp).size(width = if (i == pager.currentPage) 8.dp else 4.dp, height = 3.dp)
                                    .background(if (i == pager.currentPage) HudCyan else HudMuted.copy(alpha = 0.3f), RoundedCornerShape(1.5.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}
