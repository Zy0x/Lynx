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

    // ── Edit App Profile Rule Dialog ───────────────────────
    if (editingRule != null) {
        val rule = editingRule!!
        var targetProf by remember(rule.packageName) { mutableStateOf(rule.targetProfile) }
        var targetHz by remember(rule.packageName) { mutableStateOf(rule.targetRefreshRate) }
        var autoHud by remember(rule.packageName) { mutableStateOf(rule.autoFloatingHud) }
        var isRuleEnabled by remember(rule.packageName) { mutableStateOf(rule.enabled) }

        val isExistingRule = remember(rule.packageName, uiState.appProfileRules) {
            uiState.appProfileRules.any { it.packageName == rule.packageName }
        }

        AlertDialog(
            onDismissRequest = { editingRule = null },
            containerColor = BgElevated,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .border(0.8.dp, BorderGlass, RoundedCornerShape(22.dp)),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppIconImage(
                        packageName = rule.packageName,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rule.appName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            if (rule.isGame) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AccentOrange.copy(alpha = 0.16f),
                                    border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "GAME",
                                        color = AccentOrange,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = rule.packageName,
                            fontSize = 11.sp,
                            color = TextSecondary.copy(alpha = 0.75f),
                            maxLines = 1
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Status Otomasi Aplikasi
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgDeepOled.copy(alpha = 0.6f),
                        border = BorderStroke(0.8.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    "Status Otomasi Aplikasi",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Aktifkan profil kustom saat aplikasi dibuka",
                                    color = TextSecondary.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = isRuleEnabled,
                                onCheckedChange = { isRuleEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = BgDeepOled,
                                    checkedTrackColor = AccentCyan,
                                    uncheckedThumbColor = TextSecondary.copy(alpha = 0.5f),
                                    uncheckedTrackColor = BgElevated
                                ),
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }

                    // 2. Profil Performa Kernel
                    Column {
                        Text(
                            text = "Profil Performa Kernel",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Pilih profil CPU & GPU saat aplikasi di foreground",
                            color = TextSecondary.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BgDeepOled.copy(alpha = 0.6f),
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    "extreme" to ("Ekstrem" to AccentRed),
                                    "performance" to ("Performa" to AccentOrange),
                                    "balance" to ("Seimbang" to AccentCyan),
                                    "powersave" to ("Hemat" to AccentGreen),
                                ).forEach { (prof, meta) ->
                                    val isSel = targetProf.equals(prof, ignoreCase = true)
                                    val accentColor = meta.second
                                    Surface(
                                        onClick = { targetProf = prof },
                                        shape = RoundedCornerShape(7.dp),
                                        color = if (isSel) accentColor.copy(alpha = 0.22f) else Color.Transparent,
                                        border = if (isSel) BorderStroke(0.8.dp, accentColor.copy(alpha = 0.6f)) else null,
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = meta.first,
                                                color = if (isSel) accentColor else TextSecondary,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Kunci Refresh Rate Layar
                    Column {
                        Text(
                            "Kunci Refresh Rate Layar",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Paksa refresh rate display panel saat aplikasi aktif",
                            color = TextSecondary.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BgDeepOled.copy(alpha = 0.6f),
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    null to "Bawaan",
                                    60 to "60 Hz",
                                    90 to "90 Hz",
                                    120 to "120 Hz"
                                ).forEach { (hz, label) ->
                                    val isSel = targetHz == hz
                                    Surface(
                                        onClick = { targetHz = hz },
                                        shape = RoundedCornerShape(7.dp),
                                        color = if (isSel) AccentPurple.copy(alpha = 0.22f) else Color.Transparent,
                                        border = if (isSel) BorderStroke(0.8.dp, AccentPurple.copy(alpha = 0.6f)) else null,
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                color = if (isSel) AccentPurple else TextSecondary,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Floating Game HUD (OSD) Toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgDeepOled.copy(alpha = 0.6f),
                        border = BorderStroke(0.8.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    "Floating Game HUD (OSD)",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Tampilkan overlay FPS, suhu & watt real-time",
                                    color = TextSecondary.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = autoHud,
                                onCheckedChange = { autoHud = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = BgDeepOled,
                                    checkedTrackColor = AccentCyan,
                                    uncheckedThumbColor = TextSecondary.copy(alpha = 0.5f),
                                    uncheckedTrackColor = BgElevated
                                ),
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Surface(
                    onClick = {
                        val updated = rule.copy(
                            targetProfile = targetProf,
                            targetRefreshRate = targetHz,
                            autoFloatingHud = autoHud,
                            enabled = isRuleEnabled
                        )
                        viewModel.addOrUpdateAppProfileRule(context, updated)
                        editingRule = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = AccentCyan.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)),
                    modifier = Modifier.height(40.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Simpan Konfigurasi",
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            },
            dismissButton = {
                if (isExistingRule) {
                    Surface(
                        onClick = {
                            viewModel.deleteAppProfileRule(context, rule.packageName)
                            viewModel.removeAppFromPerf(rule.packageName)
                            editingRule = null
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = AccentRed.copy(alpha = 0.12f),
                        border = BorderStroke(0.8.dp, AccentRed.copy(alpha = 0.4f)),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Hapus Aturan",
                                color = AccentRed,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    Surface(
                        onClick = { editingRule = null },
                        shape = RoundedCornerShape(10.dp),
                        color = BgDeepOled.copy(alpha = 0.6f),
                        border = BorderStroke(0.8.dp, BorderGlass),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Batal",
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        )
    }

    // ── Add App Profile Rule Dialog ───────────────────
    if (showAddAppDialog) {
        val allApps = uiState.installedAppList
        val configuredPkgs = remember(uiState.appProfileRules) {
            uiState.appProfileRules.map { it.packageName }.toSet()
        }
        val gameCount = remember(allApps) { allApps.count { it.isGame } }

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

        AlertDialog(
            onDismissRequest = { showAddAppDialog = false },
            containerColor = BgElevated,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .border(0.8.dp, BorderGlass, RoundedCornerShape(22.dp)),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BgDeepOled.copy(alpha = 0.7f),
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Pilih Aplikasi",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                if (allApps.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = BgDeepOled.copy(alpha = 0.8f),
                                        border = BorderStroke(0.6.dp, BorderGlass)
                                    ) {
                                        Text(
                                            text = "${allApps.size}",
                                            color = TextSecondary,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = "Daftarkan aturan optimasi per-aplikasi",
                                color = TextSecondary.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = { showAddAppDialog = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                ) {
                    // Search Bar Kapsul OLED Gelap
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgDeepOled.copy(alpha = 0.7f),
                        border = BorderStroke(
                            0.8.dp,
                            if (customPkgText.isNotEmpty()) AccentCyan.copy(alpha = 0.5f) else BorderGlass
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = if (customPkgText.isNotEmpty()) AccentCyan else TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (customPkgText.isEmpty()) {
                                    Text(
                                        text = "Cari nama atau package...",
                                        color = TextSecondary.copy(alpha = 0.45f),
                                        fontSize = 12.sp
                                    )
                                }
                                BasicTextField(
                                    value = customPkgText,
                                    onValueChange = { customPkgText = it },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Default
                                    ),
                                    cursorBrush = SolidColor(AccentCyan),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (customPkgText.isNotEmpty()) {
                                IconButton(
                                    onClick = { customPkgText = "" },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hapus teks",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Filter Chips Kapsul Ramping
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("ALL", "Semua (${allApps.size})", AccentCyan),
                            Triple("GAME", "Game ($gameCount)", AccentCyan),
                            Triple("UNCONFIGURED", "Belum Diatur", AccentCyan),
                        ).forEach { (key, label, accent) ->
                            val isSel = addAppFilter == key
                            Surface(
                                onClick = { addAppFilter = key },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) accent.copy(alpha = 0.16f) else BgDeepOled.copy(alpha = 0.5f),
                                border = BorderStroke(0.8.dp, if (isSel) accent.copy(alpha = 0.55f) else BorderGlass),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) accent else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // App List / Empty State
                    if (filteredApps.isEmpty() && allApps.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = AccentCyan,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("Memuat daftar aplikasi...", color = TextSecondary, fontSize = 11.5.sp)
                            }
                        }
                    } else if (filteredApps.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = TextSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Tidak Ada Aplikasi yang Cocok",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Coba cari kata kunci lain atau ubah filter.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(
                                count = filteredApps.size,
                                key = { filteredApps[it].packageName }
                            ) { i ->
                                val app = filteredApps[i]
                                val existingRule = uiState.appProfileRules.find { it.packageName == app.packageName }
                                
                                Surface(
                                    onClick = {
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
                                    },
                                    color = Color.Transparent,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 9.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AppIconImage(
                                                packageName = app.packageName,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = app.label,
                                                        color = TextPrimary,
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    if (app.isGame) {
                                                        Spacer(Modifier.width(6.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = AccentOrange.copy(alpha = 0.16f),
                                                            border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.4f))
                                                        ) {
                                                            Text(
                                                                "GAME",
                                                                color = AccentOrange,
                                                                fontSize = 8.5.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(Modifier.height(1.5.dp))
                                                Text(
                                                    text = app.packageName,
                                                    color = TextSecondary.copy(alpha = 0.75f),
                                                    fontSize = 10.5.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        if (existingRule != null) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = BgDeepOled.copy(alpha = 0.7f),
                                                border = BorderStroke(0.6.dp, BorderGlass)
                                            ) {
                                                Text(
                                                    text = existingRule.targetProfile.uppercase(),
                                                    color = AccentCyan,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (i < filteredApps.size - 1) {
                                    HorizontalDivider(
                                        color = BorderGlass.copy(alpha = 0.25f),
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Manual package input fallback (if user entered text not in apps)
                    if (customPkgText.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            onClick = {
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
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = AccentCyan.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Konfigurasi Manual: \"${customPkgText.trim()}\"",
                                    color = AccentCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                Surface(
                    onClick = { showAddAppDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    color = BgDeepOled.copy(alpha = 0.6f),
                    border = BorderStroke(0.8.dp, BorderGlass),
                    modifier = Modifier.height(38.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tutup",
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
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
