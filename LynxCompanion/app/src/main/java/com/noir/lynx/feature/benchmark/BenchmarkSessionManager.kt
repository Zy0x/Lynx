package com.noir.lynx.feature.benchmark

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.noir.lynx.data.LynxBenchmarkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SavedBenchmarkSession(
    val id: String,
    val sessionNumber: Int,
    val timestampMs: Long,
    val dateFormatted: String,
    val appPackage: String,
    val appName: String,
    val durationSeconds: Int,
    val sampledFrames: Int,
    val averageFps: Float,
    val medianFrametimeMs: Float,
    val averageFrametimeMs: Float,
    val minFrametimeMs: Float,
    val maxFrametimeMs: Float,
    val frametimeJitterMs: Float,
    val fps1PercentLow: Float,
    val fps01PercentLow: Float,
    val jankyFramesCount: Int,
    val jankyFramesPercent: Float,
    val avgCpuClockMhz: Int,
    val avgGpuClockMhz: Int,
    val avgGpuLoadPct: Int,
    val avgBatteryTempC: Float,
    val avgBatteryWatt: Float,
    val activeProfile: String,
    val frametimes: List<Float> = emptyList(),
    val stabilityGrade: String,
    val summary: String
)

object BenchmarkSessionManager {

    private val _savedSessions = MutableStateFlow<List<SavedBenchmarkSession>>(emptyList())
    val savedSessions: StateFlow<List<SavedBenchmarkSession>> = _savedSessions.asStateFlow()

    private const val SESSIONS_FILE_NAME = "benchmark_sessions.json"

    fun loadSessions(context: Context) {
        runCatching {
            val file = File(context.filesDir, SESSIONS_FILE_NAME)
            if (!file.exists()) {
                _savedSessions.value = emptyList()
                return
            }
            val content = file.readText()
            if (content.isBlank()) {
                _savedSessions.value = emptyList()
                return
            }
            val jsonArray = JSONArray(content)
            val list = mutableListOf<SavedBenchmarkSession>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val frametimesList = mutableListOf<Float>()
                val fArray = obj.optJSONArray("frametimes")
                if (fArray != null) {
                    for (j in 0 until fArray.length()) {
                        frametimesList.add(fArray.optDouble(j, 0.0).toFloat())
                    }
                }

                list.add(
                    SavedBenchmarkSession(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        sessionNumber = obj.optInt("sessionNumber", i + 1),
                        timestampMs = obj.optLong("timestampMs", System.currentTimeMillis()),
                        dateFormatted = obj.optString("dateFormatted", "Waktu tidak diketahui"),
                        appPackage = obj.optString("appPackage", ""),
                        appName = obj.optString("appName", "Game Target"),
                        durationSeconds = obj.optInt("durationSeconds", 60),
                        sampledFrames = obj.optInt("sampledFrames", 0),
                        averageFps = obj.optDouble("averageFps", 0.0).toFloat(),
                        medianFrametimeMs = obj.optDouble("medianFrametimeMs", 0.0).toFloat(),
                        averageFrametimeMs = obj.optDouble("averageFrametimeMs", 0.0).toFloat(),
                        minFrametimeMs = obj.optDouble("minFrametimeMs", 0.0).toFloat(),
                        maxFrametimeMs = obj.optDouble("maxFrametimeMs", 0.0).toFloat(),
                        frametimeJitterMs = obj.optDouble("frametimeJitterMs", 0.0).toFloat(),
                        fps1PercentLow = obj.optDouble("fps1PercentLow", 0.0).toFloat(),
                        fps01PercentLow = obj.optDouble("fps01PercentLow", 0.0).toFloat(),
                        jankyFramesCount = obj.optInt("jankyFramesCount", 0),
                        jankyFramesPercent = obj.optDouble("jankyFramesPercent", 0.0).toFloat(),
                        avgCpuClockMhz = obj.optInt("avgCpuClockMhz", 0),
                        avgGpuClockMhz = obj.optInt("avgGpuClockMhz", 0),
                        avgGpuLoadPct = obj.optInt("avgGpuLoadPct", 0),
                        avgBatteryTempC = obj.optDouble("avgBatteryTempC", 0.0).toFloat(),
                        avgBatteryWatt = obj.optDouble("avgBatteryWatt", 0.0).toFloat(),
                        activeProfile = obj.optString("activeProfile", "extreme"),
                        frametimes = frametimesList,
                        stabilityGrade = obj.optString("stabilityGrade", "Normal"),
                        summary = obj.optString("summary", "")
                    )
                )
            }
            _savedSessions.value = list
        }
    }

    private fun persistSessions(context: Context) {
        runCatching {
            val file = File(context.filesDir, SESSIONS_FILE_NAME)
            val jsonArray = JSONArray()
            for (s in _savedSessions.value) {
                val obj = JSONObject().apply {
                    put("id", s.id)
                    put("sessionNumber", s.sessionNumber)
                    put("timestampMs", s.timestampMs)
                    put("dateFormatted", s.dateFormatted)
                    put("appPackage", s.appPackage)
                    put("appName", s.appName)
                    put("durationSeconds", s.durationSeconds)
                    put("sampledFrames", s.sampledFrames)
                    put("averageFps", s.averageFps.toDouble())
                    put("medianFrametimeMs", s.medianFrametimeMs.toDouble())
                    put("averageFrametimeMs", s.averageFrametimeMs.toDouble())
                    put("minFrametimeMs", s.minFrametimeMs.toDouble())
                    put("maxFrametimeMs", s.maxFrametimeMs.toDouble())
                    put("frametimeJitterMs", s.frametimeJitterMs.toDouble())
                    put("fps1PercentLow", s.fps1PercentLow.toDouble())
                    put("fps01PercentLow", s.fps01PercentLow.toDouble())
                    put("jankyFramesCount", s.jankyFramesCount)
                    put("jankyFramesPercent", s.jankyFramesPercent.toDouble())
                    put("avgCpuClockMhz", s.avgCpuClockMhz)
                    put("avgGpuClockMhz", s.avgGpuClockMhz)
                    put("avgGpuLoadPct", s.avgGpuLoadPct)
                    put("avgBatteryTempC", s.avgBatteryTempC.toDouble())
                    put("avgBatteryWatt", s.avgBatteryWatt.toDouble())
                    put("activeProfile", s.activeProfile)
                    put("stabilityGrade", s.stabilityGrade)
                    put("summary", s.summary)

                    val fArray = JSONArray()
                    // Keep up to 200 samples to keep JSON clean and lightweight
                    val sampled = if (s.frametimes.size > 200) {
                        val step = s.frametimes.size / 200
                        s.frametimes.filterIndexed { idx, _ -> idx % step == 0 }
                    } else {
                        s.frametimes
                    }
                    for (ft in sampled) {
                        fArray.put(ft.toDouble())
                    }
                    put("frametimes", fArray)
                }
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString(2))
        }
    }

    fun saveSession(context: Context, result: LynxBenchmarkResult): SavedBenchmarkSession {
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
        val dateStr = dateFormat.format(Date(now))
        val nextNumber = (_savedSessions.value.maxOfOrNull { it.sessionNumber } ?: 0) + 1

        val grade = when {
            result.jankyFramesPercent <= 5f -> "Ultra Smooth — Pacing Sempurna"
            result.jankyFramesPercent <= 15f -> "Sangat Baik — Stabilitas Tinggi"
            result.jankyFramesPercent <= 25f -> "Cukup Baik — Stutter Ringan"
            else -> "Fluktuatif — Perlu Optimasi"
        }

        val summary = "Benchmark ${result.appName.ifBlank { "Game Target" }} (${result.durationSeconds}s): Rata-rata ${String.format(Locale.US, "%.1f", result.averageFps)} FPS, 1% Low: ${String.format(Locale.US, "%.1f", result.fps1PercentLow)} FPS, Median Frametime: ${String.format(Locale.US, "%.1f", result.medianFrametimeMs)} ms, Jitter: ±${String.format(Locale.US, "%.1f", result.frametimeJitterMs)} ms. Frame Jank: ${result.jankyFramesPercent}%."

        val session = SavedBenchmarkSession(
            id = UUID.randomUUID().toString(),
            sessionNumber = nextNumber,
            timestampMs = now,
            dateFormatted = dateStr,
            appPackage = result.appPackage,
            appName = result.appName.ifBlank { result.appPackage.substringAfterLast('.').ifBlank { "Game Target" } },
            durationSeconds = result.durationSeconds,
            sampledFrames = result.sampledFrames,
            averageFps = result.averageFps,
            medianFrametimeMs = result.medianFrametimeMs,
            averageFrametimeMs = result.averageFrametimeMs,
            minFrametimeMs = result.minFrametimeMs,
            maxFrametimeMs = result.maxFrametimeMs,
            frametimeJitterMs = result.frametimeJitterMs,
            fps1PercentLow = result.fps1PercentLow,
            fps01PercentLow = result.fps01PercentLow,
            jankyFramesCount = result.jankyFramesCount,
            jankyFramesPercent = result.jankyFramesPercent,
            avgCpuClockMhz = result.avgCpuClockMhz,
            avgGpuClockMhz = result.avgGpuClockMhz,
            avgGpuLoadPct = result.avgGpuLoadPct,
            avgBatteryTempC = result.avgBatteryTempC,
            avgBatteryWatt = result.avgBatteryWatt,
            activeProfile = result.activeProfile,
            frametimes = result.frametimes,
            stabilityGrade = grade,
            summary = summary
        )

        _savedSessions.update { listOf(session) + it }
        persistSessions(context)
        return session
    }

    fun deleteSession(context: Context, sessionId: String) {
        _savedSessions.update { list ->
            list.filterNot { it.id == sessionId }
        }
        persistSessions(context)
        Toast.makeText(context, "Sesi benchmark berhasil dihapus", Toast.LENGTH_SHORT).show()
    }

    fun clearAllSessions(context: Context) {
        _savedSessions.value = emptyList()
        runCatching { File(context.filesDir, SESSIONS_FILE_NAME).delete() }
        Toast.makeText(context, "Seluruh riwayat sesi benchmark dibersihkan", Toast.LENGTH_SHORT).show()
    }

    fun exportSavedSessionReport(context: Context, session: SavedBenchmarkSession): Boolean {
        val sb = StringBuilder()
        sb.appendLine("# 🎮 LAPORAN PROFILING HARDWARE & FRAME PACING — SESI #${session.sessionNumber}")
        sb.appendLine("> Diuji melalui **Lynx Kernel Manager — Lab Benchmark & Optimasi**")
        sb.appendLine("- **Aplikasi / Game Target**: ${session.appName} (`${session.appPackage}`)")
        sb.appendLine("- **Waktu Pengujian**: ${session.dateFormatted}")
        sb.appendLine("- **Durasi Pengujian**: ${session.durationSeconds} Detik (${session.sampledFrames} Frames Sampled)")
        sb.appendLine("- **Profil Kernel Aktif**: `${session.activeProfile.uppercase()}`")
        sb.appendLine("- **Predikat Stabilitas**: **${session.stabilityGrade}**")
        sb.appendLine()
        sb.appendLine("## 📊 1. Analisis Metrik Frame Pacing (SurfaceFlinger)")
        sb.appendLine("- **Rata-rata FPS (Average)**: ${String.format(Locale.US, "%.1f", session.averageFps)} FPS")
        sb.appendLine("- **1% Low FPS**: ${String.format(Locale.US, "%.1f", session.fps1PercentLow)} FPS")
        sb.appendLine("- **0.1% Low FPS**: ${String.format(Locale.US, "%.1f", session.fps01PercentLow)} FPS")
        sb.appendLine("- **Median Frametime**: ${String.format(Locale.US, "%.1f", session.medianFrametimeMs)} ms")
        sb.appendLine("- **Frametime Jitter**: ±${String.format(Locale.US, "%.1f", session.frametimeJitterMs)} ms")
        sb.appendLine("- **Janky Frames (Frame Tersendat)**: ${session.jankyFramesCount} frames (${String.format(Locale.US, "%.1f", session.jankyFramesPercent)}%)")
        sb.appendLine("- **Latensi Min / Max**: ${String.format(Locale.US, "%.1f", session.minFrametimeMs)} ms / ${String.format(Locale.US, "%.1f", session.maxFrametimeMs)} ms")
        sb.appendLine()
        sb.appendLine("## ⚡ 2. Telemetri Hardware Saat Pengujian")
        sb.appendLine("- **Rata-rata CPU Clock**: ${if (session.avgCpuClockMhz > 0) "${session.avgCpuClockMhz} MHz" else "Terkunci Governor"}")
        sb.appendLine("- **Rata-rata GPU Clock & Load**: ${if (session.avgGpuClockMhz > 0) "${session.avgGpuClockMhz} MHz (${session.avgGpuLoadPct}%)" else "N/A"}")
        sb.appendLine("- **Suhu Baterai Rata-rata**: ${if (session.avgBatteryTempC > 0f) "${String.format(Locale.US, "%.1f", session.avgBatteryTempC)} °C" else "N/A"}")
        sb.appendLine("- **Konsumsi Daya Rata-rata**: ${if (session.avgBatteryWatt > 0f) "${String.format(Locale.US, "%.1f", session.avgBatteryWatt)} W" else "N/A"}")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine("*Dihasilkan secara otomatis oleh Lynx Universal Hardware Profiler Engine.*")

        val reportText = sb.toString()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        return if (clipboard != null) {
            val clip = ClipData.newPlainText("Laporan Benchmark Sesi #${session.sessionNumber}", reportText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Laporan Benchmark Sesi #${session.sessionNumber} disalin!", Toast.LENGTH_SHORT).show()
            true
        } else {
            false
        }
    }
}
