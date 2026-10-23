package com.noir.lynx.feature.gpu.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.noir.lynx.data.GpuSiliconInfo
import com.noir.lynx.data.StressBenchmarkReport
import com.noir.lynx.designsystem.theme.*

/**
 * GpuStabilityStressDialog - 30s / 60s High-Load Stress Loop & Throttling Profiler
 */
@Composable
fun GpuStabilityStressDialog(
    silicon: GpuSiliconInfo,
    onStartStressTest: (durationSec: Int, onComplete: (StressBenchmarkReport) -> Unit) -> Unit,
    onStopStressTest: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDuration by remember { mutableStateOf(30) }
    var latestReport by remember { mutableStateOf<StressBenchmarkReport?>(null) }

    val animatedProgress by animateFloatAsState(
        targetValue = silicon.stressBenchmarkProgress,
        label = "stress_progress"
    )

    Dialog(onDismissRequest = {
        if (!silicon.isStressBenchmarkRunning) onDismiss()
    }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF10131B),
            border = BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(AccentOrange.copy(alpha = 0.5f), BorderGlass)
                )
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── HEADER ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(AccentOrange.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Whatshot, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                text = "GPU Stability Stress Test",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Uji ketahanan frekuensi & deteksi thermal throttling",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !silicon.isStressBenchmarkRunning,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = BorderGlass)

                // ── DURATION SELECTOR (30s vs 60s) ──
                if (!silicon.isStressBenchmarkRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(30 to "30 Detik (Quick)", 60 to "60 Detik (Deep)").forEach { (dur, label) ->
                            val isSelected = selectedDuration == dur
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) AccentOrange.copy(alpha = 0.2f) else BgElevated,
                                border = BorderStroke(1.dp, if (isSelected) AccentOrange else BorderGlass),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clickable { selectedDuration = dur }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) AccentOrange else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // ── PROGRESS BAR & LIVE STATS ──
                if (silicon.isStressBenchmarkRunning) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Stress Progress", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentOrange
                            )
                        }
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = AccentOrange,
                            trackColor = BgElevated
                        )
                    }

                    // Live telemetry indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BgElevated,
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Clock Aktif", fontSize = 11.sp, color = TextSecondary)
                                Text(
                                    text = "${silicon.stressBenchmarkFinalClockMhz} MHz",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BgElevated,
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Suhu SoC", fontSize = 11.sp, color = TextSecondary)
                                Text(
                                    text = "${"%.1f".format(silicon.stressBenchmarkFinalTempC)}°C",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentOrange
                                )
                            }
                        }
                    }
                }

                // ── RESULT REPORT CARD ──
                val report = latestReport
                if (!silicon.isStressBenchmarkRunning && report != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, if (report.passed) AccentGreen.copy(alpha = 0.5f) else AccentRed.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Hasil Uji Stabilitas", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (report.passed) AccentGreen.copy(alpha = 0.2f) else AccentRed.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (report.passed) "STABIL (${report.stabilityScore}%)" else "THROTTLED (${report.stabilityScore}%)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (report.passed) AccentGreen else AccentRed,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Peak Clock: ${report.peakClockMhz} MHz  ->  Final: ${report.finalClockMhz} MHz",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "Suhu Awal: ${"%.1f".format(report.initialTempC)}°C  ->  Puncak: ${"%.1f".format(report.peakTempC)}°C",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "Throttling Drop: ${"%.1f".format(report.throttlingDropPercent)}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (report.throttlingDropPercent > 15f) AccentOrange else AccentGreen
                            )
                        }
                    }
                }

                // ── ACTION BUTTON ──
                Button(
                    onClick = {
                        if (silicon.isStressBenchmarkRunning) {
                            onStopStressTest()
                        } else {
                            onStartStressTest(selectedDuration) { rep ->
                                latestReport = rep
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (silicon.isStressBenchmarkRunning) AccentRed else AccentOrange
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = if (silicon.isStressBenchmarkRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (silicon.isStressBenchmarkRunning) "Hentikan Stress Test" else "Mulai Uji Beban ($selectedDuration s)",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
