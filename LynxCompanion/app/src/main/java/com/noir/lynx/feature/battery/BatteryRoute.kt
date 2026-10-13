package com.noir.lynx.feature.battery
import com.noir.lynx.ui.*

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import com.noir.lynx.feature.battery.ui.TuningChargingCategory
import com.noir.lynx.feature.battery.ui.dialogs.BatteryDetailBottomSheet
import com.noir.lynx.feature.battery.ui.dialogs.CustomCurrentLimitDialog
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.ui.LynxViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingScreenLifecycle(
    uiState: LynxUiState,
    viewModel: LynxViewModel
) {
    val state = uiState.state
    val battDetails = uiState.batteryDetails
    SideEffect {
        viewModel.updateBatteryInformationScreenVisibility(uiState.batterySubTab == 1)
    }
    LaunchedEffect(Unit) {
        viewModel.isBatteryScreenActive = true
        try {
            viewModel.refreshBatteryDetails(forceDeepStats = true)
            while (isActive) {
                delay(1000L)
                viewModel.refreshBatteryDetails(forceDeepStats = false)
            }
        } finally {
            viewModel.updateBatteryInformationScreenVisibility(false)
            viewModel.isBatteryScreenActive = false
        }
    }

    // Custom Current Limit Dialog
    if (uiState.isCustomCurrentDialogOpen) {
        CustomCurrentLimitDialog(
            currentLimitMa = state.charging.limitCurrentMa,
            onConfirm = { viewModel.setCustomCurrentLimit(it) },
            onDismiss = { viewModel.closeCustomCurrentDialog() }
        )
    }

    // Deep Telemetry Modal Bottom Sheet
    if (uiState.isBatteryDetailSheetOpen && battDetails != null) {
        BatteryDetailBottomSheet(
            battDetails = battDetails,
            onDismiss = { viewModel.closeBatteryDetailSheet() }
        )
    }
}

@Composable
fun BatteryRoute(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    TuningChargingCategory(
        uiState = uiState,
        viewModel = viewModel,
        modifier = modifier,
        manageLifecycle = true
    )
}
