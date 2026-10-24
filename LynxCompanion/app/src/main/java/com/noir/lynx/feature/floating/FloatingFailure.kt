package com.noir.lynx.feature.floating

import androidx.annotation.StringRes
import com.noir.lynx.R

/** Stable protocol codes are translated at the UI boundary, never raw vendor exceptions. */
internal object FloatingFailure {
    @StringRes fun message(code: String?): Int = when (code) {
        "permission_denied" -> R.string.floating_error_permission
        "display_creation" -> R.string.floating_error_display
        "activity_launch" -> R.string.floating_error_launch
        "task_verification", "framework_api" -> R.string.floating_error_framework
        "frame_timeout" -> R.string.floating_error_frame
        "input_rejected" -> R.string.floating_error_input
        "resize_rejected" -> R.string.floating_error_resize
        else -> R.string.floating_root_unavailable
    }
}
