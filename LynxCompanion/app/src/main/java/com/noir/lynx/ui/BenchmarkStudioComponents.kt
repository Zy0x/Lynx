package com.noir.lynx.ui

import com.noir.lynx.core.locale.localizedUiText

import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*
import com.noir.lynx.feature.battery.*
import com.noir.lynx.feature.battery.ui.*

import android.os.Bundle
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.util.VelocityTrackerAddPointsFix
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.Canvas
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.data.DeepTunable
import com.noir.lynx.data.TunableType
import com.noir.lynx.data.TunableOption
import com.noir.lynx.data.AppProfileRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.noir.lynx.feature.cpu.ui.*
import com.noir.lynx.feature.gpu.ui.*
import com.noir.lynx.feature.thermal.ui.*
import com.noir.lynx.feature.telemetry.ui.*
import com.noir.lynx.feature.battery.ui.*
import com.noir.lynx.feature.memory.ui.*
import com.noir.lynx.feature.network.ui.*
import com.noir.lynx.feature.system.ui.*
import com.noir.lynx.feature.navigation.model.TuningCategory


/**
 * MainActivity — Lynx Kernel Manager Native App.
 *
 * Full-screen Compose UI with Luxury Cyberpunk OLED Material 3 Expressive theme.
 * Dual-Mode Root support: operates standalone on any rooted device,
 * and unlocks enhanced background features when the Lynx root module is present.
 *
 * 4-Tab Luxury Navigation:
 *   1. Dashboard (Hardware Gauges, CPU Clusters Matrix, Bento Telemetry, Quick Profiles)
 *   2. SoC Tuner (Dynamic CPU Clusters, Min/Max Frequency Sliders, Governors, Thermal Limits)
 *   3. Lynx Engine (ZRAM, Charging Bypass, Audio MMAP, Network BBR, OEM Neutralizer)
 *   4. Tools & Flasher (AnyKernel3 Flasher, Boot Backup/Restore, Storage TRIM, CCleaner)
 */

@Composable
fun BenchmarkStudioDialog(
    uiState: LynxUiState,
    viewModel: LynxViewModel
) {
    val result = uiState.benchmarkResult
    val isRunning = uiState.isBenchmarking
    val context = LocalContext.current
    var selectedDuration by remember { mutableStateOf(60) }

    AlertDialog(
        onDismissRequest = {
            if (!isRunning) viewModel.closeBenchmarkDialog()
        },
        containerColor = BgCard,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = AccentPurple.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.5f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("LIVE BENCHMARK STUDIO", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        Text("Hardware Frame Pacing (SurfaceFlinger)", color = TextSecondary, fontSize = 10.sp)
                    }
                }
                IconButton(onClick = { viewModel.closeBenchmarkDialog() }) {
                    Icon(Icons.Default.Close, contentDescription = localizedUiText("Tutup"), tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Target Game Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconImage(
                            packageName = uiState.benchmarkTargetPackage,
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.benchmarkTargetAppName.ifBlank { "Game Target" },
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = uiState.benchmarkTargetPackage,
                                color = TextSecondary,
                                fontSize = 10.5.sp,
                                maxLines = 1
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentCyan.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = uiState.state.activeProfile.uppercase(),
                                color = AccentCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // If Benchmarking is in progress:
                if (isRunning) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = {
                                        val total = uiState.benchmarkTotalSeconds.coerceAtLeast(1).toFloat()
                                        val cur = uiState.benchmarkProgressSeconds.toFloat()
                                        (1f - (cur / total)).coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier.size(72.dp),
                                    color = AccentOrange,
                                    trackColor = BgDeepOled,
                                    strokeWidth = 5.dp
                                )
                                Text(
                                    text = "${uiState.benchmarkProgressSeconds}s",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                            }
                            Text(
                                text = "Merekam Presentation Timestamps...",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Silakan beralih ke game dan gerakkan kamera 3D, eksplorasi, atau rotasi layar secara intensif.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                            OutlinedButton(
                                onClick = { viewModel.cancelBenchmark() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Batalkan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (result != null) {
                    // ── BENCHMARK RESULTS VIEW ──────────────────────
                    // Hero Score Banner
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("AVERAGE RENDER FPS", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${result.averageFps} FPS",
                                        color = when {
                                            result.averageFps >= 50f -> AccentCyan
                                            result.averageFps >= 35f -> AccentGreen
                                            else -> AccentOrange
                                        },
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("MEDIAN FRAMETIME", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${result.medianFrametimeMs} ms",
                                        color = TextPrimary,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = BorderGlass, thickness = 1.dp)
                            Spacer(Modifier.height(8.dp))

                            // Stability Verdict
                            val (verdictText, verdictColor) = when {
                                result.jankyFramesPercent <= 5f -> "Ultra Smooth — Frame Pacing Sangat Stabil" to AccentGreen
                                result.jankyFramesPercent <= 15f -> "Sangat Baik — Stabilitas Tinggi" to AccentCyan
                                else -> "Variasi Frametime Terdeteksi" to AccentOrange
                            }
                            Text(
                                text = verdictText,
                                color = verdictColor,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Key Metrics Grid (6 Tiles)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BenchmarkMetricTile("1% LOW FPS", "${result.fps1PercentLow} FPS", AccentCyan, Modifier.weight(1f))
                            BenchmarkMetricTile("0.1% LOW FPS", "${result.fps01PercentLow} FPS", AccentBlue, Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BenchmarkMetricTile("FRAMETIME JITTER", "±${result.frametimeJitterMs} ms", AccentPurple, Modifier.weight(1f))
                            BenchmarkMetricTile("JANKY FRAMES", "${result.jankyFramesCount} (${result.jankyFramesPercent}%)", if (result.jankyFramesPercent > 10f) AccentRed else AccentGreen, Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BenchmarkMetricTile("PEAK / MIN LATENCY", "${result.minFrametimeMs} ms", AccentGreen, Modifier.weight(1f))
                            BenchmarkMetricTile("MAX SPIKE", "${result.maxFrametimeMs} ms", AccentOrange, Modifier.weight(1f))
                        }
                    }

                    // Hardware Telemetry during benchmark
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("TELEMETRI HARDWARE SAAT PENGUJIAN", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TelemetryMiniStat("CPU Avg", if (result.avgCpuClockMhz > 0) "${result.avgCpuClockMhz} MHz" else "--")
                                TelemetryMiniStat("GPU Avg", if (result.avgGpuClockMhz > 0) "${result.avgGpuClockMhz} MHz (${result.avgGpuLoadPct}%)" else "--")
                                TelemetryMiniStat("Suhu", if (result.avgBatteryTempC > 0f) "${result.avgBatteryTempC}°C" else "--")
                                TelemetryMiniStat("Daya", if (result.avgBatteryWatt > 0f) "${result.avgBatteryWatt} W" else "--")
                            }
                        }
                    }

                    // Interactive Frametime Line Chart
                    if (result.frametimes.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BgElevated,
                            border = BorderStroke(1.dp, BorderGlass),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("DISTRIBUSI FRAMETIME (${result.sampledFrames} FRAMES)", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("16.6ms (60 FPS)", color = AccentGreen, fontSize = 8.5.sp)
                                        Text("33.3ms (30 FPS)", color = AccentOrange, fontSize = 8.5.sp)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                FrametimeCanvasChart(frametimes = result.frametimes, modifier = Modifier.fillMaxWidth().height(100.dp))
                            }
                        }
                    }
                } else {
                    // Duration Picker & Start Prompt
                    Text("PILIH DURASI PENGUJIAN", color = TextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10 to "10s", 30 to "30s", 60 to "1 Min", 120 to "2 Min").forEach { (sec, label) ->
                            val isSel = selectedDuration == sec
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) AccentPurple.copy(alpha = 0.25f) else BgElevated,
                                border = BorderStroke(1.dp, if (isSel) AccentPurple else BorderGlass),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedDuration = sec }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) AccentPurple else TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AccentCyan.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Setelah menekan Mulai, beralihlah ke game dan gerakkan karakter untuk menguji kestabilan frame pacing nyata.",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isRunning) {
                if (result != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val reportText = """
                                    LYNX LIVE BENCHMARK REPORT
                                    Aplikasi: ${result.appName} (${result.appPackage})
                                    Profil Kernel: ${result.activeProfile.uppercase()}
                                    Rata-rata FPS: ${result.averageFps} FPS
                                    Median Frametime: ${result.medianFrametimeMs} ms
                                    1% Low FPS: ${result.fps1PercentLow} FPS
                                    Frametime Jitter: ±${result.frametimeJitterMs} ms
                                    Janky Frames (>33ms): ${result.jankyFramesCount} (${result.jankyFramesPercent}%)
                                    CPU Avg: ${result.avgCpuClockMhz} MHz | GPU Avg: ${result.avgGpuClockMhz} MHz (${result.avgGpuLoadPct}%)
                                    Suhu: ${result.avgBatteryTempC}°C | Daya: ${result.avgBatteryWatt} W
                                """.trimIndent()
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Lynx Benchmark", reportText)
                                clipboard.setPrimaryClip(clip)
                                viewModel.openBenchmarkDialog(result.appPackage)
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderGlass)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Salin", fontSize = 11.5.sp)
                        }

                        Button(
                            onClick = { viewModel.startBenchmark(selectedDuration, uiState.benchmarkTargetPackage) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Uji Ulang", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.startBenchmark(selectedDuration, uiState.benchmarkTargetPackage) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mulai Live Benchmark", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        },
        dismissButton = {
            if (!isRunning) {
                TextButton(onClick = { viewModel.closeBenchmarkDialog() }) {
                    Text("Tutup", color = TextSecondary)
                }
            }
        }
    )
}

@Composable
fun FrametimeCanvasChart(
    frametimes: List<Float>,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (frametimes.isEmpty()) return@Canvas

        val w = size.width
        val h = size.height

        val maxVal = 50f
        val y60fps = h - ((16.66f / maxVal) * h).coerceIn(0f, h)
        val y30fps = h - ((33.33f / maxVal) * h).coerceIn(0f, h)

        // Draw 60 FPS guide line (Green)
        drawLine(
            color = Color(0x6610B981),
            start = Offset(0f, y60fps),
            end = Offset(w, y60fps),
            strokeWidth = 1.5f
        )

        // Draw 30 FPS guide line (Orange)
        drawLine(
            color = Color(0x66FF9F2E),
            start = Offset(0f, y30fps),
            end = Offset(w, y30fps),
            strokeWidth = 1.5f
        )

        val count = frametimes.size
        val stepX = w / count.coerceAtLeast(1)

        val path = Path()

        for (i in frametimes.indices) {
            val ft = frametimes[i]
            val x = i * stepX
            val y = h - ((ft / maxVal) * h).coerceIn(0f, h)

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }

            if (ft > 33.33f) {
                drawCircle(
                    color = AccentRed,
                    radius = 3f,
                    center = Offset(x, y)
                )
            }
        }

        drawPath(
            path = path,
            color = AccentCyan,
            style = Stroke(width = 2f)
        )
    }
}

@Composable
private fun BenchmarkMetricTile(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgElevated,
        border = BorderStroke(1.dp, BorderGlass),
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, color = TextSecondary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(value, color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TelemetryMiniStat(label: String, value: String) {
    Column {
        Text(label, color = TextSecondary, fontSize = 8.sp)
        Text(value, color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
    }
}


