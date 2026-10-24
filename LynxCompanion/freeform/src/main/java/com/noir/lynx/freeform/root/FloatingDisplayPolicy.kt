package com.noir.lynx.freeform.root

/** Bounded allocations and safe geometry, independent of Android framework objects. */
internal object FloatingDisplayPolicy {
    fun validSize(width: Int, height: Int, density: Int) =
        width in 64..4096 && height in 64..4096 && width.toLong() * height <= 8_388_608 && density in 72..960

    data class Box(val x: Int, val y: Int, val width: Int, val height: Int)
    fun clamp(box: Box, left: Int, top: Int, safeWidth: Int, safeHeight: Int): Box {
        val w = box.width.coerceIn(1, safeWidth.coerceAtLeast(1))
        val h = box.height.coerceIn(1, safeHeight.coerceAtLeast(1))
        return Box(box.x.coerceIn(left, left + safeWidth.coerceAtLeast(w) - w),
            box.y.coerceIn(top, top + safeHeight.coerceAtLeast(h) - h), w, h)
    }

    fun resize(box: Box, dx: Int, dy: Int, left: Int, top: Int,
               safeWidth: Int, safeHeight: Int, minimum: Int): Box {
        val start = clamp(box, left, top, safeWidth, safeHeight)
        val maxWidth = (left + safeWidth.coerceAtLeast(1) - start.x).coerceAtLeast(1)
        val maxHeight = (top + safeHeight.coerceAtLeast(1) - start.y).coerceAtLeast(1)
        val minWidth = minimum.coerceIn(1, maxWidth)
        val minHeight = minimum.coerceIn(1, maxHeight)
        return start.copy(
            width = (start.width.toLong() + dx).coerceIn(minWidth.toLong(), maxWidth.toLong()).toInt(),
            height = (start.height.toLong() + dy).coerceIn(minHeight.toLong(), maxHeight.toLong()).toInt())
    }
}
