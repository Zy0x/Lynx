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
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
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
        const val ACTION_SET_MODE = "com.noir.lynx.service.SET_MODE"
        const val EXTRA_MODE = "extra_hud_mode"
        const val ACTION_SET_PIN_FPS = "com.noir.lynx.service.SET_PIN_FPS"
        const val EXTRA_PIN_FPS = "extra_pin_fps"

        val activeStyleFlow = kotlinx.coroutines.flow.MutableStateFlow(1)
        val activeModeFlow = kotlinx.coroutines.flow.MutableStateFlow(0) // 0 = Edge Drawer, 1 = Classic Floating
        val pinMiniFpsFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
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

        val prefs = getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE)
        val savedStyle = prefs.getInt("hud_style", 1)
        activeStyleFlow.value = savedStyle.coerceIn(1, 6)
        val savedMode = prefs.getInt("hud_mode", 0)
        activeModeFlow.value = savedMode.coerceIn(0, 1)
        val savedPin = prefs.getBoolean("pin_mini_fps", false)
        pinMiniFpsFlow.value = savedPin

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
        if (intent?.action == ACTION_SET_MODE || intent?.hasExtra(EXTRA_MODE) == true) {
            val newMode = intent.getIntExtra(EXTRA_MODE, activeModeFlow.value).coerceIn(0, 1)
            activeModeFlow.value = newMode
            getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE).edit().putInt("hud_mode", newMode).apply()
        }
        if (intent?.action == ACTION_SET_PIN_FPS || intent?.hasExtra(EXTRA_PIN_FPS) == true) {
            val newPin = intent.getBooleanExtra(EXTRA_PIN_FPS, pinMiniFpsFlow.value)
            pinMiniFpsFlow.value = newPin
            getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE).edit().putBoolean("pin_mini_fps", newPin).apply()
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
        val prefs = getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE)
        val initialMode = activeModeFlow.value
        val initialY = prefs.getInt("hud_y", 280)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (initialMode == 0) 0 else 80
            y = initialY
        }

        val telemetryFlow = kotlinx.coroutines.flow.MutableStateFlow(LynxRepository.FloatingHudTelemetry())
        val isHudBenchmarkingFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
        val hudBenchmarkCountdownFlow = kotlinx.coroutines.flow.MutableStateFlow(60)
        val hudBenchmarkSummaryFlow = kotlinx.coroutines.flow.MutableStateFlow<com.noir.lynx.data.LynxBenchmarkResult?>(null)
        val currentDisplayHzFlow = kotlinx.coroutines.flow.MutableStateFlow(getDisplayRefreshRate())

        // Start Telemetry Query Loop (500ms for ultra-smooth live HUD)
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                Shell.cmd("dumpsys SurfaceFlinger --timestats -enable 2>/dev/null").exec()
            }
            while (isActive) {
                val displayHz = getDisplayRefreshRate()
                currentDisplayHzFlow.value = displayHz
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
                val activeMode by activeModeFlow.collectAsState()
                val pinMiniFps by pinMiniFpsFlow.collectAsState()
                val currentDisplayHz by currentDisplayHzFlow.collectAsState()
                var isExpanded by remember { mutableStateOf(false) }

                FloatingHudContent(
                    telemetry = tel,
                    hudMode = activeMode,
                    activeStyle = activeStyle,
                    pinMiniFps = pinMiniFps,
                    currentRefreshRate = currentDisplayHz,
                    isExpanded = isExpanded,
                    isHudBenchmarking = isHudBenchmarking,
                    hudBenchmarkCountdown = hudBenchmarkCountdown,
                    hudBenchmarkSummary = hudBenchmarkSummary,
                    onToggleExpand = { isExpanded = !isExpanded },
                    onSelectMode = { newMode ->
                        val clamped = newMode.coerceIn(0, 1)
                        activeModeFlow.value = clamped
                        getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE).edit().putInt("hud_mode", clamped).apply()
                        if (clamped == 0) {
                            layoutParams.x = 0
                        } else {
                            layoutParams.x = 80
                            layoutParams.y = 200
                        }
                        try {
                            windowManager.updateViewLayout(this@apply, layoutParams)
                        } catch (_: Exception) {}
                    },
                    onSelectStyle = { newStyle ->
                        val clamped = newStyle.coerceIn(1, 6)
                        activeStyleFlow.value = clamped
                        getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE).edit().putInt("hud_style", clamped).apply()
                    },
                    onTogglePinMiniFps = {
                        val newPin = !pinMiniFpsFlow.value
                        pinMiniFpsFlow.value = newPin
                        getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE).edit().putBoolean("pin_mini_fps", newPin).apply()
                    },
                    onClose = { cleanUpAndStop() },
                    onProfileSelect = { profile ->
                        telemetryFlow.value = tel.copy(activeProfile = profile)
                        serviceScope.launch {
                            LynxRepository.setProfile(profile)
                        }
                    },
                    onBoostRam = {
                        serviceScope.launch {
                            withContext(Dispatchers.IO) {
                                LynxRepository.dropCaches()
                            }
                        }
                    },
                    onCycleRefreshRate = {
                        serviceScope.launch {
                            val supported = withContext(Dispatchers.IO) {
                                LynxRepository.readSupportedRefreshRates()
                            }.ifEmpty { listOf(60, 90, 120) }
                            val cur = currentDisplayHzFlow.value
                            val next = when {
                                supported.contains(cur) -> {
                                    val idx = supported.indexOf(cur)
                                    supported[(idx + 1) % supported.size]
                                }
                                else -> 60
                            }
                            withContext(Dispatchers.IO) {
                                LynxRepository.setDisplayRefreshRate(next)
                            }
                            currentDisplayHzFlow.value = next
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
                        if (activeMode == 0) {
                            layoutParams.x = 0
                            layoutParams.y = (layoutParams.y + dy).toInt().coerceIn(60, 1800)
                            getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE).edit().putInt("hud_y", layoutParams.y).apply()
                        } else {
                            layoutParams.x = (layoutParams.x + dx).toInt()
                            layoutParams.y = (layoutParams.y + dy).toInt()
                        }
                        try {
                            windowManager.updateViewLayout(this@apply, layoutParams)
                        } catch (_: Exception) {}
                    },
                    onDragVertical = { dy ->
                        layoutParams.x = 0
                        layoutParams.y = (layoutParams.y + dy).toInt().coerceIn(60, 1800)
                        getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE).edit().putInt("hud_y", layoutParams.y).apply()
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

// ────────────────────────────────────────────────────────────
//  MODE 0: LYNX GAME EDGE DRAWER (INFINIX / ROG GAME SPACE STYLE)
// ────────────────────────────────────────────────────────────

@Composable
fun HudEdgeDrawer(
    telemetry: LynxRepository.FloatingHudTelemetry,
    activeStyle: Int,
    pinMiniFps: Boolean,
    currentRefreshRate: Int,
    isHudBenchmarking: Boolean,
    hudBenchmarkCountdown: Int,
    hudBenchmarkSummary: com.noir.lynx.data.LynxBenchmarkResult?,
    onSelectMode: (Int) -> Unit,
    onSelectStyle: (Int) -> Unit,
    onTogglePinMiniFps: () -> Unit,
    onProfileSelect: (String) -> Unit,
    onStartHudBenchmark: () -> Unit,
    onBoostRam: () -> Unit,
    onCycleRefreshRate: () -> Unit,
    onClose: () -> Unit,
    onDragVertical: (Float) -> Unit,
) {
    var isDrawerOpen by remember { mutableStateOf(false) }
    var showStylePicker by remember { mutableStateOf(false) }
    var isBoosting by remember { mutableStateOf(false) }
    var boostFeedback by remember { mutableStateOf<String?>(null) }
    var lastTouchTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Auto-collapse after 5 seconds of inactivity when drawer is open
    LaunchedEffect(isDrawerOpen, lastTouchTime) {
        if (isDrawerOpen) {
            delay(5000L)
            isDrawerOpen = false
            showStylePicker = false
        }
    }

    if (!isDrawerOpen) {
        // ── STATE 1: COLLAPSED EDGE HANDLE (Non-intrusive) ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .wrapContentSize()
                .pointerInput(Unit) {
                    while (true) {
                        awaitPointerEventScope {
                            val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: return@awaitPointerEventScope
                            var isDragging = false
                            var lastY = down.position.y
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!isDragging) {
                                        // Tap triggered!
                                        isDrawerOpen = true
                                        lastTouchTime = System.currentTimeMillis()
                                    }
                                    break
                                }
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                if (kotlin.math.abs(dx) > 8f || kotlin.math.abs(dy) > 8f) {
                                    isDragging = true
                                    change.consume()
                                    val deltaY = change.position.y - lastY
                                    lastY = change.position.y
                                    onDragVertical(deltaY)
                                    if (dx > 14f) {
                                        isDrawerOpen = true
                                        lastTouchTime = System.currentTimeMillis()
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            // Edge Tab Handle (Docked to bezel)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(Color(0xD90A0E14))
                    .border(
                        BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                // Sleek Vertical Glowing Bar
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(NeonCyan, Color.White, NeonCyan)
                            )
                        )
                )
            }

            // Optional Mini FPS Glanceable Pill (Docked next to handle)
            if (pinMiniFps) {
                Spacer(Modifier.width(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xEB0A0E16),
                    border = BorderStroke(1.dp, BorderHud),
                    shadowElevation = 4.dp,
                    modifier = Modifier.clickable {
                        isDrawerOpen = true
                        lastTouchTime = System.currentTimeMillis()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(NeonGreen)
                        )
                        Text(
                            text = "${telemetry.renderFps} FPS",
                            color = NeonGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    } else {
        // ── STATE 2: SLIDE-OUT GAME BAR (Expanded Drawer) ──
        Surface(
            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            color = Color(0xF40A0E16),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
            shadowElevation = 16.dp,
            modifier = Modifier
                .width(268.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            lastTouchTime = System.currentTimeMillis()
                            // Swiping left closes drawer
                            if (dragAmount.x < -18f) {
                                isDrawerOpen = false
                            } else {
                                onDragVertical(dragAmount.y)
                            }
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        lastTouchTime = System.currentTimeMillis()
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(NeonCyan)
                        )
                        Text(
                            "LYNX GAME BAR",
                            color = NeonCyan,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        // Switch to Classic Floating Window
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable {
                                    isDrawerOpen = false
                                    onSelectMode(1)
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("🗔 Float", color = TextSlate, fontSize = 8.5.sp)
                        }

                        // Style Dropdown Trigger
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x2200E5FF))
                                .clickable {
                                    showStylePicker = !showStylePicker
                                    lastTouchTime = System.currentTimeMillis()
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("S$activeStyle ▾", color = NeonCyan, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }

                        // Close Button
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0x22FF5252))
                                .clickable {
                                    isDrawerOpen = false
                                }
                                .padding(3.dp)
                        ) {
                            Icon(Icons.Default.Close, "Tutup", tint = NeonRed, modifier = Modifier.size(11.dp))
                        }
                    }
                }

                // Style Picker Dropdown
                AnimatedVisibility(visible = showStylePicker) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xEE121824),
                        border = BorderStroke(1.dp, BorderHud),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val styleNames = listOf("1.Pillar", "2.Ribbon", "3.Esport", "4.Tiles", "5.Deck", "6.Ghost")
                            styleNames.forEachIndexed { idx, label ->
                                val sId = idx + 1
                                val isSel = (activeStyle == sId)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSel) NeonCyan.copy(alpha = 0.3f) else Color(0x22FFFFFF))
                                        .clickable {
                                            onSelectStyle(sId)
                                            showStylePicker = false
                                            lastTouchTime = System.currentTimeMillis()
                                        }
                                        .padding(vertical = 3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        color = if (isSel) NeonCyan else Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Telemetry Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x77000000),
                    border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        // FPS Primary Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "${telemetry.renderFps}",
                                    color = NeonGreen,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    "FPS",
                                    color = NeonGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                            Text(
                                "AVG ${telemetry.avgFps.toInt()} · 1%L ${telemetry.fps1PercentLow.toInt()}",
                                color = TextSlate,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "${String.format(java.util.Locale.US, "%.1f", telemetry.frametimeMs)} ms",
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Frametime Sparkline
                        FrametimeSparklineCanvas(
                            history = telemetry.frametimeHistory,
                            heightDp = 14,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )

                        // Hardware Metrics Grid
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            // GPU
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                CategoryBadge("GPU", NeonMagenta)
                                Text(
                                    if (telemetry.gpuFreqMhz > 0) "${telemetry.gpuFreqMhz} MHz" else "--",
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(66.dp)
                                )
                                Text(
                                    "${telemetry.gpuLoadPct}%",
                                    color = NeonMagenta,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(36.dp)
                                )
                                Text(
                                    "58°C · ${String.format(java.util.Locale.US, "%.1f", telemetry.battWatt)}W",
                                    color = TextSlate,
                                    fontSize = 8.5.sp,
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
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(66.dp)
                                )
                                Text(
                                    "${telemetry.cpuLoadPct}%",
                                    color = NeonCyan,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(36.dp)
                                )
                                Text(
                                    "${telemetry.battTempC.toInt() + 15}°C",
                                    color = TextSlate,
                                    fontSize = 8.5.sp,
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
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(66.dp)
                                )
                                Text(
                                    "${telemetry.ramPct}%",
                                    color = NeonGold,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(36.dp)
                                )
                                Text(
                                    "ZRAM ${telemetry.zramUsedGb}G",
                                    color = TextSlate,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // BAT
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                CategoryBadge("BAT", NeonOrange)
                                Text(
                                    "${telemetry.battTempC}°C",
                                    color = if (telemetry.battTempC > 42f) NeonRed else NeonOrange,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(66.dp)
                                )
                                Text(
                                    "${telemetry.battCurrentMa} mA",
                                    color = Color.White,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(55.dp)
                                )
                                Text(
                                    "${telemetry.battLevelPct}%",
                                    color = NeonCyan,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 3. Kernel Mode Quick Switcher
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "MODE KERNEL",
                        color = TextSlate,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val profiles = listOf(
                            "balance" to "BAL",
                            "performance" to "PERF",
                            "extreme" to "EXT",
                            "powersave" to "PWR"
                        )
                        profiles.forEach { (pKey, pLabel) ->
                            val isSel = telemetry.activeProfile.equals(pKey, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) NeonCyan.copy(alpha = 0.25f) else Color(0x18FFFFFF),
                                border = BorderStroke(1.dp, if (isSel) NeonCyan else Color(0x22FFFFFF)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onProfileSelect(pKey)
                                        lastTouchTime = System.currentTimeMillis()
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = pLabel,
                                        color = if (isSel) NeonCyan else Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Quick Gaming Tools
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "GAMING TOOLS",
                        color = TextSlate,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Boost RAM
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isBoosting) NeonGreen.copy(alpha = 0.2f) else Color(0x18FFFFFF),
                            border = BorderStroke(1.dp, if (isBoosting) NeonGreen else Color(0x22FFFFFF)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (!isBoosting) {
                                        isBoosting = true
                                        boostFeedback = "..."
                                        onBoostRam()
                                        lastTouchTime = System.currentTimeMillis()
                                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
                                            delay(1200L)
                                            isBoosting = false
                                            boostFeedback = null
                                        }
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    if (isBoosting) "✓ Bersih" else "⚡ Boost RAM",
                                    color = if (isBoosting) NeonGreen else NeonCyan,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text("Drop Caches", color = TextMuted, fontSize = 7.sp, maxLines = 1)
                            }
                        }

                        // Lock Refresh Rate
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0x18FFFFFF),
                            border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    onCycleRefreshRate()
                                    lastTouchTime = System.currentTimeMillis()
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "🔒 $currentRefreshRate Hz",
                                    color = NeonGold,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text("Ganti Hz", color = TextMuted, fontSize = 7.sp, maxLines = 1)
                            }
                        }

                        // Pin Mini FPS
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (pinMiniFps) NeonGreen.copy(alpha = 0.2f) else Color(0x18FFFFFF),
                            border = BorderStroke(1.dp, if (pinMiniFps) NeonGreen else Color(0x22FFFFFF)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    onTogglePinMiniFps()
                                    lastTouchTime = System.currentTimeMillis()
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "📌 Pin FPS",
                                    color = if (pinMiniFps) NeonGreen else TextSlate,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(if (pinMiniFps) "Aktif" else "Off", color = TextMuted, fontSize = 7.sp, maxLines = 1)
                            }
                        }
                    }
                }

                // 5. Live Benchmark Trigger
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isHudBenchmarking) NeonMagenta.copy(alpha = 0.2f) else Color(0x12FFFFFF),
                    border = BorderStroke(1.dp, if (isHudBenchmarking) NeonMagenta else Color(0x1AFFFFFF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!isHudBenchmarking) {
                                onStartHudBenchmark()
                                lastTouchTime = System.currentTimeMillis()
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("⏱", fontSize = 9.sp)
                            Text(
                                if (isHudBenchmarking) "Benchmarking... ${hudBenchmarkCountdown}s" else "Mulai Benchmark 60s",
                                color = if (isHudBenchmarking) NeonMagenta else TextSlate,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (hudBenchmarkSummary != null && !isHudBenchmarking) {
                            val stabPct = (100f - hudBenchmarkSummary.jankyFramesPercent).coerceIn(0f, 100f).toInt()
                            Text(
                                "Stab $stabPct% · 1%L ${hudBenchmarkSummary.fps1PercentLow.toInt()}",
                                color = NeonGreen,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
//  FLOATING HUD ROUTER & CLASSIC WINDOW RENDERER
// ────────────────────────────────────────────────────────────

@Composable
fun FloatingHudContent(
    telemetry: LynxRepository.FloatingHudTelemetry,
    hudMode: Int = 0,
    activeStyle: Int = 1,
    pinMiniFps: Boolean = false,
    currentRefreshRate: Int = 120,
    isExpanded: Boolean,
    isHudBenchmarking: Boolean = false,
    hudBenchmarkCountdown: Int = 60,
    hudBenchmarkSummary: com.noir.lynx.data.LynxBenchmarkResult? = null,
    onToggleExpand: () -> Unit,
    onSelectMode: (Int) -> Unit,
    onSelectStyle: (Int) -> Unit,
    onTogglePinMiniFps: () -> Unit,
    onClose: () -> Unit,
    onProfileSelect: (String) -> Unit,
    onBoostRam: () -> Unit,
    onCycleRefreshRate: () -> Unit,
    onStartHudBenchmark: () -> Unit = {},
    onDrag: (Float, Float) -> Unit,
    onDragVertical: (Float) -> Unit,
) {
    if (hudMode == 0) {
        // Mode 0: Edge Drawer (Infinix Game Space / ROG Game Genie Style)
        HudEdgeDrawer(
            telemetry = telemetry,
            activeStyle = activeStyle,
            pinMiniFps = pinMiniFps,
            currentRefreshRate = currentRefreshRate,
            isHudBenchmarking = isHudBenchmarking,
            hudBenchmarkCountdown = hudBenchmarkCountdown,
            hudBenchmarkSummary = hudBenchmarkSummary,
            onSelectMode = onSelectMode,
            onSelectStyle = onSelectStyle,
            onTogglePinMiniFps = onTogglePinMiniFps,
            onProfileSelect = onProfileSelect,
            onStartHudBenchmark = onStartHudBenchmark,
            onBoostRam = onBoostRam,
            onCycleRefreshRate = onCycleRefreshRate,
            onClose = onClose,
            onDragVertical = onDragVertical
        )
    } else {
        // Mode 1: Classic Floating Window
        var showStylePicker by remember { mutableStateOf(false) }

        Column(modifier = Modifier.wrapContentSize().padding(4.dp)) {
            // Style Picker Dropdown
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

            // Quick Dock-To-Edge Header Bar for Classic Floating Window
            Surface(
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                color = Color(0xAA0A0E14),
                border = BorderStroke(1.dp, BorderHud),
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "🗔 Floating Window",
                        color = TextSlate,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeonCyan.copy(alpha = 0.2f))
                            .clickable { onSelectMode(0) }
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text("◄ Kunci ke Tepi (Drawer)", color = NeonCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Active Classic HUD Style
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
