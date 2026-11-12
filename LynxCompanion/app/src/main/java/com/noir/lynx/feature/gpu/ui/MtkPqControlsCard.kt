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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideogameAsset
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxMtkPqRepository
import com.noir.lynx.data.MtkPqConfig
import com.noir.lynx.data.MtkPqHardwareSupport
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

private data class MtkPqPresetItem(
    val labelRes: Int,
    val preset: MtkPqConfig,
    val icon: ImageVector,
)

/**
 * Dynamically gated MediaTek Picture Quality (MTK PQ) control card for AAL Bypass,
 * MDP DRE local contrast, TDSHP sharpness index, and Ultra Resolution tuning.
 * Hidden automatically on non-MediaTek hardware so unsupported panels show zero dead controls.
 */
@Composable
fun MtkPqControlsCard(
    context: Context,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(LynxMtkPqRepository.loadSavedConfig(context)) }
    var support by remember {
        mutableStateOf(LynxMtkPqRepository.cachedHardwareSupport() ?: MtkPqHardwareSupport())
    }
    var isBusy by remember { mutableStateOf(false) }
    var feedbackResId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        support = LynxMtkPqRepository.probeHardwareSupport()
    }

    if (!support.probed || !support.supported) {
        return
    }

    val applyAndReload: (MtkPqConfig, Int) -> Unit = { target, okMsgRes ->
        val clean = target.sanitized()
        config = clean
        feedbackResId = null
        scope.launch {
            isBusy = true
            val result = LynxMtkPqRepository.applyConfig(
                context = context,
                config = clean,
                persistAsDefault = true,
                restartPqService = true,
            )
            support = result
            isBusy = false
            feedbackResId = if (result.lastReloadOk) okMsgRes else R.string.mtk_pq_status_reload_warn
        }
    }

    LynxCard(
        title = stringResource(R.string.mtk_pq_card_title),
        subtitle = stringResource(R.string.mtk_pq_card_subtitle),
        icon = Icons.Default.Tune,
        accentColor = AccentOrange,
        modifier = modifier,
        action = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentOrange.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.4f)),
                ) {
                    Text(
                        text = stringResource(R.string.mtk_pq_hw_badge, support.mtkPqSupportLevel),
                        color = AccentOrange,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch { support = LynxMtkPqRepository.probeHardwareSupport() }
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.mtk_pq_refresh_cd),
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
    ) {
        MtkPqTelemetryBanner(support = support)

        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.mtk_pq_presets_title),
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))

        val presets = remember {
            listOf(
                MtkPqPresetItem(R.string.mtk_pq_preset_stock, MtkPqConfig.STOCK_BALANCED, Icons.Default.Verified),
                MtkPqPresetItem(R.string.mtk_pq_preset_natural, MtkPqConfig.PURE_NATURAL, Icons.Default.AutoAwesome),
                MtkPqPresetItem(R.string.mtk_pq_preset_cinema, MtkPqConfig.CINEMA_PQ, Icons.Default.Contrast),
                MtkPqPresetItem(R.string.mtk_pq_preset_gaming, MtkPqConfig.SHARP_GAMING, Icons.Default.VideogameAsset),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { item ->
                val isSelected = config.matchesOpticalPreset(item.preset)
                Surface(
                    onClick = {
                        if (!isBusy) {
                            applyAndReload(
                                item.preset.copy(applyOnBoot = config.applyOnBoot),
                                R.string.mtk_pq_status_applied,
                            )
                        }
                    },
                    enabled = !isBusy,
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) AccentOrange.copy(alpha = 0.18f) else BgElevated,
                    border = BorderStroke(1.dp, if (isSelected) AccentOrange else BorderSubtle),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = stringResource(item.labelRes),
                            tint = if (isSelected) AccentOrange else TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(item.labelRes),
                            color = if (isSelected) AccentOrange else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 12.dp))

        LynxSwitch(
            label = stringResource(R.string.mtk_pq_aal_bypass_label),
            subLabel = stringResource(R.string.mtk_pq_aal_bypass_sub),
            checked = config.aalBypass,
            onCheckedChange = { checked ->
                feedbackResId = null
                config = config.copy(aalBypass = checked)
            },
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.mtk_pq_dre_mode_label),
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))

        val dreOptions = listOf(
            0 to R.string.mtk_pq_dre_off,
            1 to R.string.mtk_pq_dre_on,
            2 to R.string.mtk_pq_dre_auto,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            dreOptions.forEach { (mode, labelRes) ->
                val selected = config.dreMode == mode
                Surface(
                    onClick = {
                        feedbackResId = null
                        config = config.copy(dreMode = mode)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) AccentCyan.copy(alpha = 0.18f) else BgElevated,
                    border = BorderStroke(1.dp, if (selected) AccentCyan else BorderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            color = if (selected) AccentCyan else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        LynxSwitch(
            label = stringResource(R.string.mtk_pq_vp_dre_label),
            subLabel = stringResource(R.string.mtk_pq_vp_dre_sub),
            checked = config.vpDreEnabled,
            onCheckedChange = { checked ->
                feedbackResId = null
                config = config.copy(vpDreEnabled = checked)
            },
        )

        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.mtk_pq_shp_idx_label),
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.mtk_pq_shp_idx_val, config.shpIndex),
                color = AccentOrange,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = config.shpIndex.toFloat(),
            onValueChange = { raw ->
                feedbackResId = null
                config = config.copy(
                    shpIndex = raw.roundToInt().coerceIn(MtkPqConfig.MIN_SHP_INDEX, MtkPqConfig.MAX_SHP_INDEX),
                )
            },
            valueRange = MtkPqConfig.MIN_SHP_INDEX.toFloat()..MtkPqConfig.MAX_SHP_INDEX.toFloat(),
            steps = MtkPqConfig.MAX_SHP_INDEX - MtkPqConfig.MIN_SHP_INDEX - 1,
            colors = SliderDefaults.colors(
                thumbColor = AccentOrange,
                activeTrackColor = AccentOrange,
                inactiveTrackColor = BgElevated,
            ),
        )

        LynxSwitch(
            label = stringResource(R.string.mtk_pq_iso_shp_label),
            subLabel = stringResource(R.string.mtk_pq_iso_shp_sub),
            checked = config.isoShpEnabled,
            onCheckedChange = { checked ->
                feedbackResId = null
                config = config.copy(isoShpEnabled = checked)
            },
        )
        LynxSwitch(
            label = stringResource(R.string.mtk_pq_dbg_shp_label),
            subLabel = stringResource(R.string.mtk_pq_dbg_shp_sub),
            checked = config.debugShpEnabled,
            onCheckedChange = { checked ->
                feedbackResId = null
                config = config.copy(debugShpEnabled = checked)
            },
        )
        LynxSwitch(
            label = stringResource(R.string.mtk_pq_dbg_dshp_label),
            subLabel = stringResource(R.string.mtk_pq_dbg_dshp_sub),
            checked = config.debugDshpEnabled,
            onCheckedChange = { checked ->
                feedbackResId = null
                config = config.copy(debugDshpEnabled = checked)
            },
        )

        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 10.dp))

        LynxSwitch(
            label = stringResource(R.string.mtk_pq_ultrares_enable_label),
            subLabel = stringResource(R.string.mtk_pq_ultrares_enable_sub),
            checked = config.ultraResEnabled,
            onCheckedChange = { checked ->
                feedbackResId = null
                config = config.copy(ultraResEnabled = checked)
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.mtk_pq_ultrares_str_label),
                color = if (config.ultraResEnabled) TextPrimary else TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.mtk_pq_ultrares_str_val, config.ultraResStrength),
                color = if (config.ultraResEnabled) AccentCyan else TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = config.ultraResStrength.toFloat(),
            onValueChange = { raw ->
                feedbackResId = null
                config = config.copy(
                    ultraResStrength = raw.roundToInt().coerceIn(
                        MtkPqConfig.MIN_ULTRARES_STRENGTH,
                        MtkPqConfig.MAX_ULTRARES_STRENGTH,
                    ),
                )
            },
            enabled = config.ultraResEnabled,
            valueRange = MtkPqConfig.MIN_ULTRARES_STRENGTH.toFloat()..MtkPqConfig.MAX_ULTRARES_STRENGTH.toFloat(),
            steps = MtkPqConfig.MAX_ULTRARES_STRENGTH - MtkPqConfig.MIN_ULTRARES_STRENGTH - 1,
            colors = SliderDefaults.colors(
                thumbColor = AccentCyan,
                activeTrackColor = AccentCyan,
                inactiveTrackColor = BgElevated,
            ),
        )

        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 10.dp))

        LynxSwitch(
            label = stringResource(R.string.mtk_pq_boot_persist),
            subLabel = stringResource(R.string.mtk_pq_boot_persist_sub),
            checked = config.applyOnBoot,
            onCheckedChange = { checked ->
                val updated = config.copy(applyOnBoot = checked)
                config = updated
                LynxMtkPqRepository.saveConfigPrefs(context, updated)
            },
        )

        feedbackResId?.let { resId ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(resId),
                color = if (resId == R.string.mtk_pq_status_reload_warn) AccentOrange else AccentGreen,
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
                text = stringResource(if (isBusy) R.string.mtk_pq_applying_btn else R.string.mtk_pq_apply_btn),
                icon = Icons.Default.Check,
                isLoading = isBusy,
                accentColor = AccentOrange,
                modifier = Modifier
                    .weight(1.25f)
                    .heightIn(min = 48.dp),
                onClick = { applyAndReload(config, R.string.mtk_pq_status_applied) },
            )
            LynxActionButton(
                text = stringResource(R.string.mtk_pq_restore_btn),
                icon = Icons.Default.RestartAlt,
                isLoading = isBusy,
                accentColor = AccentCyan,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                onClick = {
                    val defaults = MtkPqConfig.STOCK_BALANCED.copy(applyOnBoot = config.applyOnBoot)
                    applyAndReload(defaults, R.string.mtk_pq_status_restored)
                },
            )
        }
    }
}

@Composable
private fun MtkPqTelemetryBanner(support: MtkPqHardwareSupport) {
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
            val aalState = stringResource(
                if (support.activeAalBypass) R.string.mtk_pq_state_bypassed else R.string.mtk_pq_state_active,
            )
            val svcName = support.pqServiceName.ifBlank { "-" }
            Text(
                text = stringResource(
                    R.string.mtk_pq_telemetry_primary,
                    aalState,
                    support.activeDreMode,
                    support.activeShpIndex,
                ),
                color = TextPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    R.string.mtk_pq_telemetry_secondary,
                    if (support.activeUltraResEnabled) 1 else 0,
                    support.activeUltraResStrength,
                    svcName,
                ),
                color = TextSecondary,
                fontSize = 11.sp,
            )
        }
    }
}
