package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text

private val HudAmber = Color(0xFFFFB74D)

@Composable
internal fun HudSmartTriggerOverlay(
    isSkipping: Boolean,
    isPaused: Boolean = false,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val targetBorder = when {
        isSkipping -> HudCyan
        isPaused -> HudAmber
        else -> HudBorder
    }

    val targetBackground = when {
        isSkipping -> Color(0xEE0B2538)
        isPaused -> Color(0xEE2A1C0E)
        else -> Color(0xEE12151B)
    }

    val targetTextColor = when {
        isSkipping -> HudCyan
        isPaused -> HudAmber
        else -> Color.White
    }

    val borderColor by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = tween(180),
        label = "triggerBorder"
    )

    val backgroundColor by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(180),
        label = "triggerBackground"
    )

    val iconText = when {
        isPaused -> "⏸️"
        else -> "⏩"
    }

    val labelText = when {
        isSkipping -> hudString(R.string.hud_autoskip_pill_active)
        isPaused -> hudString(R.string.hud_autoskip_pill_paused)
        else -> hudString(R.string.hud_autoskip_pill_idle)
    }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle()
        },
        shape = RoundedCornerShape(18.dp),
        color = backgroundColor,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = modifier.padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = iconText,
                fontSize = 11.5.sp
            )
            Text(
                text = labelText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = targetTextColor
            )
        }
    }
}
