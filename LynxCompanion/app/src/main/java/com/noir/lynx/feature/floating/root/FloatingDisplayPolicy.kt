package com.noir.lynx.feature.floating.root

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
}
