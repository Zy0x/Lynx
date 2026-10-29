package com.noir.lynx.feature.hud

import com.noir.lynx.feature.hud.data.*
import com.noir.lynx.feature.hud.ui.HudViewport
import org.junit.Assert.*
import org.junit.Test

class HudSnapshotTest {
    @Test fun nullableReadingsAndZeroAreDifferent() {
        val snapshot = HudSnapshot.parse("""{"version":1,"at_ms":5000,"package":"com.test.game","layer":"SurfaceView","metrics":{
            "gpu_load":{"value":0,"unit":"%","source":"GED","at_ms":5000,"status":"valid"},
            "fps":{"value":null,"unit":"FPS","source":"SF","at_ms":5000,"status":"unsupported"}},"policies":[],"history":[],"charging":null}""")
        assertEquals(0.0, snapshot[HudMetric.GPU_LOAD].value!!, 0.0)
        assertEquals("0", snapshot[HudMetric.GPU_LOAD].text())
        assertNull(snapshot[HudMetric.FPS].value)
        assertEquals("—", snapshot[HudMetric.FPS].text())
        assertNull(snapshot.charging)
    }
    @Test fun sourceCadenceDeterminesExpiryAndClearsGraph() {
        val sample = HudSnapshot(metrics = mapOf(
            "frame_time" to HudReading(16.7,"ms","SF",1000,"valid"),
            "cpu_temp" to HudReading(45.0,"°C","cpu",1000,"valid")),history = listOf(16.7f))
        val fresh = sample.fresh(2600)
        assertEquals("stale", fresh[HudMetric.FRAME_TIME].status)
        assertTrue(fresh.history.isEmpty())
        assertEquals("valid", fresh[HudMetric.CPU_TEMP].status)
        assertEquals("stale", sample.fresh(7001)[HudMetric.CPU_TEMP].status)
        assertEquals("stale", sample.fresh(999)[HudMetric.CPU_TEMP].status)
    }
    @Test fun nonValidNumbersCannotLeakIntoPresentation() {
        val reading = HudReading(120.0,"FPS","",0,"permission_denied")
        assertEquals("—",reading.text())
        val legacy = HudLegacyAdapter.project(HudSnapshot())
        assertEquals(Int.MIN_VALUE,legacy.renderFps)
        assertTrue(legacy.cpuTempC.isNaN())
        assertTrue(legacy.gpuTempC.isNaN())
    }
    @Test fun panelFitsPortraitLandscapeTabletAndNarrowWindows() {
        listOf(360f to 800f, 800f to 360f, 1080f to 800f, 280f to 640f).forEach { (w,h) ->
            val v = HudViewport(w,h)
            assertTrue(v.panelWidth <= w - 8f)
            assertTrue(v.panelWidth <= if (v.landscape) 440f else 360f)
            assertTrue(v.panelHeight <= h)
        }
    }

    @Test fun panelKeepsSafeEdgeMarginAndOrientationCaps() {
        assertEquals(352f, HudViewport(360f, 800f).panelWidth, 0.001f)
        assertEquals(360f, HudViewport(412f, 800f).panelWidth, 0.001f)
        assertEquals(440f, HudViewport(800f, 360f).panelWidth, 0.001f)
    }
}
