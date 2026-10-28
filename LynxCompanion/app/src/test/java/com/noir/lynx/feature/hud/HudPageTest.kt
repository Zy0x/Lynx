package com.noir.lynx.feature.hud

import com.noir.lynx.R
import com.noir.lynx.feature.hud.ui.HudPage
import com.noir.lynx.feature.hud.ui.label
import org.junit.Assert.assertEquals
import org.junit.Test

class HudPageTest {

    @Test
    fun hudPageOrderAndLabelsMatchFourTabRail() {
        val pages = HudPage.values().toList()
        assertEquals(listOf(HudPage.Summary, HudPage.Details, HudPage.Lab, HudPage.Tools), pages)
        assertEquals(R.string.hud_summary, HudPage.Summary.label)
        assertEquals(R.string.hud_monitor, HudPage.Details.label)
        assertEquals(R.string.hud_nav_boost, HudPage.Lab.label)
        assertEquals(R.string.hud_tools, HudPage.Tools.label)
    }
}
