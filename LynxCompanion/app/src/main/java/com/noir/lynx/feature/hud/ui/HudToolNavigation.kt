package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text

internal enum class HudToolPage(val title: Int, val isGaming: Boolean = false) {
    // 🎮 Alat Bermain (Gaming Utilities)
    Focus(R.string.hud_addin_focus, isGaming = true),
    Combo(R.string.hud_magic_title, isGaming = true),
    Recorder(R.string.hud_record_title, isGaming = true),
    Automation(R.string.hud_tool_automation, isGaming = true),
    Timer(R.string.hud_timer_title, isGaming = true),

    // ⚙️ Alat Sistem & Kustomisasi (System Tools)
    Display(R.string.hud_tool_display),
    Console(R.string.hud_console_title),
    Overlay(R.string.hud_tool_overlay),
    Session(R.string.hud_session_title)
}

internal val HudToolPage.icon: ImageVector
    get() = when (this) {
        HudToolPage.Focus -> Icons.Default.NotificationsOff
        HudToolPage.Combo -> Icons.Default.AutoFixHigh
        HudToolPage.Recorder -> Icons.Default.Videocam
        HudToolPage.Automation -> Icons.Default.Speed
        HudToolPage.Timer -> Icons.Default.Timer
        HudToolPage.Display -> Icons.Default.Brightness6
        HudToolPage.Console -> Icons.Default.Tune
        HudToolPage.Overlay -> Icons.AutoMirrored.Filled.ViewSidebar
        HudToolPage.Session -> Icons.Default.Schedule
    }

@Composable
internal fun HudToolNavigation(onSelect: (HudToolPage) -> Unit, onApps: () -> Unit) {
    val gamingTools = listOf(
        HudToolPage.Focus,
        HudToolPage.Combo,
        HudToolPage.Recorder,
        HudToolPage.Automation,
        HudToolPage.Timer
    )
    val systemTools = listOf(
        HudToolPage.Display,
        HudToolPage.Console,
        HudToolPage.Overlay,
        HudToolPage.Session
    )

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Group 1: Alat Bermain (Gaming Utilities)
        Text(
            text = hudString(R.string.hud_addin_group_tools),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = HudMuted
        )
        HudDestinationGrid(gamingTools.map { tool ->
            HudDestination(tool.title, tool.icon) { onSelect(tool) }
        })

        // Group 2: Alat Sistem & Kustomisasi (System Tools)
        Text(
            text = hudString(R.string.hud_tool_group_system),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = HudMuted
        )
        HudDestinationGrid(systemTools.map { tool ->
            HudDestination(tool.title, tool.icon) { onSelect(tool) }
        } + HudDestination(R.string.hud_apps_manage, Icons.Default.Apps, onApps))
    }
}
