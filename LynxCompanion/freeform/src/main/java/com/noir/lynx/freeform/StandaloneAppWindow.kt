package com.noir.lynx.freeform

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.os.Binder
import android.os.Process
import android.view.*
import android.widget.Toast
import com.noir.lynx.freeform.R
import com.noir.lynx.freeform.root.FloatingDisplayPolicy
import com.noir.lynx.freeform.root.IFloatingRoot
import kotlinx.coroutines.*

private const val APP_WINDOW_TITLE = "LynxAppWindow"

/** APK-owned app Surface; geometry, rendering and IPC resizing have separate ownership. */
@SuppressLint("ClickableViewAccessibility")
internal class StandaloneAppWindow(
    private val context: Context, private val wm: WindowManager, private val scope: CoroutineScope,
    private val remote: IFloatingRoot, label: String, private val component: String,
    private val widthPct: Int, private val heightPct: Int, private val config: FreeformConfig,
    private val event: (FreeformEvent) -> Unit, private val removed: () -> Unit
) : TextureView.SurfaceTextureListener {
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private val icon = runCatching {
        context.packageManager.getApplicationIcon(component.substringBefore('/'))
    }.getOrNull()
    private val chrome = FloatingWindowChrome(context, label, icon, config.appearance)
    private val root = chrome.root
    private val texture = chrome.texture
    private val params = WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.TOP or Gravity.START; title = APP_WINDOW_TITLE
        windowAnimations = 0
    }
    private val owner = Binder()
    private var displayId = -1
    private var attached = false
    private var closed = false
    private var hasFrame = false
    private var minimized = false
    private var savedBox: FloatingDisplayPolicy.Box? = null
    private var expandedBox: FloatingDisplayPolicy.Box? = null
    private var render: FloatingRenderPolicy.Spec? = null
    private var input: FloatingInputQueue? = null
    private var resizes: FloatingResizeQueue<FloatingRenderPolicy.Spec>? = null
    private var appliedBox: FloatingDisplayPolicy.Box? = null
    private var actionBusy = false
    private var layoutQueued = false
    private var lastTouch: MotionEvent? = null
    private var discardGesture = false
    private var resizingGesture = false
    private var imePolicyConfigured = true
    private var compatibilityProfile: String? = null
    private var lastEvent: FreeformEvent? = null

    private fun emit(stage: FreeformStage, reason: String? = null) {
        val next = FreeformEvent(component.substringBefore('/'), stage, reason,
            displayId.takeIf { it > 0 }, compatibilityProfile)
        if (next != lastEvent) { lastEvent = next; event(next) }
    }

    init {
        texture.surfaceTextureListener = this
        chrome.minimize.setOnClickListener { minimize() }
        chrome.close.setOnClickListener { close() }
        chrome.back.setOnClickListener { action { remote.back(displayId) } }
        chrome.maximize.setOnClickListener { maximize() }
        chrome.rotate.setOnClickListener { rotate() }
        chrome.bubble.setOnClickListener { restore() }
        drag(chrome.dragHandle, false); drag(chrome.resize, true); drag(chrome.bubble, false)
        chrome.busy(true)
        texture.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN && resizes?.busy != true) discardGesture = false
            if (displayId < 0 || minimized || closed || resizes?.busy == true || discardGesture) return@setOnTouchListener true
            val spec = render ?: return@setOnTouchListener true
            if (texture.width <= 0 || texture.height <= 0) return@setOnTouchListener true
            val copy = MotionEvent.obtain(event)
            copy.transform(android.graphics.Matrix().apply {
                setScale(spec.width.toFloat() / texture.width, spec.height.toFloat() / texture.height)
            })
            val accepted = input?.offer(copy) == true
            lastTouch?.recycle()
            lastTouch = if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) null
                else MotionEvent.obtain(copy)
            copy.recycle()
            if (!accepted) fail("input_rejected")
            true
        }
    }

    fun show() {
        emit(FreeformStage.OPENING)
        val safe = floatingSafeArea(wm, root)
        params.width = (safe.width() * widthPct.coerceIn(35, 95) / 100).coerceAtLeast(dp(240))
        params.height = (safe.height() * heightPct.coerceIn(35, 90) / 100).coerceAtLeast(dp(240))
        params.x = safe.left + (safe.width() - params.width) / 2
        params.y = safe.top + (safe.height() - params.height) / 3
        clampToViewport(false)
        try { wm.addView(root, params); attached = true; appliedBox = box() }
        catch (e: Exception) { close(); throw e }
    }

    private fun box() = FloatingDisplayPolicy.Box(params.x, params.y, params.width, params.height)
    private fun use(box: FloatingDisplayPolicy.Box) {
        params.x = box.x; params.y = box.y; params.width = box.width; params.height = box.height
    }

    fun clampToViewport(update: Boolean = true) {
        val safe = floatingSafeArea(wm, root)
        use(FloatingDisplayPolicy.clamp(box(), safe.left, safe.top, safe.width(), safe.height()))
        if (!update || !attached || closed || layoutQueued || box() == appliedBox) return
        layoutQueued = true
        root.postOnAnimation {
            layoutQueued = false
            if (attached && !closed && box() != appliedBox) {
                runCatching { wm.updateViewLayout(root, params); appliedBox = box() }.onFailure { close() }
            }
        }
    }

    private fun drag(view: View, resize: Boolean) {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var startX = 0f; var startY = 0f; var moved = false; var original = box()
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY; moved = false; original = box()
                    if (resize) resizingGesture = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX; val dy = event.rawY - startY
                    if (!moved && kotlin.math.abs(dx) + kotlin.math.abs(dy) <= slop) return@setOnTouchListener true
                    moved = true
                    if (resize) {
                        params.width = (original.width + dx.toInt()).coerceAtLeast(dp(240))
                        params.height = (original.height + dy.toInt()).coerceAtLeast(dp(240))
                    } else { params.x = original.x + dx.toInt(); params.y = original.y + dy.toInt() }
                    clampToViewport()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (resize) { resizingGesture = false; chrome.finishResize() }
                    if (resize && moved) resizeDisplay()
                    if (!moved && event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                }
            }
            true
        }
    }

    private fun viewportSpec() = FloatingRenderPolicy.fit(texture.width.coerceAtLeast(64),
        texture.height.coerceAtLeast(64), context.resources.displayMetrics.densityDpi, config.renderBudget)

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (closed || render != null) return
        val initial = viewportSpec()
        render = initial
        surface.setDefaultBufferSize(initial.width, initial.height)
        val output = Surface(surface)
        scope.launch(NonCancellable) {
            val result = withContext(NonCancellable + Dispatchers.IO) {
                runCatching { remote.open(owner, output, component, Process.myUid() / 100000,
                    initial.width, initial.height, initial.density) }.getOrNull()
            }
            output.release()
            val id = result?.takeIf { it.getBoolean("ok") }?.getInt("displayId", -1) ?: -1
            if (closed) {
                if (id >= 0) withContext(NonCancellable + Dispatchers.IO) { runCatching { remote.close(id) } }
                return@launch
            }
            if (id < 0) { fail(result?.getString("reason")); return@launch }
            displayId = id
            compatibilityProfile = result?.getString("compatibilityProfile")
            imePolicyConfigured = result?.getBoolean("imeConfigured", true) ?: true
            input = FloatingInputQueue(scope, { remote.touch(id, it) }, { fail("input_rejected") })
            resizes = FloatingResizeQueue(scope, initial, apply = { target ->
                texture.surfaceTexture?.setDefaultBufferSize(target.width, target.height)
                withContext(Dispatchers.IO) {
                    remote.resize(id, target.width, target.height, target.density).getBoolean("ok")
                }
            }, settled = { target, ok ->
                if (!closed) {
                    if (ok) {
                        render = target
                        texture.surfaceTexture?.setDefaultBufferSize(target.width, target.height)
                        updateStatus()
                    } else fail("resize_rejected")
                }
            }, busyChanged = { busy ->
                if (busy) cancelGesture()
                updateStatus()
            })
            resizeDisplay(); updateStatus()
            delay(5000)
            if (!closed && !hasFrame) fail("frame_timeout")
        }
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        if (!hasFrame) { hasFrame = true; updateStatus() }
    }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        if (width > 0 && height > 0 && !resizingGesture) resizeDisplay()
    }
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { close(); return true }

    private fun updateStatus() {
        val resizing = resizes?.busy == true
        chrome.state(when {
            resizes?.busy == true -> R.string.lynx_freeform_state_resizing
            hasFrame && displayId >= 0 -> if (imePolicyConfigured) R.string.lynx_freeform_state_ready else R.string.lynx_freeform_state_keyboard_limited
            else -> R.string.lynx_freeform_state_starting
        }, !hasFrame || resizing || actionBusy)
        emit(when {
            minimized -> FreeformStage.MINIMIZED
            resizing -> FreeformStage.RESIZING
            hasFrame && displayId > 0 -> FreeformStage.READY
            else -> FreeformStage.OPENING
        })
        chrome.busy(actionBusy || resizes?.busy == true || displayId < 0 || !hasFrame)
    }

    private fun cancelGesture() {
        discardGesture = true
        val event = lastTouch ?: return
        lastTouch = null
        event.action = MotionEvent.ACTION_CANCEL
        val accepted = input?.offer(event) == true
        event.recycle()
        if (!accepted) fail("input_rejected")
    }

    private fun action(success: () -> Unit = {}, operation: () -> Boolean) {
        if (displayId < 0 || closed || actionBusy || resizes?.busy == true) return
        actionBusy = true; updateStatus()
        scope.launch {
            try {
                val ok = withContext(Dispatchers.IO) { runCatching(operation).getOrDefault(false) }
                if (!closed) { if (ok) success() else fail(null) }
            } finally { actionBusy = false; if (!closed) updateStatus() }
        }
    }

    private fun minimize() {
        cancelGesture()
        action(success = {
            expandedBox = box(); minimized = true
            chrome.minimized(true)
            // Preserve the TextureView allocation while the overlay becomes a small bubble.
            chrome.column.layoutParams = android.widget.FrameLayout.LayoutParams(params.width, params.height)
            chrome.column.alpha = 0f; chrome.bubble.visibility = View.VISIBLE
            params.width = dp(56); params.height = dp(56); clampToViewport()
        }) { remote.visible(displayId, false) }
    }

    private fun restore() = action(success = {
        minimized = false; chrome.column.alpha = 1f; chrome.bubble.visibility = View.GONE
        chrome.minimized(false)
        chrome.column.layoutParams = android.widget.FrameLayout.LayoutParams(-1, -1)
        expandedBox?.let(::use); clampToViewport()
        root.post { resizeDisplay() }
    }) { remote.visible(displayId, true) }

    private fun maximize() {
        if (displayId < 0 || resizes?.busy == true) return
        if (savedBox == null) {
            savedBox = box()
            val safe = floatingSafeArea(wm, root)
            use(FloatingDisplayPolicy.Box(safe.left, safe.top, safe.width(), safe.height()))
        } else { savedBox?.let(::use); savedBox = null }
        clampToViewport()
    }

    private fun rotate() {
        if (displayId < 0 || resizes?.busy == true) return
        savedBox = null
        val w = params.height - chrome.controlsHeight; val h = params.width + chrome.controlsHeight
        params.x += (params.width - w) / 2; params.y += (params.height - h) / 2
        params.width = w.coerceAtLeast(dp(240)); params.height = h.coerceAtLeast(dp(240))
        clampToViewport()
    }

    private fun resizeDisplay() {
        if (displayId >= 0 && !minimized && !closed && texture.width > 0 && texture.height > 0) {
            resizes?.request(viewportSpec())
        }
    }

    private fun fail(code: String?) {
        if (closed) return
        emit(FreeformStage.FAILED, code)
        Toast.makeText(context, FloatingFailure.message(code), Toast.LENGTH_LONG).show()
        close()
    }

    fun close() {
        if (closed) return
        closed = true; chrome.dismissMenu(); resizes?.close(); input?.close()
        emit(FreeformStage.CLOSED)
        lastTouch?.recycle(); lastTouch = null
        if (attached) { attached = false; runCatching { wm.removeView(root) } }
        val id = displayId; displayId = -1
        if (id >= 0) scope.launch(NonCancellable + Dispatchers.IO) { runCatching { remote.close(id) } }
        removed()
    }
}
