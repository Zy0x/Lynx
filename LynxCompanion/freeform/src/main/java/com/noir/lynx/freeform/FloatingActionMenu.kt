package com.noir.lynx.freeform

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

/** An in-window scrollable menu: never creates an unbounded PopupWindow. */
internal class FloatingActionMenu(
    private val root: FrameLayout, top: Int, appearance: FreeformAppearance,
    status: CharSequence, actions: List<Pair<String, () -> Unit>>, private val dismissed: () -> Unit
) {
    private val context = root.context
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val overlay = FrameLayout(context)
    init {
        val available = (root.height - top - dp(8)).coerceAtLeast(1)
        val panel = ScrollView(context).apply {
            isFillViewport = false
            background = GradientDrawable().apply {
                setColor(appearance.surfaceColor); cornerRadius = dp(10).toFloat()
                setStroke(dp(1).coerceAtLeast(1), appearance.accentColor)
            }
        }
        val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4)) }
        rows.addView(TextView(context).apply {
            text = status; textSize = 12f; setTextColor(appearance.accentColor)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        })
        for ((label, action) in actions) rows.addView(TextView(context).apply {
            text = label; textSize = 14f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL; minHeight = dp(48)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { close(); action() }
        }, LinearLayout.LayoutParams(-1, -2))
        panel.addView(rows)
        overlay.setOnClickListener { close() }
        overlay.addView(panel, FrameLayout.LayoutParams(minOf(dp(280), (root.width - dp(16)).coerceAtLeast(1)), available,
            Gravity.TOP or Gravity.END).apply { rightMargin = dp(8) })
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1).apply { topMargin = top })
        panel.post {
            rows.measure(View.MeasureSpec.makeMeasureSpec(panel.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            panel.layoutParams = (panel.layoutParams as FrameLayout.LayoutParams).apply {
                height = minOf(available, rows.measuredHeight)
            }
        }
    }
    fun close() { root.removeView(overlay); dismissed() }
}
