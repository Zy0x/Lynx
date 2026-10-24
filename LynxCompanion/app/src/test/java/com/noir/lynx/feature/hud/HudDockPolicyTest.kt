package com.noir.lynx.feature.hud

import com.noir.lynx.feature.hud.data.HudDockPolicy
import org.junit.Assert.*
import org.junit.Test

class HudDockPolicyTest {
    @Test fun migrationKeepsAvailableLegacyFavoritesButExplicitEmptyStaysEmpty() {
        val installed = setOf("com.android.chrome", "com.whatsapp", "custom.launcher")
        assertEquals(listOf("com.android.chrome", "com.whatsapp"), HudDockPolicy.packages(null, installed))
        assertTrue(HudDockPolicy.packages(emptyList(), installed).isEmpty())
    }

    @Test fun anyInstalledAppCanBeAddedAndOrderSurvivesMissingPackages() {
        val saved = listOf("custom.launcher", "absent", "com.android.chrome", "custom.launcher")
        assertEquals(listOf("custom.launcher", "com.android.chrome"),
            HudDockPolicy.packages(saved, setOf("custom.launcher", "com.android.chrome")))
        assertEquals(listOf("custom.launcher", "absent", "com.android.chrome"),
            HudDockPolicy.packages(saved, setOf("custom.launcher", "absent", "com.android.chrome")))
    }
}
