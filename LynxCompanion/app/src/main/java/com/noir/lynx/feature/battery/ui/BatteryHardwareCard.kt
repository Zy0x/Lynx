package com.noir.lynx.feature.battery.ui
import com.noir.lynx.ui.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    LynxCard(
        title = stringResource(R.string.battery_hardware_card_title),
        icon = Icons.Default.Memory,
        accentColor = AccentBlue,
        modifier = modifier
    ) {
        // 2x2 Primary Hardware Capacities & Cycle Count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgCard,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = stringResource(R.string.battery_design_capacity),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (stats.designCapacityMah > 0) "${stats.designCapacityMah} mAh" else "-",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgCard,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = stringResource(R.string.battery_full_charge_capacity),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (stats.fullChargeCapacityMah > 0) {
                            if (stats.stateOfHealthPercent > 0) "${stats.fullChargeCapacityMah} mAh (${stats.stateOfHealthPercent}%)"
                            else "${stats.fullChargeCapacityMah} mAh"
                        } else "-",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgCard,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = stringResource(R.string.battery_remaining_charge),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (stats.remainingChargeMah > 0) "${stats.remainingChargeMah} mAh ($currentLevel%)" else "$currentLevel%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BgCard,
                border = BorderStroke(0.8.dp, BorderGlass),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = stringResource(R.string.battery_cycle_count),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (stats.cycleCount >= 0) stringResource(R.string.battery_cycles_suffix, stats.cycleCount) else stringResource(R.string.battery_not_available),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Real-time Electrical & Thermal Telemetry
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderGlass),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.battery_status_power_source), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                    Text("${stats.batteryStatusText} • ${stats.powerSourceText}", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.battery_cell_voltage_realtime), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = if (stats.voltageNowMv > 0) String.format(Locale.US, "%.3f V (%d mV)", stats.voltageNowMv / 1000f, stats.voltageNowMv) else "-",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.battery_current_realtime_avg), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = "${stats.realTimeCurrentMa} mA / ${stats.averageCurrentMa} mA",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (stats.realTimeCurrentMa > 0) AccentCyan else AccentOrange
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.battery_physical_temp), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = if (stats.bmsTempC > 0f) String.format(Locale.US, "%.1f °C", stats.bmsTempC) else "-",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen
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
            }
        }

        Spacer(Modifier.height(10.dp))

        // 1-Click Battery Stats & Fuel Gauge Calibration
        OutlinedButton(
            onClick = onCalibrate,
            enabled = !isCalibrating,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            if (isCalibrating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = AccentBlue,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.battery_calibrating), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.battery_calibrate_btn), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Button: View complete raw ADC details.
        OutlinedButton(
            onClick = onOpenDetail,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentCyan),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.battery_open_adc_matrix), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
