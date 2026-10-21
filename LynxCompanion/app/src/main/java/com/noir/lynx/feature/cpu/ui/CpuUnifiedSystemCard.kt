package com.noir.lynx.feature.cpu.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.controls.SafeSlider
import com.noir.lynx.designsystem.theme.*

/**
 * CpuUnifiedSystemCard — Redesigned Master System & Core Scheduler Experience.
 *
 * Architecture & Design Standards:
 * 1. Unified Master Hero: Status alignment with baseline profile & 1-click harmonized system presets.
 * 2. Integrated Accordions: Consistent with Tab Performa's Cluster and Advanced sections.
 * 3. Anti-AI-Slop & Anti-Cognitive Load: Sentence-case headers, calibrated dark palette, human-friendly explanations.
 * 4. Touch-First: 48dp target sizes, clear visual feedback, smooth spring accordions.
 */
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
    onBigTaskRotationChange: (Boolean) -> Unit = {},
    onCstateAwareChange: (Boolean) -> Unit = {},
    onEnergyAwareChange: (Boolean) -> Unit = {},
    onIowaitBoostChange: (Boolean) -> Unit = {},
    onResetSection: (Pair<String, String>) -> Unit,
    onResetToStandardProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val schedInfo = uiState.schedulerInfo
    val cpuIdle = uiState.cpuIdle
    val cpuSets = uiState.cpuSets
    val clusters = uiState.clusters

    var expandedScheduler by rememberSaveable { mutableStateOf(true) }
    var expandedIdle by rememberSaveable { mutableStateOf(false) }
    var expandedCpuSets by rememberSaveable { mutableStateOf(false) }

    var showCStateSheet by remember { mutableStateOf(false) }
    var showCpuSetSheet by remember { mutableStateOf(false) }
    var activeTooltip by remember { mutableStateOf<String?>(null) }

    // Uclamp calculation & state
    val resolveBoostPct: (SchedulerInfo) -> Float = { info ->
        when {
            info.isUclampSupported && info.uclampMin > 100 ->
                ((info.uclampMin * 100f) / 1024f).coerceIn(0f, 100f)
            info.uclampMin in 0..100 -> info.uclampMin.toFloat()
            info.topAppSchedtuneBoost in 0..100 -> info.topAppSchedtuneBoost.toFloat()
            else -> 0f
        }
    }
    var uclampMin by remember { mutableFloatStateOf(resolveBoostPct(schedInfo)) }
    var uclampMax by remember { mutableFloatStateOf(schedInfo.uclampMax.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(schedInfo) {
        val now = System.currentTimeMillis()
        if (!isDragging && (now - lastInteractionTime > 3500L)) {
            uclampMin = resolveBoostPct(schedInfo)
            uclampMax = schedInfo.uclampMax.toFloat()
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ── 1. Master System Header & Harmonized Presets ──────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF10141F),
            border = BorderStroke(0.8.dp, Brush.verticalGradient(listOf(Color(0x28FFFFFF), Color(0x10FFFFFF)))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val masterProfile = uiState.cpuComprehensiveProfile.lowercase()
                val masterLabel = when (masterProfile) {
                    "battery" -> "Mode Hemat Daya"
                    "performance", "gaming" -> "Mode Performa Gaming"
                    "extreme" -> "Mode Ekstrem Unrestricted"
                    else -> "Mode Seimbang Optimal"
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
                                    imageVector = Icons.Default.SettingsSuggest,
                                    contentDescription = null,
                                    tint = if (isModified) AccentOrange else masterAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Sistem & Penjadwal Inti",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isModified) "Parameter disesuaikan manual" else "Selaras dengan $masterLabel",
                                fontSize = 10.sp,
                                color = if (isModified) AccentOrange else TextSecondary.copy(alpha = 0.75f)
                            )
                        }
                    }

                    if (isModified) {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onResetToStandardProfile()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = AccentOrange.copy(alpha = 0.14f),
                            border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.45f)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reset Baseline", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AccentOrange)
                            }
                        }
                    }
                }

                // 4-Way Harmonized System Presets Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val systemPresets = listOf(
                        Triple("battery", "Hemat Daya", AccentGreen),
                        Triple("balanced", "Seimbang", AccentCyan),
                        Triple("gaming", "Responsif", AccentOrange),
                        Triple("extreme", "Maksimal", AccentRed)
                    )
                    val activeSysKey = when {
                        schedInfo.activePreset.isNotBlank() -> schedInfo.activePreset.lowercase()
                        masterProfile in listOf("battery", "balanced", "performance", "gaming", "extreme") ->
                            if (masterProfile == "performance") "gaming" else masterProfile
                        else -> "balanced"
                    }

                    systemPresets.forEach { (presetKey, presetLabel, presetColor) ->
                        val isSelected = activeSysKey == presetKey || (presetKey == "gaming" && activeSysKey == "performance")
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onApplySystemPreset(presetKey)
                                onApplyCpuIdlePreset(if (presetKey == "extreme") "gaming" else presetKey)
                                onApplyCpuSetPreset(if (presetKey == "extreme") "gaming" else presetKey)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) presetColor.copy(alpha = 0.18f) else Color(0xFF0C0F17),
                            border = BorderStroke(if (isSelected) 1.2.dp else 0.6.dp, if (isSelected) presetColor else Color(0x15FFFFFF)),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)) {
                                Text(
                                    text = presetLabel,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) presetColor else TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 2. Accordion 1: Penjadwal Tugas & EAS Boost ──────
        CpuSchedulerAccordion(
            expanded = expandedScheduler,
            onToggleExpanded = { expandedScheduler = !expandedScheduler },
            schedInfo = schedInfo,
            uclampMin = uclampMin,
            uclampMax = uclampMax,
            onUclampMinChange = {
                isDragging = true
                lastInteractionTime = System.currentTimeMillis()
                uclampMin = it
            },
            onUclampMinChangeFinished = { min, max ->
                isDragging = false
                lastInteractionTime = System.currentTimeMillis()
                onUclampChange(min, max)
            },
            onArchitectureModeChange = onArchitectureModeChange,
            onTopAppPreferIdleChange = onTopAppPreferIdleChange,
            onWorkqueuePowerEfficientChange = onWorkqueuePowerEfficientChange,
            onBigTaskRotationChange = onBigTaskRotationChange,
            onCstateAwareChange = onCstateAwareChange,
            onEnergyAwareChange = onEnergyAwareChange,
            onIowaitBoostChange = onIowaitBoostChange,
            onShowTooltip = { activeTooltip = it }
        )

        // ── 3. Accordion 2: Efisiensi Daya & C-States ────────
        CpuIdleEfficiencyAccordion(
            expanded = expandedIdle,
            onToggleExpanded = { expandedIdle = !expandedIdle },
            cpuIdle = cpuIdle,
            onApplyIdlePreset = onApplyCpuIdlePreset,
            onSetCoreParkingMode = onSetCoreParkingMode,
            onOpenCStateSheet = { showCStateSheet = true },
            onArmPllModeChange = onArmPllModeChange,
            onSetIdleApplyOnBoot = onSetIdleApplyOnBoot
        )

        // ── 4. Accordion 3: Isolasi Tugas & CPU Sets ─────────
        CpuSetsIsolationAccordion(
            expanded = expandedCpuSets,
            onToggleExpanded = { expandedCpuSets = !expandedCpuSets },
            cpuSets = cpuSets,
            onApplyCpuSetPreset = onApplyCpuSetPreset,
            onOpenCpuSetSheet = { showCpuSetSheet = true },
            onSetCpuSetApplyOnBoot = onSetCpuSetApplyOnBoot
        )
    }

    // Technical Tooltip Dialog
    if (activeTooltip != null) {
        AlertDialog(
            onDismissRequest = { activeTooltip = null },
            containerColor = Color(0xFF141824),
            title = { Text("Informasi Kernel Hardware", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text(activeTooltip!!, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp) },
            confirmButton = {
                TextButton(onClick = { activeTooltip = null }) { Text("Tutup", color = AccentCyan) }
            }
        )
    }

    // Granular C-States Bottom Sheet
    if (showCStateSheet) {
        CpuIdleCStatesGranularSheet(
            cpuIdle = cpuIdle,
            onToggleCStateDisabled = { idx, dis -> onToggleCStateDisabled(idx, dis) },
            onArmPllModeChange = onArmPllModeChange,
            onDismiss = { showCStateSheet = false }
        )
    }

    // Manual CPU Sets Matrix Bottom Sheet
    if (showCpuSetSheet) {
        CpuSetsManualMatrixSheet(
            cpuSets = cpuSets,
            clusters = clusters,
            onToggleCore = onToggleCpuSetCore,
            onDismiss = { showCpuSetSheet = false }
        )
    }
}
