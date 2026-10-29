package com.noir.lynx.feature.hud.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.hypot

/** Separates handle opening, metric paging and dragging before forwarding touch to Compose. */
internal class HudSidebarHostView(
    context: Context,
    private val sidebar: () -> Boolean,
    private val expanded: () -> Boolean,
    private val right: () -> Boolean,
    private val paged: () -> Boolean,
    private val onOpen: (Boolean) -> Unit,
    private val onDragStart: () -> Unit,
    private val onDrag: (Float, Float, Float) -> Unit,
    private val onDragEnd: () -> Unit,
    private val onBoundsChanged: () -> Unit
) : FrameLayout(context) {
    private enum class Gesture { Pending, Forward, Drag, Open }
    private var gesture = Gesture.Pending
    private var downX = 0f
    private var downY = 0f
    private var handle = false
    private var cancelled = false
    private val handler = Handler(Looper.getMainLooper())
    private val hold = Runnable {
        if (gesture == Gesture.Pending && !expanded()) {
            gesture = Gesture.Drag
            cancelChild()
        }
    }

    init { setOnClickListener { onOpen(true) } }

    private fun cancelChild() {
        if (cancelled) return
        cancelled = true
        val event = MotionEvent.obtain(android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        super.dispatchTouchEvent(event)
        event.recycle()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed) post(onBoundsChanged)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(hold)
        super.onDetachedFromWindow()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) { onOpen(false); return false }
        if (!sidebar()) return super.dispatchTouchEvent(event)
        val density = resources.displayMetrics.density
        val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacks(hold)
                downX = event.rawX; downY = event.rawY; cancelled = false
                handle = if (right()) event.x >= width - 48f * density else event.x <= 48f * density
                val header = event.y <= 56f * density && event.x >= 48f * density && event.x <= width - 48f * density
                gesture = if (expanded() && !header) Gesture.Forward else Gesture.Pending
                if (!expanded()) { onDragStart(); handler.postDelayed(hold, 220L) }
                val handled = super.dispatchTouchEvent(event)
                return if (gesture == Gesture.Forward) handled else true
            }
            MotionEvent.ACTION_MOVE -> {
                if (gesture == Gesture.Forward) return super.dispatchTouchEvent(event)
                if (gesture == Gesture.Open) return true
                val dx = event.rawX - downX; val dy = event.rawY - downY
                if (gesture == Gesture.Pending) {
                    if (expanded()) {
                        val outward = if (right()) dx else -dx
                        if (outward > 16f * density && abs(dx) > abs(dy) * 1.2f) {
                            gesture = Gesture.Open; cancelChild(); onOpen(false)
                        }
                    } else if (!handle && paged() && abs(dx) > slop && abs(dx) > abs(dy) * 1.2f) {
                        handler.removeCallbacks(hold); gesture = Gesture.Forward
                        return super.dispatchTouchEvent(event)
                    } else {
                        val inward = if (right()) -dx else dx
                        if (handle && inward > 16f * density && abs(dx) > abs(dy) * 1.2f) {
                            handler.removeCallbacks(hold); gesture = Gesture.Open; cancelChild(); onOpen(true)
                        } else if (hypot(dx, dy) > slop) {
                            handler.removeCallbacks(hold); gesture = Gesture.Drag; cancelChild()
                        }
                    }
                }
                if (gesture == Gesture.Drag) { onDrag(dx, dy, event.rawX); return true }
                return if (cancelled) true else super.dispatchTouchEvent(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(hold)
                val completed = gesture
                gesture = Gesture.Pending
                if (completed == Gesture.Drag) { onDragEnd(); return true }
                if (event.actionMasked == MotionEvent.ACTION_UP && !expanded() && completed == Gesture.Pending) {
                    cancelChild(); performClick(); return true
                }
                return if (cancelled || completed == Gesture.Open) true else super.dispatchTouchEvent(event)
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
