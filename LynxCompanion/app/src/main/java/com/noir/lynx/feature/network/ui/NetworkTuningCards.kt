package com.noir.lynx.feature.network.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCapabilityBadge
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.card.LynxSectionDivider
import com.noir.lynx.designsystem.card.LynxSectionHeader
import com.noir.lynx.designsystem.card.UnsupportedBadge
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.*
import java.util.Locale

@Composable
internal fun RadioEngineCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state
    val engine = uiState.networkEngineInfo

    val radioTitle = when (engine.radioVendor) {
        "mediatek_proc" -> "Optimasi Driver Radio MediaTek"
        "qualcomm_cnss" -> "Optimasi Driver Radio Qualcomm"
        "broadcom_dhd" -> "Optimasi Driver Radio Broadcom"
        "exynos_scsc" -> "Optimasi Driver Radio Exynos"
        "unisoc_sprd" -> "Optimasi Driver Radio Unisoc"
        else -> "Optimasi Driver Radio Hardware"
    }
    val radioDesc = when (engine.radioVendor) {
        "mediatek_proc" -> "Aktifkan setCAM, autoPerfCfg & agregasi AMPDU lewat /proc/net/wlan"
        "qualcomm_cnss" -> "Matikan dynamic_ps / power_save & packet filter di modul WCNSS/CNSS"
        "broadcom_dhd" -> "Nonaktifkan dhd_slpauto & aktifkan op_mode performa radio Broadcom"
        "exynos_scsc" -> "Nonaktifkan slp_enable & prioritaskan thread SCSC WLAN"
        "unisoc_sprd" -> "Nonaktifkan auto_power / dynamic_ps pada driver sprdwl"
        else -> "Parameter sysfs vendor driver tidak tersedia secara langsung di kernel ini"
    }

    LynxCard(
        title = "Radio & Wi-Fi Engine",
        subtitle = "Kontrol Android HAL, coexistence scan, failover seluler & driver fisik",
        icon = Icons.Default.Router,
        accentColor = AccentBlue,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HorizontalDivider(color = BorderSubtle, thickness = 0.7.dp)

            LynxSectionHeader(
                title = "Kontrol Radio OS & Antarmuka",
                subtitle = "Bekerja di semua OEM via Android Wi-Fi HAL, iw / wpa_cli & Settings",
                badgeText = if (engine.supportsHalLowLatency) "HAL API ${engine.androidSdk}" else "LEGACY",
                accentColor = AccentBlue
            )

            LynxSwitch(
                label = "Wi-Fi Low-Latency & Power Save Off",
                subLabel = buildString {
                    append("Paksa mode latensi rendah HAL + matikan radio DTIM power save")
                    if (engine.supportsTcpLowLatency) append(" + tcp_low_latency")
                },
                checked = state.network.wifiPingStabilizer,
                onCheckedChange = { viewModel.setWifiPingStabilizer(it) }
            )

            LynxSwitch(
                label = "Penjaga Scan Latar & Coexistence",
                subLabel = "Cegah ping spike periodik akibat background Wi-Fi/BLE scan lokasi",
                checked = state.network.antiJitterScan,
                onCheckedChange = { viewModel.setAntiJitterScan(it) }
            )

            LynxSwitch(
                label = "Cellular Hot-Standby (Failover Cepat)",
                subLabel = "Pertahankan koneksi seluler siap pakai agar transisi dari Wi-Fi minim putus",
                checked = state.network.cellularHotStandby,
                onCheckedChange = { viewModel.setCellularHotStandby(it) }
            )

            LynxSectionDivider()

            LynxSectionHeader(
                title = radioTitle,
                subtitle = engine.radioLabel,
                badgeText = if (engine.supportsHwRadio) "VERIFIED" else "UNSUPPORTED",
                accentColor = if (engine.supportsHwRadio) AccentOrange else TextSecondary
            )

            if (engine.supportsHwRadio) {
                LynxSwitch(
                    label = "Hardware CAM & Optimasi Driver",
                    subLabel = radioDesc,
                    checked = state.network.mtkHardwareAggregation,
                    onCheckedChange = { viewModel.setMtkHardwareAggregation(it) }
                )
            } else {
                UnsupportedBadge(
                    reason = "Kernel perangkat ini (GKI/Built-in) tidak mengekspos parameter sysfs driver radio vendor secara terpisah. Radio tetap dioptimalkan lewat Wi-Fi Low-Latency HAL di atas."
                )
            }
        }
    }
}

@Composable
internal fun KernelNetworkStackCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state
    val engine = uiState.networkEngineInfo
    val currentAlg = uiState.currentTcpCongestion
        .ifBlank { engine.currentAlgo }
        .ifBlank { state.network.tcpCongestion }
    val algs = uiState.availableTcpAlgorithms.ifEmpty { engine.availableAlgos }
    val currentQdisc = engine.currentQdisc.ifBlank { state.network.qdisc }
    val qdiscs = engine.availableQdiscs

    LynxCard(
        title = "Kernel Network Stack & Anti-Bufferbloat",
        subtitle = "Kongesti TCP, antrean paket qdisc, buffer RAM & pengarah IRQ",
        icon = Icons.Default.NetworkCheck,
        accentColor = AccentCyan,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HorizontalDivider(color = BorderSubtle, thickness = 0.7.dp)

            LynxSectionHeader(
                title = "Algoritma Kongesti TCP",
                subtitle = "Mengontrol kecepatan pengiriman & pemulihan paket saat jaringan padat",
                badgeText = if (currentAlg.isNotBlank()) currentAlg.uppercase() else null,
                accentColor = AccentCyan
            )

            if (algs.isEmpty()) {
                UnsupportedBadge(
                    reason = "Kernel perangkat ini tidak mengekspos pemilihan algoritma TCP dinamis (/proc/sys/net/ipv4/tcp_available_congestion_control)."
                )
            } else {
                val algInfo = mapOf(
                    "bic" to ("BIC" to "Agresif & latensi rendah"),
                    "cubic" to ("CUBIC" to "Default Linux seimbang"),
                    "reno" to ("RENO" to "Klasik ringan & stabil"),
                    "bbr" to ("BBR" to "Pacing berbasis bottleneck"),
                    "bbr2" to ("BBRv2" to "Pacing modern minim antrean"),
                    "bbr3" to ("BBRv3" to "BBR generasi terbaru"),
                    "bbrv3" to ("BBRv3" to "BBR generasi terbaru"),
                    "bbrplus" to ("BBR Plus" to "Varian BBR agresif"),
                    "westwood" to ("Westwood" to "Tahan packet loss Wi-Fi/Seluler"),
                    "htcp" to ("H-TCP" to "Throughput bandwidth tinggi"),
                    "vegas" to ("Vegas" to "Hindari kongesti berbasis RTT"),
                    "hybla" to ("Hybla" to "Tautan latensi tinggi")
                )

                algs.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { alg ->
                            val isActive = alg.equals(currentAlg, ignoreCase = true)
                            val isRecommended = alg.equals(engine.bestAlgo, ignoreCase = true)
                            val info = algInfo[alg.lowercase()]
                            Surface(
                                onClick = { viewModel.setTcpCongestion(alg) },
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isActive) AccentCyan.copy(alpha = 0.16f) else BgSurfaceLowest,
                                border = BorderStroke(
                                    if (isActive) 1.3.dp else 0.7.dp,
                                    if (isActive) AccentCyan else BorderSubtle
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = info?.first ?: alg.uppercase(),
                                            color = if (isActive) AccentCyan else TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        if (isActive) {
                                            LynxCapabilityBadge("AKTIF", AccentCyan)
                                        } else if (isRecommended) {
                                            LynxCapabilityBadge("REKOM", AccentGreen)
                                        }
                                    }
                                    Text(
                                        text = info?.second ?: "Algoritma Kernel Linux",
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        lineHeight = 13.5.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                if (currentAlg.startsWith("bbr", ignoreCase = true) && currentQdisc == "pfifo_fast") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AccentOrange.copy(alpha = 0.09f),
                        border = BorderStroke(0.6.dp, AccentOrange.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Tips BBR: BBR bekerja paling stabil jika dipasangkan dengan qdisc 'fq' atau 'fq_codel', bukan pfifo_fast.",
                            fontSize = 10.5.sp,
                            color = AccentOrange,
                            lineHeight = 14.5.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            LynxSectionDivider()

            LynxSectionHeader(
                title = "Queue Discipline (Antrean Paket)",
                subtitle = if (engine.hasTc) {
                    "Dukungan kernel default_qdisc + Live interface via tc"
                } else {
                    "Binary tc tidak tersedia di ROM; menggunakan kernel default_qdisc"
                },
                badgeText = currentQdisc.ifBlank { null }?.uppercase(),
                accentColor = AccentPurple
            )

            if (qdiscs.isEmpty()) {
                UnsupportedBadge(
                    reason = "Kernel tidak mengekspos node /proc/sys/net/core/default_qdisc."
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    qdiscs.forEach { q ->
                        val selected = q.equals(currentQdisc, ignoreCase = true)
                        Surface(
                            onClick = { viewModel.setNetworkQdisc(q) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) AccentPurple.copy(alpha = 0.16f) else BgSurfaceLowest,
                            border = BorderStroke(if (selected) 1.2.dp else 0.7.dp, if (selected) AccentPurple else BorderSubtle),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
                                Text(
                                    text = q,
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) AccentPurple else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            LynxSectionDivider()

            LynxSectionHeader(
                title = "Parameter Soket, RPS & IPv6",
                subtitle = "Optimasi ukuran buffer RAM, Fast Open & prioritas jalur paket",
                badgeText = engine.ramTierLabel.ifBlank { null },
                accentColor = AccentBlue
            )

            LynxSwitch(
                label = "Multi-Queue RPS/XPS & Anti-Bufferbloat",
                subLabel = if (engine.supportsRpsXps) {
                    "Sebarkan IRQ paket jaringan antar-inti CPU + qdisc anti-bufferbloat"
                } else {
                    "RPS/XPS per-queue tidak diekspos driver; mengatur qdisc default saja"
                },
                checked = state.network.fqCodelEnabled,
                onCheckedChange = { viewModel.setFqCodelEnabled(it) }
            )

            val bufDesc = if (engine.effectiveRmemMaxMb > 0f) {
                "Batas soket kernel saat ini ${"%.0f".format(Locale.US, engine.effectiveRmemMaxMb)} MB (skala ${engine.ramTierLabel.ifBlank { "otomatis" }})"
            } else {
                "Sesuaikan rmem_max/wmem_max proporsional kapasitas RAM & pulihkan baseline saat off"
            }
            LynxSwitch(
                label = "Buffer Soket Adaptif RAM",
                subLabel = bufDesc,
                checked = state.network.tcpBuffers16m,
                onCheckedChange = { viewModel.setTcpBuffers16m(it) }
            )

            LynxSwitch(
                label = "TCP Fast Open & Anti-Stall (${engine.fastopenNodesPresent}/5 node)",
                subLabel = "Aktifkan tcp_fastopen=3, matikan slow_start_after_idle & percepat koneksi",
                checked = state.network.tcpFastOpen,
                onCheckedChange = { viewModel.setTcpFastOpen(it) }
            )

            LynxSwitch(
                label = "Prioritas IPv4 (Matikan IPv6 Bermasalah)",
                subLabel = "Atasi jeda timeout/lag di ISP atau hotspot yang rute IPv6-nya setengah putus",
                checked = state.network.ipv6Disabled,
                onCheckedChange = { viewModel.setIpv6Disabled(it) }
            )
        }
    }
}

@Composable
internal fun PrivateDnsCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    var showDnsCatalogSheet by remember { mutableStateOf(false) }

    if (showDnsCatalogSheet) {
        PrivateDnsCatalogBottomSheet(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showDnsCatalogSheet = false }
        )
    }

    val activeCatalogItem = remember(uiState.privateDnsMode, uiState.privateDnsSpecifier) {
        if (uiState.privateDnsMode == "hostname") findDnsCatalogEntry(uiState.privateDnsSpecifier) else null
    }
    val quickFavorites = remember {
        listOf(
            Triple("Cloudflare", "1dot1dot1dot1.cloudflare-dns.com", AccentCyan),
            Triple("Google", "dns.google", AccentBlue),
            Triple("AdGuard", "dns.adguard-dns.com", AccentGreen)
        )
    }

    LynxCard(
        title = "Private DNS (DoT)",
        subtitle = "Enkripsi query DNS & akses ${PRIVATE_DNS_CATALOG.size} resolver lewat katalog sheet",
        icon = Icons.Default.Dns,
        accentColor = AccentCyan,
        action = {
            val isDot = uiState.privateDnsMode == "hostname"
            val isAuto = uiState.privateDnsMode == "opportunistic"
            LynxCapabilityBadge(
                text = when {
                    isDot -> "🔒 TLS AKTIF"
                    isAuto -> "⚡ OTOMATIS"
                    else -> "○ ISP/OFF"
                },
                color = when {
                    isDot -> AccentCyan
                    isAuto -> AccentBlue
                    else -> TextSecondary
                }
            )
        },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HorizontalDivider(color = BorderSubtle, thickness = 0.7.dp)

            // Active status summary box
            val activeRtt = if (uiState.privateDnsMode == "hostname" && uiState.privateDnsSpecifier.isNotBlank()) {
                uiState.dnsLatencyMs[uiState.privateDnsSpecifier]
            } else null

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgSurfaceLowest,
                border = BorderStroke(0.7.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = when (uiState.privateDnsMode) {
                                "hostname" -> activeCatalogItem?.name ?: "Custom Private DNS"
                                "opportunistic" -> "Mode Otomatis (Opportunistic)"
                                else -> "Nonaktif (DNS Bawaan ISP / DHCP)"
                            },
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when (uiState.privateDnsMode) {
                                "hostname" -> uiState.privateDnsSpecifier.ifBlank { "Hostname Kosong" }
                                "opportunistic" -> "Enkripsi DoT aktif otomatis bila server mendukung"
                                else -> "Menggunakan resolver standar Wi-Fi atau operator"
                            },
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            fontFamily = if (uiState.privateDnsMode == "hostname") FontFamily.Monospace else FontFamily.Default,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (activeRtt != null) {
                        val txt = if (activeRtt > 0f) "${"%.0f".format(Locale.US, activeRtt)} ms" else "Timeout"
                        val clr = if (activeRtt > 0f && activeRtt <= 45f) AccentGreen else if (activeRtt > 0f) AccentOrange else AccentRed
                        LynxCapabilityBadge(txt, clr)
                    }
                }
            }

            LynxSectionHeader(
                title = "Mode Sistem & Favorit Cepat",
                subtitle = "Ganti mode cepat atau buka katalog penuh untuk kategori & tes ping",
                accentColor = AccentCyan
            )

            // 2 System mode chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "off" to "Off (ISP)",
                    "opportunistic" to "Otomatis (DoT)"
                ).forEach { (modeKey, modeLabel) ->
                    val selected = uiState.privateDnsMode == modeKey
                    val clr = if (modeKey == "opportunistic") AccentBlue else TextSecondary
                    Surface(
                        onClick = { viewModel.setPrivateDns(modeKey, "") },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) clr.copy(alpha = 0.16f) else BgSurfaceLowest,
                        border = BorderStroke(if (selected) 1.2.dp else 0.7.dp, if (selected) clr else BorderSubtle),
                        modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                            Text(
                                text = modeLabel,
                                color = if (selected) TextPrimary else TextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // 3 Quick favorites
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickFavorites.forEach { (shortLabel, host, clr) ->
                    val selected = uiState.privateDnsMode == "hostname" &&
                        uiState.privateDnsSpecifier.equals(host, ignoreCase = true)
                    Surface(
                        onClick = { viewModel.setPrivateDns("hostname", host) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) clr.copy(alpha = 0.16f) else BgSurfaceLowest,
                        border = BorderStroke(if (selected) 1.2.dp else 0.7.dp, if (selected) clr else BorderSubtle),
                        modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
                            Text(
                                text = shortLabel,
                                color = if (selected) clr else TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Surface(
                onClick = { showDnsCatalogSheet = true },
                shape = RoundedCornerShape(12.dp),
                color = AccentCyan.copy(alpha = 0.14f),
                border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Dns, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Buka Katalog DNS (${PRIVATE_DNS_CATALOG.size} Server) & Custom",
                            color = AccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
internal fun AdbKeepAliveCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val isPortLocked = uiState.adbKeepAliveEnabled && uiState.isAdbListening
    val isPortRestoring = uiState.adbKeepAliveEnabled && !uiState.isAdbListening
    val badgeColor = when {
        isPortLocked -> AccentGreen
        isPortRestoring -> AccentOrange
        else -> TextSecondary
    }
    val badgeText = when {
        isPortLocked -> "● LOCKED :${uiState.adbPort}"
        isPortRestoring -> "⚡ PULIHKAN :${uiState.adbPort}"
        else -> "○ STANDBY"
    }

    LynxCard(
        title = "ADB Wireless Keep-Alive & Port Lock",
        subtitle = "Kunci port TCP adbd + Doze whitelist agar tetap terhubung saat layar mati",
        icon = Icons.Default.Terminal,
        accentColor = AccentGreen,
        action = { LynxCapabilityBadge(text = badgeText, color = badgeColor) },
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HorizontalDivider(color = BorderSubtle, thickness = 0.7.dp)

            LynxSwitch(
                label = "Pertahankan Port ADB Saat Layar Mati",
                subLabel = "Auto-heal oleh daemon lynxd jika sistem Android mereset port saat sleep",
                checked = uiState.adbKeepAliveEnabled,
                onCheckedChange = { viewModel.setAdbKeepAlive(it) }
            )

            LynxSectionDivider()

            LynxSectionHeader(
                title = "Konfigurasi Port TCP (1024 – 65535)",
                subtitle = "Pilih preset cepat atau masukkan port ADB wireless manual",
                accentColor = AccentGreen
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5555 to "5555 (Default)", 5556 to "5556", 8888 to "8888").forEach { (presetPort, label) ->
                    val isSelected = uiState.adbPort == presetPort
                    Surface(
                        onClick = { viewModel.applyAdbCustomPort(presetPort.toString()) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentGreen.copy(alpha = 0.16f) else BgSurfaceLowest,
                        border = BorderStroke(if (isSelected) 1.2.dp else 0.7.dp, if (isSelected) AccentGreen else BorderSubtle),
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) AccentGreen else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = uiState.adbPortInput,
                    onValueChange = { viewModel.setAdbPortInput(it) },
                    singleLine = true,
                    placeholder = { Text("Contoh: 5555", color = TextSecondary, fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGreen,
                        unfocusedBorderColor = BorderGlass,
                        focusedContainerColor = Color(0xFF0A0C12),
                        unfocusedContainerColor = Color(0xFF0A0C12)
                    )
                )

                Button(
                    onClick = { viewModel.applyAdbCustomPort(uiState.adbPortInput) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen.copy(alpha = 0.2f),
                        contentColor = AccentGreen
                    ),
                    border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.45f)),
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kunci Port", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (uiState.adbKeepAliveEnabled) {
                val ip = remember(uiState.adbStatus) {
                    val regex = """"ip_address":"([^"]+)"""".toRegex()
                    regex.find(uiState.adbStatus)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: ""
                }
                if (ip.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BgSurfaceLowest,
                        border = BorderStroke(0.7.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "adb connect $ip:${uiState.adbPort}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentCyan
                            )
                            Text(
                                text = if (uiState.isAdbListening) "Tersambung" else "Memulihkan...",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isAdbListening) AccentGreen else AccentOrange
                            )
                        }
                    }
                }
            }
        }
    }
}
