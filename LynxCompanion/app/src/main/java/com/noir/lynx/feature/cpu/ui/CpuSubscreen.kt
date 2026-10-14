package com.noir.lynx.feature.cpu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Subscreen 1: CPU Performa, Sistem, dan Monitor Hardware
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
        viewModel.refreshCpuCores()
        viewModel.refreshClusters()
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
                shape = RoundedCornerShape(10.dp),
                color = BgSurfaceLowest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.profile_override_status, "CPU", overrideProfile.uppercase(), uiState.state.activeProfile.uppercase()),
                        color = TextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.resetCpuToActiveProfile(context) }) {
                        Text("Ikuti induk", fontSize = 10.sp)
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
