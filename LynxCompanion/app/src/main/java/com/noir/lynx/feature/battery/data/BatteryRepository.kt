package com.noir.lynx.feature.battery.data

import com.noir.lynx.data.BatteryDetails
import com.noir.lynx.data.BatteryHealthStats
import com.noir.lynx.data.BatteryInfoStats
import com.noir.lynx.data.LynxRepository

/**
 * BatteryRepository: Facade for reading battery details, cycle statistics,
 * health status, and calibration.
 */
class BatteryRepository(
    private val repository: LynxRepository
) {
    suspend fun readBatteryDetails(includeDeepAdcAndTz: Boolean = false): BatteryDetails? =
        repository.readBatteryDetails(includeDeepAdcAndTz)

    suspend fun readBatteryInfoStats(currentDetails: BatteryDetails?): BatteryInfoStats =
        repository.readBatteryInfoStats(currentDetails)

    suspend fun calibrateBatteryStats(): Boolean =
        repository.calibrateBatteryStats()

    suspend fun readBatteryHealth(): BatteryHealthStats =
        repository.readBatteryHealth()
}
