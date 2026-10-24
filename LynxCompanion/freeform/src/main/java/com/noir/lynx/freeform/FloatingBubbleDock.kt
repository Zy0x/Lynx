package com.noir.lynx.freeform

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Edge-docked Material 3 floating bubble manager for minimized Lynx Virtual apps.
 * - Single minimized app: renders a clean 54dp Material 3 squircle bubble on the left/right screen edge;
 *   tapping immediately restores the app.
 * - Multiple minimized apps (2..4): groups them into a single Material 3 bubble with a 2x2 mini icon grid
 *   and a cobalt-blue count badge; tapping expands a Material 3 tray to restore any app in the group.
 */
@SuppressLint("ClickableViewAccessibility")
internal class FloatingBubbleDock(
    private val context: Context,
    private val wm: WindowManager,
    private val appearance: FreeformAppearance
) {
    internal data class Entry(
        val packageName: String,
        val label: String,
        val icon: Drawable?,
        val onRestore: () -> Unit,
        val onClose: () -> Unit
    )

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Int) = ceil(v * density.toDouble()).toInt()

    private val entries = LinkedHashMap<String, Entry>()
    private val root = FrameLayout(context)
    private var attached = false
    private var expanded = false
    private var dockRight = true
    private var posY = dp(180)

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        title = "LynxBubbleDock"
        windowAnimations = 0
    }

    fun put(entry: Entry) {
        entries[entry.packageName] = entry
        expanded = false
        sync()
    }

    fun remove(packageName: String) {
        if (entries.remove(packageName) != null) {
            if (entries.size <= 1) expanded = false
            sync()
        }
    }

    fun clear() {
        entries.clear()
        expanded = false
        detach()
    }

    fun onViewportChanged() {
        if (attached) {
            updatePosition()
            runCatching { wm.updateViewLayout(root, params) }
        }
    }

    private fun sync() {
        if (entries.isEmpty()) {
            detach()
            return
        }
        rebuildView()
        updatePosition()
        if (!attached) {
            runCatching {
                wm.addView(root, params)
                attached = true
            }
        } else {
            runCatching { wm.updateViewLayout(root, params) }
        }
    }

    private fun detach() {
        root.removeAllViews()
        if (attached) {
            attached = false
            runCatching { wm.removeView(root) }
        }
    }

    private fun updatePosition() {
        val safe = floatingSafeArea(wm, root)
        val bubbleWidth = if (expanded && entries.size > 1) dp(236) else dp(58)
        val bubbleHeight = if (expanded && entries.size > 1) dp(56 + entries.size * 50) else dp(58)
        posY = posY.coerceIn(
            safe.top + dp(12),
            (safe.bottom - bubbleHeight - dp(12)).coerceAtLeast(safe.top + dp(12))
        )
        params.x = if (dockRight) {
            (safe.right - bubbleWidth - dp(6)).coerceAtLeast(safe.left)
        } else {
            safe.left + dp(6)
        }
        params.y = posY
    }

    private fun rebuildView() {
        root.removeAllViews()
        val list = entries.values.toList()
        if (list.isEmpty()) return

        if (expanded && list.size > 1) {
            root.addView(buildExpandedGroupCard(list))
        } else {
            root.addView(buildCollapsedBubble(list))
        }
    }

    private fun buildCollapsedBubble(list: List<Entry>): View {
        val wrapper = FrameLayout(context).apply {
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

        val bubbleCard = FrameLayout(context).apply {
            elevation = dp(10).toFloat()
            background = GradientDrawable().apply {
                setColor(0xF51B1E26.toInt())
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1).coerceAtLeast(1), 0x388AB4F8)
            }
        }

        if (list.size == 1) {
            val single = list.first()
            bubbleCard.addView(
                ImageView(context).apply {
                    setImageDrawable(single.icon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                },
                FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER)
            )
        } else {
            // 2x2 Material 3 Mini Icon Grid for grouped minimized apps
            val grid = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val bottomRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            list.take(4).forEachIndexed { idx, item ->
                val iconView = ImageView(context).apply {
                    setImageDrawable(item.icon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                val lp = LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
                if (idx < 2) topRow.addView(iconView, lp) else bottomRow.addView(iconView, lp)
            }
            grid.addView(topRow)
            if (list.size > 2) grid.addView(bottomRow)
            bubbleCard.addView(grid, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        }

        // Edge indicator bar on the screen-facing side
        val edgeBar = View(context).apply {
            background = GradientDrawable().apply {
                setColor(appearance.accentColor)
                cornerRadius = dp(3).toFloat()
            }
        }
        bubbleCard.addView(
            edgeBar,
            FrameLayout.LayoutParams(
                dp(3),
                dp(18),
                Gravity.CENTER_VERTICAL or (if (dockRight) Gravity.END else Gravity.START)
            ).apply {
                leftMargin = dp(2)
                rightMargin = dp(2)
            }
        )

        wrapper.addView(bubbleCard, FrameLayout.LayoutParams(dp(54), dp(54)))

        // Cobalt Blue Count Badge when 2+ apps are grouped
        if (list.size > 1) {
            val badge = TextView(context).apply {
                text = list.size.toString()
                textSize = 10.5f
                typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                minWidth = dp(20)
                minHeight = dp(20)
                setPadding(dp(5), dp(1), dp(5), dp(1))
                elevation = dp(12).toFloat()
                background = GradientDrawable().apply {
                    setColor(0xFF0B57D0.toInt())
                    cornerRadius = dp(50).toFloat()
                    setStroke(dp(1).coerceAtLeast(1), 0xFF11141B.toInt())
                }
            }
            wrapper.addView(
                badge,
                FrameLayout.LayoutParams(-2, -2, Gravity.TOP or (if (dockRight) Gravity.START else Gravity.END))
            )
        }

        attachDragAndClick(wrapper) {
            if (list.size == 1) {
                list.first().onRestore()
            } else {
                expanded = true
                sync()
            }
        }
        return wrapper
    }

    private fun buildExpandedGroupCard(list: List<Entry>): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            elevation = dp(14).toFloat()
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(0xF71E222B.toInt())
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1).coerceAtLeast(1), 0x24FFFFFF)
            }
        }

        // Header Row with Cobalt Blue Pill + Collapse Button
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(2), dp(8))
        }
        val pill = TextView(context).apply {
            text = "✦  ${list.size} Floating Apps"
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(0xFF0B57D0.toInt())
                cornerRadius = dp(50).toFloat()
            }
        }
        header.addView(pill, LinearLayout.LayoutParams(-2, -2))
        header.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))

        val collapseBtn = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x18FFFFFF)
            }
            addView(
                ImageView(context).apply {
                    setImageDrawable(FloatingLineIcon(FloatingGlyph.CLOSE, 0xFFE8EAED.toInt()))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                },
                FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER)
            )
            setOnClickListener {
                expanded = false
                sync()
            }
        }
        header.addView(collapseBtn, LinearLayout.LayoutParams(dp(28), dp(28)))
        attachDragAndClick(header) {
            expanded = false
            sync()
        }
        card.addView(header, LinearLayout.LayoutParams(dp(212), -2))

        // App Rows
        list.forEach { entry ->
            val rowMask = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(46)
                setPadding(dp(8), dp(5), dp(6), dp(5))
                background = RippleDrawable(ColorStateList.valueOf(0x268AB4F8), null, rowMask)
                setOnClickListener {
                    expanded = false
                    entry.onRestore()
                }
            }

            val iconHolder = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(0xFF282D3A.toInt())
                    cornerRadius = dp(10).toFloat()
                }
                addView(
                    ImageView(context).apply {
                        setImageDrawable(entry.icon)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    },
                    FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
                )
            }
            row.addView(iconHolder, LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(10) })

            val labelView = TextView(context).apply {
                text = entry.label
                textSize = 13f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(0xFFF1F3F4.toInt())
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            row.addView(labelView, LinearLayout.LayoutParams(0, -2, 1f))

            val closeAppBtn = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x14FFFFFF)
                }
                addView(
                    ImageView(context).apply {
                        setImageDrawable(FloatingLineIcon(FloatingGlyph.CLOSE, 0xFFC4C7C5.toInt()))
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    },
                    FrameLayout.LayoutParams(dp(12), dp(12), Gravity.CENTER)
                )
                setOnClickListener {
                    entry.onClose()
                }
            }
            row.addView(closeAppBtn, LinearLayout.LayoutParams(dp(26), dp(26)).apply { leftMargin = dp(6) })

            card.addView(row, LinearLayout.LayoutParams(dp(212), -2))
        }

        return card
    }

    private fun attachDragAndClick(view: View, onClick: () -> Unit) {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startParamX = 0
        var startParamY = 0
        var dragging = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startParamX = params.x
                    startParamY = params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                    }
                    if (dragging && attached) {
                        val safe = floatingSafeArea(wm, root)
                        params.x = (startParamX + dx.toInt()).coerceIn(safe.left, (safe.right - root.width.coerceAtLeast(dp(54))).coerceAtLeast(safe.left))
                        params.y = (startParamY + dy.toInt()).coerceIn(safe.top, (safe.bottom - root.height.coerceAtLeast(dp(54))).coerceAtLeast(safe.top))
                        posY = params.y
                        runCatching { wm.updateViewLayout(root, params) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        val safe = floatingSafeArea(wm, root)
                        val centerX = params.x + (root.width.coerceAtLeast(dp(54)) / 2)
                        val midX = safe.left + (safe.width() / 2)
                        val newDockRight = centerX >= midX
                        val sideChanged = newDockRight != dockRight
                        dockRight = newDockRight
                        posY = params.y
                        if (sideChanged) rebuildView()
                        updatePosition()
                        if (attached) runCatching { wm.updateViewLayout(root, params) }
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        onClick()
                    }
                }
            }
            true
        }
    }
}
