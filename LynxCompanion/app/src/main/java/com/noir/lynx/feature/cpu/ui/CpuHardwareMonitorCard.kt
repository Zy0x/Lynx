package com.noir.lynx.feature.cpu.ui
import com.noir.lynx.feature.cpu.ui.CompactClusterRow

import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*

import java.util.Locale
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.noir.lynx.data.*
import com.noir.lynx.feature.cpu.ui.CpuSetsTaskShieldCard

import com.noir.lynx.ui.*

@Composable
fun CpuHardwareMonitorCard(
    uiState: LynxUiState,
    onToggleCore: (CpuCoreInfo) -> Unit,
    modifier: Modifier = Modifier
) {
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

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Master Hero Card (Bezier Waveform & 8-Core Live Grid)
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header: Title & Live indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "AKTIVITAS CPU REAL-TIME",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AccentGreen)
                        )
                        Text(
                            text = "Live Stream",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentGreen
                        )
                    }
                }

                // Hero Metric Row: Big Percent on Left, Compact Specs on Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "$curLoad%",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            lineHeight = 36.sp
                        )
                        Text(
                            text = "Beban Sistem Total",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.8.dp, BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Suhu:", fontSize = 9.5.sp, color = TextTertiary)
                                Text(if (temp > 0) "${temp}°C" else "38°C", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Rata-rata:", fontSize = 9.5.sp, color = TextTertiary)
                                Text("$avgMhz MHz", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Puncak:", fontSize = 9.5.sp, color = TextTertiary)
                                Text("$peakMhz MHz", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
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

                        val gridColor = BorderSubtle
                        drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, h * 0.25f), end = androidx.compose.ui.geometry.Offset(w, h * 0.25f), strokeWidth = 1f)
                        drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, h * 0.50f), end = androidx.compose.ui.geometry.Offset(w, h * 0.50f), strokeWidth = 1f)
                        drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, h * 0.75f), end = androidx.compose.ui.geometry.Offset(w, h * 0.75f), strokeWidth = 1f)

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
                        drawCircle(color = AccentGreen.copy(alpha = 0.3f), radius = 6f, center = androidx.compose.ui.geometry.Offset(lastX, lastY))
                        drawCircle(color = AccentGreen, radius = 3f, center = androidx.compose.ui.geometry.Offset(lastX, lastY))
                    }
                }

                // Min / Avg / Max label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Riwayat Beban (30 Detik)", fontSize = 9.sp, color = TextTertiary)
                    Text("Min $minVal% • Avg $avgVal% • Max $maxVal%", fontSize = 9.sp, color = TextSecondary)
                }

                HorizontalDivider(color = BorderSubtle, thickness = 0.8.dp)

                // 8-Core Live Grid (Always Open, Clean & Symmetrical 2x4)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STATUS INTI CPU (${if (cores.isNotEmpty()) cores.size else 8} CORES)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.8.sp
                    )
                    Text("Ketuk untuk hotplug", fontSize = 8.5.sp, color = TextTertiary)
                }

                val rows = cores.chunked(4)
                rows.forEachIndexed { rowIndex, quad ->
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
                                shape = RoundedCornerShape(8.dp),
                                color = BgSurfaceLowest,
                                border = BorderStroke(0.8.dp, if (core.isOnline) coreAccent.copy(alpha = 0.35f) else BorderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (core.coreId == 0) {
                                            showCore0Notice = true
                                        } else if (core.isSwitchable) {
                                            pendingHotplugCore = core
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "C${core.coreId}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (core.isOnline) coreAccent else TextSecondary
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(if (core.isOnline) coreAccent else AccentRed)
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = if (core.isOnline) "${core.curFreqKhz / 1000}" else "OFF",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (core.isOnline) TextPrimary else AccentRed
                                    )
                                    if (core.isOnline) {
                                        Text("MHz", fontSize = 7.5.sp, color = TextSecondary, lineHeight = 7.5.sp)
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
                                        fontSize = 8.sp,
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

        // Top CPU Consumer Processes Card (Scene-Style)
        TopProcessListCard(
            procs = uiState.topCpuProcesses,
            clusters = uiState.clusters
        )

        // Hardware Topology Spec Card
        CpuTopologySpecCard(
            socPlatform = uiState.socPlatformName,
            socTopology = uiState.socTopology,
            clusters = uiState.clusters,
            siliconDetails = uiState.siliconTopologyDetails
        )
    }

    // Hotplug Confirmation Dialog
    if (pendingHotplugCore != null) {
        val core = pendingHotplugCore!!
        val actionText = if (core.isOnline) "mematikan (Offline)" else "mengaktifkan (Online)"
        AlertDialog(
            onDismissRequest = { pendingHotplugCore = null },
            containerColor = BgCard,
            title = {
                Text(
                    text = "Konfirmasi CPU Hotplug",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin $actionText Core ${core.coreId}?",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val c = core
                        pendingHotplugCore = null
                        onToggleCore(c)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black)
                ) {
                    Text("Terapkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingHotplugCore = null }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    // Core 0 Protection Warning Dialog
    if (showCore0Notice) {
        AlertDialog(
            onDismissRequest = { showCore0Notice = false },
            containerColor = BgCard,
            title = {
                Text("Core 0 Dilindungi", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Text(
                    "Core 0 adalah Master Core sistem operasi Android yang menjalankan bootloader dan init daemon utama. Mematikan Core 0 akan memicu kernel panic seketika.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showCore0Notice = false }) {
                    Text("Mengerti", color = AccentCyan)
                }
            }
        )
    }
}

// ============================================================
//  9. TOP PROCESS LIST CARD (SCENE-STYLE PROCESS MONITOR)
// ============================================================
