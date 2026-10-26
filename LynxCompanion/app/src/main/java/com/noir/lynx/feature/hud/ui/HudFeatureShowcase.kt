package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text

/** Describes available tools without implying every hardware adapter is supported. */
@Composable
internal fun HudFeatureShowcase() {
    HudExpandableSection(hudString(R.string.hud_lab)) {
        listOf(R.string.hud_focus_title, R.string.hud_lab_bypass_title, R.string.hud_touch_title,
            R.string.hud_lab_network_title, R.string.hud_magic_title, R.string.hud_record_title,
            R.string.hud_console_title, R.string.hud_session_title).forEach { title ->
            Text(hudString(title), fontSize = 12.sp, color = HudMuted)
        }
    }
}
