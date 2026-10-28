package com.noir.lynx.feature.hud.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.TrafficStats
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import com.noir.lynx.R
import com.noir.lynx.feature.hud.addins.*
import com.noir.lynx.feature.hud.combo.ExecutionMode
import com.noir.lynx.feature.hud.combo.MacroCombo
import com.noir.lynx.feature.hud.combo.TouchShareParser
import com.noir.lynx.core.locale.LynxText as Text
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun HudGameNetworkControls() {
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

    Text(hudString(R.string.hud_game_network_status,
        if (network.transport == "Mobile") hudString(R.string.hud_network_mobile) else network.transport,
        hudString(if (network.validated) R.string.hud_network_validated else R.string.hud_network_unvalidated)), fontSize = 12.sp, color = HudBlue)
    Text(hudString(R.string.hud_network_throughput, speed ?: "—"), fontSize = 12.sp, color = HudMuted)

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
            info = "Menjaga koneksi data seluler tetap aktif di latar belakang saat terhubung ke Wi-Fi sehingga failover ke seluler instan (0 ms) saat sinyal Wi-Fi terputus.",
            onChange = runtime.networkTurbo::setCellularStandby
        )
        AddinToggle(
            label = hudString(R.string.hud_net_turbo_tcp),
            checked = tcpTuning,
            info = "Menerapkan TCP BBR congestion control, TCP window scaling, dan buffer tuning untuk meminimalkan bufferbloat dan jitter koneksi game.",
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
