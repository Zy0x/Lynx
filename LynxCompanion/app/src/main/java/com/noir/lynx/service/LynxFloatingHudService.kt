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
                .setContentTitle("Lynx Floating Game HUD Aktif")
                .setContentText("Ketuk untuk membuka Lynx atau tutup overlay")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pOpenIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Tutup HUD", pStopIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Lynx Floating Game HUD Aktif")
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


// ────────────────────────────────────────────────────────────
//  MODE 0: LYNX GAME EDGE DRAWER (INFINIX / ROG GAME SPACE STYLE)
// ────────────────────────────────────────────────────────────
