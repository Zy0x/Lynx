package com.noir.lynx.feature.hud

import com.noir.lynx.feature.hud.data.HudCapability
import com.noir.lynx.feature.hud.data.HudNetworkSample
import com.noir.lynx.feature.hud.data.computeTouchSampleMetrics
import com.noir.lynx.feature.hud.data.parseChargingStatusJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun computeTouchSampleMetricsCalculatesHzAndHandlesEmptyDeltas() {
        val empty = computeTouchSampleMetrics(emptyList(), activeFingers = 0, previousMaxFingers = 0)
        assertNull(empty.rateHz)
        assertNull(empty.intervalMs)
        assertEquals(0, empty.activeFingers)
        assertEquals(0, empty.maxFingers)

        // ~120 Hz gaming touch sampling (~8.33ms delta)
        val touch120 = computeTouchSampleMetrics(
            listOf(8.3, 8.4, 8.3, 8.2),
            activeFingers = 2,
            previousMaxFingers = 1
        )
        assertEquals(120, touch120.rateHz)
        assertEquals(2, touch120.activeFingers)
        assertEquals(2, touch120.maxFingers)
        assertTrue(touch120.intervalMs != null && touch120.intervalMs!! in 8.2..8.4)

        // ~240 Hz gaming touch sampling (~4.16ms delta)
        val touch240 = computeTouchSampleMetrics(
            listOf(4.1, 4.2, 4.1, 4.2),
            activeFingers = 4,
            previousMaxFingers = 2
        )
        assertEquals(241, touch240.rateHz)
        assertEquals(4, touch240.maxFingers)
    }
}
