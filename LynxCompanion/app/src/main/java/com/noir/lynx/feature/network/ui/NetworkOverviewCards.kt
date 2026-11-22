package com.noir.lynx.feature.network.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.data.NetworkEngineInfo
import com.noir.lynx.designsystem.card.LynxCapabilityBadge
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.card.LynxSectionDivider
import com.noir.lynx.designsystem.card.LynxSectionHeader
import com.noir.lynx.designsystem.controls.LynxActionButton
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.applyNetworkPreset
import com.noir.lynx.ui.probeNetworkLatency
import com.noir.lynx.ui.refreshNetworkEngineAll
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetworkHeroCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val tele = uiState.wifiTelemetry
    val engine = uiState.networkEngineInfo
    var showInterfacesSheet by remember { mutableStateOf(false) }

    val isWifi = tele.isConnected
    val isCellular = !isWifi && (tele.cellularConnected || tele.activeTransport == "cellular" || engine.activeTransport == "cellular")
    val isEthernet = !isWifi && !isCellular && (tele.activeTransport == "ethernet" || engine.activeTransport == "ethernet")
    val isOnline = isWifi || isCellular || isEthernet || tele.activeIface.isNotBlank()

    val heroAccent = when {
        isWifi -> AccentGreen
        isCellular -> AccentCyan
        isEthernet -> AccentBlue
        else -> AccentOrange
    }
    val heroIcon = when {
        isWifi -> Icons.Default.Wifi
        isCellular -> Icons.Default.SignalCellularAlt
        isEthernet -> Icons.Default.SettingsEthernet
        else -> Icons.Default.WifiOff
    }
    val titleLine = when {
        isWifi -> tele.ssid
        isCellular -> listOf(tele.cellularOperator, tele.cellularNetworkType)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .ifBlank { "Data Seluler Aktif" }
        isEthernet -> "Koneksi Kabel / Ethernet"
        else -> "Jaringan Terputus / Offline"
    }
    val activeIface = tele.activeIface.ifBlank { engine.activeIface }
    val subtitleLine = when {
        isWifi -> buildString {
            append(tele.wifiStandard)
            if (tele.frequencyMhz > 0) append(" • ${tele.frequencyMhz} MHz")
            if (activeIface.isNotBlank()) append(" • $activeIface")
        }
        isCellular || isEthernet -> buildString {
            if (activeIface.isNotBlank()) append("Interface: $activeIface")
            val mtu = if (tele.activeMtu > 0) tele.activeMtu else engine.activeMtu
            if (mtu > 0) append(" • MTU $mtu")
            if (tele.ipAddress.isNotBlank()) append(" • ${tele.ipAddress}")
        }
        else -> "Aktifkan Wi-Fi atau Data Seluler untuk telemetri tautan"
    }

    if (showInterfacesSheet) {
        NetworkInterfacesBottomSheet(
            engine = engine,
            onDismiss = { showInterfacesSheet = false }
        )
    }

    LynxCard(
        title = "Monitor Tautan & Radio",
        subtitle = "Telemetri Wi-Fi, seluler, rute aktif & driver",
        icon = heroIcon,
        accentColor = heroAccent,
        action = {
            Surface(
                onClick = { viewModel.refreshNetworkEngineAll(runPing = true) },
                shape = RoundedCornerShape(8.dp),
                color = heroAccent.copy(alpha = 0.14f),
                border = BorderStroke(0.7.dp, heroAccent.copy(alpha = 0.35f)),
                modifier = Modifier.heightIn(min = 34.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(
                        text = when {
                            isWifi -> "● WI-FI"
                            isCellular -> "● SELULER"
                            isEthernet -> "● KABEL"
                            else -> "○ OFFLINE"
                        },
                        color = heroAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HorizontalDivider(color = BorderSubtle, thickness = 0.7.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = titleLine,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitleLine,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isOnline) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isWifi) {
                        NetworkMetricTile(
                            label = "LINK SPEED",
                            value = if (tele.linkSpeedMbps > 0) "${tele.linkSpeedMbps} Mbps" else "—",
                            sub = if (tele.maxLinkSpeedMbps > 0) "Maks ${tele.maxLinkSpeedMbps} Mbps" else "Wi-Fi Link",
                            valueColor = AccentCyan,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        val rssiColor = when {
                            tele.rssiDbm == 0 -> TextSecondary
                            tele.rssiDbm > -70 -> AccentGreen
                            else -> AccentOrange
                        }
                        val rssiDesc = when {
                            tele.rssiDbm == 0 -> "Tidak terbaca"
                            tele.rssiDbm > -65 -> "Sangat Kuat"
                            tele.rssiDbm > -75 -> "Stabil"
                            else -> "Lemah"
                        }
                        NetworkMetricTile(
                            label = "SINYAL RSSI",
                            value = if (tele.rssiDbm != 0) "${tele.rssiDbm} dBm" else "—",
                            sub = rssiDesc,
                            valueColor = rssiColor,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    } else {
                        NetworkMetricTile(
                            label = "TRANSPORT",
                            value = tele.cellularNetworkType.ifBlank { engine.activeTransport.uppercase() },
                            sub = activeIface.ifBlank { "Rute aktif" },
                            valueColor = AccentCyan,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        NetworkMetricTile(
                            label = "MTU & IP",
                            value = "MTU ${if (tele.activeMtu > 0) tele.activeMtu else engine.activeMtu}",
                            sub = tele.ipAddress.ifBlank { engine.srcIp }.ifBlank { "IP aktif" },
                            valueColor = AccentBlue,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }

                    val pingValue = when {
                        uiState.isProbingNetwork -> "Menguji..."
                        tele.probeReachable && tele.gatewayPingMs > 0f ->
                            "${"%.1f".format(Locale.US, tele.gatewayPingMs)} ms"
                        tele.gatewayLossPct >= 100f && tele.gatewayIp.isNotBlank() -> "Timeout"
                        else -> "Belum Diuji"
                    }
                    val pingSub = when {
                        tele.probeReachable -> "Jitter ${"%.1f".format(Locale.US, tele.gatewayJitterMs)} ms"
                        tele.gatewayIp.isNotBlank() -> "Target ${tele.gatewayIp}"
                        else -> "Ketuk Uji Ping"
                    }
                    val pingColor = when {
                        uiState.isProbingNetwork -> AccentCyan
                        tele.probeReachable && tele.gatewayPingMs <= 25f -> AccentGreen
                        tele.probeReachable -> AccentOrange
                        else -> TextSecondary
                    }
                    NetworkMetricTile(
                        label = "LATENSI RTT",
                        value = pingValue,
                        sub = pingSub,
                        valueColor = pingColor,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }

            LynxSectionDivider()

            LynxSectionHeader(
                title = "Lingkungan Radio & Kernel Terdeteksi",
                subtitle = engine.radioLabel,
                badgeText = "API ${if (engine.androidSdk > 0) engine.androidSdk else android.os.Build.VERSION.SDK_INT}",
                accentColor = AccentBlue
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LynxCapabilityBadge(
                    text = "TCP: ${engine.currentAlgo.ifBlank { uiState.currentTcpCongestion.ifBlank { "?" } }.uppercase()}",
                    color = AccentCyan
                )
                LynxCapabilityBadge(
                    text = "QDISC: ${engine.currentQdisc.ifBlank { "?" }}",
                    color = AccentPurple
                )
                LynxCapabilityBadge(
                    text = if (engine.hasTc) "TC AKTIF" else "NO TC BIN",
                    color = if (engine.hasTc) AccentGreen else AccentOrange
                )
                LynxCapabilityBadge(
                    text = if (engine.hasIw) "IW OK" else "HAL/WEXT",
                    color = if (engine.hasIw) AccentBlue else TextSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = { viewModel.probeNetworkLatency() },
                    enabled = !uiState.isProbingNetwork && isOnline,
                    shape = RoundedCornerShape(10.dp),
                    color = AccentCyan.copy(alpha = 0.14f),
                    border = BorderStroke(0.7.dp, AccentCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = if (isOnline) AccentCyan else TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isProbingNetwork) "Menguji Ping..." else "Uji Ping & Jitter",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOnline) AccentCyan else TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    onClick = { showInterfacesSheet = true },
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(0.7.dp, BorderSubtle),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Interface (${engine.interfaces.size})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkMetricTile(
    label: String,
    value: String,
    sub: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = BgSurfaceLowest,
        border = BorderStroke(0.7.dp, BorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = valueColor, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = TextSecondary, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun NetworkPresetCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val activePreset = uiState.state.network.activePreset
    val engine = uiState.networkEngineInfo

    LynxCard(
        title = "Profil Jaringan Cepat (1-Tap)",
        subtitle = "Terapkan kombinasi HAL, driver radio, TCP, Qdisc & buffer secara instan",
        icon = Icons.Default.Tune,
        accentColor = AccentCyan,
        action = if (engine.hasBaseline) {
            { LynxCapabilityBadge(text = "BASELINE ADA", color = AccentGreen) }
        } else null,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HorizontalDivider(color = BorderSubtle, thickness = 0.7.dp)

            val presets = listOf(
                Triple("oem", "Standar OEM", "Pulihkan nilai asli perangkat & mode hemat daya radio"),
                Triple("gaming", "Gaming Low-Latency", "HAL+CAM aktif, scan guard, NAPI responsif & minim jitter"),
                Triple("throughput", "Throughput Maks", "Buffer adaptif RAM (${engine.ramTierLabel.ifBlank { "Auto" }}), pacing FQ & roaming bebas")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { (key, title, desc) ->
                    val selected = activePreset.equals(key, ignoreCase = true)
                    val accent = when (key) {
                        "oem" -> AccentBlue
                        "gaming" -> AccentCyan
                        else -> AccentPurple
                    }
                    Surface(
                        onClick = { viewModel.applyNetworkPreset(key) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) accent.copy(alpha = 0.16f) else BgSurfaceLowest,
                        border = BorderStroke(if (selected) 1.2.dp else 0.7.dp, if (selected) accent else BorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 68.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = title,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) accent else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = desc,
                                fontSize = 9.5.sp,
                                lineHeight = 13.sp,
                                color = TextSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkInterfacesBottomSheet(
    engine: NetworkEngineInfo,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.80f).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgCard,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp),
                shape = RoundedCornerShape(2.dp),
                color = BorderSubtle
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Inspeksi Interface Jaringan Kernel",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${engine.interfaces.size} interface non-loopback dari /sys/class/net",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextSecondary)
                }
            }

            HorizontalDivider(color = BorderGlass, thickness = 0.6.dp, modifier = Modifier.padding(top = 6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (engine.interfaces.isEmpty()) {
                    Text(
                        text = "Belum ada data interface. Ketuk refresh pada kartu monitor.",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                } else {
                    var activeOnly by remember { mutableStateOf(true) }
                    val activeIfaces = remember(engine.interfaces, engine.activeIface) {
                        engine.interfaces.filter {
                            it.state.equals("up", ignoreCase = true) ||
                                (it.name == engine.activeIface && it.name.isNotBlank())
                        }
                    }
                    val visibleIfaces = if (activeOnly && activeIfaces.isNotEmpty()) {
                        activeIfaces
                    } else {
                        engine.interfaces
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            true to "Aktif / Utama (${activeIfaces.size})",
                            false to "Semua (${engine.interfaces.size})"
                        ).forEach { (isAct, label) ->
                            val selected = activeOnly == isAct
                            Surface(
                                onClick = { activeOnly = isAct },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) AccentCyan.copy(alpha = 0.16f) else BgSurfaceLowest,
                                border = BorderStroke(
                                    if (selected) 1.2.dp else 0.7.dp,
                                    if (selected) AccentCyan else BorderSubtle
                                ),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) AccentCyan else TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    visibleIfaces.forEach { iface ->
                        val isPrimaryRoute = iface.name == engine.activeIface && iface.name.isNotBlank()
                        val isUp = iface.state.equals("up", ignoreCase = true)
                        val rowAccent = when {
                            isPrimaryRoute -> AccentCyan
                            isUp -> AccentGreen
                            else -> TextSecondary
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BgSurfaceLowest,
                            border = BorderStroke(
                                0.7.dp,
                                if (isPrimaryRoute) AccentCyan.copy(alpha = 0.45f) else BorderSubtle
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = iface.name,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        LynxCapabilityBadge(iface.kind.uppercase(), AccentBlue)
                                        if (isPrimaryRoute) {
                                            LynxCapabilityBadge("RUTE UTAMA", AccentCyan)
                                        }
                                    }
                                    LynxCapabilityBadge(iface.state.uppercase(), rowAccent)
                                }
                                Text(
                                    text = "MTU ${iface.mtu} • Antrean ${iface.rxQueues} RX / ${iface.txQueues} TX • RPS ${if (iface.rpsWritable) "Writable" else "N/A"}" +
                                        if (iface.qdisc.isNotBlank()) " • Qdisc ${iface.qdisc}" else "",
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
                LynxActionButton(
                    text = "Tutup Panel Interface",
                    accentColor = AccentCyan,
                    onClick = onDismiss
                )
            }
        }
    }
}
