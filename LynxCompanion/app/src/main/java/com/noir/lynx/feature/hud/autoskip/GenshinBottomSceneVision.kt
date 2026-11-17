package com.noir.lynx.feature.hud.autoskip

import kotlin.math.max
import kotlin.math.min

/**
 * Bottom-screen computer vision analyzer for Hoyo RPG scenes (`85%..97.2%` height).
 * Detects Open-World active character HP bars (0-tap protection), Black-Screen Interstitials,
 * and illustrated/3D Special CG Cutscene narration prompts ("Tekan untuk melanjutkan" / "Tap anywhere to continue").
 */
object GenshinBottomSceneVision {

    fun calculateBottomSceneBand(frameWidth: Int, frameHeight: Int): GenshinSkipEngine.BottomSceneBandSpec {
        val w = max(frameWidth, frameHeight).coerceAtLeast(1280)
        val h = min(frameWidth, frameHeight).coerceAtLeast(720)
        val yMin = ((h * 850L) / 1000L).toInt().coerceIn(0, h - 20)
        val yMax = ((h * 972L) / 1000L).toInt().coerceIn(yMin + 10, h - 1)
        val startByte = 16L + yMin.toLong() * w.toLong() * 4L
        val endByte = 16L + (yMax.toLong() + 1L) * w.toLong() * 4L
        return GenshinSkipEngine.BottomSceneBandSpec(w, h, yMin, yMax, startByte, endByte)
    }

    fun evaluateBottomSceneBand(
        bandBytes: ByteArray,
        spec: GenshinSkipEngine.BottomSceneBandSpec,
        isBgra: Boolean = false
    ): GenshinSkipEngine.BottomSceneMetrics {
        val bandH = (spec.yMax - spec.yMin) + 1
        val stride = spec.strideBytes
        if (bandBytes.size < bandH * stride || bandH <= 0) {
            return GenshinSkipEngine.BottomSceneMetrics(false, 0, 0, false, 0, 0, 0, false, 0, 0, 0f)
        }
        val rOff = if (isBgra) 2 else 0
        val gOff = 1
        val bOff = if (isBgra) 0 else 2

        fun idxAt(x: Int, y: Int): Int {
            if (x < 0 || x >= spec.frameWidth || y < spec.yMin || y > spec.yMax) return -1
            val idx = (y - spec.yMin) * stride + x * 4
            return if (idx >= 0 && idx + 3 < bandBytes.size) idx else -1
        }

        fun chR(idx: Int) = bandBytes[idx + rOff].toInt() and 0xFF
        fun chG(idx: Int) = bandBytes[idx + gOff].toInt() and 0xFF
        fun chB(idx: Int) = bandBytes[idx + bOff].toInt() and 0xFF

        val w = spec.frameWidth
        val h = spec.frameHeight
        val uiScaleH = min(h.toFloat(), w.toFloat() * 9f / 16f)
        val s = uiScaleH / 1080f
        val xc = w / 2

        // 1. Open-world HP bar shield (y=90.5%..94.0%, x=42%..58%)
        val hpY1 = ((h * 905L) / 1000L).toInt().coerceIn(spec.yMin, spec.yMax)
        val hpY2 = ((h * 940L) / 1000L).toInt().coerceIn(hpY1, spec.yMax)
        val hpX1 = ((w * 42L) / 100L).toInt().coerceIn(0, w - 1)
        val hpX2 = ((w * 58L) / 100L).toInt().coerceIn(hpX1, w - 1)
        val hpStepY = max(1, s.toInt())
        var hpPixels = 0
        var maxHpRun = 0
        var hy = hpY1
        while (hy <= hpY2) {
            var curRun = 0
            for (hx in hpX1..hpX2) {
                val idx = idxAt(hx, hy)
                if (idx >= 0 && GenshinDialogueVision.isOpenWorldHpGreen(chR(idx), chG(idx), chB(idx))) {
                    hpPixels++
                    curRun++
                    if (curRun > maxHpRun) maxHpRun = curRun
                } else {
                    curRun = 0
                }
            }
            hy += hpStepY
        }
        val hasHpBar = hpPixels >= (260f * s).toInt().coerceAtLeast(120) &&
            maxHpRun >= (40f * s).toInt().coerceAtLeast(24)

        // 2. Multi-row prompt text ("Tekan untuk melanjutkan" / "Tap anywhere to continue")
        val ty1 = ((h * 852L) / 1000L).toInt().coerceIn(spec.yMin, spec.yMax)
        val ty2 = ((h * 908L) / 1000L).toInt().coerceIn(ty1, spec.yMax)
        val halfPw = (260f * s).toInt().coerceAtLeast(160)
        val tx1 = (xc - halfPw).coerceAtLeast(0)
        val tx2 = (xc + halfPw).coerceAtMost(w - 1)
        var txtPixels = 0
        var txtRows = 0
        var txtEdges = 0
        var txtTot = 0
        val minRowGold = max(4, (5f * s).toInt())
        for (ty in ty1..ty2) {
            var rowGold = 0
            var prevHit = false
            for (tx in tx1..tx2) {
                val idx = idxAt(tx, ty)
                if (idx < 0) continue
                val hit = GenshinDialogueVision.isPromptTextGoldStrict(chR(idx), chG(idx), chB(idx))
                txtTot++
                if (hit) {
                    txtPixels++
                    rowGold++
                }
                if (tx > tx1 && hit != prevHit) txtEdges++
                prevHit = hit
            }
            if (rowGold >= minRowGold) txtRows++
        }
        val txtDensity = if (txtTot > 0) txtPixels.toFloat() / txtTot else 0f
        val hasPromptText = txtPixels >= (220f * s).toInt().coerceAtLeast(120) &&
            txtRows >= max(5, (7f * s).toInt()) &&
            txtEdges >= (100f * s).toInt().coerceAtLeast(60) &&
            txtDensity <= 0.42f

        // 3. Isolated centered gold diamond (y=91.5%..96.8%, x=xc +- 28*s)
        val dy1 = ((h * 915L) / 1000L).toInt().coerceIn(spec.yMin, spec.yMax)
        val dy2 = ((h * 968L) / 1000L).toInt().coerceIn(dy1, spec.yMax)
        val halfDw = (28f * s).toInt().coerceAtLeast(16)
        val dx1 = (xc - halfDw).coerceAtLeast(0)
        val dx2 = (xc + halfDw).coerceAtMost(w - 1)
        val fxL1 = (xc - (140f * s).toInt()).coerceAtLeast(0)
        val fxL2 = (xc - (36f * s).toInt()).coerceAtLeast(fxL1)
        val fxR1 = (xc + (36f * s).toInt()).coerceAtMost(w - 1)
        val fxR2 = (xc + (140f * s).toInt()).coerceAtMost(w - 1)
        var diaPixels = 0
        var diaRows = 0
        var flankGold = 0
        var flankTot = 0
        val minDiaRow = max(2, (2f * s).toInt())
        for (dy in dy1..dy2) {
            var rowDia = 0
            for (dx in dx1..dx2) {
                val idx = idxAt(dx, dy)
                if (idx >= 0 && GenshinDialogueVision.isPromptDiamondGold(chR(idx), chG(idx), chB(idx))) {
                    diaPixels++
                    rowDia++
                }
            }
            if (rowDia >= minDiaRow) {
                diaRows++
                var fx = fxL1
                while (fx < fxL2) {
                    val idx = idxAt(fx, dy)
                    if (idx >= 0) {
                        flankTot++
                        if (GenshinDialogueVision.isPromptDiamondGold(chR(idx), chG(idx), chB(idx))) flankGold++
                    }
                    fx += 2
                }
                fx = fxR1
                while (fx < fxR2) {
                    val idx = idxAt(fx, dy)
                    if (idx >= 0) {
                        flankTot++
                        if (GenshinDialogueVision.isPromptDiamondGold(chR(idx), chG(idx), chB(idx))) flankGold++
                    }
                    fx += 2
                }
            }
        }
        val flankRatio = if (flankTot > 0) flankGold.toFloat() / flankTot else 0f
        val hasDiamond = diaPixels >= max(14, (20f * s).toInt()) &&
            diaRows >= max(3, (4f * s).toInt()) &&
            flankRatio < 0.15f

        // 4. Canonical row 944 margin blackness check
        val y944 = ((h * 944L) / 1080L).toInt().coerceIn(spec.yMin, spec.yMax)
        val mx1 = ((w * 10L) / 100L).toInt().coerceIn(0, w - 1)
        val mx2 = ((w * 90L) / 100L).toInt().coerceIn(mx1, w)
        val promptW = ((h * 450) / 1080).coerceAtLeast(300)
        val cs = (w / 2) - (promptW / 2)
        val ce = (w / 2) + (promptW / 2)
        var marginBlack = 0
        var marginTot = 0
        for (mx in mx1 until mx2) {
            if (mx !in cs until ce) {
                val idx = idxAt(mx, y944)
                if (idx >= 0) {
                    marginTot++
                    if (chR(idx) < 25 && chG(idx) < 25 && chB(idx) < 25) marginBlack++
                }
            }
        }
        val marginBlackRatio = if (marginTot > 0) marginBlack.toFloat() / marginTot else 0f

        return GenshinSkipEngine.BottomSceneMetrics(
            hasOpenWorldHpBar = hasHpBar,
            hpBarPixels = hpPixels,
            maxHpRun = maxHpRun,
            hasPromptText = hasPromptText,
            promptGoldPixels = txtPixels,
            promptTextRows = txtRows,
            promptTextEdges = txtEdges,
            hasCenteredDiamond = hasDiamond,
            diamondGoldPixels = diaPixels,
            diamondRows = diaRows,
            marginBlackRatio = marginBlackRatio
        )
    }

    fun calculatePromptScanSlice(screenWidth: Int, screenHeight: Int): Pair<Long, Long> {
        val width = max(screenWidth, screenHeight).coerceAtLeast(1280).toLong()
        val height = min(screenWidth, screenHeight).coerceAtLeast(720).toLong()
        val y = ((height * 944L) / 1080L).coerceIn(0L, height - 1L)
        val xMin = ((width * 10L) / 100L).coerceAtLeast(0L)
        val xMax = ((width * 90L) / 100L).coerceAtMost(width)
        val rowStart = 16L + (y * width * 4L)
        val byteStart = rowStart + (xMin * 4L)
        val byteEnd = rowStart + (xMax * 4L)
        return byteEnd to (byteEnd - byteStart)
    }

    fun evaluatePromptSliceWithMargins(
        slice: ByteArray,
        screenWidth: Int,
        screenHeight: Int,
        isBgra: Boolean = false
    ): GenshinSkipEngine.PromptScanMetrics {
        val width = max(screenWidth, screenHeight).coerceAtLeast(1280)
        val height = min(screenWidth, screenHeight).coerceAtLeast(720)
        val xMin = (width * 0.10f).toInt()
        val promptW = ((height * 450) / 1080).coerceAtLeast(300)
        val centerStart = ((width / 2) - (promptW / 2)) - xMin
        val centerEnd = ((width / 2) + (promptW / 2)) - xMin

        val rOff = if (isBgra) 2 else 0
        val gOff = 1
        val bOff = if (isBgra) 0 else 2

        var goldMatches = 0
        var goldRun = 0
        var maxGoldRun = 0
        var marginBlackCount = 0
        var totalMarginPixels = 0

        var px = 0
        var byteIdx = 0
        while (byteIdx <= slice.size - 4) {
            val r = slice[byteIdx + rOff].toInt() and 0xFF
            val g = slice[byteIdx + gOff].toInt() and 0xFF
            val b = slice[byteIdx + bOff].toInt() and 0xFF
            if (px in centerStart until centerEnd) {
                if (GenshinDialogueVision.isPromptGoldColor(r, g, b)) {
                    goldMatches++
                    goldRun++
                    if (goldRun > maxGoldRun) maxGoldRun = goldRun
                } else {
                    goldRun = 0
                }
            } else {
                goldRun = 0
                totalMarginPixels++
                if (r < 25 && g < 25 && b < 25) marginBlackCount++
            }
            px++
            byteIdx += 4
        }

        val marginBlackRatio = if (totalMarginPixels > 0) marginBlackCount.toFloat() / totalMarginPixels else 0f
        val minGoldRun = ((height * 3) / 1080).coerceAtLeast(2)
        val isConfirmed = goldMatches >= 15 && maxGoldRun >= minGoldRun && marginBlackRatio >= 0.80f
        return GenshinSkipEngine.PromptScanMetrics(goldMatches, marginBlackRatio, isConfirmed)
    }
}
