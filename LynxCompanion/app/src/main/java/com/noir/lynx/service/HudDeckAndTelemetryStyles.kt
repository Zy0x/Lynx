package com.noir.lynx.service

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxRepository
import java.util.Locale

// ────────────────────────────────────────────────────────────
//  STYLE 5: DECK BANNER (STEAM DECK GAMESCOPE)
// ────────────────────────────────────────────────────────────

@Composable
fun HudDeckBanner(
    telemetry: LynxRepository.FloatingHudTelemetry,
    onDrag: (Float, Float) -> Unit,
    onToggleStylePicker: () -> Unit,
    onClose: () -> Unit,
    onProfileSelect: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgHud,
        border = BorderStroke(1.dp, BorderHud),
        modifier = Modifier
            .width(360.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            // Tier 1: FPS + Wide Sparkline
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "${hudNumber(telemetry.renderFps)}",
                        color = NeonGreen,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text("FPS", color = NeonGreen, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }
                FrametimeSparklineCanvas(
                    history = telemetry.frametimeHistory,
                    heightDp = 14,
                    modifier = Modifier.width(140.dp)
                )
                Text(
                    text = "AVG ${hudNumber(telemetry.avgFps)} · 1%L ${hudNumber(telemetry.fps1PercentLow)}",
                    color = TextSlate,
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x3300E5FF))
                        .clickable { onToggleStylePicker() }
                        .padding(horizontal = 3.5.dp, vertical = 1.dp)
                ) {
                    Text("S5 ▾", color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = Color(0x22FFFFFF), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

            // Tier 2: Hardware Strip
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HardwareDeckChip("GPU", "${hudNumber(telemetry.gpuLoadPct)}%", NeonMagenta, Modifier.weight(1f))
                HardwareDeckChip("CPU", "${hudNumber(telemetry.cpuLoadPct)}%", NeonCyan, Modifier.weight(1f))
                HardwareDeckChip("RAM", "${hudNumber(telemetry.ramUsedGb)}G", NeonGold, Modifier.weight(1f))
                HardwareDeckChip("BAT", "${hudNumber(telemetry.battTempC)}°C", NeonOrange, Modifier.weight(1f))
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  STYLE 6: GHOST TEXT (FRAMELESS MINIMALIST)
// ────────────────────────────────────────────────────────────

@Composable
fun HudGhostText(
    telemetry: LynxRepository.FloatingHudTelemetry,
    onDrag: (Float, Float) -> Unit,
    onToggleStylePicker: () -> Unit,
    onClose: () -> Unit,
) {
    val ghostShadow = Shadow(color = Color.Black, offset = Offset(1.5f, 1.5f), blurRadius = 3f)

    Column(
        modifier = Modifier
            .width(260.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable { onToggleStylePicker() }
            .padding(4.dp)
    ) {
        val gpuTempStr = if (telemetry.gpuTempC > 0f) "${hudNumber(telemetry.gpuTempC)}°C" else "—"
        val cpuTempStr = if (telemetry.cpuTempC > 0f) "${hudNumber(telemetry.cpuTempC)}°C" else "—"

        // Ghost Line: GPU
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("GPU", color = NeonMagenta, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontWeight = FontWeight.Bold))
            Text("${hudNumber(telemetry.gpuFreqMhz)} MHz", color = Color.White, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace))
            Text("${hudNumber(telemetry.gpuLoadPct)}%", color = NeonMagenta, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
            Text("$gpuTempStr · ${hudNumber(telemetry.battWatt)}W", color = TextSlate, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace))
        }

        // Ghost Line: CPU
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("CPU", color = NeonCyan, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontWeight = FontWeight.Bold))
            Text(telemetry.cpuCoresSummary.ifEmpty { "${hudNumber(telemetry.cpuFreqMhz)} MHz" }, color = Color.White, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace))
            Text("${hudNumber(telemetry.cpuLoadPct)}%", color = NeonCyan, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
            Text(cpuTempStr, color = TextSlate, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace))
        }

        // Ghost Line: RAM
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("RAM", color = NeonGold, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontWeight = FontWeight.Bold))
            Text("${hudNumber(telemetry.ramUsedGb)} / ${hudNumber(telemetry.ramTotalGb)} GB", color = Color.White, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace))
            Text("${hudNumber(telemetry.ramPct)}%", color = NeonGold, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
            Text("ZRAM ${hudNumber(telemetry.zramUsedGb)}G", color = TextSlate, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace))
        }

        // Ghost Line: FPS
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("FPS", color = NeonGreen, style = TextStyle(shadow = ghostShadow, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold))
            Text("${hudNumber(telemetry.renderFps)}", color = NeonGreen, style = TextStyle(shadow = ghostShadow, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
            Text("AVG ${hudNumber(telemetry.avgFps)}", color = NeonGreen.copy(alpha = 0.85f), style = TextStyle(shadow = ghostShadow, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
            Text("1%L ${hudNumber(telemetry.fps1PercentLow)}", color = NeonGold, style = TextStyle(shadow = ghostShadow, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
        }

        // Ghost Line: FT
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("FT", color = TextSlate, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontWeight = FontWeight.Bold))
            Text("${hudNumber(telemetry.frametimeMs)} ms", color = Color.White, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace))
            Text("AVG ${hudNumber(telemetry.avgFrametimeMs)}", color = TextSlate, style = TextStyle(shadow = ghostShadow, fontSize = 9.sp, fontFamily = FontFamily.Monospace))
            Text("±${hudNumber(telemetry.frametimeJitterMs)} ms", color = TextMuted, style = TextStyle(shadow = ghostShadow, fontSize = 9.sp, fontFamily = FontFamily.Monospace))
        }

        // Ghost Line: BAT
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("BAT", color = NeonOrange, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontWeight = FontWeight.Bold))
            Text("${hudNumber(telemetry.battTempC)}°C", color = NeonOrange, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace))
            Text("${hudNumber(telemetry.battCurrentMa)} mA", color = Color.White, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace))
            Text("${hudNumber(telemetry.battLevelPct)}% [${telemetry.activeProfile.uppercase().take(3)}]", color = NeonCyan, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
        }
    }
}

// ────────────────────────────────────────────────────────────
//  SHARED COMPONENT HELPERS
// ────────────────────────────────────────────────────────────

@Composable
internal fun CategoryBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .width(36.dp)
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun HardwareDeckChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0x18FFFFFF),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = color, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun FrametimeSparklineCanvas(
    history: List<Float>,
    modifier: Modifier = Modifier,
    heightDp: Int = 14
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0x66000000))
    ) {
        val count = history.size
        if (count == 0) return@Canvas
        val barWidth = size.width / count.coerceAtLeast(30)
        val maxMs = 70f
        for (i in 0 until count) {
            val v = history[i]
            val barH = (v / maxMs * size.height).coerceIn(3f, size.height)
            val x = i * barWidth
            val y = size.height - barH
            val barColor = when {
                v > 45f -> NeonRed
                v > 30f -> NeonGold
                else -> NeonGreen
            }
            drawRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size((barWidth - 0.8f).coerceAtLeast(1f), barH)
            )
        }
    }
}
