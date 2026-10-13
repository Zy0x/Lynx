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
fun DashboardTabContent(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    currentAccent: Color,
    dashboardLazyState: androidx.compose.foundation.lazy.LazyListState,
    dashboardFling: FlingBehavior,
    onExtremeConfirmRequired: () -> Unit,
    onNavigateToProfilesCatalog: () -> Unit = {},
) {
    val state = uiState.state
    val context = LocalContext.current
                LazyColumn(
                    state = dashboardLazyState,
                    flingBehavior = dashboardFling,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    dashboardHeaderItem(
                        uiState = uiState,
                        viewModel = viewModel,
                        currentAccent = currentAccent,
                        onExtremeConfirmRequired = onExtremeConfirmRequired,
                        onNavigateToProfilesCatalog = onNavigateToProfilesCatalog
                    )
                    item(key = "charging_hero_display") {
                        ChargingHeroDisplayCard(
                            battery = uiState.batteryDetails,
                            telemetry = uiState.telemetry,
                            chargingConfig = state.charging,
                            accentColor = currentAccent,
                            onSelectChargingMode = { mode ->
                                if (mode == "bypass") viewModel.setBypassCharging(true)
                                else viewModel.setChargingMode(mode)
                            }
                        )
                    }
                    item(key = "telemetry_gauges") {
                        val archLabel = when (uiState.gpuInfo.platform) {
                            "adreno" -> "Adreno"
                            "mali_ged", "mali_kbase" -> "Mali"
                            else -> if (uiState.gpuInfo.platform.isNotBlank()) uiState.gpuInfo.platform.uppercase() else "GPU"
                        }
                        TelemetryGaugesCard(
                            telemetry = uiState.telemetry,
                            cpuLoad = uiState.totalCpuLoadPercent,
                            gpuLoad = if (uiState.gpuInfo.gpuLoadPercent > 0) uiState.gpuInfo.gpuLoadPercent else uiState.telemetry?.gpuBusy ?: 0,
                            gpuArchLabel = archLabel
                        )
                    }

                    item(key = "telemetry_memory") {
                        TelemetryMemoryCard(
                            telemetry = uiState.telemetry,
                            onCleanRam = { viewModel.cleanRam() },
                            isCleaningRam = uiState.isCleaningRam
                        )
                    }

                    // ── Live Hardware Thermal Zones Matrix ─────────────────
                    item(key = "thermal_zones") {
                        val zones = uiState.thermalZones.filter { it.tempC in 15f..110f }
                        if (zones.isNotEmpty()) {
                            LynxCard(
                                title = "Matriks Sensor Termal Hardware",
                                icon = Icons.Default.Thermostat,
                                accentColor = AccentRed
                            ) {
                                val displayZones = zones.take(6)
                                displayZones.chunked(2).forEach { row ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        row.forEach { zone ->
                                            val tempColor = when {
                                                zone.tempC >= 60f -> AccentRed
                                                zone.tempC >= 45f -> AccentOrange
                                                else -> AccentGreen
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = BgElevated,
                                                border = BorderStroke(1.dp, tempColor.copy(alpha = 0.35f)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Row(
                                                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(Modifier.weight(1f)) {
                                                        val cleanType = zone.type
                                                            .replace("mtkts", "")
                                                            .replace("tsens_tz_sensor", "sensor")
                                                            .uppercase()
                                                        Text(cleanType, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                        Text(zone.type, color = TextSecondary.copy(alpha = 0.6f), fontSize = 8.5.sp, maxLines = 1)
                                                    }
                                                    Text(
                                                        String.format("%.1f°C", zone.tempC),
                                                        color = tempColor,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                            }
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    item(key = "dashboard_bottom_spacer") {
                        Spacer(modifier = Modifier.height(130.dp).navigationBarsPadding())
                    }
                }
}
