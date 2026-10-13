package com.noir.lynx.feature.battery.ui
import com.noir.lynx.ui.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noir.lynx.feature.battery.ChargingScreenLifecycle
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.ui.LynxViewModel

@Composable
fun TuningChargingCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier,
    manageLifecycle: Boolean = true
) {
    if (manageLifecycle) {
        ChargingScreenLifecycle(uiState, viewModel)
    }

    val selectedTab = uiState.batterySubTab

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (selectedTab == 0) {
            FastChargingControlCard(uiState, viewModel)
            BatteryProtectionCard(uiState, viewModel)
        } else {
            BatteryInformationContent(uiState, viewModel)
        }
    }
}
