package com.noir.lynx.ui

import com.noir.lynx.core.locale.localizedUiText

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


/**
 * MainActivity — Lynx Kernel Manager Native App.
 *
 * Full-screen Compose UI with Luxury Cyberpunk OLED Material 3 Expressive theme.
 * Dual-Mode Root support: operates standalone on any rooted device,
 * and unlocks enhanced background features when the Lynx root module is present.
 *
 * 4-Tab Luxury Navigation:
 *   1. Dashboard (Hardware Gauges, CPU Clusters Matrix, Bento Telemetry, Quick Profiles)
 *   2. SoC Tuner (Dynamic CPU Clusters, Min/Max Frequency Sliders, Governors, Thermal Limits)
 *   3. Lynx Engine (ZRAM, Charging Bypass, Audio MMAP, Network BBR, OEM Neutralizer)
 *   4. Tools & Flasher (AnyKernel3 Flasher, Boot Backup/Restore, Storage TRIM, CCleaner)
 */

@Composable
fun TuningTabContent(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    selectedCategory: String?,
    onSelectedCategoryChange: (String?) -> Unit,
    tuningHubLazyState: androidx.compose.foundation.lazy.LazyListState,
    tuningHubFling: FlingBehavior,
    subscreenLazyState: androidx.compose.foundation.lazy.LazyListState,
    subscreenFling: FlingBehavior,
    onAddAppClick: () -> Unit,
    onEditRuleClick: (AppProfileRule) -> Unit,
    onExtremeConfirmRequired: () -> Unit = {},
) {
    val state = uiState.state
    val context = LocalContext.current
    var showProfilesFeedbackSheet by rememberSaveable { mutableStateOf(false) }
                    AnimatedContent(
                        targetState = selectedCategory,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            if (targetState != null) {
                                (slideInHorizontally(animationSpec = tween(220)) { it / 3 } + fadeIn(animationSpec = tween(200)))
                                    .togetherWith(slideOutHorizontally(animationSpec = tween(200)) { -it / 3 } + fadeOut(animationSpec = tween(150)))
                            } else {
                                (slideInHorizontally(animationSpec = tween(220)) { -it / 3 } + fadeIn(animationSpec = tween(200)))
                                    .togetherWith(slideOutHorizontally(animationSpec = tween(200)) { it / 3 } + fadeOut(animationSpec = tween(150)))
                            }
                        },
                        label = "TuningHubTransition"
                    ) { currentCategory ->
                        if (currentCategory == null) {
                            // ── HUB VIEW (FULL-WIDTH SUBSYSTEM CARDS) ──
                            LazyColumn(
                                state = tuningHubLazyState,
                                flingBehavior = tuningHubFling,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                            ) {
                                item(key = "hub_header") {
                                    Spacer(Modifier.height(8.dp))
                                    if (!uiState.isModuleInstalled) {
                                        StandaloneModuleBanner(
                                            onInstallModule = {
                                                viewModel.installLynxModule("/data/local/tmp/Lynx.zip")
                                            }
                                        )
                                    }

                                    // Flat Typographic Hub Header (Minimalist & Clean)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            "Pusat Tuning",
                                            color = TextPrimary,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = (-0.3).sp
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            "Pilih modul subsistem untuk konfigurasi hardware & kernel",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                                item(key = "hub_cards") {
                                    // Vertical List of Full-width Subsystem Cards
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // 0. Kernel Performance Profiles (Preset Catalog & Per-App Automation)
                                        SubsystemCategoryCard(
                                            category = TuningCategory.PROFILES,
                                            badgeText = state.activeProfile.uppercase(),
                                            detailText1 = "${uiState.kernelProfileCatalog.profiles.size} Profil",
                                            detailText2 = "${uiState.appProfileRules.size} Aturan App",
                                            onClick = { onSelectedCategoryChange("profiles") }
                                        )

                                        // 1. Floating Game HUD & OSD
                                        val hudStyleName = when (uiState.hudStyle) {
                                            1 -> "RTSS Pillar"
                                            2 -> "Top Ribbon"
                                            3 -> "Dual Esport"
                                            4 -> "Quad Tiles"
                                            5 -> "Steam Deck"
                                            6 -> "Ghost Text"
                                            else -> "RTSS Pillar"
                                        }
                                        val hudModeName = if (uiState.hudMode == 0) "Edge Drawer" else "Floating Window"
                                        SubsystemCategoryCard(
                                            category = TuningCategory.HUD,
                                            badgeText = if (uiState.isGameHudActive) "AKTIF" else "NONAKTIF",
                                            detailText1 = hudStyleName,
                                            detailText2 = hudModeName,
                                            onClick = { onSelectedCategoryChange("hud") }
                                        )

                                        // 1. CPU & Governor
                                        SubsystemCategoryCard(
                                            category = TuningCategory.CPU,
                                            badgeText = "${uiState.cpuCores.count { it.isOnline }}/${uiState.cpuCores.size} Cores",
                                            detailText1 = "${uiState.clusters.size} Cluster",
                                            detailText2 = uiState.activeGovernorPreset.ifBlank { "Schedutil" },
                                            onClick = { onSelectedCategoryChange("cpu") }
                                        )

                                        // 2. GPU & Display
                                        SubsystemCategoryCard(
                                            category = TuningCategory.GPU,
                                            badgeText = if (uiState.gpuInfo.curFreqMhz > 0) "${uiState.gpuInfo.curFreqMhz} MHz" else "Dynamic",
                                            detailText1 = "Load ${uiState.gpuInfo.gpuLoadPercent}%",
                                            detailText2 = "${uiState.displayRefreshRate}Hz",
                                            onClick = { onSelectedCategoryChange("gpu") }
                                        )

                                        // 3. Thermal & Anti-Throttling
                                        val validThermalZones = uiState.thermalZones.filter { it.tempC in 15f..115f }
                                        val maxT = validThermalZones.maxOfOrNull { it.tempC } ?: (uiState.telemetry?.temp?.toFloat() ?: 0f)
                                        SubsystemCategoryCard(
                                            category = TuningCategory.THERMAL,
                                            badgeText = if (state.thermal.fullBypass) "BYPASS AKTIF" else "${state.thermal.customTempLimitC}°C LIMIT",
                                            detailText1 = if (maxT > 0) "Maks ${String.format("%.1f", maxT)}°C" else "Normal",
                                            detailText2 = if (state.oemNeutralizer.joyoseNeutralize) "Anti-Joyose" else "${validThermalZones.size} Sensor",
                                            onClick = { onSelectedCategoryChange("thermal") }
                                        )

                                        // 4. Battery & Charging
                                        SubsystemCategoryCard(
                                            category = TuningCategory.CHARGING,
                                            badgeText = if (state.charging.bypassEnabled) "BYPASS" else if (state.charging.extremeChargingEnabled) "EXTREME" else "${state.charging.limitCurrentMa} mA",
                                            detailText1 = "${uiState.batteryDetails?.tempC ?: 0f}°C",
                                            detailText2 = "${uiState.batteryDetails?.currentMa ?: 0} mA",
                                            onClick = { onSelectedCategoryChange("charging") }
                                        )

                                        // 5. Memory & Storage
                                        SubsystemCategoryCard(
                                            category = TuningCategory.MEMORY,
                                            badgeText = "ZRAM ${uiState.zramCompAlgorithm.ifBlank { "lz4" }.uppercase()}",
                                            detailText1 = "Swap ${state.memory.swappiness}%",
                                            detailText2 = "${uiState.ioDevices.size} I/O Dev",
                                            onClick = { onSelectedCategoryChange("memory") }
                                        )

                                        // 6. Network & Audio
                                        SubsystemCategoryCard(
                                            category = TuningCategory.NETWORK,
                                            badgeText = uiState.currentTcpCongestion.ifBlank { "bbr" }.uppercase(),
                                            detailText1 = if (state.network.wifiPingStabilizer) "FQ-CoDel" else "Standard",
                                            detailText2 = if (state.audio.lowLatencyMmap) "MMAP" else "Audio",
                                            onClick = { onSelectedCategoryChange("network") }
                                        )

                                        // 8. Subsystem & Deep Tunables
                                        SubsystemCategoryCard(
                                            category = TuningCategory.SYSTEM,
                                            badgeText = "${uiState.deepTunables.size} Tunables",
                                            detailText1 = "SELinux ${uiState.selinuxMode}",
                                            detailText2 = "Kernel Safety",
                                            onClick = { onSelectedCategoryChange("system") }
                                        )
                                    }
                                }

                                item(key = "hub_bottom_spacer") {
                                    Spacer(modifier = Modifier.height(115.dp).navigationBarsPadding())
                                }
                            }
                        } else {
                            // ── SUB-PAGE VIEW WITH PERMANENT STICKY PINNED HEADER ──
                            val cat = TuningCategory.fromId(currentCategory) ?: TuningCategory.CPU

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                            ) {
                                // 1. Sticky Header (Pinned outside scrollable area, never sinks)
                                Surface(
                                    color = Color(0xF20B0C10),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Circular Back Arrow
                                            Surface(
                                                onClick = {
                                                    android.util.Log.i("LynxNavigation", "header-back category=$selectedCategory")
                                                    onSelectedCategoryChange(null)
                                                },
                                                shape = CircleShape,
                                                color = BgElevated,
                                                border = BorderStroke(0.8.dp, BorderSubtle),
                                                modifier = Modifier.size(42.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowBack,
                                                        contentDescription = localizedUiText("Kembali ke Pusat Tuning"),
                                                        tint = TextPrimary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }

                                            // Category Icon Tile
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = BgElevated,
                                                border = BorderStroke(0.8.dp, BorderSubtle),
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = cat.icon,
                                                        contentDescription = localizedUiText(cat.title),
                                                        tint = cat.accentColor,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }

                                            // Category Title & Subtitle
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cat.title,
                                                    color = TextPrimary,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.sp,
                                                    style = androidx.compose.ui.text.TextStyle(
                                                        fontFeatureSettings = "liga 0, dlig 0"
                                                    ),
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                Spacer(Modifier.height(1.dp))
                                                Text(
                                                    text = cat.subtitle,
                                                    color = TextSecondary,
                                                    fontSize = 11.sp,
                                                    lineHeight = 14.sp,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }

                                            // Action Button for Profiles: Notification & Feedback HUD Settings
                                            if (currentCategory == "profiles") {
                                                Surface(
                                                    onClick = { showProfilesFeedbackSheet = true },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = AccentCyan.copy(alpha = 0.14f),
                                                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.45f)),
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.NotificationsActive,
                                                            contentDescription = "Gaya Feedback Notifikasi HUD",
                                                            tint = AccentCyan,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        HorizontalDivider(
                                            color = Color(0xFF1E2026),
                                            thickness = 0.8.dp
                                        )
                                    }
                                }

                                // 1.5. Sticky Category Header (Profiles, CPU, GPU, Charging Tabs)
                                if (currentCategory == "profiles") {
                                    ProfilesCategoryTabRow(
                                        selectedTab = uiState.selectedProfileTab,
                                        onSelectTab = { viewModel.selectProfileTab(it) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                } else if (currentCategory == "cpu") {
                                    CpuTabRow(
                                        selectedTab = uiState.selectedCpuTab,
                                        onSelectTab = { viewModel.selectCpuTab(it) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                } else if (currentCategory == "gpu") {
                                    GpuDisplayTabRow(
                                        selectedTab = uiState.selectedGpuTab,
                                        onSelectTab = { viewModel.selectGpuTab(it) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                } else if (currentCategory == "charging") {
                                    BatterySubscreenTabRow(
                                        selectedTab = uiState.batterySubTab,
                                        onSelectTab = { viewModel.setBatterySubTab(it) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }

                                // Own charging polling/dialogs for the whole page, not a lazy item.
                                if (currentCategory == "charging") {
                                    ChargingScreenLifecycle(uiState, viewModel)
                                }

                                // 2. Scrollable Subscreen Content Area (Virtualized LazyColumn)
                                LazyColumn(
                                    state = subscreenLazyState,
                                    flingBehavior = subscreenFling,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    item(key = "subscreen_top_spacer") {
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    when (currentCategory) {
                                        "profiles" -> tuningProfilesCategoryItems(
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
                                        "hud" -> item(key = "subscreen_hud") {
                                            TuningGameHudCategory(
                                                uiState = uiState,
                                                viewModel = viewModel
                                            )
                                        }
                                        "gpu" -> tuningGpuCategoryItems(uiState = uiState, viewModel = viewModel, context = context)
                                        "cpu" -> item(key = "subscreen_cpu") { TuningCpuCategory(uiState = uiState, viewModel = viewModel) }
                                        "thermal" -> item(key = "subscreen_thermal") { TuningThermalCategory(uiState = uiState, viewModel = viewModel) }
                                        "memory" -> item(key = "subscreen_memory") { TuningMemoryCategory(uiState = uiState, viewModel = viewModel) }
                                        "charging" -> if (uiState.batterySubTab == 1) {
                                            batteryInformationItems(uiState, viewModel)
                                        } else {
                                            item(key = "subscreen_charging") {
                                                TuningChargingCategory(uiState, viewModel, manageLifecycle = false)
                                            }
                                        }
                                        "network" -> item(key = "subscreen_network") { TuningNetworkCategory(uiState = uiState, viewModel = viewModel) }
                                        "system" -> item(key = "subscreen_system") {
                                            TuningSystemCategory(
                                                uiState = uiState,
                                                viewModel = viewModel,
                                                onAddAppClick = {
                                                    viewModel.refreshInstalledApps()
                                                    onAddAppClick()
                                                },
                                                onEditRuleClick = { onEditRuleClick(it) }
                                            )
                                        }
                                        else -> item(key = "subscreen_cpu_default") { TuningCpuCategory(uiState = uiState, viewModel = viewModel) }
                                    }
                                    item(key = "subscreen_bottom_spacer") {
                                        Spacer(modifier = Modifier.height(130.dp).navigationBarsPadding())
                                    }
                                }
                        }
                    }
                }

    if (showProfilesFeedbackSheet) {
        ProfilesFeedbackBottomSheet(
            uiState = uiState,
            viewModel = viewModel,
            context = context,
            onDismiss = { showProfilesFeedbackSheet = false }
        )
    }
}
