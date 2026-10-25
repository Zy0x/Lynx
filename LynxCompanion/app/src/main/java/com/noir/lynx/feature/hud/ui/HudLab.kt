package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HudLab(
    snapshot: HudSnapshot,
    bypassStatus: HudBypassStatus,
    busy: Boolean,
    onToggleBypass: (Boolean) -> Unit,
    onRefreshBypass: () -> Unit,
    onProbeNetwork: suspend () -> HudNetworkSample,
    touchStatus: HudTouchStatus = HudTouchStatus(),
    shieldStatus: HudShieldStatus = HudShieldStatus(),
    onToggleTouchBoost: (Boolean) -> Unit = {},
    onToggleHeadsUpShield: (Boolean) -> Unit = {},
    onSelectFocusLevel: (Int) -> Unit = {},
    selectedAddin: Int = -1,
    onSelectAddin: (Int) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var networkSample by remember { mutableStateOf<HudNetworkSample?>(null) }
    var networkBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRefreshBypass()

    }

    val capText = hudString(
        when (bypassStatus.capability) {
            HudCapability.READY -> R.string.hud_cap_ready
            HudCapability.NEEDS_ROOT -> R.string.hud_cap_needs_root
            HudCapability.UNSUPPORTED -> R.string.hud_cap_unsupported
            HudCapability.CHECKING -> R.string.hud_cap_checking
        }
    )
    val capColor = when (bypassStatus.capability) {
        HudCapability.READY -> HudCyan
        HudCapability.NEEDS_ROOT -> HudOrange
        HudCapability.UNSUPPORTED -> HudMuted
        HudCapability.CHECKING -> HudBlue
    }

    if (selectedAddin == -1) HudAddinNavigation(onSelectAddin)
    if (selectedAddin == 4) HudMagicBoxControls()

    // 1. Bypass Charging Lab Card
    if (selectedAddin == 1) HudSection(
        title = hudString(R.string.hud_lab_bypass_title),
        badge = capText,
        accentColor = capColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = hudString(R.string.hud_lab_bypass_desc),
                    fontSize = 11.5.sp,
                    color = HudMuted
                )
                if (bypassStatus.chipSummary.isNotBlank()) {
                    Text(
                        text = bypassStatus.chipSummary,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HudBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Switch(
                checked = bypassStatus.enabled || bypassStatus.active,
                onCheckedChange = { onToggleBypass(it) },
                enabled = bypassStatus.capability == HudCapability.READY && !busy,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = HudBlueBadge,
                    uncheckedThumbColor = HudMuted,
                    uncheckedTrackColor = HudElevated
                )
            )
        }

        val current = snapshot[HudMetric.BATTERY_CURRENT]
        val temp = snapshot[HudMetric.BATTERY_TEMP]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HudLabStatPill(
                label = hudString(R.string.hud_metric_current),
                value = if (current.status == "valid") "${current.text()} ${current.unit}" else "â€”",
                modifier = Modifier.weight(1f)
            )
            HudLabStatPill(
                label = hudString(R.string.hud_metric_battery_temp),
                value = if (temp.status == "valid") "${temp.text()} ${temp.unit}" else "â€”",
                modifier = Modifier.weight(1f)
            )
        }
    }

    // 2. Game Network Probe Lab Card
    val score = networkSample?.score ?: 0
    val scoreBadge = if (networkSample != null) {
        hudString(R.string.hud_net_score, score)
    } else {
        hudString(R.string.hud_network_manual_test)
    }
    val scoreColor = when {
        networkSample == null -> HudBlue
        score >= 75 -> HudCyan
        score >= 45 -> HudOrange
        else -> HudRed
    }

    if (selectedAddin == 3) HudSection(
        title = hudString(R.string.hud_lab_network_title),
        badge = scoreBadge,
        accentColor = scoreColor
    ) {
        HudGameNetworkControls()
        Text(
            text = hudString(R.string.hud_lab_network_desc),
            fontSize = 11.5.sp,
            color = HudMuted
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HudLabStatPill(
                label = hudString(R.string.hud_net_ping),
                value = networkSample?.avgMs?.let { "${it.roundToInt()} ms" } ?: "â€”",
                modifier = Modifier.weight(1f)
            )
            HudLabStatPill(
                label = hudString(R.string.hud_net_jitter),
                value = networkSample?.jitterMs?.let { "${it.roundToInt()} ms" } ?: "â€”",
                modifier = Modifier.weight(1f)
            )
            HudLabStatPill(
                label = hudString(R.string.hud_net_loss),
                value = networkSample?.let { "${it.lossPct}%" } ?: "â€”",
                modifier = Modifier.weight(1f)
            )
        }

        HudAction(
            label = if (networkBusy) hudString(R.string.hud_cap_checking) else hudString(R.string.hud_retest),
            onClick = {
                if (!networkBusy) {
                    scope.launch {
                        networkBusy = true
                        networkSample = runCatching { onProbeNetwork() }.getOrNull()
                        networkBusy = false
                    }
                }
            },
            enabled = !networkBusy,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // 3. Touch Master (Boost & Live Touch Sampling Pad)
    var touchSample by remember { mutableStateOf(HudTouchSample()) }
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

    if (selectedAddin == 2) HudSection(
        title = hudString(R.string.hud_lab_touch_title),
        badge = touchCapText,
        accentColor = touchCapColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    color = if (touchStatus.enabled) HudCyan else HudMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
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
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if ((touchSample.rateHz ?: 0) > 0) HudCyan.copy(alpha = 0.45f) else HudBorder
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = hudString(R.string.hud_touch_pad_hint),
                    fontSize = 11.sp,
                    color = if ((touchSample.rateHz ?: 0) > 0) HudCyan else HudMuted,
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HudLabStatPill(
                label = hudString(R.string.hud_touch_rate),
                value = touchSample.rateHz?.let { "$it Hz" } ?: "â€”",
                modifier = Modifier.weight(1f)
            )
            HudLabStatPill(
                label = hudString(R.string.hud_touch_interval),
                value = touchSample.intervalMs?.let { String.format(java.util.Locale.US, "%.1f ms", it) } ?: "â€”",
                modifier = Modifier.weight(1f)
            )
            HudLabStatPill(
                label = hudString(R.string.hud_touch_fingers),
                value = if (touchSample.maxFingers > 0) hudString(R.string.hud_touch_fingers_val, touchSample.maxFingers) else "â€”",
                modifier = Modifier.weight(1f)
            )
        }
    }

    // 4. Notification & Focus Shield Card
    val shieldActive = shieldStatus.headsUpBlocked || shieldStatus.zenMode > 0
    if (selectedAddin == 0) HudSection(
        title = hudString(R.string.hud_lab_shield_title),
        badge = hudString(if (shieldActive) R.string.hud_active else R.string.hud_cap_ready),
        accentColor = if (shieldActive) HudCyan else HudBlue
    ) {
        HudGlowControls()
        Text(
            text = hudString(R.string.hud_lab_shield_desc),
            fontSize = 11.5.sp,
            color = HudMuted
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = hudString(R.string.hud_shield_headsup),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = shieldStatus.headsUpBlocked,
                onCheckedChange = { onToggleHeadsUpShield(it) },
                enabled = shieldStatus.capability == HudCapability.READY && !busy,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = HudBlueBadge,
                    uncheckedThumbColor = HudMuted,
                    uncheckedTrackColor = HudElevated
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val focusLevels = listOf(
                0 to hudString(R.string.hud_shield_mode_off),
                1 to hudString(R.string.hud_shield_mode_priority),
                2 to hudString(R.string.hud_shield_mode_silent),
                3 to hudString(R.string.hud_shield_mode_alarms)
            )
            focusLevels.forEach { (level, label) ->
                val selected = when (level) {
                    0 -> shieldStatus.zenMode == 0
                    else -> shieldStatus.zenMode == level
                }
                HudChoice(
                    label = label,
                    selected = selected,
                    onClick = { onSelectFocusLevel(level) },
                    modifier = Modifier.widthIn(min = 84.dp).heightIn(min = 48.dp),
                    enabled = !busy && shieldStatus.capability == HudCapability.READY
                )
            }
        }
    }
}

@Composable
private fun HudLabStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = HudElevated,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = HudMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
