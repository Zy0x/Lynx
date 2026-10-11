package com.noir.lynx.feature.battery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
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
        BatteryRuntimeCard(uiState)
        val stats = uiState.batteryInfoStats
        RealDischargeStepHistoryCard(stats, batteryCurrentLevel(uiState))
        BatteryHardwareCard(uiState, viewModel)
    }
}

// Stable card keys let the list skip off-screen draw trees and retain its anchor.
fun LazyListScope.batteryInformationItems(uiState: LynxUiState, viewModel: LynxViewModel) {
    item(key = "battery_runtime") { BatteryRuntimeCard(uiState) }
    item(key = "battery_history") {
        RealDischargeStepHistoryCard(uiState.batteryInfoStats, batteryCurrentLevel(uiState))
    }
    item(key = "battery_hardware") { BatteryHardwareCard(uiState, viewModel) }
}
