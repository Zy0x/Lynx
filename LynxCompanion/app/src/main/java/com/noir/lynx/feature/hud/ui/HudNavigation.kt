package com.noir.lynx.feature.hud.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text

internal val HudPage.label: Int
    get() = when (this) {
        HudPage.Summary -> R.string.hud_summary
        HudPage.Details -> R.string.hud_details
        HudPage.Tools -> R.string.hud_tools
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
                    animationSpec = tween(160),
                    label = "tab_bg_$title"
                )
                val icon = when (page) {
                    HudPage.Summary -> Icons.Default.Speed
                    HudPage.Details -> Icons.AutoMirrored.Filled.ShowChart
                    HudPage.Tools -> Icons.Default.Tune
                }

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
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (active) HudBlue else HudMuted
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = title,
                            fontSize = 11.5.sp,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                            color = if (active) Color.White else HudMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HudNavigationRail(selected: HudPage, width: Float, onSelect: (HudPage) -> Unit) {
    Column(
        modifier = Modifier
            .width(width.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HudPage.values().forEach { page ->
            val active = selected == page
            val title = hudString(page.label)
            val background by animateColorAsState(
                if (active) HudElevated else Color.Transparent,
                tween(160),
                label = "bg_$title"
            )
            Surface(
                onClick = { onSelect(page) },
                shape = RoundedCornerShape(14.dp),
                color = background,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .heightIn(min = 48.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val icon = when (page) {
                        HudPage.Summary -> Icons.Default.Speed
                        HudPage.Details -> Icons.AutoMirrored.Filled.ShowChart
                        HudPage.Tools -> Icons.Default.Tune
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (active) HudBlue else HudMuted
                    )
                    Text(
                        text = title,
                        fontSize = 11.5.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        color = if (active) Color.White else HudMuted,
                        softWrap = false
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
