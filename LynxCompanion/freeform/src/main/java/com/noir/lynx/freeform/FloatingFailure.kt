package com.noir.lynx.freeform

import androidx.annotation.StringRes
import com.noir.lynx.freeform.R

/** Stable protocol codes are translated at the UI boundary, never raw vendor exceptions. */
internal object FloatingFailure {
    @StringRes fun message(code: String?): Int = when (code) {
        "android_version" -> R.string.lynx_freeform_error_android_version
        "overlay_permission" -> R.string.lynx_freeform_error_overlay_permission
        "window_limit" -> R.string.lynx_freeform_error_window_limit
        "permission_denied" -> R.string.lynx_freeform_error_permission
        "display_creation" -> R.string.lynx_freeform_error_display
        "activity_launch" -> R.string.lynx_freeform_error_launch
        "task_verification", "framework_api" -> R.string.lynx_freeform_error_framework
        "frame_timeout" -> R.string.lynx_freeform_error_frame
        "input_rejected" -> R.string.lynx_freeform_error_input
        "resize_rejected" -> R.string.lynx_freeform_error_resize
        else -> R.string.lynx_freeform_root_unavailable
    }
}
