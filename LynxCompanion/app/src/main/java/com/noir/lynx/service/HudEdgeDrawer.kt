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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
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
 * HudEdgeDrawer — Infinix XBOOST AI Inspired Full Gaming Console.
 * Matches the authentic Infinix XBOOST Game Bar layout:
 * - Floating top dock of apps (WhatsApp, Chrome, YouTube, Calculator, etc.) in freeform mode
 * - Left vertical category rail (Fitur Dasar, Alat Akses Cepat, Anti-gangguan, Gameplay Tambahan)
 * - Dual circular tachometer gauges (CPU % & GPU %) with glowing gradient dials
 * - Performance profile pills & bottom gaming action tools
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
    var selectedCategoryIndex by remember { mutableStateOf(0) }
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
                                listOf(Color(0xFFFF5252), Color.White, NeonCyan)
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
        // ── STATE 2: EXPANDED INFINIX XBOOST CONSOLE ──
        val consoleWidth = if (isLandscape) 385.dp else 330.dp

        Surface(
            shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
            color = Color(0xF4080D15),
            border = BorderStroke(1.dp, Color(0x3300E5FF)),
            shadowElevation = 24.dp,
            modifier = Modifier
                .width(consoleWidth)
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
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Infinix XBOOST Top Bar (Logo, Status Icons, Battery, Clock, Collapse)
                val currentTimeStr = remember(telemetry.renderFps) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Brand: Flame Icon + XBOOST AI
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF3D00)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚡", fontSize = 9.sp)
                        }
                        Text(
                            text = "XBOOST AI",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                    }

                    // Right Status Cluster
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Sound / Mute Indicator
                        Text("🔇", fontSize = 10.sp)

                        // Battery % and Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("🔋", fontSize = 10.sp)
                            Text(
                                text = "${telemetry.battLevelPct}%",
                                color = InfinixTextSecondary,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Digital Time Clock
                        Text(
                            text = currentTimeStr,
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )

                        // Close / Collapse Button
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
                                contentDescription = "Tutup Bilah",
                                tint = NeonRed,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                // 2. Horizontal Floating App Dock (WhatsApp, Chrome, YouTube, Calculator, etc.)
                HudXboostAppDock(
                    onAppLaunched = {
                        // Optional: keep console open or auto-collapse when app is launched
                    }
                )

                HorizontalDivider(
                    color = Color(0x18FFFFFF),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(vertical = 1.dp)
                )

                // 3. Main Console Body: Left Vertical Category Rail + Right Content Area
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left Vertical Category Rail (4 Tabs)
                    val categories = listOf(
                        Triple("🔲", "Fitur\nDasar", 0),
                        Triple("⚙️", "Alat Akses\nCepat", 1),
                        Triple("🔕", "Anti-\ngangguan", 2),
                        Triple("🪄", "Gameplay\nTambahan", 3)
                    )

                    Column(
                        modifier = Modifier.width(62.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { (icon, label, idx) ->
                            val isSelected = (selectedCategoryIndex == idx)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0x44FF3D00) else Color(0x18FFFFFF),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFFFF9100) else Color(0x15FFFFFF)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCategoryIndex = idx }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(text = icon, fontSize = 14.sp)
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else InfinixTextSecondary,
                                        fontSize = 7.5.sp,
                                        lineHeight = 9.sp,
                                        textAlign = TextAlign.Center,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Right Main Content Panel (Dynamically switches based on selected tab)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        when (selectedCategoryIndex) {
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
}
