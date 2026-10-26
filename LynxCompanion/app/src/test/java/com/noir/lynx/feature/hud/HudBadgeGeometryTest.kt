package com.noir.lynx.feature.hud

import com.noir.lynx.feature.hud.overlay.*
import org.junit.Assert.*
import org.junit.Test

class HudBadgeGeometryTest {
    @Test fun allEdgesUseMeasuredWindowSizeWithoutAMargin() {
        val bounds = HudBadgeBounds(HudScreenRect(0, 0, 2460, 1080))
        val size = 125 // 48 dp at the device's 2.6 px/dp, rather than the obsolete 42 dp.
        val left = bounds.place(-999, 500, size, size)
        val right = bounds.place(9999, 500, size, size)
        assertEquals(0, left.x); assertEquals(2460, right.x + size)
        assertEquals(-1f, left.edgeX, 0f); assertEquals(1f, right.edgeX, 0f)
        assertEquals(0, bounds.place(500, -999, size, size).y)
        assertEquals(1080, bounds.place(500, 9999, size, size).y + size)
    }

    @Test fun theActualWidthAndHeightCanDiffer() {
        val point = HudBadgeBounds(HudScreenRect(11, 22, 400, 800)).place(1000, 1000, 80, 120)
        assertEquals(320, point.x); assertEquals(680, point.y)
    }

    @Test fun cutoutOnlyBlocksItsPhysicalRectangle() {
        val notch = HudScreenRect(0, 430, 90, 650)
        val bounds = HudBadgeBounds(HudScreenRect(0, 0, 2460, 1080), listOf(notch))
        assertEquals(0, bounds.place(0, 800, 125, 125).x)
        val avoided = bounds.place(0, 480, 125, 125)
        assertFalse(notch.intersects(avoided.x, avoided.y, 125, 125))
    }

    @Test fun visibleSystemBarsAreReservedOnce() {
        val bounds = HudBadgeBounds(HudScreenRect(28, 60, 2400, 1040))
        assertEquals(28, bounds.place(-99, 500, 125, 125).x)
        assertEquals(2400, bounds.place(9999, 500, 125, 125).x + 125)
    }

    @Test fun persistedFractionsRoundTripAtEdgesAndAfterRotation() {
        for (frame in listOf(HudScreenRect(0, 60, 1080, 2400), HudScreenRect(0, 0, 2460, 1080))) {
            val bounds = HudBadgeBounds(frame)
            for (x in listOf(0f, .4f, 1f)) for (y in listOf(0f, .65f, 1f)) {
                val point = bounds.fromFractions(x, y, 125, 125)
                val fractions = bounds.fractions(point.x, point.y, 125, 125)
                assertEquals(x, fractions.first, .001f); assertEquals(y, fractions.second, .001f)
            }
        }
    }

    @Test fun repeatedDragsNeverEscapeTheFrameOrCutout() {
        val bounds = HudBadgeBounds(HudScreenRect(0, 0, 2460, 1080), listOf(HudScreenRect(0, 430, 90, 650)))
        for (x in -200..2600 step 137) for (y in -200..1200 step 113) {
            val p = bounds.place(x, y, 125, 125)
            assertTrue(p.x >= 0 && p.y >= 0 && p.x + 125 <= 2460 && p.y + 125 <= 1080)
            assertTrue(bounds.cutouts.none { it.intersects(p.x, p.y, 125, 125) })
        }
    }
}
