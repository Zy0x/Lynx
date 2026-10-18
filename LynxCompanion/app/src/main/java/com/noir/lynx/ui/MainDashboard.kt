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
        var targetProf by remember(rule) { mutableStateOf(rule.targetProfile) }
        var targetHz by remember(rule) { mutableStateOf(rule.targetRefreshRate) }
        var autoHud by remember(rule) { mutableStateOf(rule.autoFloatingHud) }
        var isRuleEnabled by remember(rule) { mutableStateOf(rule.enabled) }

        val isExistingRule = remember(rule, uiState.appProfileRules) {
            uiState.appProfileRules.any { it.packageName == rule.packageName }
        }

        Dialog(
            onDismissRequest = { editingRule = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 660.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Header Terpadu & Minimalis
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BgDeepOled.copy(alpha = 0.7f),
                                border = BorderStroke(0.8.dp, BorderGlass),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    AppIconImage(
                                        packageName = rule.packageName,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = rule.appName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
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
                                    color = TextSecondary.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            onClick = { editingRule = null },
                            shape = CircleShape,
                            color = BgDeepOled.copy(alpha = 0.7f),
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Tutup",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Pengaturan Konfigurasi Terstruktur & Terpadu
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                        text = "Status Otomasi Aplikasi",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Aktifkan profil kustom saat aplikasi dibuka",
                                        color = TextSecondary.copy(alpha = 0.7f),
                                        fontSize = 10.5.sp
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
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BgDeepOled.copy(alpha = 0.6f),
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Profil Performa Kernel",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Pilih profil CPU & GPU saat aplikasi di foreground",
                                    color = TextSecondary.copy(alpha = 0.7f),
                                    fontSize = 10.5.sp
                                )
                                Spacer(Modifier.height(10.dp))

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BgElevated.copy(alpha = 0.7f),
                                    border = BorderStroke(0.6.dp, BorderGlass.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(2.5.dp),
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
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isSel) accentColor.copy(alpha = 0.22f) else Color.Transparent,
                                                border = if (isSel) BorderStroke(0.8.dp, accentColor.copy(alpha = 0.6f)) else null,
                                                modifier = Modifier.weight(1f).fillMaxHeight()
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = meta.first,
                                                        color = if (isSel) accentColor else TextSecondary.copy(alpha = 0.8f),
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Kunci Refresh Rate Layar
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BgDeepOled.copy(alpha = 0.6f),
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Kunci Refresh Rate Layar",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Paksa refresh rate display panel saat aplikasi aktif",
                                    color = TextSecondary.copy(alpha = 0.7f),
                                    fontSize = 10.5.sp
                                )
                                Spacer(Modifier.height(10.dp))

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BgElevated.copy(alpha = 0.7f),
                                    border = BorderStroke(0.6.dp, BorderGlass.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(2.5.dp),
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
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isSel) AccentPurple.copy(alpha = 0.22f) else Color.Transparent,
                                                border = if (isSel) BorderStroke(0.8.dp, AccentPurple.copy(alpha = 0.6f)) else null,
                                                modifier = Modifier.weight(1f).fillMaxHeight()
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = label,
                                                        color = if (isSel) AccentPurple else TextSecondary.copy(alpha = 0.8f),
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                }
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
                                        text = "Floating Game HUD (OSD)",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "Tampilkan overlay FPS, suhu & watt real-time",
                                        color = TextSecondary.copy(alpha = 0.7f),
                                        fontSize = 10.5.sp
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

                    Spacer(Modifier.height(16.dp))

                    // Tombol Aksi Simetris & Seimbang
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isExistingRule) {
                            Surface(
                                onClick = {
                                    viewModel.deleteAppProfileRule(context, rule.packageName)
                                    viewModel.removeAppFromPerf(rule.packageName)
                                    editingRule = null
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = AccentRed.copy(alpha = 0.12f),
                                border = BorderStroke(0.8.dp, AccentRed.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = null,
                                            tint = AccentRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Hapus Aturan",
                                            color = AccentRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

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
                            shape = RoundedCornerShape(12.dp),
                            color = AccentCyan.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.65f)),
                            modifier = Modifier
                                .weight(if (isExistingRule) 1.35f else 1f)
                                .height(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AccentCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Simpan Konfigurasi",
                                        color = AccentCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Add App Profile Rule Dialog ───────────────────
    if (showAddAppDialog) {
        val allApps = uiState.installedAppList
        val configuredPkgs = remember(uiState.appProfileRules) {
            uiState.appProfileRules.map { it.packageName }.toSet()
        }
        val availableApps = remember(allApps, configuredPkgs) {
            allApps.filter { !configuredPkgs.contains(it.packageName) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }
        val availableGameCount = remember(availableApps) { availableApps.count { it.isGame } }
        val availableRegularCount = remember(availableApps) { availableApps.count { !it.isGame } }

        val q = customPkgText.trim().lowercase()
        val filteredApps = remember(availableApps, q, addAppFilter) {
            availableApps.filter { app ->
                val matchQuery = q.isBlank() || app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q)
                val matchCat = when (addAppFilter) {
                    "GAME" -> app.isGame
                    "APP" -> !app.isGame
                    else -> true
                }
                matchQuery && matchCat
            }
        }

        LaunchedEffect(availableApps) {
            val pkgs = availableApps.take(25).map { it.packageName }
            AppIconLoader.preload(context, pkgs)
        }

        Dialog(
            onDismissRequest = { showAddAppDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = BgElevated,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.82f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // Header Terpadu & Minimalis
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
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
                                    if (availableApps.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = BgDeepOled.copy(alpha = 0.8f),
                                            border = BorderStroke(0.6.dp, BorderGlass)
                                        ) {
                                            Text(
                                                text = "${filteredApps.size}",
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
                                    text = "Ketuk aplikasi untuk konfigurasi profil",
                                    color = TextSecondary.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            onClick = { showAddAppDialog = false },
                            shape = CircleShape,
                            color = BgDeepOled.copy(alpha = 0.7f),
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Tutup",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Search Bar Kapsul OLED Gelap
                    Surface(
                        shape = RoundedCornerShape(12.dp),
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
                                    modifier = Modifier.size(24.dp)
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

                    // Filter Chips Kapsul Ramping dengan Badge Angka Terpisah (Anti-Glyph Glitch)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("ALL", "Semua" to availableApps.size, AccentCyan),
                            Triple("GAME", "Game" to availableGameCount, AccentOrange),
                            Triple("APP", "Aplikasi" to availableRegularCount, AccentCyan),
                        ).forEach { (key, meta, accent) ->
                            val (label, count) = meta
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
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) accent else TextSecondary,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isSel) accent.copy(alpha = 0.22f) else BgDeepOled.copy(alpha = 0.7f),
                                        border = BorderStroke(0.5.dp, if (isSel) accent.copy(alpha = 0.45f) else BorderGlass.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "$count",
                                            color = if (isSel) accent else TextSecondary.copy(alpha = 0.75f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
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
                    } else if (availableApps.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AccentGreen.copy(alpha = 0.7f),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Semua Aplikasi Terdaftar",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Seluruh aplikasi terpasang telah memiliki aturan profil.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
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
                        val addAppListState = rememberLazyListState()
                        LazyColumn(
                            state = addAppListState,
                            flingBehavior = rememberFluidFlingBehavior(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(
                                count = filteredApps.size,
                                key = { filteredApps[it].packageName },
                                contentType = { "add_app_item" }
                            ) { i ->
                                val app = filteredApps[i]
                                
                                Surface(
                                    onClick = {
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
                                        showAddAppDialog = false
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f).padding(end = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = BgDeepOled.copy(alpha = 0.5f),
                                                border = BorderStroke(0.6.dp, BorderGlass.copy(alpha = 0.4f)),
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    AppIconImage(
                                                        packageName = app.packageName,
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = app.label,
                                                        color = TextPrimary,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
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
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(Modifier.height(1.5.dp))
                                                Text(
                                                    text = app.packageName,
                                                    color = TextSecondary.copy(alpha = 0.7f),
                                                    fontSize = 10.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = CircleShape,
                                            color = BgDeepOled.copy(alpha = 0.6f),
                                            border = BorderStroke(0.6.dp, BorderGlass),
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Tambah",
                                                    tint = TextSecondary.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (i < filteredApps.size - 1) {
                                    HorizontalDivider(
                                        color = BorderGlass.copy(alpha = 0.2f),
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Manual package input fallback (if user entered text)
                    val trimmedInput = customPkgText.trim()
                    val isAlreadyConfigured = configuredPkgs.contains(trimmedInput)
                    if (trimmedInput.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            onClick = {
                                if (isAlreadyConfigured) {
                                    val existing = uiState.appProfileRules.find { it.packageName == trimmedInput }
                                    if (existing != null) {
                                        editingRule = existing
                                        showAddAppDialog = false
                                    }
                                } else {
                                    val pkg = trimmedInput
                                    val label = pkg.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: pkg
                                    val newRule = AppProfileRule(
                                        packageName = pkg,
                                        appName = label,
                                        targetProfile = selectedTargetProfile,
                                        enabled = true
                                    )
                                    editingRule = newRule
                                    showAddAppDialog = false
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isAlreadyConfigured) AccentOrange.copy(alpha = 0.12f) else AccentCyan.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, if (isAlreadyConfigured) AccentOrange.copy(alpha = 0.4f) else AccentCyan.copy(alpha = 0.5f)),
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
                                    if (isAlreadyConfigured) Icons.Default.Edit else Icons.Default.Add,
                                    null,
                                    tint = if (isAlreadyConfigured) AccentOrange else AccentCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isAlreadyConfigured) "Paket Sudah Terdaftar: Edit \"$trimmedInput\"" else "Konfigurasi Manual: \"$trimmedInput\"",
                                    color = if (isAlreadyConfigured) AccentOrange else AccentCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
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
