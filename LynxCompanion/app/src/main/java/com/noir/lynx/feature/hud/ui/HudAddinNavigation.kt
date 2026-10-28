package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.font.FontWeight

internal val hudAddinTitles = listOf(
    R.string.hud_addin_focus,          // 0
    R.string.hud_lab_bypass_title,     // 1
    R.string.hud_lab_touch_title,      // 2
    R.string.hud_lab_network_title,    // 3
    R.string.hud_magic_title,          // 4
    R.string.hud_record_title,         // 5
    R.string.hud_console_title,        // 6
    R.string.hud_session_title,        // 7
    R.string.hud_gameshift_title       // 8
)

@Composable
internal fun HudAddinNavigation(onSelect: (Int) -> Unit) {
    val icons = listOf(
        Icons.Default.NotificationsOff, Icons.Default.BatteryChargingFull,
        Icons.Default.TouchApp, Icons.Default.NetworkCheck, Icons.Default.AutoFixHigh,
        Icons.Default.Videocam, Icons.Default.Tune, Icons.Default.Timer, Icons.Default.Bolt
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(listOf(8, 0, 2, 3, 1), listOf(4, 5, 6, 7)).forEachIndexed { group, indices ->
            Text(hudString(if (group == 0) R.string.hud_addin_group_game else R.string.hud_addin_group_tools),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HudMuted)
            HudDestinationGrid(indices.map { index ->
                HudDestination(hudAddinTitles[index], icons[index]) { onSelect(index) }
            })
        }
    }
}
