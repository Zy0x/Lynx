package com.noir.lynx.feature.battery.ui
import com.noir.lynx.ui.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.ui.LynxViewModel

fun batteryCurrentLevel(uiState: LynxUiState): Int =
    uiState.batteryInfoStats.currentLevel.takeIf { it > 0 } ?: (uiState.batteryDetails?.level ?: 0)

@Composable
fun BatteryInformationContent(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BatteryRuntimeLiveCard(viewModel)
        BatteryHistoryLiveCard(viewModel)
        BatteryHardwareLiveCard(uiState, viewModel)
    }
}

// Stable card keys let the list skip off-screen draw trees and retain its anchor.
fun LazyListScope.batteryInformationItems(uiState: LynxUiState, viewModel: LynxViewModel) {
    item(key = "battery_runtime") { BatteryRuntimeLiveCard(viewModel) }
    item(key = "battery_history") { BatteryHistoryLiveCard(viewModel) }
    item(key = "battery_hardware") { BatteryHardwareLiveCard(uiState, viewModel) }
}

@Composable
private fun BatteryRuntimeLiveCard(viewModel: LynxViewModel) {
    val liveState by viewModel.batteryInformationUiState.collectAsState()
    val currentLevel = liveState.stats.currentLevel.takeIf { it > 0 } ?: (liveState.details?.level ?: 0)
    BatteryRuntimeCard(liveState.stats, currentLevel)
}

@Composable
private fun BatteryHistoryLiveCard(viewModel: LynxViewModel) {
    val liveState by viewModel.batteryInformationUiState.collectAsState()
    val currentLevel = liveState.stats.currentLevel.takeIf { it > 0 } ?: (liveState.details?.level ?: 0)
    RealDischargeStepHistoryCard(liveState.stats, currentLevel)
}

@Composable
private fun BatteryHardwareLiveCard(uiState: LynxUiState, viewModel: LynxViewModel) {
    val liveState by viewModel.batteryInformationUiState.collectAsState()
    val currentLevel = liveState.stats.currentLevel.takeIf { it > 0 } ?: (liveState.details?.level ?: 0)
    BatteryHardwareCard(
        stats = liveState.stats,
        currentLevel = currentLevel,
        isCalibrating = uiState.isCalibratingBattery,
        onCalibrate = { viewModel.calibrateBatteryStats() },
        onOpenDetail = { viewModel.openBatteryDetailSheet() }
    )
}
