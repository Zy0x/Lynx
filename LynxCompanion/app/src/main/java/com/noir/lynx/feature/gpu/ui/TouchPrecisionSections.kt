package com.noir.lynx.feature.gpu.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxTouchPrecisionRepository
import com.noir.lynx.data.TouchPrecisionConfig
import com.noir.lynx.designsystem.controls.LynxNumericPresetRow
import com.noir.lynx.designsystem.controls.LynxSlider
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.theme.AccentBlue
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.AccentOrange
import com.noir.lynx.designsystem.theme.BgElevated
import com.noir.lynx.designsystem.theme.BorderGlass
import com.noir.lynx.designsystem.theme.BorderSubtle
import com.noir.lynx.designsystem.theme.TextPrimary
import com.noir.lynx.designsystem.theme.TextSecondary
import java.util.Locale

/**
 * Novatek NT36672C micro-displacement gain, bezel edge lock, SPI bus clock, and IC filter controls.
 */
@Composable
fun NovatekMicroSensitivitySection(
    config: TouchPrecisionConfig,
    onConfigChange: (TouchPrecisionConfig) -> Unit,
    onCommit: (TouchPrecisionConfig) -> Unit,
) {
    TouchSubSectionHeader(title = stringResource(R.string.touch_precision_sec_novatek_micro))

    val gainDisplay = if (config.nvtMicroGain == 0) {
        stringResource(R.string.touch_precision_nvt_micro_gain_off_val)
    } else {
        stringResource(
            R.string.touch_precision_nvt_micro_gain_val,
            config.nvtMicroGain,
            TouchPrecisionConfig.gainMultiplierText(config.nvtMicroGain),
        )
    }

    // Quick 1-tap Micro-Sense preset chips (0 / 1 / 2 / 4) fitting evenly with zero clipping
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            "1.0x Raw" to 0,
            "1.5x Arise" to 1,
            "2.0x Pro" to 2,
            "3.0x Max" to 4,
        ).forEach { (label, level) ->
            TouchChoiceChip(
                text = label,
                selected = config.nvtMicroGain == level,
                accentColor = if (level > 4) AccentOrange else AccentCyan,
                modifier = Modifier.weight(1f),
                onClick = {
                    val updated = config.copy(nvtMicroGain = level)
                    onConfigChange(updated)
                    onCommit(updated)
                },
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    LynxNumericPresetRow(
        label = stringResource(R.string.touch_precision_nvt_micro_gain_label),
        subtitle = stringResource(R.string.touch_precision_nvt_micro_gain_sub),
        value = config.nvtMicroGain,
        displayValue = gainDisplay,
        min = 0,
        max = 12,
        unit = "Lv",
        dialogTitle = stringResource(R.string.touch_precision_nvt_micro_gain_label),
        dialogHint = stringResource(R.string.touch_precision_nvt_micro_gain_sub),
        presets = TouchPrecisionConfig.GAIN_QUICK_PRESETS,
        accentColor = if (config.nvtMicroGain > 4) AccentOrange else AccentCyan,
        onCommitValue = { level ->
            val updated = config.copy(nvtMicroGain = level.coerceIn(0, 12))
            onConfigChange(updated)
            onCommit(updated)
        },
    )

    Spacer(modifier = Modifier.height(8.dp))

    LynxNumericPresetRow(
        label = stringResource(R.string.touch_precision_nvt_edge_guard_label),
        subtitle = stringResource(R.string.touch_precision_nvt_edge_guard_sub),
        value = config.nvtEdgeGuardPx,
        displayValue = stringResource(R.string.touch_precision_nvt_edge_guard_val, config.nvtEdgeGuardPx),
        min = 0,
        max = 240,
        unit = "px",
        dialogTitle = stringResource(R.string.touch_precision_nvt_edge_guard_label),
        presets = TouchPrecisionConfig.EDGE_GUARD_PRESETS,
        accentColor = AccentOrange,
        onCommitValue = { px ->
            val updated = config.copy(nvtEdgeGuardPx = px.coerceIn(0, 240))
            onConfigChange(updated)
            onCommit(updated)
        },
    )

    Spacer(modifier = Modifier.height(10.dp))
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
            TouchChoiceChip(
                text = stringResource(R.string.touch_precision_nvt_spi_mhz, mhzText),
                selected = config.nvtSpiSpeedHz == hz,
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
    HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 8.dp))
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
    HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 8.dp))

    IcLevelSelectorRow(
        title = stringResource(R.string.touch_precision_nvt_jitter_label),
        selectedLevel = config.nvtJitterLevel,
        accentColor = AccentCyan,
        onSelect = { lvl ->
            val updated = config.copy(nvtJitterLevel = lvl)
            onConfigChange(updated)
            onCommit(updated)
        },
    )

    Spacer(modifier = Modifier.height(6.dp))

    IcLevelSelectorRow(
        title = stringResource(R.string.touch_precision_nvt_smooth_label),
        selectedLevel = config.nvtSmoothLevel,
        accentColor = AccentBlue,
        onSelect = { lvl ->
            val updated = config.copy(nvtSmoothLevel = lvl)
            onConfigChange(updated)
            onCommit(updated)
        },
    )
}

@Composable
private fun IcLevelSelectorRow(
    title: String,
    selectedLevel: Int,
    accentColor: Color,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (0..3).forEach { lvl ->
                val label = if (lvl == 0) "0x00 Off" else "0x0$lvl"
                TouchChoiceChip(
                    text = label,
                    selected = selectedLevel == lvl,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(lvl) },
                )
            }
        }
    }
}

/**
 * Android framework input pipeline, kernel big-core touch IRQ affinity, and MediaTek KTCH controls.
 */
@Composable
fun InputPipelineKtchSection(
    config: TouchPrecisionConfig,
    ktchPresent: Boolean,
    resamplingSupported: Boolean = false,
    touchIrqSupported: Boolean = false,
    onConfigChange: (TouchPrecisionConfig) -> Unit,
    onCommit: (TouchPrecisionConfig) -> Unit,
) {
    TouchSubSectionHeader(title = stringResource(R.string.touch_precision_sec_pipeline_ktch))

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
        if (resamplingSupported || ktchPresent) {
            HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 8.dp))
        }
    }

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
        if (ktchPresent) {
            HorizontalDivider(color = BorderGlass.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 8.dp))
        }
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
        AnimatedVisibility(visible = config.ktchEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LynxNumericPresetRow(
                    label = stringResource(R.string.touch_precision_ktch_duration_label),
                    value = config.ktchDurationMs,
                    displayValue = stringResource(R.string.touch_precision_ktch_duration_val, config.ktchDurationMs),
                    min = 0,
                    max = 2000,
                    unit = "ms",
                    dialogTitle = stringResource(R.string.touch_precision_ktch_duration_label),
                    presets = listOf("120 ms" to 120, "280 ms (Default)" to 280, "500 ms" to 500, "800 ms" to 800),
                    accentColor = AccentCyan,
                    onCommitValue = { ms ->
                        val updated = config.copy(ktchDurationMs = ms.coerceIn(0, 2000))
                        onConfigChange(updated)
                        onCommit(updated)
                    },
                )
                LynxNumericPresetRow(
                    label = stringResource(R.string.touch_precision_ktch_eas_label),
                    value = config.ktchEasBoost,
                    displayValue = stringResource(R.string.touch_precision_ktch_eas_val, config.ktchEasBoost),
                    min = 0,
                    max = 100,
                    unit = "%",
                    dialogTitle = stringResource(R.string.touch_precision_ktch_eas_label),
                    presets = listOf("40%" to 40, "60%" to 60, "80% (Default)" to 80, "100%" to 100),
                    accentColor = AccentBlue,
                    onCommitValue = { eas ->
                        val updated = config.copy(ktchEasBoost = eas.coerceIn(0, 100))
                        onConfigChange(updated)
                        onCommit(updated)
                    },
                )
            }
        }
    }
}

/**
 * Opt-in SystemUI Edge Back Gesture & ML anti-accidental swipe guard.
 */
@Composable
fun EdgeGestureGuardSection(
    config: TouchPrecisionConfig,
    onConfigChange: (TouchPrecisionConfig) -> Unit,
    onCommit: (TouchPrecisionConfig) -> Unit,
    onCommitLatest: () -> Unit,
) {
    TouchSubSectionHeader(title = stringResource(R.string.touch_precision_sec_edge_guard))

    LynxSwitch(
        label = stringResource(R.string.touch_precision_edge_opt_in_label),
        subLabel = stringResource(R.string.touch_precision_edge_opt_in_sub),
        checked = config.edgeGestureCustomEnabled,
        onCheckedChange = { enabled ->
            val updated = config.copy(edgeGestureCustomEnabled = enabled)
            onConfigChange(updated)
            onCommit(updated)
        },
    )

    AnimatedVisibility(visible = config.edgeGestureCustomEnabled) {
        Column {
            Spacer(modifier = Modifier.height(6.dp))
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
                    TouchChoiceChip(
                        text = label,
                        selected = config.backGestureOverlay == key,
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
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accentColor.copy(alpha = 0.16f) else BgElevated,
        border = BorderStroke(
            width = if (selected) 1.dp else 0.8.dp,
            color = if (selected) accentColor.copy(alpha = 0.6f) else BorderSubtle,
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
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
