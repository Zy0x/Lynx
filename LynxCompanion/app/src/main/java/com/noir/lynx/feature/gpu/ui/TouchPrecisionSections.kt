package com.noir.lynx.feature.gpu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxTouchPrecisionRepository
import com.noir.lynx.data.TouchPrecisionConfig
import com.noir.lynx.designsystem.controls.LynxSlider
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.theme.AccentBlue
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.AccentOrange
import com.noir.lynx.designsystem.theme.BgElevated
import com.noir.lynx.designsystem.theme.BorderSubtle
import com.noir.lynx.designsystem.theme.TextPrimary
import com.noir.lynx.designsystem.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Edge Back Gesture and hardware bezel edge lock calibration controls.
 */
@Composable
fun EdgeGestureGuardSection(
    config: TouchPrecisionConfig,
    nvtPresent: Boolean,
    onConfigChange: (TouchPrecisionConfig) -> Unit,
    onCommit: (TouchPrecisionConfig) -> Unit,
    onCommitLatest: () -> Unit,
) {
    TouchSubSectionHeader(title = stringResource(R.string.touch_precision_sec_edge_guard))

    Text(
        text = stringResource(R.string.touch_precision_edge_overlay_label),
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
    Spacer(modifier = Modifier.height(6.dp))

    val overlays = listOf(
        TouchPrecisionConfig.OVERLAY_NARROW to stringResource(R.string.touch_precision_overlay_narrow),
        TouchPrecisionConfig.OVERLAY_DEFAULT to stringResource(R.string.touch_precision_overlay_default),
        TouchPrecisionConfig.OVERLAY_WIDE to stringResource(R.string.touch_precision_overlay_wide),
        TouchPrecisionConfig.OVERLAY_EXTRA_WIDE to stringResource(R.string.touch_precision_overlay_extra_wide),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        overlays.forEach { (key, label) ->
            val selected = config.backGestureOverlay == key
            TouchChoiceChip(
                text = label,
                selected = selected,
                accentColor = AccentCyan,
                onClick = {
                    val updated = config.copy(backGestureOverlay = key)
                    onConfigChange(updated)
                    onCommit(updated)
                },
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    LynxSwitch(
        label = stringResource(R.string.touch_precision_edge_link_sides),
        subLabel = stringResource(R.string.touch_precision_edge_link_sides_sub),
        checked = config.backGestureLinkSides,
        onCheckedChange = { linked ->
            val updated = if (linked) {
                config.copy(
                    backGestureLinkSides = true,
                    backGestureScaleRight = config.backGestureScaleLeft,
                )
            } else {
                config.copy(backGestureLinkSides = false)
            }
            onConfigChange(updated)
            onCommit(updated)
        },
    )

    val labelDisabled = stringResource(R.string.touch_precision_edge_scale_disabled)
    val scaleTemplate = stringResource(R.string.touch_precision_edge_scale_val, "__VAL__")
    if (config.backGestureLinkSides) {
        LynxSlider(
            label = stringResource(R.string.touch_precision_edge_scale_both),
            value = config.backGestureScaleLeft,
            valueRange = 0.0f..2.0f,
            displayValue = formatEdgeScale(config.backGestureScaleLeft, labelDisabled, scaleTemplate),
            formatDisplay = { formatEdgeScale(it, labelDisabled, scaleTemplate) },
            onValueChange = { raw ->
                val v = LynxTouchPrecisionRepository.roundScale2(raw)
                onConfigChange(config.copy(backGestureScaleLeft = v, backGestureScaleRight = v))
            },
            onValueChangeFinished = onCommitLatest,
        )
    } else {
        LynxSlider(
            label = stringResource(R.string.touch_precision_edge_scale_left),
            value = config.backGestureScaleLeft,
            valueRange = 0.0f..2.0f,
            displayValue = formatEdgeScale(config.backGestureScaleLeft, labelDisabled, scaleTemplate),
            formatDisplay = { formatEdgeScale(it, labelDisabled, scaleTemplate) },
            onValueChange = { raw ->
                val v = LynxTouchPrecisionRepository.roundScale2(raw)
                onConfigChange(config.copy(backGestureScaleLeft = v))
            },
            onValueChangeFinished = onCommitLatest,
        )
        LynxSlider(
            label = stringResource(R.string.touch_precision_edge_scale_right),
            value = config.backGestureScaleRight,
            valueRange = 0.0f..2.0f,
            displayValue = formatEdgeScale(config.backGestureScaleRight, labelDisabled, scaleTemplate),
            formatDisplay = { formatEdgeScale(it, labelDisabled, scaleTemplate) },
            onValueChange = { raw ->
                val v = LynxTouchPrecisionRepository.roundScale2(raw)
                onConfigChange(config.copy(backGestureScaleRight = v))
            },
            onValueChangeFinished = onCommitLatest,
        )
    }

    LynxSwitch(
        label = stringResource(R.string.touch_precision_ml_guard_enable),
        subLabel = stringResource(R.string.touch_precision_ml_guard_sub),
        checked = config.backGestureMlEnabled,
        onCheckedChange = { enabled ->
            val updated = config.copy(backGestureMlEnabled = enabled)
            onConfigChange(updated)
            onCommit(updated)
        },
    )

    if (config.backGestureMlEnabled) {
        LynxSlider(
            label = stringResource(R.string.touch_precision_ml_threshold_label),
            value = config.backGestureMlThreshold,
            valueRange = 0.50f..0.99f,
            displayValue = String.format(Locale.US, "%.2f", config.backGestureMlThreshold),
            formatDisplay = { String.format(Locale.US, "%.2f", LynxTouchPrecisionRepository.roundThreshold2(it)) },
            accentColor = AccentBlue,
            onValueChange = { raw ->
                onConfigChange(config.copy(backGestureMlThreshold = LynxTouchPrecisionRepository.roundThreshold2(raw)))
            },
            onValueChangeFinished = onCommitLatest,
        )
    }

    if (nvtPresent) {
        LynxSlider(
            label = stringResource(R.string.touch_precision_nvt_edge_guard_label),
            value = config.nvtEdgeGuardPx.toFloat(),
            valueRange = 0f..240f,
            displayValue = stringResource(R.string.touch_precision_nvt_edge_guard_val, config.nvtEdgeGuardPx),
            accentColor = AccentOrange,
            onValueChange = { raw ->
                onConfigChange(config.copy(nvtEdgeGuardPx = raw.roundToInt().coerceIn(0, 240)))
            },
            onValueChangeFinished = onCommitLatest,
        )
    }
}

/**
 * Novatek NT36672C micro-displacement gain, SPI bus clock presets, and IC filter controls.
 */
@Composable
fun NovatekMicroSensitivitySection(
    config: TouchPrecisionConfig,
    onConfigChange: (TouchPrecisionConfig) -> Unit,
    onCommit: (TouchPrecisionConfig) -> Unit,
    onCommitLatest: () -> Unit,
) {
    TouchSubSectionHeader(title = stringResource(R.string.touch_precision_sec_novatek_micro))

    val gainDisplay = if (config.nvtMicroGain == 0) {
        stringResource(R.string.touch_precision_nvt_micro_gain_off_val)
    } else {
        stringResource(
            R.string.touch_precision_nvt_micro_gain_val,
            config.nvtMicroGain,
            TouchPrecisionConfig.gainMultiplierText(config.nvtMicroGain),
            TouchPrecisionConfig.maxDriftPx(config.nvtMicroGain),
        )
    }

    LynxSlider(
        label = stringResource(R.string.touch_precision_nvt_micro_gain_label),
        value = config.nvtMicroGain.toFloat(),
        valueRange = 0f..12f,
        steps = 11,
        displayValue = gainDisplay,
        accentColor = if (config.nvtMicroGain > 4) AccentOrange else AccentCyan,
        onValueChange = { raw ->
            onConfigChange(config.copy(nvtMicroGain = raw.roundToInt().coerceIn(0, 12)))
        },
        onValueChangeFinished = onCommitLatest,
    )

    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.touch_precision_nvt_spi_label),
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
    Spacer(modifier = Modifier.height(6.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TouchPrecisionConfig.SPI_PRESETS_HZ.forEach { hz ->
            val mhzText = if (hz % 1_000_000 == 0) {
                (hz / 1_000_000).toString()
            } else {
                String.format(Locale.US, "%.1f", hz / 1_000_000f)
            }
            val selected = config.nvtSpiSpeedHz == hz
            TouchChoiceChip(
                text = stringResource(R.string.touch_precision_nvt_spi_mhz, mhzText),
                selected = selected,
                accentColor = if (hz > 15_000_000) AccentOrange else AccentCyan,
                onClick = {
                    val updated = config.copy(nvtSpiSpeedHz = hz)
                    onConfigChange(updated)
                    onCommit(updated)
                },
            )
        }
    }

    if (config.nvtSpiSpeedHz > 15_000_000) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = AccentOrange.copy(alpha = 0.12f),
            border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.touch_precision_nvt_spi_warning),
                color = AccentOrange,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    LynxSwitch(
        label = stringResource(R.string.touch_precision_nvt_game_mode),
        subLabel = stringResource(R.string.touch_precision_nvt_game_mode_sub),
        checked = config.nvtGameMode,
        onCheckedChange = { checked ->
            val updated = config.copy(nvtGameMode = checked)
            onConfigChange(updated)
            onCommit(updated)
        },
    )
    LynxSwitch(
        label = stringResource(R.string.touch_precision_nvt_hop_fix),
        subLabel = stringResource(R.string.touch_precision_nvt_hop_fix_sub),
        checked = config.nvtHopFix,
        onCheckedChange = { checked ->
            val updated = config.copy(nvtHopFix = checked)
            onConfigChange(updated)
            onCommit(updated)
        },
    )

    LynxSlider(
        label = stringResource(R.string.touch_precision_nvt_jitter_label),
        value = config.nvtJitterLevel.toFloat(),
        valueRange = 0f..3f,
        steps = 2,
        displayValue = if (config.nvtJitterLevel == 0) {
            stringResource(R.string.touch_precision_nvt_filter_level_off)
        } else {
            stringResource(R.string.touch_precision_nvt_filter_level_val, config.nvtJitterLevel)
        },
        onValueChange = { raw ->
            onConfigChange(config.copy(nvtJitterLevel = raw.roundToInt().coerceIn(0, 3)))
        },
        onValueChangeFinished = onCommitLatest,
    )
    LynxSlider(
        label = stringResource(R.string.touch_precision_nvt_smooth_label),
        value = config.nvtSmoothLevel.toFloat(),
        valueRange = 0f..3f,
        steps = 2,
        displayValue = if (config.nvtSmoothLevel == 0) {
            stringResource(R.string.touch_precision_nvt_filter_level_off)
        } else {
            stringResource(R.string.touch_precision_nvt_filter_level_val, config.nvtSmoothLevel)
        },
        onValueChange = { raw ->
            onConfigChange(config.copy(nvtSmoothLevel = raw.roundToInt().coerceIn(0, 3)))
        },
        onValueChangeFinished = onCommitLatest,
    )
}

/**
 * Android framework input pipeline and MediaTek kernel TouchBoost (ktch) controls.
 */
@Composable
fun InputPipelineKtchSection(
    config: TouchPrecisionConfig,
    ktchPresent: Boolean,
    resamplingSupported: Boolean = false,
    touchIrqSupported: Boolean = false,
    onConfigChange: (TouchPrecisionConfig) -> Unit,
    onCommit: (TouchPrecisionConfig) -> Unit,
    onCommitLatest: () -> Unit,
) {
    TouchSubSectionHeader(title = stringResource(R.string.touch_precision_sec_pipeline_ktch))

    if (resamplingSupported) {
        LynxSwitch(
            label = stringResource(R.string.touch_precision_resampling_label),
            subLabel = stringResource(R.string.touch_precision_resampling_sub),
            checked = config.inputResampling,
            onCheckedChange = { checked ->
                val updated = config.copy(inputResampling = checked)
                onConfigChange(updated)
                onCommit(updated)
            },
        )
    }
    if (touchIrqSupported) {
        LynxSwitch(
            label = stringResource(R.string.touch_precision_irq_affinity_label),
            subLabel = stringResource(R.string.touch_precision_irq_affinity_sub),
            checked = config.touchIrqBigCores,
            onCheckedChange = { checked ->
                val updated = config.copy(touchIrqBigCores = checked)
                onConfigChange(updated)
                onCommit(updated)
            },
        )
    }

    if (ktchPresent) {
        LynxSwitch(
            label = stringResource(R.string.touch_precision_ktch_enable),
            subLabel = stringResource(R.string.touch_precision_ktch_enable_sub),
            checked = config.ktchEnabled,
            onCheckedChange = { checked ->
                val updated = config.copy(ktchEnabled = checked)
                onConfigChange(updated)
                onCommit(updated)
            },
        )
        if (config.ktchEnabled) {
            LynxSlider(
                label = stringResource(R.string.touch_precision_ktch_duration_label),
                value = config.ktchDurationMs.toFloat(),
                valueRange = 0f..2000f,
                displayValue = stringResource(R.string.touch_precision_ktch_duration_val, config.ktchDurationMs),
                onValueChange = { raw ->
                    val stepped = ((raw / 20f).roundToInt() * 20).coerceIn(0, 2000)
                    onConfigChange(config.copy(ktchDurationMs = stepped))
                },
                onValueChangeFinished = onCommitLatest,
            )
            LynxSlider(
                label = stringResource(R.string.touch_precision_ktch_eas_label),
                value = config.ktchEasBoost.toFloat(),
                valueRange = 0f..100f,
                displayValue = stringResource(R.string.touch_precision_ktch_eas_val, config.ktchEasBoost),
                accentColor = AccentBlue,
                onValueChange = { raw ->
                    onConfigChange(config.copy(ktchEasBoost = raw.roundToInt().coerceIn(0, 100)))
                },
                onValueChangeFinished = onCommitLatest,
            )
        }
    }
}

@Composable
private fun TouchSubSectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp)) {
        Text(
            text = title.uppercase(Locale.US),
            color = AccentCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
        )
    }
}

@Composable
private fun TouchChoiceChip(
    text: String,
    selected: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accentColor.copy(alpha = 0.16f) else BgElevated,
        border = BorderStroke(
            width = if (selected) 1.dp else 0.8.dp,
            color = if (selected) accentColor.copy(alpha = 0.6f) else BorderSubtle,
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                color = if (selected) accentColor else TextPrimary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

private fun formatEdgeScale(scale: Float, disabledText: String, scaleTemplate: String): String {
    if (scale <= 0.005f) return disabledText
    return scaleTemplate.replace("__VAL__", String.format(Locale.US, "%.2f", scale))
}
