package com.noir.lynx.feature.battery.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtremeChargingScriptTest {
    @Test
    fun maximumModeRequestsOemNegotiationWithoutBypassingProtection() {
        val script = ExtremeChargingScript.buildUniversalExtremeChargingScript(
            targetMa = 25000,
            highTargetPercent = 100,
            lockoutBypass = true,
            isUnconstrainedMaxHw = false
        )

        assertTrue(script.contains("/sys/devices/platform/charger/pe40"))
        assertTrue(script.contains("/sys/devices/platform/charger/sw_jeita"))
        assertTrue(script.contains("echo 0 > \"${'$'}node\""))
        assertFalse(script.contains("constant_charge_current_max"))
        assertFalse(script.contains("sc_ibat_limit"))
        assertFalse(script.contains("pdc_max_watt"))
        assertFalse(script.contains("Battery_Temperature"))
        assertFalse(script.contains("trip_point_"))
        assertFalse(script.contains("kill -STOP"))
        assertFalse(script.contains("thermalservice override"))
    }

    @Test
    fun hyperModeIncludesHardwareUncap() {
        val script = ExtremeChargingScript.buildUniversalExtremeChargingScript(
            targetMa = 25000,
            highTargetPercent = 100,
            lockoutBypass = true,
            isUnconstrainedMaxHw = true
        )

        assertTrue(script.contains("/sys/devices/platform/charger/pe40"))
        assertTrue(script.contains("sc_ibat_limit"))
        assertTrue(script.contains("hyper-12000"))
    }
}
