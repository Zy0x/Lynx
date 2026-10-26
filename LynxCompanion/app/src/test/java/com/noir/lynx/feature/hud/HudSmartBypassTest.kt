package com.noir.lynx.feature.hud

import com.noir.lynx.feature.hud.data.HudBypassStatus
import com.noir.lynx.feature.hud.data.HudCapability
import org.junit.Assert.*
import org.junit.Test

class HudSmartBypassTest {

    @Test
    fun defaultBypassStatusInitializesSensibly() {
        val status = HudBypassStatus()
        assertEquals(HudCapability.CHECKING, status.capability)
        assertFalse(status.enabled)
        assertFalse(status.active)
        assertEquals(40, status.thresholdPct)
        assertFalse(status.thermalCutActive)
    }

    @Test
    fun bypassStatusCopiesCustomThresholdAndThermalFlag() {
        val status = HudBypassStatus(
            capability = HudCapability.READY,
            enabled = true,
            active = true,
            chipSummary = "Transsion Dual-Pump Bypass",
            thresholdPct = 60,
            thermalCutActive = true
        )

        assertEquals(60, status.thresholdPct)
        assertTrue(status.thermalCutActive)
        assertEquals("Transsion Dual-Pump Bypass", status.chipSummary)
    }

    @Test
    fun thresholdClampingLogic() {
        val clampLow = 10.coerceIn(20, 90)
        assertEquals(20, clampLow)

        val clampHigh = 95.coerceIn(20, 90)
        assertEquals(90, clampHigh)

        val clampMid = 55.coerceIn(20, 90)
        assertEquals(55, clampMid)
    }
}
