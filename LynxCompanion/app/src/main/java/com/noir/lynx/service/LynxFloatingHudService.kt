package com.noir.lynx.service

import com.noir.lynx.core.locale.AppLocale
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noir.lynx.R
import com.noir.lynx.data.LynxHudTelemetryRepository
import com.noir.lynx.data.LynxRepository
import com.noir.lynx.ui.MainActivity
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
 *
 * Engineered with:
 * - Reactive state flow (zero UI desync across Quick Tile, Activity, Notification, and Watcher)
 * - Screen-state sleep shield (pauses telemetry during screen OFF, 0% CPU wake lock)
 * - Orientation auto-clamping preventing HUD off-screen loss on rotation
 * - Debounced disk writes preventing I/O queue floods during dragging
 * - Genuine Linux thermal zone parsing (CPU/SoC & GPU) without mock values
 */
class LynxFloatingHudService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var floatView: View? = null
    private var currentLayoutParams: WindowManager.LayoutParams? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var savePositionJob: Job? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    private var isScreenInteractive = true
    private val telemetryWakeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val outsideTouchFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var receiverRegistered = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenInteractive = false
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    isScreenInteractive = true
                    telemetryWakeSignal.tryEmit(Unit)
                }
            }
        }
    }

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
        val isRunningFlow = MutableStateFlow(false)
        val isRunning: Boolean get() = isRunningFlow.value

        const val ACTION_START = "com.noir.lynx.service.START_HUD"
        const val ACTION_STOP = "com.noir.lynx.service.STOP_HUD"
        const val ACTION_SET_STYLE = "com.noir.lynx.service.SET_STYLE"
        const val EXTRA_STYLE = "extra_hud_style"
        const val ACTION_SET_MODE = "com.noir.lynx.service.SET_MODE"
        const val EXTRA_MODE = "extra_hud_mode"
        const val ACTION_SET_PIN_FPS = "com.noir.lynx.service.SET_PIN_FPS"
        const val EXTRA_PIN_FPS = "extra_pin_fps"

        val activeStyleFlow = MutableStateFlow(1)
        val activeModeFlow = MutableStateFlow(0) // 0 = Edge Drawer, 1 = Classic Floating
        val pinMiniFpsFlow = MutableStateFlow(false)
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

        if (!Settings.canDrawOverlays(this)) {
            Shell.cmd("appops set $packageName SYSTEM_ALERT_WINDOW allow 2>/dev/null").exec()
            if (!Settings.canDrawOverlays(this)) {
                stopSelf()
                return
            }
        }

        LynxHudTelemetryRepository.reset()

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        isScreenInteractive = powerManager?.isInteractive ?: true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenStateReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(screenStateReceiver, filter)
            }
            receiverRegistered = true
        } catch (_: Exception) {}

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
        isRunningFlow.value = true
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
            updateModePosition(newMode)
        }
        if (intent?.action == ACTION_SET_PIN_FPS || intent?.hasExtra(EXTRA_PIN_FPS) == true) {
            val newPin = intent.getBooleanExtra(EXTRA_PIN_FPS, pinMiniFpsFlow.value)
            pinMiniFpsFlow.value = newPin
            getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE).edit().putBoolean("pin_mini_fps", newPin).apply()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        clampHudPositionToScreen()
    }

    private fun updateModePosition(mode: Int) {
        val view = floatView ?: return
        val params = currentLayoutParams ?: return
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        val prefs = getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE)

        if (mode == 0) {
            params.x = 0
            params.y = prefs.getInt("hud_y", 280).coerceIn(60, (screenH - 120).coerceAtLeast(100))
        } else {
            params.x = prefs.getInt("hud_x", 80).coerceIn(10, (screenW - 200).coerceAtLeast(20))
            params.y = prefs.getInt("hud_y", 280).coerceIn(60, (screenH - 120).coerceAtLeast(100))
        }
        try {
            windowManager.updateViewLayout(view, params)
            updateSystemGestureExclusion(view)
        } catch (_: Exception) {}
    }

    private fun updateSystemGestureExclusion(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (activeModeFlow.value == 0) {
                val h = view.height
                if (h > 0) {
                    val dm = resources.displayMetrics
                    val exclusionW = (220 * dm.density).toInt()
                    val maxH = (200 * dm.density).toInt()
                    val exclusionRect = android.graphics.Rect(0, 0, exclusionW, h.coerceAtMost(maxH))
                    view.systemGestureExclusionRects = listOf(exclusionRect)
                }
            } else {
                view.systemGestureExclusionRects = emptyList()
            }
        }
    }

    private fun clampHudPositionToScreen() {
        val view = floatView ?: return
        val params = currentLayoutParams ?: return
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        if (activeModeFlow.value == 0) {
            params.x = 0
            params.y = params.y.coerceIn(60, (screenH - 120).coerceAtLeast(100))
        } else {
            params.x = params.x.coerceIn(10, (screenW - 120).coerceAtLeast(20))
            params.y = params.y.coerceIn(60, (screenH - 120).coerceAtLeast(100))
        }
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {}
    }

    private fun debouncedSavePosition(x: Int, y: Int) {
        savePositionJob?.cancel()
        savePositionJob = serviceScope.launch {
            delay(300L)
            getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE).edit()
                .putInt("hud_x", x)
                .putInt("hud_y", y)
                .apply()
        }
    }

    private fun cleanUpAndStop() {
        isRunningFlow.value = false
        serviceScope.cancel()
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenStateReceiver)
            } catch (_: Exception) {}
            receiverRegistered = false
        }
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
        currentLayoutParams = null
        LynxHudTelemetryRepository.reset()
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
        val dm = resources.displayMetrics
        val initialY = prefs.getInt("hud_y", 280).coerceIn(60, (dm.heightPixels - 120).coerceAtLeast(100))
        val initialX = if (initialMode == 0) 0 else prefs.getInt("hud_x", 80).coerceIn(10, (dm.widthPixels - 200).coerceAtLeast(20))

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }
        currentLayoutParams = layoutParams

        val telemetryFlow = MutableStateFlow(LynxRepository.FloatingHudTelemetry())
        val isHudBenchmarkingFlow = MutableStateFlow(false)
        val hudBenchmarkCountdownFlow = MutableStateFlow(60)
        val hudBenchmarkSummaryFlow = MutableStateFlow<com.noir.lynx.data.LynxBenchmarkResult?>(null)
        val currentDisplayHzFlow = MutableStateFlow(getDisplayRefreshRate())

        // Start Telemetry Query Loop (500ms with Screen-State Sleep Shield)
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                Shell.cmd("dumpsys SurfaceFlinger --timestats -enable 2>/dev/null").exec()
            }
            while (isActive) {
                if (!isScreenInteractive) {
                    telemetryWakeSignal.first()
                }
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
            setOnTouchListener { _, event ->
                if ((event.action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_OUTSIDE) {
                    outsideTouchFlow.tryEmit(Unit)
                    true
                } else {
                    false
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
                    val h = bottom - top
                    if (h > 0 && activeModeFlow.value == 0) {
                        val dm = resources.displayMetrics
                        val exclusionW = (220 * dm.density).toInt()
                        val maxH = (200 * dm.density).toInt()
                        val exclusionRect = android.graphics.Rect(0, 0, exclusionW, h.coerceAtMost(maxH))
                        v.systemGestureExclusionRects = listOf(exclusionRect)
                    } else if (activeModeFlow.value != 0) {
                        v.systemGestureExclusionRects = emptyList()
                    }
                }
            }
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
                var isEdgeDrawerOpen by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    outsideTouchFlow.collect {
                        isEdgeDrawerOpen = false
                        isExpanded = false
                    }
                }

                FloatingHudContent(
                    telemetry = tel,
                    hudMode = activeMode,
                    activeStyle = activeStyle,
                    pinMiniFps = pinMiniFps,
                    currentRefreshRate = currentDisplayHz,
                    isExpanded = isExpanded,
                    isEdgeDrawerOpen = isEdgeDrawerOpen,
                    onToggleEdgeDrawer = { isEdgeDrawerOpen = it },
                    isHudBenchmarking = isHudBenchmarking,
                    hudBenchmarkCountdown = hudBenchmarkCountdown,
                    hudBenchmarkSummary = hudBenchmarkSummary,
                    onToggleExpand = { isExpanded = !isExpanded },
                    onSelectMode = { newMode ->
                        val clamped = newMode.coerceIn(0, 1)
                        activeModeFlow.value = clamped
                        getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE).edit().putInt("hud_mode", clamped).apply()
                        updateModePosition(clamped)
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
                        val currentParams = currentLayoutParams ?: layoutParams
                        val metrics = resources.displayMetrics
                        val screenW = metrics.widthPixels
                        val screenH = metrics.heightPixels
                        if (activeMode == 0) {
                            currentParams.x = 0
                            currentParams.y = (currentParams.y + dy).toInt().coerceIn(60, (screenH - 120).coerceAtLeast(100))
                            debouncedSavePosition(0, currentParams.y)
                        } else {
                            currentParams.x = (currentParams.x + dx).toInt().coerceIn(10, (screenW - 120).coerceAtLeast(20))
                            currentParams.y = (currentParams.y + dy).toInt().coerceIn(60, (screenH - 120).coerceAtLeast(100))
                            debouncedSavePosition(currentParams.x, currentParams.y)
                        }
                        try {
                            windowManager.updateViewLayout(this@apply, currentParams)
                        } catch (_: Exception) {}
                    },
                    onDragVertical = { dy ->
                        val currentParams = currentLayoutParams ?: layoutParams
                        val metrics = resources.displayMetrics
                        val screenH = metrics.heightPixels
                        currentParams.x = 0
                        currentParams.y = (currentParams.y + dy).toInt().coerceIn(60, (screenH - 120).coerceAtLeast(100))
                        debouncedSavePosition(0, currentParams.y)
                        try {
                            windowManager.updateViewLayout(this@apply, currentParams)
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
            cleanUpAndStop()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                AppLocale.getString(this@LynxFloatingHudService, R.string.floating_hud_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = AppLocale.getString(this@LynxFloatingHudService, R.string.floating_hud_notification_title)
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
                .setContentTitle(AppLocale.getString(this@LynxFloatingHudService, R.string.floating_hud_notification_title))
                .setContentText(AppLocale.getString(this@LynxFloatingHudService, R.string.floating_hud_notification_tap_close))
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pOpenIntent)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    AppLocale.getString(this@LynxFloatingHudService, R.string.floating_hud_action_close),
                    pStopIntent
                )
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(AppLocale.getString(this@LynxFloatingHudService, R.string.floating_hud_notification_title))
                .setContentText(AppLocale.getString(this@LynxFloatingHudService, R.string.floating_hud_notification_tap))
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
