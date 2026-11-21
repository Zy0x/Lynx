package com.noir.lynx.feature.battery.ui
import com.noir.lynx.ui.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.noir.lynx.R
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.controls.LynxSlider
import com.noir.lynx.designsystem.controls.LynxSwitch
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.ui.LynxViewModel
import kotlin.math.round

@Composable
fun BatteryProtectionCard(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    modifier: Modifier = Modifier
) {
    val state = uiState.state
    val context = LocalContext.current
    val isPlugged = uiState.batteryDetails?.isPlugged == true
    var showSmartLimitWarningDialog by remember { mutableStateOf(false) }
    var showBypassWarningDialog by remember { mutableStateOf(false) }
    var showBypassRequiresChargerDialog by remember { mutableStateOf(false) }

    LynxCard(
        title = stringResource(R.string.battery_protection_bypass_title),
        subtitle = "Isolasi arus baterai, auto-cut cerdas & pengisian malam",
        icon = Icons.Default.Shield,
        accentColor = AccentPurple,
        modifier = modifier,
        action = {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AccentGreen.copy(alpha = 0.12f),
                border = BorderStroke(0.6.dp, AccentGreen.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Guard ≥41°C",
                        color = AccentGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) {
        // 1. Motherboard Bypass Switch (Concise 1-line subLabel)
        LynxSwitch(
            label = stringResource(R.string.battery_bypass_switch),
            subLabel = if (!isPlugged) {
                "Hubungkan kabel charger untuk mengaktifkan bypass"
            } else {
                "Alirkan daya langsung ke mesin (0 mA ke sel baterai)"
            },
            checked = state.charging.bypassEnabled && isPlugged,
            optimistic = false,
            onCheckedChange = { checked ->
                if (checked) {
                    if (!isPlugged) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.battery_bypass_plug_required_toast),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        showBypassRequiresChargerDialog = true
                    } else {
                        showBypassWarningDialog = true
                    }
                } else {
                    viewModel.setBypassCharging(false)
                }
            }
        )

        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 4.dp))

        // 2. Batas Pengisian Cerdas Toggle Switch (Concise 1-line subLabel)
        val isSmartLimitOn = state.charging.autoCutEnabled
        LynxSwitch(
            label = stringResource(R.string.battery_smart_charge_limit),
            subLabel = if (isSmartLimitOn) {
                "Aktif • Hentikan pengisian otomatis pada ${state.charging.maxBatteryPercent}%"
            } else {
                "Hentikan pengisian otomatis saat mencapai target %"
            },
            checked = isSmartLimitOn,
            optimistic = false,
            onCheckedChange = { checked ->
                if (checked) {
                    showSmartLimitWarningDialog = true
                } else {
                    viewModel.setAutoCutEnabled(false)
                }
            }
        )

        var maxBatteryValue by remember {
            mutableFloatStateOf(state.charging.maxBatteryPercent.toFloat())
        }
        var isInteractingSafety by remember { mutableStateOf(false) }
        var lastSafetyInteraction by remember { mutableLongStateOf(0L) }

        LaunchedEffect(state.charging.maxBatteryPercent) {
            if (!isInteractingSafety && (System.currentTimeMillis() - lastSafetyInteraction > 3500L)) {
                maxBatteryValue = state.charging.maxBatteryPercent.toFloat()
            }
        }

        // Progressive Disclosure: Only show slider and preset chips when Auto-Cut is ON
        AnimatedVisibility(
            visible = isSmartLimitOn,
            enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
            exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val smartLimitFull = stringResource(R.string.battery_smart_limit_full)
                LynxSlider(
                    label = "Target Batas Pengisian",
                    value = maxBatteryValue,
                    enabled = true,
                    onValueChange = {
                        isInteractingSafety = true
                        lastSafetyInteraction = System.currentTimeMillis()
                        maxBatteryValue = round(it)
                    },
                    onValueChangeFinished = {
                        isInteractingSafety = false
                        lastSafetyInteraction = System.currentTimeMillis()
                        viewModel.setMaxBatteryPercent(round(maxBatteryValue).toInt())
                    },
                    valueRange = 50f..100f,
                    steps = 49,
                    displayValue = if (maxBatteryValue >= 99.5f) {
                        smartLimitFull
                    } else {
                        "${round(maxBatteryValue).toInt()}% (Auto-Cut)"
                    },
                    formatDisplay = { v ->
                        val p = round(v).toInt()
                        if (p >= 100) smartLimitFull else "$p% (Auto-Cut)"
                    },
                    accentColor = AccentGreen
                )

                // Quick Preset Chips (1-Tap Convenience)
                val presets = listOf(
                    75 to "75%",
                    80 to "80% Optimal",
                    85 to "85%",
                    90 to "90%",
                    95 to "95%"
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(presets) { (pct, label) ->
                        val isSelected = round(maxBatteryValue).toInt() == pct
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) AccentGreen.copy(alpha = 0.18f) else BgElevated,
                            border = BorderStroke(
                                0.6.dp,
                                if (isSelected) AccentGreen.copy(alpha = 0.65f) else BorderSubtle
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    maxBatteryValue = pct.toFloat()
                                    viewModel.setMaxBatteryPercent(pct)
                                }
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) AccentGreen else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = BorderGlass, modifier = Modifier.padding(vertical = 4.dp))

        // 3. Overnight Gentle Charging Switch (Concise 1-line subLabel)
        LynxSwitch(
            label = stringResource(R.string.battery_night_sleep_guard),
            subLabel = "Batasi arus ~1.5A otomatis pada pukul 23:00 – 06:00",
            checked = state.charging.nightSleepGuardEnabled,
            onCheckedChange = { viewModel.setNightSleepGuard(it) }
        )
    }

    // Confirmation Dialog for Smart Charging Limit (Batas Pengisian Cerdas)
    if (showSmartLimitWarningDialog) {
        AlertDialog(
            onDismissRequest = { showSmartLimitWarningDialog = false },
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
                        Icon(Icons.Default.Shield, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(22.dp))
                    }
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.battery_smart_limit_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.battery_smart_limit_dialog_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSmartLimitWarningDialog = false
                        viewModel.setAutoCutEnabled(true)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black)
                ) {
                    Text(stringResource(R.string.action_understand_activate), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmartLimitWarningDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = TextSecondary)
                }
            },
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // Confirmation Dialog for Motherboard Bypass
    if (showBypassWarningDialog) {
        AlertDialog(
            onDismissRequest = { showBypassWarningDialog = false },
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
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(22.dp))
                    }
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.battery_bypass_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.battery_bypass_dialog_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBypassWarningDialog = false
                        viewModel.setBypassCharging(true)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.Black)
                ) {
                    Text(stringResource(R.string.action_understand_activate), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBypassWarningDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = TextSecondary)
                }
            },
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // Warning Dialog when Bypass is attempted without a Charger
    if (showBypassRequiresChargerDialog) {
        AlertDialog(
            onDismissRequest = { showBypassRequiresChargerDialog = false },
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
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(22.dp))
                    }
                }
            },
            title = {
                Text(
                    text = "Charger Belum Terhubung",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Fitur Real Hardware Bypass Charging membutuhkan pasokan daya listrik langsung dari adaptor/kabel charger.\n\nJika bypass diaktifkan saat kabel belum terhubung, baterai akan terisolasi sementara tidak ada pasokan listrik eksternal, sehingga ponsel seketika mengalami brownout (drop daya) dan langsung mati/reboot.\n\nSilakan colokkan kabel charger terlebih dahulu sebelum mengaktifkan fitur ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showBypassRequiresChargerDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.Black)
                ) {
                    Text("Mengerti", fontWeight = FontWeight.Bold)
                }
            },
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}
