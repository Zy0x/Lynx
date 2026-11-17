package com.noir.lynx.feature.hud.autoskip

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Upper-left header 2D computer vision analyzer and shared color predicates for Genshin Impact & Hoyo RPGs.
 * Evaluates bright circular dialogue discs, dimmed button silhouettes, and autoplay label strokes.
 * Bottom-screen narration and Open-World HP bar shields live in [GenshinBottomSceneVision].
 */
object GenshinDialogueVision {

    /** Warm cream / beige background color of Genshin Impact dialogue control discs. */
    fun isDialogueColor(r: Int, g: Int, b: Int): Boolean {
        return r in 196..252 &&
            g in 190..248 &&
            b in 175..238 &&
            (r >= g) && (g >= b) &&
            ((r - b) in 6..40)
    }

    /** Low-saturation charcoal / dark slate ink color used for icons inside dialogue discs. */
    fun isDialogueGlyphDark(r: Int, g: Int, b: Int): Boolean {
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        return mx <= 130 && (mx - mn) <= 55
    }

    /** Off-white / cream text stroke color for the "Auto" / "Otomatis" label beside [▶]. */
    fun isAutoplayLabelBright(r: Int, g: Int, b: Int): Boolean {
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        return r >= 195 && g >= 190 && b >= 180 && (mx - mn) <= 45
    }

    /** Standard Hoyoverse amber-gold narration diamond / prompt signature. */
    fun isPromptGoldColor(r: Int, g: Int, b: Int): Boolean {
        return r in 170..255 &&
            g in 130..215 &&
            b in 0..35 &&
            (r >= g)
    }

    /** Anti-aliased CG / interstitial amber-gold prompt text signature. */
    fun isPromptTextGoldStrict(r: Int, g: Int, b: Int): Boolean {
        return r in 175..255 &&
            g in 130..220 &&
            b in 0..45 &&
            (r >= g) &&
            ((r - b) >= 140)
    }

    /** Bottom centered diamond signature, tolerating bloom on luminous CG scenes. */
    fun isPromptDiamondGold(r: Int, g: Int, b: Int): Boolean {
        return r in 175..255 &&
            g in 130..235 &&
            b in 0..105 &&
            (r >= g) &&
            ((r - b) >= 120)
    }

    /** Active open-world player HP bar green signature. */
    fun isOpenWorldHpGreen(r: Int, g: Int, b: Int): Boolean {
        return g in 145..245 &&
            r in 85..215 &&
            b <= 95 &&
            (g - r) >= 15 &&
            (g - b) >= 70
    }

    fun parseScreencapHeader(header: ByteArray): GenshinSkipEngine.ScreencapHeader? {
        if (header.size < 12) return null
        val w = readLeInt(header, 0)
        val h = readLeInt(header, 4)
        val fmt = readLeInt(header, 8)
        if (w !in 320..8192 || h !in 320..8192 || fmt !in 1..64) return null
        return GenshinSkipEngine.ScreencapHeader(w, h, fmt)
    }

    private fun readLeInt(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8) or
            ((buf[offset + 2].toInt() and 0xFF) shl 16) or
            ((buf[offset + 3].toInt() and 0xFF) shl 24)
    }

    fun calculateDialogueHeaderBand(frameWidth: Int, frameHeight: Int): GenshinSkipEngine.DialogueHeaderBandSpec {
        val w = max(frameWidth, frameHeight).coerceAtLeast(1280)
        val h = min(frameWidth, frameHeight).coerceAtLeast(720)
        val uiScaleH = min(h.toFloat(), w.toFloat() * 9f / 16f)
        val yMin = ((uiScaleH * 22f) / 1080f).toInt().coerceIn(6, h - 4)
        val yMax = ((uiScaleH * 94f) / 1080f).toInt().coerceIn(yMin + 12, h - 1)
        val xMin = ((w * 15L) / 1000L).toInt().coerceIn(8, w - 2)
        val xMax = ((w * 400L) / 1000L).toInt().coerceIn(xMin + 100, w)
        val startByte = 16L + yMin.toLong() * w.toLong() * 4L
        val endByte = 16L + (yMax.toLong() + 1L) * w.toLong() * 4L
        return GenshinSkipEngine.DialogueHeaderBandSpec(w, h, xMin, xMax, yMin, yMax, startByte, endByte)
    }

    private data class DiscCandidate(val score: Int, val cx: Int, val cy: Int, val creamHits: Int)

    fun evaluateDialogueHeaderBand(
        bandBytes: ByteArray,
        spec: GenshinSkipEngine.DialogueHeaderBandSpec,
        isBgra: Boolean = false
    ): GenshinSkipEngine.DialogueHeaderMetrics {
        val bandH = (spec.yMax - spec.yMin) + 1
        val rowStride = spec.headerStrideBytes
        if (bandBytes.size < bandH * rowStride || bandH <= 0) {
            return GenshinSkipEngine.DialogueHeaderMetrics(0, 0, 0, 0f, 0f)
        }

        val rOff = if (isBgra) 2 else 0
        val gOff = 1
        val bOff = if (isBgra) 0 else 2

        fun idxAt(x: Int, y: Int): Int {
            if (x < 0 || x >= spec.frameWidth || y < spec.yMin || y > spec.yMax) return -1
            val idx = (y - spec.yMin) * rowStride + x * 4
            return if (idx >= 0 && idx + 3 < bandBytes.size) idx else -1
        }

        fun chR(idx: Int) = bandBytes[idx + rOff].toInt() and 0xFF
        fun chG(idx: Int) = bandBytes[idx + gOff].toInt() and 0xFF
        fun chB(idx: Int) = bandBytes[idx + bOff].toInt() and 0xFF
        fun lumaAt(idx: Int): Float = 0.299f * chR(idx) + 0.587f * chG(idx) + 0.114f * chB(idx)

        val probeY = ((spec.yMin + spec.yMax) / 2).coerceIn(spec.yMin, spec.yMax)
        var blackCount = 0
        var whiteCount = 0
        var rawCreamCount = 0
        val sampleStepX = max(1, spec.frameWidth / 640)
        var totalSampled = 0
        var sx = spec.xMin
        while (sx < spec.xMax) {
            val idx = idxAt(sx, probeY)
            if (idx >= 0) {
                val r = chR(idx); val g = chG(idx); val b = chB(idx)
                totalSampled++
                if (r < 30 && g < 30 && b < 30) blackCount++
                if (r > 230 && g > 230 && b > 230) whiteCount++
                if (isDialogueColor(r, g, b)) rawCreamCount++
            }
            sx += sampleStepX
        }
        val blackRatio = if (totalSampled > 0) blackCount.toFloat() / totalSampled else 0f
        val whiteRatio = if (totalSampled > 0) whiteCount.toFloat() / totalSampled else 0f

        val uiScaleH = min(spec.frameHeight.toFloat(), spec.frameWidth.toFloat() * 9f / 16f)
        val scale = uiScaleH / 1080f
        val diameter = max(22f, (uiScaleH * 46f) / 1080f)
        val radius = diameter * 0.5f
        val stepX = max(1, (diameter / 16f).toInt())
        val stepY = max(1, (diameter / 14f).toInt())
        val cyMin = ((uiScaleH * 38f) / 1080f).toInt().coerceIn(spec.yMin + 2, spec.yMax - 2)
        val cyMax = ((uiScaleH * 74f) / 1080f).toInt().coerceIn(cyMin, spec.yMax - 2)
        val cxMin = (spec.xMin + radius.toInt()).coerceAtLeast(16)
        val cxMax = (spec.xMax - radius.toInt()).coerceAtMost(spec.frameWidth - 16)

        val capXOffsets = floatArrayOf(-0.35f, -0.15f, 0f, 0.15f, 0.35f)
        val capYOffsets = floatArrayOf(-0.75f, -0.56f, 0.56f, 0.75f)
        val rimXOffsets = floatArrayOf(-0.78f, -0.58f, 0.58f, 0.78f)
        val rimYOffsets = floatArrayOf(-0.30f, 0f, 0.30f)
        val coreOffsets = floatArrayOf(-0.38f, -0.19f, 0f, 0.19f, 0.38f)

        val candidates = ArrayList<DiscCandidate>()
        var cx = cxMin
        while (cx <= cxMax) {
            var bestForX: DiscCandidate? = null
            var cy = cyMin
            while (cy <= cyMax) {
                var capHits = 0
                var capTot = 0
                for (dxF in capXOffsets) {
                    val px = (cx + dxF * radius).toInt()
                    for (dyF in capYOffsets) {
                        val idx = idxAt(px, (cy + dyF * radius).toInt())
                        if (idx >= 0) {
                            capTot++
                            if (isDialogueColor(chR(idx), chG(idx), chB(idx))) capHits++
                        }
                    }
                }
                if (capTot == 0 || capHits.toFloat() / capTot < 0.65f) {
                    cy += stepY
                    continue
                }

                var ringHits = 0
                var ringTot = 0
                for (dxF in rimXOffsets) {
                    val px = (cx + dxF * radius).toInt()
                    for (dyF in rimYOffsets) {
                        val idx = idxAt(px, (cy + dyF * radius).toInt())
                        if (idx >= 0) {
                            ringTot++
                            if (isDialogueColor(chR(idx), chG(idx), chB(idx))) ringHits++
                        }
                    }
                }
                if (ringTot == 0 || ringHits.toFloat() / ringTot < 0.55f) {
                    cy += stepY
                    continue
                }

                var cornerCream = 0
                for (dxF in floatArrayOf(-1.05f, 1.05f)) {
                    for (dyF in floatArrayOf(-1.05f, 1.05f)) {
                        val idx = idxAt((cx + dxF * radius).toInt(), (cy + dyF * radius).toInt())
                        if (idx >= 0 && isDialogueColor(chR(idx), chG(idx), chB(idx))) cornerCream++
                    }
                }
                if (cornerCream > 1) {
                    cy += stepY
                    continue
                }

                var coreDark = 0
                var coreCream = 0
                var coreTot = 0
                for (dyF in coreOffsets) {
                    val py = (cy + dyF * radius).toInt()
                    for (dxF in coreOffsets) {
                        val idx = idxAt((cx + dxF * radius).toInt(), py)
                        if (idx >= 0) {
                            coreTot++
                            val r = chR(idx); val g = chG(idx); val b = chB(idx)
                            if (isDialogueGlyphDark(r, g, b)) coreDark++
                            else if (isDialogueColor(r, g, b)) coreCream++
                        }
                    }
                }
                if (coreTot > 0) {
                    val darkFrac = coreDark.toFloat() / coreTot
                    val creamFrac = coreCream.toFloat() / coreTot
                    if (darkFrac >= 0.08f && creamFrac >= 0.16f && (darkFrac + creamFrac) >= 0.50f) {
                        val score = capHits + ringHits + coreDark
                        val totalCream = capHits + ringHits + coreCream
                        if (bestForX == null || score > bestForX.score) {
                            bestForX = DiscCandidate(score, cx, cy, totalCream)
                        }
                    }
                }
                cy += stepY
            }
            if (bestForX != null) candidates.add(bestForX)
            cx += stepX
        }

        val discs = ArrayList<DiscCandidate>()
        val nmsDist = diameter * 0.85f
        for (cand in candidates) {
            val prev = discs.lastOrNull()
            if (prev != null && abs(cand.cx - prev.cx) < nmsDist) {
                if (cand.score > prev.score) discs[discs.lastIndex] = cand
            } else {
                discs.add(cand)
            }
        }

        fun hasCleanCreamGap(c1: DiscCandidate, c2: DiscCandidate): Boolean {
            val gapX1 = (c1.cx + radius * 1.15f).toInt()
            val gapX2 = (c2.cx - radius * 1.15f).toInt()
            val midY = (c1.cy + c2.cy) / 2
            val yHalf = max(2, (radius * 0.45f).toInt())
            val gapStep = max(1, stepX)
            var gapCream = 0
            var gapTot = 0
            var gy = (midY - yHalf).coerceAtLeast(spec.yMin)
            val gyEnd = (midY + yHalf).coerceAtMost(spec.yMax)
            while (gy <= gyEnd) {
                var gx = gapX1
                while (gx <= gapX2) {
                    val idx = idxAt(gx, gy)
                    if (idx >= 0) {
                        gapTot++
                        if (isDialogueColor(chR(idx), chG(idx), chB(idx))) gapCream++
                    }
                    gx += gapStep
                }
                gy += max(1, stepY)
            }
            return gapTot > 0 && (gapCream.toFloat() / gapTot) < 0.25f
        }

        fun hasAutoLabelStrokes(leftDisc: DiscCandidate, rightBoundX: Float): Boolean {
            val x1 = (leftDisc.cx + 0.70f * diameter).toInt().coerceAtLeast(0)
            val x2 = min(rightBoundX, leftDisc.cx + 4.8f * diameter).toInt().coerceAtMost(spec.frameWidth - 1)
            val y1 = (leftDisc.cy - 0.34f * diameter).toInt().coerceAtLeast(spec.yMin)
            val y2 = (leftDisc.cy + 0.34f * diameter).toInt().coerceAtMost(spec.yMax)
            if (x2 - x1 < (diameter * 0.8f).toInt() || y2 <= y1) return false
            var bright = 0
            var total = 0
            var edges = 0
            for (py in y1..y2) {
                var prevHit = false
                for (px in x1..x2) {
                    val idx = idxAt(px, py)
                    if (idx < 0) continue
                    val hit = isAutoplayLabelBright(chR(idx), chG(idx), chB(idx))
                    total++
                    if (hit) bright++
                    if (px > x1 && hit != prevHit) edges++
                    prevHit = hit
                }
            }
            if (total <= 0) return false
            val density = bright.toFloat() / total
            val minBright = max(120, (160f * scale).toInt())
            val minEdges = max(40, (55f * scale).toInt())
            return bright >= minBright && edges >= minEdges && density in 0.04f..0.45f
        }

        fun hasDimmedSiblingDisc(centerX: Int, centerY: Int): Boolean {
            if (centerX - radius.toInt() < 0 || centerX + radius.toInt() >= spec.frameWidth) return false
            val offsets = floatArrayOf(-0.72f, -0.45f, 0f, 0.45f, 0.72f)
            var ringSum = 0f; var ringCount = 0
            var coreSum = 0f; var coreCount = 0
            for (dyF in offsets) {
                val py = (centerY + dyF * radius).toInt()
                if (py !in spec.yMin..spec.yMax) continue
                for (dxF in offsets) {
                    val px = (centerX + dxF * radius).toInt()
                    val idx = idxAt(px, py)
                    if (idx < 0) continue
                    val dist = sqrt(dxF * dxF + dyF * dyF)
                    val l = lumaAt(idx)
                    if (dist in 0.56f..0.88f) {
                        ringSum += l; ringCount++
                    } else if (dist <= 0.36f) {
                        coreSum += l; coreCount++
                    }
                }
            }
            var outSum = 0f; var outCount = 0
            for (dyF in floatArrayOf(-1.15f, 0f, 1.15f)) {
                val py = (centerY + dyF * radius).toInt()
                if (py !in spec.yMin..spec.yMax) continue
                for (dxF in floatArrayOf(-1.15f, 1.15f)) {
                    val idx = idxAt((centerX + dxF * radius).toInt(), py)
                    if (idx >= 0) {
                        outSum += lumaAt(idx); outCount++
                    }
                }
            }
            if (ringCount == 0 || coreCount == 0 || outCount == 0) return false
            val rMean = ringSum / ringCount
            val cMean = coreSum / coreCount
            val oMean = outSum / outCount
            return (rMean - oMean) >= 14f && (rMean - cMean) >= 6f
        }

        var adjPairCount = 0
        var splitPairCount = 0
        for (i in discs.indices) {
            for (j in (i + 1) until discs.size) {
                val c1 = discs[i]
                val c2 = discs[j]
                val dx = (c2.cx - c1.cx).toFloat()
                val dy = abs(c2.cy - c1.cy).toFloat()
                if (dy > 0.30f * diameter) continue
                if (dx in (1.55f * diameter)..(2.55f * diameter) && hasCleanCreamGap(c1, c2)) {
                    adjPairCount++
                } else if (dx > 2.55f * diameter && dx <= 11.5f * diameter &&
                    c1.score >= 34 && c2.score >= 34 && max(c1.score, c2.score) >= 38 &&
                    hasCleanCreamGap(c1, c2)
                ) {
                    val hasCorroboration = discs.size >= 3 ||
                        hasAutoLabelStrokes(c1, c2.cx - radius * 1.1f) ||
                        hasDimmedSiblingDisc((c2.cx - 1.96f * diameter).toInt(), c2.cy) ||
                        hasDimmedSiblingDisc((c2.cx + 1.96f * diameter).toInt(), c2.cy) ||
                        hasDimmedSiblingDisc((c1.cx + c2.cx) / 2, c2.cy)
                    if (hasCorroboration) {
                        splitPairCount++
                    }
                }
            }
        }

        var singleAnchoredConfirmed = false
        if (adjPairCount == 0 && splitPairCount == 0 && discs.size == 1) {
            val c1 = discs[0]
            if (c1.cx < (spec.frameWidth * 0.14f).toInt() && c1.score >= 40 &&
                hasAutoLabelStrokes(c1, c1.cx + 4.8f * diameter)
            ) {
                val dimmedXs = ArrayList<Int>()
                val dimStart = (c1.cx + 2.5f * diameter).toInt()
                val dimEnd = min(spec.xMax - radius.toInt() - 1, (c1.cx + 11.5f * diameter).toInt())
                val dimStepX = max(2, (diameter / 8f).toInt())
                val dimStepY = max(2, (diameter / 12f).toInt())
                var dxCand = dimStart
                while (dxCand <= dimEnd) {
                    var matchedY = false
                    var dyCand = (c1.cy - (0.15f * diameter).toInt()).coerceAtLeast(spec.yMin)
                    val dyEnd = (c1.cy + (0.15f * diameter).toInt()).coerceAtMost(spec.yMax)
                    while (dyCand <= dyEnd) {
                        if (hasDimmedSiblingDisc(dxCand, dyCand)) {
                            matchedY = true
                            break
                        }
                        dyCand += dimStepY
                    }
                    if (matchedY) {
                        val lastX = dimmedXs.lastOrNull()
                        if (lastX == null || abs(dxCand - lastX) >= nmsDist) {
                            dimmedXs.add(dxCand)
                        }
                    }
                    dxCand += dimStepX
                }
                if (dimmedXs.size >= 2) {
                    singleAnchoredConfirmed = true
                }
            }
        }

        val peakCream = max(rawCreamCount, discs.maxOfOrNull { it.creamHits } ?: 0)
        return GenshinSkipEngine.DialogueHeaderMetrics(
            discCount = discs.size,
            alignedPairCount = adjPairCount,
            peakDiscCreamMatches = peakCream,
            blackRatio = blackRatio,
            whiteRatio = whiteRatio,
            splitPairCount = splitPairCount,
            singleAnchoredConfirmed = singleAnchoredConfirmed
        )
    }
}
