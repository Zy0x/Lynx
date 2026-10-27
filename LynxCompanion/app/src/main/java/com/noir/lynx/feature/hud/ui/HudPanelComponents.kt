package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.window.Dialog
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
                    fontWeight = FontWeight.Medium,
                    fontFamily = if (numeric) FontFamily.Monospace else FontFamily.Default
                )
            ).size.width.toDp().value
        }
    } ?: 0f
}

data class HudInfoData(
    val title: String,
    val content: String,
    val accentColor: Color = HudCyan
)

internal val LocalHudInfo = compositionLocalOf<(HudInfoData?) -> Unit> { {} }

@Composable
internal fun HudInfoModal(
    data: HudInfoData,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color(0xD90A0C12))
            .clickable(onClick = onDismiss)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xF2121622),
            border = BorderStroke(1.2.dp, data.accentColor.copy(alpha = 0.6f)),
            shadowElevation = 20.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = data.accentColor.copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = data.accentColor,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Text(
                        text = data.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = HudMuted,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                HorizontalDivider(color = HudBorder, thickness = 0.8.dp)

                Text(
                    text = data.content,
                    fontSize = 12.sp,
                    lineHeight = 17.5.sp,
                    color = Color(0xFFE2E4E9)
                )

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = data.accentColor,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Text(
                        text = "Mengerti",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
internal fun HudInfoButton(
    title: String,
    info: String,
    modifier: Modifier = Modifier,
    accentColor: Color = HudCyan
) {
    val showInfo = LocalHudInfo.current
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .clickable { showInfo(HudInfoData(title, info, accentColor)) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = "Info $title",
            tint = accentColor.copy(alpha = 0.85f),
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
internal fun HudSection(
    title: String,
    badge: String? = null,
    accentColor: Color = HudCyan,
    showPlayIcon: Boolean = false,
    info: String? = null,
    infoTitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(Modifier.fillMaxWidth(), color = HudCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, HudBorder)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val required = hudLabelWidth(listOf(title)) + 
                (if (badge.isNullOrBlank()) 0f else hudLabelWidth(listOf(badge)) + 10f) + 
                (if (info.isNullOrBlank()) 0f else 32f)
            val titleContent: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (showPlayIcon) Box(Modifier.size(5.dp).clip(CircleShape).background(accentColor))
                    Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    if (!info.isNullOrBlank()) {
                        HudInfoButton(
                            title = infoTitle ?: title,
                            info = info,
                            accentColor = accentColor
                        )
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth.value < required && !badge.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        titleContent()
                        Text(badge, color = accentColor, fontSize = 12.sp)
                    }
                } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { titleContent() }
                    if (!badge.isNullOrBlank()) Text(badge, color = accentColor, fontSize = 12.sp)
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
    accentColor: Color = HudBlue,
    role: Role = Role.RadioButton
) {
    val background by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = .16f) else HudElevated,
        animationSpec = tween(200),
        label = label
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) accentColor.copy(alpha = .55f) else HudBorder),
        color = background,
        modifier = modifier
            .heightIn(min = 48.dp)
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
                    selected -> Color.White
                    else -> Color(0xFFE8EAED)
                },
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun HudDisclosure(label: String, expanded: Boolean, onClick: () -> Unit) {
    val state = hudString(if (expanded) R.string.hud_expanded else R.string.hud_collapsed)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = HudElevated,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { stateDescription = state }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (expanded) HudBlue else Color(0xFFE8EAED)
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
internal fun HudReadingLine(label: String, reading: HudReading, accentColor: Color = Color(0xFFE8EAED)) {
    val value = if (reading.status == "valid") hudString(R.string.hud_value, reading.text(), reading.unit) else "—"
    val labelWidth = hudLabelWidth(listOf(label))
    val valueWidth = hudLabelWidth(listOf(value), 12, numeric = true)
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
                        color = HudMuted
                    )
                    Text(
                        text = value,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (reading.status == "valid") accentColor else HudMuted.copy(alpha = 0.5f)
                    )
                }
            } else {
                Text(label, fontSize = 11.5.sp, color = HudMuted)
                Text(
                    text = value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (reading.status == "valid") accentColor else HudMuted.copy(alpha = 0.5f)
                )
            }
        }
    }
}
