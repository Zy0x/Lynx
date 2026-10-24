package com.noir.lynx.freeform

import kotlin.math.ceil

/** One title line at normal fonts; controls never shrink below a 48dp touch target. */
internal object FloatingChromePolicy {
    fun headerHeight(fontScale: Float, titleSp: Float) =
        maxOf(48, ceil(titleSp * fontScale.coerceAtLeast(1f) * 1.35f + 8).toInt())
    fun controlsHeight(fontScale: Float, titleSp: Float, toolbar: Boolean) =
        headerHeight(fontScale, titleSp) + if (toolbar) 48 else 0
}
