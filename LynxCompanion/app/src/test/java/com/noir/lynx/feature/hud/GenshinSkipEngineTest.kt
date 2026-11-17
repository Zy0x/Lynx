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
        // 1080p baseline requires at least 18 pixels (noise shield)
        assertEquals(18, GenshinSkipEngine.calculateMinDialogueThreshold(1080))

        // 720p budget display requires at least 12 pixels
        assertEquals(12, GenshinSkipEngine.calculateMinDialogueThreshold(720))

        // 1440p (2K) display requires at least 24 pixels
        assertEquals(24, GenshinSkipEngine.calculateMinDialogueThreshold(1440))

        // Clamping safeguards against negative or zero dimensions
        assertTrue(GenshinSkipEngine.calculateMinDialogueThreshold(0) >= 10)
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

    @Test
    fun sceneSnapshotResultConstructsCorrectly() {
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
        val result = GenshinSkipEngine.SceneSnapshotResult(
            directory = "/sdcard/Lynx/scenes",
            fullPath = "/sdcard/Lynx/scenes/scene_test_full.png",
            slicePath = "/sdcard/Lynx/scenes/scene_test_slice.png",
            baseName = "scene_test",
            telemetry = telemetry
        )
        assertEquals("/sdcard/Lynx/scenes", result.directory)
        assertEquals("scene_test", result.baseName)
        assertTrue(result.telemetry.dialogueDeclared)
    }
    @Test
    fun promptGoldColorAccuratelyIdentifiesHoyoverseNarrationDiamond() {
        // True Hoyoverse gold / amber diamond colors:
        assertTrue(GenshinSkipEngine.isPromptGoldColor(235, 180, 2))
        assertTrue(GenshinSkipEngine.isPromptGoldColor(255, 195, 6))
        assertTrue(GenshinSkipEngine.isPromptGoldColor(183, 141, 0))

        // Non-gold colors must be rejected
        assertFalse(GenshinSkipEngine.isPromptGoldColor(255, 255, 255)) // White
        assertFalse(GenshinSkipEngine.isPromptGoldColor(0, 0, 0))       // Black
        assertFalse(GenshinSkipEngine.isPromptGoldColor(236, 229, 216)) // Dialogue warm cream (too high blue)
        assertFalse(GenshinSkipEngine.isPromptGoldColor(100, 150, 255)) // Blue
    }

    @Test
    fun promptScanSliceAdaptsToScreenResolutionsAndOrientations() {
        // Spans 80% screen width (xMin = 10% W, xMax = 90% W) to verify margins
        // Infinix 2460 x 1080: xMin = 246, xMax = 2214, sliceBytes = (2214 - 246) * 4 = 7872L
        val sliceInfinix = GenshinSkipEngine.calculatePromptScanSlice(2460, 1080)
        assertEquals(7872L, sliceInfinix.second)

        // Orientation invariance: portrait input resolves identical slice
        val sliceInfinixPortrait = GenshinSkipEngine.calculatePromptScanSlice(1080, 2460)
        assertEquals(sliceInfinix.first, sliceInfinixPortrait.first)
        assertEquals(sliceInfinix.second, sliceInfinixPortrait.second)

        // Standard 16:9 1920 x 1080: xMin = 192, xMax = 1728, sliceBytes = (1728 - 192) * 4 = 6144L
        val slice1080p = GenshinSkipEngine.calculatePromptScanSlice(1920, 1080)
        assertEquals(6144L, slice1080p.second)
    }

    @Test
    fun evaluatePromptSliceWithMarginsDistinguishesCutsceneFromGameplay() {
        val totalBytes = 7872
        val slice = ByteArray(totalBytes) // all zeros = pitch black

        // Scenario A: Pitch black margins + 20 gold pixels in center prompt
        // Center prompt for 2460x1080 is at px in [759 until 1209] inside slice
        val centerByteStart = 760 * 4
        for (i in 0 until 20) {
            val idx = centerByteStart + (i * 4)
            slice[idx] = 235.toByte()     // R
            slice[idx + 1] = 180.toByte() // G
            slice[idx + 2] = 2.toByte()   // B
            slice[idx + 3] = 255.toByte() // A
        }
        val cutsceneMetrics = GenshinSkipEngine.evaluatePromptSliceWithMargins(slice, 2460, 1080)
        assertTrue(cutsceneMetrics.isConfirmedNarration)
        assertEquals(20, cutsceneMetrics.goldMatches)
        assertTrue(cutsceneMetrics.marginBlackRatio >= 0.80f)

        // Scenario B: Dark cave gameplay - margins have colored terrain/rocks (R=120, G=100, B=90)
        val caveSlice = ByteArray(totalBytes)
        // Fill margins with non-black terrain
        for (i in 0 until 500) {
            val idx = i * 4 // in left margin
            caveSlice[idx] = 120.toByte()
            caveSlice[idx + 1] = 100.toByte()
            caveSlice[idx + 2] = 90.toByte()
        }
        // Even if a torch has 20 gold pixels in center:
        for (i in 0 until 20) {
            val idx = centerByteStart + (i * 4)
            caveSlice[idx] = 235.toByte()
            caveSlice[idx + 1] = 180.toByte()
            caveSlice[idx + 2] = 2.toByte()
        }
        val caveMetrics = GenshinSkipEngine.evaluatePromptSliceWithMargins(caveSlice, 2460, 1080)
        assertFalse(caveMetrics.isConfirmedNarration) // MUST BE REJECTED!
    }

    @Test
    fun sceneClassificationDataTypesConstructAccurately() {
        val classification = GenshinSkipEngine.SceneClassification(
            sceneType = GenshinSkipEngine.SceneType.BLACK_INTERSTITIAL,
            confidence = 0.99f,
            dialogueMatches = 0,
            goldMatches = 182,
            blackRatio = 0.98f,
            whiteRatio = 0.01f
        )
        assertEquals(GenshinSkipEngine.SceneType.BLACK_INTERSTITIAL, classification.sceneType)
        assertEquals(0.99f, classification.confidence, 0.001f)
        assertEquals(182, classification.goldMatches)
    }

    @Test
    fun sceneTelemetryIncludesMultiSceneClassification() {
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
            matchingPixels = 0,
            thresholdRequired = 12,
            dialogueDeclared = false,
            mode = "AUTO",
            sceneType = "BLACK_INTERSTITIAL",
            goldMatches = 182,
            blackRatio = 0.985f,
            whiteRatio = 0.005f
        )
        val json = telemetry.toJson()
        assertTrue(json.contains("\"scene_type\": \"BLACK_INTERSTITIAL\""))
        assertTrue(json.contains("\"gold_matches\": 182"))
        assertTrue(json.contains("\"black_ratio\": 0.985"))
    }

    @Test
    fun screencapHeaderParsingValidatesDimensionsAndBgraFormat() {
        fun makeHeader(w: Int, h: Int, fmt: Int): ByteArray {
            val out = ByteArray(16)
            for (i in 0..3) {
                out[i] = ((w ushr (i * 8)) and 0xFF).toByte()
                out[4 + i] = ((h ushr (i * 8)) and 0xFF).toByte()
                out[8 + i] = ((fmt ushr (i * 8)) and 0xFF).toByte()
            }
            return out
        }

        val h2460 = GenshinSkipEngine.parseScreencapHeader(makeHeader(2460, 1080, 1))
        assertTrue(h2460 != null && h2460.width == 2460 && h2460.height == 1080 && !h2460.isBgra && h2460.isLandscape)

        val hBgra = GenshinSkipEngine.parseScreencapHeader(makeHeader(1920, 1080, 5))
        assertTrue(hBgra != null && hBgra.isBgra && hBgra.isLandscape)

        val hPortrait = GenshinSkipEngine.parseScreencapHeader(makeHeader(1080, 2460, 1))
        assertTrue(hPortrait != null && !hPortrait.isLandscape)

        assertFalse(GenshinSkipEngine.parseScreencapHeader(ByteArray(4)) != null)
        assertFalse(GenshinSkipEngine.parseScreencapHeader(makeHeader(0, 0, 1)) != null)
    }

    @Test
    fun evaluateDialogueHeaderBandDetectsCircularDiscPairsAndRejectsOpenWorldOrMenus() {
        val w = 2460
        val h = 1080
        val spec = GenshinSkipEngine.calculateDialogueHeaderBand(w, h)
        val bandH = (spec.yMax - spec.yMin) + 1

        fun fillBackground(buf: ByteArray, r: Int, g: Int, b: Int) {
            var idx = 0
            while (idx <= buf.size - 4) {
                buf[idx] = r.toByte()
                buf[idx + 1] = g.toByte()
                buf[idx + 2] = b.toByte()
                buf[idx + 3] = 255.toByte()
                idx += 4
            }
        }

        fun paintDisc(buf: ByteArray, cx: Int, cy: Int, diameter: Int = 46) {
            val r = diameter / 2.0
            for (y in (cy - 26)..(cy + 26)) {
                if (y !in spec.yMin..spec.yMax) continue
                for (x in (cx - 26)..(cx + 26)) {
                    if (x !in 0 until w) continue
                    val dx = x - cx
                    val dy = y - cy
                    val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble())
                    if (dist <= r) {
                        val isGlyph = kotlin.math.abs(dx) <= 9 && (kotlin.math.abs(dy + 5) <= 2 || kotlin.math.abs(dy - 5) <= 2 || kotlin.math.abs(dy) <= 1)
                        val idx = (y - spec.yMin) * spec.headerStrideBytes + x * 4
                        if (isGlyph) {
                            buf[idx] = 59.toByte()
                            buf[idx + 1] = 67.toByte()
                            buf[idx + 2] = 84.toByte()
                        } else {
                            buf[idx] = 236.toByte()
                            buf[idx + 1] = 229.toByte()
                            buf[idx + 2] = 216.toByte()
                        }
                        buf[idx + 3] = 255.toByte()
                    }
                }
            }
        }

        // 1. True dialogue over sunlit green foliage (controls at x=339, 432, 525)
        val dialogBand = ByteArray(bandH * spec.headerStrideBytes)
        fillBackground(dialogBand, 145, 138, 52)
        paintDisc(dialogBand, 339, 59)
        paintDisc(dialogBand, 432, 59)
        paintDisc(dialogBand, 525, 59)
        val dialogMetrics = GenshinSkipEngine.evaluateDialogueHeaderBand(dialogBand, spec)
        assertTrue(dialogMetrics.isConfirmedDialogue)
        assertTrue(dialogMetrics.alignedPairCount >= 2)

        // 2. Language / shorter "Auto" shift (controls shifted left to x=250, 343, 436)
        val shiftedBand = ByteArray(bandH * spec.headerStrideBytes)
        fillBackground(shiftedBand, 28, 42, 65)
        paintDisc(shiftedBand, 250, 58)
        paintDisc(shiftedBand, 343, 58)
        val shiftedMetrics = GenshinSkipEngine.evaluateDialogueHeaderBand(shiftedBand, spec)
        assertTrue(shiftedMetrics.isConfirmedDialogue)
        assertEquals(1, shiftedMetrics.alignedPairCount)

        // 3. Open-world canyon / sunset warm sky + hollow Tutorial outline (previously false-triggered!)
        val openWorldBand = ByteArray(bandH * spec.headerStrideBytes)
        fillBackground(openWorldBand, 140, 95, 70)
        for (y in (spec.yMin + 10)..(spec.yMax - 8)) {
            val rowOff = (y - spec.yMin) * spec.headerStrideBytes
            // Warm cream horizon pixels + hollow vertical outline borders at x=426..431 & x=474..479
            for (x in 426..431) {
                val idx = rowOff + x * 4
                openWorldBand[idx] = 236.toByte(); openWorldBand[idx + 1] = 229.toByte(); openWorldBand[idx + 2] = 216.toByte()
            }
            for (x in 474..479) {
                val idx = rowOff + x * 4
                openWorldBand[idx] = 236.toByte(); openWorldBand[idx + 1] = 229.toByte(); openWorldBand[idx + 2] = 216.toByte()
            }
        }
        val openWorldMetrics = GenshinSkipEngine.evaluateDialogueHeaderBand(openWorldBand, spec)
        assertFalse(openWorldMetrics.isConfirmedDialogue)

        // 4. Artifact / Menu tabs too closely spaced without circular spacing (~61px instead of ~93px)
        val artifactBand = ByteArray(bandH * spec.headerStrideBytes)
        fillBackground(artifactBand, 29, 111, 102)
        paintDisc(artifactBand, 358, 56, diameter = 36)
        paintDisc(artifactBand, 419, 56, diameter = 36)
        val artifactMetrics = GenshinSkipEngine.evaluateDialogueHeaderBand(artifactBand, spec)
        assertFalse(artifactMetrics.isConfirmedDialogue)
    }

    @Test
    fun tapTargetAvoidanceSteersAroundMovedOverlays() {
        val raw = GenshinSkipEngine.TapTarget(1672, 648)
        val overlayOverAdvance = listOf(GenshinSkipEngine.OverlayRect(1600, 580, 1750, 720))
        val safe = GenshinSkipEngine.resolveTapAvoidingOverlays(raw, overlayOverAdvance, 2460, 1080, paddingPx = 28)

        // Must move cleanly outside the padded overlay bounding box and remain within screen bounds
        assertFalse(overlayOverAdvance[0].containsPadded(safe.x, safe.y, 28))
        assertTrue(safe.x in 24..(2460 - 24))
        assertTrue(safe.y in 24..(1080 - 24))

        // Unobstructed targets stay exact
        val clear = GenshinSkipEngine.resolveTapAvoidingOverlays(raw, emptyList(), 2460, 1080)
        assertEquals(raw, clear)

        // Choice-free safeAdvance stays left of the right-hand dialogue choice column
        val coords = GenshinSkipEngine.resolveCoordinates(2460, 1080)
        assertTrue(coords.safeAdvance.x < coords.choice1.x - 400)
    }

    @Test
    fun evaluateDialogueHeaderBandRecognizesPartiallyDimmedAndSingleAnchoredDialogues() {
        val w = 2460
        val h = 1080
        val spec = GenshinSkipEngine.calculateDialogueHeaderBand(w, h)
        val bandH = (spec.yMax - spec.yMin) + 1

        fun fillBg(buf: ByteArray, r: Int, g: Int, b: Int) {
            var idx = 0
            while (idx <= buf.size - 4) {
                buf[idx] = r.toByte(); buf[idx + 1] = g.toByte(); buf[idx + 2] = b.toByte(); buf[idx + 3] = 255.toByte()
                idx += 4
            }
        }

        fun paintDisc(buf: ByteArray, cx: Int, cy: Int, fgR: Int = 236, fgG: Int = 229, fgB: Int = 216, glyR: Int = 59, glyG: Int = 67, glyB: Int = 84) {
            val r = 23.0
            for (y in (cy - 25)..(cy + 25)) {
                if (y !in spec.yMin..spec.yMax) continue
                for (x in (cx - 25)..(cx + 25)) {
                    if (x !in 0 until w) continue
                    val dx = x - cx
                    val dy = y - cy
                    if (kotlin.math.hypot(dx.toDouble(), dy.toDouble()) <= r) {
                        val isGlyph = kotlin.math.abs(dx) <= 9 && (kotlin.math.abs(dy + 5) <= 2 || kotlin.math.abs(dy - 5) <= 2 || kotlin.math.abs(dy) <= 1)
                        val idx = (y - spec.yMin) * spec.headerStrideBytes + x * 4
                        if (isGlyph) {
                            buf[idx] = glyR.toByte(); buf[idx + 1] = glyG.toByte(); buf[idx + 2] = glyB.toByte()
                        } else {
                            buf[idx] = fgR.toByte(); buf[idx + 1] = fgG.toByte(); buf[idx + 2] = fgB.toByte()
                        }
                    }
                }
            }
        }

        fun paintAutoTextStrokes(buf: ByteArray, startX: Int, cy: Int) {
            // Paint crisp letter-like vertical strokes ("Otomatis" / "Auto") in cream off-white
            for (xOff in 0 until 130 step 7) {
                for (wOff in 0..2) {
                    for (y in (cy - 9)..(cy + 9)) {
                        if (y !in spec.yMin..spec.yMax) continue
                        val x = startX + xOff + wOff
                        val idx = (y - spec.yMin) * spec.headerStrideBytes + x * 4
                        buf[idx] = 239.toByte(); buf[idx + 1] = 239.toByte(); buf[idx + 2] = 240.toByte()
                    }
                }
            }
        }

        // 1. Cutscene dialogue where [▶] (x=91) & [👁] (x=429) are bright while [☰] (x=339) & [🔊] (x=519) are dimmed
        val splitBand = ByteArray(bandH * spec.headerStrideBytes)
        fillBg(splitBand, 18, 22, 50)
        paintDisc(splitBand, 91, 57)
        paintAutoTextStrokes(splitBand, 130, 57)
        paintDisc(splitBand, 339, 60, fgR = 73, fgG = 72, fgB = 92, glyR = 28, glyG = 30, glyB = 58)
        paintDisc(splitBand, 429, 60)
        paintDisc(splitBand, 519, 60, fgR = 73, fgG = 72, fgB = 92, glyR = 28, glyG = 30, glyB = 58)
        val splitMetrics = GenshinSkipEngine.evaluateDialogueHeaderBand(splitBand, spec)
        assertTrue(splitMetrics.isConfirmedDialogue)
        assertTrue(splitMetrics.splitPairCount >= 1)

        // 2. Extreme dimming where only [▶] + "Auto" text are bright and [☰], [👁], [🔊] are dimmed
        val singleAnchoredBand = ByteArray(bandH * spec.headerStrideBytes)
        fillBg(singleAnchoredBand, 18, 22, 50)
        paintDisc(singleAnchoredBand, 91, 57)
        paintAutoTextStrokes(singleAnchoredBand, 130, 57)
        paintDisc(singleAnchoredBand, 339, 60, fgR = 75, fgG = 74, fgB = 96, glyR = 28, glyG = 30, glyB = 58)
        paintDisc(singleAnchoredBand, 429, 60, fgR = 75, fgG = 74, fgB = 96, glyR = 28, glyG = 30, glyB = 58)
        val singleMetrics = GenshinSkipEngine.evaluateDialogueHeaderBand(singleAnchoredBand, spec)
        assertTrue(singleMetrics.isConfirmedDialogue)
        assertTrue(singleMetrics.singleAnchoredConfirmed)

        // 3. Artifact menu tab #1 (single cream disc at x=92 without Auto text or dimmed dialogue discs) must be rejected
        val artifactTab1Band = ByteArray(bandH * spec.headerStrideBytes)
        fillBg(artifactTab1Band, 29, 111, 102)
        paintDisc(artifactTab1Band, 92, 51)
        val artifactTab1Metrics = GenshinSkipEngine.evaluateDialogueHeaderBand(artifactTab1Band, spec)
        assertFalse(artifactTab1Metrics.isConfirmedDialogue)
    }

    @Test
    fun evaluateBottomSceneBandDistinguishesSpecialCgAndBlackNarrationFromOpenWorld() {
        val w = 2460
        val h = 1080
        val spec = GenshinSkipEngine.calculateBottomSceneBand(w, h)
        val bandH = (spec.yMax - spec.yMin) + 1

        fun fillBg(buf: ByteArray, r: Int, g: Int, b: Int) {
            var idx = 0
            while (idx <= buf.size - 4) {
                buf[idx] = r.toByte(); buf[idx + 1] = g.toByte(); buf[idx + 2] = b.toByte(); buf[idx + 3] = 255.toByte()
                idx += 4
            }
        }

        fun paintPromptAndDiamond(buf: ByteArray) {
            // Paint crisp letter-like strokes across y=932..952 centered at x=1230
            for (y in 932..952) {
                for (x in 1020..1440 step 5) {
                    for (dx in 0..1) {
                        val idx = (y - spec.yMin) * spec.strideBytes + (x + dx) * 4
                        buf[idx] = 240.toByte(); buf[idx + 1] = 185.toByte(); buf[idx + 2] = 4.toByte()
                    }
                }
            }
            // Paint centered bottom diamond at y=1004..1014, x=1225..1235
            for (y in 1004..1014) {
                val half = 5 - kotlin.math.abs(y - 1009)
                for (x in (1230 - half)..(1230 + half)) {
                    val idx = (y - spec.yMin) * spec.strideBytes + x * 4
                    buf[idx] = 245.toByte(); buf[idx + 1] = 192.toByte(); buf[idx + 2] = 8.toByte()
                }
            }
        }

        // 1. Special CG / Illustrated cutscene (colored blue-purple background, NOT black margins!)
        val cgBand = ByteArray(bandH * spec.strideBytes)
        fillBg(cgBand, 36, 48, 92)
        paintPromptAndDiamond(cgBand)
        val cgMetrics = GenshinSkipEngine.evaluateBottomSceneBand(cgBand, spec)
        assertFalse(cgMetrics.hasOpenWorldHpBar)
        assertTrue(cgMetrics.isConfirmedSpecialNarration)
        assertFalse(cgMetrics.isConfirmedBlackInterstitial)

        // 2. Black-screen narration interstitial
        val blkBand = ByteArray(bandH * spec.strideBytes)
        fillBg(blkBand, 2, 2, 2)
        paintPromptAndDiamond(blkBand)
        val blkMetrics = GenshinSkipEngine.evaluateBottomSceneBand(blkBand, spec)
        assertFalse(blkMetrics.hasOpenWorldHpBar)
        assertTrue(blkMetrics.isConfirmedBlackInterstitial)

        // 3. Open-world scene with green HP bar at y=986..998 MUST suppress narration even with stray gold pixels
        val owBand = ByteArray(bandH * spec.strideBytes)
        fillBg(owBand, 60, 75, 90)
        paintPromptAndDiamond(owBand)
        for (y in 986..996) {
            for (x in 1100..1240) {
                val idx = (y - spec.yMin) * spec.strideBytes + x * 4
                owBand[idx] = 128.toByte(); owBand[idx + 1] = 190.toByte(); owBand[idx + 2] = 48.toByte()
            }
        }
        val owMetrics = GenshinSkipEngine.evaluateBottomSceneBand(owBand, spec)
        assertTrue(owMetrics.hasOpenWorldHpBar)
        assertFalse(owMetrics.isConfirmedSpecialNarration)
        assertFalse(owMetrics.isConfirmedBlackInterstitial)

        // 4. Start screen yellow email footer at y=1000..1015 without prompt text MUST be rejected
        val startBand = ByteArray(bandH * spec.strideBytes)
        fillBg(startBand, 24, 65, 130)
        for (y in 1002..1014) {
            for (x in 1050..1410) {
                val idx = (y - spec.yMin) * spec.strideBytes + x * 4
                startBand[idx] = 240.toByte(); startBand[idx + 1] = 185.toByte(); startBand[idx + 2] = 5.toByte()
            }
        }
        val startMetrics = GenshinSkipEngine.evaluateBottomSceneBand(startBand, spec)
        assertFalse(startMetrics.isConfirmedSpecialNarration)
        assertFalse(startMetrics.isConfirmedBlackInterstitial)
    }
}
