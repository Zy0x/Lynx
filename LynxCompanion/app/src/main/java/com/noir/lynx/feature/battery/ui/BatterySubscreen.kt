package com.noir.lynx.feature.battery.ui
import com.noir.lynx.ui.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.theme.rememberFluidFlingBehavior
import com.noir.lynx.feature.battery.ChargingScreenLifecycle
import com.noir.lynx.ui.LynxViewModel

/**
 * Lazy list items helper for Battery & Charging Subscreen.
 */
fun LazyListScope.batterySubscreenItems(
    uiState: LynxUiState,
    viewModel: LynxViewModel
) {
    if (uiState.batterySubTab == 1) {
        batteryInformationItems(uiState, viewModel)
    } else {
        item(key = "charging_live_hero") {
            ChargingLiveTelemetryHeroCard(uiState)
        }
        item(key = "charging_section_fast") {
            BatterySectionHeader(
                title = "Pilihan Mode Pengisian Daya",
                subtitle = "Preset regulasi arus charger & batas panas pengisian",
                accentColor = com.noir.lynx.designsystem.theme.AccentCyan,
                badge = "4 MODE"
            )
        }
        item(key = "charging_fast_control") {
            FastChargingControlCard(uiState, viewModel)
        }
        item(key = "charging_protection") {
            BatteryProtectionCard(uiState, viewModel)
        }
    }
}

/**
 * Subscreen 4: Battery & Charging Composable
 */
@Composable
fun BatterySubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val lazyState = rememberLazyListState()
    val fling = rememberFluidFlingBehavior()

    ChargingScreenLifecycle(uiState, viewModel)

    Column(modifier = modifier.fillMaxWidth()) {
        BatterySubscreenTabRow(
            selectedTab = uiState.batterySubTab,
            onSelectTab = { viewModel.setBatterySubTab(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyColumn(
            state = lazyState,
            flingBehavior = fling,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "battery_top_spacer") {
                Spacer(Modifier.height(8.dp))
            }
            batterySubscreenItems(uiState, viewModel)
            item(key = "battery_bottom_spacer") {
                Spacer(Modifier.height(130.dp).navigationBarsPadding())
            }
        }
    }
}
