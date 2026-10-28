package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text

/** Measured cells reflow rather than squeezing three readings into a narrow detail card. */
@Composable
internal fun HudStatGrid(entries: List<Pair<String, String>>) {
    HudAdaptiveGrid(entries.map { it.first }, minCell = maxOf(80f, hudLabelWidth(entries.map { it.second }, numeric = true) + 20f), maximum = 2) { index ->
        Surface(Modifier.weight(1f), color = HudElevated, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entries[index].first, fontSize = 12.sp, color = HudMuted)
                Text(entries[index].second, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color.White)
            }
        }
    }
}
