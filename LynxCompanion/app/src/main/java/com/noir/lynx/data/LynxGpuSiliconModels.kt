package com.noir.lynx.data

/**
 * Data models for Deep Silicon Kernel GPU Tuning, SurfaceFlinger Pipeline,
 * and Studio Lab Telemetry/Benchmark.
 */
data class GpuSiliconInfo(
    // Adreno Deep Silicon
    val sptpPcEnabled: Boolean = true,
    val skipSaveRestore: Boolean = false,
    val preemptLevel: Int = 1, // 0 = No preemption, 1 = Cmd buffer, 2 = Drawcall
    val wakeNice: Int = 0, // -20 to 19
    val minPwrLevel: Int = 0,
    val maxPwrLevel: Int = 0,
    val numPwrLevels: Int = 0,
    val gpuLlcSliceEnabled: Boolean = false,
    val isSptpSupported: Boolean = false,
    val isSkipSaveRestoreSupported: Boolean = false,
    val isPreemptLevelSupported: Boolean = false,
    val isWakeNiceSupported: Boolean = false,
    val isMinPwrLevelSupported: Boolean = false,
    val isLlcSliceSupported: Boolean = false,

    // SurfaceFlinger Pipeline & TouchBoost Precision
    val sfEarlyPhaseOffsetNs: Long = 0L,
    val sfEarlyGlPhaseOffsetNs: Long = 0L,
    val touchBoostDurationMs: Int = 500,
    val isVsyncSwapBypass: Boolean = false,
    val isEarlyPhaseOffsetActive: Boolean = false,

    // Studio Lab Telemetry & Stability Benchmark
    val fpsOnePercentLow: Float = 0f,
    val fpsZeroOnePercentLow: Float = 0f,
    val isStressBenchmarkRunning: Boolean = false,
    val stressBenchmarkProgress: Float = 0f,
    val stressBenchmarkDurationSec: Int = 30,
    val stressBenchmarkInitialClockMhz: Int = 0,
    val stressBenchmarkFinalClockMhz: Int = 0,
    val stressBenchmarkInitialTempC: Float = 0f,
    val stressBenchmarkFinalTempC: Float = 0f,
    val stressBenchmarkStabilityScore: Int = 100,
    val stressBenchmarkThrottlingPercent: Float = 0f,
    val stressBenchmarkLog: List<String> = emptyList()
)

data class StressBenchmarkReport(
    val durationSeconds: Int,
    val initialClockMhz: Int,
    val finalClockMhz: Int,
    val peakClockMhz: Int,
    val initialTempC: Float,
    val peakTempC: Float,
    val finalTempC: Float,
    val stabilityScore: Int,
    val throttlingDropPercent: Float,
    val frameSpikeCount: Int,
    val passed: Boolean
)
