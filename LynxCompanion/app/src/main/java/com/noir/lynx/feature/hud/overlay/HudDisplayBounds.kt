package com.noir.lynx.feature.hud.overlay

import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.WindowManager

internal data class HudPanelBounds(val left: Int, val rightInset: Int, val top: Int, val width: Int, val height: Int)

@Suppress("DEPRECATION")
internal fun readHudPanelBounds(windows: WindowManager, root: WindowInsets?): HudPanelBounds {
    if (Build.VERSION.SDK_INT >= 30) {
        val metrics = windows.currentWindowMetrics
        val inset = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        return HudPanelBounds(inset.left, inset.right, inset.top,
            metrics.bounds.width() - inset.left - inset.right, metrics.bounds.height() - inset.top - inset.bottom)
    }
    val dm = DisplayMetrics().also { windows.defaultDisplay.getRealMetrics(it) }
    val left = root?.stableInsetLeft ?: 0; val right = root?.stableInsetRight ?: 0
    val top = root?.stableInsetTop ?: 0; val bottom = root?.stableInsetBottom ?: 0
    return HudPanelBounds(left, right, top, dm.widthPixels - left - right, dm.heightPixels - top - bottom)
}

@Suppress("DEPRECATION")
internal fun readHudBadgeBounds(windows: WindowManager, root: WindowInsets?): HudBadgeBounds {
    val width: Int; val height: Int
    if (Build.VERSION.SDK_INT >= 30) {
        val metrics = windows.currentWindowMetrics
        width = metrics.bounds.width(); height = metrics.bounds.height()
    } else {
        val dm = DisplayMetrics().also { windows.defaultDisplay.getRealMetrics(it) }
        width = dm.widthPixels; height = dm.heightPixels
    }
    return HudBadgeBounds(HudScreenRect(0, 0, width, height), emptyList())
}
