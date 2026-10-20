package com.noir.lynx.service

import android.content.res.Configuration
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
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
 * HudEdgeDrawer — Lynx Game Matrix Gaming Console.
 * An authentic, original cyber-tactical gaming overlay designed specifically for Lynx:
 * - Clean horizontal segmented category bar (Performa, Alat Cepat, Perisai, Ekstra)
 * - Zero empty void: auto-wraps content naturally in both portrait & landscape
 * - Dual circular tachometer gauges (CPU & GPU) with live FPS pacing
 * - Top floating app dock launching in freeform window mode
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
    var selectedTab by remember { mutableStateOf(0) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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
                        .width(4.5.dp)
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
        // ── STATE 2: EXPANDED LYNX GAME MATRIX CONSOLE ──
        val consoleWidth = if (isLandscape) 365.dp else 320.dp

        Surface(
            shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
            color = MatrixBg,
            border = BorderStroke(1.dp, MatrixCardBorder),
            shadowElevation = 20.dp,
            modifier = Modifier
                .width(consoleWidth)
                .wrapContentHeight()
                .systemGestureExclusion()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount.x < -20f) {
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
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Header Bar: Lynx Emblem, Title, Status, Clock, Close
                val currentTimeStr = remember(telemetry.renderFps) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Brand: Lynx Insignia + LYNX MATRIX
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(NeonCyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚡", fontSize = 8.5.sp, color = Color.Black)
                        }
                        Text(
                            text = "LYNX MATRIX",
                            color = NeonCyan,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                    }

                    // Right Status: Battery, Clock, Close
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Battery Level
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("🔋", fontSize = 10.sp)
                            Text(
                                text = "${telemetry.battLevelPct}%",
                                color = MatrixTextSecondary,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Digital Clock
                        Text(
                            text = currentTimeStr,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )

                        // Close Button
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FF5252))
                                .clickable { onToggleDrawer(false) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = NeonRed,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                // 2. Horizontal Segmented Category Navigation (Full Width, Zero Waste)
                val tabs = listOf(
                    "📊 Performa",
                    "⚡ Alat Cepat",
                    "🛡️ Perisai",
                    "🪄 Ekstra"
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x33141F2E),
                    border = BorderStroke(1.dp, Color(0x18FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.5.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        tabs.forEachIndexed { idx, label ->
                            val isSelected = (selectedTab == idx)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) NeonCyan.copy(alpha = 0.22f) else Color.Transparent)
                                    .border(
                                        BorderStroke(
                                            0.8.dp,
                                            if (isSelected) NeonCyan else Color.Transparent
                                        ),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedTab = idx }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) NeonCyan else MatrixTextSecondary,
                                    fontSize = 8.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // 3. Tab Content (Auto-wrapping with no empty void)
                Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                    when (selectedTab) {
                        1 -> HudXboostQuickAccessTab(
                            currentRefreshRate = currentRefreshRate,
                            onCycleRefreshRate = onCycleRefreshRate
                        )
                        2 -> HudXboostDndTab()
                        3 -> HudXboostGameplayTab(
                            isHudBenchmarking = isHudBenchmarking,
                            hudBenchmarkCountdown = hudBenchmarkCountdown,
                            hudBenchmarkSummary = hudBenchmarkSummary,
                            onStartHudBenchmark = onStartHudBenchmark
                        )
                        else -> HudXboostGaugesAndCoreTab(
                            telemetry = telemetry,
                            currentRefreshRate = currentRefreshRate,
                            pinMiniFps = pinMiniFps,
                            onProfileSelect = onProfileSelect,
                            onBoostRam = onBoostRam,
                            onCycleRefreshRate = onCycleRefreshRate,
                            onTogglePinMiniFps = onTogglePinMiniFps,
                            onCloseDrawer = { onToggleDrawer(false) }
                        )
                    }
                }
            }
        }
    }
}
