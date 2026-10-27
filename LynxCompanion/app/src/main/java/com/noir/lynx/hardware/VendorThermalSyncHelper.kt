package com.noir.lynx.hardware

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * VendorThermalSyncHelper — Dynamic INI Parsing & Live Bind Mount Helper.
 * Automatically finds and scales vendor thermal trip points, set points, and thresholds
 * to match the user's custom temperature limit without cluttering the UI with endless buttons.
 */
object VendorThermalSyncHelper {

    suspend fun syncCustomTempLimitToVendorConfig(tempC: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val state = VendorThermalConfigManager.detectState()
            if (!state.isSupported) {
                return@withContext Result.failure(IllegalStateException(state.message))
            }
            val targetMilli = tempC * 1000
            val doc = state.activeDoc.deepCopy()

            for (sec in doc.sections) {
                // Adjust thresholds (e.g. thresholds in Qualcomm thermal-engine.conf)
                val currThresh = sec.getProperty("thresholds")
                if (currThresh != null) {
                    val tokens = currThresh.split("\\s+".toRegex()).mapNotNull { it.toIntOrNull() }
                    if (tokens.isNotEmpty()) {
                        val base = tokens.first()
                        val diff = targetMilli - base
                        val updated = tokens.map { (it + diff).coerceIn(35000, 85000) }
                        sec.setProperty("thresholds", updated.joinToString(" "))
                        val clr = sec.getProperty("thresholds_clr")
                        if (clr != null) {
                            sec.setProperty(
                                "thresholds_clr",
                                updated.map { (it - 2000).coerceAtLeast(30000) }.joinToString(" ")
                            )
                        }
                    }
                }
                // Adjust set_point
                val setPoint = sec.getProperty("set_point")?.toIntOrNull()
                if (setPoint != null) {
                    sec.setProperty("set_point", targetMilli.coerceIn(40000, 85000).toString())
                    sec.setProperty("set_point_clr", (targetMilli - 2000).coerceAtLeast(38000).toString())
                }
                // Adjust trip (e.g. MediaTek .tp/thermal.conf)
                val trip = sec.getProperty("trip")?.toIntOrNull()
                if (trip != null) {
                    sec.setProperty("trip", targetMilli.coerceIn(40000, 85000).toString())
                }
            }

            VendorThermalConfigManager.applyLiveBindMount(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setAntiDimming(enabled: Boolean): Result<String> = withContext(Dispatchers.IO) {
        try {
            val state = VendorThermalConfigManager.detectState()
            if (!state.isSupported) {
                return@withContext Result.failure(IllegalStateException(state.message))
            }
            val doc = state.activeDoc.deepCopy()
            val lcd = doc.getOrCreateSection("LCD_MANAGEMENT")
            if (enabled) {
                lcd.setProperty("thresholds", "54000 58000")
                lcd.setProperty("thresholds_clr", "52000 56000")
            } else {
                lcd.setProperty("thresholds", "48000 52000")
                lcd.setProperty("thresholds_clr", "46000 50000")
            }
            VendorThermalConfigManager.applyLiveBindMount(doc)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
