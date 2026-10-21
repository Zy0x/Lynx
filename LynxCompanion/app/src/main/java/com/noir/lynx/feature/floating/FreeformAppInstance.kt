package com.noir.lynx.feature.floating

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * FreeformAppInstance — Represents an active real application running in floating/freeform mode.
 * Each instance tracks the underlying Android OS Task ID and its on-screen bounding rectangle.
 */
data class FreeformAppInstance(
    val taskId: Int,
    val packageName: String,
    val component: String,
    val label: String,
    val icon: Bitmap,
    val bounds: Rect,
    val isMinimized: Boolean = false,
    val isMaximized: Boolean = false,
    val savedBounds: Rect = bounds
)
