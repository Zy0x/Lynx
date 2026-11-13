package com.noir.lynx.feature.gpu.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxPanelTuningRepository
import com.noir.lynx.data.PanelTuningConfig
import com.noir.lynx.data.PanelTuningHardwareSupport
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.controls.LynxActionButton
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.AccentGreen
import com.noir.lynx.designsystem.theme.AccentOrange
import com.noir.lynx.designsystem.theme.BgElevated
import com.noir.lynx.designsystem.theme.BorderGlass
import com.noir.lynx.designsystem.theme.BorderSubtle
import com.noir.lynx.designsystem.theme.TextPrimary
import com.noir.lynx.designsystem.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Hardware control card for kernel NT36672C True Panel CABC (DCS 0x55) and
 * MediaTek Display ESD Watchdog polling interval (/proc/mtk_esd_period_ms).
 * Automatically hidden when neither kernel hook is exposed by the active kernel.
 */
@Composable
fun PanelTuningCard(
    context: Context,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(LynxPanelTuningRepository.loadSavedConfig(context)) }
    var support by remember {
        mutableStateOf(LynxPanelTuningRepository.cachedHardwareSupport() ?: PanelTuningHardwareSupport())
    }
    var isBusy by remember { mutableStateOf(false) }
    var feedbackResId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        support = LynxPanelTuningRepository.probeHardwareSupport()
    }

    if (!support.probed || !support.hasAnySupport) {
        return
    }

    val applyConfigNow: (PanelTuningConfig, Int) -> Unit = { target, okRes ->
        val clean = target.sanitized()
        config = clean
        feedbackResId = null
        scope.launch {
            isBusy = true
            val result = LynxPanelTuningRepository.applyConfig(
                context = context,
                config = clean,
                persistAsDefault = true,
            )
            support = result
            isBusy = false
            feedbackResId = if (result.lastWriteOk) okRes else R.string.panel_hw_status_write_warn
        }
    }

    LynxCard(
        title = stringResource(R.string.panel_hw_card_title),
        subtitle = stringResource(R.string.panel_hw_card_subtitle),
        icon = Icons.Default.BrightnessLow,
        accentColor = AccentCyan,
        modifier = modifier,
        action = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentCyan.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.4f)),
                ) {
                    Text(
                        text = stringResource(
                            if (support.cabcSupported && support.esdSupported) {
                                R.string.panel_hw_badge_full
                            } else if (support.cabcSupported) {
                                R.string.panel_hw_badge_cabc_only
                            } else {
                                R.string.panel_hw_badge_esd_only
                            },
                        ),
                        color = AccentCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch { support = LynxPanelTuningRepository.probeHardwareSupport() }
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.panel_hw_refresh_cd),
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
    ) {
        PanelTuningTelemetryBanner(support = support)

        if (support.cabcSupported) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.panel_hw_cabc_section_title),
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.panel_hw_cabc_section_sub),
                color = TextSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))

            val cabcOptions = listOf(
                0 to R.string.panel_hw_cabc_off,
                1 to R.string.panel_hw_cabc_ui,
                2 to R.string.panel_hw_cabc_still,
                3 to R.string.panel_hw_cabc_moving,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                cabcOptions.forEach { (mode, labelRes) ->
                    val selected = config.cabcMode == mode
                    Surface(
                        onClick = {
                            feedbackResId = null
                            config = config.copy(cabcMode = mode)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) AccentCyan.copy(alpha = 0.18f) else BgElevated,
                        border = BorderStroke(1.dp, if (selected) AccentCyan else BorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                color = if (selected) AccentCyan else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        if (support.esdSupported) {
            if (support.cabcSupported) {
                HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                Spacer(Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.panel_hw_esd_section_title),
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = formatEsdPeriodLabel(config.esdPeriodMs),
                    color = if (config.esdPeriodMs == 0) AccentOrange else AccentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.panel_hw_esd_section_sub),
                color = TextSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))

            val esdPresets = listOf(
                0 to R.string.panel_hw_esd_preset_pause,
                5_000 to R.string.panel_hw_esd_preset_5s,
                15_000 to R.string.panel_hw_esd_preset_15s,
                30_000 to R.string.panel_hw_esd_preset_30s,
                60_000 to R.string.panel_hw_esd_preset_60s,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                esdPresets.forEach { (periodMs, labelRes) ->
                    val selected = config.esdPeriodMs == periodMs
                    val chipColor = if (periodMs == 0) AccentOrange else AccentCyan
                    Surface(
                        onClick = {
                            feedbackResId = null
                            config = config.copy(esdPeriodMs = periodMs)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) chipColor.copy(alpha = 0.18f) else BgElevated,
                        border = BorderStroke(1.dp, if (selected) chipColor else BorderSubtle),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                color = if (selected) chipColor else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            val isPaused = config.esdPeriodMs == 0
            val sliderSec = if (isPaused) {
                (PanelTuningConfig.DEFAULT_ESD_PERIOD_MS / 1000).toFloat()
            } else {
                (config.esdPeriodMs / 1000).coerceIn(2, 60).toFloat()
            }
            Slider(
                value = sliderSec,
                onValueChange = { rawSec ->
                    feedbackResId = null
                    val clampedMs = (rawSec.roundToInt().coerceIn(2, 60)) * 1000
                    config = config.copy(esdPeriodMs = clampedMs)
                },
                enabled = !isPaused,
                valueRange = 2f..60f,
                steps = 57,
                colors = SliderDefaults.colors(
                    thumbColor = AccentCyan,
                    activeTrackColor = AccentCyan,
                    inactiveTrackColor = BgElevated,
                ),
            )
        }

        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 10.dp))

        LynxSwitch(
            label = stringResource(R.string.panel_hw_boot_persist),
            subLabel = stringResource(R.string.panel_hw_boot_persist_sub),
            checked = config.applyOnBoot,
            onCheckedChange = { checked ->
                val updated = config.copy(applyOnBoot = checked)
                config = updated
                LynxPanelTuningRepository.saveConfigPrefs(context, updated)
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    LynxPanelTuningRepository.syncConfigJson(updated)
                }
            },
        )

        feedbackResId?.let { resId ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(resId),
                color = if (resId == R.string.panel_hw_status_write_warn) AccentOrange else AccentGreen,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LynxActionButton(
                text = stringResource(if (isBusy) R.string.panel_hw_applying_btn else R.string.panel_hw_apply_btn),
                icon = Icons.Default.Check,
                isLoading = isBusy,
                accentColor = AccentCyan,
                modifier = Modifier
                    .weight(1.25f)
                    .heightIn(min = 48.dp),
                onClick = { applyConfigNow(config, R.string.panel_hw_status_applied) },
            )
            LynxActionButton(
                text = stringResource(R.string.panel_hw_restore_btn),
                icon = Icons.Default.RestartAlt,
                isLoading = isBusy,
                accentColor = AccentOrange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                onClick = {
                    val defaults = PanelTuningConfig.STOCK_BASELINE.copy(applyOnBoot = config.applyOnBoot)
                    applyConfigNow(defaults, R.string.panel_hw_status_restored)
                },
            )
        }
    }
}

@Composable
private fun PanelTuningTelemetryBanner(support: PanelTuningHardwareSupport) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val cabcLabel = if (support.cabcSupported) {
                when (support.activeCabcMode) {
                    1 -> stringResource(R.string.panel_hw_cabc_ui)
                    2 -> stringResource(R.string.panel_hw_cabc_still)
                    3 -> stringResource(R.string.panel_hw_cabc_moving)
                    else -> stringResource(R.string.panel_hw_cabc_off)
                }
            } else {
                "-"
            }
            val esdLabel = if (support.esdSupported) {
                formatEsdPeriodLabel(support.activeEsdPeriodMs)
            } else {
                "-"
            }
            Text(
                text = stringResource(R.string.panel_hw_telemetry_line, cabcLabel, esdLabel),
                color = TextPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.panel_hw_telemetry_hint),
                color = TextSecondary,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun formatEsdPeriodLabel(periodMs: Int): String {
    return if (periodMs <= 0) {
        stringResource(R.string.panel_hw_esd_val_paused)
    } else {
        stringResource(R.string.panel_hw_esd_val_seconds, periodMs / 1000, periodMs)
    }
}
