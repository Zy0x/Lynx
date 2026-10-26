package com.noir.lynx.feature.hud.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noir.lynx.feature.hud.ui.HudSmartTriggerOverlay
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Owns the badge window only. It cannot start a game or execute a touch sequence. */
internal class HudAutoSkipWindow(
    private val context: Context, scope: CoroutineScope,
    lifecycle: LifecycleOwner, savedState: SavedStateRegistryOwner,
    skipping: StateFlow<Boolean>, paused: StateFlow<Boolean>, visible: Flow<Boolean>,
    private val onToggle: () -> Unit,
    mode: StateFlow<com.noir.lynx.feature.hud.autoskip.GenshinSkipRunner.SkipMode>? = null
) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val preferences = context.getSharedPreferences("lynx_hud_prefs", Context.MODE_PRIVATE)
    private var bounds = readHudBadgeBounds(windows, null)
    private var landscape = bounds.frame.right - bounds.frame.left > bounds.frame.bottom - bounds.frame.top
    private val alignment = MutableStateFlow(HudBadgePoint(0, 0, 0f, 0f))
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT
    ).apply {
        title = "LynxAutoSkipBadge"; gravity = Gravity.TOP or Gravity.LEFT
        if (Build.VERSION.SDK_INT >= 30) {
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    private var downX = 0f; private var downY = 0f
    private var startX = 0; private var startY = 0; private var dragging = false
    private val host: FrameLayout = object : FrameLayout(context) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (!dragging && hypot(dx, dy) >= ViewConfiguration.get(context).scaledTouchSlop) dragging = true
                    if (dragging) move(bounds.place((startX + dx).roundToInt(), (startY + dy).roundToInt(), badgeWidth(), badgeHeight()))
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) save() else performClick()
                    dragging = false
                }
                MotionEvent.ACTION_CANCEL -> { if (dragging) save(); dragging = false }
            }
            return true
        }
    }
    private val compose = ComposeView(context)
    private val visibilityJob: Job

    init {
        host.setViewTreeLifecycleOwner(lifecycle); host.setViewTreeSavedStateRegistryOwner(savedState)
        host.setOnClickListener { onToggle() }
        compose.setContent {
            val modeState = mode?.collectAsState()?.value
            val active by skipping.collectAsState(); val idle by paused.collectAsState(); val position by alignment.collectAsState()
            if (modeState != null) {
                HudSmartTriggerOverlay(modeState, contentAlignment = BiasAlignment(position.edgeX, position.edgeY))
            } else {
                HudSmartTriggerOverlay(active, idle, contentAlignment = BiasAlignment(position.edgeX, position.edgeY))
            }
        }
        host.addView(compose)
        host.visibility = View.GONE
        restore()
        host.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (r - l != oldR - oldL || b - t != oldB - oldT) restore()
        }
        host.setOnApplyWindowInsetsListener { _, insets -> updateBounds(); insets }
        runCatching { windows.addView(host, params) }
        visibilityJob = scope.launch { visible.distinctUntilChanged().collect {
            if (it) updateBounds()
            host.visibility = if (it) View.VISIBLE else View.GONE
        } }
    }

    private fun badgeWidth(): Int = host.width.takeIf { it > 0 } ?: (48f * context.resources.displayMetrics.density).roundToInt()
    private fun badgeHeight(): Int = host.height.takeIf { it > 0 } ?: (48f * context.resources.displayMetrics.density).roundToInt()
    private fun xKey() = if (landscape) "autoskip_x_landscape_fraction" else "autoskip_x_portrait_fraction"
    private fun yKey() = if (landscape) "autoskip_y_landscape_fraction" else "autoskip_y_portrait_fraction"
    private fun restore() = move(bounds.fromFractions(preferences.getFloat(xKey(), if (landscape) .94f else .88f),
        preferences.getFloat(yKey(), .65f), badgeWidth(), badgeHeight()))
    private fun save() {
        val (x, y) = bounds.fractions(params.x, params.y, badgeWidth(), badgeHeight())
        preferences.edit().putFloat(xKey(), x).putFloat(yKey(), y).apply()
    }
    private fun move(point: HudBadgePoint) {
        val changed = params.x != point.x || params.y != point.y
        params.x = point.x; params.y = point.y; alignment.value = point
        if (changed && host.isAttachedToWindow) runCatching { windows.updateViewLayout(host, params) }
    }
    fun updateBounds() {
        val next = readHudBadgeBounds(windows, host.rootWindowInsets)
        val nextLandscape = next.frame.right - next.frame.left > next.frame.bottom - next.frame.top
        val rotated = nextLandscape != landscape
        bounds = next; landscape = nextLandscape
        if (rotated) restore() else move(bounds.place(params.x, params.y, badgeWidth(), badgeHeight()))
    }
    fun close() {
        visibilityJob.cancel(); runCatching { windows.removeView(host) }; compose.disposeComposition()
    }
}
