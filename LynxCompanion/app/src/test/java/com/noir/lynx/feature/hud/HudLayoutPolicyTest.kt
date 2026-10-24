package com.noir.lynx.feature.hud

import com.noir.lynx.feature.hud.ui.HudLayoutPolicy
import com.noir.lynx.feature.hud.ui.HudViewport
import org.junit.Assert.*
import org.junit.Test

class HudLayoutPolicyTest {
    @Test fun navigationSwitchesExactlyAtMinimumContentWidth() {
        assertTrue(HudLayoutPolicy.resolve(260f, 56f).useRail)
        assertEquals(176f, HudLayoutPolicy.resolve(260f, 56f).contentWidth, 0f)
        val narrower = HudLayoutPolicy.resolve(259.9f, 56f)
        assertFalse(narrower.useRail)
        assertEquals(247.9f, narrower.contentWidth, 0.001f)
    }

    @Test fun measuredLabelsGrowRailWithoutClippingOrStealingMinimumContent() {
        val short = HudLayoutPolicy.resolve(336f, 40f)
        val long = HudLayoutPolicy.resolve(336f, 90f)
        assertEquals(72f, short.railWidth, 0f)
        assertEquals(106f, long.railWidth, 0f)
        assertTrue(long.useRail)
        assertFalse(HudLayoutPolicy.resolve(336f, 134f).useRail)
    }

    @Test fun safeWindowAndScaledLabelMatrixStaysWithinExistingOverlayLimits() {
        // Width inputs stand for measured localized text, not a substitute for font rendering tests.
        for (width in listOf(280f, 320f, 360f, 412f)) {
            for (viewport in listOf(HudViewport(width, 800f), HudViewport(width, 200f), HudViewport(800f, width))) {
                for (scale in listOf(1f, 1.3f, 2f)) {
                    for (label in listOf(52f, 58f, 84f)) {
                        val layout = HudLayoutPolicy.resolve(viewport.panelWidth, label * scale)
                        assertTrue(layout.contentWidth > 0)
                        val occupied = layout.contentWidth + HudLayoutPolicy.CONTENT_PADDING * 2 + if (layout.useRail) layout.railWidth else 0f
                        assertEquals(viewport.panelWidth, occupied, 0.001f)
                        if (layout.useRail) assertTrue(layout.contentWidth >= 176f)
                        assertTrue(viewport.panelHeight <= viewport.heightDp)
                    }
                }
            }
        }
    }

    @Test fun cellsReflowInsteadOfShrinkingLabelsOrNumbers() {
        assertTrue(HudLayoutPolicy.fits(200f, listOf(88f, 88f)))
        assertFalse(HudLayoutPolicy.fits(176f, listOf(88f, 88f)))
        assertTrue(HudLayoutPolicy.fits(196f, List(3) { 60f }, 0f))
        assertFalse(HudLayoutPolicy.fits(196f, List(3) { 78f }, 0f))
        assertTrue(HudLayoutPolicy.fits(0f, emptyList()))
    }

    @Test fun compactGridsRespectMeasuredCellsAtSmallAndScaledWidths() {
        assertEquals(3, HudLayoutPolicy.columns(176f, 48f))
        assertEquals(2, HudLayoutPolicy.columns(176f, 80f))
        assertEquals(1, HudLayoutPolicy.columns(176f, 120f))
        assertEquals(4, HudLayoutPolicy.columns(280f, 60f, maximum = 4))
        assertEquals(1, HudLayoutPolicy.columns(0f, 80f))
        for (width in listOf(176f, 224f, 280f, 336f)) for (font in listOf(1f, 1.3f, 2f)) {
            val cell = 70f * font
            val columns = HudLayoutPolicy.columns(width, cell)
            if (columns > 1) assertTrue(HudLayoutPolicy.fits(width, List(columns) { cell }, 4f))
        }
    }
}
