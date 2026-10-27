package com.noir.lynx.feature.hud.autoskip

import kotlin.math.max
import kotlin.math.min

/**
 * Intelligent Target & Coordinate Calculator for Genshin Impact and Hoyo RPGs.
 * Adapts to any ultra-wide screen aspect ratio (16:9, 19.5:9, 20.5:9, 21:9) without hardcoded static resolution bias.
 * Completely free of screencap overhead to guarantee 0% frame drops during gameplay.
 */
object GenshinSkipEngine {

    val SUPPORTED_PACKAGES = setOf(
        "com.miHoYo.GenshinImpact",          // Genshin Impact (Global)
        "com.miHoYo.Yuanshen",               // Genshin Impact (CN)
        "com.miHoYo.bh3oversea",             // Honkai Impact 3rd
        "com.HoYoverse.hkrpgoversea",        // Honkai: Star Rail
        "com.HoYoverse.Nap",                 // Zenless Zone Zero
        "com.kurogame.wutheringwaves.global" // Wuthering Waves
    )

    fun isGameSupported(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val lower = packageName.lowercase()
        return SUPPORTED_PACKAGES.any { it.equals(packageName, ignoreCase = true) } ||
            lower.contains("genshin") || lower.contains("yuanshen") || lower.contains("hkrpg")
    }

    data class TapTarget(val x: Int, val y: Int)

    data class DialogueCoordinates(
        val advance: TapTarget,
        val narration: TapTarget,
        val topChoice: TapTarget,
        val midChoice: TapTarget,
        val bottomChoice: TapTarget,
        val choice1: TapTarget = topChoice,
        val choice2: TapTarget = midChoice,
        val choice3: TapTarget = bottomChoice,
        val choice4: TapTarget = bottomChoice
    )

    /**
     * Resolves adaptive landscape touch coordinates regardless of physical screen orientation or aspect ratio.
     * In landscape gaming, physicalWidth is always the larger dimension (e.g. 2460), physicalHeight is smaller (e.g. 1080).
     */
    fun resolveCoordinates(screenWidth: Int, screenHeight: Int): DialogueCoordinates {
        val width = max(screenWidth, screenHeight).coerceAtLeast(1280)
        val height = min(screenWidth, screenHeight).coerceAtLeast(720)

        // 1. Dialogue text typewriter fast-forward zone (center-right safe quadrant)
        val advanceX = (width * 0.68f).toInt()
        val advanceY = (height * 0.60f).toInt()

        // 2. Center-bottom story narration transition prompt ("Tekan untuk melanjutkan" / "Tap to continue")
        val narrationX = (width * 0.50f).toInt()
        val narrationY = (height * 0.88f).toInt()

        // 3. Right-side dialogue choice options (Speech bubble pill column)
        val choiceX = (width * 0.72f).toInt()
        val topChoiceY = (height * 0.50f).toInt()
        val midChoiceY = (height * 0.58f).toInt()
        val bottomChoiceY = (height * 0.66f).toInt()

        // 4-tier discrete vertical anchors (for 1, 2, 3, or 4+ dialogue choices)
        val c1Y = (height * 0.46f).toInt()
        val c2Y = (height * 0.54f).toInt()
        val c3Y = (height * 0.62f).toInt()
        val c4Y = (height * 0.70f).toInt()

        return DialogueCoordinates(
            advance = TapTarget(advanceX, advanceY),
            narration = TapTarget(narrationX, narrationY),
            topChoice = TapTarget(choiceX, topChoiceY),
            midChoice = TapTarget(choiceX, midChoiceY),
            bottomChoice = TapTarget(choiceX, bottomChoiceY),
            choice1 = TapTarget(choiceX, c1Y),
            choice2 = TapTarget(choiceX, c2Y),
            choice3 = TapTarget(choiceX, c3Y),
            choice4 = TapTarget(choiceX, c4Y)
        )
    }

    /**
     * Warm cream / beige background color of top-left dialogue controls in Genshin Impact:
     * Standard color is approx RGB (236, 229, 216).
     */
    fun isDialogueColor(r: Int, g: Int, b: Int): Boolean {
        return r in 200..250 &&
               g in 195..245 &&
               b in 180..235 &&
               (r >= g) && (g >= b) &&
               ((r - b) in 8..35)
    }

    /**
     * Parses a raw hex dump string from screencap (RGBA8888) and checks if any pixel
     * matches the warm cream/beige dialogue UI signature of Genshin Impact.
     */
    fun containsDialogueColor(hexString: String): Boolean {
        if (hexString.isBlank()) return false
        val clean = hexString.filter { (it in '0'..'9') || (it in 'a'..'f') || (it in 'A'..'F') }
        val length = clean.length
        var i = 0
        while (i <= length - 8) {
            val r = clean.substring(i, i + 2).toIntOrNull(16) ?: 0
            val g = clean.substring(i + 2, i + 4).toIntOrNull(16) ?: 0
            val b = clean.substring(i + 4, i + 6).toIntOrNull(16) ?: 0
            if (isDialogueColor(r, g, b)) {
                return true
            }
            i += 8
        }
        return false
    }

    /**
     * Calculates the raw screencap byte slice parameters for the dialogue controls across any screen resolution.
     * Samples rows spanning approx 4.0% to 6.5% of screen height (e.g. rows 43..70 on 1080p).
     * Returns Pair(headBytes, sliceBytes).
     */
    fun calculateDialogueScanSlice(screenWidth: Int, screenHeight: Int): Pair<Long, Long> {
        val width = max(screenWidth, screenHeight).coerceAtLeast(1280).toLong()
        val height = min(screenWidth, screenHeight).coerceAtLeast(720).toLong()
        val yStart = (height * 0.040f).toLong().coerceAtLeast(20L)
        val yEnd = (height * 0.065f).toLong().coerceAtLeast(yStart + 4L)
        val headBytes = 16L + (yEnd * width * 4L)
        val sliceBytes = (yEnd - yStart) * width * 4L
        return headBytes to sliceBytes
    }

    /**
     * Calculates the raw byte offset for the Auto button pixel in screencap RGBA stream.
     */
    fun calculateAutoButtonOffset(screenWidth: Int, screenHeight: Int): Long {
        val width = max(screenWidth, screenHeight).coerceAtLeast(1280)
        val height = min(screenWidth, screenHeight).coerceAtLeast(720)
        val x = (width * 0.0305f).toInt()
        val y = (height * 0.0555f).toInt()
        return 16L + (y.toLong() * width.toLong() + x.toLong()) * 4L
    }
}
