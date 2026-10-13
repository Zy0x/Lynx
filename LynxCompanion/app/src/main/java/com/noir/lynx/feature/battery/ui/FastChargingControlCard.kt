package com.noir.lynx.feature.battery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.feature.battery.ui.dialogs.CustomCurrentLimitDialog
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.closeCustomCurrentDialog
import com.noir.lynx.ui.openCustomCurrentDialog
import com.noir.lynx.ui.setChargingMode
import com.noir.lynx.ui.setCustomCurrentLimit
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

@Composable
fun FastChargingControlCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state
    val battDetails = uiState.batteryDetails

    var showHyperConfirmDialog by remember { mutableStateOf(false) }

    val activeMode = remember(state.charging.chargingMode, state.charging.extremeChargingEnabled, state.charging.limitCurrentMa) {
        when {
            state.charging.chargingMode.isNotBlank() -> state.charging.chargingMode.lowercase()
            state.charging.extremeChargingEnabled -> "hyper"
            state.charging.limitCurrentMa in 500..2500 -> "cool"
            state.charging.limitCurrentMa > 2500 && state.charging.limitCurrentMa != 6000 -> "custom"
            else -> "standard"
        }
    }

    LynxCard(
        title = stringResource(R.string.battery_super_fast_charging),
        icon = Icons.Default.Bolt,
        accentColor = AccentCyan,
        modifier = modifier
    ) {
        // 4-Tier Charging Mode Selector (2x2 Matrix Grid)
        Text(
            text = "MODE PENGISIAN DAYA",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )
        Spacer(Modifier.height(6.dp))

        val modes = listOf(
            ChargingModeItem(
                id = "cool",
                title = stringResource(R.string.battery_mode_cool),
                sub = stringResource(R.string.battery_mode_cool_sub),
                icon = Icons.Default.AcUnit,
                accent = AccentCyan
            ),
            ChargingModeItem(
                id = "standard",
                title = stringResource(R.string.battery_mode_standard),
                sub = stringResource(R.string.battery_mode_standard_sub),
                icon = Icons.Default.Balance,
                accent = AccentGreen
            ),
            ChargingModeItem(
                id = "hyper",
                title = stringResource(R.string.battery_mode_hyper),
                sub = stringResource(R.string.battery_mode_hyper_sub),
                icon = Icons.Default.Bolt,
                accent = AccentOrange
            ),
            ChargingModeItem(
                id = "custom",
                title = stringResource(R.string.battery_mode_custom),
                sub = if (activeMode == "custom") "${state.charging.limitCurrentMa} mA" else stringResource(R.string.battery_mode_custom_sub),
                icon = Icons.Default.Tune,
                accent = AccentPurple
            )
        )

        // Row 1: Cool & Standard
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChargingModeCard(
                item = modes[0],
                isSelected = activeMode == "cool",
                onClick = { viewModel.setChargingMode("cool", 1000) },
                modifier = Modifier.weight(1f)
            )
            ChargingModeCard(
                item = modes[1],
                isSelected = activeMode == "standard",
                onClick = { viewModel.setChargingMode("standard") },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        // Row 2: Hyper & Custom
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChargingModeCard(
                item = modes[2],
                isSelected = activeMode == "hyper",
                onClick = {
                    if (activeMode != "hyper") {
                        showHyperConfirmDialog = true
                    }
                },
                modifier = Modifier.weight(1f)
            )
            ChargingModeCard(
                item = modes[3],
                isSelected = activeMode == "custom",
                onClick = {
                    viewModel.openCustomCurrentDialog()
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(6.dp))

        // Contextual Mode Description Card
        val modeDesc = when (activeMode) {
            "cool" -> stringResource(R.string.battery_mode_cool_desc)
            "standard" -> stringResource(R.string.battery_mode_standard_desc)
            "hyper" -> stringResource(R.string.battery_mode_hyper_desc)
            "custom" -> "Batas arus manual disetel ke ${state.charging.limitCurrentMa} mA dengan penguncian sysfs aktif."
            else -> stringResource(R.string.battery_mode_standard_desc)
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = BgElevated.copy(alpha = 0.35f),
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text(
                text = modeDesc,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        // Active Mode Status Pill
        val isPlugged = battDetails?.isPlugged == true
        val isCharging = battDetails?.isCharging == true
        val curLevel = battDetails?.level ?: 0
        val isBypassFeatureEnabled = state.charging.bypassEnabled
        val smartLimit = state.charging.maxBatteryPercent.coerceIn(50, 100)
        val isOvernightLatched = isPlugged && (battDetails?.isOvernightBypassLatched == true || curLevel >= 100)
        val isLimitLatched = isPlugged && smartLimit < 100 && (battDetails?.isSmartLimitLatched == true || (battDetails?.isBypassActive == true && curLevel >= (smartLimit - 2)))
        val isLimitUnapplied = isPlugged && smartLimit < 100 && curLevel >= smartLimit && !isLimitLatched
        val isManualBypass = isPlugged && isBypassFeatureEnabled && battDetails?.isBypassActive == true
        val isNightGentle = isPlugged && (battDetails?.isNightGentleActive == true || (state.charging.nightSleepGuardEnabled && isCharging && Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { it >= 23 || it < 6 })) && !isManualBypass
        val isSmartTapering = isPlugged && isCharging && battDetails?.isSmartTaperingActive == true && state.charging.smartTaperingEnabled && !isManualBypass
        val detectedProtocol = battDetails?.fastChargeProtocol.orEmpty().trim()
        val isHardwareFastChargeActive = isPlugged && isCharging && detectedProtocol.isNotBlank() &&
            !detectedProtocol.contains("standard", ignoreCase = true) &&
            !detectedProtocol.contains("battery power", ignoreCase = true) &&
            !detectedProtocol.contains("unknown", ignoreCase = true)

        val modePillLabel = when {
            !isPlugged -> stringResource(R.string.battery_status_disconnected)
            isOvernightLatched -> stringResource(R.string.battery_status_full_autocut)
            isLimitLatched -> stringResource(R.string.battery_status_smart_limit_reached, smartLimit, curLevel)
            isLimitUnapplied -> stringResource(R.string.battery_status_smart_limit_unapplied, smartLimit, curLevel)
            isManualBypass -> stringResource(R.string.battery_status_manual_bypass)
            isNightGentle -> stringResource(R.string.battery_status_night_gentle)
            isSmartTapering -> stringResource(R.string.battery_status_smart_tapering)
            activeMode == "hyper" -> "Mode Hyper Aktif • Daya Penuh"
            activeMode == "cool" -> "Mode Sejuk Aktif • Dibatasi ~2.0A"
            activeMode == "custom" -> "Mode Kustom Aktif • ${state.charging.limitCurrentMa} mA"
            activeMode == "standard" -> "Mode Standar Aktif • Regulasi OEM"
            isHardwareFastChargeActive -> stringResource(R.string.battery_status_fast_charging_max)
            else -> stringResource(R.string.battery_status_standard_ma, state.charging.limitCurrentMa)
        }
        val modePillColor = when {
            !isPlugged -> TextSecondary
            isOvernightLatched || isLimitLatched || isManualBypass -> AccentCyan
            isLimitUnapplied -> AccentOrange
            isNightGentle -> AccentPurple
            isSmartTapering -> AccentGreen
            activeMode == "hyper" -> AccentOrange
            activeMode == "cool" -> AccentCyan
            activeMode == "custom" -> AccentPurple
            else -> AccentGreen
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = modePillColor.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, modePillColor.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        !isPlugged -> Icons.Default.BatteryStd
                        isOvernightLatched || isLimitLatched || isManualBypass -> Icons.Default.Shield
                        isLimitUnapplied -> Icons.Default.Warning
                        isNightGentle -> Icons.Default.Bedtime
                        activeMode == "hyper" -> Icons.Default.Bolt
                        activeMode == "cool" -> Icons.Default.AcUnit
                        activeMode == "custom" -> Icons.Default.Tune
                        else -> Icons.Default.BatteryChargingFull
                    },
                    contentDescription = null,
                    tint = modePillColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = modePillLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = modePillColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Laptop / PC Port Safeguard Alert
        if (battDetails?.isLaptopPort == true && isPlugged) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AccentCyan.copy(alpha = 0.12f),
                border = BorderStroke(1.2.dp, AccentCyan.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Laptop,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.battery_laptop_port_detected, battDetails.portType),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                        Text(
                            text = stringResource(R.string.battery_laptop_port_desc),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.5.sp,
                            color = TextPrimary.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Dual Wattmeter
        if (battDetails != null) {
            val inputWatt = if (battDetails.adapterWatt > 0.05f) battDetails.adapterWatt else battDetails.chargerWatt
            val netBattWatt = (battDetails.voltageMv.toFloat() * battDetails.currentMa.coerceAtLeast(0).toFloat()) / 1_000_000f
            val netWattDisplay = if (netBattWatt > 0.05f) netBattWatt else battDetails.chargerWatt
            val efficiency = battDetails.chargingEfficiencyPercent.coerceIn(0, 100)

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = BgElevated.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Column A: Adapter Input
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BgCard,
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isPlugged) stringResource(R.string.battery_adapter_power) else stringResource(R.string.battery_connection_status),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (isPlugged && inputWatt > 0.05f) String.format(Locale.US, "%.1f W", inputWatt) else (if (isPlugged) "--" else stringResource(R.string.battery_status_disconnected)),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPlugged) AccentPurple else TextSecondary
                                )
                                Text(
                                    text = if (isPlugged) {
                                        val voltVal = if (battDetails.adapterVoltageMv > 1000) battDetails.adapterVoltageMv / 1000f else (if (battDetails.chargerVoltageMv > 1000) battDetails.chargerVoltageMv / 1000f else battDetails.voltageMv / 1000f)
                                        val curVal = if (battDetails.adapterCurrentMa > 0) battDetails.adapterCurrentMa else battDetails.currentMa.coerceAtLeast(0)
                                        String.format(Locale.US, "%.1f V • %d mA", voltVal, curVal)
                                    } else {
                                        stringResource(R.string.battery_cable_not_connected)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Column B: Battery Net Power
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BgCard,
                            border = BorderStroke(0.8.dp, BorderGlass),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isPlugged) stringResource(R.string.battery_current_to_battery) else stringResource(R.string.battery_battery_consumption),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                                Spacer(Modifier.height(2.dp))
                                val curColor = if (!isPlugged) AccentCyan else if (battDetails.currentMa > 100) AccentGreen else if (state.charging.bypassEnabled || isOvernightLatched) AccentCyan else TextPrimary
                                val battWatt = if (!isPlugged) {
                                    (battDetails.voltageMv.toFloat() * abs(battDetails.currentMa).toFloat()) / 1_000_000f
                                } else netWattDisplay
                                Text(
                                    text = if (battWatt > 0.05f) String.format(Locale.US, "%.1f W", battWatt) else "--",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = curColor
                                )
                                val bCurText = if (battDetails.currentMa > 0) "+${battDetails.currentMa} mA" else "${battDetails.currentMa} mA"
                                Text(
                                    text = String.format(Locale.US, "%.1f V • %s", battDetails.voltageMv / 1000f, bCurText),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // Efficiency Bar
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isPlugged) stringResource(R.string.battery_charging_efficiency) else stringResource(R.string.battery_power_supply_status),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = if (!isPlugged) stringResource(R.string.battery_discharging_status) else if (efficiency > 0) "$efficiency%" else if (state.charging.bypassEnabled || isOvernightLatched) stringResource(R.string.battery_direct_power_100) else "--",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isPlugged) AccentGreen else AccentCyan
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { if (!isPlugged) 0f else if (efficiency > 0) (efficiency / 100f).coerceIn(0f, 1f) else if (state.charging.bypassEnabled || isOvernightLatched) 1f else 0.5f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (isPlugged) AccentGreen else AccentCyan,
                            trackColor = BgElevated
                        )
                    }
                }
            }
        }

        // Real-time Bezier Sparkline Waveform (Live 1s stream)
        ChargingSparklineWaveform(
            samples = battDetails?.currentHistorySamples ?: emptyList(),
            currentMa = battDetails?.currentMa ?: 0,
            accentColor = if (battDetails?.isCharging == true) AccentGreen else AccentCyan,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }

    // Hyper Confirmation Dialog
    if (showHyperConfirmDialog) {
        val maxWattStr = if (battDetails?.adapterWatt != null && battDetails.adapterWatt > 10f) {
            String.format(Locale.US, "%.0fW", battDetails.adapterWatt)
        } else {
            "Daya Maksimal Hardware"
        }
        AlertDialog(
            onDismissRequest = { showHyperConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.battery_hyper_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            },
            text = {
                Text(
                    text = stringResource(R.string.battery_hyper_dialog_desc, maxWattStr),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showHyperConfirmDialog = false
                        viewModel.setChargingMode("hyper")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.Black)
                ) {
                    Text(stringResource(R.string.action_apply), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHyperConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = TextSecondary)
                }
            },
            containerColor = BgCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // Custom Current Limit Dialog
    if (uiState.isCustomCurrentDialogOpen) {
        CustomCurrentLimitDialog(
            currentLimitMa = state.charging.limitCurrentMa,
            onConfirm = { ma -> viewModel.setCustomCurrentLimit(ma) },
            onDismiss = { viewModel.closeCustomCurrentDialog() }
        )
    }
}

private data class ChargingModeItem(
    val id: String,
    val title: String,
    val sub: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accent: Color
)

@Composable
private fun ChargingModeCard(
    item: ChargingModeItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isSelected) item.accent.copy(alpha = 0.16f) else BgElevated.copy(alpha = 0.5f)
    val borderStroke = if (isSelected) BorderStroke(1.2.dp, item.accent) else BorderStroke(0.8.dp, BorderSubtle)
    val titleColor = if (isSelected) item.accent else TextPrimary

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = cardBg,
        border = borderStroke,
        modifier = modifier
            .heightIn(min = 54.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) item.accent.copy(alpha = 0.22f) else BgElevated,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (isSelected) item.accent else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.sub,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (isSelected) titleColor.copy(alpha = 0.85f) else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
