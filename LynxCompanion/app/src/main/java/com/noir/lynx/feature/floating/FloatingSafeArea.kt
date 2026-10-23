package com.noir.lynx.feature.floating

import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

internal fun floatingSafeArea(wm: WindowManager, view: View): Rect {
    if (Build.VERSION.SDK_INT >= 30) {
        val m = wm.currentWindowMetrics
        val i = m.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        return Rect(i.left, i.top, m.bounds.width() - i.right, m.bounds.height() - i.bottom)
    }
    val dm = android.util.DisplayMetrics()
    @Suppress("DEPRECATION")
    wm.defaultDisplay.getRealMetrics(dm)
    @Suppress("DEPRECATION")
    val i = view.rootWindowInsets
    @Suppress("DEPRECATION")
    return Rect(i?.stableInsetLeft ?: 0, i?.stableInsetTop ?: 0,
        dm.widthPixels - (i?.stableInsetRight ?: 0), dm.heightPixels - (i?.stableInsetBottom ?: 0))
}
