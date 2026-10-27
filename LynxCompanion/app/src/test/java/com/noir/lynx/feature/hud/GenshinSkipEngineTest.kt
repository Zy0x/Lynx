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

        // 4-tier discrete vertical anchors (for 1, 2, 3, or 4+ dialogue choices)
        assertEquals(496, resInfinix.choice1.y) // 1080 * 0.46 = 496.8
        assertEquals(583, resInfinix.choice2.y) // 1080 * 0.54 = 583.2
        assertEquals(669, resInfinix.choice3.y) // 1080 * 0.62 = 669.6
        assertEquals(756, resInfinix.choice4.y) // 1080 * 0.70 = 756.0
        assertTrue(resInfinix.choice1.y < resInfinix.choice2.y)
        assertTrue(resInfinix.choice2.y < resInfinix.choice3.y)
        assertTrue(resInfinix.choice3.y < resInfinix.choice4.y)
    }

    @Test
    fun dialogueColorDetectionAccuratelyIdentifiesGenshinWarmCream() {
        // Genshin dialogue warm cream / beige signature: RGB (236, 229, 216)
        assertTrue(GenshinSkipEngine.isDialogueColor(236, 229, 216))
        assertTrue(GenshinSkipEngine.isDialogueColor(240, 230, 218))
        assertTrue(GenshinSkipEngine.isDialogueColor(220, 212, 200))

        // Non-dialogue UI colors must be rejected
        assertFalse(GenshinSkipEngine.isDialogueColor(255, 255, 255)) // Pure white (r - b = 0)
        assertFalse(GenshinSkipEngine.isDialogueColor(0, 0, 0))       // Black
        assertFalse(GenshinSkipEngine.isDialogueColor(50, 50, 50))    // Dark grey
        assertFalse(GenshinSkipEngine.isDialogueColor(128, 128, 128)) // Mid grey
        assertFalse(GenshinSkipEngine.isDialogueColor(255, 0, 0))     // Pure red
        assertFalse(GenshinSkipEngine.isDialogueColor(0, 255, 0))     // Pure green
        assertFalse(GenshinSkipEngine.isDialogueColor(0, 0, 255))     // Pure blue
    }

    @Test
    fun rawHexParsingDetectsDialogueSignature() {
        // Hex with 4 pixels of dark grey followed by 1 pixel of warm cream (ece5d8ff)
        val sampleHexWithDialogue = "323232ff323232ff323232ffece5d8ff323232ff"
        assertTrue(GenshinSkipEngine.containsDialogueColor(sampleHexWithDialogue))

        // Hex without dialogue signature (all dark grey)
        val sampleHexWithoutDialogue = "323232ff323232ff323232ff323232ff"
        assertFalse(GenshinSkipEngine.containsDialogueColor(sampleHexWithoutDialogue))

        // Blank or corrupt string
        assertFalse(GenshinSkipEngine.containsDialogueColor(""))
        assertFalse(GenshinSkipEngine.containsDialogueColor("   "))
    }

    @Test
    fun autoButtonOffsetCalculatesPredictablePositions() {
        val offset = GenshinSkipEngine.calculateAutoButtonOffset(2460, 1080)
        // x = (2460 * 0.0305f).toInt() = 75, y = (1080 * 0.0555f).toInt() = 59
        // 16 + (59 * 2460 + 75) * 4 = 16 + (145140 + 75) * 4 = 580876L
        assertEquals(580876L, offset)
    }

    @Test
    fun dialogueScanSliceAdaptsToScreenResolutionsAndOrientations() {
        // Infinix 2460 x 1080 (20.5:9 ultrawide)
        // safeWidth = 1920, pillarbox = 270
        // xMin = 270 + 45 = 315, xMax = 270 + 280 = 550, y = 56
        // rowStart = 16 + (56 * 2460 * 4) = 551056L
        // byteStart = 551056 + (315 * 4) = 552316L
        // byteEnd = 551056 + (550 * 4) = 553256L
        // sliceBytes = 553256 - 552316 = 940L
        val sliceInfinix = GenshinSkipEngine.calculateDialogueScanSlice(2460, 1080)
        assertEquals(553256L, sliceInfinix.first)
        assertEquals(940L, sliceInfinix.second)
        assertTrue(sliceInfinix.first > sliceInfinix.second)

        // Orientation invariance: passing portrait dimensions (1080, 2460) resolves identical slice params
        val sliceInfinixPortrait = GenshinSkipEngine.calculateDialogueScanSlice(1080, 2460)
        assertEquals(sliceInfinix.first, sliceInfinixPortrait.first)
        assertEquals(sliceInfinix.second, sliceInfinixPortrait.second)

        // Standard 16:9 1920 x 1080
        // safeWidth = 1920, pillarbox = 0
        // xMin = 45, xMax = 280, y = 56
        // rowStart = 16 + (56 * 1920 * 4) = 430096L
        // byteStart = 430096 + (45 * 4) = 430276L
        // byteEnd = 430096 + (280 * 4) = 431216L
        // sliceBytes = 431216 - 430276 = 940L
        val slice1080p = GenshinSkipEngine.calculateDialogueScanSlice(1920, 1080)
        assertEquals(431216L, slice1080p.first)
        assertEquals(940L, slice1080p.second)
    }

    @Test
    fun minDialogueThresholdScalesDynamicallyWithResolution() {
        // 1080p baseline requires at least 12 pixels
        assertEquals(12, GenshinSkipEngine.calculateMinDialogueThreshold(1080))

        // 720p budget display requires at least 8 pixels
        assertEquals(8, GenshinSkipEngine.calculateMinDialogueThreshold(720))

        // 1440p (2K) display requires at least 16 pixels
        assertEquals(16, GenshinSkipEngine.calculateMinDialogueThreshold(1440))

        // Clamping safeguards against negative or zero dimensions
        assertTrue(GenshinSkipEngine.calculateMinDialogueThreshold(0) >= 6)
    }

    @Test
    fun countDialogueMatchesAccuratelyCountsPixels() {
        val sampleHexWithMultiple = "ece5d8ff323232ffece4d8ff323232ffede5daff"
        assertEquals(3, GenshinSkipEngine.countDialogueMatches(sampleHexWithMultiple))

        val sampleHexZero = "323232ff111111ff000000ff"
        assertEquals(0, GenshinSkipEngine.countDialogueMatches(sampleHexZero))
    }

    @Test
    fun sceneTelemetrySerializesValidJson() {
        val telemetry = GenshinSkipEngine.SceneTelemetry(
            timestamp = 1730042100000L,
            gamePackage = "com.miHoYo.GenshinImpact",
            width = 2460,
            height = 1080,
            safeWidth = 1920L,
            pillarbox = 270L,
            xMin = 315L,
            xMax = 550L,
            yRow = 56L,
            sliceBytes = 940L,
            matchingPixels = 78,
            thresholdRequired = 12,
            dialogueDeclared = true,
            mode = "AUTO"
        )
        val json = telemetry.toJson()
        assertTrue(json.contains("\"game_package\": \"com.miHoYo.GenshinImpact\""))
        assertTrue(json.contains("\"matching_pixels\": 78"))
        assertTrue(json.contains("\"dialogue_declared\": true"))
        assertTrue(json.contains("\"pillarbox_padding\": 270"))
    }
}
