package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text

@Composable
internal fun HudAdaptiveGrid(
    labels: List<String>,
    maximum: Int = 3,
    minCell: Float = 48f,
    content: @Composable RowScope.(Int) -> Unit
) {
    val cell = maxOf(minCell, hudLabelWidth(labels) + 16f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = HudLayoutPolicy.columns(maxWidth.value, cell, maximum = maximum)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.indices.chunked(columns).forEach { indices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    indices.forEach { content(it) }
                    repeat(columns - indices.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun HudExpandableSection(
    title: String,
    detail: String = "",
    initiallyOpen: Boolean = false,
    accentColor: Color = HudCyan,
    content: @Composable ColumnScope.() -> Unit
) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.9.dp, if (open) accentColor.copy(alpha = 0.28f) else HudBorder)
    ) {
        Column {
            Surface(
                onClick = { open = !open },
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor)
                    )
                    Text(
                        text = title,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (detail.isNotBlank()) {
                        Surface(
                            color = HudElevated,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.6.dp, HudBorder)
                        ) {
                            Text(
                                text = detail,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE2E8F0),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1
                            )
                        }
                    }
                    Icon(
                        imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = hudString(if (open) R.string.hud_expanded else R.string.hud_collapsed),
                        modifier = Modifier.size(18.dp),
                        tint = if (open) accentColor else HudMuted
                    )
                }
            }
            if (open) {
                HorizontalDivider(color = HudBorder.copy(alpha = 0.1f), thickness = 0.6.dp)
                Column(
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    content = content
                )
            }
        }
    }
}
