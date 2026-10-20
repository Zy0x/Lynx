package com.noir.lynx.feature.cpu.ui

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
import com.noir.lynx.core.locale.LynxText as Text
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

import com.noir.lynx.ui.*

@Composable
fun CpuUnifiedSystemCard(
    context: Context,
    uiState: LynxUiState,
    onApplySystemPreset: (String) -> Unit,
    onApplyCpuSetPreset: (String) -> Unit,
    onToggleCpuSetCore: (String, Int) -> Unit,
    onSetCpuSetApplyOnBoot: (Boolean) -> Unit,
    onApplyCpuIdlePreset: (String) -> Unit,
    onSetCoreParkingMode: (String) -> Unit,
    onToggleCStateDisabled: (Int, Boolean) -> Unit,
    onArmPllModeChange: (Boolean) -> Unit,
    onSetIdleApplyOnBoot: (Boolean) -> Unit,
    onUclampChange: (Float, Float) -> Unit,
    onMigrationChange: (Float, Float) -> Unit,
    onArchitectureModeChange: (String) -> Unit = {},
    onTopAppPreferIdleChange: (Boolean) -> Unit = {},
    onWorkqueuePowerEfficientChange: (Boolean) -> Unit = {},
    onResetSection: (Pair<String, String>) -> Unit,
    onResetToStandardProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showCStateSheet by remember { mutableStateOf(false) }
    var showCpuSetSheet by remember { mutableStateOf(false) }
    var activeTooltip by remember { mutableStateOf<String?>(null) }
    val schedInfo = uiState.schedulerInfo

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ── 1. Status Profil Sistem (Zen Bento Architecture) ──────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF10141F),
            border = BorderStroke(
                0.8.dp,
                Brush.verticalGradient(
                    listOf(
                        Color(0x28FFFFFF),
                        Color(0x10FFFFFF)
                    )
                )
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val masterProfile = uiState.cpuComprehensiveProfile.lowercase()
                val masterLabel = when (masterProfile) {
                    "battery" -> "Mode Hemat Daya (Powersave)"
                    "performance", "gaming" -> "Mode $PERFORMA_LABEL (Gaming Stabil)"
                    "extreme" -> "Mode Ekstrem (100% Clock Max)"
                    else -> "Mode Seimbang (Balance Optimal)"
                }
                val masterIcon = when (masterProfile) {
                    "battery" -> Icons.Default.BatteryChargingFull
                    "performance", "gaming" -> Icons.Default.Speed
                    "extreme" -> Icons.Default.LocalFireDepartment
                    else -> Icons.Default.Tune
                }
                val masterAccent = when (masterProfile) {
                    "battery" -> AccentGreen
                    "performance", "gaming" -> AccentOrange
                    "extreme" -> AccentRed
                    else -> AccentCyan
                }
                val isModified = uiState.isCpuModified

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isModified) AccentOrange.copy(alpha = 0.14f) else masterAccent.copy(alpha = 0.14f),
                            border = BorderStroke(0.6.dp, if (isModified) AccentOrange.copy(alpha = 0.35f) else masterAccent.copy(alpha = 0.35f)),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isModified) Icons.Default.Tune else masterIcon,
                                    contentDescription = null,
                                    tint = if (isModified) AccentOrange else masterAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = masterLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isModified) "Parameter disesuaikan manual" else "Selaras dengan Tab Performa",
                                fontSize = 10.sp,
                                color = if (isModified) AccentOrange else TextSecondary.copy(alpha = 0.75f)
                            )
                        }
                    }

                    if (isModified) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentOrange.copy(alpha = 0.14f),
                            border = BorderStroke(0.5.dp, AccentOrange.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "Kustom",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentOrange,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }
                    }
                }

                if (isModified) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0C0F17),
                        border = BorderStroke(0.5.dp, Color(0x12FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Konfigurasi telah disesuaikan di luar baseline master. Anda dapat mereset parameter agar kembali selaras.",
                                fontSize = 10.5.sp,
                                color = TextSecondary.copy(alpha = 0.8f),
                                lineHeight = 14.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    onClick = onResetToStandardProfile,
                                    shape = RoundedCornerShape(8.dp),
                                    color = AccentOrange.copy(alpha = 0.16f),
                                    border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.45f)),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = AccentOrange,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Reset ke Baseline",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AccentOrange
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Core Efficiency (Formerly CPU Idle Core Parking)
        CpuIdleCoreParkingCard(
            cpuIdle = uiState.cpuIdle,
            clusters = uiState.clusters,
            onApplyPreset = onApplyCpuIdlePreset,
            onSetCoreParkingMode = onSetCoreParkingMode,
            onToggleCStateDisabled = { idx, dis -> onToggleCStateDisabled(idx, dis) },
            onArmPllModeChange = onArmPllModeChange,
            onApplyOnBootChange = onSetIdleApplyOnBoot,
            onResetToOem = { onResetSection("cpuidle" to "Core Efficiency") },
            isModified = uiState.cpuIdle.applyOnBoot,
            onOpenGranularSheet = { showCStateSheet = true }
        )

        // 3. Task Shield and App Isolation (formerly CPU Sets).
        CpuSetsTaskShieldCard(
            cpuSets = uiState.cpuSets,
            clusters = uiState.clusters,
            onApplyPreset = onApplyCpuSetPreset,
            onToggleCore = onToggleCpuSetCore,
            onApplyOnBootChange = onSetCpuSetApplyOnBoot,
            onResetToOem = { onResetSection("cpuset" to "Task Shield & Isolasi") },
            isModified = uiState.cpuSets.applyOnBoot,
            onOpenMatrixSheet = { showCpuSetSheet = true }
        )

        // 4. Task Priority Boost (Uclamp) & Kernel Scheduler Card
        KernelSchedulerCard(
            schedInfo = schedInfo,
            onApplyPreset = onApplySystemPreset,
            onUclampChange = onUclampChange,
            onMigrationChange = onMigrationChange,
            onArchitectureModeChange = onArchitectureModeChange,
            onTopAppPreferIdleChange = onTopAppPreferIdleChange,
            onWorkqueuePowerEfficientChange = onWorkqueuePowerEfficientChange,
            onShowTooltip = { activeTooltip = it },
            onResetToOem = { onResetSection("scheduler" to "Penjadwal Kernel") }
        )
        Spacer(Modifier.height(10.dp))
    }

    // Technical Tooltip Dialog
    if (activeTooltip != null) {
        AlertDialog(
            onDismissRequest = { activeTooltip = null },
            containerColor = Color(0xFF141824),
            title = {
                Text(
                    text = "Informasi Kernel Hardware",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = activeTooltip!!,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { activeTooltip = null }) {
                    Text("Tutup", color = AccentCyan)
                }
            }
        )
    }

    // Granular C-States Bottom Sheet
    if (showCStateSheet) {
        CpuIdleCStatesGranularSheet(
            cpuIdle = uiState.cpuIdle,
            onToggleCStateDisabled = { idx, dis -> onToggleCStateDisabled(idx, dis) },
            onArmPllModeChange = onArmPllModeChange,
            onDismiss = { showCStateSheet = false }
        )
    }

    // Manual CPU Sets Matrix Bottom Sheet
    if (showCpuSetSheet) {
        CpuSetsManualMatrixSheet(
            cpuSets = uiState.cpuSets,
            clusters = uiState.clusters,
            onToggleCore = onToggleCpuSetCore,
            onDismiss = { showCpuSetSheet = false }
        )
    }
}

// ============================================================
//  7. KERNEL SCHEDULER CARD (EAS UCLAMP & ARCHITECTURE)
// ============================================================

