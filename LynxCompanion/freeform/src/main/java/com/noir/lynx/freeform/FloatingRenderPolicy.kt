package com.noir.lynx.freeform

import kotlin.math.min
import kotlin.math.sqrt

/** Render the host viewport with a bounded GPU allocation and readable phone layout. */
internal object FloatingRenderPolicy {
    const val MAX_PIXELS = 1_500_000
    const val MAX_EDGE = 1600
    data class Spec(val width: Int, val height: Int, val density: Int)

    fun fit(width: Int, height: Int, density: Int, budget: RenderBudget = RenderBudget()): Spec {
        require(width > 0 && height > 0 && density > 0)
        val scale = min(1.0, min(budget.maxEdge.toDouble() / maxOf(width, height),
            sqrt(budget.maxPixels.toDouble() / (width.toDouble() * height))))
        fun even(value: Double) = ((value.toInt() / 2) * 2).coerceIn(64, budget.maxEdge)
        val w = even(width * scale)
        val h = even(height * scale)
        // A narrow host gets a 320dp app layout without a full-screen render buffer.
        val dpi = min(density * scale, w * 160.0 / 320).toInt().coerceIn(72, 960)
        return Spec(w, h, dpi)
    }
}
