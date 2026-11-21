package com.noir.lynx.feature.battery.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
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

internal fun resolveActiveChargingModeKey(uiState: LynxUiState): String {
    val charging = uiState.state.charging
    return when {
        charging.chargingMode.equals("hyper", ignoreCase = true) || charging.extremeChargingEnabled -> "hyper"
        charging.chargingMode.equals("cool", ignoreCase = true) -> "cool"
        charging.chargingMode.equals("custom", ignoreCase = true) -> "custom"
        charging.chargingMode.equals("standard", ignoreCase = true) -> "standard"
        charging.chargingMode.isNotBlank() -> charging.chargingMode.lowercase()
        charging.limitCurrentMa in 500..2500 -> "cool"
        charging.limitCurrentMa > 2500 && charging.limitCurrentMa != 6000 -> "custom"
        else -> "standard"
    }
}

/**
 * ChargingLiveTelemetryHeroCard — Compact Zen Live Electrical & Waveform Hero for Tab 0.
 * Eliminates repetitive "disconnected" text clutter and separates live telemetry from mode controls.
 */
@Composable
fun ChargingLiveTelemetryHeroCard(
    uiState: LynxUiState,
    modifier: Modifier = Modifier
) {
    val state = uiState.state
    val battDetails = uiState.batteryDetails
    val activeMode = remember(
        state.charging.chargingMode,
        state.charging.extremeChargingEnabled,
        state.charging.limitCurrentMa
    ) { resolveActiveChargingModeKey(uiState) }

    val isPlugged = battDetails?.isPlugged == true
    val isCharging = battDetails?.isCharging == true
    val curLevel = (battDetails?.level ?: uiState.telemetry?.battLevel ?: 0).coerceIn(0, 100)
    val smartLimit = state.charging.maxBatteryPercent.coerceIn(50, 100)
    val isAutoCut = state.charging.autoCutEnabled
    val isOvernightLatched = isPlugged && isAutoCut && (battDetails?.isOvernightBypassLatched == true || curLevel >= 100)
    val isLimitLatched = isPlugged && isAutoCut && smartLimit < 100 &&
        (battDetails?.isSmartLimitLatched == true || (battDetails?.isBypassActive == true && curLevel >= (smartLimit - 2)))
    val isManualBypass = isPlugged && state.charging.bypassEnabled && battDetails?.isBypassActive == true
    val isNightGentle = isPlugged && (battDetails?.isNightGentleActive == true ||
        (state.charging.nightSleepGuardEnabled && isCharging && Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { it >= 23 || it < 6 })) && !isManualBypass

    val statusLabel = when {
        !isPlugged -> when (activeMode) {
            "hyper" -> "Discharging • Mode Hyper Siaga"
            "cool" -> "Discharging • Mode Sejuk Siaga"
            "custom" -> "Discharging • Kustom ${state.charging.limitCurrentMa} mA"
            else -> "Discharging • Mode Standar Siaga"
        }
        isOvernightLatched -> "Auto-Cut 100% Aktif • Arus Dihentikan"
        isLimitLatched -> "Batas Cerdas $smartLimit% Tercapai • Bypass"
        isManualBypass -> "Direct Bypass Aktif • 0 mA ke Sel"
        isNightGentle -> "Night Guard Aktif • Regulasi ~1.5A"
        activeMode == "hyper" -> "Mode Hyper Aktif • Arus Maksimal"
        activeMode == "cool" -> "Mode Sejuk Aktif • Regulasi ~1.0A"
        activeMode == "custom" -> "Mode Kustom Aktif • ${state.charging.limitCurrentMa} mA"
        else -> "Mode Standar Aktif • Regulasi OEM"
    }

    val heroAccent = when {
        !isPlugged -> when (activeMode) {
            "hyper" -> AccentOrange
            "cool" -> AccentCyan
            "custom" -> AccentPurple
            else -> AccentGreen
        }
        isOvernightLatched || isLimitLatched || isManualBypass -> AccentPurple
        isNightGentle -> AccentBlue
        activeMode == "hyper" -> AccentOrange
        activeMode == "cool" -> AccentCyan
        activeMode == "custom" -> AccentPurple
        else -> AccentGreen
    }

    val animatedAccent by animateColorAsState(
        targetValue = heroAccent,
        animationSpec = tween(durationMillis = 200),
        label = "charging_hero_accent"
    )

    val tempC = battDetails?.realPhysicalTempC?.takeIf { it > 0f }
        ?: battDetails?.tempC?.takeIf { it > 0f }
        ?: uiState.telemetry?.temp?.toFloatOrNull()
        ?: 0f
    val tempColor = when {
        tempC >= 43f -> AccentRed
        tempC >= 39f -> AccentOrange
        else -> AccentCyan
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF10141F),
        border = BorderStroke(
            0.8.dp,
            Brush.verticalGradient(
                listOf(
                    animatedAccent.copy(alpha = 0.32f),
                    Color(0x15FFFFFF)
                )
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Top Status Bar: Live status dot + concise mode status + temp badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = CircleShape,
                            color = animatedAccent.copy(alpha = 0.20f),
                            modifier = Modifier.size(12.dp)
                        ) {}
                        Surface(
                            shape = CircleShape,
                            color = animatedAccent,
                            modifier = Modifier.size(6.dp)
                        ) {}
                    }
                    Text(
                        text = statusLabel,
                        color = TextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (tempC > 0f) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = tempColor.copy(alpha = 0.12f),
                        border = BorderStroke(0.6.dp, tempColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f°C", tempC),
                            color = tempColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // 2. Compact 2-Column Live Electrical Tiles
            if (battDetails != null) {
                val inputWatt = if (battDetails.adapterWatt > 0.05f) battDetails.adapterWatt else battDetails.chargerWatt
                val netBattWatt = (battDetails.voltageMv.toFloat() * battDetails.currentMa.coerceAtLeast(0).toFloat()) / 1_000_000f
                val netWattDisplay = if (netBattWatt > 0.05f) netBattWatt else battDetails.chargerWatt
                val efficiency = battDetails.chargingEfficiencyPercent.coerceIn(0, 100)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left Tile: Power Source / Battery Level when unplugged
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (isPlugged) "Pasokan Adaptor" else "Level & Tegangan Sel",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (isPlugged) {
                                    if (inputWatt > 0.05f) String.format(Locale.US, "%.1f W", inputWatt) else "Terhubung"
                                } else {
                                    "$curLevel%"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isPlugged) AccentPurple else TextPrimary
                            )
                            val subLeft = if (isPlugged) {
                                val voltVal = when {
                                    battDetails.adapterVoltageMv > 1000 -> battDetails.adapterVoltageMv / 1000f
                                    battDetails.chargerVoltageMv > 1000 -> battDetails.chargerVoltageMv / 1000f
                                    else -> battDetails.voltageMv / 1000f
                                }
                                val curVal = if (battDetails.adapterCurrentMa > 0) battDetails.adapterCurrentMa else battDetails.currentMa.coerceAtLeast(0)
                                String.format(Locale.US, "%.1f V • %d mA", voltVal, curVal)
                            } else {
                                String.format(Locale.US, "%.2f V • Baterai", battDetails.voltageMv / 1000f)
                            }
                            Text(
                                text = subLeft,
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Right Tile: Battery Net Power & Current
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BgElevated,
                        border = BorderStroke(0.6.dp, BorderSubtle),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (isPlugged) "Daya Masuk Baterai" else "Konsumsi Berjalan",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(2.dp))
                            val curColor = when {
                                !isPlugged -> AccentCyan
                                battDetails.currentMa > 100 -> AccentGreen
                                isManualBypass || isOvernightLatched || isLimitLatched -> AccentPurple
                                else -> TextPrimary
                            }
                            val battWatt = if (!isPlugged) {
                                (battDetails.voltageMv.toFloat() * abs(battDetails.currentMa).toFloat()) / 1_000_000f
                            } else netWattDisplay
                            Text(
                                text = if (isManualBypass || isOvernightLatched || isLimitLatched) "0.0 W"
                                else if (battWatt > 0.05f) String.format(Locale.US, "%.1f W", battWatt) else "--",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = curColor
                            )
                            val bCurText = if (battDetails.currentMa > 0) "+${battDetails.currentMa} mA" else "${battDetails.currentMa} mA"
                            Text(
                                text = String.format(Locale.US, "%.2f V • %s", battDetails.voltageMv / 1000f, bCurText),
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Only display Efficiency / USB Port info when actually plugged in
                if (isPlugged) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (battDetails.isLaptopPort) {
                                "Port USB/PD (${battDetails.portType}) • Regulasi Aman"
                            } else {
                                stringResource(R.string.battery_charging_efficiency)
                            },
                            fontSize = 10.5.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = if (isManualBypass || isOvernightLatched || isLimitLatched) "Direct Bypass (100%)"
                            else if (efficiency > 0) "$efficiency%" else "Aktif",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen
                        )
                    }
                }
            }

            // 3. Real-time Bezier Sparkline Waveform
            ChargingSparklineWaveform(
                samples = battDetails?.currentHistorySamples ?: emptyList(),
                currentMa = battDetails?.currentMa ?: 0,
                accentColor = if (isCharging) AccentGreen else AccentCyan
            )
        }
    }
}

/**
 * FastChargingControlCard — Clean 2x2 Bento Matrix for Charging Mode Selection.
 * Zero redundant paragraph blocks or duplicate status pills.
 */
@Composable
fun FastChargingControlCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state
    val battDetails = uiState.batteryDetails
    var showHyperConfirmDialog by remember { mutableStateOf(false) }

    val activeMode = remember(
        state.charging.chargingMode,
        state.charging.extremeChargingEnabled,
        state.charging.limitCurrentMa
    ) { resolveActiveChargingModeKey(uiState) }

    val modes = listOf(
        ChargingModeItem(
            id = "cool",
            title = stringResource(R.string.battery_mode_cool),
            sub = "Sejuk • Limit ~1.0A",
            icon = Icons.Default.AcUnit,
            accent = AccentCyan
        ),
        ChargingModeItem(
            id = "standard",
            title = stringResource(R.string.battery_mode_standard),
            sub = "Seimbang • Default OEM",
            icon = Icons.Default.Balance,
            accent = AccentGreen
        ),
        ChargingModeItem(
            id = "hyper",
            title = stringResource(R.string.battery_mode_hyper),
            sub = "Daya Penuh • Max Speed",
            icon = Icons.Default.Bolt,
            accent = AccentOrange
        ),
        ChargingModeItem(
            id = "custom",
            title = stringResource(R.string.battery_mode_custom),
            sub = if (activeMode == "custom") "Dikunci • ${state.charging.limitCurrentMa} mA" else "Atur Manual (mA)",
            icon = Icons.Default.Tune,
            accent = AccentPurple
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
            containerColor = Color(0xFF10141F),
            shape = RoundedCornerShape(18.dp),
            icon = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentOrange.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, AccentOrange.copy(alpha = 0.35f)),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(22.dp))
                    }
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.battery_hyper_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.battery_hyper_dialog_desc, maxWattStr),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showHyperConfirmDialog = false
                        viewModel.setChargingMode("hyper")
                    },
                    shape = RoundedCornerShape(10.dp),
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
    val cardBg by animateColorAsState(
        targetValue = if (isSelected) item.accent.copy(alpha = 0.14f) else BgCard,
        animationSpec = tween(durationMillis = 180),
        label = "charging_card_bg_${item.id}"
    )
    val borderStroke by animateColorAsState(
        targetValue = if (isSelected) item.accent.copy(alpha = 0.70f) else BorderSubtle,
        animationSpec = tween(durationMillis = 180),
        label = "charging_card_border_${item.id}"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(if (isSelected) 1.dp else 0.8.dp, borderStroke),
        modifier = modifier
            .heightIn(min = 62.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) item.accent.copy(alpha = 0.20f) else BgElevated,
                border = BorderStroke(0.6.dp, if (isSelected) item.accent.copy(alpha = 0.35f) else BorderSubtle),
                modifier = Modifier.size(36.dp)
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
            Spacer(Modifier.width(10.dp))
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) item.accent else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isSelected) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = item.accent.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "AKTIF",
                                color = item.accent,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.sub,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (isSelected) item.accent.copy(alpha = 0.85f) else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
