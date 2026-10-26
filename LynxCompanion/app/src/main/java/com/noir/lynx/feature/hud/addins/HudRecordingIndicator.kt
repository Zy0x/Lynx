package com.noir.lynx.feature.hud.addins

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.Keep
import com.noir.lynx.R
import com.noir.lynx.core.locale.AppLocale
import java.util.Locale
import kotlin.math.abs

/**
 * Material 3 Cyberpunk Floating Pill Recording Indicator.
 * Draggable, minimum 48dp touch target, with pulsing recording dot,
 * live duration timer, and tap-to-stop control.
 */
@Keep
class HudRecordingIndicator(private val context: Context, private val stop: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val windows = context.getSystemService(WindowManager::class.java)
    private var containerView: View? = null
    private var timeView: TextView? = null
    private var dotView: View? = null
    private var pulseAnimator: ValueAnimator? = null
    private var state = HudRecordingState()

    private val tick = object : Runnable {
        override fun run() {
            if (state.phase == HudRecordingPhase.RECORDING) {
                val elapsedSec = ((SystemClock.elapsedRealtime() - state.startedAt).coerceAtLeast(0) / 1000)
                timeView?.text = String.format(Locale.US, "%02d:%02d", elapsedSec / 60, elapsedSec % 60)
            } else if (state.phase == HudRecordingPhase.COUNTDOWN) {
                timeView?.text = "${state.countdown}s"
            } else if (state.phase == HudRecordingPhase.SAVING) {
                timeView?.text = AppLocale.getString(context, R.string.hud_record_saving)
            }
            if (containerView != null) main.postDelayed(this, 1000)
        }
    }

    fun update(value: HudRecordingState, visible: Boolean) = main.post {
        state = value
        if (!visible || !value.busy) {
            remove()
            return@post
        }
        if (containerView != null) {
            // Update live labels if already attached
            if (value.phase == HudRecordingPhase.COUNTDOWN) {
                timeView?.text = "${value.countdown}s"
            }
            return@post
        }

        val density = context.resources.displayMetrics.density
        val pillWidth = (112 * density).toInt()
        val pillHeight = (48 * density).toInt()

        val params = WindowManager.LayoutParams(
            pillWidth,
            pillHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (context.resources.displayMetrics.widthPixels - pillWidth - (16 * density)).toInt().coerceAtLeast(0)
            y = (24 * density).toInt()
            setTitle("Lynx recorder floating pill")
        }

        // Capsule Pill Layout
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())

            val pillBg = GradientDrawable().apply {
                setColor(0xF212141C.toInt()) // Deep OLED obsidian
                cornerRadius = 24 * density // Full capsule radius
                val strokeColor = if (value.phase == HudRecordingPhase.SAVING) 0xFF00FFAA.toInt() else 0xFFFA6B75.toInt()
                setStroke((1.5f * density).toInt().coerceAtLeast(1), strokeColor)
            }
            background = pillBg
            elevation = 10 * density
            contentDescription = AppLocale.getString(context, R.string.hud_record_stop)
        }

        // Pulsing Red Recording Dot
        val dot = View(context).apply {
            val dotSize = (10 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                rightMargin = (8 * density).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFFF3B30.toInt())
            }
        }
        dotView = dot
        root.addView(dot)

        // Pulsing Alpha Animation
        pulseAnimator = ValueAnimator.ofFloat(1.0f, 0.25f).apply {
            duration = 750
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                dot.alpha = anim.animatedValue as Float
            }
            start()
        }

        // Monospaced Timer Text
        val timer = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            val initialSeconds = ((SystemClock.elapsedRealtime() - value.startedAt).coerceAtLeast(0) / 1000)
            text = if (value.phase == HudRecordingPhase.COUNTDOWN) "${value.countdown}s"
            else String.format(Locale.US, "%02d:%02d", initialSeconds / 60, initialSeconds % 60)
        }
        timeView = timer
        root.addView(timer)

        // Mini Stop Icon Box
        val stopIcon = TextView(context).apply {
            text = "⏹"
            textSize = 12f
            setTextColor(0xFFFA6B75.toInt())
            gravity = Gravity.CENTER
            setPadding((4 * density).toInt(), 0, (2 * density).toInt(), 0)
        }
        root.addView(stopIcon)

        // Draggable gesture & tap to stop
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var baseX = 0
        var baseY = 0
        var moved = false

        root.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    baseX = params.x
                    baseY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        val maxW = (context.resources.displayMetrics.widthPixels - params.width).coerceAtLeast(0)
                        val maxH = (context.resources.displayMetrics.heightPixels - params.height).coerceAtLeast(0)
                        params.x = (baseX + dx).toInt().coerceIn(0, maxW)
                        params.y = (baseY + dy).toInt().coerceIn(0, maxH)
                        runCatching { windows.updateViewLayout(root, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        stop()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        runCatching {
            windows.addView(root, params)
            containerView = root
            tick.run()
        }
    }

    private fun remove() {
        main.removeCallbacks(tick)
        pulseAnimator?.cancel()
        pulseAnimator = null
        containerView?.let { v ->
            runCatching { windows.removeView(v) }
        }
        containerView = null
        timeView = null
        dotView = null
    }

    fun close() = main.post { remove() }
}
