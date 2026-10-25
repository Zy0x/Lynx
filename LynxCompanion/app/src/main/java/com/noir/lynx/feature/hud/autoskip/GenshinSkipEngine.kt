package com.noir.lynx.feature.hud.autoskip

import kotlin.math.max
import kotlin.math.min

/**
 * Intelligent Target & Coordinate Calculator for Genshin Impact and Hoyo RPGs.
 * Adapts to any ultra-wide screen aspect ratio (16:9, 19.5:9, 20.5:9, 21:9) without hardcoded static resolution bias.
 */
object GenshinSkipEngine {

    val SUPPORTED_PACKAGES = setOf(
        "com.miHoYo.GenshinImpact",       // Genshin Impact (Global)
        "com.miHoYo.Yuanshen",            // Genshin Impact (CN)
        "com.miHoYo.bh3oversea",          // Honkai Impact 3rd
        "com.HoYoverse.hkrpgoversea",     // Honkai: Star Rail
        "com.HoYoverse.Nap",              // Zenless Zone Zero
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
        val topChoice: TapTarget,
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
        val advanceX = (width * 0.72f).toInt()
        val advanceY = (height * 0.60f).toInt()

        // 2. Right-side dialogue choice options (Speech bubble pill column)
        val choiceX = (width * 0.68f).toInt()
        val topChoiceY = (height * 0.67f).toInt()
        val bottomChoiceY = (height * 0.77f).toInt()

        return DialogueCoordinates(
            advance = TapTarget(advanceX, advanceY),
            topChoice = TapTarget(choiceX, topChoiceY),
            bottomChoice = TapTarget(choiceX, bottomChoiceY)
        )
    }
}
