package com.noir.lynx.feature.battery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.ui.LynxViewModel
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

    LynxCard(
        title = stringResource(R.string.battery_super_fast_charging),
        icon = Icons.Default.Bolt,
        accentColor = AccentCyan,
        modifier = modifier
    ) {
        // Master Switch: Super Fast Charging
        LynxSwitch(
            label = stringResource(R.string.battery_fast_charging),
            subLabel = stringResource(R.string.battery_fast_charging_desc),
            checked = state.charging.extremeChargingEnabled,
            onCheckedChange = { viewModel.setMasterFastCharging(it) }
        )

        // Active Mode Status Pill
        val isPlugged = battDetails?.isPlugged == true
        val isCharging = battDetails?.isCharging == true
        val curLevel = battDetails?.level ?: 0
        val smartLimit = state.charging.maxBatteryPercent.coerceIn(50, 100)
        val isOvernightLatched = isPlugged && (battDetails?.isOvernightBypassLatched == true || curLevel >= 100)
        val isLimitLatched = isPlugged && smartLimit < 100 && (battDetails?.isSmartLimitLatched == true || curLevel >= smartLimit || (battDetails?.isBypassActive == true && curLevel >= (smartLimit - 2)))
        val isManualBypass = isPlugged && state.charging.bypassEnabled
        val isBypassActive = isLimitLatched || isOvernightLatched || isManualBypass || (isPlugged && battDetails?.isBypassActive == true)
        val isNightGentle = isPlugged && (battDetails?.isNightGentleActive == true || (state.charging.nightSleepGuardEnabled && isCharging && Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { it >= 23 || it < 6 })) && !isBypassActive
        val isSmartTapering = isPlugged && isCharging && battDetails?.isSmartTaperingActive == true && state.charging.smartTaperingEnabled && !isBypassActive
        val isExtreme = state.charging.extremeChargingEnabled && isPlugged && !isBypassActive

        val modePillLabel = when {
            !isPlugged -> stringResource(R.string.battery_status_disconnected)
            isOvernightLatched -> stringResource(R.string.battery_status_full_autocut)
            isLimitLatched -> stringResource(R.string.battery_status_smart_limit_reached, smartLimit, curLevel)
            isManualBypass -> stringResource(R.string.battery_status_manual_bypass)
            isNightGentle -> stringResource(R.string.battery_status_night_gentle)
            isSmartTapering -> stringResource(R.string.battery_status_smart_tapering)
            isExtreme -> if (state.charging.isUnconstrainedMaxHw) stringResource(R.string.battery_status_fast_charging_max) else stringResource(R.string.battery_status_fast_charging_ma, state.charging.limitCurrentMa)
            else -> stringResource(R.string.battery_status_standard_ma, state.charging.limitCurrentMa)
        }
        val modePillColor = when {
            !isPlugged -> TextSecondary
            isOvernightLatched || isLimitLatched || isManualBypass -> AccentCyan
            isNightGentle -> AccentPurple
            isSmartTapering -> AccentGreen
            isExtreme -> AccentCyan
            else -> AccentOrange
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
                        isNightGentle -> Icons.Default.Bedtime
                        isExtreme -> Icons.Default.Bolt
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
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // Quick-Pills for Current Limit (4 Clean Mobile-First Options)
        Text(
            text = stringResource(R.string.battery_current_limit_options),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )
        Spacer(Modifier.height(6.dp))

        val quickPresets = listOf(
            Triple("2.0A", "Standar", 2000 to false),
            Triple("4.5A", "Cepat", 4500 to false),
            Triple("6.0A", "Maksimal", 6000 to true)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickPresets.forEach { (ampLabel, descLabel, pair) ->
                val (ma, isMaxHw) = pair
                val isSelected = if (isMaxHw) (state.charging.isUnconstrainedMaxHw || state.charging.limitCurrentMa == 6000) else (!state.charging.isUnconstrainedMaxHw && state.charging.limitCurrentMa == ma)
                val pillColor = if (isSelected) AccentCyan else BgElevated
                val textColor = if (isSelected) AccentCyan else TextPrimary

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = pillColor.copy(alpha = if (isSelected) 0.18f else 0.5f),
                    border = BorderStroke(1.dp, if (isSelected) AccentCyan else BorderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clickable { viewModel.setQuickCurrentPreset(ma, isMaxHw) }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = ampLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = textColor
                        )
                        Text(
                            text = descLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = if (isSelected) textColor.copy(alpha = 0.85f) else TextSecondary
                        )
                    }
                }
            }

            // Custom Pill
            val isCustom = !state.charging.isUnconstrainedMaxHw && state.charging.limitCurrentMa !in listOf(2000, 4500, 6000)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isCustom) AccentPurple.copy(alpha = 0.18f) else BgElevated.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, if (isCustom) AccentPurple else BorderSubtle),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clickable { viewModel.openCustomCurrentDialog() }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = if (isCustom) AccentPurple else TextSecondary, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = if (isCustom) "${state.charging.limitCurrentMa}" else "Kustom",
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 11.5.sp,
                            fontWeight = if (isCustom) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isCustom) AccentPurple else TextPrimary
                        )
                    }
                    Text(
                        text = if (isCustom) "mA" else "Manual",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = if (isCustom) AccentPurple.copy(alpha = 0.85f) else TextSecondary
                    )
                }
            }
        }
    }
}
