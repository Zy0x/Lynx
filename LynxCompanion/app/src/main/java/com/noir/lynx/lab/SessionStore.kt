package com.noir.lynx.lab

import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SessionStore {

    private const val MAX_SAVED_SESSIONS = 20

    private fun getSessionsDir(context: Context): File {
        val dir = File(context.filesDir, "lab/sessions")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun saveSession(context: Context, report: FrameSessionReport): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = getSessionsDir(context)
            val file = File(dir, "session_${report.timestamp}_${report.packageName}.json")

            val json = JSONObject().apply {
                put("id", report.id)
                put("packageName", report.packageName)
                put("timestamp", report.timestamp)
                put("sessionDurationSec", report.sessionDurationSec)
                put("totalFrames", report.totalFrames)
                put("avgFps", report.avgFps)
                put("low1PctFps", report.low1PctFps)
                put("low01PctFps", report.low01PctFps)
                put("frameTimeVarianceMs", report.frameTimeVarianceMs)
                put("p50Ms", report.p50Ms)
                put("p95Ms", report.p95Ms)
                put("p99Ms", report.p99Ms)
                put("stabilityPct", report.stabilityPct)
                put("jankCount", report.jankCount)
                put("peakTempC", report.peakTempC)
                put("avgTempC", report.avgTempC)
                put("peakGpuMhz", report.peakGpuMhz)
                put("recommendation", report.recommendation ?: "")

                val dropsArray = JSONArray()
                report.dropEvents.forEach { d ->
                    val dObj = JSONObject().apply {
                        put("timeOffsetSec", d.timeOffsetSec)
                        put("frameTimeMs", d.frameTimeMs)
                        put("gpuClockMhz", d.gpuClockMhz)
                        put("gpuLoadPct", d.gpuLoadPct)
                        put("tempC", d.tempC)
                        put("suspectedCause", d.suspectedCause)
                    }
                    dropsArray.put(dObj)
                }
                put("dropEvents", dropsArray)

                // Downsample samples if large (> 1200 items) to keep file compact
                val step = if (report.samples.size > 1200) (report.samples.size / 600) else 1
                val samplesArray = JSONArray()
                for (i in report.samples.indices step step) {
                    val s = report.samples[i]
                    val sObj = JSONObject().apply {
                        put("t", s.timestampMs)
                        put("d", s.frameDurationMs)
                        put("c", s.gpuClockMhz)
                        put("l", s.gpuLoadPct)
                        put("temp", s.tempC)
                    }
                    samplesArray.put(sObj)
                }
                put("samples", samplesArray)
            }

            file.writeText(json.toString(2))

            // Enforce max 20 retention
            val allFiles = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            if (allFiles.size > MAX_SAVED_SESSIONS) {
                allFiles.drop(MAX_SAVED_SESSIONS).forEach { it.delete() }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun listSessions(context: Context): List<FrameSessionReport> = withContext(Dispatchers.IO) {
        val dir = getSessionsDir(context)
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        val list = mutableListOf<FrameSessionReport>()

        for (file in files) {
            try {
                val str = file.readText()
                val obj = JSONObject(str)

                val drops = mutableListOf<DropEvent>()
                val dArr = obj.optJSONArray("dropEvents")
                if (dArr != null) {
                    for (i in 0 until dArr.length()) {
                        val dObj = dArr.getJSONObject(i)
                        drops.add(
                            DropEvent(
                                timeOffsetSec = dObj.optInt("timeOffsetSec"),
                                frameTimeMs = dObj.optDouble("frameTimeMs").toFloat(),
                                gpuClockMhz = dObj.optInt("gpuClockMhz"),
                                gpuLoadPct = dObj.optInt("gpuLoadPct"),
                                tempC = dObj.optDouble("tempC").toFloat(),
                                suspectedCause = dObj.optString("suspectedCause")
                            )
                        )
                    }
                }

                val samples = mutableListOf<FrameSample>()
                val sArr = obj.optJSONArray("samples")
                if (sArr != null) {
                    for (i in 0 until sArr.length()) {
                        val sObj = sArr.getJSONObject(i)
                        samples.add(
                            FrameSample(
                                timestampMs = sObj.optLong("t"),
                                frameDurationMs = sObj.optDouble("d").toFloat(),
                                gpuClockMhz = sObj.optInt("c"),
                                gpuLoadPct = sObj.optInt("l"),
                                tempC = sObj.optDouble("temp").toFloat()
                            )
                        )
                    }
                }

                list.add(
                    FrameSessionReport(
                        id = obj.optString("id", file.nameWithoutExtension),
                        packageName = obj.optString("packageName", "Game / App"),
                        timestamp = obj.optLong("timestamp", file.lastModified()),
                        sessionDurationSec = obj.optInt("sessionDurationSec"),
                        totalFrames = obj.optInt("totalFrames"),
                        avgFps = obj.optDouble("avgFps").toFloat(),
                        low1PctFps = obj.optDouble("low1PctFps").toFloat(),
                        low01PctFps = obj.optDouble("low01PctFps").toFloat(),
                        frameTimeVarianceMs = obj.optDouble("frameTimeVarianceMs").toFloat(),
                        p50Ms = obj.optDouble("p50Ms").toFloat(),
                        p95Ms = obj.optDouble("p95Ms").toFloat(),
                        p99Ms = obj.optDouble("p99Ms").toFloat(),
                        stabilityPct = obj.optDouble("stabilityPct").toFloat(),
                        jankCount = obj.optInt("jankCount"),
                        peakTempC = obj.optDouble("peakTempC").toFloat(),
                        avgTempC = obj.optDouble("avgTempC").toFloat(),
                        peakGpuMhz = obj.optInt("peakGpuMhz"),
                        dropEvents = drops,
                        samples = samples,
                        recommendation = obj.optString("recommendation").ifBlank { null }
                    )
                )
            } catch (e: Exception) {
                // Ignore corrupted file
            }
        }
        list
    }

    suspend fun exportToDownload(context: Context, report: FrameSessionReport): String? = withContext(Dispatchers.IO) {
        try {
            val downloadDir = File("/sdcard/Download/Lynx")
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val cleanPkg = report.packageName.replace(".", "_")
            val targetFile = File(downloadDir, "Lynx_Lab_${cleanPkg}_${report.timestamp}.csv")

            val sb = StringBuilder()
            sb.appendLine("LYNX KERNEL MANAGER - PERFORMANCE LAB SESSION REPORT")
            sb.appendLine("Package,${report.packageName}")
            sb.appendLine("Duration (sec),${report.sessionDurationSec}")
            sb.appendLine("Total Frames,${report.totalFrames}")
            sb.appendLine("Average FPS,${report.avgFps}")
            sb.appendLine("1% Low FPS,${report.low1PctFps}")
            sb.appendLine("0.1% Low FPS,${report.low01PctFps}")
            sb.appendLine("Stability (%),${report.stabilityPct}")
            sb.appendLine("Jank Count,${report.jankCount}")
            sb.appendLine("Peak Temp (°C),${report.peakTempC}")
            sb.appendLine("Peak GPU Clock (MHz),${report.peakGpuMhz}")
            sb.appendLine("Recommendation,${report.recommendation ?: "N/A"}")
            sb.appendLine()
            sb.appendLine("TimestampMs,FrameDurationMs,GpuClockMHz,GpuLoadPct,TempC")
            for (s in report.samples) {
                sb.appendLine("${s.timestampMs},${s.frameDurationMs},${s.gpuClockMhz},${s.gpuLoadPct},${s.tempC}")
            }

            targetFile.writeText(sb.toString())
            targetFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
