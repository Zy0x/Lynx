package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text

internal val HudPage.label: Int
    get() = when (this) {
        HudPage.Summary -> R.string.hud_summary
        HudPage.Details -> R.string.hud_monitor
        HudPage.Lab -> R.string.hud_nav_boost
        HudPage.Tools -> R.string.hud_tools
    }

internal val HudPage.icon: ImageVector
    get() = when (this) {
        HudPage.Summary -> Icons.Default.Speed
        HudPage.Details -> Icons.AutoMirrored.Filled.ShowChart
        HudPage.Lab -> Icons.Default.SportsEsports
        HudPage.Tools -> Icons.Default.Tune
    }

/** A horizontally scrollable fallback keeps every destination one tap away on small windows. */
@Composable
internal fun HudTopTabBar(selected: HudPage, onSelect: (HudPage) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HudPage.values().forEach { page ->
            val active = selected == page
            Column(
                Modifier.widthIn(min = 72.dp).heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) HudCyan.copy(alpha = .14f) else HudCard)
                    .selectable(active, role = Role.Tab, onClick = { onSelect(page) })
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
            ) {
                Icon(page.icon, null, tint = if (active) HudCyan else HudMuted, modifier = Modifier.size(18.dp))
                Text(hudString(page.label), fontSize = 12.sp, color = if (active) Color.White else HudMuted)
            }
        }
    }
}

@Composable
internal fun HudNavigationRail(
    selected: HudPage,
    width: Float,
    onSelect: (HudPage) -> Unit,
    modifier: Modifier = Modifier,
    dockRight: Boolean = false
) {
    Column(
        modifier.width(width.dp).fillMaxHeight().verticalScroll(rememberScrollState())
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HudPage.values().forEach { page ->
            val active = selected == page
            val title = hudString(page.label)
            val tint by animateColorAsState(if (active) HudCyan else HudMuted, tween(180), label = title)
            val background by animateColorAsState(
                if (active) HudCyan.copy(alpha = .10f) else Color.Transparent, tween(180), label = title)
            Surface(
                color = background,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (active) HudCyan.copy(alpha = .20f) else Color.Transparent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(active, role = Role.Tab, onClick = { onSelect(page) })
            ) {
                Box {
                    Box(Modifier.align(if (dockRight) Alignment.CenterEnd else Alignment.CenterStart)
                        .width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (active) HudCyan else Color.Transparent))
                    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(page.icon, null, tint = tint, modifier = Modifier.size(20.dp))
                        Text(title, fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) Color.White else HudMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}
