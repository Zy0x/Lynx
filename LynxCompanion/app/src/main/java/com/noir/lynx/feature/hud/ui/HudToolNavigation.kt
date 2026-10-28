package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text

internal enum class HudToolPage(val title: Int) {
    Display(R.string.hud_tool_display),
    Overlay(R.string.hud_tool_overlay),
    Automation(R.string.hud_tool_automation),
    Timer(R.string.hud_timer_title)
}

@Composable
internal fun HudToolNavigation(onSelect: (HudToolPage) -> Unit, onApps: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(hudString(R.string.hud_tool_navigation_hint), fontSize = 12.sp, color = HudMuted)
        HudDestinationGrid(listOf(
            HudDestination(HudToolPage.Display.title, Icons.Default.Brightness6) { onSelect(HudToolPage.Display) },
            HudDestination(HudToolPage.Overlay.title, Icons.AutoMirrored.Filled.ViewSidebar) { onSelect(HudToolPage.Overlay) },
            HudDestination(HudToolPage.Automation.title, Icons.Default.AutoFixHigh) { onSelect(HudToolPage.Automation) },
            HudDestination(HudToolPage.Timer.title, Icons.Default.Timer) { onSelect(HudToolPage.Timer) },
            HudDestination(R.string.hud_apps_manage, Icons.Default.Apps, onApps)
        ))
    }
}
