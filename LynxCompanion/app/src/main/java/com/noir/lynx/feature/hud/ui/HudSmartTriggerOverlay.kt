package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.noir.lynx.R
import com.noir.lynx.feature.hud.autoskip.GenshinSkipRunner

/** Small visual token inside a full 48 dp gesture target. Supports 3 discrete operational modes. */
@Composable
fun HudSmartTriggerOverlay(
    mode: GenshinSkipRunner.SkipMode,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center
) {
    val tint by animateColorAsState(
        when (mode) {
            GenshinSkipRunner.SkipMode.AUTO -> HudCyan
            GenshinSkipRunner.SkipMode.MANUAL -> HudOrange
            GenshinSkipRunner.SkipMode.PAUSED -> HudMuted
        },
        tween(200),
        label = "skipModeTint"
    )
    val status = hudString(
        when (mode) {
            GenshinSkipRunner.SkipMode.AUTO -> R.string.hud_autoskip_mode_auto
            GenshinSkipRunner.SkipMode.MANUAL -> R.string.hud_autoskip_mode_manual
            GenshinSkipRunner.SkipMode.PAUSED -> R.string.hud_autoskip_mode_paused
        }
    )
    val description = hudString(R.string.hud_skip_control_description, status)

    Box(
        modifier = modifier.size(48.dp).semantics { this.contentDescription = description },
        contentAlignment = contentAlignment
    ) {
        Surface(
            color = HudBackground,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(0.75.dp, tint.copy(alpha = 0.5f)),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when (mode) {
                        GenshinSkipRunner.SkipMode.AUTO -> Icons.Default.AutoMode
                        GenshinSkipRunner.SkipMode.MANUAL -> Icons.Rounded.FastForward
                        GenshinSkipRunner.SkipMode.PAUSED -> Icons.Rounded.Pause
                    },
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(19.dp)
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(4.dp)
                        .background(tint, CircleShape)
                )
            }
        }
    }
}

/** Backward-compatible overload for legacy call sites. */
@Composable
fun HudSmartTriggerOverlay(
    isSkipping: Boolean,
    isPaused: Boolean = false,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center
) {
    val mode = if (isPaused) {
        GenshinSkipRunner.SkipMode.PAUSED
    } else if (isSkipping) {
        GenshinSkipRunner.SkipMode.AUTO
    } else {
        GenshinSkipRunner.SkipMode.PAUSED
    }
    HudSmartTriggerOverlay(mode = mode, modifier = modifier, contentAlignment = contentAlignment)
}
