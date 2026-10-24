package com.noir.lynx.freeform

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*

/** Material 3 compact single-row chrome. Extra actions live in the Material 3 menu sheet. */
internal class FloatingWindowChrome(
    private val context: Context,
    label: String,
    appIcon: Drawable?,
    private val appearance: FreeformAppearance
) {
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Int) = kotlin.math.ceil(v * density.toDouble()).toInt()
    private val headerHeight = dp(FloatingChromePolicy.headerHeight(context.resources.configuration.fontScale, appearance.titleSizeSp))
    private var toolbarShown = appearance.showToolbar
    private var minimized = false
    val controlsHeight get() = headerHeight + if (toolbarShown) dp(48) else 0
    val root = FrameLayout(context)
    val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val title = TextView(context).apply {
        text = label
        textSize = appearance.titleSizeSp
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(0xFFF1F3F4.toInt())
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
    }
    val dragHandle: View = title
    val appIconButton = FrameLayout(context).apply {
        contentDescription = context.getString(R.string.lynx_freeform_minimize)
        tooltipText = contentDescription
        isClickable = true
        isFocusable = true
        val bg = GradientDrawable().apply {
            setColor(0xFF252A36.toInt())
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1).coerceAtLeast(1), 0x268AB4F8)
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(10).toFloat()
        }
        background = RippleDrawable(ColorStateList.valueOf(0x338AB4F8), bg, mask)
        addView(
            ImageView(context).apply { setImageDrawable(appIcon) },
            FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
        )
    }
    val texture = android.view.TextureView(context)
    private var state: CharSequence = context.getString(R.string.lynx_freeform_state_starting)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        isIndeterminate = true
        indeterminateTintList = ColorStateList.valueOf(appearance.accentColor)
    }
    val bubble = FrameLayout(context).apply {
        visibility = View.GONE
        contentDescription = label
        elevation = dp(10).toFloat()
        background = GradientDrawable().apply {
            setColor(0xF51B1E26.toInt())
            cornerRadius = dp(28).toFloat()
            setStroke(dp(1).coerceAtLeast(1), 0x338AB4F8)
        }
        addView(
            ImageView(context).apply { setImageDrawable(appIcon) },
            FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER)
        )
    }
    val minimize = button(context, FloatingGlyph.MINIMIZE, R.string.lynx_freeform_minimize)
    val close = button(context, FloatingGlyph.CLOSE, R.string.lynx_freeform_close)
    val back = button(context, FloatingGlyph.BACK, R.string.lynx_freeform_back)
    val rotate = button(context, FloatingGlyph.ROTATE, R.string.lynx_freeform_rotate)
    val maximize = button(context, FloatingGlyph.MAXIMIZE, R.string.lynx_freeform_maximize)
    val resize = button(context, FloatingGlyph.RESIZE, R.string.lynx_freeform_resize, circularPill = false)
    private val more = button(context, FloatingGlyph.MORE, R.string.lynx_freeform_window_actions)
    private val footer = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xF5171A22.toInt())
    }
    private var menu: FloatingActionMenu? = null
    private val hideResizeHint = Runnable {
        resize.animate().alpha(.28f).setDuration(200).start()
    }

    init {
        root.background = panel(dp(appearance.cornerRadiusDp).toFloat())
        root.clipToOutline = true
        root.elevation = dp(12).toFloat()
        root.addView(column, FrameLayout.LayoutParams(-1, -1))

        val headerContainer = FrameLayout(context).apply {
            setBackgroundColor(0xF5171A22.toInt())
        }
        val topHandlePill = View(context).apply {
            background = GradientDrawable().apply {
                setColor(0x40FFFFFF)
                cornerRadius = dp(4).toFloat()
            }
        }
        headerContainer.addView(
            topHandlePill,
            FrameLayout.LayoutParams(dp(30), dp(3).coerceAtLeast(2), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(4)
            }
        )

        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(4), 0)
            addView(appIconButton, LinearLayout.LayoutParams(dp(32), dp(32)).apply { rightMargin = dp(10) })
            addView(title, LinearLayout.LayoutParams(0, -1, 1f))
            addView(more, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(close, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        headerContainer.addView(header, FrameLayout.LayoutParams(-1, -1))

        column.addView(headerContainer, LinearLayout.LayoutParams(-1, headerHeight))
        column.addView(texture, LinearLayout.LayoutParams(-1, 0, 1f))
        footer.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        footer.addView(rotate, LinearLayout.LayoutParams(dp(48), dp(48)))
        footer.addView(maximize, LinearLayout.LayoutParams(dp(48), dp(48)))
        footer.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        column.addView(footer, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(progress, FrameLayout.LayoutParams(-1, dp(2)).apply { topMargin = headerHeight - dp(2) })
        root.addView(resize, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM or Gravity.RIGHT))
        root.addView(bubble, FrameLayout.LayoutParams(-1, -1))
        placeResizeHandle()
        more.setOnClickListener { toggleMenu() }
        title.setOnClickListener { showResizeHint() }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { showResizeHint() }
            override fun onViewDetachedFromWindow(v: View) { root.removeCallbacks(hideResizeHint); resize.animate().cancel() }
        })
    }

    private fun placeResizeHandle() {
        footer.visibility = if (toolbarShown) View.VISIBLE else View.GONE
        footer.setPadding(dp(4), 0, dp(48), 0)
        resize.setPadding(dp(22), dp(22), dp(8), dp(8))
        resize.visibility = if (minimized) View.GONE else View.VISIBLE
        resize.bringToFront()
        bubble.bringToFront()
    }

    private fun toggleMenu() {
        if (menu != null) { dismissMenu(); return }
        placeResizeHandle()
        fun action(glyph: FloatingGlyph, id: Int, view: View) =
            Triple(glyph, context.getString(id)) { if (view.isEnabled) view.performClick(); Unit }
        menu = FloatingActionMenu(
            root = root,
            top = headerHeight,
            appearance = appearance,
            status = state,
            actions = listOf(
                action(FloatingGlyph.BACK, R.string.lynx_freeform_back, back),
                action(FloatingGlyph.MINIMIZE, R.string.lynx_freeform_minimize, minimize),
                action(FloatingGlyph.MAXIMIZE, R.string.lynx_freeform_maximize, maximize),
                action(FloatingGlyph.ROTATE, R.string.lynx_freeform_rotate, rotate),
                Triple(FloatingGlyph.RESIZE, context.getString(R.string.lynx_freeform_resize)) { showResizeHint() },
                Triple(
                    FloatingGlyph.TOOLBAR,
                    context.getString(if (toolbarShown) R.string.lynx_freeform_hide_toolbar else R.string.lynx_freeform_show_toolbar)
                ) {
                    toolbarShown = !toolbarShown
                    placeResizeHandle()
                }
            )
        ) { menu = null }
    }

    fun dismissMenu() { menu?.close(); menu = null }
    fun showResizeHint(hold: Boolean = false) {
        root.removeCallbacks(hideResizeHint); resize.animate().cancel(); resize.alpha = 1f
        if (!hold) root.postDelayed(hideResizeHint, 3000)
    }
    fun finishResize() { showResizeHint() }
    fun state(resource: Int, busy: Boolean) {
        state = context.getString(resource)
        title.contentDescription = context.getString(R.string.lynx_freeform_title_state, title.text, state)
        progress.visibility = if (busy) View.VISIBLE else View.GONE
    }
    fun busy(value: Boolean) {
        listOf(back, rotate, maximize, minimize, appIconButton).forEach { it.isEnabled = !value; it.alpha = if (value) .45f else 1f }
    }
    fun minimized(value: Boolean) {
        minimized = value
        resize.visibility = if (value) View.GONE else View.VISIBLE
        if (value) root.removeCallbacks(hideResizeHint) else showResizeHint()
        progress.visibility = if (value) View.GONE else progress.visibility
    }

    private fun panel(radius: Float) = GradientDrawable().apply {
        setColor(appearance.surfaceColor)
        cornerRadius = radius
        setStroke(dp(1).coerceAtLeast(1), 0x1FFFFFFF)
    }

    private fun button(
        context: Context,
        glyph: FloatingGlyph,
        description: Int,
        circularPill: Boolean = true
    ) = ImageView(context).apply {
        val iconColor = if (glyph == FloatingGlyph.RESIZE) 0xFFC4C7C5.toInt() else 0xFFE8EAED.toInt()
        setImageDrawable(FloatingLineIcon(glyph, iconColor))
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(13), dp(13), dp(13), dp(13))
        contentDescription = context.getString(description)
        tooltipText = contentDescription
        isClickable = true
        isFocusable = true
        background = if (circularPill) {
            val circle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x16FFFFFF)
            }
            val mask = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            val inset = dp(7)
            RippleDrawable(
                ColorStateList.valueOf(0x338AB4F8),
                InsetDrawable(circle, inset, inset, inset, inset),
                InsetDrawable(mask, inset, inset, inset, inset)
            )
        } else {
            null
        }
    }
}
