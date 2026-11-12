package com.noir.lynx.feature.gpu.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.noir.lynx.data.LynxTouchPrecisionRepository
import com.noir.lynx.data.TouchPrecisionConfig
import com.noir.lynx.data.TouchPrecisionHardwareSupport
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.controls.LynxActionButton
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.theme.AccentBlue
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.AccentGreen
import com.noir.lynx.designsystem.theme.AccentOrange
import com.noir.lynx.designsystem.theme.BgElevated
import com.noir.lynx.designsystem.theme.BorderSubtle
import com.noir.lynx.designsystem.theme.TextPrimary
import com.noir.lynx.designsystem.theme.TextSecondary
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Isolated stateful card inside GPU & Display Tab 1 for Novatek micro-sensitivity,
 * hardware/framework bezel edge guard, SPI bus clock, and KTCH/input pipeline calibration.
 */
@Composable
fun TouchPrecisionCalibrationCard(
    context: Context,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(LynxTouchPrecisionRepository.loadSavedConfig(context)) }
    val latestConfigRef = remember { arrayOf(config) }
    var support by remember {
        mutableStateOf(
            LynxTouchPrecisionRepository.cachedHardwareSupport() ?: TouchPrecisionHardwareSupport(),
        )
    }
    var isBusy by remember { mutableStateOf(false) }
    var feedbackResId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        support = LynxTouchPrecisionRepository.probeHardwareSupport()
    }

    val commitLive: (TouchPrecisionConfig) -> Unit = { updated ->
        latestConfigRef[0] = updated
        scope.launch {
            isBusy = true
            support = LynxTouchPrecisionRepository.applyConfig(
                context = context,
                config = updated,
                persistAsDefault = updated.applyOnBoot,
            )
            isBusy = false
        }
    }
    val updateConfig: (TouchPrecisionConfig) -> Unit = { updated ->
        feedbackResId = null
        latestConfigRef[0] = updated
        config = updated
    }
    val commitLatest: () -> Unit = { commitLive(latestConfigRef[0]) }

    LynxCard(
        title = stringResource(R.string.touch_precision_card_title),
        subtitle = stringResource(R.string.touch_precision_card_subtitle),
        icon = Icons.Default.TouchApp,
        accentColor = AccentCyan,
        modifier = modifier,
        action = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = (if (support.nvtPresent) AccentCyan else AccentBlue).copy(alpha = 0.14f),
                    border = BorderStroke(
                        0.8.dp,
                        (if (support.nvtPresent) AccentCyan else AccentBlue).copy(alpha = 0.4f),
                    ),
                ) {
                    Text(
                        text = stringResource(
                            if (support.nvtPresent) R.string.touch_precision_hw_detected_badge
                            else R.string.touch_precision_hw_generic_badge
                        ),
                        color = if (support.nvtPresent) AccentCyan else AccentBlue,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            isBusy = true
                            support = LynxTouchPrecisionRepository.probeHardwareSupport()
                            isBusy = false
                        }
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.touch_precision_refresh_cd),
                        tint = TextSecondary,
                    )
                }
            }
        },
    ) {
        TouchTelemetryBanner(support = support)

        EdgeGestureGuardSection(
            config = config,
            nvtPresent = support.nvtPresent,
            onConfigChange = updateConfig,
            onCommit = commitLive,
            onCommitLatest = commitLatest,
        )

        if (support.nvtPresent) {
            NovatekMicroSensitivitySection(
                config = config,
                onConfigChange = updateConfig,
                onCommit = commitLive,
                onCommitLatest = commitLatest,
            )
        }

        InputPipelineKtchSection(
            config = config,
            ktchPresent = support.ktchPresent,
            onConfigChange = updateConfig,
            onCommit = commitLive,
            onCommitLatest = commitLatest,
        )

        Spacer(modifier = Modifier.height(8.dp))

        LynxSwitch(
            label = stringResource(R.string.touch_precision_boot_persist),
            subLabel = stringResource(R.string.touch_precision_boot_persist_sub),
            checked = config.applyOnBoot,
            onCheckedChange = { checked ->
                val updated = config.copy(applyOnBoot = checked)
                latestConfigRef[0] = updated
                config = updated
                LynxTouchPrecisionRepository.saveConfigPrefs(context, updated)
            },
        )

        feedbackResId?.let { resId ->
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AccentGreen.copy(alpha = 0.12f),
                border = BorderStroke(0.8.dp, AccentGreen.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(resId),
                    color = AccentGreen,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LynxActionButton(
                text = stringResource(R.string.touch_precision_apply_save_btn),
                icon = Icons.Default.Check,
                isLoading = isBusy,
                accentColor = AccentCyan,
                modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch {
                        isBusy = true
                        support = LynxTouchPrecisionRepository.applyConfig(
                            context = context,
                            config = config,
                            persistAsDefault = true,
                        )
                        feedbackResId = R.string.touch_precision_status_saved
                        isBusy = false
                    }
                },
            )
            LynxActionButton(
                text = stringResource(R.string.touch_precision_restore_default_btn),
                icon = Icons.Default.RestartAlt,
                isLoading = isBusy,
                accentColor = AccentOrange,
                modifier = Modifier.weight(1f),
                onClick = {
                    val defaults = TouchPrecisionConfig.VALIDATED_DEFAULT.copy(applyOnBoot = config.applyOnBoot)
                    latestConfigRef[0] = defaults
                    config = defaults
                    scope.launch {
                        isBusy = true
                        support = LynxTouchPrecisionRepository.applyConfig(
                            context = context,
                            config = defaults,
                            persistAsDefault = true,
                        )
                        feedbackResId = R.string.touch_precision_status_restored
                        isBusy = false
                    }
                },
            )
        }
    }
}

@Composable
private fun TouchTelemetryBanner(support: TouchPrecisionHardwareSupport) {
    val spiMhz = String.format(Locale.US, "%.1f", support.nvtSpiHzActive / 1_000_000f)
    val gainMult = TouchPrecisionConfig.gainMultiplierText(support.nvtMicroGainActive)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BgElevated,
        border = BorderStroke(0.8.dp, BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            if (support.nvtPresent) {
                Text(
                    text = stringResource(
                        R.string.touch_precision_live_stats_line,
                        gainMult,
                        support.nvtEdgeGuardActive,
                        spiMhz,
                        support.nvtSpiErrors + support.nvtCsumErrors,
                        support.nvtDtUsLast,
                    ),
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(3.dp))
            }
            Text(
                text = stringResource(
                    R.string.touch_precision_live_edge_line,
                    support.edgeWidthLeftPx,
                    support.edgeWidthRightPx,
                    support.edgeMlThresholdActive,
                    support.activeSlop,
                ),
                color = TextSecondary,
                fontSize = 11.sp,
            )
        }
    }
}
