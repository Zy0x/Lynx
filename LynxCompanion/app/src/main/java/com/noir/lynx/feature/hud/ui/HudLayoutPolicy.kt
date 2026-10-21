package com.noir.lynx.feature.hud.ui

import kotlin.math.max

internal enum class HudPage { Summary, Details, Tools }

internal data class HudPanelLayout(val railWidth: Float, val contentWidth: Float, val useRail: Boolean)

/** All widths are dp; label widths must already include the current font scale. */
internal object HudLayoutPolicy {
    const val CONTENT_PADDING = 6f
    const val MIN_CONTENT = 176f

    fun resolve(panelWidth: Float, widestLabel: Float): HudPanelLayout {
        val rail = max(72f, widestLabel + 16f)
        val remaining = panelWidth - rail - CONTENT_PADDING * 2
        val useRail = remaining >= MIN_CONTENT
        return HudPanelLayout(rail, max(0f, if (useRail) remaining else panelWidth - CONTENT_PADDING * 2), useRail)
    }

    fun fits(available: Float, cells: List<Float>, gap: Float = 8f): Boolean =
        cells.sum() + gap * (cells.size - 1).coerceAtLeast(0) <= available
}
