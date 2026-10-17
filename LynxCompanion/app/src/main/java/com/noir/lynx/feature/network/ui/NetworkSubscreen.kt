package com.noir.lynx.feature.network.ui
import com.noir.lynx.ui.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel

/**
 * Subscreen 6: Network & Audio Composable
 */
@Composable
fun NetworkSubscreen(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Live Wi-Fi Telemetry & Link Monitor Card ────────────
        LaunchedEffect(Unit) {
            viewModel.refreshWifiTelemetry()
            viewModel.refreshTcpAlgorithms()
        }

        val wifi = uiState.wifiTelemetry
        LynxCard(
            title = "Live Wi-Fi Telemetry & Link Monitor",
            icon = Icons.Default.Wifi,
            accentColor = if (wifi.isConnected) AccentGreen else AccentOrange
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (wifi.isConnected) wifi.ssid else "Wi-Fi Terputus / Tidak Aktif",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (wifi.isConnected) "${wifi.wifiStandard} • ${wifi.frequencyMhz} MHz" else "Aktifkan Wi-Fi untuk telemetri",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Surface(
                    onClick = { viewModel.refreshWifiTelemetry() },
                    shape = RoundedCornerShape(8.dp),
                    color = (if (wifi.isConnected) AccentGreen else AccentOrange).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, (if (wifi.isConnected) AccentGreen else AccentOrange).copy(alpha = 0.35f))
                ) {
                    Text(
                        if (wifi.isConnected) "● TERHUBUNG" else "○ DISCONNECTED",
                        color = if (wifi.isConnected) AccentGreen else AccentOrange,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (wifi.isConnected) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Link Speed
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("LINK SPEED", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text("${wifi.linkSpeedMbps} Mbps", color = AccentCyan, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Text("Maks: ${wifi.maxLinkSpeedMbps} Mbps", color = TextSecondary, fontSize = 9.sp)
                        }
                    }
                    // RSSI Signal
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("SINYAL RSSI", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text("${wifi.rssiDbm} dBm", color = if (wifi.rssiDbm > -70) AccentGreen else AccentOrange, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Text(if (wifi.rssiDbm > -65) "Sangat Kuat" else if (wifi.rssiDbm > -75) "Bagus" else "Lemah", color = TextSecondary, fontSize = 9.sp)
                        }
                    }
                    // Gateway Ping
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("LATENSI GATEWAY", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text(if (wifi.gatewayPingMs > 0) "${String.format("%.1f", wifi.gatewayPingMs)} ms" else "Sub-5ms", color = AccentPurple, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Text(if (wifi.gatewayPingMs < 6) "Zero Jitter" else "Normal", color = TextSecondary, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        // ── TCP Congestion Control Card ──────────────────────────
        LynxCard(
            title = "TCP Congestion Control",
            icon = Icons.Default.NetworkCheck,
            accentColor = AccentCyan
        ) {
            val currentAlg = uiState.currentTcpCongestion.ifBlank { state.network.tcpCongestion }
            val algs = uiState.availableTcpAlgorithms

            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Algoritma kontrol kongesti TCP/IP aktif mempengaruhi latensi dan throughput koneksi game online.",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                if (currentAlg.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentCyan.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.35f))
                    ) {
                        Text(
                            "● ${currentAlg.uppercase()}",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (algs.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(1.dp, BorderGlass),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentOrange.copy(alpha = 0.2f),
                            modifier = Modifier.padding(end = 10.dp)
                        ) {
                            Text(
                                "UNSUPPORTED",
                                color = AccentOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            "Kernel perangkat ini tidak mengekspos pemilihan algoritma TCP dinamis via sysfs.",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
            } else {
                val algInfo = mapOf(
                    "bic"      to Pair("BIC", "High Speed & Low Latency (Kernel Active)"),
                    "cubic"    to Pair("CUBIC", "Linux Default — Balanced"),
                    "reno"     to Pair("RENO", "Classic Standard — Stable"),
                    "bbr"      to Pair("BBR", "Google BBR — Low Latency Gaming"),
                    "westwood" to Pair("Westwood", "WiFi & Wireless Loss Tolerant"),
                    "htcp"     to Pair("H-TCP", "High Bandwidth Delay Product"),
                    "vegas"    to Pair("Vegas", "Delay-Based Congestion Avoidance"),
                    "hybla"    to Pair("Hybla", "Satellite & High Latency Links")
                )

                algs.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { alg ->
                            val isActive = alg.equals(currentAlg, ignoreCase = true)
                            val info = algInfo[alg.lowercase()]
                            Surface(
                                onClick = { viewModel.setTcpCongestion(alg) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isActive) AccentCyan.copy(alpha = 0.16f) else BgElevated,
                                border = BorderStroke(
                                    1.5.dp, if (isActive) AccentCyan else BorderGlass
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            info?.first ?: alg.uppercase(),
                                            color = if (isActive) AccentCyan else TextPrimary,
                                            fontWeight = FontWeight.Bold, fontSize = 12.sp
                                        )
                                        if (isActive) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AccentCyan.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    "AKTIF",
                                                    color = AccentCyan,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        info?.second ?: "Algoritma Kernel Linux",
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // ── Wi-Fi Gaming & Low-Latency Suite Card ────────────────
        LynxCard(
            title = "Wi-Fi Gaming & Low-Latency Suite",
            icon = Icons.Default.Bolt,
            accentColor = AccentBlue
        ) {
            LynxSwitch(
                label = "Wi-Fi Ultra-Low Latency & CAM Mode",
                subLabel = "Bypass 802.11 DTIM radio sleep & paksa HAL low-latency untuk respon game instan",
                checked = state.network.wifiPingStabilizer,
                onCheckedChange = { viewModel.setWifiPingStabilizer(it) },
            )
            LynxSwitch(
                label = "Anti-Lag Wi-Fi Background Scan Guard",
                subLabel = "Hentikan pemindaian periodik di latar belakang untuk mencegah ping spike tiap 15-30 detik",
                checked = state.network.antiJitterScan,
                onCheckedChange = { viewModel.setAntiJitterScan(it) },
            )
        }

        // ── Throughput & Buffer Accelerator Card ─────────────────
        LynxCard(
            title = "Throughput & Buffer Accelerator",
            icon = Icons.Default.Speed,
            accentColor = AccentPurple
        ) {
            LynxSwitch(
                label = "Multi-Queue RFS & Bufferbloat Guard",
                subLabel = "Distribusikan IRQ paket via 32K Flow Steering & mitigasi bufferbloat antrean",
                checked = state.network.fqCodelEnabled,
                onCheckedChange = { viewModel.setFqCodelEnabled(it) },
            )
            LynxSwitch(
                label = "High-Bandwidth 16 MB TCP Window",
                subLabel = "Perluas buffer socket ke 16 MB untuk memaksimalkan throughput internet kencang",
                checked = state.network.tcpBuffers16m,
                onCheckedChange = { viewModel.setTcpBuffers16m(it) },
            )
            LynxSwitch(
                label = "TCP Fast Open & Anti-Stall Guard",
                subLabel = "tcp_notsent_lowat 16KB, nonaktifkan slow-start idle, & kirim data instan di SYN",
                checked = state.network.tcpFastOpen,
                onCheckedChange = { viewModel.setTcpFastOpen(it) },
            )
        }

        // ── Hardware Platform Wi-Fi Optimization ─────────────────
        LynxCard(
            title = "Hardware Platform Wi-Fi Engine",
            icon = Icons.Default.Router,
            accentColor = AccentOrange
        ) {
            LynxSwitch(
                label = "MediaTek A-MSDU / VHT Aggregation",
                subLabel = "Agregasi subframe ganda 802.11ac + EDCCA threshold (Graceful Fallback di non-MTK)",
                checked = state.network.mtkHardwareAggregation,
                onCheckedChange = { viewModel.setMtkHardwareAggregation(it) },
            )
        }

        // ── Fast Gaming Private DNS Switcher Card (DoT) ──────────
        val dnsOptions = listOf(
            Triple("off", "", "Off (Bawaan ISP / DHCP)"),
            Triple("hostname", "1dot1dot1dot1.cloudflare-dns.com", "Cloudflare (1.1.1.1) • Low Ping & CDN"),
            Triple("hostname", "dns.google", "Google DNS • Kecepatan Stabil"),
            Triple("hostname", "dns.adguard-dns.com", "AdGuard DNS • Blokir Iklan & Pelacak"),
            Triple("hostname", "dns.quad9.net", "Quad9 • Perlindungan Malware & Anti-Phishing")
        )

        LynxCard(
            title = "Fast Gaming Private DNS (DoT)",
            icon = Icons.Default.Dns,
            accentColor = AccentCyan
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "DNS-over-TLS Terenkripsi",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (uiState.privateDnsMode == "hostname") "Aktif: ${uiState.privateDnsSpecifier}" else "Menggunakan DNS default ISP (tanpa enkripsi)",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                val isDot = uiState.privateDnsMode == "hostname"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDot) AccentCyan.copy(alpha = 0.15f) else BgElevated,
                    border = BorderStroke(1.dp, if (isDot) AccentCyan.copy(alpha = 0.5f) else BorderGlass)
                ) {
                    Text(
                        if (isDot) "🔒 TLS AKTIF" else "○ OFF (ISP)",
                        color = if (isDot) AccentCyan else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                "Pilih Resolver DNS Gaming (1-Tap):",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                dnsOptions.forEach { (mode, spec, label) ->
                    val isSelected = (mode == "off" && uiState.privateDnsMode != "hostname") ||
                            (mode == "hostname" && uiState.privateDnsMode == "hostname" && uiState.privateDnsSpecifier == spec)
                    Surface(
                        onClick = { viewModel.setPrivateDns(mode, spec) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentCyan.copy(alpha = 0.16f) else BgElevated,
                        border = BorderStroke(1.dp, if (isSelected) AccentCyan else BorderGlass),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                label,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Terpilih", tint = AccentCyan, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── ADB Wireless Keep-Alive & Custom Port Lock Card ──────
        LaunchedEffect(Unit) { viewModel.refreshAdbConfig() }
        LynxCard(
            title = "ADB Wireless Keep-Alive & Port Lock",
            icon = Icons.Default.Terminal,
            accentColor = AccentGreen
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Mengunci port TCP adbd secara permanen (persist.adb.tcp.port + Doze Whitelist) agar tidak pernah reset atau terputus saat layar mati maupun setelah reboot.",
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                val isPortLocked = uiState.adbKeepAliveEnabled && uiState.isAdbListening
                val isPortRestoring = uiState.adbKeepAliveEnabled && !uiState.isAdbListening
                val badgeColor = when {
                    isPortLocked -> AccentGreen
                    isPortRestoring -> AccentOrange
                    else -> TextSecondary
                }
                val badgeBg = when {
                    isPortLocked -> AccentGreen.copy(alpha = 0.15f)
                    isPortRestoring -> AccentOrange.copy(alpha = 0.15f)
                    else -> BgElevated
                }
                val badgeBorder = when {
                    isPortLocked -> AccentGreen.copy(alpha = 0.4f)
                    isPortRestoring -> AccentOrange.copy(alpha = 0.4f)
                    else -> BorderGlass
                }
                val badgeText = when {
                    isPortLocked -> "● LOCKED : ${uiState.adbPort}"
                    isPortRestoring -> "⚡ MEMULIHKAN : ${uiState.adbPort}"
                    else -> "○ STANDBY"
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg,
                    border = BorderStroke(1.dp, badgeBorder)
                ) {
                    Text(
                        badgeText,
                        color = badgeColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            LynxSwitch(
                label = "Pertahankan Port ADB Saat Layar Mati",
                subLabel = "Auto-heal oleh daemon lynxd jika sistem Android mereset port saat sleep",
                checked = uiState.adbKeepAliveEnabled,
                onCheckedChange = { viewModel.setAdbKeepAlive(it) },
            )

            HorizontalDivider(
                color = BorderGlass.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            Text(
                "Konfigurasi Manual Custom Port (1024 – 65535)",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Quick port presets
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5555 to "5555 (Default)", 5556 to "5556", 8888 to "8888").forEach { (presetPort, label) ->
                    val isSelected = uiState.adbPort == presetPort
                    Surface(
                        onClick = { viewModel.applyAdbCustomPort(presetPort.toString()) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) AccentGreen.copy(alpha = 0.16f) else BgElevated,
                        border = BorderStroke(1.dp, if (isSelected) AccentGreen else BorderGlass),
                        modifier = Modifier.heightIn(min = 38.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                label,
                                color = if (isSelected) AccentGreen else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Manual custom port entry + Apply button
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
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgElevated,
                        border = BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "adb connect $ip:${uiState.adbPort}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentCyan
                            )
                            Text(
                                if (uiState.isAdbListening) "Tersambung" else "Memulihkan...",
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

/**
 * Backward compatibility alias for legacy call sites.
 */
@Composable
fun TuningNetworkCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    NetworkSubscreen(uiState = uiState, viewModel = viewModel, modifier = modifier)
}
