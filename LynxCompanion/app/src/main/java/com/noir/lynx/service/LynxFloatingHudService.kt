package com.noir.lynx.service

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.*
import android.provider.Settings
import android.view.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.*
import com.noir.lynx.R
import com.noir.lynx.core.locale.AppLocale
import com.noir.lynx.feature.floating.FloatingToolType
import com.noir.lynx.feature.floating.LynxFloatingWindowManager
import com.noir.lynx.feature.floating.LynxFreeformManager
import com.noir.lynx.feature.hud.HudController
import com.noir.lynx.feature.hud.data.*
import com.noir.lynx.feature.hud.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.roundToInt

/** Bounded overlay host. Native collection is read-only and scoped to screen-on lifetime. */
class LynxFloatingHudService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    private lateinit var windows: WindowManager
    private lateinit var displays: DisplayManager
    private lateinit var repository: HudStreamRepository
    private lateinit var preferences: HudPreferencesStore
    private lateinit var controller: HudController
    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private val interactive = MutableStateFlow(false)
    private val rendered = MutableStateFlow(HudSnapshot())
    private val viewport = MutableStateFlow(HudViewport())
    private val open = MutableStateFlow(false)
    private val dockRight = MutableStateFlow(false)
    private val hz = MutableStateFlow(HudReading(unit = "Hz"))
    private val rates = MutableStateFlow<List<Float>>(emptyList())
    private var safeLeft = 0
    private var safeTop = 0
    private var safeWidth = 0
    private var safeHeight = 0
    private var registered = false

    companion object {
        val isRunningFlow = MutableStateFlow(false)
        val isRunning: Boolean get() = isRunningFlow.value
        val activeStyleFlow = MutableStateFlow(1)
        val activeModeFlow = MutableStateFlow(0)
        val pinMiniFpsFlow = MutableStateFlow(false)
        val snapshotFlow = MutableStateFlow(HudSnapshot())
        const val ACTION_START = "com.noir.lynx.service.START_HUD"
        const val ACTION_STOP = "com.noir.lynx.service.STOP_HUD"
        const val ACTION_SET_STYLE = "com.noir.lynx.service.SET_STYLE"
        const val EXTRA_STYLE = "extra_hud_style"
        const val ACTION_SET_MODE = "com.noir.lynx.service.SET_MODE"
        const val EXTRA_MODE = "extra_hud_mode"
        const val ACTION_SET_PIN_FPS = "com.noir.lynx.service.SET_PIN_FPS"
        const val EXTRA_PIN_FPS = "extra_pin_fps"
        const val ACTION_OPEN_TOOL = "com.noir.lynx.service.OPEN_FLOATING_TOOL"
        const val EXTRA_TOOL_TYPE = "extra_tool_type"
        private const val CHANNEL = "lynx_game_hud_channel"
        private const val WINDOW_TITLE = "LynxFloatingHud"
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            interactive.value = intent?.action != Intent.ACTION_SCREEN_OFF && (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
            if (!interactive.value) {
                open.value = false
                com.noir.lynx.feature.floating.StandaloneApps.closeWindows()
                LynxFloatingWindowManager.close()
            }
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) = updateDisplay()
        override fun onDisplayChanged(id: Int) { updateDisplay(); updateBounds() }
        override fun onDisplayRemoved(id: Int) = updateDisplay()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        saved.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windows = getSystemService(WINDOW_SERVICE) as WindowManager
        displays = getSystemService(DISPLAY_SERVICE) as DisplayManager
        repository = HudStreamRepository(this)
        preferences = HudPreferencesStore(this)
        controller = HudController(this, scope)
        LynxFloatingWindowManager.init(this, windows, scope, this, this)
        LynxFreeformManager.init(this, windows, scope, this, this)
        com.noir.lynx.feature.floating.StandaloneApps.init(this, windows, scope)
        val prefs = getSharedPreferences("lynx_hud_prefs", MODE_PRIVATE)
        activeStyleFlow.value = prefs.getInt("hud_style", 1).coerceIn(1, 6)
        activeModeFlow.value = prefs.getInt("hud_mode", 0).coerceIn(0, 1)
        dockRight.value = prefs.getBoolean("hud_dock_right", false)
        pinMiniFpsFlow.value = preferences.read().pinned
        startNotification()
        interactive.value = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(screenReceiver, filter)
        registered = true
        displays.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        updateBounds()
        updateDisplay()
        createOverlay()
        scope.launch {
            preferences.changes.collect { pinMiniFpsFlow.value = it.pinned }
        }
        scope.launch {
            open.collect { isOpen ->
                val p = params ?: return@collect
                val v = view ?: return@collect
                val dm = resources.displayMetrics
                if (isOpen && activeModeFlow.value == 0) {
                    p.width = (viewport.value.panelWidth * dm.density).toInt()
                    p.height = WindowManager.LayoutParams.WRAP_CONTENT
                } else {
                    p.width = WindowManager.LayoutParams.WRAP_CONTENT
                    p.height = WindowManager.LayoutParams.WRAP_CONTENT
                }
                v.post { clamp() }
                runCatching { windows.updateViewLayout(v, p) }
            }
        }
        scope.launch {
            interactive.collectLatest { on ->
                if (!on) {
                    repository.unavailable("stale")
                    rendered.value = repository.current()
                    snapshotFlow.value = rendered.value
                    return@collectLatest
                }
                coroutineScope {
                    launch(Dispatchers.IO) { repository.collect() }
                    try {
                        while (isActive) {
                            val sample = repository.current()
                            val displayReading = hz.value.copy(atMs = SystemClock.elapsedRealtime())
                            val current = sample.copy(metrics = sample.metrics + (HudMetric.DISPLAY_HZ.key to displayReading))
                            rendered.value = current
                            snapshotFlow.value = current
                            delay(250)
                        }
                    } finally {
                        withContext(NonCancellable + Dispatchers.IO) { repository.stop() }
                    }
                }
            }
        }
        isRunningFlow.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val prefs = getSharedPreferences("lynx_hud_prefs", MODE_PRIVATE)
        when (intent?.action) {
            ACTION_SET_STYLE -> {
                activeStyleFlow.value = intent.getIntExtra(EXTRA_STYLE, 1).coerceIn(1, 6)
                prefs.edit().putInt("hud_style", activeStyleFlow.value).apply()
            }
            ACTION_SET_MODE -> {
                activeModeFlow.value = intent.getIntExtra(EXTRA_MODE, 0).coerceIn(0, 1)
                open.value = false
                prefs.edit().putInt("hud_mode", activeModeFlow.value).apply()
                view?.post { clamp() }
            }
            ACTION_SET_PIN_FPS -> preferences.pin(intent.getBooleanExtra(EXTRA_PIN_FPS, false))
            ACTION_OPEN_TOOL -> {
                val toolName = intent.getStringExtra(EXTRA_TOOL_TYPE)
                val tool = runCatching { FloatingToolType.valueOf(toolName ?: "") }.getOrDefault(FloatingToolType.BROWSER)
                LynxFloatingWindowManager.openTool(tool)
            }
        }
        return START_NOT_STICKY
    }

    private fun targetDisplay(): Display? = view?.display ?: displays.getDisplay(Display.DEFAULT_DISPLAY)
    private fun updateDisplay() {
        val display = targetDisplay()
        val mode = display?.mode
        hz.value = if (mode != null) HudReading(mode.refreshRate.toDouble(), "Hz", "Display ${display.displayId}: mode ${mode.modeId}", SystemClock.elapsedRealtime(), "valid") else HudReading(unit = "Hz", status = "unsupported")
        rates.value = display?.supportedModes?.filter { mode != null && it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            ?.map { it.refreshRate }?.distinctBy { (it * 10).roundToInt() }?.sorted() ?: emptyList()
    }

    @Suppress("DEPRECATION")
    private fun updateBounds() {
        val density = resources.displayMetrics.density
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windows.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            safeLeft = insets.left; safeTop = insets.top
            safeWidth = metrics.bounds.width() - insets.left - insets.right
            safeHeight = metrics.bounds.height() - insets.top - insets.bottom
        } else {
            val metrics = android.util.DisplayMetrics()
            windows.defaultDisplay.getRealMetrics(metrics)
            val insets = view?.rootWindowInsets
            safeLeft = insets?.stableInsetLeft ?: 0; safeTop = insets?.stableInsetTop ?: 0
            safeWidth = metrics.widthPixels - safeLeft - (insets?.stableInsetRight ?: 0)
            safeHeight = metrics.heightPixels - safeTop - (insets?.stableInsetBottom ?: 0)
        }
        viewport.value = HudViewport(safeWidth / density, safeHeight / density)
        com.noir.lynx.feature.floating.StandaloneApps.viewportChanged()
        clamp()
    }

    private fun clamp() {
        val v = view ?: return
        val p = params ?: return
        val maxX = (safeLeft + safeWidth - v.width).coerceAtLeast(safeLeft)
        val nextX = if (activeModeFlow.value == 0) {
            if (dockRight.value) maxX else safeLeft
        } else {
            p.x.coerceIn(safeLeft, maxX)
        }
        val nextY = p.y.coerceIn(safeTop, (safeTop + safeHeight - v.height).coerceAtLeast(safeTop))
        if (p.x != nextX || p.y != nextY) {
            p.x = nextX
            p.y = nextY
            runCatching { windows.updateViewLayout(v, p) }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val handle = (48 * resources.displayMetrics.density).toInt()
            val top = ((v.height - handle) / 2).coerceAtLeast(0)
            val left = if (dockRight.value) (v.width - handle).coerceAtLeast(0) else 0
            val right = if (dockRight.value) v.width else handle.coerceAtMost(v.width)
            v.systemGestureExclusionRects = if (activeModeFlow.value == 0 && !open.value)
                listOf(android.graphics.Rect(left, top, right, (top + handle).coerceAtMost(v.height))) else emptyList()
        }
    }

    private fun dragSidebar(localX: Float, deltaLocalY: Float) {
        val p = params ?: return
        val v = view ?: return
        val maxX = (safeLeft + safeWidth - v.width).coerceAtLeast(safeLeft)
        val maxY = (safeTop + safeHeight - v.height).coerceAtLeast(safeTop)
        val fingerScreenX = p.x + localX
        val isRight = fingerScreenX > (safeLeft + safeWidth / 2)
        dockRight.value = isRight
        p.x = if (isRight) maxX else safeLeft
        p.y = (p.y + deltaLocalY.toInt()).coerceIn(safeTop, maxY)
        runCatching { windows.updateViewLayout(v, p) }
    }

    private fun drag(dx: Float, dy: Float) {
        val p = params ?: return
        val v = view ?: return
        val maxX = (safeLeft + safeWidth - v.width).coerceAtLeast(safeLeft)
        val maxY = (safeTop + safeHeight - v.height).coerceAtLeast(safeTop)
        p.x = if (activeModeFlow.value == 0) {
            if (dockRight.value) maxX else safeLeft
        } else {
            (p.x + dx.toInt()).coerceIn(safeLeft, maxX)
        }
        p.y = (p.y + dy.toInt()).coerceIn(safeTop, maxY)
        runCatching { windows.updateViewLayout(v, p) }
        val key = if (viewport.value.landscape) "hud_y_landscape_fraction" else "hud_y_portrait_fraction"
        getSharedPreferences("lynx_hud_prefs", MODE_PRIVATE).edit()
            .putFloat(key, (p.y - safeTop).toFloat() / safeHeight.coerceAtLeast(1))
            .putInt("hud_x", p.x)
            .putBoolean("hud_dock_right", dockRight.value)
            .apply()
    }

    private fun finishDrag() {
        val p = params ?: return
        val v = view ?: return
        val maxX = (safeLeft + safeWidth - v.width).coerceAtLeast(safeLeft)
        val maxY = (safeTop + safeHeight - v.height).coerceAtLeast(safeTop)
        if (activeModeFlow.value == 0) {
            p.x = if (dockRight.value) maxX else safeLeft
        } else {
            p.x = p.x.coerceIn(safeLeft, maxX)
        }
        p.y = p.y.coerceIn(safeTop, maxY)
        runCatching { windows.updateViewLayout(v, p) }
        val key = if (viewport.value.landscape) "hud_y_landscape_fraction" else "hud_y_portrait_fraction"
        getSharedPreferences("lynx_hud_prefs", MODE_PRIVATE).edit()
            .putFloat(key, (p.y - safeTop).toFloat() / safeHeight.coerceAtLeast(1))
            .putInt("hud_x", p.x)
            .putBoolean("hud_dock_right", dockRight.value)
            .apply()
        clamp()
    }

    private fun createOverlay() {
        val pref = getSharedPreferences("lynx_hud_prefs", MODE_PRIVATE)
        val key = if (viewport.value.landscape) "hud_y_landscape_fraction" else "hud_y_portrait_fraction"
        val initial = pref.getFloat(key, pref.getInt("hud_y", 280).toFloat() / safeHeight.coerceAtLeast(1)).coerceIn(0f, 1f)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT
        ).apply {
            title = WINDOW_TITLE
            gravity = Gravity.TOP or Gravity.LEFT
            x = if (activeModeFlow.value == 0) {
                if (dockRight.value) safeLeft + safeWidth else safeLeft
            } else {
                pref.getInt("hud_x", safeLeft)
            }
            y = safeTop + (safeHeight * initial).toInt()
        }
        val host = ComposeView(this)
        host.setViewTreeLifecycleOwner(this)
        host.setViewTreeSavedStateRegistryOwner(this)
        host.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                open.value = false
                false
            } else false
        }
        host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> host.post { clamp() } }
        host.setOnApplyWindowInsetsListener { _, insets -> host.post { updateBounds() }; insets }
        host.setContent {
            val sample by rendered.collectAsState()
            val size by viewport.collectAsState()
            val expanded by open.collectAsState()
            val isDockRight by dockRight.collectAsState()
            val mode by activeModeFlow.collectAsState()
            val style by activeStyleFlow.collectAsState()
            val pinned by pinMiniFpsFlow.collectAsState()
            val action by controller.actions.collectAsState()
            val apps by controller.apps.collectAsState()
            val supported by rates.collectAsState()
            if (mode == 0) {
                HudSidebar(
                    snapshot = sample,
                    viewport = size,
                    open = expanded,
                    preferences = preferences,
                    action = action,
                    apps = apps,
                    rates = supported,
                    onOpen = { open.value = it },
                    onDrag = { drag(0f, it) },
                    onProfile = controller::profile,
                    onHz = { controller.refresh(it, supported, targetDisplay()) { hz.value.value?.toFloat() } },
                    onCapture = { controller.screenshot { hidden -> host.visibility = if (hidden) View.INVISIBLE else View.VISIBLE } },
                    onApp = controller::launch,
                    onStop = { stopSelf() },
                    onBoostRam = controller::boostRam,
                    dockRight = isDockRight,
                    onDrag2D = ::dragSidebar,
                    onDragEnd = ::finishDrag
                )
            } else {
                FloatingHudContent(
                    telemetry = HudLegacyAdapter.project(sample),
                    hudMode = 1,
                    activeStyle = style,
                    pinMiniFps = pinned,
                    currentRefreshRate = sample[HudMetric.DISPLAY_HZ].value?.roundToInt() ?: 0,
                    isExpanded = expanded,
                    onToggleExpand = { open.value = !open.value },
                    onSelectMode = {
                        activeModeFlow.value = it
                        open.value = false
                        pref.edit().putInt("hud_mode", it).apply()
                    },
                    onSelectStyle = {
                        activeStyleFlow.value = it
                        pref.edit().putInt("hud_style", it).apply()
                    },
                    onTogglePinMiniFps = { preferences.pin(!pinned) },
                    onClose = { stopSelf() },
                    onProfileSelect = { profile ->
                        if (profile == "extreme") {
                            activeModeFlow.value = 0
                            open.value = true
                        } else controller.profile(profile)
                    },
                    onBoostRam = {},
                    onCycleRefreshRate = {},
                    onDrag = ::drag,
                    onDragVertical = { drag(0f, it) }
                )
            }
        }
        view = host
        runCatching { windows.addView(host, params); updateDisplay() }.onFailure { stopSelf() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateBounds(); updateDisplay()
        val key = if (viewport.value.landscape) "hud_y_landscape_fraction" else "hud_y_portrait_fraction"
        params?.y = safeTop + (safeHeight * getSharedPreferences("lynx_hud_prefs", MODE_PRIVATE).getFloat(key, 0.2f)).toInt()
        view?.post { clamp(); params?.let { p -> runCatching { windows.updateViewLayout(view, p) } } }
    }

    private fun startNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, AppLocale.getString(this, R.string.floating_hud_channel_name), NotificationManager.IMPORTANCE_LOW))
        val launch = Intent(this, com.noir.lynx.ui.MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(
            8801,
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(AppLocale.getString(this, R.string.floating_hud_notification_title))
                .setContentText(AppLocale.getString(this, R.string.floating_hud_notification_tap_close))
                .setContentIntent(pending)
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, AppLocale.getString(this, R.string.floating_hud_action_close), stop).build())
                .build()
        )
    }

    override fun onDestroy() {
        if (registered) unregisterReceiver(screenReceiver)
        if (::displays.isInitialized) displays.unregisterDisplayListener(displayListener)
        com.noir.lynx.feature.floating.StandaloneApps.destroy()
        scope.cancel()
        if (::controller.isInitialized) CoroutineScope(Dispatchers.IO).launch { controller.restoreDisplay() }
        LynxFloatingWindowManager.destroy()
        LynxFreeformManager.destroy()
        view?.let { runCatching { windows.removeView(it) }; it.disposeComposition() }
        view = null
        snapshotFlow.value = HudSnapshot()
        isRunningFlow.value = false
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
