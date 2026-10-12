package com.noir.lynx.feature.cpu.ui.cluster

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.noir.lynx.R
import java.util.Locale

/**
 * Governor Tunable Human-Friendly Metadata & Formatters.
 */
data class TunableMetaInfo(
    val title: String,
    val description: String,
    val hint: String?,
    val icon: ImageVector,
    val iconColor: Color
)

@Composable
fun formatTunableDisplay(key: String, value: String, unit: String): String {
    val cleanVal = value.trim()
    val num = cleanVal.toLongOrNull()
    return if (num != null && (key.endsWith("_us") || unit == "µs")) {
        when {
            num == 0L -> stringResource(R.string.tunable_instant)
            num >= 1000L && num % 1000L == 0L -> "${num / 1000} ms"
            num >= 1000L -> String.format(Locale.US, "%.1f ms", num / 1000f)
            else -> "$num µs"
        }
    } else if (cleanVal.isNotEmpty()) {
        "$cleanVal $unit".trim()
    } else {
        "--"
    }
}

@Composable
fun getTunableMeta(
    key: String,
    fallbackName: String,
    clusterAccent: Color,
    isLittle: Boolean = true
): TunableMetaInfo {
    return when (key.lowercase()) {
        "up_rate_limit_us", "rate_limit_us" -> TunableMetaInfo(
            title = stringResource(R.string.tunable_ramp_up_title),
            description = stringResource(R.string.tunable_ramp_up_desc),
            hint = if (isLittle) {
                stringResource(R.string.tunable_ramp_up_hint_little)
            } else {
                stringResource(R.string.tunable_ramp_up_hint_big)
            },
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            iconColor = Color(0xFF00E5FF)
        )
        "down_rate_limit_us" -> TunableMetaInfo(
            title = stringResource(R.string.tunable_ramp_down_title),
            description = stringResource(R.string.tunable_ramp_down_desc),
            hint = if (isLittle) {
                stringResource(R.string.tunable_ramp_down_hint_little)
            } else {
                stringResource(R.string.tunable_ramp_down_hint_big)
            },
            icon = Icons.AutoMirrored.Filled.TrendingDown,
            iconColor = Color(0xFFFF9100)
        )
        "iowait_boost_enable" -> TunableMetaInfo(
            title = stringResource(R.string.tunable_iowait_title),
            description = stringResource(R.string.tunable_iowait_desc),
            hint = stringResource(R.string.tunable_iowait_hint),
            icon = Icons.Default.Bolt,
            iconColor = Color(0xFFFFD600)
        )
        "hispeed_freq" -> TunableMetaInfo(
            title = stringResource(R.string.tunable_hispeed_freq_title),
            description = stringResource(R.string.tunable_hispeed_freq_desc),
            hint = stringResource(R.string.tunable_hispeed_freq_hint),
            icon = Icons.Default.Speed,
            iconColor = clusterAccent
        )
        "go_hispeed_load", "up_threshold" -> TunableMetaInfo(
            title = stringResource(R.string.tunable_up_threshold_title),
            description = stringResource(R.string.tunable_up_threshold_desc),
            hint = stringResource(R.string.tunable_up_threshold_hint),
            icon = Icons.Default.Tune,
            iconColor = clusterAccent
        )
        "down_threshold" -> TunableMetaInfo(
            title = stringResource(R.string.tunable_down_threshold_title),
            description = stringResource(R.string.tunable_down_threshold_desc),
            hint = stringResource(R.string.tunable_down_threshold_hint),
            icon = Icons.Default.Tune,
            iconColor = clusterAccent
        )
        "sampling_rate", "timer_rate" -> TunableMetaInfo(
            title = stringResource(R.string.tunable_sampling_rate_title),
            description = stringResource(R.string.tunable_sampling_rate_desc),
            hint = stringResource(R.string.tunable_sampling_rate_hint),
            icon = Icons.Default.Timer,
            iconColor = clusterAccent
        )
        "sampling_down_factor" -> TunableMetaInfo(
            title = stringResource(R.string.tunable_sampling_down_factor_title),
            description = stringResource(R.string.tunable_sampling_down_factor_desc),
            hint = stringResource(R.string.tunable_sampling_down_factor_hint),
            icon = Icons.Default.FastForward,
            iconColor = clusterAccent
        )
        "min_sample_time" -> TunableMetaInfo(
            title = stringResource(R.string.tunable_min_sample_time_title),
            description = stringResource(R.string.tunable_min_sample_time_desc),
            hint = stringResource(R.string.tunable_min_sample_time_hint),
            icon = Icons.Default.Timer,
            iconColor = clusterAccent
        )
        else -> TunableMetaInfo(
            title = fallbackName,
            description = stringResource(R.string.tunable_fallback_desc, key),
            hint = null,
            icon = Icons.Default.Tune,
            iconColor = clusterAccent
        )
    }
}
