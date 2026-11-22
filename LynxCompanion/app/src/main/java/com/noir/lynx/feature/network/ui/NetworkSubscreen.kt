package com.noir.lynx.feature.network.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.refreshAdbConfig
import com.noir.lynx.ui.refreshNetworkEngineAll

/**
 * Subscreen 6: Dedicated 3-Tab Wi-Fi & Network Engine Composable:
 * - targetTab == 0: Monitor & Radio (NetworkHeroCard + NetworkPresetCard + RadioEngineCard)
 * - targetTab == 1: Kernel & TCP (KernelNetworkStackCard)
 * - targetTab == 2: DNS & ADB (PrivateDnsCard + AdbKeepAliveCard)
 * - targetTab == null: Stacked compatibility fallback (all cards)
 */
@Composable
fun NetworkSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    targetTab: Int? = null,
    modifier: Modifier = Modifier
) {
    if (targetTab == null) {
        LaunchedEffect(Unit) {
            viewModel.refreshNetworkEngineAll(runPing = true)
            viewModel.refreshAdbConfig()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (targetTab == null || targetTab == 0) {
            NetworkHeroCard(uiState = uiState, viewModel = viewModel)
            NetworkPresetCard(uiState = uiState, viewModel = viewModel)
            RadioEngineCard(uiState = uiState, viewModel = viewModel)
        }

        if (targetTab == null || targetTab == 1) {
            KernelNetworkStackCard(uiState = uiState, viewModel = viewModel)
        }

        if (targetTab == null || targetTab == 2) {
            PrivateDnsCard(uiState = uiState, viewModel = viewModel)
            AdbKeepAliveCard(uiState = uiState, viewModel = viewModel)
        }
    }
}

/**
 * Backward compatibility alias for call sites referencing TuningNetworkCategory.
 */
@Composable
fun TuningNetworkCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    NetworkSubscreen(uiState = uiState, viewModel = viewModel, targetTab = null, modifier = modifier)
}
