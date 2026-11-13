package com.noir.lynx.feature.hud.ui

import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.feature.hud.addins.*
import com.noir.lynx.core.locale.LynxText as Text

@Composable
internal fun AddinToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    info: String? = null,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            if (!info.isNullOrBlank()) {
                HudInfoButton(title = label, info = info)
            }
        }
        Switch(checked, onChange, enabled = enabled, colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = HudBlueBadge,
            uncheckedThumbColor = HudMuted, uncheckedTrackColor = HudElevated))
    }
}

/**
* Universal ZoneTouch Master Controls.
*/
@Composable
internal fun HudZoneTouchControls() {
    val runtime = HudGamingAddins.current ?: return
    val zoneTouch = runtime.zoneTouch
    val esportsEnabled by zoneTouch.esportsEnabled.collectAsState()
    val samplingBoost by zoneTouch.samplingBoost.collectAsState()

    val zoneTouchInfo = "${hudString(R.string.hud_zonetouch_desc)}\n\n" +
    "• Esports Low-Latency Touch: Memaksa responsivitas touch event ke prioritas tertinggi di subsistem Android InputReader.\n\n" +
    "• Sampling Rate Boost: Meningkatkan touch report rate hingga batas maksimal hardware digitizer (240Hz - 480Hz)."

    HudSection(
        title = hudString(R.string.hud_zonetouch_title),
        badge = hudString(if (esportsEnabled) R.string.hud_active else R.string.hud_addin_inactive),
        accentColor = if (esportsEnabled) HudCyan else HudBlue,
        info = zoneTouchInfo
    ) {
        AddinToggle(
            label = hudString(R.string.hud_zonetouch_esports),
            checked = esportsEnabled,
            onChange = zoneTouch::setEsportsEnabled
        )

        if (esportsEnabled) {
            AddinToggle(
                label = hudString(R.string.hud_zonetouch_sampling),
                checked = samplingBoost,
                onChange = zoneTouch::setSamplingBoost
            )
        }
    }
}

@Composable
internal fun HudMagicBoxControls() {
    // Backwards-compatible router to HudComboControls
    HudComboControls()
}
