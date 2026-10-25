package com.noir.lynx.feature.hud.ui

import kotlin.math.max

internal enum class HudPage { Summary, Details, Lab, Tools }

internal data class HudPanelLayout(val railWidth: Float, val contentWidth: Float, val useRail: Boolean)

/** All widths are dp; label widths must already include the current font scale. */
internal object HudLayoutPolicy {
    const val RAIL_WIDTH = 64f
    const val CONTENT_PADDING = 6f
    const val MIN_CONTENT = 176f

    fun resolve(panelWidth: Float, widestLabel: Float): HudPanelLayout {
        val rail = max(RAIL_WIDTH, widestLabel + 16f)
        val remaining = panelWidth - rail - CONTENT_PADDING * 2
        val useRail = remaining >= MIN_CONTENT
        return HudPanelLayout(rail, max(0f, if (useRail) remaining else panelWidth - CONTENT_PADDING * 2), useRail)
    }

    fun fits(available: Float, cells: List<Float>, gap: Float = 8f): Boolean =
        cells.sum() + gap * (cells.size - 1).coerceAtLeast(0) <= available

    fun columns(available: Float, cell: Float, gap: Float = 4f, maximum: Int = 3): Int =
        ((available + gap) / (cell.coerceAtLeast(48f) + gap)).toInt().coerceIn(1, maximum.coerceAtLeast(1))
}
