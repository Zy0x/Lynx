package com.noir.lynx.feature.cpu.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*

/**
 * CpuHardwareMonitorCard — Redesigned Master CPU Monitor & Silicon Telemetry.
 *
 * Standards:
 * 1. Master Telemetry Hero: High-precision Bezier waveform with 30s min/avg/max tracking.
 * 2. 8-Core Bento Matrix: Tactile 2x4 core chips with Little/Big role markers, MHz, and load bars.
 * 3. Hotplug Safety: Protected Core 0 notice and explicit confirmation modal with haptics.
 * 4. Structured Process & Topology Sub-cards: Sentence case, clean tabular figures, zero visual slop.
 */
@Composable
fun CpuHardwareMonitorCard(
    uiState: LynxUiState,
    onToggleCore: (CpuCoreInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val cores = uiState.cpuCores
    val history = uiState.cpuLoadHistory
    val curLoad = uiState.totalCpuLoadPercent.coerceIn(0, 100)
    val minVal = if (history.isNotEmpty()) history.minOrNull() ?: curLoad else curLoad
    val maxVal = if (history.isNotEmpty()) history.maxOrNull() ?: curLoad else curLoad
    val avgVal = if (history.isNotEmpty()) history.average().toInt() else curLoad

    var showCore0Notice by remember { mutableStateOf(false) }
    var pendingHotplugCore by remember { mutableStateOf<CpuCoreInfo?>(null) }

    // Telemetry summary metrics
    val temp = uiState.resolveCpuTempC()
    val onlineCores = cores.filter { it.isOnline }
    val avgMhz = if (onlineCores.isNotEmpty()) onlineCores.map { it.curFreqKhz / 1000 }.average().toInt() else 0
    val peakMhz = uiState.clusters.map { it.curMax / 1000 }.maxOrNull() ?: 2050

    val (loadCategoryLabel, loadCategoryColor) = when {
        curLoad <= 25 -> Pair("Beban Ringan", AccentGreen)
        curLoad <= 65 -> Pair("Beban Optimal", AccentCyan)
        curLoad <= 85 -> Pair("Beban Tinggi", AccentOrange)
        else -> Pair("Beban Puncak", AccentRed)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ── 1. Master Telemetry Hero Card (Waveform & 8-Core Grid) ──
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF10141F),
            border = BorderStroke(0.8.dp, Brush.verticalGradient(listOf(Color(0x28FFFFFF), Color(0x10FFFFFF)))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header: Title & Live indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentGreen.copy(alpha = 0.12f),
                            border = BorderStroke(0.5.dp, AccentGreen.copy(alpha = 0.35f)),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Analytics, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Aktivitas CPU Real-Time", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("Pemantauan live frekuensi dan beban inti", fontSize = 9.5.sp, color = TextSecondary.copy(alpha = 0.75f))
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentGreen.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, AccentGreen.copy(alpha = 0.35f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(AccentGreen))
                            Text("Live", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                        }
                    }
                }

                // Hero Metric Cluster: Total Load + Status Pill on Left, Bento Stats on Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$curLoad",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary,
                                lineHeight = 36.sp
                            )
                            Text(
                                text = "%",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(loadCategoryColor))
                            Spacer(Modifier.width(5.dp))
                            Text(loadCategoryLabel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = loadCategoryColor)
                        }
                    }

                    // Mini Stats Grid
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0C0F17),
                        border = BorderStroke(0.6.dp, Color(0x15FFFFFF))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Suhu:", fontSize = 9.5.sp, color = TextSecondary.copy(alpha = 0.6f))
                                Text(if (temp > 0) "${temp}°C" else "38°C", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Rata-rata:", fontSize = 9.5.sp, color = TextSecondary.copy(alpha = 0.6f))
                                Text("$avgMhz MHz", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = TextPrimary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Puncak:", fontSize = 9.5.sp, color = TextSecondary.copy(alpha = 0.6f))
                                Text("$peakMhz MHz", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = AccentCyan)
                            }
                        }
                    }
                }

                // Bezier Curve Load Waveform
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        if (w <= 0 || h <= 0) return@Canvas

                        val gridColor = Color(0x12FFFFFF)
                        drawLine(gridColor, start = Offset(0f, h * 0.25f), end = Offset(w, h * 0.25f), strokeWidth = 1f)
                        drawLine(gridColor, start = Offset(0f, h * 0.50f), end = Offset(w, h * 0.50f), strokeWidth = 1f)
                        drawLine(gridColor, start = Offset(0f, h * 0.75f), end = Offset(w, h * 0.75f), strokeWidth = 1f)

                        val points = if (history.size < 2) listOf(curLoad, curLoad) else history
                        val stepX = w / (points.size - 1).coerceAtLeast(1)
                        val path = Path()
                        val fillPath = Path()

                        points.forEachIndexed { i, load ->
                            val normY = (1f - (load.coerceIn(0, 100) / 100f)) * (h - 8f) + 4f
                            val x = i * stepX
                            if (i == 0) {
                                path.moveTo(x, normY)
                                fillPath.moveTo(x, h)
                                fillPath.lineTo(x, normY)
                            } else {
                                val prevLoad = points[i - 1]
                                val prevNormY = (1f - (prevLoad.coerceIn(0, 100) / 100f)) * (h - 8f) + 4f
                                val prevX = (i - 1) * stepX
                                val cx1 = prevX + (x - prevX) / 2f
                                val cy1 = prevNormY
                                val cx2 = prevX + (x - prevX) / 2f
                                val cy2 = normY
                                path.cubicTo(cx1, cy1, cx2, cy2, x, normY)
                                fillPath.cubicTo(cx1, cy1, cx2, cy2, x, normY)
                            }
                        }

                        fillPath.lineTo(w, h)
                        fillPath.close()

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(AccentGreen.copy(alpha = 0.35f), AccentGreen.copy(alpha = 0.05f), Color.Transparent)
                            )
                        )
                        drawPath(path = path, color = AccentGreen, style = Stroke(width = 2f))

                        val lastX = w
                        val lastY = (1f - (curLoad / 100f)) * (h - 8f) + 4f
                        drawCircle(color = AccentGreen.copy(alpha = 0.3f), radius = 6f, center = Offset(lastX, lastY))
                        drawCircle(color = AccentGreen, radius = 3f, center = Offset(lastX, lastY))
                    }
                }

                // Min / Avg / Max label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Riwayat Beban (30 Detik)", fontSize = 9.sp, color = TextTertiary)
                    Text("Min $minVal% • Avg $avgVal% • Max $maxVal%", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                }

                HorizontalDivider(color = Color(0x12FFFFFF), thickness = 0.8.dp)

                // 8-Core Live Grid Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Status Inti CPU (${if (cores.isNotEmpty()) cores.size else 8} Inti)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text("Ketuk inti untuk kelola daya", fontSize = 8.5.sp, color = TextTertiary)
                }

                // 2x4 Core Tactile Bento Grid
                val rows = cores.chunked(4)
                rows.forEachIndexed { _, quad ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quad.forEach { core ->
                            val parentCluster = uiState.clusters.find { it.containsCore(core.coreId) }
                            val isPerfCore = parentCluster?.let { it.role.contains("Big", true) || it.role.contains("Perf", true) || it.id > 0 } ?: (core.coreId >= 6)
                            val coreAccent = if (!core.isOnline) TextTertiary else if (isPerfCore) AccentOrange else AccentGreen
                            val load = core.loadPercent.coerceIn(0, 100)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF0C0F17),
                                border = BorderStroke(0.6.dp, if (core.isOnline) coreAccent.copy(alpha = 0.35f) else Color(0x15FFFFFF)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (core.coreId == 0) {
                                            showCore0Notice = true
                                        } else if (core.isSwitchable) {
                                            pendingHotplugCore = core
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "C${core.coreId}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (core.isOnline) coreAccent else TextSecondary
                                            )
                                            if (core.coreId == 0) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = null,
                                                    tint = TextTertiary,
                                                    modifier = Modifier.size(9.dp)
                                                )
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(3.dp),
                                                color = if (isPerfCore) AccentOrange.copy(alpha = 0.15f) else AccentGreen.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = if (isPerfCore) "B" else "L",
                                                    fontSize = 7.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isPerfCore) AccentOrange else AccentGreen,
                                                    modifier = Modifier.padding(horizontal = 2.5.dp, vertical = 0.5.dp)
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(if (core.isOnline) coreAccent else AccentRed)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = if (core.isOnline) "${core.curFreqKhz / 1000}" else "OFF",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (core.isOnline) TextPrimary else AccentRed
                                    )
                                    if (core.isOnline) {
                                        Text("MHz", fontSize = 8.sp, color = TextSecondary, lineHeight = 8.sp)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(BgElevated)
                                    ) {
                                        if (core.isOnline) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(fraction = (load / 100f).coerceIn(0.04f, 1f))
                                                    .fillMaxHeight()
                                                    .background(coreAccent)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = if (core.isOnline) "$load%" else "--",
                                        fontSize = 8.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                        color = if (core.isOnline) TextSecondary else TextTertiary
                                    )
                                }
                            }
                        }
                        if (quad.size < 4) {
                            for (i in 0 until (4 - quad.size)) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // ── 2. Top CPU Consumer Processes Card ────────────────────
        TopProcessListCard(
            procs = uiState.topCpuProcesses,
            clusters = uiState.clusters
        )

        // ── 3. Hardware Topology Spec Card ────────────────────────
        CpuTopologySpecCard(
            socPlatform = uiState.socPlatformName,
            socTopology = uiState.socTopology,
            clusters = uiState.clusters,
            siliconDetails = uiState.siliconTopologyDetails,
            uiState = uiState
        )
    }

    // Hotplug Confirmation Dialog
    if (pendingHotplugCore != null) {
        val core = pendingHotplugCore!!
        val willTurnOff = core.isOnline
        AlertDialog(
            onDismissRequest = { pendingHotplugCore = null },
            containerColor = Color(0xFF141824),
            shape = RoundedCornerShape(14.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (willTurnOff) Icons.Default.PowerSettingsNew else Icons.Default.Bolt,
                        contentDescription = null,
                        tint = if (willTurnOff) AccentOrange else AccentGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (willTurnOff) "Parkir Inti CPU ${core.coreId}?" else "Aktifkan Inti CPU ${core.coreId}?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            },
            text = {
                Text(
                    text = if (willTurnOff) {
                        "Inti CPU ${core.coreId} akan dinonaktifkan sementara untuk menghemat konsumsi daya. Anda dapat mengaktifkannya kembali kapan saja."
                    } else {
                        "Inti CPU ${core.coreId} akan diaktifkan kembali ke status siaga untuk memproses beban kerja komputasi."
                    },
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val c = core
                        pendingHotplugCore = null
                        onToggleCore(c)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (willTurnOff) AccentOrange else AccentGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (willTurnOff) "Nonaktifkan Inti" else "Aktifkan Inti", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingHotplugCore = null }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    // Core 0 Protection Notice Dialog (Reassuring & Informative)
    if (showCore0Notice) {
        AlertDialog(
            onDismissRequest = { showCore0Notice = false },
            containerColor = Color(0xFF141824),
            shape = RoundedCornerShape(14.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Core 0 Terlindungi", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            },
            text = {
                Text(
                    "Core 0 berfungsi sebagai Master Core untuk sistem operasi Android dan kernel. Demi menjaga stabilitas serta responsivitas perangkat, Core 0 selalu dipertahankan aktif secara otomatis.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.5.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showCore0Notice = false }) {
                    Text("Mengerti", color = AccentCyan, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}
