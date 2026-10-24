package com.noir.lynx.freeform

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Compact single-row chrome. Extra actions stay in the bounded in-panel menu. */
internal class FloatingWindowChrome(private val context: Context, label: String, appIcon: Drawable?, private val appearance: FreeformAppearance) {
    private val density = context.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()
    private val headerHeight = dp(FloatingChromePolicy.headerHeight(context.resources.configuration.fontScale, appearance.titleSizeSp))
    private var toolbarShown = appearance.showToolbar
    private var resizeArmed = false
    val controlsHeight get() = headerHeight + if (toolbarShown) dp(48) else 0
    val root = FrameLayout(context)
    val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val title = TextView(context).apply {
        text = label; textSize = appearance.titleSizeSp; setTextColor(Color.WHITE)
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER_VERTICAL
    }
    val dragHandle: View = title
    val texture = android.view.TextureView(context)
    private var state: CharSequence = context.getString(R.string.lynx_freeform_state_starting)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        isIndeterminate = true; indeterminateTintList = ColorStateList.valueOf(appearance.accentColor)
    }
    val bubble = FrameLayout(context).apply {
        visibility = View.GONE; contentDescription = label; background = panel(dp(28).toFloat())
        addView(ImageView(context).apply { setImageDrawable(appIcon) }, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
    }
    val minimize = button(context, FloatingGlyph.MINIMIZE, R.string.lynx_freeform_minimize)
    val close = button(context, FloatingGlyph.CLOSE, R.string.lynx_freeform_close)
    val back = button(context, FloatingGlyph.BACK, R.string.lynx_freeform_back)
    val rotate = button(context, FloatingGlyph.ROTATE, R.string.lynx_freeform_rotate)
    val maximize = button(context, FloatingGlyph.MAXIMIZE, R.string.lynx_freeform_maximize)
    val resize = button(context, FloatingGlyph.RESIZE, R.string.lynx_freeform_resize)
    private val more = button(context, FloatingGlyph.MORE, R.string.lynx_freeform_window_actions)
    private val footer = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private var menu: FloatingActionMenu? = null

    init {
        root.background = panel(dp(appearance.cornerRadiusDp).toFloat()); root.clipToOutline = true
        root.elevation = dp(4).toFloat()
        root.addView(column, FrameLayout.LayoutParams(-1, -1))
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), 0, 0, 0)
            addView(ImageView(context).apply { setImageDrawable(appIcon); setPadding(dp(4), dp(4), dp(4), dp(4)) },
                LinearLayout.LayoutParams(dp(32), dp(32)))
            addView(title, LinearLayout.LayoutParams(0, -1, 1f))
            addView(more, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        column.addView(header, LinearLayout.LayoutParams(-1, headerHeight))
        column.addView(texture, LinearLayout.LayoutParams(-1, 0, 1f))
        footer.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))
        footer.addView(rotate, LinearLayout.LayoutParams(dp(48), dp(48)))
        footer.addView(maximize, LinearLayout.LayoutParams(dp(48), dp(48)))
        footer.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        column.addView(footer, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(progress, FrameLayout.LayoutParams(-1, dp(2)).apply { topMargin = headerHeight - dp(2) })
        root.addView(bubble, FrameLayout.LayoutParams(-1, -1))
        placeResizeHandle()
        more.setOnClickListener { toggleMenu() }
    }

    private fun placeResizeHandle() {
        (resize.parent as? ViewGroup)?.removeView(resize)
        footer.visibility = if (toolbarShown) View.VISIBLE else View.GONE
        resize.setPadding(dp(16), dp(16), dp(16), dp(16))
        if (toolbarShown) footer.addView(resize, LinearLayout.LayoutParams(dp(48), dp(48)))
        else root.addView(resize, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM or Gravity.END))
        resize.visibility = if (toolbarShown || resizeArmed) View.VISIBLE else View.GONE
        resize.bringToFront(); bubble.bringToFront()
    }

    private fun toggleMenu() {
        if (menu != null) { dismissMenu(); return }
        resizeArmed = false; placeResizeHandle()
        fun action(id: Int, view: View) = context.getString(id) to { if (view.isEnabled) view.performClick(); Unit }
        menu = FloatingActionMenu(root, headerHeight, appearance, state, listOf(
            action(R.string.lynx_freeform_back, back), action(R.string.lynx_freeform_minimize, minimize),
            action(R.string.lynx_freeform_maximize, maximize), action(R.string.lynx_freeform_rotate, rotate),
            context.getString(R.string.lynx_freeform_resize) to { resizeArmed = true; placeResizeHandle() },
            context.getString(if (toolbarShown) R.string.lynx_freeform_hide_toolbar else R.string.lynx_freeform_show_toolbar) to {
                toolbarShown = !toolbarShown; placeResizeHandle()
            }
        )) { menu = null }
    }

    fun dismissMenu() { menu?.close(); menu = null }
    fun finishResize() { resizeArmed = false; if (!toolbarShown) resize.visibility = View.GONE }
    fun state(resource: Int, busy: Boolean) {
        state = context.getString(resource)
        title.contentDescription = context.getString(R.string.lynx_freeform_title_state, title.text, state)
        progress.visibility = if (busy) View.VISIBLE else View.GONE
    }
    fun busy(value: Boolean) {
        listOf(back, rotate, maximize, minimize).forEach { it.isEnabled = !value; it.alpha = if (value) .45f else 1f }
    }
    fun minimized(value: Boolean) {
        resize.visibility = if (value || (!toolbarShown && !resizeArmed)) View.GONE else View.VISIBLE
        progress.visibility = if (value) View.GONE else progress.visibility
    }
    private fun panel(radius: Float) = GradientDrawable().apply {
        setColor(appearance.surfaceColor); cornerRadius = radius
        setStroke(dp(1).coerceAtLeast(1), Color.argb(65, Color.red(appearance.accentColor), Color.green(appearance.accentColor), Color.blue(appearance.accentColor)))
    }
    private fun button(context: Context, glyph: FloatingGlyph, description: Int) = ImageView(context).apply {
        setImageDrawable(FloatingLineIcon(glyph, appearance.accentColor)); scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(14), dp(14), dp(14), dp(14))
        contentDescription = context.getString(description); tooltipText = contentDescription
        isClickable = true; isFocusable = true
        background = RippleDrawable(ColorStateList.valueOf(0x3359e0d6), null, panel(dp(10).toFloat()))
    }
}
