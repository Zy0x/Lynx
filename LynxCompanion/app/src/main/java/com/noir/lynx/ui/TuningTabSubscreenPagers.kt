package com.noir.lynx.ui

import android.content.Context
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.noir.lynx.data.AppProfileRule
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.controls.LynxTabPager
import com.noir.lynx.feature.audio.ui.AudioSubscreen
import com.noir.lynx.feature.audio.ui.AudioTabRow
import com.noir.lynx.feature.battery.ChargingScreenLifecycle
import com.noir.lynx.feature.battery.ui.BatteryProtectionCard
import com.noir.lynx.feature.battery.ui.BatterySubscreenTabRow
import com.noir.lynx.feature.battery.ui.FastChargingControlCard
import com.noir.lynx.feature.battery.ui.batteryInformationItems
import com.noir.lynx.feature.cpu.ui.CpuTabRow
import com.noir.lynx.feature.cpu.ui.tuningCpuCategoryItems
import com.noir.lynx.feature.gpu.ui.GpuDisplayTabRow
import com.noir.lynx.feature.gpu.ui.tuningGpuCategoryItems
import com.noir.lynx.feature.memory.ui.MemoryStorageTabRow
import com.noir.lynx.feature.memory.ui.MemorySubscreen
import com.noir.lynx.feature.network.ui.NetworkTabRow
import com.noir.lynx.feature.network.ui.NetworkSubscreen
import com.noir.lynx.feature.network.ui.TuningNetworkCategory

@Composable
fun MemoryStorageSwipeableSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    subscreenFling: FlingBehavior,
    modifier: Modifier = Modifier
) {
    val page0State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page1State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page2State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LaunchedEffect(Unit) {
        viewModel.loadMemorySubsystemData(forceRefresh = true)
        viewModel.refreshMountXStorageInfo()
        viewModel.refreshIoDevices(forceRefresh = true)
    }

    LynxTabPager(
        pageCount = 3,
        selectedPage = uiState.selectedMemoryTab,
        onPageSelected = { viewModel.setSelectedMemoryTab(it) },
        tabRow = { curPage, onSelect ->
            MemoryStorageTabRow(
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
            item(key = "memory_spacer_$page") { Spacer(Modifier.height(4.dp)) }
            item(key = "memory_content_$page") {
                MemorySubscreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    targetTab = page
                )
            }
            item(key = "memory_bottom_spacer_$page") { Spacer(modifier = Modifier.height(130.dp).navigationBarsPadding()) }
        }
    }
}

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
    val page3State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LaunchedEffect(Unit) {
        viewModel.refreshGpuInfo()
        viewModel.refreshGraphicsHwui()
        viewModel.refreshDisplayRefreshRate()
        viewModel.refreshDisplayCalibration()
        viewModel.refreshGraphicsCapabilities()
        viewModel.refreshDisplayPipeline()
        viewModel.refreshSavedLabSessions(context)
        viewModel.loadPerAppGraphicsRules(context)
        viewModel.refreshInstalledApps()
    }

    LynxTabPager(
        pageCount = 4,
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
            2 -> page2State
            else -> page3State
        }
        LazyColumn(
            state = listState,
            flingBehavior = subscreenFling,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "gpu_p${page}_top") { Spacer(Modifier.height(8.dp)) }
            tuningGpuCategoryItems(
                uiState = uiState,
                viewModel = viewModel,
                context = context,
                targetTab = page
            )
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
            item(key = "charging_p${page}_top") { Spacer(Modifier.height(4.dp)) }
            if (page == 0) {
                item(key = "charging_live_hero") {
                    com.noir.lynx.feature.battery.ui.ChargingLiveTelemetryHeroCard(uiState)
                }
                item(key = "charging_section_fast") {
                    com.noir.lynx.feature.battery.ui.BatterySectionHeader(
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
fun NetworkSwipeableSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    subscreenFling: FlingBehavior,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        viewModel.refreshNetworkEngineAll(runPing = true)
        viewModel.refreshAdbConfig()
    }

    val page0State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page1State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page2State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LynxTabPager(
        pageCount = 3,
        selectedPage = uiState.selectedNetworkTab.coerceIn(0, 2),
        onPageSelected = { viewModel.selectNetworkTab(it) },
        tabRow = { curPage, onSelect ->
            NetworkTabRow(
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
            item(key = "net_p${page}_top") { Spacer(Modifier.height(8.dp)) }
            item(key = "subscreen_network_tab_$page") {
                NetworkSubscreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    targetTab = page
                )
            }
            item(key = "net_p${page}_bottom") { Spacer(modifier = Modifier.height(130.dp).navigationBarsPadding()) }
        }
    }
}

@Composable
fun AudioSwipeableSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    subscreenFling: FlingBehavior,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        viewModel.refreshAudioSubsystem()
    }

    var selectedAudioTab by rememberSaveable { mutableIntStateOf(0) }
    val page0State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page1State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page2State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LynxTabPager(
        pageCount = 3,
        selectedPage = selectedAudioTab.coerceIn(0, 2),
        onPageSelected = { selectedAudioTab = it },
        tabRow = { curPage, onSelect ->
            AudioTabRow(
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
            item(key = "audio_p${page}_top") { Spacer(Modifier.height(4.dp)) }
            item(key = "subscreen_audio_tab_$page") {
                AudioSubscreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    targetTab = page
                )
            }
            item(key = "audio_p${page}_bottom") { Spacer(modifier = Modifier.height(130.dp).navigationBarsPadding()) }
        }
    }
}

// ============================================================
//  Lynx Engine (HUD) Subscreen Pager (Swipeable Tab-Based)
// ============================================================

@Composable
fun HudSwipeableSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    context: Context,
    subscreenFling: FlingBehavior,
    modifier: Modifier = Modifier
) {
    val store = remember(context) { com.noir.lynx.feature.hud.data.HudPreferencesStore(context) }
    val prefs by store.changes.collectAsState(initial = store.read())
    var selectedHudTab by rememberSaveable { mutableIntStateOf(0) }
    val page0State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page1State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page2State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val page3State = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Hero Master Switch Card (Always visible at the top)
        com.noir.lynx.feature.hud.ui.EngineHeroMasterCard(
            isActive = uiState.isGameHudActive,
            hudMode = uiState.hudMode,
            onToggle = { viewModel.toggleGameHud(context, it) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LynxTabPager(
            pageCount = 4,
            selectedPage = selectedHudTab,
            onPageSelected = { selectedHudTab = it },
            tabRow = { curPage, onSelect ->
                com.noir.lynx.feature.hud.ui.EngineTabRow(
                    selectedTab = curPage,
                    onSelectTab = onSelect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { page ->
            val listState = when (page) {
                0 -> page0State
                1 -> page1State
                2 -> page2State
                else -> page3State
            }
            LazyColumn(
                state = listState,
                flingBehavior = subscreenFling,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "hud_p${page}_top") { Spacer(Modifier.height(4.dp)) }
                when (page) {
                    0 -> item(key = "tab_engine_appearance") {
                        com.noir.lynx.feature.hud.ui.EngineAppearanceCard(
                            uiState = uiState,
                            viewModel = viewModel,
                            context = context
                        )
                    }
                    1 -> item(key = "tab_engine_metrics") {
                        com.noir.lynx.feature.hud.ui.EngineMetricsCard(
                            prefs = prefs,
                            store = store
                        )
                    }
                    2 -> item(key = "tab_engine_floating") {
                        com.noir.lynx.feature.hud.ui.EngineFloatingCard(
                            prefs = prefs,
                            store = store
                        )
                    }
                    3 -> item(key = "tab_engine_recorder") {
                        com.noir.lynx.feature.hud.ui.EngineRecorderCard(
                            context = context
                        )
                    }
                }
                item(key = "hud_p${page}_bottom") {
                    Spacer(modifier = Modifier.height(130.dp).navigationBarsPadding())
                }
            }
        }
    }
}

