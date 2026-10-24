package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.HudReading

@Composable
internal fun hudLabelWidth(labels: List<String>, size: Int = 12, numeric: Boolean = false): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return labels.maxOfOrNull {
        with(density) {
            measurer.measure(
                AnnotatedString(it),
                MaterialTheme.typography.bodyLarge.copy(
                    fontSize = size.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = if (numeric) FontFamily.Monospace else FontFamily.Default
                )
            ).size.width.toDp().value
        }
    } ?: 0f
}

@Composable
internal fun HudSection(
    title: String,
    badge: String? = null,
    accentColor: Color = HudCyan,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.8.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!badge.isNullOrBlank()) {
                    Text(
                        text = badge,
                        color = HudMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            content()
        }
    }
}

@Composable
internal fun HudChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = HudCyan,
    role: Role = Role.RadioButton
) {
    val background by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.16f) else HudElevated,
        animationSpec = tween(160),
        label = label
    )
    val border by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.55f) else HudBorder,
        animationSpec = tween(160),
        label = "border_$label"
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = background,
        border = BorderStroke(0.8.dp, border),
        modifier = modifier
            .heightIn(min = 42.dp)
            .semantics {
                this.selected = selected
                this.role = role
            }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = when {
                    !enabled -> HudMuted.copy(alpha = 0.4f)
                    selected -> accentColor
                    else -> Color.White
                },
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun HudDisclosure(label: String, expanded: Boolean, onClick: () -> Unit) {
    val state = hudString(if (expanded) R.string.hud_expanded else R.string.hud_collapsed)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = HudElevated,
        border = BorderStroke(0.8.dp, HudBorder),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .semantics { stateDescription = state }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (expanded) HudCyan else Color.White
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = HudMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun HudReadingLine(label: String, reading: HudReading, accentColor: Color = Color.White) {
    val value = hudString(R.string.hud_value, reading.text(), reading.unit)
    val labelWidth = hudLabelWidth(listOf(label))
    val valueWidth = hudLabelWidth(listOf(value), 13, numeric = true)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = maxWidth.value
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (HudLayoutPolicy.fits(available, listOf(labelWidth, valueWidth))) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        color = HudMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = value,
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = if (reading.status == "valid") accentColor else HudMuted.copy(alpha = 0.5f)
                    )
                }
            } else {
                Text(label, fontSize = 11.5.sp, color = HudMuted)
                Text(
                    text = value,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = if (reading.status == "valid") accentColor else HudMuted.copy(alpha = 0.5f)
                )
            }
        }
    }
}
