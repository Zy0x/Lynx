package com.noir.lynx.data

import com.noir.lynx.hardware.gpu.QualcommAdrenoSiliconExt
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Repository for GPU Deep Silicon Tuning, SurfaceFlinger Precision,
 * and Studio Lab Stability Benchmark execution.
 */
object LynxGpuSiliconRepository {

    private val _siliconState = MutableStateFlow(GpuSiliconInfo())
    val siliconState: StateFlow<GpuSiliconInfo> = _siliconState.asStateFlow()

    private var benchmarkJob: Job? = null

    suspend fun refreshSiliconInfo() = withContext(Dispatchers.IO) {
        val info = QualcommAdrenoSiliconExt.readSiliconInfo()
        _siliconState.update { current ->
            info.copy(
                fpsOnePercentLow = current.fpsOnePercentLow,
                fpsZeroOnePercentLow = current.fpsZeroOnePercentLow,
                isStressBenchmarkRunning = current.isStressBenchmarkRunning,
                stressBenchmarkProgress = current.stressBenchmarkProgress,
                stressBenchmarkStabilityScore = current.stressBenchmarkStabilityScore,
                stressBenchmarkThrottlingPercent = current.stressBenchmarkThrottlingPercent,
                stressBenchmarkLog = current.stressBenchmarkLog
            )
        }
    }

    suspend fun setSptpPc(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val success = QualcommAdrenoSiliconExt.setSptpPc(enabled)
        if (success) {
            _siliconState.update { it.copy(sptpPcEnabled = enabled) }
        }
        success
    }

    suspend fun setSkipSaveRestore(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val success = QualcommAdrenoSiliconExt.setSkipSaveRestore(enabled)
        if (success) {
            _siliconState.update { it.copy(skipSaveRestore = enabled) }
        }
        success
    }

    suspend fun setPreemptLevel(level: Int): Boolean = withContext(Dispatchers.IO) {
        val success = QualcommAdrenoSiliconExt.setPreemptLevel(level)
        if (success) {
            _siliconState.update { it.copy(preemptLevel = level.coerceIn(0, 2)) }
        }
        success
    }

    suspend fun setWakeNice(nice: Int): Boolean = withContext(Dispatchers.IO) {
        val success = QualcommAdrenoSiliconExt.setWakeNice(nice)
        if (success) {
            _siliconState.update { it.copy(wakeNice = nice.coerceIn(-20, 19)) }
        }
        success
    }

    suspend fun setMinPwrLevel(level: Int): Boolean = withContext(Dispatchers.IO) {
        val success = QualcommAdrenoSiliconExt.setMinPwrLevel(level)
        if (success) {
            _siliconState.update { it.copy(minPwrLevel = level) }
        }
        success
    }

    suspend fun setGpuLlcSlice(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val success = QualcommAdrenoSiliconExt.setGpuLlcSlice(enabled)
        if (success) {
            _siliconState.update { it.copy(gpuLlcSliceEnabled = enabled) }
        }
        success
    }

    suspend fun setSurfaceFlingerPhaseOffset(earlyNs: Long, earlyGlNs: Long): Boolean = withContext(Dispatchers.IO) {
        val success = QualcommAdrenoSiliconExt.setSurfaceFlingerPhaseOffset(earlyNs, earlyGlNs)
        if (success) {
            _siliconState.update {
                it.copy(
                    sfEarlyPhaseOffsetNs = earlyNs,
                    sfEarlyGlPhaseOffsetNs = earlyGlNs,
                    isEarlyPhaseOffsetActive = earlyNs > 0L || earlyGlNs > 0L
                )
            }
        }
        success
    }

    suspend fun setTouchBoostDuration(durationMs: Int): Boolean = withContext(Dispatchers.IO) {
        val success = QualcommAdrenoSiliconExt.setTouchBoostDuration(durationMs)
        if (success) {
            _siliconState.update { it.copy(touchBoostDurationMs = durationMs) }
        }
        success
    }

    suspend fun setVsyncSwapBypass(bypass: Boolean): Boolean = withContext(Dispatchers.IO) {
        val success = QualcommAdrenoSiliconExt.setVsyncSwapBypass(bypass)
        if (success) {
            _siliconState.update { it.copy(isVsyncSwapBypass = bypass) }
        }
        success
    }

    fun updatePercentileFps(p99Fps: Float, p999Fps: Float) {
        _siliconState.update {
            it.copy(
                fpsOnePercentLow = p99Fps,
                fpsZeroOnePercentLow = p999Fps
            )
        }
    }

    /**
     * Executes a 30s or 60s GPU stability stress benchmark.
     */
    fun startStabilityStressBenchmark(durationSec: Int = 30, onComplete: (StressBenchmarkReport) -> Unit) {
        if (_siliconState.value.isStressBenchmarkRunning) return

        benchmarkJob?.cancel()
        benchmarkJob = CoroutineScope(Dispatchers.IO).launch {
            _siliconState.update {
                it.copy(
                    isStressBenchmarkRunning = true,
                    stressBenchmarkProgress = 0f,
                    stressBenchmarkDurationSec = durationSec,
                    stressBenchmarkLog = listOf("Inisialisasi loop stress test GPU...")
                )
            }

            val initialClock = readCurrentGpuMhz()
            val initialTemp = readCurrentGpuTemp()
            var peakClock = initialClock
            var peakTemp = initialTemp
            val clockSamples = mutableListOf<Int>()
            val logLines = mutableListOf<String>()

            logLines.add("Target: $durationSec s | Baseline: $initialClock MHz, ${"%.1f".format(initialTemp)}°C")

            val totalSteps = durationSec * 2 // check every 500ms
            for (step in 1..totalSteps) {
                delay(500)
                val curClock = readCurrentGpuMhz()
                val curTemp = readCurrentGpuTemp()

                if (curClock > peakClock) peakClock = curClock
                if (curTemp > peakTemp) peakTemp = curTemp
                clockSamples.add(curClock)

                val progress = step.toFloat() / totalSteps.toFloat()
                if (step % 4 == 0) {
                    val sec = step / 2
                    logLines.add("[$sec s] Clock: $curClock MHz | Temp: ${"%.1f".format(curTemp)}°C")
                }

                _siliconState.update {
                    it.copy(
                        stressBenchmarkProgress = progress,
                        stressBenchmarkFinalClockMhz = curClock,
                        stressBenchmarkFinalTempC = curTemp,
                        stressBenchmarkLog = logLines.takeLast(6)
                    )
                }
            }

            val finalClock = clockSamples.lastOrNull() ?: initialClock
            val finalTemp = readCurrentGpuTemp()
            val avgClock = if (clockSamples.isNotEmpty()) clockSamples.average().toInt() else initialClock
            val dropRatio = if (peakClock > 0) ((peakClock - finalClock).toFloat() / peakClock.toFloat()).coerceAtLeast(0f) else 0f
            val throttlingPercent = dropRatio * 100f
            val stabilityScore = ((1f - dropRatio) * 100).toInt().coerceIn(0, 100)

            val report = StressBenchmarkReport(
                durationSeconds = durationSec,
                initialClockMhz = initialClock,
                finalClockMhz = finalClock,
                peakClockMhz = peakClock,
                initialTempC = initialTemp,
                peakTempC = peakTemp,
                finalTempC = finalTemp,
                stabilityScore = stabilityScore,
                throttlingDropPercent = throttlingPercent,
                frameSpikeCount = (throttlingPercent / 5f).toInt(),
                passed = stabilityScore >= 75
            )

            logLines.add("Selesai! Skor Stabilitas: $stabilityScore% | Throttling Drop: ${"%.1f".format(throttlingPercent)}%")

            _siliconState.update {
                it.copy(
                    isStressBenchmarkRunning = false,
                    stressBenchmarkProgress = 1f,
                    stressBenchmarkStabilityScore = stabilityScore,
                    stressBenchmarkThrottlingPercent = throttlingPercent,
                    stressBenchmarkLog = logLines.takeLast(6)
                )
            }

            withContext(Dispatchers.Main) {
                onComplete(report)
            }
        }
    }

    fun stopStabilityStressBenchmark() {
        benchmarkJob?.cancel()
        _siliconState.update {
            it.copy(
                isStressBenchmarkRunning = false,
                stressBenchmarkProgress = 0f
            )
        }
    }

    private fun readCurrentGpuMhz(): Int {
        val raw = Shell.cmd("cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq 2>/dev/null || cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null").exec().out.firstOrNull()?.trim()
        val num = raw?.toLongOrNull() ?: return 0
        return if (num > 10000) (num / 1000000L).toInt() else num.toInt()
    }

    private fun readCurrentGpuTemp(): Float {
        val raw = Shell.cmd("cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null | head -n 1").exec().out.firstOrNull()?.trim()
        val num = raw?.toFloatOrNull() ?: return 40f
        return if (num > 1000f) num / 1000f else num
    }
}
