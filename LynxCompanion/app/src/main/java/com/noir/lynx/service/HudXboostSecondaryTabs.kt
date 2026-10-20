package com.noir.lynx.service

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * HudXboostSecondaryTabs — Provides content for tabs 2, 3, and 4:
 * Tab 2: Alat Akses Cepat (Hz, TouchBoost, Bypass Charging)
 * Tab 3: Anti-gangguan (Blokir Notifikasi Heads-Up, Pencegahan Salah Sentuh)
 * Tab 4: Gameplay Tambahan (Crosshair, Benchmark 60s)
 */

@Composable
fun HudXboostQuickAccessTab(
    currentRefreshRate: Int,
    onCycleRefreshRate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isTouchBoostActive by remember { mutableStateOf(true) }
    var isBypassChargeActive by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = "ALAT AKSES CEPAT",
            color = MatrixTextPrimary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        // 1. Refresh Rate Switcher
        MatrixSettingCard(
            title = "Refresh Rate Layar",
            subtitle = "Saat ini: $currentRefreshRate Hz",
            icon = "⚡",
            actionText = "Ganti Hz",
            onAction = onCycleRefreshRate
        )

        // 2. TouchBoost Multi-OEM
        MatrixToggleCard(
            title = "TouchBoost Responsif",
            subtitle = "Meningkatkan responsivitas sentuhan layar",
            icon = "🎯",
            isChecked = isTouchBoostActive,
            onToggle = {
                isTouchBoostActive = !isTouchBoostActive
                coroutineScope.launch(Dispatchers.IO) {
                    val v = if (isTouchBoostActive) "1" else "0"
                    Shell.cmd("echo $v > /sys/class/touch/touch_dev/touch_boost 2>/dev/null").exec()
                }
            }
        )

        // 3. Bypass Charging / AutoCut
        MatrixToggleCard(
            title = "Bypass Charging (Direct Power)",
            subtitle = "Daya langsung ke motherboard, tahan panas baterai",
            icon = "🔋",
            isChecked = isBypassChargeActive,
            onToggle = {
                isBypassChargeActive = !isBypassChargeActive
                coroutineScope.launch(Dispatchers.IO) {
                    val v = if (isBypassChargeActive) "0" else "1"
                    Shell.cmd("echo $v > /sys/class/power_supply/battery/charging_enabled 2>/dev/null").exec()
                }
            }
        )
    }
}

@Composable
fun HudXboostDndTab(
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isHeadsUpBlocked by remember { mutableStateOf(false) }
    var isMistouchLocked by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = "PERISAI GAME (ANTI-GANGGUAN)",
            color = MatrixTextPrimary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        // 1. Blokir Notifikasi Heads-Up
        MatrixToggleCard(
            title = "Blokir Banner Notifikasi",
            subtitle = "Sembunyikan pop-up pesan WA saat bermain",
            icon = "🔕",
            isChecked = isHeadsUpBlocked,
            onToggle = {
                isHeadsUpBlocked = !isHeadsUpBlocked
                coroutineScope.launch(Dispatchers.IO) {
                    val v = if (isHeadsUpBlocked) "0" else "1"
                    Shell.cmd("settings put global heads_up_notifications_enabled $v 2>/dev/null").exec()
                }
            }
        )

        // 2. Kunci Tombol Navigasi / Pencegahan Salah Sentuh
        MatrixToggleCard(
            title = "Pencegahan Salah Sentuh",
            subtitle = "Kunci navigasi gestur tepi layar selama game",
            icon = "🛡️",
            isChecked = isMistouchLocked,
            onToggle = {
                isMistouchLocked = !isMistouchLocked
            }
        )
    }
}

@Composable
fun HudXboostGameplayTab(
    isHudBenchmarking: Boolean,
    hudBenchmarkCountdown: Int,
    hudBenchmarkSummary: com.noir.lynx.data.LynxBenchmarkResult?,
    onStartHudBenchmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isCrosshairEnabled by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = "EKSTRA GAMEPLAY",
            color = MatrixTextPrimary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        // 1. Crosshair / Virtual Aim
        MatrixToggleCard(
            title = "Crosshair Virtual Aim",
            subtitle = "Titik bidik presisi di tengah layar untuk FPS",
            icon = "🪄",
            isChecked = isCrosshairEnabled,
            onToggle = {
                isCrosshairEnabled = !isCrosshairEnabled
            }
        )

        // 2. Benchmark Stabilitas 60 Detik
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isHudBenchmarking) NeonMagenta.copy(alpha = 0.18f) else Color(0x33141F2E),
            border = BorderStroke(1.dp, if (isHudBenchmarking) NeonMagenta else Color(0x18FFFFFF)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (!isHudBenchmarking) {
                        onStartHudBenchmark()
                    }
                }
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("⏱", fontSize = 13.sp)
                        Text(
                            text = if (isHudBenchmarking) "Menguji Pacing... ${hudBenchmarkCountdown}s" else "Uji Stabilitas 60 Detik",
                            color = if (isHudBenchmarking) NeonMagenta else MatrixTextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (isHudBenchmarking) {
                        Text("${hudBenchmarkCountdown}s", color = NeonMagenta, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (hudBenchmarkSummary != null && !isHudBenchmarking) {
                    val stabPct = (100f - hudBenchmarkSummary.jankyFramesPercent).coerceIn(0f, 100f).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Stabilitas: $stabPct%", color = NeonGreen, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        Text("1% Low: ${hudBenchmarkSummary.fps1PercentLow.toInt()} FPS", color = NeonGold, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixToggleCard(
    title: String,
    subtitle: String,
    icon: String,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isChecked) NeonCyan.copy(alpha = 0.12f) else Color(0x33141F2E),
        border = BorderStroke(1.dp, if (isChecked) NeonCyan.copy(alpha = 0.4f) else Color(0x18FFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(icon, fontSize = 14.sp)
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(title, color = MatrixTextPrimary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = MatrixTextMuted, fontSize = 7.sp)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isChecked) NeonCyan else Color(0x22FFFFFF))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isChecked) "AKTIF" else "OFF",
                    color = if (isChecked) Color.Black else MatrixTextSecondary,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MatrixSettingCard(
    title: String,
    subtitle: String,
    icon: String,
    actionText: String,
    onAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0x33141F2E),
        border = BorderStroke(1.dp, Color(0x18FFFFFF)),
        modifier = Modifier.fillMaxWidth().clickable { onAction() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(icon, fontSize = 14.sp)
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(title, color = MatrixTextPrimary, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = MatrixTextMuted, fontSize = 7.sp)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NeonGold.copy(alpha = 0.2f))
                    .border(BorderStroke(0.8.dp, NeonGold), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(actionText, color = NeonGold, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
