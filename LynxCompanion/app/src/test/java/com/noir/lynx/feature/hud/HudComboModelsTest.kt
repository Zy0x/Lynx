package com.noir.lynx.feature.hud.combo

import org.junit.Assert.*
import org.junit.Test

class HudComboModelsTest {

    @Test
    fun recordedTouchCalculatesPixelAccurately() {
        val touch = RecordedTouch(xRatio = 0.50f, yRatio = 0.25f, delayBeforeMs = 150L)
        assertTrue(touch.valid())
        assertEquals(540, touch.toPixelX(1080))
        assertEquals(615, touch.toPixelY(2460))

        // Landscape 2460x1080
        assertEquals(1230, touch.toPixelX(2460))
        assertEquals(270, touch.toPixelY(1080))
    }

    @Test
    fun macroComboBadgeGeneratesSubscripts() {
        val c1 = MacroCombo(id = "c1")
        assertEquals("C₁", c1.displayBadge)

        val c2 = MacroCombo(id = "c2")
        assertEquals("C₂", c2.displayBadge)

        val c5 = MacroCombo(id = "c5")
        assertEquals("C₅", c5.displayBadge)

        val c10 = MacroCombo(id = "c10")
        assertEquals("C₁₀", c10.displayBadge)
    }

    @Test
    fun touchShareParserExportsAndImportsAccurately() {
        val original = MacroCombo(
            id = "c1",
            name = "Combo Fast Kick",
            enabled = true,
            executionMode = ExecutionMode.ONE_SHOT,
            triggerXRatio = 0.85f,
            triggerYRatio = 0.40f,
            loopDelayMs = 200L,
            taps = listOf(
                RecordedTouch(0.35f, 0.70f, 0L),
                RecordedTouch(0.40f, 0.65f, 180L),
                RecordedTouch(0.80f, 0.20f, 350L)
            )
        )

        val exportedPassphrase = TouchShareParser.exportToString(original)
        assertTrue(exportedPassphrase.startsWith("LYNX-COMBO#1#c1#"))

        val imported = TouchShareParser.importFromString(exportedPassphrase, targetId = "c2")
        assertNotNull(imported)
        assertEquals("c2", imported?.id)
        assertEquals("Combo Fast Kick", imported?.name)
        assertEquals(ExecutionMode.ONE_SHOT, imported?.executionMode)
        assertEquals(3, imported?.taps?.size)
        assertEquals(0.35f, imported?.taps?.get(0)?.xRatio ?: 0f, 0.001f)
        assertEquals(180L, imported?.taps?.get(1)?.delayBeforeMs)
        assertEquals(350L, imported?.taps?.get(2)?.delayBeforeMs)
    }

    @Test
    fun touchShareParserRejectsMalformedStrings() {
        assertNull(TouchShareParser.importFromString(""))
        assertNull(TouchShareParser.importFromString("SOME-RANDOM-TEXT"))
        assertNull(TouchShareParser.importFromString("LYNX-COMBO#999#invalid#payload"))
    }
}
