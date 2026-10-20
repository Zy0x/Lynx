package com.noir.lynx.service

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * HudQuickToolsAndBenchmark — Infinix Game Space style in-game utility dock.
 * Provides 1-tap tactile actions: RAM Boost, Display Refresh Rate switch,
 * Mini FPS Bezel Pin toggle, and 60-second hardware stability benchmark.
 */
@Composable
internal fun ColumnScope.HudQuickToolsAndBenchmark(
    currentRefreshRate: Int,
    pinMiniFps: Boolean,
    isHudBenchmarking: Boolean,
    hudBenchmarkCountdown: Int,
    hudBenchmarkSummary: com.noir.lynx.data.LynxBenchmarkResult?,
    onBoostRam: () -> Unit,
    onCycleRefreshRate: () -> Unit,
    onTogglePinMiniFps: () -> Unit,
    onStartHudBenchmark: () -> Unit,
    onInteraction: () -> Unit = {}
) {
    var isBoosting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "ALAT GAME",
            color = InfinixTextSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )

        // 3-Column Squircle Tool Tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Tool 1: Boost RAM (Drop Caches)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isBoosting) NeonGreen.copy(alpha = 0.22f) else InfinixCard,
                border = BorderStroke(1.dp, if (isBoosting) NeonGreen else InfinixCardBorder),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        if (!isBoosting) {
                            isBoosting = true
                            onBoostRam()
                            onInteraction()
                            coroutineScope.launch {
                                delay(1200L)
                                isBoosting = false
                            }
                        }
                    }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (isBoosting) "🧹" else "🧹",
                        fontSize = 12.sp
                    )
                    Text(
                        text = if (isBoosting) "Bersih!" else "Boost RAM",
                        color = if (isBoosting) NeonGreen else InfinixTextPrimary,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isBoosting) "Selesai" else "Drop Cache",
                        color = if (isBoosting) NeonGreen.copy(alpha = 0.8f) else InfinixTextMuted,
                        fontSize = 7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Tool 2: Display Hz Cycle
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = InfinixCard,
                border = BorderStroke(1.dp, InfinixCardBorder),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onCycleRefreshRate()
                        onInteraction()
                    }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "⚡",
                        fontSize = 12.sp
                    )
                    Text(
                        text = "$currentRefreshRate Hz",
                        color = NeonGold,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Ganti Hz",
                        color = InfinixTextMuted,
                        fontSize = 7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Tool 3: Pin Mini FPS to Bezel
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (pinMiniFps) NeonGreen.copy(alpha = 0.22f) else InfinixCard,
                border = BorderStroke(1.dp, if (pinMiniFps) NeonGreen else InfinixCardBorder),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onTogglePinMiniFps()
                        onInteraction()
                    }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "📌",
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Pin FPS",
                        color = if (pinMiniFps) NeonGreen else InfinixTextPrimary,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (pinMiniFps) "Aktif" else "Mati",
                        color = if (pinMiniFps) NeonGreen.copy(alpha = 0.8f) else InfinixTextMuted,
                        fontSize = 7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Benchmark Action Bar
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isHudBenchmarking) NeonMagenta.copy(alpha = 0.2f) else InfinixCard,
            border = BorderStroke(1.dp, if (isHudBenchmarking) NeonMagenta else InfinixCardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (!isHudBenchmarking) {
                        onStartHudBenchmark()
                        onInteraction()
                    }
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("⏱", fontSize = 10.sp)
                    Text(
                        text = if (isHudBenchmarking) "Menguji... ${hudBenchmarkCountdown}s" else "Mulai Benchmark 60s",
                        color = if (isHudBenchmarking) NeonMagenta else InfinixTextPrimary,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (hudBenchmarkSummary != null && !isHudBenchmarking) {
                    val stabPct = (100f - hudBenchmarkSummary.jankyFramesPercent).coerceIn(0f, 100f).toInt()
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = NeonGreen.copy(alpha = 0.15f),
                        border = BorderStroke(0.6.dp, NeonGreen.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "Stab $stabPct% · 1%L ${hudBenchmarkSummary.fps1PercentLow.toInt()}",
                            color = NeonGreen,
                            fontSize = 7.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                        )
                    }
                } else if (!isHudBenchmarking) {
                    Text(
                        text = "Uji Pacing ➔",
                        color = InfinixTextMuted,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
