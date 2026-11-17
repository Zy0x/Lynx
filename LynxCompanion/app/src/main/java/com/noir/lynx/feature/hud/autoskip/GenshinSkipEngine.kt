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
 * Delegates multi-topology 2D vision primitives to [GenshinDialogueVision].
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
        SPECIAL_NARRATION,
        WHITE_LOADING,
        OPEN_WORLD,
        START_SCREEN;

        val isSkippableDialogue: Boolean
            get() = this == STANDARD_DIALOGUE || this == BLACK_INTERSTITIAL || this == SPECIAL_NARRATION
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
        val choice4: TapTarget = bottomChoice,
        val safeAdvance: TapTarget = advance
    )

    /**
     * Resolves adaptive landscape touch coordinates regardless of physical screen orientation or aspect ratio.
     * In landscape gaming, physicalWidth is always the larger dimension (e.g. 2460), physicalHeight is smaller (e.g. 1080).
     */
    fun resolveCoordinates(screenWidth: Int, screenHeight: Int): DialogueCoordinates {
        val width = max(screenWidth, screenHeight).coerceAtLeast(1280)
        val height = min(screenWidth, screenHeight).coerceAtLeast(720)

        // 1. Legacy typewriter fast-forward zone & choice-free safe advance quadrant (center-left)
        val advanceX = (width * 0.68f).toInt()
        val advanceY = (height * 0.60f).toInt()
        val safeAdvanceX = (width * 0.34f).toInt()
        val safeAdvanceY = (height * 0.72f).toInt()

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
            choice4 = TapTarget(choiceX, c4Y),
            safeAdvance = TapTarget(safeAdvanceX, safeAdvanceY)
        )
    }

    data class OverlayRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        fun containsPadded(x: Int, y: Int, padPx: Int): Boolean {
            return x in (left - padPx)..(right + padPx) &&
                y in (top - padPx)..(bottom + padPx)
        }
    }

    /**
     * Dynamically adapts any synthetic dialogue tap coordinate so it NEVER lands inside a visible
     * movable overlay (Auto-Skip shortcut pill or Sidebar), regardless of where the user drags it.
     */
    fun resolveTapAvoidingOverlays(
        target: TapTarget,
        excludedRects: List<OverlayRect>,
        screenWidth: Int,
        screenHeight: Int,
        paddingPx: Int = 28
    ): TapTarget {
        val w = max(screenWidth, screenHeight).coerceAtLeast(1280)
        val h = min(screenWidth, screenHeight).coerceAtLeast(720)
        val clamped = TapTarget(target.x.coerceIn(16, w - 16), target.y.coerceIn(16, h - 16))
        if (excludedRects.isEmpty() || excludedRects.none { it.containsPadded(clamped.x, clamped.y, paddingPx) }) {
            return clamped
        }
        val step = max(24, (h * 44) / 1080)
        val marginX = max(24, (w * 3) / 100)
        val marginY = max(24, (h * 6) / 100)
        for (ring in 1..6) {
            val delta = step * ring
            val candidates = listOf(
                TapTarget(clamped.x - delta, clamped.y),
                TapTarget(clamped.x + delta, clamped.y),
                TapTarget(clamped.x, clamped.y - delta),
                TapTarget(clamped.x, clamped.y + delta),
                TapTarget(clamped.x - delta, clamped.y - delta),
                TapTarget(clamped.x - delta, clamped.y + delta),
                TapTarget(clamped.x + delta, clamped.y - delta),
                TapTarget(clamped.x + delta, clamped.y + delta)
            )
            for (cand in candidates) {
                if (cand.x in marginX..(w - marginX) &&
                    cand.y in marginY..(h - marginY) &&
                    excludedRects.none { it.containsPadded(cand.x, cand.y, paddingPx) }
                ) {
                    return cand
                }
            }
        }
        return clamped
    }

    data class ScreencapHeader(
        val width: Int,
        val height: Int,
        val pixelFormat: Int
    ) {
        val isBgra: Boolean get() = pixelFormat == 5
        val isLandscape: Boolean get() = width >= height
    }

    data class DialogueHeaderBandSpec(
        val frameWidth: Int,
        val frameHeight: Int,
        val xMin: Int,
        val xMax: Int,
        val yMin: Int,
        val yMax: Int,
        val bandStartByte: Long,
        val bandEndByte: Long
    ) {
        val headerStrideBytes: Int get() = frameWidth * 4
        val bandBytes: Int get() = (bandEndByte - bandStartByte).toInt().coerceAtLeast(0)
    }

    data class DialogueHeaderMetrics(
        val discCount: Int,
        val alignedPairCount: Int,
        val peakDiscCreamMatches: Int,
        val blackRatio: Float,
        val whiteRatio: Float,
        val splitPairCount: Int = 0,
        val singleAnchoredConfirmed: Boolean = false
    ) {
        val isConfirmedDialogue: Boolean
            get() = alignedPairCount >= 1 || splitPairCount >= 1 || singleAnchoredConfirmed

        val needsOpenWorldHpCheck: Boolean
            get() = alignedPairCount == 0 && (splitPairCount >= 1 || singleAnchoredConfirmed)
    }

    data class BottomSceneBandSpec(
        val frameWidth: Int,
        val frameHeight: Int,
        val yMin: Int,
        val yMax: Int,
        val bandStartByte: Long,
        val bandEndByte: Long
    ) {
        val strideBytes: Int get() = frameWidth * 4
        val bandBytes: Int get() = (bandEndByte - bandStartByte).toInt().coerceAtLeast(0)
    }

    data class BottomSceneMetrics(
        val hasOpenWorldHpBar: Boolean,
        val hpBarPixels: Int,
        val maxHpRun: Int,
        val hasPromptText: Boolean,
        val promptGoldPixels: Int,
        val promptTextRows: Int,
        val promptTextEdges: Int,
        val hasCenteredDiamond: Boolean,
        val diamondGoldPixels: Int,
        val diamondRows: Int,
        val marginBlackRatio: Float
    ) {
        val isConfirmedBlackInterstitial: Boolean
            get() = !hasOpenWorldHpBar &&
                marginBlackRatio >= 0.80f &&
                (hasPromptText || (hasCenteredDiamond && promptGoldPixels >= 30))

        val isConfirmedSpecialNarration: Boolean
            get() = !hasOpenWorldHpBar && hasPromptText && hasCenteredDiamond
    }

    data class PromptScanMetrics(
        val goldMatches: Int,
        val marginBlackRatio: Float,
        val isConfirmedNarration: Boolean
    )

    fun isDialogueColor(r: Int, g: Int, b: Int): Boolean = GenshinDialogueVision.isDialogueColor(r, g, b)
    fun isDialogueGlyphDark(r: Int, g: Int, b: Int): Boolean = GenshinDialogueVision.isDialogueGlyphDark(r, g, b)
    fun isPromptGoldColor(r: Int, g: Int, b: Int): Boolean = GenshinDialogueVision.isPromptGoldColor(r, g, b)

    fun parseScreencapHeader(header: ByteArray): ScreencapHeader? =
        GenshinDialogueVision.parseScreencapHeader(header)

    fun calculateDialogueHeaderBand(frameWidth: Int, frameHeight: Int): DialogueHeaderBandSpec =
        GenshinDialogueVision.calculateDialogueHeaderBand(frameWidth, frameHeight)

    fun evaluateDialogueHeaderBand(
        bandBytes: ByteArray,
        spec: DialogueHeaderBandSpec,
        isBgra: Boolean = false
    ): DialogueHeaderMetrics = GenshinDialogueVision.evaluateDialogueHeaderBand(bandBytes, spec, isBgra)

    fun calculateBottomSceneBand(frameWidth: Int, frameHeight: Int): BottomSceneBandSpec =
        GenshinBottomSceneVision.calculateBottomSceneBand(frameWidth, frameHeight)

    fun evaluateBottomSceneBand(
        bandBytes: ByteArray,
        spec: BottomSceneBandSpec,
        isBgra: Boolean = false
    ): BottomSceneMetrics = GenshinBottomSceneVision.evaluateBottomSceneBand(bandBytes, spec, isBgra)

    fun calculatePromptScanSlice(screenWidth: Int, screenHeight: Int): Pair<Long, Long> =
        GenshinBottomSceneVision.calculatePromptScanSlice(screenWidth, screenHeight)

    fun evaluatePromptSliceWithMargins(
        slice: ByteArray,
        screenWidth: Int,
        screenHeight: Int,
        isBgra: Boolean = false
    ): PromptScanMetrics = GenshinBottomSceneVision.evaluatePromptSliceWithMargins(slice, screenWidth, screenHeight, isBgra)

    fun containsDialogueColor(hexString: String): Boolean = countDialogueMatches(hexString) > 0

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
            if (isDialogueColor(r, g, b)) count++
            i += 8
        }
        return count
    }

    fun calculateMinDialogueThreshold(screenHeight: Int): Int {
        val height = min(screenHeight, 4320).coerceAtLeast(720).toLong()
        return ((height * 18L) / 1080L).coerceAtLeast(10L).toInt()
    }

    fun calculateDialogueScanSlice(screenWidth: Int, screenHeight: Int): Pair<Long, Long> {
        val width = max(screenWidth, screenHeight).coerceAtLeast(1280).toLong()
        val height = min(screenWidth, screenHeight).coerceAtLeast(720).toLong()
        val safeWidth = (height * 16L) / 9L
        val pillarbox = if (width > safeWidth) (width - safeWidth) / 2L else 0L
        val xMin = pillarbox + ((safeWidth * 45L) / 1920L).coerceAtLeast(40L)
        val xMax = pillarbox + ((safeWidth * 280L) / 1920L).coerceAtLeast(250L)
        val y = ((height * 56L) / 1080L).coerceAtLeast(20L)
        val rowStart = 16L + (y * width * 4L)
        val byteStart = rowStart + (xMin * 4L)
        val byteEnd = rowStart + (xMax * 4L)
        return byteEnd to (byteEnd - byteStart)
    }

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
        val declared = sceneClassification?.sceneType?.isSkippableDialogue
            ?: (matchingPixels >= threshold)

        val dir = File("/sdcard/Lynx/scenes").apply { if (!exists()) mkdirs() }
        val targetDir = if (dir.canWrite()) dir else context.getExternalFilesDir("scenes") ?: dir
        if (!targetDir.exists()) targetDir.mkdirs()

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val baseName = "scene_${sdf.format(Date())}"
        val fullFile = File(targetDir, "${baseName}_full.png")
        val jsonFile = File(targetDir, "${baseName}_telemetry.json")

        Shell.cmd("screencap -p '${fullFile.absolutePath}'").exec()

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
