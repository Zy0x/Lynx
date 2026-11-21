package com.noir.lynx.feature.memory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.applyLmkPreset
import com.noir.lynx.ui.loadMemorySubsystemData
import com.noir.lynx.ui.refreshMountXStorageInfo
import com.noir.lynx.ui.setCustomLmkMinfreeMb
import com.noir.lynx.ui.setEntropyThresholds

/**
 * MemorySubscreen — Modular domain router for Memory & Storage:
 * - targetTab == 0: RAM & ZRAM (MemoryHeroCard + MemoryVmZramCard)
 * - targetTab == 1: VM & Entropi (MemoryVmAdvancedCard + MemoryEntropyCard)
 * - targetTab == 2: Storage & I/O (MountXStorageCard)
 * - targetTab == null: Stacked compatibility fallback (all cards)
 */
@Composable
fun MemorySubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    targetTab: Int? = null,
    modifier: Modifier = Modifier
) {
    // Only self-load when used standalone (targetTab == null); the tabbed pager loads once at screen entry.
    if (targetTab == null) {
        LaunchedEffect(Unit) {
            viewModel.loadMemorySubsystemData(forceRefresh = true)
            viewModel.refreshMountXStorageInfo()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (targetTab == null || targetTab == 0) {
            MemoryHeroCard(uiState = uiState, viewModel = viewModel)
            MemoryVmZramCard(
                uiState = uiState,
                viewModel = viewModel
            )
        }

        if (targetTab == null || targetTab == 1) {
            MemoryVmAdvancedCard(
                uiState = uiState,
                viewModel = viewModel
            )
            MemoryEntropyCard(
                memoryEntropy = uiState.memoryEntropy,
                onApplyEntropy = { readThresh, writeThresh ->
                    viewModel.setEntropyThresholds(readThresh, writeThresh)
                },
                onApplyLmkPreset = { preset ->
                    viewModel.applyLmkPreset(preset)
                },
                onApplyCustomLmkMb = { tiersMb ->
                    viewModel.setCustomLmkMinfreeMb(tiersMb)
                }
            )
        }

        if (targetTab == null || targetTab == 2) {
            MountXStorageCard(
                uiState = uiState,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun TuningMemoryCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    MemorySubscreen(uiState = uiState, viewModel = viewModel, targetTab = null, modifier = modifier)
}
