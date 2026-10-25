package com.noir.lynx.feature.hud

import com.noir.lynx.feature.hud.autoskip.GenshinSkipEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenshinSkipEngineTest {

    @Test
    fun packageRecognitionIdentifiesHoyoAndRpgGames() {
        assertTrue(GenshinSkipEngine.isGameSupported("com.miHoYo.GenshinImpact"))
        assertTrue(GenshinSkipEngine.isGameSupported("com.miHoYo.Yuanshen"))
        assertTrue(GenshinSkipEngine.isGameSupported("com.HoYoverse.hkrpgoversea"))
        assertTrue(GenshinSkipEngine.isGameSupported("com.HoYoverse.Nap"))
        assertTrue(GenshinSkipEngine.isGameSupported("com.kurogame.wutheringwaves.global"))
        assertTrue(GenshinSkipEngine.isGameSupported("com.custom.genshin.clone"))

        assertFalse(GenshinSkipEngine.isGameSupported("com.whatsapp"))
        assertFalse(GenshinSkipEngine.isGameSupported("com.android.chrome"))
        assertFalse(GenshinSkipEngine.isGameSupported(""))
    }

    @Test
    fun coordinateResolutionAdaptsAccuratelyToAspectRatios() {
        // Standard 16:9 (1920 x 1080)
        val res16_9 = GenshinSkipEngine.resolveCoordinates(1920, 1080)
        assertEquals(1305, res16_9.advance.x)
        assertEquals(648, res16_9.advance.y)
        assertEquals(960, res16_9.narration.x)
        assertEquals(950, res16_9.narration.y)
        assertEquals(1382, res16_9.topChoice.x)
        assertEquals(540, res16_9.topChoice.y)
        assertEquals(626, res16_9.midChoice.y)
        assertEquals(712, res16_9.bottomChoice.y)

        // Ultra-wide 20.5:9 Infinix Note 11s (2460 x 1080)
        val resInfinix = GenshinSkipEngine.resolveCoordinates(2460, 1080)
        assertEquals(1672, resInfinix.advance.x)
        assertEquals(648, resInfinix.advance.y)
        assertEquals(1230, resInfinix.narration.x)
        assertEquals(950, resInfinix.narration.y)
        assertEquals(1771, resInfinix.topChoice.x)
        assertEquals(540, resInfinix.topChoice.y)
        assertEquals(626, resInfinix.midChoice.y)
        assertEquals(712, resInfinix.bottomChoice.y)

        // Orientation invariance: passing portrait dimensions (1080, 2460) resolves identical landscape targets
        val resPortraitInput = GenshinSkipEngine.resolveCoordinates(1080, 2460)
        assertEquals(resInfinix.advance.x, resPortraitInput.advance.x)
        assertEquals(resInfinix.advance.y, resPortraitInput.advance.y)
        assertEquals(resInfinix.narration.x, resPortraitInput.narration.x)
        assertEquals(resInfinix.narration.y, resPortraitInput.narration.y)
        assertEquals(resInfinix.topChoice.x, resPortraitInput.topChoice.x)
        assertEquals(resInfinix.bottomChoice.y, resPortraitInput.bottomChoice.y)

        // Safety checks: choice targets are vertically ordered and within screen bounds
        assertTrue(resInfinix.topChoice.y < resInfinix.midChoice.y)
        assertTrue(resInfinix.midChoice.y < resInfinix.bottomChoice.y)
        assertTrue(resInfinix.bottomChoice.y < resInfinix.narration.y)
        assertTrue(resInfinix.advance.x < 2460)
        assertTrue(resInfinix.advance.y < 1080)
    }
}
