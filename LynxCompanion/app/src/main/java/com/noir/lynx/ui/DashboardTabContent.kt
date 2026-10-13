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
                    dashboardHeaderItem(uiState, viewModel, currentAccent)
                    item(key = "charging_hero_display") {
                        ChargingHeroDisplayCard(
                            battery = uiState.batteryDetails,
                            telemetry = uiState.telemetry,
                            chargingConfig = state.charging,
                            accentColor = currentAccent
                        )
                    }
                    item(key = "telemetry_gauges") {
                        TelemetryGaugesCard(
                            telemetry = uiState.telemetry,
                            cpuLoad = uiState.totalCpuLoadPercent
                        )
                    }

                    item(key = "telemetry_memory") {
                        TelemetryMemoryCard(telemetry = uiState.telemetry)
                    }

                    item(key = "quick_profiles") {
                        QuickProfileSwitcherCard(
                            catalog = uiState.kernelProfileCatalog,
                            currentProfile = state.activeProfile,
                            hasOverrides = state.profileOverrides.isNotEmpty(),
                            onProfileSelected = { id, clearOverrides ->
                                viewModel.selectKernelPerformanceProfile(id, context, clearOverrides)
                            },
                            onExtremeConfirmRequired = onExtremeConfirmRequired,
                            onNavigateToProfilesCatalog = onNavigateToProfilesCatalog
                        )
                    }

                    // ── Live Floating Game HUD & OSD Card ─────────────────
                    item(key = "game_hud") {
                        LynxCard(
                            title = "Floating Game HUD & OSD",
                            icon = Icons.Default.Visibility,
                            accentColor = AccentCyan
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Overlay Game HUD Real-Time",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "Tampilkan FPS hardware, clock CPU/GPU, watt baterai & quick profile switcher di atas semua game.",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                                Switch(
                                    checked = uiState.isGameHudActive,
                                    onCheckedChange = { viewModel.toggleGameHud(context, it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = BgDeepOled,
                                        checkedTrackColor = AccentCyan,
                                        uncheckedThumbColor = TextSecondary,
                                        uncheckedTrackColor = BgElevated,
                                        uncheckedBorderColor = BorderGlass
                                    )
                                )
                            }

                            if (uiState.isGameHudActive) {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = AccentCyan.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Info, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "HUD aktif! Sentuh header HUD di layar atau pilih gaya tampilan di bawah untuk mengubah layout secara instan.",
                                            color = TextPrimary,
                                            fontSize = 10.5.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(
                                "MODE INTERAKSI OSD",
                                color = AccentCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Option 1: Edge Drawer
                                val isDrawerSel = (uiState.hudMode == 0)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDrawerSel) AccentCyan.copy(alpha = 0.18f) else BgElevated,
                                    border = BorderStroke(1.dp, if (isDrawerSel) AccentCyan else BorderGlass),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.setHudMode(context, 0) }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isDrawerSel) AccentCyan else Color(0x33FFFFFF))
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "Edge Drawer",
                                                color = if (isDrawerSel) AccentCyan else TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Bilah tipis tepi layar ala Infinix Game Space / ROG. Usap untuk buka Game Bar tanpa menghalangi visual game.",
                                            color = TextSecondary,
                                            fontSize = 9.sp,
                                            lineHeight = 12.sp
                                        )
                                    }
                                }

                                // Option 2: Floating Window
                                val isFloatSel = (uiState.hudMode == 1)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isFloatSel) AccentCyan.copy(alpha = 0.18f) else BgElevated,
                                    border = BorderStroke(1.dp, if (isFloatSel) AccentCyan else BorderGlass),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.setHudMode(context, 1) }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isFloatSel) AccentCyan else Color(0x33FFFFFF))
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "Floating Window",
                                                color = if (isFloatSel) AccentCyan else TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Jendela melayang bebas yang selalu tampil di layar. Bebas dipindah ke posisi mana pun.",
                                            color = TextSecondary,
                                            fontSize = 9.sp,
                                            lineHeight = 12.sp
                                        )
                                    }
                                }
                            }

                            // Mini FPS Pin option (if Edge Drawer mode)
                            if (uiState.hudMode == 0) {
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (uiState.hudPinMiniFps) AccentCyan.copy(alpha = 0.14f) else BgElevated,
                                    border = BorderStroke(1.dp, if (uiState.hudPinMiniFps) AccentCyan else BorderGlass),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setHudPinMiniFps(context, !uiState.hudPinMiniFps) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PushPin, null, tint = if (uiState.hudPinMiniFps) AccentCyan else TextSecondary, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    "Pin Mini FPS di Samping Bilah",
                                                    color = if (uiState.hudPinMiniFps) AccentCyan else TextPrimary,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    "Tampilkan tag angka FPS kecil saat bilah samping tertutup",
                                                    color = TextSecondary,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = uiState.hudPinMiniFps,
                                            onCheckedChange = { viewModel.setHudPinMiniFps(context, it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = AccentCyan,
                                                checkedTrackColor = AccentCyan.copy(alpha = 0.35f)
                                            ),
                                            modifier = Modifier.scale(0.75f)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(
                                "PILIHAN GAYA TAMPILAN OSD",
                                color = AccentCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(Modifier.height(6.dp))

                            val hudStyleOptions = listOf(
                                1 to "1. Vertical Pillar (RTSS)",
                                2 to "2. Top Nano-Ribbon",
                                3 to "3. Dual-Block Esport",
                                4 to "4. Quad-Tiles Modular",
                                5 to "5. Steam Deck Banner",
                                6 to "6. Ghost Text (Frameless)"
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                hudStyleOptions.chunked(2).forEach { rowStyles ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        rowStyles.forEach { (sId, sLabel) ->
                                            val isSel = (uiState.hudStyle == sId)
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSel) AccentCyan.copy(alpha = 0.18f) else BgElevated,
                                                border = BorderStroke(1.dp, if (isSel) AccentCyan else BorderGlass),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { viewModel.setHudStyle(context, sId) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isSel) AccentCyan else Color(0x33FFFFFF))
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = sLabel,
                                                        color = if (isSel) AccentCyan else TextPrimary,
                                                        fontSize = 10.sp,
                                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Live Hardware Benchmark & Frame Profiler Studio Card ─
                    item(key = "benchmark") {
                        LynxCard(
                            title = "Live Hardware Benchmark & Frame Pacing",
                            icon = Icons.Default.Assessment,
                            accentColor = AccentPurple
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Studio Benchmark & Frametime Jitter",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "Uji kestabilan frame pacing, 1% Low FPS, & hardware presentation timestamp via SurfaceFlinger.",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                }
                                Button(
                                    onClick = { viewModel.openBenchmarkDialog() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Uji", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }



                    // ── FKM Parity: Battery Health & Deep Sleep Card ───────────
                    item(key = "battery_health") {
                        BatteryHealthStatsCard(
                            batteryHealth = uiState.batteryHealthStats,
                            onRefresh = { viewModel.refreshBatteryHealth() }
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
