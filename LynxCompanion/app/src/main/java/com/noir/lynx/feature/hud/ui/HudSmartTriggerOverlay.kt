package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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

@Composable
internal fun HudSmartTriggerOverlay(
    isSkipping: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val borderColor by animateColorAsState(
        targetValue = if (isSkipping) HudCyan else HudBorder,
        animationSpec = tween(180),
        label = "triggerBorder"
    )

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle()
        },
        shape = RoundedCornerShape(18.dp),
        color = if (isSkipping) Color(0xEE0B2538) else Color(0xEE12151B),
        border = BorderStroke(1.5.dp, borderColor),
        modifier = modifier.padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "⏩",
                fontSize = 11.5.sp
            )
            Text(
                text = if (isSkipping) {
                    hudString(R.string.hud_autoskip_pill_active)
                } else {
                    hudString(R.string.hud_autoskip_pill_idle)
                },
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = if (isSkipping) HudCyan else Color.White
            )
        }
    }
}
