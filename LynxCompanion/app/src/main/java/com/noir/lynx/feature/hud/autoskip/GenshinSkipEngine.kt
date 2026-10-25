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
        val bottomChoice: TapTarget
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

        return DialogueCoordinates(
            advance = TapTarget(advanceX, advanceY),
            narration = TapTarget(narrationX, narrationY),
            topChoice = TapTarget(choiceX, topChoiceY),
            midChoice = TapTarget(choiceX, midChoiceY),
            bottomChoice = TapTarget(choiceX, bottomChoiceY)
        )
    }

    /**
     * Warm cream / beige background color of top-left dialogue controls in Genshin Impact:
     * Standard color is approx RGB (236, 229, 216).
     */
    fun isDialogueColor(r: Int, g: Int, b: Int): Boolean {
        return r in 210..248 &&
               g in 200..242 &&
               b in 188..232 &&
               (r >= g) && (g >= b) &&
               ((r - b) in 8..35)
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
