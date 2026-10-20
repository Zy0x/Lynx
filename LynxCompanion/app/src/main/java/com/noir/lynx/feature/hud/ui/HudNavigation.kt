package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text

internal val HudPage.label: Int get() = when (this) {
    HudPage.Summary -> R.string.hud_summary
    HudPage.Details -> R.string.hud_details
    HudPage.Tools -> R.string.hud_tools
}

@Composable
internal fun HudNavigationRail(selected: HudPage, width: Float, onSelect: (HudPage) -> Unit) {
    Column(Modifier.width(width.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)
        .selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HudPage.values().forEach { page ->
            val active = selected == page
            val title = hudString(page.label)
            val background by animateColorAsState(if (active) HudCyan.copy(alpha = 0.12f) else Color.Transparent, tween(200), label = title)
            Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).clip(RoundedCornerShape(10.dp))
                .background(background).selectable(active, role = Role.Tab, onClick = { onSelect(page) })
                .heightIn(min = 64.dp)) {
                if (active) Box(Modifier.align(Alignment.CenterStart).width(3.dp).height(24.dp)
                    .background(HudCyan, RoundedCornerShape(2.dp)))
                Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    val icon = when(page) { HudPage.Summary -> Icons.Default.Speed; HudPage.Details -> Icons.Default.ShowChart; HudPage.Tools -> Icons.Default.Tune }
                    Icon(icon, null, Modifier.size(20.dp), tint = if (active) HudCyan else HudMuted)
                    Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = if (active) HudCyan else HudMuted, softWrap = false)
                }
            }
        }
    }
}

@Composable
internal fun HudPageMenu(selected: HudPage, onSelect: (HudPage) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).selectableGroup().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HudPage.values().forEach { page ->
            HudChoice(hudString(page.label), selected == page, { onSelect(page) }, Modifier.fillMaxWidth(), role = Role.Tab)
        }
    }
}
