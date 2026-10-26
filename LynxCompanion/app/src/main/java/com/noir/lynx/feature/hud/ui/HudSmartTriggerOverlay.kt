package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

/** Small visual token inside a full 48 dp gesture target. Idle state does not animate. */
@Composable
fun HudSmartTriggerOverlay(isSkipping: Boolean, isPaused: Boolean = false, modifier: Modifier = Modifier) {
    val tint by animateColorAsState(if (isPaused) HudOrange else if (isSkipping) HudCyan else HudMuted, tween(200), label = "skipState")
    val status = hudString(if (isPaused) R.string.hud_autoskip_pill_paused else if (isSkipping) R.string.hud_autoskip_pill_active else R.string.hud_skip_ready)
    val description = hudString(R.string.hud_skip_control_description, status)
    Box(modifier.size(48.dp).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Surface(color = HudBackground, shape = RoundedCornerShape(12.dp), border = BorderStroke(.75.dp, tint.copy(alpha = .5f)), modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (isPaused) Icons.Rounded.Pause else if (isSkipping) Icons.Rounded.FastForward else Icons.Rounded.PlayArrow,
                    contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
                Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(4.dp).background(tint, CircleShape))
            }
        }
    }
}
