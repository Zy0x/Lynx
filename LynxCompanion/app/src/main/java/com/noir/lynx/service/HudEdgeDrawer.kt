package com.noir.lynx.service

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxRepository
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun HudEdgeDrawer(
    telemetry: LynxRepository.FloatingHudTelemetry,
    activeStyle: Int,
    pinMiniFps: Boolean,
    currentRefreshRate: Int,
    isHudBenchmarking: Boolean,
    hudBenchmarkCountdown: Int,
    hudBenchmarkSummary: com.noir.lynx.data.LynxBenchmarkResult?,
    onSelectMode: (Int) -> Unit,
    onSelectStyle: (Int) -> Unit,
    onTogglePinMiniFps: () -> Unit,
    onProfileSelect: (String) -> Unit,
    onStartHudBenchmark: () -> Unit,
    onBoostRam: () -> Unit,
    onCycleRefreshRate: () -> Unit,
    onClose: () -> Unit,
    onDragVertical: (Float) -> Unit,
) {
    var isDrawerOpen by remember { mutableStateOf(false) }
    var showStylePicker by remember { mutableStateOf(false) }
    var lastTouchTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Generous auto-collapse (12s) to prevent jarring psychological cut-offs
    LaunchedEffect(isDrawerOpen, lastTouchTime) {
        if (isDrawerOpen) {
            delay(12000L)
            isDrawerOpen = false
            showStylePicker = false
        }
    }

    if (!isDrawerOpen) {
        // ── STATE 1: COLLAPSED EDGE HANDLE (Anti-Back Gesture Protected) ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .wrapContentSize()
                .systemGestureExclusion()
                .pointerInput(Unit) {
                    while (true) {
                        awaitPointerEventScope {
                            val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: return@awaitPointerEventScope
                            var isDragging = false
                            var isVerticalDrag = false
                            var lastY = down.position.y
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!isDragging) {
                                        // Clean single tap: expand drawer
                                        isDrawerOpen = true
                                        lastTouchTime = System.currentTimeMillis()
                                    }
                                    break
                                }
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y

                                if (!isDragging && (kotlin.math.abs(dx) > 10f || kotlin.math.abs(dy) > 10f)) {
                                    isDragging = true
                                    isVerticalDrag = kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.2f
                                }

                                if (isDragging) {
                                    change.consume()
                                    if (isVerticalDrag) {
                                        // Smooth vertical repositioning along bezel without opening drawer
                                        val deltaY = change.position.y - lastY
                                        lastY = change.position.y
                                        onDragVertical(deltaY)
                                    } else if (dx > 30f) {
                                        // Deliberate horizontal pull outward: expand drawer
                                        isDrawerOpen = true
                                        lastTouchTime = System.currentTimeMillis()
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            // Refined Tactical Bezel Handle (Ergonomic Pill)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp))
                    .background(Color(0xEB0D131C))
                    .border(
                        BorderStroke(1.2.dp, Color(0x3800E5FF)),
                        shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Sleek Glowing Vertical Spine
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(26.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(NeonCyan, Color.White, NeonCyan)
                                )
                            )
                    )
                    // Visual Affordance Chevron
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Tarik Bilah",
                        tint = NeonCyan.copy(alpha = 0.8f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Optional Glanceable Mini-FPS Pill
            if (pinMiniFps) {
                Spacer(Modifier.width(4.dp))
                val fpsVal = telemetry.renderFps
                val statusDotColor = when {
                    fpsVal >= 55 -> NeonGreen
                    fpsVal >= 30 -> NeonGold
                    else -> NeonRed
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xEB0C1118),
                    border = BorderStroke(1.dp, BorderHud.copy(alpha = 0.5f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.clickable {
                        isDrawerOpen = true
                        lastTouchTime = System.currentTimeMillis()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Text(
                            text = "$fpsVal FPS",
                            color = statusDotColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    } else {
        // ── STATE 2: SLIDE-OUT GAME BAR (Zen Bento Redesign) ──
        Surface(
            shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
            color = Color(0xF50B1017),
            border = BorderStroke(1.dp, Color(0x2800E5FF)),
            shadowElevation = 18.dp,
            modifier = Modifier
                .width(296.dp)
                .systemGestureExclusion()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            lastTouchTime = System.currentTimeMillis()
                            // Swiping left closes drawer smoothly
                            if (dragAmount.x < -20f) {
                                isDrawerOpen = false
                            } else {
                                onDragVertical(dragAmount.y)
                            }
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .padding(10.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        lastTouchTime = System.currentTimeMillis()
                    },
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                // 1. Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(NeonCyan)
                        )
                        Text(
                            "LYNX DECK",
                            color = NeonCyan,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Switch to Classic Floating Window
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x18FFFFFF))
                                .clickable {
                                    isDrawerOpen = false
                                    onSelectMode(1)
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("Window", color = TextSlate, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Style Dropdown Trigger
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonCyan.copy(alpha = 0.15f))
                                .border(BorderStroke(0.8.dp, NeonCyan.copy(alpha = 0.4f)), RoundedCornerShape(6.dp))
                                .clickable {
                                    showStylePicker = !showStylePicker
                                    lastTouchTime = System.currentTimeMillis()
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("S$activeStyle ▾", color = NeonCyan, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }

                        // Complete Shutdown HUD Action
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0x18FFFFFF))
                                .clickable {
                                    isDrawerOpen = false
                                    onClose()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PowerSettingsNew,
                                contentDescription = "Matikan HUD",
                                tint = TextSlate,
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        // Prominent, Comfortable Close / Collapse Drawer Button
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FF5252))
                                .clickable {
                                    isDrawerOpen = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Tutup Bilah",
                                tint = NeonRed,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                // Style Picker Dropdown
                AnimatedVisibility(
                    visible = showStylePicker,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xEE121824),
                        border = BorderStroke(1.dp, BorderHud),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val styleNames = listOf("Pillar", "Ribbon", "Esport", "Tiles", "Deck", "Ghost")
                            styleNames.forEachIndexed { idx, _ ->
                                val sId = idx + 1
                                val isSel = (activeStyle == sId)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(if (isSel) NeonCyan.copy(alpha = 0.3f) else Color(0x18FFFFFF))
                                        .clickable {
                                            onSelectStyle(sId)
                                            showStylePicker = false
                                            lastTouchTime = System.currentTimeMillis()
                                        }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        color = if (isSel) NeonCyan else Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Hero Performance Card (Live FPS & Frametime)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x55111923),
                    border = BorderStroke(1.dp, Color(0x20FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "${telemetry.renderFps}",
                                    color = NeonGreen,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    "FPS",
                                    color = NeonGreen,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }

                            val stabilityText = if (telemetry.fps1PercentLow >= 50f) "STABIL" else "AKTIF"
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0x1A00E676),
                                border = BorderStroke(0.6.dp, NeonGreen.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", telemetry.frametimeMs)} ms · $stabilityText",
                                    color = TextSlate,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Frametime Sparkline
                        FrametimeSparklineCanvas(
                            history = telemetry.frametimeHistory,
                            heightDp = 12,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }

                // 3. Ergonomic 2x2 Bento Stat Tiles
                val gpuTempStr = if (telemetry.gpuTempC > 0f) "${telemetry.gpuTempC.toInt()}°C" else if (telemetry.battTempC > 0f) "${telemetry.battTempC.toInt()}°C" else "--"
                val cpuTempStr = if (telemetry.cpuTempC > 0f) "${telemetry.cpuTempC.toInt()}°C" else if (telemetry.battTempC > 0f) "${telemetry.battTempC.toInt()}°C" else "--"

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    // Row 1: GPU & CPU
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        HudBentoTile(
                            label = "GPU",
                            dotColor = NeonMagenta,
                            primaryText = if (telemetry.gpuFreqMhz > 0) "${telemetry.gpuFreqMhz} MHz" else "Otomatis",
                            subText = "${telemetry.gpuLoadPct}% Load · $gpuTempStr",
                            modifier = Modifier.weight(1f)
                        )
                        HudBentoTile(
                            label = "CPU",
                            dotColor = NeonCyan,
                            primaryText = telemetry.cpuCoresSummary.ifEmpty { if (telemetry.cpuFreqMhz > 0) "${telemetry.cpuFreqMhz} MHz" else "Multi-Core" },
                            subText = "${telemetry.cpuLoadPct}% Load · $cpuTempStr",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 2: BATERAI & RAM
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        val batDotColor = if (telemetry.battTempC > 42f) NeonRed else NeonOrange
                        HudBentoTile(
                            label = "BATERAI",
                            dotColor = batDotColor,
                            primaryText = "${telemetry.battTempC.toInt()}°C · ${telemetry.battLevelPct}%",
                            subText = "${telemetry.battCurrentMa} mA · ${String.format(Locale.US, "%.1f", telemetry.battWatt)}W",
                            modifier = Modifier.weight(1f)
                        )
                        HudBentoTile(
                            label = "MEMORI",
                            dotColor = NeonGold,
                            primaryText = if (telemetry.ramUsedGb > 0f) "${telemetry.ramUsedGb} / ${telemetry.ramTotalGb}G" else "--",
                            subText = "${telemetry.ramPct}% · ZRAM ${telemetry.zramUsedGb}G",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 4. Kernel Performance Mode Selector
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "PROFIL PERFORMA",
                        color = TextSlate,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val profiles = listOf(
                            "balance" to "BAL",
                            "performance" to "PERF",
                            "extreme" to "EXT",
                            "powersave" to "PWR"
                        )
                        profiles.forEach { (pKey, pLabel) ->
                            val isSel = telemetry.activeProfile.equals(pKey, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = if (isSel) NeonCyan.copy(alpha = 0.22f) else Color(0x14FFFFFF),
                                border = BorderStroke(1.dp, if (isSel) NeonCyan else Color(0x1FFFFFFF)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onProfileSelect(pKey)
                                        lastTouchTime = System.currentTimeMillis()
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = pLabel,
                                        color = if (isSel) NeonCyan else Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Gaming Quick Tools & Benchmark
                HudQuickToolsAndBenchmark(
                    currentRefreshRate = currentRefreshRate,
                    pinMiniFps = pinMiniFps,
                    isHudBenchmarking = isHudBenchmarking,
                    hudBenchmarkCountdown = hudBenchmarkCountdown,
                    hudBenchmarkSummary = hudBenchmarkSummary,
                    onBoostRam = onBoostRam,
                    onCycleRefreshRate = onCycleRefreshRate,
                    onTogglePinMiniFps = onTogglePinMiniFps,
                    onStartHudBenchmark = onStartHudBenchmark,
                    onInteraction = { lastTouchTime = System.currentTimeMillis() }
                )
            }
        }
    }
}

@Composable
private fun HudBentoTile(
    label: String,
    dotColor: Color,
    primaryText: String,
    subText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0x55111923),
        border = BorderStroke(1.dp, Color(0x18FFFFFF)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = label,
                    color = TextSlate,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            Text(
                text = primaryText,
                color = Color.White,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subText,
                color = TextSlate.copy(alpha = 0.8f),
                fontSize = 7.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
