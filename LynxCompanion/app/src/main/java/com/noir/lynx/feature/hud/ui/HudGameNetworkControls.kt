package com.noir.lynx.feature.hud.ui

import android.net.TrafficStats
import android.os.Build
import android.os.SystemClock
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.feature.hud.addins.*
import com.noir.lynx.core.locale.LynxText as Text
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun HudGameNetworkControls(showSettings: Boolean = true) {
    val runtime = HudGamingAddins.current ?: return
    val boost by runtime.wifiBoost.collectAsState()
    val turboEnabled by runtime.networkTurbo.turboEnabled.collectAsState()
    val cellularStandby by runtime.networkTurbo.cellularStandby.collectAsState()
    val tcpTuning by runtime.networkTurbo.tcpTuning.collectAsState()
    val qos by runtime.qos.state.collectAsState()
    val pkg by runtime.magic.foreground.collectAsState()
    var network by remember { mutableStateOf(runtime.network()) }
    var speed by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(runtime) {
        var previous = TrafficStats.getTotalRxBytes()
        var at = SystemClock.elapsedRealtime()
        while (true) {
            delay(1000)
            network = runtime.network()
            val now = TrafficStats.getTotalRxBytes(); val time = SystemClock.elapsedRealtime()
            speed = if (previous >= 0 && now >= previous && time > at) {
                String.format(Locale.US, "%.2f Mbps", (now - previous) * .008 / (time - at))
            } else null
            previous = now; at = time
        }
    }

    if (!showSettings) {
        Text(hudString(R.string.hud_game_network_status,
            if (network.transport == "Mobile") hudString(R.string.hud_network_mobile) else network.transport,
            hudString(if (network.validated) R.string.hud_network_validated else R.string.hud_network_unvalidated)), fontSize = 12.sp, color = HudBlue)
        Text(hudString(R.string.hud_network_throughput, speed ?: "—"), fontSize = 12.sp, color = HudMuted)

        Text(hudString(R.string.hud_network_dual,
            hudString(if (network.wifi) R.string.hud_active else R.string.hud_addin_inactive),
            hudString(if (network.cellular) R.string.hud_active else R.string.hud_addin_inactive)), fontSize = 12.sp, color = HudMuted)
    } else {
        // Dual-Channel Network Turbo Switches
        AddinToggle(
            label = hudString(R.string.hud_net_turbo_toggle),
            checked = turboEnabled,
            info = hudString(R.string.hud_net_turbo_desc),
            onChange = runtime.networkTurbo::setTurboEnabled
        )

        if (turboEnabled) {
            AddinToggle(
                label = hudString(R.string.hud_net_turbo_cellular),
                checked = cellularStandby,
                info = hudString(R.string.hud_network_cellular_help),
                onChange = runtime.networkTurbo::setCellularStandby
            )
            AddinToggle(
                label = hudString(R.string.hud_net_turbo_tcp),
                checked = tcpTuning,
                info = hudString(R.string.hud_network_tcp_help),
                onChange = runtime.networkTurbo::setTcpTuning
            )
        }

        AddinToggle(
            label = hudString(R.string.hud_wifi_latency),
            checked = boost,
            enabled = Build.VERSION.SDK_INT >= 29 && (network.wifi || boost),
            info = hudString(R.string.hud_wifi_latency_note),
            onChange = runtime::setWifiBoost
        )

        Text(hudString(R.string.hud_network_dual,
            hudString(if (network.wifi) R.string.hud_active else R.string.hud_addin_inactive),
            hudString(if (network.cellular) R.string.hud_active else R.string.hud_addin_inactive)), fontSize = 12.sp, color = HudMuted)

        val qosNote = hudString(if (qos.capability == com.noir.lynx.feature.hud.data.HudCapability.READY) R.string.hud_network_qos_note else R.string.hud_network_qos_unavailable)
        AddinToggle(
            label = hudString(R.string.hud_network_qos),
            checked = qos.enabled,
            enabled = !qos.busy && qos.capability == com.noir.lynx.feature.hud.data.HudCapability.READY && validMagicPackage(pkg),
            info = qosNote,
            onChange = runtime::setGameQos
        )
    }
}
