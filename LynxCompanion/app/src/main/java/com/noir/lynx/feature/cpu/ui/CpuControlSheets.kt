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
fun CpuIdleCStatesGranularSheet(
    cpuIdle: CpuIdleInfo,
    onToggleCStateDisabled: (Int, Boolean) -> Unit,
    onArmPllModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderGlass) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Kustomisasi C-States & Latensi",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Driver: ${cpuIdle.driver} • Kontrol status tidur inti CPU",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentBlue.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, AccentBlue.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${cpuIdle.states.size} Level",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Menonaktifkan status tidur dalam (Deep C-States) akan memangkas micro-stuttering dan latensi bangun prosesor, namun sedikit meningkatkan konsumsi daya siaga.",
                fontSize = 10.5.sp,
                color = TextTertiary,
                lineHeight = 14.5.sp
            )

            // C-States List
            if (cpuIdle.states.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Tidak ada status idle spesifik yang diekspos oleh driver kernel saat ini.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                cpuIdle.states.forEach { state ->
                    val isAllowed = !state.isDisabled
                    val stateAccent = if (isAllowed) AccentCyan else TextTertiary

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.8.dp, if (isAllowed) BorderGlass else BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = stateAccent.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "Level ${state.index}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = stateAccent,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = state.name.ifBlank { "State ${state.index}" },
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }

                                if (state.desc.isNotBlank()) {
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = state.desc,
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }

                                Spacer(Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Latensi: ${state.latencyUs} µs",
                                        fontSize = 9.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextTertiary
                                    )
                                    Text(
                                        text = "•",
                                        fontSize = 9.5.sp,
                                        color = TextTertiary
                                    )
                                    Text(
                                        text = "Residensi: ${state.residencyUs} µs",
                                        fontSize = 9.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextTertiary
                                    )
                                }
                            }

                            Switch(
                                checked = isAllowed,
                                onCheckedChange = { allowed ->
                                    onToggleCStateDisabled(state.index, !allowed)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AccentCyan,
                                    checkedTrackColor = AccentCyan.copy(alpha = 0.35f),
                                    uncheckedThumbColor = TextTertiary,
                                    uncheckedTrackColor = BgElevated
                                )
                            )
                        }
                    }
                }
            }

            // ARM PLL Section (if supported)
            if (cpuIdle.isArmPllSupported) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "ARM PLL Hardware Power-Down",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Matikan PLL clock generator saat seluruh core dalam kluster berada di status idle.",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                lineHeight = 13.5.sp
                            )
                        }

                        Switch(
                            checked = cpuIdle.armPllMode,
                            onCheckedChange = onArmPllModeChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AccentBlue,
                                checkedTrackColor = AccentBlue.copy(alpha = 0.35f),
                                uncheckedThumbColor = TextTertiary,
                                uncheckedTrackColor = BgElevated
                            )
                        )
                    }
                }
            }

            // Close Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BgSurfaceLowest,
                    contentColor = TextPrimary
                ),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Text("Tutup Kustomisasi", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ============================================================
//  7B. CPU SETS MANUAL MATRIX SHEET (BOTTOM SHEET)
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuSetsManualMatrixSheet(
    cpuSets: CpuSetsInfo,
    clusters: List<CpuClusterInfo>,
    onToggleCore: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val totalCores = cpuSets.totalCoresCount.coerceIn(4, 16)
    val isBigCore = { coreId: Int ->
        clusters.any { (it.role.contains("Big", ignoreCase = true) || it.role.contains("Prime", ignoreCase = true) || it.role.contains("Performance", ignoreCase = true) || it.id > 0) && it.containsCore(coreId) }
            || (clusters.isEmpty() && coreId >= 6)
    }

    val groups = listOf(
        Triple("top-app", "Top-App (Game & Aplikasi Aktif)", AccentCyan),
        Triple("foreground", "Foreground (Aplikasi Latar Depan)", AccentBlue),
        Triple("background", "Background (Tugas Latar Belakang)", AccentOrange),
        Triple("system-background", "System Background (Daemon Kernel)", TextSecondary)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderGlass) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Matriks Alokasi Core (CPU Sets)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Isolasi thread & penugasan core per-grup cgroup",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentCyan.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "$totalCores Cores",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Ketuk core (C0-C${totalCores - 1}) pada masing-masing kelompok tugas untuk mengizinkan atau melarang thread berjalan pada inti fisik tersebut.",
                fontSize = 10.5.sp,
                color = TextTertiary,
                lineHeight = 14.5.sp
            )

            // Groups
            groups.forEach { (groupKey, groupTitle, groupAccent) ->
                val assignedString = when (groupKey) {
                    "top-app" -> cpuSets.topAppCpus
                    "foreground" -> cpuSets.foregroundCpus
                    "background" -> cpuSets.backgroundCpus
                    "system-background" -> cpuSets.systemBackgroundCpus
                    else -> ""
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgSurfaceLowest,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = groupTitle,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = groupAccent.copy(alpha = 0.16f),
                                border = BorderStroke(0.6.dp, groupAccent.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "Core: ${assignedString.ifBlank { "0" }}",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = groupAccent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Core Grid (8 Cores: chunked by 4)
                        val coreIndices = (0 until totalCores).toList()
                        val rows = coreIndices.chunked(4)
                        rows.forEach { rowCores ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowCores.forEach { coreId ->
                                    val isAssigned = cpuSets.isCoreInGroup(groupKey, coreId)
                                    val isBig = isBigCore(coreId)
                                    val roleLabel = if (isBig) "Big" else "Lit"

                                    Surface(
                                        onClick = { onToggleCore(groupKey, coreId) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 44.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isAssigned) groupAccent.copy(alpha = 0.2f) else BgElevated,
                                        border = BorderStroke(
                                            width = if (isAssigned) 1.2.dp else 0.8.dp,
                                            color = if (isAssigned) groupAccent else BorderSubtle
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "C$coreId",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isAssigned) groupAccent else TextTertiary
                                            )
                                            Text(
                                                text = roleLabel,
                                                fontSize = 8.5.sp,
                                                color = if (isAssigned) groupAccent.copy(alpha = 0.8f) else TextTertiary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Close Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BgSurfaceLowest,
                    contentColor = TextPrimary
                ),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Text("Selesai & Tutup", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ============================================================
//  8. CPU HARDWARE MONITOR CARD (TAB 2: MONITOR)
// ============================================================

