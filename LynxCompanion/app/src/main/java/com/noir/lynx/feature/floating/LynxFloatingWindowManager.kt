package com.noir.lynx.feature.floating

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noir.lynx.feature.floating.ui.FloatingWindowFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
object LynxFloatingWindowManager {
    private val mutableState = MutableStateFlow(FloatingWindowState())
    val state = mutableState.asStateFlow()

    private var appContext: Context? = null
    private var windowManager: WindowManager? = null
    private var coroutineScope: CoroutineScope? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var savedStateRegistryOwner: SavedStateRegistryOwner? = null

    private var floatingView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    fun init(
        context: Context,
        wm: WindowManager,
        scope: CoroutineScope,
        owner: LifecycleOwner,
        savedStateOwner: SavedStateRegistryOwner
    ) {
        appContext = context.applicationContext
        windowManager = wm
        coroutineScope = scope
        lifecycleOwner = owner
        savedStateRegistryOwner = savedStateOwner
    }

    fun destroy() {
        val wm = windowManager
        floatingView?.let { view ->
            runCatching { wm?.removeView(view) }
            view.disposeComposition()
        }
        floatingView = null
        layoutParams = null
        mutableState.value = FloatingWindowState()
        appContext = null
        windowManager = null
        coroutineScope = null
        lifecycleOwner = null
        savedStateRegistryOwner = null
    }

    fun openTool(tool: FloatingToolType, url: String? = null) {
        val next = mutableState.value.copy(
            isOpen = true,
            isMinimized = false,
            activeTool = tool,
            customUrl = url ?: mutableState.value.customUrl
        )
        mutableState.value = next
        ensureAttached()
    }

    fun close() {
        mutableState.value = mutableState.value.copy(isOpen = false, isMinimized = false)
        floatingView?.visibility = View.GONE
    }

    fun minimize() {
        mutableState.value = mutableState.value.copy(isMinimized = true)
        updateWindowDimensionsForMinimized()
    }

    fun restore() {
        mutableState.value = mutableState.value.copy(isMinimized = false)
        updateWindowDimensionsForExpanded()
    }

    fun selectTool(tool: FloatingToolType) {
        mutableState.value = mutableState.value.copy(activeTool = tool)
    }

    fun cycleOpacity() {
        val nextAlpha = when {
            mutableState.value.alpha >= 0.9f -> 0.78f
            mutableState.value.alpha >= 0.75f -> 0.58f
            mutableState.value.alpha >= 0.55f -> 0.40f
            else -> 0.95f
        }
        mutableState.value = mutableState.value.copy(alpha = nextAlpha)
    }

    fun cyclePresetSize() {
        val currentW = mutableState.value.widthDp
        val (nextW, nextH) = when {
            currentW < 300f -> 340f to 440f // to Standard
            currentW < 380f -> 420f to 520f // to Large
            else -> 280f to 360f            // to Compact
        }
        mutableState.value = mutableState.value.copy(widthDp = nextW, heightDp = nextH)
        updateWindowDimensionsForExpanded()
    }

    fun drag(dx: Float, dy: Float) {
        val p = layoutParams ?: return
        val v = floatingView ?: return
        val wm = windowManager ?: return

        p.x = (p.x + dx.toInt()).coerceAtLeast(0)
        p.y = (p.y + dy.toInt()).coerceAtLeast(0)
        runCatching { wm.updateViewLayout(v, p) }
    }

    fun resize(dwPx: Float, dhPx: Float) {
        val ctx = appContext ?: return
        val density = ctx.resources.displayMetrics.density
        val currentW = mutableState.value.widthDp
        val currentH = mutableState.value.heightDp

        val newW = (currentW + (dwPx / density)).coerceIn(250f, 600f)
        val newH = (currentH + (dhPx / density)).coerceIn(220f, 800f)

        mutableState.value = mutableState.value.copy(widthDp = newW, heightDp = newH)
        updateWindowDimensionsForExpanded()
    }

    private fun ensureAttached() {
        val ctx = appContext ?: return
        val wm = windowManager ?: return
        val owner = lifecycleOwner ?: return
        val savedOwner = savedStateRegistryOwner ?: return

        if (floatingView == null) {
            val density = ctx.resources.displayMetrics.density
            val widthPx = (mutableState.value.widthDp * density).toInt()
            val heightPx = (mutableState.value.heightDp * density).toInt()

            val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

            layoutParams = WindowManager.LayoutParams(
                widthPx,
                heightPx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = mutableState.value.xPx
                y = mutableState.value.yPx
                title = "LynxStandaloneFloatingWindow"
            }

            val composeView = ComposeView(ctx).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(savedOwner)
                setContent {
                    val s by state.collectAsState()
                    FloatingWindowFrame(
                        state = s,
                        onDrag = ::drag,
                        onResize = ::resize,
                        onMinimize = ::minimize,
                        onRestore = ::restore,
                        onClose = ::close,
                        onSelectTool = ::selectTool,
                        onCycleOpacity = ::cycleOpacity,
                        onCyclePresetSize = ::cyclePresetSize
                    )
                }
            }

            floatingView = composeView
            runCatching { wm.addView(composeView, layoutParams) }
        } else {
            floatingView?.visibility = View.VISIBLE
            if (mutableState.value.isMinimized) {
                updateWindowDimensionsForMinimized()
            } else {
                updateWindowDimensionsForExpanded()
            }
        }
    }

    private fun updateWindowDimensionsForExpanded() {
        val ctx = appContext ?: return
        val wm = windowManager ?: return
        val v = floatingView ?: return
        val p = layoutParams ?: return

        val density = ctx.resources.displayMetrics.density
        p.width = (mutableState.value.widthDp * density).toInt()
        p.height = (mutableState.value.heightDp * density).toInt()
        runCatching { wm.updateViewLayout(v, p) }
    }

    private fun updateWindowDimensionsForMinimized() {
        val ctx = appContext ?: return
        val wm = windowManager ?: return
        val v = floatingView ?: return
        val p = layoutParams ?: return

        val density = ctx.resources.displayMetrics.density
        p.width = (56 * density).toInt()
        p.height = (56 * density).toInt()
        runCatching { wm.updateViewLayout(v, p) }
    }
}
