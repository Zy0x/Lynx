package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text

private val HudAmber = Color(0xFFFFB74D)

/**
 * Ultra-compact, non-intrusive floating trigger badge for Dialogue Auto-Skip.
 * Slim 34×34 dp circular footprint: prevents game UI occlusion and eliminates dead zones.
 */
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

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle()
        },
        shape = CircleShape,
        color = backgroundColor,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = modifier
            .padding(1.dp)
            .size(34.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = iconText,
                fontSize = 13.sp,
                color = targetTextColor
            )
        }
    }
}
