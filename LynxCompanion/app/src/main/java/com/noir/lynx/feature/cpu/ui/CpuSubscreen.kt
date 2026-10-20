package com.noir.lynx.feature.cpu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.noir.lynx.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.*

/**
 * Subscreen 1: CPU performance, system, and hardware monitor.
 */
@Composable
fun CpuSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clusters = uiState.clusters
    val schedInfo = uiState.schedulerInfo
    var pendingResetSection by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Auto refresh CPU data on screen launch
    LaunchedEffect(Unit) {
        if (uiState.clusters.isEmpty()) {
            viewModel.refreshClusters()
        }
        if (uiState.cpuCores.isEmpty()) {
            viewModel.refreshCpuCores()
        }
        viewModel.refreshSchedulerInfo(context)
        viewModel.loadCpuSetsInfo(context)
        viewModel.loadCpuIdleInfo(context)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        uiState.state.profileOverrides[com.noir.lynx.profiles.ProfileDomain.CPU]?.let { overrideProfile ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF10141F),
                border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(AccentOrange, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.profile_override_status, "CPU", overrideProfile.uppercase(), uiState.state.activeProfile.uppercase()),
                            color = TextSecondary.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                    Surface(
                        onClick = { viewModel.resetCpuToActiveProfile(context) },
                        shape = RoundedCornerShape(8.dp),
                        color = AccentOrange.copy(alpha = 0.12f),
                        border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.35f)),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp),
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
                                text = "Ikuti Global",
                                color = AccentOrange,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
        when (uiState.selectedCpuTab) {
            0 -> {
                // TAB 0: PERFORMANCE (Consumer Level 1 & Level 2)
                CpuDashboardHeroCard(uiState = uiState)

                // Smart Recommendation Banner (Non-intrusive & Dismissible)
                val rec = uiState.cpuRecommendation
                if (rec != null && !uiState.isCpuRecommendationDismissed) {
                    CpuSmartRecommendationBanner(
                        recommendation = rec,
                        onApplyRecommendation = {
                            viewModel.applyComprehensiveCpuProfile("balanced", context)
                            viewModel.dismissCpuRecommendation()
                        },
                        onDismiss = { viewModel.dismissCpuRecommendation() }
                    )
                }

                // Unified Performance Card (1-Click Pills & Advanced Expansion)
                CpuUnifiedPerformanceCard(
                    uiState = uiState,
                    clusters = clusters,
                    governorTunables = uiState.governorTunables,
                    onApplyProfile = { profile -> viewModel.applyComprehensiveCpuProfile(profile, context) },
                    onFreqChange = { policyId, min, max -> viewModel.setClusterFrequency(policyId, min, max) },
                    onGovChange = { policyId, gov -> viewModel.setClusterGovernor(policyId, gov) },
                    onLoadTunables = { policyId, gov -> viewModel.loadGovernorTunables(policyId, gov) },
                    onTunableChange = { policyId, gov, k, v -> viewModel.setGovernorTunable(policyId, gov, k, v) },
                    onLockToggle = { policyId, lock, min, max -> viewModel.setClusterLock(policyId, lock, min, max) },
                    schedInfo = schedInfo,
                    onPpmPolicyChange = { idx, en -> viewModel.setPpmPolicy(idx, en, context) },
                    onQcomTouchboostChange = { en -> viewModel.setQcomTouchboost(en, context) },
                    onQcomInputBoostChange = { freq, ms -> viewModel.setQcomInputBoost(freq, ms, context) },
                    onMtkInterconnectBoostChange = { en -> viewModel.setMtkInterconnectBusBoost(en, context) },
                    onMtkCpuPowerModeChange = { mode -> viewModel.setMtkCpuPowerMode(mode, context) },
                    onMtkDlptBypassChange = { en -> viewModel.setMtkDlptImaxBypass(en, context) },
                    onQcomDevfreqBusBoostChange = { en -> viewModel.setQcomDevfreqBusBoost(en, context) },
                    onUniversalBusProfileChange = { prof -> viewModel.setUniversalBusBandwidthProfile(prof, context) },
                    onUniversalTouchBoostChange = { en -> viewModel.setUniversalTouchBoost(en, context) },
                    onUniversalAntiThrottlingGuardChange = { en -> viewModel.setUniversalAntiThrottlingGuard(en, context) }
                )
            }
            1 -> {
                // TAB 1: SYSTEM (Level 1 Presets & Level 2 Detailed Controls)
                CpuUnifiedSystemCard(
                    context = context,
                    uiState = uiState,
                    onApplySystemPreset = { preset -> viewModel.applySchedulerPreset(preset, context) },
                    onApplyCpuSetPreset = { preset -> viewModel.applyCpuSetPreset(preset, context) },
                    onToggleCpuSetCore = { group, coreId -> viewModel.toggleCpuSetCore(group, coreId, context) },
                    onSetCpuSetApplyOnBoot = { enabled -> viewModel.setCpuSetApplyOnBoot(enabled, context) },
                    onApplyCpuIdlePreset = { preset -> viewModel.applyCpuIdlePreset(preset, context) },
                    onSetCoreParkingMode = { mode -> viewModel.setCoreParkingMode(mode, uiState.cpuCores.size.coerceAtLeast(8), context) },
                    onToggleCStateDisabled = { stateIndex, disabled -> viewModel.setCpuIdleStateDisabled(stateIndex, disabled, context) },
                    onArmPllModeChange = { enabled -> viewModel.setArmPllMode(enabled) },
                    onSetIdleApplyOnBoot = { enabled -> viewModel.setCpuIdleApplyOnBoot(enabled, context) },
                    onUclampChange = { min, max ->
                        viewModel.setSchedulerTunable("uclamp_min", min.toLong(), context)
                        viewModel.setSchedulerTunable("uclamp_max", max.toLong(), context)
                    },
                    onMigrationChange = { up, down ->
                        viewModel.setSchedulerTunable("sched_upmigrate", up.toLong(), context)
                        viewModel.setSchedulerTunable("sched_downmigrate", down.toLong(), context)
                    },
                    onArchitectureModeChange = { mode -> viewModel.setSchedulerArchitectureMode(mode, context) },
                    onTopAppPreferIdleChange = { en -> viewModel.setSchedtunePreferIdle("top-app", en, context) },
                    onWorkqueuePowerEfficientChange = { en -> viewModel.setWorkqueuePowerEfficient(en, context) },
                    onResetSection = { pendingResetSection = it },
                    onResetToStandardProfile = { viewModel.resetCpuToActiveProfile(context) }
                )
            }
            2 -> {
                // TAB 2: MONITOR (Telemetry & Hardware Specs)
                CpuHardwareMonitorCard(
                    uiState = uiState,
                    onToggleCore = { core -> viewModel.setCpuCoreOnline(core.coreId, !core.isOnline) }
                )
            }
        }

        // Section Reset Confirmation Dialog
        if (pendingResetSection != null) {
            val (sectionKey, sectionTitle) = pendingResetSection!!
            AlertDialog(
                onDismissRequest = { pendingResetSection = null },
                containerColor = BgCard,
                title = { Text(stringResource(R.string.reset_section_title, sectionTitle), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = { Text(stringResource(R.string.reset_section_message, sectionTitle), fontSize = 12.5.sp, color = TextSecondary) },
                confirmButton = {
                    Button(
                        onClick = {
                            when (sectionKey) {
                                "cpuset" -> viewModel.resetCpuSetsToOem(context)
                                "cpuidle" -> viewModel.resetCpuIdleToOem(context)
                                "scheduler" -> viewModel.resetSchedulerTunablesToOem(context)
                            }
                            pendingResetSection = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Reset ke Default", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingResetSection = null }) {
                        Text("Batal", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            )
        }
    }
}

/**
 * Backward compatibility alias for legacy call sites.
 */
@Composable
fun TuningCpuCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    CpuSubscreen(uiState = uiState, viewModel = viewModel, modifier = modifier)
}

/**
 * High-performance virtualized LazyListScope items for CPU category.
 * Each card is measured and drawn independently by Compose rather than in a single monolithic layout.
 */
fun androidx.compose.foundation.lazy.LazyListScope.tuningCpuCategoryItems(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: android.content.Context,
    targetTab: Int = uiState.selectedCpuTab
) {
    val clusters = uiState.clusters
    val schedInfo = uiState.schedulerInfo

    if (targetTab == 0) {
        uiState.state.profileOverrides[com.noir.lynx.profiles.ProfileDomain.CPU]?.let { overrideProfile ->
            item(key = "cpu_profile_override_status") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF10141F),
                    border = BorderStroke(0.6.dp, Color(0x18FFFFFF)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AccentOrange, CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.profile_override_status, "CPU", overrideProfile.uppercase(), uiState.state.activeProfile.uppercase()),
                                color = TextSecondary.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Surface(
                            onClick = { viewModel.resetCpuToActiveProfile(context) },
                            shape = RoundedCornerShape(8.dp),
                            color = AccentOrange.copy(alpha = 0.12f),
                            border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.35f)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp),
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
                                    text = "Ikuti Global",
                                    color = AccentOrange,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    when (targetTab) {
        0 -> {
            item(key = "cpu_hero_card") {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    CpuDashboardHeroCard(uiState = uiState)
                }
            }

            val rec = uiState.cpuRecommendation
            if (rec != null && !uiState.isCpuRecommendationDismissed) {
                item(key = "cpu_recommendation_banner") {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        CpuSmartRecommendationBanner(
                            recommendation = rec,
                            onApplyRecommendation = {
                                viewModel.applyComprehensiveCpuProfile("balanced", context)
                                viewModel.dismissCpuRecommendation()
                            },
                            onDismiss = { viewModel.dismissCpuRecommendation() }
                        )
                    }
                }
            }

            item(key = "cpu_unified_performance_card") {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    CpuUnifiedPerformanceCard(
                        uiState = uiState,
                        clusters = clusters,
                        governorTunables = uiState.governorTunables,
                        onApplyProfile = { profile -> viewModel.applyComprehensiveCpuProfile(profile, context) },
                        onFreqChange = { policyId, min, max -> viewModel.setClusterFrequency(policyId, min, max) },
                        onGovChange = { policyId, gov -> viewModel.setClusterGovernor(policyId, gov) },
                        onLoadTunables = { policyId, gov -> viewModel.loadGovernorTunables(policyId, gov) },
                        onTunableChange = { policyId, gov, k, v -> viewModel.setGovernorTunable(policyId, gov, k, v) },
                        onLockToggle = { policyId, lock, min, max -> viewModel.setClusterLock(policyId, lock, min, max) },
                        schedInfo = schedInfo,
                        onPpmPolicyChange = { idx, en -> viewModel.setPpmPolicy(idx, en, context) },
                        onQcomTouchboostChange = { en -> viewModel.setQcomTouchboost(en, context) },
                        onQcomInputBoostChange = { freq, ms -> viewModel.setQcomInputBoost(freq, ms, context) },
                        onMtkInterconnectBoostChange = { en -> viewModel.setMtkInterconnectBusBoost(en, context) },
                        onMtkCpuPowerModeChange = { mode -> viewModel.setMtkCpuPowerMode(mode, context) },
                        onMtkDlptBypassChange = { en -> viewModel.setMtkDlptImaxBypass(en, context) },
                        onQcomDevfreqBusBoostChange = { en -> viewModel.setQcomDevfreqBusBoost(en, context) },
                        onUniversalBusProfileChange = { prof -> viewModel.setUniversalBusBandwidthProfile(prof, context) },
                        onUniversalTouchBoostChange = { en -> viewModel.setUniversalTouchBoost(en, context) },
                        onUniversalAntiThrottlingGuardChange = { en -> viewModel.setUniversalAntiThrottlingGuard(en, context) }
                    )
                }
            }
        }
        1 -> {
            item(key = "cpu_unified_system_card") {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    CpuUnifiedSystemCard(
                        context = context,
                        uiState = uiState,
                        onApplySystemPreset = { preset -> viewModel.applySchedulerPreset(preset, context) },
                        onApplyCpuSetPreset = { preset -> viewModel.applyCpuSetPreset(preset, context) },
                        onToggleCpuSetCore = { group, coreId -> viewModel.toggleCpuSetCore(group, coreId, context) },
                        onSetCpuSetApplyOnBoot = { enabled -> viewModel.setCpuSetApplyOnBoot(enabled, context) },
                        onApplyCpuIdlePreset = { preset -> viewModel.applyCpuIdlePreset(preset, context) },
                        onSetCoreParkingMode = { mode -> viewModel.setCoreParkingMode(mode, uiState.cpuCores.size.coerceAtLeast(8), context) },
                        onToggleCStateDisabled = { stateIndex, disabled -> viewModel.setCpuIdleStateDisabled(stateIndex, disabled, context) },
                        onArmPllModeChange = { enabled -> viewModel.setArmPllMode(enabled) },
                        onSetIdleApplyOnBoot = { enabled -> viewModel.setCpuIdleApplyOnBoot(enabled, context) },
                        onUclampChange = { min, max ->
                            viewModel.setSchedulerTunable("uclamp_min", min.toLong(), context)
                            viewModel.setSchedulerTunable("uclamp_max", max.toLong(), context)
                        },
                        onMigrationChange = { up, down ->
                            viewModel.setSchedulerTunable("sched_upmigrate", up.toLong(), context)
                            viewModel.setSchedulerTunable("sched_downmigrate", down.toLong(), context)
                        },
                        onArchitectureModeChange = { mode -> viewModel.setSchedulerArchitectureMode(mode, context) },
                        onTopAppPreferIdleChange = { en -> viewModel.setSchedtunePreferIdle("top-app", en, context) },
                        onWorkqueuePowerEfficientChange = { en -> viewModel.setWorkqueuePowerEfficient(en, context) },
                        onResetSection = { },
                        onResetToStandardProfile = { viewModel.resetCpuToActiveProfile(context) }
                    )
                }
            }
        }
        2 -> {
            item(key = "cpu_hardware_monitor_card") {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    CpuHardwareMonitorCard(
                        uiState = uiState,
                        onToggleCore = { core -> viewModel.setCpuCoreOnline(core.coreId, !core.isOnline) }
                    )
                }
            }
        }
    }
}
