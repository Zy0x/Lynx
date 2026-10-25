package com.noir.lynx.feature.hud.ui

import android.net.TrafficStats
import android.os.Build
import android.os.SystemClock
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
import com.noir.lynx.R
import com.noir.lynx.feature.hud.addins.*
import com.noir.lynx.core.locale.LynxText as Text
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun HudAddinNavigation(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(R.string.hud_addin_focus, R.string.hud_lab_bypass_title, R.string.hud_lab_touch_title,
            R.string.hud_lab_network_title, R.string.hud_magic_title).forEachIndexed { index, title ->
            FilterChip(selected == index, { onSelect(index) }, label = { Text(hudString(title), fontSize = 12.sp) },
                modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
private fun AddinToggle(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(checked, onChange, enabled = enabled)
    }
}

@Composable
internal fun HudGlowControls() {
    val runtime = HudGamingAddins.current ?: return
    val enabled by runtime.glow.collectAsState()
    val color by runtime.color.collectAsState()
    val connected by runtime.listenerConnected.collectAsState()
    AddinToggle(hudString(R.string.hud_glow_toggle), enabled, onChange = runtime::setGlow)
    Text(hudString(R.string.hud_glow_description), fontSize = 12.sp, color = HudMuted)
    if (!connected) {
        HudAction(hudString(R.string.hud_glow_grant), onClick = runtime::openNotificationAccess, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(R.string.hud_glow_cyan, R.string.hud_glow_blue, R.string.hud_glow_amber).forEachIndexed { index, label ->
            FilterChip(color == index, { runtime.setColor(index) }, label = { Text(hudString(label), fontSize = 12.sp) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    HudAction(hudString(R.string.hud_glow_preview), onClick = { runtime.previewGlow() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
}

@Composable
internal fun HudGameNetworkControls() {
    val runtime = HudGamingAddins.current ?: return
    val boost by runtime.wifiBoost.collectAsState()
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
    Text(hudString(R.string.hud_network_throughput, speed ?: "â€”"), fontSize = 12.sp, color = HudMuted)
    AddinToggle(hudString(R.string.hud_wifi_latency), boost,
        enabled = Build.VERSION.SDK_INT >= 29 && (network.wifi || boost), onChange = runtime::setWifiBoost)
    Text(hudString(R.string.hud_wifi_latency_note), fontSize = 12.sp, color = HudMuted)
    Text(hudString(R.string.hud_network_dual,
        hudString(if (network.wifi) R.string.hud_active else R.string.hud_addin_inactive),
        hudString(if (network.cellular) R.string.hud_active else R.string.hud_addin_inactive)), fontSize = 12.sp, color = HudMuted)
    Text(hudString(R.string.hud_network_bonding_note), fontSize = 12.sp, color = HudMuted)
    AddinToggle(hudString(R.string.hud_network_qos), qos.enabled,
        enabled = !qos.busy && qos.capability == com.noir.lynx.feature.hud.data.HudCapability.READY && validMagicPackage(pkg),
        onChange = runtime::setGameQos)
    Text(hudString(if (qos.capability == com.noir.lynx.feature.hud.data.HudCapability.READY) R.string.hud_network_qos_note else R.string.hud_network_qos_unavailable), fontSize = 12.sp, color = HudMuted)
}

@Composable
internal fun HudMagicBoxControls() {
    val runtime = HudGamingAddins.current ?: return
    val points by runtime.magic.points.collectAsState()
    val interval by runtime.magic.interval.collectAsState()
    val running by runtime.magic.running.collectAsState()
    val root by runtime.rootAvailable.collectAsState()
    val pkg by runtime.magic.foreground.collectAsState()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    selected = selected.coerceIn(0, points.lastIndex)
    val point = points[selected]
    val xLabel = hudString(R.string.hud_magic_x)
    val yLabel = hudString(R.string.hud_magic_y)
    val delayLabel = hudString(R.string.hud_magic_delay)
    HudSection(title = hudString(R.string.hud_magic_title), badge = hudString(if (running) R.string.hud_active else R.string.hud_addin_inactive)) {
        Text(hudString(R.string.hud_magic_description), fontSize = 12.sp, color = HudMuted)
        Text(pkg.ifBlank { "â€”" }, fontSize = 12.sp, color = HudBlue)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            points.forEachIndexed { index, _ ->
                FilterChip(selected == index, { selected = index }, label = { Text(hudString(R.string.hud_magic_point, index + 1)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        Text(hudString(R.string.hud_magic_position, point.x, point.y), fontSize = 12.sp, color = HudMuted)
        Slider(point.x.toFloat(), { x -> runtime.magic.setPoints(points.toMutableList().apply { this[selected] = point.copy(x = x.toInt()) }) },
            valueRange = 1f..99f, enabled = !running, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = xLabel })
        Slider(point.y.toFloat(), { y -> runtime.magic.setPoints(points.toMutableList().apply { this[selected] = point.copy(y = y.toInt()) }) },
            valueRange = 1f..99f, enabled = !running, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = yLabel })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HudAction(hudString(R.string.hud_magic_add), enabled = !running && points.size < 5,
                onClick = { runtime.magic.setPoints(points + HudTapPoint()); selected = points.size }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
            HudAction(hudString(R.string.hud_magic_remove), enabled = !running && points.size > 1,
                onClick = { runtime.magic.setPoints(points.filterIndexed { i, _ -> i != selected }); selected = 0 }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
        }
        Text(hudString(R.string.hud_magic_interval, interval), fontSize = 12.sp, color = HudMuted)
        Slider(interval.toFloat(), { runtime.magic.setInterval(it.toInt()) }, valueRange = 750f..5000f, enabled = !running, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = delayLabel })
        HudAction(hudString(if (running) R.string.hud_magic_stop else R.string.hud_magic_start),
            enabled = running || (root && validMagicPackage(pkg)), onClick = { if (running) runtime.magic.stop() else runtime.startMagic() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), isDanger = running)
        Text(hudString(R.string.hud_magic_limits), fontSize = 12.sp, color = HudMuted)
        if (!root) Text(hudString(R.string.hud_cap_needs_root), fontSize = 12.sp, color = HudOrange)
    }
}
