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
import androidx.compose.material.icons.filled.ChevronLeft
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HudEdgeDrawer — Infinix Game Space inspired Edge Bar overlay.
 * Docked cleanly to the bezel when collapsed, and expands into a full-featured,
 * psychologically calming in-game gaming suite with real-time telemetry,
 * performance profiles, and tactile game tools (RAM Boost, Hz switch, Pin FPS, Benchmark).
 */
@Composable
fun HudEdgeDrawer(
    telemetry: LynxRepository.FloatingHudTelemetry,
    activeStyle: Int,
    pinMiniFps: Boolean,
    currentRefreshRate: Int,
    isDrawerOpen: Boolean = false,
    onToggleDrawer: (Boolean) -> Unit = {},
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
    var showStylePicker by remember { mutableStateOf(false) }

    LaunchedEffect(isDrawerOpen) {
        if (!isDrawerOpen) {
            showStylePicker = false
        }
    }

    if (!isDrawerOpen) {
        // ── STATE 1: COLLAPSED BEZEL DOCK (Precision Glowing Handle) ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .wrapContentSize()
                .systemGestureExclusion()
                .padding(end = 14.dp)
                .pointerInput(Unit) {
                    val touchSlop = 14.dp.toPx()
                    while (true) {
                        awaitPointerEventScope {
                            val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: return@awaitPointerEventScope
                            down.consume()
                            var isDragging = false
                            var isVerticalDrag = false
                            var lastY = down.position.y
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) {
                                    if (!isDragging) {
                                        onToggleDrawer(true)
                                    }
                                    break
                                }
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y

                                if (!isDragging && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                                    isDragging = true
                                    isVerticalDrag = kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.1f
                                }

                                if (isDragging) {
                                    if (isVerticalDrag) {
                                        val deltaY = change.position.y - lastY
                                        lastY = change.position.y
                                        onDragVertical(deltaY)
                                    } else if (dx > touchSlop * 1.15f) {
                                        onToggleDrawer(true)
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            // Bezel Docked Pill Handle
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(Color(0xE60A0E17))
                    .border(
                        BorderStroke(0.8.dp, NeonCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                    )
                    .padding(start = 2.dp, end = 3.dp, top = 4.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(NeonCyan, Color.White, NeonCyan)
                            )
                        )
                )
            }

            // Glanceable Bezel FPS Badge (if Pinned)
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
                    color = Color(0xEB0A0E17),
                    border = BorderStroke(0.8.dp, BorderHud.copy(alpha = 0.5f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.clickable { onToggleDrawer(true) }
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
        // ── STATE 2: INFINIX GAME SPACE SLIDE-OUT BAR ──
        Surface(
            shape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
            color = InfinixBg,
            border = BorderStroke(1.dp, InfinixCardBorder),
            shadowElevation = 20.dp,
            modifier = Modifier
                .width(292.dp)
                .systemGestureExclusion()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount.x < -18f) {
                                onToggleDrawer(false)
                            } else {
                                onDragVertical(dragAmount.y)
                            }
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .padding(11.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        showStylePicker = false
                    },
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                // 1. Infinix Game Space Top Header (Clock, Battery, Actions)
                val currentTimeStr = remember(telemetry.renderFps) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Brand & Clock Pod
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(InfinixAccent)
                        )
                        Text(
                            text = "GAME SPACE",
                            color = InfinixAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp
                        )
                        Text(
                            text = "· $currentTimeStr",
                            color = InfinixTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Right Controls (Battery, Window, Style, Close)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Switch to Floating Window Mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x18FFFFFF))
                                .clickable {
                                    onToggleDrawer(false)
                                    onSelectMode(1)
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("Jendela", color = InfinixTextSecondary, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Style Dropdown Trigger (S1-S6)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonCyan.copy(alpha = 0.15f))
                                .border(BorderStroke(0.8.dp, NeonCyan.copy(alpha = 0.4f)), RoundedCornerShape(6.dp))
                                .clickable {
                                    showStylePicker = !showStylePicker
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text("S$activeStyle ▾", color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }

                        // Power Off Overlay
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0x18FFFFFF))
                                .clickable {
                                    onToggleDrawer(false)
                                    onClose()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PowerSettingsNew,
                                contentDescription = "Matikan HUD",
                                tint = InfinixTextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        }

                        // Collapse Drawer Button
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FF5252))
                                .clickable {
                                    onToggleDrawer(false)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = "Tutup Bilah",
                                tint = NeonRed,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Style Picker Dropdown (when tapped)
                AnimatedVisibility(
                    visible = showStylePicker,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xEE111824),
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

                // 2. Hero Performance Pod (FPS & Frametime)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = InfinixCard,
                    border = BorderStroke(1.dp, InfinixCardBorder),
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
                                    text = "${telemetry.renderFps}",
                                    color = InfinixAccent,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "FPS",
                                    color = InfinixAccent,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                if (currentRefreshRate > 0) {
                                    Text(
                                        text = "· ${currentRefreshRate}Hz",
                                        color = InfinixTextMuted,
                                        fontSize = 8.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }

                            val stabilityText = if (telemetry.fps1PercentLow >= 50f) "STABIL" else "AKTIF"
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = InfinixAccent.copy(alpha = 0.15f),
                                border = BorderStroke(0.6.dp, InfinixAccent.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", telemetry.frametimeMs)} ms · $stabilityText",
                                    color = InfinixAccent,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Smooth Frametime Canvas Sparkline
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

                    // Row 2: BATERAI & MEMORI
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

                // 4. Kernel Performance Profile Switcher (Infinix Game Mode Buttons)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "MODE PERFORMA",
                        color = InfinixTextSecondary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val profiles = listOf(
                            "powersave" to "PWR",
                            "balance" to "BAL",
                            "performance" to "PERF",
                            "extreme" to "EXT"
                        )
                        profiles.forEach { (pKey, pLabel) ->
                            val isSel = telemetry.activeProfile.equals(pKey, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(7.dp),
                                color = if (isSel) NeonCyan.copy(alpha = 0.22f) else InfinixCard,
                                border = BorderStroke(1.dp, if (isSel) NeonCyan else InfinixCardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onProfileSelect(pKey)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = pLabel,
                                        color = if (isSel) NeonCyan else InfinixTextPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Infinix Gaming Tools & Benchmark
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
                    onInteraction = {}
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
        color = InfinixCard,
        border = BorderStroke(1.dp, InfinixCardBorder),
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
                    color = InfinixTextSecondary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            Text(
                text = primaryText,
                color = InfinixTextPrimary,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subText,
                color = InfinixTextMuted,
                fontSize = 7.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
