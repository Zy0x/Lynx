package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        border = BorderStroke(0.9.dp, HudBorder)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
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
                        color = accentColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!badge.isNullOrBlank()) {
                    Surface(
                        color = accentColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.6.dp, accentColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = badge,
                            color = accentColor,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
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
        targetValue = if (selected) accentColor.copy(alpha = 0.18f) else HudElevated,
        animationSpec = tween(180),
        label = label
    )
    val border by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.65f) else HudBorder,
        animationSpec = tween(180),
        label = "border_$label"
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = background,
        border = BorderStroke(1.dp, border),
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
                    else -> Color(0xFFE2E8F0)
                },
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
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
        shape = RoundedCornerShape(11.dp),
        color = HudElevated,
        border = BorderStroke(0.8.dp, if (expanded) HudCyan.copy(alpha = 0.4f) else HudBorder),
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
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (expanded) HudCyan else Color(0xFFE2E8F0)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (expanded) HudCyan else HudMuted,
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
                        fontSize = 11.5.sp,
                        color = HudMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = value,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (reading.status == "valid") accentColor else HudMuted.copy(alpha = 0.5f)
                    )
                }
            } else {
                Text(label, fontSize = 11.sp, color = HudMuted)
                Text(
                    text = value,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (reading.status == "valid") accentColor else HudMuted.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
internal fun HudMetricTile(
    label: String,
    reading: HudReading,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.White,
    progress: Float? = null
) {
    val valueText = reading.text()
    val isValid = reading.status == "valid"
    Surface(
        modifier = modifier,
        color = HudElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.7.dp, HudBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.5.sp,
                color = HudMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = valueText,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (isValid) accentColor else HudMuted.copy(alpha = 0.5f),
                    maxLines = 1
                )
                if (isValid && reading.unit.isNotBlank()) {
                    Text(
                        text = reading.unit,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HudMuted,
                        modifier = Modifier.padding(bottom = 1.dp),
                        maxLines = 1
                    )
                }
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                    color = if (accentColor == Color.White) HudCyan else accentColor,
                    trackColor = HudCard
                )
            }
        }
    }
}
