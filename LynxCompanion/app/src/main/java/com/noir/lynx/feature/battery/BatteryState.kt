package com.noir.lynx.feature.battery

import com.noir.lynx.data.BatteryDetails
import com.noir.lynx.data.BatteryHealthStats
import com.noir.lynx.data.BatteryInfoStats
import com.noir.lynx.data.ChargingConfig

data class BatteryState(
    val details: BatteryDetails? = null,
    val infoStats: BatteryInfoStats = BatteryInfoStats(),
    val healthStats: BatteryHealthStats = BatteryHealthStats(),
    val chargingConfig: ChargingConfig = ChargingConfig(),
    val selectedSubTab: Int = 0,
    val isDetailSheetOpen: Boolean = false,
    val isCustomCurrentDialogOpen: Boolean = false,
    val isCalibrating: Boolean = false
)
