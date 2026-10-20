package com.noir.lynx.feature.thermal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.hardware.BatteryChargingThresholds
import com.noir.lynx.hardware.LiveThermalTelemetry
import com.noir.lynx.hardware.VendorThermalConfigManager
import com.noir.lynx.hardware.VendorThermalStudioState
import com.noir.lynx.hardware.VendorThermalTweaks
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * VendorThermalStudioCard — Interactive configuration studio for vendor-level thermal engine files
 * (Qualcomm thermal-engine.conf / MediaTek .tp/thermal.conf).
 * Features real-time mitigation radar, anti-dimming, CPU/GPU headroom, instant live bind mounting,
 * and permanent Magisk Magic Mount overlay generation.
 */
@Composable
fun VendorThermalStudioCard(
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var studioState by remember { mutableStateOf<VendorThermalStudioState?>(null) }
    var telemetry by remember { mutableStateOf(LiveThermalTelemetry()) }
    var isLoading by remember { mutableStateOf(true) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var showRebootDialog by remember { mutableStateOf(false) }

    // Editable thresholds state
    var t1 by remember { mutableIntStateOf(38) }
    var t2 by remember { mutableIntStateOf(40) }
    var t3 by remember { mutableIntStateOf(43) }
    var t4 by remember { mutableIntStateOf(46) }
    var t5 by remember { mutableIntStateOf(50) }

    // Tweaks state
    var antiDimming by remember { mutableStateOf(false) }
    var cpuGpuHeadroom by remember { mutableStateOf(false) }

    fun refreshState() {
        coroutineScope.launch {
            val state = VendorThermalConfigManager.detectState()
            studioState = state
            val active = state.activeThresholds
            t1 = active.t1
            t2 = active.t2
            t3 = active.t3
            t4 = active.t4
            t5 = active.t5
            antiDimming = state.tweaks.antiLcdDimming
            cpuGpuHeadroom = state.tweaks.cpuGpuHeadroom
            telemetry = state.telemetry
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshState()
        // Real-time telemetry radar polling loop (every 2.5 seconds)
        while (isActive) {
            delay(2500)
            val currentThresholds = BatteryChargingThresholds(t1, t2, t3, t4, t5)
            telemetry = VendorThermalConfigManager.queryLiveTelemetry(currentThresholds)
        }
    }

    val state = studioState ?: return
    if (!state.isSupported) return // Don't show if vendor conf not applicable

    val isExtremeHot = t5 >= 46

    LynxCard(
        title = "Studio Konfigurasi Thermal Vendor (Magic Mount)",
        icon = Icons.Default.Tune,
        accentColor = if (state.isLiveBindMounted) AccentCyan else if (state.isOverlayActive) AccentOrange else AccentGreen,
        modifier = modifier
    ) {
        // ── 1. Status Header & Overlay Pill ──────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (state.vendorArchitecture) {
                        "qcom" -> "Qualcomm Snapdragon (thermal-engine.conf)"
                        "mtk" -> "MediaTek (thermal.conf)"
                        else -> "Vendor Thermal Engine"
                    },
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        state.isLiveBindMounted -> "⚡ Live Bind Mount Aktif (Tanpa Reboot)"
                        state.isOverlayActive -> "Modul Magic Mount Aktif di /system/vendor/etc/"
                        else -> "Konfigurasi Pabrik Bawaan ROM Aktif"
                    },
                    color = if (state.isLiveBindMounted) AccentCyan else TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = if (state.isLiveBindMounted) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = when {
                    state.isLiveBindMounted -> AccentCyan.copy(alpha = 0.16f)
                    state.isOverlayActive -> AccentOrange.copy(alpha = 0.16f)
                    else -> AccentGreen.copy(alpha = 0.16f)
                },
                border = BorderStroke(
                    0.8.dp,
                    when {
                        state.isLiveBindMounted -> AccentCyan.copy(alpha = 0.6f)
                        state.isOverlayActive -> AccentOrange.copy(alpha = 0.6f)
                        else -> AccentGreen.copy(alpha = 0.6f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    state.isLiveBindMounted -> AccentCyan
                                    state.isOverlayActive -> AccentOrange
                                    else -> AccentGreen
                                }
                            )
                    )
                    Text(
                        text = when {
                            state.isLiveBindMounted -> "LIVE BIND"
                            state.isOverlayActive -> "MODUL AKTIF"
                            else -> "OEM STOCK"
                        },
                        color = when {
                            state.isLiveBindMounted -> AccentCyan
                            state.isOverlayActive -> AccentOrange
                            else -> AccentGreen
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // ── 2. Radar Telemetri Mitigasi Real-Time ───────────────────
        val currentTempColor = when {
            telemetry.sensorTempC >= 46f -> AccentRed
            telemetry.sensorTempC >= 41f -> AccentOrange
            telemetry.sensorTempC > 0f -> AccentGreen
            else -> TextSecondary
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(1.dp, currentTempColor.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(currentTempColor)
                        )
                        Text(
                            text = "RADAR MITIGASI SUHU REAL-TIME",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (telemetry.daemonPid.isNotEmpty()) {
                        Text(
                            text = "PID: ${telemetry.daemonPid}",
                            color = TextSecondary.copy(alpha = 0.6f),
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (telemetry.sensorTempC > 0f) String.format("%.1f°C", telemetry.sensorTempC) else "Memindai...",
                            color = currentTempColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Sensor: quiet-therm-adc",
                            color = TextSecondary,
                            fontSize = 9.5.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        val activeLevelText = when (telemetry.activeMitigationLevel) {
                            0 -> "Full Throttle (~3.200 mA)"
                            1 -> "Level 1 (~2.500 mA)"
                            2 -> "Level 2 (~2.000 mA)"
                            3 -> "Level 3 (~1.500 mA)"
                            4 -> "Level 4 (~1.000 mA)"
                            5 -> "Level 5 (Cutoff / 0 mA)"
                            else -> "Standby"
                        }
                        Text(
                            text = activeLevelText,
                            color = if (telemetry.activeMitigationLevel > 0) AccentOrange else AccentGreen,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (telemetry.isThrottled) "⚠️ Mitigasi Arus Sedang Aktif" else "⚡ Maksimum (Unthrottled)",
                            color = if (telemetry.isThrottled) AccentOrange else AccentGreen,
                            fontSize = 9.5.sp
                        )
                    }
                }
            }
        }

        // ── 3. Tangga Suhu Mitigasi Pengisian Daya ───────────────────
        Text(
            text = "TANGGA SUHU PENGISIAN DAYA ([BATTERY_CHARGING_CTL])",
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ThresholdStepperRow(
            label = "Level 1: Fast Charge Ringan",
            subLabel = "Arus diturunkan ke ~2500mA",
            temp = t1,
            isActive = telemetry.activeMitigationLevel == 1,
            onTempChange = { t1 = it.coerceIn(35, t2 - 1) },
            accent = AccentGreen
        )

        ThresholdStepperRow(
            label = "Level 2: Pengisian Standar",
            subLabel = "Arus diturunkan ke ~2000mA",
            temp = t2,
            isActive = telemetry.activeMitigationLevel == 2,
            onTempChange = { t2 = it.coerceIn(t1 + 1, t3 - 1) },
            accent = AccentGreen
        )

        ThresholdStepperRow(
            label = "Level 3: Pengisian Moderat",
            subLabel = "Arus diturunkan ke ~1500mA",
            temp = t3,
            isActive = telemetry.activeMitigationLevel == 3,
            onTempChange = { t3 = it.coerceIn(t2 + 1, t4 - 1) },
            accent = AccentOrange
        )

        ThresholdStepperRow(
            label = "Level 4: Pengisian Darurat Sejuk",
            subLabel = "Arus dibatasi ke ~1000mA",
            temp = t4,
            isActive = telemetry.activeMitigationLevel == 4,
            onTempChange = { t4 = it.coerceIn(t3 + 1, t5 - 1) },
            accent = AccentOrange
        )

        ThresholdStepperRow(
            label = "Level 5: Batas Pemutusan Penuh (Safety Cut)",
            subLabel = "Trickle float / pengisian daya dihentikan",
            temp = t5,
            isActive = telemetry.activeMitigationLevel == 5,
            onTempChange = { t5 = it.coerceIn(t4 + 1, 55) },
            accent = if (t5 >= 46) AccentRed else AccentOrange
        )

        // Hysteresis Formula Info
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = BgDeepOled,
            border = BorderStroke(0.6.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Autorenew, null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                    Text(
                        text = "Histeresis Pemulihan Otomatis (-2°C Anti-Flapping):",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Pemicu: $t1°C / $t2°C / $t3°C / $t4°C / $t5°C\nPulih: ${(t1 - 2).coerceAtLeast(30)}°C / ${(t2 - 2).coerceAtLeast(31)}°C / ${(t3 - 2).coerceAtLeast(32)}°C / ${(t4 - 2).coerceAtLeast(33)}°C / ${(t5 - 2).coerceAtLeast(34)}°C",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (isExtremeHot) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AccentRedDim,
                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, null, tint = AccentRed, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Peringatan Suhu Tinggi: Level 5 disetel di $t5°C. Sangat disarankan menggunakan pendingin eksternal (phone cooler / kipas) saat pengisian cepat untuk mencegah baterai kembung.",
                        color = AccentRed,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // ── 4. Aturan Ekstra: Anti-Dimming & CPU/GPU Headroom ─────────
        Text(
            text = "ATURAN MITIGASI VENDOR TAMBAHAN",
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )

        LynxSwitch(
            label = "Cegah Layar Meredup Otomatis (Anti-Dimming)",
            subLabel = "Menaikkan ambang batas [LCD_MANAGEMENT] ke 56°C agar kecerahan layar tidak otomatis meredup sendiri saat ponsel hangat atau sedang dicas",
            checked = antiDimming,
            onCheckedChange = { antiDimming = it }
        )

        Spacer(Modifier.height(4.dp))

        LynxSwitch(
            label = "Headroom Termal CPU & GPU (+5°C Sustained)",
            subLabel = "Melonggarkan set point throttling CPU cluster Silver/Gold dan GPU Adreno agar clock tidak terpotong terlalu dini saat bermain game berat",
            checked = cpuGpuHeadroom,
            onCheckedChange = { cpuGpuHeadroom = it }
        )

        // Action Message
        actionMessage?.let { msg ->
            Text(
                text = msg,
                color = AccentCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // ── 5. Baris Tombol Aksi (Live Bind Mount & Permanent Mount) ──
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Live Bind Mount Button (Height 50dp, Instant Reload)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (state.isLiveBindMounted) AccentCyan.copy(alpha = 0.2f) else AccentCyan.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        coroutineScope.launch {
                            val newThresholds = BatteryChargingThresholds(t1, t2, t3, t4, t5)
                            val tweaks = VendorThermalTweaks(antiLcdDimming = antiDimming, cpuGpuHeadroom = cpuGpuHeadroom)
                            val res = VendorThermalConfigManager.applyLiveBindMount(newThresholds, tweaks)
                            if (res.isSuccess) {
                                actionMessage = "⚡ Live Bind Mount Aktif! Daemon termal dimuat ulang seketika."
                                refreshState()
                            } else {
                                actionMessage = "Gagal Live Mount: ${res.exceptionOrNull()?.message}"
                            }
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Bolt, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.isLiveBindMounted) "Perbarui Uji Coba Instan (Live Bind)" else "Uji Coba Instan (Tanpa Reboot)",
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Permanent Magisk Overlay Button (Height 50dp)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AccentOrange.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        coroutineScope.launch {
                            val newThresholds = BatteryChargingThresholds(t1, t2, t3, t4, t5)
                            val tweaks = VendorThermalTweaks(antiLcdDimming = antiDimming, cpuGpuHeadroom = cpuGpuHeadroom)
                            val res = VendorThermalConfigManager.applyCustomThresholds(newThresholds, tweaks)
                            if (res.isSuccess) {
                                actionMessage = "Konfigurasi permanen disimpan ke modul Lynx."
                                showRebootDialog = true
                                refreshState()
                            } else {
                                actionMessage = "Gagal Simpan: ${res.exceptionOrNull()?.message}"
                            }
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Save, null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Simpan Permanen ke Modul Magisk",
                        color = AccentOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Cancel Live Mount Button (if active)
            if (state.isLiveBindMounted) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            coroutineScope.launch {
                                val res = VendorThermalConfigManager.cancelLiveBindMount()
                                if (res.isSuccess) {
                                    actionMessage = "Live Bind Mount telah dibatalkan."
                                    refreshState()
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Batalkan Uji Coba Live",
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Reset to Stock Button
            if (state.isOverlayActive) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            coroutineScope.launch {
                                val res = VendorThermalConfigManager.resetToStockBaseline()
                                if (res.isSuccess) {
                                    actionMessage = "Overlay dihapus. Kembali ke stock baseline."
                                    showRebootDialog = true
                                    refreshState()
                                } else {
                                    actionMessage = "Gagal: ${res.exceptionOrNull()?.message}"
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Restore, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Pulihkan Konfigurasi Bawaan Pabrik (Stock)",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // ── 6. Magic Mount Confirmation Dialog ───────────────────────────
    if (showRebootDialog) {
        AlertDialog(
            onDismissRequest = { showRebootDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(22.dp))
                    Text(
                        text = "Magic Mount Overlay Disiapkan",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = "Berkas konfigurasi termal vendor telah disimpan ke direktori modul Lynx (/system/vendor/etc/).\n\nAgar partisi sistem memuat konfigurasi ini secara permanen melalui Magisk Magic Mount, perangkat perlu dimulai ulang (reboot).\n\nApakah Anda ingin memulai ulang sekarang?",
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentCyan.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, AccentCyan),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            showRebootDialog = false
                            Shell.cmd("/system/bin/reboot").submit()
                        }
                ) {
                    Text(
                        text = "Mulai Ulang Sekarang",
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebootDialog = false }) {
                    Text("Nanti Saja", color = TextSecondary, fontSize = 12.5.sp)
                }
            },
            containerColor = BgElevated,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        )
    }
}

/**
 * Reusable stepper row with active status badge and glowing neon highlight.
 */
@Composable
private fun ThresholdStepperRow(
    label: String,
    subLabel: String,
    temp: Int,
    isActive: Boolean,
    onTempChange: (Int) -> Unit,
    accent: Color
) {
    val borderColor by animateColorAsState(
        targetValue = if (isActive) AccentCyan else BorderSubtle,
        label = "stepperBorder"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) AccentCyan.copy(alpha = 0.08f) else BgElevated,
        border = BorderStroke(if (isActive) 1.2.dp else 0.6.dp, borderColor),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isActive) AccentCyan else TextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AccentCyan.copy(alpha = 0.2f),
                            border = BorderStroke(0.6.dp, AccentCyan)
                        ) {
                            Text(
                                text = "SEDANG AKTIF",
                                color = AccentCyan,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subLabel,
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    lineHeight = 12.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Minus Button (44dp Touch Box)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(BgDeepOled)
                        .clickable { onTempChange(temp - 1) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Remove, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                }

                // Temp Value Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accent.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, accent.copy(alpha = 0.5f)),
                    modifier = Modifier.width(52.dp).height(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$temp°C",
                            color = accent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Plus Button (44dp Touch Box)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(BgDeepOled)
                        .clickable { onTempChange(temp + 1) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
