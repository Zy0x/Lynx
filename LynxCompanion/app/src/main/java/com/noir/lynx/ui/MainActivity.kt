package com.noir.lynx.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
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
            title = { Text("⚠️ Mode Extreme — Peringatan Bahaya") },
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

@Composable
fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmapState = remember(packageName) {
        try {
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
            bmp.asImageBitmap()
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
    LaunchedEffect(uiState.currentTab) {
        scrollState.scrollTo(0)
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
        var targetProf by remember(rule) { mutableStateOf(rule.targetProfile) }
        var targetHz by remember(rule) { mutableStateOf(rule.targetRefreshRate) }
        var autoHud by remember(rule) { mutableStateOf(rule.autoFloatingHud) }
        var isRuleEnabled by remember(rule) { mutableStateOf(rule.enabled) }

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
                            Triple("GAME", "🎮 Game ($gameCount)", AccentOrange),
                            Triple("UNCONFIGURED", "⚡ Belum Dikonfigurasi", AccentGreen),
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
                                                                "🎮 GAME",
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

    Scaffold(
        containerColor = BgDeepOled,
        bottomBar = {
            // Floating Luxury Pill Navigation Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xF2121522),
                border = BorderStroke(1.dp, BorderGlass),
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier.height(68.dp)
                ) {
                    val navItems = listOf(
                        Triple("Dashboard", Icons.Default.Speed, 0),
                        Triple("SoC Tuner", Icons.Default.Tune, 1),
                        Triple("Lynx Engine", Icons.Default.Bolt, 2),
                        Triple("Tools", Icons.Default.Build, 3),
                    )
                    navItems.forEach { (label, icon, index) ->
                        val isSelected = uiState.currentTab == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.switchTab(index) },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) currentAccent else TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) currentAccent else TextSecondary
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = currentAccent.copy(alpha = 0.16f),
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
        ) {
            LaunchedEffect(uiState.currentTab) {
                scrollState.scrollTo(0)
            }

            // ── Luxury Top Header ──────────────────────────────────────────────
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(22.dp),
                color = BgCard,
                border = BorderStroke(1.dp, BorderGlass)
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
                                shape = RoundedCornerShape(14.dp),
                                color = currentAccent.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, currentAccent.copy(alpha = 0.35f)),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = currentAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "LYNX ",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary,
                                        letterSpacing = 1.5.sp,
                                    )
                                    Text(
                                        text = "KERNEL",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = currentAccent,
                                        letterSpacing = 1.sp,
                                    )
                                }
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
                                        fontWeight = FontWeight.Medium,
                                        color = if (uiState.isModuleInstalled) currentAccent else AccentOrange,
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

            when (uiState.currentTab) {
                // ── TAB 0: DASHBOARD ────────────────────────────────────────
                0 -> {
                    LiveTelemetryCard(telemetry = uiState.telemetry)

                    LynxCard(
                        title = "PROFIL PERFORMA KERNEL",
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
                        title = "FLOATING GAME HUD & OSD",
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
                                        "HUD aktif! Sentuh pil HUD di layar untuk membuka detail CPU/GPU atau switch profil seketika.",
                                        color = TextPrimary,
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    // ── Live Hardware Benchmark & Frame Profiler Studio Card ─
                    LynxCard(
                        title = "LIVE HARDWARE BENCHMARK & FRAME PACING",
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
                            title = "ESTIMASI DAYA & KONSUMSI WATT",
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
                                    border = BorderStroke(1.dp, BorderGlass)
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
                                HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 8.dp))
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
                            title = "MATRIKS SENSOR TERMAL HARDWARE",
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


                // ── TAB 1: SOC TUNER ────────────────────────────────────────
                1 -> {
                    // ── CPU Topology & Core Architecture Card ───────────────
                    if (uiState.clusters.isNotEmpty()) {
                        LynxCard(
                            title = "TOPOLOGI CORE & ARSITEKTUR CPU",
                            icon = Icons.Default.Memory,
                            accentColor = AccentCyan
                        ) {
                            val totalCores = uiState.clusters.sumOf { cluster ->
                                cluster.cpus.trim().split(Regex("[ ,]+")).filter { it.isNotBlank() }.sumOf { s ->
                                    if (s.contains("-")) {
                                        val p = s.split("-")
                                        val start = p.getOrNull(0)?.toIntOrNull() ?: 0
                                        val end = p.getOrNull(1)?.toIntOrNull() ?: start
                                        (end - start + 1).coerceAtLeast(1)
                                    } else 1
                                }
                            }
                            Text(
                                "$totalCores Cores Total (${uiState.clusters.size} Hardware Clusters)",
                                color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.clusters.forEach { cluster ->
                                    val isBig = cluster.id > 0
                                    val clusterCores = cluster.cpus.trim().split(Regex("[ ,]+")).filter { it.isNotBlank() }.sumOf { s ->
                                        if (s.contains("-")) {
                                            val p = s.split("-")
                                            val start = p.getOrNull(0)?.toIntOrNull() ?: 0
                                            val end = p.getOrNull(1)?.toIntOrNull() ?: start
                                            (end - start + 1).coerceAtLeast(1)
                                        } else 1
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = BgElevated,
                                        border = BorderStroke(1.dp, if (isBig) AccentOrange.copy(alpha = 0.35f) else AccentCyan.copy(alpha = 0.35f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(Modifier.padding(10.dp)) {
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Policy ${cluster.id}", color = TextSecondary, fontSize = 10.5.sp)
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isBig) AccentOrange.copy(alpha = 0.18f) else AccentCyan.copy(alpha = 0.18f)
                                                ) {
                                                    Text(
                                                        if (isBig) "BIG" else "LITTLE",
                                                        color = if (isBig) AccentOrange else AccentCyan,
                                                        fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                "$clusterCores Cores",
                                                color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "CPU: ${cluster.cpus}",
                                                color = TextSecondary, fontSize = 10.5.sp
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "Gov: ${cluster.curGov}",
                                                color = if (isBig) AccentOrange else AccentCyan,
                                                fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "Max: ${cluster.curMax / 1000} MHz",
                                                color = TextSecondary, fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── CPU Cores Dynamic Matrix & Hotplug Card ─────────────
                    if (uiState.cpuCores.isNotEmpty()) {
                        LaunchedEffect(Unit) { viewModel.refreshCpuCores() }
                        LynxCard(
                            title = "MATRIKS CORE CPU & HOTPLUG",
                            icon = Icons.Default.Speed,
                            accentColor = AccentCyan
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    val onlineCount = uiState.cpuCores.count { it.isOnline }
                                    Text(
                                        "$onlineCount / ${uiState.cpuCores.size} Cores Aktif",
                                        color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp
                                    )
                                    Text(
                                        "Kontrol on/off per core untuk uji performa, termal, & daya",
                                        color = TextSecondary, fontSize = 11.sp
                                    )
                                }
                                Surface(
                                    onClick = { viewModel.setAllCpuCoresOnline() },
                                    shape = RoundedCornerShape(8.dp),
                                    color = AccentCyan.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        "Semua Online",
                                        color = AccentCyan,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            val cores = uiState.cpuCores
                            val rows = cores.chunked(2)
                            rows.forEach { pair ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pair.forEach { core ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = BgElevated,
                                            border = BorderStroke(
                                                1.dp,
                                                if (core.isOnline) AccentCyan.copy(alpha = 0.35f) else BorderGlass
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(7.dp)
                                                                .background(
                                                                    if (core.isOnline) AccentGreen else AccentRed,
                                                                    shape = CircleShape
                                                                )
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(
                                                            "CPU ${core.coreId}",
                                                            color = TextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(
                                                        if (core.isOnline) {
                                                            if (core.curFreqKhz > 0) "${core.curFreqKhz / 1000} MHz" else "Aktif"
                                                        } else "Offline",
                                                        color = if (core.isOnline) AccentCyan else TextSecondary,
                                                        fontSize = 10.5.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                                if (core.isSwitchable) {
                                                    Switch(
                                                        checked = core.isOnline,
                                                        onCheckedChange = { viewModel.setCpuCoreOnline(core.coreId, it) },
                                                        colors = SwitchDefaults.colors(
                                                            checkedThumbColor = AccentCyan,
                                                            checkedTrackColor = AccentCyan.copy(alpha = 0.3f),
                                                            uncheckedThumbColor = TextSecondary,
                                                            uncheckedTrackColor = BgCard
                                                        ),
                                                        modifier = Modifier.scale(0.8f)
                                                    )
                                                } else {
                                                    Text(
                                                        "Master",
                                                        color = TextSecondary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    CpuClusterTunerCard(
                        clusters = uiState.clusters,
                        governorTunables = uiState.governorTunables,
                        onFreqChange = { policyId, min, max -> viewModel.setClusterFrequency(policyId, min, max) },
                        onGovChange = { policyId, gov -> viewModel.setClusterGovernor(policyId, gov) },
                        onLoadTunables = { policyId, gov -> viewModel.loadGovernorTunables(policyId, gov) },
                        onTunableChange = { policyId, gov, key, value -> viewModel.setGovernorTunable(policyId, gov, key, value) }
                    )

                    LynxCard(
                        title = "OVERCLOCK & THERMAL MITIGATION",
                        icon = Icons.Default.LocalFireDepartment,
                        accentColor = AccentRed
                    ) {
                        LynxSwitch(
                            label = "Mode Kernel Overclock (OC)",
                            subLabel = "Aktifkan batas frekuensi tertinggi tanpa throttling awal",
                            checked = state.overclock.enabled,
                            onCheckedChange = { viewModel.setOverclockEnabled(it) },
                        )

                        var floorValue by remember(state.overclock.cpuFloorRatio) {
                            mutableFloatStateOf(state.overclock.cpuFloorRatio.toFloat())
                        }
                        LynxSlider(
                            label = "Floor Frekuensi CPU Gaming (Anti-Droop)",
                            value = floorValue,
                            onValueChange = { floorValue = it },
                            onValueChangeFinished = { viewModel.setCpuFloorRatio(floorValue.toInt()) },
                            valueRange = 60f..100f,
                            steps = 7,
                            displayValue = "${floorValue.toInt()}%",
                            accentColor = AccentOrange
                        )

                        LynxSwitch(
                            label = "Full Thermal Bypass (Unrestricted)",
                            subLabel = "Nonaktifkan pembatasan termal OEM (Phone cooler diwajibkan)",
                            checked = state.thermal.fullBypass,
                            onCheckedChange = { viewModel.setThermalBypass(it) },
                        )

                        var tempLimitValue by remember(state.thermal.customTempLimitC) {
                            mutableFloatStateOf(state.thermal.customTempLimitC.toFloat())
                        }
                        LynxSlider(
                            label = "Batas Suhu Thermal Custom",
                            value = tempLimitValue,
                            onValueChange = { tempLimitValue = it },
                            onValueChangeFinished = { viewModel.setCustomTempLimit(tempLimitValue.toInt()) },
                            valueRange = 45f..60f,
                            steps = 14,
                            displayValue = "${tempLimitValue.toInt()}°C",
                            accentColor = AccentRed
                        )

                        var uclampValue by remember(state.uclamp.gameMinRatio) {
                            mutableFloatStateOf(state.uclamp.gameMinRatio.toFloat())
                        }
                        LynxSlider(
                            label = "UCLAMP Task Clamping Min (Gaming)",
                            value = uclampValue,
                            onValueChange = { uclampValue = it },
                            onValueChangeFinished = { viewModel.setUclampGameMin(uclampValue.toInt()) },
                            valueRange = 30f..100f,
                            steps = 13,
                            displayValue = "${uclampValue.toInt()}%",
                            accentColor = AccentCyan
                        )
                    }

                    // ── GPU Advanced Control & Live Telemetry Card ──────────
                    LynxCard(
                        title = "KONTROL GPU & LIVE TELEMETRI",
                        icon = Icons.Default.SportsEsports,
                        accentColor = AccentOrange
                    ) {
                        val gpu = uiState.gpuInfo
                        val isMali = gpu.platform.contains("mali", ignoreCase = true)
                        val isAdreno = gpu.platform.contains("adreno", ignoreCase = true)
                        val platLabel = when {
                            isMali -> "MediaTek Mali GED"
                            isAdreno -> "Qualcomm Adreno QTI"
                            else -> "Universal GPU"
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (gpu.curFreqMhz > 0) "${gpu.curFreqMhz} MHz" else "GPU Siap Tuning",
                                    color = AccentOrange, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "Arsitektur: $platLabel",
                                    color = TextSecondary, fontSize = 11.sp
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BgElevated,
                                border = BorderStroke(1.dp, BorderGlass)
                            ) {
                                Text(
                                    if (gpu.maxFreqMhz > 0) "Maks: ${gpu.maxFreqMhz} MHz" else "Dynamic Clock",
                                    color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // GPU Load bar
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Beban Komputasi GPU", color = TextSecondary, fontSize = 11.sp)
                                Text("${gpu.gpuLoadPercent}%", color = if (gpu.gpuLoadPercent > 70) AccentRed else AccentOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { (gpu.gpuLoadPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = if (gpu.gpuLoadPercent > 70) AccentRed else AccentOrange,
                                trackColor = BgElevated,
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (isMali) "Tingkat MTK GED / GPU Boost" else "Tingkat Adreno Boost",
                            color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        val activeBoost = if (isMali) gpu.gedBoostLevel else gpu.adrenoBoostLevel
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Triple(0, "Mati (0)", "Hemat Daya"),
                                Triple(1, "Level 1", "Normal Boost"),
                                Triple(2, "Level 2", "Hardcore Boost")
                            ).forEach { (lvl, title, sub) ->
                                val isSelected = activeBoost == lvl
                                Surface(
                                    onClick = { viewModel.setGpuBoostLevel(lvl) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) AccentOrange.copy(alpha = 0.2f) else BgElevated,
                                    border = BorderStroke(1.dp, if (isSelected) AccentOrange else BorderGlass)
                                ) {
                                    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(title, color = if (isSelected) AccentOrange else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(sub, color = TextSecondary, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }

                    // ── Governor Tunables & Energy Presets Card ─────────────
                    LynxCard(
                        title = "TUNING GOVERNOR INTELIGEN (SCHEDUTIL)",
                        icon = Icons.Default.Tune,
                        accentColor = AccentCyan
                    ) {
                        Text(
                            "Optimasi waktu respon transisi frekuensi CPU (rate limit). Preset gaming mempercepat kenaikan clock ke frekuensi puncak (0µs ramp-up).",
                            color = TextSecondary, fontSize = 11.5.sp, lineHeight = 16.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val currentGovPreset = uiState.activeGovernorPreset
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Triple("responsive", "🚀 Responsif", "0µs ramp-up"),
                                Triple("balanced", "⚖️ Seimbang", "1000µs default"),
                                Triple("powersave", "🍃 Hemat Daya", "4000µs hemat")
                            ).forEach { (preset, label, note) ->
                                val isActive = currentGovPreset == preset
                                Surface(
                                    onClick = { viewModel.applyGovernorPreset(preset) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isActive) AccentCyan.copy(alpha = 0.2f) else BgElevated,
                                    border = BorderStroke(1.dp, if (isActive) AccentCyan else BorderGlass)
                                ) {
                                    Column(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(label, color = if (isActive) AccentCyan else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(note, color = TextSecondary, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }


                    // ── Display Refresh Rate & Touch Card ───────────────────────
                    LaunchedEffect(Unit) {
                        viewModel.refreshDisplayRefreshRate()
                    }
                    LynxCard(
                        title = "DISPLAY REFRESH RATE & TOUCH",
                        icon = Icons.Default.Smartphone,
                        accentColor = AccentCyan
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Kecepatan Refresh Layar (Display FPS)", color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Kunci refresh rate panel untuk pengalaman visual super mulus",
                                    color = TextSecondary, fontSize = 11.sp)
                            }
                            if (uiState.displayRefreshRate > 0) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AccentCyan.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        "${uiState.displayRefreshRate} Hz",
                                        color = AccentCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Dynamic refresh rate selector chips (Zero-Hardcoding)
                        val rates = uiState.supportedRefreshRates.ifEmpty { listOf(60, 90, 120) }
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rates.forEach { hz ->
                                val isSelected = uiState.displayRefreshRate == hz
                                Surface(
                                    onClick = { viewModel.setDisplayRefreshRate(hz) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) AccentCyan.copy(alpha = 0.22f) else BgElevated,
                                    border = BorderStroke(1.5.dp, if (isSelected) AccentCyan else BorderGlass)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    ) {
                                        Text(
                                            "${hz}Hz",
                                            color = if (isSelected) AccentCyan else TextPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            when (hz) {
                                                60 -> "Hemat"
                                                90 -> "Halus"
                                                120 -> "Gaming"
                                                144, 165 -> "Ultra"
                                                else -> "Tersedia"
                                            },
                                            color = if (isSelected) AccentCyan.copy(alpha = 0.8f) else TextSecondary,
                                            fontSize = 9.5.sp
                                        )
                                    }
                                }
                            }
                        }

                        LynxSwitch(
                            label = "TouchBoost & Responsivitas Sentuh",
                            subLabel = "Prioritaskan sampling rate sentuhan 240Hz+ pada driver input",
                            checked = state.displayTouch.touchboost,
                            onCheckedChange = { viewModel.setTouchboost(it) }
                        )
                    }

                    // ── GPU Advanced Control Card ──────────────────────────────
                    val gpu = uiState.gpuInfo
                    LynxCard(
                        title = "GPU ADVANCED CONTROL",
                        icon = Icons.Default.Devices,
                        accentColor = AccentBlue
                    ) {
                        // SoC Architecture Selector (Manual Override)
                        Text("Hardware SoC Architecture (Engine Profile)", color = TextPrimary, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                        val socOptions = listOf(
                            Pair("auto", "🤖 Auto AI"),
                            Pair("qcom", "🐉 Snapdragon"),
                            Pair("mtk", "⚡ MediaTek"),
                            Pair("generic", "🐧 Generic GKI")
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            socOptions.forEach { (socKey, socLabel) ->
                                val isSelected = (uiState.socOverride.ifEmpty { "auto" }) == socKey
                                Surface(
                                    onClick = { viewModel.setSocOverride(socKey) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) AccentBlue.copy(alpha = 0.22f) else BgElevated,
                                    border = BorderStroke(1.2.dp, if (isSelected) AccentBlue else BorderGlass)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                                    ) {
                                        Text(
                                            socLabel,
                                            color = if (isSelected) AccentBlue else TextSecondary,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(bottom = 10.dp))

                        // Platform badge
                        val platformLabel = when (gpu.platform) {
                            "adreno"   -> "Adreno (Qualcomm)"
                            "mali_ged" -> "Mali GED (MediaTek)"
                            else       -> "Generic GPU"
                        }
                        val maxBoost = if (gpu.platform == "mali_ged") 2 else 3
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Platform", color = TextSecondary, fontSize = 12.sp)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AccentBlue.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.4f))
                            ) {
                                Text(platformLabel, color = AccentBlue, fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }

                        if (gpu.platform != "generic") {
                            // GPU Boost Level selector
                            val boostLevel = if (gpu.platform == "adreno") gpu.adrenoBoostLevel else gpu.gedBoostLevel
                            val boostLabels = if (maxBoost == 3)
                                listOf("Off", "Low", "Medium", "High")
                            else
                                listOf("Off", "Medium", "High")

                            Text("GPU Boost Level", color = TextPrimary, fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                boostLabels.forEachIndexed { idx, label ->
                                    val isActive = boostLevel == idx
                                    Surface(
                                        onClick = { viewModel.setGpuBoostLevel(idx) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isActive) AccentBlue.copy(alpha = 0.22f) else BgElevated,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.5.dp,
                                            if (isActive) AccentBlue else BorderGlass
                                        )
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        ) {
                                            Text(label,
                                                color = if (isActive) AccentBlue else TextSecondary,
                                                fontSize = 11.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                                            Text("$idx", color = if (isActive) AccentBlue else TextSecondary, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }

                            // GPU Freq control & status (Adreno & MediaTek Mali)
                            if (gpu.availFreqsMhz.isNotEmpty()) {
                                Spacer(Modifier.height(14.dp))
                                Text("GPU Frequency Range", color = TextPrimary, fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Min: ${gpu.minFreqMhz} MHz", color = AccentCyan, fontSize = 12.sp)
                                    Text("Max: ${gpu.maxFreqMhz} MHz", color = AccentOrange, fontSize = 12.sp)
                                    Text("Now: ${gpu.curFreqMhz} MHz", color = TextSecondary, fontSize = 12.sp)
                                }
                                val minRange = gpu.availFreqsMhz.minOrNull()?.toFloat() ?: 0f
                                val maxRange = gpu.availFreqsMhz.maxOrNull()?.toFloat() ?: 1000f
                                var gpuMax by remember(gpu.maxFreqMhz) { mutableFloatStateOf(gpu.maxFreqMhz.toFloat()) }
                                LynxSlider(
                                    label = "Batas Maksimum GPU",
                                    value = gpuMax,
                                    onValueChange = { gpuMax = it },
                                    onValueChangeFinished = { viewModel.setGpuFreq(null, gpuMax.toInt()) },
                                    valueRange = minRange..maxRange,
                                    steps = (gpu.availFreqsMhz.size - 2).coerceAtLeast(0),
                                    displayValue = "${gpuMax.toInt()} MHz",
                                    accentColor = AccentBlue
                                )
                            }
                        } else {
                            Text(
                                "GPU node tidak terdeteksi di kernel ini. Kontrol GPU tidak tersedia.",
                                color = TextSecondary, fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    // ── FKM Parity: Voltage Control (Undervolting) Card ───────
                    VoltageControlCard(
                        voltageInfo = uiState.voltageInfo,
                        onApplyOffset = { viewModel.applyVoltageOffset(it) }
                    )

                    // ── FKM Parity: Display Calibration (KCAL & HBM) Card ────
                    DisplayCalibrationCard(
                        displayCalibration = uiState.displayCalibration,
                        onSetKcal = { en, r, g, b, sat, v, c, h ->
                            viewModel.setKcalParams(en, r, g, b, sat, v, c, h)
                        },
                        onSetHbm = { viewModel.setHbmEnabled(it) }
                    )

                    // ── I/O Scheduler Card ────────────────────────────────────
                    if (uiState.ioDevices.isNotEmpty()) {
                        LynxCard(
                            title = "I/O SCHEDULER MANAGER",
                            icon = Icons.Default.Storage,
                            accentColor = AccentPurple
                        ) {
                            uiState.ioDevices.forEach { dev ->
                                Text(
                                    "/dev/${dev.device}",
                                    color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                                )
                                // Scheduler chip selector
                                Text("Scheduler", color = TextSecondary, fontSize = 11.sp,
                                    modifier = Modifier.padding(bottom = 4.dp))
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(dev.availableSchedulers.size) { i ->
                                        val sched = dev.availableSchedulers[i]
                                        val isActive = sched == dev.currentScheduler
                                        Surface(
                                            onClick = { viewModel.setIoScheduler(dev.device, sched) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isActive) AccentPurple.copy(alpha = 0.2f) else BgElevated,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp, if (isActive) AccentPurple else BorderGlass
                                            )
                                        ) {
                                            Text(sched,
                                                color = if (isActive) AccentPurple else TextSecondary,
                                                fontSize = 11.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                                        }
                                    }
                                }

                                // Read-ahead slider
                                Spacer(Modifier.height(10.dp))
                                var raValue by remember(dev.readAheadKb) { mutableFloatStateOf(dev.readAheadKb.toFloat()) }
                                LynxSlider(
                                    label = "Read-Ahead Buffer",
                                    value = raValue,
                                    onValueChange = { raValue = it },
                                    onValueChangeFinished = { viewModel.setReadAheadKb(dev.device, raValue.toInt()) },
                                    valueRange = 128f..4096f,
                                    steps = 7,
                                    displayValue = "${raValue.toInt()} KB",
                                    accentColor = AccentPurple
                                )
                                HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    } // ── end I/O Scheduler if ──

                    // ── KERNEL SCHEDULER & GOVERNOR DEEP TUNABLES (CFS / EAS / BORE) ──
                    val schedInfo = uiState.schedulerInfo
                    var schedUpRate by remember(schedInfo.upRateLimitUs) { mutableFloatStateOf(schedInfo.upRateLimitUs.toFloat()) }
                    var schedDownRate by remember(schedInfo.downRateLimitUs) { mutableFloatStateOf(schedInfo.downRateLimitUs.toFloat()) }
                    var schedLatency by remember(schedInfo.schedLatencyNs) { mutableFloatStateOf((schedInfo.schedLatencyNs / 1000000f)) }
                    var schedMinGran by remember(schedInfo.schedMinGranularityNs) { mutableFloatStateOf((schedInfo.schedMinGranularityNs / 1000000f)) }
                    var schedWakeGran by remember(schedInfo.schedWakeupGranularityNs) { mutableFloatStateOf((schedInfo.schedWakeupGranularityNs / 1000000f)) }
                    var schedMigCost by remember(schedInfo.schedMigrationCostNs) { mutableFloatStateOf((schedInfo.schedMigrationCostNs / 1000f)) }

                    LynxCard(
                        title = "PENJADWAL KERNEL & GUBERNUR",
                        icon = Icons.Default.Speed,
                        accentColor = AccentCyan
                    ) {
                        // Header info & BORE Status badge
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = schedInfo.schedulerName,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Optimasi latensi context-switch & responsivitas cpufreq",
                                    color = TextSecondary,
                                    fontSize = 10.5.sp
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (schedInfo.isBoreSupported) AccentGreen.copy(alpha = 0.18f) else BgElevated,
                                border = BorderStroke(1.dp, if (schedInfo.isBoreSupported) AccentGreen else BorderGlass)
                            ) {
                                Text(
                                    text = if (schedInfo.isBoreSupported) "BORE: ACTIVE" else "BORE: UNSUPPORTED",
                                    color = if (schedInfo.isBoreSupported) AccentGreen else TextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // 3 Quick Presets
                        Text("Preset Penjadwal Cepat", color = AccentCyan, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Triple("gaming", "⚡ Gaming", AccentOrange),
                                Triple("balanced", "⚖️ Balanced", AccentBlue),
                                Triple("battery", "🔋 Battery", AccentGreen)
                            ).forEach { (preset, label, color) ->
                                val isSel = schedInfo.activePreset == preset
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) color.copy(alpha = 0.22f) else BgElevated,
                                    border = BorderStroke(1.dp, if (isSel) color else BorderGlass),
                                    modifier = Modifier.weight(1f).clickable {
                                        viewModel.applySchedulerPreset(preset)
                                    }
                                ) {
                                    Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            color = if (isSel) color else TextSecondary,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 10.dp))

                        // Schedutil Up Rate Limit
                        LynxSlider(
                            label = "Schedutil Ramp-Up Rate Limit",
                            value = schedUpRate,
                            onValueChange = { schedUpRate = it },
                            onValueChangeFinished = { viewModel.setSchedulerTunable("up_rate_limit_us", schedUpRate.toLong()) },
                            valueRange = 0f..10000f,
                            steps = 19,
                            displayValue = if (schedUpRate == 0f) "0 µs (Instant Jump)" else "${schedUpRate.toInt()} µs",
                            accentColor = AccentOrange
                        )
                        Text(
                            "Waktu tunggu sebelum CPU menaikkan frekuensi. 0µs langsung melompat ke frekuensi puncak saat game membutuhkan daya.",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        // Schedutil Down Rate Limit
                        LynxSlider(
                            label = "Schedutil Ramp-Down Rate Limit",
                            value = schedDownRate,
                            onValueChange = { schedDownRate = it },
                            onValueChangeFinished = { viewModel.setSchedulerTunable("down_rate_limit_us", schedDownRate.toLong()) },
                            valueRange = 1000f..40000f,
                            steps = 38,
                            displayValue = "${schedDownRate.toInt() / 1000} ms (${schedDownRate.toInt()} µs)",
                            accentColor = AccentBlue
                        )
                        Text(
                            "Waktu tahan sebelum CPU menurunkan clock. Mempertahankan frekuensi tinggi lebih lama mencegah micro-stutter saat frame drop.",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        // CFS Latency
                        LynxSlider(
                            label = "CFS Target Scheduling Latency",
                            value = schedLatency,
                            onValueChange = { schedLatency = it },
                            onValueChangeFinished = { viewModel.setSchedulerTunable("sched_latency_ns", (schedLatency * 1000000).toLong()) },
                            valueRange = 2f..24f,
                            steps = 21,
                            displayValue = "${schedLatency.toInt()} ms",
                            accentColor = AccentPurple
                        )
                        Text(
                            "Periode target di mana seluruh task yang siap dieksekusi dijamin mendapat giliran CPU. Latensi lebih kecil meningkatkan kehalusan UI/game.",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        // CFS Min Granularity
                        LynxSlider(
                            label = "CFS Min Preemption Granularity",
                            value = schedMinGran,
                            onValueChange = { schedMinGran = it },
                            onValueChangeFinished = { viewModel.setSchedulerTunable("sched_min_granularity_ns", (schedMinGran * 1000000).toLong()) },
                            valueRange = 0.5f..8f,
                            steps = 14,
                            displayValue = "${String.format("%.1f", schedMinGran)} ms",
                            accentColor = AccentCyan
                        )
                        Text(
                            "Jatah waktu minimum yang dijamin untuk setiap task sebelum kernel mengizinkan preemption oleh task lain.",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        // CFS Wakeup Granularity
                        LynxSlider(
                            label = "CFS Wakeup Granularity",
                            value = schedWakeGran,
                            onValueChange = { schedWakeGran = it },
                            onValueChangeFinished = { viewModel.setSchedulerTunable("sched_wakeup_granularity_ns", (schedWakeGran * 1000000).toLong()) },
                            valueRange = 0.5f..8f,
                            steps = 14,
                            displayValue = "${String.format("%.1f", schedWakeGran)} ms",
                            accentColor = AccentGreen
                        )
                        Text(
                            "Keuntungan latensi yang dibutuhkan task yang baru bangun untuk menggeser task yang sedang berjalan di CPU.",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        // Task Migration Cost
                        LynxSlider(
                            label = "Task Migration Cost (Cache-Hot)",
                            value = schedMigCost,
                            onValueChange = { schedMigCost = it },
                            onValueChangeFinished = { viewModel.setSchedulerTunable("sched_migration_cost_ns", (schedMigCost * 1000).toLong()) },
                            valueRange = 100f..3000f,
                            steps = 28,
                            displayValue = "${schedMigCost.toInt()} µs",
                            accentColor = AccentOrange
                        )
                        Text(
                            "Waktu task dianggap masih berada dalam cache L1/L2 sebelum diizinkan migrasi ke inti CPU lain.",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        // Child Process Runs First
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Child Process Runs First", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Prioritaskan eksekusi child process saat fork untuk mempercepat buka aplikasi", color = TextSecondary, fontSize = 10.sp)
                            }
                            Switch(
                                checked = schedInfo.schedChildRunsFirst,
                                onCheckedChange = { viewModel.setSchedulerTunable("sched_child_runs_first", if (it) 1L else 0L) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = BgDeepOled,
                                    checkedTrackColor = AccentCyan,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = BgElevated,
                                    uncheckedBorderColor = BorderGlass
                                ),
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }

                    // ── Otomasi Profil Per-Aplikasi (Scene-Grade Engine) ───────
                    LaunchedEffect(Unit) {
                        viewModel.refreshApplistPerf()
                    }
                    LynxCard(
                        title = "OTOMASI PROFIL PER-APLIKASI",
                        icon = Icons.Default.SportsEsports,
                        accentColor = AccentGreen
                    ) {
                        // Master Service Toggle
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Layanan Otomasi Latar Belakang",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "Pantau aplikasi aktif dan alihkan profil CPU/GPU seketika (0ms boost saat launch)",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            Switch(
                                checked = uiState.isAppAutomationActive,
                                onCheckedChange = { viewModel.toggleAppAutomation(context, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = BgDeepOled,
                                    checkedTrackColor = AccentGreen,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = BgElevated,
                                    uncheckedBorderColor = BorderGlass
                                )
                            )
                        }

                        HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 10.dp))

                        // Header: Rule Count & Add Button
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${uiState.appProfileRules.size} Aturan Profil Dikonfigurasi",
                                    color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp
                                )
                                Text(
                                    "Profil khusus diterapkan otomatis saat aplikasi berada di foreground",
                                    color = TextSecondary, fontSize = 10.5.sp
                                )
                            }
                            Surface(
                                onClick = {
                                    viewModel.refreshInstalledApps()
                                    showAddAppDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = AccentGreen.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Add, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Tambah", color = AccentGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // App Profile Rules List
                        if (uiState.appProfileRules.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BgElevated,
                                border = BorderStroke(1.dp, BorderGlass),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(
                                    Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Tune, null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Belum ada aturan khusus",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Klik tombol 'Tambah' untuk mengunci profil tertentu (Performa, Extreme, Balance, Hemat) ke game atau app favorit.",
                                        color = TextSecondary,
                                        fontSize = 10.5.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 14.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                uiState.appProfileRules.forEach { rule ->
                                    val (badgeLabel, badgeColor) = when (rule.targetProfile.lowercase()) {
                                        "extreme" -> "EXTREME" to AccentRed
                                        "performance" -> "PERFORMA" to AccentOrange
                                        "powersave" -> "HEMAT" to AccentGreen
                                        else -> "BALANCE" to AccentBlue
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = BgElevated,
                                        border = BorderStroke(1.dp, if (rule.isEnabled) badgeColor.copy(alpha = 0.35f) else BorderGlass),
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            editingRule = rule
                                        }
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AppIconImage(
                                                    packageName = rule.packageName,
                                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            rule.appName,
                                                            color = TextPrimary,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        if (rule.isGame) {
                                                            Spacer(Modifier.width(4.dp))
                                                            Text("🎮", fontSize = 10.sp)
                                                        }
                                                        Spacer(Modifier.width(6.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = badgeColor.copy(alpha = 0.18f),
                                                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                                                        ) {
                                                            Text(
                                                                badgeLabel,
                                                                color = badgeColor,
                                                                fontSize = 8.5.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                        if (rule.targetRefreshRate != null) {
                                                            Spacer(Modifier.width(4.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = AccentPurple.copy(alpha = 0.18f),
                                                                border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.4f))
                                                            ) {
                                                                Text(
                                                                    "${rule.targetRefreshRate}Hz",
                                                                    color = AccentPurple,
                                                                    fontSize = 8.5.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }
                                                        if (rule.autoFloatingHud) {
                                                            Spacer(Modifier.width(4.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = AccentCyan.copy(alpha = 0.18f),
                                                                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f))
                                                            ) {
                                                                Text(
                                                                    "HUD",
                                                                    color = AccentCyan,
                                                                    fontSize = 8.5.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        rule.packageName,
                                                        color = TextSecondary,
                                                        fontSize = 9.5.sp,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Switch(
                                                    checked = rule.isEnabled,
                                                    onCheckedChange = {
                                                        viewModel.toggleAppProfileRule(context, rule.packageName, it)
                                                    },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = BgDeepOled,
                                                        checkedTrackColor = badgeColor,
                                                        uncheckedThumbColor = TextSecondary,
                                                        uncheckedTrackColor = BgCard,
                                                        uncheckedBorderColor = BorderGlass
                                                    ),
                                                    modifier = Modifier.scale(0.8f)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        viewModel.deleteAppProfileRule(context, rule.packageName)
                                                        viewModel.removeAppFromPerf(rule.packageName)
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Hapus Aturan",
                                                        tint = TextSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── DEEP KERNEL & SYSTEM TUNABLES (Universal Dynamic Scanner & # Parser) ────
                    var manualNodePath by remember { mutableStateOf("") }
                    var maxVisibleItems by remember { mutableStateOf(20) }
                    var deepSearchQuery by remember { mutableStateOf("") }

                    LynxCard(
                        title = "DEEP KERNEL & SYSTEM TUNABLES",
                        icon = Icons.Default.Search,
                        accentColor = AccentCyan
                    ) {
                        Text(
                            "Mesin pemindai adaptif universal. Membaca seluruh file konfigurasi kernel, OEM, CPU, GPU, & ROM. Petunjuk internal (#) dikonversi otomatis menjadi widget interaktif (toggle, slider, dropdown pilihan).",
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Action Bar: Scan button + Auto-Discovery status + Count info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.runDeepScan() },
                                enabled = !uiState.isDeepScanning,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentCyan.copy(alpha = 0.16f),
                                    contentColor = AccentCyan
                                ),
                                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                if (uiState.isDeepScanning) {
                                    CircularProgressIndicator(
                                        color = AccentCyan,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Memindai...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (uiState.deepTunables.isEmpty()) "Pindai Kernel" else "Pindai Ulang", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (uiState.deepTunables.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = AccentGreen.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            "⚡ Auto-Discovered",
                                            color = AccentGreen,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BgElevated,
                                    border = BorderStroke(1.dp, BorderGlass)
                                ) {
                                    Text(
                                        "${uiState.deepTunables.size} Node",
                                        color = if (uiState.deepTunables.isNotEmpty()) AccentCyan else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Search Filter for Deep Tunables
                        OutlinedTextField(
                            value = deepSearchQuery,
                            onValueChange = { deepSearchQuery = it },
                            placeholder = { Text("Cari parameter, nama, atau path...", color = TextSecondary, fontSize = 11.5.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                            },
                            trailingIcon = {
                                if (deepSearchQuery.isNotBlank()) {
                                    IconButton(onClick = { deepSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(14.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 12.sp,
                                color = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = BorderGlass,
                                focusedContainerColor = BgElevated,
                                unfocusedContainerColor = BgElevated
                            )
                        )

                        // Category Filter Chips
                        val categories = listOf("ALL", "CPU", "GPU", "MEM", "IO", "PWR", "DISP")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            items(categories.size) { i ->
                                val cat = categories[i]
                                val isSelected = uiState.selectedDeepCategory.equals(cat, ignoreCase = true)
                                Surface(
                                    onClick = { viewModel.filterDeepCategory(cat) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) AccentCyan.copy(alpha = 0.2f) else BgElevated,
                                    border = BorderStroke(1.dp, if (isSelected) AccentCyan else BorderGlass)
                                ) {
                                    Text(
                                        cat,
                                        color = if (isSelected) AccentCyan else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // Manual Node Inspector Input
                        Text("Inspeksi Node Kustom:", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = manualNodePath,
                                onValueChange = { manualNodePath = it },
                                placeholder = { Text("/proc/ppm/policy_status atau /sys/...", color = TextSecondary, fontSize = 11.sp) },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TextPrimary
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentCyan,
                                    unfocusedBorderColor = BorderGlass,
                                    focusedContainerColor = Color(0xFF0A0C12),
                                    unfocusedContainerColor = Color(0xFF0A0C12)
                                )
                            )

                            Button(
                                onClick = {
                                    if (manualNodePath.isNotBlank()) {
                                        viewModel.inspectManualNode(manualNodePath.trim())
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentBlue.copy(alpha = 0.2f),
                                    contentColor = AccentBlue
                                ),
                                border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.4f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text("Inspeksi", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Inspected Node Card (if available)
                        uiState.manualInspectResult?.let { inspected ->
                            Spacer(Modifier.height(10.dp))
                            Text("HASIL INSPEKSI MANUAL:", color = AccentBlue, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            DeepTunableItemCard(
                                tunable = inspected,
                                onApply = { path, value -> viewModel.applyDeepTunable(path, value) }
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Filtered Deep Tunables List
                        val filteredTunables = remember(uiState.deepTunables, uiState.selectedDeepCategory) {
                            val sel = uiState.selectedDeepCategory.uppercase()
                            if (sel == "ALL" || sel.isBlank()) {
                                uiState.deepTunables
                            } else {
                                uiState.deepTunables.filter { tunable ->
                                    when (sel) {
                                        "CPU"  -> tunable.category.contains("CPU", ignoreCase = true)
                                        "GPU"  -> tunable.category.contains("GPU", ignoreCase = true)
                                        "MEM"  -> tunable.category.contains("Mem", ignoreCase = true) || tunable.category.contains("VM", ignoreCase = true)
                                        "IO"   -> tunable.category.contains("Storage", ignoreCase = true) || tunable.category.contains("I/O", ignoreCase = true)
                                        "PWR"  -> tunable.category.contains("Power", ignoreCase = true) || tunable.category.contains("Thermal", ignoreCase = true)
                                        "DISP" -> tunable.category.contains("Display", ignoreCase = true) || tunable.category.contains("Touch", ignoreCase = true)
                                        else   -> tunable.category.contains(sel, ignoreCase = true)
                                    }
                                }
                            }
                        }

                        val displayedTunables = remember(filteredTunables, deepSearchQuery) {
                            if (deepSearchQuery.isBlank()) {
                                filteredTunables
                            } else {
                                val q = deepSearchQuery.trim().lowercase()
                                filteredTunables.filter { t ->
                                    t.name.lowercase().contains(q) ||
                                    t.desc.lowercase().contains(q) ||
                                    t.category.lowercase().contains(q) ||
                                    t.path.lowercase().contains(q) ||
                                    t.help.lowercase().contains(q) ||
                                    t.rawName.lowercase().contains(q)
                                }
                            }
                        }

                        if (displayedTunables.isEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = BgElevated,
                                border = BorderStroke(1.dp, BorderGlass)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        if (uiState.isDeepScanning) "Sedang menganalisis seluruh sysfs & procfs..."
                                        else if (deepSearchQuery.isNotBlank()) "Tidak ditemukan parameter yang cocok dengan \"$deepSearchQuery\"."
                                        else "Belum ada node yang dipindai. Ketuk 'Pindai Kernel' di atas untuk memulai.",
                                        color = TextSecondary,
                                        fontSize = 11.5.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            val itemsToShow = displayedTunables.take(maxVisibleItems)
                            itemsToShow.forEach { tunable ->
                                DeepTunableItemCard(
                                    tunable = tunable,
                                    onApply = { path, value -> viewModel.applyDeepTunable(path, value) }
                                )
                                Spacer(Modifier.height(8.dp))
                            }

                            if (displayedTunables.size > maxVisibleItems) {
                                Button(
                                    onClick = { maxVisibleItems += 30 },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BgElevated,
                                        contentColor = AccentCyan
                                    ),
                                    border = BorderStroke(1.dp, BorderGlass)
                                ) {
                                    Text("Muat Lebih Banyak (${displayedTunables.size - maxVisibleItems} tersisa)", fontSize = 11.5.sp)
                                }
                            }
                        }
                    }

                } // ── end Tab 1 (1 -> {) ──

                // ── TAB 2: LYNX DEITY ENGINE ────────────────────────────────
                2 -> {
                    if (!uiState.isModuleInstalled) {
                        StandaloneModuleBanner(
                            onInstallModule = {
                                viewModel.installLynxModule("/data/local/tmp/Lynx.zip")
                            }
                        )
                    }

                    LynxCard(
                        title = "MEMORY & SWAPPINESS CACHE",
                        icon = Icons.Default.Storage,
                        accentColor = AccentBlue
                    ) {
                        // Dynamic ZRAM Compression Algorithm Selection
                        Text(
                            "Algoritma Kompresi ZRAM (Kernel Swap)",
                            color = TextPrimary, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp)
                        )
                        val algos = uiState.availZramCompAlgorithms.ifEmpty { listOf("lz4", "lzo", "zstd") }
                        val activeAlgo = uiState.zramCompAlgorithm.ifBlank { "lz4" }
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            algos.forEach { algo ->
                                val isSelected = algo == activeAlgo
                                Surface(
                                    onClick = { viewModel.setZramCompAlgorithm(algo) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) AccentBlue.copy(alpha = 0.22f) else BgElevated,
                                    border = BorderStroke(1.2.dp, if (isSelected) AccentBlue else BorderGlass)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            algo.uppercase(),
                                            color = if (isSelected) AccentBlue else TextPrimary,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            when (algo) {
                                                "zstd" -> "Rasio Max"
                                                "lz4" -> "Cepat"
                                                "lz4hc" -> "Optimal"
                                                "lzo-rle" -> "Hemat"
                                                else -> "Kompresi"
                                            },
                                            color = if (isSelected) AccentBlue.copy(alpha = 0.8f) else TextSecondary,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }

                        val ramTotal = (uiState.telemetry?.ramTotalMb ?: 4096).toFloat()
                        val minZram = 512f
                        val maxZram = maxOf(4096f, ramTotal)
                        val zramSteps = (((maxZram - minZram) / 512f).toInt() - 1).coerceAtLeast(0)

                        var zramValue by remember(state.memory.zramSizeMb) {
                            mutableFloatStateOf(state.memory.zramSizeMb.toFloat().coerceIn(minZram, maxZram))
                        }
                        LynxSlider(
                            label = "Ukuran Alokasi ZRAM (Skala RAM Fisik: ${ramTotal.toInt()} MB)",
                            value = zramValue,
                            onValueChange = { zramValue = it },
                            onValueChangeFinished = { viewModel.setZramSizeMb(zramValue.toInt()) },
                            valueRange = minZram..maxZram,
                            steps = zramSteps,
                            displayValue = "${zramValue.toInt()} MB",
                            accentColor = AccentBlue
                        )

                        // ── VM Tuning Presets ─────────────────────────────
                        Text(
                            "Preset Rekomendasi Virtual Memory",
                            color = TextPrimary, fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                        )
                        val vmPresets = listOf(
                            Triple("gaming", "⚡ Gaming", "Zero-stutter I/O"),
                            Triple("balanced", "⚖️ Balanced", "OEM Default"),
                            Triple("battery", "🔋 Battery", "Low Writeback")
                        )
                        val activeVmPreset = uiState.vmAdvanced.activePreset
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            vmPresets.forEach { (presetKey, title, subtitle) ->
                                val isSelected = activeVmPreset == presetKey
                                Surface(
                                    onClick = { viewModel.applyVmPreset(presetKey) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) AccentBlue.copy(alpha = 0.22f) else BgElevated,
                                    border = BorderStroke(1.2.dp, if (isSelected) AccentBlue else BorderGlass)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                    ) {
                                        Text(
                                            title,
                                            color = if (isSelected) AccentBlue else TextPrimary,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            subtitle,
                                            color = if (isSelected) AccentBlue.copy(alpha = 0.8f) else TextSecondary,
                                            fontSize = 9.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        var swapValue by remember(uiState.vmAdvanced.swappiness) {
                            mutableFloatStateOf(uiState.vmAdvanced.swappiness.toFloat())
                        }
                        LynxSlider(
                            label = "Virtual Memory Swappiness",
                            value = swapValue,
                            onValueChange = { swapValue = it },
                            onValueChangeFinished = { viewModel.setSwappiness(swapValue.toInt()) },
                            valueRange = 0f..200f,
                            steps = 19,
                            displayValue = "${swapValue.toInt()}",
                            accentColor = AccentBlue
                        )

                        var dirtyValue by remember(uiState.vmAdvanced.dirtyRatio) {
                            mutableFloatStateOf(uiState.vmAdvanced.dirtyRatio.toFloat())
                        }
                        LynxSlider(
                            label = "VM Dirty Ratio (Batas Writeback)",
                            value = dirtyValue,
                            onValueChange = { dirtyValue = it },
                            onValueChangeFinished = { viewModel.setDirtyRatio(dirtyValue.toInt()) },
                            valueRange = 5f..60f,
                            steps = 10,
                            displayValue = "${dirtyValue.toInt()}%",
                            accentColor = AccentBlue
                        )

                        var dirtyBgValue by remember(uiState.vmAdvanced.dirtyBackgroundRatio) {
                            mutableFloatStateOf(uiState.vmAdvanced.dirtyBackgroundRatio.toFloat())
                        }
                        LynxSlider(
                            label = "VM Dirty Background Ratio",
                            value = dirtyBgValue,
                            onValueChange = { dirtyBgValue = it },
                            onValueChangeFinished = { viewModel.setDirtyBackgroundRatio(dirtyBgValue.toInt()) },
                            valueRange = 1f..30f,
                            steps = 28,
                            displayValue = "${dirtyBgValue.toInt()}%",
                            accentColor = AccentBlue
                        )

                        var vfsValue by remember(uiState.vmAdvanced.vfsCachePressure) {
                            mutableFloatStateOf(uiState.vmAdvanced.vfsCachePressure.toFloat())
                        }
                        LynxSlider(
                            label = "VFS Cache Pressure (Reclaim Rate)",
                            value = vfsValue,
                            onValueChange = { vfsValue = it },
                            onValueChangeFinished = { viewModel.setVfsCachePressure(vfsValue.toInt()) },
                            valueRange = 50f..200f,
                            steps = 14,
                            displayValue = "${vfsValue.toInt()}",
                            accentColor = AccentBlue
                        )

                        var expireValue by remember(uiState.vmAdvanced.dirtyExpireCentisecs) {
                            mutableFloatStateOf((uiState.vmAdvanced.dirtyExpireCentisecs / 100).toFloat())
                        }
                        LynxSlider(
                            label = "VM Dirty Expire Time",
                            value = expireValue,
                            onValueChange = { expireValue = it },
                            onValueChangeFinished = { viewModel.setDirtyExpireCentisecs((expireValue * 100).toInt()) },
                            valueRange = 10f..60f,
                            steps = 9,
                            displayValue = "${expireValue.toInt()}s",
                            accentColor = AccentBlue
                        )

                        var writebackValue by remember(uiState.vmAdvanced.dirtyWritebackCentisecs) {
                            mutableFloatStateOf((uiState.vmAdvanced.dirtyWritebackCentisecs / 100).toFloat())
                        }
                        LynxSlider(
                            label = "VM Dirty Writeback Interval",
                            value = writebackValue,
                            onValueChange = { writebackValue = it },
                            onValueChangeFinished = { viewModel.setDirtyWritebackCentisecs((writebackValue * 100).toInt()) },
                            valueRange = 1f..15f,
                            steps = 13,
                            displayValue = "${writebackValue.toInt()}s",
                            accentColor = AccentBlue
                        )

                        var statValue by remember(uiState.vmAdvanced.statInterval) {
                            mutableFloatStateOf(uiState.vmAdvanced.statInterval.toFloat())
                        }
                        LynxSlider(
                            label = "VM Stat Interval (Pembaruan Statistik)",
                            value = statValue,
                            onValueChange = { statValue = it },
                            onValueChangeFinished = { viewModel.setVmStatInterval(statValue.toInt()) },
                            valueRange = 1f..10f,
                            steps = 8,
                            displayValue = "${statValue.toInt()}s",
                            accentColor = AccentBlue
                        )

                        Spacer(Modifier.height(8.dp))
                        LynxActionButton(
                            text = "Bebaskan Cache RAM (Drop Caches)",
                            icon = Icons.Default.CleaningServices,
                            onClick = { viewModel.dropCaches() },
                            accentColor = AccentCyan,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    LynxCard(
                        title = "CHARGING CONTROLLER & BYPASS",
                        icon = Icons.Default.BatteryChargingFull,
                        accentColor = AccentCyan
                    ) {
                        LynxSwitch(
                            label = "Bypass Charging (Direct Motherboard)",
                            subLabel = "Arus langsung mengalir ke board tanpa mengisi baterai saat gaming",
                            checked = state.charging.bypassEnabled,
                            onCheckedChange = { viewModel.setBypassCharging(it) },
                        )

                        var tempCutoffValue by remember(state.charging.tempCutoffC) {
                            mutableFloatStateOf(state.charging.tempCutoffC.toFloat())
                        }
                        LynxSlider(
                            label = "Batas Suhu Thermal AutoCut",
                            value = tempCutoffValue,
                            onValueChange = { tempCutoffValue = it },
                            onValueChangeFinished = { viewModel.setTempCutoff(tempCutoffValue.toInt()) },
                            valueRange = 40f..50f,
                            steps = 9,
                            displayValue = "${tempCutoffValue.toInt()}°C",
                            accentColor = AccentOrange
                        )

                        var currentLimitValue by remember(state.charging.limitCurrentMa) {
                            mutableFloatStateOf(state.charging.limitCurrentMa.toFloat())
                        }
                        LynxSlider(
                            label = "Batas Arus Pengisian Game",
                            value = currentLimitValue,
                            onValueChange = { currentLimitValue = it },
                            onValueChangeFinished = { viewModel.setChargeCurrentLimit(currentLimitValue.toInt()) },
                            valueRange = 500f..3500f,
                            steps = 11,
                            displayValue = "${currentLimitValue.toInt()} mA",
                            accentColor = AccentCyan
                        )

                        var maxBatteryValue by remember(state.charging.maxBatteryPercent) {
                            mutableFloatStateOf(state.charging.maxBatteryPercent.toFloat())
                        }
                        LynxSlider(
                            label = "Batas Pengisian Baterai (Stop-At-%)",
                            value = maxBatteryValue,
                            onValueChange = { maxBatteryValue = it },
                            onValueChangeFinished = { viewModel.setMaxBatteryPercent(maxBatteryValue.toInt()) },
                            valueRange = 70f..95f,
                            steps = 4,
                            displayValue = "${maxBatteryValue.toInt()}%",
                            accentColor = AccentCyan
                        )
                    }

                    LynxCard(
                        title = "SUBSYSTEM & OEM NEUTRALIZER",
                        icon = Icons.Default.Shield,
                        accentColor = AccentPurple
                    ) {
                        LynxSwitch(
                            label = "Wi-Fi Ping Stabilizer & FQ-CoDel",
                            subLabel = "Mengurangi bufferbloat dan jitter koneksi game",
                            checked = state.network.wifiPingStabilizer,
                            onCheckedChange = { viewModel.setWifiPingStabilizer(it) },
                        )
                        LynxSwitch(
                            label = "TouchBoost & Lock Refresh Rate",
                            subLabel = "Kunci respon sentuhan layar pada tingkat tertinggi",
                            checked = state.displayTouch.touchboost,
                            onCheckedChange = { viewModel.setTouchboost(it) },
                        )
                        LynxSwitch(
                            label = "Low-Latency Audio MMAP",
                            subLabel = "Bypass mixer audio Android untuk latensi terendah",
                            checked = state.audio.lowLatencyMmap,
                            onCheckedChange = { viewModel.setAudioMmap(it) },
                        )
                        LynxSwitch(
                            label = "Netralkan Joyose / GOS Throttling",
                            subLabel = "Hentikan pembatasan performa buatan dari OEM",
                            checked = state.oemNeutralizer.joyoseNeutralize,
                            onCheckedChange = { viewModel.setJoyoseNeutralize(it) },
                        )
                    }

                    // ── TCP Congestion Control Card ──────────────────────────
                    LaunchedEffect(Unit) { viewModel.refreshTcpAlgorithms() }
                    LynxCard(
                        title = "TCP CONGESTION CONTROL",
                        icon = Icons.Default.NetworkCheck,
                        accentColor = AccentCyan
                    ) {
                        val currentAlg = uiState.currentTcpCongestion.ifBlank { state.network.tcpCongestion }
                        val algs = uiState.availableTcpAlgorithms

                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Algoritma kontrol kongesti TCP/IP aktif mempengaruhi latensi dan throughput koneksi game online.",
                                color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            if (currentAlg.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AccentCyan.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        "● ${currentAlg.uppercase()}",
                                        color = AccentCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        if (algs.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BgElevated,
                                border = BorderStroke(1.dp, BorderGlass),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AccentOrange.copy(alpha = 0.2f),
                                        modifier = Modifier.padding(end = 10.dp)
                                    ) {
                                        Text(
                                            "UNSUPPORTED",
                                            color = AccentOrange,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        "Kernel perangkat ini tidak mengekspos pemilihan algoritma TCP dinamis via sysfs.",
                                        color = TextSecondary,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        } else {
                            val algInfo = mapOf(
                                "bic"      to Pair("⚡ BIC", "High Speed & Low Latency (Kernel Active)"),
                                "cubic"    to Pair("📶 CUBIC", "Linux Default — Balanced"),
                                "reno"     to Pair("🔁 RENO", "Classic Standard — Stable"),
                                "bbr"      to Pair("🎮 BBR", "Google BBR — Low Latency Gaming"),
                                "westwood" to Pair("📡 Westwood", "WiFi & Wireless Loss Tolerant"),
                                "htcp"     to Pair("🚀 H-TCP", "High Bandwidth Delay Product"),
                                "vegas"    to Pair("⏱️ Vegas", "Delay-Based Congestion Avoidance"),
                                "hybla"    to Pair("🌐 Hybla", "Satellite & High Latency Links")
                            )

                            algs.chunked(2).forEach { row ->
                                Row(
                                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    row.forEach { alg ->
                                        val isActive = alg.equals(currentAlg, ignoreCase = true)
                                        val info = algInfo[alg.lowercase()]
                                        Surface(
                                            onClick = { viewModel.setTcpCongestion(alg) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isActive) AccentCyan.copy(alpha = 0.16f) else BgElevated,
                                            border = BorderStroke(
                                                1.5.dp, if (isActive) AccentCyan else BorderGlass
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        info?.first ?: alg.uppercase(),
                                                        color = if (isActive) AccentCyan else TextPrimary,
                                                        fontWeight = FontWeight.Bold, fontSize = 12.sp
                                                    )
                                                    if (isActive) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = AccentCyan.copy(alpha = 0.2f)
                                                        ) {
                                                            Text(
                                                                "AKTIF",
                                                                color = AccentCyan,
                                                                fontSize = 8.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    info?.second ?: "Algoritma Kernel Linux",
                                                    color = TextSecondary,
                                                    fontSize = 10.sp,
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // ── KSM (Kernel Same-page Merging) Card ──────────────────
                    LaunchedEffect(Unit) { viewModel.refreshKsmStats() }
                    val ksm = uiState.ksmStats
                    LynxCard(
                        title = "KSM — KERNEL MEMORY MERGING",
                        icon = Icons.Default.Memory,
                        accentColor = AccentBlue
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Kernel Same-page Merging", color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Gabung halaman memori identik, hemat ${String.format("%.1f", ksm.savedMb)} MB RAM",
                                    color = TextSecondary, fontSize = 11.sp)
                            }
                            Switch(
                                checked = ksm.enabled,
                                onCheckedChange = { viewModel.setKsmEnabled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = AccentBlue,
                                    checkedTrackColor = AccentBlue.copy(alpha = 0.4f))
                            )
                        }
                        if (ksm.enabled) {
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${ksm.pagesSharing}", color = AccentBlue,
                                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Halaman Sharing", color = TextSecondary, fontSize = 10.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${ksm.pagesShared}", color = AccentCyan,
                                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Halaman Shared", color = TextSecondary, fontSize = 10.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(String.format("%.1f MB", ksm.savedMb), color = AccentOrange,
                                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("RAM Hemat", color = TextSecondary, fontSize = 10.sp)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            var scanValue by remember(ksm.pagesToScan) { mutableFloatStateOf(ksm.pagesToScan.toFloat()) }
                            LynxSlider(
                                label = "Pages to Scan per Cycle",
                                value = scanValue,
                                onValueChange = { scanValue = it },
                                onValueChangeFinished = { viewModel.setKsmTunables(scanValue.toInt(), ksm.sleepMs) },
                                valueRange = 50f..500f,
                                steps = 8,
                                displayValue = "${scanValue.toInt()} pages",
                                accentColor = AccentBlue
                            )
                        }
                    }

                    // ── LMK Minfree Preset Card ──────────────────────────────
                    LynxCard(
                        title = "LMK — LOW MEMORY KILLER",
                        icon = Icons.Default.DeleteSweep,
                        accentColor = AccentOrange
                    ) {
                        Text(
                            "Atur seberapa agresif sistem Android mengakhiri aplikasi latar belakang untuk membebaskan RAM.",
                            color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        val lmkPresets = listOf(
                            Triple("conservative", "🛡️ Conservative", "Simpan lebih banyak app di RAM"),
                            Triple("balanced",     "⚖️ Balanced",     "Standar harian — default Android"),
                            Triple("gaming",       "🎮 Gaming",       "Prioritaskan game aktif"),
                            Triple("aggressive",   "⚡ Aggressive",   "Kill agresif, maksimalkan RAM bebas"),
                        )
                        lmkPresets.forEach { (id, label, desc) ->
                            Surface(
                                onClick = { viewModel.applyLmkPreset(id) },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = BgElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(label, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(desc, color = TextSecondary, fontSize = 11.sp)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Text(
                            "⚠️ LMK Kernel hanya efektif pada Android 10 ke bawah. Android 11+ menggunakan LMKD userspace.",
                            color = TextSecondary.copy(alpha = 0.7f), fontSize = 10.sp, lineHeight = 14.sp
                        )
                    }

                    // ── FKM Parity: Sound Control (Gain Booster) Card ─────────
                    SoundControlCard(
                        soundControl = uiState.soundControl,
                        onSetGain = { hpL, hpR, spk, mic, hpMode ->
                            viewModel.setSoundGain(hpL, hpR, spk, mic, hpMode)
                        }
                    )

                    // ── FKM Parity: Memory LMK & Entropy Tuner Card ───────────
                    MemoryEntropyCard(
                        memoryEntropy = uiState.memoryEntropy,
                        onSetEntropy = { r, w ->
                            viewModel.setEntropyThresholds(r, w)
                        }
                    )

                }

                // ── TAB 3: TOOLS & FLASHER ──────────────────────────────────
                3 -> {
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
                        title = "PEMELIHARAAN & DIAGNOSTIK 1-KLIK",
                        icon = Icons.Default.Build,
                        accentColor = AccentCyan
                    ) {
                        LynxActionButton(
                            text = "⚡ Live Benchmark & Hardware Frame Profiler",
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
                            text = "🛡️ Reset Aman Kernel ke Bawaan (Stock Safe)",
                            icon = Icons.Default.RestartAlt,
                            onClick = { viewModel.resetKernelToStock() },
                            accentColor = AccentGreen
                        )
                    }

                    // ── SELinux & Kernel Printk Logging Card ───────────────────
                    LynxCard(
                        title = "SELINUX & KERNEL PRINTK LOGGING",
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

                    var customScriptText by remember(uiState.customRulesScript) {
                        mutableStateOf(uiState.customRulesScript)
                    }
                    LynxCard(
                        title = "CUSTOM SYSFS RULES & BOOT TWEAKS",
                        icon = Icons.Default.Terminal,
                        accentColor = AccentOrange
                    ) {
                        Text(
                            "Tulis perintah sysfs atau shell kustom Anda sendiri. Skrip disimpan di /data/adb/modules/Lynx/custom_rules.sh dan dieksekusi otomatis saat boot oleh service.sh.",
                            color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        // Quick snippet chips
                        Text("Templat Cepat:", color = AccentOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp))
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
                                    onClick = { customScriptText += code },
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
                            onValueChange = { customScriptText = it },
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
                                onClick = { viewModel.saveCustomRules(customScriptText) },
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
                                    viewModel.saveCustomRules(customScriptText)
                                    viewModel.executeCustomRules()
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
                            title = "MONITOR SENSOR THERMAL HARDWARE",
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
                        title = "WAKELOCK BLOCKER & DEEP SLEEP AUDIT",
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
                                border = BorderStroke(1.dp, BorderGlass),
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
                                "SUMBER WAKEUP TERTINGGI (${uiState.topWakelocks.size})",
                                color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold
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
                        title = "KERNEL CAPABILITY MATRIX & NODE INSPECTOR",
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
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

// ============================================================
//  DeepTunableItemCard — Adaptive Sysfs Node Control with # Docs
// ============================================================

@Composable
private fun DeepTunableItemCard(
    tunable: DeepTunable,
    onApply: (String, String) -> Unit
) {
    var editValue by remember(tunable.value) { mutableStateOf(tunable.value) }
    var sliderValue by remember(tunable.value, tunable.min, tunable.max) {
        val fVal = tunable.value.toFloatOrNull() ?: tunable.min
        mutableStateOf(fVal.coerceIn(tunable.min, tunable.max))
    }
    var showPathDetails by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = BgElevated,
        border = BorderStroke(1.dp, BorderGlass)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 1. Header: Name + Category + RW status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tunable.name,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (tunable.rawName.isNotBlank() && tunable.rawName != tunable.name) {
                        Text(
                            tunable.rawName,
                            color = TextTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when {
                            tunable.category.contains("CPU", true) -> AccentCyan.copy(alpha = 0.15f)
                            tunable.category.contains("GPU", true) -> AccentPurple.copy(alpha = 0.15f)
                            tunable.category.contains("Memory", true) || tunable.category.contains("VM", true) -> AccentBlue.copy(alpha = 0.15f)
                            tunable.category.contains("Storage", true) || tunable.category.contains("I/O", true) -> AccentOrange.copy(alpha = 0.15f)
                            tunable.category.contains("Power", true) || tunable.category.contains("Thermal", true) -> AccentGreen.copy(alpha = 0.15f)
                            else -> AccentCyan.copy(alpha = 0.15f)
                        },
                        border = BorderStroke(1.dp, BorderGlass)
                    ) {
                        Text(
                            tunable.category,
                            color = when {
                                tunable.category.contains("CPU", true) -> AccentCyan
                                tunable.category.contains("GPU", true) -> AccentPurple
                                tunable.category.contains("Memory", true) || tunable.category.contains("VM", true) -> AccentBlue
                                tunable.category.contains("Storage", true) || tunable.category.contains("I/O", true) -> AccentOrange
                                tunable.category.contains("Power", true) || tunable.category.contains("Thermal", true) -> AccentGreen
                                else -> AccentCyan
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (tunable.writable) AccentGreen.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (tunable.writable) AccentGreen.copy(alpha = 0.4f) else Color.Red.copy(alpha = 0.3f))
                    ) {
                        Text(
                            if (tunable.writable) "R/W" else "READ-ONLY",
                            color = if (tunable.writable) AccentGreen else Color(0xFFFF5252),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 2. Friendly Description
            if (tunable.desc.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    tunable.desc,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }

            // 3. Recommendation Tip
            if (tunable.recommendation.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentGreen.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Tips: ${tunable.recommendation}",
                            color = AccentGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 4. Collapsible Monospace Path
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPathDetails = !showPathDetails },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (showPathDetails) tunable.path else tunable.path.take(45) + (if (tunable.path.length > 45) "..." else ""),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TextTertiary,
                    maxLines = if (showPathDetails) 3 else 1
                )
                Text(
                    if (showPathDetails) "Tutup" else "Path",
                    fontSize = 8.5.sp,
                    color = AccentCyan.copy(alpha = 0.8f)
                )
            }

            // 5. Inline '#' Kernel Comment Banner (if available)
            if (tunable.help.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x1AFFB300),
                    border = BorderStroke(1.dp, Color(0x4DFFB300)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(14.dp).padding(top = 1.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                "PETUNJUK KERNEL (#):",
                                color = Color(0xFFFFB300),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                tunable.help,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TextPrimary,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 6. Interactive Tunable Controls
            if (!tunable.writable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Nilai Terbaca:", color = TextSecondary, fontSize = 11.5.sp)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0x1AFFFFFF),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Text(
                            tunable.value.ifBlank { "(kosong)" },
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            } else {
                when (tunable.type) {
                    TunableType.BOOL -> {
                        val isChecked = tunable.value == "1" ||
                                tunable.value.equals("enable", ignoreCase = true) ||
                                tunable.value.equals("enabled", ignoreCase = true) ||
                                tunable.value.equals("on", ignoreCase = true) ||
                                tunable.value.equals("y", ignoreCase = true)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    if (isChecked) "Status: Aktif (1)" else "Status: Nonaktif (0)",
                                    color = if (isChecked) AccentCyan else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (isChecked) "Sentuh sakelar untuk mematikan" else "Sentuh sakelar untuk mengaktifkan",
                                    color = TextTertiary,
                                    fontSize = 9.5.sp
                                )
                            }
                            Switch(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val newVal = if (checked) "1" else "0"
                                    onApply(tunable.path, newVal)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = BgDeepOled,
                                    checkedTrackColor = AccentCyan,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = Color(0xFF161A24)
                                )
                            )
                        }
                    }

                    TunableType.CHOICE -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Pilihan Tersedia:", color = TextSecondary, fontSize = 10.5.sp)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = AccentCyan.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        "Aktif: ${tunable.value}",
                                        color = AccentCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(tunable.options.size) { i ->
                                    val opt = tunable.options[i]
                                    val isSelected = opt.value == tunable.value ||
                                            (tunable.value.isBlank() && i == 0)
                                    Surface(
                                        onClick = { onApply(tunable.path, opt.value) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) AccentCyan.copy(alpha = 0.25f) else Color(0x1AFFFFFF),
                                        border = BorderStroke(1.dp, if (isSelected) AccentCyan else BorderGlass)
                                    ) {
                                        Text(
                                            opt.label.ifBlank { opt.value },
                                            color = if (isSelected) AccentCyan else TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TunableType.SLIDER -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Nilai Parameter:", color = TextSecondary, fontSize = 11.sp)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AccentCyan.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f))
                                ) {
                                    val displayVal = if (tunable.unit.isNotEmpty()) {
                                        "${sliderValue.toInt()} ${tunable.unit}"
                                    } else {
                                        "${sliderValue.toInt()}"
                                    }
                                    Text(
                                        displayVal,
                                        color = AccentCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Slider(
                                value = sliderValue,
                                onValueChange = { sliderValue = it },
                                valueRange = tunable.min..tunable.max,
                                steps = if (tunable.step > 0 && (tunable.max - tunable.min) / tunable.step > 1) {
                                    ((tunable.max - tunable.min) / tunable.step).toInt() - 1
                                } else 0,
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentCyan,
                                    activeTrackColor = AccentCyan,
                                    inactiveTrackColor = Color(0xFF161A24)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Min: ${tunable.min.toInt()}${tunable.unit}",
                                    color = TextTertiary,
                                    fontSize = 9.5.sp
                                )

                                Button(
                                    onClick = { onApply(tunable.path, sliderValue.toInt().toString()) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentCyan.copy(alpha = 0.2f),
                                        contentColor = AccentCyan
                                    ),
                                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Terapkan (${sliderValue.toInt()})", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }

                                Text(
                                    "Max: ${tunable.max.toInt()}${tunable.unit}",
                                    color = TextTertiary,
                                    fontSize = 9.5.sp
                                )
                            }
                        }
                    }

                    TunableType.STEPPER -> {
                        val curInt = tunable.value.toIntOrNull() ?: tunable.min.toInt()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Tingkat Level:", color = TextSecondary, fontSize = 11.sp)
                                Text(
                                    "Rentang: ${tunable.min.toInt()} - ${tunable.max.toInt()} ${tunable.unit}",
                                    color = TextTertiary,
                                    fontSize = 9.5.sp
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val nextVal = (curInt - tunable.step.toInt()).coerceAtLeast(tunable.min.toInt())
                                        onApply(tunable.path, nextVal.toString())
                                    },
                                    enabled = curInt > tunable.min.toInt(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("-", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AccentCyan.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        "$curInt ${tunable.unit}".trim(),
                                        color = AccentCyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val nextVal = (curInt + tunable.step.toInt()).coerceAtMost(tunable.max.toInt())
                                        onApply(tunable.path, nextVal.toString())
                                    },
                                    enabled = curInt < tunable.max.toInt(),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    TunableType.INT, TunableType.TEXT -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = editValue,
                                onValueChange = { editValue = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TextPrimary
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentCyan,
                                    unfocusedBorderColor = BorderGlass,
                                    focusedContainerColor = Color(0xFF0A0C12),
                                    unfocusedContainerColor = Color(0xFF0A0C12)
                                )
                            )

                            Button(
                                onClick = { onApply(tunable.path, editValue.trim()) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentCyan.copy(alpha = 0.2f),
                                    contentColor = AccentCyan
                                ),
                                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Terapkan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                result.jankyFramesPercent <= 5f -> "⭐ Ultra Smooth — Frame Pacing Sangat Stabil" to AccentGreen
                                result.jankyFramesPercent <= 15f -> "✅ Sangat Baik — Stabilitas Tinggi" to AccentCyan
                                else -> "⚠️ Variasi Frametime Terdeteksi" to AccentOrange
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
                                    🔥 LYNX LIVE BENCHMARK REPORT
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


