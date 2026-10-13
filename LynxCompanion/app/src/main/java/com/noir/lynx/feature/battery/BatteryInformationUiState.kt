package com.noir.lynx.feature.battery

import androidx.compose.runtime.Immutable
import com.noir.lynx.data.BatteryDetails
import com.noir.lynx.data.BatteryInfoStats

/** Fast-changing battery values isolated from the app-wide tuning state. */
@Immutable
data class BatteryInformationUiState(
    val details: BatteryDetails? = null,
    val stats: BatteryInfoStats = BatteryInfoStats()
)
