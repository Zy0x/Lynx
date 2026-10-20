package com.noir.lynx.service

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxRepository

@Composable
fun FloatingHudContent(
    telemetry: LynxRepository.FloatingHudTelemetry,
    hudMode: Int = 0,
    activeStyle: Int = 1,
    pinMiniFps: Boolean = false,
    currentRefreshRate: Int = 120,
    isExpanded: Boolean,
    isEdgeDrawerOpen: Boolean = false,
    onToggleEdgeDrawer: (Boolean) -> Unit = {},
    isHudBenchmarking: Boolean = false,
    hudBenchmarkCountdown: Int = 60,
    hudBenchmarkSummary: com.noir.lynx.data.LynxBenchmarkResult? = null,
    onToggleExpand: () -> Unit,
    onSelectMode: (Int) -> Unit,
    onSelectStyle: (Int) -> Unit,
    onTogglePinMiniFps: () -> Unit,
    onClose: () -> Unit,
    onProfileSelect: (String) -> Unit,
    onBoostRam: () -> Unit,
    onCycleRefreshRate: () -> Unit,
    onStartHudBenchmark: () -> Unit = {},
    onDrag: (Float, Float) -> Unit,
    onDragVertical: (Float) -> Unit,
) {
    if (hudMode == 0) return
    run {
        // Mode 1: Classic Floating Window
        var showStylePicker by remember { mutableStateOf(false) }

        LaunchedEffect(isExpanded) {
            if (!isExpanded) {
                showStylePicker = false
            }
        }

        Column(modifier = Modifier.wrapContentSize().padding(4.dp)) {
            // Style Picker Dropdown
            AnimatedVisibility(visible = showStylePicker) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgHud,
                    border = BorderStroke(1.dp, BorderHud),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val styleNames = listOf("1.Pillar", "2.Ribbon", "3.Esport", "4.Tiles", "5.Deck", "6.Ghost")
                        styleNames.forEachIndexed { idx, label ->
                            val sId = idx + 1
                            val isSel = (activeStyle == sId)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) NeonCyan.copy(alpha = 0.3f) else Color(0x22FFFFFF))
                                    .clickable {
                                        onSelectStyle(sId)
                                        showStylePicker = false
                                    }
                                    .padding(horizontal = 5.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) NeonCyan else Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // Quick Dock-To-Edge Header Bar for Classic Floating Window
            Surface(
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                color = Color(0xAA0A0E14),
                border = BorderStroke(1.dp, BorderHud),
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Floating Window",
                        color = TextSlate,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeonCyan.copy(alpha = 0.2f))
                            .clickable { onSelectMode(0) }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(androidx.compose.ui.res.stringResource(com.noir.lynx.R.string.hud_dock_edge), color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Active Classic HUD Style (Styles 1-6)
            when (activeStyle) {
                2 -> HudTopRibbon(
                    telemetry = telemetry,
                    onDrag = onDrag,
                    onToggleStylePicker = { showStylePicker = !showStylePicker },
                    onClose = onClose
                )
                3 -> HudDualBlock(
                    telemetry = telemetry,
                    onDrag = onDrag,
                    onToggleStylePicker = { showStylePicker = !showStylePicker },
                    onClose = onClose,
                    onProfileSelect = onProfileSelect
                )
                4 -> HudQuadTiles(
                    telemetry = telemetry,
                    onDrag = onDrag,
                    onToggleStylePicker = { showStylePicker = !showStylePicker },
                    onClose = onClose,
                    onProfileSelect = onProfileSelect
                )
                5 -> HudDeckBanner(
                    telemetry = telemetry,
                    onDrag = onDrag,
                    onToggleStylePicker = { showStylePicker = !showStylePicker },
                    onClose = onClose,
                    onProfileSelect = onProfileSelect
                )
                6 -> HudGhostText(
                    telemetry = telemetry,
                    onDrag = onDrag,
                    onToggleStylePicker = { showStylePicker = !showStylePicker },
                    onClose = onClose
                )
                else -> HudVerticalPillar(
                    telemetry = telemetry,
                    isExpanded = isExpanded,
                    isHudBenchmarking = isHudBenchmarking,
                    hudBenchmarkCountdown = hudBenchmarkCountdown,
                    hudBenchmarkSummary = hudBenchmarkSummary,
                    onToggleExpand = onToggleExpand,
                    onToggleStylePicker = { showStylePicker = !showStylePicker },
                    onClose = onClose,
                    onProfileSelect = onProfileSelect,
                    onStartHudBenchmark = onStartHudBenchmark,
                    onDrag = onDrag
                )
            }
        }
    }
}
