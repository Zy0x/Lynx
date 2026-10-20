package com.noir.lynx.ui

import android.content.Context
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.noir.lynx.data.AppProfileRule
import com.noir.lynx.data.DeepTunable
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.feature.cpu.ui.CpuSubscreen
import com.noir.lynx.feature.cpu.ui.tuningCpuCategoryItems as modularTuningCpuCategoryItems
import com.noir.lynx.feature.gpu.ui.GpuSubscreen
import com.noir.lynx.feature.gpu.ui.tuningGpuCategoryItems as modularTuningGpuCategoryItems
import com.noir.lynx.feature.thermal.ui.ThermalSubscreen
import com.noir.lynx.feature.memory.ui.MemorySubscreen
import com.noir.lynx.feature.network.ui.NetworkSubscreen
import com.noir.lynx.feature.audio.ui.AudioSubscreen
import com.noir.lynx.feature.system.ui.SystemSubscreen
import com.noir.lynx.feature.system.ui.DeepTunableItemCard as ModularDeepTunableItemCard

/**
 * Re-export facade for modularized Category Subscreens.
 * The concrete implementations now live in their respective feature packages:
 * - [com.noir.lynx.feature.cpu.ui.CpuSubscreen]
 * - [com.noir.lynx.feature.gpu.ui.GpuSubscreen]
 * - [com.noir.lynx.feature.thermal.ui.ThermalSubscreen]
 * - [com.noir.lynx.feature.memory.ui.MemorySubscreen]
 * - [com.noir.lynx.feature.network.ui.NetworkSubscreen]
 * - [com.noir.lynx.feature.system.ui.SystemSubscreen]
 */

@Composable
fun TuningCpuCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    CpuSubscreen(uiState = uiState, viewModel = viewModel, modifier = modifier)
}

fun LazyListScope.tuningCpuCategoryItems(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    targetTab: Int = uiState.selectedCpuTab
) {
    modularTuningCpuCategoryItems(uiState = uiState, viewModel = viewModel, context = context, targetTab = targetTab)
}

fun LazyListScope.tuningGpuCategoryItems(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    targetTab: Int = uiState.selectedGpuTab
) {
    modularTuningGpuCategoryItems(uiState = uiState, viewModel = viewModel, context = context, targetTab = targetTab)
}

@Composable
fun TuningGpuCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    GpuSubscreen(uiState = uiState, viewModel = viewModel, modifier = modifier)
}

@Composable
fun TuningGameHudCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    com.noir.lynx.feature.hud.ui.GameHudSubscreen(uiState = uiState, viewModel = viewModel, modifier = modifier)
}

@Composable
fun TuningThermalCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    ThermalSubscreen(uiState = uiState, viewModel = viewModel, modifier = modifier)
}

@Composable
fun TuningMemoryCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    MemorySubscreen(uiState = uiState, viewModel = viewModel, modifier = modifier)
}

@Composable
fun TuningNetworkCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    NetworkSubscreen(uiState = uiState, viewModel = viewModel, modifier = modifier)
}

@Composable
fun TuningAudioCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    AudioSubscreen(uiState = uiState, viewModel = viewModel, modifier = modifier)
}

@Composable
fun TuningSystemCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onAddAppClick: () -> Unit,
    onEditRuleClick: (AppProfileRule) -> Unit,
    modifier: Modifier = Modifier
) {
    SystemSubscreen(
        uiState = uiState,
        viewModel = viewModel,
        onAddAppClick = onAddAppClick,
        onEditRuleClick = onEditRuleClick,
        modifier = modifier
    )
}

@Composable
fun DeepTunableItemCard(
    tunable: DeepTunable,
    onApply: (String, String) -> Unit
) {
    ModularDeepTunableItemCard(tunable = tunable, onApply = onApply)
}
