package com.noir.lynx.feature.battery.ui
import com.noir.lynx.ui.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

    LynxCard(
        title = stringResource(R.string.battery_protection_bypass_title),
        icon = Icons.Default.Shield,
        accentColor = AccentGreen,
        modifier = modifier
    ) {
        // Motherboard Bypass Switch
        LynxSwitch(
            label = stringResource(R.string.battery_bypass_switch),
            subLabel = stringResource(R.string.battery_bypass_desc),
            checked = state.charging.bypassEnabled,
            onCheckedChange = { viewModel.setBypassCharging(it) }
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

        LynxSlider(
            label = stringResource(R.string.battery_smart_charge_limit),
            value = maxBatteryValue,
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
            displayValue = if (maxBatteryValue >= 99.5f) smartLimitFull else context.getString(R.string.battery_smart_limit_bypass, round(maxBatteryValue).toInt()),
            formatDisplay = { v ->
                val p = round(v).toInt()
                if (p >= 100) smartLimitFull else context.getString(R.string.battery_smart_limit_bypass, p)
            },
            accentColor = AccentGreen
        )

        Text(
            text = stringResource(R.string.battery_smart_charge_limit_desc),
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = TextSecondary,
            lineHeight = 15.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Overnight Gentle Charging Switch (Slow 1.5A)
        LynxSwitch(
            label = stringResource(R.string.battery_night_sleep_guard),
            subLabel = stringResource(R.string.battery_night_sleep_guard_desc),
            checked = state.charging.nightSleepGuardEnabled,
            onCheckedChange = { viewModel.setNightSleepGuard(it) }
        )


        // Silent Emergency Guard Status Banner
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
}
