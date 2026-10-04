package com.noir.lynx.lab

import kotlin.math.max
import kotlin.math.sqrt

data class FrameSample(
    val timestampMs: Long,
    val frameDurationMs: Float,
    val gpuClockMhz: Int,
    val gpuLoadPct: Int,
    val tempC: Float
)

data class DropEvent(
    val timeOffsetSec: Int,
    val frameTimeMs: Float,
    val gpuClockMhz: Int,
    val gpuLoadPct: Int,
    val tempC: Float,
    val suspectedCause: String
)

data class FrameSessionReport(
    val id: String,
    val packageName: String,
    val timestamp: Long,
    val sessionDurationSec: Int,
    val totalFrames: Int,
    val avgFps: Float,
    val low1PctFps: Float,
    val low01PctFps: Float,
    val frameTimeVarianceMs: Float,
    val p50Ms: Float,
    val p95Ms: Float,
    val p99Ms: Float,
    val stabilityPct: Float,
    val jankCount: Int,
    val peakTempC: Float,
    val avgTempC: Float,
    val peakGpuMhz: Int,
    val dropEvents: List<DropEvent> = emptyList(),
    val samples: List<FrameSample> = emptyList(),
    val recommendation: String? = null
)

object FrameAnalyzer {

    fun analyze(
        samples: List<FrameSample>,
        pkg: String,
        targetHz: Int = 120
    ): FrameSessionReport {
        if (samples.isEmpty()) {
            return FrameSessionReport(
                id = System.currentTimeMillis().toString(),
                packageName = pkg,
                timestamp = System.currentTimeMillis(),
                sessionDurationSec = 0,
                totalFrames = 0,
                avgFps = 0f,
                low1PctFps = 0f,
                low01PctFps = 0f,
                frameTimeVarianceMs = 0f,
                p50Ms = 0f,
                p95Ms = 0f,
                p99Ms = 0f,
                stabilityPct = 100f,
                jankCount = 0,
                peakTempC = 0f,
                avgTempC = 0f,
                peakGpuMhz = 0
            )
        }

        val totalFrames = samples.size
        val durationMs = max(1000L, samples.last().timestampMs - samples.first().timestampMs)
        val durationSec = (durationMs / 1000L).toInt()

        val avgFps = (totalFrames.toFloat() / (durationMs.toFloat() / 1000f)).coerceAtLeast(0f)

        val durations = samples.map { it.frameDurationMs }.sorted()
        val p50 = durations[(durations.size * 0.50).toInt().coerceIn(0, durations.size - 1)]
        val p95 = durations[(durations.size * 0.95).toInt().coerceIn(0, durations.size - 1)]
        val p99 = durations[(durations.size * 0.99).toInt().coerceIn(0, durations.size - 1)]

        // 1% Low FPS: average of worst 1% frame times
        val count1Pct = max(1, (durations.size * 0.01).toInt())
        val worst1PctDurations = durations.takeLast(count1Pct)
        val avgWorst1Pct = worst1PctDurations.average().toFloat()
        val low1PctFps = if (avgWorst1Pct > 0f) (1000f / avgWorst1Pct).coerceAtMost(avgFps) else avgFps

        // 0.1% Low FPS
        val count01Pct = max(1, (durations.size * 0.001).toInt())
        val worst01PctDurations = durations.takeLast(count01Pct)
        val avgWorst01Pct = worst01PctDurations.average().toFloat()
        val low01PctFps = if (avgWorst01Pct > 0f) (1000f / avgWorst01Pct).coerceAtMost(low1PctFps) else low1PctFps

        // Variance & standard deviation
        val avgDuration = durations.average().toFloat()
        val variance = durations.map { (it - avgDuration) * (it - avgDuration) }.average().toFloat()
        val stdDev = sqrt(variance)

        // Ideal vsync target duration
        val idealVsyncMs = 1000f / max(30, targetHz)
        val stableFrames = durations.count { it <= idealVsyncMs * 1.5f }
        val stabilityPct = (stableFrames.toFloat() / durations.size.toFloat() * 100f).coerceIn(0f, 100f)

        // Jank count (> 2x vsync interval)
        val jankThreshold = idealVsyncMs * 2.0f
        val jankCount = durations.count { it > jankThreshold }

        // Thermal & Clock metrics
        val peakTemp = samples.maxOfOrNull { it.tempC } ?: 0f
        val avgTemp = samples.map { it.tempC }.average().toFloat()
        val peakGpu = samples.maxOfOrNull { it.gpuClockMhz } ?: 0

        // Correlate drop events
        val startTime = samples.first().timestampMs
        val dropEvents = mutableListOf<DropEvent>()
        for (i in samples.indices) {
            val s = samples[i]
            if (s.frameDurationMs > jankThreshold) {
                val offsetSec = ((s.timestampMs - startTime) / 1000L).toInt()
                val prevSample = if (i > 0) samples[i - 1] else s
                val cause = DropCorrelator.correlate(s, prevSample, peakGpu)
                dropEvents.add(
                    DropEvent(
                        timeOffsetSec = offsetSec,
                        frameTimeMs = s.frameDurationMs,
                        gpuClockMhz = s.gpuClockMhz,
                        gpuLoadPct = s.gpuLoadPct,
                        tempC = s.tempC,
                        suspectedCause = cause
                    )
                )
            }
        }

        // Generate intelligent recommendation
        val recommendation = DropCorrelator.generateRecommendation(dropEvents, avgFps, peakGpu, targetHz)

        return FrameSessionReport(
            id = System.currentTimeMillis().toString(),
            packageName = pkg,
            timestamp = System.currentTimeMillis(),
            sessionDurationSec = durationSec,
            totalFrames = totalFrames,
            avgFps = avgFps,
            low1PctFps = low1PctFps,
            low01PctFps = low01PctFps,
            frameTimeVarianceMs = stdDev,
            p50Ms = p50,
            p95Ms = p95,
            p99Ms = p99,
            stabilityPct = stabilityPct,
            jankCount = jankCount,
            peakTempC = peakTemp,
            avgTempC = avgTemp,
            peakGpuMhz = peakGpu,
            dropEvents = dropEvents.take(15),
            samples = samples,
            recommendation = recommendation
        )
    }
}
