package com.noir.lynx.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noir.lynx.data.LynxRepository
import com.noir.lynx.ui.MainActivity
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlin.math.roundToInt

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
    var lastTouchTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Auto-collapse after 5 seconds of inactivity when drawer is open
    LaunchedEffect(isDrawerOpen, lastTouchTime) {
        if (isDrawerOpen) {
            delay(5000L)
            isDrawerOpen = false
            showStylePicker = false
        }
    }

    if (!isDrawerOpen) {
        // ── STATE 1: COLLAPSED EDGE HANDLE (Non-intrusive) ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .wrapContentSize()
                .pointerInput(Unit) {
                    while (true) {
                        awaitPointerEventScope {
                            val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: return@awaitPointerEventScope
                            var isDragging = false
                            var lastY = down.position.y
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!isDragging) {
                                        // Tap triggered!
                                        isDrawerOpen = true
                                        lastTouchTime = System.currentTimeMillis()
                                    }
                                    break
                                }
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                if (kotlin.math.abs(dx) > 8f || kotlin.math.abs(dy) > 8f) {
                                    isDragging = true
                                    change.consume()
                                    val deltaY = change.position.y - lastY
                                    lastY = change.position.y
                                    onDragVertical(deltaY)
                                    if (dx > 14f) {
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
            // Edge Tab Handle (Docked to bezel)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(Color(0xD90A0E14))
                    .border(
                        BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                // Sleek Vertical Glowing Bar
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(NeonCyan, Color.White, NeonCyan)
                            )
                        )
                )
            }

            // Optional Mini FPS Glanceable Pill (Docked next to handle)
            if (pinMiniFps) {
                Spacer(Modifier.width(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xEB0A0E16),
                    border = BorderStroke(1.dp, BorderHud),
                    shadowElevation = 4.dp,
                    modifier = Modifier.clickable {
                        isDrawerOpen = true
                        lastTouchTime = System.currentTimeMillis()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(NeonGreen)
                        )
                        Text(
                            text = "${telemetry.renderFps} FPS",
                            color = NeonGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    } else {
        // ── STATE 2: SLIDE-OUT GAME BAR (Expanded Drawer) ──
        Surface(
            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            color = Color(0xF40A0E16),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
            shadowElevation = 16.dp,
            modifier = Modifier
                .width(268.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            lastTouchTime = System.currentTimeMillis()
                            // Swiping left closes drawer
                            if (dragAmount.x < -18f) {
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
                    .padding(8.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        lastTouchTime = System.currentTimeMillis()
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(NeonCyan)
                        )
                        Text(
                            "LYNX GAME BAR",
                            color = NeonCyan,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Switch to Classic Floating Window
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable {
                                    isDrawerOpen = false
                                    onSelectMode(1)
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Float", color = TextSlate, fontSize = 8.5.sp)
                        }

                        // Style Dropdown Trigger
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x2200E5FF))
                                .clickable {
                                    showStylePicker = !showStylePicker
                                    lastTouchTime = System.currentTimeMillis()
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("S$activeStyle ▾", color = NeonCyan, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }

                        // Close Button
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0x22FF5252))
                                .clickable {
                                    isDrawerOpen = false
                                }
                                .padding(3.dp)
                        ) {
                            Icon(Icons.Default.Close, "Tutup", tint = NeonRed, modifier = Modifier.size(11.dp))
                        }
                    }
                }

                // Style Picker Dropdown
                AnimatedVisibility(visible = showStylePicker) {
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
                            val styleNames = listOf("1.Pillar", "2.Ribbon", "3.Esport", "4.Tiles", "5.Deck", "6.Ghost")
                            styleNames.forEachIndexed { idx, label ->
                                val sId = idx + 1
                                val isSel = (activeStyle == sId)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSel) NeonCyan.copy(alpha = 0.3f) else Color(0x22FFFFFF))
                                        .clickable {
                                            onSelectStyle(sId)
                                            showStylePicker = false
                                            lastTouchTime = System.currentTimeMillis()
                                        }
                                        .padding(vertical = 3.dp),
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

                // 2. Telemetry Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x77000000),
                    border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        // FPS Primary Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "${telemetry.renderFps}",
                                    color = NeonGreen,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    "FPS",
                                    color = NeonGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                            Text(
                                "AVG ${telemetry.avgFps.toInt()} · 1%L ${telemetry.fps1PercentLow.toInt()}",
                                color = TextSlate,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "${String.format(java.util.Locale.US, "%.1f", telemetry.frametimeMs)} ms",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Frametime Sparkline
                        FrametimeSparklineCanvas(
                            history = telemetry.frametimeHistory,
                            heightDp = 14,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )

                        // Hardware Metrics Grid
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            // GPU
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                CategoryBadge("GPU", NeonMagenta)
                                Text(
                                    if (telemetry.gpuFreqMhz > 0) "${telemetry.gpuFreqMhz} MHz" else "--",
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(66.dp)
                                )
                                Text(
                                    "${telemetry.gpuLoadPct}%",
                                    color = NeonMagenta,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(36.dp)
                                )
                                Text(
                                    "58°C · ${String.format(java.util.Locale.US, "%.1f", telemetry.battWatt)}W",
                                    color = TextSlate,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // CPU
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                CategoryBadge("CPU", NeonCyan)
                                Text(
                                    telemetry.cpuCoresSummary.ifEmpty { if (telemetry.cpuFreqMhz > 0) "${telemetry.cpuFreqMhz} MHz" else "--" },
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(66.dp)
                                )
                                Text(
                                    "${telemetry.cpuLoadPct}%",
                                    color = NeonCyan,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(36.dp)
                                )
                                Text(
                                    "${telemetry.battTempC.toInt() + 15}°C",
                                    color = TextSlate,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // RAM
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                CategoryBadge("RAM", NeonGold)
                                Text(
                                    if (telemetry.ramUsedGb > 0f) "${telemetry.ramUsedGb} / ${telemetry.ramTotalGb}G" else "--",
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(66.dp)
                                )
                                Text(
                                    "${telemetry.ramPct}%",
                                    color = NeonGold,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(36.dp)
                                )
                                Text(
                                    "ZRAM ${telemetry.zramUsedGb}G",
                                    color = TextSlate,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // BAT
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                CategoryBadge("BAT", NeonOrange)
                                Text(
                                    "${telemetry.battTempC}°C",
                                    color = if (telemetry.battTempC > 42f) NeonRed else NeonOrange,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(66.dp)
                                )
                                Text(
                                    "${telemetry.battCurrentMa} mA",
                                    color = Color.White,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(55.dp)
                                )
                                Text(
                                    "${telemetry.battLevelPct}%",
                                    color = NeonCyan,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 3. Kernel Mode Quick Switcher
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "MODE KERNEL",
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
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) NeonCyan.copy(alpha = 0.25f) else Color(0x18FFFFFF),
                                border = BorderStroke(1.dp, if (isSel) NeonCyan else Color(0x22FFFFFF)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onProfileSelect(pKey)
                                        lastTouchTime = System.currentTimeMillis()
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 4.dp),
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

//  FLOATING HUD ROUTER & CLASSIC WINDOW RENDERER
// ────────────────────────────────────────────────────────────

