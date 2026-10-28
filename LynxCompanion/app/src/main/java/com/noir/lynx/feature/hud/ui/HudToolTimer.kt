package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun HudToolTimer(timerRemainingSec: Int, timerActivePreset: Int, onSetTimer: (Int) -> Unit) {
    // Magic Box · Cooldown Timer
    val mins = timerRemainingSec / 60
    val secs = timerRemainingSec % 60
    val timerBadge = if (timerRemainingSec > 0) {
        String.format("%02d:%02d", mins, secs)
    } else {
        hudString(R.string.hud_timer_ready)
    }
    HudExpandableSection(
        title = hudString(R.string.hud_timer_title),
        detail = timerBadge,
        initiallyOpen = true,
        info = hudString(R.string.hud_timer_desc)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = String.format("%02d:%02d", mins, secs),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (timerRemainingSec > 0) HudCyan else Color.White
            )
            HudChoice(
                label = hudString(R.string.hud_timer_reset),
                selected = false,
                onClick = {
                    onSetTimer(0)
                },
                modifier = Modifier.width(82.dp),
                enabled = timerRemainingSec > 0
            )
        }

        val presets = listOf(
            30 to "30s",
            60 to "60s",
            120 to "2m",
            180 to "3m"
        )
        HudAdaptiveGrid(presets.map { it.second }, maximum = 2) { index ->
            val (sec, label) = presets[index]
            HudChoice(label, timerRemainingSec > 0 && timerActivePreset == sec,
                { onSetTimer(sec) }, Modifier.weight(1f))
        }
    }

}
