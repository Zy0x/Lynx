package com.noir.lynx.feature.hud.overlay

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.hypot

/** Separates handle opening, metric paging and dragging before forwarding touch to Compose. Supports multi-touch & split touch. */
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
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
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
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            if (expanded()) onOpen(false)
            return false
        }
        if (!sidebar()) return super.dispatchTouchEvent(event)
        val density = resources.displayMetrics.density
        val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                handler.removeCallbacks(hold)
                downX = event.rawX; downY = event.rawY; cancelled = false
                handle = if (right()) event.x >= width - 48f * density else event.x <= 48f * density
                val header = event.y <= 56f * density && event.x >= 48f * density && event.x <= width - 48f * density
                gesture = if (expanded() && !header) Gesture.Forward else Gesture.Pending
                if (!expanded()) { onDragStart(); handler.postDelayed(hold, 300L) }
                val handled = super.dispatchTouchEvent(event)
                return if (gesture == Gesture.Forward) handled else true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val actionIdx = event.actionIndex
                val px = event.getX(actionIdx)
                val py = event.getY(actionIdx)
                val onHandle = if (right()) px >= width - 48f * density else px <= 48f * density
                if (onHandle && (!handle || activePointerId == MotionEvent.INVALID_POINTER_ID || gesture == Gesture.Forward)) {
                    activePointerId = event.getPointerId(actionIdx)
                    handler.removeCallbacks(hold)
                    downX = if (Build.VERSION.SDK_INT >= 29) event.getRawX(actionIdx) else event.rawX + (px - event.x)
                    downY = if (Build.VERSION.SDK_INT >= 29) event.getRawY(actionIdx) else event.rawY + (py - event.y)
                    handle = true; cancelled = false
                    gesture = Gesture.Pending
                    if (!expanded()) { onDragStart(); handler.postDelayed(hold, 300L) }
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIdx = if (activePointerId != MotionEvent.INVALID_POINTER_ID) event.findPointerIndex(activePointerId) else 0
                if (pointerIdx == -1) return super.dispatchTouchEvent(event)
                if (gesture == Gesture.Forward) return super.dispatchTouchEvent(event)
                if (gesture == Gesture.Open) return true
                val curRawX = if (Build.VERSION.SDK_INT >= 29) event.getRawX(pointerIdx) else event.rawX + (event.getX(pointerIdx) - event.x)
                val curRawY = if (Build.VERSION.SDK_INT >= 29) event.getRawY(pointerIdx) else event.rawY + (event.getY(pointerIdx) - event.y)
                val dx = curRawX - downX; val dy = curRawY - downY
                if (gesture == Gesture.Pending) {
                    if (expanded()) {
                        val outward = if (right()) dx else -dx
                        if (outward > 14f * density && abs(dx) > abs(dy) * 0.8f) {
                            gesture = Gesture.Open; cancelChild(); onOpen(false)
                            return true
                        }
                    } else if (!handle && paged() && abs(dx) > slop && abs(dx) > abs(dy) * 1.1f) {
                        handler.removeCallbacks(hold); gesture = Gesture.Forward
                        return super.dispatchTouchEvent(event)
                    } else {
                        val inward = if (right()) -dx else dx
                        val inwardThreshold = maxOf(slop, 10f * density)
                        // 1. Natural inward pull/swipe gesture (allows thumb arc up to ~60 degrees):
                        if (handle && inward >= inwardThreshold && inward > abs(dy) * 0.5f) {
                            handler.removeCallbacks(hold); gesture = Gesture.Open; cancelChild(); onOpen(true)
                            return true
                        }
                        // 2. Deliberate vertical repositioning drag along the screen bezel:
                        else if (abs(dy) > slop && abs(dy) > abs(dx) * 1.3f) {
                            handler.removeCallbacks(hold); gesture = Gesture.Drag; cancelChild()
                        }
                        // 3. Outward cross-screen drag (e.g. dragging handle across screen to switch sides):
                        else if (abs(dx) > slop && inward < -slop) {
                            handler.removeCallbacks(hold); gesture = Gesture.Drag; cancelChild()
                        }
                    }
                }
                if (gesture == Gesture.Drag) { onDrag(dx, dy, curRawX); return true }
                return if (cancelled) true else super.dispatchTouchEvent(event)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val actionIdx = event.actionIndex
                val pointerId = event.getPointerId(actionIdx)
                if (pointerId == activePointerId) {
                    handler.removeCallbacks(hold)
                    val curRawX = if (Build.VERSION.SDK_INT >= 29) event.getRawX(actionIdx) else event.rawX + (event.getX(actionIdx) - event.x)
                    val curRawY = if (Build.VERSION.SDK_INT >= 29) event.getRawY(actionIdx) else event.rawY + (event.getY(actionIdx) - event.y)
                    val completed = gesture
                    gesture = Gesture.Pending
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    if (completed == Gesture.Drag) {
                        val totalDist = hypot(curRawX - downX, curRawY - downY)
                        if (totalDist < slop) {
                            cancelChild(); performClick(); return true
                        }
                        onDragEnd(); return true
                    }
                    if (!expanded()) {
                        if (completed == Gesture.Pending || completed == Gesture.Forward) {
                            val totalDist = hypot(curRawX - downX, curRawY - downY)
                            if (totalDist < slop * 1.5f) {
                                cancelChild(); performClick(); return true
                            }
                        }
                    }
                    return if (cancelled || completed == Gesture.Open) true else super.dispatchTouchEvent(event)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(hold)
                val completed = gesture
                gesture = Gesture.Pending
                activePointerId = MotionEvent.INVALID_POINTER_ID
                if (completed == Gesture.Drag) {
                    val totalDist = hypot(event.rawX - downX, event.rawY - downY)
                    if (totalDist < slop) {
                        cancelChild(); performClick(); return true
                    }
                    onDragEnd(); return true
                }
                if (event.actionMasked == MotionEvent.ACTION_UP && !expanded()) {
                    if (completed == Gesture.Pending || completed == Gesture.Forward) {
                        val totalDist = hypot(event.rawX - downX, event.rawY - downY)
                        if (totalDist < slop * 1.5f) {
                            cancelChild(); performClick(); return true
                        }
                    }
                }
                return if (cancelled || completed == Gesture.Open) true else super.dispatchTouchEvent(event)
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
