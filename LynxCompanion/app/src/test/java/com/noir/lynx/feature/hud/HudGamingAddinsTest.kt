package com.noir.lynx.feature.hud

import com.noir.lynx.feature.hud.addins.*
import org.junit.Assert.*
import org.junit.Test

class HudGamingAddinsTest {
    @Test fun qosRulesAreScopedToOneUidAndUniqueOwner() {
        val token = "ab".repeat(16)
        val add = gameQosRule("iptables", "-A", 10649, token)
        assertTrue(add.contains("--uid-owner 10649"))
        assertTrue(add.contains("--comment lynx-hud-$token"))
        assertEquals(add.replace(" -A ", " -D "), gameQosRule("iptables", "-D", 10649, token))
        listOf("iptables;reboot", "nft").forEach {
            assertThrows(IllegalArgumentException::class.java) { gameQosRule(it, "-A", 10649, token) }
        }
        assertThrows(IllegalArgumentException::class.java) { gameQosRule("iptables", "-F", 10649, token) }
        assertThrows(IllegalArgumentException::class.java) { gameQosRule("iptables", "-A", 0, token) }
        assertThrows(IllegalArgumentException::class.java) { gameQosRule("iptables", "-A", 10649, "';reboot") }
    }
    @Test fun combinedGlowOpacityNeverBlocksUnderlyingTouches() {
        for (limit in listOf(0f, .2f, .8f, 1f)) {
            val alpha = glowWindowAlpha(limit)
            val combined = 1.0 - Math.pow(1.0 - alpha, 4.0)
            assertTrue(combined <= limit + .00001)
        }
    }
    @Test fun glowRejectsReplayDuplicatesOngoingAndStaleNotifications() {
        assertTrue(eligibleGlow("lynx", "chat", null, 1000, 1100, 0))
        assertTrue(eligibleGlow("lynx", "chat", 900, 1000, 1100, 0))
        assertFalse(eligibleGlow("lynx", "lynx", null, 1000, 1100, 0))
        assertFalse(eligibleGlow("lynx", "chat", 1000, 1000, 1100, 0))
        assertFalse(eligibleGlow("lynx", "chat", null, 1000, 12000, 0))
        assertFalse(eligibleGlow("lynx", "chat", null, 1000, 900, 0))
        assertFalse(eligibleGlow("lynx", "chat", null, 1000, 1100, 2))
        assertFalse(eligibleGlow("lynx", "chat", null, 1000, 1100, 512))
    }
    @Test fun restoreOnlyOurLastWriteAndPreserveUserChanges() {
        assertTrue(shouldRestore("2", "2"))
        assertFalse(shouldRestore("0", "2"))
        assertFalse(shouldRestore(null, "2"))
        assertFalse(shouldRestore("2", null))
        assertEquals("alarms", zenArgument(3))
        assertEquals("none", zenArgument(2))
    }

    @Test fun macroRequiresExactAppScreenAndGeometry() {
        fun allowed(actual: String = "com.noir.lynx", open: Boolean = false, on: Boolean = true, w: Int = 1080, h: Int = 2460) =
            canRunMagic("com.noir.lynx", actual, open, on, 1080, 2460, w, h)
        assertTrue(allowed())
        assertFalse(allowed(actual = "com.android.settings"))
        assertFalse(allowed(actual = ""))
        assertFalse(allowed(open = true))
        assertFalse(allowed(on = false))
        assertFalse(allowed(w = 2460, h = 1080))
        assertFalse(allowed(w = 0))
    }

    @Test fun macroRejectsShellMetacharactersAndOffScreenPoints() {
        listOf("", "android", "pkg;reboot", "com.app'", "com.app\ninput tap 0 0", "com.app/Activity").forEach {
            assertFalse(it, validMagicPackage(it))
        }
        assertTrue(validMagicPackage("com.example.game_2"))
        assertTrue(HudTapPoint(1, 99).valid())
        assertFalse(HudTapPoint(0, 50).valid())
        assertFalse(HudTapPoint(50, 100).valid())
    }
}
