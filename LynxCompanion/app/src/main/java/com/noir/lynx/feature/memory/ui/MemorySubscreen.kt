package com.noir.lynx.feature.memory.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.applyLmkPreset
import com.noir.lynx.ui.refreshKsmStats
import com.noir.lynx.ui.setEntropyThresholds

/**
 * Subscreen 5: Memory & Storage I/O Composable
 * Modular 4-card architecture with anti-accidental-drag safety locks, staged ZRAM apply,
 * PSI/lmkd honesty, and discrete Read-Ahead controls.
 */
@Composable
fun MemorySubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        viewModel.refreshKsmStats()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MemoryHeroCard(uiState = uiState, viewModel = viewModel)

        MemoryVmZramCard(uiState = uiState, viewModel = viewModel)

        MemoryEntropyCard(
            memoryEntropy = uiState.memoryEntropy,
            onSetEntropy = { readThresh, writeThresh ->
                viewModel.setEntropyThresholds(readThresh, writeThresh)
            },
            onApplyLmkPreset = { preset ->
                viewModel.applyLmkPreset(preset)
            }
        )

        MountXStorageCard(uiState = uiState, viewModel = viewModel)
    }
}

/**
 * Backward compatibility alias for legacy call sites.
 */
@Composable
fun TuningMemoryCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    MemorySubscreen(uiState = uiState, viewModel = viewModel, modifier = modifier)
}
