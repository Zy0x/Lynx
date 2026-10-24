package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text

@Composable
internal fun HudAdaptiveGrid(labels: List<String>, maximum: Int = 3, minCell: Float = 48f,
    content: @Composable RowScope.(Int) -> Unit) {
    val cell = maxOf(minCell, hudLabelWidth(labels) + 16f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = HudLayoutPolicy.columns(maxWidth.value, cell, maximum = maximum)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.indices.chunked(columns).forEach { indices ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    indices.forEach { content(it) }
                    repeat(columns - indices.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun HudExpandableSection(title: String, detail: String = "", initiallyOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    Surface(Modifier.fillMaxWidth(), color = HudCard, shape = RoundedCornerShape(10.dp), border = BorderStroke(.8.dp, HudBorder)) {
        Column {
            Surface(onClick = { open = !open }, color = HudCard, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HudCyan)
                        if (detail.isNotBlank()) Text(detail, fontSize = 12.sp, color = HudMuted)
                    }
                    Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        hudString(if (open) R.string.hud_expanded else R.string.hud_collapsed), Modifier.size(18.dp), tint = HudMuted)
                }
            }
            if (open) Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        }
    }
}
