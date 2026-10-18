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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob


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
                        viewModel.selectKernelPerformanceProfile("extreme", context, true)
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
            .background(BgDeepOled),
    ) {
        when {
            uiState.isLoading -> LoadingScreen()
            !uiState.isRootAvailable -> ErrorScreen(uiState = uiState, onRetry = { viewModel.retryRootCheck() })
            else -> MainDashboard(
                uiState = uiState,
                viewModel = viewModel,
                onExtremeConfirmRequired = { showExtremeDialog = true },
            )
        }

        // Ultra-Compact Toast HUD Feedback Layer
        LynxModeToastHud(
            toastData = uiState.activeModeToast,
            feedbackStyle = uiState.modeFeedbackStyle,
            onDismiss = { viewModel.dismissModeFeedback() }
        )

        // Automatic routing of successMessage & errorMessage to the Universal Toast HUD
        LaunchedEffect(uiState.successMessage) {
            val msg = uiState.successMessage
            if (!msg.isNullOrBlank()) {
                val isProfileSwitch = (msg.contains("Profil", ignoreCase = true) || msg.contains("Mode", ignoreCase = true)) && msg.contains("diterapkan", ignoreCase = true)
                if (!isProfileSwitch) {
                    viewModel.triggerUniversalToast(
                        title = msg,
                        statusTag = "SUKSES",
                        type = com.noir.lynx.data.ToastType.SUCCESS,
                        accentColorHex = 0xFF00E676,
                        iconType = "success"
                    )
                }
                viewModel.dismissSuccess()
            }
        }

        LaunchedEffect(uiState.errorMessage) {
            val msg = uiState.errorMessage
            if (!msg.isNullOrBlank()) {
                viewModel.triggerUniversalToast(
                    title = msg,
                    statusTag = "GAGAL",
                    type = com.noir.lynx.data.ToastType.ERROR,
                    accentColorHex = 0xFFFF5252,
                    iconType = "error",
                    durationMs = 2800L
                )
                viewModel.dismissError()
            }
        }
    }
}

// ============================================================
//  Loading Screen
// ============================================================

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
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
fun ErrorScreen(uiState: LynxUiState, onRetry: () -> Unit = {}) {
    // Auto-detect root permission as soon as user returns or grants root
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000L)
            onRetry()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
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
                Spacer(Modifier.height(20.dp))
                Surface(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = "Periksa Ulang Izin Root",
                            color = AccentCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
//  Ultra-Smooth App Icon Loader & Memory Cache (Preloaded & Zero IPC Jank)
// ============================================================

object AppIconLoader {
    val cache = android.util.LruCache<String, ImageBitmap>(500)
    private val iconDispatcher = Dispatchers.IO.limitedParallelism(2)
    private val scope = CoroutineScope(iconDispatcher + SupervisorJob())

    fun getCached(packageName: String): ImageBitmap? = cache.get(packageName)

    suspend fun loadIcon(context: android.content.Context, packageName: String): ImageBitmap? {
        val cached = cache.get(packageName)
        if (cached != null) return cached

        return withContext(iconDispatcher) {
            try {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(packageName)
                val targetSize = 96
                val bmp = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, targetSize, targetSize)
                drawable.draw(canvas)
                val imageBitmap = bmp.asImageBitmap()
                cache.put(packageName, imageBitmap)
                imageBitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    fun preload(context: android.content.Context, packageNames: Collection<String>) {
        if (packageNames.isEmpty()) return
        scope.launch {
            val pm = context.packageManager
            for (pkg in packageNames) {
                if (cache.get(pkg) != null) continue
                try {
                    val drawable = pm.getApplicationIcon(pkg)
                    val targetSize = 96
                    val bmp = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, targetSize, targetSize)
                    drawable.draw(canvas)
                    cache.put(pkg, bmp.asImageBitmap())
                } catch (_: Exception) {}
            }
        }
    }
}

@Composable
fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmapState by remember(packageName) { mutableStateOf(AppIconLoader.getCached(packageName)) }

    if (bitmapState == null) {
        LaunchedEffect(packageName) {
            val img = AppIconLoader.loadIcon(context, packageName)
            if (img != null) {
                bitmapState = img
            }
        }
    }

    if (bitmapState != null) {
        Image(
            bitmap = bitmapState!!,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Icon(
            Icons.Default.Android,
            contentDescription = null,
            tint = AccentCyan.copy(alpha = 0.6f),
            modifier = modifier
        )
    }
}

// ============================================================
//  Main Dashboard — 4-Tab Luxury Material 3 Navigation
// ============================================================

