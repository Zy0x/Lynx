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
        hudAddinTitles.forEach { title ->
            Text(hudString(title), fontSize = 12.sp, color = HudMuted)
        }
    }
}
