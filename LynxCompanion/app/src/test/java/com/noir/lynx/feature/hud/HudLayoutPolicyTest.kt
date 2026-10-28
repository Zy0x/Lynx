package com.noir.lynx.feature.hud

import com.noir.lynx.feature.hud.ui.HudLayoutPolicy
import com.noir.lynx.feature.hud.ui.HudViewport
import org.junit.Assert.*
import org.junit.Test

class HudLayoutPolicyTest {
    @Test fun portraitKeepsCompactRailAndProtectsReadableContent() {
        for (panel in listOf(291f, 321f, 336f)) for (label in listOf(48f, 70f, 140f)) {
            val layout = HudLayoutPolicy.resolve(panel, label)
            assertTrue(layout.useRail)
            assertTrue(layout.railWidth in 64f..88f)
            assertTrue(layout.contentWidth >= HudLayoutPolicy.MIN_CONTENT)
        }
        assertFalse(HudLayoutPolicy.resolve(240f, 48f).useRail)
    }

    @Test fun navigationSwitchesExactlyAtMinimumContentWidth() {
        assertTrue(HudLayoutPolicy.resolve(266f, 48f).useRail)
        assertEquals(64f, HudLayoutPolicy.resolve(266f, 48f).railWidth, 0f)
        assertEquals(176f, HudLayoutPolicy.resolve(266f, 48f).contentWidth, 0f)
        val narrower = HudLayoutPolicy.resolve(265.9f, 48f)
        assertFalse(narrower.useRail)
        assertEquals(245.9f, narrower.contentWidth, 0.001f)
    }

    @Test fun longTranslationsWrapWithinBoundedRail() {
        val short = HudLayoutPolicy.resolve(336f, 40f)
        val long = HudLayoutPolicy.resolve(336f, 90f)
        assertEquals(HudLayoutPolicy.RAIL_WIDTH, short.railWidth, 0f)
        assertEquals(88f, long.railWidth, 0f)
        assertTrue(long.useRail)
        assertEquals(long, HudLayoutPolicy.resolve(336f, 240f))
    }

    @Test fun portraitPanelStaysAtBottomOfSafeAreaWithoutMovingLandscape() {
        for (density in listOf(1f, 2.625f, 3f)) {
            for (height in listOf(480f, 800f, 1200f)) {
                val portrait = HudViewport(360f, height)
                val safeHeight = (height * density).toInt()
                val top = portrait.panelTop(72, safeHeight, density)
                assertTrue(top >= 72)
                assertTrue(top + kotlin.math.ceil(portrait.panelHeight * density).toInt() <= 72 + safeHeight)
                assertTrue(portrait.panelHeight <= 600f)
            }
            assertEquals(72, HudViewport(800f, 360f).panelTop(72, (360f * density).toInt(), density))
        }
    }

    @Test fun safeWindowAndScaledLabelMatrixStaysWithinExistingOverlayLimits() {
        // Width inputs stand for measured localized text, not a substitute for font rendering tests.
        for (width in listOf(280f, 320f, 360f, 412f)) {
            for (viewport in listOf(HudViewport(width, 800f), HudViewport(width, 200f), HudViewport(800f, width))) {
                for (scale in listOf(1f, 1.3f, 2f)) {
                    for (label in listOf(48f, 52f, 58f, 84f)) {
                        val layout = HudLayoutPolicy.resolve(viewport.panelWidth, label * scale)
                        assertTrue(layout.contentWidth > 0)
                        val occupied = layout.contentWidth + HudLayoutPolicy.CONTENT_PADDING * 2 + if (layout.useRail) layout.railWidth + HudLayoutPolicy.RAIL_GAP else 0f
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

    @Test fun detailGridsIncludeTheirActualSixDpSpacing() {
        assertEquals(1, HudLayoutPolicy.columns(165f, 80f, gap = 6f, maximum = 2))
        assertEquals(2, HudLayoutPolicy.columns(166f, 80f, gap = 6f, maximum = 2))
        for (width in listOf(176f, 220f, 280f)) for (cell in listOf(80f, 104f, 160f)) {
            val columns = HudLayoutPolicy.columns(width, cell, gap = 6f, maximum = 2)
            if (columns > 1) assertTrue(HudLayoutPolicy.fits(width, List(columns) { cell }, 6f))
        }
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
