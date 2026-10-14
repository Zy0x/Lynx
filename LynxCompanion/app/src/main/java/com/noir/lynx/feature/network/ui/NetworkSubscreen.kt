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
        // ── TCP Congestion Control Card ──────────────────────────
        LaunchedEffect(Unit) { viewModel.refreshTcpAlgorithms() }
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

        // ── Subsystem Audio & Wi-Fi Enhancer Card ────────────────
        LynxCard(
            title = "Subsystem Audio & Wi-Fi Enhancer",
            icon = Icons.Default.Wifi,
            accentColor = AccentBlue
        ) {
            LynxSwitch(
                label = "Wi-Fi Ping Stabilizer & FQ-CoDel",
                subLabel = "Mengurangi bufferbloat dan jitter koneksi game online",
                checked = state.network.wifiPingStabilizer,
                onCheckedChange = { viewModel.setWifiPingStabilizer(it) },
            )
            LynxSwitch(
                label = "Low-Latency Audio MMAP",
                subLabel = "Bypass mixer audio Android untuk latensi respon suara terendah",
                checked = state.audio.lowLatencyMmap,
                onCheckedChange = { viewModel.setAudioMmap(it) },
            )
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

        // ── Sound Control (Gain Booster) Card ────────────────────
        SoundControlCard(
            soundControl = uiState.soundControl,
            onSetGain = { hpL, hpR, spk, mic, hpMode ->
                viewModel.setSoundGain(hpL, hpR, spk, mic, hpMode)
            }
        )
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
