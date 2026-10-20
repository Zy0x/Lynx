package com.noir.lynx.ui

import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*
import com.noir.lynx.feature.battery.*
import com.noir.lynx.feature.battery.ui.*

import android.os.Bundle
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.util.VelocityTrackerAddPointsFix
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.Canvas
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.data.DeepTunable
import com.noir.lynx.data.TunableType
import com.noir.lynx.data.TunableOption
import com.noir.lynx.data.AppProfileRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.noir.lynx.feature.cpu.ui.*
import com.noir.lynx.feature.gpu.ui.*
import com.noir.lynx.feature.thermal.ui.*
import com.noir.lynx.feature.telemetry.ui.*
import com.noir.lynx.feature.battery.ui.*
import com.noir.lynx.feature.memory.ui.*
import com.noir.lynx.feature.network.ui.*
import com.noir.lynx.feature.system.ui.*
import com.noir.lynx.feature.navigation.model.TuningCategory
import com.noir.lynx.designsystem.profile.AddAppRuleDialog
import com.noir.lynx.designsystem.profile.EditAppRuleDialog

@Composable
fun MainDashboard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onExtremeConfirmRequired: () -> Unit,
) {
    val state = uiState.state
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val dashboardLazyState = rememberLazyListState()
    val toolsLazyState = rememberLazyListState()
    val tuningHubLazyState = rememberLazyListState()
    val subscreenLazyState = rememberLazyListState()

    val dashboardFling = rememberFluidFlingBehavior()
    val tuningHubFling = rememberFluidFlingBehavior()
    val toolsFling = rememberFluidFlingBehavior()
    val subscreenFling = rememberFluidFlingBehavior()
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(uiState.selectedTuningCategory) }

    LaunchedEffect(uiState.selectedTuningCategory) {
        if (uiState.selectedTuningCategory != selectedCategory) {
            selectedCategory = uiState.selectedTuningCategory
        }
    }

    var selectedToolsCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val toolsHubLazyState = rememberLazyListState()
    val toolsSubscreenLazyState = rememberLazyListState()
    val toolsHubFling = rememberFluidFlingBehavior()
    val toolsSubscreenFling = rememberFluidFlingBehavior()

    LaunchedEffect(Unit) {
        viewModel.syncHudPrefs(context)
    }
    BackHandler(enabled = uiState.currentTab == 1 && selectedCategory != null) {
        android.util.Log.i("LynxNavigation", "system-back category=$selectedCategory scrolling=${subscreenLazyState.isScrollInProgress}")
        selectedCategory = null
        viewModel.setSelectedTuningCategory(null)
    }
    BackHandler(enabled = (uiState.currentTab == 2 || uiState.currentTab == 3) && selectedToolsCategory != null) {
        android.util.Log.i("LynxNavigation", "system-back toolsCategory=$selectedToolsCategory")
        selectedToolsCategory = null
    }
    LaunchedEffect(selectedToolsCategory) {
        toolsSubscreenLazyState.scrollToItem(0)
    }
    LaunchedEffect(selectedCategory) {
        subscreenLazyState.scrollToItem(0)
        if (selectedCategory == "gpu") {
            viewModel.refreshGpuInfo()
            viewModel.refreshGraphicsCapabilities()
            viewModel.refreshDisplayRefreshRate()
        }
    }
    var lastCpuTab by rememberSaveable { mutableStateOf(uiState.selectedCpuTab) }
    var lastGpuTab by rememberSaveable { mutableStateOf(uiState.selectedGpuTab) }
    var lastBattTab by rememberSaveable { mutableStateOf(uiState.batterySubTab) }
    var lastProfileTab by rememberSaveable { mutableStateOf(uiState.selectedProfileTab) }
    var lastNetTab by rememberSaveable { mutableStateOf(uiState.selectedNetworkTab) }
    LaunchedEffect(uiState.selectedCpuTab, uiState.selectedGpuTab, uiState.batterySubTab, uiState.selectedProfileTab, uiState.selectedNetworkTab) {
        if (uiState.selectedCpuTab != lastCpuTab || uiState.selectedGpuTab != lastGpuTab || uiState.batterySubTab != lastBattTab || uiState.selectedProfileTab != lastProfileTab || uiState.selectedNetworkTab != lastNetTab) {
            lastCpuTab = uiState.selectedCpuTab
            lastGpuTab = uiState.selectedGpuTab
            lastBattTab = uiState.batterySubTab
            lastProfileTab = uiState.selectedProfileTab
            lastNetTab = uiState.selectedNetworkTab
            subscreenLazyState.scrollToItem(0)
        }
    }
    var showAddAppDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<AppProfileRule?>(null) }
    var selectedTargetProfile by remember { mutableStateOf("performance") }

    val currentAccent = when (uiState.selectedAccent) {
        "blue" -> AccentBlue
        "purple" -> AccentPurple
        "orange" -> AccentOrange
        "red" -> AccentRed
        "green" -> AccentGreen
        else -> AccentCyan
    }

    // ── Edit App Profile Rule Dialog (Modular Zen) ──────────
    if (editingRule != null) {
        val rule = editingRule!!
        val isExistingRule = remember(rule, uiState.appProfileRules) {
            uiState.appProfileRules.any { it.packageName == rule.packageName }
        }
        EditAppRuleDialog(
            rule = rule,
            isExistingRule = isExistingRule,
            supportedRefreshRates = uiState.supportedRefreshRates,
            onDismiss = { editingRule = null },
            onSave = { updatedRule ->
                viewModel.addOrUpdateAppProfileRule(context, updatedRule)
                editingRule = null
            },
            onDelete = { pkg ->
                viewModel.deleteAppProfileRule(context, pkg)
                viewModel.removeAppFromPerf(pkg)
                editingRule = null
            }
        )
    }

    // ── Add App Profile Rule Dialog (Modular Zen) ───────────
    if (showAddAppDialog) {
        AddAppRuleDialog(
            installedApps = uiState.installedAppList,
            configuredRules = uiState.appProfileRules,
            defaultTargetProfile = selectedTargetProfile,
            onDismiss = { showAddAppDialog = false },
            onSelectApp = { newRule ->
                editingRule = newRule
                showAddAppDialog = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeepOled)
    ) {
        // ── 1. Full Edge-to-Edge Virtualized Content (Glides behind Navbar) ────
        when (uiState.currentTab) {
            // ── TAB 0: DASHBOARD ────────────────────────────────────────
            0 -> DashboardTabContent(
                uiState = uiState,
                viewModel = viewModel,
                currentAccent = currentAccent,
                dashboardLazyState = dashboardLazyState,
                dashboardFling = dashboardFling,
                onExtremeConfirmRequired = onExtremeConfirmRequired,
                onNavigateToProfilesCatalog = {
                    android.util.Log.i("LynxNavigation", "navigating to profiles catalog")
                    selectedCategory = "profiles"
                    viewModel.setSelectedTuningCategory("profiles")
                    viewModel.switchTab(1)
                }
            )
            // ── TAB 1: TUNING HUB ────────────────────────────────────────
            1 -> TuningTabContent(
                uiState = uiState,
                viewModel = viewModel,
                selectedCategory = selectedCategory,
                onSelectedCategoryChange = {
                    selectedCategory = it
                    viewModel.setSelectedTuningCategory(it)
                },
                tuningHubLazyState = tuningHubLazyState,
                tuningHubFling = tuningHubFling,
                subscreenLazyState = subscreenLazyState,
                subscreenFling = subscreenFling,
                onAddAppClick = { showAddAppDialog = true },
                onEditRuleClick = { editingRule = it },
                onExtremeConfirmRequired = onExtremeConfirmRequired
            )

            // ── TAB 2 & 3: TOOLS & FLASHER (CATEGORIZED TOOLS & LAB HUB) ─
            2, 3 -> ToolsTabContent(
                uiState = uiState,
                viewModel = viewModel,
                selectedCategory = selectedToolsCategory,
                onSelectedCategoryChange = { selectedToolsCategory = it },
                toolsHubLazyState = toolsHubLazyState,
                toolsHubFling = toolsHubFling,
                toolsSubscreenLazyState = toolsSubscreenLazyState,
                toolsSubscreenFling = toolsSubscreenFling,
                onNavigateToTuning = { targetCategory ->
                    selectedCategory = targetCategory
                    viewModel.setSelectedTuningCategory(targetCategory)
                    viewModel.switchTab(1)
                }
            )
        }

        // ── 2. Subtle Bottom Atmospheric Vignette (Soft content pass-through) ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(115.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            BgDeepOled.copy(alpha = 0.55f),
                            BgDeepOled.copy(alpha = 0.92f)
                        )
                    )
                )
        )

        // ── 3. Model 1: Floating Dynamic Capsule Dock (True Overlay) ────────
        LynxBottomNavigationDock(uiState, currentAccent) { index -> viewModel.switchTab(index) }
    }
}
