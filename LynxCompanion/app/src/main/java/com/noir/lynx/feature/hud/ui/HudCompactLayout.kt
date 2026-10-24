package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
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
    accentColor: Color = Color(0xFFE8EAED),
    content: @Composable ColumnScope.() -> Unit
) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudCard,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column {
            Surface(
                onClick = { open = !open },
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (detail.isNotBlank()) {
                            Text(
                                text = detail,
                                fontSize = 11.5.sp,
                                color = HudBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Surface(
                        color = HudElevated,
                        shape = CircleShape,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = hudString(if (open) R.string.hud_expanded else R.string.hud_collapsed),
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFFE8EAED)
                            )
                        }
                    }
                }
            }
            if (open) {
                HorizontalDivider(color = HudBorder, thickness = 0.5.dp)
                Column(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
            }
        }
    }
}
