package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.ui.HudSwipeableSubscreen
import com.noir.lynx.ui.LynxViewModel

/**
 * GameHudSubscreen — Entry point for Lynx Engine Subscreen.
 * Delegates directly to the modular, swipeable 52dp tab-based HudSwipeableSubscreen.
 */
@Composable
fun GameHudSubscreen(uiState: LynxUiState, viewModel: LynxViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val subscreenFling = ScrollableDefaults.flingBehavior()
    HudSwipeableSubscreen(
        uiState = uiState,
        viewModel = viewModel,
        context = context,
        subscreenFling = subscreenFling,
        modifier = modifier
    )
}
