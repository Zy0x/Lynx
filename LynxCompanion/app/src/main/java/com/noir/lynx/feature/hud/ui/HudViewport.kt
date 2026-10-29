package com.noir.lynx.feature.hud.ui

import kotlin.math.ceil
import kotlin.math.min

/** Safe display dimensions in dp, independent of the underlying activity's requested orientation. */
data class HudViewport(val widthDp: Float = 360f, val heightDp: Float = 720f) {
    val landscape get() = widthDp > heightDp
    val panelWidth get() = min((widthDp - 8f).coerceAtLeast(1f), if (landscape) 440f else 360f)
    val panelHeight get() = if (landscape) heightDp else min(heightDp * .65f, 480f)
    fun panelTop(safeTop: Int, safeHeight: Int, density: Float): Int =
        if (landscape) safeTop else safeTop + (safeHeight - ceil(panelHeight * density).toInt()).coerceAtLeast(0)
}
