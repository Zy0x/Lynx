package com.noir.lynx.feature.benchmark.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.feature.benchmark.BenchmarkSessionManager
import com.noir.lynx.feature.benchmark.SavedBenchmarkSession
import com.noir.lynx.ui.AppIconImage
import com.noir.lynx.ui.FrametimeCanvasChart
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.*
import java.util.Locale

/**
 * BenchmarkLabSubscreen — Luxury Benchmark Studio & System Optimization Hub.
 * Features live SurfaceFlinger frame pacing profiler, 1-click storage & memory suite,
 * and persistent session history log with interactive frametime waveforms.
 */
@Composable
fun BenchmarkLabSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onOpenOverlay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val savedSessions by BenchmarkSessionManager.savedSessions.collectAsState()
    var selectedDuration by remember { mutableStateOf(60) }
    var inspectedSession by remember { mutableStateOf<SavedBenchmarkSession?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<SavedBenchmarkSession?>(null) }
    var showResetStockConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        BenchmarkSessionManager.loadSessions(context)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. Live Hardware Profiler & Benchmark Studio Hero Card ─────────
        LiveBenchmarkStudioHeroCard(
            uiState = uiState,
            selectedDuration = selectedDuration,
            onSelectDuration = { selectedDuration = it },
            onStartBenchmark = { viewModel.startBenchmark(selectedDuration) },
            onCancelBenchmark = { viewModel.cancelBenchmark() },
            onDetectTarget = { viewModel.openBenchmarkDialog() },
            onOpenOverlay = onOpenOverlay
        )

        // ── 2. Storage & System Optimization Bento Suite ────────────────────
        SystemOptimizationSuiteCard(
            uiState = uiState,
            onRunFstrim = { viewModel.runQuickMaintenance("fstrim") },
            onRunDropCaches = { viewModel.runQuickMaintenance("drop_caches") },
            onExportDiagnostic = { viewModel.exportDiagnosticZip(context) },
            onRequestResetStock = { showResetStockConfirm = true }
        )

        // ── 3. Persistent Benchmark History Log Card ────────────────────────
        SavedBenchmarkHistoryCard(
            savedSessions = savedSessions,
            onInspect = { inspectedSession = it },
            onDelete = { sessionToDelete = it },
            onClearAll = { showClearAllConfirm = true }
        )

        Spacer(Modifier.height(115.dp).navigationBarsPadding())
    }

    // ── Inspection Modal Dialog ─────────────────────────────────────────────
    if (inspectedSession != null) {
        BenchmarkSessionDetailDialog(
            session = inspectedSession!!,
            onDismiss = { inspectedSession = null },
            onExport = {
                BenchmarkSessionManager.exportSavedSessionReport(context, inspectedSession!!)
            }
        )
    }

    // ── Confirmation Dialogs ────────────────────────────────────────────────
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = AccentRed)
                    Text("Bersihkan Seluruh Riwayat?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    "Semua catatan riwayat benchmark (${savedSessions.size} sesi) akan dihapus secara permanen dari penyimpanan perangkat.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        BenchmarkSessionManager.clearAllSessions(context)
                        showClearAllConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("Bersihkan", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearAllConfirm = false },
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = BgCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (sessionToDelete != null) {
        val s = sessionToDelete!!
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AccentRed)
                    Text("Hapus Sesi #${s.sessionNumber}?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    "Catatan pengujian sesi #${s.sessionNumber} (${s.appName}, ${String.format(Locale.US, "%.1f", s.averageFps)} FPS, ${s.dateFormatted}) akan dihapus permanen.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        BenchmarkSessionManager.deleteSession(context, s.id)
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("Hapus", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { sessionToDelete = null },
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = BgCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showResetStockConfirm) {
        AlertDialog(
            onDismissRequest = { showResetStockConfirm = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = AccentGreen)
                    Text("Reset Kernel ke Bawaan?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    "Semua parameter CPU, GPU, governor, thermal, dan I/O tuning akan dikembalikan secara aman ke konfigurasi bawaan pabrik OEM.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetKernelToStock()
                        showResetStockConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) {
                    Text("Reset Aman", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showResetStockConfirm = false },
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = BgCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ============================================================================
// 1. Live Hardware Profiler & Benchmark Studio Hero Card
// ============================================================================
@Composable
private fun LiveBenchmarkStudioHeroCard(
    uiState: LynxUiState,
    selectedDuration: Int,
    onSelectDuration: (Int) -> Unit,
    onStartBenchmark: () -> Unit,
    onCancelBenchmark: () -> Unit,
    onDetectTarget: () -> Unit,
    onOpenOverlay: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isRunning = uiState.isBenchmarking
    val result = uiState.benchmarkResult
    val targetApp = uiState.benchmarkTargetAppName.ifBlank { "Game Target" }
    val targetPkg = uiState.benchmarkTargetPackage.ifBlank { "com.HoYoverse.hkrpgoversea" }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_bench")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_dot_alpha"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, if (isRunning) AccentOrange.copy(alpha = 0.6f) else BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Title + Liveness Dot + Target Status Badge
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
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isRunning) AccentOrange.copy(alpha = 0.16f) else BgElevated)
                            .border(
                                BorderStroke(0.8.dp, if (isRunning) AccentOrange.copy(alpha = 0.5f) else BorderSubtle),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = if (isRunning) AccentOrange else AccentPurple,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "LIVE HARDWARE PROFILER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRunning) AccentOrange else AccentPurple,
                            letterSpacing = 0.6.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isRunning) AccentOrange.copy(alpha = dotAlpha)
                                        else AccentGreen.copy(alpha = 0.6f)
                                    )
                            )
                            Text(
                                text = if (isRunning) "MEREKAM PRESENTATION TIMESTAMPS" else "FRAME PACING STUDIO SIAP",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentCyan.copy(alpha = 0.16f),
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = uiState.state.activeProfile.uppercase(),
                        color = AccentCyan,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Target Game Banner (Clickable to switch / detect foreground app)
            Surface(
                onClick = onDetectTarget,
                shape = RoundedCornerShape(14.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIconImage(
                            packageName = targetPkg,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Column {
                            Text(
                                text = targetApp,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = targetPkg,
                                color = TextSecondary,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Ganti", fontSize = 11.sp, color = AccentCyan, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.ChevronRight, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Benchmark Running State View
            if (isRunning) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF070B0E),
                    border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = {
                                    val total = uiState.benchmarkTotalSeconds.coerceAtLeast(1).toFloat()
                                    val cur = uiState.benchmarkProgressSeconds.toFloat()
                                    (1f - (cur / total)).coerceIn(0f, 1f)
                                },
                                modifier = Modifier.size(76.dp),
                                color = AccentOrange,
                                trackColor = BgElevated,
                                strokeWidth = 5.dp
                            )
                            Text(
                                text = "${uiState.benchmarkProgressSeconds}s",
                                color = TextPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text(
                            text = "Silakan beralih ke game dan lakukan rotasi kamera 3D atau pertempuran intensif.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp
                        )

                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCancelBenchmark()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                            border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.heightIn(min = 44.dp)
                        ) {
                            Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Batalkan Pengujian", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (result != null) {
                // Latest Result Hero Summary
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("AVERAGE RENDER FPS", color = TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", result.averageFps)} FPS",
                                    color = when {
                                        result.averageFps >= 50f -> AccentCyan
                                        result.averageFps >= 35f -> AccentGreen
                                        else -> AccentOrange
                                    },
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("MEDIAN FRAMETIME", color = TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", result.medianFrametimeMs)} ms",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Stability Verdict Tag
                        val (verdictText, verdictColor) = when {
                            result.jankyFramesPercent <= 5f -> "Ultra Smooth — Frame Pacing Sangat Stabil" to AccentGreen
                            result.jankyFramesPercent <= 15f -> "Sangat Baik — Stabilitas Tinggi" to AccentCyan
                            else -> "Variasi Frametime Terdeteksi" to AccentOrange
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = verdictColor.copy(alpha = 0.15f),
                            border = BorderStroke(0.6.dp, verdictColor.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = verdictText,
                                color = verdictColor,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // 4 Mini Tiles
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MetricChip("1% LOW", "${String.format(Locale.US, "%.1f", result.fps1PercentLow)} FPS", AccentCyan, Modifier.weight(1f))
                            MetricChip("JITTER", "±${String.format(Locale.US, "%.1f", result.frametimeJitterMs)} ms", AccentPurple, Modifier.weight(1f))
                            MetricChip("JANK FRAME", "${result.jankyFramesPercent}%", if (result.jankyFramesPercent > 10f) AccentRed else AccentGreen, Modifier.weight(1f))
                            MetricChip("SAMPLED", "${result.sampledFrames}", TextPrimary, Modifier.weight(1f))
                        }

                        // Frametime Canvas Waveform
                        if (result.frametimes.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text("DISTRIBUSI FRAMETIME REAL-TIME", color = TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            FrametimeCanvasChart(
                                frametimes = result.frametimes,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF070B0E))
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }

            // Duration Picker
            Text("PILIH DURASI PENGUJIAN BENCHMARK", color = TextTertiary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10 to "10s", 30 to "30s", 60 to "1 Min", 120 to "2 Min").forEach { (sec, label) ->
                    val isSel = selectedDuration == sec
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSel) AccentPurple.copy(alpha = 0.22f) else BgElevated,
                        border = BorderStroke(1.dp, if (isSel) AccentPurple else BorderGlass),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectDuration(sec)
                            }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) AccentPurple else TextSecondary,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }

            // Action Buttons Row: Hero Start + Open Overlay
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartBenchmark()
                    },
                    enabled = !isRunning,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                    modifier = Modifier
                        .weight(1.3f)
                        .heightIn(min = 50.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isRunning) "Sedang Menguji..." else "Mulai Benchmark",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenOverlay()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                ) {
                    Icon(Icons.Default.PictureInPicture, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Buka Overlay", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ============================================================================
// 2. Storage & System Optimization Bento Suite
// ============================================================================
@Composable
private fun SystemOptimizationSuiteCard(
    uiState: LynxUiState,
    onRunFstrim: () -> Unit,
    onRunDropCaches: () -> Unit,
    onExportDiagnostic: () -> Unit,
    onRequestResetStock: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentCyan.copy(alpha = 0.15f))
                        .border(BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.4f)), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Build, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("SUITE OPTIMASI & STORAGE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentCyan, letterSpacing = 0.5.sp)
                    Text("Pemeliharaan Sistem 1-Klik", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }

            Text(
                text = "Pembersihan memori fisik, defragmentasi database aplikasi Android, dan trim blok penyimpanan flash NAND.",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )

            // Optimization Modules Bento Grid
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Module 1: SQLite & FSTRIM
                OptimizationActionTile(
                    title = "Optimasi SQLite & FSTRIM Storage",
                    subtitle = "Trim blok memori flash NAND yang tidak terpakai dan jalankan VACUUM defragmentasi database SQLite.",
                    icon = Icons.Default.Storage,
                    accent = AccentCyan,
                    buttonText = if (uiState.isMaintenanceExecuting) "Memproses..." else "Jalankan FSTRIM",
                    isLoading = uiState.isMaintenanceExecuting,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRunFstrim()
                    }
                )

                // Module 2: CCleaner
                OptimizationActionTile(
                    title = "Jalankan CCleaner (Memory Purge)",
                    subtitle = "Memadatkan ZRAM swap, membersihkan cache kernel L1/L2/L3 (drop_caches 3), dan merapikan log crash.",
                    icon = Icons.Default.CleaningServices,
                    accent = AccentBlue,
                    buttonText = if (uiState.isMaintenanceExecuting) "Memproses..." else "Jalankan Purge",
                    isLoading = uiState.isMaintenanceExecuting,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRunDropCaches()
                    }
                )

                // Module 3: Ekspor Diagnostik ZIP
                OptimizationActionTile(
                    title = "Ekspor Laporan Diagnostik (.ZIP)",
                    subtitle = "Kompilasi ringkasan dmesg, logcat, sysfs kernel, dan konfigurasi modul ke direktori /sdcard/Lynx/.",
                    icon = Icons.Default.FolderZip,
                    accent = AccentOrange,
                    buttonText = if (uiState.diagnosticHubState.isExportingZip) "Mengekspor..." else "Ekspor (.ZIP)",
                    isLoading = uiState.diagnosticHubState.isExportingZip,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onExportDiagnostic()
                    }
                )

                // Module 4: Reset Aman Kernel
                OptimizationActionTile(
                    title = "Reset Aman Kernel ke Bawaan (Stock Safe)",
                    subtitle = "Kembalikan seluruh parameter CPU, GPU, governor, dan I/O scheduler ke konfigurasi awal OEM.",
                    icon = Icons.Default.RestartAlt,
                    accent = AccentGreen,
                    buttonText = "Reset Kernel",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRequestResetStock()
                    }
                )
            }
        }
    }
}

// ============================================================================
// 3. Persistent Benchmark History Log Card
// ============================================================================
@Composable
private fun SavedBenchmarkHistoryCard(
    savedSessions: List<SavedBenchmarkSession>,
    onInspect: (SavedBenchmarkSession) -> Unit,
    onDelete: (SavedBenchmarkSession) -> Unit,
    onClearAll: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
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
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentPurple.copy(alpha = 0.15f))
                            .border(BorderStroke(0.8.dp, AccentPurple.copy(alpha = 0.4f)), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.History, null, tint = AccentPurple, modifier = Modifier.size(20.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("RIWAYAT SESI BENCHMARK", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentPurple, letterSpacing = 0.5.sp)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BgElevated,
                                border = BorderStroke(0.8.dp, BorderGlass)
                            ) {
                                Text(
                                    text = "${savedSessions.size} Sesi",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text("Arsip Frame Pacing 1, 2, 3, dst", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }

                if (savedSessions.isNotEmpty()) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClearAll()
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = AccentRed.copy(alpha = 0.12f),
                        border = BorderStroke(0.8.dp, AccentRed.copy(alpha = 0.4f)),
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, null, tint = AccentRed, modifier = Modifier.size(15.dp))
                            Text("Bersihkan", fontSize = 10.5.sp, color = AccentRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Text(
                text = "Setiap pengujian benchmark yang selesai otomatis diarsipkan secara lokal. Anda dapat membuka detail analitik atau menghapus sesi kapan saja.",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )

            if (savedSessions.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.HourglassEmpty, null, tint = TextTertiary, modifier = Modifier.size(28.dp))
                        Text("Belum Ada Sesi Benchmark Tersimpan", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                        Text(
                            text = "Jalankan benchmark di atas untuk mengarsipkan data sesi pengujian pertama.",
                            fontSize = 10.5.sp,
                            color = TextTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    savedSessions.forEach { session ->
                        SavedBenchmarkItemRow(
                            session = session,
                            onInspect = { onInspect(session) },
                            onDelete = { onDelete(session) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedBenchmarkItemRow(
    session: SavedBenchmarkSession,
    onInspect: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderGlass),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onInspect()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Sesi # + Date + App Name + Delete Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentPurple.copy(alpha = 0.16f),
                        border = BorderStroke(0.8.dp, AccentPurple.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "SESI #${session.sessionNumber}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentPurple,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = session.appName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDelete()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Hapus Sesi",
                        tint = AccentRed.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Middle Row: 4 Metric Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricChip("AVG FPS", "${String.format(Locale.US, "%.1f", session.averageFps)} FPS", AccentCyan, Modifier.weight(1f))
                MetricChip("1% LOW", "${String.format(Locale.US, "%.1f", session.fps1PercentLow)} FPS", AccentBlue, Modifier.weight(1f))
                MetricChip("JITTER", "±${String.format(Locale.US, "%.1f", session.frametimeJitterMs)} ms", AccentPurple, Modifier.weight(1f))
                MetricChip("DURASI", "${session.durationSeconds}s", TextPrimary, Modifier.weight(1f))
            }

            // Bottom Row: Stability Verdict Tag + Open Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.dateFormatted,
                    fontSize = 10.5.sp,
                    color = TextTertiary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Buka Detail", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentCyan)
                    Icon(Icons.Default.ChevronRight, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ============================================================================
// 4. BenchmarkSessionDetailDialog — Inspection Modal
// ============================================================================
@Composable
private fun BenchmarkSessionDetailDialog(
    session: SavedBenchmarkSession,
    onDismiss: () -> Unit,
    onExport: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = BgCard,
            border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentPurple.copy(alpha = 0.16f))
                                .border(BorderStroke(0.8.dp, AccentPurple.copy(alpha = 0.5f)), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Speed, null, tint = AccentPurple, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(
                                text = "DETAIL BENCHMARK #${session.sessionNumber}",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${session.appName} • ${session.dateFormatted}",
                                fontSize = 10.5.sp,
                                color = TextTertiary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, "Tutup", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Summary Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("PREDIKAT STABILITAS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AccentPurple)
                            Text(session.stabilityGrade, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(session.summary, fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp)
                        }
                    }

                    // 6 Key Precision Metrics Grid
                    Text("METRIK ANALITIK SURFACEFLINGER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextTertiary)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("Average FPS", "${String.format(Locale.US, "%.1f", session.averageFps)} FPS", AccentCyan, Modifier.weight(1f))
                        MetricTile("1% Low FPS", "${String.format(Locale.US, "%.1f", session.fps1PercentLow)} FPS", AccentBlue, Modifier.weight(1f))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("Median Latency", "${String.format(Locale.US, "%.1f", session.medianFrametimeMs)} ms", TextPrimary, Modifier.weight(1f))
                        MetricTile("Jitter Frametime", "±${String.format(Locale.US, "%.1f", session.frametimeJitterMs)} ms", AccentPurple, Modifier.weight(1f))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("Janky Frames", "${session.jankyFramesCount} (${session.jankyFramesPercent}%)", if (session.jankyFramesPercent > 10f) AccentRed else AccentGreen, Modifier.weight(1f))
                        MetricTile("Total Sample", "${session.sampledFrames} frames", TextSecondary, Modifier.weight(1f))
                    }

                    // Hardware Telemetry
                    Text("TELEMETRI HARDWARE SAAT PENGUJIAN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextTertiary)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("CPU Clock", fontSize = 9.sp, color = TextTertiary)
                                Text(if (session.avgCpuClockMhz > 0) "${session.avgCpuClockMhz} MHz" else "--", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Column {
                                Text("GPU Load", fontSize = 9.sp, color = TextTertiary)
                                Text(if (session.avgGpuClockMhz > 0) "${session.avgGpuClockMhz}MHz (${session.avgGpuLoadPct}%)" else "--", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                            }
                            Column {
                                Text("Suhu", fontSize = 9.sp, color = TextTertiary)
                                Text(if (session.avgBatteryTempC > 0f) "${session.avgBatteryTempC}°C" else "--", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                            }
                            Column {
                                Text("Daya", fontSize = 9.sp, color = TextTertiary)
                                Text(if (session.avgBatteryWatt > 0f) "${session.avgBatteryWatt}W" else "--", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                            }
                        }
                    }

                    // Frametime Waveform Chart
                    if (session.frametimes.isNotEmpty()) {
                        Text("DISTRIBUSI FRAMETIME LENGKAP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextTertiary)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BgElevated,
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                FrametimeCanvasChart(
                                    frametimes = session.frametimes,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF070B0E))
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                    ) {
                        Text("Tutup", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onExport()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = 46.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Salin Laporan", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ============================================================================
// Helpers
// ============================================================================
@Composable
private fun MetricChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = BgCard,
        border = BorderStroke(0.6.dp, BorderSubtle),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 8.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderGlass),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, color = TextTertiary, fontSize = 9.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(value, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OptimizationActionTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    buttonText: String,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderGlass),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.15f))
                        .border(BorderStroke(0.8.dp, accent.copy(alpha = 0.4f)), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(subtitle, fontSize = 10.sp, color = TextSecondary, lineHeight = 14.sp)
                }
            }

            Button(
                onClick = onClick,
                enabled = !isLoading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                } else {
                    Text(buttonText, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
