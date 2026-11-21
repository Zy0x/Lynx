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
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.AccentPurple
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
            BatterySectionHeader(
                title = "Regulasi Arus & Pengisian Cepat",
                subtitle = "Preset arus charger, wattmeter ganda & telemetri gelombang",
                accentColor = AccentCyan,
                badge = "4 MODE"
            )
            FastChargingControlCard(uiState, viewModel)
            BatterySectionHeader(
                title = "Proteksi Sel & Bypass Hardware",
                subtitle = "Isolasi arus baterai, batas auto-cut & pengisian malam",
                accentColor = AccentPurple,
                badge = "SAFETY"
            )
            BatteryProtectionCard(uiState, viewModel)
        } else {
            BatteryInformationContent(uiState, viewModel)
        }
    }
}
