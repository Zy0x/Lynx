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
    val touchSlop by zoneTouch.touchSlop.collectAsState()
    val edgeDeadzone by zoneTouch.edgeDeadzone.collectAsState()

    val zoneTouchInfo = "${hudString(R.string.hud_zonetouch_desc)}\n\n" +
    "• Esports Low-Latency Touch: Memaksa responsivitas touch event ke prioritas tertinggi di subsistem Android InputReader.\n\n" +
    "• Sampling Rate Boost: Meningkatkan touch report rate hingga batas maksimal hardware digitizer (240Hz - 480Hz).\n\n" +
    "• Ultra-Sensitive Touch Slop (2px): Mengurangi ambang batas jarak pergerakan jari sebelum diakui sebagai gesture scroll/swipe, membuat sentuhan sangat peka.\n\n" +
    "• Anti-Mistouch Edge Deadzone: Mengatur lebar area mati di tepi layar (0-32px) untuk mencegah ketidaksengajaan sentuhan telapak tangan saat memegang HP."

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

            AddinToggle(
                label = hudString(R.string.hud_zonetouch_slop),
                checked = touchSlop <= 2,
                info = hudString(R.string.hud_zonetouch_slop_desc),
                onChange = { ultra ->
                    zoneTouch.setTouchSlop(if (ultra) 2 else 8)
                }
            )

            Text(hudString(R.string.hud_zonetouch_deadzone, edgeDeadzone), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Slider(
                value = edgeDeadzone.toFloat(),
                onValueChange = { zoneTouch.setEdgeDeadzone(it.toInt()) },
                valueRange = 0f..32f,
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }
    }
}

/**
* Universal GameShift / Heavy Mode Reactive Turbo Controls.
*/
@Composable
internal fun HudGameShiftControls() {
    val runtime = HudGamingAddins.current ?: return
    val gameShift = runtime.gameShift
    val enabled by gameShift.enabled.collectAsState()
    val isBurst by gameShift.isBurstActive.collectAsState()
    val spikes by gameShift.spikeCount.collectAsState()

    val gameShiftInfo = "${hudString(R.string.hud_gameshift_desc)}\n\n" +
    "• Reaktif Spike FPS Drop: Otomatis mendeteksi saat terjadi drop frame berat atau lonjakan beban CPU/GPU saat teamfight di game.\n\n" +
    "• CPU/GPU Clock Burst: Memberikan burst boost sesaat pada frekuensi inti CPU dan GPU untuk menstabilkan 60/90/120 FPS secara instan."

    HudSection(
        title = hudString(R.string.hud_gameshift_title),
        badge = if (isBurst) "🚀 TURBO BURST" else hudString(if (enabled) R.string.hud_active else R.string.hud_addin_inactive),
        accentColor = if (isBurst) HudRed else (if (enabled) HudCyan else HudBlue),
        info = gameShiftInfo
    ) {

        AddinToggle(
            label = hudString(R.string.hud_gameshift_toggle),
            checked = enabled,
            onChange = gameShift::setEnabled
        )

        Text(hudString(R.string.hud_gameshift_status, spikes), fontSize = 12.sp, color = if (isBurst) HudCyan else HudMuted)
    }
}

@Composable
internal fun HudMagicBoxControls() {
    // Backwards-compatible router to HudComboControls
    HudComboControls()
}
