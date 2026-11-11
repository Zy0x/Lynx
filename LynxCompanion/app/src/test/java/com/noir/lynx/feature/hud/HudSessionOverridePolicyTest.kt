package com.noir.lynx.feature.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HudSessionOverridePolicyTest {

    @Test
    fun parseDaemonStatusAppExtractsTargetAppAndIgnoresIdleOrLaunchers() {
        assertEquals(
            "com.shopee.id",
            HudController.parseDaemonStatusApp(
                """{"profile":"performance","modifier":"None","app":"com.shopee.id (game)","temp_c":36.1,"battery_pct":84}"""
            )
        )
        assertNull(
            HudController.parseDaemonStatusApp(
                """{"profile":"balance","modifier":"None","app":"None (Idle/ScreenOff)"}"""
            )
        )
        assertNull(
            HudController.parseDaemonStatusApp(
                """{"profile":"balance","modifier":"None","app":"com.miui.home (normal)"}"""
            )
        )
        assertNull(
            HudController.parseDaemonStatusApp(
                """{"profile":"balance","modifier":"None","app":"com.noir.lynx:hud (system)"}"""
            )
        )
    }

    @Test
    fun isSystemOrLauncherPkgSeparatesSystemOverlayAndTargetApps() {
        assertTrue(HudController.isSystemOrLauncherPkg(null))
        assertTrue(HudController.isSystemOrLauncherPkg(""))
        assertTrue(HudController.isSystemOrLauncherPkg("com.noir.lynx"))
        assertTrue(HudController.isSystemOrLauncherPkg("com.noir.lynx:hud"))
        assertTrue(HudController.isSystemOrLauncherPkg("com.android.systemui"))
        assertTrue(HudController.isSystemOrLauncherPkg("com.miui.home"))
        assertTrue(HudController.isSystemOrLauncherPkg("com.google.android.apps.nexuslauncher"))

        assertFalse(HudController.isSystemOrLauncherPkg("com.shopee.id"))
        assertFalse(HudController.isSystemOrLauncherPkg("com.mobile.legends"))
    }

    @Test
    fun buildHudOverridePayloadSanitizesPackageAndValidatesProfile() {
        assertEquals(
            "com.shopee.id|balance",
            HudController.buildHudOverridePayload("com.shopee.id", "balance")
        )
        assertEquals(
            "com.shopee.id|extreme",
            HudController.buildHudOverridePayload("com.shopee.id'| \n", "EXTREME")
        )
        assertNull(HudController.buildHudOverridePayload("com.shopee.id", "auto"))
        assertNull(HudController.buildHudOverridePayload("", "balance"))
    }
}
