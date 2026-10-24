package com.noir.lynx.freeform

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

/** Material 3 in-window scrollable menu sheet with leading glyph icons and tonal status badge. */
internal class FloatingActionMenu(
    private val root: FrameLayout,
    top: Int,
    appearance: FreeformAppearance,
    status: CharSequence,
    actions: List<Triple<FloatingGlyph, String, () -> Unit>>,
    private val dismissed: () -> Unit
) {
    private val context = root.context
    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    private val overlay = FrameLayout(context)

    init {
        val available = (root.height - top - dp(8)).coerceAtLeast(1)
        val panel = ScrollView(context).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            elevation = dp(12).toFloat()
            background = GradientDrawable().apply {
                setColor(0xF71E222B.toInt())
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1).coerceAtLeast(1), 0x22FFFFFF)
            }
        }

        val rows = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        // Material 3 Sidekick Cobalt Status Pill Header Row
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(8))
        }
        val statusBadge = TextView(context).apply {
            text = "✦  $status"
            textSize = 11.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(0xFF0B57D0.toInt())
                cornerRadius = dp(50).toFloat()
            }
        }
        headerRow.addView(statusBadge, LinearLayout.LayoutParams(-2, -2))
        rows.addView(headerRow, LinearLayout.LayoutParams(-1, -2))

        // Subtle Material 3 Horizontal Divider
        rows.addView(
            View(context).apply { setBackgroundColor(0x18FFFFFF) },
            LinearLayout.LayoutParams(-1, dp(1).coerceAtLeast(1)).apply {
                leftMargin = dp(6)
                rightMargin = dp(6)
                bottomMargin = dp(4)
            }
        )

        // Material 3 Menu Action Rows with Tonal Icon Circle + Label
        for ((glyph, label, action) in actions) {
            val itemMask = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
            }
            val itemRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(46)
                setPadding(dp(10), dp(6), dp(12), dp(6))
                isClickable = true
                isFocusable = true
                background = RippleDrawable(ColorStateList.valueOf(0x268AB4F8), null, itemMask)
                setOnClickListener { close(); action() }
            }

            val iconBox = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(0xFF282D3A.toInt())
                    cornerRadius = dp(10).toFloat()
                }
                addView(
                    ImageView(context).apply {
                        setImageDrawable(FloatingLineIcon(glyph, appearance.accentColor))
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    },
                    FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER)
                )
            }
            itemRow.addView(iconBox, LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(12) })

            val textView = TextView(context).apply {
                text = label
                textSize = 13.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(0xFFE8EAED.toInt())
                gravity = Gravity.CENTER_VERTICAL
            }
            itemRow.addView(textView, LinearLayout.LayoutParams(0, -2, 1f))

            rows.addView(itemRow, LinearLayout.LayoutParams(-1, -2))
        }

        panel.addView(rows)
        overlay.setOnClickListener { close() }
        overlay.addView(
            panel,
            FrameLayout.LayoutParams(
                minOf(dp(268), (root.width - dp(16)).coerceAtLeast(1)),
                available,
                Gravity.TOP or Gravity.END
            ).apply {
                rightMargin = dp(8)
                topMargin = dp(4)
            }
        )
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1).apply { topMargin = top })
        panel.post {
            rows.measure(
                View.MeasureSpec.makeMeasureSpec(panel.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            panel.layoutParams = (panel.layoutParams as FrameLayout.LayoutParams).apply {
                height = minOf(available, rows.measuredHeight)
            }
        }
    }

    fun close() {
        root.removeView(overlay)
        dismissed()
    }
}
