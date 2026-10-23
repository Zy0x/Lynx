package com.noir.lynx.feature.floating

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.os.Binder
import android.os.Process
import android.view.*
import android.widget.*
import com.noir.lynx.R
import com.noir.lynx.feature.floating.root.FloatingDisplayPolicy
import com.noir.lynx.feature.floating.root.IFloatingRoot
import kotlinx.coroutines.*

private const val APP_WINDOW_TITLE = "LynxAppWindow"

/** A real app rendered into Lynx's bounded TextureView, independently of ROM freeform chrome. */
@SuppressLint("ClickableViewAccessibility")
internal class StandaloneAppWindow(
    private val context: Context, private val wm: WindowManager, private val scope: CoroutineScope,
    private val remote: IFloatingRoot, private val label: String, private val component: String,
    private val widthPct: Int, private val heightPct: Int, private val removed: () -> Unit
) : TextureView.SurfaceTextureListener {
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private val root = FrameLayout(context)
    private val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(10, 15, 22)) }
    private val title = TextView(context).apply {
        setTextColor(Color.CYAN); textSize = 12f; maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private val texture = TextureView(context).apply { surfaceTextureListener = this@StandaloneAppWindow }
    private val bubble = TextView(context).apply {
        text = label.take(2); textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.CYAN)
        contentDescription = label
        setBackgroundColor(Color.rgb(10, 15, 22)); visibility = View.GONE
    }
    private val params = WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            // The remote app owns the editor; keep its host Surface behind the physical-display IME.
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.TOP or Gravity.START; title = APP_WINDOW_TITLE
    }
    private val owner = Binder()
    private var displayId = -1
    private var attached = false
    private var closed = false
    private var hasFrame = false
    private var minimized = false
    private var savedBox: FloatingDisplayPolicy.Box? = null
    private var expandedBox: FloatingDisplayPolicy.Box? = null
    private var renderWidth = 0
    private var renderHeight = 0
    private var input: FloatingInputQueue? = null
    private var resizeJob: Job? = null
    private var appliedBox: FloatingDisplayPolicy.Box? = null
    private var actionBusy = false

    init {
        root.clipChildren = true
        root.addView(column, FrameLayout.LayoutParams(-1, -1))
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))
        title.setPadding(dp(8), 0, 0, 0)
        title.text = context.getString(R.string.floating_waiting, label)
        fun button(symbol: String, description: Int, action: () -> Unit) {
            header.addView(TextView(context).apply {
                text = symbol; contentDescription = context.getString(description); textSize = 18f
                gravity = Gravity.CENTER; setTextColor(Color.WHITE); setOnClickListener { action() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        button("−", R.string.floating_minimize) { minimize() }
        button("×", R.string.floating_close) { close() }
        column.addView(header, LinearLayout.LayoutParams(-1, dp(48)))
        column.addView(texture, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = TextView(context).apply {
            text = "‹"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(Color.CYAN)
            contentDescription = context.getString(R.string.floating_back)
            setOnClickListener { action({ remote.back(displayId) }) }
        }
        footer.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        val maximize = TextView(context).apply {
            text = "□"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.CYAN)
            contentDescription = context.getString(R.string.floating_maximize)
            setOnClickListener { maximize() }
        }
        footer.addView(maximize, LinearLayout.LayoutParams(dp(48), dp(48)))
        val grip = TextView(context).apply {
            text = "◢"; textSize = 22f; gravity = Gravity.END or Gravity.CENTER_VERTICAL; setTextColor(Color.CYAN)
            contentDescription = context.getString(R.string.floating_resize)
        }
        footer.addView(grip, LinearLayout.LayoutParams(0, dp(48), 1f))
        column.addView(footer, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(bubble, FrameLayout.LayoutParams(-1, -1))
        bubble.setOnClickListener { restore() }
        drag(title, false); drag(grip, true); drag(bubble, false)
        texture.setOnTouchListener { _, event ->
            if (displayId < 0 || minimized || closed || texture.width == 0 || texture.height == 0) return@setOnTouchListener true
            val copy = MotionEvent.obtain(event)
            val matrix = android.graphics.Matrix().apply {
                setScale(renderWidth.toFloat() / texture.width, renderHeight.toFloat() / texture.height)
            }
            copy.transform(matrix)
            val accepted = input?.offer(copy) == true
            copy.recycle()
            if (!accepted) fail()
            true
        }
    }

    fun show() {
        val safe = floatingSafeArea(wm, root)
        params.width = (safe.width() * widthPct.coerceIn(35, 95) / 100).coerceAtLeast(dp(240))
        params.height = (safe.height() * heightPct.coerceIn(35, 90) / 100).coerceAtLeast(dp(240))
        params.x = safe.left + (safe.width() - params.width) / 2
        params.y = safe.top + (safe.height() - params.height) / 3
        clampToViewport(false)
        try {
            wm.addView(root, params); attached = true
            appliedBox = FloatingDisplayPolicy.Box(params.x, params.y, params.width, params.height)
        } catch (e: Exception) { close(); throw e }
    }

    fun clampToViewport(update: Boolean = true) {
        val safe = floatingSafeArea(wm, root)
        val b = FloatingDisplayPolicy.clamp(FloatingDisplayPolicy.Box(params.x, params.y, params.width, params.height),
            safe.left, safe.top, safe.width(), safe.height())
        params.x = b.x; params.y = b.y; params.width = b.width; params.height = b.height
        if (update && attached && !closed && b != appliedBox) {
            runCatching { wm.updateViewLayout(root, params); appliedBox = b }.onFailure { close() }
        }
    }

    private fun drag(view: View, resize: Boolean) {
        var x = 0f; var y = 0f; var moved = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x = event.rawX; y = event.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - x; val dy = event.rawY - y
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 2) moved = true
                    if (resize) {
                        params.width = (params.width + dx.toInt()).coerceAtLeast(dp(240))
                        params.height = (params.height + dy.toInt()).coerceAtLeast(dp(240))
                    } else { params.x += dx.toInt(); params.y += dy.toInt() }
                    x = event.rawX; y = event.rawY; clampToViewport()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (resize && moved) resizeDisplay()
                    if (!moved && event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                }
            }
            true
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (closed || displayId >= 0) return
        renderWidth = context.resources.displayMetrics.widthPixels.coerceIn(240, 2160)
        renderHeight = (renderWidth.toFloat() * height / width.coerceAtLeast(1)).toInt().coerceIn(240, 3840)
        surface.setDefaultBufferSize(renderWidth, renderHeight)
        val output = Surface(surface)
        scope.launch(NonCancellable) {
            // A synchronous Binder call cannot be cancelled. Always receive and clean up its result.
            val result = withContext(NonCancellable + Dispatchers.IO) {
                runCatching { remote.open(owner, output, component, Process.myUid() / 100000,
                    renderWidth, renderHeight, context.resources.displayMetrics.densityDpi) }.getOrNull()
            }
            output.release()
            val id = result?.takeIf { it.getBoolean("ok") }?.getInt("displayId", -1) ?: -1
            if (closed || !isActive) {
                if (id >= 0) withContext(NonCancellable + Dispatchers.IO) { runCatching { remote.close(id) } }
                return@launch
            }
            if (id < 0) { fail(); return@launch }
            displayId = id
            input = FloatingInputQueue(scope, { remote.touch(id, it) }, ::fail)
            updateTitle()
            delay(5000)
            if (!closed && !hasFrame) fail()
        }
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        if (!hasFrame) { hasFrame = true; updateTitle() }
    }
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        if (width > 0 && height > 0) resizeDisplay()
    }
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { close(); return true }
    private fun updateTitle() {
        title.text = context.getString(if (hasFrame && displayId >= 0) R.string.floating_running else R.string.floating_waiting, label)
    }

    private fun action(operation: () -> Boolean, success: () -> Unit = {}) {
        if (displayId < 0 || closed || actionBusy) return
        actionBusy = true
        scope.launch {
            try {
                val ok = withContext(Dispatchers.IO) { runCatching(operation).getOrDefault(false) }
                if (!closed) { if (ok) success() else fail() }
            } finally { actionBusy = false }
        }
    }

    private fun minimize() = action({ remote.visible(displayId, false) }) {
        expandedBox = FloatingDisplayPolicy.Box(params.x, params.y, params.width, params.height)
        minimized = true; column.alpha = 0f; bubble.visibility = View.VISIBLE
        params.width = dp(56); params.height = dp(56); clampToViewport()
    }

    private fun restore() = action({ remote.visible(displayId, true) }) {
        minimized = false; column.alpha = 1f; bubble.visibility = View.GONE
        expandedBox?.let { params.x = it.x; params.y = it.y; params.width = it.width; params.height = it.height }
        clampToViewport()
    }

    private fun maximize() {
        if (savedBox == null) {
            savedBox = FloatingDisplayPolicy.Box(params.x, params.y, params.width, params.height)
            val safe = floatingSafeArea(wm, root)
            params.x = safe.left; params.y = safe.top; params.width = safe.width(); params.height = safe.height()
        } else {
            savedBox?.let { params.x = it.x; params.y = it.y; params.width = it.width; params.height = it.height }
            savedBox = null
        }
        clampToViewport(); root.post { resizeDisplay() }
    }

    private fun resizeDisplay() {
        if (displayId < 0 || minimized || closed || texture.width == 0) return
        resizeJob?.cancel()
        resizeJob = scope.launch {
            delay(150)
            val height = (renderWidth.toFloat() * texture.height / texture.width).toInt().coerceIn(240, 3840)
            val result = withContext(Dispatchers.IO) {
                runCatching { remote.resize(displayId, renderWidth, height, context.resources.displayMetrics.densityDpi) }.getOrNull()
            }
            if (!closed && result?.getBoolean("ok") == true) {
                renderHeight = height; texture.surfaceTexture?.setDefaultBufferSize(renderWidth, height)
            } else if (!closed) fail()
        }
    }

    private fun fail() {
        if (closed) return
        Toast.makeText(context, R.string.floating_root_unavailable, Toast.LENGTH_LONG).show()
        close()
    }

    fun close() {
        if (closed) return
        closed = true; resizeJob?.cancel(); input?.close()
        if (attached) { attached = false; runCatching { wm.removeView(root) } }
        val id = displayId; displayId = -1
        if (id >= 0) scope.launch(NonCancellable + Dispatchers.IO) { runCatching { remote.close(id) } }
        removed()
    }
}
