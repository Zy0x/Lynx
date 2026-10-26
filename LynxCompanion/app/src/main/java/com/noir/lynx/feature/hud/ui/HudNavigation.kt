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
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

/** A page selector replaces compressed tabs when content cannot fit beside a rail. */
@Composable
internal fun HudPageSelector(selected: HudPage, onSelect: (HudPage) -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(onClick = { expanded = !expanded }, color = HudElevated, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(selected.icon, contentDescription = null, tint = HudCyan, modifier = Modifier.size(18.dp))
                Text(hudString(selected.label), fontSize = 12.sp, color = Color.White, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ExpandMore, hudString(R.string.hud_pages), tint = HudMuted, modifier = Modifier.size(18.dp))
            }
        }
        if (expanded) HudPage.values().forEach { page ->
            HudChoice(hudString(page.label), selected == page, { expanded = false; onSelect(page) }, Modifier.fillMaxWidth())
        }
    }
}

internal val HudPage.label: Int
    get() = when (this) {
        HudPage.Summary -> R.string.hud_summary
        HudPage.Details -> R.string.hud_monitor
        HudPage.Lab -> R.string.hud_lab
        HudPage.Tools -> R.string.hud_tools
    }

internal val HudPage.icon: ImageVector
    get() = when (this) {
        HudPage.Summary -> Icons.Default.Speed
        HudPage.Details -> Icons.AutoMirrored.Filled.ShowChart
        HudPage.Lab -> Icons.Default.Science
        HudPage.Tools -> Icons.Default.Tune
    }

@Composable
internal fun HudTopTabBar(
    selected: HudPage,
    onSelect: (HudPage) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = HudCard,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudPage.values().forEach { page ->
                val active = selected == page
                val title = hudString(page.label)
                val background by animateColorAsState(
                    targetValue = if (active) HudElevated else Color.Transparent,
                    animationSpec = tween(200),
                    label = "tab_bg_$title"
                )

                Surface(
                    onClick = { onSelect(page) },
                    shape = RoundedCornerShape(13.dp),
                    color = background,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 5.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (active) HudBlue else HudMuted
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                            color = if (active) Color.White else HudMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HudNavigationRail(
    selected: HudPage,
    width: Float,
    onSelect: (HudPage) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(width.dp),
        color = HudCard,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp, horizontal = 3.dp)
                .selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HudPage.values().forEach { page ->
                val active = selected == page
                val title = hudString(page.label)
                val pillColor by animateColorAsState(
                    targetValue = if (active) HudBlueBadge.copy(alpha = 0.35f) else Color.Transparent,
                    animationSpec = tween(200),
                    label = "rail_pill_$title"
                )
                val iconTint by animateColorAsState(
                    targetValue = if (active) HudBlue else HudMuted,
                    animationSpec = tween(200),
                    label = "rail_icon_$title"
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .selectable(
                            selected = active,
                            onClick = { onSelect(page) },
                            role = Role.Tab
                        )
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(pillColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = iconTint
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) Color.White else HudMuted
                    )
                }
            }
        }
    }
}

@Composable
internal fun HudPageMenu(selected: HudPage, onSelect: (HudPage) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .selectableGroup()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HudPage.values().forEach { page ->
            HudChoice(
                label = hudString(page.label),
                selected = selected == page,
                onClick = { onSelect(page) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
