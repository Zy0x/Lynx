package com.noir.lynx.ui

import android.os.Bundle
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll


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
class MainActivity : ComponentActivity() {

    private val viewModel: LynxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            LynxAppContent(uiState = uiState, viewModel = viewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setAppForeground(true)
    }

    override fun onStop() {
        super.onStop()
        viewModel.setAppForeground(false)
    }
}

// ============================================================
//  Root App Content
// ============================================================

@Composable
fun LynxAppContent(uiState: LynxUiState, viewModel: LynxViewModel) {
    val context = LocalContext.current
    var showExtremeDialog by remember { mutableStateOf(false) }

    // Extreme mode confirmation dialog
    if (showExtremeDialog) {
        AlertDialog(
            onDismissRequest = { showExtremeDialog = false },
            containerColor = BgCard,
            titleContentColor = AccentRed,
            textContentColor = TextSecondary,
            title = { Text("Mode Extreme — Peringatan Bahaya") },
            text = {
                Text(
                    "Mode Extreme mengunci CPU & GPU pada frekuensi maksimum mutlak dan menonaktifkan " +
                    "seluruh proteksi termal OEM. Direkomendasikan HANYA dengan phone cooler eksternal.\n\n" +
                    "Penggunaan tanpa pendingin aktif dapat memicu degradasi baterai permanen dan komponen SoC."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setProfile("extreme", context)
                        showExtremeDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = AccentRed),
                ) {
                    Text("Aktifkan Extreme", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtremeDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            },
        )
    }

    if (uiState.showBenchmarkDialog) {
        BenchmarkStudioDialog(uiState = uiState, viewModel = viewModel)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeepOled)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        when {
            uiState.isLoading -> LoadingScreen()
            !uiState.isRootAvailable -> ErrorScreen(uiState)
            else -> MainDashboard(
                uiState = uiState,
                viewModel = viewModel,
                onExtremeConfirmRequired = { showExtremeDialog = true },
            )
        }

        // Success snackbar overlay
        uiState.successMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp, start = 16.dp, end = 16.dp),
                action = {
                    TextButton(onClick = { viewModel.dismissSuccess() }) {
                        Text("OK", color = AccentCyan, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = BgElevated,
                contentColor = TextPrimary,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(msg)
            }
        }

        // Error snackbar overlay
        uiState.errorMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp, start = 16.dp, end = 16.dp),
                action = {
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("Tutup", color = AccentOrange, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = BgElevated,
                contentColor = TextPrimary,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(msg)
            }
        }
    }
}

// ============================================================
//  Loading Screen
// ============================================================

@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = AccentCyan,
                strokeWidth = 3.dp,
                modifier = Modifier.size(42.dp)
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Menginisialisasi Kernel Root Engine...",
                color = TextSecondary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ============================================================
//  Error / Non-Root Screen
// ============================================================

@Composable
fun ErrorScreen(uiState: LynxUiState) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = BgCard,
            border = BorderStroke(1.dp, BorderGlass),
            modifier = Modifier.padding(28.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = AccentOrange.copy(alpha = 0.15f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Akses Superuser Diperlukan",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = uiState.errorMessage ?: "Akses Root tidak terdeteksi. Berikan izin Superuser melalui Magisk, KernelSU, atau APatch untuk mengontrol parameter kernel.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

// ============================================================
//  App Icon Loader Composable
// ============================================================

private val appIconMemoryCache = android.util.LruCache<String, ImageBitmap>(150)

@Composable
fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmapState = remember(packageName) {
        appIconMemoryCache.get(packageName) ?: try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            val bmp = Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            val img = bmp.asImageBitmap()
            appIconMemoryCache.put(packageName, img)
            img
        } catch (_: Exception) {
            null
        }
    }
    if (bitmapState != null) {
        Image(
            bitmap = bitmapState,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Icon(
            Icons.Default.Android,
            contentDescription = null,
            tint = AccentCyan,
            modifier = modifier
        )
    }
}

// ============================================================
//  Main Dashboard — 4-Tab Luxury Material 3 Navigation
// ============================================================

@Composable
fun MainDashboard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onExtremeConfirmRequired: () -> Unit,
) {
    val state = uiState.state
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val subscreenScrollState = rememberScrollState()
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val isSubscreenActive = uiState.currentTab == 1 && selectedCategory != null
    var isNavbarVisible by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(uiState.currentTab) {
        scrollState.scrollTo(0)
        isNavbarVisible = true
    }
    LaunchedEffect(Unit) {
        viewModel.syncHudPrefs(context)
    }
    BackHandler(enabled = uiState.currentTab == 1 && selectedCategory != null) {
        selectedCategory = null
    }
    LaunchedEffect(selectedCategory, uiState.selectedCpuTab, uiState.selectedGpuTab) {
        subscreenScrollState.scrollTo(0)
        scrollState.scrollTo(0)
        isNavbarVisible = true
    }

    val activeScrollValue = if (isSubscreenActive) subscreenScrollState.value else scrollState.value
    LaunchedEffect(activeScrollValue) {
        if (activeScrollValue <= 10 && !isNavbarVisible) {
            isNavbarVisible = true
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < -12f) {
                    if (isNavbarVisible) isNavbarVisible = false
                } else if (delta > 12f) {
                    if (!isNavbarVisible) isNavbarVisible = true
                }
                return Offset.Zero
            }
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
            .nestedScroll(nestedScrollConnection)
    ) {
        // ── 1. Full Edge-to-Edge Scrollable Content (Glides behind Navbar) ────
        val contentModifier = if (isSubscreenActive) {
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        } else {
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
        }

        Column(
            modifier = contentModifier,
        ) {
            LaunchedEffect(uiState.currentTab) {
                scrollState.scrollTo(0)
            }

            when (uiState.currentTab) {
                // ── TAB 0: DASHBOARD ────────────────────────────────────────
                0 -> {
                    // ── Clean Minimalist Top Header (Dashboard Only) ────────────
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = BgCard,
                        border = BorderStroke(0.8.dp, BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            // Top Row: Brand & Status Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = BgElevated,
                                        border = BorderStroke(0.8.dp, BorderSubtle),
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Bolt,
                                                contentDescription = null,
                                                tint = currentAccent,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Lynx Kernel",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            letterSpacing = 0.sp,
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (uiState.isModuleInstalled) currentAccent else AccentOrange,
                                                modifier = Modifier.size(6.dp)
                                            ) {}
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (uiState.isModuleInstalled) "Magisk Deity Active" else "Standalone Root Mode",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = if (uiState.isModuleInstalled) TextSecondary else AccentOrange,
                                            )
                                        }
                                    }
                                }
                                StatusBadge(profile = state.activeProfile)
                            }

                            // Bottom Row: Dynamic Accent Theme Picker
                            HorizontalDivider(
                                color = BorderGlass.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Palette,
                                        null,
                                        tint = currentAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "TEMA AKSEN",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(
                                        "cyan" to AccentCyan,
                                        "blue" to AccentBlue,
                                        "purple" to AccentPurple,
                                        "orange" to AccentOrange,
                                        "red" to AccentRed,
                                        "green" to AccentGreen
                                    ).forEach { (name, color) ->
                                        val isSelected = uiState.selectedAccent == name
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clickable { viewModel.setAccentColor(name) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = color,
                                                border = if (isSelected) BorderStroke(2.dp, Color.White) else null,
                                                modifier = Modifier.size(if (isSelected) 16.dp else 11.dp)
                                            ) {}
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LiveTelemetryCard(telemetry = uiState.telemetry)

                    LynxCard(
                        title = "Profil Performa Kernel",
                        icon = Icons.Default.Speed,
                        accentColor = AccentOrange
                    ) {
                        ProfileGrid(
                            currentProfile = state.activeProfile,
                            onProfileSelected = { viewModel.setProfile(it, context) },
                            onExtremeConfirmRequired = onExtremeConfirmRequired,
                        )
                    }

                    // ── Live Floating Game HUD & OSD Card ─────────────────
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

                    // ── Live Hardware Benchmark & Frame Profiler Studio Card ─
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

                    // ── Live Battery Wattage & Thermal Power Card ───────────
                    uiState.telemetry?.let { tel ->
                        LynxCard(
                            title = "Estimasi Daya & Konsumsi Watt",
                            icon = Icons.Default.Bolt,
                            accentColor = AccentBlue
                        ) {
                            val v = tel.battVoltMv.toDouble()
                            val c = Math.abs(tel.battCurrentMa.toDouble())
                            val watts = if (v > 0 && c > 0) (v * c) / 1_000_000.0 else 0.0
                            val isCharging = tel.battCurrentMa > 0
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        if (watts > 0) String.format("%.2f Watt", watts) else "Daya Terukur: --",
                                        color = if (isCharging) AccentCyan else AccentOrange,
                                        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        if (isCharging) "Sedang Mengisi Daya (Input Power)" else "Konsumsi Baterai Aktif (Discharging)",
                                        color = TextSecondary, fontSize = 11.sp
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BgElevated,
                                    border = BorderStroke(0.8.dp, BorderSubtle)
                                ) {
                                    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Thermostat, null, tint = AccentRed, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("${tel.temp}°C", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Tegangan: ${tel.battVoltMv} mV", color = TextSecondary, fontSize = 11.sp)
                                Text("Arus: ${tel.battCurrentMa} mA", color = TextSecondary, fontSize = 11.sp)
                                Text("Baterai: ${tel.battLevel}%", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            uiState.batteryDetails?.let { batt ->
                                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Status: ${batt.health}", color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    if (batt.cycleCount >= 0) {
                                        Text("Siklus: ${batt.cycleCount}x", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (batt.chargeCounterMah > 0) {
                                        Text("Kapasitas: ${batt.chargeCounterMah} mAh", color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // ── FKM Parity: Battery Health & Deep Sleep Card ───────────
                    BatteryHealthStatsCard(
                        batteryHealth = uiState.batteryHealthStats,
                        onRefresh = { viewModel.refreshBatteryHealth() }
                    )

                    // ── Live Hardware Thermal Zones Matrix ─────────────────
                    if (uiState.thermalZones.isNotEmpty()) {
                        LynxCard(
                            title = "Matriks Sensor Termal Hardware",
                            icon = Icons.Default.Thermostat,
                            accentColor = AccentRed
                        ) {
                            val zones = uiState.thermalZones.filter { it.tempC in 15f..110f }
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


                // ── TAB 1: TUNING ENGINE (UNIFIED HUB & SUBSCREENS) ────
                1 -> {
                    AnimatedContent(
                        targetState = selectedCategory,
                        modifier = if (isSubscreenActive) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
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
                            Column(modifier = Modifier.fillMaxWidth()) {
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

                                // Vertical List of Full-width Subsystem Cards
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // 1. CPU & Governor
                                    SubsystemCategoryCard(
                                        category = TuningCategory.CPU,
                                        badgeText = "${uiState.cpuCores.count { it.isOnline }}/${uiState.cpuCores.size} Cores",
                                        detailText1 = "${uiState.clusters.size} Cluster",
                                        detailText2 = uiState.activeGovernorPreset.ifBlank { "Schedutil" },
                                        onClick = { selectedCategory = "cpu" }
                                    )

                                    // 2. GPU & Display
                                    SubsystemCategoryCard(
                                        category = TuningCategory.GPU,
                                        badgeText = if (uiState.gpuInfo.curFreqMhz > 0) "${uiState.gpuInfo.curFreqMhz} MHz" else "Dynamic",
                                        detailText1 = "Load ${uiState.gpuInfo.gpuLoadPercent}%",
                                        detailText2 = "${uiState.displayRefreshRate}Hz",
                                        onClick = { selectedCategory = "gpu" }
                                    )

                                    // 3. Thermal & Anti-Throttling
                                    val validThermalZones = uiState.thermalZones.filter { it.tempC in 15f..115f }
                                    val maxT = validThermalZones.maxOfOrNull { it.tempC } ?: (uiState.telemetry?.temp?.toFloat() ?: 0f)
                                    SubsystemCategoryCard(
                                        category = TuningCategory.THERMAL,
                                        badgeText = if (state.thermal.fullBypass) "BYPASS AKTIF" else "${state.thermal.customTempLimitC}°C LIMIT",
                                        detailText1 = if (maxT > 0) "Maks ${String.format("%.1f", maxT)}°C" else "Normal",
                                        detailText2 = if (state.oemNeutralizer.joyoseNeutralize) "Anti-Joyose" else "${validThermalZones.size} Sensor",
                                        onClick = { selectedCategory = "thermal" }
                                    )

                                    // 4. Battery & Charging
                                    SubsystemCategoryCard(
                                        category = TuningCategory.CHARGING,
                                        badgeText = if (state.charging.bypassEnabled) "BYPASS" else if (state.charging.extremeChargingEnabled) "EXTREME" else "${state.charging.limitCurrentMa} mA",
                                        detailText1 = "${uiState.batteryDetails?.tempC ?: 0f}°C",
                                        detailText2 = "${uiState.batteryDetails?.currentMa ?: 0} mA",
                                        onClick = { selectedCategory = "charging" }
                                    )

                                    // 5. Memory & Storage
                                    SubsystemCategoryCard(
                                        category = TuningCategory.MEMORY,
                                        badgeText = "ZRAM ${uiState.zramCompAlgorithm.ifBlank { "lz4" }.uppercase()}",
                                        detailText1 = "Swap ${state.memory.swappiness}%",
                                        detailText2 = "${uiState.ioDevices.size} I/O Dev",
                                        onClick = { selectedCategory = "memory" }
                                    )

                                    // 6. Network & Audio
                                    SubsystemCategoryCard(
                                        category = TuningCategory.NETWORK,
                                        badgeText = uiState.currentTcpCongestion.ifBlank { "bbr" }.uppercase(),
                                        detailText1 = if (state.network.wifiPingStabilizer) "FQ-CoDel" else "Standard",
                                        detailText2 = if (state.audio.lowLatencyMmap) "MMAP" else "Audio",
                                        onClick = { selectedCategory = "network" }
                                    )

                                    // 7. Subsystem & Automation
                                    SubsystemCategoryCard(
                                        category = TuningCategory.SYSTEM,
                                        badgeText = "${uiState.appProfileRules.size} Aturan",
                                        detailText1 = if (uiState.isAppAutomationActive) "Auto ON" else "Auto OFF",
                                        detailText2 = "${uiState.deepTunables.size} Tunables",
                                        onClick = { selectedCategory = "system" }
                                    )
                                }
                            }
                        } else {
                            // ── SUB-PAGE VIEW WITH PERMANENT STICKY PINNED HEADER ──
                            val cat = TuningCategory.fromId(currentCategory) ?: TuningCategory.CPU

                            Column(modifier = Modifier.fillMaxSize()) {
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
                                                onClick = { selectedCategory = null },
                                                shape = CircleShape,
                                                color = BgElevated,
                                                border = BorderStroke(0.8.dp, BorderSubtle),
                                                modifier = Modifier.size(42.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowBack,
                                                        contentDescription = "Kembali ke Pusat Tuning",
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
                                                        contentDescription = cat.title,
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
                                        }
                                        HorizontalDivider(
                                            color = Color(0xFF1E2026),
                                            thickness = 0.8.dp
                                        )
                                    }
                                }

                                // 1.5. Sticky Category Header (CPU Tabs & GPU & Display Tabs)
                                if (currentCategory == "cpu") {
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
                                }

                                // 2. Scrollable Subscreen Content Area
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .verticalScroll(subscreenScrollState)
                                ) {
                                    Spacer(Modifier.height(8.dp))
                                    when (currentCategory) {
                                        "cpu" -> TuningCpuCategory(uiState = uiState, viewModel = viewModel)
                                        "gpu" -> TuningGpuCategory(uiState = uiState, viewModel = viewModel)
                                        "thermal" -> TuningThermalCategory(uiState = uiState, viewModel = viewModel)
                                        "memory" -> TuningMemoryCategory(uiState = uiState, viewModel = viewModel)
                                        "charging" -> TuningChargingCategory(uiState = uiState, viewModel = viewModel)
                                        "network" -> TuningNetworkCategory(uiState = uiState, viewModel = viewModel)
                                        "system" -> TuningSystemCategory(
                                            uiState = uiState,
                                            viewModel = viewModel,
                                            onAddAppClick = {
                                                viewModel.refreshInstalledApps()
                                                showAddAppDialog = true
                                            },
                                            onEditRuleClick = { editingRule = it }
                                        )
                                        else -> TuningCpuCategory(uiState = uiState, viewModel = viewModel)
                                    }
                                    Spacer(modifier = Modifier.height(115.dp).navigationBarsPadding())
                                }
                            }
                        }
                    }
                }

                // ── TAB 2 & 3: TOOLS & FLASHER ──────────────────────────────
                2, 3 -> {
                    Spacer(Modifier.height(8.dp))
                    FlasherCard(
                        isFlashing = uiState.isFlashing,
                        flashLog = uiState.flashLog,
                        onFlash = { viewModel.flashKernel(it) }
                    )

                    BootBackupCard(
                        backups = uiState.backups,
                        isBackingUp = uiState.isBackingUp,
                        onBackup = { viewModel.backupBoot() },
                        onRestore = { viewModel.restoreBoot(it) }
                    )

                    LynxCard(
                        title = "Pemeliharaan & Diagnostik 1-Klik",
                        icon = Icons.Default.Build,
                        accentColor = AccentCyan
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

                    // ── SELinux & Kernel Printk Logging Card ───────────────────
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


                    // ── Custom Sysfs Rules & Boot Tweaks Card ───────────────────
                    LaunchedEffect(Unit) { viewModel.loadCustomRules() }

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

                        // Quick snippet chips & Reload button
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

                    // ── Dynamic Hardware Thermal Zones Card ────────────────────
                    LaunchedEffect(Unit) { viewModel.refreshThermalZones() }
                    if (uiState.thermalZones.isNotEmpty()) {
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
                                                    "${String.format("%.1f", tz.tempC)}°C",
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

                    // ── Wakelock Blocker & Deep Sleep Audit Card ─────────────────────
                    LaunchedEffect(Unit) { viewModel.refreshWakelocks() }
                    val wlInfo = uiState.wakelockBlockerInfo
                    LynxCard(
                        title = "Wakelock Blocker & Deep Sleep Audit",
                        icon = Icons.Default.Bedtime,
                        accentColor = AccentPurple
                    ) {
                        // Driver Status Header
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

                        // Aggressive Doze Switch
                        LynxSwitch(
                            label = "Aggressive Doze (Paksa Idle)",
                            subLabel = "Paksa status idle sistem saat layar mati untuk memotong konsumsi baterai standby",
                            checked = wlInfo.aggressiveDozeEnabled,
                            onCheckedChange = { viewModel.toggleAggressiveDoze(it) }
                        )

                        Spacer(Modifier.height(8.dp))

                        // Wakeup Sources List Header
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

                    // ── Kernel Capability Matrix & Hardware Node Inspector Card ──
                    LaunchedEffect(Unit) {
                        if (uiState.capabilityReport == null) {
                            viewModel.scanKernelCapabilities()
                        }
                    }
                    val capReport = uiState.capabilityReport
                    LynxCard(
                        title = "Kernel Capability Matrix & Node Inspector",
                        icon = Icons.Default.CheckCircle,
                        accentColor = AccentGreen
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                val score = capReport?.scorePercent ?: 0
                                Text(
                                    "Skor Kompatibilitas Kernel: $score%",
                                    color = if (score >= 70) AccentGreen else AccentOrange,
                                    fontWeight = FontWeight.Bold, fontSize = 13.sp
                                )
                                Text(
                                    capReport?.kernelRelease?.take(36) ?: "Memindai kernel...",
                                    color = TextSecondary, fontSize = 11.sp, maxLines = 1
                                )
                            }
                            IconButton(onClick = { viewModel.scanKernelCapabilities() }) {
                                Icon(Icons.Default.Refresh, "Rescan", tint = AccentGreen, modifier = Modifier.size(20.dp))
                            }
                        }

                        if (capReport == null) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = AccentGreen, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Memeriksa 9 subsistem hardware...", color = TextSecondary, fontSize = 12.sp)
                            }
                        } else {
                            capReport.items.forEach { cap ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = BgElevated,
                                    border = BorderStroke(1.dp, BorderGlass),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    cap.title,
                                                    color = TextPrimary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (cap.isSupported) AccentGreen.copy(alpha = 0.18f) else Color.Red.copy(alpha = 0.12f),
                                                    border = BorderStroke(1.dp, if (cap.isSupported) AccentGreen.copy(alpha = 0.5f) else Color.Red.copy(alpha = 0.3f))
                                                ) {
                                                    Text(
                                                        if (cap.isSupported) "SUPPORTED" else "UNAVAILABLE",
                                                        color = if (cap.isSupported) AccentGreen else Color.Red.copy(alpha = 0.8f),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                cap.detail,
                                                color = TextSecondary,
                                                fontSize = 10.5.sp,
                                                lineHeight = 14.sp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── FKM Parity: Custom Shell Script Manager Card ──────────
                    CustomScriptManagerCard(
                        scripts = uiState.customScripts,
                        onExecute = { viewModel.executeCustomScript(it) },
                        onSave = { viewModel.saveCustomScript(it) },
                        onDelete = { viewModel.deleteCustomScript(it) }
                    )

                    // ── FKM Parity: Live Kernel Dmesg Ring Buffer Viewer Card ─
                    DmesgViewerCard(
                        dmesgState = uiState.dmesgState,
                        onLoadLogs = { viewModel.loadDmesgLog(it) },
                        onExport = { viewModel.exportDmesgLog() }
                    )

                }
            }
            if (!isSubscreenActive) {
                // Bottom spacer so content can scroll completely clear of the floating dock
                Spacer(modifier = Modifier.height(115.dp).navigationBarsPadding())
            }
        }

        // ── 2. Subtle Bottom Atmospheric Vignette (Soft content pass-through) ──
        AnimatedVisibility(
            visible = isNavbarVisible,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(125.dp)
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
        }

        // ── 3. Model 1: Floating Dynamic Capsule Dock (True Overlay) ────────
        val haptic = LocalHapticFeedback.current
        val navItems = listOf(
            Triple("Dashboard", Icons.Default.Speed, 0),
            Triple("Tuning Engine", Icons.Default.Tune, 1),
            Triple("Tools", Icons.Default.Build, 2),
        )

        AnimatedVisibility(
            visible = isNavbarVisible,
            enter = slideInVertically(
                initialOffsetY = { it * 2 },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(animationSpec = tween(180)),
            exit = slideOutVertically(
                targetOffsetY = { it * 2 },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
            Surface(
                shape = CircleShape,
                color = Color(0xD90E121E), // Luxury Translucent OLED Glass (~85% opacity)
                border = BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color(0x40FFFFFF), // Specular light reflection on top edge
                            Color(0x18FFFFFF), // Mid translucent edge
                            Color(0x0AFFFFFF)  // Base glass edge
                        )
                    )
                ),
                shadowElevation = 14.dp,
                modifier = Modifier.shadow(
                    elevation = 18.dp,
                    shape = CircleShape,
                    spotColor = currentAccent.copy(alpha = 0.35f),
                    ambientColor = currentAccent.copy(alpha = 0.18f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = 0.76f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    navItems.forEach { (label, icon, index) ->
                        val isSelected = uiState.currentTab == index

                        val animatedBgColor by animateColorAsState(
                            targetValue = if (isSelected) currentAccent.copy(alpha = 0.16f) else Color.Transparent,
                            animationSpec = tween(durationMillis = 220),
                            label = "nav_bg_$index"
                        )
                        val animatedBorderColor by animateColorAsState(
                            targetValue = if (isSelected) currentAccent.copy(alpha = 0.45f) else Color.Transparent,
                            animationSpec = tween(durationMillis = 220),
                            label = "nav_border_$index"
                        )
                        val animatedIconTint by animateColorAsState(
                            targetValue = if (isSelected) currentAccent else TextSecondary.copy(alpha = 0.70f),
                            animationSpec = tween(durationMillis = 200),
                            label = "nav_icon_$index"
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.05f else 0.95f,
                            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                            label = "nav_scale_$index"
                        )
                        val horizontalPadding by animateDpAsState(
                            targetValue = if (isSelected) 16.dp else 12.dp,
                            animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow),
                            label = "nav_pad_$index"
                        )

                        Surface(
                            modifier = Modifier
                                .height(48.dp)
                                .defaultMinSize(minWidth = 48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (uiState.currentTab == 1 && index == 1) {
                                        selectedCategory = null
                                    } else {
                                        viewModel.switchTab(index)
                                    }
                                },
                            shape = CircleShape,
                            color = animatedBgColor,
                            border = BorderStroke(1.dp, animatedBorderColor),
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = horizontalPadding)
                                    .fillMaxHeight(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = animatedIconTint,
                                    modifier = Modifier
                                        .size(21.dp)
                                        .scale(iconScale)
                                )
                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = fadeIn(animationSpec = tween(180, delayMillis = 40)) +
                                            expandHorizontally(
                                                animationSpec = spring(
                                                    dampingRatio = 0.76f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                expandFrom = Alignment.Start
                                            ),
                                    exit = fadeOut(animationSpec = tween(120)) +
                                            shrinkHorizontally(
                                                animationSpec = spring(
                                                    dampingRatio = 0.76f,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                shrinkTowards = Alignment.Start
                                            )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = label,
                                            color = currentAccent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

// ============================================================
//  LIVE HARDWARE BENCHMARK & FRAME PACING STUDIO
// ============================================================

@Composable
fun BenchmarkStudioDialog(
    uiState: LynxUiState,
    viewModel: LynxViewModel
) {
    val result = uiState.benchmarkResult
    val isRunning = uiState.isBenchmarking
    val context = LocalContext.current
    var selectedDuration by remember { mutableStateOf(60) }

    AlertDialog(
        onDismissRequest = {
            if (!isRunning) viewModel.closeBenchmarkDialog()
        },
        containerColor = BgCard,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = AccentPurple.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.5f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("LIVE BENCHMARK STUDIO", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        Text("Hardware Frame Pacing (SurfaceFlinger)", color = TextSecondary, fontSize = 10.sp)
                    }
                }
                IconButton(onClick = { viewModel.closeBenchmarkDialog() }) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Target Game Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconImage(
                            packageName = uiState.benchmarkTargetPackage,
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.benchmarkTargetAppName.ifBlank { "Game Target" },
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = uiState.benchmarkTargetPackage,
                                color = TextSecondary,
                                fontSize = 10.5.sp,
                                maxLines = 1
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentCyan.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = uiState.state.activeProfile.uppercase(),
                                color = AccentCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // If Benchmarking is in progress:
                if (isRunning) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = {
                                        val total = uiState.benchmarkTotalSeconds.coerceAtLeast(1).toFloat()
                                        val cur = uiState.benchmarkProgressSeconds.toFloat()
                                        (1f - (cur / total)).coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier.size(72.dp),
                                    color = AccentOrange,
                                    trackColor = BgDeepOled,
                                    strokeWidth = 5.dp
                                )
                                Text(
                                    text = "${uiState.benchmarkProgressSeconds}s",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                            }
                            Text(
                                text = "Merekam Presentation Timestamps...",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Silakan beralih ke game dan gerakkan kamera 3D, eksplorasi, atau rotasi layar secara intensif.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                            OutlinedButton(
                                onClick = { viewModel.cancelBenchmark() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Batalkan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (result != null) {
                    // ── BENCHMARK RESULTS VIEW ──────────────────────
                    // Hero Score Banner
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("AVERAGE RENDER FPS", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${result.averageFps} FPS",
                                        color = when {
                                            result.averageFps >= 50f -> AccentCyan
                                            result.averageFps >= 35f -> AccentGreen
                                            else -> AccentOrange
                                        },
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("MEDIAN FRAMETIME", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${result.medianFrametimeMs} ms",
                                        color = TextPrimary,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = BorderGlass, thickness = 1.dp)
                            Spacer(Modifier.height(8.dp))

                            // Stability Verdict
                            val (verdictText, verdictColor) = when {
                                result.jankyFramesPercent <= 5f -> "Ultra Smooth — Frame Pacing Sangat Stabil" to AccentGreen
                                result.jankyFramesPercent <= 15f -> "Sangat Baik — Stabilitas Tinggi" to AccentCyan
                                else -> "Variasi Frametime Terdeteksi" to AccentOrange
                            }
                            Text(
                                text = verdictText,
                                color = verdictColor,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Key Metrics Grid (6 Tiles)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BenchmarkMetricTile("1% LOW FPS", "${result.fps1PercentLow} FPS", AccentCyan, Modifier.weight(1f))
                            BenchmarkMetricTile("0.1% LOW FPS", "${result.fps01PercentLow} FPS", AccentBlue, Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BenchmarkMetricTile("FRAMETIME JITTER", "±${result.frametimeJitterMs} ms", AccentPurple, Modifier.weight(1f))
                            BenchmarkMetricTile("JANKY FRAMES", "${result.jankyFramesCount} (${result.jankyFramesPercent}%)", if (result.jankyFramesPercent > 10f) AccentRed else AccentGreen, Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BenchmarkMetricTile("PEAK / MIN LATENCY", "${result.minFrametimeMs} ms", AccentGreen, Modifier.weight(1f))
                            BenchmarkMetricTile("MAX SPIKE", "${result.maxFrametimeMs} ms", AccentOrange, Modifier.weight(1f))
                        }
                    }

                    // Hardware Telemetry during benchmark
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("TELEMETRI HARDWARE SAAT PENGUJIAN", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TelemetryMiniStat("CPU Avg", if (result.avgCpuClockMhz > 0) "${result.avgCpuClockMhz} MHz" else "--")
                                TelemetryMiniStat("GPU Avg", if (result.avgGpuClockMhz > 0) "${result.avgGpuClockMhz} MHz (${result.avgGpuLoadPct}%)" else "--")
                                TelemetryMiniStat("Suhu", if (result.avgBatteryTempC > 0f) "${result.avgBatteryTempC}°C" else "--")
                                TelemetryMiniStat("Daya", if (result.avgBatteryWatt > 0f) "${result.avgBatteryWatt} W" else "--")
                            }
                        }
                    }

                    // Interactive Frametime Line Chart
                    if (result.frametimes.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BgElevated,
                            border = BorderStroke(1.dp, BorderGlass),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("DISTRIBUSI FRAMETIME (${result.sampledFrames} FRAMES)", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("16.6ms (60 FPS)", color = AccentGreen, fontSize = 8.5.sp)
                                        Text("33.3ms (30 FPS)", color = AccentOrange, fontSize = 8.5.sp)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                FrametimeCanvasChart(frametimes = result.frametimes, modifier = Modifier.fillMaxWidth().height(100.dp))
                            }
                        }
                    }
                } else {
                    // Duration Picker & Start Prompt
                    Text("PILIH DURASI PENGUJIAN", color = TextSecondary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10 to "10s", 30 to "30s", 60 to "1 Min", 120 to "2 Min").forEach { (sec, label) ->
                            val isSel = selectedDuration == sec
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) AccentPurple.copy(alpha = 0.25f) else BgElevated,
                                border = BorderStroke(1.dp, if (isSel) AccentPurple else BorderGlass),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedDuration = sec }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) AccentPurple else TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AccentCyan.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Setelah menekan Mulai, beralihlah ke game dan gerakkan karakter untuk menguji kestabilan frame pacing nyata.",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isRunning) {
                if (result != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val reportText = """
                                    LYNX LIVE BENCHMARK REPORT
                                    Aplikasi: ${result.appName} (${result.appPackage})
                                    Profil Kernel: ${result.activeProfile.uppercase()}
                                    Rata-rata FPS: ${result.averageFps} FPS
                                    Median Frametime: ${result.medianFrametimeMs} ms
                                    1% Low FPS: ${result.fps1PercentLow} FPS
                                    Frametime Jitter: ±${result.frametimeJitterMs} ms
                                    Janky Frames (>33ms): ${result.jankyFramesCount} (${result.jankyFramesPercent}%)
                                    CPU Avg: ${result.avgCpuClockMhz} MHz | GPU Avg: ${result.avgGpuClockMhz} MHz (${result.avgGpuLoadPct}%)
                                    Suhu: ${result.avgBatteryTempC}°C | Daya: ${result.avgBatteryWatt} W
                                """.trimIndent()
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Lynx Benchmark", reportText)
                                clipboard.setPrimaryClip(clip)
                                viewModel.openBenchmarkDialog(result.appPackage)
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderGlass)
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Salin", fontSize = 11.5.sp)
                        }

                        Button(
                            onClick = { viewModel.startBenchmark(selectedDuration, uiState.benchmarkTargetPackage) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Uji Ulang", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.startBenchmark(selectedDuration, uiState.benchmarkTargetPackage) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mulai Live Benchmark", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        },
        dismissButton = {
            if (!isRunning) {
                TextButton(onClick = { viewModel.closeBenchmarkDialog() }) {
                    Text("Tutup", color = TextSecondary)
                }
            }
        }
    )
}

@Composable
fun FrametimeCanvasChart(
    frametimes: List<Float>,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (frametimes.isEmpty()) return@Canvas

        val w = size.width
        val h = size.height

        val maxVal = 50f
        val y60fps = h - ((16.66f / maxVal) * h).coerceIn(0f, h)
        val y30fps = h - ((33.33f / maxVal) * h).coerceIn(0f, h)

        // Draw 60 FPS guide line (Green)
        drawLine(
            color = Color(0x6610B981),
            start = Offset(0f, y60fps),
            end = Offset(w, y60fps),
            strokeWidth = 1.5f
        )

        // Draw 30 FPS guide line (Orange)
        drawLine(
            color = Color(0x66FF9F2E),
            start = Offset(0f, y30fps),
            end = Offset(w, y30fps),
            strokeWidth = 1.5f
        )

        val count = frametimes.size
        val stepX = w / count.coerceAtLeast(1)

        val path = Path()

        for (i in frametimes.indices) {
            val ft = frametimes[i]
            val x = i * stepX
            val y = h - ((ft / maxVal) * h).coerceIn(0f, h)

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }

            if (ft > 33.33f) {
                drawCircle(
                    color = AccentRed,
                    radius = 3f,
                    center = Offset(x, y)
                )
            }
        }

        drawPath(
            path = path,
            color = AccentCyan,
            style = Stroke(width = 2f)
        )
    }
}

@Composable
private fun BenchmarkMetricTile(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgElevated,
        border = BorderStroke(1.dp, BorderGlass),
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, color = TextSecondary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(value, color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TelemetryMiniStat(label: String, value: String) {
    Column {
        Text(label, color = TextSecondary, fontSize = 8.sp)
        Text(value, color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
    }
}


