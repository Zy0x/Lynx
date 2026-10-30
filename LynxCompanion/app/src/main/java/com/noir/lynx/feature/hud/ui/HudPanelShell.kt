package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.dp
import com.noir.lynx.R
import com.noir.lynx.feature.hud.HudActionState
import com.noir.lynx.feature.hud.data.*

/** Fixed navigation and header surround an independently scrolling page. */
@Composable
internal fun HudPanelShell(
    viewport: HudViewport, dockRight: Boolean, page: HudPage, title: String,
    snapshot: HudSnapshot, action: HudActionState, bypassActive: Boolean,
    canNavigateBack: Boolean, onBack: () -> Unit, onSelect: (HudPage) -> Unit,
    onClose: () -> Unit, content: @Composable () -> Unit
) {
    var info by remember { mutableStateOf<HudInfoData?>(null) }
    val shape = if (dockRight) RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 6.dp, bottomEnd = 6.dp)
        else RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 24.dp, bottomEnd = 24.dp)
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
    val width = viewport.panelWidth
    val labels = HudPage.values().map { hudString(it.label) }
    val layout = HudLayoutPolicy.resolve(width, hudLabelWidth(labels))
    val drawer: @Composable () -> Unit = {
        CompositionLocalProvider(LocalHudInfo provides { info = it }) {
            Box(Modifier.width(width.dp).height(viewport.panelHeight.dp).graphicsLayer {
                translationX = (1f - entrance.value) * size.width * if (dockRight) 1f else -1f
                alpha = .4f + .6f * entrance.value
                clip = true
            }) {
                Surface(Modifier.fillMaxSize(), shape = shape, color = HudBackground,
                    border = BorderStroke(1.dp, HudBorder)) {
                    Column {
                        HudPanelHeader(title, snapshot[HudMetric.BATTERY_LEVEL], snapshot[HudMetric.BATTERY_TEMP],
                            snapshot.charging, bypassActive, canNavigateBack, onBack, onClose)
                        HorizontalDivider(color = HudBorder, modifier = Modifier.padding(horizontal = 12.dp))
                        HudRecordingBar()
                        if (!layout.useRail) HudTopTabBar(page, onSelect, Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        Row(Modifier.weight(1f).fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (layout.useRail && !dockRight) HudNavigationRail(page, layout.railWidth, onSelect, dockRight = false)
                            Column(Modifier.weight(1f)) {
                                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                                if (action.busy || action.message != null || !action.detailMessage.isNullOrBlank()) {
                                    Box(Modifier.heightIn(max = (viewport.panelHeight * .3f).dp)
                                        .verticalScroll(rememberScrollState())) {
                                        HudFeedbackBanner(
                                            if (action.busy) hudString(R.string.hud_pending)
                                            else action.message?.let { hudString(it) } ?: hudString(R.string.hud_done),
                                            action.detailMessage, action.busy, action.actionType
                                        )
                                    }
                                }
                            }
                            if (layout.useRail && dockRight) HudNavigationRail(page, layout.railWidth, onSelect, dockRight = true)
                        }
                    }
                }
                info?.let { HudInfoModal(it, shape) { info = null } }
            }
        }
    }
    drawer()
}
