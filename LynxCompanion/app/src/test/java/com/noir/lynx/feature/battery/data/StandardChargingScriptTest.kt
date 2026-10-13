package com.noir.lynx.feature.battery.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardChargingScriptTest {
    @Test
    fun standardModeDisablesFastNegotiationAndRetainsChargeSafety() {
        val script = StandardChargingScript.buildOemStandardChargingScript()

        assertTrue(script.contains("/sys/devices/platform/charger/pe20"))
        assertTrue(script.contains("/sys/devices/platform/charger/pe40"))
        assertTrue(script.contains("/sys/devices/platform/charger/enable_sc"))
        assertTrue(script.contains("/sys/devices/platform/charger/sw_jeita"))
        assertTrue(script.contains("/sys/devices/platform/charger/BatteryNotify"))
        assertFalse(script.contains("thermal_zone"))
        assertFalse(script.contains("trip_point"))
        assertFalse(script.contains("current_max"))
        assertFalse(script.contains("sc_ibat_limit"))
        assertFalse(script.contains("Battery_Temperature"))
    }
}
