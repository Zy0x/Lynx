package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.addins.HudGamingAddins
import com.noir.lynx.feature.hud.data.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HudTouchPage(
    touchStatus: HudTouchStatus,
    busy: Boolean,
    onToggleTouchBoost: (Boolean) -> Unit
) {
    var touchSample by remember { mutableStateOf(HudTouchSample()) }
    val runtime = HudGamingAddins.current
    val zoneTouch = runtime?.zoneTouch

    val esportsEnabled by zoneTouch?.esportsEnabled?.collectAsState() ?: remember { mutableStateOf(false) }
    val samplingBoost by zoneTouch?.samplingBoost?.collectAsState() ?: remember { mutableStateOf(false) }
    val touchSlop by zoneTouch?.touchSlop?.collectAsState() ?: remember { mutableStateOf(2) }

    val touchCapText = hudString(
        when (touchStatus.capability) {
            HudCapability.READY -> R.string.hud_cap_ready
            HudCapability.NEEDS_ROOT -> R.string.hud_cap_needs_root
            HudCapability.UNSUPPORTED -> R.string.hud_cap_unsupported
            HudCapability.CHECKING -> R.string.hud_cap_checking
        }
    )
    val touchCapColor = when (touchStatus.capability) {
        HudCapability.READY -> if (touchStatus.enabled) HudCyan else HudBlue
        HudCapability.NEEDS_ROOT -> HudOrange
        HudCapability.UNSUPPORTED -> HudMuted
        HudCapability.CHECKING -> HudBlue
    }

    val touchInfo = "${hudString(R.string.hud_lab_touch_desc)}\n\n" +
        "• Kernel CPU Touch Boost: Memaksa governor CPU melompat ke frekuensi tertinggi seketika jari menyentuh layar.\n\n" +
        "• Esports Low-Latency: Prioritas real-time SCHED_FIFO pada thread InputReader & InputDispatcher Android.\n\n" +
        "• Sampling Rate Boost: Memaksa digitizer IC hardware ke kecepatan baca maksimal (hingga 240Hz/480Hz).\n\n" +
        "• Ultra-Sensitive Touch Slop (2px): Responsivitas gesekan instan dengan meminimalkan deadzone pergerakan jari."

    // 2 Subpages: 0 = Setelan (Controls & Fine Tuning), 1 = Uji Sentuh (Spacious Test Pad)
    HudSubpages(listOf(R.string.hud_detail_controls, R.string.hud_detail_test)) { section ->
        if (section == 0) {
            HudSection(
                title = hudString(R.string.hud_lab_touch_title),
                badge = touchCapText,
                accentColor = touchCapColor,
                info = touchInfo
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 1. Master Kernel Touch Boost Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = hudString(R.string.hud_touch_boost_label),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = if (touchStatus.backendLabel.isNotBlank()) touchStatus.backendLabel else hudString(R.string.hud_lab_touch_desc),
                                fontSize = 10.5.sp,
                                fontFamily = if (touchStatus.backendLabel.isNotBlank()) FontFamily.Monospace else FontFamily.Default,
                                color = if (touchStatus.enabled) HudCyan else HudMuted
                            )
                        }
                        Switch(
                            checked = touchStatus.enabled,
                            onCheckedChange = { onToggleTouchBoost(it) },
                            enabled = touchStatus.capability == HudCapability.READY && !busy,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = HudBlueBadge,
                                uncheckedThumbColor = HudMuted,
                                uncheckedTrackColor = HudElevated
                            )
                        )
                    }

                    // 2. Esports & Digitizer Fine-Tuning
                    if (zoneTouch != null) {
                        AddinToggle(
                            label = hudString(R.string.hud_zonetouch_esports),
                            checked = esportsEnabled,
                            info = "Memaksa InputReader ke prioritas real-time (RT) untuk memangkas latensi input.",
                            onChange = zoneTouch::setEsportsEnabled
                        )

                        if (esportsEnabled) {
                            AddinToggle(
                                label = hudString(R.string.hud_zonetouch_sampling),
                                checked = samplingBoost,
                                info = "Menaikkan touch reporting rate digitizer ke batas maksimal.",
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
                        }
                    }
                }
            }
        } else {
            // Uji Sentuh (Spacious Multi-Touch Pad >= 160dp)
            HudSection(
                title = hudString(R.string.hud_lab_touch_title),
                badge = if ((touchSample.rateHz ?: 0) > 0) "${touchSample.rateHz} Hz" else "Siap Uji",
                accentColor = if ((touchSample.rateHz ?: 0) > 0) HudCyan else HudBlue,
                info = "Pad Uji Sentuh Interaktif:\n\nSentuh, ketuk, atau geser beberapa jari bersamaan di dalam area pad untuk mengukur responsivitas sampling layar (Hz) dan latensi interval (ms) secara real-time."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .pointerInput(Unit) {
                                val timestamps = ArrayList<Long>(32)
                                var maxFingers = 0
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) {
                                            timestamps.clear()
                                            continue
                                        }
                                        if (pressed.size > maxFingers) {
                                            maxFingers = pressed.size
                                        }
                                        val primary = pressed.first()
                                        for (hist in primary.historical) {
                                            if (timestamps.isEmpty() || hist.uptimeMillis > timestamps.last()) {
                                                timestamps.add(hist.uptimeMillis)
                                            }
                                        }
                                        if (timestamps.isEmpty() || primary.uptimeMillis > timestamps.last()) {
                                            timestamps.add(primary.uptimeMillis)
                                        }
                                        while (timestamps.size > 24) {
                                            timestamps.removeAt(0)
                                        }
                                        val deltas = ArrayList<Double>(timestamps.size)
                                        for (i in 1 until timestamps.size) {
                                            val dt = (timestamps[i] - timestamps[i - 1]).toDouble()
                                            if (dt in 1.0..120.0) deltas.add(dt)
                                        }
                                        if (deltas.isNotEmpty() || pressed.isNotEmpty()) {
                                            touchSample = computeTouchSampleMetrics(deltas, pressed.size, maxFingers)
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            },
                        color = HudElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = if ((touchSample.rateHz ?: 0) > 0) HudCyan else HudBorder
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = if ((touchSample.rateHz ?: 0) > 0) HudCyan else HudMuted,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = if ((touchSample.rateHz ?: 0) > 0)
                                        "Mendeteksi Sentuhan Aktif (${touchSample.activeFingers} Jari)"
                                    else
                                        hudString(R.string.hud_touch_pad_hint),
                                    fontSize = 12.sp,
                                    fontWeight = if ((touchSample.rateHz ?: 0) > 0) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if ((touchSample.rateHz ?: 0) > 0) HudCyan else HudMuted,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Area luas untuk uji multi-finger swipe & tap gesture",
                                    fontSize = 10.5.sp,
                                    color = HudMuted.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Stat Grid Real-Time
                    HudStatGrid(listOf(
                        hudString(R.string.hud_touch_rate) to (touchSample.rateHz?.let { "$it Hz" } ?: "—"),
                        hudString(R.string.hud_touch_interval) to (touchSample.intervalMs?.let { String.format(java.util.Locale.US, "%.1f ms", it) } ?: "—"),
                        hudString(R.string.hud_touch_fingers) to (if (touchSample.maxFingers > 0) hudString(R.string.hud_touch_fingers_val, touchSample.maxFingers) else "—")
                    ))
                }
            }
        }
    }
}
