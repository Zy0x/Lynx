package com.noir.lynx.feature.hud.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.feature.hud.data.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun HudNetworkPage(onProbeNetwork: suspend () -> HudNetworkSample) {
    val scope = rememberCoroutineScope()
    var networkSample by remember { mutableStateOf<HudNetworkSample?>(null) }
    var networkBusy by remember { mutableStateOf(false) }
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

    HudSubpages(listOf(R.string.hud_detail_connection, R.string.hud_detail_options, R.string.hud_detail_test)) { section ->
        HudSection(hudString(R.string.hud_lab_network_title), badge = scoreBadge, accentColor = scoreColor,
            info = hudString(R.string.hud_lab_network_desc)) {
            if (section != 2) HudGameNetworkControls(showSettings = section == 1)
            else {
                HudStatGrid(listOf(
                    hudString(R.string.hud_net_ping) to (networkSample?.avgMs?.let { "${it.roundToInt()} ms" } ?: "—"),
                    hudString(R.string.hud_net_jitter) to (networkSample?.jitterMs?.let { "${it.roundToInt()} ms" } ?: "—"),
                    hudString(R.string.hud_net_loss) to (networkSample?.let { "${it.lossPct}%" } ?: "—")
                ))
                Text(hudString(R.string.hud_lab_network_desc), fontSize = 12.sp, color = HudMuted)
                HudAction(hudString(if (networkBusy) R.string.hud_cap_checking else R.string.hud_retest),
                    enabled = !networkBusy, onClick = {
                        if (!networkBusy) scope.launch {
                            networkBusy = true
                            networkSample = runCatching { onProbeNetwork() }.getOrNull()
                            networkBusy = false
                        }
                    }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
