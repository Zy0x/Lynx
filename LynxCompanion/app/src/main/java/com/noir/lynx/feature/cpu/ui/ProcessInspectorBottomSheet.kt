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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessInspectorBottomSheet(
    process: CpuProcessInfo,
    displayName: String,
    clusters: List<CpuClusterInfo> = emptyList(),
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var detail by remember(process.pid) {
        mutableStateOf(
            CpuProcessDetail(
                pid = process.pid,
                name = displayName,
                packageName = process.packageName
            )
        )
    }
    var isLoading by remember(process.pid) { mutableStateOf(true) }
    var statusFeedback by remember(process.pid) { mutableStateOf<String?>(null) }
    var confirmKill by remember(process.pid) { mutableStateOf(false) }

    LaunchedEffect(process.pid) {
        isLoading = true
        val fetched = withContext(Dispatchers.IO) {
            LynxRepository.readProcessDetail(process.pid, displayName, process.packageName)
        }
        detail = fetched
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextTertiary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProcessIcon(
                        packageName = process.packageName,
                        processName = process.name,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "PID ${detail.pid} • ${detail.packageName}",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (detail.isSystemCritical) AccentOrange.copy(alpha = 0.14f) else AccentCyan.copy(alpha = 0.14f),
                    border = BorderStroke(
                        0.8.dp,
                        if (detail.isSystemCritical) AccentOrange.copy(alpha = 0.4f) else AccentCyan.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = if (detail.isSystemCritical) "SISTEM INTI" else String.format(Locale.US, "%.1f%% CPU", process.cpuPercent),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (detail.isSystemCritical) AccentOrange else AccentCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // 2x2 Clean Telemetry Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Thread Aktif", fontSize = 9.5.sp, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isLoading) "..." else "${detail.threadsCount} Threads",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Memori Fisik (RSS)", fontSize = 9.5.sp, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isLoading) "..." else String.format(Locale.US, "%.1f MB", detail.rssMemoryMb),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val niceDesc = when {
                    detail.nicePriority <= -5 -> "Prioritas Tinggi"
                    detail.nicePriority >= 5 -> "Latar Belakang"
                    else -> "Standar"
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Prioritas (Nice)", fontSize = 9.5.sp, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isLoading) "..." else "${detail.nicePriority} ($niceDesc)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (detail.nicePriority < 0) AccentCyan else TextPrimary
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Afinitas Core Aktif", fontSize = 9.5.sp, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isLoading) "..." else "Core ${detail.cpusAllowedList}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen
                        )
                    }
                }
            }

            // 1. CPU Priority Control (Renice across all threads in /proc/<pid>/task/*)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Prioritas Penjadwalan CPU (Renice Seluruh Thread)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    val niceOptions = listOf(
                        Triple(-15, "Tinggi [-15]", AccentCyan),
                        Triple(0, "Normal [0]", AccentGreen),
                        Triple(10, "Latar [+10]", AccentOrange)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgElevated, RoundedCornerShape(8.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        niceOptions.forEach { (targetNice, label, color) ->
                            val isSelected = when (targetNice) {
                                -15 -> detail.nicePriority <= -5
                                10 -> detail.nicePriority >= 5
                                else -> detail.nicePriority in -4..4
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) color.copy(alpha = 0.16f) else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 1.dp else 0.dp,
                                        color = if (isSelected) color.copy(alpha = 0.55f) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        scope.launch {
                                            val ok = withContext(Dispatchers.IO) {
                                                LynxRepository.setProcessPriority(detail.pid, targetNice)
                                            }
                                            if (ok) {
                                                val refreshed = withContext(Dispatchers.IO) {
                                                    LynxRepository.readProcessDetail(detail.pid, displayName, process.packageName)
                                                }
                                                detail = refreshed
                                                statusFeedback = "Prioritas ${refreshed.threadsCount} thread diatur ke Nice ${refreshed.nicePriority}"
                                            } else {
                                                statusFeedback = "Gagal mengubah prioritas proses"
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) color else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // 2. CPU Core Cluster Affinity (Taskset across all threads in /proc/<pid>/task/*)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(0.8.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Kunci Afinitas Kluster Core (Taskset Seluruh Thread)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    val littleCoreIds = clusters.firstOrNull { it.id == 0 }?.let { c ->
                        c.cpus.trim().split(Regex("[ ,]+")).filter { it.isNotBlank() }.flatMap { t ->
                            if (t.contains("-")) {
                                val b = t.split("-")
                                val s = b.getOrNull(0)?.toIntOrNull() ?: 0
                                val e = b.getOrNull(1)?.toIntOrNull() ?: s
                                (s..e).toList()
                            } else listOfNotNull(t.toIntOrNull())
                        }
                    }?.takeIf { it.isNotEmpty() } ?: listOf(0, 1, 2, 3, 4, 5)

                    val bigCoreIds = clusters.filter { it.id > 0 }.flatMap { c ->
                        c.cpus.trim().split(Regex("[ ,]+")).filter { it.isNotBlank() }.flatMap { t ->
                            if (t.contains("-")) {
                                val b = t.split("-")
                                val s = b.getOrNull(0)?.toIntOrNull() ?: 0
                                val e = b.getOrNull(1)?.toIntOrNull() ?: s
                                (s..e).toList()
                            } else listOfNotNull(t.toIntOrNull())
                        }
                    }.distinct().sorted().takeIf { it.isNotEmpty() } ?: listOf(6, 7)

                    val allCoreIds = (littleCoreIds + bigCoreIds).distinct().sorted()
                    fun toHexMask(ids: List<Int>): String {
                        var mask = 0
                        ids.forEach { id -> if (id in 0..30) mask = mask or (1 shl id) }
                        return mask.toString(16).padStart(2, '0')
                    }
                    val allRangeStr = "${allCoreIds.firstOrNull() ?: 0}-${allCoreIds.lastOrNull() ?: 7}"
                    val bigRangeStr = "${bigCoreIds.firstOrNull() ?: 6}-${bigCoreIds.lastOrNull() ?: 7}"
                    val littleRangeStr = "${littleCoreIds.firstOrNull() ?: 0}-${littleCoreIds.lastOrNull() ?: 5}"
                    val allHex = toHexMask(allCoreIds)
                    val bigHex = toHexMask(bigCoreIds)
                    val littleHex = toHexMask(littleCoreIds)

                    val affinityOptions = listOf(
                        Triple(allHex, "Semua ($allRangeStr)", AccentCyan),
                        Triple(bigHex, "Big ($bigRangeStr)", AccentOrange),
                        Triple(littleHex, "Little ($littleRangeStr)", AccentGreen)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgElevated, RoundedCornerShape(8.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        affinityOptions.forEach { (hexMask, label, color) ->
                            val isSelected = when (hexMask) {
                                bigHex -> detail.cpusAllowedList == bigRangeStr || detail.cpusAllowedList == bigCoreIds.joinToString(",")
                                littleHex -> detail.cpusAllowedList == littleRangeStr || detail.cpusAllowedList == littleCoreIds.joinToString(",")
                                else -> detail.cpusAllowedList == allRangeStr || detail.cpusAllowedList == "0-7"
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) color.copy(alpha = 0.16f) else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 1.dp else 0.dp,
                                        color = if (isSelected) color.copy(alpha = 0.55f) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        scope.launch {
                                            val ok = withContext(Dispatchers.IO) {
                                                LynxRepository.setProcessAffinity(detail.pid, hexMask)
                                            }
                                            if (ok) {
                                                val refreshed = withContext(Dispatchers.IO) {
                                                    LynxRepository.readProcessDetail(detail.pid, displayName, process.packageName)
                                                }
                                                detail = refreshed
                                                statusFeedback = "Afinitas ${refreshed.threadsCount} thread dikunci ke Core ${refreshed.cpusAllowedList}"
                                            } else {
                                                statusFeedback = "Gagal mengubah afinitas core proses"
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) color else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Status Feedback Banner if user applied an action
            statusFeedback?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentCyan,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }

            // Action Buttons (Force Stop / Close)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (detail.isSystemCritical) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.8.dp, BorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Proses Sistem Dilindungi",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextTertiary
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            if (!confirmKill) {
                                confirmKill = true
                            } else {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        LynxRepository.terminateProcess(detail.pid, detail.packageName)
                                    }
                                    onDismiss()
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (confirmKill) AccentRed else AccentRed.copy(alpha = 0.14f),
                            contentColor = if (confirmKill) Color.White else AccentRed
                        ),
                        border = BorderStroke(0.8.dp, AccentRed.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (confirmKill) "Konfirmasi Hentikan" else "Hentikan Proses",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(0.75f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BgSurfaceLowest,
                        contentColor = TextPrimary
                    ),
                    border = BorderStroke(0.8.dp, BorderSubtle)
                ) {
                    Text("Tutup", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ============================================================
//  10. CPU TOPOLOGY SPEC CARD (HARDWARE TRUTH ARCHITECTURE)
// ============================================================

