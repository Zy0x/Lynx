package com.noir.lynx.feature.battery.ui
import com.noir.lynx.ui.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var showSmartLimitWarningDialog by remember { mutableStateOf(false) }
    var showBypassWarningDialog by remember { mutableStateOf(false) }

    LynxCard(
        title = stringResource(R.string.battery_protection_bypass_title),
        icon = Icons.Default.Shield,
        accentColor = AccentGreen,
        modifier = modifier
    ) {
        // 1. Motherboard Bypass Switch
        LynxSwitch(
            label = stringResource(R.string.battery_bypass_switch),
            subLabel = stringResource(R.string.battery_bypass_desc),
            checked = state.charging.bypassEnabled,
            optimistic = false,
            onCheckedChange = { checked ->
                if (checked) {
                    showBypassWarningDialog = true
                } else {
                    viewModel.setBypassCharging(false)
                }
            }
        )

        Spacer(Modifier.height(4.dp))

        // 2. Batas Pengisian Cerdas Toggle Switch (Default: ALWAYS OFF)
        LynxSwitch(
            label = stringResource(R.string.battery_smart_charge_limit),
            subLabel = stringResource(R.string.battery_smart_limit_switch_desc),
            checked = state.charging.autoCutEnabled,
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

        val context = LocalContext.current
        val smartLimitFull = stringResource(R.string.battery_smart_limit_full)
        val isSmartLimitOn = state.charging.autoCutEnabled
        val disabledNote = stringResource(R.string.battery_smart_limit_disabled_note)

        // 3. Batas Pengisian Cerdas Slider (Controlled by isSmartLimitOn)
        LynxSlider(
            label = stringResource(R.string.battery_smart_charge_limit),
            value = maxBatteryValue,
            enabled = isSmartLimitOn,
            onValueChange = {
                if (isSmartLimitOn) {
                    isInteractingSafety = true
                    lastSafetyInteraction = System.currentTimeMillis()
                    maxBatteryValue = round(it)
                }
            },
            onValueChangeFinished = {
                if (isSmartLimitOn) {
                    isInteractingSafety = false
                    lastSafetyInteraction = System.currentTimeMillis()
                    viewModel.setMaxBatteryPercent(round(maxBatteryValue).toInt())
                }
            },
            valueRange = 50f..100f,
            steps = 49,
            displayValue = if (!isSmartLimitOn) {
                disabledNote
            } else if (maxBatteryValue >= 99.5f) {
                smartLimitFull
            } else {
                context.getString(R.string.battery_smart_limit_bypass, round(maxBatteryValue).toInt())
            },
            formatDisplay = { v ->
                if (!isSmartLimitOn) {
                    disabledNote
                } else {
                    val p = round(v).toInt()
                    if (p >= 100) smartLimitFull else context.getString(R.string.battery_smart_limit_bypass, p)
                }
            },
            accentColor = AccentGreen
        )

        Text(
            text = stringResource(R.string.battery_smart_charge_limit_desc),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = if (isSmartLimitOn) TextSecondary else TextSecondary.copy(alpha = 0.5f),
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // 4. Overnight Gentle Charging Switch (Slow 1.5A)
        LynxSwitch(
            label = stringResource(R.string.battery_night_sleep_guard),
            subLabel = stringResource(R.string.battery_night_sleep_guard_desc),
            checked = state.charging.nightSleepGuardEnabled,
            onCheckedChange = { viewModel.setNightSleepGuard(it) }
        )

        // 5. Silent Emergency Guard Status Banner
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = BgCard,
            border = BorderStroke(0.8.dp, BorderGlass),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.battery_thermal_protection_banner),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = TextPrimary.copy(alpha = 0.85f)
                )
            }
        }
    }

    // Confirmation Dialog for Smart Charging Limit (Batas Pengisian Cerdas)
    if (showSmartLimitWarningDialog) {
        AlertDialog(
            onDismissRequest = { showSmartLimitWarningDialog = false },
            icon = { Icon(Icons.Default.Shield, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(28.dp)) },
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
            containerColor = BgCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // Confirmation Dialog for Motherboard Bypass
    if (showBypassWarningDialog) {
        AlertDialog(
            onDismissRequest = { showBypassWarningDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(28.dp)) },
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
            containerColor = BgCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}
