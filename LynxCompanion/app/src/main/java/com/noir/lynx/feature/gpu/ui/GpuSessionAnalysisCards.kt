package com.noir.lynx.feature.gpu.ui

import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

import java.util.Locale
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.display.ColorMatrixEngine
import com.noir.lynx.display.ColorMatrixProfile
import com.noir.lynx.display.DisplayPipelineInfo
import com.noir.lynx.hardware.DisplayModeInfo
import com.noir.lynx.hardware.GpuBackend
import com.noir.lynx.hardware.GraphicsCapabilities
import com.noir.lynx.hardware.NodeStatus
import com.noir.lynx.lab.DropCorrelator
import com.noir.lynx.lab.DropEvent
import com.noir.lynx.lab.FrameSessionReport
import java.text.SimpleDateFormat
import java.util.*
import com.noir.lynx.ui.*

@Composable
fun SessionSummaryCard(
    report: FrameSessionReport,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentCyan
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LynxCard(
        title = "Metrik Sesi: ${report.packageName.substringAfterLast('.')}",
        subtitle = "Pukul ${timeFormat.format(Date(report.timestamp))} • Durasi ${report.sessionDurationSec}s • ${report.totalFrames} Frame",
        icon = Icons.Default.Speed,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        // ── 4-Metric Grid ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Avg FPS
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("RATA-RATA FPS", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = String.format("%.1f", report.avgFps),
                        color = cardAccent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // 1% Low FPS
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("1% LOW FPS", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = String.format("%.1f", report.low1PctFps),
                        color = if (report.low1PctFps < 45f) AccentRed else AccentOrange,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // 0.1% Low FPS
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("0.1% LOW FPS", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = String.format("%.1f", report.low01PctFps),
                        color = if (report.low01PctFps < 30f) AccentRed else TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Stability %
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("KESTABILAN", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = String.format("%.0f%%", report.stabilityPct),
                        color = if (report.stabilityPct >= 90f) AccentGreen else AccentOrange,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // ── Secondary Detail Stats ──
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Variansi Frame Time (Deviasi)", color = TextSecondary, fontSize = 11.5.sp)
                    Text("${String.format("%.2f", report.frameTimeVarianceMs)} ms", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Frame Jank (Drop > 16.7ms)", color = TextSecondary, fontSize = 11.5.sp)
                    Text("${report.jankCount} frame", color = if (report.jankCount > 10) AccentRed else TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Persentil Frame Time (p50 / p95 / p99)", color = TextSecondary, fontSize = 11.5.sp)
                    Text(
                        "${String.format("%.1f", report.p50Ms)} / ${String.format("%.1f", report.p95Ms)} / ${String.format("%.1f", report.p99Ms)} ms",
                        color = AccentCyan,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Suhu GPU Puncak / Rata-rata", color = TextSecondary, fontSize = 11.5.sp)
                    Text(
                        "${String.format("%.1f", report.peakTempC)}°C / ${String.format("%.1f", report.avgTempC)}°C",
                        color = if (report.peakTempC >= 50f) AccentRed else AccentGreen,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Frekuensi GPU Puncak", color = TextSecondary, fontSize = 11.5.sp)
                    Text("${report.peakGpuMhz} MHz", color = AccentOrange, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Export Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                onClick = {
                    onExportCsv()
                    Toast.makeText(context, "Sesi diexport ke Download/Lynx/", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(8.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.5f)),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Export CSV",
                        tint = cardAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Export Sesi ke CSV",
                        color = cardAccent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ============================================================
//  5. PERFORMANCE LAB — DROP ROOT CAUSE ANALYSIS CARD
// ============================================================

@Composable
fun DropAnalysisCard(
    report: FrameSessionReport,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentOrange

    val dropEvents = report.dropEvents
    val recommendation = report.recommendation
        ?: DropCorrelator.generateRecommendation(dropEvents, report.avgFps, report.peakGpuMhz, 120)

    LynxCard(
        title = "Analisis Akar Penyebab Frame Drop",
        subtitle = "Korelasi timestamp frame drop terhadap clock, thermal, & CPU queue",
        icon = Icons.Default.Troubleshoot,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        if (dropEvents.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text("Tidak Terdeteksi Stutter Signifikan", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Frame pacing berjalan mulus mendekati target refresh rate.", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        } else {
            // Recommendation Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardAccent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, cardAccent.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = cardAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Rekomendasi Optimasi Lynx", color = cardAccent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = recommendation,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Top Drop Events Table
            Text("Sampel Kejadian Frame Drop Terparah", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                dropEvents.take(5).forEach { event ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Detik ke-${event.timeOffsetSec}s",
                                    color = AccentCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${String.format("%.1f", event.frameTimeMs)} ms",
                                    color = AccentRed,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = event.suspectedCause,
                                color = TextPrimary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Kondisi: ${event.gpuClockMhz} MHz • ${event.gpuLoadPct}% Load • ${String.format("%.1f", event.tempC)}°C",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  6. PERFORMANCE LAB — SESSION HISTORY LIST
// ============================================================

@Composable
fun SessionHistoryList(
    sessions: List<FrameSessionReport>,
    onSelectSession: (FrameSessionReport) -> Unit,
    modifier: Modifier = Modifier
) {
    val cardAccent = AccentPurple
    val timeFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    LynxCard(
        title = "Riwayat Sesi Rekaman (${sessions.size}/20)",
        subtitle = "Daftar histori benchmark frame pacing yang tersimpan secara lokal",
        icon = Icons.Default.History,
        accentColor = cardAccent,
        modifier = modifier
    ) {
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Belum ada rekaman sesi. Mulai rekam pada panel di atas.",
                    color = TextSecondary,
                    fontSize = 11.5.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sessions.take(6).forEach { session ->
                    Surface(
                        onClick = { onSelectSession(session) },
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = session.packageName.substringAfterLast('.'),
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    softWrap = true
                                )
                                Text(
                                    text = "${timeFormat.format(Date(session.timestamp))} • ${session.sessionDurationSec}s",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AccentCyanDim,
                                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "${String.format("%.1f", session.avgFps)} FPS",
                                        color = AccentCyan,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AccentOrangeDim,
                                    border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "1% ${String.format("%.1f", session.low1PctFps)}",
                                        color = AccentOrange,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  7. HARDWARE CAPABILITY & INFO DASHBOARD (OVERHAULED INFO TAB)
// ============================================================

