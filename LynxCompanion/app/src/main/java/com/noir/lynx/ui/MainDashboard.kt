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

@Composable
fun MainDashboard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onExtremeConfirmRequired: () -> Unit,
) {
    val state = uiState.state
    val context = LocalContext.current
    val dashboardLazyState = rememberLazyListState()
    val toolsLazyState = rememberLazyListState()
    val tuningHubLazyState = rememberLazyListState()
    val subscreenLazyState = rememberLazyListState()

    val dashboardFling = rememberFluidFlingBehavior()
    val tuningHubFling = rememberFluidFlingBehavior()
    val toolsFling = rememberFluidFlingBehavior()
    val subscreenFling = rememberFluidFlingBehavior()
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.syncHudPrefs(context)
    }
    BackHandler(enabled = uiState.currentTab == 1 && selectedCategory != null) {
        android.util.Log.i("LynxNavigation", "system-back category=$selectedCategory scrolling=${subscreenLazyState.isScrollInProgress}")
        selectedCategory = null
    }
    LaunchedEffect(selectedCategory) {
        subscreenLazyState.scrollToItem(0)
        if (selectedCategory == "gpu") {
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
    }
    var lastCpuTab by rememberSaveable { mutableStateOf(uiState.selectedCpuTab) }
    var lastGpuTab by rememberSaveable { mutableStateOf(uiState.selectedGpuTab) }
    var lastBattTab by rememberSaveable { mutableStateOf(uiState.batterySubTab) }
    LaunchedEffect(uiState.selectedCpuTab, uiState.selectedGpuTab, uiState.batterySubTab) {
        if (uiState.selectedCpuTab != lastCpuTab || uiState.selectedGpuTab != lastGpuTab || uiState.batterySubTab != lastBattTab) {
            lastCpuTab = uiState.selectedCpuTab
            lastGpuTab = uiState.selectedGpuTab
            lastBattTab = uiState.batterySubTab
            subscreenLazyState.scrollToItem(0)
        }
    }
    var showAddAppDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<AppProfileRule?>(null) }
    var addAppFilter by remember { mutableStateOf("ALL") }
    var customPkgText by remember { mutableStateOf("") }
    var selectedTargetProfile by remember { mutableStateOf("performance") }

    val currentAccent = when (uiState.selectedAccent) {
        "blue" -> AccentBlue
        "purple" -> AccentPurple
        "orange" -> AccentOrange
        "red" -> AccentRed
        "green" -> AccentGreen
        else -> AccentCyan
    }

    // ── Dialog Edit Rule Profil Aplikasi ───────────────────────
    if (editingRule != null) {
        val rule = editingRule!!
        var targetProf by remember(rule.packageName) { mutableStateOf(rule.targetProfile) }
        var targetHz by remember(rule.packageName) { mutableStateOf(rule.targetRefreshRate) }
        var autoHud by remember(rule.packageName) { mutableStateOf(rule.autoFloatingHud) }
        var isRuleEnabled by remember(rule.packageName) { mutableStateOf(rule.enabled) }

        AlertDialog(
            onDismissRequest = { editingRule = null },
            containerColor = BgCard,
            titleContentColor = TextPrimary,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppIconImage(
                        packageName = rule.packageName,
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(rule.appName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                        Text(rule.packageName, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Status Aturan Toggle
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Status Otomasi Aplikasi", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Aktifkan profil kustom untuk aplikasi ini", color = TextSecondary, fontSize = 10.5.sp)
                        }
                        Switch(
                            checked = isRuleEnabled,
                            onCheckedChange = { isRuleEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BgDeepOled,
                                checkedTrackColor = AccentGreen
                            ),
                            modifier = Modifier.scale(0.85f)
                        )
                    }

                    HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f))

                    // Target Profil Kernel
                    Column {
                        Text("Profil Performa Kernel", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        Text("Pilih profil CPU/GPU saat aplikasi berada di foreground", color = TextSecondary, fontSize = 10.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "extreme" to ("Extreme" to AccentRed),
                                "performance" to ("Performa" to AccentOrange),
                                "balance" to ("Balance" to AccentBlue),
                                "powersave" to ("Hemat" to AccentGreen),
                            ).forEach { (prof, meta) ->
                                val isSel = targetProf.equals(prof, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) meta.second.copy(alpha = 0.22f) else BgElevated,
                                    border = BorderStroke(1.dp, if (isSel) meta.second else BorderGlass),
                                    modifier = Modifier.weight(1f).clickable { targetProf = prof }
                                ) {
                                    Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = meta.first,
                                            color = if (isSel) meta.second else TextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Target Display Refresh Rate
                    Column {
                        Text("Kunci Refresh Rate Layar", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        Text("Paksa refresh rate panel saat aplikasi berjalan di layar", color = TextSecondary, fontSize = 10.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                null to "Bawaan",
                                60 to "60 Hz",
                                90 to "90 Hz",
                                120 to "120 Hz"
                            ).forEach { (hz, label) ->
                                val isSel = targetHz == hz
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) AccentPurple.copy(alpha = 0.22f) else BgElevated,
                                    border = BorderStroke(1.dp, if (isSel) AccentPurple else BorderGlass),
                                    modifier = Modifier.weight(1f).clickable { targetHz = hz }
                                ) {
                                    Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            color = if (isSel) AccentPurple else TextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Auto Floating HUD Toggle
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Floating Game HUD (OSD)", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Buka overlay FPS & watt otomatis saat aplikasi dibuka", color = TextSecondary, fontSize = 10.sp)
                        }
                        Switch(
                            checked = autoHud,
                            onCheckedChange = { autoHud = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BgDeepOled,
                                checkedTrackColor = AccentCyan
                            ),
                            modifier = Modifier.scale(0.85f)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val updated = rule.copy(
                        targetProfile = targetProf,
                        targetRefreshRate = targetHz,
                        autoFloatingHud = autoHud,
                        enabled = isRuleEnabled
                    )
                    viewModel.addOrUpdateAppProfileRule(context, updated)
                    editingRule = null
                }) {
                    Text("Simpan", color = AccentGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.deleteAppProfileRule(context, rule.packageName)
                    viewModel.removeAppFromPerf(rule.packageName)
                    editingRule = null
                }) {
                    Text("Hapus Aturan", color = AccentRed)
                }
            }
        )
    }

    // ── Dialog Tambah Aturan Profil Aplikasi ───────────────────
    if (showAddAppDialog) {
        AlertDialog(
            onDismissRequest = { showAddAppDialog = false },
            containerColor = BgCard,
            titleContentColor = TextPrimary,
            title = {
                Text("Pilih Aplikasi / Game", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                    // Search text field
                    OutlinedTextField(
                        value = customPkgText,
                        onValueChange = { customPkgText = it },
                        placeholder = { Text("Cari nama atau package...", color = TextSecondary, fontSize = 11.5.sp) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true,
                        trailingIcon = {
                            if (customPkgText.isNotBlank()) {
                                IconButton(onClick = { customPkgText = "" }) {
                                    Icon(Icons.Default.Clear, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                        )
                    )

                    // Filter category chips (Semua, Game, Belum Dikonfigurasi)
                    val allApps = uiState.installedAppList
                    val configuredPkgs = remember(uiState.appProfileRules) {
                        uiState.appProfileRules.map { it.packageName }.toSet()
                    }
                    val gameCount = remember(allApps) { allApps.count { it.isGame } }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("ALL", "Semua (${allApps.size})", AccentCyan),
                            Triple("GAME", "Game ($gameCount)", AccentOrange),
                            Triple("UNCONFIGURED", "Belum Dikonfigurasi", AccentGreen),
                        ).forEach { (key, label, accent) ->
                            val isSel = addAppFilter == key
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) accent.copy(alpha = 0.22f) else BgElevated,
                                border = BorderStroke(1.dp, if (isSel) accent else BorderGlass),
                                modifier = Modifier.weight(1f).clickable { addAppFilter = key }
                            ) {
                                Box(Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        color = if (isSel) accent else TextSecondary,
                                        fontSize = 9.5.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    val q = customPkgText.trim().lowercase()
                    val filteredApps = remember(allApps, q, addAppFilter, configuredPkgs) {
                        allApps.filter { app ->
                            val matchQuery = q.isBlank() || app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q)
                            val matchCat = when (addAppFilter) {
                                "GAME" -> app.isGame
                                "UNCONFIGURED" -> !configuredPkgs.contains(app.packageName)
                                else -> true
                            }
                            matchQuery && matchCat
                        }
                    }

                    if (filteredApps.isEmpty() && allApps.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("Memuat daftar aplikasi...", color = TextSecondary, fontSize = 11.5.sp)
                        }
                    } else if (filteredApps.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("Tidak ada aplikasi yang cocok.", color = TextSecondary, fontSize = 11.5.sp)
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredApps.size) { i ->
                                val app = filteredApps[i]
                                val existingRule = uiState.appProfileRules.find { it.packageName == app.packageName }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BgElevated,
                                    border = BorderStroke(1.dp, if (existingRule != null) AccentCyan.copy(alpha = 0.4f) else BorderGlass),
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        if (existingRule != null) {
                                            editingRule = existingRule
                                        } else {
                                            val newRule = AppProfileRule(
                                                packageName = app.packageName,
                                                appName = app.label,
                                                targetProfile = if (app.isGame) "extreme" else selectedTargetProfile,
                                                enabled = true,
                                                targetRefreshRate = if (app.isGame) 120 else null,
                                                autoFloatingHud = app.isGame,
                                                isGame = app.isGame
                                            )
                                            editingRule = newRule
                                        }
                                        showAddAppDialog = false
                                    }
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AppIconImage(
                                                packageName = app.packageName,
                                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(6.dp))
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        app.label,
                                                        color = TextPrimary,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    if (app.isGame) {
                                                        Spacer(Modifier.width(5.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = AccentOrange.copy(alpha = 0.18f),
                                                            border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.4f))
                                                        ) {
                                                            Text(
                                                                "GAME",
                                                                color = AccentOrange,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    app.packageName,
                                                    color = TextSecondary.copy(alpha = 0.8f),
                                                    fontSize = 9.5.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        if (existingRule != null) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = AccentCyan.copy(alpha = 0.15f),
                                                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f))
                                            ) {
                                                Text(
                                                    existingRule.targetProfile.uppercase(),
                                                    color = AccentCyan,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (customPkgText.isNotBlank()) {
                    TextButton(onClick = {
                        val pkg = customPkgText.trim()
                        val label = pkg.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkg
                        val newRule = AppProfileRule(
                            packageName = pkg,
                            appName = label,
                            targetProfile = selectedTargetProfile,
                            enabled = true
                        )
                        editingRule = newRule
                        showAddAppDialog = false
                    }) {
                        Text("Konfigurasi Manual", color = AccentCyan, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAppDialog = false }) {
                    Text("Tutup", color = TextSecondary)
                }
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
            0 -> DashboardTabContent(uiState, viewModel, currentAccent, dashboardLazyState, dashboardFling, onExtremeConfirmRequired)
                1 -> TuningTabContent(uiState, viewModel, selectedCategory, { selectedCategory = it }, tuningHubLazyState, tuningHubFling, subscreenLazyState, subscreenFling, { showAddAppDialog = true }, { editingRule = it })

                // ── TAB 2 & 3: TOOLS & FLASHER ──────────────────────────────
                2, 3 -> ToolsTabContent(uiState, viewModel, toolsLazyState, toolsFling)
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
