package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text

/** A destination opens a focused child page without changing any device setting. */
internal data class HudDestination(val title: Int, val icon: ImageVector, val onClick: () -> Unit)

@Composable
internal fun HudDestinationGrid(destinations: List<HudDestination>) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = HudLayoutPolicy.columns(maxWidth.value, 88f * fontScale, gap = 6f, maximum = 2)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            destinations.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { destination ->
                        Surface(
                            onClick = destination.onClick,
                            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 76.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = HudCard,
                            border = BorderStroke(1.dp, HudBorder)
                        ) {
                            Column(
                                Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                            ) {
                                Icon(destination.icon, null, tint = HudCyan, modifier = Modifier.size(22.dp))
                                Text(hudString(destination.title), fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium, color = Color.White, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
