package com.noir.lynx.service

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import java.lang.reflect.Proxy
import android.hardware.display.DisplayManager
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
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
import com.noir.lynx.feature.hud.autoskip.GenshinSkipEngine
import com.noir.lynx.feature.hud.data.*
import com.noir.lynx.feature.hud.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs
import kotlin.math.hypot
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
    private var registered = false

    private enum class GestureMode { UNDECIDED, SWIPE_OPEN, DRAG_MOVE, PASS_THROUGH }

    companion object {
        const val ACTION_HUD_STATE_CHANGED = "com.noir.lynx.HUD_STATE_CHANGED"
        const val EXTRA_IS_RUNNING = "extra_is_running"
        const val FLAG_FILE_NAME = "hud_active.flag"
        val isRunningFlow = MutableStateFlow(false)
        val isRunning: Boolean
            get() {
                val ctx = runCatching { com.noir.lynx.data.LynxRepository.appContext ?: com.noir.lynx.LynxApp.instance }.getOrNull()
                val fileActive = runCatching {
                    ctx?.let { java.io.File(it.filesDir, FLAG_FILE_NAME).exists() } ?: false
                }.getOrDefault(false)
                return fileActive || isRunningFlow.value
            }
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
                if (activeModeFlow.value == 0) {
                    p.gravity = Gravity.TOP or if (dockRight.value) Gravity.RIGHT else Gravity.LEFT
                    p.x = if (dockRight.value) safeRight else safeLeft
                    p.width = if (isOpen) (viewport.value.panelWidth * dm.density).toInt() else WindowManager.LayoutParams.WRAP_CONTENT
                    p.height = WindowManager.LayoutParams.WRAP_CONTENT
                } else {
                    p.gravity = Gravity.TOP or Gravity.LEFT
                    p.width = WindowManager.LayoutParams.WRAP_CONTENT
                    p.height = WindowManager.LayoutParams.WRAP_CONTENT
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
                            val current = sample.copy(metrics = sample.metrics + (HudMetric.DISPLAY_HZ.key to displayReading))
                            rendered.value = current
                            snapshotFlow.value = current
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
        runCatching { java.io.File(filesDir, FLAG_FILE_NAME).createNewFile() }
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
        rates.value = display?.supportedModes?.filter { mode != null && it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            ?.map { it.refreshRate }?.distinctBy { (it * 10).roundToInt() }?.sorted() ?: emptyList()
    }

    @Suppress("DEPRECATION")
    private fun updateBounds() {
        val density = resources.displayMetrics.density
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windows.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            safeLeft = insets.left; safeRight = insets.right; safeTop = insets.top
            safeWidth = metrics.bounds.width() - insets.left - insets.right
            safeHeight = metrics.bounds.height() - insets.top - insets.bottom
        } else {
            val metrics = android.util.DisplayMetrics()
            windows.defaultDisplay.getRealMetrics(metrics)
            val insets = view?.rootWindowInsets
            safeLeft = insets?.stableInsetLeft ?: 0
            safeRight = insets?.stableInsetRight ?: 0
            safeTop = insets?.stableInsetTop ?: 0
            safeWidth = metrics.widthPixels - safeLeft - safeRight
            safeHeight = metrics.heightPixels - safeTop - (insets?.stableInsetBottom ?: 0)
        }
        viewport.value = HudViewport(safeWidth / density, safeHeight / density)
        com.noir.lynx.feature.floating.StandaloneApps.viewportChanged()
        clamp()
    }

    private fun updateExclusionRects(v: View) {
        if (Build.VERSION.SDK_INT >= 29) {
            v.systemGestureExclusionRects = if (activeModeFlow.value == 0 && !open.value && v.width > 0 && v.height > 0) {
                listOf(Rect(0, 0, v.width, v.height))
            } else {
                emptyList()
            }
        }
    }

    private fun clamp() {
        val v = view ?: return
        val p = params ?: return
        val nextGravity: Int
        val nextX: Int
        if (activeModeFlow.value == 0) {
            nextGravity = Gravity.TOP or if (dockRight.value) Gravity.RIGHT else Gravity.LEFT
            nextX = if (dockRight.value) safeRight else safeLeft
        } else {
            nextGravity = Gravity.TOP or Gravity.LEFT
            val maxX = (safeLeft + safeWidth - v.width).coerceAtLeast(safeLeft)
            nextX = p.x.coerceIn(safeLeft, maxX)
        }
        val nextY = p.y.coerceIn(safeTop, (safeTop + safeHeight - v.height).coerceAtLeast(safeTop))
        if (p.gravity != nextGravity || p.x != nextX || p.y != nextY) {
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
        val key = if (viewport.value.landscape) "hud_y_landscape_fraction" else "hud_y_portrait_fraction"
        getSharedPreferences("lynx_hud_prefs", MODE_PRIVATE).edit()
            .putFloat(key, (p.y - safeTop).toFloat() / safeHeight.coerceAtLeast(1))
            .putInt("hud_x", p.x)
            .putBoolean("hud_dock_right", dockRight.value)
            .apply()
    }

    private inner class HudHostView(context: Context) : FrameLayout(context) {
        private var downRawX = 0f
        private var downRawY = 0f
        private var downLocalX = 0f
        private var downLocalY = 0f
        private var startWindowY = 0
        private var gestureMode = GestureMode.UNDECIDED
        private var childCancelled = false

        private val holdToDragRunnable = Runnable {
            if (gestureMode == GestureMode.UNDECIDED && activeModeFlow.value == 0 && !open.value) {
                gestureMode = GestureMode.DRAG_MOVE
                cancelChildTouch()
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }

        private fun cancelChildTouch() {
            if (!childCancelled) {
                childCancelled = true
                val now = SystemClock.uptimeMillis()
                val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            setupTouchableInsets()
        }

        private fun setupTouchableInsets() {
            try {
                val listenerClass = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
                val addMethod = ViewTreeObserver::class.java.getMethod("addOnComputeInternalInsetsListener", listenerClass)
                val insetsInfoClass = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")
                val setTouchableInsets = insetsInfoClass.getMethod("setTouchableInsets", Int::class.javaPrimitiveType)
                val touchableRegionField = insetsInfoClass.getField("touchableRegion")

                val proxy = Proxy.newProxyInstance(
                    listenerClass.classLoader,
                    arrayOf(listenerClass)
                ) { _, method, args ->
                    if (method.name == "onComputeInternalInsets" && args != null && args.isNotEmpty()) {
                        val info = args[0]
                        if (open.value) {
                            setTouchableInsets.invoke(info, 0)
                        } else {
                            setTouchableInsets.invoke(info, 3)
                            val region = touchableRegionField.get(info) as? Region ?: Region()
                            val density = resources.displayMetrics.density

                            val handleHeight = (72f * density).toInt()
                            val handleWidth = width.coerceAtLeast(1)

                            region.set(0, 0, handleWidth, handleHeight)

                            val hasAutoSkip = preferences.read().autoSkipEnabled &&
                                GenshinSkipEngine.isGameSupported(rendered.value.packageName)

                            if (hasAutoSkip) {
                                val triggerSize = (40f * density).toInt()
                                val triggerTop = handleHeight
                                val triggerBottom = (triggerTop + triggerSize).coerceAtMost(height.coerceAtLeast(triggerTop + 1))
                                val isRight = dockRight.value
                                val triggerRect = if (isRight) {
                                    Rect((width - triggerSize).coerceAtLeast(0), triggerTop, width, triggerBottom)
                                } else {
                                    Rect(0, triggerTop, triggerSize.coerceAtMost(width), triggerBottom)
                                }
                                region.op(triggerRect, Region.Op.UNION)
                            }
                        }
                    }
                    null
                }
                addMethod.invoke(viewTreeObserver, proxy)
            } catch (_: Throwable) {}
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            updateExclusionRects(this)
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                open.value = false
                return false
            }
            if (activeModeFlow.value != 0) {
                return super.dispatchTouchEvent(ev)
            }

            val density = resources.displayMetrics.density
            val dragSlopPx = 8f * density
            val swipeOpenThresholdPx = 16f * density
            val isExpanded = open.value

            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    mainHandler.removeCallbacks(holdToDragRunnable)
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    downLocalX = ev.x
                    downLocalY = ev.y
                    startWindowY = params?.y ?: safeTop
                    childCancelled = false

                    if (isExpanded) {
                        val earWidthPx = 22f * density
                        val onInnerEar = if (dockRight.value) downLocalX <= earWidthPx else downLocalX >= (width - earWidthPx)
                        val inHeaderArea = downLocalY <= 48f * density && downLocalX < (width - 76f * density)
                        gestureMode = if (onInnerEar || inHeaderArea) GestureMode.UNDECIDED else GestureMode.PASS_THROUGH
                    } else {
                        val handleHeightPx = 72f * density
                        val isInsideHandle = downLocalY <= handleHeightPx
                        if (isInsideHandle) {
                            gestureMode = GestureMode.UNDECIDED
                            mainHandler.postDelayed(holdToDragRunnable, 220L)
                        } else {
                            val hasAutoSkip = preferences.read().autoSkipEnabled &&
                                GenshinSkipEngine.isGameSupported(rendered.value.packageName)

                            val triggerSizePx = 40f * density
                            val isRight = dockRight.value
                            val isInsideTrigger = hasAutoSkip && downLocalY <= (handleHeightPx + triggerSizePx) &&
                                (if (isRight) downLocalX >= (width - triggerSizePx) else downLocalX <= triggerSizePx)

                            if (isInsideTrigger) {
                                gestureMode = GestureMode.PASS_THROUGH
                            } else {
                                return false
                            }
                        }
                    }
                    val handled = super.dispatchTouchEvent(ev)
                    return if (gestureMode == GestureMode.PASS_THROUGH) handled else true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (gestureMode == GestureMode.PASS_THROUGH || gestureMode == GestureMode.SWIPE_OPEN) {
                        return if (childCancelled) true else super.dispatchTouchEvent(ev)
                    }

                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    val absDx = abs(dx)
                    val absDy = abs(dy)

                    if (gestureMode == GestureMode.UNDECIDED) {
                        if (isExpanded) {
                            val outwardDx = if (dockRight.value) dx else -dx
                            if (outwardDx >= swipeOpenThresholdPx && absDx > absDy * 1.2f) {
                                gestureMode = GestureMode.SWIPE_OPEN
                                cancelChildTouch()
                                open.value = false
                                return true
                            }
                            if (hypot(dx, dy) >= dragSlopPx) {
                                gestureMode = GestureMode.DRAG_MOVE
                                cancelChildTouch()
                            }
                        } else {
                            val inwardDx = if (dockRight.value) -dx else dx
                            val selectedCount = preferences.read().selected.size
                            val isPinnedMultiPage = pinMiniFpsFlow.value && selectedCount > 2
                            val handleStripPx = 28f * density
                            val touchedHandleStrip = if (dockRight.value) {
                                downLocalX >= (width - handleStripPx)
                            } else {
                                downLocalX <= handleStripPx
                            }

                            if (!touchedHandleStrip && isPinnedMultiPage && absDx >= dragSlopPx && absDx > absDy * 1.2f) {
                                mainHandler.removeCallbacks(holdToDragRunnable)
                                gestureMode = GestureMode.PASS_THROUGH
                                return super.dispatchTouchEvent(ev)
                            }

                            if (inwardDx >= swipeOpenThresholdPx && absDx > absDy * 1.2f) {
                                mainHandler.removeCallbacks(holdToDragRunnable)
                                gestureMode = GestureMode.SWIPE_OPEN
                                cancelChildTouch()
                                open.value = true
                                return true
                            }

                            if (absDy >= dragSlopPx && absDy >= absDx) {
                                mainHandler.removeCallbacks(holdToDragRunnable)
                                gestureMode = GestureMode.DRAG_MOVE
                                cancelChildTouch()
                            } else if (inwardDx <= -dragSlopPx * 1.5f) {
                                mainHandler.removeCallbacks(holdToDragRunnable)
                                gestureMode = GestureMode.DRAG_MOVE
                                cancelChildTouch()
                            }
                        }
                    }

                    if (gestureMode == GestureMode.DRAG_MOVE) {
                        val p = params ?: return true
                        val maxY = (safeTop + safeHeight - height).coerceAtLeast(safeTop)
                        val isRight = if (dockRight.value) {
                            ev.rawX >= safeLeft + safeWidth * 0.40f
                        } else {
                            ev.rawX > safeLeft + safeWidth * 0.60f
                        }
                        if (dockRight.value != isRight) {
                            dockRight.value = isRight
                        }
                        val nextGravity = Gravity.TOP or if (isRight) Gravity.RIGHT else Gravity.LEFT
                        val nextX = if (isRight) safeRight else safeLeft
                        val nextY = (startWindowY + dy.toInt()).coerceIn(safeTop, maxY)
                        if (p.gravity != nextGravity || p.x != nextX || p.y != nextY) {
                            p.gravity = nextGravity
                            p.x = nextX
                            p.y = nextY
                            runCatching { windows.updateViewLayout(this, p) }
                        }
                        return true
                    }

                    return if (childCancelled) true else super.dispatchTouchEvent(ev)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(holdToDragRunnable)
                    val finishedMode = gestureMode
                    gestureMode = GestureMode.UNDECIDED
                    if (finishedMode == GestureMode.DRAG_MOVE) {
                        savePositionPrefs()
                        clamp()
                        return true
                    }
                    if (!isExpanded && finishedMode == GestureMode.UNDECIDED) {
                        cancelChildTouch()
                        return true
                    }
                    return if (childCancelled) true else super.dispatchTouchEvent(ev)
                }
            }
            return if (childCancelled) true else super.dispatchTouchEvent(ev)
        }
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
            if (activeModeFlow.value == 0) {
                gravity = Gravity.TOP or if (dockRight.value) Gravity.RIGHT else Gravity.LEFT
                x = if (dockRight.value) safeRight else safeLeft
            } else {
                gravity = Gravity.TOP or Gravity.LEFT
                x = pref.getInt("hud_x", safeLeft)
            }
            y = safeTop + (safeHeight * initial).toInt()
        }
        val composeView = ComposeView(this)
        val host = HudHostView(this).apply {
            setViewTreeLifecycleOwner(this@LynxFloatingHudService)
            setViewTreeSavedStateRegistryOwner(this@LynxFloatingHudService)
            addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> post { clamp() } }
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
            val hudPrefs by preferences.changes.collectAsState(initial = preferences.read())

            LaunchedEffect(sample.packageName, expanded, size, hudPrefs) {
                val dm = resources.displayMetrics
                controller.syncAutoSkip(
                    pkg = sample.packageName,
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
                    onDrag = {},
                    onProfile = controller::profile,
                    onHz = { controller.refresh(it, supported, targetDisplay()) { hz.value.value?.toFloat() } },
                    onCapture = { controller.screenshot { hidden -> host.visibility = if (hidden) View.INVISIBLE else View.VISIBLE } },
                    onApp = controller::launch,
                    onStop = { stopSelf() },
                    onBoostRam = controller::boostRam,
                    dockRight = isDockRight,
                    onDrag2D = null,
                    onDragEnd = {},
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
                    onToggleAutoSkip = controller::toggleAutoSkip
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
