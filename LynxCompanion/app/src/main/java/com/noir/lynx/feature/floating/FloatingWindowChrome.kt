package com.noir.lynx.feature.floating

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import com.noir.lynx.R

/** Lightweight native chrome around a Surface, using scalable line icons and 48dp controls. */
internal class FloatingWindowChrome(context: Context, label: String, appIcon: Drawable?) {
    private val cyan = Color.rgb(89, 224, 214)
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private val headerHeight = maxOf(dp(48), (12 * context.resources.displayMetrics.scaledDensity * 2.8f).toInt())
    val controlsHeight = headerHeight + dp(48)
    val root = FrameLayout(context)
    val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val title = TextView(context).apply {
        text = label; textSize = 12f; setTextColor(Color.WHITE); maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    val status = TextView(context).apply {
        textSize = 12f; setTextColor(cyan); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
    }
    val dragHandle = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
        addView(title); addView(status)
    }
    val texture = android.view.TextureView(context)
    val bubble = FrameLayout(context).apply {
        visibility = View.GONE; contentDescription = label
        background = panel(dp(28).toFloat())
        addView(ImageView(context).apply { setImageDrawable(appIcon); scaleType = ImageView.ScaleType.FIT_CENTER },
            FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
    }
    val minimize = button(context, Glyph.MINIMIZE, R.string.floating_minimize)
    val close = button(context, Glyph.CLOSE, R.string.floating_close)
    val back = button(context, Glyph.BACK, R.string.floating_back)
    val rotate = button(context, Glyph.ROTATE, R.string.floating_rotate)
    val maximize = button(context, Glyph.MAXIMIZE, R.string.floating_maximize)
    val resize = button(context, Glyph.RESIZE, R.string.floating_resize)

    init {
        root.background = panel(dp(16).toFloat())
        root.clipToOutline = true
        root.elevation = dp(8).toFloat()
        root.addView(column, FrameLayout.LayoutParams(-1, -1))
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(context).apply { setImageDrawable(appIcon); setPadding(dp(10), dp(10), dp(6), dp(10)) },
                LinearLayout.LayoutParams(dp(44), dp(48)))
            addView(dragHandle, LinearLayout.LayoutParams(0, -1, 1f))
            addView(minimize, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        column.addView(header, LinearLayout.LayoutParams(-1, headerHeight))
        column.addView(texture, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(rotate, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(maximize, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(resize, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        column.addView(footer, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(bubble, FrameLayout.LayoutParams(-1, -1))
        status.setText(R.string.floating_state_starting)
    }

    fun busy(value: Boolean) {
        listOf(back, rotate, maximize, minimize).forEach { it.isEnabled = !value; it.alpha = if (value) .45f else 1f }
    }

    private fun panel(radius: Float) = GradientDrawable().apply {
        setColor(Color.rgb(10, 15, 20)); cornerRadius = radius
        setStroke(dp(1).coerceAtLeast(1), Color.argb(80, 89, 224, 214))
    }

    private fun button(context: Context, glyph: Glyph, description: Int) = ImageView(context).apply {
        setImageDrawable(LineIcon(glyph, cyan)); scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(13), dp(13), dp(13), dp(13))
        contentDescription = context.getString(description); tooltipText = contentDescription
        isClickable = true; isFocusable = true
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(50, 89, 224, 214)), null, panel(dp(12).toFloat()))
    }

    private enum class Glyph { CLOSE, MINIMIZE, BACK, ROTATE, MAXIMIZE, RESIZE }
    private class LineIcon(private val glyph: Glyph, color: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND
        }
        override fun draw(canvas: Canvas) {
            val checkpoint = canvas.save()
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = canvas.drawLine(x1, y1, x2, y2, paint)
            when (glyph) {
                Glyph.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
                Glyph.MINIMIZE -> line(5f, 16f, 19f, 16f)
                Glyph.BACK -> { line(15f, 5f, 8f, 12f); line(8f, 12f, 15f, 19f) }
                Glyph.MAXIMIZE -> canvas.drawRoundRect(4f, 5f, 20f, 19f, 2f, 2f, paint)
                Glyph.RESIZE -> { line(6f, 19f, 19f, 6f); line(12f, 19f, 19f, 12f); line(18f, 19f, 19f, 18f) }
                Glyph.ROTATE -> {
                    canvas.drawArc(4f, 4f, 20f, 20f, 40f, 275f, false, paint)
                    line(18f, 3f, 18f, 8f); line(18f, 8f, 13f, 8f)
                }
            }
            canvas.restoreToCount(checkpoint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
        @Deprecated("Drawable contract") override fun getOpacity() = PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth() = 24
        override fun getIntrinsicHeight() = 24
    }
}
