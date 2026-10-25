package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun HudLab(
    snapshot: HudSnapshot,
    bypassStatus: HudBypassStatus,
    busy: Boolean,
    onToggleBypass: (Boolean) -> Unit,
    onRefreshBypass: () -> Unit,
    onProbeNetwork: suspend () -> HudNetworkSample
) {
    val scope = rememberCoroutineScope()
    var networkSample by remember { mutableStateOf<HudNetworkSample?>(null) }
    var networkBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRefreshBypass()
        while (true) {
            networkBusy = true
            networkSample = runCatching { onProbeNetwork() }.getOrNull()
            networkBusy = false
            delay(5_000L)
        }
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

    // 1. Bypass Charging Lab Card
    HudSection(
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
                value = if (current.status == "valid") "${current.text()} ${current.unit}" else "—",
                modifier = Modifier.weight(1f)
            )
            HudLabStatPill(
                label = hudString(R.string.hud_metric_battery_temp),
                value = if (temp.status == "valid") "${temp.text()} ${temp.unit}" else "—",
                modifier = Modifier.weight(1f)
            )
        }
    }

    // 2. Game Network Probe Lab Card
    val score = networkSample?.score ?: 0
    val scoreBadge = if (networkSample != null) {
        hudString(R.string.hud_net_score, score)
    } else {
        hudString(R.string.hud_cap_checking)
    }
    val scoreColor = when {
        networkSample == null -> HudBlue
        score >= 75 -> HudCyan
        score >= 45 -> HudOrange
        else -> HudRed
    }

    HudSection(
        title = hudString(R.string.hud_lab_network_title),
        badge = scoreBadge,
        accentColor = scoreColor
    ) {
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
                value = networkSample?.avgMs?.let { "${it.roundToInt()} ms" } ?: "—",
                modifier = Modifier.weight(1f)
            )
            HudLabStatPill(
                label = hudString(R.string.hud_net_jitter),
                value = networkSample?.jitterMs?.let { "${it.roundToInt()} ms" } ?: "—",
                modifier = Modifier.weight(1f)
            )
            HudLabStatPill(
                label = hudString(R.string.hud_net_loss),
                value = networkSample?.let { "${it.lossPct}%" } ?: "—",
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
