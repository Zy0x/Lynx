package com.noir.lynx.service

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.*
import com.noir.lynx.feature.floating.FloatingToolType
import com.noir.lynx.feature.floating.LynxFloatingWindowManager
import com.noir.lynx.feature.floating.LynxFreeformManager
import com.noir.lynx.feature.hud.HudController
import com.noir.lynx.feature.hud.addins.HudGamingAddins
import com.noir.lynx.feature.hud.autoskip.GenshinSkipEngine
import com.noir.lynx.feature.hud.data.*
import com.noir.lynx.feature.hud.ui.*
import com.noir.lynx.feature.hud.overlay.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs
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
    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var autoSkipWindow: HudAutoSkipWindow? = null
    private var dragStartY = 0
    private var magicPinWindow: com.noir.lynx.feature.hud.overlay.HudMagicBoxPinWindow? = null
    private var comboRecorderWindow: com.noir.lynx.feature.hud.overlay.HudComboRecorderWindow? = null
    private var comboTriggersOverlay: com.noir.lynx.feature.hud.overlay.HudComboTriggersOverlay? = null
    private val interactive = MutableStateFlow(false)
    private val rendered = MutableStateFlow(HudSnapshot())
    private val viewport = MutableStateFlow(HudViewport())
    private val open = MutableStateFlow(false)
    private val dockRight = MutableStateFlow(false)
    private val hz = MutableStateFlow(HudReading(unit = "Hz"))
    private val rates = MutableStateFlow<List<Float>>(emptyList())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var safeLeft = 0
    private var safeRight = 0
    private var safeTop = 0
    private var safeWidth = 0
    private var safeHeight = 0
    private var screenWidth = 0
    private var screenHeight = 0
    private var registered = false
    private val foregroundDetector by lazy { LynxForegroundDetector(this) }
    private fun resolveActivePackage(samplePkg: String): String = samplePkg.takeIf { it.isNotBlank() }
        ?: controller.currentPackage?.takeIf { it.isNotBlank() }
        ?: LynxAppAutomationService.activeGamePackage?.takeIf { it.isNotBlank() }
        ?: foregroundDetector.queryTopResumedPackage()?.packageName.orEmpty()

    private fun resolveContextualProfile(pkg: String, streamProfile: String?): String {
        val streamProf = streamProfile?.takeIf { it.isNotBlank() && it != "null" }
        if (streamProf != null) {
            return com.noir.lynx.data.LynxRepository.normalizeProfileKey(streamProf)
        }
        return com.noir.lynx.data.LynxRepository.readCurrentProfileFast()
    }

    companion object {
        const val ACTION_HUD_STATE_CHANGED = "com.noir.lynx.HUD_STATE_CHANGED"
        const val EXTRA_IS_RUNNING = "extra_is_running"
        const val FLAG_FILE_NAME = "hud_active.flag"
        val isRunningFlow = MutableStateFlow(false)
        fun isHudProcessAlive(context: Context): Boolean = hudProcessAlive(context, FLAG_FILE_NAME).also {
            if (!it) isRunningFlow.value = false
        }
        val isRunning: Boolean get() = runCatching {
            com.noir.lynx.data.LynxRepository.appContext ?: com.noir.lynx.LynxApp.instance
        }.getOrNull()?.let { isHudProcessAlive(it) } ?: false
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
        if (!Settings.canDrawOverlays(this)) {
            runCatching {
                com.topjohnwu.superuser.Shell.cmd(
                    "appops set $packageName SYSTEM_ALERT_WINDOW allow 2>/dev/null",
                    "pm grant $packageName android.permission.SYSTEM_ALERT_WINDOW 2>/dev/null"
                ).exec()
            }
        }
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
        createAutoSkipOverlay()
        createMagicPinOverlay()
        scope.launch {
            preferences.changes.collect { pinMiniFpsFlow.value = it.pinned }
        }
        scope.launch {
            HudGamingAddins.current?.magic?.isPlacing?.collect { placing ->
                if (placing) open.value = false
            }
        }
        scope.launch {
            HudGamingAddins.current?.comboManager?.editingComboId?.collect { editingId ->
                if (editingId != null) open.value = false
            }
        }
        scope.launch {
            open.collect { isOpen ->
                val p = params ?: return@collect
                val v = view ?: return@collect
                val dm = resources.displayMetrics
                if (activeModeFlow.value == 0) {
                    p.gravity = Gravity.TOP or if (dockRight.value) Gravity.RIGHT else Gravity.LEFT
                    p.x = if (dockRight.value) safeRight else safeLeft
                    p.width = if (isOpen) (viewport.value.panelWidth * dm.density).toInt() else WindowManager.LayoutParams.WRAP_CONTENT
                    p.height = WindowManager.LayoutParams.WRAP_CONTENT
                    if (isOpen) {
                        // Capture and save current handle Y before expanding
                        if (p.y > safeTop + 2) {
                            val isLandscape = viewport.value.landscape || (screenWidth > screenHeight && screenWidth > 0)
                            val key = if (isLandscape) "hud_y_landscape_fraction" else "hud_y_portrait_fraction"
                            val frac = ((p.y - safeTop).toFloat() / safeHeight.coerceAtLeast(1)).coerceIn(0.05f, 0.95f)
                            getSharedPreferences("lynx_hud_prefs", MODE_PRIVATE).edit()
                                .putFloat(key, frac)
                                .putBoolean("hud_dock_right", dockRight.value)
                                .apply()
                        }
                        p.y = viewport.value.panelTop(safeTop, safeHeight, dm.density, getSavedHandleYFraction())
                    } else {
                        // Restore collapsed handle to user's saved strategic position
                        val frac = getSavedHandleYFraction()
                        val handleHeight = (72f * dm.density).roundToInt()
                        val maxHandleY = (safeTop + safeHeight - handleHeight).coerceAtLeast(safeTop)
                        p.y = (safeTop + safeHeight * frac).roundToInt().coerceIn(safeTop, maxHandleY)
                    }
                } else {
                    p.gravity = Gravity.TOP or Gravity.LEFT
                    p.width = WindowManager.LayoutParams.WRAP_CONTENT
                    p.height = WindowManager.LayoutParams.WRAP_CONTENT
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (isOpen) {
                        p.flags = p.flags or android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                        p.blurBehindRadius = 45
                    } else {
                        p.flags = p.flags and android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                        p.blurBehindRadius = 0
                    }
                }
                runCatching { windows.updateViewLayout(v, p) }
                v.post { clamp() }
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
                            val detectedPkg = resolveActivePackage(sample.packageName)
                            val resolvedProfile = resolveContextualProfile(detectedPkg, sample.profile)
                            val current = sample.copy(
                                packageName = detectedPkg,
                                profile = resolvedProfile,
                                metrics = sample.metrics + (HudMetric.DISPLAY_HZ.key to displayReading)
                            )
                            rendered.value = current
                            snapshotFlow.value = current
                            val dm = resources.displayMetrics
                            HudGamingAddins.current?.comboManager?.updateDimensions(current.packageName, open.value, dm.widthPixels, dm.heightPixels)
                            val isFocused = open.value || pinMiniFpsFlow.value || activeModeFlow.value == 1
                            val pollInterval = if (isFocused) 500L else 1500L
                            delay(pollInterval)
                        }
                    } finally {
                        withContext(NonCancellable + Dispatchers.IO) { repository.stop() }
                    }
                }
            }
        }
        runCatching {
            val flagFile = java.io.File(filesDir, FLAG_FILE_NAME)
            flagFile.writeText(android.os.Process.myPid().toString())
        }
        isRunningFlow.value = true
        scope.launch(Dispatchers.IO) {
            runCatching {
                val pid = android.os.Process.myPid()
                com.topjohnwu.superuser.Shell.cmd("echo -1000 > /proc/$pid/oom_score_adj").exec()
            }
        }
        runCatching {
            sendBroadcast(Intent(ACTION_HUD_STATE_CHANGED).putExtra(EXTRA_IS_RUNNING, true).setPackage(packageName))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val prefs = getSharedPreferences("lynx_hud_prefs", MODE_PRIVATE)
        if (intent?.hasExtra(EXTRA_MODE) == true) {
            val targetMode = intent.getIntExtra(EXTRA_MODE, 0).coerceIn(0, 1)
            activeModeFlow.value = targetMode
            open.value = false
            view?.post { clamp() }
        }
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
        return START_STICKY
    }

    private fun targetDisplay(): Display? = view?.display ?: displays.getDisplay(Display.DEFAULT_DISPLAY)
    private fun updateDisplay() {
        val display = targetDisplay()
        val mode = display?.mode
        hz.value = if (mode != null) HudReading(mode.refreshRate.toDouble(), "Hz", "Display ${display.displayId}: mode ${mode.modeId}", SystemClock.elapsedRealtime(), "valid") else HudReading(unit = "Hz", status = "unsupported")
        rates.value = display?.supportedModes?.filter { m ->
            mode != null && ((m.physicalWidth == mode.physicalWidth && m.physicalHeight == mode.physicalHeight) ||
                    (m.physicalWidth == mode.physicalHeight && m.physicalHeight == mode.physicalWidth))
        }?.map { it.refreshRate }?.distinctBy { (it * 10).roundToInt() }?.sorted()
            ?.ifEmpty { listOf(60f, 90f, 120f) } ?: listOf(60f, 90f, 120f)
    }

    @Suppress("DEPRECATION")
    private fun updateBounds() {
        val density = resources.displayMetrics.density
        val bounds = readHudPanelBounds(windows, view?.rootWindowInsets)
        safeLeft = bounds.left; safeRight = bounds.rightInset; safeTop = bounds.top
        safeWidth = bounds.width; safeHeight = bounds.height
        screenWidth = safeLeft + safeWidth + safeRight
        screenHeight = safeTop + safeHeight
        val rotated = viewport.value.landscape != (safeWidth > safeHeight)
        viewport.value = HudViewport(safeWidth / density, safeHeight / density)
        if (rotated && !open.value) params?.y = safeTop + (safeHeight * getSavedHandleYFraction()).roundToInt()
        com.noir.lynx.feature.floating.StandaloneApps.viewportChanged()
        clamp()
        autoSkipWindow?.updateBounds()
    }

    private fun updateExclusionRects(v: View) {
        if (Build.VERSION.SDK_INT >= 29) {
            v.systemGestureExclusionRects = if (activeModeFlow.value == 0 && !open.value && v.width > 0 && v.height > 0) {
                val grip = (48f * resources.displayMetrics.density).roundToInt().coerceAtMost(v.width)
                listOf(if (dockRight.value) Rect(v.width - grip, 0, v.width, v.height) else Rect(0, 0, grip, v.height))
            } else {
                emptyList()
            }
        }
    }

    private fun getSavedHandleYFraction(): Float {
        val pref = getSharedPreferences("lynx_hud_prefs", MODE_PRIVATE)
        val isLandscape = viewport.value.landscape || (screenWidth > screenHeight && screenWidth > 0)
        val key = if (isLandscape) "hud_y_landscape_fraction" else "hud_y_portrait_fraction"
        val defaultFraction = if (isLandscape) 0.28f else 0.40f
        return pref.getFloat(key, defaultFraction).coerceIn(0.05f, 0.95f)
    }

    private fun clamp() {
        val v = view ?: return
        val p = params ?: return
        val nextGravity: Int
        val nextX: Int
        val nextY: Int
        val density = resources.displayMetrics.density
        if (activeModeFlow.value == 0) {
            nextGravity = Gravity.TOP or if (dockRight.value) Gravity.RIGHT else Gravity.LEFT
            nextX = if (dockRight.value) safeRight else safeLeft
            if (open.value) {
                nextY = viewport.value.panelTop(safeTop, safeHeight, density, getSavedHandleYFraction())
            } else {
                val handleHeight = if (v.height in 1..(120f * density).toInt()) v.height else (72f * density).roundToInt()
                val maxY = (safeTop + safeHeight - handleHeight).coerceAtLeast(safeTop)
                val currentY = if (p.y <= safeTop + 2) {
                    (safeTop + safeHeight * getSavedHandleYFraction()).roundToInt()
                } else {
                    p.y
                }
                nextY = currentY.coerceIn(safeTop, maxY)
            }
        } else {
            nextGravity = Gravity.TOP or Gravity.LEFT
            val maxX = (safeLeft + safeWidth - v.width).coerceAtLeast(safeLeft)
            nextX = p.x.coerceIn(safeLeft, maxX)
            nextY = p.y.coerceIn(safeTop, (safeTop + safeHeight - v.height).coerceAtLeast(safeTop))
        }

        val nextWidth = if (activeModeFlow.value == 0 && open.value) (viewport.value.panelWidth * density).roundToInt()
            else WindowManager.LayoutParams.WRAP_CONTENT
        if (p.gravity != nextGravity || p.x != nextX || p.y != nextY || p.width != nextWidth) {
            p.width = nextWidth
            p.gravity = nextGravity
            p.x = nextX
            p.y = nextY
            runCatching { windows.updateViewLayout(v, p) }
        }
        updateExclusionRects(v)
    }

    private fun dragClassic(dx: Float, dy: Float) {
        val p = params ?: return
        val v = view ?: return
        val maxX = (safeLeft + safeWidth - v.width).coerceAtLeast(safeLeft)
        val maxY = (safeTop + safeHeight - v.height).coerceAtLeast(safeTop)
        p.gravity = Gravity.TOP or Gravity.LEFT
        p.x = (p.x + dx.toInt()).coerceIn(safeLeft, maxX)
        p.y = (p.y + dy.toInt()).coerceIn(safeTop, maxY)
        runCatching { windows.updateViewLayout(v, p) }
        savePositionPrefs()
    }

    private fun savePositionPrefs() {
        val p = params ?: return
        val isLandscape = viewport.value.landscape || (screenWidth > screenHeight && screenWidth > 0)
        val key = if (isLandscape) "hud_y_landscape_fraction" else "hud_y_portrait_fraction"
        val pref = getSharedPreferences("lynx_hud_prefs", MODE_PRIVATE).edit()
        pref.putBoolean("hud_dock_right", dockRight.value)
        if (activeModeFlow.value == 0) {
            if (!open.value) {
                val frac = ((p.y - safeTop).toFloat() / safeHeight.coerceAtLeast(1)).coerceIn(0.05f, 0.95f)
                pref.putFloat(key, frac)
            }
        } else {
            val frac = ((p.y - safeTop).toFloat() / safeHeight.coerceAtLeast(1)).coerceIn(0.05f, 0.95f)
            pref.putFloat(key, frac)
            pref.putInt("hud_x", p.x)
        }
        pref.apply()
    }

    private fun createAutoSkipOverlay() {
        val visible = combine(rendered, preferences.changes, interactive) { sample, prefs, isInteractive ->
            val pkg = resolveActivePackage(sample.packageName)
            if (pkg.isNotBlank() && controller.currentPackage != pkg) controller.currentPackage = pkg
            prefs.autoSkipEnabled && GenshinSkipEngine.isGameSupported(pkg) && isInteractive
        }
        autoSkipWindow = HudAutoSkipWindow(this, scope, this, this, controller.isSkipping,
            controller.isPaused, visible, controller::toggleAutoSkip,
            mode = controller.autoSkipMode, executing = controller.autoSkipExecuting)
    }

    private fun createMagicPinOverlay() {
        val magic = HudGamingAddins.current?.magic ?: return
        magicPinWindow = com.noir.lynx.feature.hud.overlay.HudMagicBoxPinWindow(
            context = this,
            scope = scope,
            lifecycle = this,
            savedState = this,
            magic = magic,
            onClosePlacer = { reopen -> if (reopen) open.value = true }
        )

        val comboManager = HudGamingAddins.current?.comboManager
        if (comboManager != null) {
            comboRecorderWindow = com.noir.lynx.feature.hud.overlay.HudComboRecorderWindow(
                context = this,
                scope = scope,
                lifecycle = this,
                savedState = this,
                comboManager = comboManager,
                onClosePlacer = { reopen -> if (reopen) open.value = true }
            )

            comboTriggersOverlay = com.noir.lynx.feature.hud.overlay.HudComboTriggersOverlay(
                context = this,
                scope = scope,
                lifecycle = this,
                savedState = this,
                comboManager = comboManager,
                isHudOpenFlow = open
            )
        }
    }

    private fun createOverlay() {
        val pref = getSharedPreferences("lynx_hud_prefs", MODE_PRIVATE)
        val initial = getSavedHandleYFraction()
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            title = WINDOW_TITLE
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            if (activeModeFlow.value == 0) {
                gravity = Gravity.TOP or if (dockRight.value) Gravity.RIGHT else Gravity.LEFT
                x = if (dockRight.value) safeRight else safeLeft
            } else {
                gravity = Gravity.TOP or Gravity.LEFT
                x = pref.getInt("hud_x", safeLeft)
            }
            y = safeTop + (safeHeight * initial).roundToInt()
        }
        val composeView = ComposeView(this)
        val host = HudSidebarHostView(
            context = this,
            sidebar = { activeModeFlow.value == 0 }, expanded = { open.value }, right = { dockRight.value },
            paged = { pinMiniFpsFlow.value && HudMiniPolicy.metrics(preferences.read().selected).size > 1 },
            onOpen = { open.value = it }, onDragStart = { dragStartY = params?.y ?: safeTop },
            onDrag = { _, dy, rawX ->
                val p = params
                if (p != null) {
                    val nextRight = if (dockRight.value) rawX >= safeLeft + safeWidth * .4f else rawX > safeLeft + safeWidth * .6f
                    dockRight.value = nextRight
                    p.gravity = Gravity.TOP or if (nextRight) Gravity.RIGHT else Gravity.LEFT
                    p.x = if (nextRight) safeRight else safeLeft
                    p.y = (dragStartY + dy.toInt()).coerceIn(safeTop, (safeTop + safeHeight - (view?.height ?: 0)).coerceAtLeast(safeTop))
                    view?.let { runCatching { windows.updateViewLayout(it, p) } }
                }
            }, onDragEnd = { savePositionPrefs(); clamp() },
            onBoundsChanged = { view?.let { updateExclusionRects(it) }; clamp() }
        ).apply {
            setViewTreeLifecycleOwner(this@LynxFloatingHudService)
            setViewTreeSavedStateRegistryOwner(this@LynxFloatingHudService)
            addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
            setOnApplyWindowInsetsListener { _, insets -> post { updateBounds() }; insets }
        }
        composeView.setContent {
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
            val bypassStatus by controller.bypassStatus.collectAsState()
            val focusActive by controller.focusActive.collectAsState()
            val touchStatus by controller.touchStatus.collectAsState()
            val shieldStatus by controller.shieldStatus.collectAsState()
            val brightnessLocked by controller.brightnessLocked.collectAsState()
            val isSkipping by controller.isSkipping.collectAsState()
            val isPaused by controller.isPaused.collectAsState()
            val isTappingLive by controller.autoSkipExecuting.collectAsState()
            val hudPrefs by preferences.changes.collectAsState(initial = preferences.read())

            LaunchedEffect(sample.packageName, expanded, size, hudPrefs) {
                val dm = resources.displayMetrics
                val activePkg = resolveActivePackage(sample.packageName)
                controller.syncAutoSkip(
                    pkg = activePkg,
                    isHudOpen = expanded,
                    width = dm.widthPixels,
                    height = dm.heightPixels,
                    prefs = hudPrefs
                )
            }
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
                    onProfile = controller::profile,
                    onHz = { targetHz ->
                        controller.refresh(targetHz, supported.ifEmpty { listOf(60f, 90f, 120f) }, targetDisplay()) {
                            updateDisplay()
                            val cur: Float? = try {
                                kotlinx.coroutines.runBlocking { com.noir.lynx.data.LynxDisplayRepository.readDisplayRefreshRate().toFloat() }
                            } catch (_: Throwable) { null }
                            if (cur != null && kotlin.math.abs(cur - targetHz) < 1.0f) cur else hz.value.value?.toFloat()
                        }
                    },
                    onCapture = {
                        controller.screenshot(
                            hide = { hidden -> host.visibility = if (hidden) View.INVISIBLE else View.VISIBLE },
                            onCaptured = { uri ->
                                HudGamingAddins.current?.screenshotPreview?.show(uri)
                            }
                        )
                    },
                    onApp = controller::launch,
                    onStop = { stopSelf() },
                    onBoostRam = controller::boostRam,
                    dockRight = isDockRight,
                    bypassStatus = bypassStatus,
                    focusActive = focusActive,
                    onToggleFocus = controller::toggleFocus,
                    onToggleBypass = controller::setBypass,
                    onRefreshBypass = controller::refreshBypassStatus,
                    onProbeNetwork = controller::probeNetwork,
                    touchStatus = touchStatus,
                    shieldStatus = shieldStatus,
                    brightnessLocked = brightnessLocked,
                    onToggleTouchBoost = controller::setTouchBoost,
                    onToggleHeadsUpShield = controller::setHeadsUpShield,
                    onSelectFocusLevel = controller::setFocusLevel,
                    onToggleBrightnessLock = controller::setBrightnessLock,
                    isSkipping = isSkipping,
                    isPaused = isPaused,
                    isTappingLive = isTappingLive,
                    onToggleAutoSkip = controller::toggleAutoSkip,
                    sceneResult = controller.lastSceneSnapshot.collectAsState().value,
                    onSnapshotScene = {
                        host.visibility = View.INVISIBLE
                        mainHandler.postDelayed({
                            controller.triggerSceneSnapshot { dir ->
                                host.visibility = View.VISIBLE
                                android.widget.Toast.makeText(
                                    this@LynxFloatingHudService,
                                    "📸 Snapshot diagnosa tersimpan di $dir",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }, 180L)
                    }
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
                    onBoostRam = controller::boostRam,
                    onCycleRefreshRate = {
                        val supportedRates = supported.ifEmpty { listOf(60f, 90f, 120f) }
                        val currentVal = hz.value.value?.toFloat() ?: 60f
                        val currentIdx = supportedRates.indexOfFirst { kotlin.math.abs(it - currentVal) < 0.5f }
                        val nextIdx = if (currentIdx >= 0) (currentIdx + 1) % supportedRates.size else 0
                        val nextHz = supportedRates[nextIdx]
                        controller.refresh(nextHz, supportedRates, targetDisplay()) {
                            updateDisplay()
                            val cur: Float? = try {
                                kotlinx.coroutines.runBlocking { com.noir.lynx.data.LynxDisplayRepository.readDisplayRefreshRate().toFloat() }
                            } catch (_: Throwable) { null }
                            if (cur != null && kotlin.math.abs(cur - nextHz) < 1.0f) cur else hz.value.value?.toFloat()
                        }
                    },
                    onDrag = ::dragClassic,
                    onDragVertical = { dragClassic(0f, it) }
                )
            }
        }
        view = host
        runCatching { windows.addView(host, params); updateDisplay() }.onFailure { stopSelf() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateBounds(); updateDisplay()
        if (!open.value) params?.y = safeTop + (safeHeight * getSavedHandleYFraction()).roundToInt()
        view?.post { clamp(); params?.let { p -> runCatching { windows.updateViewLayout(view, p) } } }
        autoSkipWindow?.updateBounds()
    }

    private fun startNotification() {
        startForeground(8801, hudForegroundNotification(this, CHANNEL, Intent(this, javaClass).setAction(ACTION_STOP)))
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        if (registered) unregisterReceiver(screenReceiver)
        if (::displays.isInitialized) displays.unregisterDisplayListener(displayListener)
        com.noir.lynx.feature.floating.StandaloneApps.destroy()
        scope.cancel()
        if (::controller.isInitialized) CoroutineScope(Dispatchers.IO).launch { controller.restoreDisplay() }
        LynxFloatingWindowManager.destroy()
        LynxFreeformManager.destroy()
        view?.let {
            runCatching { windows.removeView(it) }
            ((it as? ViewGroup)?.getChildAt(0) as? ComposeView)?.disposeComposition()
        }
        view = null
        autoSkipWindow?.close()
        autoSkipWindow = null
        magicPinWindow?.close()
        magicPinWindow = null
        comboRecorderWindow?.close()
        comboRecorderWindow = null
        comboTriggersOverlay?.close()
        comboTriggersOverlay = null
        snapshotFlow.value = HudSnapshot()
        runCatching { java.io.File(filesDir, FLAG_FILE_NAME).delete() }
        isRunningFlow.value = false
        runCatching {
            sendBroadcast(Intent(ACTION_HUD_STATE_CHANGED).putExtra(EXTRA_IS_RUNNING, false).setPackage(packageName))
        }
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
