package com.noir.lynx.freeform

import org.junit.Assert.*
import org.junit.Test

class FloatingChromePolicyTest {
    @Test fun compactChromeHalvesDefaultReservedHeight() {
        for (font in listOf(1f, 1.3f, 2f)) {
            assertEquals(48, FloatingChromePolicy.controlsHeight(font, 12f, false))
            assertEquals(96, FloatingChromePolicy.controlsHeight(font, 12f, true))
        }
    }
    @Test fun largerCustomizedFontGrowsHeaderInsteadOfClipping() {
        assertTrue(FloatingChromePolicy.headerHeight(2f, 18f) > 48)
        assertTrue(FloatingChromePolicy.headerHeight(3f, 12f) > 48)
    }
    @Test fun portableConfigurationRejectsExcessiveAllocations() {
        assertTrue(runCatching { RenderBudget(10_000_000, 4096) }.isFailure)
        assertTrue(runCatching { FreeformConfig(maxWindows = 20) }.isFailure)
        assertTrue(runCatching { FreeformAppearance(titleSizeSp = 8f) }.isFailure)
        val budget = RenderBudget(524_288, 960)
        val render = FloatingRenderPolicy.fit(1440, 2560, 480, budget)
        assertTrue(render.width * render.height <= budget.maxPixels)
        assertTrue(maxOf(render.width, render.height) <= budget.maxEdge)
    }
}
