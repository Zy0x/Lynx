package com.noir.lynx.ui

import android.content.Context
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noir.lynx.data.AppProfileRule
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.controls.LynxTabPager
import com.noir.lynx.feature.battery.ChargingScreenLifecycle
import com.noir.lynx.feature.battery.ui.BatteryProtectionCard
import com.noir.lynx.feature.battery.ui.BatterySubscreenTabRow
import com.noir.lynx.feature.battery.ui.FastChargingControlCard
import com.noir.lynx.feature.battery.ui.batteryInformationItems
import com.noir.lynx.feature.cpu.ui.CpuTabRow
import com.noir.lynx.feature.cpu.ui.tuningCpuCategoryItems
import com.noir.lynx.feature.gpu.ui.tuningGpuCategoryItems
import com.noir.lynx.feature.network.ui.NetworkAudioTabRow
import com.noir.lynx.feature.network.ui.TuningNetworkCategory

@Composable
fun CpuSwipeableSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    subscreenFling: FlingBehavior,
    modifier: Modifier = Modifier
) {
    val page0State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page1State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page2State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LynxTabPager(
        pageCount = 3,
        selectedPage = uiState.selectedCpuTab,
        onPageSelected = { viewModel.selectCpuTab(it) },
        tabRow = { curPage, onSelect ->
            CpuTabRow(
                selectedTab = curPage,
                onSelectTab = onSelect,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        },
        modifier = modifier
    ) { page ->
        val listState = when (page) {
            0 -> page0State
            1 -> page1State
            else -> page2State
        }
        LazyColumn(
            state = listState,
            flingBehavior = subscreenFling,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "cpu_p${page}_top") { Spacer(Modifier.height(8.dp)) }
            tuningCpuCategoryItems(uiState, viewModel, context, targetTab = page)
            item(key = "cpu_p${page}_bottom") { Spacer(modifier = Modifier.height(130.dp).navigationBarsPadding()) }
        }
    }
}

@Composable
fun GpuSwipeableSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    subscreenFling: FlingBehavior,
    modifier: Modifier = Modifier
) {
    val page0State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page1State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page2State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LaunchedEffect(Unit) {
        viewModel.refreshGpuInfo()
        viewModel.refreshGraphicsHwui()
        viewModel.refreshDisplayRefreshRate()
        viewModel.refreshDisplayCalibration()
        viewModel.refreshGraphicsCapabilities()
        viewModel.refreshDisplayPipeline()
        viewModel.refreshColorConflict()
        viewModel.refreshSavedLabSessions(context)
        viewModel.loadPerAppGraphicsRules(context)
        viewModel.refreshInstalledApps()
    }

    LynxTabPager(
        pageCount = 3,
        selectedPage = uiState.selectedGpuTab,
        onPageSelected = { viewModel.selectGpuTab(it) },
        tabRow = { curPage, onSelect ->
            GpuDisplayTabRow(
                selectedTab = curPage,
                onSelectTab = onSelect,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        },
        modifier = modifier
    ) { page ->
        val listState = when (page) {
            0 -> page0State
            1 -> page1State
            else -> page2State
        }
        LazyColumn(
            state = listState,
            flingBehavior = subscreenFling,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "gpu_p${page}_top") { Spacer(Modifier.height(8.dp)) }
            tuningGpuCategoryItems(uiState, viewModel, context, targetTab = page)
            item(key = "gpu_p${page}_bottom") { Spacer(modifier = Modifier.height(130.dp).navigationBarsPadding()) }
        }
    }
}

@Composable
fun BatterySwipeableSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    subscreenFling: FlingBehavior,
    modifier: Modifier = Modifier
) {
    val page0State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page1State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    ChargingScreenLifecycle(uiState, viewModel)
    LynxTabPager(
        pageCount = 2,
        selectedPage = uiState.batterySubTab,
        onPageSelected = { viewModel.setBatterySubTab(it) },
        tabRow = { curPage, onSelect ->
            BatterySubscreenTabRow(
                selectedTab = curPage,
                onSelectTab = onSelect,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        },
        modifier = modifier
    ) { page ->
        val listState = if (page == 0) page0State else page1State
        LazyColumn(
            state = listState,
            flingBehavior = subscreenFling,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "charging_p${page}_top") { Spacer(Modifier.height(8.dp)) }
            if (page == 0) {
                item(key = "charging_fast_control") {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        FastChargingControlCard(uiState, viewModel)
                    }
                }
                item(key = "charging_protection") {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        BatteryProtectionCard(uiState, viewModel)
                    }
                }
            } else {
                batteryInformationItems(uiState, viewModel)
            }
            item(key = "charging_p${page}_bottom") { Spacer(modifier = Modifier.height(130.dp).navigationBarsPadding()) }
        }
    }
}

@Composable
fun ProfilesSwipeableSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    subscreenFling: FlingBehavior,
    onAddAppClick: () -> Unit,
    onEditRuleClick: (AppProfileRule) -> Unit,
    onExtremeConfirmRequired: () -> Unit,
    modifier: Modifier = Modifier
) {
    val page0State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LynxTabPager(
        pageCount = 2,
        selectedPage = uiState.selectedProfileTab,
        onPageSelected = { viewModel.selectProfileTab(it) },
        tabRow = { curPage, onSelect ->
            ProfilesCategoryTabRow(
                selectedTab = curPage,
                onSelectTab = onSelect,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        },
        modifier = modifier
    ) { page ->
        if (page == 0) {
            LazyColumn(
                state = page0State,
                flingBehavior = subscreenFling,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "profiles_top") { Spacer(Modifier.height(8.dp)) }
                tuningProfilesCategoryItems(
                    uiState = uiState,
                    viewModel = viewModel,
                    context = context,
                    onAddAppClick = {
                        viewModel.refreshInstalledApps()
                        onAddAppClick()
                    },
                    onEditRuleClick = { onEditRuleClick(it) },
                    onExtremeConfirmRequired = onExtremeConfirmRequired
                )
                item(key = "profiles_bottom") { Spacer(modifier = Modifier.height(130.dp).navigationBarsPadding()) }
            }
        } else {
            ProfilesAutomationScreen(
                uiState = uiState,
                viewModel = viewModel,
                context = context,
                onAddAppClick = {
                    viewModel.refreshInstalledApps()
                    onAddAppClick()
                },
                onEditRuleClick = { onEditRuleClick(it) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun NetworkAudioSwipeableSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    subscreenFling: FlingBehavior,
    modifier: Modifier = Modifier
) {
    val page0State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page1State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LynxTabPager(
        pageCount = 2,
        selectedPage = uiState.selectedNetworkTab,
        onPageSelected = { viewModel.selectNetworkTab(it) },
        tabRow = { curPage, onSelect ->
            NetworkAudioTabRow(
                selectedTab = curPage,
                onSelectTab = onSelect,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        },
        modifier = modifier
    ) { page ->
        val listState = if (page == 0) page0State else page1State
        LazyColumn(
            state = listState,
            flingBehavior = subscreenFling,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "net_audio_p${page}_top") { Spacer(Modifier.height(8.dp)) }
            if (page == 0) {
                item(key = "subscreen_network") { TuningNetworkCategory(uiState = uiState, viewModel = viewModel) }
            } else {
                item(key = "subscreen_audio") { TuningAudioCategory(uiState = uiState, viewModel = viewModel) }
            }
            item(key = "net_audio_p${page}_bottom") { Spacer(modifier = Modifier.height(130.dp).navigationBarsPadding()) }
        }
    }
}
