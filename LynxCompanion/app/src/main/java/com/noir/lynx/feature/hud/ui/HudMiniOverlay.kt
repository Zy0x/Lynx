package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.feature.hud.data.*
import com.noir.lynx.core.locale.LynxText as Text
import kotlin.math.min

/** Narrow visual grip inside a full 48 dp touch target. */
@Composable
internal fun SidekickEdgeTab(dockRight: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.width(48.dp).height(72.dp), contentAlignment = if (dockRight) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(color = HudElevated, shape = RoundedCornerShape(8.dp), modifier = Modifier.width(12.dp).height(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.width(3.dp).height(24.dp).background(HudCyan, CircleShape))
            }
        }
    }
}

/** One measured reading per page prevents labels, values and units from competing for half-width cells. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HudMiniOverlay(
    snapshot: HudSnapshot, prefs: HudPreferences, viewport: HudViewport,
    onOpen: () -> Unit, dockRight: Boolean = false
) {
    val metrics = HudMiniPolicy.metrics(prefs.selected)
    val labels = metrics.map { hudString(it.label) }
    val values = metrics.map { snapshot[it].let { reading ->
        if (reading.status == "valid") hudString(R.string.hud_value, reading.text(), reading.unit) else "—"
    } }
    val budget = min(216f, (viewport.widthDp - 56f).coerceAtLeast(1f))
    val valueWidth = hudLabelWidth(values + "-9999.9 mA", 14, numeric = true) + 8f
    val width = (maxOf(hudLabelWidth(labels), valueWidth) + 20f).coerceIn(min(96f, budget), budget)
    val pager = rememberPagerState(pageCount = { metrics.size })
    val description = hudString(R.string.hud_open)
    Row(
        Modifier.width((48f + if (prefs.pinned) width + 4f else 0f).dp)
            .heightIn(min = min(72f, viewport.heightDp).dp, max = min(viewport.heightDp, maxOf(72f, viewport.heightDp * .35f)).dp)
            .semantics { contentDescription = description }
            .clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val capsule: @Composable () -> Unit = {
            Surface(Modifier.width(width.dp), color = HudBackground, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    HorizontalPager(pager, userScrollEnabled = metrics.size > 1, modifier = Modifier.fillMaxWidth()) { page ->
                        val index = page.coerceIn(metrics.indices)
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(labels[index], fontSize = 12.sp, color = HudMuted)
                            Text(values[index], fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold, color = if (metrics[index] == HudMetric.FPS) HudCyan else Color.White,
                                maxLines = 1, softWrap = false)
                        }
                    }
                    if (metrics.size > 1) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
                        metrics.indices.forEach { index ->
                            Box(Modifier.padding(horizontal = 2.dp).size(if (index == pager.currentPage) 8.dp else 4.dp, 3.dp)
                                .background(if (index == pager.currentPage) HudCyan else HudMuted, CircleShape))
                        }
                    }
                }
            }
        }
        if (!dockRight) SidekickEdgeTab(false)
        if (prefs.pinned) {
            if (!dockRight) Spacer(Modifier.width(4.dp))
            capsule()
            if (dockRight) Spacer(Modifier.width(4.dp))
        }
        if (dockRight) SidekickEdgeTab(true)
    }
}
