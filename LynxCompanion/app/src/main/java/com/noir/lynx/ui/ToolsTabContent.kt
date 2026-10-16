package com.noir.lynx.ui

import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*
import com.noir.lynx.feature.battery.*
import com.noir.lynx.feature.battery.ui.*
import com.noir.lynx.feature.battery.ui.lab.BatteryLabSubscreen

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
import com.noir.lynx.core.locale.localizedUiText
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
import androidx.compose.ui.res.stringResource
import com.noir.lynx.core.locale.AppLocale
import com.noir.lynx.R
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
import com.noir.lynx.feature.navigation.model.ToolsCategory

/**
 * ToolsTabContent — Categorized Tools & Lab Hub.
 *
 * Implements a Hub View & Subscreen View pattern with luxury animations,
 * sticky pinned headers, and strict separation between diagnostic tools/benches
 * and kernel tuning parameters (Zero Redundancy).
 */
@Composable
fun ToolsTabContent(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    selectedCategory: String?,
    onSelectedCategoryChange: (String?) -> Unit,
    toolsHubLazyState: androidx.compose.foundation.lazy.LazyListState,
    toolsHubFling: FlingBehavior,
    toolsSubscreenLazyState: androidx.compose.foundation.lazy.LazyListState,
    toolsSubscreenFling: FlingBehavior,
    onNavigateToTuning: (String) -> Unit = {}
) {
    val state = uiState.state

    LaunchedEffect(Unit) {
        viewModel.loadCustomRules()
        viewModel.refreshThermalZones()
        viewModel.refreshWakelocks()
        if (uiState.capabilityReport == null) {
            viewModel.scanKernelCapabilities()
        }
    }

    var customScriptText by remember { mutableStateOf("") }
    var hasUserEditedCustomScript by remember { mutableStateOf(false) }
    var lastCustomScriptInteraction by remember { mutableLongStateOf(0L) }
    var lastCustomScriptBtnClick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(uiState.customRulesScript) {
        val now = System.currentTimeMillis()
        if (!hasUserEditedCustomScript || (now - lastCustomScriptInteraction > 3000L && customScriptText.isEmpty())) {
            customScriptText = uiState.customRulesScript
        }
    }

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
        label = "ToolsHubTransition"
    ) { currentCategory ->
        if (currentCategory == null) {
            // ── HUB VIEW (CATEGORIZED TOOLS & LAB CARDS) ───────────────────
            LazyColumn(
                state = toolsHubLazyState,
                flingBehavior = toolsHubFling,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                item(key = "hub_header") {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "Pusat Alat & Lab",
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Pilih modul utilitas untuk pengujian lab, benchmark, flasher & audit kernel",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                item(key = "hub_tool_cards") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val details = uiState.batteryDetails
                        val health = uiState.batteryHealthStats

                        // 1. Lab Baterai & Daya (Charging & Capacity Lab)
                        val chargeBadge = if (details?.isCharging == true) {
                            "${details.currentMa} mA"
                        } else {
                            "${details?.level ?: 0}%"
                        }
                        ToolCategoryCard(
                            category = ToolsCategory.BATTERY_LAB,
                            badgeText = chargeBadge,
                            detailText1 = "Bench Pengisian Sesi",
                            detailText2 = "${health.actualCapacityMah} mAh Fisik (${health.healthPct}%)",
                            onClick = { onSelectedCategoryChange(ToolsCategory.BATTERY_LAB.id) }
                        )

                        // 2. Lab Benchmark & Optimasi
                        ToolCategoryCard(
                            category = ToolsCategory.BENCHMARK,
                            badgeText = "BENCH",
                            detailText1 = "Hardware FPS Studio",
                            detailText2 = "Storage I/O & CCleaner",
                            onClick = { onSelectedCategoryChange(ToolsCategory.BENCHMARK.id) }
                        )

                        // 3. Flasher & Partisi Boot
                        ToolCategoryCard(
                            category = ToolsCategory.FLASHER,
                            badgeText = "${uiState.backups.size} BACKUP",
                            detailText1 = "AnyKernel3 Flasher",
                            detailText2 = "Boot Backup & Restore",
                            onClick = { onSelectedCategoryChange(ToolsCategory.FLASHER.id) }
                        )

                        // 4. Diagnostik & Audit Sistem
                        ToolCategoryCard(
                            category = ToolsCategory.SYSTEM_DIAG,
                            badgeText = "DIAGNOSTIK",
                            detailText1 = "${uiState.thermalZones.size} Sensor Thermal",
                            detailText2 = "SELinux ${uiState.selinuxMode}",
                            onClick = { onSelectedCategoryChange(ToolsCategory.SYSTEM_DIAG.id) }
                        )

                        // 5. Preferensi & Media Decoder
                        ToolCategoryCard(
                            category = ToolsCategory.PREFERENCES,
                            badgeText = "SETELAN",
                            detailText1 = "Bahasa Aplikasi",
                            detailText2 = "Media Decoder OMX",
                            onClick = { onSelectedCategoryChange(ToolsCategory.PREFERENCES.id) }
                        )
                    }
                }

                item(key = "hub_bottom_spacer") {
                    Spacer(modifier = Modifier.height(115.dp).navigationBarsPadding())
                }
            }
        } else {
            // ── SUB-PAGE VIEW WITH PERMANENT STICKY PINNED HEADER ─────────
            val cat = ToolsCategory.fromId(currentCategory) ?: ToolsCategory.BATTERY_LAB

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // 1. Sticky Header
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
                                onClick = { onSelectedCategoryChange(null) },
                                shape = CircleShape,
                                color = BgElevated,
                                border = BorderStroke(0.8.dp, BorderSubtle),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = localizedUiText("Kembali ke Pusat Alat"),
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
                                    maxLines = 1
                                )
                                Text(
                                    text = cat.subtitle,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                        HorizontalDivider(color = BorderGlass)
                    }
                }

                // 2. Subscreen Scrollable Content
                LazyColumn(
                    state = toolsSubscreenLazyState,
                    flingBehavior = toolsSubscreenFling,
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "sub_top_spacer") {
                        Spacer(Modifier.height(8.dp))
                    }

                    when (cat) {
                        ToolsCategory.BATTERY_LAB -> {
                            item(key = "battery_lab_subscreen") {
                                BatteryLabSubscreen(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onNavigateToTuningBattery = { onNavigateToTuning("charging") }
                                )
                            }
                        }

                        ToolsCategory.BENCHMARK -> {
                            item(key = "maintenance_actions") {
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    LynxCard(
                                        title = "Pemeliharaan & Benchmark 1-Klik",
                                        icon = Icons.Default.Assessment,
                                        accentColor = AccentPurple
                                    ) {
                                        LynxActionButton(
                                            text = "Live Benchmark & Hardware Frame Profiler",
                                            icon = Icons.Default.Assessment,
                                            onClick = { viewModel.openBenchmarkDialog() },
                                            accentColor = AccentPurple,
                                            modifier = Modifier.padding(bottom = 10.dp)
                                        )
                                        LynxActionButton(
                                            text = "Optimasi SQLite & FSTRIM Storage",
                                            icon = Icons.Default.Storage,
                                            onClick = { viewModel.runMaintenance() },
                                            isLoading = uiState.maintenanceRunning,
                                            accentColor = AccentCyan,
                                            modifier = Modifier.padding(bottom = 10.dp)
                                        )
                                        LynxActionButton(
                                            text = "Jalankan CCleaner (Memory Purge)",
                                            icon = Icons.Default.Speed,
                                            onClick = { viewModel.runCCleaner() },
                                            accentColor = AccentBlue,
                                            modifier = Modifier.padding(bottom = 10.dp)
                                        )
                                        LynxActionButton(
                                            text = "Ekspor Laporan Diagnostik 1-Klik (.ZIP)",
                                            icon = Icons.Default.Build,
                                            onClick = { viewModel.exportBugReport() },
                                            isLoading = uiState.exportRunning,
                                            accentColor = AccentOrange,
                                            modifier = Modifier.padding(bottom = 10.dp)
                                        )
                                        LynxActionButton(
                                            text = "Reset Aman Kernel ke Bawaan (Stock Safe)",
                                            icon = Icons.Default.RestartAlt,
                                            onClick = { viewModel.resetKernelToStock() },
                                            accentColor = AccentGreen
                                        )
                                    }
                                }
                            }
                        }

                        ToolsCategory.FLASHER -> {
                            item(key = "flasher") {
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    FlasherCard(
                                        isFlashing = uiState.isFlashing,
                                        flashLog = uiState.flashLog,
                                        onFlash = { viewModel.flashKernel(it) }
                                    )
                                }
                            }

                            item(key = "boot_backup") {
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                    BootBackupCard(
                                        backups = uiState.backups,
                                        isBackingUp = uiState.isBackingUp,
                                        onBackup = { viewModel.backupBoot() },
                                        onRestore = { viewModel.restoreBoot(it) }
                                    )
                                }
                            }

                            item(key = "flasher_reset_action") {
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    LynxCard(
                                        title = "Restorasi Keamanan Kernel",
                                        icon = Icons.Default.Security,
                                        accentColor = AccentGreen
                                    ) {
                                        Text(
                                            "Kembalikan seluruh parameter sysfs dan governor ke setelan aman bawaan pabrik OEM tanpa menghapus data.",
                                            color = TextSecondary,
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp,
                                            modifier = Modifier.padding(bottom = 10.dp)
                                        )
                                        LynxActionButton(
                                            text = "Reset Aman Kernel ke Bawaan (Stock Safe)",
                                            icon = Icons.Default.RestartAlt,
                                            onClick = { viewModel.resetKernelToStock() },
                                            accentColor = AccentGreen
                                        )
                                    }
                                }
                            }
                        }

                        ToolsCategory.SYSTEM_DIAG -> {
                            // Wakelock Blocker & Deep Sleep Audit Card
                            item(key = "wakelock_audit") {
                                val wlInfo = uiState.wakelockBlockerInfo
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    LynxCard(
                                        title = "Wakelock Blocker & Deep Sleep Audit",
                                        icon = Icons.Default.Bedtime,
                                        accentColor = AccentPurple
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text(
                                                    "Audit Sumber Bangun Kernel (Wakeup Sources)",
                                                    color = TextPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    "Mendeteksi driver & thread yang menahan CPU dari mode deep sleep",
                                                    color = TextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (wlInfo.isDriverSupported) AccentGreen.copy(alpha = 0.18f) else AccentOrange.copy(alpha = 0.18f),
                                                border = BorderStroke(1.dp, if (wlInfo.isDriverSupported) AccentGreen.copy(alpha = 0.4f) else AccentOrange.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    if (wlInfo.isDriverSupported) "DRIVER: BOEFFLA" else "DRIVER: UNSUPPORTED",
                                                    color = if (wlInfo.isDriverSupported) AccentGreen else AccentOrange,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        if (!wlInfo.isDriverSupported) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = BgElevated,
                                                border = BorderStroke(0.8.dp, BorderSubtle),
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                                            ) {
                                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Info, null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        "Kernel stock OEM ini tidak memiliki driver Boeffla Wakelock Blocker. Pemantauan wakeup sources tetap aktif 100%, dan penghematan daya dilakukan via Aggressive Doze.",
                                                        color = TextSecondary, fontSize = 10.5.sp, lineHeight = 15.sp
                                                    )
                                                }
                                            }
                                        }

                                        LynxSwitch(
                                            label = "Aggressive Doze (Paksa Idle)",
                                            subLabel = "Paksa status idle sistem saat layar mati untuk memotong konsumsi baterai standby",
                                            checked = wlInfo.aggressiveDozeEnabled,
                                            onCheckedChange = { viewModel.toggleAggressiveDoze(it) }
                                        )

                                        Spacer(Modifier.height(8.dp))

                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Sumber Wakeup Tertinggi (${uiState.topWakelocks.size})",
                                                color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold
                                            )
                                            IconButton(onClick = { viewModel.refreshWakelocks() }) {
                                                Icon(Icons.Default.Refresh, "Refresh", tint = AccentPurple, modifier = Modifier.size(18.dp))
                                            }
                                        }

                                        if (uiState.topWakelocks.isEmpty()) {
                                            Text(
                                                "Tidak ada sumber wakeup yang aktif menahan sistem. Deep sleep berfungsi optimal.",
                                                color = AccentCyan, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        } else {
                                            uiState.topWakelocks.take(8).forEach { wl ->
                                                val isBlocked = wlInfo.blockedWakelocks.contains(wl.name)
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = BgElevated,
                                                    border = BorderStroke(1.dp, if (isBlocked) AccentRed.copy(alpha = 0.5f) else BorderGlass),
                                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                                ) {
                                                    Row(
                                                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Text(
                                                                    wl.name,
                                                                    color = if (isBlocked) AccentRed else TextPrimary,
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                                if (isBlocked) {
                                                                    Spacer(Modifier.width(6.dp))
                                                                    Surface(shape = RoundedCornerShape(4.dp), color = AccentRed.copy(alpha = 0.2f)) {
                                                                        Text("DIBLOKIR", color = AccentRed, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                                    }
                                                                }
                                                            }
                                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                                                Text(
                                                                    "${wl.activeCount}x bangun",
                                                                    color = AccentCyan,
                                                                    fontSize = 10.5.sp
                                                                )
                                                                if (wl.preventSuspendMs > 0) {
                                                                    Spacer(Modifier.width(8.dp))
                                                                    Text(
                                                                        "${wl.preventSuspendMs}ms tahan sleep",
                                                                        color = AccentOrange,
                                                                        fontSize = 10.5.sp
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        if (wlInfo.isDriverSupported) {
                                                            Switch(
                                                                checked = isBlocked,
                                                                onCheckedChange = { viewModel.toggleWakelockBlocked(wl.name, it) },
                                                                colors = SwitchDefaults.colors(
                                                                    checkedThumbColor = BgDeepOled,
                                                                    checkedTrackColor = AccentRed
                                                                ),
                                                                modifier = Modifier.scale(0.8f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Dynamic Hardware Thermal Zones Card
                            if (uiState.thermalZones.isNotEmpty()) {
                                item(key = "thermal_zones_monitor") {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                        LynxCard(
                                            title = "Monitor Sensor Thermal Hardware",
                                            icon = Icons.Default.Thermostat,
                                            accentColor = AccentRed
                                        ) {
                                            Row(
                                                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "${uiState.thermalZones.size} Sensor Thermal Hardware Aktif",
                                                    color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                                                )
                                                IconButton(onClick = { viewModel.refreshThermalZones() }) {
                                                    Icon(Icons.Default.Refresh, "Refresh", tint = AccentRed, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            val chunkedZones = uiState.thermalZones.chunked(2)
                                            chunkedZones.forEach { row ->
                                                Row(
                                                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    row.forEach { tz ->
                                                        val tempColor = when {
                                                            tz.tempC >= 60f -> AccentRed
                                                            tz.tempC >= 45f -> AccentOrange
                                                            else -> AccentCyan
                                                        }
                                                        Surface(
                                                            shape = RoundedCornerShape(10.dp),
                                                            color = BgElevated,
                                                            border = BorderStroke(1.dp, BorderGlass),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Row(
                                                                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Column(Modifier.weight(1f)) {
                                                                    Text(
                                                                        tz.type.take(16),
                                                                        color = TextPrimary,
                                                                        fontSize = 11.sp,
                                                                        fontWeight = FontWeight.Medium,
                                                                        maxLines = 1
                                                                    )
                                                                    Text("Zone ${tz.id}", color = TextSecondary, fontSize = 9.sp)
                                                                }
                                                                Text(
                                                                    "${String.format(java.util.Locale.US, "%.1f", tz.tempC)}°C",
                                                                    color = tempColor,
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold
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
                            }

                            // SELinux & Kernel Printk Logging Card
                            item(key = "selinux_card") {
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    LynxCard(
                                        title = "SELinux & Kernel Printk Logging",
                                        icon = Icons.Default.Security,
                                        accentColor = AccentCyan
                                    ) {
                                        val isEnforcing = uiState.selinuxMode.equals("Enforcing", ignoreCase = true)
                                        Row(
                                            Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text("Status Mode SELinux", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                Text(
                                                    if (isEnforcing) "Perlindungan keamanan kernel aktif (Enforcing)" else "Mode Permissive aktif (Debugging / Modifikasi)",
                                                    color = TextSecondary, fontSize = 11.sp
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isEnforcing) AccentCyan.copy(alpha = 0.18f) else AccentOrange.copy(alpha = 0.18f),
                                                border = BorderStroke(1.dp, if (isEnforcing) AccentCyan else AccentOrange)
                                            ) {
                                                Text(
                                                    uiState.selinuxMode.uppercase(),
                                                    color = if (isEnforcing) AccentCyan else AccentOrange,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        Row(
                                            Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { viewModel.setSelinuxMode(true) },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isEnforcing) AccentCyan else BgElevated
                                                ),
                                                border = BorderStroke(1.dp, if (isEnforcing) AccentCyan else BorderGlass)
                                            ) {
                                                Text("Enforcing", color = if (isEnforcing) Color.Black else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Button(
                                                onClick = { viewModel.setSelinuxMode(false) },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (!isEnforcing) AccentOrange else BgElevated
                                                ),
                                                border = BorderStroke(1.dp, if (!isEnforcing) AccentOrange else BorderGlass)
                                            ) {
                                                Text("Permissive", color = if (!isEnforcing) Color.Black else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(bottom = 10.dp))

                                        LynxSwitch(
                                            label = "Kernel Printk Silent Mode (Zero Overhead)",
                                            subLabel = "Nonaktifkan pencatatan dmesg di background untuk mencegah micro-stutter saat gaming",
                                            checked = uiState.isPrintkSilent,
                                            onCheckedChange = { viewModel.setPrintkSilent(it) }
                                        )
                                    }
                                }
                            }

                            // Custom Sysfs Rules & Boot Tweaks Card
                            item(key = "custom_rules") {
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                    LynxCard(
                                        title = "Custom Sysfs Rules & Boot Tweaks",
                                        icon = Icons.Default.Terminal,
                                        accentColor = AccentOrange
                                    ) {
                                        Text(
                                            "Tulis perintah sysfs atau shell kustom Anda sendiri. Skrip disimpan di /data/adb/modules/Lynx/custom_rules.sh dan dieksekusi otomatis saat boot oleh service.sh.",
                                            color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
                                            modifier = Modifier.padding(bottom = 10.dp)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Templat Cepat:", color = AccentOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Surface(
                                                onClick = {
                                                    viewModel.loadCustomRules()
                                                    customScriptText = uiState.customRulesScript
                                                    hasUserEditedCustomScript = false
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                color = AccentCyan.copy(alpha = 0.12f),
                                                border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.3f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.Refresh, null, tint = AccentCyan, modifier = Modifier.size(12.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Muat Ulang Berkas", fontSize = 10.sp, color = AccentCyan, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                                        ) {
                                            val snippets = listOf(
                                                "+ TCP FastOpen" to "\necho 3 > /proc/sys/net/ipv4/tcp_fastopen",
                                                "+ Drop Caches" to "\necho 3 > /proc/sys/vm/drop_caches",
                                                "+ Compact Memory" to "\necho 1 > /proc/sys/vm/compact_memory",
                                                "+ Sched Migration" to "\necho 32 > /proc/sys/kernel/sched_nr_migrate",
                                            )
                                            items(snippets.size) { i ->
                                                val (title, code) = snippets[i]
                                                Surface(
                                                    onClick = {
                                                        customScriptText += code
                                                        hasUserEditedCustomScript = true
                                                        lastCustomScriptInteraction = System.currentTimeMillis()
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = BgElevated,
                                                    border = BorderStroke(1.dp, BorderGlass)
                                                ) {
                                                    Text(
                                                        title,
                                                        color = AccentOrange,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = customScriptText,
                                            onValueChange = {
                                                customScriptText = it
                                                hasUserEditedCustomScript = true
                                                lastCustomScriptInteraction = System.currentTimeMillis()
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 130.dp, max = 240.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.5.sp,
                                                color = TextPrimary
                                            ),
                                            placeholder = { Text("# Tulis skrip shell Anda disini...", color = TextSecondary, fontSize = 11.sp) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = AccentOrange,
                                                unfocusedBorderColor = BorderGlass,
                                                focusedContainerColor = Color(0xFF0A0C12),
                                                unfocusedContainerColor = Color(0xFF0A0C12),
                                            )
                                        )

                                        Spacer(Modifier.height(10.dp))

                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastCustomScriptBtnClick >= 400L) {
                                                        lastCustomScriptBtnClick = now
                                                        hasUserEditedCustomScript = false
                                                        lastCustomScriptInteraction = 0L
                                                        viewModel.saveCustomRules(customScriptText)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                                            ) {
                                                Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Simpan Skrip", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Button(
                                                onClick = {
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastCustomScriptBtnClick >= 400L) {
                                                        lastCustomScriptBtnClick = now
                                                        hasUserEditedCustomScript = false
                                                        lastCustomScriptInteraction = 0L
                                                        viewModel.saveCustomRules(customScriptText)
                                                        viewModel.executeCustomRules()
                                                    }
                                                },
                                                enabled = !uiState.customRulesRunning,
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                                            ) {
                                                if (uiState.customRulesRunning) {
                                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                } else {
                                                    Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Jalankan (Root)", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        uiState.customRulesOutput?.let { out ->
                                            Spacer(Modifier.height(10.dp))
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF08090D),
                                                border = BorderStroke(1.dp, BorderGlass),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(Modifier.padding(10.dp)) {
                                                    Text("LOG EKSEKUSI TERAKHIR:", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        out,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 11.sp,
                                                        color = TextSecondary,
                                                        lineHeight = 15.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Full Kernel Capabilities Diagnostic Items
                            toolsKernelDiagnosticsItems(uiState, viewModel)
                        }

                        ToolsCategory.PREFERENCES -> {
                            toolsPreferenceItems()
                        }
                    }

                    item(key = "sub_bottom_spacer") {
                        Spacer(modifier = Modifier.height(115.dp).navigationBarsPadding())
                    }
                }
            }
        }
    }
}
