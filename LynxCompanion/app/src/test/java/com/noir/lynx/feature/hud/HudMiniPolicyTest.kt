package com.noir.lynx.feature.hud

import com.noir.lynx.feature.hud.data.HudMetric
import com.noir.lynx.feature.hud.ui.HudMiniPolicy
import com.noir.lynx.feature.hud.ui.HudViewport
import org.junit.Assert.*
import org.junit.Test

class HudMiniPolicyTest {
    @Test fun anEmptyOrUnknownOptionalSelectionStillPinsFps() {
        assertEquals(listOf(HudMetric.FPS), HudMiniPolicy.metrics(emptySet()))
        assertEquals(listOf(HudMetric.FPS), HudMiniPolicy.metrics(setOf("unknown")))
    }

    @Test fun fpsStaysFirstAndOptionalMetricsRetainCatalogOrder() {
        val metrics = HudMiniPolicy.metrics(setOf("gpu_clock", "fps", "cpu_load", "frame_time"))
        assertEquals(listOf(HudMetric.FPS, HudMetric.FRAME_TIME, HudMetric.CPU_LOAD, HudMetric.GPU_CLOCK), metrics)
        assertEquals(metrics.size, metrics.distinct().size)
    }

    @Test fun aSingleNonFpsSelectionCreatesTwoSwipeablePages() {
        assertEquals(2, HudMiniPolicy.metrics(setOf("cpu_load")).size)
    }

    @Test fun openingAndRotatingKeepsPanelInsideSafeViewport() {
        for (size in listOf(280f to 640f, 415f to 920f, 800f to 1200f)) {
            val portrait = HudViewport(size.first, size.second)
            val landscape = HudViewport(size.second, size.first)
            assertTrue(portrait.panelWidth <= portrait.widthDp)
            assertTrue(landscape.panelWidth <= landscape.widthDp)
            assertTrue(portrait.panelHeight <= 480f)
            assertEquals(landscape.heightDp, landscape.panelHeight, .001f)
            assertTrue(landscape.panelWidth > portrait.panelWidth || portrait.widthDp >= 440f)
        }
    }
}
