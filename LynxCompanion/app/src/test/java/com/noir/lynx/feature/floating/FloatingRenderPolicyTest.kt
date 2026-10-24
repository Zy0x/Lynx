package com.noir.lynx.feature.floating

import com.noir.lynx.feature.floating.root.FloatingDisplayPolicy
import org.junit.Assert.*
import org.junit.Test

class FloatingRenderPolicyTest {
    @Test fun portraitAndLandscapeStayWithinRenderBudget() {
        for (dp in listOf(280, 320, 360, 412)) for (dpi in listOf(160, 320, 480, 640)) {
            val w = dp * dpi / 160
            val h = 900 * dpi / 160
            for ((width, height) in listOf(w to h, h to w)) {
                val s = FloatingRenderPolicy.fit(width, height, dpi)
                assertTrue(FloatingDisplayPolicy.validSize(s.width, s.height, s.density))
                assertTrue(s.width * s.height <= FloatingRenderPolicy.MAX_PIXELS)
                assertTrue(maxOf(s.width, s.height) <= FloatingRenderPolicy.MAX_EDGE)
                assertTrue(s.width <= width && s.height <= height)
                assertEquals(0, s.width % 2); assertEquals(0, s.height % 2)
                assertEquals(width.toDouble() / height, s.width.toDouble() / s.height, .015)
            }
        }
    }

    @Test fun narrowWindowKeepsPhoneLayoutWithoutFullScreenBuffer() {
        val s = FloatingRenderPolicy.fit(720, 900, 440)
        assertEquals(720, s.width); assertEquals(900, s.height)
        assertTrue(s.width * 160.0 / s.density >= 320)
        val oldFullScreenPixels = 1080 * (1080 * 900 / 720)
        assertTrue(s.width * s.height < oldFullScreenPixels / 2)
    }

    @Test fun largeWindowScalesDensityWithPixels() {
        val s = FloatingRenderPolicy.fit(2400, 1600, 320)
        assertEquals(1200.0, s.width * 160.0 / s.density, 10.0)
        assertTrue(s.width * s.height <= FloatingRenderPolicy.MAX_PIXELS)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroViewportNeverCreatesDisplay() { FloatingRenderPolicy.fit(0, 900, 440) }
}
