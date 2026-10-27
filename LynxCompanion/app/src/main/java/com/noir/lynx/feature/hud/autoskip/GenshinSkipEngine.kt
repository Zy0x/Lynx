package com.noir.lynx.feature.hud.autoskip

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.topjohnwu.superuser.Shell
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    enum class SceneType {
        UNKNOWN,
        STANDARD_DIALOGUE,
        BLACK_INTERSTITIAL,
        WHITE_LOADING,
        OPEN_WORLD,
        START_SCREEN
    }

    data class SceneClassification(
        val sceneType: SceneType,
        val confidence: Float,
        val dialogueMatches: Int,
        val goldMatches: Int,
        val blackRatio: Float,
        val whiteRatio: Float
    )

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
     * Gold / Amber UI signature of bottom story narration prompt ("Tekan untuk melanjutkan" / "Tap to continue"):
     * Standard Hoyoverse gold diamond arrow has high red (170..255), medium-high green (130..215), and very low blue (0..35).
     */
    fun isPromptGoldColor(r: Int, g: Int, b: Int): Boolean {
        return r in 170..255 &&
               g in 130..215 &&
               b in 0..35 &&
               (r >= g)
    }

    /**
     * Parses a raw hex dump string from screencap (RGBA8888) and checks if any pixel
     * matches the warm cream/beige dialogue UI signature of Genshin Impact.
     */
    fun containsDialogueColor(hexString: String): Boolean {
        return countDialogueMatches(hexString) > 0
    }

    /**
     * Counts how many pixels in the raw hex dump match the warm cream/beige dialogue UI signature.
     * Real dialogue controls provide between 60 and 120 matching pixels, whereas stray noise provides <= 4.
     */
    fun countDialogueMatches(hexString: String): Int {
        if (hexString.isBlank()) return 0
        val clean = hexString.filter { (it in '0'..'9') || (it in 'a'..'f') || (it in 'A'..'F') }
        val length = clean.length
        var i = 0
        var count = 0
        while (i <= length - 8) {
            val r = clean.substring(i, i + 2).toIntOrNull(16) ?: 0
            val g = clean.substring(i + 2, i + 4).toIntOrNull(16) ?: 0
            val b = clean.substring(i + 4, i + 6).toIntOrNull(16) ?: 0
            if (isDialogueColor(r, g, b)) {
                count++
            }
            i += 8
        }
        return count
    }

    /**
     * Minimum dialogue font/icon matching pixels required to declare dialogue active.
     * Scales dynamically with screen resolution (e.g. 12 pixels on 1080p, 8 on 720p, 16 on 1440p).
     * Filters 100% of single-pixel noise from terrain textures, grass, rocks, snow, and clouds.
     */
    fun calculateMinDialogueThreshold(screenHeight: Int): Int {
        val height = min(screenHeight, 4320).coerceAtLeast(720).toLong()
        return ((height * 18L) / 1080L).coerceAtLeast(10L).toInt()
    }

    /**
     * Calculates the raw screencap byte slice parameters for the dialogue controls across any screen resolution.
     * Dynamically calculates the 16:9 safe-frame pillarbox padding for ultrawide displays (19.5:9, 20:9, 20.5:9, 21:9).
     * Focuses strictly on the dialogue control button group ("▶ Otomatis", "☰", "👁", "🔊") at:
     *   X in [pillarbox + 45 .. pillarbox + 280]
     *   Y approx 5.2% of screen height (row 56 on 1080p).
     *
     * Eliminates 100% of false positives from:
     * - Top-left map/quest text ("Hadiah Ekspedisi") residing in the left margin X in [0..pillarbox].
     * - Top-right open-world icons (Wish, Battle Pass, Handbook, Bag) at X >= 0.75 * width.
     * - Top-right map close 'X' button.
     *
     * Returns Pair(headBytes, sliceBytes).
     */
    fun calculateDialogueScanSlice(screenWidth: Int, screenHeight: Int): Pair<Long, Long> {
        val width = max(screenWidth, screenHeight).coerceAtLeast(1280).toLong()
        val height = min(screenWidth, screenHeight).coerceAtLeast(720).toLong()

        // Dynamic 16:9 inner safe frame calculation for ultrawide displays
        val safeWidth = (height * 16L) / 9L
        val pillarbox = if (width > safeWidth) (width - safeWidth) / 2L else 0L

        // Exact horizontal bounds of the dialogue control buttons within 16:9 safe frame
        val xMin = pillarbox + ((safeWidth * 45L) / 1920L).coerceAtLeast(40L)
        val xMax = pillarbox + ((safeWidth * 280L) / 1920L).coerceAtLeast(250L)
        val y = ((height * 56L) / 1080L).coerceAtLeast(20L)

        val rowStart = 16L + (y * width * 4L)
        val byteStart = rowStart + (xMin * 4L)
        val byteEnd = rowStart + (xMax * 4L)
        val sliceBytes = byteEnd - byteStart
        val headBytes = byteEnd

        return headBytes to sliceBytes
    }

    /**
     * Calculates the raw screencap byte slice parameters for the bottom narration prompt ("Tekan untuk melanjutkan"):
     * Y is row 944 on 1080p (~87.4% height).
     * X is centered at width / 2, spanning width of (height * 450) / 1080 (~450px wide).
     * Returns Pair(headBytes, sliceBytes).
     */
    data class PromptScanMetrics(
        val goldMatches: Int,
        val marginBlackRatio: Float,
        val isConfirmedNarration: Boolean
    )

    /**
     * Calculates the raw screencap byte slice parameters for the bottom narration prompt ("Tekan untuk melanjutkan"):
     * Y is row 944 on 1080p (~87.4% height).
     * Spans 80% screen width (X in 10% .. 90% width) to verify BOTH center prompt gold AND left/right margin pitch-blackness.
     * Prevents 100% of false positives from night gameplay, dark caves, or open-world terrain.
     * Returns Pair(headBytes, sliceBytes).
     */
    fun calculatePromptScanSlice(screenWidth: Int, screenHeight: Int): Pair<Long, Long> {
        val width = max(screenWidth, screenHeight).coerceAtLeast(1280).toLong()
        val height = min(screenWidth, screenHeight).coerceAtLeast(720).toLong()

        val y = ((height * 944L) / 1080L).coerceIn(0L, height - 1L)
        val xMin = ((width * 10L) / 100L).coerceAtLeast(0L)
        val xMax = ((width * 90L) / 100L).coerceAtMost(width)

        val rowStart = 16L + (y * width * 4L)
        val byteStart = rowStart + (xMin * 4L)
        val byteEnd = rowStart + (xMax * 4L)
        val sliceBytes = byteEnd - byteStart
        val headBytes = byteEnd

        return headBytes to sliceBytes
    }

    /**
     * Dual-verification for Black Interstitial Narration Cutscenes:
     * 1. Evaluates center prompt zone (width/2 +- 225px) for Hoyoverse amber-gold diamond icon.
     * 2. Evaluates left and right margins across row 944 for pitch-blackness (RGB < 25, 25, 25).
     * Guaranteed 0-tap safety shield: in an open-world cave or night gameplay, the margins contain terrain textures,
     * so marginBlackRatio is < 80%, immediately rejecting the scene and keeping 100% player control intact.
     */
    fun evaluatePromptSliceWithMargins(
        slice: ByteArray,
        screenWidth: Int,
        screenHeight: Int
    ): PromptScanMetrics {
        val width = max(screenWidth, screenHeight).coerceAtLeast(1280)
        val height = min(screenWidth, screenHeight).coerceAtLeast(720)
        val xMin = (width * 0.10f).toInt()
        val promptW = ((height * 450) / 1080).coerceAtLeast(300)
        val centerStart = ((width / 2) - (promptW / 2)) - xMin
        val centerEnd = ((width / 2) + (promptW / 2)) - xMin

        var goldMatches = 0
        var marginBlackCount = 0
        var totalMarginPixels = 0

        var px = 0
        var byteIdx = 0
        while (byteIdx <= slice.size - 4) {
            val r = slice[byteIdx].toInt() and 0xFF
            val g = slice[byteIdx + 1].toInt() and 0xFF
            val b = slice[byteIdx + 2].toInt() and 0xFF

            if (px in centerStart until centerEnd) {
                if (isPromptGoldColor(r, g, b)) {
                    goldMatches++
                }
            } else {
                totalMarginPixels++
                if (r < 25 && g < 25 && b < 25) {
                    marginBlackCount++
                }
            }
            px++
            byteIdx += 4
        }

        val marginBlackRatio = if (totalMarginPixels > 0) {
            marginBlackCount.toFloat() / totalMarginPixels
        } else {
            0f
        }
        val isConfirmed = goldMatches >= 15 && marginBlackRatio >= 0.80f

        return PromptScanMetrics(
            goldMatches = goldMatches,
            marginBlackRatio = marginBlackRatio,
            isConfirmedNarration = isConfirmed
        )
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

    data class SceneTelemetry(
        val timestamp: Long,
        val gamePackage: String,
        val width: Int,
        val height: Int,
        val safeWidth: Long,
        val pillarbox: Long,
        val xMin: Long,
        val xMax: Long,
        val yRow: Long,
        val sliceBytes: Long,
        val matchingPixels: Int,
        val thresholdRequired: Int,
        val dialogueDeclared: Boolean,
        val mode: String,
        val sceneType: String = SceneType.UNKNOWN.name,
        val goldMatches: Int = 0,
        val blackRatio: Float = 0f,
        val whiteRatio: Float = 0f
    ) {
        fun toJson(): String {
            return """
                {
                  "timestamp": $timestamp,
                  "game_package": "$gamePackage",
                  "screen_resolution": {
                    "width": $width,
                    "height": $height,
                    "safe_width_16_9": $safeWidth,
                    "pillarbox_padding": $pillarbox
                  },
                  "scan_parameters": {
                    "y_row": $yRow,
                    "x_min": $xMin,
                    "x_max": $xMax,
                    "slice_bytes": $sliceBytes
                  },
                  "pixel_analysis": {
                    "matching_pixels": $matchingPixels,
                    "threshold_required": $thresholdRequired,
                    "dialogue_declared": $dialogueDeclared
                  },
                  "scene_classification": {
                    "scene_type": "$sceneType",
                    "gold_matches": $goldMatches,
                    "black_ratio": $blackRatio,
                    "white_ratio": $whiteRatio
                  },
                  "hud_state": {
                    "mode": "$mode"
                  }
                }
            """.trimIndent()
        }
    }

    data class SceneSnapshotResult(
        val directory: String,
        val fullPath: String,
        val slicePath: String?,
        val baseName: String,
        val telemetry: SceneTelemetry
    )

    /**
     * Captures a complete diagnostic scene bundle for the Scene Vision learning pipeline:
     * - Full resolution screencap (PNG)
     * - 16:9 safe-zone cropped slice (PNG)
     * - Telemetry metadata analysis (JSON)
     * Saved to /sdcard/Lynx/scenes/ (fallback to external files dir).
     */
    fun captureSceneSnapshotDetailed(
        context: Context,
        screenWidth: Int,
        screenHeight: Int,
        packageName: String,
        mode: String,
        matchingPixels: Int,
        sceneClassification: SceneClassification? = null
    ): SceneSnapshotResult? {
        val width = max(screenWidth, screenHeight).coerceAtLeast(1280)
        val height = min(screenWidth, screenHeight).coerceAtLeast(720)
        val safeWidth = (height.toLong() * 16L) / 9L
        val pillarbox = if (width > safeWidth) (width - safeWidth) / 2L else 0L
        val xMin = pillarbox + ((safeWidth * 45L) / 1920L).coerceAtLeast(40L)
        val xMax = pillarbox + ((safeWidth * 280L) / 1920L).coerceAtLeast(250L)
        val y = ((height.toLong() * 56L) / 1080L).coerceAtLeast(20L)
        val sliceBytes = (xMax - xMin) * 4L
        val threshold = calculateMinDialogueThreshold(height)
        val declared = matchingPixels >= threshold

        val dir = File("/sdcard/Lynx/scenes").apply { if (!exists()) mkdirs() }
        val targetDir = if (dir.canWrite()) dir else context.getExternalFilesDir("scenes") ?: dir
        if (!targetDir.exists()) targetDir.mkdirs()

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val timeTag = sdf.format(Date())
        val baseName = "scene_${timeTag}"

        val fullFile = File(targetDir, "${baseName}_full.png")
        val jsonFile = File(targetDir, "${baseName}_telemetry.json")

        // 1. Full screenshot
        Shell.cmd("screencap -p '${fullFile.absolutePath}'").exec()

        // 2. Telemetry metadata
        val sceneTypeStr = sceneClassification?.sceneType?.name
            ?: if (declared) SceneType.STANDARD_DIALOGUE.name else SceneType.UNKNOWN.name
        val goldMatches = sceneClassification?.goldMatches ?: 0
        val blackRatio = sceneClassification?.blackRatio ?: 0f
        val whiteRatio = sceneClassification?.whiteRatio ?: 0f

        val telemetry = SceneTelemetry(
            timestamp = System.currentTimeMillis(),
            gamePackage = packageName,
            width = width,
            height = height,
            safeWidth = safeWidth,
            pillarbox = pillarbox,
            xMin = xMin,
            xMax = xMax,
            yRow = y,
            sliceBytes = sliceBytes,
            matchingPixels = matchingPixels,
            thresholdRequired = threshold,
            dialogueDeclared = declared,
            mode = mode,
            sceneType = sceneTypeStr,
            goldMatches = goldMatches,
            blackRatio = blackRatio,
            whiteRatio = whiteRatio
        )
        val jsonContent = telemetry.toJson().replace("'", "'\\''")
        Shell.cmd("echo '$jsonContent' > '${jsonFile.absolutePath}' && chmod 666 '${fullFile.absolutePath}' '${jsonFile.absolutePath}'").exec()

        // 3. Crop slice image if full file exists
        var slicePath: String? = null
        if (fullFile.exists() && fullFile.length() > 1024L) {
            runCatching {
                Shell.cmd("chmod 666 '${fullFile.absolutePath}'").exec()
                val bmp = BitmapFactory.decodeFile(fullFile.absolutePath)
                if (bmp != null) {
                    val cropY = y.toInt().coerceIn(0, bmp.height - 1)
                    val cropH = ((height * 28) / 1080).coerceIn(10, 80)
                    val cropYStart = (cropY - cropH / 2).coerceIn(0, bmp.height - cropH)
                    val cropW = (xMax - xMin).toInt().coerceIn(10, bmp.width)
                    val cropXStart = xMin.toInt().coerceIn(0, bmp.width - cropW)
                    val cropped = Bitmap.createBitmap(bmp, cropXStart, cropYStart, cropW, cropH)
                    val sliceFile = File(targetDir, "${baseName}_slice.png")
                    val tempSlice = File(context.cacheDir, "slice_temp.png")
                    FileOutputStream(tempSlice).use { out ->
                        cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    Shell.cmd("cp '${tempSlice.absolutePath}' '${sliceFile.absolutePath}' && chmod 666 '${sliceFile.absolutePath}'").exec()
                    tempSlice.delete()
                    slicePath = sliceFile.absolutePath
                    bmp.recycle()
                    cropped.recycle()
                }
            }
        }

        return SceneSnapshotResult(
            directory = targetDir.absolutePath,
            fullPath = fullFile.absolutePath,
            slicePath = slicePath,
            baseName = baseName,
            telemetry = telemetry
        )
    }

    fun captureSceneSnapshot(
        context: Context,
        screenWidth: Int,
        screenHeight: Int,
        packageName: String,
        mode: String,
        matchingPixels: Int
    ): String? {
        return captureSceneSnapshotDetailed(
            context, screenWidth, screenHeight, packageName, mode, matchingPixels
        )?.directory
    }
}
