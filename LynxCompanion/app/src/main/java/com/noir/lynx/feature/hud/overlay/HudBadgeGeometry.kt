package com.noir.lynx.feature.hud.overlay

import kotlin.math.roundToInt

internal data class HudScreenRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun intersects(x: Int, y: Int, width: Int, height: Int) =
        x < right && x + width > left && y < bottom && y + height > top
}

internal data class HudBadgePoint(val x: Int, val y: Int, val edgeX: Float, val edgeY: Float)

/** Absolute display pixels. Insets are applied once; cutouts are local obstacles, not full-edge margins. */
internal data class HudBadgeBounds(val frame: HudScreenRect, val cutouts: List<HudScreenRect> = emptyList()) {
    fun place(rawX: Int, rawY: Int, width: Int, height: Int): HudBadgePoint {
        val maxX = (frame.right - width).coerceAtLeast(frame.left)
        val maxY = (frame.bottom - height).coerceAtLeast(frame.top)
        fun clamped(x: Int, y: Int) = x.coerceIn(frame.left, maxX) to y.coerceIn(frame.top, maxY)
        val original = clamped(rawX, rawY)
        // Candidate edges include every obstacle so adjacent cutouts can be avoided together.
        val xs = listOf(original.first, frame.left, maxX) + cutouts.flatMap { listOf(it.left - width, it.right) }
        val ys = listOf(original.second, frame.top, maxY) + cutouts.flatMap { listOf(it.top - height, it.bottom) }
        val point = if (cutouts.none { it.intersects(original.first, original.second, width, height) }) original
        else xs.flatMap { x -> ys.map { y -> clamped(x, y) } }.distinct()
            .filter { (x, y) -> cutouts.none { it.intersects(x, y, width, height) } }
            .minByOrNull { (x, y) ->
                val dx = x.toLong() - rawX; val dy = y.toLong() - rawY
                dx * dx + dy * dy
            } ?: original
        return HudBadgePoint(point.first, point.second,
            when (point.first) { frame.left -> -1f; maxX -> 1f; else -> 0f },
            when (point.second) { frame.top -> -1f; maxY -> 1f; else -> 0f })
    }

    fun fromFractions(x: Float, y: Float, width: Int, height: Int) = place(
        frame.left + ((frame.right - frame.left - width).coerceAtLeast(0) * x.coerceIn(0f, 1f)).roundToInt(),
        frame.top + ((frame.bottom - frame.top - height).coerceAtLeast(0) * y.coerceIn(0f, 1f)).roundToInt(), width, height)

    fun fractions(x: Int, y: Int, width: Int, height: Int) =
        ((x - frame.left).toFloat() / (frame.right - frame.left - width).coerceAtLeast(1)).coerceIn(0f, 1f) to
            ((y - frame.top).toFloat() / (frame.bottom - frame.top - height).coerceAtLeast(1)).coerceIn(0f, 1f)
}
