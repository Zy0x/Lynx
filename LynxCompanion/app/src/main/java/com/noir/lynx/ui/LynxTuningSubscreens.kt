package com.noir.lynx.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.noir.lynx.data.*

// ============================================================
//  CATEGORY 1: CPU & GOVERNOR SUBSCREEN
// ============================================================

@Composable
fun TuningCpuCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state = uiState.state
    var pendingCoreAction by remember { mutableStateOf<CpuCoreInfo?>(null) }
    var showMasterCoreNotice by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Scene-Style Master Hero Card (Top Processes + SoC Info + 4-Column Per-Core Matrix) ──
        if (uiState.cpuCores.isNotEmpty()) {
            LaunchedEffect(Unit) {
                viewModel.refreshCpuCores()
                viewModel.refreshClusters()
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF131417),
                border = BorderStroke(1.dp, Color(0xFF1E2026)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // ── TOP HALF: Processes (Left) + Divider + SoC & 8-Bar Spectrum (Right) ──
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Column: Top 5 CPU Processes
                        Column(
                            modifier = Modifier.weight(1.15f).padding(end = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val procs = uiState.topCpuProcesses.take(5)
                            if (procs.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(95.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Memindai proses...", fontSize = 11.sp, color = Color(0xFF757585))
                                }
                            } else {
                                procs.forEach { p ->
                                    val isDaemon = p.name.contains("flinger", ignoreCase = true) ||
                                            p.name.contains("audio", ignoreCase = true) ||
                                            p.name.contains("sh", ignoreCase = true) ||
                                            p.name.contains("hardware", ignoreCase = true) ||
                                            p.name.contains("server", ignoreCase = true) ||
                                            p.name.contains("kernel", ignoreCase = true)
                                    val isGoogle = p.name.contains("google", ignoreCase = true)
                                    val isSystemUi = p.name.contains("sistem", ignoreCase = true) || p.name.contains("ui", ignoreCase = true)

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f).padding(end = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF1E2026)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                when {
                                                    isDaemon -> Icon(
                                                        imageVector = Icons.Default.Terminal,
                                                        contentDescription = null,
                                                        tint = Color(0xFFFFCC00),
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    isGoogle -> Icon(
                                                        imageVector = Icons.Default.Search,
                                                        contentDescription = null,
                                                        tint = Color(0xFF4285F4),
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    isSystemUi -> Icon(
                                                        imageVector = Icons.Default.Android,
                                                        contentDescription = null,
                                                        tint = Color(0xFF3DDC84),
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    else -> Icon(
                                                        imageVector = Icons.Default.Widgets,
                                                        contentDescription = null,
                                                        tint = Color(0xFF2979FF),
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = p.name,
                                                fontSize = 11.sp,
                                                color = Color(0xFFD0D0D5),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1f%%", p.cpuPercent),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = Color(0xFFA0A0AB)
                                        )
                                    }
                                }
                            }
                        }

                        // Thin Vertical Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF22242B))
                        )

                        // Right Column: Temperature, CPU, 8-Bar Spectrum, SoC Name, Total Load
                        Column(
                            modifier = Modifier.weight(1.0f).padding(start = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Top Right: Temperature
                            val tempVal = uiState.telemetry?.temp?.toFloatOrNull() ?: (uiState.batteryDetails?.realPhysicalTempC ?: 28f)
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f°C", tempVal),
                                    fontSize = 11.5.sp,
                                    color = Color(0xFFA0A0AB)
                                )
                            }

                            Spacer(Modifier.height(1.dp))

                            // Title: CPU
                            Text(
                                text = "CPU",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )

                            Spacer(Modifier.height(8.dp))

                            // The 8-Bar Live Core Spectrum Graph (One bar per core C0..C7)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.5.dp),
                                verticalAlignment = Alignment.Bottom,
                                modifier = Modifier.height(26.dp)
                            ) {
                                val allCores = uiState.cpuCores
                                (0..7).forEach { idx ->
                                    val core = allCores.getOrNull(idx)
                                    val isOnline = core?.isOnline ?: true
                                    val load = (core?.loadPercent ?: 0).coerceIn(0, 100)
                                    val targetH = if (!isOnline) {
                                        2.dp
                                    } else {
                                        (4f + (load / 100f) * 22f).dp
                                    }
                                    val animatedHeight by animateDpAsState(
                                        targetValue = targetH,
                                        animationSpec = tween(durationMillis = 250),
                                        label = "spectrum_$idx"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(7.dp)
                                            .height(animatedHeight)
                                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 1.dp, bottomEnd = 1.dp))
                                            .background(
                                                if (!isOnline) Color(0xFF33353E)
                                                else Color(0xFF2979FF)
                                            )
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // SoC Name & Topology (e.g. MT6781 (6+2))
                            val socText = "${uiState.socPlatformName.ifBlank { "MT6781" }} ${uiState.socTopology.ifBlank { "(6+2)" }}"
                            Text(
                                text = socText,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(2.dp))

                            // Total Load
                            Text(
                                text = "Load: ${uiState.totalCpuLoadPercent}%",
                                fontSize = 11.5.sp,
                                color = Color(0xFFA0A0AB)
                            )
                        }
                    }

                    // Thin Horizontal Divider across the card
                    HorizontalDivider(
                        color = Color(0xFF22242B),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    // ── BOTTOM HALF: 4 Columns x 2 Rows of Cores (Frameless, Scene-Style) ──
                    val cores = uiState.cpuCores
                    val rows = cores.chunked(4)
                    rows.forEachIndexed { rowIndex, quad ->
                        if (rowIndex > 0) Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            quad.forEach { core ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            if (core.coreId == 0) {
                                                showMasterCoreNotice = true
                                            } else if (core.isSwitchable) {
                                                pendingCoreAction = core
                                            }
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // 1. Percentage
                                    Text(
                                        text = if (core.isOnline) "${core.loadPercent}%" else "--",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (core.isOnline) Color(0xFFE2E2E8) else Color(0xFF555866)
                                    )

                                    Spacer(Modifier.height(4.dp))

                                    // 2. 5-Bar Vertical Mini Equalizer Graphic
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.Bottom,
                                        modifier = Modifier.height(13.dp)
                                    ) {
                                        val load = core.loadPercent.coerceIn(0, 100)
                                        val isOnline = core.isOnline

                                        // 5 natural equalizer height multipliers
                                        val multipliers = listOf(0.9f, 1.0f, 0.85f, 0.65f, 0.95f)
                                        multipliers.forEachIndexed { bIdx, mult ->
                                            val targetH = if (!isOnline) {
                                                1.5.dp
                                            } else if (load < 5) {
                                                2.dp
                                            } else {
                                                val ratio = ((load * mult).coerceIn(10f, 100f) / 100f)
                                                (2.5f + ratio * 10.5f).dp
                                            }
                                            val barH by animateDpAsState(
                                                targetValue = targetH,
                                                animationSpec = tween(durationMillis = 200),
                                                label = "miniEq_${core.coreId}_$bIdx"
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .width(3.5.dp)
                                                    .height(barH)
                                                    .clip(RoundedCornerShape(1.dp))
                                                    .background(
                                                        if (!isOnline) Color(0xFF33353E)
                                                        else if (load < 5) Color(0xFF2979FF).copy(alpha = 0.45f)
                                                        else Color(0xFF2979FF)
                                                    )
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(5.dp))

                                    // 3. Live Frequency
                                    Text(
                                        text = if (core.isOnline) "${core.curFreqKhz / 1000}MHz" else "OFFLINE",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (core.isOnline) Color.White else Color(0xFFFF5252),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(Modifier.height(1.dp))

                                    // 4. Frequency Range (Guaranteed Synchronized with Parent Cluster Domain)
                                    val parentCluster = uiState.clusters.find { it.containsCore(core.coreId) }
                                    val minKhz = parentCluster?.curMin ?: if (core.minFreqKhz > 0) core.minFreqKhz else 500000L
                                    val maxKhz = parentCluster?.curMax ?: if (core.maxFreqKhz > 0) core.maxFreqKhz else 2000000L
                                    val isLocked = parentCluster?.isLocked ?: core.isLocked
                                    val rangeText = if (minKhz == maxKhz) {
                                        if (isLocked) "${minKhz / 1000}MHz 🔒" else "${minKhz / 1000}MHz"
                                    } else {
                                        if (isLocked) "${minKhz / 1000}~${maxKhz / 1000}MHz 🔒" else "${minKhz / 1000}~${maxKhz / 1000}MHz"
                                    }
                                    val rangeColor = if (isLocked) Color(0xFF00E676) else Color(0xFF757585)
                                    Text(
                                        text = rangeText,
                                        fontSize = 9.sp,
                                        fontWeight = if (isLocked) FontWeight.SemiBold else FontWeight.Normal,
                                        color = rangeColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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

            // ── Real-Time CPU Load History Waveform Card (Canvas Graph) ──
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF131417),
                border = BorderStroke(1.dp, Color(0xFF1E2026)),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val history = uiState.cpuLoadHistory
                    val curLoad = uiState.totalCpuLoadPercent.coerceIn(0, 100)
                    val minVal = if (history.isNotEmpty()) history.minOrNull() ?: curLoad else curLoad
                    val maxVal = if (history.isNotEmpty()) history.maxOrNull() ?: curLoad else curLoad
                    val avgVal = if (history.isNotEmpty()) history.average().toInt() else curLoad

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                tint = Color(0xFF2979FF),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Grafik Beban CPU (Real-Time)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        // Current Load Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF2979FF).copy(alpha = 0.15f),
                            border = BorderStroke(0.8.dp, Color(0xFF2979FF).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "$curLoad%",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2979FF),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Smooth Bezier Curve Canvas Waveform
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            if (w <= 0 || h <= 0) return@Canvas

                            // Draw subtle horizontal grid lines (25%, 50%, 75%)
                            val gridColor = Color(0xFF1E2026)
                            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, h * 0.25f), end = androidx.compose.ui.geometry.Offset(w, h * 0.25f), strokeWidth = 1f)
                            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, h * 0.50f), end = androidx.compose.ui.geometry.Offset(w, h * 0.50f), strokeWidth = 1f)
                            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, h * 0.75f), end = androidx.compose.ui.geometry.Offset(w, h * 0.75f), strokeWidth = 1f)

                            val points = if (history.size < 2) {
                                listOf(curLoad, curLoad)
                            } else {
                                history
                            }

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

                            // Fill with vertical gradient
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF2979FF).copy(alpha = 0.35f),
                                        Color(0xFF2979FF).copy(alpha = 0.05f),
                                        Color.Transparent
                                    )
                                )
                            )

                            // Draw line stroke
                            drawPath(
                                path = path,
                                color = Color(0xFF2979FF),
                                style = Stroke(width = 2.5f)
                            )

                            // Draw glowing pulse dot at the latest point
                            val lastX = w
                            val lastY = (1f - (curLoad / 100f)) * (h - 8f) + 4f
                            drawCircle(
                                color = Color(0xFF2979FF).copy(alpha = 0.3f),
                                radius = 7f,
                                center = androidx.compose.ui.geometry.Offset(lastX, lastY)
                            )
                            drawCircle(
                                color = Color(0xFF2979FF),
                                radius = 3.5f,
                                center = androidx.compose.ui.geometry.Offset(lastX, lastY)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Stats summary bar (Min, Avg, Max)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Min: $minVal%", fontSize = 10.5.sp, color = Color(0xFF757585))
                        Text("Rata-rata: $avgVal%", fontSize = 10.5.sp, color = Color(0xFFA0A0AB), fontWeight = FontWeight.Medium)
                        Text("Puncak: $maxVal%", fontSize = 10.5.sp, color = Color(0xFF2979FF), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ── Dialog Konfirmasi Toggle Core CPU (Hotplug) ──
        if (pendingCoreAction != null) {
            val targetCore = pendingCoreAction!!
            val willEnable = !targetCore.isOnline
            val parentCluster = uiState.clusters.find { it.containsCore(targetCore.coreId) }
            val clusterType = parentCluster?.role ?: if (targetCore.coreId >= 4) "Performance" else "Efficiency"
            AlertDialog(
                onDismissRequest = { pendingCoreAction = null },
                containerColor = Color(0xFF16181D),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFCCCCCC),
                shape = RoundedCornerShape(16.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (willEnable) Icons.Default.PowerSettingsNew else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (willEnable) Color(0xFF00E5FF) else Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (willEnable) "Aktifkan Core ${targetCore.coreId}?" else "Nonaktifkan Core ${targetCore.coreId}?",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (willEnable) {
                                "Apakah Anda yakin ingin mengaktifkan kembali CPU Core ${targetCore.coreId} ($clusterType)? Core ini akan langsung dialokasikan oleh kernel scheduler untuk menangani beban kerja aplikasi."
                            } else {
                                "Apakah Anda yakin ingin mematikan CPU Core ${targetCore.coreId} ($clusterType)? Mematikan core akan menghentikan alokasi proses kernel pada core ini untuk menghemat konsumsi daya baterai."
                            },
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            color = Color(0xFFAAAAAA)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF202228),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Status saat ini:", fontSize = 11.sp, color = Color(0xFF888899))
                                Text(
                                    if (targetCore.isOnline) "Aktif (${targetCore.curFreqKhz / 1000}MHz)" else "OFFLINE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (targetCore.isOnline) Color(0xFF2979FF) else Color(0xFFFF5252)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.setCpuCoreOnline(targetCore.coreId, willEnable)
                            pendingCoreAction = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (willEnable) Color(0xFF2979FF) else Color(0xFFD32F2F)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (willEnable) "Aktifkan Core" else "Nonaktifkan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { pendingCoreAction = null }
                    ) {
                        Text("Batal", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    }
                }
            )
        }

        // ── Dialog Peringatan Core 0 (Master Core Protection) ──
        if (showMasterCoreNotice) {
            AlertDialog(
                onDismissRequest = { showMasterCoreNotice = false },
                containerColor = Color(0xFF16181D),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFCCCCCC),
                shape = RoundedCornerShape(16.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Core 0 Dilindungi (Master Core)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Text(
                        "CPU Core 0 adalah boot processor utama kernel Linux yang menangani interrupt sistem, root scheduler, dan zygote init. Core ini diproteksi agar tidak dapat dinonaktifkan demi mencegah kernel panic atau freeze perangkat.",
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        color = Color(0xFFAAAAAA)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showMasterCoreNotice = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Mengerti", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            )
        }

        // ── Cluster Frequency & Governor Tuning Card ────────────
        CpuClusterTunerCard(
            clusters = uiState.clusters,
            governorTunables = uiState.governorTunables,
            onFreqChange = { policyId, min, max -> viewModel.setClusterFrequency(policyId, min, max) },
            onGovChange = { policyId, gov -> viewModel.setClusterGovernor(policyId, gov) },
            onLoadTunables = { policyId, gov -> viewModel.loadGovernorTunables(policyId, gov) },
            onTunableChange = { policyId, gov, key, value -> viewModel.setGovernorTunable(policyId, gov, key, value) },
            activeGovernorPreset = uiState.activeGovernorPreset,
            onApplyGovernorPreset = { preset -> viewModel.applyGovernorPreset(preset) },
            onLockToggle = { policyId, isLock, min, max -> viewModel.setClusterLock(policyId, isLock, min, max) }
        )

        // ── Penjadwal Kernel & Arsitektur Multicore (CFS / EAS / HMP / BORE) ──────
        val schedInfo = uiState.schedulerInfo
        var schedUpRate by remember(schedInfo.upRateLimitUs) { mutableFloatStateOf(schedInfo.upRateLimitUs.toFloat()) }
        var schedDownRate by remember(schedInfo.downRateLimitUs) { mutableFloatStateOf(schedInfo.downRateLimitUs.toFloat()) }
        var schedLatency by remember(schedInfo.schedLatencyNs) { mutableFloatStateOf((schedInfo.schedLatencyNs / 1000000f)) }
        var schedMinGran by remember(schedInfo.schedMinGranularityNs) { mutableFloatStateOf((schedInfo.schedMinGranularityNs / 1000000f)) }
        var schedWakeGran by remember(schedInfo.schedWakeupGranularityNs) { mutableFloatStateOf((schedInfo.schedWakeupGranularityNs / 1000000f)) }
        var schedMigCost by remember(schedInfo.schedMigrationCostNs) { mutableFloatStateOf((schedInfo.schedMigrationCostNs / 1000f)) }

        // EAS states
        var uclampMinVal by remember(schedInfo.uclampMin) { mutableFloatStateOf(schedInfo.uclampMin.toFloat()) }
        var uclampMaxVal by remember(schedInfo.uclampMax) { mutableFloatStateOf(schedInfo.uclampMax.toFloat()) }

        // HMP states with live hysteresis protection
        var upmigrateVal by remember(schedInfo.schedUpmigrate) { mutableFloatStateOf(schedInfo.schedUpmigrate.toFloat()) }
        var downmigrateVal by remember(schedInfo.schedDownmigrate) { mutableFloatStateOf(schedInfo.schedDownmigrate.toFloat()) }
        var initTaskLoadVal by remember(schedInfo.schedInitTaskLoad) { mutableFloatStateOf(schedInfo.schedInitTaskLoad.toFloat()) }
        var spillNrRunVal by remember(schedInfo.schedSpillNrRun) { mutableFloatStateOf(schedInfo.schedSpillNrRun.toFloat()) }
        var spillLoadVal by remember(schedInfo.schedSpillLoad) { mutableFloatStateOf(schedInfo.schedSpillLoad.toFloat()) }
        var showAdvancedSched by remember { mutableStateOf(false) }
        var activeTweakConfig by remember { mutableStateOf<TweakConfig?>(null) }
        var activeDualTweakConfig by remember { mutableStateOf<DualTweakConfig?>(null) }

        LynxCard(
            title = "Penjadwal Kernel & Arsitektur Multicore",
            icon = Icons.Default.Speed,
            accentColor = AccentCyan
        ) {
            // Header info & Architecture badges
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = schedInfo.schedulerName,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "EAS Energy Model & CFS Granularity",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AccentCyan.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = schedInfo.schedulerType,
                            color = AccentCyan,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (schedInfo.isBoreSupported) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentGreen.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "BORE",
                                color = AccentGreen,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // ── Mode Arsitektur Penjadwal (Architecture Mode Selector: EAS vs HMP vs Hybrid) ──
            if (schedInfo.isModeSwitchSupported) {
                val modes = mutableListOf(
                    Triple("eas", "⚡ EAS", AccentCyan),
                    Triple("hmp", "🏛️ HMP", AccentOrange)
                )
                if (schedInfo.isHybridSupported) {
                    modes.add(Triple("hybrid", "🔀 Hybrid", AccentPurple))
                }

                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modes.forEach { (modeKey, modeLabel, modeColor) ->
                        val isModeSel = schedInfo.activeArchitectureMode.equals(modeKey, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isModeSel) modeColor.copy(alpha = 0.22f) else BgElevated,
                            border = BorderStroke(1.dp, if (isModeSel) modeColor else BorderGlass),
                            modifier = Modifier.weight(1f).clickable {
                                viewModel.setSchedulerArchitectureMode(modeKey, context)
                            }
                        ) {
                            Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = modeLabel,
                                    color = if (isModeSel) modeColor else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isModeSel) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // ── 3 Quick Presets Bar ──
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Triple("gaming", "⚡ Gaming", AccentOrange),
                    Triple("balanced", "⚖️ Balanced", AccentBlue),
                    Triple("battery", "🔋 Battery", AccentGreen)
                ).forEach { (preset, label, color) ->
                    val isSel = schedInfo.activePreset == preset
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSel) color.copy(alpha = 0.22f) else BgElevated,
                        border = BorderStroke(1.dp, if (isSel) color else BorderGlass),
                        modifier = Modifier.weight(1f).clickable {
                            viewModel.applySchedulerPreset(preset, context)
                        }
                    ) {
                        Box(Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                color = if (isSel) color else TextSecondary,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ── Terapkan saat Boot Switch (Clean Flat Row) ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Terapkan saat Boot", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
                    Text("Pulihkan setelan penjadwal otomatis saat boot", color = TextSecondary, fontSize = 9.sp)
                }
                Switch(
                    checked = schedInfo.applyOnBoot,
                    onCheckedChange = { viewModel.setSchedulerApplyOnBoot(it, context) },
                    colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = AccentCyan),
                    modifier = Modifier.scale(0.8f)
                )
            }

            // ── EAS & ENERGY MODEL ──────────────────────────────
            val showEas = if (schedInfo.isModeSwitchSupported) {
                schedInfo.activeArchitectureMode.equals("eas", ignoreCase = true) || schedInfo.activeArchitectureMode.equals("hybrid", ignoreCase = true)
            } else {
                schedInfo.isEasSupported
            }
            if (showEas) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
                ) {
                    Text("EAS ENERGY MODEL", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Spacer(Modifier.width(8.dp))
                    HorizontalDivider(color = AccentCyan.copy(alpha = 0.25f), modifier = Modifier.weight(1f))
                }

                // sched_boost if supported
                if (schedInfo.isSchedBoostSupported) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Boost:", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        listOf(
                            0 to "0: Off",
                            1 to "1: Minor",
                            2 to "2: Game",
                            3 to "3: Max"
                        ).forEach { (lvl, title) ->
                            val isSel = schedInfo.schedBoost == lvl
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) AccentCyan.copy(alpha = 0.22f) else BgElevated,
                                border = BorderStroke(1.dp, if (isSel) AccentCyan else BorderGlass),
                                modifier = Modifier.weight(1f).clickable {
                                    viewModel.setSchedulerTunable("sched_boost", lvl.toLong(), context)
                                }
                            ) {
                                Box(Modifier.padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        title,
                                        color = if (isSel) AccentCyan else TextSecondary,
                                        fontSize = 9.5.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // uclamp if supported (Unified Dual Tile)
                if (schedInfo.isUclampSupported) {
                    LynxDualTweakTile(
                        title = "Rentang Utilisasi Uclamp",
                        subtitle = "Kapasitas minimum task aktif & batas atas background",
                        val1Display = if (uclampMinVal == 0f) "0" else "${uclampMinVal.toInt()}",
                        val2Display = "${uclampMaxVal.toInt()}",
                        onClick = {
                            activeDualTweakConfig = DualTweakConfig(
                                id = "uclamp_range",
                                title = "Rentang Utilisasi Uclamp (EAS)",
                                category = "EAS",
                                description = "Kontrol komprehensif alokasi kapasitas CPU thread aktif (Floor) untuk menghilangkan jeda/delay tiba-tiba, serta pembatasan daya task latar belakang (Ceiling).",
                                item1 = DualTweakItem(
                                    id = "uclamp_min",
                                    label = "Uclamp Floor (Anti-Delay)",
                                    guideNote = "• Gaming/Berat: 512 (Prioritas tinggi, instan responsif)\n• Seimbang: 128 (Dorongan halus, hemat daya)\n• Ringan/Hemat: 0 (Bawaan sistem kernel)",
                                    currentValue = uclampMinVal,
                                    defaultValue = 0f,
                                    valueRange = 0f..1024f,
                                    steps = 31,
                                    formatDisplay = { v -> if (v == 0f) "0 (Bawaan / Hemat)" else "${v.toInt()} (${(v / 1024f * 100).toInt()}%)" },
                                    onApply = { v ->
                                        uclampMinVal = v
                                        viewModel.setSchedulerTunable("uclamp_min", v.toLong(), context)
                                    }
                                ),
                                item2 = DualTweakItem(
                                    id = "uclamp_max",
                                    label = "Uclamp Ceiling (Batas Atas)",
                                    guideNote = "• Gaming/Berat: 1024 (Kapasitas penuh 100% tanpa limitasi)\n• Seimbang: 1024 (Standar Linux)\n• Ringan/Hemat: 640 (Batasi daya latar belakang)",
                                    currentValue = uclampMaxVal,
                                    defaultValue = 1024f,
                                    valueRange = 128f..1024f,
                                    steps = 27,
                                    formatDisplay = { v -> if (v >= 1024f) "1024 (100% Maksimal)" else "${v.toInt()} (${(v / 1024f * 100).toInt()}%)" },
                                    onApply = { v ->
                                        uclampMaxVal = v
                                        viewModel.setSchedulerTunable("uclamp_max", v.toLong(), context)
                                    }
                                ),
                                liveStatus = if (uclampMinVal >= 512f) "Status: ⚡ Prioritas Tinggi (Anti-Delay Aktif)" else if (uclampMinVal > 0f) "Status: ⚖️ Dorongan Halus (Seimbang)" else "Status: 🛡️ Bawaan Kernel (EAS Standar)",
                                liveStatusSafe = true,
                                onResetAll = {
                                    uclampMinVal = 0f
                                    uclampMaxVal = 1024f
                                    viewModel.setSchedulerTunable("uclamp_min", 0L, context)
                                    viewModel.setSchedulerTunable("uclamp_max", 1024L, context)
                                }
                            )
                        }
                    )
                }
            }

            // ── HMP TASK MIGRATION ──────────────────────────────
            val showHmp = if (schedInfo.isModeSwitchSupported) {
                schedInfo.activeArchitectureMode.equals("hmp", ignoreCase = true) || schedInfo.activeArchitectureMode.equals("hybrid", ignoreCase = true)
            } else {
                schedInfo.isHmpSupported
            }
            if (showHmp) {
                val hystBuffer = (upmigrateVal - downmigrateVal).toInt()
                val isBufferSafe = hystBuffer >= 5
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
                ) {
                    Text("HMP TASK MIGRATION", color = AccentOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Spacer(Modifier.width(8.dp))
                    HorizontalDivider(color = AccentOrange.copy(alpha = 0.25f), modifier = Modifier.weight(1f))
                    Text(
                        text = "Buffer: +$hystBuffer%",
                        color = if (isBufferSafe) AccentGreen else AccentOrange,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                // Sched Migration Hysteresis Pair (Unified Dual Tile)
                LynxDualTweakTile(
                    title = "Ambang Migrasi HMP",
                    subtitle = "Ambang batas promosi Little → Big & penurunan Big → Little",
                    val1Display = "${upmigrateVal.toInt()}%",
                    val2Display = "${downmigrateVal.toInt()}%",
                    accentColor = AccentOrange,
                    onClick = {
                        activeDualTweakConfig = DualTweakConfig(
                            id = "hmp_migration",
                            title = "Ambang Migrasi HMP (Hysteresis)",
                            category = "HMP",
                            description = "Mengatur sensitivitas perpindahan task antara Little Core dan Big Core. Buffer histeresis otomatis dikunci (Up > Down + 5%) untuk mencegah task bolak-balik (ping-pong stutter).",
                            item1 = DualTweakItem(
                                id = "sched_upmigrate",
                                label = "Sched Upmigrate (Little → Big)",
                                guideNote = "• Gaming/Berat: 60% - 65% (Cepat lompat ke Big Core)\n• Seimbang: 85% (Bawaan standar pabrikan)\n• Ringan/Hemat: 95% (Tahan beban di Little Core)",
                                currentValue = upmigrateVal,
                                defaultValue = 85f,
                                valueRange = 40f..100f,
                                steps = 11,
                                formatDisplay = { v -> "${v.toInt()}%" },
                                onApply = { v ->
                                    upmigrateVal = v
                                    if (downmigrateVal > v - 5f) {
                                        downmigrateVal = (v - 5f).coerceAtLeast(20f)
                                    }
                                    viewModel.setSchedulerHysteresis(upmigrateVal.toInt(), downmigrateVal.toInt(), context)
                                }
                            ),
                            item2 = DualTweakItem(
                                id = "sched_downmigrate",
                                label = "Sched Downmigrate (Big → Little)",
                                guideNote = "• Gaming/Berat: 45% - 50% (Tahan task di Big Core lebih lama)\n• Seimbang: 65% (Bawaan standar)\n• Ringan/Hemat: 80% (Cepat turun ke Little Core)",
                                currentValue = downmigrateVal,
                                defaultValue = 65f,
                                valueRange = 20f..95f,
                                steps = 14,
                                formatDisplay = { v -> "${v.toInt()}%" },
                                onApply = { v ->
                                    val maxAllowed = (upmigrateVal - 5f).coerceAtLeast(20f)
                                    downmigrateVal = v.coerceAtMost(maxAllowed)
                                    viewModel.setSchedulerHysteresis(upmigrateVal.toInt(), downmigrateVal.toInt(), context)
                                }
                            ),
                            liveStatus = "Buffer Histeresis: +$hystBuffer% (${if (isBufferSafe) "Aman / Bebas Stutter" else "Terlalu Sempit"}) • ${if (upmigrateVal <= 70f) "⚡ Agresif" else if (upmigrateVal >= 90f) "🔋 Hemat" else "⚖️ Seimbang"}",
                            liveStatusSafe = isBufferSafe,
                            onResetAll = {
                                upmigrateVal = 85f
                                downmigrateVal = 65f
                                viewModel.setSchedulerHysteresis(85, 65, context)
                            }
                        )
                    }
                )
            }

            // ── SCHEDUTIL & FREQUENCY ───────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
            ) {
                Text("SCHEDUTIL & FREQUENCY", color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Spacer(Modifier.width(8.dp))
                HorizontalDivider(color = AccentBlue.copy(alpha = 0.25f), modifier = Modifier.weight(1f))
            }

            // Schedutil Rate Limit Pair (Unified Dual Tile)
            LynxDualTweakTile(
                title = "Rate Limit Respons Clock",
                subtitle = "Kecepatan lompatan clock naik & durasi penahanan clock turun",
                val1Display = if (schedUpRate == 0f) "0 µs" else "${schedUpRate.toInt()} µs",
                val2Display = "${schedDownRate.toInt() / 1000} ms",
                accentColor = AccentBlue,
                onClick = {
                    activeDualTweakConfig = DualTweakConfig(
                        id = "schedutil_rate_limits",
                        title = "Rate Limit Respons Clock (Schedutil)",
                        category = "Schedutil",
                        description = "Mengatur dinamika transisi frekuensi CPU. Ramp-up menentukan jeda sebelum clock dinaikkan, sementara Ramp-down mengontrol berapa lama frekuensi tinggi ditahan sebelum turun.",
                        item1 = DualTweakItem(
                            id = "up_rate_limit_us",
                            label = "Ramp-Up Rate Limit (Naik Clock)",
                            guideNote = "• Gaming/Berat: 0 µs (Lompatan instan ke frekuensi puncak)\n• Seimbang: 500 µs (Transisi halus & stabil)\n• Ringan/Hemat: 2000 µs (Cegah lonjakan clock singkat)",
                            currentValue = schedUpRate,
                            defaultValue = 500f,
                            valueRange = 0f..10000f,
                            steps = 19,
                            formatDisplay = { v -> if (v == 0f) "0 µs (Instan / Tanpa Jeda)" else "${v.toInt()} µs" },
                            onApply = { v ->
                                schedUpRate = v
                                viewModel.setSchedulerTunable("up_rate_limit_us", v.toLong(), context)
                            }
                        ),
                        item2 = DualTweakItem(
                            id = "down_rate_limit_us",
                            label = "Ramp-Down Rate Limit (Tahan Clock)",
                            guideNote = "• Gaming/Berat: 30 ms - 40 ms (Tahan clock tinggi cegah micro-stutter)\n• Seimbang: 10 ms - 20 ms (Standar responsif)\n• Ringan/Hemat: 1 ms - 2 ms (Segera turunkan clock demi hemat baterai)",
                            currentValue = schedDownRate,
                            defaultValue = 20000f,
                            valueRange = 1000f..40000f,
                            steps = 38,
                            formatDisplay = { v -> "${v.toInt() / 1000} ms (${v.toInt()} µs)" },
                            onApply = { v ->
                                schedDownRate = v
                                viewModel.setSchedulerTunable("down_rate_limit_us", v.toLong(), context)
                            }
                        ),
                        liveStatus = if (schedUpRate == 0f) "Status: 🚀 Mode Instan (Performa Maksimum)" else if (schedUpRate >= 1500f) "Status: 🔋 Mode Efisiensi (Hemat Daya)" else "Status: ⚖️ Mode Seimbang (Rekomendasi)",
                        liveStatusSafe = true,
                        onResetAll = {
                            schedUpRate = 500f
                            schedDownRate = 20000f
                            viewModel.setSchedulerTunable("up_rate_limit_us", 500L, context)
                            viewModel.setSchedulerTunable("down_rate_limit_us", 20000L, context)
                        }
                    )
                }
            )

            // ── EXPANDABLE ACCORDION FOR ADVANCED / MICRO TUNABLES ──
            Spacer(Modifier.height(4.dp))
            val advancedCount = 5 + (if (schedInfo.isSpillSupported) 2 else 0)

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgElevated,
                border = BorderStroke(1.dp, if (showAdvancedSched) AccentCyan.copy(alpha = 0.5f) else BorderGlass),
                modifier = Modifier.fillMaxWidth().clickable { showAdvancedSched = !showAdvancedSched }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = if (showAdvancedSched) AccentCyan else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Parameter Granularitas Lanjutan ($advancedCount)",
                            color = if (showAdvancedSched) TextPrimary else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        imageVector = if (showAdvancedSched) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = if (showAdvancedSched) AccentCyan else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (showAdvancedSched) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Sched Init Task Load Tile
                    LynxTweakTile(
                        title = "Init Task Load (Fork Initial)",
                        subtitle = "Estimasi beban awal proses baru saat fork",
                        displayValue = "${initTaskLoadVal.toInt()}%",
                        onClick = {
                            activeTweakConfig = TweakConfig(
                                id = "sched_init_task_load",
                                title = "Sched Init Task Load (Fork Initial Load)",
                                category = "HMP",
                                description = "Estimasi beban awal proses baru saat pertama kali dibuat (fork). Nilai tinggi langsung mengeksekusi proses baru di Big Core demi kecepatan startup aplikasi/game.",
                                currentValue = initTaskLoadVal,
                                defaultValue = 35f,
                                valueRange = 5f..100f,
                                steps = 18,
                                formatDisplay = { v -> "${v.toInt()}%" },
                                guideNote = "• Gaming/Berat: 60% (Start langsung di Big Core)\n• Seimbang: 35% (Standar Android)\n• Ringan/Hemat: 15% (Start di Little Core)",
                                statusInfo = if (initTaskLoadVal >= 50f) "🚀 Start Big Core" else "⚖️ Standar Little Core",
                                onApply = { v ->
                                    initTaskLoadVal = v
                                    viewModel.setSchedulerTunable("sched_init_task_load", v.toLong(), context)
                                }
                            )
                        }
                    )

                    // Spilling controls if supported
                    if (schedInfo.isSpillSupported) {
                        LynxTweakTile(
                            title = "Sched Spill Nr Run",
                            subtitle = "Batas antrean task sebelum dialihkan ke core lain",
                            displayValue = "${spillNrRunVal.toInt()} Task",
                            onClick = {
                                activeTweakConfig = TweakConfig(
                                    id = "sched_spill_nr_run",
                                    title = "Sched Spill Nr Run (Queue Spill Threshold)",
                                    category = "HMP",
                                    description = "Maksimum jumlah antrean task pada satu CPU core sebelum dialihkan (spillover) ke core lain yang lebih senggang.",
                                    currentValue = spillNrRunVal,
                                    defaultValue = 3f,
                                    valueRange = 1f..10f,
                                    steps = 8,
                                    formatDisplay = { v -> "${v.toInt()} Task" },
                                    guideNote = "• Gaming/Berat: 2 Task (Spillover cepat ke core lain)\n• Seimbang: 3 Task (Standar distribusi)\n• Ringan/Hemat: 5 Task (Minim migrasi antar core)",
                                    onApply = { v ->
                                        spillNrRunVal = v
                                        viewModel.setSchedulerTunable("sched_spill_nr_run", v.toLong(), context)
                                    }
                                )
                            }
                        )

                        LynxTweakTile(
                            title = "Sched Spill Load Threshold",
                            subtitle = "Ambang batas beban core untuk spillover",
                            displayValue = "${spillLoadVal.toInt()}%",
                            onClick = {
                                activeTweakConfig = TweakConfig(
                                    id = "sched_spill_load",
                                    title = "Sched Spill Load Threshold",
                                    category = "HMP",
                                    description = "Ambang batas beban CPU sebelum mengizinkan spillover ke core lain.",
                                    currentValue = spillLoadVal,
                                    defaultValue = 90f,
                                    valueRange = 50f..100f,
                                    steps = 10,
                                    formatDisplay = { v -> "${v.toInt()}%" },
                                    guideNote = "• Gaming/Berat: 75% (Distribusi beban cepat)\n• Seimbang: 90% (Standar)\n• Ringan/Hemat: 98% (Tunggu core penuh)",
                                    onApply = { v ->
                                        spillLoadVal = v
                                        viewModel.setSchedulerTunable("sched_spill_load", v.toLong(), context)
                                    }
                                )
                            }
                        )
                    }

                    // CFS Latency Tile
                    LynxTweakTile(
                        title = "CFS Target Scheduling Latency",
                        subtitle = "Target periode siklus eksekusi seluruh task",
                        displayValue = "${schedLatency.toInt()} ms",
                        onClick = {
                            activeTweakConfig = TweakConfig(
                                id = "sched_latency_ns",
                                title = "CFS Target Scheduling Latency",
                                category = "CFS",
                                description = "Periode target di mana seluruh task yang siap dieksekusi dijamin mendapat giliran CPU. Latensi lebih kecil meningkatkan kehalusan animasi UI dan konsistensi frame game.",
                                currentValue = schedLatency,
                                defaultValue = 10f,
                                valueRange = 2f..24f,
                                steps = 21,
                                formatDisplay = { v -> "${v.toInt()} ms" },
                                guideNote = "• Gaming/Berat: 4 ms (Eksekusi cepat & responsif)\n• Seimbang: 10 ms (Standar Linux CFS)\n• Ringan/Hemat: 18 ms (Minim context switch)",
                                statusInfo = if (schedLatency <= 6f) "⚡ Responsif" else "⚖️ Standar CFS",
                                onApply = { v ->
                                    schedLatency = v
                                    viewModel.setSchedulerTunable("sched_latency_ns", (v * 1000000).toLong(), context)
                                }
                            )
                        }
                    )

                    // CFS Min Granularity Tile
                    LynxTweakTile(
                        title = "CFS Min Preemption Granularity",
                        subtitle = "Jatah waktu minimum yang dijamin untuk setiap task",
                        displayValue = "${String.format("%.1f", schedMinGran)} ms",
                        onClick = {
                            activeTweakConfig = TweakConfig(
                                id = "sched_min_granularity_ns",
                                title = "CFS Min Preemption Granularity",
                                category = "CFS",
                                description = "Jatah waktu minimum yang dijamin untuk setiap task sebelum kernel mengizinkan preemption (pemotongan giliran) oleh task lain.",
                                currentValue = schedMinGran,
                                defaultValue = 3f,
                                valueRange = 0.5f..8f,
                                steps = 14,
                                formatDisplay = { v -> "${String.format("%.1f", v)} ms" },
                                guideNote = "• Gaming/Berat: 1.0 ms (Preemption cepat)\n• Seimbang: 3.0 ms (Standar CFS)\n• Ringan/Hemat: 5.0 ms (Throughput maksimal)",
                                onApply = { v ->
                                    schedMinGran = v
                                    viewModel.setSchedulerTunable("sched_min_granularity_ns", (v * 1000000).toLong(), context)
                                }
                            )
                        }
                    )

                    // CFS Wakeup Granularity Tile
                    LynxTweakTile(
                        title = "CFS Wakeup Granularity",
                        subtitle = "Keuntungan latensi task yang baru bangun",
                        displayValue = "${String.format("%.1f", schedWakeGran)} ms",
                        onClick = {
                            activeTweakConfig = TweakConfig(
                                id = "sched_wakeup_granularity_ns",
                                title = "CFS Wakeup Granularity",
                                category = "CFS",
                                description = "Keuntungan latensi yang dibutuhkan task yang baru bangun untuk menggeser task yang sedang berjalan di CPU.",
                                currentValue = schedWakeGran,
                                defaultValue = 2f,
                                valueRange = 0.5f..8f,
                                steps = 14,
                                formatDisplay = { v -> "${String.format("%.1f", v)} ms" },
                                guideNote = "• Gaming/Berat: 1.0 ms (Task bangun instan jalan)\n• Seimbang: 2.0 ms (Standar CFS)\n• Ringan/Hemat: 4.0 ms (Minim interupsi task berjalan)",
                                onApply = { v ->
                                    schedWakeGran = v
                                    viewModel.setSchedulerTunable("sched_wakeup_granularity_ns", (v * 1000000).toLong(), context)
                                }
                            )
                        }
                    )

                    // Task Migration Cost Tile
                    LynxTweakTile(
                        title = "Task Migration Cost (Cache-Hot)",
                        subtitle = "Proteksi cache L1/L2 sebelum diizinkan migrasi",
                        displayValue = "${schedMigCost.toInt()} µs",
                        onClick = {
                            activeTweakConfig = TweakConfig(
                                id = "sched_migration_cost_ns",
                                title = "Task Migration Cost (Cache-Hot)",
                                category = "CFS",
                                description = "Waktu task dianggap masih berada dalam cache L1/L2 sebelum diizinkan migrasi ke inti CPU lain.",
                                currentValue = schedMigCost,
                                defaultValue = 200f,
                                valueRange = 100f..3000f,
                                steps = 28,
                                formatDisplay = { v -> "${v.toInt()} µs" },
                                guideNote = "• Gaming/Berat: 100 µs (Migrasi lincah)\n• Seimbang: 200 µs (Standar CFS)\n• Ringan/Hemat: 600 µs (Proteksi cache L1/L2)",
                                onApply = { v ->
                                    schedMigCost = v
                                    viewModel.setSchedulerTunable("sched_migration_cost_ns", (v * 1000).toLong(), context)
                                }
                            )
                        }
                    )

                    // Child Process Runs First
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Child Process Runs First", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
                            Text("Prioritaskan eksekusi child process saat fork", color = TextSecondary, fontSize = 9.sp)
                        }
                        Switch(
                            checked = schedInfo.schedChildRunsFirst,
                            onCheckedChange = { viewModel.setSchedulerTunable("sched_child_runs_first", if (it) 1L else 0L, context) },
                            colors = SwitchDefaults.colors(checkedThumbColor = BgDeepOled, checkedTrackColor = AccentCyan),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            }
        }

        // ── Bypass Throttling & Kontrol Termal Card ─────────────────
        LynxCard(
            title = "Bypass Throttling & Kontrol Termal",
            icon = Icons.Default.LocalFireDepartment,
            accentColor = AccentRed
        ) {
            LynxSwitch(
                label = "CPU Anti-Throttling (Max Clock Lock)",
                subLabel = "Kunci frekuensi maksimum CPU ke batas pabrik tertinggi tanpa pemotongan thermal",
                checked = state.overclock.enabled,
                onCheckedChange = { viewModel.setOverclockEnabled(it) },
            )

            var floorValue by remember(state.overclock.cpuFloorRatio) {
                mutableFloatStateOf(state.overclock.cpuFloorRatio.toFloat())
            }
            LynxTweakTile(
                title = "Ambang Bawah Frekuensi CPU (Clock Floor)",
                subtitle = "Menahan frekuensi CPU agar tidak drop saat gameplay",
                displayValue = "${floorValue.toInt()}%",
                onClick = {
                    activeTweakConfig = TweakConfig(
                        id = "cpu_floor",
                        title = "Ambang Bawah Frekuensi CPU (Clock Floor)",
                        category = "Termal & Clock",
                        description = "Menahan frekuensi CPU agar tidak turun di bawah persentase ini saat aplikasi atau game sedang berjalan di foreground.",
                        currentValue = floorValue,
                        defaultValue = 60f,
                        valueRange = 60f..100f,
                        steps = 7,
                        formatDisplay = { v -> "${v.toInt()}%" },
                        guideNote = "• Gaming/Berat: 85% (Tahan clock tinggi saat gameplay)\n• Seimbang: 70% (Standar harian)\n• Ringan/Hemat: 60% (Batas aman efisiensi daya)",
                        statusInfo = if (floorValue >= 80f) "⚡ Agresif" else "⚖️ Standar",
                        onApply = { v ->
                            floorValue = v
                            viewModel.setCpuFloorRatio(v.toInt())
                        }
                    )
                }
            )

            LynxSwitch(
                label = "Full Thermal Bypass (Unrestricted)",
                subLabel = "Nonaktifkan pembatasan termal OEM (Phone cooler diwajibkan)",
                checked = state.thermal.fullBypass,
                onCheckedChange = { viewModel.setThermalBypass(it) },
            )

            var tempLimitValue by remember(state.thermal.customTempLimitC) {
                mutableFloatStateOf(state.thermal.customTempLimitC.toFloat())
            }
            LynxTweakTile(
                title = "Batas Suhu Thermal Custom",
                subtitle = "Ambang batas trip point termal SoC sebelum throttling",
                displayValue = "${tempLimitValue.toInt()}°C",
                onClick = {
                    activeTweakConfig = TweakConfig(
                        id = "temp_limit",
                        title = "Batas Suhu Thermal Custom",
                        category = "Termal",
                        description = "Ambang batas temperatur trip point termal SoC sebelum thermal daemon mengambil tindakan perlindungan atau pemotongan performa.",
                        currentValue = tempLimitValue,
                        defaultValue = 50f,
                        valueRange = 45f..60f,
                        steps = 14,
                        formatDisplay = { v -> "${v.toInt()}°C" },
                        guideNote = "• Gaming/Berat: 58°C (Toleransi tinggi, disarankan pakai cooler)\n• Seimbang: 50°C (Standar harian aman)\n• Ringan/Hemat: 45°C (Perangkat dingin, baterai awet)",
                        statusInfo = if (tempLimitValue >= 55f) "🔥 Suhu Tinggi (Cooler Disarankan)" else "🛡️ Suhu Aman",
                        onApply = { v ->
                            tempLimitValue = v
                            viewModel.setCustomTempLimit(v.toInt())
                        }
                    )
                }
            )
        }

        // ── Voltage Control (Undervolting) Card ─────────────────
        VoltageControlCard(
            voltageInfo = uiState.voltageInfo,
            onApplyOffset = { viewModel.applyVoltageOffset(it) }
        )

        // ── Universal Tweak Bottom Sheet Drawer (Single & Dual) ──
        if (activeTweakConfig != null) {
            LynxTweakSheet(
                config = activeTweakConfig!!,
                onDismiss = { activeTweakConfig = null }
            )
        }
        if (activeDualTweakConfig != null) {
            LynxDualTweakSheet(
                config = activeDualTweakConfig!!,
                onDismiss = { activeDualTweakConfig = null }
            )
        }
    }
}

// ============================================================
//  CATEGORY 2: GPU & DISPLAY SUBSCREEN
// ============================================================

@Composable
fun TuningGpuCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state
    val gpu = uiState.gpuInfo
    val isMali = gpu.platform.contains("mali", ignoreCase = true)
    val isAdreno = gpu.platform.contains("adreno", ignoreCase = true)
    val platLabel = when {
        isMali -> "MediaTek Mali GED"
        isAdreno -> "Qualcomm Adreno QTI"
        else -> "Universal GPU"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── GPU Advanced Control & Live Telemetry Card ──────────
        LynxCard(
            title = "Kontrol GPU & Live Telemetri",
            icon = Icons.Default.SportsEsports,
            accentColor = AccentOrange
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (gpu.curFreqMhz > 0) "${gpu.curFreqMhz} MHz" else "GPU Siap Tuning",
                        color = AccentOrange, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Arsitektur: $platLabel",
                        color = TextSecondary, fontSize = 11.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderGlass)
                ) {
                    Text(
                        if (gpu.maxFreqMhz > 0) "Maks: ${gpu.maxFreqMhz} MHz" else "Dynamic Clock",
                        color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            // GPU Load bar
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Beban Komputasi GPU", color = TextSecondary, fontSize = 11.sp)
                    Text("${gpu.gpuLoadPercent}%", color = if (gpu.gpuLoadPercent > 70) AccentRed else AccentOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { (gpu.gpuLoadPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (gpu.gpuLoadPercent > 70) AccentRed else AccentOrange,
                    trackColor = BgElevated,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                if (isMali) "Tingkat MTK GED / GPU Boost" else "Tingkat Adreno Boost",
                color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            val activeBoost = if (isMali) gpu.gedBoostLevel else gpu.adrenoBoostLevel
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(0, "Mati (0)", "Hemat Daya"),
                    Triple(1, "Level 1", "Normal Boost"),
                    Triple(2, "Level 2", "Hardcore Boost")
                ).forEach { (lvl, title, sub) ->
                    val isSelected = activeBoost == lvl
                    Surface(
                        onClick = { viewModel.setGpuBoostLevel(lvl) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentOrange.copy(alpha = 0.2f) else BgElevated,
                        border = BorderStroke(1.dp, if (isSelected) AccentOrange else BorderGlass)
                    ) {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(title, color = if (isSelected) AccentOrange else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(sub, color = TextSecondary, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        // ── GPU Advanced Hardware Control Card ───────────────────
        LynxCard(
            title = "GPU Advanced Control",
            icon = Icons.Default.Devices,
            accentColor = AccentBlue
        ) {
            // SoC Architecture Selector (Manual Override)
            Text("Hardware SoC Architecture (Engine Profile)", color = TextPrimary, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
            val socOptions = listOf(
                Pair("auto", "🤖 Auto AI"),
                Pair("qcom", "🐉 Snapdragon"),
                Pair("mtk", "⚡ MediaTek"),
                Pair("generic", "🐧 Generic GKI")
            )
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                socOptions.forEach { (socKey, socLabel) ->
                    val isSelected = (uiState.socOverride.ifEmpty { "auto" }) == socKey
                    Surface(
                        onClick = { viewModel.setSocOverride(socKey) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentBlue.copy(alpha = 0.22f) else BgElevated,
                        border = BorderStroke(1.2.dp, if (isSelected) AccentBlue else BorderGlass)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                        ) {
                            Text(
                                socLabel,
                                color = if (isSelected) AccentBlue else TextSecondary,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(bottom = 10.dp))

            // Platform badge
            val platformLabel = when (gpu.platform) {
                "adreno"   -> "Adreno (Qualcomm)"
                "mali_ged" -> "Mali GED (MediaTek)"
                else       -> "Generic GPU"
            }
            val maxBoost = if (gpu.platform == "mali_ged") 2 else 3
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Platform", color = TextSecondary, fontSize = 12.sp)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentBlue.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.4f))
                ) {
                    Text(platformLabel, color = AccentBlue, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }

            if (gpu.platform != "generic") {
                // GPU Boost Level selector
                val boostLevel = if (gpu.platform == "adreno") gpu.adrenoBoostLevel else gpu.gedBoostLevel
                val boostLabels = if (maxBoost == 3)
                    listOf("Off", "Low", "Medium", "High")
                else
                    listOf("Off", "Medium", "High")

                Text("GPU Boost Level", color = TextPrimary, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    boostLabels.forEachIndexed { idx, label ->
                        val isActive = boostLevel == idx
                        Surface(
                            onClick = { viewModel.setGpuBoostLevel(idx) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isActive) AccentBlue.copy(alpha = 0.22f) else BgElevated,
                            border = BorderStroke(
                                1.5.dp,
                                if (isActive) AccentBlue else BorderGlass
                            )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Text(label,
                                    color = if (isActive) AccentBlue else TextSecondary,
                                    fontSize = 11.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                                Text("$idx", color = if (isActive) AccentBlue else TextSecondary, fontSize = 10.sp)
                            }
                        }
                    }
                }

                // GPU Freq control & status
                if (gpu.availFreqsMhz.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("GPU Frequency Range", color = TextPrimary, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Min: ${gpu.minFreqMhz} MHz", color = AccentCyan, fontSize = 12.sp)
                        Text("Max: ${gpu.maxFreqMhz} MHz", color = AccentOrange, fontSize = 12.sp)
                        Text("Now: ${gpu.curFreqMhz} MHz", color = TextSecondary, fontSize = 12.sp)
                    }
                    val minRange = gpu.availFreqsMhz.minOrNull()?.toFloat() ?: 0f
                    val maxRange = gpu.availFreqsMhz.maxOrNull()?.toFloat() ?: 1000f
                    var gpuMax by remember(gpu.maxFreqMhz) { mutableFloatStateOf(gpu.maxFreqMhz.toFloat()) }
                    LynxSlider(
                        label = "Batas Maksimum GPU",
                        value = gpuMax,
                        onValueChange = { gpuMax = it },
                        onValueChangeFinished = { viewModel.setGpuFreq(null, gpuMax.toInt()) },
                        valueRange = minRange..maxRange,
                        steps = (gpu.availFreqsMhz.size - 2).coerceAtLeast(0),
                        displayValue = "${gpuMax.toInt()} MHz",
                        accentColor = AccentBlue
                    )
                }
            } else {
                Text(
                    "GPU node tidak terdeteksi di kernel ini. Kontrol GPU tidak tersedia.",
                    color = TextSecondary, fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        // ── Display Refresh Rate & Touch Card ───────────────────
        LaunchedEffect(Unit) {
            viewModel.refreshDisplayRefreshRate()
        }
        LynxCard(
            title = "Display Refresh Rate & Touch",
            icon = Icons.Default.Smartphone,
            accentColor = AccentCyan
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Kecepatan Refresh Layar (Display FPS)", color = TextPrimary,
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Kunci refresh rate panel untuk pengalaman visual super mulus",
                        color = TextSecondary, fontSize = 11.sp)
                }
                if (uiState.displayRefreshRate > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentCyan.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f))
                    ) {
                        Text(
                            "${uiState.displayRefreshRate} Hz",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Dynamic refresh rate selector chips
            val rates = uiState.supportedRefreshRates.ifEmpty { listOf(60, 90, 120) }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rates.forEach { hz ->
                    val isSelected = uiState.displayRefreshRate == hz
                    Surface(
                        onClick = { viewModel.setDisplayRefreshRate(hz) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentCyan.copy(alpha = 0.22f) else BgElevated,
                        border = BorderStroke(1.5.dp, if (isSelected) AccentCyan else BorderGlass)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text(
                                "${hz}Hz",
                                color = if (isSelected) AccentCyan else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                            Text(
                                when (hz) {
                                    60 -> "Hemat"
                                    90 -> "Halus"
                                    120 -> "Gaming"
                                    144, 165 -> "Ultra"
                                    else -> "Tersedia"
                                },
                                color = if (isSelected) AccentCyan.copy(alpha = 0.8f) else TextSecondary,
                                fontSize = 9.5.sp
                            )
                        }
                    }
                }
            }

            LynxSwitch(
                label = "TouchBoost & Responsivitas Sentuh",
                subLabel = "Prioritaskan sampling rate sentuhan 240Hz+ pada driver input",
                checked = state.displayTouch.touchboost,
                onCheckedChange = { viewModel.setTouchboost(it) }
            )
        }

        // ── Display Calibration (KCAL & HBM) Card ───────────────
        DisplayCalibrationCard(
            displayCalibration = uiState.displayCalibration,
            onSetKcal = { en, r, g, b, sat, v, c, h ->
                viewModel.setKcalParams(en, r, g, b, sat, v, c, h)
            },
            onSetHbm = { viewModel.setHbmEnabled(it) }
        )
    }
}

// ============================================================
//  CATEGORY 3: RAM & STORAGE I/O SUBSCREEN
// ============================================================

@Composable
fun TuningMemoryCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Memory & Swappiness Cache Card ──────────────────────
        LynxCard(
            title = "Memory & Swappiness Cache",
            icon = Icons.Default.Storage,
            accentColor = AccentBlue
        ) {
            // Dynamic ZRAM Compression Algorithm Selection
            Text(
                "Algoritma Kompresi ZRAM (Kernel Swap)",
                color = TextPrimary, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp)
            )
            val algos = uiState.availZramCompAlgorithms.ifEmpty { listOf("lz4", "lzo", "zstd") }
            val activeAlgo = uiState.zramCompAlgorithm.ifBlank { "lz4" }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                algos.forEach { algo ->
                    val isSelected = algo == activeAlgo
                    Surface(
                        onClick = { viewModel.setZramCompAlgorithm(algo) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentBlue.copy(alpha = 0.22f) else BgElevated,
                        border = BorderStroke(1.2.dp, if (isSelected) AccentBlue else BorderGlass)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                algo.uppercase(),
                                color = if (isSelected) AccentBlue else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                when (algo) {
                                    "zstd" -> "Rasio Max"
                                    "lz4" -> "Cepat"
                                    "lz4hc" -> "Optimal"
                                    "lzo-rle" -> "Hemat"
                                    else -> "Kompresi"
                                },
                                color = if (isSelected) AccentBlue.copy(alpha = 0.8f) else TextSecondary,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            val ramTotal = (uiState.telemetry?.ramTotalMb ?: 4096).toFloat()
            val minZram = 512f
            val maxZram = maxOf(4096f, ramTotal)
            val zramSteps = (((maxZram - minZram) / 512f).toInt() - 1).coerceAtLeast(0)

            var zramValue by remember(state.memory.zramSizeMb) {
                mutableFloatStateOf(state.memory.zramSizeMb.toFloat().coerceIn(minZram, maxZram))
            }
            LynxSlider(
                label = "Ukuran Alokasi ZRAM (Skala RAM Fisik: ${ramTotal.toInt()} MB)",
                value = zramValue,
                onValueChange = { zramValue = it },
                onValueChangeFinished = { viewModel.setZramSizeMb(zramValue.toInt()) },
                valueRange = minZram..maxZram,
                steps = zramSteps,
                displayValue = "${zramValue.toInt()} MB",
                accentColor = AccentBlue
            )

            // ── VM Tuning Presets ─────────────────────────────
            Text(
                "Preset Rekomendasi Virtual Memory",
                color = TextPrimary, fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
            )
            val vmPresets = listOf(
                Triple("gaming", "⚡ Gaming", "Zero-stutter I/O"),
                Triple("balanced", "⚖️ Balanced", "OEM Default"),
                Triple("battery", "🔋 Battery", "Low Writeback")
            )
            val activeVmPreset = uiState.vmAdvanced.activePreset
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                vmPresets.forEach { (presetKey, title, subtitle) ->
                    val isSelected = activeVmPreset == presetKey
                    Surface(
                        onClick = { viewModel.applyVmPreset(presetKey) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentBlue.copy(alpha = 0.22f) else BgElevated,
                        border = BorderStroke(1.2.dp, if (isSelected) AccentBlue else BorderGlass)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                title,
                                color = if (isSelected) AccentBlue else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                subtitle,
                                color = if (isSelected) AccentBlue.copy(alpha = 0.8f) else TextSecondary,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            var swapValue by remember(uiState.vmAdvanced.swappiness) {
                mutableFloatStateOf(uiState.vmAdvanced.swappiness.toFloat())
            }
            LynxSlider(
                label = "Virtual Memory Swappiness",
                value = swapValue,
                onValueChange = { swapValue = it },
                onValueChangeFinished = { viewModel.setSwappiness(swapValue.toInt()) },
                valueRange = 0f..200f,
                steps = 19,
                displayValue = "${swapValue.toInt()}",
                accentColor = AccentBlue
            )

            var dirtyValue by remember(uiState.vmAdvanced.dirtyRatio) {
                mutableFloatStateOf(uiState.vmAdvanced.dirtyRatio.toFloat())
            }
            LynxSlider(
                label = "VM Dirty Ratio (Batas Writeback)",
                value = dirtyValue,
                onValueChange = { dirtyValue = it },
                onValueChangeFinished = { viewModel.setDirtyRatio(dirtyValue.toInt()) },
                valueRange = 5f..60f,
                steps = 10,
                displayValue = "${dirtyValue.toInt()}%",
                accentColor = AccentBlue
            )

            var dirtyBgValue by remember(uiState.vmAdvanced.dirtyBackgroundRatio) {
                mutableFloatStateOf(uiState.vmAdvanced.dirtyBackgroundRatio.toFloat())
            }
            LynxSlider(
                label = "VM Dirty Background Ratio",
                value = dirtyBgValue,
                onValueChange = { dirtyBgValue = it },
                onValueChangeFinished = { viewModel.setDirtyBackgroundRatio(dirtyBgValue.toInt()) },
                valueRange = 1f..30f,
                steps = 28,
                displayValue = "${dirtyBgValue.toInt()}%",
                accentColor = AccentBlue
            )

            var vfsValue by remember(uiState.vmAdvanced.vfsCachePressure) {
                mutableFloatStateOf(uiState.vmAdvanced.vfsCachePressure.toFloat())
            }
            LynxSlider(
                label = "VFS Cache Pressure (Reclaim Rate)",
                value = vfsValue,
                onValueChange = { vfsValue = it },
                onValueChangeFinished = { viewModel.setVfsCachePressure(vfsValue.toInt()) },
                valueRange = 50f..200f,
                steps = 14,
                displayValue = "${vfsValue.toInt()}",
                accentColor = AccentBlue
            )

            var expireValue by remember(uiState.vmAdvanced.dirtyExpireCentisecs) {
                mutableFloatStateOf((uiState.vmAdvanced.dirtyExpireCentisecs / 100).toFloat())
            }
            LynxSlider(
                label = "VM Dirty Expire Time",
                value = expireValue,
                onValueChange = { expireValue = it },
                onValueChangeFinished = { viewModel.setDirtyExpireCentisecs((expireValue * 100).toInt()) },
                valueRange = 10f..60f,
                steps = 9,
                displayValue = "${expireValue.toInt()}s",
                accentColor = AccentBlue
            )

            var writebackValue by remember(uiState.vmAdvanced.dirtyWritebackCentisecs) {
                mutableFloatStateOf((uiState.vmAdvanced.dirtyWritebackCentisecs / 100).toFloat())
            }
            LynxSlider(
                label = "VM Dirty Writeback Interval",
                value = writebackValue,
                onValueChange = { writebackValue = it },
                onValueChangeFinished = { viewModel.setDirtyWritebackCentisecs((writebackValue * 100).toInt()) },
                valueRange = 1f..15f,
                steps = 13,
                displayValue = "${writebackValue.toInt()}s",
                accentColor = AccentBlue
            )

            var statValue by remember(uiState.vmAdvanced.statInterval) {
                mutableFloatStateOf(uiState.vmAdvanced.statInterval.toFloat())
            }
            LynxSlider(
                label = "VM Stat Interval (Pembaruan Statistik)",
                value = statValue,
                onValueChange = { statValue = it },
                onValueChangeFinished = { viewModel.setVmStatInterval(statValue.toInt()) },
                valueRange = 1f..10f,
                steps = 8,
                displayValue = "${statValue.toInt()}s",
                accentColor = AccentBlue
            )

            Spacer(Modifier.height(8.dp))
            LynxActionButton(
                text = "Bebaskan Cache RAM (Drop Caches)",
                icon = Icons.Default.CleaningServices,
                onClick = { viewModel.dropCaches() },
                accentColor = AccentCyan,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // ── KSM (Kernel Same-page Merging) Card ──────────────────
        LaunchedEffect(Unit) { viewModel.refreshKsmStats() }
        val ksm = uiState.ksmStats
        LynxCard(
            title = "KSM — Kernel Memory Merging",
            icon = Icons.Default.Memory,
            accentColor = AccentBlue
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Kernel Same-page Merging", color = TextPrimary,
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Gabung halaman memori identik, hemat ${String.format("%.1f", ksm.savedMb)} MB RAM",
                        color = TextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = ksm.enabled,
                    onCheckedChange = { viewModel.setKsmEnabled(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = AccentBlue,
                        checkedTrackColor = AccentBlue.copy(alpha = 0.4f))
                )
            }
            if (ksm.enabled) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${ksm.pagesSharing}", color = AccentBlue,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Halaman Sharing", color = TextSecondary, fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${ksm.pagesShared}", color = AccentCyan,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Halaman Shared", color = TextSecondary, fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format("%.1f MB", ksm.savedMb), color = AccentOrange,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("RAM Hemat", color = TextSecondary, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                var scanValue by remember(ksm.pagesToScan) { mutableFloatStateOf(ksm.pagesToScan.toFloat()) }
                LynxSlider(
                    label = "Pages to Scan per Cycle",
                    value = scanValue,
                    onValueChange = { scanValue = it },
                    onValueChangeFinished = { viewModel.setKsmTunables(scanValue.toInt(), ksm.sleepMs) },
                    valueRange = 50f..500f,
                    steps = 8,
                    displayValue = "${scanValue.toInt()} pages",
                    accentColor = AccentBlue
                )
            }
        }

        // ── LMK Minfree Preset Card ──────────────────────────────
        LynxCard(
            title = "LMK — Low Memory Killer",
            icon = Icons.Default.DeleteSweep,
            accentColor = AccentOrange
        ) {
            Text(
                "Atur seberapa agresif sistem Android mengakhiri aplikasi latar belakang untuk membebaskan RAM.",
                color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            val lmkPresets = listOf(
                Triple("conservative", "🛡️ Conservative", "Simpan lebih banyak app di RAM"),
                Triple("balanced",     "⚖️ Balanced",     "Standar harian — default Android"),
                Triple("gaming",       "🎮 Gaming",       "Prioritaskan game aktif"),
                Triple("aggressive",   "⚡ Aggressive",   "Kill agresif, maksimalkan RAM bebas"),
            )
            lmkPresets.forEach { (id, label, desc) ->
                Surface(
                    onClick = { viewModel.applyLmkPreset(id) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderGlass)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(desc, color = TextSecondary, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(
                "⚠️ LMK Kernel hanya efektif pada Android 10 ke bawah. Android 11+ menggunakan LMKD userspace.",
                color = TextSecondary.copy(alpha = 0.7f), fontSize = 10.sp, lineHeight = 14.sp
            )
        }

        // ── Memory LMK & Entropy Tuner Card ──────────────────────
        MemoryEntropyCard(
            memoryEntropy = uiState.memoryEntropy,
            onSetEntropy = { r, w ->
                viewModel.setEntropyThresholds(r, w)
            }
        )

        // ── I/O Scheduler Card ───────────────────────────────────
        if (uiState.ioDevices.isNotEmpty()) {
            LynxCard(
                title = "I/O Scheduler Manager",
                icon = Icons.Default.Storage,
                accentColor = AccentPurple
            ) {
                uiState.ioDevices.forEach { dev ->
                    Text(
                        "/dev/${dev.device}",
                        color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                    )
                    Text("Scheduler", color = TextSecondary, fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(dev.availableSchedulers.size) { i ->
                            val sched = dev.availableSchedulers[i]
                            val isActive = sched == dev.currentScheduler
                            Surface(
                                onClick = { viewModel.setIoScheduler(dev.device, sched) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isActive) AccentPurple.copy(alpha = 0.2f) else BgElevated,
                                border = BorderStroke(
                                    1.dp, if (isActive) AccentPurple else BorderGlass
                                )
                            ) {
                                Text(sched,
                                    color = if (isActive) AccentPurple else TextSecondary,
                                    fontSize = 11.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    var raValue by remember(dev.readAheadKb) { mutableFloatStateOf(dev.readAheadKb.toFloat()) }
                    LynxSlider(
                        label = "Read-Ahead Buffer",
                        value = raValue,
                        onValueChange = { raValue = it },
                        onValueChangeFinished = { viewModel.setReadAheadKb(dev.device, raValue.toInt()) },
                        valueRange = 128f..4096f,
                        steps = 7,
                        displayValue = "${raValue.toInt()} KB",
                        accentColor = AccentPurple
                    )
                    HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

// ============================================================
//  CATEGORY 4: BATTERY & CHARGING SUBSCREEN
// ============================================================

@Composable
fun TuningChargingCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state
    val battDetails = uiState.batteryDetails

    LaunchedEffect(Unit) {
        viewModel.refreshBatteryDetails()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ============================================================
        //  BENTO CARD 1: CHARGING TELEMETRY & HARDWARE INSPECTOR
        // ============================================================
        LynxCard(
            title = "Hardware Inspector & Telemetri",
            icon = Icons.Default.BatteryChargingFull,
            accentColor = AccentCyan
        ) {
            // Live Charging Status Badge
            val isOvernightLatched = battDetails?.isOvernightBypassLatched == true || (battDetails?.level ?: 0) >= 100
            val isSmartTapering = battDetails?.isSmartTaperingActive == true && state.charging.smartTaperingEnabled

            val currentModeLabel = when {
                isOvernightLatched -> "🔒 100% Full: Hardware Bypass Aktif (Net 0mA - Aman Tidur)"
                state.charging.bypassEnabled -> "⚡ Bypass Charging Aktif (Baterai Latch)"
                isSmartTapering -> "🍃 Smart Tapering Aktif (Mendinginkan Baterai 90%+)"
                state.charging.extremeChargingEnabled -> "🔥 Extreme Fast Charge (Lockout Bypass)"
                state.charging.limitCurrentMa >= 3000 -> "🚀 High-Current Fast Charge (${state.charging.limitCurrentMa} mA)"
                else -> "⚖️ Pengisian Dibatasi (${state.charging.limitCurrentMa} mA)"
            }
            val currentModeColor = when {
                isOvernightLatched -> AccentCyan
                state.charging.bypassEnabled -> AccentCyan
                isSmartTapering -> AccentGreen
                state.charging.extremeChargingEnabled -> AccentRed
                state.charging.limitCurrentMa >= 3000 -> AccentCyan
                else -> AccentOrange
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = currentModeColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, currentModeColor.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isOvernightLatched || state.charging.bypassEnabled) Icons.Default.BatteryChargingFull else if (isSmartTapering) Icons.Default.Shield else Icons.Default.Bolt,
                        contentDescription = null,
                        tint = currentModeColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currentModeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = currentModeColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Overnight 100% Bypass Latch In-App Banner
            if (isOvernightLatched) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(1.2.dp, AccentCyan.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp).padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Overnight Guard Aktif (100% Full Latch)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Baterai telah terisi penuh 100%. Sistem otomatis mengunci sirkuit ke True Hardware Bypass (Net 0mA). Daya operasional HP disuplai langsung oleh adaptor charger via Vsys sehingga baterai tidak akan drop dan sangat aman ditinggal tidur semalaman di kasur.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = TextPrimary.copy(alpha = 0.9f),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            } else if (isSmartTapering) {
                // Smart Tapering In-App Banner (90%+)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.2.dp, AccentGreen.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(20.dp).padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Smart Tapering Aktif (Pendinginan 90%+)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentGreen
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Baterai mencapai 90%+. Arus masuk diturunkan bertahap dan sensor termal dikembalikan normal untuk mendinginkan suhu baterai & bodi HP secara optimal sebelum mencapai 100%. Beban sistem tetap terjamin aman tanpa defisit daya.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = TextPrimary.copy(alpha = 0.9f),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Emergency Thermal Guard Alert Banner (if tripped)
            if (battDetails?.isEmergencyGuardActive == true) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentRed.copy(alpha = 0.15f),
                    border = BorderStroke(1.2.dp, AccentRed.copy(alpha = 0.8f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(20.dp).padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Emergency Thermal Guard Aktif!",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentRed
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Suhu sensor fisik nyata terdeteksi ≥ 46.0°C. Sistem otomatis mencabut temperature spoofing dan menahan arus di batas aman 2.0A demi melindungi kesehatan baterai dan komponen IC.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = TextPrimary.copy(alpha = 0.9f),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Hardware IC & Fast Charge Protocol Badges
            val icLabel = battDetails?.activeICName?.ifBlank { "Direct Pump 2:1 / PMIC" } ?: "Direct Pump 2:1 / PMIC"
            val protoLabel = battDetails?.fastChargeProtocol?.ifBlank { "Standard DCP / Battery" } ?: "Standard DCP / Battery"

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Active IC Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("Active IC", style = MaterialTheme.typography.labelSmall, fontSize = 9.5.sp, color = TextSecondary)
                            Text(
                                text = icLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentPurple,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Protocol Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("Protokol", style = MaterialTheme.typography.labelSmall, fontSize = 9.5.sp, color = TextSecondary)
                            Text(
                                text = protoLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentCyan,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Dual Wattmeter & Metrics Matrix
            if (battDetails != null) {
                val inputWatt = if (battDetails.adapterWatt > 0.05f) battDetails.adapterWatt else battDetails.chargerWatt
                val netBattWatt = (battDetails.voltageMv.toFloat() * battDetails.currentMa.coerceAtLeast(0).toFloat()) / 1_000_000f
                val netWattDisplay = if (netBattWatt > 0.05f) netBattWatt else battDetails.chargerWatt
                val efficiency = battDetails.chargingEfficiencyPercent.coerceIn(0, 100)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Row 1: Dual Wattmeter (Adapter Input vs Battery Net)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Column A: Adapter Input
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BgCard,
                                border = BorderStroke(0.8.dp, BorderGlass),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Daya Masuk (Adapter)", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (inputWatt > 0.05f) String.format(java.util.Locale.US, "%.1f W", inputWatt) else "--",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentPurple
                                    )
                                    val voltVal = if (battDetails.adapterVoltageMv > 1000) battDetails.adapterVoltageMv / 1000f else (if (battDetails.chargerVoltageMv > 1000) battDetails.chargerVoltageMv / 1000f else battDetails.voltageMv / 1000f)
                                    val curVal = if (battDetails.adapterCurrentMa > 0) battDetails.adapterCurrentMa else battDetails.currentMa.coerceAtLeast(0)
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.1f V • %d mA", voltVal, curVal),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            // Column B: Battery Net Power
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BgCard,
                                border = BorderStroke(0.8.dp, BorderGlass),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Net Baterai", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = TextSecondary)
                                    Spacer(Modifier.height(2.dp))
                                    val curColor = if (battDetails.currentMa > 100) AccentGreen else if (state.charging.bypassEnabled) AccentCyan else TextPrimary
                                    Text(
                                        text = if (netWattDisplay > 0.05f) String.format(java.util.Locale.US, "%.1f W", netWattDisplay) else "--",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = curColor
                                    )
                                    val bCurText = if (battDetails.currentMa > 0) "+${battDetails.currentMa} mA" else "${battDetails.currentMa} mA"
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.1f V • %s", battDetails.voltageMv / 1000f, bCurText),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        // Row 2: Conversion Efficiency Progress Bar
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Efisiensi Konversi IC Pump", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, color = TextSecondary)
                                Text(
                                    text = if (efficiency > 0) "$efficiency%" else if (state.charging.bypassEnabled) "100% (Bypass)" else "--",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { if (efficiency > 0) (efficiency / 100f).coerceIn(0f, 1f) else if (state.charging.bypassEnabled) 1f else 0.5f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = AccentGreen,
                                trackColor = BgElevated
                            )
                        }

                        // Row 3: Thermal Duality (Suhu Sensor Fisik Nyata vs Sensor Baterai/Spoof)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BgCard,
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("Thermal Duality Sensor", style = MaterialTheme.typography.labelSmall, fontSize = 10.5.sp, color = TextSecondary)
                                    val isSpoofed = state.charging.thermalLockoutBypassEnabled && (battDetails.tempC in 27.5f..28.5f)
                                    val spoofLabel = if (isSpoofed) "Spoofed 28°C (Bypass Aktif)" else "${String.format(java.util.Locale.US, "%.1f°C", battDetails.tempC)}"
                                    Text(
                                        text = "Sensor Baterai: $spoofLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = if (isSpoofed) AccentCyan else TextSecondary,
                                        maxLines = 1
                                    )
                                }
                                val physColor = if (battDetails.realPhysicalTempC >= 46f) AccentRed else if (battDetails.realPhysicalTempC >= 42f) AccentOrange else AccentGreen
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Fisik Nyata", style = MaterialTheme.typography.labelSmall, fontSize = 9.5.sp, color = TextSecondary)
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.1f°C", battDetails.realPhysicalTempC),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = physColor,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ============================================================
        //  BENTO CARD 2: HIGH-CURRENT & ACCELERATION CONTROLS
        // ============================================================
        LynxCard(
            title = "Kontrol Arus Tinggi & Akselerasi",
            icon = Icons.Default.Speed,
            accentColor = AccentCyan
        ) {
            LynxSwitch(
                label = "Extreme Fast Charging (High Current)",
                subLabel = "Membuka batas arus tertinggi (hingga 4500-6000mA), mengaktifkan protokol RFC/PD penuh, dan menghapus pembatasan bertahap bawaan kernel.",
                checked = state.charging.extremeChargingEnabled,
                onCheckedChange = { viewModel.setExtremeCharging(it) },
            )

            LynxSwitch(
                label = "Thermal Lockout Bypass (DV2_TBAT)",
                subLabel = "Mem-bypass limitasi thermal lockout (Battery_Temperature = 28°C spoofing & abcct disable) agar charger tidak drop ke 500mA saat baterai hangat ketika gaming.",
                checked = state.charging.thermalLockoutBypassEnabled,
                onCheckedChange = { viewModel.setThermalLockoutBypass(it) },
            )

            var highTargetValue by remember(state.charging.highCurrentTargetPercent) {
                mutableFloatStateOf(state.charging.highCurrentTargetPercent.toFloat())
            }
            LynxSlider(
                label = "Target Kapasitas Arus Tinggi (sc_tuisoc)",
                value = highTargetValue,
                onValueChange = { highTargetValue = it },
                onValueChangeFinished = { viewModel.setHighCurrentTarget(highTargetValue.toInt()) },
                valueRange = 80f..100f,
                steps = 19,
                displayValue = "${highTargetValue.toInt()}% Kapasitas",
                accentColor = AccentCyan
            )
            Text(
                text = "Menahan pengisian pompa arus tinggi (Direct Charge Pump 2:1) hingga baterai mencapai ${highTargetValue.toInt()}% sebelum beralih ke arus rendah trickle. Default pabrikan biasanya membatasi di 70-80%.",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Spacer(Modifier.height(6.dp))

            var currentLimitValue by remember(state.charging.limitCurrentMa) {
                mutableFloatStateOf(state.charging.limitCurrentMa.toFloat())
            }
            LynxSlider(
                label = "Batas Arus Pengisian Game",
                value = currentLimitValue,
                onValueChange = { currentLimitValue = it },
                onValueChangeFinished = { viewModel.setChargeCurrentLimit(currentLimitValue.toInt()) },
                valueRange = 1000f..6000f,
                steps = 9,
                displayValue = if (currentLimitValue >= 3500f) "${currentLimitValue.toInt()} mA (Fast Charge)" else "${currentLimitValue.toInt()} mA",
                accentColor = AccentPurple
            )
        }

        // ============================================================
        //  BENTO CARD 3: SAFETY & BATTERY LONGEVITY
        // ============================================================
        LynxCard(
            title = "Proteksi & Kesehatan Baterai",
            icon = Icons.Default.Security,
            accentColor = AccentGreen
        ) {
            LynxSwitch(
                label = "Smart Tapering & Overnight Guard",
                subLabel = "Menurunkan arus bertahap di atas 90% (90-95% 1500mA, 95-99% 750mA) demi mendinginkan baterai, serta mengunci True Hardware Bypass di 100% (Net 0mA) agar baterai tidak drop dan mustahil overcharge saat ditinggal tidur di kasur.",
                checked = state.charging.smartTaperingEnabled,
                onCheckedChange = { viewModel.setSmartTapering(it) },
            )

            LynxSwitch(
                label = "Emergency Thermal Guard (46.0°C)",
                subLabel = "Failsafe cerdas anti-overheat. Jika sensor termal fisik nyata (mtktsAP/thermal_zone) mencapai ≥ 46°C, temperatur spoofing dicabut otomatis dan arus diturunkan seketika ke level aman.",
                checked = state.charging.emergencyTempGuardEnabled,
                onCheckedChange = { viewModel.setEmergencyTempGuard(it) },
            )

            LynxSwitch(
                label = "Bypass Charging (Direct Motherboard)",
                subLabel = "Mengalirkan arus charger langsung ke motherboard (Vsys) tanpa mengisi ataupun menguras baterai. Persentase baterai tertahan stabil (latch) dan suhu baterai tetap dingin saat gaming.",
                checked = state.charging.bypassEnabled,
                onCheckedChange = { viewModel.setBypassCharging(it) },
            )

            var tempCutoffValue by remember(state.charging.tempCutoffC) {
                mutableFloatStateOf(state.charging.tempCutoffC.toFloat())
            }
            LynxSlider(
                label = "Batas Suhu Thermal AutoCut (Kasur / Pelindung Suhu)",
                value = tempCutoffValue,
                onValueChange = { tempCutoffValue = it },
                onValueChangeFinished = { viewModel.setTempCutoff(tempCutoffValue.toInt()) },
                valueRange = 40f..50f,
                steps = 9,
                displayValue = "${tempCutoffValue.toInt()}°C",
                accentColor = AccentOrange
            )
            Text(
                text = "Ambang batas suhu pemotong arus jika panas terperangkap (misal saat HP ditaruh di kasur/bantal). Arus otomatis dipangkas ke 1200mA saat menyentuh suhu ini hingga bodi kembali dingin.",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            var maxBatteryValue by remember(state.charging.maxBatteryPercent) {
                mutableFloatStateOf(state.charging.maxBatteryPercent.toFloat())
            }
            LynxSlider(
                label = "Batas Pengisian Baterai (Stop-At-%)",
                value = maxBatteryValue,
                onValueChange = { maxBatteryValue = it },
                onValueChangeFinished = { viewModel.setMaxBatteryPercent(maxBatteryValue.toInt()) },
                valueRange = 70f..100f,
                steps = 5,
                displayValue = if (maxBatteryValue >= 99.5f) "100% (Penuh & Auto-Bypass)" else "${maxBatteryValue.toInt()}%",
                accentColor = AccentGreen
            )
        }
    }
}

// ============================================================
//  CATEGORY 5: NETWORK & AUDIO SUBSCREEN
// ============================================================

@Composable
fun TuningNetworkCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── TCP Congestion Control Card ──────────────────────────
        LaunchedEffect(Unit) { viewModel.refreshTcpAlgorithms() }
        LynxCard(
            title = "TCP Congestion Control",
            icon = Icons.Default.NetworkCheck,
            accentColor = AccentCyan
        ) {
            val currentAlg = uiState.currentTcpCongestion.ifBlank { state.network.tcpCongestion }
            val algs = uiState.availableTcpAlgorithms

            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Algoritma kontrol kongesti TCP/IP aktif mempengaruhi latensi dan throughput koneksi game online.",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                if (currentAlg.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentCyan.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.35f))
                    ) {
                        Text(
                            "● ${currentAlg.uppercase()}",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (algs.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentOrange.copy(alpha = 0.2f),
                            modifier = Modifier.padding(end = 10.dp)
                        ) {
                            Text(
                                "UNSUPPORTED",
                                color = AccentOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            "Kernel perangkat ini tidak mengekspos pemilihan algoritma TCP dinamis via sysfs.",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
            } else {
                val algInfo = mapOf(
                    "bic"      to Pair("⚡ BIC", "High Speed & Low Latency (Kernel Active)"),
                    "cubic"    to Pair("📶 CUBIC", "Linux Default — Balanced"),
                    "reno"     to Pair("🔁 RENO", "Classic Standard — Stable"),
                    "bbr"      to Pair("🎮 BBR", "Google BBR — Low Latency Gaming"),
                    "westwood" to Pair("📡 Westwood", "WiFi & Wireless Loss Tolerant"),
                    "htcp"     to Pair("🚀 H-TCP", "High Bandwidth Delay Product"),
                    "vegas"    to Pair("⏱️ Vegas", "Delay-Based Congestion Avoidance"),
                    "hybla"    to Pair("🌐 Hybla", "Satellite & High Latency Links")
                )

                algs.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { alg ->
                            val isActive = alg.equals(currentAlg, ignoreCase = true)
                            val info = algInfo[alg.lowercase()]
                            Surface(
                                onClick = { viewModel.setTcpCongestion(alg) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isActive) AccentCyan.copy(alpha = 0.16f) else BgElevated,
                                border = BorderStroke(
                                    1.5.dp, if (isActive) AccentCyan else BorderGlass
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            info?.first ?: alg.uppercase(),
                                            color = if (isActive) AccentCyan else TextPrimary,
                                            fontWeight = FontWeight.Bold, fontSize = 12.sp
                                        )
                                        if (isActive) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentCyan.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    "AKTIF",
                                                    color = AccentCyan,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        info?.second ?: "Algoritma Kernel Linux",
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // ── Subsystem Audio & Wi-Fi Enhancer Card ────────────────
        LynxCard(
            title = "Subsystem Audio & Wi-Fi Enhancer",
            icon = Icons.Default.Wifi,
            accentColor = AccentBlue
        ) {
            LynxSwitch(
                label = "Wi-Fi Ping Stabilizer & FQ-CoDel",
                subLabel = "Mengurangi bufferbloat dan jitter koneksi game online",
                checked = state.network.wifiPingStabilizer,
                onCheckedChange = { viewModel.setWifiPingStabilizer(it) },
            )
            LynxSwitch(
                label = "Low-Latency Audio MMAP",
                subLabel = "Bypass mixer audio Android untuk latensi respon suara terendah",
                checked = state.audio.lowLatencyMmap,
                onCheckedChange = { viewModel.setAudioMmap(it) },
            )
        }

        // ── Sound Control (Gain Booster) Card ────────────────────
        SoundControlCard(
            soundControl = uiState.soundControl,
            onSetGain = { hpL, hpR, spk, mic, hpMode ->
                viewModel.setSoundGain(hpL, hpR, spk, mic, hpMode)
            }
        )
    }
}

// ============================================================
//  CATEGORY 6: SUBSYSTEM & DEEP TUNABLES SUBSCREEN
// ============================================================

@Composable
fun TuningSystemCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onAddAppClick: () -> Unit,
    onEditRuleClick: (AppProfileRule) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = uiState.state
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Otomasi Profil Per-Aplikasi Card ────────────────────
        LynxCard(
            title = "Otomasi Profil Per-Aplikasi",
            icon = Icons.Default.SportsEsports,
            accentColor = AccentGreen
        ) {
            // Master Service Toggle
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Layanan Otomasi Latar Belakang",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        "Pantau aplikasi aktif dan alihkan profil CPU/GPU seketika (0ms boost saat launch)",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                Switch(
                    checked = uiState.isAppAutomationActive,
                    onCheckedChange = { viewModel.toggleAppAutomation(context, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgDeepOled,
                        checkedTrackColor = AccentGreen,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = BgElevated,
                        uncheckedBorderColor = BorderGlass
                    )
                )
            }

            HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 10.dp))

            // Header: Rule Count & Add Button
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${uiState.appProfileRules.size} Aturan Profil Dikonfigurasi",
                        color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp
                    )
                    Text(
                        "Profil khusus diterapkan otomatis saat aplikasi berada di foreground",
                        color = TextSecondary, fontSize = 10.5.sp
                    )
                }
                Surface(
                    onClick = onAddAppClick,
                    shape = RoundedCornerShape(10.dp),
                    color = AccentGreen.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f))
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tambah", color = AccentGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // App Profile Rules List
            if (uiState.appProfileRules.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Tune, null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Belum ada aturan khusus",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Klik tombol 'Tambah' untuk mengunci profil tertentu (Performa, Extreme, Balance, Hemat) ke game atau app favorit.",
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                uiState.appProfileRules.forEach { rule ->
                    val profColor = when (rule.targetProfile.lowercase()) {
                        "extreme" -> AccentRed
                        "performance" -> AccentOrange
                        "powersave" -> AccentGreen
                        else -> AccentCyan
                    }
                    val profLabel = when (rule.targetProfile.lowercase()) {
                        "extreme" -> "🔥 Extreme"
                        "performance" -> "⚡ Performa"
                        "powersave" -> "🍃 Hemat"
                        else -> "⚖️ Balance"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, if (rule.enabled) profColor.copy(alpha = 0.35f) else BorderGlass),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            onEditRuleClick(rule)
                        }
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                AppIconImage(
                                    packageName = rule.packageName,
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            rule.appName,
                                            color = if (rule.enabled) TextPrimary else TextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                        if (rule.isGame) {
                                            Spacer(Modifier.width(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentOrange.copy(alpha = 0.18f)
                                            ) {
                                                Text(
                                                    "GAME",
                                                    color = AccentOrange,
                                                    fontSize = 7.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = profColor.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, profColor.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                profLabel,
                                                color = profColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                        if (rule.targetRefreshRate != null) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentPurple.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "${rule.targetRefreshRate} Hz",
                                                    color = AccentPurple,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (rule.autoFloatingHud) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentCyan.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "OSD",
                                                    color = AccentCyan,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = { enabled ->
                                        val updated = rule.copy(enabled = enabled)
                                        viewModel.addOrUpdateAppProfileRule(context, updated)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = BgDeepOled,
                                        checkedTrackColor = profColor,
                                        uncheckedThumbColor = TextSecondary,
                                        uncheckedTrackColor = BgElevated
                                    ),
                                    modifier = Modifier.scale(0.75f)
                                )
                                IconButton(
                                    onClick = { onEditRuleClick(rule) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── OEM & Framework Neutralizer Card ────────────────────
        LynxCard(
            title = "OEM & Framework Neutralizer",
            icon = Icons.Default.Shield,
            accentColor = AccentPurple
        ) {
            LynxSwitch(
                label = "Netralkan Joyose / GOS Throttling",
                subLabel = "Hentikan pembatasan performa buatan dari OEM (Xiaomi Joyose, Samsung GOS, dsb)",
                checked = state.oemNeutralizer.joyoseNeutralize,
                onCheckedChange = { viewModel.setJoyoseNeutralize(it) },
            )
        }

        // ── Deep Kernel & System Tunables Card ──────────────────
        var manualNodePath by remember { mutableStateOf("") }
        var maxVisibleItems by remember { mutableStateOf(20) }
        var deepSearchQuery by remember { mutableStateOf("") }

        LynxCard(
            title = "Deep Kernel & System Tunables",
            icon = Icons.Default.Search,
            accentColor = AccentCyan
        ) {
            Text(
                "Mesin pemindai adaptif universal. Membaca seluruh file konfigurasi kernel, OEM, CPU, GPU, & ROM. Petunjuk internal (#) dikonversi otomatis menjadi widget interaktif (toggle, slider, dropdown pilihan).",
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Action Bar: Scan button + Auto-Discovery status + Count info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.runDeepScan() },
                    enabled = !uiState.isDeepScanning,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan.copy(alpha = 0.16f),
                        contentColor = AccentCyan
                    ),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    if (uiState.isDeepScanning) {
                        CircularProgressIndicator(
                            color = AccentCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Memindai...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (uiState.deepTunables.isEmpty()) "Pindai Kernel" else "Pindai Ulang", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.deepTunables.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AccentCyan.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f))
                        ) {
                            Text(
                                "${uiState.deepTunables.size} Node",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass)
                    ) {
                        Text(
                            if (uiState.isDeepScanning) "Memindai Sysfs..." else "Zero Hardcoding",
                            color = if (uiState.isDeepScanning) AccentCyan else TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Manual Node Direct Tester
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = manualNodePath,
                    onValueChange = { manualNodePath = it },
                    placeholder = { Text("/proc/sys/... atau /sys/...", fontSize = 11.sp, color = TextTertiary) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedContainerColor = BgElevated,
                        unfocusedContainerColor = BgElevated
                    )
                )
                Button(
                    onClick = {
                        if (manualNodePath.isNotBlank()) {
                            viewModel.inspectManualNode(manualNodePath.trim())
                            manualNodePath = ""
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan.copy(alpha = 0.18f),
                        contentColor = AccentCyan
                    ),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Uji Node", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Search Bar & Category Filter Chips
            if (uiState.deepTunables.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = deepSearchQuery,
                    onValueChange = {
                        deepSearchQuery = it
                        maxVisibleItems = 20
                    },
                    placeholder = { Text("Cari parameter kernel, deskripsi, atau path...", fontSize = 11.sp, color = TextTertiary) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = {
                        if (deepSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { deepSearchQuery = "" }) {
                                Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedContainerColor = BgElevated,
                        unfocusedContainerColor = BgElevated
                    )
                )

                // Quick Category Filters
                val detectedCategories = remember(uiState.deepTunables) {
                    listOf("Semua") + uiState.deepTunables.map { it.category }.distinct().sorted()
                }
                var activeCategoryFilter by remember { mutableStateOf("Semua") }

                Spacer(Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(detectedCategories) { catName ->
                        val isCatSel = activeCategoryFilter == catName
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCatSel) AccentCyan.copy(alpha = 0.22f) else BgElevated,
                            border = BorderStroke(1.dp, if (isCatSel) AccentCyan else BorderGlass),
                            modifier = Modifier.clickable {
                                activeCategoryFilter = catName
                                maxVisibleItems = 20
                            }
                        ) {
                            Text(
                                catName,
                                color = if (isCatSel) AccentCyan else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isCatSel) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                val displayedTunables = remember(uiState.deepTunables, deepSearchQuery, activeCategoryFilter) {
                    uiState.deepTunables.filter { tunable ->
                        val matchesCat = activeCategoryFilter == "Semua" || tunable.category.equals(activeCategoryFilter, ignoreCase = true)
                        val matchesSearch = deepSearchQuery.isBlank() ||
                                tunable.name.contains(deepSearchQuery, ignoreCase = true) ||
                                tunable.rawName.contains(deepSearchQuery, ignoreCase = true) ||
                                tunable.desc.contains(deepSearchQuery, ignoreCase = true) ||
                                tunable.path.contains(deepSearchQuery, ignoreCase = true) ||
                                tunable.help.contains(deepSearchQuery, ignoreCase = true)
                        matchesCat && matchesSearch
                    }
                }

                if (displayedTunables.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.SearchOff, null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Tidak ada parameter yang cocok dengan '$deepSearchQuery'",
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val itemsToShow = displayedTunables.take(maxVisibleItems)
                    itemsToShow.forEach { tunable ->
                        DeepTunableItemCard(
                            tunable = tunable,
                            onApply = { path, value -> viewModel.applyDeepTunable(path, value) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (displayedTunables.size > maxVisibleItems) {
                        Button(
                            onClick = { maxVisibleItems += 30 },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BgElevated,
                                contentColor = AccentCyan
                            ),
                            border = BorderStroke(1.dp, BorderGlass)
                        ) {
                            Text("Muat Lebih Banyak (${displayedTunables.size - maxVisibleItems} tersisa)", fontSize = 11.5.sp)
                        }
                    }
                }
            } else if (!uiState.isDeepScanning) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Explore, null, tint = AccentCyan, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Belum ada hasil pemindaian sysfs",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tekan tombol 'Pindai Kernel' di atas untuk memulai penemuan parameter secara dinamis pada perangkat Anda.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
//  DeepTunableItemCard — Adaptive Sysfs Node Control with # Docs
// ============================================================

@Composable
fun DeepTunableItemCard(
    tunable: DeepTunable,
    onApply: (String, String) -> Unit
) {
    var editValue by remember(tunable.value) { mutableStateOf(tunable.value) }
    var sliderValue by remember(tunable.value, tunable.min, tunable.max) {
        val fVal = tunable.value.toFloatOrNull() ?: tunable.min
        mutableStateOf(fVal.coerceIn(tunable.min, tunable.max))
    }
    var showPathDetails by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = BgElevated,
        border = BorderStroke(1.dp, BorderGlass)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 1. Header: Name + Category + RW status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tunable.name,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (tunable.rawName.isNotBlank() && tunable.rawName != tunable.name) {
                        Text(
                            tunable.rawName,
                            color = TextTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when {
                            tunable.category.contains("CPU", true) -> AccentCyan.copy(alpha = 0.15f)
                            tunable.category.contains("GPU", true) -> AccentPurple.copy(alpha = 0.15f)
                            tunable.category.contains("Memory", true) || tunable.category.contains("VM", true) -> AccentBlue.copy(alpha = 0.15f)
                            tunable.category.contains("Storage", true) || tunable.category.contains("I/O", true) -> AccentOrange.copy(alpha = 0.15f)
                            tunable.category.contains("Power", true) || tunable.category.contains("Thermal", true) -> AccentGreen.copy(alpha = 0.15f)
                            else -> AccentCyan.copy(alpha = 0.15f)
                        },
                        border = BorderStroke(1.dp, BorderGlass)
                    ) {
                        Text(
                            tunable.category,
                            color = when {
                                tunable.category.contains("CPU", true) -> AccentCyan
                                tunable.category.contains("GPU", true) -> AccentPurple
                                tunable.category.contains("Memory", true) || tunable.category.contains("VM", true) -> AccentBlue
                                tunable.category.contains("Storage", true) || tunable.category.contains("I/O", true) -> AccentOrange
                                tunable.category.contains("Power", true) || tunable.category.contains("Thermal", true) -> AccentGreen
                                else -> AccentCyan
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (tunable.writable) AccentGreen.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (tunable.writable) AccentGreen.copy(alpha = 0.4f) else Color.Red.copy(alpha = 0.3f))
                    ) {
                        Text(
                            if (tunable.writable) "R/W" else "READ-ONLY",
                            color = if (tunable.writable) AccentGreen else Color(0xFFFF5252),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 2. Friendly Description
            if (tunable.desc.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    tunable.desc,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }

            // 3. Recommendation Tip
            if (tunable.recommendation.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentGreen.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Tips: ${tunable.recommendation}",
                            color = AccentGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 4. Collapsible Monospace Path
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPathDetails = !showPathDetails },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (showPathDetails) tunable.path else tunable.path.take(45) + (if (tunable.path.length > 45) "..." else ""),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TextTertiary,
                    maxLines = if (showPathDetails) 3 else 1
                )
                Text(
                    if (showPathDetails) "Tutup" else "Path",
                    fontSize = 8.5.sp,
                    color = AccentCyan.copy(alpha = 0.8f)
                )
            }

            // 5. Inline '#' Kernel Comment Banner (if available)
            if (tunable.help.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x1AFFB300),
                    border = BorderStroke(1.dp, Color(0x4DFFB300)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(14.dp).padding(top = 1.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                "PETUNJUK KERNEL (#):",
                                color = Color(0xFFFFB300),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                tunable.help,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TextPrimary,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 6. Interactive Tunable Controls
            if (!tunable.writable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Nilai Terbaca:", color = TextSecondary, fontSize = 11.5.sp)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0x1AFFFFFF),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Text(
                            tunable.value.ifBlank { "(kosong)" },
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            } else {
                when (tunable.type) {
                    TunableType.BOOL -> {
                        val isChecked = tunable.value == "1" ||
                                tunable.value.equals("enable", ignoreCase = true) ||
                                tunable.value.equals("enabled", ignoreCase = true) ||
                                tunable.value.equals("on", ignoreCase = true) ||
                                tunable.value.equals("y", ignoreCase = true)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    if (isChecked) "Status: Aktif (1)" else "Status: Nonaktif (0)",
                                    color = if (isChecked) AccentCyan else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (isChecked) "Sentuh sakelar untuk mematikan" else "Sentuh sakelar untuk mengaktifkan",
                                    color = TextTertiary,
                                    fontSize = 9.5.sp
                                )
                            }
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val newVal = if (checked) "1" else "0"
                                    onApply(tunable.path, newVal)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = BgDeepOled,
                                    checkedTrackColor = AccentCyan,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = Color(0xFF161A24)
                                )
                            )
                        }
                    }

                    TunableType.CHOICE -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Pilihan Tersedia:", color = TextSecondary, fontSize = 10.5.sp)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AccentCyan.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        "Aktif: ${tunable.value}",
                                        color = AccentCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(tunable.options.size) { i ->
                                    val opt = tunable.options[i]
                                    val isSelected = opt.value == tunable.value ||
                                            (tunable.value.isBlank() && i == 0)
                                    Surface(
                                        onClick = { onApply(tunable.path, opt.value) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) AccentCyan.copy(alpha = 0.25f) else Color(0x1AFFFFFF),
                                        border = BorderStroke(1.dp, if (isSelected) AccentCyan else BorderGlass)
                                    ) {
                                        Text(
                                            opt.label.ifBlank { opt.value },
                                            color = if (isSelected) AccentCyan else TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TunableType.SLIDER -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Nilai Parameter:", color = TextSecondary, fontSize = 11.sp)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AccentCyan.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f))
                                ) {
                                    val displayVal = if (tunable.unit.isNotEmpty()) {
                                        "${sliderValue.toInt()} ${tunable.unit}"
                                    } else {
                                        "${sliderValue.toInt()}"
                                    }
                                    Text(
                                        displayVal,
                                        color = AccentCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Slider(
                                value = sliderValue,
                                onValueChange = { sliderValue = it },
                                valueRange = tunable.min..tunable.max,
                                steps = if (tunable.step > 0 && (tunable.max - tunable.min) / tunable.step > 1) {
                                    ((tunable.max - tunable.min) / tunable.step).toInt() - 1
                                } else 0,
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentCyan,
                                    activeTrackColor = AccentCyan,
                                    inactiveTrackColor = Color(0xFF161A24)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Min: ${tunable.min.toInt()}${tunable.unit}",
                                    color = TextTertiary,
                                    fontSize = 9.5.sp
                                )

                                Button(
                                    onClick = { onApply(tunable.path, sliderValue.toInt().toString()) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentCyan.copy(alpha = 0.2f),
                                        contentColor = AccentCyan
                                    ),
                                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Terapkan (${sliderValue.toInt()})", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }

                                Text(
                                    "Max: ${tunable.max.toInt()}${tunable.unit}",
                                    color = TextTertiary,
                                    fontSize = 9.5.sp
                                )
                            }
                        }
                    }

                    TunableType.STEPPER -> {
                        val curInt = tunable.value.toIntOrNull() ?: tunable.min.toInt()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Tingkat Level:", color = TextSecondary, fontSize = 11.sp)
                                Text(
                                    "Rentang: ${tunable.min.toInt()} - ${tunable.max.toInt()} ${tunable.unit}",
                                    color = TextTertiary,
                                    fontSize = 9.5.sp
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val nextVal = (curInt - tunable.step.toInt()).coerceAtLeast(tunable.min.toInt())
                                        onApply(tunable.path, nextVal.toString())
                                    },
                                    enabled = curInt > tunable.min.toInt(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("-", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AccentCyan.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        "$curInt ${tunable.unit}".trim(),
                                        color = AccentCyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val nextVal = (curInt + tunable.step.toInt()).coerceAtMost(tunable.max.toInt())
                                        onApply(tunable.path, nextVal.toString())
                                    },
                                    enabled = curInt < tunable.max.toInt(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    TunableType.INT, TunableType.TEXT -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = editValue,
                                onValueChange = { editValue = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TextPrimary
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentCyan,
                                    unfocusedBorderColor = BorderGlass,
                                    focusedContainerColor = Color(0xFF0A0C12),
                                    unfocusedContainerColor = Color(0xFF0A0C12)
                                )
                            )

                            Button(
                                onClick = { onApply(tunable.path, editValue.trim()) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentCyan.copy(alpha = 0.2f),
                                    contentColor = AccentCyan
                                ),
                                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Terapkan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
