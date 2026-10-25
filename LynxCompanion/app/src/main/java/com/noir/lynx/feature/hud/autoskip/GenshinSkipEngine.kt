package com.noir.lynx.feature.hud.autoskip

import com.topjohnwu.superuser.Shell
import kotlin.math.max
import kotlin.math.min

/**
 * Intelligent Target & Coordinate Calculator for Genshin Impact and Hoyo RPGs.
 * Adapts to any ultra-wide screen aspect ratio (16:9, 19.5:9, 20.5:9, 21:9) without hardcoded static resolution bias.
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
     * Header is 16 bytes (width, height, format, colorspace).
     */
    fun calculateAutoButtonOffset(screenWidth: Int, screenHeight: Int): Long {
        val width = max(screenWidth, screenHeight).coerceAtLeast(1280)
        val height = min(screenWidth, screenHeight).coerceAtLeast(720)
        val x = (width * 0.0305f).toInt()
        val y = (height * 0.0555f).toInt()
        return 16L + (y.toLong() * width.toLong() + x.toLong()) * 4L
    }

    /**
     * Fast sub-second dialogue presence check via screencap pipe.
     * Reads only 16 bytes at the Auto-button pixel offset instead of capturing full framebuffer.
     */
    fun checkDialogueActive(screenWidth: Int, screenHeight: Int): Boolean {
        val offset = calculateAutoButtonOffset(screenWidth, screenHeight)
        val cmd = "screencap | od -j $offset -N 16 -t u1 | head -n 1"
        return try {
            val process = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val reader = process.inputStream.bufferedReader()
            val line = reader.readLine()?.trim() ?: ""
            process.destroy()
            if (line.isEmpty()) return false
            val parts = line.split("\\s+".toRegex())
            if (parts.size < 5) return false

            // Check primary pixel
            val r1 = parts[1].toIntOrNull() ?: return false
            val g1 = parts[2].toIntOrNull() ?: return false
            val b1 = parts[3].toIntOrNull() ?: return false
            if (isDialogueColor(r1, g1, b1)) return true

            // Check adjacent pixel if available
            if (parts.size >= 8) {
                val r2 = parts[5].toIntOrNull() ?: 0
                val g2 = parts[6].toIntOrNull() ?: 0
                val b2 = parts[7].toIntOrNull() ?: 0
                if (isDialogueColor(r2, g2, b2)) return true
            }

            false
        } catch (_: Throwable) {
            false
        }
    }
}
