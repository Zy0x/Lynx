package com.noir.lynx.feature.floating

import com.noir.lynx.feature.floating.root.FloatingDisplayPolicy
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
    @Test fun systemBackendRequiresActualTaskEvidence() {
        val fullscreen = "Display #0\n * Task{abc #42 A=100:com.example.chat U=0 mode=fullscreen}"
        val floating = fullscreen.replace("fullscreen", "freeform")
        assertFalse(SystemFreeformEvidence.confirmed("", fullscreen, "com.example.chat", false))
        assertTrue(SystemFreeformEvidence.confirmed(fullscreen, floating, "com.example.chat", false))
        assertFalse(SystemFreeformEvidence.confirmed("", floating, "com.example", false))
        assertFalse(SystemFreeformEvidence.confirmed("", "Broadcast completed: result=0", "com.example.chat", true))
        val virtual = fullscreen.replace("#0", "#4")
        assertTrue(SystemFreeformEvidence.confirmed(fullscreen, virtual, "com.example.chat", true))
        assertFalse(SystemFreeformEvidence.confirmed(virtual, virtual, "com.example.chat", true))
    }
}
