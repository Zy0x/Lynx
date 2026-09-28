package com.noir.lynx.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noir.lynx.data.LynxRepository
import com.noir.lynx.ui.MainActivity
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlin.math.roundToInt

/**
 * LynxFloatingHudService — Real-time floating Game HUD / OSD Overlay.
 *
 * Supports 6 distinct, customizable layout styles:
 * 1. VERTICAL_PILLAR (Modern Slim RTSS)
 * 2. TOP_RIBBON (Top Nano-Ribbon)
 * 3. DUAL_BLOCK (Dual-Block Esport)
 * 4. QUAD_TILES (Quad-Tiles Modular)
 * 5. DECK_BANNER (Steam Deck Banner)
 * 6. GHOST_TEXT (Ghost Frameless)
 */
class LynxFloatingHudService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var floatView: View? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    private fun getDisplayRefreshRate(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.mode?.refreshRate?.roundToInt() ?: 120
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay?.mode?.refreshRate?.roundToInt() ?: 120
            }
        } catch (_: Exception) {
            120
        }
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    companion object {
        var isRunning: Boolean = false
            private set
        const val ACTION_START = "com.noir.lynx.service.START_HUD"
        const val ACTION_STOP = "com.noir.lynx.service.STOP_HUD"
        const val ACTION_SET_STYLE = "com.noir.lynx.service.SET_STYLE"
        const val EXTRA_STYLE = "extra_hud_style"
        val activeStyleFlow = kotlinx.coroutines.flow.MutableStateFlow(1)
        private const val NOTIF_CHANNEL_ID = "lynx_game_hud_channel"
        private const val NOTIF_ID = 8801
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val savedStyle = getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE).getInt("hud_style", 1)
        activeStyleFlow.value = savedStyle.coerceIn(1, 6)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildForegroundNotification())
        createFloatingHud()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cleanUpAndStop()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_SET_STYLE || intent?.hasExtra(EXTRA_STYLE) == true) {
            val newStyle = intent.getIntExtra(EXTRA_STYLE, activeStyleFlow.value).coerceIn(1, 6)
            activeStyleFlow.value = newStyle
            getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE).edit().putInt("hud_style", newStyle).apply()
        }
        return START_STICKY
    }

    private fun cleanUpAndStop() {
        isRunning = false
        serviceScope.cancel()
        floatView?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (e: Exception) {
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) {}
            }
        }
        floatView = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingHud() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 200
        }

        val telemetryFlow = kotlinx.coroutines.flow.MutableStateFlow(LynxRepository.FloatingHudTelemetry())
        val isHudBenchmarkingFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
        val hudBenchmarkCountdownFlow = kotlinx.coroutines.flow.MutableStateFlow(60)
        val hudBenchmarkSummaryFlow = kotlinx.coroutines.flow.MutableStateFlow<com.noir.lynx.data.LynxBenchmarkResult?>(null)

        // Start Telemetry Query Loop (500ms for ultra-smooth live HUD)
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                Shell.cmd("dumpsys SurfaceFlinger --timestats -enable 2>/dev/null").exec()
            }
            while (isActive) {
                val displayHz = getDisplayRefreshRate()
                val tel = withContext(Dispatchers.IO) {
                    LynxRepository.readFloatingHudTelemetry(displayHz)
                }
                telemetryFlow.value = tel
                delay(500L)
            }
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LynxFloatingHudService)
            setViewTreeSavedStateRegistryOwner(this@LynxFloatingHudService)
            setContent {
                val tel by telemetryFlow.collectAsState()
                val isHudBenchmarking by isHudBenchmarkingFlow.collectAsState()
                val hudBenchmarkCountdown by hudBenchmarkCountdownFlow.collectAsState()
                val hudBenchmarkSummary by hudBenchmarkSummaryFlow.collectAsState()
                val activeStyle by activeStyleFlow.collectAsState()
                var isExpanded by remember { mutableStateOf(false) }

                FloatingHudContent(
                    telemetry = tel,
                    activeStyle = activeStyle,
                    isExpanded = isExpanded,
                    isHudBenchmarking = isHudBenchmarking,
                    hudBenchmarkCountdown = hudBenchmarkCountdown,
                    hudBenchmarkSummary = hudBenchmarkSummary,
                    onToggleExpand = { isExpanded = !isExpanded },
                    onSelectStyle = { newStyle ->
                        val clamped = newStyle.coerceIn(1, 6)
                        activeStyleFlow.value = clamped
                        getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE).edit().putInt("hud_style", clamped).apply()
                    },
                    onClose = { cleanUpAndStop() },
                    onProfileSelect = { profile ->
                        telemetryFlow.value = tel.copy(activeProfile = profile)
                        serviceScope.launch {
                            LynxRepository.setProfile(profile)
                        }
                    },
                    onStartHudBenchmark = {
                        serviceScope.launch {
                            if (isHudBenchmarkingFlow.value) return@launch
                            isHudBenchmarkingFlow.value = true
                            hudBenchmarkSummaryFlow.value = null
                            try {
                                val res = withContext(Dispatchers.IO) {
                                    LynxRepository.runHardwareBenchmark(durationSeconds = 60) { rem ->
                                        hudBenchmarkCountdownFlow.value = rem
                                    }
                                }
                                hudBenchmarkSummaryFlow.value = res
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                isHudBenchmarkingFlow.value = false
                            }
                        }
                    },
                    onDrag = { dx, dy ->
                        layoutParams.x = (layoutParams.x + dx).toInt()
                        layoutParams.y = (layoutParams.y + dy).toInt()
                        try {
                            windowManager.updateViewLayout(this@apply, layoutParams)
                        } catch (_: Exception) {}
                    }
                )
            }
        }

        floatView = composeView
        try {
            windowManager.addView(composeView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Lynx Game HUD Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status overlay Game HUD Lynx"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pOpenIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LynxFloatingHudService::class.java).apply {
            action = ACTION_STOP
        }
        val pStopIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIF_CHANNEL_ID)
                .setContentTitle("🎮 Lynx Floating Game HUD Aktif")
                .setContentText("Ketuk untuk membuka Lynx atau tutup overlay")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pOpenIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Tutup HUD", pStopIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("🎮 Lynx Floating Game HUD Aktif")
                .setContentText("Ketuk untuk membuka Lynx")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pOpenIntent)
                .build()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanUpAndStop()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

// ============================================================
//  COMPOSE OVERLAY UI & 6 DISTINCT LAYOUT STYLES
// ============================================================

private val BgHud = Color(0xDD0A0E14)
private val BorderHud = Color(0x4400E5FF)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonMagenta = Color(0xFFFF4081)
private val NeonGreen = Color(0xFF00E676)
private val NeonGold = Color(0xFFFFD600)
private val NeonOrange = Color(0xFFFF9100)
private val NeonRed = Color(0xFFFF5252)
private val TextSlate = Color(0xFFCFD8DC)
private val TextMuted = Color(0x99FFFFFF)

@Composable
fun FloatingHudContent(
    telemetry: LynxRepository.FloatingHudTelemetry,
    activeStyle: Int = 1,
    isExpanded: Boolean,
    isHudBenchmarking: Boolean = false,
    hudBenchmarkCountdown: Int = 60,
    hudBenchmarkSummary: com.noir.lynx.data.LynxBenchmarkResult? = null,
    onToggleExpand: () -> Unit,
    onSelectStyle: (Int) -> Unit,
    onClose: () -> Unit,
    onProfileSelect: (String) -> Unit,
    onStartHudBenchmark: () -> Unit = {},
    onDrag: (Float, Float) -> Unit,
) {
    var showStylePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.wrapContentSize().padding(4.dp)) {
        // Floating Style Switcher Quick Picker Bar
        AnimatedVisibility(visible = showStylePicker) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgHud,
                border = BorderStroke(1.dp, BorderHud),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val styleNames = listOf("1.Pillar", "2.Ribbon", "3.Esport", "4.Tiles", "5.Deck", "6.Ghost")
                    styleNames.forEachIndexed { idx, label ->
                        val sId = idx + 1
                        val isSel = (activeStyle == sId)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) NeonCyan.copy(alpha = 0.3f) else Color(0x22FFFFFF))
                                .clickable {
                                    onSelectStyle(sId)
                                    showStylePicker = false
                                }
                                .padding(horizontal = 5.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) NeonCyan else Color.White,
                                fontSize = 9.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Active HUD Style Render
        when (activeStyle) {
            2 -> HudTopRibbon(
                telemetry = telemetry,
                onDrag = onDrag,
                onToggleStylePicker = { showStylePicker = !showStylePicker },
                onClose = onClose
            )
            3 -> HudDualBlock(
                telemetry = telemetry,
                onDrag = onDrag,
                onToggleStylePicker = { showStylePicker = !showStylePicker },
                onClose = onClose,
                onProfileSelect = onProfileSelect
            )
            4 -> HudQuadTiles(
                telemetry = telemetry,
                onDrag = onDrag,
                onToggleStylePicker = { showStylePicker = !showStylePicker },
                onClose = onClose,
                onProfileSelect = onProfileSelect
            )
            5 -> HudDeckBanner(
                telemetry = telemetry,
                onDrag = onDrag,
                onToggleStylePicker = { showStylePicker = !showStylePicker },
                onClose = onClose,
                onProfileSelect = onProfileSelect
            )
            6 -> HudGhostText(
                telemetry = telemetry,
                onDrag = onDrag,
                onToggleStylePicker = { showStylePicker = !showStylePicker },
                onClose = onClose
            )
            else -> HudVerticalPillar(
                telemetry = telemetry,
                isExpanded = isExpanded,
                isHudBenchmarking = isHudBenchmarking,
                hudBenchmarkCountdown = hudBenchmarkCountdown,
                hudBenchmarkSummary = hudBenchmarkSummary,
                onToggleExpand = onToggleExpand,
                onToggleStylePicker = { showStylePicker = !showStylePicker },
                onClose = onClose,
                onProfileSelect = onProfileSelect,
                onStartHudBenchmark = onStartHudBenchmark,
                onDrag = onDrag
            )
        }
    }
}

// ────────────────────────────────────────────────────────────
//  STYLE 1: VERTICAL PILLAR (MODERN SLIM RTSS)
// ────────────────────────────────────────────────────────────

@Composable
fun HudVerticalPillar(
    telemetry: LynxRepository.FloatingHudTelemetry,
    isExpanded: Boolean,
    isHudBenchmarking: Boolean,
    hudBenchmarkCountdown: Int,
    hudBenchmarkSummary: com.noir.lynx.data.LynxBenchmarkResult?,
    onToggleExpand: () -> Unit,
    onToggleStylePicker: () -> Unit,
    onClose: () -> Unit,
    onProfileSelect: (String) -> Unit,
    onStartHudBenchmark: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BgHud,
        border = BorderStroke(1.dp, BorderHud),
        shadowElevation = 8.dp,
        modifier = Modifier.width(275.dp)
    ) {
        Column(modifier = Modifier.padding(7.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.DragHandle, "Geser", tint = Color(0x99FFFFFF), modifier = Modifier.size(14.dp))
                    Text(
                        "LYNX TELEMETRY",
                        color = NeonCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    // Style switch badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x2200E5FF))
                            .clickable { onToggleStylePicker() }
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("S1 ▾", color = NeonCyan, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                    // Profile badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x3300E5FF))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(telemetry.activeProfile.uppercase().take(3), color = NeonCyan, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0x22FF5252))
                            .clickable { onClose() }
                            .padding(2.dp)
                    ) {
                        Icon(Icons.Default.Close, "Tutup", tint = NeonRed, modifier = Modifier.size(10.dp))
                    }
                }
            }

            HorizontalDivider(color = Color(0x22FFFFFF), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

            // 4-Column Aligned Rows
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // GPU
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge("GPU", NeonMagenta)
                    Text(
                        if (telemetry.gpuFreqMhz > 0) "${telemetry.gpuFreqMhz} MHz" else "--",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(70.dp)
                    )
                    Text(
                        "${telemetry.gpuLoadPct}%",
                        color = NeonMagenta,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(42.dp)
                    )
                    Text(
                        "58°C · ${String.format(java.util.Locale.US, "%.1f", telemetry.battWatt)}W",
                        color = TextSlate,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }

                // CPU
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge("CPU", NeonCyan)
                    Text(
                        telemetry.cpuCoresSummary.ifEmpty { if (telemetry.cpuFreqMhz > 0) "${telemetry.cpuFreqMhz} MHz" else "--" },
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(70.dp)
                    )
                    Text(
                        "${telemetry.cpuLoadPct}%",
                        color = NeonCyan,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(42.dp)
                    )
                    Text(
                        "${telemetry.battTempC.toInt() + 15}°C",
                        color = TextSlate,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }

                // RAM
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge("RAM", NeonGold)
                    Text(
                        if (telemetry.ramUsedGb > 0f) "${telemetry.ramUsedGb} / ${telemetry.ramTotalGb}G" else "--",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(70.dp)
                    )
                    Text(
                        "${telemetry.ramPct}%",
                        color = NeonGold,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(42.dp)
                    )
                    Text(
                        "ZRAM ${telemetry.zramUsedGb}G",
                        color = TextSlate,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }

                // FPS
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge("FPS", NeonGreen)
                    Text(
                        "${telemetry.renderFps} FPS",
                        color = NeonGreen,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(70.dp)
                    )
                    Text(
                        "AVG ${telemetry.avgFps.toInt()}",
                        color = NeonGreen.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(48.dp)
                    )
                    Text(
                        "1%L ${telemetry.fps1PercentLow.toInt()}",
                        color = NeonGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Frametime
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge("FT", TextSlate)
                    Text(
                        "${String.format(java.util.Locale.US, "%.1f", telemetry.frametimeMs)} ms",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(70.dp)
                    )
                    Text(
                        "AVG ${String.format(java.util.Locale.US, "%.1f", telemetry.avgFrametimeMs)}",
                        color = TextSlate,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(48.dp)
                    )
                    Text(
                        "±${String.format(java.util.Locale.US, "%.1f", telemetry.frametimeJitterMs)} ms",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Real-time Frametime Sparkline Canvas
                FrametimeSparklineCanvas(
                    history = telemetry.frametimeHistory,
                    heightDp = 14,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // BAT
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge("BAT", NeonOrange)
                    Text(
                        "${telemetry.battTempC}°C",
                        color = if (telemetry.battTempC > 42f) NeonRed else NeonOrange,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(55.dp)
                    )
                    Text(
                        "${telemetry.battCurrentMa} mA",
                        color = if (telemetry.isCharging) NeonGreen else Color.White,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(60.dp)
                    )
                    Text(
                        "${telemetry.battLevelPct}% · ${if (telemetry.isCharging) "+" else ""}${String.format(java.util.Locale.US, "%.1f", telemetry.battWatt)}W",
                        color = TextSlate,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quick Profile Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf("balance" to "BAL", "performance" to "PERF", "extreme" to "EXT", "powersave" to "PWR").forEach { (k, label) ->
                    val isSel = telemetry.activeProfile.equals(k, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSel) NeonCyan else Color(0x18FFFFFF))
                            .clickable { onProfileSelect(k) }
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSel) Color.Black else Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  STYLE 2: TOP NANO-RIBBON (HORIZONTAL BAR)
// ────────────────────────────────────────────────────────────

@Composable
fun HudTopRibbon(
    telemetry: LynxRepository.FloatingHudTelemetry,
    onDrag: (Float, Float) -> Unit,
    onToggleStylePicker: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BgHud,
        border = BorderStroke(1.dp, BorderHud),
        modifier = Modifier
            .width(365.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // FPS
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                CategoryBadge("FPS", NeonGreen)
                Text("${telemetry.renderFps}", color = NeonGreen, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                Text("A:${telemetry.avgFps.toInt()}", color = TextSlate, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }

            Text("|", color = Color(0x33FFFFFF), fontSize = 10.sp)

            // GPU
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("GPU", color = NeonMagenta, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                Text("${telemetry.gpuLoadPct}%", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            Text("|", color = Color(0x33FFFFFF), fontSize = 10.sp)

            // CPU
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("CPU", color = NeonCyan, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                Text("${telemetry.cpuLoadPct}%", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }

            Text("|", color = Color(0x33FFFFFF), fontSize = 10.sp)

            // RAM
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("RAM", color = NeonGold, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                Text("${telemetry.ramUsedGb}G", color = Color.White, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace)
            }

            Text("|", color = Color(0x33FFFFFF), fontSize = 10.sp)

            // BAT & Style
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${telemetry.battTempC.toInt()}°C", color = NeonOrange, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x3300E5FF))
                        .clickable { onToggleStylePicker() }
                        .padding(horizontal = 3.5.dp, vertical = 1.dp)
                ) {
                    Text("S2 ▾", color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  STYLE 3: DUAL-BLOCK ESPORT (ROG STYLE)
// ────────────────────────────────────────────────────────────

@Composable
fun HudDualBlock(
    telemetry: LynxRepository.FloatingHudTelemetry,
    onDrag: (Float, Float) -> Unit,
    onToggleStylePicker: () -> Unit,
    onClose: () -> Unit,
    onProfileSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .width(340.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Left Block: FPS Hero Hub
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BgHud,
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
            modifier = Modifier.width(125.dp)
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("FPS HUB", color = NeonCyan, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    Text("${telemetry.refreshRateHz}Hz", color = NeonGreen, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "${telemetry.renderFps}",
                    color = NeonGreen,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
                Text("AVG: ${telemetry.avgFps.toInt()} · 1%L: ${telemetry.fps1PercentLow.toInt()}", color = TextSlate, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                Text("FT: ${String.format(java.util.Locale.US, "%.1f", telemetry.frametimeMs)}ms", color = TextMuted, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(3.dp))
                FrametimeSparklineCanvas(history = telemetry.frametimeHistory, heightDp = 14)
            }
        }

        // Right Block: Hardware & Power Matrix
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BgHud,
            border = BorderStroke(1.dp, NeonMagenta.copy(alpha = 0.35f)),
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("HARDWARE", color = NeonMagenta, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x3300E5FF))
                                .clickable { onToggleStylePicker() }
                                .padding(horizontal = 3.5.dp, vertical = 1.dp)
                        ) {
                            Text("S3 ▾", color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x22FFFFFF))
                                .padding(horizontal = 3.5.dp, vertical = 1.dp)
                        ) {
                            Text(telemetry.activeProfile.uppercase().take(3), color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                HorizontalDivider(color = Color(0x22FFFFFF), thickness = 1.dp, modifier = Modifier.padding(vertical = 3.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("GPU ${telemetry.gpuFreqMhz}M", color = NeonMagenta, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("${telemetry.gpuLoadPct}% · 58°C", color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("CPU ${telemetry.cpuFreqMhz}M", color = NeonCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("${telemetry.cpuLoadPct}% · ${telemetry.battTempC.toInt() + 15}°C", color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("RAM ${telemetry.ramUsedGb}G", color = NeonGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("ZRAM ${telemetry.zramUsedGb}G", color = TextSlate, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("BAT ${telemetry.battTempC}°C", color = NeonOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("${telemetry.battLevelPct}% · ${String.format(java.util.Locale.US, "%.1f", telemetry.battWatt)}W", color = TextSlate, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  STYLE 4: QUAD-TILES (NOTHING OS / MODULAR GRID)
// ────────────────────────────────────────────────────────────

@Composable
fun HudQuadTiles(
    telemetry: LynxRepository.FloatingHudTelemetry,
    onDrag: (Float, Float) -> Unit,
    onToggleStylePicker: () -> Unit,
    onClose: () -> Unit,
    onProfileSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(310.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("LYNX QUAD-TILES", color = NeonCyan, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x3300E5FF))
                    .clickable { onToggleStylePicker() }
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text("S4 ▾", color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Tile 1: FPS
            Surface(shape = RoundedCornerShape(8.dp), color = BgHud, border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.4f)), modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(5.dp)) {
                    Text("FPS PACING", color = NeonGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text("${telemetry.renderFps}", color = NeonGreen, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text("AVG ${telemetry.avgFps.toInt()} · 1%L ${telemetry.fps1PercentLow.toInt()}", color = TextSlate, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }
            // Tile 2: GPU
            Surface(shape = RoundedCornerShape(8.dp), color = BgHud, border = BorderStroke(1.dp, NeonMagenta.copy(alpha = 0.4f)), modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(5.dp)) {
                    Text("GPU ENGINE", color = NeonMagenta, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text("${telemetry.gpuLoadPct}%", color = NeonMagenta, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text("${telemetry.gpuFreqMhz}M · 58°C", color = TextSlate, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Tile 3: CPU
            Surface(shape = RoundedCornerShape(8.dp), color = BgHud, border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)), modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(5.dp)) {
                    Text("CPU LOAD", color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text("${telemetry.cpuLoadPct}%", color = NeonCyan, fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text(telemetry.cpuCoresSummary.ifEmpty { "${telemetry.cpuFreqMhz}M" }, color = TextSlate, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }
            // Tile 4: RAM & BAT
            Surface(shape = RoundedCornerShape(8.dp), color = BgHud, border = BorderStroke(1.dp, NeonGold.copy(alpha = 0.4f)), modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(5.dp)) {
                    Text("MEM & POWER", color = NeonGold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text("${telemetry.ramUsedGb}G", color = NeonGold, fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text("${telemetry.battTempC}°C · ${String.format(java.util.Locale.US, "%.1f", telemetry.battWatt)}W", color = TextSlate, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  STYLE 5: DECK BANNER (STEAM DECK GAMESCOPE)
// ────────────────────────────────────────────────────────────

@Composable
fun HudDeckBanner(
    telemetry: LynxRepository.FloatingHudTelemetry,
    onDrag: (Float, Float) -> Unit,
    onToggleStylePicker: () -> Unit,
    onClose: () -> Unit,
    onProfileSelect: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgHud,
        border = BorderStroke(1.dp, BorderHud),
        modifier = Modifier
            .width(360.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            // Tier 1: FPS + Wide Sparkline
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${telemetry.renderFps}", color = NeonGreen, fontSize = 17.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Text("FPS", color = NeonGreen, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }
                FrametimeSparklineCanvas(
                    history = telemetry.frametimeHistory,
                    heightDp = 14,
                    modifier = Modifier.width(140.dp)
                )
                Text("AVG ${telemetry.avgFps.toInt()} · 1%L ${telemetry.fps1PercentLow.toInt()}", color = TextSlate, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x3300E5FF))
                        .clickable { onToggleStylePicker() }
                        .padding(horizontal = 3.5.dp, vertical = 1.dp)
                ) {
                    Text("S5 ▾", color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = Color(0x22FFFFFF), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

            // Tier 2: Hardware Strip
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HardwareDeckChip("GPU", "${telemetry.gpuLoadPct}%", NeonMagenta, Modifier.weight(1f))
                HardwareDeckChip("CPU", "${telemetry.cpuLoadPct}%", NeonCyan, Modifier.weight(1f))
                HardwareDeckChip("RAM", "${telemetry.ramUsedGb}G", NeonGold, Modifier.weight(1f))
                HardwareDeckChip("BAT", "${telemetry.battTempC.toInt()}°C", NeonOrange, Modifier.weight(1f))
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  STYLE 6: GHOST TEXT (FRAMELESS MINIMALIST)
// ────────────────────────────────────────────────────────────

@Composable
fun HudGhostText(
    telemetry: LynxRepository.FloatingHudTelemetry,
    onDrag: (Float, Float) -> Unit,
    onToggleStylePicker: () -> Unit,
    onClose: () -> Unit,
) {
    val ghostShadow = Shadow(color = Color.Black, offset = Offset(1.5f, 1.5f), blurRadius = 3f)

    Column(
        modifier = Modifier
            .width(260.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable { onToggleStylePicker() }
            .padding(4.dp)
    ) {
        // Ghost Line: GPU
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("GPU", color = NeonMagenta, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontWeight = FontWeight.Bold))
            Text("${telemetry.gpuFreqMhz} MHz", color = Color.White, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace))
            Text("${telemetry.gpuLoadPct}%", color = NeonMagenta, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
            Text("58°C · ${String.format(java.util.Locale.US, "%.1f", telemetry.battWatt)}W", color = TextSlate, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace))
        }

        // Ghost Line: CPU
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("CPU", color = NeonCyan, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontWeight = FontWeight.Bold))
            Text(telemetry.cpuCoresSummary.ifEmpty { "${telemetry.cpuFreqMhz} MHz" }, color = Color.White, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace))
            Text("${telemetry.cpuLoadPct}%", color = NeonCyan, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
            Text("${telemetry.battTempC.toInt() + 15}°C", color = TextSlate, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace))
        }

        // Ghost Line: RAM
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("RAM", color = NeonGold, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontWeight = FontWeight.Bold))
            Text("${telemetry.ramUsedGb} / ${telemetry.ramTotalGb} GB", color = Color.White, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace))
            Text("${telemetry.ramPct}%", color = NeonGold, style = TextStyle(shadow = ghostShadow, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
            Text("ZRAM ${telemetry.zramUsedGb}G", color = TextSlate, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace))
        }

        // Ghost Line: FPS
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("FPS", color = NeonGreen, style = TextStyle(shadow = ghostShadow, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold))
            Text("${telemetry.renderFps}", color = NeonGreen, style = TextStyle(shadow = ghostShadow, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace))
            Text("AVG ${telemetry.avgFps.toInt()}", color = NeonGreen.copy(alpha = 0.85f), style = TextStyle(shadow = ghostShadow, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
            Text("1%L ${telemetry.fps1PercentLow.toInt()}", color = NeonGold, style = TextStyle(shadow = ghostShadow, fontSize = 10.sp, fontFamily = FontFamily.Monospace))
        }

        // Ghost Line: FT
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("FT", color = TextSlate, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontWeight = FontWeight.Bold))
            Text("${String.format(java.util.Locale.US, "%.1f", telemetry.frametimeMs)} ms", color = Color.White, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace))
            Text("AVG ${String.format(java.util.Locale.US, "%.1f", telemetry.avgFrametimeMs)}", color = TextSlate, style = TextStyle(shadow = ghostShadow, fontSize = 9.sp, fontFamily = FontFamily.Monospace))
            Text("±${String.format(java.util.Locale.US, "%.1f", telemetry.frametimeJitterMs)} ms", color = TextMuted, style = TextStyle(shadow = ghostShadow, fontSize = 9.sp, fontFamily = FontFamily.Monospace))
        }

        // Ghost Line: BAT
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("BAT", color = NeonOrange, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontWeight = FontWeight.Bold))
            Text("${telemetry.battTempC}°C", color = NeonOrange, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace))
            Text("${telemetry.battCurrentMa} mA", color = Color.White, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace))
            Text("${telemetry.battLevelPct}% [${telemetry.activeProfile.uppercase().take(3)}]", color = NeonCyan, style = TextStyle(shadow = ghostShadow, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
        }
    }
}

// ────────────────────────────────────────────────────────────
//  SHARED COMPONENT HELPERS
// ────────────────────────────────────────────────────────────

@Composable
private fun CategoryBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .width(36.dp)
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun HardwareDeckChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0x18FFFFFF),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = color, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun FrametimeSparklineCanvas(
    history: List<Float>,
    modifier: Modifier = Modifier,
    heightDp: Int = 14
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0x66000000))
    ) {
        val count = history.size
        if (count == 0) return@Canvas
        val barWidth = size.width / count.coerceAtLeast(30)
        val maxMs = 70f
        for (i in 0 until count) {
            val v = history[i]
            val barH = (v / maxMs * size.height).coerceIn(3f, size.height)
            val x = i * barWidth
            val y = size.height - barH
            val barColor = when {
                v > 45f -> NeonRed
                v > 30f -> NeonGold
                else -> NeonGreen
            }
            drawRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size((barWidth - 0.8f).coerceAtLeast(1f), barH)
            )
        }
    }
}
