package com.noir.lynx.feature.battery.ui
import com.noir.lynx.ui.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.data.BatteryInfoStats
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.ui.LynxViewModel
import java.util.Locale

@Composable
fun BatteryHardwareCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val stats = uiState.batteryInfoStats
    val currentLevel = stats.currentLevel.takeIf { it > 0 } ?: (uiState.batteryDetails?.level ?: 0)
    BatteryHardwareCard(
        stats = stats,
        currentLevel = currentLevel,
        isCalibrating = uiState.isCalibratingBattery,
        onCalibrate = { viewModel.calibrateBatteryStats() },
        onOpenDetail = { viewModel.openBatteryDetailSheet() },
        modifier = modifier
    )
}

@Composable
fun BatteryHardwareCard(
    stats: BatteryInfoStats,
    currentLevel: Int,
    isCalibrating: Boolean,
    onCalibrate: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCalibrateConfirm by remember { mutableStateOf(false) }

    val soh = stats.stateOfHealthPercent.coerceIn(0, 100)
    val wear = stats.wearLevelPercent.coerceIn(0, 100)
    val sohAccent = when {
        soh <= 0 -> AccentCyan
        soh >= 85 -> AccentGreen
        soh >= 70 -> AccentCyan
        else -> AccentOrange
    }
    val lostMah = if (stats.designCapacityMah > stats.fullChargeCapacityMah && stats.fullChargeCapacityMah > 0) {
        stats.designCapacityMah - stats.fullChargeCapacityMah
    } else 0
    val nominalV = if (stats.nominalVoltageMv > 0) stats.nominalVoltageMv / 1000f else 3.85f
    val designWh = if (stats.designCapacityMah > 0) (stats.designCapacityMah * nominalV) / 1000f else 0f
    val fullWh = if (stats.fullChargeCapacityMah > 0) (stats.fullChargeCapacityMah * nominalV) / 1000f else 0f

    LynxCard(
        title = stringResource(R.string.battery_hardware_card_title),
        subtitle = "Kesehatan sel (SoH), kapasitas BMS & diagnostik sensor fisik",
        icon = Icons.Default.Memory,
        accentColor = AccentBlue,
        modifier = modifier
    ) {
        // 1. State of Health (SoH) & Cell Degradation Hero Banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, sohAccent.copy(alpha = 0.40f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = sohAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Column {
                            Text(
                                text = "Kesehatan Sel Baterai (State of Health)",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (soh > 0) "$soh% Kapasitas Utuh" else "Kesehatan Kernel: ${stats.kernelHealthText}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = sohAccent.copy(alpha = 0.14f),
                        border = BorderStroke(0.6.dp, sohAccent.copy(alpha = 0.50f))
                    ) {
                        Text(
                            text = stats.healthVerdictBadge.ifBlank { stats.kernelHealthText },
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = sohAccent,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                if (soh > 0) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BgSurfaceLowest)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((soh / 100f).coerceIn(0.05f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(sohAccent.copy(alpha = 0.8f), sohAccent)
                                    )
                                )
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (lostMah > 0) "Degradasi sel: $wear% (-$lostMah mAh)" else "Degradasi sel: 0% (Kapasitas penuh)",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = stats.remainingCycleEstimateText,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 2. 2x2 Primary Hardware Capacities & Cycle Count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(11.dp)) {
                    Text(
                        text = stringResource(R.string.battery_design_capacity),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (stats.designCapacityMah > 0) "${stats.designCapacityMah} mAh" else "-",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (designWh > 0f) {
                        Text(
                            text = String.format(Locale.US, "≈ %.1f Wh (3.85V)", designWh),
                            fontSize = 9.5.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(11.dp)) {
                    Text(
                        text = stringResource(R.string.battery_full_charge_capacity),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (stats.fullChargeCapacityMah > 0) "${stats.fullChargeCapacityMah} mAh" else "-",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                    if (fullWh > 0f) {
                        Text(
                            text = String.format(Locale.US, "≈ %.1f Wh aktual", fullWh),
                            fontSize = 9.5.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(11.dp)) {
                    Text(
                        text = stringResource(R.string.battery_remaining_charge),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (stats.remainingChargeMah > 0) "${stats.remainingChargeMah} mAh ($currentLevel%)" else "$currentLevel%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated,
                border = BorderStroke(0.6.dp, BorderSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(11.dp)) {
                    Text(
                        text = stringResource(R.string.battery_cycle_count),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (stats.cycleCount >= 0) stringResource(R.string.battery_cycles_suffix, stats.cycleCount) else stats.kernelHealthText.ifBlank { "-" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 3. Multi-Point Thermal Sensors Strip (Battery, Charger IC, USB Port when available)
        val hasMultiThermal = stats.bmsTempC > 0f || stats.chargerIcTempC > 0f || stats.usbPortTempC > 0f
        if (hasMultiThermal) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (stats.bmsTempC > 0f) {
                    ThermalSensorChip(
                        label = "Suhu Sel BMS",
                        tempC = stats.bmsTempC,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (stats.chargerIcTempC > 0f) {
                    ThermalSensorChip(
                        label = "Charger IC",
                        tempC = stats.chargerIcTempC,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (stats.usbPortTempC > 0f) {
                    ThermalSensorChip(
                        label = "Port USB",
                        tempC = stats.usbPortTempC,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // 4. Non-Redundant BMS Hardware Architecture Details
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = BgElevated,
            border = BorderStroke(0.6.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.battery_fuel_gauge_chip), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = stats.fuelGaugeChip,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.battery_tech_kernel_health), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = "${stats.technology} • ${stats.kernelHealthText}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                if (stats.batteryResistanceMohm > 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Resistansi Internal Sel (ESR)", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                        Text(
                            text = "${stats.batteryResistanceMohm} mΩ",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    }
                }
                if (stats.averageCurrentMa != 0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Arus Rata-rata BMS", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                        Text(
                            text = "${stats.averageCurrentMa} mA",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (stats.averageCurrentMa > 0) AccentGreen else AccentOrange
                        )
                    }
                }
                if (stats.negotiatedProtocolText != "-" && stats.isCharging) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Protokol & VBUS Aktif", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                        Text(
                            text = if (stats.vbusVoltageV > 0f) {
                                String.format(Locale.US, "%s (%.2f V)", stats.negotiatedProtocolText, stats.vbusVoltageV)
                            } else stats.negotiatedProtocolText,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 5. Button: View complete raw ADC details (48.dp touch target)
        Surface(
            onClick = onOpenDetail,
            shape = RoundedCornerShape(12.dp),
            color = AccentCyan.copy(alpha = 0.10f),
            border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.40f)),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Analytics, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.battery_open_adc_matrix), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AccentCyan)
            }
        }

        Spacer(Modifier.height(8.dp))

        // 6. Button: Calibrate & Reset Battery Stats with confirmation dialog (48.dp touch target)
        Surface(
            onClick = { if (!isCalibrating) showCalibrateConfirm = true },
            enabled = !isCalibrating,
            shape = RoundedCornerShape(12.dp),
            color = AccentBlue.copy(alpha = 0.12f),
            border = BorderStroke(0.8.dp, AccentBlue.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCalibrating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = AccentBlue,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.battery_calibrating), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.battery_calibrate_btn), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                }
            }
        }
    }

    if (showCalibrateConfirm) {
        AlertDialog(
            onDismissRequest = { showCalibrateConfirm = false },
            containerColor = BgCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = "Kalibrasi Fuel Gauge & Reset Statistik?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Tindakan ini akan menyinkronkan ulang pembacaan fuel gauge kernel dan mereset riwayat siklus berjalan (SOT, langkah penurunan 1%, serta statistik konsumsi aplikasi saat ini).",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCalibrateConfirm = false
                        onCalibrate()
                    }
                ) {
                    Text("Kalibrasi Sekarang", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCalibrateConfirm = false }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ThermalSensorChip(
    label: String,
    tempC: Float,
    modifier: Modifier = Modifier
) {
    val chipColor = when {
        tempC >= 45f -> AccentRed
        tempC >= 41f -> AccentOrange
        else -> AccentGreen
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BgElevated,
        border = BorderStroke(0.6.dp, chipColor.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Thermostat,
                    contentDescription = null,
                    tint = chipColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = String.format(Locale.US, "%.1f°C", tempC),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = chipColor
            )
        }
    }
}
