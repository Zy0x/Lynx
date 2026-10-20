package com.noir.lynx.service

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxRepository
import java.util.Locale

// ────────────────────────────────────────────────────────────
//  STYLE 1: VERTICAL PILLAR (MODERN SLIM RTSS)
// ────────────────────────────────────────────────────────────

@Composable
fun HudVerticalPillar(
    telemetry: LynxRepository.FloatingHudTelemetry,
    isExpanded: Boolean,
    isHudBenchmarking: Boolean,
    hudBenchmarkCountdown: Int,
    hudBenchmarkSummary: com.noir.lynx.data.LynxBenchmarkResult?,
    onToggleExpand: () -> Unit,
    onToggleStylePicker: () -> Unit,
    onClose: () -> Unit,
    onProfileSelect: (String) -> Unit,
    onStartHudBenchmark: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BgHud,
        border = BorderStroke(1.dp, BorderHud),
        shadowElevation = 8.dp,
        modifier = Modifier.width(275.dp)
    ) {
        Column(modifier = Modifier.padding(7.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.DragHandle, "Geser", tint = Color(0x99FFFFFF), modifier = Modifier.size(14.dp))
                    Text(
                        text = "LYNX TELEMETRY",
                        color = NeonCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x2200E5FF))
                            .clickable { onToggleStylePicker() }
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("S1 ▾", color = NeonCyan, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x3300E5FF))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(telemetry.activeProfile.uppercase().take(3), color = NeonCyan, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0x22FF5252))
                            .clickable { onClose() }
                            .padding(2.dp)
                    ) {
                        Icon(Icons.Default.Close, "Tutup", tint = NeonRed, modifier = Modifier.size(10.dp))
                    }
                }
            }

            HorizontalDivider(color = Color(0x22FFFFFF), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

            // 4-Column Aligned Rows
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // GPU
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge("GPU", NeonMagenta)
                    Text(
                        text = if (telemetry.gpuFreqMhz > 0) "${telemetry.gpuFreqMhz} MHz" else "--",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(70.dp)
                    )
                    Text(
                        text = "${telemetry.gpuLoadPct}%",
                        color = NeonMagenta,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(42.dp)
                    )
                    val gpuTempStr = if (telemetry.gpuTempC > 0f) "${telemetry.gpuTempC.toInt()}°C" else if (telemetry.battTempC > 0f) "${telemetry.battTempC.toInt()}°C" else "--"
                    Text(
                        text = "$gpuTempStr · ${String.format(Locale.US, "%.1f", telemetry.battWatt)}W",
                        color = TextSlate,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }

                // CPU
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge("CPU", NeonCyan)
                    Text(
                        text = telemetry.cpuCoresSummary.ifEmpty { if (telemetry.cpuFreqMhz > 0) "${telemetry.cpuFreqMhz} MHz" else "--" },
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(70.dp)
                    )
                    Text(
                        text = "${telemetry.cpuLoadPct}%",
                        color = NeonCyan,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(42.dp)
                    )
                    val cpuTempStr = if (telemetry.cpuTempC > 0f) "${telemetry.cpuTempC.toInt()}°C" else if (telemetry.battTempC > 0f) "${telemetry.battTempC.toInt()}°C" else "--"
                    Text(
                        text = cpuTempStr,
                        color = TextSlate,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }

                // RAM
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge("RAM", NeonGold)
                    Text(
                        text = if (telemetry.ramUsedGb > 0f) "${telemetry.ramUsedGb} / ${telemetry.ramTotalGb}G" else "--",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(70.dp)
                    )
                    Text(
                        text = "${telemetry.ramPct}%",
                        color = NeonGold,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(42.dp)
                    )
                    Text(
                        text = "ZRAM ${telemetry.zramUsedGb}G",
                        color = TextSlate,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }

                // FPS
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge("FPS", NeonGreen)
                    Text(
                        text = "${telemetry.renderFps} FPS",
                        color = NeonGreen,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(70.dp)
                    )
                    Text(
                        text = "AVG ${telemetry.avgFps.toInt()}",
                        color = NeonGreen.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(48.dp)
                    )
                    Text(
                        text = "1%L ${telemetry.fps1PercentLow.toInt()}",
                        color = NeonGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Frametime
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge("FT", TextSlate)
                    Text(
                        text = "${String.format(Locale.US, "%.1f", telemetry.frametimeMs)} ms",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(70.dp)
                    )
                    Text(
                        text = "AVG ${String.format(Locale.US, "%.1f", telemetry.avgFrametimeMs)}",
                        color = TextSlate,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(48.dp)
                    )
                    Text(
                        text = "±${String.format(Locale.US, "%.1f", telemetry.frametimeJitterMs)} ms",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Real-time Frametime Sparkline Canvas
                FrametimeSparklineCanvas(
                    history = telemetry.frametimeHistory,
                    heightDp = 14,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // BAT
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge("BAT", NeonOrange)
                    Text(
                        text = "${telemetry.battTempC}°C",
                        color = if (telemetry.battTempC > 42f) NeonRed else NeonOrange,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(55.dp)
                    )
                    Text(
                        text = "${telemetry.battCurrentMa} mA",
                        color = if (telemetry.isCharging) NeonGreen else Color.White,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(60.dp)
                    )
                    Text(
                        text = "${telemetry.battLevelPct}% · ${if (telemetry.isCharging) "+" else ""}${String.format(Locale.US, "%.1f", telemetry.battWatt)}W",
                        color = TextSlate,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick Profile Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf("balance" to "BAL", "performance" to "PERF", "extreme" to "EXT", "powersave" to "PWR").forEach { (k, label) ->
                    val isSel = telemetry.activeProfile.equals(k, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSel) NeonCyan else Color(0x18FFFFFF))
                            .clickable { onProfileSelect(k) }
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) Color.Black else Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  STYLE 2: TOP NANO-RIBBON (HORIZONTAL BAR)
// ────────────────────────────────────────────────────────────

@Composable
fun HudTopRibbon(
    telemetry: LynxRepository.FloatingHudTelemetry,
    onDrag: (Float, Float) -> Unit,
    onToggleStylePicker: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BgHud,
        border = BorderStroke(1.dp, BorderHud),
        modifier = Modifier
            .width(365.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // FPS
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                CategoryBadge("FPS", NeonGreen)
                Text("${telemetry.renderFps}", color = NeonGreen, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                Text("A:${telemetry.avgFps.toInt()}", color = TextSlate, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }

            Text("|", color = Color(0x33FFFFFF), fontSize = 10.sp)

            // GPU
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("GPU", color = NeonMagenta, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                Text("${telemetry.gpuLoadPct}%", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            Text("|", color = Color(0x33FFFFFF), fontSize = 10.sp)

            // CPU
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("CPU", color = NeonCyan, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                Text("${telemetry.cpuLoadPct}%", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            Text("|", color = Color(0x33FFFFFF), fontSize = 10.sp)

            // RAM
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("RAM", color = NeonGold, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                Text("${telemetry.ramUsedGb}G", color = Color.White, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
            }

            Text("|", color = Color(0x33FFFFFF), fontSize = 10.sp)

            // BAT & Style
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${telemetry.battTempC.toInt()}°C", color = NeonOrange, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x3300E5FF))
                        .clickable { onToggleStylePicker() }
                        .padding(horizontal = 3.5.dp, vertical = 1.dp)
                ) {
                    Text("S2 ▾", color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  STYLE 3: DUAL-BLOCK ESPORT (ROG STYLE)
// ────────────────────────────────────────────────────────────

@Composable
fun HudDualBlock(
    telemetry: LynxRepository.FloatingHudTelemetry,
    onDrag: (Float, Float) -> Unit,
    onToggleStylePicker: () -> Unit,
    onClose: () -> Unit,
    onProfileSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .width(340.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Left Block: FPS Hero Hub
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BgHud,
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
            modifier = Modifier.width(125.dp)
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("FPS HUB", color = NeonCyan, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    Text("${telemetry.refreshRateHz}Hz", color = NeonGreen, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "${telemetry.renderFps}",
                    color = NeonGreen,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
                Text("AVG: ${telemetry.avgFps.toInt()} · 1%L: ${telemetry.fps1PercentLow.toInt()}", color = TextSlate, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                Text("FT: ${String.format(Locale.US, "%.1f", telemetry.frametimeMs)}ms", color = TextMuted, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(3.dp))
                FrametimeSparklineCanvas(history = telemetry.frametimeHistory, heightDp = 14)
            }
        }

        // Right Block: Hardware & Power Matrix
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BgHud,
            border = BorderStroke(1.dp, NeonMagenta.copy(alpha = 0.35f)),
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("HARDWARE", color = NeonMagenta, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x3300E5FF))
                                .clickable { onToggleStylePicker() }
                                .padding(horizontal = 3.5.dp, vertical = 1.dp)
                        ) {
                            Text("S3 ▾", color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x22FFFFFF))
                                .padding(horizontal = 3.5.dp, vertical = 1.dp)
                        ) {
                            Text(telemetry.activeProfile.uppercase().take(3), color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                HorizontalDivider(color = Color(0x22FFFFFF), thickness = 1.dp, modifier = Modifier.padding(vertical = 3.dp))
                val gpuTempStr = if (telemetry.gpuTempC > 0f) "${telemetry.gpuTempC.toInt()}°C" else if (telemetry.battTempC > 0f) "${telemetry.battTempC.toInt()}°C" else "--"
                val cpuTempStr = if (telemetry.cpuTempC > 0f) "${telemetry.cpuTempC.toInt()}°C" else if (telemetry.battTempC > 0f) "${telemetry.battTempC.toInt()}°C" else "--"
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("GPU ${telemetry.gpuFreqMhz}M", color = NeonMagenta, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("${telemetry.gpuLoadPct}% · $gpuTempStr", color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("CPU ${telemetry.cpuFreqMhz}M", color = NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("${telemetry.cpuLoadPct}% · $cpuTempStr", color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("RAM ${telemetry.ramUsedGb}G", color = NeonGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("ZRAM ${telemetry.zramUsedGb}G", color = TextSlate, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("BAT ${telemetry.battTempC}°C", color = NeonOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("${telemetry.battLevelPct}% · ${String.format(Locale.US, "%.1f", telemetry.battWatt)}W", color = TextSlate, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  STYLE 4: QUAD-TILES (NOTHING OS / MODULAR GRID)
// ────────────────────────────────────────────────────────────

@Composable
fun HudQuadTiles(
    telemetry: LynxRepository.FloatingHudTelemetry,
    onDrag: (Float, Float) -> Unit,
    onToggleStylePicker: () -> Unit,
    onClose: () -> Unit,
    onProfileSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(310.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("LYNX QUAD-TILES", color = NeonCyan, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x3300E5FF))
                    .clickable { onToggleStylePicker() }
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text("S4 ▾", color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Tile 1: FPS
            Surface(shape = RoundedCornerShape(8.dp), color = BgHud, border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.4f)), modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(5.dp)) {
                    Text("FPS PACING", color = NeonGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text("${telemetry.renderFps}", color = NeonGreen, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text("AVG ${telemetry.avgFps.toInt()} · 1%L ${telemetry.fps1PercentLow.toInt()}", color = TextSlate, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }
            // Tile 2: GPU
            Surface(shape = RoundedCornerShape(8.dp), color = BgHud, border = BorderStroke(1.dp, NeonMagenta.copy(alpha = 0.4f)), modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(5.dp)) {
                    Text("GPU ENGINE", color = NeonMagenta, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text("${telemetry.gpuLoadPct}%", color = NeonMagenta, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    val gpuTempStr = if (telemetry.gpuTempC > 0f) "${telemetry.gpuTempC.toInt()}°C" else if (telemetry.battTempC > 0f) "${telemetry.battTempC.toInt()}°C" else "--"
                    Text("${telemetry.gpuFreqMhz}M · $gpuTempStr", color = TextSlate, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Tile 3: CPU
            Surface(shape = RoundedCornerShape(8.dp), color = BgHud, border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)), modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(5.dp)) {
                    Text("CPU LOAD", color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text("${telemetry.cpuLoadPct}%", color = NeonCyan, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text(telemetry.cpuCoresSummary.ifEmpty { "${telemetry.cpuFreqMhz}M" }, color = TextSlate, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }
            // Tile 4: RAM & BAT
            Surface(shape = RoundedCornerShape(8.dp), color = BgHud, border = BorderStroke(1.dp, NeonGold.copy(alpha = 0.4f)), modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(5.dp)) {
                    Text("MEM & POWER", color = NeonGold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text("${telemetry.ramUsedGb}G", color = NeonGold, fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text("${telemetry.battTempC}°C · ${String.format(Locale.US, "%.1f", telemetry.battWatt)}W", color = TextSlate, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
