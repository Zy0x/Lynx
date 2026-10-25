package com.noir.lynx.feature.hud

import com.noir.lynx.feature.hud.data.HudCapability
import com.noir.lynx.feature.hud.data.HudNetworkSample
import com.noir.lynx.feature.hud.data.parseChargingStatusJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudNetworkSampleTest {

    @Test
    fun scoreHandlesHealthyDegradedAndFailedSamples() {
        val healthy = HudNetworkSample(avgMs = 18.0, jitterMs = 2.0, lossPct = 0)
        assertTrue("Healthy ping should score >= 85", healthy.score >= 85)

        val degraded = HudNetworkSample(avgMs = 95.0, jitterMs = 20.0, lossPct = 10)
        assertTrue("Degraded sample should score between 20 and 70", degraded.score in 20..70)

        val offline = HudNetworkSample(avgMs = null, jitterMs = null, lossPct = 100)
        assertEquals(0, offline.score)
    }

    @Test
    fun chargingStatusJsonParserDeterminesBypassCapabilityAccurately() {
        val noRoot = parseChargingStatusJson("""{"bypass_enabled":false}""", rootAvailable = false)
        assertEquals(HudCapability.NEEDS_ROOT, noRoot.capability)

        val supportedActive = parseChargingStatusJson(
            """
            {
              "bypass_enabled": true,
              "bypass_active": true,
              "dynamic_scanned_nodes_count": 3,
              "detected_hardware_chips": "Qualcomm PMI632 Charger"
            }
            """.trimIndent(),
            rootAvailable = true
        )
        assertEquals(HudCapability.READY, supportedActive.capability)
        assertTrue(supportedActive.enabled)
        assertTrue(supportedActive.active)
        assertEquals("Qualcomm PMI632 Charger", supportedActive.chipSummary)

        val unsupportedGeneric = parseChargingStatusJson(
            """
            {
              "bypass_enabled": false,
              "bypass_active": false,
              "dynamic_scanned_nodes_count": 0,
              "detected_hardware_chips": "Generic Power Supply"
            }
            """.trimIndent(),
            rootAvailable = true
        )
        assertEquals(HudCapability.UNSUPPORTED, unsupportedGeneric.capability)
        assertFalse(unsupportedGeneric.enabled)
    }
}
