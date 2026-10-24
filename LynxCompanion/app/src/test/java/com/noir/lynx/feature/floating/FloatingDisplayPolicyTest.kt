package com.noir.lynx.feature.floating

import org.junit.Assert.*
import org.junit.Test

class FloatingDisplayPolicyTest {
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
