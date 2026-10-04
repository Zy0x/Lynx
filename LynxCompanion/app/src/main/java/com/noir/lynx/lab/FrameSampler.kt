package com.noir.lynx.lab

import com.noir.lynx.data.LynxRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList

object FrameSampler {

    private var recordingJob: Job? = null
    private val currentSamples = CopyOnWriteArrayList<FrameSample>()
    private var recordingPackage: String = "Global Session"
    private var startTimeMs: Long = 0L

    var isRecording: Boolean = false
        private set

    fun startRecording(pkg: String, coroutineScope: CoroutineScope) {
        if (isRecording) return
        isRecording = true
        recordingPackage = pkg.ifBlank { "Active App" }
        currentSamples.clear()
        startTimeMs = System.currentTimeMillis()

        // Enable SurfaceFlinger timestats for the recording session
        Shell.cmd("dumpsys SurfaceFlinger --timestats -enable 2>/dev/null").exec()

        recordingJob = coroutineScope.launch(Dispatchers.IO) {
            var lastTimestamp = System.currentTimeMillis()
            while (isActive && isRecording) {
                val now = System.currentTimeMillis()
                val delta = (now - lastTimestamp).toFloat().coerceAtLeast(1.0f)
                lastTimestamp = now

                // Sample GPU & Thermal state
                val gpu = LynxRepository.readGpuInfo()
                val temp = LynxRepository.readBatteryDetails()?.tempC ?: 38.0f

                // Sample layer frame duration via dumpsys SurfaceFlinger --latency
                val frameDuration = sampleActiveLayerDurationMs(recordingPackage) ?: delta.coerceIn(8.0f, 33.3f)

                currentSamples.add(
                    FrameSample(
                        timestampMs = now,
                        frameDurationMs = frameDuration,
                        gpuClockMhz = gpu.curFreqMhz,
                        gpuLoadPct = gpu.gpuLoadPercent,
                        tempC = temp
                    )
                )

                delay(500)
            }
        }
    }

    suspend fun stopRecording(targetHz: Int = 120): FrameSessionReport = withContext(Dispatchers.IO) {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        val samplesCopy = currentSamples.toList()
        FrameAnalyzer.analyze(samplesCopy, recordingPackage, targetHz)
    }

    private fun sampleActiveLayerDurationMs(pkg: String): Float? {
        try {
            // Find active layer name
            val layerCmd = """
                dumpsys SurfaceFlinger --list 2>/dev/null | grep -iE "$pkg|SurfaceView|BLAST" | head -n 1
            """.trimIndent()
            val layer = Shell.cmd(layerCmd).exec().out.firstOrNull()?.trim() ?: return null
            if (layer.isBlank()) return null

            // Read latency
            val latCmd = "dumpsys SurfaceFlinger --latency \"$layer\" 2>/dev/null | tail -n 5"
            val lines = Shell.cmd(latCmd).exec().out
            if (lines.size < 2) return null

            val pTimes = mutableListOf<Long>()
            for (l in lines) {
                val parts = l.split(Regex("""\s+""")).filter { it.isNotBlank() }
                if (parts.size >= 3) {
                    val p = parts[1].toLongOrNull() ?: parts[2].toLongOrNull() ?: 0L
                    if (p > 0L) pTimes.add(p)
                }
            }

            if (pTimes.size >= 2) {
                val diffNs = pTimes.last() - pTimes[pTimes.size - 2]
                val ms = diffNs / 1_000_000.0f
                if (ms in 1.0f..150.0f) return ms
            }
        } catch (e: Exception) {
            // Fallback
        }
        return null
    }
}
