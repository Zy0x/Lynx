package com.noir.lynx.freeform

import com.noir.lynx.freeform.root.FloatingDisplayPolicy
import org.junit.Assert.*
import org.junit.Test

class FloatingDisplayPolicyTest {
    @Test fun cutoutsAndSmallViewportsKeepCompleteWindowReachable() {
        for (width in listOf(280, 320, 360, 412)) for (height in listOf(180, 640, 900)) {
            val b = FloatingDisplayPolicy.clamp(FloatingDisplayPolicy.Box(999, 999, 600, 800), 18, 32, width, height)
            assertTrue(b.x >= 18 && b.y >= 32)
            assertTrue(b.x + b.width <= 18 + width)
            assertTrue(b.y + b.height <= 32 + height)
        }
    }
    @Test fun rejectInvalidOrExcessiveDisplayAllocations() {
        assertTrue(FloatingDisplayPolicy.validSize(1080, 1920, 420))
        assertFalse(FloatingDisplayPolicy.validSize(4096, 4096, 420))
        assertFalse(FloatingDisplayPolicy.validSize(0, 1920, 420))
        assertFalse(FloatingDisplayPolicy.validSize(Int.MAX_VALUE, Int.MAX_VALUE, 420))
        assertFalse(FloatingDisplayPolicy.validSize(1080, 1920, 0))
    }

    @Test fun bottomRightResizeKeepsTopLeftAnchoredAtScreenEdges() {
        val original = FloatingDisplayPolicy.Box(100, 200, 300, 400)
        val expanded = FloatingDisplayPolicy.resize(original, Int.MAX_VALUE, Int.MAX_VALUE, 10, 20, 500, 800, 240)
        assertEquals(FloatingDisplayPolicy.Box(100, 200, 410, 620), expanded)
        val shrunk = FloatingDisplayPolicy.resize(original, Int.MIN_VALUE, Int.MIN_VALUE, 10, 20, 500, 800, 240)
        assertEquals(FloatingDisplayPolicy.Box(100, 200, 240, 240), shrunk)
    }

    @Test fun resizeMinimumFitsShortSafeAreaAndHonorsFingerDelta() {
        val original = FloatingDisplayPolicy.Box(18, 32, 200, 100)
        assertEquals(FloatingDisplayPolicy.Box(18, 32, 230, 120),
            FloatingDisplayPolicy.resize(original, 30, 20, 18, 32, 320, 180, 64))
        assertEquals(FloatingDisplayPolicy.Box(18, 32, 240, 180),
            FloatingDisplayPolicy.resize(original, -1000, -1000, 18, 32, 320, 180, 240))
    }
}
