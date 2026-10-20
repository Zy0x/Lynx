package com.noir.lynx.feature.hud

import com.noir.lynx.display.HudDisplaySession
import org.junit.Assert.*
import org.junit.Test

class HudDisplaySessionTest {
    @Test fun preservesFractionalRefreshRates() {
        val mode = HudDisplaySession.parsePreferred("User preferred display mode: 1080 2460 59.94")!!
        assertEquals(1080,mode.width)
        assertEquals(2460,mode.height)
        assertEquals(59.94f,mode.hz,0.001f)
        assertEquals("1080 2460 59.94",mode.arguments())
    }
    @Test fun unsupportedOrMalformedReadbacksCannotAuthorizeWrites() {
        listOf("Unknown command", "Permission Denial", "User preferred display mode: null", "User preferred display mode: 0 0 120", "User preferred display mode: 1080 2460 NaN", "User preferred display mode: 1080 2460 120;reboot").forEach {
            assertNull(HudDisplaySession.parsePreferred(it))
        }
    }
}
