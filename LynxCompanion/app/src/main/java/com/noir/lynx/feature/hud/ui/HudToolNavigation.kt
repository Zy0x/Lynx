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
    // 🎮 Alat Bermain (Gaming Tools)
    GameShift(R.string.hud_gameshift_title, isGaming = true),
    Focus(R.string.hud_addin_focus, isGaming = true),
    Touch(R.string.hud_lab_touch_title, isGaming = true),
    Network(R.string.hud_lab_network_title, isGaming = true),
    Bypass(R.string.hud_lab_bypass_title, isGaming = true),
    Combo(R.string.hud_magic_title, isGaming = true),
    Recorder(R.string.hud_record_title, isGaming = true),
    Console(R.string.hud_console_title, isGaming = true),
    Session(R.string.hud_session_title, isGaming = true),

    // ⚙️ Alat Sistem & Tampilan (System Tools)
    Display(R.string.hud_tool_display),
    Overlay(R.string.hud_tool_overlay),
    Automation(R.string.hud_tool_automation),
    Timer(R.string.hud_timer_title)
}

internal val HudToolPage.icon: ImageVector
    get() = when (this) {
        HudToolPage.GameShift -> Icons.Default.Bolt
        HudToolPage.Focus -> Icons.Default.NotificationsOff
        HudToolPage.Touch -> Icons.Default.TouchApp
        HudToolPage.Network -> Icons.Default.NetworkCheck
        HudToolPage.Bypass -> Icons.Default.BatteryChargingFull
        HudToolPage.Combo -> Icons.Default.AutoFixHigh
        HudToolPage.Recorder -> Icons.Default.Videocam
        HudToolPage.Console -> Icons.Default.Tune
        HudToolPage.Session -> Icons.Default.Schedule
        HudToolPage.Display -> Icons.Default.Brightness6
        HudToolPage.Overlay -> Icons.AutoMirrored.Filled.ViewSidebar
        HudToolPage.Automation -> Icons.Default.Speed
        HudToolPage.Timer -> Icons.Default.Timer
    }

@Composable
internal fun HudToolNavigation(onSelect: (HudToolPage) -> Unit, onApps: () -> Unit) {
    val gamingTools = listOf(
        HudToolPage.GameShift,
        HudToolPage.Focus,
        HudToolPage.Touch,
        HudToolPage.Network,
        HudToolPage.Bypass,
        HudToolPage.Combo,
        HudToolPage.Recorder,
        HudToolPage.Console,
        HudToolPage.Session
    )
    val systemTools = listOf(
        HudToolPage.Display,
        HudToolPage.Overlay,
        HudToolPage.Automation,
        HudToolPage.Timer
    )

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Group 1: Alat Bermain
        Text(
            text = hudString(R.string.hud_addin_group_tools),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = HudMuted
        )
        HudDestinationGrid(gamingTools.map { tool ->
            HudDestination(tool.title, tool.icon) { onSelect(tool) }
        })

        // Group 2: Alat Sistem
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
