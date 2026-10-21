package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
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
        with(density) { measurer.measure(AnnotatedString(it), MaterialTheme.typography.bodyLarge.copy(fontSize = size.sp,
            fontWeight = FontWeight.SemiBold, fontFamily = if (numeric) FontFamily.Monospace else FontFamily.Default)).size.width.toDp().value }
    } ?: 0f
}

@Composable
internal fun HudSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HudCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.8.dp, HudBorder)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = HudCyan, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            content()
        }
    }
}

@Composable
internal fun HudChoice(label: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, role: Role = Role.RadioButton) {
    val background by animateColorAsState(if (selected) HudCyan.copy(alpha = 0.14f) else Color.Transparent, tween(200), label = label)
    val border by animateColorAsState(if (selected) HudCyan.copy(alpha = 0.4f) else Color.Transparent, tween(200), label = "border_$label")
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(9.dp),
        color = background,
        border = BorderStroke(0.8.dp, border),
        modifier = modifier.heightIn(min = 44.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (!enabled) HudMuted.copy(alpha = 0.4f) else if (selected) HudCyan else HudMuted,
                fontSize = 11.5.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun HudDisclosure(label: String, expanded: Boolean, onClick: () -> Unit) {
    val state = hudString(if (expanded) R.string.hud_expanded else R.string.hud_collapsed)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = HudCard,
        border = BorderStroke(0.8.dp, HudBorder),
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).semantics { stateDescription = state }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, Modifier.weight(1f), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = HudCyan)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = HudMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun HudReadingLine(label: String, reading: HudReading) {
    val value = hudString(R.string.hud_value, reading.text(), reading.unit)
    val labelWidth = hudLabelWidth(listOf(label))
    val valueWidth = hudLabelWidth(listOf(value), 13, numeric = true)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = maxWidth.value
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (HudLayoutPolicy.fits(available, listOf(labelWidth, valueWidth))) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), fontSize = 11.5.sp, color = HudMuted)
                    Text(value, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
                        color = if (reading.status == "valid") Color.White else HudMuted.copy(alpha = 0.5f))
                }
            } else {
                Text(label, fontSize = 11.sp, color = HudMuted)
                Text(value, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
                    color = if (reading.status == "valid") Color.White else HudMuted.copy(alpha = 0.5f))
            }
            if (reading.status != "valid") Text(statusText(reading.status), fontSize = 10.sp, color = HudMuted.copy(alpha = 0.5f))
        }
    }
}
