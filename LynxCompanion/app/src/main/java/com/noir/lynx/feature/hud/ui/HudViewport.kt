package com.noir.lynx.feature.hud.ui

import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/** Safe display geometry; portrait expansion follows the saved handle, without moving its dock. */
data class HudViewport(val widthDp: Float = 360f, val heightDp: Float = 720f) {
    val landscape get() = widthDp > heightDp
    val panelWidth get() = min((widthDp - 8f).coerceAtLeast(1f), if (landscape) 440f else 360f)
    val panelHeight get() = if (landscape) heightDp else min(heightDp * .65f, 440f)
    fun panelTop(safeTop: Int, safeHeight: Int, density: Float, handleFraction: Float): Int {
        val bottomLimit = safeTop + (safeHeight - ceil(panelHeight * density).toInt()).coerceAtLeast(0)
        // Align the header center (28 dp) with the collapsed grip center (36 dp).
        val anchor = safeTop + (safeHeight * handleFraction.coerceIn(0f, 1f) + 8f * density).roundToInt()
        return if (landscape) safeTop else anchor.coerceIn(safeTop, bottomLimit)
    }
}
