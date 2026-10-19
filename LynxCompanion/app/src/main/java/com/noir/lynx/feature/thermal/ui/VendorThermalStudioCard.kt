package com.noir.lynx.feature.thermal.ui

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
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.hardware.BatteryChargingThresholds
import com.noir.lynx.hardware.VendorThermalConfigManager
import com.noir.lynx.hardware.VendorThermalStudioState
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch

/**
 * VendorThermalStudioCard — Interactive configuration studio for vendor-level thermal engine files
 * (Qualcomm thermal-engine.conf / MediaTek .tp/thermal.conf) with automated Magisk Magic Mount overlay generation.
 */
@Composable
fun VendorThermalStudioCard(
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var studioState by remember { mutableStateOf<VendorThermalStudioState?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var showRebootDialog by remember { mutableStateOf(false) }

    // Editable thresholds state
    var t1 by remember { mutableIntStateOf(38) }
    var t2 by remember { mutableIntStateOf(40) }
    var t3 by remember { mutableIntStateOf(43) }
    var t4 by remember { mutableIntStateOf(46) }
    var t5 by remember { mutableIntStateOf(50) }

    fun refreshState() {
        isLoading = true
        coroutineScope.launch {
            val state = VendorThermalConfigManager.detectState()
            studioState = state
            val active = state.activeThresholds
            t1 = active.t1
            t2 = active.t2
            t3 = active.t3
            t4 = active.t4
            t5 = active.t5
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshState()
    }

    val state = studioState ?: return
    if (!state.isSupported) return // Don't show if vendor conf not applicable

    val isExtremeHot = t5 >= 46

    LynxCard(
        title = "Studio Konfigurasi Thermal Vendor (Magic Mount)",
        icon = Icons.Default.Tune,
        accentColor = if (state.isOverlayActive) AccentOrange else AccentCyan,
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
                    text = if (state.isOverlayActive) {
                        "Modul Magic Mount Aktif di /system/vendor/etc/"
                    } else {
                        "Konfigurasi Pabrik Bawaan ROM Aktif"
                    },
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (state.isOverlayActive) AccentOrange.copy(alpha = 0.16f) else AccentGreen.copy(alpha = 0.16f),
                border = BorderStroke(0.8.dp, if (state.isOverlayActive) AccentOrange.copy(alpha = 0.6f) else AccentGreen.copy(alpha = 0.6f))
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
                            .background(if (state.isOverlayActive) AccentOrange else AccentGreen)
                    )
                    Text(
                        text = if (state.isOverlayActive) "MODUL AKTIF" else "OEM STOCK",
                        color = if (state.isOverlayActive) AccentOrange else AccentGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // ── 2. Context Explanation ──────────────────────────────────
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                Text(
                    text = "Sesuaikan tangga suhu proteksi pengisian daya ([BATTERY_CHARGING_CTL]). Nilai akan disimpan ke dalam modul Lynx dan aktif saat booting melalui Magisk Magic Mount.",
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp
                )
            }
        }

        // ── 3. Interactive Threshold Steps (Touch-First >= 48dp) ─────
        Text(
            text = "TANGGA SUHU MITIGASI PENGISIAN DAYA (BATTERY THROTTLING)",
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ThresholdStepperRow(
            label = "Level 1: Fast Charge Ringan",
            subLabel = "Arus mulai diturunkan ke ~2500mA",
            temp = t1,
            onTempChange = { t1 = it.coerceIn(35, t2 - 1) },
            accent = AccentGreen
        )

        ThresholdStepperRow(
            label = "Level 2: Pengisian Standar",
            subLabel = "Arus diturunkan ke ~2000mA",
            temp = t2,
            onTempChange = { t2 = it.coerceIn(t1 + 1, t3 - 1) },
            accent = AccentGreen
        )

        ThresholdStepperRow(
            label = "Level 3: Pengisian Moderat",
            subLabel = "Arus diturunkan ke ~1500mA",
            temp = t3,
            onTempChange = { t3 = it.coerceIn(t2 + 1, t4 - 1) },
            accent = AccentOrange
        )

        ThresholdStepperRow(
            label = "Level 4: Pengisian Darurat Sejuk",
            subLabel = "Arus dibatasi ke ~1000mA",
            temp = t4,
            onTempChange = { t4 = it.coerceIn(t3 + 1, t5 - 1) },
            accent = AccentOrange
        )

        ThresholdStepperRow(
            label = "Level 5: Batas Pemutusan Penuh (Safety Cut)",
            subLabel = "Trickle float / pengisian daya dihentikan",
            temp = t5,
            onTempChange = { t5 = it.coerceIn(t4 + 1, 55) },
            accent = if (t5 >= 46) AccentRed else AccentOrange
        )

        // ── 4. Dynamic Hysteresis & Safety Badges ───────────────────
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
                        text = "Histeresis Pemulihan Otomatis (-2°C):",
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
                        text = "Peringatan Suhu Tinggi: Ambang batas Level 5 disetel di $t5°C. Sangat disarankan menggunakan pendingin eksternal (phone cooler / kipas) saat pengisian cepat untuk mencegah baterai kembung.",
                        color = AccentRed,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

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

        // ── 5. Action Buttons (Touch-First >= 48dp) ──────────────────
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Apply CTA Button (Height 52dp)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AccentOrange.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, AccentOrange.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        coroutineScope.launch {
                            val newThresholds = BatteryChargingThresholds(
                                t1 = t1, t2 = t2, t3 = t3, t4 = t4, t5 = t5
                            )
                            val res = VendorThermalConfigManager.applyCustomThresholds(newThresholds)
                            if (res.isSuccess) {
                                actionMessage = "Overlay berhasil dibuat!"
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
                    Icon(Icons.Default.Save, null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Simpan & Buat Overlay Magisk",
                        color = AccentOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }

            // Reset Button (Height 44dp)
            if (state.isOverlayActive) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BgElevated,
                    border = BorderStroke(0.8.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
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
                    text = "Berkas konfigurasi termal vendor telah disimpan ke direktori modul Lynx (/system/vendor/etc/).\n\nAgar partisi sistem memuat konfigurasi ini melalui Magisk Magic Mount, perangkat perlu dimulai ulang (reboot).\n\nApakah Anda ingin memulai ulang sekarang?",
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
 * Reusable stepper row with >= 48dp touch targets for precise degree picking.
 */
@Composable
private fun ThresholdStepperRow(
    label: String,
    subLabel: String,
    temp: Int,
    onTempChange: (Int) -> Unit,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgElevated,
        border = BorderStroke(0.6.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = label,
                    color = TextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
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
                // Minus Button (48dp Touch Box)
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

                // Plus Button (48dp Touch Box)
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
